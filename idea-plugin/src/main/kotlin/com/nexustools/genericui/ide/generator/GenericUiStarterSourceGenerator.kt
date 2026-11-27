package com.nexustools.genericui.ide.generator

import com.nexustools.genericui.ide.model.GenericUiExtensionModule
import com.nexustools.genericui.ide.model.GenericUiProjectConfig
import com.nexustools.genericui.ide.model.GenericUiProjectTemplateType

/**
 * Generates starter Kotlin Multiplatform source files (`MainFragment.kt`)
 * and SASS stylesheet (`main.scss`) tailored to the selected [GenericUiProjectTemplateType].
 */
class GenericUiStarterSourceGenerator {

    /**
     * Generates `src/commonMain/kotlin/<packagePath>/MainFragment.kt` matching [GenericUiProjectConfig.templateType].
     *
     * When [GenericUiExtensionModule.DSL] is enabled, demonstrates the `:extensions:ui:dsl` `bootstrap { ... }`
     * and `style { ... }` builders (plus companion `:dsl` extension builders where supported); otherwise
     * demonstrates core `getBootstrap().apply { ... }` and `getStyle().apply { ... }` configuration.
     */
    fun generateMainFragmentKt(config: GenericUiProjectConfig): String = buildString {
        val resolvedExtensions = config.resolvedExtensions()
        val useDsl = GenericUiExtensionModule.DSL in resolvedExtensions

        appendLine("package ${config.packageName}")
        appendLine()
        appendLine("import com.nexustools.genericui.component.fragment.Fragment")
        appendLine("import com.nexustools.genericui.component.fragment.FragmentBundle")
        appendLine("import com.nexustools.genericui.component.fragment.MainAppFragment")
        if (useDsl) {
            appendLine("import com.nexustools.genericui.ext.dsl.bootstrap")
            appendLine("import com.nexustools.genericui.ext.dsl.style")
        }
        when (config.templateType) {
            GenericUiProjectTemplateType.GAME_2D -> {
                if (useDsl) {
                    appendLine("import com.nexustools.genericui.ext.engine2d.dsl.game2DCanvas")
                } else {
                    appendLine("import com.nexustools.genericui.ext.engine2d.Game2DCanvasHost")
                }
            }
            GenericUiProjectTemplateType.GAME_3D -> {
                if (useDsl) {
                    appendLine("import com.nexustools.genericui.ext.engine3d.dsl.game3DCanvas")
                } else {
                    appendLine("import com.nexustools.genericui.ext.engine3d.Game3DCanvasHost")
                }
            }
            else -> Unit
        }
        appendLine("import com.nexustools.genericui.style.CssCursor")
        appendLine("import com.nexustools.genericui.theme.SpacingScale")
        appendLine()
        when (config.templateType) {
            GenericUiProjectTemplateType.STANDARD_FRAGMENT -> {
                appendLine("/**")
                appendLine(" * Primary application fragment for ${config.projectName}.")
                appendLine(" * Built on GenericUI's Fragment & Bootstrap/CSS/SASS styling system.")
                appendLine(" */")
                appendLine("@MainAppFragment")
                appendLine("class MainFragment : Fragment() {")
                appendLine("    val title: String = \"${config.projectName}\"")
                appendLine()
                appendLine("    override fun onCreate(savedState: FragmentBundle?) {")
                appendLine("        super.onCreate(savedState)")
                appendStylingSnippet(useDsl)
                appendLine("        // Initialize GenericUI Fragment hierarchy, MenuBar, ToolBar, and responsive widgets")
                appendExtensionGuidanceComments(resolvedExtensions)
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
                appendStylingSnippet(useDsl)
                appendLine("        // Configure DockWorkspaceFragment panels, IdeCodeEditor, and AIChatDrawer")
                appendExtensionGuidanceComments(resolvedExtensions)
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
                appendStylingSnippet(useDsl)
                appendLine("        // Configure OverlayDockWorkspaceFragment with ThreeSceneCanvas and floating HUD docks")
                appendExtensionGuidanceComments(resolvedExtensions)
                appendLine("    }")
                appendLine("}")
            }
            GenericUiProjectTemplateType.ADAPTIVE_SPLIT_TAB -> {
                appendLine("/**")
                appendLine(" * Responsive Desktop/Mobile HybridColumnFragment for ${config.projectName}.")
                appendLine(" * Uses split master-detail layout on Desktop/Tablet and bottom-tab navigation on Mobile.")
                appendLine(" */")
                appendLine("@MainAppFragment")
                appendLine("class MainFragment : Fragment() {")
                appendLine("    val title: String = \"${config.projectName} - Adaptive Media App\"")
                appendLine()
                appendLine("    override fun onCreate(savedState: FragmentBundle?) {")
                appendLine("        super.onCreate(savedState)")
                appendStylingSnippet(useDsl)
                appendLine("        // Configure HybridColumnFragment sections and VideoPlayer / AudioPlayer media views")
                appendExtensionGuidanceComments(resolvedExtensions)
                appendLine("    }")
                appendLine("}")
            }
            GenericUiProjectTemplateType.GAME_2D -> {
                appendLine("/**")
                appendLine(" * Hardware-accelerated 2D Game Engine stage fragment for ${config.projectName}.")
                appendLine(" * Integrates :extensions:game:engine2d, :extensions:physics:box2d, and :extensions:game:gamepad.")
                appendLine(" */")
                appendLine("@MainAppFragment")
                appendLine("class MainFragment : Fragment() {")
                appendLine("    val title: String = \"${config.projectName} - 2D Game\"")
                appendLine()
                appendLine("    override fun onCreate(savedState: FragmentBundle?) {")
                appendLine("        super.onCreate(savedState)")
                appendStylingSnippet(useDsl)
                if (useDsl) {
                    appendLine("        game2DCanvas(width = 1280, height = 720, autoStart = true)")
                } else {
                    appendLine("        val host = Game2DCanvasHost()")
                    appendLine("        addChild(host.canvas)")
                    appendLine("        host.start()")
                }
                appendExtensionGuidanceComments(resolvedExtensions)
                appendLine("    }")
                appendLine("}")
            }
            GenericUiProjectTemplateType.GAME_3D -> {
                appendLine("/**")
                appendLine(" * Hardware-accelerated 3D Game Engine stage fragment for ${config.projectName}.")
                appendLine(" * Integrates :extensions:game:engine3d, :extensions:graphics:threejs, :extensions:physics:box3d, and :extensions:game:gamepad.")
                appendLine(" */")
                appendLine("@MainAppFragment")
                appendLine("class MainFragment : Fragment() {")
                appendLine("    val title: String = \"${config.projectName} - 3D Game\"")
                appendLine()
                appendLine("    override fun onCreate(savedState: FragmentBundle?) {")
                appendLine("        super.onCreate(savedState)")
                appendStylingSnippet(useDsl)
                if (useDsl) {
                    appendLine("        game3DCanvas(width = 1280, height = 720, autoStart = true)")
                } else {
                    appendLine("        val host = Game3DCanvasHost()")
                    appendLine("        addChild(host.canvas)")
                    appendLine("        host.start()")
                }
                appendExtensionGuidanceComments(resolvedExtensions)
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
                appendStylingSnippet(useDsl)
                appendLine("        // Configure CenteredMenuPageFragment and ValidatedStepPageFragment navigation stack")
                appendExtensionGuidanceComments(resolvedExtensions)
                appendLine("    }")
                appendLine("}")
            }
        }
    }

    /**
     * Generates `src/commonMain/kotlin/<packagePath>/AppServiceWorker.kt` when
     * [GenericUiExtensionModule.SERVICE_WORKER] is enabled, using core `:extensions:system:serviceworker`
     * APIs directly (backed by `:extensions:system:ipc` on Desktop and Android).
     */
    fun generateServiceWorkerKt(config: GenericUiProjectConfig): String = buildString {
        appendLine("package ${config.packageName}")
        appendLine()
        appendLine("import com.nexustools.genericui.ext.serviceworker.MainServiceWorker")
        appendLine("import com.nexustools.genericui.ext.serviceworker.ServiceWorker")
        appendLine()
        appendLine("/**")
        appendLine(" * Background Service Worker for ${config.projectName}.")
        appendLine(" * Uses :extensions:system:serviceworker directly (backed by :extensions:system:ipc on Desktop and Android).")
        appendLine(" */")
        appendLine("@MainServiceWorker")
        appendLine("class AppServiceWorker : ServiceWorker() {")
        appendLine("    override fun onStart() {")
        appendLine("        if (supportsOngoingNotification) {")
        appendLine("            setNotificationTitle(\"${config.projectName}\")")
        appendLine("            setNotificationText(\"Running in background\")")
        appendLine("        }")
        appendLine("        if (supportsSystemTray) {")
        appendLine("            setTrayTooltip(\"${config.projectName}\")")
        appendLine("        }")
        appendLine("    }")
        append("}")
    }

    /**
     * Generates `src/commonTest/kotlin/<packagePath>/MainFragmentTest.kt` powered by the
     * unified `:tests` (`JestElectricRunner`) cross-platform UI test suite.
     */
    fun generateMainFragmentTestKt(config: GenericUiProjectConfig): String = buildString {
        appendLine("package ${config.packageName}")
        appendLine()
        appendLine("import com.nexustools.genericui.tests.JestElectricRunner")
        appendLine("import kotlin.test.Test")
        appendLine()
        appendLine("/**")
        appendLine(" * Multi-backend UI test suite for ${config.projectName} powered by `:tests` (`JestElectricRunner`).")
        appendLine(" */")
        appendLine("class MainFragmentTest {")
        appendLine()
        appendLine("    @Test")
        appendLine("    fun testMainFragmentMountsSuccessfully() {")
        appendLine("        JestElectricRunner.runSuite(")
        appendLine("            suiteName = \"${config.projectName}\",")
        appendLine("            visualOnWindows = false")
        appendLine("        ) {")
        appendLine("            it(\"creates and resumes MainFragment\") {")
        appendLine("                val controller = launchFragment { MainFragment() }")
        appendLine("                expect(controller.fragment.title.isNotBlank()).toBe(true)")
        appendLine("            }")
        appendLine("        }")
        appendLine("    }")
        append("}")
    }

    private fun StringBuilder.appendStylingSnippet(useDsl: Boolean) {
        if (useDsl) {
            appendLine("        bootstrap {")
            appendLine("            padding = SpacingScale.S3")
            appendLine("        }")
            appendLine("        style {")
            appendLine("            setCursor(CssCursor.DEFAULT)")
            appendLine("        }")
        } else {
            appendLine("        getBootstrap().apply {")
            appendLine("            padding = SpacingScale.S3")
            appendLine("        }")
            appendLine("        getStyle().apply {")
            appendLine("            setCursor(CssCursor.DEFAULT)")
            appendLine("        }")
        }
    }

    private fun StringBuilder.appendExtensionGuidanceComments(resolvedExtensions: Set<GenericUiExtensionModule>) {
        if (GenericUiExtensionModule.WEB_VIEW in resolvedExtensions) {
            appendLine("        // WebView uses out-of-process Alien + SVG + Canvas2D + Canvas3D + Media + NJS + IPC on Desktop, native WebView on Mobile, and <iframe> on Web")
        } else {
            if (GenericUiExtensionModule.SVG in resolvedExtensions) {
                appendLine("        // SVG vector scene graph active via :extensions:graphics:svg")
            }
            if (GenericUiExtensionModule.IPC in resolvedExtensions && GenericUiExtensionModule.SERVICE_WORKER !in resolvedExtensions) {
                appendLine("        // Desktop + Android IPC transport active via :extensions:system:ipc")
            }
        }
        if (GenericUiExtensionModule.SERVICE_WORKER in resolvedExtensions) {
            appendLine("        // Connect to AppServiceWorker via ServiceWorkerExtension.channel")
        }
        if (GenericUiExtensionModule.MEDIA_CONTROLS in resolvedExtensions) {
            appendLine("        // OS media transport controls active via MediaControlsSession (:extensions:media:mediacontrols)")
        }
        if (GenericUiExtensionModule.PIP in resolvedExtensions) {
            appendLine("        // Picture-in-Picture active via FragmentPipHelper (:extensions:ui:pip)")
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
