package com.nexustools.genericui.component.shell

import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.enums.ui.ThemeMode
import com.nexustools.genericui.nativeimpl.shell.ApplicationNativeImpl
import com.nexustools.genericui.res.Resources
import com.nexustools.genericui.resources.ResourceID
import com.nexustools.genericui.style.css.CssStyleSheet
import com.nexustools.genericui.style.css.PrecompiledStyleSheet
import com.nexustools.genericui.style.css.StyleSheetRegistry
import com.nexustools.genericui.style.theme.SystemThemeDetector
import com.nexustools.genericui.window.TitleBar

/**
 * Standard Container-driven application (`Application`).
 *
 * Accepts a [Container] (and does not force developers to use `Fragment`s if they do not need them).
 * For Fragment-driven applications (including Helper Fragments like `AdaptiveSplitTabFragment` and `DockWorkspaceFragment`),
 * use [FragmentApplication].
 */
open class Application(
    initialContent: Container? = null,
    title: String = "GenericUI Application",
    initialThemeColor: String? = null
) : IApplication {

    override val mainWindow: Window = Window(title = title)
    var appNativeImpl: ApplicationNativeImpl<*>? = null
        private set

    private var themeModeState: ThemeMode = ThemeMode.AUTO
    private var themeColorState: String? = initialThemeColor
    private val activeStyleSheets = mutableListOf<PrecompiledStyleSheet>()
    private val themeChangeListeners = mutableListOf<(ThemeMode) -> Unit>()

    private val systemThemeObserver: (ThemeMode) -> Unit = { detected ->
        if (themeModeState.isAuto) {
            val resolved = if (!detected.isAuto) detected else getResolvedTheme()
            SystemThemeDetector.onThemeAppliedHook?.invoke(themeModeState, resolved)
            notifyThemeChangeListeners(resolved)
        }
    }

    init {
        SystemThemeDetector.addSystemThemeListener(systemThemeObserver)
        if (initialContent != null) {
            mainWindow.setContent(initialContent)
        }
    }

    open override fun setContent(container: Container) {
        mainWindow.setContent(container)
        applyActiveStyleSheetsToRootContent()
    }

    open override fun getContent(): Container? = mainWindow.getContent()

    override fun setMenuBar(menuBar: MenuBar?) {
        mainWindow.setMenuBar(menuBar)
    }

    override fun getMenuBar(): MenuBar? = mainWindow.getMenuBar()

    override fun setTitleBar(titleBar: TitleBar?) {
        mainWindow.setTitleBar(titleBar)
    }

    override fun getTitleBar(): TitleBar? = mainWindow.getTitleBar()

    override fun setThemeMode(mode: ThemeMode) {
        themeModeState = mode
        appNativeImpl?.setThemeMode(mode)
        val resolved = getResolvedTheme()
        SystemThemeDetector.onThemeAppliedHook?.invoke(mode, resolved)
        notifyThemeChangeListeners(resolved)
    }

    override fun getThemeMode(): ThemeMode = appNativeImpl?.getThemeMode() ?: themeModeState

    override fun setTheme(theme: ThemeMode) {
        setThemeMode(theme)
    }

    override fun getTheme(): ThemeMode = getThemeMode()

    override fun getResolvedTheme(): ThemeMode {
        val current = getThemeMode()
        if (!current.isAuto) return current
        val nativeDetected = appNativeImpl?.detectSystemThemeMode()?.takeIf { !it.isAuto }
        return nativeDetected ?: SystemThemeDetector.detectSystemTheme()
    }

    override fun addThemeChangeListener(listener: (ThemeMode) -> Unit) {
        if (listener !in themeChangeListeners) {
            themeChangeListeners.add(listener)
        }
    }

    override fun removeThemeChangeListener(listener: (ThemeMode) -> Unit) {
        themeChangeListeners.remove(listener)
    }

    private fun notifyThemeChangeListeners(resolvedTheme: ThemeMode) {
        for (listener in themeChangeListeners.toList()) {
            listener(resolvedTheme)
        }
    }

    override fun addStyleSheet(styleSheetPath: String) {
        val normalized = StyleSheetRegistry.normalizePath(styleSheetPath)
        if (normalized.isEmpty()) return
        val resolved = StyleSheetRegistry.resolveByPath(normalized)
            ?: createFallbackStyleSheet(normalized)
        addStyleSheet(resolved)
    }

    override fun addStyleSheet(styleSheetResourceId: ResourceID) {
        val resolved = StyleSheetRegistry.resolveByResourceId(styleSheetResourceId)
            ?: Resources.getStyleSheet(styleSheetResourceId)
            ?: createFallbackStyleSheet(styleSheetResourceId.toResourceUri())
        StyleSheetRegistry.register(resolved, styleSheetResourceId)
        addStyleSheet(resolved)
    }

    override fun addStyleSheet(styleSheet: PrecompiledStyleSheet) {
        StyleSheetRegistry.register(styleSheet)
        val existingIdx = activeStyleSheets.indexOfFirst {
            it === styleSheet ||
                (it.sourcePath.isNotBlank() &&
                    StyleSheetRegistry.normalizePath(it.sourcePath) == StyleSheetRegistry.normalizePath(styleSheet.sourcePath))
        }
        if (existingIdx >= 0) {
            activeStyleSheets[existingIdx] = styleSheet
        } else {
            activeStyleSheets.add(styleSheet)
        }
        styleSheet.applyTo(this)
        applyActiveStyleSheetsToRootContent()
        appNativeImpl?.addStyleSheet(styleSheet)
        SystemThemeDetector.onStyleSheetChangedHook?.invoke(this)
    }

    override fun removeStyleSheet(styleSheetPath: String): Boolean {
        val normalized = StyleSheetRegistry.normalizePath(styleSheetPath)
        if (normalized.isEmpty()) return false
        val resolved = StyleSheetRegistry.resolveByPath(normalized)
        if (resolved != null && resolved in activeStyleSheets) {
            return removeStyleSheet(resolved)
        }
        val baseName = normalized.substringAfterLast('/').substringBeforeLast('.')
        val target = activeStyleSheets.firstOrNull {
            val candidate = StyleSheetRegistry.normalizePath(it.sourcePath)
            val nameCandidate = StyleSheetRegistry.normalizePath(it.name)
            val cssCandidate = StyleSheetRegistry.normalizePath(it.cssResourcePath)
            candidate == normalized ||
                candidate.endsWith("/$normalized") ||
                nameCandidate == normalized ||
                (baseName.isNotEmpty() && nameCandidate == baseName) ||
                cssCandidate == normalized ||
                cssCandidate.endsWith("/$normalized")
        } ?: return false
        return removeStyleSheet(target)
    }

    override fun removeStyleSheet(styleSheetResourceId: ResourceID): Boolean {
        val registered = StyleSheetRegistry.resolveByResourceId(styleSheetResourceId)
        if (registered != null && removeStyleSheet(registered)) {
            return true
        }
        val uri = StyleSheetRegistry.normalizePath(styleSheetResourceId.toResourceUri())
        val byUri = activeStyleSheets.firstOrNull { StyleSheetRegistry.normalizePath(it.sourcePath) == uri }
        return if (byUri != null) removeStyleSheet(byUri) else false
    }

    override fun removeStyleSheet(styleSheet: PrecompiledStyleSheet): Boolean {
        val removed = activeStyleSheets.remove(styleSheet)
        if (removed) {
            appNativeImpl?.removeStyleSheet(styleSheet)
            SystemThemeDetector.onStyleSheetChangedHook?.invoke(this)
        }
        return removed
    }

    override fun hasStyleSheet(styleSheetPath: String): Boolean {
        val normalized = StyleSheetRegistry.normalizePath(styleSheetPath)
        if (normalized.isEmpty()) return false
        val resolved = StyleSheetRegistry.resolveByPath(normalized)
        if (resolved != null && resolved in activeStyleSheets) {
            return true
        }
        val baseName = normalized.substringAfterLast('/').substringBeforeLast('.')
        return activeStyleSheets.any {
            val candidate = StyleSheetRegistry.normalizePath(it.sourcePath)
            val nameCandidate = StyleSheetRegistry.normalizePath(it.name)
            val cssCandidate = StyleSheetRegistry.normalizePath(it.cssResourcePath)
            candidate == normalized ||
                candidate.endsWith("/$normalized") ||
                nameCandidate == normalized ||
                (baseName.isNotEmpty() && nameCandidate == baseName) ||
                cssCandidate == normalized ||
                cssCandidate.endsWith("/$normalized")
        }
    }

    override fun hasStyleSheet(styleSheetResourceId: ResourceID): Boolean {
        val registered = StyleSheetRegistry.resolveByResourceId(styleSheetResourceId)
        if (registered != null && registered in activeStyleSheets) return true
        val uri = StyleSheetRegistry.normalizePath(styleSheetResourceId.toResourceUri())
        return activeStyleSheets.any { StyleSheetRegistry.normalizePath(it.sourcePath) == uri }
    }

    override fun getStyleSheets(): List<PrecompiledStyleSheet> = activeStyleSheets.toList()

    override fun getStyleSheetPaths(): List<String> = activeStyleSheets.map { it.sourcePath }

    private fun applyActiveStyleSheetsToRootContent() {
        val content = mainWindow.getContent()
        if (content != null) {
            for (sheet in activeStyleSheets) {
                sheet.styleSheet.applyToTree(content)
            }
        }
        val currentFragRoot = mainWindow.fragmentContainer?.currentFragment
        if (currentFragRoot != null && currentFragRoot !== content) {
            for (sheet in activeStyleSheets) {
                sheet.styleSheet.applyToTree(currentFragRoot)
            }
        }
    }

    private fun createFallbackStyleSheet(sourcePath: String): PrecompiledStyleSheet {
        val emptyCss = CssStyleSheet.empty()
        val logicalName = sourcePath.substringAfterLast('/').substringBeforeLast('.').ifBlank { "StyleSheet" }
        return object : PrecompiledStyleSheet {
            override val name: String = logicalName
            override val sourcePath: String = sourcePath
            override val cssResourcePath: String = sourcePath
            override val styleSheet: CssStyleSheet = emptyCss
            override val rawCss: String = ""
        }
    }

    /**
     * Sets the application's primary accent / chrome theme color (e.g., `"#0d6efd"` or `"#2A80FF"`),
     * propagated to all platform backends that support it (Web `<meta name="theme-color">` and
     * `--genericui-theme-color`, Android `statusBarColor` / `TaskDescription` / Material primary,
     * iOS `UIView.tintColor`, and Desktop window accent / CSS variables).
     */
    override fun setThemeColor(colorHex: String?) {
        themeColorState = colorHex
        appNativeImpl?.setThemeColor(colorHex)
    }

    override fun getThemeColor(): String? = appNativeImpl?.getThemeColor() ?: themeColorState

    open override fun run(factory: NativeFactory) {
        GenericUI.initialize(factory)
        val impl = factory.createApplication()
        appNativeImpl = impl
        impl.setThemeMode(themeModeState)
        if (themeColorState != null) {
            impl.setThemeColor(themeColorState)
        }
        for (sheet in activeStyleSheets) {
            impl.addStyleSheet(sheet)
        }
        val resolved = getResolvedTheme()
        SystemThemeDetector.onThemeAppliedHook?.invoke(themeModeState, resolved)
        impl.runEventLoop {
            onApplicationStarted()
            applyActiveStyleSheetsToRootContent()
            mainWindow.show(factory)
        }
    }

    protected open fun onApplicationStarted() {}

    override fun exit(exitCode: Int) {
        mainWindow.close()
        appNativeImpl?.exitApplication(exitCode)
    }

    override fun dispose() {
        SystemThemeDetector.removeSystemThemeListener(systemThemeObserver)
        themeChangeListeners.clear()
        mainWindow.dispose()
        appNativeImpl?.dispose()
        appNativeImpl = null
    }
}
