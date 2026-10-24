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
            media()           // transitively enables gpu
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
                ":desktop:shared",
                ":mobile:shared",
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
        assertTrue(generatedKt.contains("CssStyleSheet.precompiled("))
        assertTrue(generatedKt.contains("selector = \".page\""))
        assertTrue(generatedKt.contains("selector = \".page .menu\""))
        assertTrue(generatedKt.contains("selector = \".page .menu:hover\""))
        assertTrue(generatedKt.contains("\"background-color\" to \"#713b15\""))
    }
}
