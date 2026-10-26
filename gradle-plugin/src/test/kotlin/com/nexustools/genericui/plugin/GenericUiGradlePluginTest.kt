package com.nexustools.genericui.plugin

import org.gradle.testfixtures.ProjectBuilder
import kotlin.test.Test
import kotlin.test.assertNotNull
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
        assertTrue(ext.backends.swingConfig.targetClassifiers.size == 6)
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
        assertTrue(generatedKt.contains("object StylePrecompiledStyleSheet : PrecompiledStyleSheet"))
        assertTrue(generatedKt.contains("override val cssResourcePath: String = \"style.css\""))
        assertTrue(generatedKt.contains("CssStyleSheet.precompiled("))
        assertTrue(generatedKt.contains("selector = \".page\""))
        assertTrue(generatedKt.contains("selector = \".page .menu\""))
        assertTrue(generatedKt.contains("selector = \".page .menu:hover\""))
        assertTrue(generatedKt.contains("\"background-color\" to \"#713b15\""))
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
        stylesSubDir.mkdirs()

        java.io.File(resourcesDir, "_tokens.scss").writeText("\$brand-bg: #112233;\n")
        java.io.File(resourcesDir, "style.scss").writeText(
            """
            @import "tokens";
            body {
                background: ${'$'}brand-bg;
                .hero {
                    color: #ffffff;
                }
            }
            """.trimIndent()
        )
        java.io.File(stylesSubDir, "app.scss").writeText(
            """
            ${'$'}accent: #ff6600;
            genericui:root {
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
        assertTrue(styleCssContent.contains("body .hero"), "Compiled web style.css should flatten nested SASS selectors")
        assertTrue(rootAppCss.readText().contains("#ff6600"), "Compiled web app.css should resolve SASS variables")
        assertTrue(rootAppCss.readText().contains("genericui\\:root .card"), "Compiled web app.css should escape genericui:<tag> selectors")
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

        val expectedBuildTargetTasks = listOf(
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
            "webProduction",
            "packageGenericUiWebDistribution"
        )
        for (taskName in expectedBuildTargetTasks) {
            assertNotNull(
                project.tasks.findByName(taskName),
                "Expected GenericUI build target task '$taskName' to be registered"
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

        val webDistTask = project.tasks.getByName("packageGenericUiWebDistribution") as GenericUiWebDistributionTask
        webDistTask.packageWebDistribution()

        val distDir = webDistTask.outputDirectory.get().asFile
        val indexHtmlFile = java.io.File(distDir, "index.html")
        val loaderJsFile = java.io.File(distDir, "genericui-loader.js")
        val namespaceXmlFile = java.io.File(distDir, "genericui-namespace.xml")
        val genericUiCssFile = java.io.File(distDir, "genericui.css")
        val stagedIconFile = java.io.File(distDir, "web-icon.svg")

        assertTrue(indexHtmlFile.exists(), "index.html should be generated in build/dist/web")
        assertTrue(loaderJsFile.exists(), "genericui-loader.js should be generated in build/dist/web")
        assertTrue(namespaceXmlFile.exists(), "genericui-namespace.xml should be generated in build/dist/web")
        assertTrue(genericUiCssFile.exists(), "genericui.css should be generated in build/dist/web")
        assertTrue(stagedIconFile.exists(), "web-icon.svg should be staged in build/dist/web")

        val namespaceXml = namespaceXmlFile.readText()
        assertTrue(namespaceXml.contains("<namespaceSpec xmlns=\"https://nexustools.com/ns/genericui/spec\" prefix=\"genericui\" uri=\"https://nexustools.com/ns/genericui\" version=\"1.0\">"))
        assertTrue(namespaceXml.contains("tag=\"genericui:root\""))
        assertTrue(namespaceXml.contains("tag=\"genericui:window\""))
        assertTrue(namespaceXml.contains("tag=\"genericui:fragment\""))
        assertTrue(namespaceXml.contains("tag=\"genericui:fragment-container\""))
        assertTrue(namespaceXml.contains("tag=\"genericui:scrollfragment\""))
        assertTrue(namespaceXml.contains("tag=\"genericui:menubar\""))
        assertTrue(namespaceXml.contains("tag=\"genericui:dockworkspace\""))

        val genericUiCss = genericUiCssFile.readText()
        assertTrue(genericUiCss.contains("genericui\\:root"))
        assertTrue(genericUiCss.contains("hr.dropdown-divider,\ngenericui\\:menuseparator"))
        assertTrue(genericUiCss.contains("genericui\\:scrollfragment"))

        val html = indexHtmlFile.readText()
        assertTrue(html.contains("xmlns:genericui=\"https://nexustools.com/ns/genericui\""))
        assertTrue(html.contains("<link id=\"genericui-namespace-spec\" rel=\"namespace\" data-namespace-prefix=\"genericui\" data-namespace-uri=\"https://nexustools.com/ns/genericui\" type=\"application/xml\" href=\"genericui-namespace.xml?"))
        assertTrue(html.contains("<link id=\"genericui-css\" rel=\"stylesheet\" href=\"genericui.css?"))
        assertTrue(html.contains("<genericui:root></genericui:root>"))
        assertTrue(!html.contains("<div id=\"genericui-app\""))
        assertTrue(html.contains("<link id=\"genericui-favicon\" rel=\"icon\" type=\"image/svg+xml\" href=\"web-icon.svg\">"))
        assertTrue(html.contains("<img id=\"genericui-splash-icon\" src=\"web-icon.svg\""))
        assertTrue(html.contains("width=\"64\" height=\"64\""))
        assertTrue(html.contains("id=\"genericui-splash-progress-bar\""))
        assertTrue(html.contains("id=\"genericui-splash-error-stack\""))
        assertTrue(html.contains("<script type=\"loader-wasm\" src=\"wasm/test-web-studio-wasm-js.js?"))
        assertTrue(html.contains("<script type=\"loader-js\" src=\"js/test-web-studio.js?"))
        assertTrue(html.contains("<script id=\"genericui-loader-script\" src=\"genericui-loader.js?"))
        assertTrue(html.contains("src=\"https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/js/bootstrap.bundle.min.js\""))
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

        // 1. Verify Web Distribution (manifest.webmanifest + installable WebApp HTML tags + themeColor)
        val webDistTask = project.tasks.getByName("packageGenericUiWebDistribution") as GenericUiWebDistributionTask
        webDistTask.packageWebDistribution()

        val distDir = webDistTask.outputDirectory.get().asFile
        val manifestFile = java.io.File(distDir, "manifest.webmanifest")
        val indexHtmlFile = java.io.File(distDir, "index.html")

        assertTrue(manifestFile.exists(), "manifest.webmanifest should be generated when allowInstallAsWebApp or webManifest is enabled")
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

        val html = indexHtmlFile.readText()
        assertTrue(html.contains("<link id=\"genericui-web-manifest\" rel=\"manifest\" href=\"manifest.webmanifest?"))
        assertTrue(html.contains("<meta id=\"genericui-theme-color\" name=\"theme-color\" content=\"#2A80FF\">"))
        assertTrue(html.contains("<meta id=\"genericui-mobile-web-app-capable\" name=\"mobile-web-app-capable\" content=\"yes\">"))
        assertTrue(html.contains("<meta id=\"genericui-apple-mobile-web-app-capable\" name=\"apple-mobile-web-app-capable\" content=\"yes\">"))
        assertTrue(html.contains("<meta id=\"genericui-application-name\" name=\"application-name\" content=\"SampleWebStudio\">"))
        assertTrue(html.contains("<link id=\"genericui-apple-touch-icon\" rel=\"apple-touch-icon\" href=\"web-icon.svg\">"))

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
        assertTrue(rKtText.contains("val theme_color: ResourceId"))
        assertTrue(rKtText.contains("val colorPrimary: ResourceId"))

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

        // 1. Web distribution should stage and reference the default icon.svg
        val webDistTask = project.tasks.getByName("packageGenericUiWebDistribution") as GenericUiWebDistributionTask
        webDistTask.packageWebDistribution()
        val distDir = webDistTask.outputDirectory.get().asFile
        val defaultWebSvg = java.io.File(distDir, "icon.svg")
        assertTrue(defaultWebSvg.exists(), "Default GenericUI icon.svg should be staged in build/dist/web when no icon is configured")
        val svgContent = defaultWebSvg.readText()
        assertTrue(svgContent.contains("macTopClip") && svgContent.contains("gtkTopClip") && svgContent.contains("winTopClip"))
        val indexHtml = java.io.File(distDir, "index.html").readText()
        assertTrue(indexHtml.contains("<link id=\"genericui-favicon\" rel=\"icon\" type=\"image/svg+xml\" href=\"icon.svg\">"))
        assertTrue(indexHtml.contains("<img id=\"genericui-splash-icon\" src=\"icon.svg\""))

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
        assertTrue(rKtFile.readText().contains("val icon: ResourceId"), "R.drawable.icon should be generated by default")
        val backendResOut = resTask.backendOutputDirectory.get().asFile
        assertTrue(java.io.File(backendResOut, "windows/icon.ico").exists(), "Windows backend resources should include icon.ico")
        assertTrue(java.io.File(backendResOut, "android/res/drawable/icon.png").exists(), "Android backend resources should include icon.png")
        assertTrue(java.io.File(backendResOut, "apple/Assets.xcassets/AppIcon.appiconset/icon.png").exists(), "Apple backend resources should include icon.png")
        assertTrue(java.io.File(backendResOut, "linux/drawable/icon.png").exists(), "Linux backend resources should include icon.png")
        assertTrue(java.io.File(backendResOut, "web/icon.png").exists(), "Web/Swing backend resources should include icon.png")
        assertTrue(java.io.File(backendResOut, "web/icon.ico").exists(), "Web/Swing backend resources should include icon.ico")
    }
}
