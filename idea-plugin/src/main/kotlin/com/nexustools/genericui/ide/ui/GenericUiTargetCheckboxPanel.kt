package com.nexustools.genericui.ide.ui

import com.nexustools.genericui.ide.generator.GenericUiGradleScriptGenerator
import com.nexustools.genericui.ide.model.GenericUiExtensionModule
import com.nexustools.genericui.ide.model.GenericUiProjectConfig
import com.nexustools.genericui.ide.model.GenericUiProjectTemplateType
import com.nexustools.genericui.ide.model.GenericUiTargetBackend
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.GridLayout
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea

/**
 * Interactive Swing/IntelliJ IDEA configuration panel with grouped checkboxes for selecting
 * `GenericUI` target backends (`windows`, `macos`, `gtk`, `qt`, `swing`, `android`, `ios`, `web`),
 * quick-select preset buttons (`All Except Swing`, `Desktop Only (No Swing)`, `Mobile Only`, `Select All`, `Clear All`),
 * 11 first-party extension checkboxes with automatic transitive dependency resolution,
 * starter project template selector, and a live preview of the generated `genericui { ... }` Gradle DSL block.
 */
class GenericUiTargetCheckboxPanel(
    private val scriptGenerator: GenericUiGradleScriptGenerator = GenericUiGradleScriptGenerator()
) : JPanel(BorderLayout(8, 8)) {

    val backendCheckboxes: Map<GenericUiTargetBackend, JCheckBox> =
        GenericUiTargetBackend.entries.associateWith { backend ->
            JCheckBox(backend.displayName, backend.defaultSelected).apply {
                name = "backend_${backend.id}"
            }
        }

    val swingLwjglGlCheckbox: JCheckBox = JCheckBox("Swing: Enable LWJGL OpenGL / OpenGL ES (GLCanvas)", true).apply {
        name = "swing_lwjgl_gl"
    }

    val swingVulkanCheckbox: JCheckBox = JCheckBox("Swing: Enable LWJGL Vulkan & MoltenVK (VKCanvas)", true).apply {
        name = "swing_vulkan"
    }

    val extensionCheckboxes: Map<GenericUiExtensionModule, JCheckBox> =
        GenericUiExtensionModule.entries.associateWith { ext ->
            JCheckBox(ext.displayName, false).apply {
                name = "ext_${ext.id}"
                toolTipText = ext.description
            }
        }

    val allExceptSwingButton: JButton = JButton("All Except Swing")
    val desktopOnlyNoSwingButton: JButton = JButton("Desktop Only (No Swing)")
    val mobileOnlyButton: JButton = JButton("Mobile Only")
    val selectAllButton: JButton = JButton("Select All")
    val clearAllButton: JButton = JButton("Clear All")

    val templateComboBox: JComboBox<GenericUiProjectTemplateType> =
        JComboBox(GenericUiProjectTemplateType.entries.toTypedArray())

    val compileSassCheckbox: JCheckBox = JCheckBox("Compile SASS (.scss / .sass) Stylesheets at Build Time", true)

    val dslPreviewArea: JTextArea = JTextArea(14, 60).apply {
        isEditable = false
    }

    private var updatingCheckboxes: Boolean = false

    init {
        val mainContent = JPanel()
        mainContent.layout = BoxLayout(mainContent, BoxLayout.Y_AXIS)

        // 1. Preset Buttons Row
        val presetBar = JPanel(FlowLayout(FlowLayout.LEFT, 6, 4)).apply {
            border = BorderFactory.createTitledBorder("Quick Target Presets")
            add(allExceptSwingButton)
            add(desktopOnlyNoSwingButton)
            add(mobileOnlyButton)
            add(selectAllButton)
            add(clearAllButton)
        }
        mainContent.add(presetBar)

        // 2. Grouped Target Backend Checkboxes
        val backendsContainer = JPanel(GridLayout(1, 3, 8, 8)).apply {
            border = BorderFactory.createTitledBorder("Target Platform Backends")
        }

        val desktopPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = BorderFactory.createTitledBorder("Desktop Targets")
            for (backend in GenericUiTargetBackend.entries.filter { it.category == "Desktop" }) {
                add(backendCheckboxes.getValue(backend))
            }
            add(swingLwjglGlCheckbox)
            add(swingVulkanCheckbox)
        }

        val mobilePanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = BorderFactory.createTitledBorder("Mobile Targets")
            for (backend in GenericUiTargetBackend.entries.filter { it.category == "Mobile" }) {
                add(backendCheckboxes.getValue(backend))
            }
        }

        val webPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = BorderFactory.createTitledBorder("Web Targets")
            for (backend in GenericUiTargetBackend.entries.filter { it.category == "Web" }) {
                add(backendCheckboxes.getValue(backend))
            }
        }

        backendsContainer.add(desktopPanel)
        backendsContainer.add(mobilePanel)
        backendsContainer.add(webPanel)
        mainContent.add(backendsContainer)

        // 3. Extension Modules Checkboxes
        val extensionsPanel = JPanel(GridLayout(0, 2, 6, 4)).apply {
            border = BorderFactory.createTitledBorder("GenericUI Extension Modules (Auto-Resolves Dependencies)")
            for (ext in GenericUiExtensionModule.entries) {
                add(extensionCheckboxes.getValue(ext))
            }
        }
        mainContent.add(extensionsPanel)

        // 4. Template & SASS Options
        val optionsPanel = JPanel(FlowLayout(FlowLayout.LEFT, 8, 4)).apply {
            border = BorderFactory.createTitledBorder("Starter Template & Stylesheet Compiler")
            add(JLabel("Starter Template:"))
            add(templateComboBox)
            add(compileSassCheckbox)
        }
        mainContent.add(optionsPanel)

        add(mainContent, BorderLayout.CENTER)

        // 5. Live Gradle DSL Preview
        val previewPanel = JPanel(BorderLayout()).apply {
            border = BorderFactory.createTitledBorder("Generated build.gradle.kts genericui { ... } DSL Preview")
            add(JScrollPane(dslPreviewArea), BorderLayout.CENTER)
        }
        add(previewPanel, BorderLayout.SOUTH)

        wireListeners()
        syncSwingSubCheckboxes()
        refreshDslPreview()
    }

    private fun wireListeners() {
        allExceptSwingButton.addActionListener { selectAllExceptSwing() }
        desktopOnlyNoSwingButton.addActionListener { selectDesktopExceptSwing() }
        mobileOnlyButton.addActionListener { selectMobileOnly() }
        selectAllButton.addActionListener { selectAllBackends() }
        clearAllButton.addActionListener { clearAllBackends() }

        for ((backend, checkbox) in backendCheckboxes) {
            checkbox.addActionListener {
                if (!updatingCheckboxes) {
                    if (backend == GenericUiTargetBackend.SWING) {
                        syncSwingSubCheckboxes()
                    }
                    refreshDslPreview()
                }
            }
        }

        swingLwjglGlCheckbox.addActionListener {
            if (!updatingCheckboxes) refreshDslPreview()
        }
        swingVulkanCheckbox.addActionListener {
            if (!updatingCheckboxes) refreshDslPreview()
        }

        for ((ext, checkbox) in extensionCheckboxes) {
            checkbox.addActionListener {
                if (!updatingCheckboxes) {
                    if (checkbox.isSelected) {
                        enableRequiredExtensionsFor(ext)
                    }
                    refreshDslPreview()
                }
            }
        }

        templateComboBox.addActionListener {
            if (!updatingCheckboxes) {
                val chosenTemplate = selectedTemplateType
                if (chosenTemplate.defaultExtensions.isNotEmpty()) {
                    val merged = getSelectedExtensions() + chosenTemplate.defaultExtensions
                    setSelectedExtensions(merged)
                } else {
                    refreshDslPreview()
                }
            }
        }

        compileSassCheckbox.addActionListener {
            if (!updatingCheckboxes) refreshDslPreview()
        }
    }

    private fun syncSwingSubCheckboxes() {
        val swingChecked = backendCheckboxes.getValue(GenericUiTargetBackend.SWING).isSelected
        swingLwjglGlCheckbox.isEnabled = swingChecked
        swingVulkanCheckbox.isEnabled = swingChecked
    }

    private fun enableRequiredExtensionsFor(extension: GenericUiExtensionModule) {
        updatingCheckboxes = true
        try {
            for (req in extension.requiredExtensions()) {
                extensionCheckboxes.getValue(req).isSelected = true
            }
        } finally {
            updatingCheckboxes = false
        }
    }

    /** Returns the currently checked [GenericUiTargetBackend]s in canonical declaration order. */
    fun getSelectedBackends(): Set<GenericUiTargetBackend> =
        GenericUiTargetBackend.entries
            .filter { backendCheckboxes.getValue(it).isSelected }
            .toCollection(LinkedHashSet())

    /** Updates the target backend checkboxes to match [backends] and refreshes the DSL preview. */
    fun setSelectedBackends(backends: Set<GenericUiTargetBackend>) {
        updatingCheckboxes = true
        try {
            for ((backend, checkbox) in backendCheckboxes) {
                checkbox.isSelected = backend in backends
            }
            syncSwingSubCheckboxes()
        } finally {
            updatingCheckboxes = false
        }
        refreshDslPreview()
    }

    /** Checks or unchecks a single [backend] checkbox and refreshes the DSL preview. */
    fun setBackendSelected(backend: GenericUiTargetBackend, selected: Boolean) {
        updatingCheckboxes = true
        try {
            backendCheckboxes.getValue(backend).isSelected = selected
            if (backend == GenericUiTargetBackend.SWING) {
                syncSwingSubCheckboxes()
            }
        } finally {
            updatingCheckboxes = false
        }
        refreshDslPreview()
    }

    /** Returns true if [backend]'s checkbox is currently checked. */
    fun isBackendSelected(backend: GenericUiTargetBackend): Boolean =
        backendCheckboxes.getValue(backend).isSelected

    /** Returns the currently checked [GenericUiExtensionModule]s (transitively resolved). */
    fun getSelectedExtensions(): Set<GenericUiExtensionModule> {
        val direct = GenericUiExtensionModule.entries
            .filter { extensionCheckboxes.getValue(it).isSelected }
            .toSet()
        return GenericUiExtensionModule.resolveTransitive(direct)
    }

    /** Updates the extension checkboxes (automatically checking transitive dependencies) and refreshes the DSL preview. */
    fun setSelectedExtensions(extensions: Set<GenericUiExtensionModule>) {
        val resolved = GenericUiExtensionModule.resolveTransitive(extensions)
        updatingCheckboxes = true
        try {
            for ((ext, checkbox) in extensionCheckboxes) {
                checkbox.isSelected = ext in resolved
            }
        } finally {
            updatingCheckboxes = false
        }
        refreshDslPreview()
    }

    /**
     * Checks or unchecks a single [extension] checkbox. When [selected] is true,
     * all required transitive extensions are also checked automatically.
     */
    fun setExtensionSelected(extension: GenericUiExtensionModule, selected: Boolean) {
        updatingCheckboxes = true
        try {
            extensionCheckboxes.getValue(extension).isSelected = selected
            if (selected) {
                for (req in extension.requiredExtensions()) {
                    extensionCheckboxes.getValue(req).isSelected = true
                }
            }
        } finally {
            updatingCheckboxes = false
        }
        refreshDslPreview()
    }

    /** Returns true if [extension]'s checkbox is currently checked. */
    fun isExtensionSelected(extension: GenericUiExtensionModule): Boolean =
        extensionCheckboxes.getValue(extension).isSelected

    /** Preset action: checks all backends EXCEPT Swing (`windows`, `macos`, `gtk`, `qt`, `android`, `ios`, `web`). */
    fun selectAllExceptSwing() {
        setSelectedBackends(GenericUiTargetBackend.allExceptSwingSet())
    }

    /** Preset action: checks only the 4 native Desktop backends (`windows`, `macos`, `gtk`, `qt`), leaving `swing`, `android`, `ios`, and `web` unchecked. */
    fun selectDesktopExceptSwing() {
        setSelectedBackends(GenericUiTargetBackend.desktopExceptSwingSet())
    }

    /** Alias for [selectDesktopExceptSwing] (`allNativeDesktop()`). */
    fun selectAllNativeDesktop() {
        selectDesktopExceptSwing()
    }

    /** Alias for [selectDesktopExceptSwing] (`allNativeDesktop()`). */
    fun selectNativeDesktop() {
        selectDesktopExceptSwing()
    }

    /** Preset action: checks only the Mobile backends (`android`, `ios`). */
    fun selectMobileOnly() {
        setSelectedBackends(GenericUiTargetBackend.mobileSet())
    }

    /** Preset action: checks all 8 target backends including Swing. */
    fun selectAllBackends() {
        setSelectedBackends(GenericUiTargetBackend.allSet())
    }

    /** Preset action: unchecks all target backend checkboxes. */
    fun clearAllBackends() {
        setSelectedBackends(emptySet())
    }

    /** Currently selected starter template in [templateComboBox]. */
    var selectedTemplateType: GenericUiProjectTemplateType
        get() = (templateComboBox.selectedItem as? GenericUiProjectTemplateType)
            ?: GenericUiProjectTemplateType.STANDARD_FRAGMENT
        set(value) {
            templateComboBox.selectedItem = value
        }

    /** Returns the current live preview of the `genericui { ... }` Gradle DSL block. */
    fun getDslPreviewText(): String = dslPreviewArea.text

    /** Recomputes and updates [dslPreviewArea] based on current checkbox states. */
    fun refreshDslPreview() {
        val snapshotConfig = toProjectConfig(
            projectName = "PreviewApp",
            packageName = "com.example.preview",
            projectRootPath = "."
        )
        dslPreviewArea.text = scriptGenerator.generateGenericUiDslBlock(snapshotConfig)
    }

    /** Builds a [GenericUiProjectConfig] from the current UI state and supplied project metadata. */
    fun toProjectConfig(
        projectName: String,
        packageName: String,
        projectRootPath: String,
        genericUiLibPath: String = "D:/IdeaProjects/GenericUI"
    ): GenericUiProjectConfig = GenericUiProjectConfig(
        projectName = projectName,
        packageName = packageName,
        projectRootPath = projectRootPath,
        genericUiLibPath = genericUiLibPath,
        selectedBackends = getSelectedBackends(),
        selectedExtensions = getSelectedExtensions(),
        templateType = selectedTemplateType,
        compileSass = compileSassCheckbox.isSelected,
        swingUseLwjglGL = swingLwjglGlCheckbox.isSelected,
        swingUseVulkan = swingVulkanCheckbox.isSelected
    )

    /** Populates all checkboxes and controls in this panel from an existing [GenericUiProjectConfig]. */
    fun applyProjectConfig(config: GenericUiProjectConfig) {
        updatingCheckboxes = true
        try {
            for ((backend, checkbox) in backendCheckboxes) {
                checkbox.isSelected = backend in config.selectedBackends
            }
            swingLwjglGlCheckbox.isSelected = config.swingUseLwjglGL
            swingVulkanCheckbox.isSelected = config.swingUseVulkan
            syncSwingSubCheckboxes()

            val resolvedExts = config.resolvedExtensions()
            for ((ext, checkbox) in extensionCheckboxes) {
                checkbox.isSelected = ext in resolvedExts
            }

            templateComboBox.selectedItem = config.templateType
            compileSassCheckbox.isSelected = config.compileSass
        } finally {
            updatingCheckboxes = false
        }
        refreshDslPreview()
    }
}
