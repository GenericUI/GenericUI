package com.nexustools.genericui.ide.generator

import com.nexustools.genericui.ide.model.GenericUiProjectConfig
import com.nexustools.genericui.ide.model.GenericUiProjectTemplateType

/**
 * Generates starter Kotlin Multiplatform source files (`MainFragment.kt`)
 * and SASS stylesheet (`main.scss`) tailored to the selected [GenericUiProjectTemplateType].
 */
class GenericUiStarterSourceGenerator {

    /**
     * Generates `src/commonMain/kotlin/<packagePath>/MainFragment.kt` matching [GenericUiProjectConfig.templateType].
     */
    fun generateMainFragmentKt(config: GenericUiProjectConfig): String = buildString {
        appendLine("package ${config.packageName}")
        appendLine()
        appendLine("import com.nexustools.genericui.component.fragment.Fragment")
        appendLine("import com.nexustools.genericui.component.fragment.FragmentBundle")
        appendLine("import com.nexustools.genericui.component.fragment.MainAppFragment")
        appendLine()
        when (config.templateType) {
            GenericUiProjectTemplateType.STANDARD_FRAGMENT -> {
                appendLine("/**")
                appendLine(" * Primary application fragment for ${config.projectName}.")
                appendLine(" * Built on GenericUI's Fragment & Bootstrap/SASS dual-mode styling system.")
                appendLine(" */")
                appendLine("@MainAppFragment")
                appendLine("class MainFragment : Fragment() {")
                appendLine("    val title: String = \"${config.projectName}\"")
                appendLine()
                appendLine("    override fun onCreate(savedState: FragmentBundle?) {")
                appendLine("        super.onCreate(savedState)")
                appendLine("        // Initialize GenericUI Fragment hierarchy, MenuBar, ToolBar, and responsive widgets")
                appendLine("    }")
                appendLine("}")
            }
            GenericUiProjectTemplateType.DOCK_WORKSPACE -> {
                appendLine("/**")
                appendLine(" * GIMP-style Single/Multi-Window dockable workspace fragment for ${config.projectName}.")
                appendLine(" * Supports left/right/bottom dock zones, floating tear-off windows, and central document tabs.")
                appendLine(" */")
                appendLine("@MainAppFragment")
                appendLine("class MainFragment : Fragment() {")
                appendLine("    val title: String = \"${config.projectName} - Dock Workspace\"")
                appendLine("    var singleWindowMode: Boolean = true")
                appendLine()
                appendLine("    override fun onCreate(savedState: FragmentBundle?) {")
                appendLine("        super.onCreate(savedState)")
                appendLine("        // Configure DockWorkspaceFragment panels, IdeCodeEditor, and AIChatDrawer")
                appendLine("    }")
                appendLine("}")
            }
            GenericUiProjectTemplateType.OVERLAY_DOCK_3D -> {
                appendLine("/**")
                appendLine(" * Full-bleed 3D Three.js scene viewport with translucent overlay dock panels for ${config.projectName}.")
                appendLine(" */")
                appendLine("@MainAppFragment")
                appendLine("class MainFragment : Fragment() {")
                appendLine("    val title: String = \"${config.projectName} - 3D Overlay Workspace\"")
                appendLine()
                appendLine("    override fun onCreate(savedState: FragmentBundle?) {")
                appendLine("        super.onCreate(savedState)")
                appendLine("        // Configure OverlayDockWorkspaceFragment with ThreeSceneCanvas and floating HUD docks")
                appendLine("    }")
                appendLine("}")
            }
            GenericUiProjectTemplateType.ADAPTIVE_SPLIT_TAB -> {
                appendLine("/**")
                appendLine(" * Responsive Desktop/Mobile AdaptiveSplitTabFragment for ${config.projectName}.")
                appendLine(" * Uses split master-detail layout on Desktop/Tablet and bottom-tab navigation on Mobile.")
                appendLine(" */")
                appendLine("@MainAppFragment")
                appendLine("class MainFragment : Fragment() {")
                appendLine("    val title: String = \"${config.projectName} - Adaptive Media App\"")
                appendLine()
                appendLine("    override fun onCreate(savedState: FragmentBundle?) {")
                appendLine("        super.onCreate(savedState)")
                appendLine("        // Configure AdaptiveSplitTabFragment sections and VideoPlayer / AudioPlayer media views")
                appendLine("    }")
                appendLine("}")
            }
            GenericUiProjectTemplateType.WIZARD_PAGE_SYSTEM -> {
                appendLine("/**")
                appendLine(" * Multi-step CenteredMenuPageFragment & ValidatedStepPageFragment workflow for ${config.projectName}.")
                appendLine(" */")
                appendLine("@MainAppFragment")
                appendLine("class MainFragment : Fragment() {")
                appendLine("    val title: String = \"${config.projectName} - Multi-Step Wizard\"")
                appendLine()
                appendLine("    override fun onCreate(savedState: FragmentBundle?) {")
                appendLine("        super.onCreate(savedState)")
                appendLine("        // Configure CenteredMenuPageFragment and ValidatedStepPageFragment navigation stack")
                appendLine("    }")
                appendLine("}")
            }
        }
    }

    /**
     * Generates `src/commonMain/resources/styles/main.scss` with Bootstrap-compatible variables and theme rules.
     */
    fun generateStyleScss(config: GenericUiProjectConfig): String = buildString {
        appendLine("// ${config.projectName} - GenericUI SASS Stylesheet")
        appendLine("\$primary-accent: #0d6efd;")
        appendLine("\$surface-bg: #181a1f;")
        appendLine("\$surface-fg: #e6edf3;")
        appendLine("\$panel-radius: 8px;")
        appendLine()
        appendLine(".app-root {")
        appendLine("    background-color: \$surface-bg;")
        appendLine("    color: \$surface-fg;")
        appendLine("    padding: 16px;")
        appendLine()
        appendLine("    .toolbar-header {")
        appendLine("        border-radius: \$panel-radius;")
        appendLine("        border-color: \$primary-accent;")
        appendLine("    }")
        appendLine("}")
    }
}
