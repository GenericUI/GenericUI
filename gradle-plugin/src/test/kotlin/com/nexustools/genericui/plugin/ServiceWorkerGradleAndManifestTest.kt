package com.nexustools.genericui.plugin

import com.nexustools.genericui.plugin.spi.ExtensionBuildContext
import com.nexustools.genericui.plugin.spi.GenericUiExtensionContributorRegistry
import com.nexustools.genericui.plugin.spi.ServiceWorkerExtensionContributor
import com.nexustools.genericui.plugin.spi.ServiceWorkerMessageCodegen
import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ServiceWorkerGradleAndManifestTest {

    private fun writePngFile(file: File, argbColor: Int) {
        file.parentFile?.mkdirs()
        val img = BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until 4) {
            for (x in 0 until 4) {
                img.setRGB(x, y, argbColor)
            }
        }
        ImageIO.write(img, "PNG", file)
    }

    private fun createBasicAppExtension(enableServiceWorker: Boolean = true): GenericUiExtension {
        val ext = GenericUiExtension().apply {
            app.id = "com.example.swapp"
            app.name = "SwApp"
            app.version = "1.2.3"
            app.packageName = "com.example.swapp"
            application.baseName = "swapp"
            backends.windowsEnabled = true
            backends.macosEnabled = true
            backends.swingConfig.enabled = true
            backends.androidEnabled = true
            backends.iosEnabled = true
            backends.webEnabled = true
            if (enableServiceWorker) {
                extensions.serviceWorker(true)
            }
        }
        return ext
    }

    @Test
    fun extensionsDslCatalogAndLibraryMappingRegisterServiceWorkerCleanly() {
        assertEquals(52, GenericUiGradlePlugin.KNOWN_GENERICUI_MODULES.size)
        assertTrue(":extensions:system:serviceworker" in GenericUiGradlePlugin.KNOWN_GENERICUI_MODULES)
        assertFalse(":extensions:system:serviceworker:dsl" in GenericUiGradlePlugin.KNOWN_GENERICUI_MODULES)
        assertTrue(":extensions:system:ipc" in GenericUiGradlePlugin.KNOWN_GENERICUI_MODULES)
        assertNull(ServiceWorkerExtensionContributor.dslModulePath)
        assertEquals(
            "genericui-ext-serviceworker",
            GenericUiNativeSharedLibraryCatalog.classifyKlibGroup(
                uniqueName = "com.nexustools.genericui.extensions.system:serviceworker",
                konanTargetName = "mingw_x64",
                backendVariant = "windows"
            )
        )
        assertEquals(
            "genericui-ext-ipc",
            GenericUiNativeSharedLibraryCatalog.classifyKlibGroup(
                uniqueName = "com.nexustools.genericui.extensions.system:ipc",
                konanTargetName = "mingw_x64",
                backendVariant = "windows"
            )
        )

        val config = GenericUiExtensionsConfig()
        config.serviceWorker {
            autostart = true
            trayIcon = "src/commonMain/resources/tray.png"
            openWindowOnLaunch = false
            androidForegroundServiceType = "mediaPlayback|dataSync"
            androidSpecialUseFgsSubtype = "Custom media sync"
        }
        assertTrue(config.serviceWorkerEnabled)
        assertTrue(config.serviceWorkerConfig.autostart)
        assertEquals("src/commonMain/resources/tray.png", config.serviceWorkerConfig.trayIcon)
        assertFalse(config.serviceWorkerConfig.openWindowOnLaunch)
        assertEquals("mediaPlayback|dataSync", config.serviceWorkerConfig.androidForegroundServiceType)
        assertEquals("true", config.extensionOptions["serviceWorker.autostart"])
        assertEquals("src/commonMain/resources/tray.png", config.extensionOptions["serviceWorker.trayIcon"])
        assertEquals("false", config.extensionOptions["serviceWorker.openWindowOnLaunch"])
        assertEquals("mediaPlayback|dataSync", config.extensionOptions["serviceWorker.androidForegroundServiceType"])
        assertTrue(":extensions:system:serviceworker" in config.enabledExtensionModulePaths())

        val allConfig = GenericUiExtensionsConfig().apply { enableAll() }
        assertTrue(allConfig.serviceWorkerEnabled)
        assertTrue(":extensions:system:serviceworker" in allConfig.enabledExtensionModulePaths())
    }

    @Test
    fun zeroDefaultFootprintWhenNoMainServiceWorkerExists() {
        val tempDir = Files.createTempDirectory("genericui-sw-zero").toFile()
        try {
            val commonDir = File(tempDir, "src/commonMain/kotlin/com/example/zero").apply { mkdirs() }
            File(commonDir, "MainFrag.kt").writeText(
                """
                package com.example.zero
                import com.nexustools.genericui.component.fragment.Fragment
                import com.nexustools.genericui.component.fragment.MainAppFragment

                @MainAppFragment
                class MainFrag : Fragment()
                """.trimIndent()
            )
            val ext = createBasicAppExtension(enableServiceWorker = true)
            val extContext = ExtensionBuildContext(
                projectRootDir = tempDir,
                projectName = "zeroapp",
                appId = ext.app.id,
                appName = ext.app.name,
                appVersion = ext.app.version,
                baseName = ext.application.baseName,
                generatedPackageName = "com.example.zero.generated",
                enabledExtensionModulePaths = ext.extensions.enabledExtensionModulePaths().toSet(),
                discoveredFragments = GenericUiFragmentScanner.scanProject(tempDir),
                discoveredSymbols = emptyList(),
                extensionOptions = GenericUiGradlePlugin.buildPluginExtensionOptionsStatic(ext)
            )

            assertNull(ServiceWorkerExtensionContributor.contributeAndroid(extContext, null))
            assertNull(ServiceWorkerExtensionContributor.contributeDesktop(extContext))
            assertNull(ServiceWorkerExtensionContributor.contributeWebServiceWorker(extContext))
            assertNull(ServiceWorkerExtensionContributor.contributeAppleMobile(extContext))

            val manifestOut = GenericUiAndroidManifestXmlGenerator.generate(
                appId = ext.app.id,
                appName = ext.app.name,
                appVersion = ext.app.version,
                model = GenericUiAndroidManifestModel(),
                fragments = extContext.discoveredFragments,
                enabledExtensionModulePaths = ext.extensions.enabledExtensionModulePaths(),
                projectRootDir = tempDir,
                discoveredSymbols = emptyList(),
                extensionOptions = extContext.extensionOptions
            )
            assertFalse(manifestOut.manifestXml.contains("<service"))
            assertFalse(manifestOut.manifestXml.contains("<receiver"))
            assertFalse(manifestOut.manifestXml.contains("FOREGROUND_SERVICE"))
            assertFalse(manifestOut.manifestXml.contains("POST_NOTIFICATIONS"))
            assertFalse(manifestOut.manifestXml.contains("RECEIVE_BOOT_COMPLETED"))

            val launcherOut = File(tempDir, "build/launchers")
            val generatedLaunchers = GenericUiGenerateLaunchersTask.generateForProject(
                projectDir = tempDir,
                outputRootDir = launcherOut,
                extension = ext
            )
            assertFalse(generatedLaunchers.any { it.name.contains("ServiceWorkerMain") })
            val winMain = File(launcherOut, "windows/kotlin/com/example/swapp/generated/GeneratedWindowsLauncherMain.kt")
                .readText(Charsets.UTF_8)
            assertFalse(winMain.contains("prepareDesktopMainLaunch"))
            val webMain = File(launcherOut, "web/kotlin/com/example/swapp/generated/GeneratedWebLauncherMain.kt")
                .readText(Charsets.UTF_8)
            assertFalse(webMain.contains("isWorkerGlobalScope"))

            // Web distribution with manifestEnabled=true but no @MainServiceWorker must emit ZERO .sw.js/.sw.wasm
            val webDistOut = File(tempDir, "build/dist/web")
            val buildJsDir = File(tempDir, "build/dist/js/productionExecutable").apply { mkdirs() }
            File(buildJsDir, "swapp.js").writeText("console.log('main app');", Charsets.UTF_8)
            val buildWasmDir = File(tempDir, "build/dist/wasmJs/productionExecutable").apply { mkdirs() }
            File(buildWasmDir, "swapp.wasm").writeBytes(byteArrayOf(0x00, 0x61, 0x73, 0x6d))

            val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
            val webTask = project.tasks.register("packageWebTest", GenericUiWebDistributionTask::class.java).get()
            webTask.projectRootPath.set(tempDir.absolutePath)
            webTask.projectBuildPath.set(File(tempDir, "build").absolutePath)
            webTask.baseName.set("swapp")
            webTask.appName.set("SwApp")
            webTask.manifestEnabled.set(true)
            webTask.allowInstallAsWebApp.set(false)
            webTask.serviceWorkerEnabled.set(false)
            webTask.serviceWorkerBinaryBaseNames.set(emptyList())
            webTask.outputDirectory.set(webDistOut)
            webTask.packageWebDistribution()

            assertTrue(File(webDistOut, "serviceworker.js").isFile)
            assertFalse(File(webDistOut, "swapp.sw.js").exists())
            assertFalse(File(webDistOut, "swapp.sw.wasm").exists())
            assertTrue(File(webDistOut, "serviceworker.js").readText().contains("const SW_WASM_BASES = [];"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun compileTimeValidationRejectsMultipleWorkersBadBaseClassOrMissingNoArgConstructor() {
        val scanSpecs = GenericUiExtensionContributorRegistry.allAnnotationScanSpecs(
            setOf(":extensions:system:serviceworker")
        )

        fun scanAndValidate(files: Map<String, String>, enabledBackends: String = "windows,android") {
            val dir = Files.createTempDirectory("genericui-sw-val").toFile()
            try {
                for ((rel, content) in files) {
                    val f = File(dir, rel).apply { parentFile?.mkdirs() }
                    f.writeText(content, Charsets.UTF_8)
                }
                val symbols = GenericUiFragmentScanner.scanProjectSymbols(dir, scanSpecs)
                val ctx = ExtensionBuildContext(
                    projectRootDir = dir,
                    projectName = "val",
                    appId = "com.example.val",
                    appName = "ValApp",
                    appVersion = "1.0.0",
                    baseName = "val",
                    generatedPackageName = "com.example.val.generated",
                    enabledExtensionModulePaths = setOf(":extensions:system:serviceworker"),
                    discoveredSymbols = symbols,
                    extensionOptions = mapOf(ServiceWorkerExtensionContributor.OPTION_ENABLED_BACKENDS to enabledBackends)
                )
                GenericUiExtensionContributorRegistry.validateActiveContributors(ctx, symbols)
            } finally {
                dir.deleteRecursively()
            }
        }

        // 1. Two common @MainServiceWorker classes fail with GradleException
        assertFailsWith<GradleException> {
            scanAndValidate(
                mapOf(
                    "src/commonMain/kotlin/a/WorkerA.kt" to """
                        package a
                        import com.nexustools.genericui.ext.serviceworker.MainServiceWorker
                        import com.nexustools.genericui.ext.serviceworker.ServiceWorker
                        @MainServiceWorker class WorkerA : ServiceWorker()
                    """.trimIndent(),
                    "src/commonMain/kotlin/a/WorkerB.kt" to """
                        package a
                        import com.nexustools.genericui.ext.serviceworker.MainServiceWorker
                        import com.nexustools.genericui.ext.serviceworker.ServiceWorker
                        @MainServiceWorker class WorkerB : ServiceWorker()
                    """.trimIndent()
                )
            )
        }

        // 2. Does not extend ServiceWorker -> GradleException
        assertFailsWith<GradleException> {
            scanAndValidate(
                mapOf(
                    "src/commonMain/kotlin/a/BadBaseWorker.kt" to """
                        package a
                        import com.nexustools.genericui.ext.serviceworker.MainServiceWorker
                        @MainServiceWorker class BadBaseWorker
                    """.trimIndent()
                )
            )
        }

        // 3. Missing zero-arg / all-default constructor -> GradleException
        assertFailsWith<GradleException> {
            scanAndValidate(
                mapOf(
                    "src/commonMain/kotlin/a/ArgWorker.kt" to """
                        package a
                        import com.nexustools.genericui.ext.serviceworker.MainServiceWorker
                        import com.nexustools.genericui.ext.serviceworker.ServiceWorker
                        @MainServiceWorker class ArgWorker(val required: String) : ServiceWorker()
                    """.trimIndent()
                )
            )
        }

        // 4. Target-scoped workers for disjoint targets pass validation and resolve distinctly!
        scanAndValidate(
            mapOf(
                "src/commonMain/kotlin/a/AndroidOnlyWorker.kt" to """
                    package a
                    import com.nexustools.genericui.enums.system.AppTarget
                    import com.nexustools.genericui.ext.serviceworker.MainServiceWorker
                    import com.nexustools.genericui.ext.serviceworker.ServiceWorker
                    @MainServiceWorker(targets = [AppTarget.ANDROID])
                    class AndroidOnlyWorker(val tag: String = "ok") : ServiceWorker()
                """.trimIndent(),
                "src/commonMain/kotlin/a/WindowsOnlyWorker.kt" to """
                    package a
                    import com.nexustools.genericui.enums.system.AppTarget
                    import com.nexustools.genericui.ext.serviceworker.MainServiceWorker
                    import com.nexustools.genericui.ext.serviceworker.ServiceWorker
                    @MainServiceWorker(targets = [AppTarget.WINDOWS])
                    class WindowsOnlyWorker : ServiceWorker()
                """.trimIndent()
            )
        )
    }

    @Test
    fun messageCodegenEmitsOnePublicTypePerFileWithNestedEnumsListsMapsAndNullables() {
        val tempDir = Files.createTempDirectory("genericui-sw-codegen").toFile()
        try {
            val srcDir = File(tempDir, "src/commonMain/kotlin/com/example/msgs").apply { mkdirs() }
            File(srcDir, "Priority.kt").writeText(
                """
                package com.example.msgs
                enum class Priority { LOW, HIGH }
                """.trimIndent()
            )
            File(srcDir, "Attachment.kt").writeText(
                """
                package com.example.msgs
                import com.nexustools.genericui.ext.serviceworker.ServiceWorkerMessage
                @ServiceWorkerMessage
                data class Attachment(val path: String, val sizeBytes: Long)
                """.trimIndent()
            )
            File(srcDir, "UploadTaskMsg.kt").writeText(
                """
                package com.example.msgs
                import com.nexustools.genericui.ext.serviceworker.ServiceWorkerMessage
                @ServiceWorkerMessage
                data class UploadTaskMsg(
                    val id: Int,
                    val title: String,
                    val note: String? = null,
                    val progress: Float = 0f,
                    val duration: Double = 1.5,
                    val urgent: Boolean = false,
                    val priority: Priority = Priority.LOW,
                    val tags: List<String> = emptyList(),
                    val headers: Map<String, Int> = emptyMap(),
                    val attachment: Attachment? = null
                )
                """.trimIndent()
            )

            val outDir = File(tempDir, "build/generated/serviceworker")
            val generated = ServiceWorkerMessageCodegen.generateCodecSources(
                sourceDirectories = listOf(File(tempDir, "src/commonMain/kotlin")),
                outputDirectory = outDir,
                registrarPackageName = "com.example.generated"
            )
            assertEquals(3, generated.size)
            val attachmentCodec = generated.first { it.name == "AttachmentServiceWorkerCodec.kt" }
            val uploadCodec = generated.first { it.name == "UploadTaskMsgServiceWorkerCodec.kt" }
            val registrar = generated.first { it.name == "GeneratedServiceWorkerCodecRegistrar.kt" }

            // Strict 1 public top-level declaration per generated file
            val topLevelDecls = Regex("""(?m)^(?:public\s+)?(?:object|class|interface|enum\s+class)\s+""")
            for (f in generated) {
                val txt = f.readText(Charsets.UTF_8)
                assertEquals(1, topLevelDecls.findAll(txt).count(), "Expected 1 public type in ${f.name}")
                assertFalse(txt.contains("::class.java"))
            }

            val uploadSrc = uploadCodec.readText(Charsets.UTF_8)
            assertTrue(uploadSrc.contains("AttachmentServiceWorkerCodec.encode"))
            assertTrue(uploadSrc.contains("com.example.msgs.Priority.entries.firstOrNull"))
            assertTrue(uploadSrc.contains("it.isArray"))
            assertTrue(uploadSrc.contains("it.isMap || it.isObject"))

            val registrarSrc = registrar.readText(Charsets.UTF_8)
            assertTrue(registrarSrc.contains("ServiceWorkerCodecRegistry.register(com.example.msgs.AttachmentServiceWorkerCodec)"))
            assertTrue(registrarSrc.contains("ServiceWorkerCodecRegistry.register(com.example.msgs.UploadTaskMsgServiceWorkerCodec)"))
            assertTrue(attachmentCodec.exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun androidDesktopWebAndIosGenerationRespectAllFlagsAndZeroIosSimulation() {
        val tempDir = Files.createTempDirectory("genericui-sw-full").toFile()
        try {
            val commonDir = File(tempDir, "src/commonMain/kotlin/com/example/swapp").apply { mkdirs() }
            File(commonDir, "AppFrag.kt").writeText(
                """
                package com.example.swapp
                import com.nexustools.genericui.component.fragment.Fragment
                import com.nexustools.genericui.component.fragment.MainAppFragment
                @MainAppFragment
                class AppFrag : Fragment()
                """.trimIndent()
            )
            File(commonDir, "BackgroundWorker.kt").writeText(
                """
                package com.example.swapp
                import com.nexustools.genericui.ext.serviceworker.MainServiceWorker
                import com.nexustools.genericui.ext.serviceworker.ServiceWorker
                @MainServiceWorker
                class BackgroundWorker : ServiceWorker()
                """.trimIndent()
            )

            // Distinct PNGs for app icon vs trayIcon
            val appIconFile = File(tempDir, "src/commonMain/resources/app_icon.png")
            val trayIconFile = File(tempDir, "src/commonMain/resources/custom_tray.png")
            writePngFile(appIconFile, 0xFF112233.toInt())
            writePngFile(trayIconFile, 0xFF44CC55.toInt())

            val ext = createBasicAppExtension(enableServiceWorker = true).apply {
                application.desktop.icon = "src/commonMain/resources/app_icon.png"
                extensions.serviceWorker {
                    autostart = false
                    trayIcon = "src/commonMain/resources/custom_tray.png"
                    openWindowOnLaunch = false
                    androidForegroundServiceType = "mediaPlayback|dataSync"
                }
            }
            val scanSpecs = GenericUiExtensionContributorRegistry.allAnnotationScanSpecs(
                ext.extensions.enabledExtensionModulePaths().toSet(),
                tempDir
            )
            val discoveredFrags = GenericUiFragmentScanner.scanProject(tempDir)
            val discoveredSyms = GenericUiFragmentScanner.scanProjectSymbols(tempDir, scanSpecs)
            val extOptions = GenericUiGradlePlugin.buildPluginExtensionOptionsStatic(ext)
            val extCtx = ExtensionBuildContext(
                projectRootDir = tempDir,
                projectName = "swapp",
                appId = ext.app.id,
                appName = ext.app.name,
                appVersion = ext.app.version,
                baseName = ext.application.baseName,
                generatedPackageName = "com.example.swapp.generated",
                enabledExtensionModulePaths = ext.extensions.enabledExtensionModulePaths().toSet(),
                discoveredFragments = discoveredFrags,
                discoveredSymbols = discoveredSyms,
                extensionOptions = extOptions
            )

            // 1. Android with autostart=false: mediaPlayback|dataSync permissions & service, NO BOOT_COMPLETED
            val androidOff = GenericUiAndroidManifestXmlGenerator.generate(
                appId = ext.app.id,
                appName = ext.app.name,
                appVersion = ext.app.version,
                model = GenericUiAndroidManifestModel(),
                fragments = discoveredFrags,
                enabledExtensionModulePaths = ext.extensions.enabledExtensionModulePaths(),
                projectRootDir = tempDir,
                discoveredSymbols = discoveredSyms,
                extensionOptions = extOptions
            )
            assertTrue(androidOff.manifestXml.contains("android.permission.FOREGROUND_SERVICE"))
            assertTrue(androidOff.manifestXml.contains("android.permission.POST_NOTIFICATIONS"))
            assertTrue(androidOff.manifestXml.contains("android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK"))
            assertTrue(androidOff.manifestXml.contains("android.permission.FOREGROUND_SERVICE_DATA_SYNC"))
            assertTrue(androidOff.manifestXml.contains("android:name=\".GeneratedAndroidServiceWorkerService\""))
            assertTrue(androidOff.manifestXml.contains("android:foregroundServiceType=\"mediaPlayback|dataSync\""))
            assertFalse(androidOff.manifestXml.contains("RECEIVE_BOOT_COMPLETED"))
            assertFalse(androidOff.manifestXml.contains("GeneratedAndroidServiceWorkerBootReceiver"))

            // 2. Android with autostart=true + specialUse: emits BOOT_COMPLETED, BootReceiver, and PROPERTY_SPECIAL_USE_FGS_SUBTYPE
            ext.extensions.serviceWorker {
                autostart = true
                trayIcon = "src/commonMain/resources/custom_tray.png"
                openWindowOnLaunch = false
                androidForegroundServiceType = "specialUse"
            }
            val autostartOpts = GenericUiGradlePlugin.buildPluginExtensionOptionsStatic(ext)
            val androidOn = GenericUiAndroidManifestXmlGenerator.generate(
                appId = ext.app.id,
                appName = ext.app.name,
                appVersion = ext.app.version,
                model = GenericUiAndroidManifestModel(),
                fragments = discoveredFrags,
                enabledExtensionModulePaths = ext.extensions.enabledExtensionModulePaths(),
                projectRootDir = tempDir,
                discoveredSymbols = discoveredSyms,
                extensionOptions = autostartOpts
            )
            assertTrue(androidOn.manifestXml.contains("android.permission.RECEIVE_BOOT_COMPLETED"))
            assertTrue(androidOn.manifestXml.contains("android.permission.FOREGROUND_SERVICE_SPECIAL_USE"))
            assertTrue(androidOn.manifestXml.contains("android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"))
            assertTrue(androidOn.manifestXml.contains("android:name=\".GeneratedAndroidServiceWorkerBootReceiver\""))

            val androidContribOn = ServiceWorkerExtensionContributor.contributeAndroid(
                extCtx.copy(extensionOptions = autostartOpts),
                null
            )
            assertNotNull(androidContribOn)
            assertTrue(androidContribOn.generatedSources.any { it.relativeFilePath.endsWith("GeneratedAndroidServiceWorkerService.kt") })
            assertTrue(androidContribOn.generatedSources.any { it.relativeFilePath.endsWith("GeneratedAndroidServiceWorkerBootReceiver.kt") })

            val androidActOut = File(tempDir, "build/android-activities-check")
            val androidConfig = GenericUiResolvedVariantConfig(
                targetScope = GenericUiVariantScope.ANDROID,
                appId = ext.app.id,
                appName = ext.app.name,
                appVersion = ext.app.version,
                packageName = ext.app.packageName
            )
            GenericUiGenerateAndroidActivitiesTask.generateAndroidActivities(
                outputDir = androidActOut,
                discoveredFragments = discoveredFrags,
                resolvedConfig = androidConfig,
                enabledExtensionModulePaths = ext.extensions.enabledExtensionModulePaths(),
                projectRootDir = tempDir,
                extensionOptions = autostartOpts
            )
            val swServiceFile = File(androidActOut, "com/example/swapp/GeneratedAndroidServiceWorkerService.kt")
            assertTrue(swServiceFile.isFile)
            val swServiceSrc = swServiceFile.readText(Charsets.UTF_8)
            assertTrue(swServiceSrc.contains("com.example.swapp.generated.GeneratedServiceWorkerCodecRegistrar.registerAll()"))
            assertFalse(swServiceSrc.contains("com.example.swapp.GeneratedServiceWorkerCodecRegistrar.registerAll()"))

            val wiredProject = ProjectBuilder.builder().withProjectDir(tempDir).build()
            wiredProject.plugins.apply("com.nexustools.genericui")
            val wiredExt = wiredProject.extensions.getByType(GenericUiExtension::class.java)
            wiredExt.app.id = "com.example.swapp"
            wiredExt.app.packageName = "com.example.swapp"
            wiredExt.backends.android(true)
            wiredExt.extensions.serviceWorker(true)
            val wiredActTask = wiredProject.tasks.getByName("generateGenericUiAndroidActivities") as GenericUiGenerateAndroidActivitiesTask
            val wiredManifestTask = wiredProject.tasks.getByName("generateGenericUiAndroidManifest") as GenericUiGenerateAndroidManifestTask
            assertTrue(wiredActTask.sourceFiles.files.any { it.name == "BackgroundWorker.kt" })
            assertTrue(wiredManifestTask.sourceFiles.files.any { it.name == "AppFrag.kt" })
            assertEquals("com.example.swapp.generated", wiredActTask.generatedRegistrarPackageName.get())

            // 3. Desktop secondary binary + autostart + trayIcon unwrapped precedence + openWindowOnLaunch=false check
            val desktopContrib = ServiceWorkerExtensionContributor.contributeDesktop(
                extCtx.copy(extensionOptions = autostartOpts)
            )
            assertNotNull(desktopContrib)
            assertEquals(listOf("swapp-sw"), desktopContrib.secondaryExecutables.map { it.binaryBaseName })
            assertEquals("windows", desktopContrib.secondaryExecutables.single().windowsSubsystem)
            assertEquals("Service Worker", desktopContrib.secondaryExecutables.single().macosHelperBundleSuffix)
            assertEquals("serviceworker", desktopContrib.secondaryExecutables.single().macosHelperBundleIdSuffix)
            assertTrue(desktopContrib.secondaryExecutables.single().macosHelperLsUiElement)
            assertEquals(1, desktopContrib.autostartEntries.size)
            assertEquals("swapp-sw", desktopContrib.autostartEntries.single().executableName)
            assertEquals("Service Worker", desktopContrib.autostartEntries.single().macosHelperBundleSuffix)
            assertTrue(desktopContrib.generatedSources.any { it.relativeFilePath.endsWith("GeneratedSwingServiceWorkerMain.kt") })

            val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
            val desktopOut = File(tempDir, "build/desktop-res")
            val desktopTask = project.tasks.register("desktopResTest", GenericUiDesktopResourceTask::class.java).get()
            desktopTask.projectRootPath.set(tempDir.absolutePath)
            desktopTask.baseName.set("swapp")
            desktopTask.appName.set("SwApp")
            desktopTask.appId.set("com.example.swapp")
            desktopTask.appVersion.set("1.2.3")
            desktopTask.vendor.set("Example")
            desktopTask.fallbackIconPath.set("src/commonMain/resources/app_icon.png")
            desktopTask.embedWindowsResources.set(false)
            desktopTask.macMinimumSystemVersion.set("12.0")
            desktopTask.macCategory.set("public.app-category.utilities")
            desktopTask.macHighResolutionCapable.set(true)
            desktopTask.linuxCategories.set("Utility;")
            desktopTask.linuxTerminal.set(false)
            desktopTask.customResourceEntries.set(emptyMap())
            desktopTask.enabledExtensionModulePaths.set(ext.extensions.enabledExtensionModulePaths())
            desktopTask.extensionOptions.set(autostartOpts)
            desktopTask.outputDirectory.set(desktopOut)
            desktopTask.generateDesktopResources()

            val stagedMacTrayIcon = File(desktopOut, "macos/tray_icon.png")
            assertTrue(stagedMacTrayIcon.isFile)
            val stagedTrayImg = ImageIO.read(stagedMacTrayIcon)
            assertEquals(0xFF44CC55.toInt(), stagedTrayImg.getRGB(0, 0), "Tray icon must use custom_tray.png unwrapped")
            assertEquals(0xFF44CC55.toInt(), stagedTrayImg.getRGB(stagedTrayImg.width / 2, stagedTrayImg.height / 2))

            // Launchers check: Desktop openWindowOnLaunch=false hook, Web worker branch, iOS zero-simulation
            val launchersDir = File(tempDir, "build/launchers-on")
            GenericUiGenerateLaunchersTask.generateForProject(tempDir, launchersDir, ext)
            val winMain = File(launchersDir, "windows/kotlin/com/example/swapp/generated/GeneratedWindowsLauncherMain.kt")
                .readText(Charsets.UTF_8)
            assertTrue(winMain.contains("ServiceWorkerExtension.configure"))
            assertTrue(winMain.contains("openWindowOnLaunch = false"))
            assertTrue(winMain.contains("workerBinaryBaseName = \"swapp-sw\""))
            assertTrue(winMain.contains("prepareDesktopMainLaunch(args)"))

            val swingWorkerMain = File(launchersDir, "swing/kotlin/com/example/swapp/generated/GeneratedSwingServiceWorkerMain.kt")
                .readText(Charsets.UTF_8)
            assertTrue(swingWorkerMain.contains("@file:JvmName(\"GeneratedSwingServiceWorkerMain\")"))
            assertTrue(swingWorkerMain.contains("BackgroundWorker()"))

            val webMain = File(launchersDir, "web/kotlin/com/example/swapp/generated/GeneratedWebLauncherMain.kt")
                .readText(Charsets.UTF_8)
            assertTrue(webMain.contains("WebWorkerGlobalBridge.isWorkerGlobalScope"))
            assertTrue(webMain.contains("ServiceWorkerExtension.ensureStarted()"))

            val iosDescriptor = File(launchersDir, "ios/kotlin/com/example/swapp/generated/GeneratedIosAppDescriptor.kt")
                .readText(Charsets.UTF_8)
            assertFalse(iosDescriptor.contains("ServiceWorkerExtension.install"))
            assertFalse(iosDescriptor.contains("registerWorkerFactory"))
            val iosMain = File(launchersDir, "ios/kotlin/com/example/swapp/generated/GeneratedIosLauncherMain.kt")
                .readText(Charsets.UTF_8)
            assertFalse(iosMain.contains("ServiceWorkerExtension"))

            // 4. Web distribution with @MainServiceWorker on Web emits swapp.sw.js, swapp.sw.wasm, and SW_WASM_BASES
            val webDistOut = File(tempDir, "build/dist-with-sw/web")
            val buildJsDir = File(tempDir, "build/dist/js/productionExecutable").apply { mkdirs() }
            File(buildJsDir, "swapp.js").writeText("console.log('boot swapp.wasm');", Charsets.UTF_8)
            val buildWasmDir = File(tempDir, "build/dist/wasmJs/productionExecutable").apply { mkdirs() }
            File(buildWasmDir, "swapp.wasm").writeBytes(byteArrayOf(0x00, 0x61, 0x73, 0x6d))

            val webTask = project.tasks.register("packageWebWithSw", GenericUiWebDistributionTask::class.java).get()
            webTask.projectRootPath.set(tempDir.absolutePath)
            webTask.projectBuildPath.set(File(tempDir, "build").absolutePath)
            webTask.baseName.set("swapp")
            webTask.appName.set("SwApp")
            webTask.manifestEnabled.set(false)
            webTask.allowInstallAsWebApp.set(false)
            webTask.serviceWorkerEnabled.set(true)
            webTask.serviceWorkerBinaryBaseNames.set(listOf("swapp"))
            webTask.outputDirectory.set(webDistOut)
            webTask.packageWebDistribution()

            val swWasm = File(webDistOut, "swapp.sw.wasm")
            val swJs = File(webDistOut, "swapp.sw.js")
            val rootSw = File(webDistOut, "serviceworker.js")
            assertTrue(swWasm.isFile)
            assertTrue(swJs.isFile)
            assertTrue(swJs.readText().contains("swapp.sw.wasm"))
            val rootSwTxt = rootSw.readText()
            assertTrue(rootSwTxt.contains("const SW_WASM_BASES = [\"swapp\"];"))
            assertTrue(rootSwTxt.contains("importScripts(\"./\" + SW_WASM_BASES[i] + \".sw.js\")"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun desktopDistributionsIncludeAndStageSecondaryServiceWorkerExecutables() {
        val tempDir = Files.createTempDirectory("genericui-sw-desktop-dist").toFile()
        try {
            val commonDir = File(tempDir, "src/commonMain/kotlin/com/example/swdist").apply { mkdirs() }
            File(commonDir, "AppFrag.kt").writeText(
                """
                package com.example.swdist
                import com.nexustools.genericui.component.fragment.Fragment
                import com.nexustools.genericui.component.fragment.MainAppFragment
                @MainAppFragment
                class AppFrag : Fragment()
                """.trimIndent()
            )
            File(commonDir, "DistWorker.kt").writeText(
                """
                package com.example.swdist
                import com.nexustools.genericui.ext.serviceworker.MainServiceWorker
                import com.nexustools.genericui.ext.serviceworker.ServiceWorker
                @MainServiceWorker
                class DistWorker : ServiceWorker()
                """.trimIndent()
            )
            val ext = createBasicAppExtension(enableServiceWorker = true).apply {
                app.id = "com.example.swdist"
                app.name = "SwDist"
                app.packageName = "com.example.swdist"
                application.baseName = "swdist"
                backends.gtkEnabled = true
                backends.kdeEnabled = true
            }
            val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
            listOf(
                "genericuiValidateApp",
                "generateGenericUiLaunchers",
                "compileGenericUiResources",
                "prepareGenericUiDesktopResources",
                "bundleGenericUiAngle",
                "bundleGenericUiVulkan",
                "linkDebugExecutableMingwX64",
                "compileKotlinMingwX64",
                "linkSwdistswDebugExecutableMingwX64",
                "linkDebugExecutableMacosArm64",
                "compileKotlinMacosArm64",
                "linkSwdistswDebugExecutableMacosArm64",
                "linkGtkDebugExecutableLinuxX64",
                "compileGtkKotlinLinuxX64",
                "linkGtkSwdistswDebugExecutableLinuxX64",
                "linkDebugExecutableLinuxX64",
                "compileKotlinLinuxX64",
                "linkKdeDebugExecutableLinuxX64",
                "compileKdeKotlinLinuxX64",
                "linkKdeSwdistswDebugExecutableLinuxX64"
            ).forEach { name ->
                project.tasks.register(name)
            }

            GenericUiMultiplatformConfigurator.registerBuildTargetTasks(project, ext)

            val devWin = project.tasks.getByName("developmentWindows")
            val winDeps = devWin.taskDependencies.getDependencies(devWin).map { it.name }.toSet()
            assertTrue("linkDebugExecutableMingwX64" in winDeps)
            assertTrue("linkSwdistswDebugExecutableMingwX64" in winDeps)

            val devMac = project.tasks.getByName("developmentMac")
            val macDeps = devMac.taskDependencies.getDependencies(devMac).map { it.name }.toSet()
            assertTrue("linkDebugExecutableMacosArm64" in macDeps)
            assertTrue("linkSwdistswDebugExecutableMacosArm64" in macDeps)

            val devGtk = project.tasks.getByName("developmentLinuxGtk")
            val gtkDeps = devGtk.taskDependencies.getDependencies(devGtk).map { it.name }.toSet()
            assertTrue("linkGtkDebugExecutableLinuxX64" in gtkDeps)
            assertTrue("linkGtkSwdistswDebugExecutableLinuxX64" in gtkDeps)

            val devKde = project.tasks.getByName("developmentLinuxKde")
            val kdeDeps = devKde.taskDependencies.getDependencies(devKde).map { it.name }.toSet()
            assertTrue("linkKdeDebugExecutableLinuxX64" in kdeDeps)
            assertTrue("linkKdeSwdistswDebugExecutableLinuxX64" in kdeDeps)

            val buildDir = project.layout.buildDirectory.asFile.get()
            fun writeStubFile(rel: String, content: ByteArray = byteArrayOf(1, 2, 3, 4)) {
                val f = File(buildDir, rel)
                f.parentFile?.mkdirs()
                f.writeBytes(content)
            }
            writeStubFile("bin/mingwX64/debugExecutable/swdist.exe")
            writeStubFile("bin/mingwX64/debugExecutable/libgenericui.dll")
            writeStubFile("bin/mingwX64/swdistswDebugExecutable/swdist-sw.exe")
            writeStubFile("bin/macosArm64/debugExecutable/swdist.kexe")
            writeStubFile("bin/macosArm64/debugExecutable/libgenericui.dylib")
            writeStubFile("bin/macosArm64/swdistswDebugExecutable/swdist-sw.kexe")
            writeStubFile("bin/linuxX64/gtkDebugExecutable/swdist-gtk.kexe")
            writeStubFile("bin/linuxX64/gtkDebugExecutable/libgenericui-gtk.so")
            writeStubFile("bin/linuxX64/gtkSwdistswDebugExecutable/swdist-sw.kexe")
            writeStubFile("bin/linuxX64/kdeDebugExecutable/swdist-kde.kexe")
            writeStubFile("bin/linuxX64/kdeDebugExecutable/libgenericui-kde.so")
            writeStubFile("bin/linuxX64/kdeSwdistswDebugExecutable/swdist-sw.kexe")

            // Seed legacy serviceworker binaries in dist/bundle directories to verify stale cleanup
            writeStubFile("dist/windows/x64/development/serviceworker.exe")
            writeStubFile("dist/mac/development/SwDist.app/Contents/MacOS/serviceworker")
            writeStubFile("dist/mac/development/SwDist.app/Contents/Helpers/SwDist Service Worker.app/Contents/MacOS/serviceworker")
            writeStubFile("dist/linux-gtk/x64/development/serviceworker.kexe")
            writeStubFile("dist/linux-gtk/x64/development/serviceworker")
            writeStubFile("dist/linux-kde/x64/development/serviceworker.kexe")
            writeStubFile("dist/linux-kde/x64/development/serviceworker")

            devWin.actions.forEach { it.execute(devWin) }
            val winDist = File(buildDir, "dist/windows/x64/development")
            assertTrue(File(winDist, "swdist.exe").isFile)
            assertTrue(File(winDist, "swdist-sw.exe").isFile)
            assertFalse(File(winDist, "serviceworker.exe").exists(), "Legacy serviceworker.exe must be removed from Windows dist")

            devMac.actions.forEach { it.execute(devMac) }
            val macAppDir = File(buildDir, "dist/mac/development/SwDist.app")
            val macOsDir = File(macAppDir, "Contents/MacOS")
            assertTrue(File(macOsDir, "swdist").isFile)
            assertFalse(File(macOsDir, "swdist-sw").exists(), "swdist-sw must live inside nested Helpers/*.app bundle on macOS")
            assertFalse(File(macOsDir, "serviceworker").exists(), "Legacy serviceworker must not remain in Contents/MacOS")
            val helperBundle = File(macAppDir, "Contents/Helpers/SwDist Service Worker.app")
            assertTrue(File(helperBundle, "Contents/MacOS/swdist-sw").isFile)
            assertFalse(File(helperBundle, "Contents/MacOS/serviceworker").exists(), "Legacy serviceworker must be removed from helper bundle")
            assertTrue(File(helperBundle, "Contents/PkgInfo").isFile)
            val helperPlist = File(helperBundle, "Contents/Info.plist").readText(Charsets.UTF_8)
            assertTrue(helperPlist.contains("com.example.swdist.serviceworker"))
            assertTrue(helperPlist.contains("<string>SwDist Service Worker</string>"))
            assertTrue(helperPlist.contains("<string>swdist-sw</string>"))
            assertTrue(helperPlist.contains("<key>LSUIElement</key>"))
            assertTrue(helperPlist.contains("<key>LSMultipleInstancesProhibited</key>"))
            assertTrue(helperPlist.contains("<key>NSLocalNetworkUsageDescription</key>"))
            val mainPlist = File(macAppDir, "Contents/Info.plist").readText(Charsets.UTF_8)
            assertTrue(mainPlist.contains("<key>LSMultipleInstancesProhibited</key>"))
            assertTrue(mainPlist.contains("<true/>"))

            devGtk.actions.forEach { it.execute(devGtk) }
            val gtkDist = File(buildDir, "dist/linux-gtk/x64/development")
            assertTrue(File(gtkDist, "swdist-gtk.kexe").isFile)
            assertTrue(File(gtkDist, "swdist-sw.kexe").isFile)
            assertTrue(File(gtkDist, "swdist-sw").isFile)
            assertFalse(File(gtkDist, "serviceworker.kexe").exists())
            assertFalse(File(gtkDist, "serviceworker").exists())

            devKde.actions.forEach { it.execute(devKde) }
            val kdeDist = File(buildDir, "dist/linux-kde/x64/development")
            assertTrue(File(kdeDist, "swdist-kde.kexe").isFile)
            assertTrue(File(kdeDist, "swdist-sw.kexe").isFile)
            assertTrue(File(kdeDist, "swdist-sw").isFile)
            assertFalse(File(kdeDist, "serviceworker.kexe").exists())
            assertFalse(File(kdeDist, "serviceworker").exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun serviceWorkerAndDesktopDslConfigureMacosTrayIconLocalNetworkAndBonjourServices() {
        val extConfig = GenericUiExtensionsConfig()
        extConfig.serviceWorker {
            autostart = true
            trayIcon = "icons/tray.svg"
            macosTrayIcon("icons/macos-tray.svg")
            macLocalNetworkUsageDescription = "Discover receivers on your local network."
            bonjourServices("_googlecast._tcp", "_googlecast._tcp.")
        }
        assertEquals("icons/macos-tray.svg", extConfig.serviceWorkerConfig.macosTrayIcon)
        assertEquals(
            "Discover receivers on your local network.",
            extConfig.serviceWorkerConfig.macLocalNetworkUsageDescription
        )
        assertEquals(
            listOf("_googlecast._tcp", "_googlecast._tcp."),
            extConfig.serviceWorkerConfig.macBonjourServices
        )
        assertEquals("icons/macos-tray.svg", extConfig.extensionOptions["serviceWorker.macosTrayIcon"])
        assertEquals(
            "Discover receivers on your local network.",
            extConfig.extensionOptions["serviceWorker.macLocalNetworkUsageDescription"]
        )
        assertEquals(
            "_googlecast._tcp,_googlecast._tcp.",
            extConfig.extensionOptions["serviceWorker.macBonjourServices"]
        )

        val desktopConfig = GenericUiDesktopConfig().apply {
            macMultipleInstancesProhibited = false
            macosTrayIcon("icons/desktop-macos-tray.png")
            macLocalNetworkUsageDescription = "Desktop local network usage"
            bonjourServices("_airplay._tcp")
        }
        assertFalse(desktopConfig.macMultipleInstancesProhibited)
        assertEquals("icons/desktop-macos-tray.png", desktopConfig.macosTrayIcon)
        assertEquals("Desktop local network usage", desktopConfig.macLocalNetworkUsageDescription)
        assertEquals(listOf("_airplay._tcp"), desktopConfig.macBonjourServices)
    }

    @Test
    fun macosResourceCompilerEmitsMacosTrayIconKeysOnlyWhenExplicitlyConfigured() {
        val tempRoot = Files.createTempDirectory("genericui-sw-mactray").toFile()
        try {
            val noOverrideDir = File(tempRoot, "no-override").apply { mkdirs() }
            writePngFile(File(noOverrideDir, "src/commonMain/resources/app_icon.png"), 0xFF2266AA.toInt())
            File(noOverrideDir, "src/commonMain/resources/tray.svg").writeText(
                """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><circle cx="12" cy="12" r="9"/></svg>"""
            )

            val projNoOverride = ProjectBuilder.builder().withProjectDir(noOverrideDir).build()
            projNoOverride.pluginManager.apply("com.nexustools.genericui")
            val extNoOverride = projNoOverride.extensions.getByType(GenericUiExtension::class.java)
            extNoOverride.application {
                appName = "SilhouetteApp"
                baseName = "silhouette"
                icons(common = "src/commonMain/resources/app_icon.png")
            }
            extNoOverride.backends { macos(true) }
            extNoOverride.extensions.serviceWorker {
                trayIcon = "src/commonMain/resources/tray.svg"
            }

            val desktopNoOverride = projNoOverride.tasks.getByName("prepareGenericUiDesktopResources") as GenericUiDesktopResourceTask
            desktopNoOverride.generateDesktopResources()
            val macResOut1 = desktopNoOverride.outputDirectory.get().asFile
            assertTrue(File(macResOut1, "macos/tray_icon.png").isFile)
            assertTrue(File(macResOut1, "macos/tray_icon.svg").isFile)
            assertFalse(File(macResOut1, "macos/macos_tray_icon.png").exists())
            assertFalse(File(macResOut1, "macos/macos_tray_icon.svg").exists())

            val resNoOverride = projNoOverride.tasks.getByName("compileGenericUiResources") as GenericUiResourceCompilerTask
            resNoOverride.compileResources()
            val macCNoOverride = File(resNoOverride.backendOutputDirectory.get().asFile, "macos/genericui_app_resources.c")
                .readText(Charsets.UTF_8)
            assertTrue(macCNoOverride.contains("\"tray_icon_png\""))
            assertTrue(macCNoOverride.contains("\"tray_icon_svg\""))
            assertFalse(macCNoOverride.contains("\"macos_tray_icon_png\""))
            assertFalse(macCNoOverride.contains("\"macos_tray_icon_svg\""))

            val withOverrideDir = File(tempRoot, "with-override").apply { mkdirs() }
            writePngFile(File(withOverrideDir, "src/commonMain/resources/app_icon.png"), 0xFF2266AA.toInt())
            File(withOverrideDir, "src/commonMain/resources/tray.svg").writeText(
                """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><circle cx="12" cy="12" r="9"/></svg>"""
            )
            File(withOverrideDir, "src/commonMain/resources/macos_tray.svg").writeText(
                """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><rect x="4" y="4" width="16" height="16"/></svg>"""
            )

            val projWithOverride = ProjectBuilder.builder().withProjectDir(withOverrideDir).build()
            projWithOverride.pluginManager.apply("com.nexustools.genericui")
            val extWithOverride = projWithOverride.extensions.getByType(GenericUiExtension::class.java)
            extWithOverride.application {
                appName = "ExplicitMacTray"
                baseName = "explicitmactray"
                icons(common = "src/commonMain/resources/app_icon.png")
            }
            extWithOverride.backends { macos(true) }
            extWithOverride.extensions.serviceWorker {
                trayIcon = "src/commonMain/resources/tray.svg"
                macosTrayIcon("src/commonMain/resources/macos_tray.svg")
            }

            val desktopWithOverride = projWithOverride.tasks.getByName("prepareGenericUiDesktopResources") as GenericUiDesktopResourceTask
            desktopWithOverride.generateDesktopResources()
            val macResOut2 = desktopWithOverride.outputDirectory.get().asFile
            assertTrue(File(macResOut2, "macos/macos_tray_icon.png").isFile)
            assertTrue(File(macResOut2, "macos/macos_tray_icon.svg").isFile)

            val resWithOverride = projWithOverride.tasks.getByName("compileGenericUiResources") as GenericUiResourceCompilerTask
            resWithOverride.compileResources()
            val macCWithOverride = File(resWithOverride.backendOutputDirectory.get().asFile, "macos/genericui_app_resources.c")
                .readText(Charsets.UTF_8)
            assertTrue(macCWithOverride.contains("\"macos_tray_icon_png\""))
            assertTrue(macCWithOverride.contains("\"macos_tray_icon_svg\""))
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun macosInfoPlistAndNestedHelperBundleIncludeLocalNetworkAndBonjourConfiguration() {
        val tempDir = Files.createTempDirectory("genericui-sw-mac-plist").toFile()
        try {
            val commonDir = File(tempDir, "src/commonMain/kotlin/com/example/cast").apply { mkdirs() }
            File(commonDir, "CastFrag.kt").writeText(
                """
                package com.example.cast
                import com.nexustools.genericui.component.fragment.Fragment
                import com.nexustools.genericui.component.fragment.MainAppFragment
                @MainAppFragment
                class CastFrag : Fragment()
                """.trimIndent()
            )
            File(commonDir, "CastWorker.kt").writeText(
                """
                package com.example.cast
                import com.nexustools.genericui.ext.serviceworker.MainServiceWorker
                import com.nexustools.genericui.ext.serviceworker.ServiceWorker
                @MainServiceWorker
                class CastWorker : ServiceWorker()
                """.trimIndent()
            )

            val ext = createBasicAppExtension(enableServiceWorker = true).apply {
                app.id = "com.example.castreceiver"
                app.name = "Cast Receiver"
                app.packageName = "com.example.cast"
                application.baseName = "castreceiver"
                extensions.serviceWorker {
                    autostart = true
                    macLocalNetworkUsageDescription = "Required for local Cast mDNS receiver discovery."
                    bonjourServices("_googlecast._tcp", "_googlecast._tcp.")
                }
            }
            val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
            listOf(
                "genericuiValidateApp",
                "generateGenericUiLaunchers",
                "compileGenericUiResources",
                "prepareGenericUiDesktopResources",
                "bundleGenericUiAngle",
                "bundleGenericUiVulkan",
                "linkDebugExecutableMacosArm64",
                "compileKotlinMacosArm64",
                "linkCastreceiverswDebugExecutableMacosArm64"
            ).forEach { name ->
                project.tasks.register(name)
            }
            GenericUiMultiplatformConfigurator.registerBuildTargetTasks(project, ext)

            val buildDir = project.layout.buildDirectory.asFile.get()
            fun writeStub(rel: String) {
                val f = File(buildDir, rel)
                f.parentFile?.mkdirs()
                f.writeBytes(byteArrayOf(1, 2, 3, 4))
            }
            writeStub("bin/macosArm64/debugExecutable/castreceiver.kexe")
            writeStub("bin/macosArm64/castreceiverswDebugExecutable/castreceiver-sw.kexe")

            val devMac = project.tasks.getByName("developmentMac")
            devMac.actions.forEach { it.execute(devMac) }

            val appBundle = File(buildDir, "dist/mac/development/Cast Receiver.app")
            val mainPlist = File(appBundle, "Contents/Info.plist").readText(Charsets.UTF_8)
            assertTrue(mainPlist.contains("<key>LSMultipleInstancesProhibited</key>"))
            assertTrue(mainPlist.contains("<true/>"))
            assertTrue(mainPlist.contains("<key>NSLocalNetworkUsageDescription</key>"))
            assertTrue(mainPlist.contains("Required for local Cast mDNS receiver discovery."))
            assertTrue(mainPlist.contains("<key>NSBonjourServices</key>"))
            assertTrue(mainPlist.contains("<string>_googlecast._tcp</string>"))
            assertTrue(mainPlist.contains("<string>_googlecast._tcp.</string>"))

            val helperBundle = File(appBundle, "Contents/Helpers/Cast Receiver Service Worker.app")
            assertTrue(File(helperBundle, "Contents/MacOS/castreceiver-sw").isFile)
            val helperPlist = File(helperBundle, "Contents/Info.plist").readText(Charsets.UTF_8)
            assertTrue(helperPlist.contains("com.example.castreceiver.serviceworker"))
            assertTrue(helperPlist.contains("<string>castreceiver-sw</string>"))
            assertTrue(helperPlist.contains("<key>LSUIElement</key>"))
            assertTrue(helperPlist.contains("<key>LSMultipleInstancesProhibited</key>"))
            assertTrue(helperPlist.contains("Required for local Cast mDNS receiver discovery."))
            assertTrue(helperPlist.contains("<string>_googlecast._tcp</string>"))

            // Also verify prepareGenericUiDesktopResources emits the same keys + Helpers autostart path
            val project2 = ProjectBuilder.builder().withProjectDir(tempDir).build()
            val desktopOut = File(tempDir, "build/desktop-res-plist")
            val desktopTask = project2.tasks.register("desktopPlistCheck", GenericUiDesktopResourceTask::class.java).get()
            desktopTask.projectRootPath.set(tempDir.absolutePath)
            desktopTask.baseName.set("castreceiver")
            desktopTask.appName.set("Cast Receiver")
            desktopTask.appId.set("com.example.castreceiver")
            desktopTask.appVersion.set("1.0.0")
            desktopTask.vendor.set("Example")
            desktopTask.embedWindowsResources.set(false)
            desktopTask.macMinimumSystemVersion.set("12.0")
            desktopTask.macCategory.set("public.app-category.utilities")
            desktopTask.macHighResolutionCapable.set(true)
            desktopTask.linuxCategories.set("Utility;")
            desktopTask.linuxTerminal.set(false)
            desktopTask.customResourceEntries.set(emptyMap())
            desktopTask.enabledExtensionModulePaths.set(ext.extensions.enabledExtensionModulePaths())
            desktopTask.extensionOptions.set(GenericUiGradlePlugin.buildPluginExtensionOptionsStatic(ext))
            desktopTask.outputDirectory.set(desktopOut)
            desktopTask.generateDesktopResources()

            val generatedInfoPlist = File(desktopOut, "macos/Info.plist").readText(Charsets.UTF_8)
            assertTrue(generatedInfoPlist.contains("<key>LSMultipleInstancesProhibited</key>"))
            assertTrue(generatedInfoPlist.contains("<key>NSLocalNetworkUsageDescription</key>"))
            assertTrue(generatedInfoPlist.contains("<key>NSBonjourServices</key>"))
            assertTrue(generatedInfoPlist.contains("<string>_googlecast._tcp</string>"))

            val macAutostartPlist = File(desktopOut, "autostart/macos/com.example.castreceiver.serviceworker.plist")
                .readText(Charsets.UTF_8)
            assertTrue(
                macAutostartPlist.contains("Contents/Helpers/Cast Receiver Service Worker.app/Contents/MacOS/castreceiver-sw"),
                "macOS LaunchAgent plist should target nested Service Worker.app executable"
            )
            assertTrue(
                macAutostartPlist.contains("<key>MachServices</key>"),
                "macOS LaunchAgent plist should register MachServices for XPC"
            )
            assertTrue(
                macAutostartPlist.contains("<key>com.example.castreceiver.serviceworker</key>"),
                "macOS LaunchAgent MachServices should declare entry identifier"
            )
            assertTrue(
                macAutostartPlist.contains("<true/>"),
                "macOS LaunchAgent MachServices entry should be enabled"
            )

            val linuxDesktopFile = File(
                desktopOut,
                "autostart/linux/com.example.castreceiver.serviceworker.desktop"
            )
            assertTrue(linuxDesktopFile.isFile, "Linux autostart should emit .desktop file")
            val linuxDesktop = linuxDesktopFile.readText(Charsets.UTF_8)
            assertTrue(linuxDesktop.contains("[Desktop Entry]"))
            assertTrue(linuxDesktop.contains("Name=Cast Receiver"))
            assertTrue(linuxDesktop.contains("Exec=castreceiver-sw"))
            assertTrue(linuxDesktop.contains("X-GNOME-Autostart-enabled=true"))

            val linuxAutostartDir = File(desktopOut, "autostart/linux")
            assertTrue(
                linuxAutostartDir.listFiles()?.none { it.name.endsWith(".service") } == true,
                "Linux autostart must not emit D-Bus .service files"
            )
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun desktopAutostartEmitsMacosMachServicesPlistAndLinuxDesktopOnly() {
        val tempDir = Files.createTempDirectory("genericui-sw-autostart").toFile()
        try {
            val commonDir = File(tempDir, "src/commonMain/kotlin/com/example/numapp").apply { mkdirs() }
            File(commonDir, "MainFrag.kt").writeText(
                """
                package com.example.numapp
                import com.nexustools.genericui.component.fragment.Fragment
                import com.nexustools.genericui.component.fragment.MainAppFragment
                @MainAppFragment
                class MainFrag : Fragment()
                """.trimIndent()
            )
            File(commonDir, "NumWorker.kt").writeText(
                """
                package com.example.numapp
                import com.nexustools.genericui.ext.serviceworker.MainServiceWorker
                import com.nexustools.genericui.ext.serviceworker.ServiceWorker
                @MainServiceWorker
                class NumWorker : ServiceWorker()
                """.trimIndent()
            )

            val ext = createBasicAppExtension(enableServiceWorker = true).apply {
                app.id = "com.example.99-cast.app"
                app.name = "Num Cast"
                app.packageName = "com.example.numapp"
                application.baseName = "numcast"
                extensions.serviceWorker {
                    autostart = true
                }
            }

            val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
            val desktopOut = File(tempDir, "build/desktop-res-autostart")
            val desktopTask = project.tasks.register("desktopAutostartCheck", GenericUiDesktopResourceTask::class.java).get()
            desktopTask.projectRootPath.set(tempDir.absolutePath)
            desktopTask.baseName.set("numcast")
            desktopTask.appName.set("Num Cast")
            desktopTask.appId.set("com.example.99-cast.app")
            desktopTask.appVersion.set("1.0.0")
            desktopTask.vendor.set("Example")
            desktopTask.embedWindowsResources.set(false)
            desktopTask.macMinimumSystemVersion.set("12.0")
            desktopTask.macCategory.set("public.app-category.utilities")
            desktopTask.macHighResolutionCapable.set(true)
            desktopTask.linuxCategories.set("Utility;")
            desktopTask.linuxTerminal.set(false)
            desktopTask.customResourceEntries.set(emptyMap())
            desktopTask.enabledExtensionModulePaths.set(ext.extensions.enabledExtensionModulePaths())
            desktopTask.extensionOptions.set(GenericUiGradlePlugin.buildPluginExtensionOptionsStatic(ext))
            desktopTask.outputDirectory.set(desktopOut)
            desktopTask.generateDesktopResources()

            val linuxAutostartDir = File(desktopOut, "autostart/linux")
            val linuxDesktopFile = File(linuxAutostartDir, "com.example.99-cast.app.serviceworker.desktop")
            assertTrue(linuxDesktopFile.isFile, "Expected .desktop autostart file at ${linuxDesktopFile.absolutePath}")
            val linuxDesktopText = linuxDesktopFile.readText(Charsets.UTF_8)
            assertTrue(linuxDesktopText.contains("[Desktop Entry]"))
            assertTrue(linuxDesktopText.contains("Name=Num Cast"))
            assertTrue(linuxDesktopText.contains("Exec=numcast-sw"))
            assertTrue(linuxDesktopText.contains("X-GNOME-Autostart-enabled=true"))
            assertTrue(
                linuxAutostartDir.listFiles()?.none { it.name.endsWith(".service") } == true,
                "Linux autostart must not emit D-Bus .service files"
            )

            val macPlist = File(desktopOut, "autostart/macos/com.example.99-cast.app.serviceworker.plist")
                .readText(Charsets.UTF_8)
            assertTrue(
                macPlist.contains("/Applications/Num Cast.app/Contents/Helpers/Num Cast Service Worker.app/Contents/MacOS/numcast-sw"),
                "macOS LaunchAgent plist should point to nested numcast-sw helper executable:\n$macPlist"
            )
            val expectedMachServicesBlock = listOf(
                "    <key>MachServices</key>",
                "    <dict>",
                "        <key>com.example.99-cast.app.serviceworker</key>",
                "        <true/>",
                "    </dict>"
            ).joinToString("\n")
            assertTrue(
                macPlist.replace("\r\n", "\n").contains(expectedMachServicesBlock),
                "macOS LaunchAgent plist missing MachServices block:\n$macPlist"
            )
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun generatedServiceWorkerMainsInitializeResourcesAndSchemaAndLinkHelpersRpath() {
        val tempDir = Files.createTempDirectory("genericui-sw-init-calls").toFile()
        try {
            val commonDir = File(tempDir, "src/commonMain/kotlin/com/example/initapp").apply { mkdirs() }
            File(commonDir, "MainFrag.kt").writeText(
                """
                package com.example.initapp
                import com.nexustools.genericui.component.fragment.Fragment
                import com.nexustools.genericui.component.fragment.MainAppFragment
                @MainAppFragment
                class MainFrag : Fragment()
                """.trimIndent()
            )
            File(commonDir, "BgWorker.kt").writeText(
                """
                package com.example.initapp
                import com.nexustools.genericui.ext.serviceworker.MainServiceWorker
                import com.nexustools.genericui.ext.serviceworker.ServiceWorker
                @MainServiceWorker
                class BgWorker : ServiceWorker()
                """.trimIndent()
            )

            val ext = createBasicAppExtension(enableServiceWorker = true).apply {
                app.id = "com.example.initapp"
                app.name = "InitApp"
                app.packageName = "com.example.initapp"
                application.baseName = "initapp"
                resources.enabled = true
                resources.packageName = "com.example.initapp.res"
                extensions.schema(true)
                extensions.serviceWorker {
                    macosTrayIcon("icons/mac_tray.svg")
                }
            }

            val launcherOut = File(tempDir, "build/launchers")
            GenericUiGenerateLaunchersTask.generateForProject(
                projectDir = tempDir,
                outputRootDir = launcherOut,
                extension = ext
            )

            val macWorkerSrc = File(launcherOut, "macos/kotlin/com/example/initapp/generated/GeneratedMacosServiceWorkerMain.kt")
                .readText(Charsets.UTF_8)
            assertTrue(macWorkerSrc.contains("com.example.initapp.res.GeneratedResourceRegistry.ensureInitialized()"))
            assertTrue(macWorkerSrc.contains("com.example.initapp.generated.GeneratedSchemaRegistrar.ensureRegistered()"))
            assertTrue(macWorkerSrc.contains("com.example.initapp.generated.GeneratedServiceWorkerCodecRegistrar.registerAll()"))
            assertTrue(macWorkerSrc.contains("macosTrayIcon = \"icons/mac_tray.svg\""))
            assertTrue(macWorkerSrc.contains("workerBinaryBaseName = \"initapp-sw\""))
            assertTrue(
                macWorkerSrc.indexOf("GeneratedResourceRegistry.ensureInitialized()") <
                    macWorkerSrc.indexOf("runDesktopWorkerMain")
            )
            assertTrue(
                macWorkerSrc.indexOf("GeneratedSchemaRegistrar.ensureRegistered()") <
                    macWorkerSrc.indexOf("runDesktopWorkerMain")
            )

            val swingWorkerSrc = File(launcherOut, "swing/kotlin/com/example/initapp/generated/GeneratedSwingServiceWorkerMain.kt")
                .readText(Charsets.UTF_8)
            assertTrue(swingWorkerSrc.contains("com.example.initapp.res.GeneratedResourceRegistry.ensureInitialized()"))
            assertTrue(swingWorkerSrc.contains("com.example.initapp.generated.GeneratedSchemaRegistrar.ensureRegistered()"))
            assertTrue(swingWorkerSrc.contains("com.example.initapp.generated.GeneratedServiceWorkerCodecRegistrar.registerAll()"))
            assertTrue(swingWorkerSrc.contains("macosTrayIcon = \"icons/mac_tray.svg\""))

            val extCtx = ExtensionBuildContext(
                projectRootDir = tempDir,
                projectName = "initapp",
                appId = ext.app.id,
                appName = ext.app.name,
                appVersion = ext.app.version,
                baseName = ext.application.baseName,
                generatedPackageName = "com.example.initapp.generated",
                enabledExtensionModulePaths = ext.extensions.enabledExtensionModulePaths().toSet(),
                discoveredFragments = GenericUiFragmentScanner.scanProject(tempDir),
                discoveredSymbols = GenericUiFragmentScanner.scanProjectSymbols(
                    tempDir,
                    GenericUiExtensionContributorRegistry.allAnnotationScanSpecs(
                        ext.extensions.enabledExtensionModulePaths().toSet(),
                        tempDir
                    )
                ),
                extensionOptions = GenericUiGradlePlugin.buildPluginExtensionOptionsStatic(ext)
            )
            val contrib = ServiceWorkerExtensionContributor.contributeDesktop(extCtx)
            assertNotNull(contrib)
            assertEquals("initapp-sw", contrib.secondaryExecutables.single().binaryBaseName)
            assertTrue(
                "-Wl,-rpath,@executable_path/../../../../Frameworks" in
                    contrib.secondaryExecutables.single().macosExtraLinkerOpts
            )
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun hyphenatedBaseNameProducesSanitizedSecondaryTaskAndSwBinaryNames() {
        assertEquals("app-sw", ServiceWorkerExtensionContributor.resolveWorkerBinaryBaseName(""))
        assertEquals(
            "episode-caster-receiver-sw",
            ServiceWorkerExtensionContributor.resolveWorkerBinaryBaseName("episode-caster-receiver")
        )

        val tempDir = Files.createTempDirectory("genericui-sw-hyphen-base").toFile()
        try {
            val commonDir = File(tempDir, "src/commonMain/kotlin/com/example/epcast").apply { mkdirs() }
            File(commonDir, "MainFrag.kt").writeText(
                """
                package com.example.epcast
                import com.nexustools.genericui.component.fragment.Fragment
                import com.nexustools.genericui.component.fragment.MainAppFragment
                @MainAppFragment
                class MainFrag : Fragment()
                """.trimIndent()
            )
            File(commonDir, "CastWorker.kt").writeText(
                """
                package com.example.epcast
                import com.nexustools.genericui.ext.serviceworker.MainServiceWorker
                import com.nexustools.genericui.ext.serviceworker.ServiceWorker
                @MainServiceWorker
                class CastWorker : ServiceWorker()
                """.trimIndent()
            )
            val ext = createBasicAppExtension(enableServiceWorker = true).apply {
                app.id = "com.example.episodecaster"
                app.name = "Episode Caster Receiver"
                app.packageName = "com.example.epcast"
                application.baseName = "episode-caster-receiver"
                backends.gtkEnabled = true
                backends.kdeEnabled = true
                extensions.serviceWorker {
                    autostart = true
                }
            }
            val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
            listOf(
                "genericuiValidateApp",
                "generateGenericUiLaunchers",
                "compileGenericUiResources",
                "prepareGenericUiDesktopResources",
                "bundleGenericUiAngle",
                "bundleGenericUiVulkan",
                "linkDebugExecutableMingwX64",
                "compileKotlinMingwX64",
                "linkEpisodecasterreceiverswDebugExecutableMingwX64",
                "linkDebugExecutableMacosArm64",
                "compileKotlinMacosArm64",
                "linkEpisodecasterreceiverswDebugExecutableMacosArm64",
                "linkGtkDebugExecutableLinuxX64",
                "compileGtkKotlinLinuxX64",
                "linkGtkEpisodecasterreceiverswDebugExecutableLinuxX64",
                "linkDebugExecutableLinuxX64",
                "compileKotlinLinuxX64",
                "linkKdeDebugExecutableLinuxX64",
                "compileKdeKotlinLinuxX64",
                "linkKdeEpisodecasterreceiverswDebugExecutableLinuxX64"
            ).forEach { name ->
                project.tasks.register(name)
            }
            GenericUiMultiplatformConfigurator.registerBuildTargetTasks(project, ext)

            val devWin = project.tasks.getByName("developmentWindows")
            val devMac = project.tasks.getByName("developmentMac")
            val devGtk = project.tasks.getByName("developmentLinuxGtk")
            val devKde = project.tasks.getByName("developmentLinuxKde")
            assertTrue("linkEpisodecasterreceiverswDebugExecutableMingwX64" in devWin.taskDependencies.getDependencies(devWin).map { it.name })
            assertTrue("linkEpisodecasterreceiverswDebugExecutableMacosArm64" in devMac.taskDependencies.getDependencies(devMac).map { it.name })
            assertTrue("linkGtkEpisodecasterreceiverswDebugExecutableLinuxX64" in devGtk.taskDependencies.getDependencies(devGtk).map { it.name })
            assertTrue("linkKdeEpisodecasterreceiverswDebugExecutableLinuxX64" in devKde.taskDependencies.getDependencies(devKde).map { it.name })

            val buildDir = project.layout.buildDirectory.asFile.get()
            fun writeStub(rel: String) {
                val f = File(buildDir, rel)
                f.parentFile?.mkdirs()
                f.writeBytes(byteArrayOf(1, 2, 3, 4))
            }
            writeStub("bin/mingwX64/debugExecutable/episode-caster-receiver.exe")
            writeStub("bin/mingwX64/episodecasterreceiverswDebugExecutable/episode-caster-receiver-sw.exe")
            writeStub("bin/macosArm64/debugExecutable/episode-caster-receiver.kexe")
            writeStub("bin/macosArm64/episodecasterreceiverswDebugExecutable/episode-caster-receiver-sw.kexe")
            writeStub("bin/linuxX64/gtkDebugExecutable/episode-caster-receiver-gtk.kexe")
            writeStub("bin/linuxX64/gtkEpisodecasterreceiverswDebugExecutable/episode-caster-receiver-sw.kexe")
            writeStub("bin/linuxX64/kdeDebugExecutable/episode-caster-receiver-kde.kexe")
            writeStub("bin/linuxX64/kdeEpisodecasterreceiverswDebugExecutable/episode-caster-receiver-sw.kexe")

            devWin.actions.forEach { it.execute(devWin) }
            assertTrue(File(buildDir, "dist/windows/x64/development/episode-caster-receiver-sw.exe").isFile)

            devMac.actions.forEach { it.execute(devMac) }
            val helperApp = File(
                buildDir,
                "dist/mac/development/Episode Caster Receiver.app/Contents/Helpers/Episode Caster Receiver Service Worker.app"
            )
            assertTrue(File(helperApp, "Contents/MacOS/episode-caster-receiver-sw").isFile)
            val helperPlist = File(helperApp, "Contents/Info.plist").readText(Charsets.UTF_8)
            assertTrue(helperPlist.contains("<key>CFBundleExecutable</key>"))
            assertTrue(helperPlist.contains("<string>episode-caster-receiver-sw</string>"))
            assertTrue(helperPlist.contains("<string>com.example.episodecaster.serviceworker</string>"))

            devGtk.actions.forEach { it.execute(devGtk) }
            assertTrue(File(buildDir, "dist/linux-gtk/x64/development/episode-caster-receiver-sw.kexe").isFile)
            assertTrue(File(buildDir, "dist/linux-gtk/x64/development/episode-caster-receiver-sw").isFile)

            devKde.actions.forEach { it.execute(devKde) }
            assertTrue(File(buildDir, "dist/linux-kde/x64/development/episode-caster-receiver-sw.kexe").isFile)
            assertTrue(File(buildDir, "dist/linux-kde/x64/development/episode-caster-receiver-sw").isFile)

            val resProject = ProjectBuilder.builder().withProjectDir(tempDir).build()
            val desktopOut = File(tempDir, "build/desktop-res-hyphen")
            val desktopTask = resProject.tasks.register("desktopHyphenCheck", GenericUiDesktopResourceTask::class.java).get()
            desktopTask.projectRootPath.set(tempDir.absolutePath)
            desktopTask.baseName.set("episode-caster-receiver")
            desktopTask.appName.set("Episode Caster Receiver")
            desktopTask.appId.set("com.example.episodecaster")
            desktopTask.appVersion.set("1.0.0")
            desktopTask.vendor.set("Example")
            desktopTask.embedWindowsResources.set(false)
            desktopTask.macMinimumSystemVersion.set("12.0")
            desktopTask.macCategory.set("public.app-category.utilities")
            desktopTask.macHighResolutionCapable.set(true)
            desktopTask.linuxCategories.set("Utility;")
            desktopTask.linuxTerminal.set(false)
            desktopTask.customResourceEntries.set(emptyMap())
            desktopTask.enabledExtensionModulePaths.set(ext.extensions.enabledExtensionModulePaths())
            desktopTask.extensionOptions.set(GenericUiGradlePlugin.buildPluginExtensionOptionsStatic(ext))
            desktopTask.outputDirectory.set(desktopOut)
            desktopTask.generateDesktopResources()

            val macAgentPlist = File(desktopOut, "autostart/macos/com.example.episodecaster.serviceworker.plist")
                .readText(Charsets.UTF_8)
            assertTrue(
                macAgentPlist.contains(
                    "/Applications/Episode Caster Receiver.app/Contents/Helpers/Episode Caster Receiver Service Worker.app/Contents/MacOS/episode-caster-receiver-sw"
                )
            )
            val linuxDesktop = File(desktopOut, "autostart/linux/com.example.episodecaster.serviceworker.desktop")
                .readText(Charsets.UTF_8)
            assertTrue(linuxDesktop.contains("Exec=episode-caster-receiver-sw"))
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
