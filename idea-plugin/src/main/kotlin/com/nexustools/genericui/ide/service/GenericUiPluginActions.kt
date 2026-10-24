package com.nexustools.genericui.ide.service

import com.nexustools.genericui.ide.model.GenericUiProjectConfig
import com.nexustools.genericui.ide.ui.GenericUiManageTargetsDialog
import com.nexustools.genericui.ide.ui.GenericUiNewProjectWizardDialog
import java.nio.file.Path

/**
 * Action controller for the IntelliJ IDEA menu actions registered in `META-INF/plugin.xml`:
 * - `GenericUI.NewProject` ("New GenericUI Project...")
 * - `GenericUI.ManageTargets` ("Manage GenericUI Targets & Extensions...")
 */
class GenericUiPluginActions(
    private val projectManagerService: GenericUiProjectManagerService = GenericUiProjectManagerService()
) {
    companion object {
        const val PLUGIN_ID: String = "com.nexustools.genericui.idea"
        const val NEW_PROJECT_ACTION_ID: String = "GenericUI.NewProject"
        const val MANAGE_TARGETS_ACTION_ID: String = "GenericUI.ManageTargets"
        const val NEW_PROJECT_ACTION_TEXT: String = "New GenericUI Project..."
        const val MANAGE_TARGETS_ACTION_TEXT: String = "Manage GenericUI Targets & Extensions..."
    }

    /**
     * Creates a configured [GenericUiNewProjectWizardDialog] ready to display or manipulate programmatically.
     */
    fun createNewProjectWizard(initialConfig: GenericUiProjectConfig? = null): GenericUiNewProjectWizardDialog {
        return GenericUiNewProjectWizardDialog(projectManagerService, initialConfig)
    }

    /**
     * Creates a configured [GenericUiManageTargetsDialog] pre-populated with the targets and extensions
     * parsed from [projectDir]'s `build.gradle.kts`.
     */
    fun createManageTargetsDialog(projectDir: Path): GenericUiManageTargetsDialog {
        return GenericUiManageTargetsDialog(projectDir, projectManagerService)
    }
}
