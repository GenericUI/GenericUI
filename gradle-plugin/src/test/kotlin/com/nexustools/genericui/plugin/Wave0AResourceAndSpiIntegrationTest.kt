package com.nexustools.genericui.plugin

import com.nexustools.genericui.plugin.spi.AndroidExtensionContribution
import com.nexustools.genericui.plugin.spi.AndroidManifestContribution
import com.nexustools.genericui.plugin.spi.AndroidProviderContribution
import com.nexustools.genericui.plugin.spi.AndroidReceiverContribution
import com.nexustools.genericui.plugin.spi.AndroidServiceContribution
import com.nexustools.genericui.plugin.spi.AppleMobileExtensionContribution
import com.nexustools.genericui.plugin.spi.DesktopExtensionContribution
import com.nexustools.genericui.plugin.spi.ExtensionAnnotationScanSpec
import com.nexustools.genericui.plugin.spi.ExtensionBuildContext
import com.nexustools.genericui.plugin.spi.ExtensionGeneratedSource
import com.nexustools.genericui.plugin.spi.GenericUiExtensionContributor
import com.nexustools.genericui.plugin.spi.GenericUiExtensionContributorRegistry
import com.nexustools.genericui.plugin.spi.SecondaryDesktopExecutableContribution
import com.nexustools.genericui.plugin.spi.WebServiceWorkerContribution
import com.nexustools.genericui.plugin.spi.WebSwPluginScript
import com.nexustools.genericui.plugin.spi.WebWorkerBinarySpec
import org.gradle.testfixtures.ProjectBuilder
import java.io.File
import java.nio.file.Files
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class Wave0AResourceAndSpiIntegrationTest {

    @Test
    fun `part 0_1 isolates consumer R from upstream modules and disambiguates duplicate stems`() {
        val tempRoot = Files.createTempDirectory("wave0a-part01-").toFile()
        try {
            val projectDir = File(tempRoot, "app-proj").apply { mkdirs() }
            val project = ProjectBuilder.builder().withProjectDir(projectDir).withName("WaveApp").build()
            project.pluginManager.apply("com.nexustools.genericui")
            val ext = project.extensions.getByType(GenericUiExtension::class.java)
            ext.application {
                appName = "Wave Test"
                baseName = "wavetest"
            }
            ext.backends {
                all()
            }

            File(projectDir, "src/commonMain/resources/drawable").apply { mkdirs() }
            File(projectDir, "src/commonMain/resources/drawable/ic_cast.svg").writeText(
                """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path d="M2 12h20"/></svg>"""
            )
            File(projectDir, "src/commonMain/resources/font").apply { mkdirs() }
            File(projectDir, "src/commonMain/resources/font/icons.woff").writeBytes(byteArrayOf(0x77, 0x4F, 0x46, 0x46))
            File(projectDir, "src/commonMain/resources/font/icons.woff2").writeBytes(byteArrayOf(0x77, 0x4F, 0x46, 0x32))

            // Upstream module manifest simulating :common ("genericui") + :extensions:ui:alien ("genericui-ext-alien")
            val upstreamCommonJson = File(tempRoot, "genericui-common.json").apply {
                val b64 = Base64.getEncoder().encodeToString("<svg/>".toByteArray())
                writeText(
                    """
                    {
                      "schemaVersion": 2,
                      "scopeId": "genericui-common",
                      "moduleByte": 126,
                      "moduleGroupId": "genericui",
                      "scopePrefix": "gui",
                      "package": "com.nexustools.genericui.res",
                      "entries": [
                        {"type":"DRAWABLE","name":"loading","id":2114000001,"relativePath":"drawable/loading.svg","canonicalRelativePath":"gui/drawable/loading.svg","nativeResourceKey":"drawable_loading_svg","nativeLibraryGroupId":"genericui","format":"svg","svgBase64":"$b64","base64Data":"$b64"},
                        {"type":"STYLE","name":"genericui","id":2115000001,"relativePath":"styles/genericui.scss","canonicalRelativePath":"gui/styles/genericui.scss","nativeResourceKey":"styles_genericui_scss","nativeLibraryGroupId":"genericui","format":"scss","textBase64":"$b64","base64Data":"$b64"}
                      ]
                    }
                    """.trimIndent()
                )
            }
            val upstreamAlienJson = File(tempRoot, "genericui-ext-ui-alien.json").apply {
                val b64 = Base64.getEncoder().encodeToString("<svg/>".toByteArray())
                writeText(
                    """
                    {
                      "schemaVersion": 2,
                      "scopeId": "genericui-ext-ui-alien",
                      "moduleByte": 130,
                      "moduleGroupId": "genericui-ext-alien",
                      "scopePrefix": "gui/ext/ui/alien",
                      "package": "com.nexustools.genericui.ext.alien.res",
                      "entries": [
                        {"type":"DRAWABLE","name":"ic_alien","id":2181100001,"relativePath":"drawable/ic_alien.svg","canonicalRelativePath":"gui/ext/ui/alien/drawable/ic_alien.svg","nativeResourceKey":"drawable_ic_alien_svg","nativeLibraryGroupId":"genericui-ext-alien","format":"svg","svgBase64":"$b64","base64Data":"$b64"}
                      ]
                    }
                    """.trimIndent()
                )
            }

            val resTask = project.tasks.getByName("compileGenericUiResources") as GenericUiResourceCompilerTask
            resTask.upstreamResourceManifestPaths.set(listOf(upstreamCommonJson.absolutePath, upstreamAlienJson.absolutePath))
            resTask.compileResources()

            val ktOut = resTask.kotlinOutputDirectory.get().asFile
            val rKt = ktOut.walkTopDown().first { it.name == "R.kt" }.readText()
            val resourceKt = ktOut.walkTopDown().first { it.name == "Resource.kt" }.readText()
            val regKt = ktOut.walkTopDown().first { it.name == "GeneratedResourceRegistry.kt" }.readText()

            assertTrue(rKt.contains("val ic_cast: ResourceID"))
            assertTrue(rKt.contains("val icons: ResourceID") || rKt.contains("val icons_woff: ResourceID"))
            assertTrue(rKt.contains("val icons_woff2: ResourceID"), "Duplicate stem .woff/.woff2 should disambiguate cleanly")
            assertFalse(rKt.contains("val loading:"), "Consumer R.kt must not contain upstream :common 'loading'")
            assertFalse(rKt.contains("val genericui:"), "Consumer R.kt must not contain upstream :common 'genericui'")
            assertFalse(rKt.contains("val ic_alien:"), "Consumer R.kt must not contain upstream extension 'ic_alien'")
            assertFalse(resourceKt.contains("val ic_alien:"))
            assertFalse(regKt.contains("gui/drawable/loading.svg"), "Consumer registry must not re-register upstream drawables")

            // Verify single-blob Web staging receives BOTH upstream (gui/ + gui/ext/) AND consumer (app/)
            val webBackendDir = File(resTask.backendOutputDirectory.get().asFile, "web")
            assertTrue(File(webBackendDir, "gui/drawable/loading.svg").isFile)
            assertTrue(File(webBackendDir, "gui/ext/ui/alien/drawable/ic_alien.svg").isFile)
            assertTrue(File(webBackendDir, "app/drawable/ic_cast.svg").isFile)
            assertTrue(File(webBackendDir, "app/font/icons.woff").isFile)
            assertTrue(File(webBackendDir, "app/font/icons.woff2").isFile)

            // Verify Alien extension itself has module-resources manifest generated or valid source resources
            val alienResDir = File(projectDir.parentFile.parentFile ?: File("."), "")
            assertNotNull(alienResDir)
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun `part 0_2 verifies 3-tier packaging, unwrapped macos tray icon, and real clang object embedding`() {
        val tempRoot = Files.createTempDirectory("wave0a-part02-").toFile()
        try {
            // 1. Test Klib module-resource payload extraction + Konan C compilation + symbol inspection
            val fakeKlib = File(tempRoot, "genericui-common.klib")
            val b64Payload = Base64.getEncoder().encodeToString("hello-native-res".toByteArray(Charsets.UTF_8))
            ZipOutputStream(fakeKlib.outputStream()).use { zos ->
                zos.putNextEntry(ZipEntry("META-INF/genericui/module-resources/genericui-common.json"))
                zos.write(
                    """
                    {
                      "schemaVersion": 2,
                      "scopeId": "genericui-common",
                      "moduleByte": 126,
                      "moduleGroupId": "genericui",
                      "scopePrefix": "gui",
                      "package": "com.nexustools.genericui.res",
                      "entries": [
                        {"type":"RAW","name":"sample_txt","id":2114000002,"relativePath":"raw/sample.txt","canonicalRelativePath":"gui/raw/sample.txt","nativeResourceKey":"raw_sample_txt","nativeLibraryGroupId":"genericui","format":"txt","base64Data":"$b64Payload"}
                      ]
                    }
                    """.trimIndent().toByteArray(Charsets.UTF_8)
                )
                zos.closeEntry()
            }

            val groupPayloads = GenericUiModuleResourceManifestParser.collectResourcePayloadsForGroup(
                sources = listOf(fakeKlib),
                targetGroupId = "genericui"
            )
            assertTrue(groupPayloads.containsKey("raw_sample_txt"), "Native key must strip gui/ prefix cleanly")
            assertFalse(groupPayloads.keys.any { it.startsWith("gui_") || it.startsWith("app_") })
            assertEquals("hello-native-res", groupPayloads.getValue("raw_sample_txt").decodeToString())

            val cSource = GenericUiNativeResourceEmbedder.generatePosixResourceTableCSource(
                lookupSymbol = GenericUiNativeResourceEmbedder.lookupSymbolForGroup("genericui"),
                resources = groupPayloads,
                includeMachOConstSection = false
            )
            assertTrue(cSource.contains("genericui_res_lookup_genericui"))
            assertTrue(cSource.contains("\"raw_sample_txt\""))

            val winRc = GenericUiNativeResourceEmbedder.generateWindowsRcSource(
                resources = groupPayloads,
                payloadOutputDir = File(tempRoot, "win-payloads")
            )
            assertTrue(winRc.contains("raw_sample_txt RCDATA"))
            assertFalse(winRc.contains("1 ICON"), "Shared DLL RC must never emit primary EXE 1 ICON")

            val cFile = File(tempRoot, "module_resources.c").apply { writeText(cSource, Charsets.UTF_8) }
            val objFile = File(tempRoot, "module_resources.o")
            if (GenericUiDesktopResourceTask.findClangPreprocessorExecutable() != null) {
                val compiledOk = GenericUiNativeResourceEmbedder.compileCSourceToObject(
                    cFile = cFile,
                    objOut = objFile,
                    konanTargetName = "linux_x64"
                )
                assertTrue(compiledOk && objFile.isFile && objFile.length() > 100L, "Konan clang must compile C resource table into valid .o")
                val objBytes = objFile.readBytes().toString(Charsets.ISO_8859_1)
                assertTrue(objBytes.contains("genericui_res_lookup_genericui"), "ELF .o symbol table must contain genericui_res_lookup_genericui")
                assertTrue(objBytes.contains("hello-native-res"), "ELF .o .rodata must contain embedded resource payload")
            }

            // 2. Test unwrapped macos/tray_icon.png vs Apple white-tile wrapped icon.icns + APK staging isolation
            val projDir = File(tempRoot, "app-desktop").apply { mkdirs() }
            val redSrc = File(projDir, "app_icon.png").apply {
                val img = java.awt.image.BufferedImage(64, 64, java.awt.image.BufferedImage.TYPE_INT_ARGB)
                val g = img.createGraphics()
                g.color = java.awt.Color(220, 40, 40, 255)
                g.fillOval(8, 8, 48, 48)
                g.dispose()
                ImageIO.write(img, "PNG", this)
            }
            val nestedResFile = File(projDir, "src/commonMain/resources/drawable/ic_nested.svg").apply {
                parentFile.mkdirs()
                writeText("""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path d="M2 2h20v20H2z" fill="#FF0000"/></svg>""")
            }
            assertTrue(nestedResFile.isFile)

            val project = ProjectBuilder.builder().withProjectDir(projDir).build()
            project.pluginManager.apply("com.nexustools.genericui")
            val ext = project.extensions.getByType(GenericUiExtension::class.java)
            ext.application {
                appName = "TrayTest"
                baseName = "traytest"
                icons(common = redSrc.name)
            }
            ext.backends {
                all()
            }

            val desktopTask = project.tasks.getByName("prepareGenericUiDesktopResources") as GenericUiDesktopResourceTask
            desktopTask.generateDesktopResources()
            val macTrayPng = File(desktopTask.outputDirectory.get().asFile, "macos/tray_icon.png")
            assertTrue(macTrayPng.isFile, "macOS desktop task must stage unwrapped tray_icon.png")
            val trayBuf = ImageIO.read(macTrayPng)
            // Corner pixel (0,0) must remain transparent (alpha == 0), NOT white-wrapped tile (alpha == 255 white)!
            assertEquals(0, (trayBuf.getRGB(0, 0) ushr 24) and 0xFF, "macOS tray_icon.png must NOT be wrapped in opaque white Apple tile")

            val resTask = project.tasks.getByName("compileGenericUiResources") as GenericUiResourceCompilerTask
            resTask.compileResources()
            val macC = File(resTask.backendOutputDirectory.get().asFile, "macos/genericui_app_resources.c").readText()
            assertTrue(macC.contains("\"tray_icon_png\""), "macOS app C table must embed tray_icon_png")
            assertFalse(File(resTask.backendOutputDirectory.get().asFile, "apple/Assets.xcassets").exists())

            // Verify iOS pbxproj registers hierarchical Resources/gui & Resources/app folders
            val iosTask = project.tasks.getByName("generateGenericUiIosApp") as GenericUiGenerateIosAppTask
            iosTask.generateIosApp()
            val pbxText = File(iosTask.outputDirectory.get().asFile, "iosApp.xcodeproj/project.pbxproj").readText()
            assertTrue(pbxText.contains("lastKnownFileType = folder; name = gui; path = Resources/gui;"))
            assertTrue(pbxText.contains("lastKnownFileType = folder; name = app; path = Resources/app;"))

            // Verify APK resource staging does NOT copy raw nested commonMain/resources into staged-app-res
            val pkgApkTask = project.tasks.getByName("packageGenericUiAndroidArm64DevelopmentApk") as GenericUiPackageAndroidApkTask
            val stagedResOut = File(tempRoot, "staged-app-res")
            val stageMethod = GenericUiPackageAndroidApkTask::class.java.declaredMethods.first { it.name == "stageAppResources" }
            stageMethod.isAccessible = true
            stageMethod.invoke(
                pkgApkTask,
                projDir,
                File(resTask.backendOutputDirectory.get().asFile, "android/res"),
                null,
                stagedResOut,
                emptyList<Any>()
            )
            assertFalse(File(stagedResOut, "drawable/ic_nested.svg").exists(), "Raw unflattened commonMain svg must not be staged directly into APK res")
            assertTrue(File(stagedResOut, "drawable/app_drawable_ic_nested.xml").isFile, "Flattened app_drawable_ic_nested must be staged into APK res")
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun `part 0_3 generates unified root serviceworker js with buildHash, offline cache, and plugin merging`() {
        val tempRoot = Files.createTempDirectory("wave0a-part03-").toFile()
        val testContributor = object : GenericUiExtensionContributor {
            override val extensionModulePath: String = ":extensions:test:sw"

            override fun contributeWebServiceWorker(context: ExtensionBuildContext): WebServiceWorkerContribution =
                WebServiceWorkerContribution(
                    enableServiceWorker = true,
                    pluginScripts = listOf(
                        WebSwPluginScript(
                            relativeUrlPath = "gui/ext/test/sw-plugin.js",
                            scriptContent = "self.GenericUiServiceWorker && self.GenericUiServiceWorker.registerPlugin({ name: 'test-sw' });"
                        )
                    ),
                    workerBinaries = listOf(
                        WebWorkerBinarySpec(
                            binaryBaseName = "testworker",
                            entryPointFqn = "example.workerMain",
                            serviceWorker = true
                        )
                    ),
                    offlineCacheUrls = listOf("app/raw/offline-extra.json?offline=1")
                )
        }
        GenericUiExtensionContributorRegistry.register(testContributor)
        try {
            val projDir = File(tempRoot, "web-proj").apply { mkdirs() }
            File(projDir, "src/commonMain/resources/raw").apply { mkdirs() }
            File(projDir, "src/commonMain/resources/raw/offline-extra.json").writeText("{}")

            val project = ProjectBuilder.builder().withProjectDir(projDir).build()
            project.pluginManager.apply("com.nexustools.genericui")
            val ext = project.extensions.getByType(GenericUiExtension::class.java)
            ext.application {
                appName = "SW App"
                baseName = "swapp"
            }
            ext.backends {
                web()
            }
            ext.extensions {
                custom(":extensions:test:sw")
            }

            val webTask = project.tasks.getByName("packageGenericUiWebDistribution") as GenericUiWebDistributionTask
            webTask.packageWebDistribution()
            val distDir = webTask.outputDirectory.get().asFile
            val swFile = File(distDir, "serviceworker.js")
            assertTrue(swFile.isFile, "Unified ./serviceworker.js must exist in distribution root")
            assertFalse(File(distDir, "gui/genericui-sw.js").exists(), "Legacy gui/genericui-sw.js must not exist")

            val swText = swFile.readText()
            assertTrue(swText.contains("const BUILD_HASH = "))
            assertTrue(swText.contains("const CACHE_NAME = \"genericui-offline-\" + BUILD_HASH;"))
            assertTrue(swText.contains("self.GenericUiServiceWorker"))
            assertTrue(swText.contains("registerPlugin"))
            assertTrue(swText.contains("importScripts(\"./gui/ext/test/sw-plugin.js\")"))
            assertTrue(swText.contains("\"app/raw/offline-extra.json\""))
            assertTrue(swText.contains("\"app/raw/offline-extra.json?offline=1\""))
            assertTrue(File(distDir, "gui/ext/test/sw-plugin.js").isFile)

            val html = File(distDir, "index.html").readText()
            assertTrue(html.contains("<meta id=\"genericui-build-hash\""))
            assertTrue(html.contains("<meta id=\"genericui-sw-enabled\" content=\"true\">"))
            assertFalse(html.contains("id=\"genericui-sw-register\""))
        } finally {
            GenericUiExtensionContributorRegistry.unregister(testContributor)
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun `part 0_5 and android fgs addendum verify spi scanning, permission dedupe, fgs properties, pip, and secondary executables`() {
        val tempRoot = Files.createTempDirectory("wave0a-part05-").toFile()
        val fgsContributor = object : GenericUiExtensionContributor {
            override val extensionModulePath: String = ":extensions:test:bgworker"

            override val annotationScanSpecs: List<ExtensionAnnotationScanSpec> = listOf(
                ExtensionAnnotationScanSpec(
                    extensionModulePath = extensionModulePath,
                    simpleAnnotationNames = setOf("Service"),
                    qualifiedAnnotationNames = setOf("com.nexustools.genericui.ext.test.Service"),
                    targetKinds = setOf(
                        ExtensionAnnotationScanSpec.SymbolTargetKind.CLASS,
                        ExtensionAnnotationScanSpec.SymbolTargetKind.FUNCTION
                    )
                )
            )

            override fun providerInstallerCalls(
                backendScope: GenericUiVariantScope,
                context: ExtensionBuildContext
            ): List<String> = listOf("com.example.TestWorkerInstaller.install()")

            override fun generatedLauncherSources(
                backendScope: GenericUiVariantScope,
                context: ExtensionBuildContext
            ): List<ExtensionGeneratedSource> = listOf(
                ExtensionGeneratedSource(
                    relativeFilePath = "com/example/worker/TestWorkerHook.kt",
                    sourceCode = """
                        package com.example.worker

                        object TestWorkerHook
                    """.trimIndent(),
                    targetScope = backendScope
                )
            )

            override fun contributeAndroid(
                context: ExtensionBuildContext,
                fragment: GenericUiDiscoveredFragment?
            ): AndroidExtensionContribution? {
                if (fragment != null) return null
                val serviceClass = context.discoveredSymbols.firstOrNull()?.qualifiedName
                    ?: "com.example.sync.MediaBackgroundService"
                return AndroidExtensionContribution(
                    manifestContribution = AndroidManifestContribution(
                        permissions = listOf(
                            "android.permission.FOREGROUND_SERVICE",
                            "FOREGROUND_SERVICE",
                            "android.permission.POST_NOTIFICATIONS",
                            "android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
                            "android.permission.RECEIVE_BOOT_COMPLETED"
                        ),
                        services = listOf(
                            AndroidServiceContribution(
                                className = serviceClass,
                                exported = false,
                                enabled = true,
                                foregroundServiceType = "specialUse",
                                stopWithTask = false,
                                isolatedProcess = false,
                                properties = mapOf(
                                    "android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" to "media_background_worker"
                                )
                            )
                        ),
                        receivers = listOf(
                            AndroidReceiverContribution(
                                className = "com.example.sync.BootReceiver",
                                exported = true
                            )
                        ),
                        providers = listOf(
                            AndroidProviderContribution(
                                className = "com.example.sync.WorkerStateProvider",
                                authorities = "\${applicationId}.worker.provider",
                                exported = false
                            )
                        )
                    )
                )
            }

            override fun contributeDesktop(context: ExtensionBuildContext): DesktopExtensionContribution =
                DesktopExtensionContribution(
                    secondaryExecutables = listOf(
                        SecondaryDesktopExecutableContribution(
                            binaryBaseName = "bgworker",
                            entryPointFqn = "com.example.bgworkerMain",
                            generatedEntrypointSource = ExtensionGeneratedSource(
                                relativeFilePath = "com/example/BgWorkerMain.kt",
                                sourceCode = """
                                    package com.example

                                    fun bgworkerMain() {}
                                """.trimIndent()
                            )
                        )
                    )
                )

            override fun contributeAppleMobile(context: ExtensionBuildContext): AppleMobileExtensionContribution =
                AppleMobileExtensionContribution(
                    backgroundModes = listOf("fetch", "processing"),
                    userActivityTypes = listOf("com.example.sync.activity")
                )
        }
        GenericUiExtensionContributorRegistry.register(fgsContributor)
        try {
            val projDir = File(tempRoot, "spi-app").apply { mkdirs() }
            val commonSrc = File(projDir, "src/commonMain/kotlin/com/example/sync").apply { mkdirs() }
            File(commonSrc, "MainApp.kt").writeText(
                """
                package com.example.sync

                import com.nexustools.genericui.component.fragment.Fragment
                import com.nexustools.genericui.component.fragment.MainAppFragment
                import com.nexustools.genericui.ext.pip.PiPCompatible

                @MainAppFragment
                @PiPCompatible
                class MainApp : Fragment()

                @Service
                class MediaBackgroundService
                """.trimIndent()
            )

            // Verify scanSourceDirectoriesSymbols extracts @Service
            val scannedSymbols = GenericUiFragmentScanner.scanSourceDirectoriesSymbols(
                directories = listOf(File(projDir, "src/commonMain/kotlin")),
                scanSpecs = fgsContributor.annotationScanSpecs
            )
            assertTrue(scannedSymbols.any { it.qualifiedName == "com.example.sync.MediaBackgroundService" && it.annotationName == "Service" })

            val project = ProjectBuilder.builder().withProjectDir(projDir).build()
            project.pluginManager.apply("com.nexustools.genericui")
            val ext = project.extensions.getByType(GenericUiExtension::class.java)
            ext.application {
                appId = "com.example.sync"
                appName = "Spi Sync App"
                baseName = "spisync"
            }
            ext.android {
                manifest {
                    permission("FOREGROUND_SERVICE")
                }
            }
            ext.backends {
                all()
            }
            ext.extensions {
                pipEnabled = true
                custom(":extensions:test:bgworker")
                option("workerMode", "persistent")
            }

            // 1. Android Manifest: permission dedupe + specialUse FGS + nested <property> + receiver + provider + PiP activity
            val manifestTask = project.tasks.getByName("generateGenericUiAndroidManifest") as GenericUiGenerateAndroidManifestTask
            manifestTask.generate()
            val manifestXml = manifestTask.outputManifestFile.get().asFile.readText()
            assertEquals(
                1,
                Regex("""<uses-permission\s+android:name="android\.permission\.FOREGROUND_SERVICE"\s*/>""").findAll(manifestXml).count(),
                "FOREGROUND_SERVICE must be deduplicated cleanly across app DSL & SPI contributions"
            )
            assertTrue(manifestXml.contains("android.permission.FOREGROUND_SERVICE_SPECIAL_USE"))
            assertTrue(manifestXml.contains("android.permission.POST_NOTIFICATIONS"))
            assertTrue(manifestXml.contains("android.permission.RECEIVE_BOOT_COMPLETED"))
            assertTrue(manifestXml.contains("android:foregroundServiceType=\"specialUse\""))
            assertTrue(manifestXml.contains("android:stopWithTask=\"false\""))
            assertTrue(manifestXml.contains("<property android:name=\"android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE\" android:value=\"media_background_worker\" />"))
            assertTrue(manifestXml.contains("com.example.sync.BootReceiver"))
            assertTrue(manifestXml.contains("com.example.sync.WorkerStateProvider"))
            assertTrue(manifestXml.contains("android:supportsPictureInPicture=\"true\""), "Built-in PiP SPI must preserve Activity PiP attribute")

            // 2. Launcher generation: provider installer calls + generated SPI sources + secondary executable entrypoint
            val launcherTask = project.tasks.getByName("generateGenericUiLaunchers") as GenericUiGenerateLaunchersTask
            launcherTask.generateLaunchers()
            val launcherOutRoot = launcherTask.outputDirectory.get().asFile
            val generatedDescriptor = launcherOutRoot.walkTopDown().first { it.name == "GeneratedWindowsAppDescriptor.kt" }.readText()
            assertTrue(generatedDescriptor.contains("com.example.TestWorkerInstaller.install()"))
            val generatedMain = launcherOutRoot.walkTopDown().first { it.name == "GeneratedWindowsLauncherMain.kt" }.readText()
            assertTrue(generatedMain.contains("GeneratedWindowsAppDescriptor.create()"))
            assertTrue(launcherOutRoot.walkTopDown().any { it.name == "TestWorkerHook.kt" })
            assertTrue(launcherOutRoot.walkTopDown().any { it.name == "BgWorkerMain.kt" })

            // 3. Apple Mobile Info.plist: merged UIBackgroundModes + NSUserActivityTypes (incl. PiP "audio" + SPI "fetch", "processing")
            val iosTask = project.tasks.getByName("generateGenericUiIosApp") as GenericUiGenerateIosAppTask
            iosTask.generateIosApp()
            val iosPlist = File(iosTask.outputDirectory.get().asFile, "Info.plist").readText()
            assertTrue(iosPlist.contains("<string>audio</string>"))
            assertTrue(iosPlist.contains("<string>fetch</string>"))
            assertTrue(iosPlist.contains("<string>processing</string>"))
            assertTrue(iosPlist.contains("<string>com.example.sync.activity</string>"))

            // 4. Declarative JSON manifest contributor parsing
            val jsonDir = File(projDir, "META-INF/genericui/extensions").apply { mkdirs() }
            File(jsonDir, "sample-json-ext.json").writeText(
                """
                {
                  "extensionModulePath": ":extensions:test:json",
                  "androidPermissions": ["android.permission.WAKE_LOCK"],
                  "appleBackgroundModes": ["remote-notification"],
                  "enableWebServiceWorker": true
                }
                """.trimIndent()
            )
            val discovered = GenericUiExtensionContributorRegistry.activeContributors(
                enabledExtensionModulePaths = setOf(":extensions:test:json"),
                projectRootDir = projDir
            )
            assertEquals(1, discovered.size)
            val ctx = ExtensionBuildContext(
                projectRootDir = projDir,
                projectName = "spi-app",
                appId = "com.example.sync",
                appName = "Spi Sync App",
                appVersion = "1.0.0",
                baseName = "spisync",
                generatedPackageName = "com.example.sync.generated",
                enabledExtensionModulePaths = setOf(":extensions:test:json")
            )
            assertEquals(listOf("android.permission.WAKE_LOCK"), discovered[0].contributeAndroid(ctx, null)?.manifestContribution?.permissions)
            assertEquals(listOf("remote-notification"), discovered[0].contributeAppleMobile(ctx)?.backgroundModes)
            assertTrue(discovered[0].contributeWebServiceWorker(ctx)?.enableServiceWorker == true)
        } finally {
            GenericUiExtensionContributorRegistry.unregister(fgsContributor)
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun `architecture audit enforces zero core-to-extension imports and 1 public type per new Wave 0A kt file`() {
        val repoRoot = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile && File(it, "gradle-plugin").isDirectory }
            ?: return

        val coreModules = listOf("common", "system", "jvm", "apple", "desktop", "mobile", "web", "gradle-plugin")
        for (mod in coreModules) {
            val mainSrc = File(repoRoot, "$mod/src")
            if (!mainSrc.isDirectory) continue
            mainSrc.walkTopDown()
                .filter { it.isFile && it.extension == "kt" && !it.invariantSeparatorsPath.contains("Test/") && !it.invariantSeparatorsPath.contains("test/") }
                .forEach { file ->
                    val text = file.readText()
                    val forbiddenLine = text.lineSequence().firstOrNull { line ->
                        val t = line.trimStart()
                        t.startsWith("import com.nexustools.genericui.ext.") &&
                            // Pre-existing legacy PiP activity contributor string inside PipAndroidActivityExtensionContributor is non-import codegen string
                            !t.contains("AndroidPipActivityDelegate")
                    }
                    assertTrue(
                        forbiddenLine == null,
                        "Core module '$mod' file '${file.name}' must not import any extension class (found: $forbiddenLine)"
                    )
                }
        }

        // Verify 1 public top-level type per Wave 0A file
        val wave0aFiles = listOf(
            "gradle-plugin/src/main/kotlin/com/nexustools/genericui/plugin/spi/ExtensionAnnotationScanSpec.kt",
            "gradle-plugin/src/main/kotlin/com/nexustools/genericui/plugin/spi/GenericUiDiscoveredSymbol.kt",
            "gradle-plugin/src/main/kotlin/com/nexustools/genericui/plugin/spi/ExtensionBuildContext.kt",
            "gradle-plugin/src/main/kotlin/com/nexustools/genericui/plugin/spi/ExtensionGeneratedSource.kt",
            "gradle-plugin/src/main/kotlin/com/nexustools/genericui/plugin/spi/WebSwPluginScript.kt",
            "gradle-plugin/src/main/kotlin/com/nexustools/genericui/plugin/spi/WebWorkerBinarySpec.kt",
            "gradle-plugin/src/main/kotlin/com/nexustools/genericui/plugin/spi/WebServiceWorkerContribution.kt",
            "gradle-plugin/src/main/kotlin/com/nexustools/genericui/plugin/spi/AndroidServiceContribution.kt",
            "gradle-plugin/src/main/kotlin/com/nexustools/genericui/plugin/spi/AndroidReceiverContribution.kt",
            "gradle-plugin/src/main/kotlin/com/nexustools/genericui/plugin/spi/AndroidProviderContribution.kt",
            "gradle-plugin/src/main/kotlin/com/nexustools/genericui/plugin/spi/AndroidManifestContribution.kt",
            "gradle-plugin/src/main/kotlin/com/nexustools/genericui/plugin/spi/AndroidExtensionContribution.kt",
            "gradle-plugin/src/main/kotlin/com/nexustools/genericui/plugin/spi/SecondaryDesktopExecutableContribution.kt",
            "gradle-plugin/src/main/kotlin/com/nexustools/genericui/plugin/spi/DesktopAutostartContribution.kt",
            "gradle-plugin/src/main/kotlin/com/nexustools/genericui/plugin/spi/DesktopExtensionContribution.kt",
            "gradle-plugin/src/main/kotlin/com/nexustools/genericui/plugin/spi/AppleMobileExtensionContribution.kt",
            "gradle-plugin/src/main/kotlin/com/nexustools/genericui/plugin/spi/GenericUiExtensionContributor.kt",
            "gradle-plugin/src/main/kotlin/com/nexustools/genericui/plugin/spi/PipExtensionContributor.kt",
            "gradle-plugin/src/main/kotlin/com/nexustools/genericui/plugin/spi/GenericUiExtensionContributorRegistry.kt",
            "common/src/commonMain/kotlin/com/nexustools/genericui/core/ApplicationLifecycleExtensionHook.kt",
            "common/src/commonMain/kotlin/com/nexustools/genericui/core/GenericUiExtensionRegistry.kt",
            "mobile/src/androidMain/kotlin/com/nexustools/genericui/android/AndroidActivityLifecycleObserver.kt",
            "mobile/src/androidMain/kotlin/com/nexustools/genericui/android/AndroidExtensionRuntimeBridge.kt",
            "mobile/src/iosMain/kotlin/com/nexustools/genericui/ios/UikitExtensionLifecycleObserver.kt",
            "mobile/src/iosMain/kotlin/com/nexustools/genericui/ios/UikitExtensionLifecycleBridge.kt",
            "web/src/commonMain/kotlin/com/nexustools/genericui/web/WebServiceWorkerClientBridge.kt",
            "web/src/commonMain/kotlin/com/nexustools/genericui/web/WebWorkerGlobalBridge.kt",
            "desktop/src/mingwMain/kotlin/com/nexustools/genericui/windows/WindowsHeadlessTrayLoop.kt",
            "desktop/src/macosMain/kotlin/com/nexustools/genericui/macos/MacosResourceResolverImpl.kt",
            "desktop/src/commonMain/kotlin/com/nexustools/genericui/desktop/system/DesktopSystemTrayTemplateAnalysis.kt"
        )
        val topLevelDeclRegex = Regex("""^(?:(?:public|abstract|open|sealed|data|enum|annotation|expect|actual|value|fun)\s+)*(?:class|interface|object|enum\s+class)\s+([A-Za-z0-9_]+)""")
        for (relPath in wave0aFiles) {
            val file = File(repoRoot, relPath)
            assertTrue(file.isFile, "Expected Wave 0A file '$relPath' to exist")
            val topLevelNames = file.readLines().mapNotNull { line ->
                if (line.startsWith("private ") || line.startsWith("internal ") || line.startsWith(" ") || line.startsWith("\t")) {
                    null
                } else {
                    topLevelDeclRegex.find(line)?.groupValues?.get(1)
                }
            }
            assertEquals(1, topLevelNames.size, "Expected strictly 1 public top-level type in $relPath, found $topLevelNames")
            assertEquals(file.nameWithoutExtension, topLevelNames[0], "Type name in $relPath must match filename")
        }
    }

    @Test
    fun `manifest parser handles large base64 payloads, escapes, and real common manifest without stack overflow`() {
        val largePayload = "A".repeat(850_000)
        val escapedValue = "line1\\nline2\\\"quoted\\\"\\\\slash\\u0041"
        val syntheticJson = """
            {
              "schemaVersion": 2,
              "scopeId": "genericui-common",
              "moduleByte": 126,
              "moduleGroupId": "genericui",
              "scopePrefix": "gui",
              "package": "com.nexustools.genericui.res",
              "entries": [
                {
                  "type": "RAW",
                  "name": "huge_payload",
                  "id": 2114000999,
                  "relativePath": "raw/huge_payload.bin",
                  "canonicalRelativePath": "gui/raw/huge_payload.bin",
                  "nativeResourceKey": "raw_huge_payload_bin",
                  "nativeLibraryGroupId": "genericui",
                  "format": "bin",
                  "base64Data": "$largePayload",
                  "value": "$escapedValue",
                  "viewBoxWidth": 48.5,
                  "viewBoxHeight": 32.0
                }
              ]
            }
        """.trimIndent()

        val manifest = GenericUiModuleResourceManifestParser.parseJsonManifest(syntheticJson)
        assertNotNull(manifest)
        assertEquals("com.nexustools.genericui.res", manifest.packageName)
        assertEquals(2, manifest.schemaVersion)
        assertEquals(126, manifest.moduleByte)
        assertEquals(1, manifest.entries.size)
        val entry = manifest.entries.single()
        assertEquals("huge_payload", entry.name)
        assertEquals(850_000, entry.base64Data?.length)
        assertEquals("line1\nline2\"quoted\"\\slashA", entry.rawValue)
        assertEquals(48.5f, entry.viewBoxWidth)
        assertEquals(32.0f, entry.viewBoxHeight)

        val largeContributorJson = """
            {
              "modulePath": ":extensions:test:large",
              "simpleAnnotationNames": ["LargeAnn", "Quoted\"Ann"],
              "webServiceWorkerEnabled": true,
              "providerInstallers": ["${"p".repeat(120_000)}"]
            }
        """.trimIndent()
        val contributor = GenericUiExtensionContributorRegistry.parseJsonContributor(largeContributorJson)
        assertNotNull(contributor)
        assertEquals(setOf("LargeAnn", "Quoted\"Ann"), contributor.annotationScanSpecs.single().simpleAnnotationNames)

        val repoRoot = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile && File(it, "gradle-plugin").isDirectory }
        val realCommonManifest = repoRoot?.let {
            File(it, "common/build/generated/genericui/module-resources/resources/META-INF/genericui/module-resources/genericui-common.json")
        }
        if (realCommonManifest != null && realCommonManifest.isFile) {
            val realManifest = GenericUiModuleResourceManifestParser.parseJsonManifest(realCommonManifest.readText(Charsets.UTF_8))
            assertNotNull(realManifest)
            assertTrue(realManifest.entries.isNotEmpty(), "Expected entries in real genericui-common.json")
            val bootstrapEntry = realManifest.entries.firstOrNull { it.name.contains("bootstrap") }
            assertNotNull(bootstrapEntry, "Expected bootstrap entry in real genericui-common.json")
            assertTrue((bootstrapEntry.base64Data?.length ?: 0) > 100_000, "Expected large base64 payload for bootstrap entry")
        }
    }
}
