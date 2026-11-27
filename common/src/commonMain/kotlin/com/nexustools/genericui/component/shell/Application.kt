package com.nexustools.genericui.component.shell

import com.nexustools.genericui.component.composite.Composite
import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.fragment.FragmentBundle
import com.nexustools.genericui.component.fragment.FragmentContainer
import com.nexustools.genericui.component.fragment.FragmentHost
import com.nexustools.genericui.component.fragment.FragmentLaunchCoordinator
import com.nexustools.genericui.component.fragment.FragmentRegistry
import com.nexustools.genericui.component.fragment.IFragment
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.layout.styled.StyledContainer
import com.nexustools.genericui.component.surface.styled.StyledComponent
import com.nexustools.genericui.component.system.PreferenceStore
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.core.GenericUiExtensionRegistry
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.core.UiTimerScheduler
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.enums.system.BackendTarget
import com.nexustools.genericui.enums.ui.ThemeMode
import com.nexustools.genericui.enums.window.WindowState
import com.nexustools.genericui.event.KeyShortcut
import com.nexustools.genericui.exception.NativeUninitializedException
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.nativeimpl.shell.ApplicationNativeImpl
import com.nexustools.genericui.nativeimpl.shell.MenuNativeImpl
import com.nexustools.genericui.nativeimpl.system.NetworkMonitorNativeImpl
import com.nexustools.genericui.res.Resources
import com.nexustools.genericui.resources.ResourceID
import com.nexustools.genericui.style.css.CssStyleSheet
import com.nexustools.genericui.style.css.PrecompiledStyleSheet
import com.nexustools.genericui.style.css.StyleSheetRegistry
import com.nexustools.genericui.style.theme.SystemThemeDetector
import com.nexustools.genericui.style.theme.ThemeConfig
import com.nexustools.genericui.style.theme.ThemeStyleSheetRegistry
import com.nexustools.genericui.window.IWindow
import com.nexustools.genericui.window.MainWindowSavedState
import com.nexustools.genericui.window.TitleBar
import com.nexustools.genericui.window.WindowAspectRatioHelper
import kotlin.reflect.KClass

/**
 * Standard Container-driven or Fragment-driven application (`Application`).
 *
 * Accepts an optional [Container] (and does not force developers to use `Fragment`s if they do not need them),
 * and also implements [FragmentHost] and provides overridable [createMainFragment] / [onCreateMainFragment]
 * routines for Desktop targets (including Swing).
 */
open class Application(
    initialContent: Container? = null,
    title: String = "GenericUI Application",
    initialThemeColor: String? = null
) : IApplication, FragmentHost {

    override val mainWindow: Window = Window(title = title)
    internal var appNativeImpl: ApplicationNativeImpl<*>? = null
        private set
    private var activeFactory: NativeFactory? = null

    var mainFragmentClass: KClass<out Fragment>? = null
    var mainFragmentFactory: ((FragmentBundle?) -> Fragment)? = null
    var launchArguments: FragmentBundle? = null
    var suppressInitialMainWindowShow: Boolean = false
    var exitWhenNoFragmentsOpen: Boolean = true
    private var isEventLoopRunning: Boolean = false
    private var defaultMainFragmentFactory: ((Array<String>) -> Fragment)? = null

    private var inFragmentBundleCreate: Boolean = false
    private var inStringArrayCreate: Boolean = false
    private var inFragmentBundleOnCreate: Boolean = false
    private var inStringArrayOnCreate: Boolean = false

    internal fun bindDefaultMainFragmentFactory(factory: (Array<String>) -> Fragment) {
        defaultMainFragmentFactory = factory
    }

    open val rootFragmentContainer: FragmentContainer?
        get() = mainWindow.fragmentContainer

    override val currentFragment: Fragment?
        get() = rootFragmentContainer?.currentFragment

    override val backStackDepth: Int
        get() = rootFragmentContainer?.backStackDepth ?: 0

    override val forwardStackDepth: Int
        get() = rootFragmentContainer?.forwardStackDepth ?: 0

    override val canGoBack: Boolean
        get() = rootFragmentContainer?.canGoBack ?: false

    override val canGoForward: Boolean
        get() = rootFragmentContainer?.canGoForward ?: false

    override fun setFragment(fragment: Fragment) {
        fragment.application = this
        mainWindow.getOrCreateFragmentContainer().setFragment(fragment)
    }

    override fun pushFragment(fragment: Fragment) {
        fragment.application = this
        mainWindow.getOrCreateFragmentContainer().pushFragment(fragment)
    }

    override fun popFragment(): Boolean =
        mainWindow.fragmentContainer?.popFragment() ?: false

    override fun goForward(): Boolean =
        mainWindow.fragmentContainer?.goForward() ?: false

    override fun replaceFragment(fragment: Fragment) {
        fragment.application = this
        mainWindow.getOrCreateFragmentContainer().replaceFragment(fragment)
    }

    private fun resolveCapabilityFactory(operationName: String): NativeFactory =
        activeFactory
            ?: mainWindow.ExplicitFactory
            ?: mainWindow.resolveActiveFactory()
            ?: GenericUI.activeFactory
            ?: throw NativeUninitializedException("Application", operationName)

    override val supportsMultipleWindows: Boolean
        get() = resolveCapabilityFactory("supportsMultipleWindows").supportsMultipleWindows

    override val supportsDialogWindows: Boolean
        get() = resolveCapabilityFactory("supportsDialogWindows").supportsDialogWindows

    override fun canOpenAdditionalWindow(fragmentClass: KClass<out IFragment>): Boolean {
        val factory = resolveCapabilityFactory("canOpenAdditionalWindow")
        return factory.supportsMultipleWindows &&
            !FragmentRegistry.isDialogFragment(fragmentClass) &&
            FragmentRegistry.supportsMultipleWindows(fragmentClass)
    }

    /** Reified convenience for [canOpenAdditionalWindow]. */
    inline fun <reified T : IFragment> canOpenAdditionalWindow(): Boolean =
        canOpenAdditionalWindow(T::class)

    /** Reified convenience for [startFragment]. */
    inline fun <reified T : IFragment> startFragment(
        launchArgs: FragmentBundle? = null,
        openAdditionalWindow: Boolean = false,
        addToBackStack: Boolean = true,
        clearTask: Boolean = false
    ): T = startFragment(
        fragmentClass = T::class,
        launchArgs = launchArgs,
        openAdditionalWindow = openAdditionalWindow,
        addToBackStack = addToBackStack,
        clearTask = clearTask
    )

    /** Reified convenience for [startFragment] with key-value pair arguments. */
    inline fun <reified T : IFragment> startFragment(
        firstArg: Pair<String, Any?>,
        vararg extraArgs: Pair<String, Any?>,
        openAdditionalWindow: Boolean = false,
        addToBackStack: Boolean = true,
        clearTask: Boolean = false
    ): T = startFragment(
        fragmentClass = T::class,
        launchArgs = FragmentBundle.of(firstArg, *extraArgs),
        openAdditionalWindow = openAdditionalWindow,
        addToBackStack = addToBackStack,
        clearTask = clearTask
    )

    /**
     * Overridable routine that instantiates the default `@MainAppFragment` on Desktop targets.
     */
    open fun createMainFragment(args: FragmentBundle? = launchArguments): Fragment? {
        if (!inStringArrayCreate) {
            inFragmentBundleCreate = true
            try {
                val rawArgs = args?.getCommandLineArgsArray() ?: emptyArray()
                val fromArray = createMainFragment(rawArgs)
                if (fromArray != null) {
                    if (fromArray.arguments == null && args != null) {
                        fromArray.arguments = args
                    }
                    fromArray.application = this
                    return fromArray
                }
            } finally {
                inFragmentBundleCreate = false
            }
        }
        val effectiveArgs = args ?: launchArguments
        val created = mainFragmentFactory?.invoke(effectiveArgs)
            ?: defaultMainFragmentFactory?.invoke(effectiveArgs?.getCommandLineArgsArray() ?: emptyArray())
            ?: mainFragmentClass?.let { FragmentRegistry.instantiate(it, effectiveArgs) }
        if (created != null) {
            if (created.arguments == null && effectiveArgs != null) {
                created.arguments = effectiveArgs
            }
            created.application = this
        }
        return created
    }

    /**
     * Overridable routine that instantiates the default `@MainAppFragment` on Desktop targets using raw CLI `args`.
     */
    open fun createMainFragment(args: Array<String>): Fragment? {
        if (!inFragmentBundleCreate) {
            inStringArrayCreate = true
            try {
                val bundle = launchArguments ?: if (args.isNotEmpty()) FragmentBundle.fromCommandLineArgs(args) else null
                return createMainFragment(bundle)
            } finally {
                inStringArrayCreate = false
            }
        }
        val effectiveArgs = launchArguments ?: if (args.isNotEmpty()) FragmentBundle.fromCommandLineArgs(args) else null
        val created = defaultMainFragmentFactory?.invoke(args)
            ?: mainFragmentFactory?.invoke(effectiveArgs)
            ?: mainFragmentClass?.let { FragmentRegistry.instantiate(it, effectiveArgs) }
        if (created != null) {
            if (created.arguments == null && effectiveArgs != null) {
                created.arguments = effectiveArgs
            }
            created.application = this
        }
        return created
    }

    /**
     * Overridable routine invoked on Desktop targets to create and mount the main [Fragment] into [mainWindow].
     */
    open fun onCreateMainFragment(args: FragmentBundle? = launchArguments): Fragment? {
        if (!inStringArrayOnCreate) {
            inFragmentBundleOnCreate = true
            try {
                val rawArgs = args?.getCommandLineArgsArray() ?: emptyArray()
                return onCreateMainFragment(rawArgs)
            } finally {
                inFragmentBundleOnCreate = false
            }
        }
        val effectiveArgs = args ?: launchArguments
        val fragment = createMainFragment(effectiveArgs) ?: return null
        if (fragment.arguments == null && effectiveArgs != null) {
            fragment.arguments = effectiveArgs
        }
        fragment.application = this
        if (mainWindow.fragmentContainer?.currentFragment !== fragment) {
            setFragment(fragment)
        }
        return fragment
    }

    /**
     * Overridable routine invoked on Desktop targets to create and mount the main [Fragment] into [mainWindow].
     */
    open fun onCreateMainFragment(args: Array<String>): Fragment? {
        if (!inFragmentBundleOnCreate) {
            inStringArrayOnCreate = true
            try {
                val bundle = launchArguments ?: if (args.isNotEmpty()) FragmentBundle.fromCommandLineArgs(args) else null
                return onCreateMainFragment(bundle)
            } finally {
                inStringArrayOnCreate = false
            }
        }
        val effectiveArgs = launchArguments ?: if (args.isNotEmpty()) FragmentBundle.fromCommandLineArgs(args) else null
        val fragment = createMainFragment(args) ?: return null
        if (fragment.arguments == null && effectiveArgs != null) {
            fragment.arguments = effectiveArgs
        }
        fragment.application = this
        if (mainWindow.fragmentContainer?.currentFragment !== fragment) {
            setFragment(fragment)
        }
        return fragment
    }

    private var themeModeState: ThemeMode = GenericUI.defaultThemeMode
    private var themeColorState: String? = initialThemeColor
    private var themeConfigState: ThemeConfig = GenericUI.defaultThemeConfig.let { base ->
        if (!initialThemeColor.isNullOrBlank()) base.withPrimaryAccentColor(initialThemeColor) else base
    }
    internal var hasExplicitThemeMode: Boolean = false
        private set
    internal var hasExplicitThemeColor: Boolean = !initialThemeColor.isNullOrBlank()
        private set
    internal var hasExplicitThemeConfig: Boolean = false
        private set
    private var suppressExplicitThemeTracking: Boolean = false
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

    private class ApplicationMenuAnchorContainer(
        private var boundFactory: NativeFactory? = null
    ) : Container("application-menu-anchor") {
        fun bindFactory(factory: NativeFactory?) {
            if (factory != null) {
                boundFactory = factory
            }
        }

        override fun resolveActiveFactory(): NativeFactory? =
            boundFactory ?: GenericUI.activeFactory

        override fun resolveChildFactory(): NativeFactory? =
            resolveActiveFactory()

        override fun resolveInitialNativeVisibility(): Boolean = false
    }

    /**
     * macOS-specific Application Menu instance installed during [run] when running on
     * [BackendTarget.MacOS], or `null` on all non-macOS targets (Windows, Linux, Swing,
     * Android, iOS, iPadOS, Web).
     */
    var macApplicationMenu: MacApplicationMenu? = null
        private set
    private var applicationMenuAnchor: ApplicationMenuAnchorContainer? = null
    private var isSyncingApplicationMenu: Boolean = false

    override var persistMainWindowState: Boolean = true
    override var defaultMainWindowFullscreen: Boolean = false
    private var defaultMainWindowSize: Size? = null
    private var defaultMainWindowPositionX: Int? = null
    private var defaultMainWindowPositionY: Int? = null
    private var isRestoringMainWindowState: Boolean = false
    private var pendingMainWindowStateTimerId: Int? = null
    private var pendingMainWindowStateDirty: Boolean = false

    private var isHandlingSystemThemeChange: Boolean = false

    private val systemThemeObserver: (ThemeMode) -> Unit = { detected ->
        if (!isHandlingSystemThemeChange && themeModeState.isAuto) {
            isHandlingSystemThemeChange = true
            try {
                val resolved = if (!detected.isAuto) detected else getResolvedTheme()
                appNativeImpl?.setThemeMode(ThemeMode.AUTO)
                SystemThemeDetector.onThemeConfigChangedHook?.invoke(this, themeConfigState, resolved)
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
        mainWindow.onBeforeFirstVisibleShow = { factory ->
            applyInitialMainWindowStateIfNeeded(factory)
        }
        mainWindow.onPersistentWindowStateChanged = { immediate ->
            scheduleOrWriteMainWindowState(immediate)
        }
        mainWindow.onFlushPersistentWindowState = {
            flushPendingMainWindowState()
        }
        SystemThemeDetector.addSystemThemeListener(systemThemeObserver)
        ThemeStyleSheetRegistry.onThemeStyleSheetsChanged = {
            if (current === this) {
                applyActiveStyleSheetsToRootContent()
            }
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

    /**
     * macOS-only lifecycle hook invoked by [run] exclusively when the active backend is
     * [BackendTarget.MacOS] (`supportsSystemMenuBar = true`) to install macOS-specific items
     * into the screen-top Application Menu (between "About <App>" and "Hide <App>").
     *
     * Never invoked on Windows, Linux, Swing, Android, iOS, iPadOS, or Web, keeping per-window
     * cross-platform [Menu] / [MenuBar] instances completely isolated from the macOS App Menu.
     */
    protected open fun onInstallMacApplicationMenu(menu: MacApplicationMenu) {}

    private fun installMacApplicationMenu(factory: NativeFactory, impl: ApplicationNativeImpl<*>) {
        if (factory.backendTarget != BackendTarget.MacOS || !factory.supportsSystemMenuBar) {
            return
        }
        macApplicationMenu?.dispose()
        val menuTitle = mainWindow.getTitle()
            .takeIf { it.isNotBlank() && it != "GenericUI Application" }
            ?: GenericUI.applicationName.takeIf { it.isNotBlank() && it != "GenericUI" }
            ?: mainWindow.getTitle().ifBlank { "Application" }
        val macMenu = MacApplicationMenu(label = menuTitle)
        macApplicationMenu = macMenu
        onInstallMacApplicationMenu(macMenu)
        macMenu.onMutated = { syncMacApplicationMenuToNative() }
        syncMacApplicationMenuToNative(factory = factory, impl = impl, appTitle = menuTitle)
    }

    private fun syncMacApplicationMenuToNative(
        factory: NativeFactory? = activeFactory ?: GenericUI.activeFactory,
        impl: ApplicationNativeImpl<*>? = appNativeImpl,
        appTitle: String = mainWindow.getTitle()
    ) {
        if (isSyncingApplicationMenu) return
        val resolvedImpl = impl ?: return
        val resolvedFactory = factory ?: return
        if (resolvedFactory.backendTarget != BackendTarget.MacOS || !resolvedFactory.supportsSystemMenuBar) return
        val macMenu = macApplicationMenu ?: return
        isSyncingApplicationMenu = true
        try {
            val anchor = applicationMenuAnchor ?: ApplicationMenuAnchorContainer(resolvedFactory).also {
                applicationMenuAnchor = it
            }
            anchor.bindFactory(resolvedFactory)
            anchor.ensureNativeImpl()
            val backingMenu = macMenu.internalMenu()
            if (backingMenu.parent == null) {
                backingMenu.parent = anchor
            }
            realizeMenuSubtree(backingMenu)
            resolvedImpl.setMacApplicationMenu(backingMenu.nativeImpl as? MenuNativeImpl<*>, appTitle)
        } finally {
            isSyncingApplicationMenu = false
        }
    }

    private fun realizeMenuSubtree(component: com.nexustools.genericui.core.AbstractComponent) {
        component.ensureNativeImpl()
        if (component is com.nexustools.genericui.core.AbstractContainer) {
            for (child in component.children) {
                realizeMenuSubtree(child)
            }
        }
    }

    override fun setTitleBar(titleBar: TitleBar?) {
        mainWindow.setTitleBar(titleBar)
    }

    override fun getTitleBar(): TitleBar? = mainWindow.getTitleBar()

    override fun setThemeMode(mode: ThemeMode) {
        if (!suppressExplicitThemeTracking) {
            hasExplicitThemeMode = true
        }
        themeModeState = mode
        themeConfigState = themeConfigState.withMode(mode)
        appNativeImpl?.setThemeMode(mode)
        appNativeImpl?.setThemeConfig(themeConfigState)
        val resolved = getResolvedTheme()
        SystemThemeDetector.onThemeConfigChangedHook?.invoke(this, themeConfigState, resolved)
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
            applyActiveStyleSheetsToRootContent()
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

    private var cachedCascadeRegistryVersion: Long = -1L
    private var cachedCascadeInputSheets: List<CssStyleSheet> = emptyList()
    private var cachedCombinedCascadeSheet: CssStyleSheet? = null
    private var hasAppliedAnyCascadeStyleSheet: Boolean = false

    private fun inferStyleSheetCascadeOrder(sheet: PrecompiledStyleSheet): Int {
        val src = StyleSheetRegistry.normalizePath(sheet.sourcePath)
        val css = StyleSheetRegistry.normalizePath(sheet.cssResourcePath)
        val nameNorm = sheet.name.trim().lowercase()
        val isTheme = src.contains("theme") || css.contains("theme") || nameNorm.contains("theme")
        val isExt = src.contains("ext/") || css.contains("ext/") || src.contains("extension")
        val isCore = isBuiltInStyleSheet(sheet) ||
            ((src.contains("genericui") || css.contains("genericui")) && !isExt)
        val isAppStyle = !isTheme && !isExt && !isCore && (
            src.endsWith("/style.css") ||
                src.endsWith("/style.scss") ||
                src == "style.css" ||
                src == "style.scss" ||
                css.endsWith("/style.css") ||
                css == "style.css" ||
                nameNorm == "style" ||
                nameNorm.contains("styleprecompiled")
            )
        return when {
            isCore && !isTheme -> 100
            isCore -> 200
            isExt && !isTheme -> 300
            isExt -> 400
            isAppStyle -> 500
            else -> 700
        }
    }

    internal fun buildCombinedActiveStyleSheet(): CssStyleSheet? {
        val coreStyleSheet = Resources.coreDefaultStyleSheet()
        val hasCoreStyle = coreStyleSheet.rules.isNotEmpty()
        val themeLayers = ThemeStyleSheetRegistry.getCompiledThemeLayers()
        val regVersion = ThemeStyleSheetRegistry.registryVersion
        if (!hasCoreStyle && themeLayers.isEmpty() && activeStyleSheets.isEmpty()) {
            if (!hasAppliedAnyCascadeStyleSheet) return null
            return CssStyleSheet.empty()
        }

        val combinedEntries = ArrayList<Pair<Int, CssStyleSheet>>(
            (if (hasCoreStyle) 1 else 0) + themeLayers.size + activeStyleSheets.size
        )
        if (hasCoreStyle) {
            combinedEntries.add(100 to coreStyleSheet)
        }
        for ((order, cssSheet) in themeLayers) {
            combinedEntries.add(order to cssSheet)
        }
        for (precompiled in activeStyleSheets) {
            combinedEntries.add(inferStyleSheetCascadeOrder(precompiled) to precompiled.styleSheet)
        }
        val orderedSheets = combinedEntries.sortedBy { it.first }.map { it.second }
        if (regVersion == cachedCascadeRegistryVersion &&
            orderedSheets.size == cachedCascadeInputSheets.size &&
            orderedSheets.indices.all { orderedSheets[it] === cachedCascadeInputSheets[it] } &&
            cachedCombinedCascadeSheet != null
        ) {
            return cachedCombinedCascadeSheet
        }
        val combined = CssStyleSheet.combineCascade(orderedSheets)
        cachedCascadeRegistryVersion = regVersion
        cachedCascadeInputSheets = orderedSheets
        cachedCombinedCascadeSheet = combined
        return combined
    }

    fun applyActiveStyleSheetsTo(
        root: com.nexustools.genericui.core.AbstractComponent,
        viewportWidthPx: Int = mainWindow.getWindowSize().width.takeIf { it > 0 } ?: 1280,
        refreshThemeDependents: Boolean = true
    ) {
        val activeFactory = mainWindow.resolveActiveFactory() ?: GenericUI.activeFactory
        val engineEnabled = activeFactory?.isStyleSheetEngineEnabled ?: true
        if (engineEnabled) {
            val combined = buildCombinedActiveStyleSheet()
            if (combined != null) {
                hasAppliedAnyCascadeStyleSheet = true
                combined.applyToTree(root, viewportWidthPx)
            }
        }
        if (refreshThemeDependents) {
            refreshThemeDependentTree(root)
        }
    }

    fun applyActiveStyleSheetsToRootContent() {
        val content = mainWindow.getContent()
        if (content != null) {
            applyActiveStyleSheetsTo(content)
        }
        val currentFragRoot = mainWindow.fragmentContainer?.currentFragment
        if (currentFragRoot != null && currentFragRoot !== content) {
            applyActiveStyleSheetsTo(currentFragRoot)
        }
    }

    private fun refreshThemeDependentTree(component: com.nexustools.genericui.core.AbstractComponent) {
        if (component is com.nexustools.genericui.component.content.ImageView) {
            component.onThemeChanged()
        }
        if (component is com.nexustools.genericui.component.action.Button) {
            component.onThemeChanged()
        }
        if (component is StyledContainer) {
            component.onThemeChanged()
        }
        if (component is StyledComponent) {
            component.onThemeChanged()
        }
        if (component is com.nexustools.genericui.component.form.styled.StyledTextField) {
            component.onThemeChanged()
        }
        if (component is Composite) {
            component.onThemeChanged()
            for (child in component.internalCompositeChildren()) {
                refreshThemeDependentTree(child)
            }
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
        if (!suppressExplicitThemeTracking && !colorHex.isNullOrBlank()) {
            hasExplicitThemeColor = true
        }
        themeColorState = colorHex
        if (!colorHex.isNullOrBlank()) {
            themeConfigState = themeConfigState.withPrimaryAccentColor(colorHex)
        }
        appNativeImpl?.setThemeColor(colorHex)
        appNativeImpl?.setThemeConfig(themeConfigState)
        SystemThemeDetector.onThemeConfigChangedHook?.invoke(this, themeConfigState, getResolvedTheme())
        applyActiveStyleSheetsToRootContent()
    }

    override fun getThemeColor(): String? = appNativeImpl?.getThemeColor() ?: themeColorState

    override fun setThemeConfig(config: ThemeConfig) {
        if (!suppressExplicitThemeTracking) {
            hasExplicitThemeConfig = true
            hasExplicitThemeMode = true
            if ((config.colors.primary ?: config.colors.accent) != null) {
                hasExplicitThemeColor = true
            }
        }
        themeConfigState = config
        themeModeState = config.mode
        val primaryColor = config.colors.primary ?: config.colors.accent
        if (primaryColor != null && (!suppressExplicitThemeTracking || !hasExplicitThemeColor || themeColorState.isNullOrBlank())) {
            themeColorState = primaryColor.toHex()
        }
        appNativeImpl?.setThemeConfig(config)
        appNativeImpl?.setThemeMode(config.mode)
        if (themeColorState != null) {
            appNativeImpl?.setThemeColor(themeColorState)
        }
        val resolved = getResolvedTheme()
        SystemThemeDetector.onThemeConfigChangedHook?.invoke(this, config, resolved)
        SystemThemeDetector.onThemeAppliedHook?.invoke(config.mode, resolved)
        notifyThemeChangeListeners(resolved)
    }

    /**
     * Installs build/manifest theme defaults during launch bootstrap while preserving any explicit
     * theme mode, color, or config already chosen by a custom [Application] subclass.
     */
    internal fun applyDescriptorThemeDefaults(
        defaultThemeConfig: ThemeConfig,
        defaultThemeMode: ThemeMode,
        defaultThemeColor: String?
    ) {
        suppressExplicitThemeTracking = true
        try {
            val effectiveMode = if (hasExplicitThemeMode) themeModeState else defaultThemeMode
            val effectiveColor = if (hasExplicitThemeColor) {
                themeColorState
            } else {
                defaultThemeColor?.takeIf { it.isNotBlank() } ?: themeColorState
            }
            if (!hasExplicitThemeConfig) {
                var mergedConfig = defaultThemeConfig.withMode(effectiveMode)
                if (!effectiveColor.isNullOrBlank()) {
                    mergedConfig = mergedConfig.withPrimaryAccentColor(effectiveColor)
                }
                setThemeConfig(mergedConfig)
            } else {
                if (!hasExplicitThemeMode) {
                    setThemeMode(defaultThemeMode)
                }
                if (!hasExplicitThemeColor && !defaultThemeColor.isNullOrBlank()) {
                    setThemeColor(defaultThemeColor)
                }
            }
        } finally {
            suppressExplicitThemeTracking = false
        }
    }

    override fun getThemeConfig(): ThemeConfig = themeConfigState

    override fun setDefaultMainWindowGeometry(width: Int, height: Int, x: Int?, y: Int?) {
        defaultMainWindowSize = Size(width.coerceAtLeast(1), height.coerceAtLeast(1))
        if (x != null && y != null) {
            defaultMainWindowPositionX = x
            defaultMainWindowPositionY = y
        } else {
            defaultMainWindowPositionX = null
            defaultMainWindowPositionY = null
        }
    }

    private fun isWindowStatePersistenceSupported(factory: NativeFactory? = activeFactory ?: GenericUI.activeFactory): Boolean =
        factory?.supportsWindowStatePersistence ?: true

    internal fun applyInitialMainWindowStateIfNeeded(factory: NativeFactory? = activeFactory ?: GenericUI.activeFactory) {
        if (!isWindowStatePersistenceSupported(factory)) {
            return
        }
        isRestoringMainWindowState = true
        mainWindow.withSuppressedExplicitTracking {
            try {
                val saved = if (persistMainWindowState) {
                    MainWindowSavedState.fromPreferenceElement(
                        getPreferenceStore().getElement(MainWindowSavedState.PREF_KEY)
                    )
                } else {
                    null
                }
                val monitorBounds = factory?.getPrimaryMonitorBounds()
                val aspectRatio = mainWindow.getAspectRatio()
                val minSize = mainWindow.getMinWindowSize()

                if (saved != null && saved.hasValidSize) {
                    val clamped = WindowAspectRatioHelper.clampClientSize(
                        size = Size(saved.width, saved.height),
                        aspectRatio = aspectRatio,
                        minSize = minSize
                    )
                    mainWindow.setWindowSize(clamped)
                    mainWindow.updateLastWindowedNormalSize(clamped)
                    val savedBounds = saved.x?.let { sx ->
                        saved.y?.let { sy -> Rect(sx, sy, clamped.width, clamped.height) }
                    }
                    if (savedBounds != null && (factory?.isWindowRectOnScreen(savedBounds) ?: true)) {
                        mainWindow.setWindowPosition(savedBounds.x, savedBounds.y)
                        mainWindow.updateLastWindowedNormalPosition(savedBounds.x, savedBounds.y)
                    } else if (monitorBounds != null) {
                        val centeredX = monitorBounds.x + ((monitorBounds.width - clamped.width) / 2).coerceAtLeast(0)
                        val centeredY = monitorBounds.y + ((monitorBounds.height - clamped.height) / 2).coerceAtLeast(0)
                        mainWindow.setWindowPosition(centeredX, centeredY)
                        mainWindow.updateLastWindowedNormalPosition(centeredX, centeredY)
                    }
                } else {
                    val baseSize = when {
                        mainWindow.hasExplicitSize -> mainWindow.getWindowSize()
                        defaultMainWindowSize != null -> defaultMainWindowSize!!
                        monitorBounds != null -> Size(
                            ((monitorBounds.width * 2) / 3).coerceAtLeast(1),
                            ((monitorBounds.height * 2) / 3).coerceAtLeast(1)
                        )
                        else -> mainWindow.getWindowSize()
                    }
                    val clamped = WindowAspectRatioHelper.clampClientSize(
                        size = baseSize,
                        aspectRatio = aspectRatio,
                        minSize = minSize
                    )
                    mainWindow.setWindowSize(clamped)
                    mainWindow.updateLastWindowedNormalSize(clamped)

                    val defX = defaultMainWindowPositionX
                    val defY = defaultMainWindowPositionY
                    if (mainWindow.hasExplicitPosition) {
                        val currentBounds = mainWindow.windowBounds
                        mainWindow.updateLastWindowedNormalPosition(currentBounds.x, currentBounds.y)
                    } else if (defX != null && defY != null) {
                        val candidate = Rect(defX, defY, clamped.width, clamped.height)
                        if (factory?.isWindowRectOnScreen(candidate) ?: true) {
                            mainWindow.setWindowPosition(defX, defY)
                            mainWindow.updateLastWindowedNormalPosition(defX, defY)
                        } else if (monitorBounds != null) {
                            val centeredX = monitorBounds.x + ((monitorBounds.width - clamped.width) / 2).coerceAtLeast(0)
                            val centeredY = monitorBounds.y + ((monitorBounds.height - clamped.height) / 2).coerceAtLeast(0)
                            mainWindow.setWindowPosition(centeredX, centeredY)
                            mainWindow.updateLastWindowedNormalPosition(centeredX, centeredY)
                        }
                    } else if (monitorBounds != null) {
                        val centeredX = monitorBounds.x + ((monitorBounds.width - clamped.width) / 2).coerceAtLeast(0)
                        val centeredY = monitorBounds.y + ((monitorBounds.height - clamped.height) / 2).coerceAtLeast(0)
                        mainWindow.setWindowPosition(centeredX, centeredY)
                        mainWindow.updateLastWindowedNormalPosition(centeredX, centeredY)
                    }
                }

                if (saved != null) {
                    if (saved.fullscreen) {
                        mainWindow.setStateBeforeFullscreenInternal(saved.windowState)
                        mainWindow.setFullscreen(true)
                    } else {
                        if (mainWindow.isFullscreen()) {
                            mainWindow.setFullscreen(false)
                        }
                        if (saved.windowState != WindowState.NORMAL) {
                            mainWindow.setWindowState(saved.windowState)
                        }
                    }
                } else {
                    val wantFullscreen = if (mainWindow.hasExplicitFullscreen) {
                        mainWindow.isFullscreen()
                    } else {
                        defaultMainWindowFullscreen
                    }
                    if (wantFullscreen) {
                        mainWindow.setFullscreen(true)
                    }
                }
            } finally {
                isRestoringMainWindowState = false
            }
        }
    }

    private fun scheduleOrWriteMainWindowState(immediate: Boolean) {
        if (!persistMainWindowState ||
            isRestoringMainWindowState ||
            !mainWindow.hasShownVisiblyOnce ||
            !isWindowStatePersistenceSupported()
        ) {
            return
        }
        pendingMainWindowStateDirty = true
        if (immediate || (activeFactory == null && GenericUI.activeFactory == null)) {
            flushPendingMainWindowState()
            return
        }
        pendingMainWindowStateTimerId?.let { UiTimerScheduler.clearTimer(it) }
        pendingMainWindowStateTimerId = UiTimerScheduler.setTimeout(MAIN_WINDOW_STATE_DEBOUNCE_MS) {
            pendingMainWindowStateTimerId = null
            if (pendingMainWindowStateDirty) {
                pendingMainWindowStateDirty = false
                writeMainWindowStateNow()
            }
        }
    }

    internal fun flushPendingMainWindowState() {
        pendingMainWindowStateTimerId?.let { UiTimerScheduler.clearTimer(it) }
        pendingMainWindowStateTimerId = null
        if (!pendingMainWindowStateDirty) {
            return
        }
        pendingMainWindowStateDirty = false
        if (!persistMainWindowState ||
            isRestoringMainWindowState ||
            !mainWindow.hasShownVisiblyOnce ||
            !isWindowStatePersistenceSupported()
        ) {
            return
        }
        writeMainWindowStateNow()
    }

    private fun writeMainWindowStateNow() {
        val store = getPreferenceStore()
        if (store.isDisposed) {
            return
        }
        val normalSize = mainWindow.lastWindowedNormalSize
        val currentSize = mainWindow.getWindowSize()
        val width = if (normalSize.width > 0) normalSize.width else currentSize.width.coerceAtLeast(1)
        val height = if (normalSize.height > 0) normalSize.height else currentSize.height.coerceAtLeast(1)
        val fullscreen = mainWindow.isFullscreen()
        val persistedState = if (fullscreen) {
            mainWindow.stateBeforeFullscreen
        } else {
            when (mainWindow.getWindowState()) {
                WindowState.MAXIMIZED -> WindowState.MAXIMIZED
                else -> WindowState.NORMAL
            }
        }
        val hasPos = mainWindow.hasLastWindowedNormalPosition
        val saved = MainWindowSavedState(
            x = if (hasPos) mainWindow.lastWindowedNormalPositionX else null,
            y = if (hasPos) mainWindow.lastWindowedNormalPositionY else null,
            width = width,
            height = height,
            fullscreen = fullscreen,
            windowState = persistedState
        )
        store.putElement(MainWindowSavedState.PREF_KEY, saved.toPreferenceElement())
        store.flush()
    }

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
        isExitingApplication = false
        appShutdownDispatched = false
        isEventLoopRunning = false
        current = this
        activeFactory = factory
        GenericUI.initialize(factory)
        ensureNetworkMonitor(factory)
        val impl = factory.createApplication()
        appNativeImpl = impl
        impl.setOnOfflineChanged(onOfflineChangedCallback)
        impl.setThemeConfig(themeConfigState)
        impl.setThemeMode(themeModeState)
        if (themeColorState != null) {
            impl.setThemeColor(themeColorState)
        }
        installMacApplicationMenu(factory, impl)
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
        SystemThemeDetector.onThemeConfigChangedHook?.invoke(this, themeConfigState, resolved)
        SystemThemeDetector.onThemeAppliedHook?.invoke(themeModeState, resolved)
        notifyThemeChangeListeners(resolved)
        impl.runEventLoop {
            isEventLoopRunning = true
            if (!suppressInitialMainWindowShow &&
                mainWindow.getContent() == null && mainWindow.fragmentContainer?.currentFragment == null &&
                (mainFragmentFactory != null || defaultMainFragmentFactory != null || mainFragmentClass != null)
            ) {
                val created = onCreateMainFragment(launchArguments)
                if (created != null && mainWindow.fragmentContainer?.currentFragment == null && mainWindow.getContent() == null) {
                    setFragment(created)
                }
            }
            applyActiveStyleSheetsToRootContent()
            onApplicationStarted()
            applyActiveStyleSheetsToRootContent()
            if (!suppressInitialMainWindowShow && (!mainWindow.isWindowShown || mainWindow.realizedFactory !== factory)) {
                mainWindow.show(factory)
            }
            GenericUiExtensionRegistry.dispatchAppEventLoopStarted(this, factory)
            finishWebSplash()
            checkExitIfNoFragmentsOpen()
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

    open fun onLaunchArgumentsApplied(args: FragmentBundle?) {}

    private var isExitingApplication: Boolean = false
    private var appShutdownDispatched: Boolean = false

    private fun dispatchAppShutdownOnce() {
        if (!appShutdownDispatched) {
            appShutdownDispatched = true
            GenericUiExtensionRegistry.dispatchAppShutdown(this)
        }
    }

    private fun isFragmentDrivenApplication(): Boolean =
        this is FragmentApplication ||
            mainFragmentClass != null ||
            mainFragmentFactory != null ||
            defaultMainFragmentFactory != null ||
            mainWindow.fragmentContainer != null ||
            suppressInitialMainWindowShow

    /**
     * Returns `true` when at least one UI fragment is currently open across [mainWindow],
     * [FragmentLaunchCoordinator], attached dialogs, or registered extension coordinators.
     */
    open fun hasAnyOpenFragment(): Boolean {
        val mainHasOpenFragment = !suppressInitialMainWindowShow &&
            !mainWindow.isDisposed &&
            mainWindow.isWindowShown &&
            mainWindow.isVisible &&
            mainWindow.fragmentContainer?.currentFragment != null
        if (mainHasOpenFragment) return true
        if (mainWindow.attachedDialogWindows.any {
                !it.isDisposed && it.isWindowShown && it.isVisible && it.fragmentContainer?.currentFragment != null
            }
        ) {
            return true
        }
        if (FragmentLaunchCoordinator.hasOpenFragments()) return true
        return GenericUiExtensionRegistry.hasAnyExtensionOpenFragments(this)
    }

    /**
     * Exits the application with status `0` when [exitWhenNoFragmentsOpen] is enabled on a
     * fragment-driven application whose event loop is active and no open fragments remain.
     */
    fun checkExitIfNoFragmentsOpen() {
        if (!exitWhenNoFragmentsOpen) return
        if (!isFragmentDrivenApplication()) return
        if (isExitingApplication || appShutdownDispatched) return
        if (!isEventLoopRunning) return
        if (!hasAnyOpenFragment()) {
            exit(0)
        }
    }

    override fun exit(exitCode: Int) {
        if (isExitingApplication) return
        isExitingApplication = true
        isEventLoopRunning = false
        try {
            dispatchAppShutdownOnce()
            flushPendingMainWindowState()
            mainWindow.close()
            appNativeImpl?.exitApplication(exitCode)
        } finally {
            isExitingApplication = false
        }
    }

    override fun dispose() {
        isEventLoopRunning = false
        dispatchAppShutdownOnce()
        flushPendingMainWindowState()
        pendingMainWindowStateTimerId?.let { UiTimerScheduler.clearTimer(it) }
        pendingMainWindowStateTimerId = null
        mainWindow.onBeforeFirstVisibleShow = null
        mainWindow.onPersistentWindowStateChanged = null
        mainWindow.onFlushPersistentWindowState = null
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
        macApplicationMenu?.dispose()
        macApplicationMenu = null
        applicationMenuAnchor?.dispose()
        applicationMenuAnchor = null
        mainWindow.dispose()
        appNativeImpl?.dispose()
        appNativeImpl = null
        activeFactory = null
        if (current === this) {
            current = null
        }
    }

    companion object {
        private const val MAIN_WINDOW_STATE_DEBOUNCE_MS = 200
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

        fun findApplicationForWindow(window: Any?): IApplication? {
            if (window == null) return null
            return windowApplications[window]
        }
    }
}
