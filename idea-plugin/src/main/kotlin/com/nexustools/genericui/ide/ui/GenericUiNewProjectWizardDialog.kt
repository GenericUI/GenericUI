package com.nexustools.genericui.ide.ui

import com.nexustools.genericui.ide.model.GenericUiProjectConfig
import com.nexustools.genericui.ide.service.GenericUiProjectManagerService
import java.awt.BorderLayout
import java.awt.GridLayout
import java.nio.file.Path
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField

/**
 * New Project Wizard component & controller for creating a new `GenericUI` multiplatform project
 * in IntelliJ IDEA.
 *
 * Collects project name, package name, project root directory, and `GenericUI` library path,
 * embeds [GenericUiTargetCheckboxPanel] for selecting target backends and extensions via checkboxes,
 * validates user inputs, and invokes [GenericUiProjectManagerService.createProject].
 */
class GenericUiNewProjectWizardDialog(
    private val projectManagerService: GenericUiProjectManagerService = GenericUiProjectManagerService(),
    initialConfig: GenericUiProjectConfig? = null
) : JPanel(BorderLayout(10, 10)) {

    val projectNameField: JTextField = JTextField(initialConfig?.projectName ?: "MyGenericUiApp", 28)
    val packageNameField: JTextField = JTextField(initialConfig?.packageName ?: "com.example.mygenericuiapp", 28)
    val projectRootPathField: JTextField = JTextField(initialConfig?.projectRootPath ?: "", 36)
    val genericUiLibPathField: JTextField = JTextField(initialConfig?.genericUiLibPath ?: "D:/IdeaProjects/GenericUI", 36)

    val checkboxPanel: GenericUiTargetCheckboxPanel = GenericUiTargetCheckboxPanel()

    init {
        val metadataPanel = JPanel(GridLayout(4, 2, 8, 6)).apply {
            border = BorderFactory.createTitledBorder("GenericUI Project Metadata")
            add(JLabel("Project Name:"))
            add(projectNameField)
            add(JLabel("Package Name:"))
            add(packageNameField)
            add(JLabel("Project Location:"))
            add(projectRootPathField)
            add(JLabel("GenericUI Framework Path:"))
            add(genericUiLibPathField)
        }

        add(metadataPanel, BorderLayout.NORTH)
        add(checkboxPanel, BorderLayout.CENTER)

        if (initialConfig != null) {
            checkboxPanel.applyProjectConfig(initialConfig)
        }
    }

    /**
     * Validates the wizard fields and checkbox selections.
     * Returns a list of validation error messages (empty when valid).
     */
    fun validateWizardInputs(): List<String> = buildList {
        if (projectNameField.text.isBlank()) {
            add("Project name must not be blank.")
        }
        val pkg = packageNameField.text.trim()
        if (pkg.isBlank() || !Regex("""^[a-zA-Z_][a-zA-Z0-9_]*(\.[a-zA-Z_][a-zA-Z0-9_]*)*$""").matches(pkg)) {
            add("Package name '$pkg' is not a valid Kotlin package identifier.")
        }
        if (projectRootPathField.text.isBlank()) {
            add("Project root path must not be blank.")
        }
        if (checkboxPanel.getSelectedBackends().isEmpty()) {
            add("Select at least one GenericUI target backend.")
        }
    }

    /**
     * Constructs the [GenericUiProjectConfig] from the current wizard text fields and checkbox selections.
     */
    fun buildProjectConfig(): GenericUiProjectConfig =
        checkboxPanel.toProjectConfig(
            projectName = projectNameField.text.trim(),
            packageName = packageNameField.text.trim(),
            projectRootPath = projectRootPathField.text.trim(),
            genericUiLibPath = genericUiLibPathField.text.trim()
        )

    /**
     * Validates the wizard state and scaffolds the new `GenericUI` project on disk.
     */
    fun finishAndCreateProject(): Path {
        val errors = validateWizardInputs()
        require(errors.isEmpty()) {
            "Invalid GenericUI New Project Wizard configuration: ${errors.joinToString("; ")}"
        }
        return projectManagerService.createProject(buildProjectConfig())
    }
}
