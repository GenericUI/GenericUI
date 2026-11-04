package com.nexustools.genericui.component.shell

import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.system.PreferenceStore
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.enums.ui.ThemeMode
import com.nexustools.genericui.nativeimpl.shell.ApplicationNativeImpl
import com.nexustools.genericui.nativeimpl.system.NetworkMonitorNativeImpl
import com.nexustools.genericui.res.Resources
import com.nexustools.genericui.resources.ResourceID
import com.nexustools.genericui.style.css.CssStyleSheet
import com.nexustools.genericui.style.css.PrecompiledStyleSheet
import com.nexustools.genericui.style.css.StyleSheetRegistry
import com.nexustools.genericui.style.theme.SystemThemeDetector
import com.nexustools.genericui.window.IWindow
import com.nexustools.genericui.window.TitleBar

/**
 * Standard Container-driven application (`Application`).
 *
 * Accepts a [Container] (and does not force developers to use `Fragment`s if they do not need them).
 * For Fragment-driven applications (including Helper Fragments like `HybridColumnFragment` and `DockWorkspaceFragment`),
 * use [FragmentApplication].
 */
open class Application(
    initialContent: Container? = null,
    title: String = "GenericUI Application",
    initialThemeColor: String? = null
) : IApplication {

    override val mainWindow: Window = Window(title = title)
    internal var appNativeImpl: ApplicationNativeImpl<*>? = null
        private set
    private var activeFactory: NativeFactory? = null

    private var themeModeState: ThemeMode = GenericUI.defaultThemeMode
    private var themeColorState: String? = initialThemeColor
    private val activeStyleSheets = mutableListOf<PrecompiledStyleSheet>()
    private val builtInStyleSheets = mutableListOf<PrecompiledStyleSheet>()
    private val themeChangeListeners = mutableListOf<(ThemeMode) -> Unit>()

    private var networkMonitorNativeImpl: NetworkMonitorNativeImpl? = null
    private var networkMonitorFactory: NativeFactory? = null
    private var simulatedOfflineOverride: Boolean? = null
    private val offlineListeners = mutableListOf<(Boolean) -> Unit>()
    private var onOfflineChangedCallback: ((Boolean) -> Unit)? = null
    private var lastKnownOfflineState: Boolean = false
    private var defaultPreferenceStore: PreferenceStore? = null

    private var isHandlingSystemThemeChange: Boolean = false

    private val systemThemeObserver: (ThemeMode) -> Unit = { detected ->
        if (!isHandlingSystemThemeChange && themeModeState.isAuto) {
            isHandlingSystemThemeChange = true
            try {
                val resolved = if (!detected.isAuto) detected else getResolvedTheme()
                appNativeImpl?.setThemeMode(ThemeMode.AUTO)
                SystemThemeDetector.onThemeAppliedHook?.invoke(ThemeMode.AUTO, resolved)
                notifyThemeChangeListeners(resolved)
            } finally {
                isHandlingSystemThemeChange = false
            }
        }
    }

    init {
        current = this
        Resources.ensureBuiltInResourcesRegistered()
        for (builtInId in listOf(Resources.GENERICUI, Resources.BOOTSTRAP, Resources.BOOTSTRAP_ICONS)) {
            val sheet = StyleSheetRegistry.resolveByResourceId(builtInId) ?: Resources.getStyleSheet(builtInId)
            if (sheet != null && sheet !in builtInStyleSheets) {
                builtInStyleSheets.add(sheet)
            }
        }
        registerWindowApplication(mainWindow, this)
        SystemThemeDetector.addSystemThemeListener(systemThemeObserver)
        if (GenericUI.activeFactory != null) {
            ensureNetworkMonitor(GenericUI.activeFactory)
        }
        if (initialContent != null) {
            if (initialContent is Fragment) {
                initialContent.application = this
            }
            mainWindow.setContent(initialContent)
        }
    }

    override val isOffline: Boolean
        get() {
            simulatedOfflineOverride?.let { return it }
            val monitor = ensureNetworkMonitor(GenericUI.activeFactory)
            if (monitor != null) {
                return !monitor.isOnline()
            }
            return appNativeImpl?.isOffline() ?: false
        }

    override val isOnline: Boolean
        get() = !isOffline

    override fun addOfflineListener(listener: (isOffline: Boolean) -> Unit) {
        ensureNetworkMonitor(GenericUI.activeFactory)
        if (listener !in offlineListeners) {
            offlineListeners.add(listener)
        }
    }

    override fun removeOfflineListener(listener: (isOffline: Boolean) -> Unit) {
        offlineListeners.remove(listener)
    }

    override fun setOnOfflineChanged(listener: ((isOffline: Boolean) -> Unit)?) {
        ensureNetworkMonitor(GenericUI.activeFactory)
        onOfflineChangedCallback = listener
        appNativeImpl?.setOnOfflineChanged(listener)
    }

    /**
     * Overrides or clears the simulated offline state for testing or offline preview mode,
     * immediately notifying registered offline listeners.
     */
    fun setSimulatedOffline(offline: Boolean?) {
        simulatedOfflineOverride = offline
        notifyOfflineStateChanged(isOffline)
    }

    private fun ensureNetworkMonitor(factory: NativeFactory?): NetworkMonitorNativeImpl? {
        val activeFactory = factory ?: GenericUI.activeFactory ?: return networkMonitorNativeImpl
        val existing = networkMonitorNativeImpl
        if (existing != null && networkMonitorFactory === activeFactory) {
            return existing
        }
        existing?.setOnConnectivityChanged(null)
        existing?.dispose()
        val monitor = activeFactory.createNetworkMonitor()
        networkMonitorNativeImpl = monitor
        networkMonitorFactory = activeFactory
        lastKnownOfflineState = simulatedOfflineOverride ?: !monitor.isOnline()
        monitor.setOnConnectivityChanged { online ->
            if (simulatedOfflineOverride == null) {
                notifyOfflineStateChanged(!online)
            }
        }
        return monitor
    }

    private fun notifyOfflineStateChanged(offline: Boolean) {
        lastKnownOfflineState = offline
        onOfflineChangedCallback?.invoke(offline)
        for (listener in offlineListeners.toList()) {
            listener(offline)
        }
    }

    open override fun setContent(container: Container) {
        if (container is Fragment) {
            container.application = this
        }
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

    override fun getThemeMode(): ThemeMode =
        if (themeModeState.isAuto) ThemeMode.AUTO else (appNativeImpl?.getThemeMode() ?: themeModeState)

    override fun getResolvedTheme(): ThemeMode {
        val current = getThemeMode()
        if (!current.isAuto) return current
        SystemThemeDetector.overrideDetectedTheme?.let {
            if (!it.isAuto) return it
        }
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
        applyActiveStyleSheetsToRootContent()
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

    private fun isBuiltInStyleSheet(styleSheet: PrecompiledStyleSheet): Boolean {
        for (builtInId in listOf(Resources.GENERICUI, Resources.BOOTSTRAP, Resources.BOOTSTRAP_ICONS)) {
            val builtIn = StyleSheetRegistry.resolveByResourceId(builtInId) ?: Resources.getStyleSheet(builtInId)
            if (builtIn === styleSheet) return true
            val normBuiltIn = StyleSheetRegistry.normalizePath(builtInId.relativePath)
            if (normBuiltIn.isNotEmpty() &&
                (StyleSheetRegistry.normalizePath(styleSheet.sourcePath) == normBuiltIn ||
                    StyleSheetRegistry.normalizePath(styleSheet.cssResourcePath) == normBuiltIn)
            ) {
                return true
            }
        }
        return false
    }

    override fun addStyleSheet(styleSheet: PrecompiledStyleSheet) {
        StyleSheetRegistry.register(styleSheet)
        if (isBuiltInStyleSheet(styleSheet)) {
            val existingBuiltInIdx = builtInStyleSheets.indexOfFirst {
                it === styleSheet ||
                    (it.sourcePath.isNotBlank() &&
                        StyleSheetRegistry.normalizePath(it.sourcePath) == StyleSheetRegistry.normalizePath(styleSheet.sourcePath))
            }
            if (existingBuiltInIdx >= 0) {
                builtInStyleSheets[existingBuiltInIdx] = styleSheet
            } else {
                builtInStyleSheets.add(styleSheet)
            }
            appNativeImpl?.addStyleSheet(styleSheet)
            SystemThemeDetector.onStyleSheetChangedHook?.invoke(this)
            return
        }
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
        if (resolved != null && (resolved in activeStyleSheets || resolved in builtInStyleSheets)) {
            return removeStyleSheet(resolved)
        }
        val baseName = normalized.substringAfterLast('/').substringBeforeLast('.')
        val allSheets = activeStyleSheets + builtInStyleSheets
        val target = allSheets.firstOrNull {
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
            ?: Resources.getStyleSheet(styleSheetResourceId)
        if (registered != null && removeStyleSheet(registered)) {
            return true
        }
        val uri = StyleSheetRegistry.normalizePath(styleSheetResourceId.toResourceUri())
        val relPath = StyleSheetRegistry.normalizePath(styleSheetResourceId.relativePath)
        val allSheets = activeStyleSheets + builtInStyleSheets
        val byMatch = allSheets.firstOrNull {
            val src = StyleSheetRegistry.normalizePath(it.sourcePath)
            val css = StyleSheetRegistry.normalizePath(it.cssResourcePath)
            val name = StyleSheetRegistry.normalizePath(it.name)
            src == uri ||
                (relPath.isNotEmpty() && (src == relPath || css == relPath)) ||
                name == StyleSheetRegistry.normalizePath(styleSheetResourceId.name)
        }
        return if (byMatch != null) removeStyleSheet(byMatch) else false
    }

    override fun removeStyleSheet(styleSheet: PrecompiledStyleSheet): Boolean {
        val removedUser = activeStyleSheets.remove(styleSheet)
        val removedBuiltIn = builtInStyleSheets.remove(styleSheet)
        val removed = removedUser || removedBuiltIn
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
        if (resolved != null && (resolved in activeStyleSheets || resolved in builtInStyleSheets)) {
            return true
        }
        val baseName = normalized.substringAfterLast('/').substringBeforeLast('.')
        val allSheets = activeStyleSheets + builtInStyleSheets
        return allSheets.any {
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
            ?: Resources.getStyleSheet(styleSheetResourceId)
        if (registered != null && (registered in activeStyleSheets || registered in builtInStyleSheets)) return true
        val uri = StyleSheetRegistry.normalizePath(styleSheetResourceId.toResourceUri())
        val relPath = StyleSheetRegistry.normalizePath(styleSheetResourceId.relativePath)
        val allSheets = activeStyleSheets + builtInStyleSheets
        return allSheets.any {
            val src = StyleSheetRegistry.normalizePath(it.sourcePath)
            val css = StyleSheetRegistry.normalizePath(it.cssResourcePath)
            val name = StyleSheetRegistry.normalizePath(it.name)
            src == uri ||
                (relPath.isNotEmpty() && (src == relPath || css == relPath)) ||
                name == StyleSheetRegistry.normalizePath(styleSheetResourceId.name)
        }
    }

    override fun getStyleSheets(): List<PrecompiledStyleSheet> = activeStyleSheets.toList()

    override fun getStyleSheetPaths(): List<String> = activeStyleSheets.map { it.sourcePath }

    private fun applyActiveStyleSheetsToRootContent() {
        val activeFactory = mainWindow.resolveActiveFactory() ?: GenericUI.activeFactory
        val engineEnabled = activeFactory?.isStyleSheetEngineEnabled ?: true
        val content = mainWindow.getContent()
        if (content != null) {
            if (engineEnabled) {
                for (sheet in activeStyleSheets) {
                    sheet.styleSheet.applyToTree(content)
                }
            }
            refreshThemeDependentTree(content)
        }
        val currentFragRoot = mainWindow.fragmentContainer?.currentFragment
        if (currentFragRoot != null && currentFragRoot !== content) {
            if (engineEnabled) {
                for (sheet in activeStyleSheets) {
                    sheet.styleSheet.applyToTree(currentFragRoot)
                }
            }
            refreshThemeDependentTree(currentFragRoot)
        }
    }

    private fun refreshThemeDependentTree(component: com.nexustools.genericui.core.AbstractComponent) {
        if (component is com.nexustools.genericui.component.content.ImageView) {
            component.onThemeChanged()
        }
        if (component is com.nexustools.genericui.core.AbstractContainer) {
            for (child in component.children) {
                refreshThemeDependentTree(child)
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

    override fun getPreferenceStore(): PreferenceStore {
        val existing = defaultPreferenceStore
        if (existing != null) {
            return existing
        }
        val created = PreferenceStore()
        defaultPreferenceStore = created
        return created
    }

    open override fun run(factory: NativeFactory) {
        current = this
        activeFactory = factory
        GenericUI.initialize(factory)
        ensureNetworkMonitor(factory)
        val impl = factory.createApplication()
        appNativeImpl = impl
        impl.setOnOfflineChanged(onOfflineChangedCallback)
        impl.setThemeMode(themeModeState)
        if (themeColorState != null) {
            impl.setThemeColor(themeColorState)
        }
        for (builtInId in listOf(Resources.GENERICUI, Resources.BOOTSTRAP, Resources.BOOTSTRAP_ICONS)) {
            val builtInSheet = StyleSheetRegistry.resolveByResourceId(builtInId) ?: Resources.getStyleSheet(builtInId)
            if (builtInSheet != null) {
                if (hasStyleSheet(builtInId)) {
                    impl.addStyleSheet(builtInSheet)
                } else {
                    impl.removeStyleSheet(builtInSheet)
                }
            }
        }
        for (sheet in activeStyleSheets) {
            impl.addStyleSheet(sheet)
        }
        val resolved = getResolvedTheme()
        SystemThemeDetector.onThemeAppliedHook?.invoke(themeModeState, resolved)
        notifyThemeChangeListeners(resolved)
        impl.runEventLoop {
            onApplicationStarted()
            applyActiveStyleSheetsToRootContent()
            mainWindow.show(factory)
            finishWebSplash()
        }
    }

    override val isWebSplashSupported: Boolean
        get() = (activeFactory ?: GenericUI.activeFactory)?.isWebSplashSupported == true

    override fun setWebSplashStatus(statusText: String, type: BootstrapType) {
        (activeFactory ?: GenericUI.activeFactory)?.setWebSplashStatus(statusText, type)
    }

    override fun setWebSplashProgress(progressPercent: Int, type: BootstrapType) {
        (activeFactory ?: GenericUI.activeFactory)?.setWebSplashProgress(progressPercent, type)
    }

    override fun setWebSplashError(message: String, stackTrace: String?, title: String) {
        (activeFactory ?: GenericUI.activeFactory)?.setWebSplashError(message, stackTrace, title)
    }

    override fun finishWebSplash() {
        (activeFactory ?: GenericUI.activeFactory)?.finishWebSplash()
    }

    protected open fun onApplicationStarted() {}

    override fun exit(exitCode: Int) {
        mainWindow.close()
        appNativeImpl?.exitApplication(exitCode)
    }

    override fun dispose() {
        unregisterWindowApplication(mainWindow, this)
        defaultPreferenceStore?.dispose()
        defaultPreferenceStore = null
        SystemThemeDetector.removeSystemThemeListener(systemThemeObserver)
        themeChangeListeners.clear()
        networkMonitorNativeImpl?.setOnConnectivityChanged(null)
        networkMonitorNativeImpl?.dispose()
        networkMonitorNativeImpl = null
        networkMonitorFactory = null
        offlineListeners.clear()
        onOfflineChangedCallback = null
        mainWindow.dispose()
        appNativeImpl?.dispose()
        appNativeImpl = null
        activeFactory = null
        if (current === this) {
            current = null
        }
    }

    companion object {
        var current: Application? = null
            internal set

        private val windowApplications = linkedMapOf<Any, IApplication>()

        internal fun registerWindowApplication(window: Any, application: IApplication) {
            windowApplications[window] = application
        }

        internal fun unregisterWindowApplication(window: Any, application: IApplication) {
            if (windowApplications[window] === application) {
                windowApplications.remove(window)
            }
        }

        internal fun findApplicationForWindow(window: Any?): IApplication? {
            if (window == null) return null
            return windowApplications[window]
        }
    }
}
