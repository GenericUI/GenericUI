package com.nexustools.genericui.plugin

import org.gradle.testfixtures.ProjectBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GenericUiGradlePluginTest {

    @Test
    fun `plugin registers genericui extension and resolves transitive extension modules`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("java")
        project.pluginManager.apply("com.nexustools.genericui")

        val ext = project.extensions.findByType(GenericUiExtension::class.java)
        assertNotNull(ext, "genericui extension should be registered")

        ext.backends {
            windows()
            gtk()
            qt()
            android()
            ios()
            swing {
                useLwjglGL = true
                useVulkan = true
                targetAllDesktopClassifiers()
            }
            web()
        }
        ext.graphics {
            bundleAngleOnWindows = true
            bundleMoltenVkOnApple = true
            bundleShaderc = true
            bundleVlcOnDesktop = true
            enableVlcHdrToneMapping = true
            enableVlcHardwareDecode = true
        }
        ext.extensions {
            imageProcessing() // transitively enables gpu
            media()           // self-contained media extension (libvlc / libvlcj / native components)
            webView()         // Project-of-Projects :extensions:webview
            aiChat()          // transitively enables codeEditor -> canvas2d
            box2d()           // transitively enables canvas2d + canvas3d -> gpu
            box3d()           // transitively enables canvas3d + threeJs -> gpu
        }
        ext.styles {
            compileSass = true
            minify = true
        }

        assertTrue(ext.backends.windowsEnabled)
        assertTrue(ext.backends.requiresSharedDesktopModule)
        assertTrue(ext.backends.requiresSharedMobileModule)
        assertTrue(ext.backends.enabledBackendModulePaths().containsAll(
            listOf(
                ":systemCommon",
                ":desktop:common",
                ":desktop:nativeCommon",
                ":desktop:linuxCommon",
                ":mobile:common",
                ":desktop:windows",
                ":desktop:gtk",
                ":desktop:qt",
                ":mobile:android",
                ":mobile:ios",
                ":desktop:swing",
                ":web"
            )
        ))
        assertTrue(ext.backends.swingConfig.enabled)
        assertTrue(ext.backends.swingConfig.useLwjglGL)
        assertTrue(ext.backends.swingConfig.useVulkan)
        assertTrue(ext.backends.swingConfig.targetClassifiers.size == 8)
        assertTrue(ext.backends.swingConfig.targetClassifiers.all { it.isDesktop })
        assertTrue(ext.graphics.bundleVlcOnDesktop)
        assertTrue(ext.graphics.enableVlcHdrToneMapping)
        assertTrue(ext.graphics.enableVlcHardwareDecode)

        // Verify transitive extension resolution and :extensions:webview
        assertTrue(ext.extensions.gpuEnabled)
        assertTrue(ext.extensions.imageProcessingEnabled)
        assertTrue(ext.extensions.canvas2dEnabled)
        assertTrue(ext.extensions.canvas3dEnabled)
        assertTrue(ext.extensions.mediaEnabled)
        assertTrue(ext.extensions.webViewEnabled)
        assertTrue(ext.extensions.codeEditorEnabled)
        assertTrue(ext.extensions.aiChatEnabled)
        assertTrue(ext.extensions.threeJsEnabled)
        assertTrue(ext.extensions.box2dEnabled)
        assertTrue(ext.extensions.box3dEnabled)
        assertTrue(GenericUiGradlePlugin.PROJECT_OF_PROJECTS_EXTENSIONS.contains(":extensions:webview"))
        assertTrue(ext.extensions.enabledExtensionModulePaths().containsAll(
            listOf(
                ":extensions:gpu",
                ":extensions:imageprocessing",
                ":extensions:canvas2d",
                ":extensions:canvas3d",
                ":extensions:media",
                ":extensions:webview",
                ":extensions:codeeditor",
                ":extensions:aichat",
                ":extensions:threejs",
                ":extensions:box2d",
                ":extensions:box3d"
            )
        ))
        assertTrue(
            ext.extensions.providerInstallerCallsForBackend(GenericUiVariantScope.ANDROID)
                .contains("com.nexustools.genericui.android.AndroidWebViewExtensionProvider.install()")
        )
        assertTrue(
            ext.extensions.providerInstallerCallsForBackend(GenericUiVariantScope.IOS)
                .contains("com.nexustools.genericui.ios.IosWebViewExtensionProvider.install()")
        )
        assertTrue(
            ext.extensions.providerInstallerCallsForBackend(GenericUiVariantScope.WEB)
                .contains("com.nexustools.genericui.ext.webview.web.WebWebViewExtensionProvider.install()")
        )
        assertTrue(
            ext.extensions.providerInstallerCallsForBackend(GenericUiVariantScope.WINDOWS)
                .contains("com.nexustools.genericui.ext.webview.desktop.DesktopWebViewExtensionProvider.install()")
        )
        assertTrue(
            ext.extensions.providerInstallerCallsForBackend(GenericUiVariantScope.SWING)
                .contains("com.nexustools.genericui.ext.webview.swing.SwingWebViewExtensionProvider.install()")
        )

        assertNotNull(project.tasks.findByName("bundleGenericUiAngle"))
        assertNotNull(project.tasks.findByName("bundleGenericUiVulkan"))
        assertNotNull(project.tasks.findByName("compileGenericUiSass"))
        assertNotNull(project.tasks.findByName("printGenericUiConfig"))
    }

    @Test
    fun `presets allExceptSwing and allDesktopExceptSwing and composite build coordinates work`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("com.nexustools.genericui")
        val ext = project.extensions.getByType(GenericUiExtension::class.java)

        ext.backends.allExceptSwing()
        assertTrue(ext.backends.windowsEnabled)
        assertTrue(ext.backends.macosEnabled)
        assertTrue(ext.backends.gtkEnabled)
        assertTrue(ext.backends.qtEnabled)
        assertTrue(ext.backends.androidEnabled)
        assertTrue(ext.backends.iosEnabled)
        assertTrue(ext.backends.webEnabled)
        assertTrue(!ext.backends.swingConfig.enabled)

        ext.backends.allNativeDesktop()
        assertTrue(ext.backends.windowsEnabled)
        assertTrue(ext.backends.macosEnabled)
        assertTrue(ext.backends.gtkEnabled)
        assertTrue(ext.backends.qtEnabled)
        assertTrue(!ext.backends.swingConfig.enabled)
        assertTrue(!ext.backends.androidEnabled)
        assertTrue(!ext.backends.iosEnabled)
        assertTrue(!ext.backends.webEnabled)

        kotlin.test.assertEquals(
            "com.nexustools.genericui:common:0.1.0-SNAPSHOT",
            GenericUiGradlePlugin.toMavenCoordinate(":common")
        )
        kotlin.test.assertEquals(
            "com.nexustools.genericui.desktop:windows:0.1.0-SNAPSHOT",
            GenericUiGradlePlugin.toMavenCoordinate(":desktop:windows")
        )
        kotlin.test.assertEquals(
            "com.nexustools.genericui.extensions.canvas2d:common:0.1.0-SNAPSHOT",
            GenericUiGradlePlugin.toMavenCoordinate(":extensions:canvas2d:common")
        )
        kotlin.test.assertEquals(
            "com.nexustools.genericui.extensions:threejs:0.1.0-SNAPSHOT",
            GenericUiGradlePlugin.toMavenCoordinate(":extensions:threejs")
        )

        // Verify precompiling SASS into Kotlin (.kt) PrecompiledStyleSheet source
        val sassTask = project.tasks.getByName("compileGenericUiSass") as GenericUiSassCompileTask
        val css = sassTask.transformSassToCss(
            """
            ${'$'}primary: #713b15;
            .page {
                background-color: ${'$'}primary;
                .menu {
                    color: #ffffff;
                    &:hover {
                        opacity: 0.9;
                    }
                }
            }
            """.trimIndent(),
            minifyOutput = false
        )
        val generatedKt = sassTask.generatePrecompiledKotlinSource(
            cssText = css,
            packageName = "com.example.styles",
            objectName = "StylePrecompiledStyleSheet",
            stylesheetName = "style"
        )
        assertTrue(generatedKt.contains("package com.example.styles"))
        assertTrue(generatedKt.contains("object StylePrecompiledStyleSheet : BlinkStyleSheet()"))
        assertTrue(generatedKt.contains("override val cssResourcePath: String = \"style.css\""))
        assertTrue(generatedKt.contains("override val rawCss: String = \"\""))
        assertTrue(generatedKt.contains("override val compiledRules: List<CompiledStyleRule>"))
        assertTrue(generatedKt.contains("override val ruleSet: BlinkRuleBucketMap = BlinkRuleBucketMap.fromCompiledStyleRules(compiledRules)"))
        assertTrue(generatedKt.contains("CompiledStyleRule("))
        assertTrue(generatedKt.contains("CompiledSelectorSegment("))
        assertTrue(generatedKt.contains("ComponentStyle().apply"))
        assertTrue(generatedKt.contains("BlinkStyleEngine.registerStyleSheet(this)"))
        assertTrue(generatedKt.contains("StyleSheetRegistry.register(name, cssResourcePath, this)"))
        assertTrue(generatedKt.contains("fun install(app: IApplication)"))
        assertTrue(generatedKt.contains("attachTo(app)"))
        assertTrue(generatedKt.contains("selector = \".page\""))
        assertTrue(generatedKt.contains("selector = \".page .menu\""))
        assertTrue(generatedKt.contains("selector = \".page .menu:hover\""))
        assertTrue(generatedKt.contains("setBackgroundColor(RgbaColor(113, 59, 21, 255), false)"))
        assertFalse(generatedKt.contains("setRawDeclaration"))
    }

    @Test
    fun `compileGenericUiSass generates css files from compiled sass for the web target`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("com.nexustools.genericui")
        val ext = project.extensions.getByType(GenericUiExtension::class.java)
        ext.backends {
            web()
        }
        ext.styles {
            compileSass = true
            generateWebCss = true
            webCssBundleFileName = "bundle.css"
            sourceDir = "src/commonMain/resources"
        }

        val resourcesDir = java.io.File(project.projectDir, "src/commonMain/resources")
        val stylesSubDir = java.io.File(resourcesDir, "styles")
        val valuesSubDir = java.io.File(resourcesDir, "values")
        val drawableSubDir = java.io.File(resourcesDir, "drawable")
        stylesSubDir.mkdirs()
        valuesSubDir.mkdirs()
        drawableSubDir.mkdirs()

        java.io.File(valuesSubDir, "colors.xml").writeText(
            """
            <resources>
                <color name="brand_primary">#2563eb</color>
                <dimen name="card_pad">16dp</dimen>
            </resources>
            """.trimIndent()
        )
        java.io.File(drawableSubDir, "hero_bg.svg").writeText("<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>")

        java.io.File(resourcesDir, "_tokens.scss").writeText("\$brand-bg: #112233;\n")
        java.io.File(resourcesDir, "style.scss").writeText(
            """
            @import "tokens";
            body {
                background: ${'$'}brand-bg;
                .hero {
                    color: @color/brand_primary;
                    background-image: url("@drawable/hero_bg");
                    padding: @dimen/card_pad;
                }
            }
            """.trimIndent()
        )
        java.io.File(stylesSubDir, "app.scss").writeText(
            """
            ${'$'}accent: #ff6600;
            genericui:app {
                .card {
                    border-color: ${'$'}accent;
                }
            }
            """.trimIndent()
        )

        val sassTask = project.tasks.getByName("compileGenericUiSass") as GenericUiSassCompileTask
        sassTask.sourceDirectory.set(resourcesDir)
        sassTask.compileStyles()

        val webCssDir = sassTask.webCssOutputDirectory.get().asFile
        val rootStyleCss = java.io.File(webCssDir, "style.css")
        val nestedAppCss = java.io.File(webCssDir, "styles/app.css")
        val rootAppCss = java.io.File(webCssDir, "app.css")
        val bundleCss = java.io.File(webCssDir, "bundle.css")

        assertTrue(rootStyleCss.exists(), "Web target style.css should be generated from style.scss")
        assertTrue(nestedAppCss.exists(), "Web target styles/app.css should be generated from styles/app.scss")
        assertTrue(rootAppCss.exists(), "Web target root app.css convenience copy should be generated")
        assertTrue(bundleCss.exists(), "Web target bundle.css should be generated")

        val styleCssContent = rootStyleCss.readText()
        assertTrue(styleCssContent.contains("#112233"), "Compiled web style.css should resolve imported SASS variables")
        assertTrue(styleCssContent.contains("#2563eb"), "Compiled web style.css should resolve @color/brand_primary")
        assertTrue(styleCssContent.contains("assets/drawable/hero_bg.svg"), "Compiled web style.css should resolve @drawable/hero_bg to web asset URL")
        assertTrue(styleCssContent.contains("16px"), "Compiled web style.css should resolve @dimen/card_pad")
        assertTrue(styleCssContent.contains("body .hero"), "Compiled web style.css should flatten nested SASS selectors")
        assertTrue(rootAppCss.readText().contains("#ff6600"), "Compiled web app.css should resolve SASS variables")
        assertTrue(rootAppCss.readText().contains("genericui\\:app .card"), "Compiled web app.css should escape genericui:<tag> selectors")
    }

    @Test
    fun `multiplatform configurator creates genericui sourcesets and development production target tasks`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("com.nexustools.genericui")
        val ext = project.extensions.getByType(GenericUiExtension::class.java)

        ext.application {
            baseName = "ExampleApp"
        }
        ext.backends {
            all()
        }
        ext.sourceSets {
            common {
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
            }
            windowsMain {
                kotlinSrcDir("src/windowsMain/kotlin")
            }
            macMain {
                kotlinSrcDir("src/macMain/kotlin")
            }
            gtkMain {
                kotlinSrcDir("src/gtkMain/kotlin")
            }
            qtMain {
                kotlinSrcDir("src/qtMain/kotlin")
            }
            webMain {
                kotlinSrcDir("src/webMain/kotlin")
            }
        }

        val kotlin = project.extensions.getByType(
            org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension::class.java
        )
        val sourceSetNames = kotlin.sourceSets.names
        val expectedSourceSets = listOf(
            "commonMain",
            "common",
            "commonTest",
            "web",
            "webMain",
            "webTest",
            "systemCommon",
            "systemMain",
            "systemTest",
            "desktopCommon",
            "desktopMain",
            "desktopTest",
            "swing",
            "swingMain",
            "swingTest",
            "desktopNativeCommon",
            "desktopNativeMain",
            "desktopNativeTest",
            "nativeDesktopCommon",
            "nativeDesktopMain",
            "nativeDesktopTest",
            "windows",
            "windowsMain",
            "windowsTest",
            "mac",
            "macMain",
            "macosMain",
            "macTest",
            "macosTest",
            "linuxCommon",
            "linuxMain",
            "linuxTest",
            "gtk",
            "gtkMain",
            "gtkTest",
            "qt",
            "qtMain",
            "qtTest",
            "mobileCommon",
            "mobileMain",
            "mobileTest",
            "android",
            "androidMain",
            "androidTest",
            "ios",
            "iosMain",
            "iosTest",
            "wasmJsMain",
            "jsMain"
        )
        for (expected in expectedSourceSets) {
            assertTrue(
                expected in sourceSetNames,
                "Expected GenericUI sourceSet '$expected' to be created in kotlin.sourceSets (found: $sourceSetNames)"
            )
        }

        // Verify the exact hierarchical dependsOn graph:
        // common -> web
        // common -> systemCommon -> desktopCommon -> swing
        //                        -> desktopCommon -> desktopNativeCommon -> (windows, mac, linuxCommon -> (gtk, qt))
        //                        -> mobileCommon -> (android, ios)
        val ss = kotlin.sourceSets
        assertTrue(ss.getByName("common") in ss.getByName("web").dependsOn)
        assertTrue(ss.getByName("common") in ss.getByName("systemCommon").dependsOn)
        assertTrue(ss.getByName("systemMain") in ss.getByName("desktopCommon").dependsOn)
        assertTrue(ss.getByName("systemMain") in ss.getByName("mobileCommon").dependsOn)
        assertTrue(ss.getByName("desktopMain") in ss.getByName("swing").dependsOn)
        assertTrue(ss.getByName("desktopMain") in ss.getByName("nativeDesktopCommon").dependsOn)
        assertTrue(ss.getByName("nativeDesktopMain") in ss.getByName("linuxCommon").dependsOn)
        assertTrue(ss.getByName("linuxCommon") in ss.getByName("linuxMain").dependsOn)
        assertTrue(ss.getByName("nativeDesktopTest") in ss.getByName("linuxTest").dependsOn)
        assertTrue(ss.getByName("linuxMain") in ss.getByName("gtk").dependsOn)
        assertTrue(ss.getByName("linuxMain") in ss.getByName("qt").dependsOn)
        assertTrue(ss.getByName("linuxTest") in ss.getByName("gtkTest").dependsOn)
        assertTrue(ss.getByName("linuxTest") in ss.getByName("qtTest").dependsOn)
        assertTrue(ss.getByName("nativeDesktopMain") in ss.getByName("windows").dependsOn)
        assertTrue(ss.getByName("nativeDesktopMain") in ss.getByName("mac").dependsOn)
        assertTrue(ss.getByName("mobileMain") in ss.getByName("android").dependsOn)
        assertTrue(ss.getByName("mobileMain") in ss.getByName("ios").dependsOn)

        val targetNames = kotlin.targets.names
        assertTrue("macosArm64" in targetNames, "Expected macosArm64 target to be registered")
        assertTrue("iosArm64" in targetNames, "Expected iosArm64 target to be registered")
        assertTrue("iosSimulatorArm64" in targetNames, "Expected iosSimulatorArm64 target to be registered")
        assertFalse("macosX64" in targetNames, "macosX64 target must be removed")
        assertFalse("iosX64" in targetNames, "iosX64 target must be removed")
        assertFalse("macosX64" in sourceSetNames, "macosX64 sourceSet must be removed")
        assertFalse("macosX64Main" in sourceSetNames, "macosX64Main sourceSet must be removed")
        assertFalse("macosX64Test" in sourceSetNames, "macosX64Test sourceSet must be removed")
        assertFalse("iosX64" in sourceSetNames, "iosX64 sourceSet must be removed")
        assertFalse("iosX64Main" in sourceSetNames, "iosX64Main sourceSet must be removed")
        assertFalse("iosX64Test" in sourceSetNames, "iosX64Test sourceSet must be removed")

        val expectedBuildTargetTasks = listOf(
            "developmentWindows",
            "productionWindows",
            "developmentWindowsX64",
            "productionWindowsX64",
            "developmentWindowsX32",
            "productionWindowsX32",
            "developmentWindowsArm64",
            "productionWindowsArm64",
            "developmentMac",
            "productionMac",
            "developmentMacArm64",
            "productionMacArm64",
            "developmentLinuxGtk",
            "productionLinuxGtk",
            "developmentLinuxGtkX32",
            "productionLinuxGtkX32",
            "developmentLinuxGtkX64",
            "productionLinuxGtkX64",
            "developmentLinuxGtkArm32",
            "productionLinuxGtkArm32",
            "developmentLinuxGtkArm64",
            "productionLinuxGtkArm64",
            "developmentLinuxQt",
            "productionLinuxQt",
            "developmentLinuxQtX32",
            "productionLinuxQtX32",
            "developmentLinuxQtX64",
            "productionLinuxQtX64",
            "developmentLinuxQtArm32",
            "productionLinuxQtArm32",
            "developmentLinuxQtArm64",
            "productionLinuxQtArm64",
            "developmentAndroid",
            "productionAndroid",
            "developmentAndroidDex",
            "productionAndroidDex",
            "developmentAndroidX32",
            "productionAndroidX32",
            "developmentAndroidX64",
            "productionAndroidX64",
            "developmentAndroidArm32",
            "productionAndroidArm32",
            "developmentAndroidArm64",
            "productionAndroidArm64",
            "developmentIos",
            "productionIos",
            "developmentIosArm64",
            "productionIosArm64",
            "developmentIosSimulatorArm64",
            "productionIosSimulatorArm64",
            "developmentSwing",
            "productionSwing",
            "developmentWeb",
            "productionWeb",
            "packageGenericUiWebDistribution",
            "packageGenericUiWebDevelopmentDistribution",
            "packageGenericUiWebProductionDistribution",
            "packageGenericUiWebTestDistribution",
            "packageGenericUiAndroidDevelopmentApk",
            "packageGenericUiAndroidProductionApk"
        )
        for (taskName in expectedBuildTargetTasks) {
            assertNotNull(
                project.tasks.findByName(taskName),
                "Expected GenericUI build target task '$taskName' to be registered"
            )
        }

        val expectedTestTargetTasks = listOf(
            "testWindows",
            "testWindowsX64",
            "testWindowsX32",
            "testWindowsArm64",
            "testMac",
            "testMacos",
            "testMacArm64",
            "testMacosArm64",
            "testLinux",
            "testGtk",
            "testLinuxGtk",
            "testLinuxGtkX64",
            "testLinuxGtkArm64",
            "testLinuxGtkX32",
            "testLinuxGtkArm32",
            "testQt",
            "testLinuxQt",
            "testLinuxQtX64",
            "testLinuxQtArm64",
            "testLinuxQtX32",
            "testLinuxQtArm32",
            "testSwing",
            "testAndroid",
            "testAndroidDex",
            "testAndroidX32",
            "testAndroidX64",
            "testAndroidArm32",
            "testAndroidArm64",
            "testIos",
            "testIosArm64",
            "testIosSimulatorArm64",
            "testWeb"
        )
        for (testTaskName in expectedTestTargetTasks) {
            val testTask = assertNotNull(
                project.tasks.findByName(testTaskName),
                "Expected GenericUI test target task '$testTaskName' to be registered"
            )
            assertEquals(
                "genericui test",
                testTask.group,
                "Expected GenericUI test task '$testTaskName' to belong to group 'genericui test'"
            )
        }

        val expectedDistPaths = mapOf(
            "developmentWindowsX64" to "dist/windows/x64/development",
            "productionWindowsX64" to "dist/windows/x64/production",
            "developmentWindowsX32" to "dist/windows/x32/development",
            "productionWindowsX32" to "dist/windows/x32/production",
            "developmentWindowsArm64" to "dist/windows/arm64/development",
            "productionWindowsArm64" to "dist/windows/arm64/production",
            "developmentMac" to "dist/mac/development",
            "productionMac" to "dist/mac/production",
            "developmentMacArm64" to "dist/mac/development",
            "productionMacArm64" to "dist/mac/production",
            "developmentLinuxGtkX64" to "dist/linux-gtk/x64/development",
            "productionLinuxGtkX64" to "dist/linux-gtk/x64/production",
            "developmentLinuxQtArm64" to "dist/linux-qt/arm64/development",
            "productionLinuxQtArm64" to "dist/linux-qt/arm64/production",
            "developmentAndroid" to "dist/android/dex/development",
            "productionAndroid" to "dist/android/dex/production",
            "developmentAndroidDex" to "dist/android/dex/development",
            "productionAndroidDex" to "dist/android/dex/production",
            "developmentAndroidArm64" to "dist/android/arm64/development",
            "productionAndroidArm64" to "dist/android/arm64/production",
            "developmentIosArm64" to "dist/ios/arm64/development",
            "productionIosArm64" to "dist/ios/arm64/production",
            "developmentIosSimulatorArm64" to "dist/ios/simulatorArm64/development",
            "productionIosSimulatorArm64" to "dist/ios/simulatorArm64/production",
            "developmentSwing" to "dist/swing/development",
            "productionSwing" to "dist/swing/production",
            "developmentWeb" to "dist/web/development",
            "productionWeb" to "dist/web/production"
        )
        for ((taskName, expectedDistRelPath) in expectedDistPaths) {
            val task = project.tasks.getByName(taskName)
            assertTrue(
                task.extensions.extraProperties.get("genericUiDistRelativePath") == expectedDistRelPath,
                "Task '$taskName' should output to build/$expectedDistRelPath"
            )
        }

        val removedLegacyBuildTargetTasks = listOf(
            "windowsDevelopment",
            "windowsProduction",
            "macDevelopment",
            "macProduction",
            "macosDevelopment",
            "macosProduction",
            "linuxDevelopmentGtk",
            "linuxProductionGtk",
            "linuxDevelopmentQt",
            "linuxProductionQt",
            "swingDevelopment",
            "swingProduction",
            "androidDevelopment",
            "androidProduction",
            "iosDevelopment",
            "iosProduction",
            "webDevelopment",
            "webProduction"
        )
        for (legacyTaskName in removedLegacyBuildTargetTasks) {
            assertNull(
                project.tasks.findByName(legacyTaskName),
                "Legacy build target task '$legacyTaskName' must not be registered"
            )
        }
    }

    @Test
    fun `packageGenericUiWebDistribution generates index html with svg favicon splash icon and hashed wasm js loader scripts`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("com.nexustools.genericui")
        val ext = project.extensions.getByType(GenericUiExtension::class.java)

        ext.application {
            appName = "Test Web Studio"
            baseName = "test-web-studio"
            icons(
                windows = "win-icon.ico",
                linux = "linux-icon.png",
                web = "web-icon.svg",
                common = "common-icon.png"
            )
        }
        ext.backends {
            web()
        }

        kotlin.test.assertEquals("web-icon.svg", ext.application.resolveEffectiveWebIconPath())
        java.io.File(project.projectDir, "web-icon.svg").writeText("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 64 64\"></svg>")

        // Stage sample application resources in src/commonMain/resources to verify resources-manifest.json discovery
        val sampleDrawable = java.io.File(project.projectDir, "src/commonMain/resources/drawable/hero-banner.png")
        sampleDrawable.parentFile.mkdirs()
        sampleDrawable.writeBytes(GenericUiDefaultIconProvider.loadDefaultIconPngBytes())
        val sampleStyle = java.io.File(project.projectDir, "src/commonMain/resources/styles/custom-theme.css")
        sampleStyle.parentFile.mkdirs()
        sampleStyle.writeText(":root { --brand-accent: #ff5500; }")
        val sampleXml = java.io.File(project.projectDir, "src/commonMain/resources/xml/main-layout.xml")
        sampleXml.parentFile.mkdirs()
        sampleXml.writeText("<genericui:fragment xmlns:genericui=\"https://nexustools.com/ns/genericui\"/>")

        val webDistTask = project.tasks.getByName("packageGenericUiWebDistribution") as GenericUiWebDistributionTask
        webDistTask.packageWebDistribution()

        val distDir = webDistTask.outputDirectory.get().asFile
        assertTrue(distDir.invariantSeparatorsPath.endsWith("build/dist/web/development"), "Default web dist output should be build/dist/web/development")
        val indexHtmlFile = java.io.File(distDir, "index.html")
        val loaderJsFile = java.io.File(distDir, "loader.js")
        val namespaceXmlFile = java.io.File(distDir, "genericui-namespace.xml")
        val genericUiCssFile = java.io.File(distDir, "gui/genericui.css")
        val resourcesManifestFile = java.io.File(distDir, "resources.json")
        val stagedIconFile = java.io.File(distDir, "res/web-icon.svg")

        assertTrue(indexHtmlFile.exists(), "index.html should be generated in build/dist/web/development")
        assertTrue(loaderJsFile.exists(), "loader.js should be generated in build/dist/web/development")
        assertTrue(namespaceXmlFile.exists(), "genericui-namespace.xml should be generated in build/dist/web/development")
        assertTrue(genericUiCssFile.exists(), "gui/genericui.css should be generated in build/dist/web/development")
        assertTrue(resourcesManifestFile.exists(), "resources.json should be generated in build/dist/web/development")
        assertTrue(stagedIconFile.exists(), "res/web-icon.svg should be staged in build/dist/web/development")

        // Verify root directory only contains allowed root files + gui/ + res/
        val allowedRootEntries = setOf(
            "index.html",
            "loader.js",
            "manifest.webmanifest",
            "genericui-namespace.xml",
            "resources.json",
            "test-web-studio.js",
            "test-web-studio.wasm",
            "gui",
            "res"
        )
        for (entry in distDir.listFiles().orEmpty()) {
            assertTrue(entry.name in allowedRootEntries, "Unexpected entry '${entry.name}' in root of web distribution")
        }

        // Verify resources.json entries
        val resourcesManifestJson = resourcesManifestFile.readText()
        assertTrue(resourcesManifestJson.contains("\"id\": \"styles/genericui\""))
        assertTrue(resourcesManifestJson.contains("\"path\": \"gui/genericui.css\""))
        assertTrue(resourcesManifestJson.contains("\"id\": \"xml/genericui-namespace\""))
        assertTrue(resourcesManifestJson.contains("\"path\": \"genericui-namespace.xml\""))
        assertTrue(resourcesManifestJson.contains("\"id\": \"drawable/hero-banner\""))
        assertTrue(resourcesManifestJson.contains("\"path\": \"res/drawable/hero-banner.png\""))
        assertTrue(resourcesManifestJson.contains("\"mimeType\": \"image/png\""))
        assertTrue(resourcesManifestJson.contains("\"id\": \"styles/custom-theme\""))
        assertTrue(resourcesManifestJson.contains("\"path\": \"res/styles/custom-theme.css\""))
        assertTrue(resourcesManifestJson.contains("\"mimeType\": \"text/css\""))
        assertTrue(resourcesManifestJson.contains("\"id\": \"xml/main-layout\""))
        assertTrue(resourcesManifestJson.contains("\"path\": \"res/xml/main-layout.xml\""))
        assertTrue(resourcesManifestJson.contains("\"hash\": \""))
        assertTrue(resourcesManifestJson.contains("\"mimeType\": \"application/xml\""))
        assertTrue(resourcesManifestJson.contains("\"size\": "))

        val namespaceXml = namespaceXmlFile.readText()
        assertTrue(namespaceXml.contains("<namespaceSpec xmlns=\"https://nexustools.com/ns/genericui/spec\" prefix=\"genericui\" uri=\"https://nexustools.com/ns/genericui\" version=\"1.0\">"))
        assertTrue(namespaceXml.contains("tag=\"genericui:app\""))
        assertTrue(namespaceXml.contains("tag=\"genericui:window\""))
        assertTrue(namespaceXml.contains("tag=\"genericui:fragment\""))
        assertTrue(namespaceXml.contains("tag=\"genericui:fragment-container\""))
        assertTrue(namespaceXml.contains("tag=\"genericui:menubar\""))
        assertTrue(namespaceXml.contains("tag=\"genericui:dockworkspace\""))

        val genericUiCss = genericUiCssFile.readText()
        assertTrue(genericUiCss.contains("genericui\\:app"))
        assertTrue(genericUiCss.contains("hr.dropdown-divider,\ngenericui\\:menuseparator"))
        assertTrue(genericUiCss.contains("genericui\\:fragment.scroll-fragment"))

        val html = indexHtmlFile.readText()
        assertTrue(html.contains("xmlns:genericui=\"https://nexustools.com/ns/genericui\""))
        assertTrue(html.contains("<link id=\"genericui-namespace-spec\" rel=\"namespace\" data-namespace-prefix=\"genericui\" data-namespace-uri=\"https://nexustools.com/ns/genericui\" type=\"application/xml\" href=\"genericui-namespace.xml?"))
        assertTrue(html.contains("<link id=\"genericui-css\" rel=\"stylesheet\" href=\"gui/genericui.css?"))
        assertTrue(html.contains("<genericui:app></genericui:app>"))
        assertTrue(!html.contains("<div id=\"genericui-app\""))
        assertTrue(html.contains("<link id=\"genericui-favicon\" rel=\"icon\" type=\"image/svg+xml\" href=\"res/web-icon.svg\">"))
        assertTrue(html.contains("<img id=\"genericui-splash-icon\" src=\"res/web-icon.svg\""))
        assertTrue(html.contains("width=\"64\" height=\"64\""))
        assertTrue(html.contains("id=\"genericui-splash-progress-bar\""))
        assertTrue(html.contains("id=\"genericui-splash-error-stack\""))
        assertTrue(html.contains("<script type=\"loader-wasm\" src=\"test-web-studio.wasm?"))
        assertTrue(html.contains("<script type=\"loader-js\" src=\"test-web-studio.js?"))
        assertTrue(html.contains("<link id=\"genericui-resources-manifest\" rel=\"resources\" type=\"application/json\" href=\"resources.json?"))
        assertTrue(!html.contains("<script id=\"genericui-resource-manifest\">"))
        assertTrue(html.contains("<script id=\"genericui-loader-script\" src=\"loader.js?"))
        assertTrue(html.contains("<link id=\"genericui-bootstrap-css\" rel=\"stylesheet\" href=\"gui/bootstrap.min.css?"))
        assertTrue(html.contains("<script id=\"genericui-bootstrap-js\" src=\"gui/bootstrap.bundle.min.js?"))
        assertTrue(html.contains("<link id=\"genericui-bootstrap-icons-css\" rel=\"stylesheet\" href=\"gui/bootstrap-icons.min.css?"))
        assertTrue(!html.contains("cdn.jsdelivr.net"))

        // Verify strictly short 5 status strings in index.html and loader.js
        val loaderJs = loaderJsFile.readText()
        assertTrue(html.contains("id=\"genericui-splash-status\""))
        assertTrue(html.contains(">Initializing</span></div>"))
        assertTrue(html.contains("id=\"genericui-splash-status-icon\" class=\"genericui-splash-status-icon spinner-border spinner-border-sm\""))
        assertTrue(!html.contains("Loading application..."))
        assertTrue(loaderJs.contains("\"Initializing\""))
        assertTrue(loaderJs.contains("\"Loading Resources\""))
        assertTrue(loaderJs.contains("\"Loading Application\""))
        assertTrue(loaderJs.contains("\"Error\""))
        assertTrue(loaderJs.contains("\"Ready\""))
        assertTrue(!loaderJs.contains("Detecting WebAssembly support"))
        assertTrue(!loaderJs.contains("Downloading WebAssembly"))
        assertTrue(!loaderJs.contains("Initializing WebAssembly"))
        assertTrue(!loaderJs.contains("Downloading JavaScript"))
        assertTrue(!loaderJs.contains("Starting JavaScript"))

        // Resources preloaded before Wasm/JS regardless of webManifest; Wasm failures fall back to JS silently
        assertTrue(loaderJs.contains("\"Loading Fallback\""))
        assertTrue(loaderJs.contains("link[rel=\"resources\"]"))
        assertTrue(loaderJs.contains("buildHashedResourceUrl"))
        assertTrue(loaderJs.contains("./resources.json"))
        assertTrue(loaderJs.contains("logWasmFallbackErrorSilently(wasmErr);"))
        assertTrue(
            loaderJs.indexOf("state.wasmSupported = detectWasmSupport();") < loaderJs.indexOf("return preloadAllResources(config)"),
            "loader.js must detect WebAssembly support upfront before preloading resources"
        )
        assertTrue(loaderJs.contains("state.totalBytes = state.resourceBytes + appBundleBytes;"))
        assertTrue(genericUiCss.contains("user-select: text !important"))
        assertTrue(genericUiCss.contains(".genericui-splash.genericui-splash-hidden"))
        val splashMarkup = html.substring(html.indexOf("id=\"genericui-splash\""), html.indexOf("<genericui:app>"))
        assertTrue(!splashMarkup.contains("style=\""), "Splash markup must not contain inline style attributes")
        assertTrue(genericUiCss.contains("#genericui-splash-error-stack"))
        assertTrue(!resourcesManifestJson.contains(".php\""))
        assertTrue(!resourcesManifestJson.contains(".scss\""))
    }

    @Test
    fun testWebManifestInstallableWebAppAndThemeColorAcrossBackends() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("com.nexustools.genericui")
        val ext = project.extensions.getByType(GenericUiExtension::class.java)

        ext.application {
            appName = "Sample Web Studio"
            baseName = "sample-web-studio"
            description = "Cross-platform media & studio showcase"
            themeColor = "#2A80FF"
            webIcon("web-icon.svg")
            webManifest {
                allowInstallAsWebApp = true
                shortName = "SampleWebStudio"
                display = "standalone"
                orientation = "any"
                backgroundColor = "#0B0E14"
                categories("entertainment", "multimedia")
                shortcut(
                    name = "Now Playing",
                    shortName = "Playing",
                    description = "Jump directly to the active player",
                    url = "./?tab=player"
                )
            }
        }
        ext.backends {
            allExceptSwing()
        }

        java.io.File(project.projectDir, "web-icon.svg").writeText("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 64 64\"></svg>")

        // Stage a sample app resource to verify inclusion in manifest.webmanifest resources and gui/genericui-sw.js
        val appFont = java.io.File(project.projectDir, "src/commonMain/resources/font/inter.woff2")
        appFont.parentFile.mkdirs()
        appFont.writeBytes(byteArrayOf(0x77, 0x4F, 0x46, 0x32))

        // Stage real Kotlin/Wasm + Kotlin/JS build outputs so the offline resource list contains existing bundle files
        val wasmOut = java.io.File(project.layout.buildDirectory.get().asFile, "dist/wasmJs/productionExecutable").apply { mkdirs() }
        java.io.File(wasmOut, "sample-web-studio-wasm-js.js").writeText("/* wasm glue */")
        java.io.File(wasmOut, "sample-web-studio-wasm-js.wasm").writeBytes(byteArrayOf(0x00, 0x61, 0x73, 0x6d, 0x01, 0x00, 0x00, 0x00))
        val jsOut = java.io.File(project.layout.buildDirectory.get().asFile, "dist/js/productionExecutable").apply { mkdirs() }
        java.io.File(jsOut, "sample-web-studio.js").writeText("/* js ir bundle */")

        // A comment-only placeholder left behind by an older plugin version must be removed from the distribution
        val distDirEarly = (project.tasks.getByName("packageGenericUiWebDistribution") as GenericUiWebDistributionTask).outputDirectory.get().asFile
        distDirEarly.mkdirs()
        java.io.File(distDirEarly, "bootstrap.min.css").writeText("/* GenericUI Offline Bootstrap 5.3.3 Stylesheet (bootstrap.min.css) */\n")

        // 1. Verify Web Distribution (manifest.webmanifest + gui/genericui-sw.js + resources.json + installable WebApp HTML tags + themeColor)
        val webDistTask = project.tasks.getByName("packageGenericUiWebDistribution") as GenericUiWebDistributionTask
        webDistTask.packageWebDistribution()

        val distDir = webDistTask.outputDirectory.get().asFile
        val manifestFile = java.io.File(distDir, "manifest.webmanifest")
        val swFile = java.io.File(distDir, "gui/genericui-sw.js")
        val resourcesManifestFile = java.io.File(distDir, "resources.json")
        val indexHtmlFile = java.io.File(distDir, "index.html")
        val loaderJsFile = java.io.File(distDir, "loader.js")

        assertTrue(manifestFile.exists(), "manifest.webmanifest should be generated when allowInstallAsWebApp or webManifest is enabled")
        assertTrue(swFile.exists(), "gui/genericui-sw.js should be generated when allowInstallAsWebApp or webManifest is enabled")
        assertTrue(resourcesManifestFile.exists(), "resources.json should be generated in build/dist/web/development")

        val manifestJson = manifestFile.readText()
        assertTrue(manifestJson.contains("\"name\": \"Sample Web Studio\""))
        assertTrue(manifestJson.contains("\"short_name\": \"SampleWebStudio\""))
        assertTrue(manifestJson.contains("\"description\": \"Cross-platform media & studio showcase\""))
        assertTrue(manifestJson.contains("\"display\": \"standalone\""))
        assertTrue(manifestJson.contains("\"theme_color\": \"#2A80FF\""))
        assertTrue(manifestJson.contains("\"background_color\": \"#0B0E14\""))
        assertTrue(manifestJson.contains("\"categories\": [\"entertainment\", \"multimedia\"]"))
        assertTrue(manifestJson.contains("\"sizes\": \"192x192 512x512\""))
        assertTrue(manifestJson.contains("\"url\": \"./?tab=player\""))
        assertTrue(manifestJson.contains("\"offline_enabled\": true"))
        assertTrue(manifestJson.contains("\"resources\": ["))
        assertTrue(manifestJson.contains("\"./\""))
        assertTrue(manifestJson.contains("\"index.html\""))
        assertTrue(manifestJson.contains("\"gui/genericui.css\""))
        // Comment-only placeholder assets are removed, while real packaged Bootstrap assets are staged in gui/
        assertTrue(!java.io.File(distDir, "genericui-styles.css").exists(), "genericui-styles.css placeholder must not exist in build/dist/web")
        for (packagedAsset in listOf("gui/bootstrap.min.css", "gui/bootstrap.bundle.min.js", "gui/bootstrap-icons.min.css", "gui/fonts/bootstrap-icons.woff2", "gui/fonts/bootstrap-icons.woff")) {
            val f = java.io.File(distDir, packagedAsset)
            assertTrue(f.exists() && f.length() > 1000L, "$packagedAsset real packaged asset must exist in build/dist/web")
        }
        assertTrue(manifestJson.contains("\"resources.json\""))
        assertTrue(manifestJson.contains("\"sample-web-studio.wasm\""))
        assertTrue(manifestJson.contains("\"sample-web-studio.js\""))
        val sampleResourcesJson = resourcesManifestFile.readText()
        assertTrue(sampleResourcesJson.contains("\"id\": \"wasm/sample-web-studio\""))
        assertTrue(sampleResourcesJson.contains("\"id\": \"js/sample-web-studio\""))
        assertTrue(sampleResourcesJson.contains("\"size\": "))
        assertTrue(manifestJson.contains("\"res/web-icon.svg\""))
        assertTrue(manifestJson.contains("\"res/icon-192.png\""))
        assertTrue(manifestJson.contains("\"res/icon-512.png\""))
        assertTrue(manifestJson.contains("\"res/favicon.ico\""))
        assertTrue(manifestJson.contains("\"res/font/inter.woff2\""))

        val swJs = swFile.readText()
        assertTrue(swJs.contains("const CACHE_NAME = \"genericui-offline-v1\";"))
        assertTrue(swJs.contains("cache.addAll(OFFLINE_RESOURCES)"))
        assertTrue(swJs.contains("self.skipWaiting()"))
        assertTrue(swJs.contains("self.clients.claim()"))
        assertTrue(swJs.contains("\"./\""))
        assertTrue(swJs.contains("\"index.html\""))
        assertTrue(swJs.contains("\"resources.json\""))
        assertTrue(swJs.contains("\"res/font/inter.woff2\""))

        val html = indexHtmlFile.readText()
        val loaderJs = loaderJsFile.readText()
        assertTrue(html.contains("<link id=\"genericui-web-manifest\" rel=\"manifest\" href=\"manifest.webmanifest?"))
        assertTrue(html.contains("<meta id=\"genericui-theme-color\" name=\"theme-color\" content=\"#2A80FF\">"))
        assertTrue(html.contains("<meta id=\"genericui-mobile-web-app-capable\" name=\"mobile-web-app-capable\" content=\"yes\">"))
        assertTrue(html.contains("<meta id=\"genericui-apple-mobile-web-app-capable\" name=\"apple-mobile-web-app-capable\" content=\"yes\">"))
        assertTrue(html.contains("<meta id=\"genericui-application-name\" name=\"application-name\" content=\"SampleWebStudio\">"))
        assertTrue(html.contains("<link id=\"genericui-apple-touch-icon\" rel=\"apple-touch-icon\" href=\"res/web-icon.svg\">"))
        assertTrue(html.contains("navigator.serviceWorker.register('./gui/genericui-sw.js')"))
        assertTrue(loaderJs.contains("navigator.serviceWorker.register('./gui/genericui-sw.js')"))

        // 2. Verify Desktop Resources (Windows .rc, macOS Info.plist, Linux .desktop) receive themeColor
        val desktopResTask = project.tasks.getByName("prepareGenericUiDesktopResources") as GenericUiDesktopResourceTask
        desktopResTask.generateDesktopResources()
        val desktopOutDir = desktopResTask.outputDirectory.get().asFile
        val winRc = java.io.File(desktopOutDir, "windows/app_resources.rc").readText()
        val macPlist = java.io.File(desktopOutDir, "macos/Info.plist").readText()
        val linuxDesktop = java.io.File(desktopOutDir, "linux/sample-web-studio.desktop").readText()

        assertTrue(winRc.contains("VALUE \"ThemeColor\", \"#2A80FF\\0\""))
        assertTrue(macPlist.contains("<key>GenericUIThemeColor</key>"))
        assertTrue(macPlist.contains("<string>#2A80FF</string>"))
        assertTrue(linuxDesktop.contains("X-GenericUI-ThemeColor=#2A80FF"))

        // 3. Verify Resource Compiler (R.kt / RC.kt, Android colors.xml, iOS/macOS AccentColor.colorset, Web CSS vars)
        val resTask = project.tasks.getByName("compileGenericUiResources") as GenericUiResourceCompilerTask
        resTask.compileResources()
        val ktResOut = resTask.kotlinOutputDirectory.get().asFile
        val backendResOut = resTask.backendOutputDirectory.get().asFile

        val rKtFile = ktResOut.walkTopDown().first { it.name == "R.kt" }
        val rKtText = rKtFile.readText()
        assertTrue(rKtText.contains("val theme_color: ResourceID"))
        assertTrue(rKtText.contains("val colorPrimary: ResourceID"))

        val regKtFile = ktResOut.walkTopDown().first { it.name == "GeneratedResourceRegistry.kt" }
        val regKtText = regKtFile.readText()
        assertTrue(regKtText.contains("const val APP_NAME: String = \"Sample Web Studio\""))
        assertTrue(regKtText.contains("GenericUI.applicationName = APP_NAME"))

        val androidColorsXml = java.io.File(backendResOut, "android/res/values/colors.xml").readText()
        assertTrue(androidColorsXml.contains("<color name=\"theme_color\">#2A80FF</color>"))
        assertTrue(androidColorsXml.contains("<color name=\"colorPrimary\">#2A80FF</color>"))

        val appleAccentColorJson = java.io.File(backendResOut, "apple/Assets.xcassets/AccentColor.colorset/Contents.json").readText()
        assertTrue(appleAccentColorJson.contains("\"hex\": \"#2A80FF\""))

        val webCssVars = java.io.File(backendResOut, "web/genericui-resource-vars.css").readText()
        assertTrue(webCssVars.contains("--genericui-theme-color: #2A80FF;"))
        assertTrue(webCssVars.contains("--rc-color-theme-color: #2A80FF;"))
    }

    @Test
    fun `default genericui icon is used across web desktop and resources when no icons are provided by build kts`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("com.nexustools.genericui")
        val ext = project.extensions.getByType(GenericUiExtension::class.java)

        ext.application {
            appName = "Iconless App"
            baseName = "iconless-app"
        }
        ext.backends {
            allExceptSwing()
        }

        // Verify default icon paths across web and desktop backends
        kotlin.test.assertEquals("icon.svg", ext.application.resolveEffectiveWebIconPath())
        kotlin.test.assertEquals("icon.ico", ext.application.desktop.resolveEffectiveWindowsIconPath())
        kotlin.test.assertEquals("icon.png", ext.application.desktop.resolveEffectiveMacIconPath())
        kotlin.test.assertEquals("icon.png", ext.application.desktop.resolveEffectiveLinuxIconPath())
        kotlin.test.assertEquals("icon.png", ext.application.desktop.resolveEffectiveCommonIconPath())
        assertTrue(GenericUiDefaultIconProvider.loadDefaultIconIcoBytes().size > 1000, "Bundled default icon.ico should be present")
        assertTrue(GenericUiDefaultIconProvider.loadDefaultIconPngBytes().size > 1000, "Bundled default icon.png should be present")

        // 1. Web distribution should stage and reference the default gui/icon.svg
        val webDistTask = project.tasks.getByName("packageGenericUiWebDistribution") as GenericUiWebDistributionTask
        webDistTask.packageWebDistribution()
        val distDir = webDistTask.outputDirectory.get().asFile
        val defaultWebSvg = java.io.File(distDir, "gui/icon.svg")
        assertTrue(defaultWebSvg.exists(), "Default GenericUI gui/icon.svg should be staged in build/dist/web/development when no icon is configured")
        val svgContent = defaultWebSvg.readText()
        assertTrue(svgContent.contains("macTopClip") && svgContent.contains("gtkTopClip") && svgContent.contains("winTopClip"))
        val indexHtml = java.io.File(distDir, "index.html").readText()
        assertTrue(indexHtml.contains("<link id=\"genericui-favicon\" rel=\"icon\" type=\"image/svg+xml\" href=\"gui/icon.svg\">"))
        assertTrue(indexHtml.contains("<img id=\"genericui-splash-icon\" src=\"gui/icon.svg\""))

        // 2. Desktop resources should stage default icon.ico, icon.png, and icon.svg
        val desktopResTask = project.tasks.getByName("prepareGenericUiDesktopResources") as GenericUiDesktopResourceTask
        desktopResTask.generateDesktopResources()
        val desktopOutDir = desktopResTask.outputDirectory.get().asFile
        assertTrue(java.io.File(desktopOutDir, "runtime-assets/icon.ico").exists(), "Default icon.ico should be staged in runtime-assets")
        assertTrue(java.io.File(desktopOutDir, "runtime-assets/icon.png").exists(), "Default icon.png should be staged in runtime-assets")
        assertTrue(java.io.File(desktopOutDir, "runtime-assets/icon.svg").exists(), "Default icon.svg should be staged in runtime-assets")
        val winRc = java.io.File(desktopOutDir, "windows/app_resources.rc").readText()
        assertTrue(winRc.contains("1 ICON"), "Windows .rc should embed primary exe icon (1 ICON)")
        assertTrue(winRc.contains("IDI_APPICON ICON"), "Windows .rc should embed default icon.ico")
        assertTrue(winRc.contains("icon.ico"), "Windows .rc should point to icon.ico")

        // 3. Resource compiler should register R.drawable.icon and stage default icon.ico / icon.png across backends
        val resTask = project.tasks.getByName("compileGenericUiResources") as GenericUiResourceCompilerTask
        resTask.compileResources()
        val rKtFile = resTask.kotlinOutputDirectory.get().asFile.walkTopDown().first { it.name == "R.kt" }
        assertTrue(rKtFile.readText().contains("val icon: ResourceID"), "R.drawable.icon should be generated by default")
        val backendResOut = resTask.backendOutputDirectory.get().asFile
        assertTrue(java.io.File(backendResOut, "windows/icon.ico").exists(), "Windows backend resources should include icon.ico")
        assertTrue(java.io.File(backendResOut, "android/res/drawable/icon.png").exists(), "Android backend resources should include icon.png")
        assertTrue(java.io.File(backendResOut, "apple/Assets.xcassets/AppIcon.appiconset/icon.png").exists(), "Apple backend resources should include icon.png")
        assertTrue(java.io.File(backendResOut, "apple/Assets.xcassets/AppIcon.appiconset/icon.icns").exists(), "Apple backend resources should include icon.icns")
        assertTrue(java.io.File(backendResOut, "apple/icon.icns").exists(), "Apple backend root should include icon.icns")
        assertTrue(java.io.File(desktopOutDir, "macos/icon.icns").exists(), "macOS desktop resources should include icon.icns")
        assertTrue(java.io.File(backendResOut, "linux/drawable/icon.png").exists(), "Linux backend resources should include icon.png")
        assertTrue(java.io.File(backendResOut, "web/icon.png").exists(), "Web/Swing backend resources should include icon.png")
        assertTrue(java.io.File(backendResOut, "web/icon.ico").exists(), "Web/Swing backend resources should include icon.ico")

        // 4. Verify non-512 viewBox SVG and SVG arc (A/a) paths rasterize and generate valid Windows .ico and Apple .icns
        val pinkTvSvg = """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="512" height="512" fill="none">
              <path d="M 227 112 V 89 A 22 22 0 0 1 271 89 V 112 Z" fill="#E0E7EF" stroke="#000000" stroke-width="20"/>
              <rect x="34" y="112" width="444" height="345" rx="46" fill="#FF7E96"/>
            </svg>
        """.trimIndent()
        val tempSvg = java.io.File(project.projectDir, "pink-tv.svg").apply { writeText(pinkTvSvg) }
        val genIconsDir = java.io.File(project.projectDir, "build/test-gen-icons").apply { mkdirs() }
        val genWinIco = GenericUiDefaultIconProvider.ensureWindowsIcoFromSourceOrDefault(tempSvg, project.projectDir, genIconsDir, "pink-tv.ico")
        val genMacIcns = GenericUiDefaultIconProvider.ensureAppleIcnsFromSourceOrDefault(tempSvg, project.projectDir, genIconsDir, "pink-tv.icns")
        assertTrue(genWinIco.exists() && genWinIco.length() > 1000L, "Windows .ico should be generated from source .svg")
        val icnsBytes = genMacIcns.readBytes()
        assertTrue(
            icnsBytes.size > 1000 &&
                icnsBytes[0] == 'i'.code.toByte() &&
                icnsBytes[1] == 'c'.code.toByte() &&
                icnsBytes[2] == 'n'.code.toByte() &&
                icnsBytes[3] == 's'.code.toByte(),
            "Apple .icns should have valid 'icns' magic header when generated from source .svg"
        )
        val svg128 = """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 128 128">
                <rect x="0" y="0" width="128" height="128" fill="#FF3366"/>
            </svg>
        """.trimIndent()
        val rendered256 = javax.imageio.ImageIO.read(
            java.io.ByteArrayInputStream(GenericUiDefaultIconProvider.renderSvgTextToPngBytes(svg128, 256))
        )
        val bottomRightAlpha = (rendered256.getRGB(240, 240) ushr 24) and 0xFF
        assertTrue(bottomRightAlpha > 200, "128x128 viewBox SVG should scale to fill the bottom-right quadrant of a 256x256 icon")
    }

    @Test
    fun `testTarget tasks respect enabled backends and packageGenericUiWebTestDistribution stages test js and harness`() {
        val desktopOnlyProject = ProjectBuilder.builder().withName("desktop-only-app").build()
        desktopOnlyProject.pluginManager.apply("com.nexustools.genericui")
        val desktopExt = desktopOnlyProject.extensions.getByType(GenericUiExtension::class.java)
        desktopExt.backends {
            allNativeDesktop()
        }

        // Enabled native desktop backends must register their test<Target> tasks
        for (enabledTask in listOf(
            "testWindows",
            "testWindowsX64",
            "testWindowsX32",
            "testWindowsArm64",
            "testMac",
            "testMacos",
            "testMacArm64",
            "testMacosArm64",
            "testLinux",
            "testGtk",
            "testLinuxGtk",
            "testLinuxGtkX64",
            "testQt",
            "testLinuxQt",
            "testLinuxQtX64"
        )) {
            assertNotNull(
                desktopOnlyProject.tasks.findByName(enabledTask),
                "Expected '$enabledTask' to be registered when allNativeDesktop() is configured"
            )
        }

        // Disabled backends (web, android, ios, swing) must NOT register their test<Target> tasks
        for (disabledTask in listOf(
            "testWeb",
            "packageGenericUiWebTestDistribution",
            "testAndroid",
            "testAndroidDex",
            "testIos",
            "testIosArm64",
            "testSwing"
        )) {
            assertNull(
                desktopOnlyProject.tasks.findByName(disabledTask),
                "Expected '$disabledTask' NOT to be registered when only native desktop backends are enabled"
            )
        }

        // Verify web test distribution packaging and auto-generated test bootstraps
        val webProject = ProjectBuilder.builder().withName("sample-web-test-app").build()
        webProject.group = "com.example.webtest"
        webProject.pluginManager.apply("com.nexustools.genericui")
        val webExt = webProject.extensions.getByType(GenericUiExtension::class.java)
        webExt.application {
            appName = "Sample Web Test App"
            baseName = "sample-web-test-app"
        }
        webExt.backends {
            all()
        }

        val launchersTask = webProject.tasks.getByName("generateGenericUiLaunchers") as GenericUiGenerateLaunchersTask
        launchersTask.generateLaunchers()
        val launchersRoot = launchersTask.outputDirectory.get().asFile
        val expectedBootstraps = mapOf(
            "windowsTest/kotlin/com/example/webtest/generated/GeneratedWindowsTestBootstrap.kt" to "WindowsJestElectricBootstrap.installIfEnabled()",
            "macosTest/kotlin/com/example/webtest/generated/GeneratedMacosTestBootstrap.kt" to "MacosJestElectricBootstrap.installIfEnabled()",
            "gtkTest/kotlin/com/example/webtest/generated/GeneratedGtkTestBootstrap.kt" to "GtkJestElectricBootstrap.installIfEnabled()",
            "qtTest/kotlin/com/example/webtest/generated/GeneratedQtTestBootstrap.kt" to "QtJestElectricBootstrap.installIfEnabled()",
            "swingTest/kotlin/com/example/webtest/generated/GeneratedSwingTestBootstrap.kt" to "SwingJestElectricBootstrap.installIfEnabled()",
            "androidTest/kotlin/com/example/webtest/generated/GeneratedAndroidTestBootstrap.kt" to "AndroidJestElectricBootstrap.installIfEnabled()",
            "iosTest/kotlin/com/example/webtest/generated/GeneratedIosTestBootstrap.kt" to "IosJestElectricBootstrap.installIfEnabled()",
            "webTest/kotlin/com/example/webtest/generated/GeneratedWebTestBootstrap.kt" to "WebJestElectricBootstrap.installIfEnabled()"
        )
        for ((relPath, expectedCall) in expectedBootstraps) {
            val file = java.io.File(launchersRoot, relPath)
            assertTrue(file.isFile, "Expected generated test bootstrap at $relPath")
            assertTrue(file.readText().contains(expectedCall), "Expected $relPath to contain '$expectedCall'")
        }

        // Stage main JS + test JS bundles and packageGenericUiWebTestDistribution
        val buildDir = webProject.layout.buildDirectory.get().asFile
        val jsMainDir = java.io.File(buildDir, "dist/js/developmentExecutable").apply { mkdirs() }
        java.io.File(jsMainDir, "sample-web-test-app.js").writeText("/* main js bundle */")
        val jsTestDir = java.io.File(buildDir, "compileSync/js/test/testDevelopmentExecutable/kotlin").apply { mkdirs() }
        java.io.File(jsTestDir, "sample-web-test-app_test.js").writeText("/* test js bundle */")

        val webTestDistTask = webProject.tasks.getByName("packageGenericUiWebTestDistribution") as GenericUiWebDistributionTask
        webTestDistTask.packageWebDistribution()

        val testDistDir = webTestDistTask.outputDirectory.get().asFile
        assertTrue(
            testDistDir.invariantSeparatorsPath.endsWith("build/distributions/web/test"),
            "packageGenericUiWebTestDistribution should output to build/distributions/web/test"
        )
        assertTrue(java.io.File(testDistDir, "sample-web-test-app.js").isFile, "Main JS bundle should be staged")
        assertTrue(java.io.File(testDistDir, "sample-web-test-app_test.js").isFile, "Test JS bundle should be staged")
        assertTrue(java.io.File(testDistDir, "web-test-harness.js").isFile, "web-test-harness.js should be staged")

        val testIndexHtml = java.io.File(testDistDir, "index.html").readText()
        assertTrue(testIndexHtml.contains("<script type=\"loader-js\" src=\"sample-web-test-app.js?"))
        assertTrue(testIndexHtml.contains("<script type=\"loader-js\" src=\"sample-web-test-app_test.js?"))
        assertTrue(testIndexHtml.contains("<script type=\"loader-js\" src=\"web-test-harness.js?"))

        val testLoaderJs = java.io.File(testDistDir, "loader.js").readText()
        assertTrue(testLoaderJs.contains("querySelectorAll('script[type=\"loader-js\"]')"))
        assertTrue(testLoaderJs.contains("loadAdditionalJsScriptsSequentially"))
    }

    @Test
    fun `theme DSL configures light and dark overrides and emits Android Material XML, launcher ThemeConfig, and Web genericui-theme css`() {
        val project = ProjectBuilder.builder().withName("themed-app").build()
        project.group = "com.example.themed"
        project.pluginManager.apply("com.nexustools.genericui")

        val commonKotlinDir = java.io.File(project.projectDir, "src/commonMain/kotlin/com/example/themed").apply { mkdirs() }
        java.io.File(commonKotlinDir, "MainFragment.kt").writeText(
            """
            package com.example.themed

            import com.nexustools.genericui.component.fragment.Fragment
            import com.nexustools.genericui.component.fragment.MainAppFragment

            @MainAppFragment
            class MainFragment : Fragment()
            """.trimIndent()
        )

        val ext = project.extensions.getByType(GenericUiExtension::class.java)
        assertFalse(ext.themeConfig.hasCustomThemeOverrides(), "Default GenericUiThemeConfig should have no custom overrides")

        ext.app {
            id = "com.example.themed"
            name = "Themed App"
            packageName = "com.example.themed"
        }
        ext.backends {
            all()
        }
        ext.theme {
            mode = "auto"
            primaryColor = "#2563eb"
            secondaryColor = "#475569"
            accentColor = "#7c3aed"
            successColor = "#16a34a"
            dangerColor = "#dc2626"
            warningColor = "#d97706"
            infoColor = "#0284c7"
            light {
                backgroundColor = "#f8fafc"
                surfaceColor = "#ffffff"
                textPrimaryColor = "#0f172a"
                borderColor = "#e2e8f0"
            }
            dark {
                primaryColor = "#60a5fa"
                backgroundColor = "#0f172a"
                surfaceColor = "#1e293b"
                textPrimaryColor = "#f8fafc"
                borderColor = "#334155"
            }
        }

        assertTrue(ext.themeConfig.hasCustomThemeOverrides(), "Configured GenericUiThemeConfig should report custom overrides")
        assertTrue(ext.themeConfig.light.hasCustomOverrides())
        assertTrue(ext.themeConfig.dark.hasCustomOverrides())
        val lightResolved = ext.themeConfig.resolveLightColors()
        val darkResolved = ext.themeConfig.resolveDarkColors()
        assertEquals("#2563eb", lightResolved["primary"])
        assertEquals("#7c3aed", lightResolved["accent"])
        assertEquals("#f8fafc", lightResolved["background"])
        assertEquals("#60a5fa", darkResolved["primary"])
        assertEquals("#0f172a", darkResolved["background"])
        assertEquals("#1e293b", darkResolved["surface"])

        // 1. Verify Android resource compiler generates genericui_theme_colors.xml, values/themes.xml, and values-night/themes.xml
        val resTask = project.tasks.getByName("compileGenericUiResources") as GenericUiResourceCompilerTask
        resTask.compileResources()
        val androidResDir = java.io.File(resTask.backendOutputDirectory.get().asFile, "android/res")
        val themeColorsXmlFile = java.io.File(androidResDir, "values/genericui_theme_colors.xml")
        val dayThemesXmlFile = java.io.File(androidResDir, "values/themes.xml")
        val nightThemesXmlFile = java.io.File(androidResDir, "values-night/themes.xml")
        assertTrue(themeColorsXmlFile.isFile, "Expected res/values/genericui_theme_colors.xml")
        assertTrue(dayThemesXmlFile.isFile, "Expected res/values/themes.xml")
        assertTrue(nightThemesXmlFile.isFile, "Expected res/values-night/themes.xml")

        val themeColorsXml = themeColorsXmlFile.readText()
        assertTrue(themeColorsXml.contains("<color name=\"genericui_light_primary\">#2563eb</color>"))
        assertTrue(themeColorsXml.contains("<color name=\"genericui_light_primary_dark\">"))
        assertTrue(themeColorsXml.contains("<color name=\"genericui_light_accent\">#7c3aed</color>"))
        assertTrue(themeColorsXml.contains("<color name=\"genericui_light_background\">#f8fafc</color>"))
        assertTrue(themeColorsXml.contains("<color name=\"genericui_dark_primary\">#60a5fa</color>"))
        assertTrue(themeColorsXml.contains("<color name=\"genericui_dark_background\">#0f172a</color>"))
        assertTrue(themeColorsXml.contains("<color name=\"genericui_dark_surface\">#1e293b</color>"))

        val dayThemesXml = dayThemesXmlFile.readText()
        assertTrue(dayThemesXml.contains("<style name=\"Theme.GenericUI.App\" parent=\"@android:style/Theme.Material.Light.NoActionBar\">"))
        assertTrue(dayThemesXml.contains("<item name=\"android:colorPrimary\">@color/genericui_light_primary</item>"))
        assertTrue(dayThemesXml.contains("<item name=\"android:colorAccent\">@color/genericui_light_accent</item>"))
        assertTrue(dayThemesXml.contains("<item name=\"android:statusBarColor\">@color/genericui_light_primary_dark</item>"))

        val nightThemesXml = nightThemesXmlFile.readText()
        assertTrue(nightThemesXml.contains("<style name=\"Theme.GenericUI.App\" parent=\"@android:style/Theme.Material.NoActionBar\">"))
        assertTrue(nightThemesXml.contains("<item name=\"android:colorPrimary\">@color/genericui_dark_primary</item>"))
        assertTrue(nightThemesXml.contains("<item name=\"android:windowBackground\">@color/genericui_dark_background</item>"))

        // 2. Verify AndroidManifest.xml uses @style/Theme.GenericUI.App by default
        val manifestTask = project.tasks.getByName("generateGenericUiAndroidManifest") as GenericUiGenerateAndroidManifestTask
        manifestTask.generate()
        val manifestXml = manifestTask.outputManifestFile.get().asFile.readText()
        assertTrue(
            manifestXml.contains("android:theme=\"@style/Theme.GenericUI.App\""),
            "AndroidManifest.xml should default to @style/Theme.GenericUI.App"
        )

        // 3. Verify generated launcher descriptors and Android Application emit ThemeConfig(light = ThemeColorSet.fromHex(...), dark = ThemeColorSet.fromHex(...))
        val launchersTask = project.tasks.getByName("generateGenericUiLaunchers") as GenericUiGenerateLaunchersTask
        launchersTask.generateLaunchers()
        val launchersRoot = launchersTask.outputDirectory.get().asFile
        val winDescriptor = java.io.File(launchersRoot, "windows/kotlin/com/example/themed/generated/GeneratedWindowsAppDescriptor.kt").readText()
        val winMain = java.io.File(launchersRoot, "windows/kotlin/com/example/themed/generated/GeneratedWindowsLauncherMain.kt").readText()
        assertTrue(winDescriptor.contains("themeConfig = ThemeConfig("))
        assertTrue(winDescriptor.contains("light = ThemeColorSet.fromHex("))
        assertTrue(winDescriptor.contains("dark = ThemeColorSet.fromHex("))
        assertTrue(winDescriptor.contains("primary = \"#2563eb\""))
        assertTrue(winDescriptor.contains("primary = \"#60a5fa\""))
        assertTrue(winMain.contains("GenericUI.defaultThemeConfig ="))
        assertTrue(winMain.contains("app.setThemeConfig("))

        val androidActivitiesTask = project.tasks.getByName("generateGenericUiAndroidActivities") as GenericUiGenerateAndroidActivitiesTask
        androidActivitiesTask.generate()
        val androidAppKt = java.io.File(androidActivitiesTask.outputDirectory.get().asFile, "com/example/themed/GeneratedAndroidApplication.kt").readText()
        assertTrue(androidAppKt.contains("themeConfig = ThemeConfig("))
        assertTrue(androidAppKt.contains("light = ThemeColorSet.fromHex("))
        assertTrue(androidAppKt.contains("dark = ThemeColorSet.fromHex("))
        assertTrue(androidAppKt.contains("GenericUI.defaultThemeConfig ="))
        assertTrue(androidAppKt.contains("app.setThemeConfig("))

        // 4. Verify Web distribution pre-generates gui/genericui-theme.css and injects <link id="genericui-build-theme-css" ...>
        val webDistTask = project.tasks.getByName("packageGenericUiWebDistribution") as GenericUiWebDistributionTask
        webDistTask.packageWebDistribution()
        val webOutDir = webDistTask.outputDirectory.get().asFile
        val themeCssFile = java.io.File(webOutDir, "gui/genericui-theme.css")
        assertTrue(themeCssFile.isFile, "Expected gui/genericui-theme.css to be generated when theme overrides are configured")
        val themeCss = themeCssFile.readText()
        assertTrue(themeCss.contains(":root, [data-bs-theme=\"light\"]"))
        assertTrue(themeCss.contains("[data-bs-theme=\"dark\"]"))
        assertTrue(themeCss.contains("--bs-primary: #2563eb;"))
        assertTrue(themeCss.contains("--bs-primary-rgb: 37, 99, 235;"))
        assertTrue(themeCss.contains("--bs-primary: #60a5fa;"))
        assertTrue(themeCss.contains(".btn-primary"))
        assertTrue(themeCss.contains(".btn-outline-primary"))
        assertTrue(themeCss.contains(".bg-primary"))
        assertTrue(themeCss.contains(".text-primary"))
        assertTrue(themeCss.contains(".border-primary"))

        val indexHtml = java.io.File(webOutDir, "index.html").readText()
        assertTrue(
            indexHtml.contains("<link id=\"genericui-build-theme-css\" rel=\"stylesheet\" href=\"gui/genericui-theme.css\">"),
            "Expected index.html to inject #genericui-build-theme-css after bootstrap.min.css and genericui.css"
        )
    }
}
