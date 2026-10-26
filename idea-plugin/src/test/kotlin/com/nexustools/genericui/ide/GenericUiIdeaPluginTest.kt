package com.nexustools.genericui.ide

import com.nexustools.genericui.ide.generator.GenericUiBuildFileInspector
import com.nexustools.genericui.ide.generator.GenericUiGradleScriptGenerator
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

        // Preset: Mobile Only
        panel.selectMobileOnly()
        assertEquals(
            setOf(GenericUiTargetBackend.ANDROID, GenericUiTargetBackend.IOS),
            panel.getSelectedBackends()
        )

        // Preset: Select All (enables Swing and its LWJGL GL/Vulkan sub-checkboxes)
        panel.selectAllBackends()
        assertEquals(GenericUiTargetBackend.allSet(), panel.getSelectedBackends())
        assertTrue(panel.isBackendSelected(GenericUiTargetBackend.SWING))
        assertTrue(panel.swingLwjglGlCheckbox.isEnabled)
        assertTrue(panel.swingVulkanCheckbox.isEnabled)

        // Preset: Clear All & back to All Except Swing
        panel.clearAllBackends()
        assertTrue(panel.getSelectedBackends().isEmpty())

        panel.selectAllExceptSwing()
        assertEquals(GenericUiTargetBackend.allExceptSwingSet(), panel.getSelectedBackends())
    }

    @Test
    fun testCheckboxPanelTransitiveExtensionSelection() {
        val panel = GenericUiTargetCheckboxPanel()
        assertTrue(panel.getSelectedExtensions().isEmpty())

        // Checking THREE_JS should automatically check CANVAS_3D, OVERLAY, CANVAS_2D, and GPU
        panel.setExtensionSelected(GenericUiExtensionModule.THREE_JS, true)
        assertTrue(panel.isExtensionSelected(GenericUiExtensionModule.THREE_JS))
        assertTrue(panel.isExtensionSelected(GenericUiExtensionModule.CANVAS_3D))
        assertTrue(panel.isExtensionSelected(GenericUiExtensionModule.OVERLAY))
        assertTrue(panel.isExtensionSelected(GenericUiExtensionModule.CANVAS_2D))
        assertTrue(panel.isExtensionSelected(GenericUiExtensionModule.GPU))

        // Checking AI_CHAT should automatically check CODE_EDITOR, OVERLAY, and CANVAS_2D
        panel.setExtensionSelected(GenericUiExtensionModule.AI_CHAT, true)
        assertTrue(panel.isExtensionSelected(GenericUiExtensionModule.AI_CHAT))
        assertTrue(panel.isExtensionSelected(GenericUiExtensionModule.CODE_EDITOR))
        assertTrue(panel.isExtensionSelected(GenericUiExtensionModule.OVERLAY))
        assertTrue(panel.isExtensionSelected(GenericUiExtensionModule.CANVAS_2D))

        val preview = panel.getDslPreviewText()
        assertTrue(preview.contains("gpu()"))
        assertTrue(preview.contains("overlay()"))
        assertTrue(preview.contains("canvas2d()"))
        assertTrue(preview.contains("canvas3d()"))
        assertTrue(preview.contains("codeEditor()"))
        assertTrue(preview.contains("aiChat()"))
        assertTrue(preview.contains("threeJs()"))
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

        // 3. Custom Subset: Windows + GTK + Web + Swing
        val customSubsetConfig = allExceptSwingConfig.copy(
            selectedBackends = linkedSetOf(
                GenericUiTargetBackend.WINDOWS,
                GenericUiTargetBackend.GTK,
                GenericUiTargetBackend.SWING,
                GenericUiTargetBackend.WEB
            ),
            swingUseLwjglGL = true,
            swingUseVulkan = true
        )
        val buildCustom = generator.generateBuildGradleKts(customSubsetConfig)
        assertFalse(buildCustom.contains("kotlin {"))
        assertTrue(buildCustom.contains("windows()"))
        assertTrue(buildCustom.contains("gtk()"))
        assertTrue(buildCustom.contains("useLwjglGL = true"))
        assertTrue(buildCustom.contains("useVulkan = true"))
        assertTrue(buildCustom.contains("web()"))
        assertFalse(buildCustom.contains("macos()"))
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
            selectedExtensions = setOf(GenericUiExtensionModule.THREE_JS)
        )
        val initialBuildKts = generator.generateBuildGradleKts(initialConfig)

        val parsed = inspector.inspectBuildGradleKts(initialBuildKts, "InspectorDemo", "/tmp/inspector")
        assertEquals("InspectorDemo", parsed.projectName)
        assertEquals(GenericUiTargetBackend.allExceptSwingSet(), parsed.selectedBackends)
        assertEquals(
            setOf(
                GenericUiExtensionModule.GPU,
                GenericUiExtensionModule.OVERLAY,
                GenericUiExtensionModule.CANVAS_2D,
                GenericUiExtensionModule.CANVAS_3D,
                GenericUiExtensionModule.THREE_JS
            ),
            parsed.selectedExtensions
        )

        // Verify parsing of allNativeDesktop() and allDesktopExceptSwing()
        assertEquals(
            GenericUiTargetBackend.nativeDesktopSet(),
            inspector.parseSelectedBackends("genericui { backends { allNativeDesktop() } }")
        )
        assertEquals(
            GenericUiTargetBackend.nativeDesktopSet(),
            inspector.parseSelectedBackends("genericui { backends { allDesktopExceptSwing() } }")
        )

        // Update to custom subset (Windows + Qt + Android) and add BOX_3D
        val updatedConfig = parsed.copy(
            selectedBackends = linkedSetOf(
                GenericUiTargetBackend.WINDOWS,
                GenericUiTargetBackend.QT,
                GenericUiTargetBackend.ANDROID
            ),
            selectedExtensions = setOf(GenericUiExtensionModule.BOX_3D)
        )
        val updatedBuildKts = inspector.updateBuildGradleKts(initialBuildKts, updatedConfig)
        assertFalse(updatedBuildKts.contains("kotlin {"))

        val reparsed = inspector.inspectBuildGradleKts(updatedBuildKts, "InspectorDemo", "/tmp/inspector")
        assertEquals(
            setOf(
                GenericUiTargetBackend.WINDOWS,
                GenericUiTargetBackend.QT,
                GenericUiTargetBackend.ANDROID
            ),
            reparsed.selectedBackends
        )
        assertTrue(GenericUiExtensionModule.BOX_3D in reparsed.selectedExtensions)
        assertTrue(GenericUiExtensionModule.THREE_JS in reparsed.selectedExtensions)
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
            assertTrue(Files.isRegularFile(createdRoot.resolve("src/commonMain/resources/styles/main.scss")))

            val mainFragmentText = Files.readString(createdRoot.resolve("src/commonMain/kotlin/com/nexustools/sampleide/MainFragment.kt"))
            assertTrue(mainFragmentText.contains("@MainAppFragment"))
            assertTrue(mainFragmentText.contains("class MainFragment : Fragment()"))

            val settingsText = Files.readString(createdRoot.resolve("settings.gradle.kts"))
            assertTrue(settingsText.contains("includeBuild(\"lib/GenericUI\")"))
            assertTrue(settingsText.contains("id(\"com.nexustools.genericui.settings\")"))

            val buildText = Files.readString(createdRoot.resolve("build.gradle.kts"))
            assertFalse(buildText.contains("kotlin {"))
            assertFalse(buildText.contains("entryPoint"))
            assertTrue(buildText.contains("id = \"com.nexustools.sampleide\""))
            assertTrue(buildText.contains("name = \"SampleDockIde\""))
            assertTrue(buildText.contains("baseName = \"SampleDockIde\""))

            // Open Manage Targets Dialog on the scaffolded project and modify checkboxes
            val manageDialog = actions.createManageTargetsDialog(createdRoot)
            assertEquals(
                GenericUiTargetBackend.desktopExceptSwingSet(),
                manageDialog.checkboxPanel.getSelectedBackends()
            )
            assertTrue(manageDialog.checkboxPanel.isExtensionSelected(GenericUiExtensionModule.AI_CHAT))

            // Check WEB and MEDIA checkboxes in the Manage Targets Dialog and apply changes
            manageDialog.checkboxPanel.setBackendSelected(GenericUiTargetBackend.WEB, true)
            manageDialog.checkboxPanel.setExtensionSelected(GenericUiExtensionModule.MEDIA, true)
            manageDialog.applyChanges()

            val reloaded = GenericUiProjectManagerService().loadProjectConfig(createdRoot)
            assertTrue(GenericUiTargetBackend.WEB in reloaded.selectedBackends)
            assertTrue(GenericUiExtensionModule.MEDIA in reloaded.selectedExtensions)
            assertTrue(GenericUiExtensionModule.GPU in reloaded.selectedExtensions)
            assertTrue(GenericUiExtensionModule.OVERLAY in reloaded.selectedExtensions)
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
