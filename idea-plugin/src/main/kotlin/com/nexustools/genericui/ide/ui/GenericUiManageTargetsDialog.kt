package com.nexustools.genericui.ide.ui

import com.nexustools.genericui.ide.model.GenericUiProjectConfig
import com.nexustools.genericui.ide.service.GenericUiProjectManagerService
import java.awt.BorderLayout
import java.nio.file.Path
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * Project Management dialog panel (`Tools -> GenericUI -> Manage GenericUI Targets & Extensions...`)
 * that inspects an existing project's `build.gradle.kts`, pre-populates [GenericUiTargetCheckboxPanel]
 * with the currently enabled target backends and extensions, and writes the updated `build.gradle.kts`
 * when the user applies changes.
 */
class GenericUiManageTargetsDialog(
    val projectDir: Path,
    private val projectManagerService: GenericUiProjectManagerService = GenericUiProjectManagerService()
) : JPanel(BorderLayout(10, 10)) {

    val checkboxPanel: GenericUiTargetCheckboxPanel = GenericUiTargetCheckboxPanel()
    private var loadedConfig: GenericUiProjectConfig

    init {
        loadedConfig = projectManagerService.loadProjectConfig(projectDir)
        checkboxPanel.applyProjectConfig(loadedConfig)

        val headerPanel = JPanel(BorderLayout()).apply {
            border = BorderFactory.createTitledBorder("Managing Existing GenericUI Project")
            add(
                JLabel("Project: ${loadedConfig.projectName} (${projectDir.toAbsolutePath().normalize()})"),
                BorderLayout.CENTER
            )
        }

        add(headerPanel, BorderLayout.NORTH)
        add(checkboxPanel, BorderLayout.CENTER)
    }

    /**
     * Reloads the project's `build.gradle.kts` from disk and updates all checkboxes.
     */
    fun reloadFromDisk(): GenericUiProjectConfig {
        loadedConfig = projectManagerService.loadProjectConfig(projectDir)
        checkboxPanel.applyProjectConfig(loadedConfig)
        return loadedConfig
    }

    /**
     * Writes the updated target backends, Swing LWJGL options, extensions, and SASS setting
     * back to the project's `build.gradle.kts`.
     */
    fun applyChanges(): Path {
        val updatedBuildFile = projectManagerService.updateProjectTargetsAndExtensions(
            projectDir = projectDir,
            updatedBackends = checkboxPanel.getSelectedBackends(),
            updatedExtensions = checkboxPanel.getSelectedExtensions(),
            swingUseLwjglGL = checkboxPanel.swingLwjglGlCheckbox.isSelected,
            swingUseVulkan = checkboxPanel.swingVulkanCheckbox.isSelected,
            compileSass = checkboxPanel.compileSassCheckbox.isSelected
        )
        loadedConfig = projectManagerService.loadProjectConfig(projectDir)
        return updatedBuildFile
    }
}
