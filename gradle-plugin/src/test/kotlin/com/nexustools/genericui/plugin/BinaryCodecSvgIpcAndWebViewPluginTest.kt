package com.nexustools.genericui.plugin

import com.nexustools.genericui.plugin.spi.ExtensionBuildContext
import com.nexustools.genericui.plugin.spi.GenericUiExtensionContributor
import com.nexustools.genericui.plugin.spi.GenericUiExtensionContributorRegistry
import com.nexustools.genericui.plugin.spi.PipExtensionContributor
import com.nexustools.genericui.plugin.spi.ServiceWorkerExtensionContributor
import com.nexustools.genericui.plugin.spi.WebViewExtensionContributor
import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BinaryCodecSvgIpcAndWebViewPluginTest {

    @Test
    fun `spi defaults and deleted non-ui dsl modules are cleaned across contributors and registry`() {
        val bareContributor = object : GenericUiExtensionContributor {
            override val extensionModulePath: String = ":extensions:custom:bare"
        }
        assertNull(bareContributor.dslModulePath)
        assertNull(bareContributor.alienModulePath)
        assertTrue(bareContributor.binaryCodecScanSpecs.isEmpty())
        assertNull(PipExtensionContributor.dslModulePath)
        assertNull(ServiceWorkerExtensionContributor.dslModulePath)

        val jsonWithoutDsl = GenericUiExtensionContributorRegistry.parseJsonContributor(
            """
            {
              "extensionModulePath": ":extensions:custom:sensor",
              "alienModulePath": ":extensions:custom:sensor:alien",
              "binaryCodecSimpleAnnotationNames": ["CustomBinary"],
              "binaryCodecQualifiedAnnotationNames": ["com.example.CustomBinary"]
            }
            """.trimIndent()
        )
        assertNotNull(jsonWithoutDsl)
        assertNull(jsonWithoutDsl.dslModulePath)
        assertEquals(":extensions:custom:sensor:alien", jsonWithoutDsl.alienModulePath)
        assertEquals(setOf("CustomBinary"), jsonWithoutDsl.binaryCodecScanSpecs.single().simpleAnnotationNames)
        assertEquals(
            setOf("com.example.CustomBinary"),
            jsonWithoutDsl.binaryCodecScanSpecs.single().qualifiedAnnotationNames
        )

        val deletedDsl = setOf(
            ":extensions:system:serviceworker:dsl",
            ":extensions:media:mediacontrols:dsl",
            ":extensions:ui:pip:dsl",
            ":extensions:game:gamepad:dsl"
        )
        for (mod in deletedDsl) {
            assertFalse(mod in GenericUiGradlePlugin.KNOWN_GENERICUI_MODULES, "Deleted module '$mod' must not be in KNOWN_GENERICUI_MODULES")
        }

        val allConfig = GenericUiExtensionsConfig().apply { enableAll() }
        val allCommon = allConfig.enabledExtensionModulePaths()
        val allDesktop = allConfig.enabledExtensionModulePathsForTarget(GenericUiVariantScope.WINDOWS)
        for (mod in deletedDsl) {
            assertFalse(mod in allCommon, "Deleted module '$mod' must not be emitted in common paths")
            assertFalse(mod in allDesktop, "Deleted module '$mod' must not be emitted in desktop paths")
        }
    }

    @Test
    fun `ipc is strictly target scoped to desktop and android and never wired to web or ios`() {
        val cfg = GenericUiExtensionsConfig().apply { ipc() }
        assertTrue(cfg.ipcEnabled)
        assertTrue(":extensions:system:ipc" in GenericUiGradlePlugin.KNOWN_GENERICUI_MODULES)
        assertFalse(":extensions:system:ipc:dsl" in GenericUiGradlePlugin.KNOWN_GENERICUI_MODULES)

        assertFalse(":extensions:system:ipc" in cfg.enabledExtensionModulePaths())
        assertFalse(":extensions:system:ipc" in cfg.enabledExtensionModulePathsForTarget(GenericUiVariantScope.WEB))
        assertFalse(":extensions:system:ipc" in cfg.enabledExtensionModulePathsForTarget(GenericUiVariantScope.IOS))
        assertFalse(":extensions:system:ipc" in cfg.enabledExtensionModulePathsForTarget(GenericUiVariantScope.IPADOS))

        for (supported in listOf(
            GenericUiVariantScope.WINDOWS,
            GenericUiVariantScope.MACOS,
            GenericUiVariantScope.GTK,
            GenericUiVariantScope.KDE,
            GenericUiVariantScope.SWING,
            GenericUiVariantScope.ANDROID
        )) {
            assertTrue(
                ":extensions:system:ipc" in cfg.enabledExtensionModulePathsForTarget(supported),
                "Expected :extensions:system:ipc for $supported"
            )
            assertTrue(
                "com.nexustools.genericui.ext.ipc.IpcExtension.registerProviders()" in cfg.providerInstallerCallsForBackend(supported),
                "Expected IpcExtension.registerProviders() for $supported"
            )
        }
        for (unsupported in listOf(GenericUiVariantScope.WEB, GenericUiVariantScope.IOS, GenericUiVariantScope.IPADOS)) {
            assertFalse(
                "com.nexustools.genericui.ext.ipc.IpcExtension.registerProviders()" in cfg.providerInstallerCallsForBackend(unsupported),
                "IpcExtension must not be emitted for $unsupported"
            )
        }

        // Verify KMP configuration wiring isolates :extensions:system:ipc strictly to Desktop + Android configs
        val project = ProjectBuilder.builder().withName("ipc-isolation").build()
        val kmpCfgNames = listOf(
            "commonMainImplementation",
            "systemMainImplementation",
            "jvmCommonImplementation",
            "jvmMainImplementation",
            "nativeDesktopMainImplementation",
            "androidMainImplementation",
            "jsMainImplementation",
            "wasmJsMainImplementation",
            "webMainImplementation",
            "iosMainImplementation",
            "ipadosMainImplementation"
        )
        for (name in kmpCfgNames) {
            project.configurations.create(name)
        }
        project.pluginManager.apply("com.nexustools.genericui")
        val ext = project.extensions.getByType(GenericUiExtension::class.java)
        ext.configureMultiplatformTargets = false
        ext.backends { all() }
        ext.extensions {
            serviceWorker()
            webView()
        }
        (project as org.gradle.api.internal.project.ProjectInternal).evaluate()

        fun depCoords(cfgName: String): List<String> =
            project.configurations.getByName(cfgName).dependencies.map { "${it.group}:${it.name}" }

        for (targetCfg in listOf("jvmMainImplementation", "nativeDesktopMainImplementation", "androidMainImplementation")) {
            assertTrue(
                "com.nexustools.genericui.extensions.system:ipc" in depCoords(targetCfg),
                "Expected :extensions:system:ipc in $targetCfg, got ${depCoords(targetCfg)}"
            )
        }
        for (desktopCfg in listOf("jvmMainImplementation", "nativeDesktopMainImplementation")) {
            assertTrue(
                "com.nexustools.genericui.extensions.ui:alien" in depCoords(desktopCfg),
                "Expected :extensions:ui:alien in $desktopCfg"
            )
            assertTrue(
                "com.nexustools.genericui.extensions.graphics.svg:alien" in depCoords(desktopCfg),
                "Expected :extensions:graphics:svg:alien in $desktopCfg"
            )
        }
        assertFalse(
            "com.nexustools.genericui.extensions.ui:alien" in depCoords("androidMainImplementation"),
            "Desktop WebView alien must not leak into androidMainImplementation"
        )
        for (forbiddenCfg in listOf(
            "commonMainImplementation",
            "systemMainImplementation",
            "jvmCommonImplementation",
            "jsMainImplementation",
            "wasmJsMainImplementation",
            "webMainImplementation",
            "iosMainImplementation",
            "ipadosMainImplementation"
        )) {
            assertFalse(
                "com.nexustools.genericui.extensions.system:ipc" in depCoords(forbiddenCfg),
                "Unexpected :extensions:system:ipc in $forbiddenCfg"
            )
            assertFalse(
                "com.nexustools.genericui.extensions.ui:alien" in depCoords(forbiddenCfg),
                "Unexpected Desktop WebView :extensions:ui:alien in $forbiddenCfg"
            )
        }
    }

    @Test
    fun `svg and alien companion modules auto wire and emit bootstrap register calls`() {
        val svgOnly = GenericUiExtensionsConfig().apply { svg() }
        assertTrue(svgOnly.svgEnabled)
        assertEquals(listOf(":extensions:graphics:svg"), svgOnly.enabledExtensionModulePaths())
        svgOnly.dsl(true)
        assertEquals(
            listOf(":extensions:graphics:svg", ":extensions:ui:dsl", ":extensions:graphics:svg:dsl"),
            svgOnly.enabledExtensionModulePaths()
        )
        assertTrue(
            "com.nexustools.genericui.ext.svg.system.SystemSvgProvider.install()" in
                svgOnly.providerInstallerCallsForBackend(GenericUiVariantScope.WINDOWS)
        )
        assertTrue(
            "com.nexustools.genericui.ext.svg.web.WebSvgProvider.install()" in
                svgOnly.providerInstallerCallsForBackend(GenericUiVariantScope.WEB)
        )

        val alienCfg = GenericUiExtensionsConfig().apply {
            alien()
            svg()
            canvas2d()
            canvas3d()
            media()
        }
        val commonMods = alienCfg.enabledExtensionModulePaths()
        assertTrue(":extensions:graphics:svg:alien" in commonMods)
        assertTrue(":extensions:graphics:canvas2d:alien" in commonMods)
        assertTrue(":extensions:graphics:canvas3d:alien" in commonMods)
        assertTrue(":extensions:media:media:alien" in commonMods)

        for (scope in listOf(
            GenericUiVariantScope.WINDOWS,
            GenericUiVariantScope.SWING,
            GenericUiVariantScope.ANDROID,
            GenericUiVariantScope.IOS,
            GenericUiVariantScope.WEB
        )) {
            val calls = alienCfg.providerInstallerCallsForBackend(scope)
            val expectedBootstrap = if (scope == GenericUiVariantScope.WEB) {
                "com.nexustools.genericui.ext.alien.web.WebAlienBootstrap.install()"
            } else {
                "com.nexustools.genericui.ext.alien.system.SystemAlienBootstrap.install()"
            }
            assertTrue(expectedBootstrap in calls, "Missing Alien bootstrap for $scope")
            assertTrue("com.nexustools.genericui.ext.svg.alien.SvgAlienExtension.register()" in calls)
            assertTrue("com.nexustools.genericui.ext.canvas2d.alien.Canvas2DAlienExtension.register()" in calls)
            assertTrue("com.nexustools.genericui.ext.canvas3d.alien.Canvas3DAlienExtension.register()" in calls)
            assertTrue("com.nexustools.genericui.ext.media.alien.MediaAlienExtension.register()" in calls)
        }
    }

    @Test
    fun `native shared library catalog classifies alien companions at rank 5 after alien and base extensions`() {
        assertEquals(
            "genericui-ext-ipc",
            GenericUiNativeSharedLibraryCatalog.classifyKlibGroup(
                "com.nexustools.genericui.extensions.system:ipc",
                "mingw_x64",
                "windows"
            )
        )
        assertEquals(
            "genericui-ext-svg",
            GenericUiNativeSharedLibraryCatalog.classifyKlibGroup(
                "com.nexustools.genericui.extensions.graphics:svg",
                "mingw_x64",
                "windows"
            )
        )
        assertEquals(
            "genericui-ext-alien",
            GenericUiNativeSharedLibraryCatalog.classifyKlibGroup(
                "com.nexustools.genericui.extensions.ui:alien",
                "mingw_x64",
                "windows"
            )
        )
        assertEquals(
            "genericui-ext-svg-alien",
            GenericUiNativeSharedLibraryCatalog.classifyKlibGroup(
                "com.nexustools.genericui.extensions.graphics.svg:alien",
                "mingw_x64",
                "windows"
            )
        )
        assertEquals(
            "genericui-ext-canvas2d-alien",
            GenericUiNativeSharedLibraryCatalog.classifyKlibGroup(
                "com.nexustools.genericui.extensions.graphics.canvas2d:alien",
                "mingw_x64",
                "windows"
            )
        )
        assertEquals(
            "genericui-ext-canvas3d-alien",
            GenericUiNativeSharedLibraryCatalog.classifyKlibGroup(
                "com.nexustools.genericui.extensions.graphics.canvas3d:alien",
                "mingw_x64",
                "windows"
            )
        )
        assertEquals(
            "genericui-ext-media-alien",
            GenericUiNativeSharedLibraryCatalog.classifyKlibGroup(
                "com.nexustools.genericui.extensions.media.media:alien",
                "mingw_x64",
                "windows"
            )
        )

        val tempDir = Files.createTempDirectory("genericui-alien-klib-plan").toFile()
        try {
            fun fakeKlibDir(folderName: String, escapedUniqueName: String, depends: List<String>): File {
                val dir = File(tempDir, folderName)
                val manifest = File(dir, "default/manifest").apply { parentFile.mkdirs() }
                manifest.writeText(
                    buildString {
                        appendLine("unique_name=$escapedUniqueName")
                        if (depends.isNotEmpty()) {
                            appendLine("depends=${depends.joinToString(" ")}")
                        }
                        appendLine("native_targets=macos_arm64 linux_x64 linux_arm64 mingw_x64")
                    },
                    Charsets.UTF_8
                )
                return dir
            }

            val klibDirs = listOf(
                fakeKlibDir("common", "com.nexustools.genericui\\:common", emptyList()),
                fakeKlibDir("alien", "com.nexustools.genericui.extensions.ui\\:alien", listOf("com.nexustools.genericui:common")),
                fakeKlibDir("svg", "com.nexustools.genericui.extensions.graphics\\:svg", listOf("com.nexustools.genericui:common")),
                fakeKlibDir(
                    "svg-alien",
                    "com.nexustools.genericui.extensions.graphics.svg\\:alien",
                    listOf(
                        "com.nexustools.genericui.extensions.ui:alien",
                        "com.nexustools.genericui.extensions.graphics:svg"
                    )
                )
            )
            val plan = GenericUiNativeSharedLibraryCatalog.planSharedLibraries(klibDirs, "mingw_x64", "windows")
            val groupIds = plan.groupsOrdered.map { it.groupId }
            val alienIdx = groupIds.indexOf("genericui-ext-alien")
            val svgIdx = groupIds.indexOf("genericui-ext-svg")
            val svgAlienIdx = groupIds.indexOf("genericui-ext-svg-alien")
            assertTrue(alienIdx >= 0 && svgIdx >= 0 && svgAlienIdx > alienIdx && svgAlienIdx > svgIdx)
            val svgAlienPlan = plan.groupsOrdered[svgAlienIdx]
            assertTrue("genericui-ext-alien" in svgAlienPlan.upstreamGroupIds)
            assertTrue("genericui-ext-svg" in svgAlienPlan.upstreamGroupIds)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `binary codec task scans BinarySerializable classes and generates registrar wired into launchers`() {
        val tempDir = Files.createTempDirectory("genericui-bincodec-test").toFile()
        try {
            val srcDir = File(tempDir, "src/commonMain/kotlin/com/example/models").apply { mkdirs() }
            File(srcDir, "BinaryModels.kt").writeText(
                """
                package com.example.models

                import com.nexustools.genericui.component.system.BinarySerializable

                enum class SyncMode { FAST, FULL }

                @BinarySerializable
                data class NestedMeta(
                    val tag: String = "default-tag",
                    val priority: Byte = 1,
                    val flags: Short = 2
                )

                @BinarySerializable
                data class SyncPayload(
                    val id: Int,
                    val active: Boolean = true,
                    val timestamp: Long,
                    val ratio: Float = 0.5f,
                    val score: Double = 1.0,
                    val rawBytes: ByteArray? = null,
                    val mode: SyncMode = SyncMode.FAST,
                    val meta: NestedMeta = NestedMeta(),
                    val tags: List<String> = emptyList(),
                    val counts: Map<String, Int> = emptyMap(),
                    val optionalNote: String? = "note"
                )
                """.trimIndent()
            )
            File(srcDir, "MainFrag.kt").writeText(
                """
                package com.example.models
                import com.nexustools.genericui.component.fragment.Fragment
                import com.nexustools.genericui.component.fragment.MainAppFragment

                @MainAppFragment
                class MainFrag : Fragment()
                """.trimIndent()
            )

            val project = ProjectBuilder.builder().withProjectDir(tempDir).withName("codecapp").build()
            project.group = "com.example.models"
            project.pluginManager.apply("com.nexustools.genericui")
            val codecTask = project.tasks.getByName("generateGenericUiBinaryCodecs") as GenericUiGenerateBinaryCodecsTask
            codecTask.generateBinaryCodecs()

            val outDir = codecTask.outputDirectory.get().asFile
            val nestedCodec = File(outDir, "com/example/models/NestedMetaBinaryCodec.kt")
            val payloadCodec = File(outDir, "com/example/models/SyncPayloadBinaryCodec.kt")
            val registrar = File(outDir, "com/example/models/generated/GeneratedBinaryCodecRegistrar.kt")
            assertTrue(nestedCodec.isFile, "Expected NestedMetaBinaryCodec.kt")
            assertTrue(payloadCodec.isFile, "Expected SyncPayloadBinaryCodec.kt")
            assertTrue(registrar.isFile, "Expected GeneratedBinaryCodecRegistrar.kt")

            val payloadSrc = payloadCodec.readText()
            assertTrue(payloadSrc.contains("object SyncPayloadBinaryCodec : DataClassCodec<SyncPayload>"))
            assertTrue(payloadSrc.contains("PreferenceElement.ofBoolean("))
            assertTrue(payloadSrc.contains("PreferenceElement.ofBytes("))
            assertTrue(payloadSrc.contains("PreferenceElement.ofArray("))
            assertTrue(payloadSrc.contains("PreferenceElement.ofMap("))
            assertTrue(payloadSrc.contains("com.example.models.NestedMetaBinaryCodec.encode("))
            assertTrue(payloadSrc.contains("com.example.models.SyncMode.entries"))

            val regSrc = registrar.readText()
            assertTrue(regSrc.contains("object GeneratedBinaryCodecRegistrar"))
            assertTrue(regSrc.contains("fun registerAll()"))
            assertTrue(regSrc.contains("DataClassCodecRegistry.register(com.example.models.NestedMeta::class, com.example.models.NestedMetaBinaryCodec)"))
            assertTrue(regSrc.contains("DataClassCodecRegistry.register(com.example.models.SyncPayload::class, com.example.models.SyncPayloadBinaryCodec)"))

            val ext = project.extensions.getByType(GenericUiExtension::class.java)
            ext.backends {
                windows()
                android()
                web()
            }
            val launcherOut = File(tempDir, "build/launchers")
            GenericUiGenerateLaunchersTask.generateForProject(tempDir, launcherOut, ext, project.group.toString())
            val winDescriptor = File(launcherOut, "windows/kotlin/com/example/models/generated/GeneratedWindowsAppDescriptor.kt").readText()
            assertTrue(winDescriptor.contains("com.example.models.generated.GeneratedBinaryCodecRegistrar.registerAll()"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `webView spec and contributor wire desktop alien companions native executable and swing main without collisions`() {
        val specDefaults = GenericUiWebViewExtensionSpec()
        assertTrue(specDefaults.enableCanvas2D)
        assertTrue(specDefaults.enableWebGL)
        assertTrue(specDefaults.enableMedia)
        assertTrue(specDefaults.enableSvg)
        assertTrue(specDefaults.enableWebAudio)
        assertTrue(specDefaults.enableWebSockets)
        assertTrue(specDefaults.enableStorage)
        assertTrue(specDefaults.enableShadowDom)
        assertTrue(specDefaults.enableIframes)
        assertTrue(specDefaults.enableWorkers)

        val cfg = GenericUiExtensionsConfig()
        cfg.webView {
            enableWebGL = false
            enableMedia = false
            enableWebAudio = false
        }
        assertTrue(cfg.webViewEnabled)
        assertEquals("false", cfg.extensionOptions[GenericUiWebViewExtensionSpec.OPTION_ENABLE_WEBGL])
        assertEquals(listOf(":extensions:ui:webview"), cfg.enabledExtensionModulePaths())
        assertEquals(listOf(":extensions:ui:webview"), cfg.enabledExtensionModulePathsForTarget(GenericUiVariantScope.WEB))
        assertEquals(listOf(":extensions:ui:webview"), cfg.enabledExtensionModulePathsForTarget(GenericUiVariantScope.ANDROID))
        assertEquals(listOf(":extensions:ui:webview"), cfg.enabledExtensionModulePathsForTarget(GenericUiVariantScope.IOS))

        val desktopMods = cfg.enabledExtensionModulePathsForTarget(GenericUiVariantScope.WINDOWS)
        assertTrue(":extensions:ui:webview" in desktopMods)
        assertTrue(":extensions:ui:alien" in desktopMods)
        assertTrue(":extensions:system:ipc" in desktopMods)
        assertTrue(":extensions:graphics:svg" in desktopMods)
        assertTrue(":extensions:graphics:svg:alien" in desktopMods)
        assertTrue(":extensions:graphics:canvas2d" in desktopMods)
        assertTrue(":extensions:graphics:canvas2d:alien" in desktopMods)
        assertFalse(":extensions:graphics:canvas3d:alien" in desktopMods, "Canvas3D alien disabled when enableWebGL=false")
        assertFalse(":extensions:media:media:alien" in desktopMods, "Media alien disabled when enableMedia=false && enableWebAudio=false")

        val tempDir = Files.createTempDirectory("genericui-webview-sw-test").toFile()
        try {
            val srcDir = File(tempDir, "src/commonMain/kotlin/com/example/browserapp").apply { mkdirs() }
            File(srcDir, "BrowserMain.kt").writeText(
                """
                package com.example.browserapp
                import com.nexustools.genericui.component.fragment.Fragment
                import com.nexustools.genericui.component.fragment.MainAppFragment
                import com.nexustools.genericui.ext.serviceworker.MainServiceWorker
                import com.nexustools.genericui.ext.serviceworker.ServiceWorker

                @MainAppFragment
                class BrowserMain : Fragment()

                @MainServiceWorker
                class BrowserBgWorker : ServiceWorker()
                """.trimIndent()
            )

            val project = ProjectBuilder.builder().withProjectDir(tempDir).withName("browser").build()
            project.group = "com.example.browserapp"
            project.pluginManager.apply("com.nexustools.genericui")
            assertNull(project.tasks.findByName("bundleGenericUiWebViewProcess"))

            val ext = project.extensions.getByType(GenericUiExtension::class.java)
            ext.application.baseName = "browser"
            ext.backends {
                windows()
                macos()
                gtk()
                kde()
                swing { enabled = true }
                web()
                android()
                ios()
            }
            ext.extensions {
                serviceWorker()
                webView()
            }

            val launcherOut = File(tempDir, "build/launchers")
            val generated = GenericUiGenerateLaunchersTask.generateForProject(
                projectDir = tempDir,
                outputRootDir = launcherOut,
                extension = ext,
                projectGroup = project.group.toString()
            )
            val swingWorkerFile = generated.firstOrNull { it.name == "GeneratedSwingWebViewWorkerMain.kt" }
            assertNotNull(swingWorkerFile, "Expected Swing GeneratedSwingWebViewWorkerMain.kt")
            val swingWorkerSrc = swingWorkerFile.readText()
            assertTrue(swingWorkerSrc.contains("object GeneratedSwingWebViewWorkerMain"))
            assertTrue(swingWorkerSrc.contains("@JvmStatic"))
            assertTrue(swingWorkerSrc.contains("fun main(args: Array<String>)"))
            assertTrue(swingWorkerSrc.contains("com.example.browserapp.generated.GeneratedBinaryCodecRegistrar.registerAll()"))
            assertTrue(swingWorkerSrc.contains("com.nexustools.genericui.ext.svg.alien.SvgAlienExtension.register()"))
            assertTrue(swingWorkerSrc.contains("com.nexustools.genericui.ext.canvas2d.alien.Canvas2DAlienExtension.register()"))
            assertTrue(swingWorkerSrc.contains("com.nexustools.genericui.ext.canvas3d.alien.Canvas3DAlienExtension.register()"))
            assertTrue(swingWorkerSrc.contains("com.nexustools.genericui.ext.media.alien.MediaAlienExtension.register()"))
            assertTrue(swingWorkerSrc.contains("com.nexustools.genericui.ext.webview.desktop.process.WebViewWorkerMain.main(args)"))

            val winWorkerFile = generated.firstOrNull { it.name == "GeneratedWindowsWebViewWorkerMain.kt" }
            assertNotNull(winWorkerFile)
            val winWorkerSrc = winWorkerFile.readText()
            assertTrue(winWorkerSrc.contains("fun mainWebViewWorker(args: Array<String>)"))
            assertTrue(winWorkerSrc.contains("com.nexustools.genericui.ext.webview.desktop.process.WebViewWorkerMain.main(args)"))

            assertTrue(generated.any { it.name == "GeneratedSwingServiceWorkerMain.kt" })
            assertTrue(generated.any { it.name == "GeneratedWindowsServiceWorkerMain.kt" })
            assertEquals(generated.map { it.relativeTo(launcherOut).invariantSeparatorsPath }.distinct().size, generated.size)

            val swingDescriptor = File(launcherOut, "swing/kotlin/com/example/browserapp/generated/GeneratedSwingAppDescriptor.kt").readText()
            assertTrue(swingDescriptor.contains("\"webview.swing.workerMainClass\", \"com.example.browserapp.generated.GeneratedSwingWebViewWorkerMain\""))
            val winDescriptor = File(launcherOut, "windows/kotlin/com/example/browserapp/generated/GeneratedWindowsAppDescriptor.kt").readText()
            assertTrue(winDescriptor.contains("\"webview.desktop.workerBinaryBaseName\", \"browser-webview\""))
            val webDescriptor = File(launcherOut, "web/kotlin/com/example/browserapp/generated/GeneratedWebAppDescriptor.kt").readText()
            assertFalse(webDescriptor.contains("SystemAlienBootstrap"))
            assertFalse(webDescriptor.contains("SvgAlienExtension"))
            assertFalse(webDescriptor.contains("IpcExtension"))

            val desktopCtx = ExtensionBuildContext(
                projectRootDir = tempDir,
                projectName = "browser",
                appId = "com.example.browserapp",
                appName = "Browser",
                appVersion = "1.0.0",
                baseName = "browser",
                generatedPackageName = "com.example.browserapp.generated",
                enabledExtensionModulePaths = ext.extensions.enabledExtensionModulePathsForTarget(GenericUiVariantScope.WINDOWS).toSet(),
                extensionOptions = GenericUiGradlePlugin.buildPluginExtensionOptionsStatic(ext)
            )
            val contrib = WebViewExtensionContributor.contributeDesktop(desktopCtx)
            assertNotNull(contrib)
            val secExe = contrib.secondaryExecutables.single()
            assertEquals("browser-webview", secExe.binaryBaseName)
            assertEquals("com.example.browserapp.generated.mainWebViewWorker", secExe.entryPointFqn)
            assertEquals("WebView", secExe.macosHelperBundleSuffix)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `native shared library catalog plans libnjs so dylib and njs dll for njs klibs`() {
        val tempDir = Files.createTempDirectory("genericui-njs-shared-plan").toFile()
        try {
            fun fakeKlibDir(folderName: String, escapedUniqueName: String, depends: List<String>): File {
                val dir = File(tempDir, folderName)
                val manifest = File(dir, "default/manifest").apply { parentFile.mkdirs() }
                manifest.writeText(
                    buildString {
                        appendLine("unique_name=$escapedUniqueName")
                        if (depends.isNotEmpty()) {
                            appendLine("depends=${depends.joinToString(" ")}")
                        }
                        appendLine("native_targets=macos_arm64 macos_x64 linux_x64 linux_arm64 mingw_x64")
                    },
                    Charsets.UTF_8
                )
                return dir
            }

            val klibDirs = listOf(
                fakeKlibDir("njs-common", "com.nexustools.njs\\:common", emptyList()),
                fakeKlibDir("njs-native", "com.nexustools.njs\\:native", listOf("com.nexustools.njs:common"))
            )

            val expectedByTarget = mapOf(
                "linux_x64" to "libnjs.so",
                "macos_arm64" to "libnjs.dylib",
                "mingw_x64" to "njs.dll"
            )
            for ((target, expectedLib) in expectedByTarget) {
                val plan = GenericUiNativeSharedLibraryCatalog.planSharedLibraries(
                    libraryFiles = klibDirs,
                    konanTargetName = target,
                    backendVariant = "windows"
                )
                val njsGroup = plan.groupsOrdered.firstOrNull { it.groupId == GenericUiNativeSharedLibraryCatalog.NJS_GROUP_ID }
                assertNotNull(njsGroup, "Expected NJS shared library group for $target")
                assertEquals(expectedLib, njsGroup.sharedLibraryFileName)
                assertEquals(
                    setOf("com.nexustools.njs:common", "com.nexustools.njs:native"),
                    njsGroup.klibs.map { it.uniqueName }.toSet()
                )
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `swing fat jar verifier enforces njs runtime and webview worker classes when required`() {
        val tempDir = Files.createTempDirectory("genericui-swing-njs-verify").toFile()
        try {
            val project = ProjectBuilder.builder().withProjectDir(tempDir).withName("swingVerify").build()
            val task = project.tasks.register(
                "verifySwingHelper",
                GenericUiPackageSwingJarTask::class.java
            ).get()

            fun writeSyntheticJar(jarFile: File, classNames: List<String>) {
                val manifest = Manifest().apply {
                    mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
                    mainAttributes[Attributes.Name.MAIN_CLASS] = "com.example.AppMain"
                }
                JarOutputStream(FileOutputStream(jarFile), manifest).use { jos ->
                    for (fqcn in classNames) {
                        jos.putNextEntry(JarEntry("${fqcn.replace('.', '/')}.class"))
                        jos.write(byteArrayOf(0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte()))
                        jos.closeEntry()
                    }
                }
            }

            val baseRuntime = listOf(
                "com.example.AppMain",
                "com.nexustools.genericui.swing.SwingLauncher",
                "com.nexustools.genericui.swing.SwingNativeFactory",
                "com.nexustools.genericui.swing.SwingApplicationImpl",
                "com.nexustools.genericui.swing.SwingWindowImpl",
                "com.nexustools.genericui.core.GenericUI",
                "com.nexustools.genericui.component.fragment.Fragment",
                "kotlin.Unit"
            )
            val missingNjsJar = File(tempDir, "missing-njs.jar")
            writeSyntheticJar(missingNjsJar, baseRuntime)
            val missingErr = assertFailsWith<GradleException> {
                task.verifySwingFatJar(
                    jarFile = missingNjsJar,
                    expectedMainClass = "com.example.AppMain",
                    requireGenericUiRuntimeClasses = true,
                    requireNjsRuntimeClasses = true,
                    requireWebViewWorkerClasses = true
                )
            }
            assertTrue(missingErr.message.orEmpty().contains("com.nexustools.njs.runtime.Global"))
            assertTrue(missingErr.message.orEmpty().contains("com.nexustools.njs.compiler.JavaTranspiler"))
            assertTrue(missingErr.message.orEmpty().contains("WebViewWorkerMain"))

            val completeJar = File(tempDir, "complete-swing-webview.jar")
            writeSyntheticJar(
                completeJar,
                baseRuntime + listOf(
                    "com.nexustools.njs.runtime.Global",
                    "com.nexustools.njs.compiler.JavaTranspiler",
                    "com.nexustools.genericui.ext.webview.desktop.process.WebViewWorkerMain"
                )
            )
            task.verifySwingFatJar(
                jarFile = completeJar,
                expectedMainClass = "com.example.AppMain",
                requireGenericUiRuntimeClasses = true,
                requireNjsRuntimeClasses = true,
                requireWebViewWorkerClasses = true
            )
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `webview transitively enables desktop schema media and gpu wiring without explicit schema or media`() {
        val desktopBackends = GenericUiBackendsConfig().apply { allDesktop() }
        val androidOnlyBackends = GenericUiBackendsConfig().apply { android(true) }

        val webViewDefault = GenericUiExtensionsConfig().apply { webView() }
        assertFalse(webViewDefault.isCommonSchemaEnabled())
        assertTrue(webViewDefault.isDesktopSchemaEnabled(desktopBackends))
        assertFalse(webViewDefault.isDesktopSchemaEnabled(androidOnlyBackends))
        assertTrue(webViewDefault.isAnySchemaEnabled(desktopBackends))
        assertFalse(webViewDefault.isAnySchemaEnabled(androidOnlyBackends))

        val webViewMinimal = GenericUiExtensionsConfig().apply {
            webView {
                enableMedia = false
                enableWebAudio = false
                enableWebGL = false
            }
        }
        assertFalse(webViewMinimal.isDesktopSchemaEnabled(desktopBackends))
        assertFalse(webViewMinimal.isAnySchemaEnabled(desktopBackends))

        val extDefaultWebView = GenericUiExtension().apply {
            backends.all()
            extensions.webView()
        }
        assertTrue(GenericUiMultiplatformConfigurator.isMediaExtensionEnabled(extDefaultWebView))
        assertTrue(GenericUiMultiplatformConfigurator.isGpuExtensionEnabled(extDefaultWebView))
        assertEquals(
            "true",
            GenericUiGradlePlugin.buildPluginExtensionOptionsStatic(extDefaultWebView)[
                ServiceWorkerExtensionContributor.OPTION_SCHEMA_ENABLED
            ]
        )

        val extAndroidOnlyWebView = GenericUiExtension().apply {
            backends.android(true)
            extensions.webView()
        }
        assertEquals(
            "false",
            GenericUiGradlePlugin.buildPluginExtensionOptionsStatic(extAndroidOnlyWebView)[
                ServiceWorkerExtensionContributor.OPTION_SCHEMA_ENABLED
            ]
        )

        val extNoMediaOrGlWebView = GenericUiExtension().apply {
            backends.allDesktop()
            extensions.webView {
                enableMedia = false
                enableWebAudio = false
                enableWebGL = false
            }
        }
        assertFalse(GenericUiMultiplatformConfigurator.isMediaExtensionEnabled(extNoMediaOrGlWebView))
        // Desktop WebView always enables Alien -> Canvas2D -> ImageProcessing -> GPU even when WebGL is disabled.
        assertTrue(GenericUiMultiplatformConfigurator.isGpuExtensionEnabled(extNoMediaOrGlWebView))

        val extSchemaOnlyDesktop = GenericUiExtension().apply {
            backends.allDesktop()
            extensions.schema(true)
        }
        assertFalse(GenericUiMultiplatformConfigurator.isMediaExtensionEnabled(extSchemaOnlyDesktop))
        assertFalse(GenericUiMultiplatformConfigurator.isGpuExtensionEnabled(extSchemaOnlyDesktop))
    }
}
