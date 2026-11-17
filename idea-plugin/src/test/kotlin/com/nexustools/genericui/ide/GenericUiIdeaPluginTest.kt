package com.nexustools.genericui.ide

import com.nexustools.genericui.ide.generator.GenericUiBuildFileInspector
import com.nexustools.genericui.ide.generator.GenericUiGradleScriptGenerator
import com.nexustools.genericui.ide.generator.GenericUiStarterSourceGenerator
import com.nexustools.genericui.ide.model.GenericUiExtensionModule
import com.nexustools.genericui.ide.model.GenericUiProjectConfig
import com.nexustools.genericui.ide.model.GenericUiProjectTemplateType
import com.nexustools.genericui.ide.model.GenericUiTargetBackend
import com.nexustools.genericui.ide.service.GenericUiPluginActions
import com.nexustools.genericui.ide.service.GenericUiProjectManagerService
import com.nexustools.genericui.ide.ui.GenericUiTargetCheckboxPanel
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GenericUiIdeaPluginTest {

    @Test
    fun testCheckboxPanelDefaultStateAndPresetButtons() {
        val panel = GenericUiTargetCheckboxPanel()

        // Default selection should be allExceptSwing()
        assertEquals(GenericUiTargetBackend.allExceptSwingSet(), panel.getSelectedBackends())
        assertFalse(panel.isBackendSelected(GenericUiTargetBackend.SWING))
        assertFalse(panel.swingLwjglGlCheckbox.isEnabled)
        assertFalse(panel.swingVulkanCheckbox.isEnabled)
        assertTrue(panel.getDslPreviewText().contains("allExceptSwing()"))
        assertTrue(panel.getDslPreviewText().contains("app {"))
        assertTrue(panel.getDslPreviewText().contains("application {"))
        assertTrue(panel.getDslPreviewText().contains("theme {"))
        assertTrue(panel.getDslPreviewText().contains("mode = \"auto\""))
        assertTrue(panel.getDslPreviewText().contains("globalStyleSheet = \"styles/main.scss\""))

        // Preset: Desktop Only (No Swing) / Native Desktop
        panel.selectNativeDesktop()
        assertEquals(
            setOf(
                GenericUiTargetBackend.WINDOWS,
                GenericUiTargetBackend.MACOS,
                GenericUiTargetBackend.GTK,
                GenericUiTargetBackend.QT
            ),
            panel.getSelectedBackends()
        )
        assertTrue(panel.getDslPreviewText().contains("allNativeDesktop()"))

        // Preset: Mobile Only (now includes Android, iOS, and iPadOS)
        panel.selectMobileOnly()
        assertEquals(
            setOf(
                GenericUiTargetBackend.ANDROID,
                GenericUiTargetBackend.IOS,
                GenericUiTargetBackend.IPADOS
            ),
            panel.getSelectedBackends()
        )
        assertTrue(panel.isBackendSelected(GenericUiTargetBackend.IPADOS))

        // Preset: Apple Only (macOS, iOS, iPadOS)
        panel.selectAppleOnly()
        assertEquals(
            setOf(
                GenericUiTargetBackend.MACOS,
                GenericUiTargetBackend.IOS,
                GenericUiTargetBackend.IPADOS
            ),
            panel.getSelectedBackends()
        )

        // Preset: Select All (enables Swing and its LWJGL GL/Vulkan sub-checkboxes, plus IPadOS)
        panel.selectAllBackends()
        assertEquals(GenericUiTargetBackend.allSet(), panel.getSelectedBackends())
        assertTrue(panel.isBackendSelected(GenericUiTargetBackend.SWING))
        assertTrue(panel.isBackendSelected(GenericUiTargetBackend.IPADOS))
        assertTrue(panel.swingLwjglGlCheckbox.isEnabled)
        assertTrue(panel.swingVulkanCheckbox.isEnabled)

        // Preset: Clear All & back to All Except Swing
        panel.clearAllBackends()
        assertTrue(panel.getSelectedBackends().isEmpty())

        panel.selectAllExceptSwing()
        assertEquals(GenericUiTargetBackend.allExceptSwingSet(), panel.getSelectedBackends())
        assertTrue(panel.isBackendSelected(GenericUiTargetBackend.IPADOS))
    }

    @Test
    fun testCheckboxPanelTransitiveExtensionSelection() {
        val panel = GenericUiTargetCheckboxPanel()
        assertTrue(panel.getSelectedExtensions().isEmpty())

        // Checking THREE_JS should automatically check CANVAS_3D, OVERLAY, CANVAS_2D, IMAGE_PROCESSING, and GPU
        panel.setExtensionSelected(GenericUiExtensionModule.THREE_JS, true)
        assertTrue(panel.isExtensionSelected(GenericUiExtensionModule.THREE_JS))
        assertTrue(panel.isExtensionSelected(GenericUiExtensionModule.CANVAS_3D))
        assertTrue(panel.isExtensionSelected(GenericUiExtensionModule.OVERLAY))
        assertTrue(panel.isExtensionSelected(GenericUiExtensionModule.CANVAS_2D))
        assertTrue(panel.isExtensionSelected(GenericUiExtensionModule.IMAGE_PROCESSING))
        assertTrue(panel.isExtensionSelected(GenericUiExtensionModule.GPU))

        // Checking AI_CHAT should automatically check CODE_EDITOR, OVERLAY, CANVAS_2D, IMAGE_PROCESSING, and GPU
        panel.setExtensionSelected(GenericUiExtensionModule.AI_CHAT, true)
        assertTrue(panel.isExtensionSelected(GenericUiExtensionModule.AI_CHAT))
        assertTrue(panel.isExtensionSelected(GenericUiExtensionModule.CODE_EDITOR))
        assertTrue(panel.isExtensionSelected(GenericUiExtensionModule.OVERLAY))
        assertTrue(panel.isExtensionSelected(GenericUiExtensionModule.CANVAS_2D))

        // Checking ENGINE_2D should automatically check ENGINE, GAMEPAD, BOX_2D, CANVAS_3D, CANVAS_2D, IMAGE_PROCESSING, GPU, OVERLAY
        panel.setExtensionSelected(GenericUiExtensionModule.ENGINE_2D, true)
        assertTrue(panel.isExtensionSelected(GenericUiExtensionModule.ENGINE_2D))
        assertTrue(panel.isExtensionSelected(GenericUiExtensionModule.ENGINE))
        assertTrue(panel.isExtensionSelected(GenericUiExtensionModule.GAMEPAD))
        assertTrue(panel.isExtensionSelected(GenericUiExtensionModule.BOX_2D))

        val preview = panel.getDslPreviewText()
        assertTrue(preview.contains("gpu()"))
        assertTrue(preview.contains("imageProcessing()"))
        assertTrue(preview.contains("overlay()"))
        assertTrue(preview.contains("canvas2d()"))
        assertTrue(preview.contains("canvas3d()"))
        assertTrue(preview.contains("box2d()"))
        assertTrue(preview.contains("gamepad()"))
        assertTrue(preview.contains("engine()"))
        assertTrue(preview.contains("engine2d()"))
        assertTrue(preview.contains("codeEditor()"))
        assertTrue(preview.contains("aiChat()"))
        assertTrue(preview.contains("threeJs()"))
    }

    @Test
    fun testAllCategorizedExtensionsDslModulesAndTransitiveDependencies() {
        assertEquals(19, GenericUiExtensionModule.entries.size)
        for (ext in GenericUiExtensionModule.entries) {
            assertTrue(ext.description.isNotBlank(), "Missing description for $ext")
            assertEquals(ext, GenericUiExtensionModule.fromDslMethod(ext.dslMethodName))
            assertEquals(ext, GenericUiExtensionModule.fromDslMethod(ext.gradleModulePath))
            ext.dslModulePath?.let { dslPath ->
                assertEquals(ext, GenericUiExtensionModule.fromDslMethod(dslPath))
            }
        }

        // Verify categorized Gradle module paths
        assertEquals(":extensions:graphics:gpu", GenericUiExtensionModule.GPU.gradleModulePath)
        assertEquals(":extensions:graphics:imageprocessing", GenericUiExtensionModule.IMAGE_PROCESSING.gradleModulePath)
        assertEquals(":extensions:graphics:canvas2d", GenericUiExtensionModule.CANVAS_2D.gradleModulePath)
        assertEquals(":extensions:graphics:canvas2d:dsl", GenericUiExtensionModule.CANVAS_2D.dslModulePath)
        assertEquals(":extensions:graphics:canvas3d", GenericUiExtensionModule.CANVAS_3D.gradleModulePath)
        assertEquals(":extensions:graphics:canvas3d:dsl", GenericUiExtensionModule.CANVAS_3D.dslModulePath)
        assertEquals(":extensions:graphics:threejs", GenericUiExtensionModule.THREE_JS.gradleModulePath)
        assertEquals(":extensions:graphics:threejs:dsl", GenericUiExtensionModule.THREE_JS.dslModulePath)

        assertEquals(":extensions:physics:box2d", GenericUiExtensionModule.BOX_2D.gradleModulePath)
        assertEquals(":extensions:physics:box2d:dsl", GenericUiExtensionModule.BOX_2D.dslModulePath)
        assertEquals(":extensions:physics:box3d", GenericUiExtensionModule.BOX_3D.gradleModulePath)
        assertEquals(":extensions:physics:box3d:dsl", GenericUiExtensionModule.BOX_3D.dslModulePath)

        assertEquals(":extensions:game:gamepad", GenericUiExtensionModule.GAMEPAD.gradleModulePath)
        assertEquals(":extensions:game:gamepad:dsl", GenericUiExtensionModule.GAMEPAD.dslModulePath)
        assertEquals(":extensions:game:engine", GenericUiExtensionModule.ENGINE.gradleModulePath)
        assertEquals(":extensions:game:engine:dsl", GenericUiExtensionModule.ENGINE.dslModulePath)
        assertEquals(":extensions:game:engine2d", GenericUiExtensionModule.ENGINE_2D.gradleModulePath)
        assertEquals(":extensions:game:engine2d:dsl", GenericUiExtensionModule.ENGINE_2D.dslModulePath)
        assertEquals(":extensions:game:engine3d", GenericUiExtensionModule.ENGINE_3D.gradleModulePath)
        assertEquals(":extensions:game:engine3d:dsl", GenericUiExtensionModule.ENGINE_3D.dslModulePath)

        assertEquals(":extensions:media:media", GenericUiExtensionModule.MEDIA.gradleModulePath)
        assertEquals(":extensions:media:media:dsl", GenericUiExtensionModule.MEDIA.dslModulePath)
        assertEquals(":extensions:media:mediacontrols", GenericUiExtensionModule.MEDIA_CONTROLS.gradleModulePath)
        assertEquals(":extensions:media:mediacontrols:dsl", GenericUiExtensionModule.MEDIA_CONTROLS.dslModulePath)

        assertEquals(":extensions:ui:dsl", GenericUiExtensionModule.DSL.gradleModulePath)
        assertEquals(":extensions:ui:overlay", GenericUiExtensionModule.OVERLAY.gradleModulePath)
        assertEquals(":extensions:ui:overlay:dsl", GenericUiExtensionModule.OVERLAY.dslModulePath)
        assertEquals(":extensions:ui:alien", GenericUiExtensionModule.ALIEN.gradleModulePath)
        assertEquals(":extensions:ui:alien:dsl", GenericUiExtensionModule.ALIEN.dslModulePath)
        assertEquals(":extensions:ui:webview", GenericUiExtensionModule.WEB_VIEW.gradleModulePath)
        assertEquals(":extensions:ui:webview:dsl", GenericUiExtensionModule.WEB_VIEW.dslModulePath)
        assertEquals(":extensions:ui:codeeditor", GenericUiExtensionModule.CODE_EDITOR.gradleModulePath)
        assertEquals(":extensions:ui:codeeditor:dsl", GenericUiExtensionModule.CODE_EDITOR.dslModulePath)
        assertEquals(":extensions:ui:aichat", GenericUiExtensionModule.AI_CHAT.gradleModulePath)
        assertEquals(":extensions:ui:aichat:dsl", GenericUiExtensionModule.AI_CHAT.dslModulePath)

        // GPU, ImageProcessing, and UI DSL itself do not have separate :dsl submodules
        assertFalse(GenericUiExtensionModule.GPU.hasDslModule)
        assertFalse(GenericUiExtensionModule.IMAGE_PROCESSING.hasDslModule)
        assertFalse(GenericUiExtensionModule.DSL.hasDslModule)

        // Media depends only on Overlay (not GPU)
        assertEquals(
            setOf(GenericUiExtensionModule.MEDIA, GenericUiExtensionModule.OVERLAY),
            GenericUiExtensionModule.resolveTransitive(setOf(GenericUiExtensionModule.MEDIA))
        )

        // Alien depends on Canvas2D -> ImageProcessing, GPU, Overlay
        assertEquals(
            setOf(
                GenericUiExtensionModule.GPU,
                GenericUiExtensionModule.IMAGE_PROCESSING,
                GenericUiExtensionModule.CANVAS_2D,
                GenericUiExtensionModule.OVERLAY,
                GenericUiExtensionModule.ALIEN
            ),
            GenericUiExtensionModule.resolveTransitive(setOf(GenericUiExtensionModule.ALIEN))
        )

        // Gamepad + Engine + Engine3D + DSL resolve full module paths + companion :dsl submodules
        val engine3dPaths = GenericUiExtensionModule.resolveModulePaths(
            setOf(GenericUiExtensionModule.ENGINE_3D, GenericUiExtensionModule.DSL)
        )
        assertTrue(":extensions:game:gamepad" in engine3dPaths)
        assertTrue(":extensions:game:gamepad:dsl" in engine3dPaths)
        assertTrue(":extensions:game:engine" in engine3dPaths)
        assertTrue(":extensions:game:engine:dsl" in engine3dPaths)
        assertTrue(":extensions:game:engine3d" in engine3dPaths)
        assertTrue(":extensions:game:engine3d:dsl" in engine3dPaths)
        assertTrue(":extensions:physics:box3d" in engine3dPaths)
        assertTrue(":extensions:physics:box3d:dsl" in engine3dPaths)
        assertTrue(":extensions:graphics:threejs" in engine3dPaths)
        assertTrue(":extensions:graphics:threejs:dsl" in engine3dPaths)
        assertTrue(":extensions:ui:dsl" in engine3dPaths)
        assertFalse(":extensions:graphics:gpu:dsl" in engine3dPaths)
    }

    @Test
    fun testConsolidatedCoreModulesAndIpadosTargetSupport() {
        assertEquals(9, GenericUiTargetBackend.entries.size)
        assertEquals(GenericUiTargetBackend.IPADOS, GenericUiTargetBackend.fromId("ipados"))
        assertEquals(GenericUiTargetBackend.IPADOS, GenericUiTargetBackend.fromId("IPadOS"))
        assertTrue(GenericUiTargetBackend.IPADOS.isMobile)
        assertTrue(GenericUiTargetBackend.IPADOS.isApple)
        assertTrue(GenericUiTargetBackend.IPADOS.isIosFamily)
        assertTrue(GenericUiTargetBackend.IPADOS.isSystem)
        assertFalse(GenericUiTargetBackend.IPADOS.isDesktop)
        assertEquals(":mobile", GenericUiTargetBackend.IPADOS.gradleModulePath)
        assertEquals("ipados", GenericUiTargetBackend.IPADOS.dslMethodName)

        // IPadOS alone requires :common, :system, :apple, :mobile, and :tests when testing is enabled
        val ipadosOnly = GenericUiProjectConfig(
            projectName = "TabletApp",
            packageName = "com.example.tablet",
            projectRootPath = "/tmp/tablet",
            selectedBackends = setOf(GenericUiTargetBackend.IPADOS),
            enableTesting = true
        )
        assertEquals(
            setOf(":common", ":system", ":apple", ":mobile", ":tests"),
            ipadosOnly.requiredCoreModules()
        )
        assertTrue(ipadosOnly.requiresAppleModule())
        assertTrue(ipadosOnly.requiresSharedMobile())
        assertFalse(ipadosOnly.requiresJvmModule())
        assertFalse(ipadosOnly.requiresDesktopModule())
        assertFalse(ipadosOnly.requiresWebModule())

        // Web only requires :common, :web (and :tests when enabled)
        val webOnly = ipadosOnly.copy(
            selectedBackends = setOf(GenericUiTargetBackend.WEB),
            enableTesting = false
        )
        assertEquals(setOf(":common", ":web"), webOnly.requiredCoreModules())

        // Swing + Android requires :jvm alongside :desktop/:mobile
        val jvmConfig = ipadosOnly.copy(
            selectedBackends = setOf(GenericUiTargetBackend.SWING, GenericUiTargetBackend.ANDROID)
        )
        assertEquals(
            setOf(":common", ":system", ":jvm", ":desktop", ":mobile", ":tests"),
            jvmConfig.requiredCoreModules()
        )
    }

    @Test
    fun testGradleScriptGeneratorReliesEntirelyOnGenericUiDsl() {
        val generator = GenericUiGradleScriptGenerator()

        // 1. All Except Swing
        val allExceptSwingConfig = GenericUiProjectConfig(
            projectName = "StudioApp",
            packageName = "com.nexustools.studio",
            projectRootPath = "/tmp/studio",
            selectedBackends = GenericUiTargetBackend.allExceptSwingSet(),
            selectedExtensions = setOf(GenericUiExtensionModule.AI_CHAT, GenericUiExtensionModule.MEDIA)
        )
        val buildAllExceptSwing = generator.generateBuildGradleKts(allExceptSwingConfig)
        assertFalse(buildAllExceptSwing.contains("kotlin {"))
        assertFalse(buildAllExceptSwing.contains("entryPoint"))
        assertTrue(buildAllExceptSwing.contains("id = \"com.nexustools.studio\""))
        assertTrue(buildAllExceptSwing.contains("name = \"StudioApp\""))
        assertTrue(buildAllExceptSwing.contains("baseName = \"StudioApp\""))
        assertTrue(buildAllExceptSwing.contains("theme {"))
        assertTrue(buildAllExceptSwing.contains("mode = \"auto\""))
        assertTrue(buildAllExceptSwing.contains("globalStyleSheet = \"styles/main.scss\""))
        assertTrue(buildAllExceptSwing.contains("allExceptSwing()"))
        assertTrue(buildAllExceptSwing.contains("gpu()"))
        assertTrue(buildAllExceptSwing.contains("overlay()"))
        assertTrue(buildAllExceptSwing.contains("media()"))
        assertTrue(buildAllExceptSwing.contains("canvas2d()"))
        assertTrue(buildAllExceptSwing.contains("codeEditor()"))
        assertTrue(buildAllExceptSwing.contains("aiChat()"))

        // 2. Desktop Only Except Swing (allNativeDesktop)
        val desktopOnlyConfig = allExceptSwingConfig.copy(
            selectedBackends = GenericUiTargetBackend.nativeDesktopSet()
        )
        val buildDesktopOnly = generator.generateBuildGradleKts(desktopOnlyConfig)
        assertFalse(buildDesktopOnly.contains("kotlin {"))
        assertTrue(buildDesktopOnly.contains("allNativeDesktop()"))

        // 3. Custom Subset: Windows + GTK + Web + Swing + IPadOS
        val customSubsetConfig = allExceptSwingConfig.copy(
            selectedBackends = linkedSetOf(
                GenericUiTargetBackend.WINDOWS,
                GenericUiTargetBackend.GTK,
                GenericUiTargetBackend.SWING,
                GenericUiTargetBackend.IPADOS,
                GenericUiTargetBackend.WEB
            ),
            swingUseLwjglGL = true,
            swingUseVulkan = true,
            windowAlwaysOnTop = true,
            windowAspectRatioWidth = 16,
            windowAspectRatioHeight = 9,
            appleIcon = "icons/apple-app.png",
            macIcon = "icons/mac-app.icns",
            iosIcon = "icons/ios-app.png",
            ipadosIcon = "icons/ipados-app.png",
            selectedExtensions = setOf(GenericUiExtensionModule.ENGINE_2D, GenericUiExtensionModule.ALIEN)
        )
        val buildCustom = generator.generateBuildGradleKts(customSubsetConfig)
        assertFalse(buildCustom.contains("kotlin {"))
        assertTrue(buildCustom.contains("windows()"))
        assertTrue(buildCustom.contains("gtk()"))
        assertTrue(buildCustom.contains("ipados()"))
        assertTrue(buildCustom.contains("useLwjglGL = true"))
        assertTrue(buildCustom.contains("useVulkan = true"))
        assertTrue(buildCustom.contains("web()"))
        assertTrue(buildCustom.contains("appleIcon = \"icons/apple-app.png\""))
        assertTrue(buildCustom.contains("window {"))
        assertTrue(buildCustom.contains("alwaysOnTop = true"))
        assertTrue(buildCustom.contains("aspectRatio(16, 9)"))
        assertTrue(buildCustom.contains("macos {\n        icon(\"icons/mac-app.icns\")"))
        assertTrue(buildCustom.contains("ios {\n        icon(\"icons/ios-app.png\")"))
        assertTrue(buildCustom.contains("ipados {\n        icon(\"icons/ipados-app.png\")"))
        assertTrue(buildCustom.contains("gamepad()"))
        assertTrue(buildCustom.contains("engine()"))
        assertTrue(buildCustom.contains("engine2d()"))
        assertTrue(buildCustom.contains("alien()"))
        assertTrue(buildCustom.contains("testing {"))
        assertTrue(buildCustom.contains("enabled = true"))
        assertFalse(buildCustom.contains("qt()"))
    }

    @Test
    fun testBuildFileInspectorParsesAndUpdatesInPlace() {
        val generator = GenericUiGradleScriptGenerator()
        val inspector = GenericUiBuildFileInspector(generator)

        val initialConfig = GenericUiProjectConfig(
            projectName = "InspectorDemo",
            packageName = "com.example.inspector",
            projectRootPath = "/tmp/inspector",
            selectedBackends = GenericUiTargetBackend.allExceptSwingSet(),
            selectedExtensions = setOf(GenericUiExtensionModule.THREE_JS),
            windowAlwaysOnTop = true,
            windowAspectRatioWidth = 16,
            windowAspectRatioHeight = 9,
            appleIcon = "assets/apple.png",
            macIcon = "assets/mac.png",
            iosIcon = "assets/ios.png",
            ipadosIcon = "assets/ipados.png",
            enableTesting = false
        )
        val initialBuildKts = generator.generateBuildGradleKts(initialConfig)

        val parsed = inspector.inspectBuildGradleKts(initialBuildKts, "InspectorDemo", "/tmp/inspector")
        assertEquals("InspectorDemo", parsed.projectName)
        assertEquals("auto", parsed.themeMode)
        assertEquals("styles/main.scss", parsed.globalStyleSheet)
        assertEquals(GenericUiTargetBackend.allExceptSwingSet(), parsed.selectedBackends)
        assertTrue(parsed.windowAlwaysOnTop)
        assertEquals(16, parsed.windowAspectRatioWidth)
        assertEquals(9, parsed.windowAspectRatioHeight)
        assertEquals("assets/apple.png", parsed.appleIcon)
        assertEquals("assets/mac.png", parsed.macIcon)
        assertEquals("assets/ios.png", parsed.iosIcon)
        assertEquals("assets/ipados.png", parsed.ipadosIcon)
        assertFalse(parsed.enableTesting)
        assertEquals(
            setOf(
                GenericUiExtensionModule.GPU,
                GenericUiExtensionModule.IMAGE_PROCESSING,
                GenericUiExtensionModule.CANVAS_2D,
                GenericUiExtensionModule.CANVAS_3D,
                GenericUiExtensionModule.THREE_JS,
                GenericUiExtensionModule.OVERLAY
            ),
            parsed.selectedExtensions
        )

        // Verify parsing of window { alwaysOnTop; aspectRatio(16, 9) }, allMobile() -> ipados, theme, and game extensions
        val customThemeParsed = inspector.inspectBuildGradleKts(
            """
            genericui {
                application {
                    baseName = "GameDemo"
                    appleIcon("icons/shared-apple.png")
                }
                window {
                    alwaysOnTop
                    aspectRatio(16, 9)
                }
                theme {
                    mode = "dark"
                    styleSheet = "styles/theme-override.scss"
                }
                backends {
                    allMobile()
                    swing {
                        useLwjglGL = false
                        useVulkan = false
                    }
                }
                ipados {
                    icon = "icons/ipad-pro.png"
                }
                extensions {
                    engine3d()
                    dsl()
                }
            }
            """.trimIndent()
        )
        assertEquals("dark", customThemeParsed.themeMode)
        assertEquals("styles/theme-override.scss", customThemeParsed.globalStyleSheet)
        assertTrue(customThemeParsed.windowAlwaysOnTop)
        assertEquals(16, customThemeParsed.windowAspectRatioWidth)
        assertEquals(9, customThemeParsed.windowAspectRatioHeight)
        assertEquals("icons/shared-apple.png", customThemeParsed.appleIcon)
        assertEquals("icons/ipad-pro.png", customThemeParsed.ipadosIcon)
        assertTrue(GenericUiTargetBackend.IPADOS in customThemeParsed.selectedBackends)
        assertTrue(GenericUiTargetBackend.ANDROID in customThemeParsed.selectedBackends)
        assertTrue(GenericUiTargetBackend.IOS in customThemeParsed.selectedBackends)
        assertTrue(GenericUiTargetBackend.SWING in customThemeParsed.selectedBackends)
        assertFalse(customThemeParsed.swingUseLwjglGL)
        assertFalse(customThemeParsed.swingUseVulkan)
        assertTrue(GenericUiExtensionModule.GAMEPAD in customThemeParsed.selectedExtensions)
        assertTrue(GenericUiExtensionModule.ENGINE in customThemeParsed.selectedExtensions)
        assertTrue(GenericUiExtensionModule.ENGINE_3D in customThemeParsed.selectedExtensions)
        assertTrue(GenericUiExtensionModule.BOX_3D in customThemeParsed.selectedExtensions)
        assertTrue(GenericUiExtensionModule.DSL in customThemeParsed.selectedExtensions)

        // Verify parsing of allNativeDesktop() and allDesktopExceptSwing()
        assertEquals(
            GenericUiTargetBackend.nativeDesktopSet(),
            inspector.parseSelectedBackends("genericui { backends { allNativeDesktop() } }")
        )
        assertEquals(
            GenericUiTargetBackend.nativeDesktopSet(),
            inspector.parseSelectedBackends("genericui { backends { allDesktopExceptSwing() } }")
        )
        assertEquals(
            setOf(GenericUiTargetBackend.MACOS, GenericUiTargetBackend.IPADOS),
            inspector.parseSelectedBackends("genericui { backends { mac(); ipad() } }")
        )
        assertTrue(
            GenericUiExtensionModule.ENGINE_2D in inspector.parseSelectedExtensions(
                "genericui { extensions { engine2D() } }"
            )
        )

        // Update to custom subset (Windows + Qt + Android + IPadOS) and add BOX_3D + GAMEPAD
        val updatedConfig = parsed.copy(
            selectedBackends = linkedSetOf(
                GenericUiTargetBackend.WINDOWS,
                GenericUiTargetBackend.QT,
                GenericUiTargetBackend.ANDROID,
                GenericUiTargetBackend.IPADOS
            ),
            selectedExtensions = setOf(GenericUiExtensionModule.BOX_3D, GenericUiExtensionModule.GAMEPAD),
            enableTesting = true
        )
        val updatedBuildKts = inspector.updateBuildGradleKts(initialBuildKts, updatedConfig)
        assertFalse(updatedBuildKts.contains("kotlin {"))

        val reparsed = inspector.inspectBuildGradleKts(updatedBuildKts, "InspectorDemo", "/tmp/inspector")
        assertEquals(
            setOf(
                GenericUiTargetBackend.WINDOWS,
                GenericUiTargetBackend.QT,
                GenericUiTargetBackend.ANDROID,
                GenericUiTargetBackend.IPADOS
            ),
            reparsed.selectedBackends
        )
        assertTrue(GenericUiExtensionModule.BOX_3D in reparsed.selectedExtensions)
        assertTrue(GenericUiExtensionModule.THREE_JS in reparsed.selectedExtensions)
        assertTrue(GenericUiExtensionModule.GAMEPAD in reparsed.selectedExtensions)
        assertTrue(reparsed.enableTesting)
    }

    @Test
    fun testProjectManagerScaffoldsAndUpdatesProjectInTempDir() {
        val tempDir = Files.createTempDirectory("genericui_idea_plugin_test_")
        try {
            val actions = GenericUiPluginActions()
            val initialConfig = GenericUiProjectConfig(
                projectName = "SampleDockIde",
                packageName = "com.nexustools.sampleide",
                projectRootPath = tempDir.toString(),
                genericUiLibPath = "",
                selectedBackends = GenericUiTargetBackend.desktopExceptSwingSet(),
                selectedExtensions = setOf(GenericUiExtensionModule.CODE_EDITOR, GenericUiExtensionModule.AI_CHAT),
                templateType = GenericUiProjectTemplateType.DOCK_WORKSPACE
            )

            val wizard = actions.createNewProjectWizard(initialConfig)
            assertTrue(wizard.validateWizardInputs().isEmpty())
            val createdRoot = wizard.finishAndCreateProject()

            assertTrue(Files.isRegularFile(createdRoot.resolve("settings.gradle.kts")))
            assertTrue(Files.isRegularFile(createdRoot.resolve("build.gradle.kts")))
            assertTrue(Files.isRegularFile(createdRoot.resolve("gradle.properties")))
            assertFalse(Files.exists(createdRoot.resolve("src/commonMain/kotlin/com/nexustools/sampleide/Main.kt")))
            assertTrue(Files.isRegularFile(createdRoot.resolve("src/commonMain/kotlin/com/nexustools/sampleide/MainFragment.kt")))
            assertTrue(Files.isRegularFile(createdRoot.resolve("src/commonTest/kotlin/com/nexustools/sampleide/MainFragmentTest.kt")))
            assertTrue(Files.isRegularFile(createdRoot.resolve("src/commonMain/resources/styles/main.scss")))

            val mainFragmentText = Files.readString(createdRoot.resolve("src/commonMain/kotlin/com/nexustools/sampleide/MainFragment.kt"))
            assertTrue(mainFragmentText.contains("@MainAppFragment"))
            assertTrue(mainFragmentText.contains("class MainFragment : Fragment()"))
            assertTrue(mainFragmentText.contains("getBootstrap().apply {"))
            assertTrue(mainFragmentText.contains("getStyle().apply {"))
            assertFalse(mainFragmentText.lowercase().contains("tint"))

            val mainFragmentTestText = Files.readString(createdRoot.resolve("src/commonTest/kotlin/com/nexustools/sampleide/MainFragmentTest.kt"))
            assertTrue(mainFragmentTestText.contains("import com.nexustools.genericui.tests.JestElectricRunner"))
            assertTrue(mainFragmentTestText.contains("JestElectricRunner.runSuite"))
            assertTrue(mainFragmentTestText.contains("launchFragment { MainFragment() }"))

            val styleScssText = Files.readString(createdRoot.resolve("src/commonMain/resources/styles/main.scss"))
            assertFalse(styleScssText.lowercase().contains("tint"))

            val starterGenerator = GenericUiStarterSourceGenerator()
            val dslFragmentText = starterGenerator.generateMainFragmentKt(
                initialConfig.copy(selectedExtensions = initialConfig.selectedExtensions + GenericUiExtensionModule.DSL)
            )
            assertTrue(dslFragmentText.contains("import com.nexustools.genericui.ext.dsl.bootstrap"))
            assertTrue(dslFragmentText.contains("import com.nexustools.genericui.ext.dsl.style"))
            assertTrue(dslFragmentText.contains("bootstrap {"))
            assertTrue(dslFragmentText.contains("style {"))
            assertFalse(dslFragmentText.lowercase().contains("tint"))

            // Verify Game 2D & Game 3D starter generation (with & without :dsl)
            val game2dDsl = starterGenerator.generateMainFragmentKt(
                initialConfig.copy(
                    templateType = GenericUiProjectTemplateType.GAME_2D,
                    selectedExtensions = setOf(GenericUiExtensionModule.ENGINE_2D, GenericUiExtensionModule.DSL)
                )
            )
            assertTrue(game2dDsl.contains("import com.nexustools.genericui.ext.engine2d.dsl.game2DCanvas"))
            assertTrue(game2dDsl.contains("game2DCanvas("))

            val game3dCore = starterGenerator.generateMainFragmentKt(
                initialConfig.copy(
                    templateType = GenericUiProjectTemplateType.GAME_3D,
                    selectedExtensions = setOf(GenericUiExtensionModule.ENGINE_3D)
                )
            )
            assertTrue(game3dCore.contains("import com.nexustools.genericui.ext.engine3d.Game3DCanvasHost"))
            assertTrue(game3dCore.contains("Game3DCanvasHost()"))

            val settingsText = Files.readString(createdRoot.resolve("settings.gradle.kts"))
            assertTrue(settingsText.contains("includeBuild(\"lib/GenericUI\")"))
            assertTrue(settingsText.contains("id(\"com.nexustools.genericui.settings\")"))

            val buildText = Files.readString(createdRoot.resolve("build.gradle.kts"))
            assertFalse(buildText.contains("kotlin {"))
            assertFalse(buildText.contains("entryPoint"))
            assertTrue(buildText.contains("id = \"com.nexustools.sampleide\""))
            assertTrue(buildText.contains("name = \"SampleDockIde\""))
            assertTrue(buildText.contains("baseName = \"SampleDockIde\""))
            assertTrue(buildText.contains("theme {"))
            assertTrue(buildText.contains("mode = \"auto\""))
            assertTrue(buildText.contains("globalStyleSheet = \"styles/main.scss\""))
            assertTrue(buildText.contains("testing {"))

            // Open Manage Targets Dialog on the scaffolded project and modify checkboxes + window/Apple icon options
            val manageDialog = actions.createManageTargetsDialog(createdRoot)
            assertEquals(
                GenericUiTargetBackend.desktopExceptSwingSet(),
                manageDialog.checkboxPanel.getSelectedBackends()
            )
            assertTrue(manageDialog.checkboxPanel.isExtensionSelected(GenericUiExtensionModule.AI_CHAT))

            // Check WEB, IPADOS, MEDIA, and ENGINE_2D, enable 16:9 alwaysOnTop window, and set Apple icons
            manageDialog.checkboxPanel.setBackendSelected(GenericUiTargetBackend.WEB, true)
            manageDialog.checkboxPanel.setBackendSelected(GenericUiTargetBackend.IPADOS, true)
            manageDialog.checkboxPanel.setExtensionSelected(GenericUiExtensionModule.MEDIA, true)
            manageDialog.checkboxPanel.setExtensionSelected(GenericUiExtensionModule.ENGINE_2D, true)
            manageDialog.checkboxPanel.windowAlwaysOnTopCheckbox.isSelected = true
            manageDialog.checkboxPanel.setSelectedWindowAspectRatio(16, 9)
            manageDialog.checkboxPanel.appleIconField.text = "icons/apple.png"
            manageDialog.checkboxPanel.ipadosIconField.text = "icons/ipados.png"
            manageDialog.applyChanges()

            val reloaded = GenericUiProjectManagerService().loadProjectConfig(createdRoot)
            assertTrue(GenericUiTargetBackend.WEB in reloaded.selectedBackends)
            assertTrue(GenericUiTargetBackend.IPADOS in reloaded.selectedBackends)
            assertTrue(GenericUiExtensionModule.MEDIA in reloaded.selectedExtensions)
            assertTrue(GenericUiExtensionModule.ENGINE_2D in reloaded.selectedExtensions)
            assertTrue(GenericUiExtensionModule.ENGINE in reloaded.selectedExtensions)
            assertTrue(GenericUiExtensionModule.GAMEPAD in reloaded.selectedExtensions)
            assertTrue(GenericUiExtensionModule.BOX_2D in reloaded.selectedExtensions)
            assertTrue(GenericUiExtensionModule.GPU in reloaded.selectedExtensions)
            assertTrue(GenericUiExtensionModule.OVERLAY in reloaded.selectedExtensions)
            assertTrue(reloaded.windowAlwaysOnTop)
            assertEquals(16, reloaded.windowAspectRatioWidth)
            assertEquals(9, reloaded.windowAspectRatioHeight)
            assertEquals("icons/apple.png", reloaded.appleIcon)
            assertEquals("icons/ipados.png", reloaded.ipadosIcon)
        } finally {
            Files.deleteIfExists(tempDir.resolve("lib/GenericUI"))
            deleteDirectorySafe(tempDir)
        }
    }

    @Test
    fun testPluginXmlDescriptorIsPackaged() {
        val resourceStream = javaClass.classLoader.getResourceAsStream("META-INF/plugin.xml")
        assertNotNull(resourceStream, "META-INF/plugin.xml must be present on the plugin classpath")
        val xmlContent = resourceStream.bufferedReader().use { it.readText() }
        assertTrue(xmlContent.contains("<id>com.nexustools.genericui.idea</id>"))
        assertTrue(xmlContent.contains("GenericUI.NewProject"))
        assertTrue(xmlContent.contains("GenericUI.ManageTargets"))
        assertTrue(xmlContent.contains("ipados"))
        assertTrue(xmlContent.contains("gamepad"))
        assertTrue(xmlContent.contains("engine2d"))
        assertTrue(xmlContent.contains("engine3d"))
        assertTrue(xmlContent.contains("JestElectricRunner"))
    }

    private fun deleteDirectorySafe(root: Path) {
        if (!Files.exists(root)) return
        Files.deleteIfExists(root.resolve("lib/GenericUI"))
        Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (dir != root && (Files.isSymbolicLink(dir) || attrs.isOther)) {
                    Files.deleteIfExists(dir)
                    return FileVisitResult.SKIP_SUBTREE
                }
                return FileVisitResult.CONTINUE
            }

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                Files.deleteIfExists(file)
                return FileVisitResult.CONTINUE
            }

            override fun postVisitDirectory(dir: Path, exc: java.io.IOException?): FileVisitResult {
                Files.deleteIfExists(dir)
                return FileVisitResult.CONTINUE
            }
        })
    }
}
