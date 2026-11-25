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
import javax.swing.JTextField
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * Interactive Swing/IntelliJ IDEA configuration panel with grouped checkboxes for selecting
 * `GenericUI` target backends (`windows`, `macos`, `gtk`, `kde`, `swing`, `android`, `ios`, `ipados`, `web`),
 * quick-select preset buttons (`All Except Swing`, `Desktop Only (No Swing)`, `Mobile Only`, `Apple Only`, `Select All`, `Clear All`),
 * 19 categorized first-party extension checkboxes (Graphics, Physics, Game & Input, Media, UI) with automatic
 * transitive dependency resolution, starter project template selector, `:tests` (`JestElectricRunner`) toggle,
 * window defaults (`alwaysOnTop`, `aspectRatio`), Apple icon overrides (`appleIcon`, `macos`, `ios`, `ipados`),
 * and a live preview of the generated `genericui { ... }` Gradle DSL block.
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

    val swingLwjglGlCheckbox: JCheckBox = JCheckBox("Swing: Enable LWJGL OpenGL / OpenGL ES (Canvas3D GL)", true).apply {
        name = "swing_lwjgl_gl"
    }

    val swingVulkanCheckbox: JCheckBox = JCheckBox("Swing: Enable LWJGL Vulkan & MoltenVK (Canvas3D VK)", true).apply {
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
    val appleOnlyButton: JButton = JButton("Apple Only")
    val selectAllButton: JButton = JButton("Select All")
    val clearAllButton: JButton = JButton("Clear All")

    val templateComboBox: JComboBox<GenericUiProjectTemplateType> =
        JComboBox(GenericUiProjectTemplateType.entries.toTypedArray())

    val compileSassCheckbox: JCheckBox = JCheckBox("Compile SASS (.scss / .sass) Stylesheets at Build Time", true)
    val enableTestingCheckbox: JCheckBox = JCheckBox("Enable :tests (JestElectricRunner)", true)
    val windowAlwaysOnTopCheckbox: JCheckBox = JCheckBox("Window: Always On Top", false)
    val windowAspectRatioComboBox: JComboBox<String> = JComboBox(
        arrayOf("Unconstrained", "16:9", "16:10", "21:9", "4:3", "1:1")
    )

    val appleIconField: JTextField = JTextField("", 14)
    val macIconField: JTextField = JTextField("", 14)
    val iosIconField: JTextField = JTextField("", 14)
    val ipadosIconField: JTextField = JTextField("", 14)

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
            add(appleOnlyButton)
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
            border = BorderFactory.createTitledBorder("Mobile & Tablet Targets")
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

        // 3. Categorized Extension Modules Checkboxes
        val extensionsPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = BorderFactory.createTitledBorder("GenericUI Extension Modules (Auto-Resolves Dependencies)")
            for (category in GenericUiExtensionModule.CATEGORIES) {
                val categoryPanel = JPanel(GridLayout(0, 2, 6, 2)).apply {
                    border = BorderFactory.createTitledBorder(category)
                    for (ext in GenericUiExtensionModule.byCategory(category)) {
                        add(extensionCheckboxes.getValue(ext))
                    }
                }
                add(categoryPanel)
            }
        }
        mainContent.add(extensionsPanel)

        // 4. Template, SASS, Testing, & Window Options
        val optionsPanel = JPanel(FlowLayout(FlowLayout.LEFT, 8, 4)).apply {
            border = BorderFactory.createTitledBorder("Starter Template, Testing & Window Defaults")
            add(JLabel("Starter Template:"))
            add(templateComboBox)
            add(compileSassCheckbox)
            add(enableTestingCheckbox)
            add(windowAlwaysOnTopCheckbox)
            add(JLabel("Aspect Ratio:"))
            add(windowAspectRatioComboBox)
        }
        mainContent.add(optionsPanel)

        // 5. Apple Icon & Per-Target Apple Icon Options
        val appleIconPanel = JPanel(FlowLayout(FlowLayout.LEFT, 8, 4)).apply {
            border = BorderFactory.createTitledBorder("Apple Icon Options (Optional)")
            add(JLabel("Shared appleIcon:"))
            add(appleIconField)
            add(JLabel("macOS icon:"))
            add(macIconField)
            add(JLabel("iOS icon:"))
            add(iosIconField)
            add(JLabel("iPadOS icon:"))
            add(ipadosIconField)
        }
        mainContent.add(appleIconPanel)

        add(mainContent, BorderLayout.CENTER)

        // 6. Live Gradle DSL Preview
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
        appleOnlyButton.addActionListener { selectAppleOnly() }
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

        compileSassCheckbox.addItemListener {
            if (!updatingCheckboxes) refreshDslPreview()
        }
        enableTestingCheckbox.addItemListener {
            if (!updatingCheckboxes) refreshDslPreview()
        }
        windowAlwaysOnTopCheckbox.addItemListener {
            if (!updatingCheckboxes) refreshDslPreview()
        }
        windowAspectRatioComboBox.addActionListener {
            if (!updatingCheckboxes) refreshDslPreview()
        }

        appleIconField.onTextChanged { if (!updatingCheckboxes) refreshDslPreview() }
        macIconField.onTextChanged { if (!updatingCheckboxes) refreshDslPreview() }
        iosIconField.onTextChanged { if (!updatingCheckboxes) refreshDslPreview() }
        ipadosIconField.onTextChanged { if (!updatingCheckboxes) refreshDslPreview() }
    }

    private fun JTextField.onTextChanged(callback: () -> Unit) {
        document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = callback()
            override fun removeUpdate(e: DocumentEvent?) = callback()
            override fun changedUpdate(e: DocumentEvent?) = callback()
        })
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

    /** Preset action: checks all backends EXCEPT Swing (`windows`, `macos`, `gtk`, `kde`, `android`, `ios`, `ipados`, `web`). */
    fun selectAllExceptSwing() {
        setSelectedBackends(GenericUiTargetBackend.allExceptSwingSet())
    }

    /** Preset action: checks only the 4 native Desktop backends (`windows`, `macos`, `gtk`, `kde`), leaving `swing`, `android`, `ios`, `ipados`, and `web` unchecked. */
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

    /** Preset action: checks only the Mobile backends (`android`, `ios`, `ipados`). */
    fun selectMobileOnly() {
        setSelectedBackends(GenericUiTargetBackend.mobileSet())
    }

    /** Preset action: checks only the Apple backends (`macos`, `ios`, `ipados`). */
    fun selectAppleOnly() {
        setSelectedBackends(GenericUiTargetBackend.appleSet())
    }

    /** Preset action: checks all 9 target backends including Swing. */
    fun selectAllBackends() {
        setSelectedBackends(GenericUiTargetBackend.allSet())
    }

    /** Preset action: unchecks all target backend checkboxes. */
    fun clearAllBackends() {
        setSelectedBackends(emptySet())
    }

    /** Returns the selected window aspect ratio pair `(width, height)`, or `null` when unconstrained. */
    fun getSelectedWindowAspectRatio(): Pair<Int, Int>? {
        val raw = (windowAspectRatioComboBox.selectedItem as? String)?.trim() ?: return null
        val parts = raw.split(':')
        if (parts.size != 2) return null
        val w = parts[0].toIntOrNull() ?: return null
        val h = parts[1].toIntOrNull() ?: return null
        if (w <= 0 || h <= 0) return null
        return w to h
    }

    /** Sets the selected window aspect ratio preset (`null` for `"Unconstrained"`). */
    fun setSelectedWindowAspectRatio(width: Int?, height: Int?) {
        updatingCheckboxes = true
        try {
            applyAspectRatioSelection(width, height)
        } finally {
            updatingCheckboxes = false
        }
        refreshDslPreview()
    }

    private fun applyAspectRatioSelection(width: Int?, height: Int?) {
        if (width == null || height == null || width <= 0 || height <= 0) {
            windowAspectRatioComboBox.selectedItem = "Unconstrained"
            return
        }
        val label = "$width:$height"
        var found = false
        for (i in 0 until windowAspectRatioComboBox.itemCount) {
            if (windowAspectRatioComboBox.getItemAt(i) == label) {
                found = true
                break
            }
        }
        if (!found) {
            windowAspectRatioComboBox.addItem(label)
        }
        windowAspectRatioComboBox.selectedItem = label
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
    ): GenericUiProjectConfig {
        val aspectRatio = getSelectedWindowAspectRatio()
        return GenericUiProjectConfig(
            projectName = projectName,
            packageName = packageName,
            projectRootPath = projectRootPath,
            genericUiLibPath = genericUiLibPath,
            selectedBackends = getSelectedBackends(),
            selectedExtensions = getSelectedExtensions(),
            templateType = selectedTemplateType,
            compileSass = compileSassCheckbox.isSelected,
            swingUseLwjglGL = swingLwjglGlCheckbox.isSelected,
            swingUseVulkan = swingVulkanCheckbox.isSelected,
            enableTesting = enableTestingCheckbox.isSelected,
            windowAlwaysOnTop = windowAlwaysOnTopCheckbox.isSelected,
            windowAspectRatioWidth = aspectRatio?.first,
            windowAspectRatioHeight = aspectRatio?.second,
            appleIcon = appleIconField.text.trim(),
            macIcon = macIconField.text.trim(),
            iosIcon = iosIconField.text.trim(),
            ipadosIcon = ipadosIconField.text.trim()
        )
    }

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
            enableTestingCheckbox.isSelected = config.enableTesting
            windowAlwaysOnTopCheckbox.isSelected = config.windowAlwaysOnTop
            applyAspectRatioSelection(config.windowAspectRatioWidth, config.windowAspectRatioHeight)
            appleIconField.text = config.appleIcon
            macIconField.text = config.macIcon
            iosIconField.text = config.iosIcon
            ipadosIconField.text = config.ipadosIcon
        } finally {
            updatingCheckboxes = false
        }
        refreshDslPreview()
    }
}
