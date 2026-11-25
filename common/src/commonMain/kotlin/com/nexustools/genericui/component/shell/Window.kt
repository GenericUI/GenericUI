package com.nexustools.genericui.component.shell

import com.nexustools.genericui.component.dialog.DialogWindow
import com.nexustools.genericui.component.dock.PanelWindow
import com.nexustools.genericui.component.fragment.FragmentBundle
import com.nexustools.genericui.component.fragment.FragmentContainer
import com.nexustools.genericui.component.fragment.FragmentRegistry
import com.nexustools.genericui.component.fragment.IFragment
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.navigation.BottomNavigationBar
import com.nexustools.genericui.component.navigation.Navbar
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.enums.window.DisplayCutoutMode
import com.nexustools.genericui.enums.window.WindowState
import com.nexustools.genericui.exception.MultipleWindowsUnsupportedException
import com.nexustools.genericui.exception.NativeUninitializedException
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.nativeimpl.shell.WindowNativeImpl
import com.nexustools.genericui.window.IWindow
import com.nexustools.genericui.window.TitleBar
import com.nexustools.genericui.window.TitleCapabilities
import com.nexustools.genericui.window.WindowAspectRatioHelper
import com.nexustools.genericui.window.WindowConfiguration
import kotlin.reflect.KClass

/**
 * Top-level host window (`Window`).
 *
 * **Deferred Upward `NativeImpl` Resolution Root**:
 * - When [show] is called (or when the window is attached to a running [Application]), [isWindowShown] becomes `true`
 *   and [resolveActiveFactory] provides the active [NativeFactory] to all visible descendant components.
 * - While a [Window] is not yet shown, its children remain in lightweight Kotlin state (`nativeImpl == null`).
 *
 * **`TitleBar` Instruction Class & `MenuBar` Handling**:
 * - [setTitleBar] accepts a [TitleBar] instruction object (not a component).
 * - When neither `supportsCustomTitleBar` nor `supportsNavbarMenuBar` is enabled on the active [WindowNativeImpl],
 *   [setTitleBar] is a 0-CPU no-op and components inside [TitleBar] remain unattached (`nativeImpl == null`).
 * - When `titleBar.embedMenuBar == true` and `supportsEmbeddedMenuBarInTitleBar == true`, the `MenuBar` is embedded
 *   into the custom window titlebar; otherwise it is installed in the system/window menu bar slot.
 */
open class Window(
    title: String = "GenericUI",
    initialSize: Size = Size(1280, 800)
) : AbstractContainer("window"), IWindow {
    init {
        htmlTag = "genericui:window"
    }

    override val windowId: String = "window-${nextWindowId()}"
    internal var secondaryOwnerWindow: IWindow? = null
    open override val ownerWindow: IWindow?
        get() = secondaryOwnerWindow

    internal var ExplicitFactory: NativeFactory? = null
    internal var realizedFactory: NativeFactory? = null
    final override var isWindowShown: Boolean = false
        private set
    private var windowVisibleState: Boolean = true
    private var hideRequestedBeforeInitialShow: Boolean = false
    internal var hasShownVisiblyOnce: Boolean = false
        private set
    internal var onBeforeFirstVisibleShow: ((NativeFactory?) -> Unit)? = null
    internal var onPersistentWindowStateChanged: ((immediate: Boolean) -> Unit)? = null
    internal var onFlushPersistentWindowState: (() -> Unit)? = null

    override var isVisible: Boolean
        get() = windowVisibleState
        set(value) {
            if (value) {
                if (!windowVisibleState) {
                    windowVisibleState = true
                    hideRequestedBeforeInitialShow = false
                    if (!hasShownVisiblyOnce) {
                        hasShownVisiblyOnce = true
                        onBeforeFirstVisibleShow?.invoke(ExplicitFactory ?: GenericUI.activeFactory)
                    }
                    if (isWindowShown || ExplicitFactory != null || GenericUI.activeFactory != null) {
                        val impl = ensureNativeImpl() as? WindowNativeImpl<*>
                        if (impl != null) {
                            if (fullscreenState || fullscreenElement != null) {
                                val target = fullscreenElement ?: contentContainer ?: this
                                val elImpl = if (target === this) impl else target.ensureNativeImpl()
                                impl.setAspectRatio(null)
                                syncNavBarToNative(impl)
                                syncBottomNavToNative(impl)
                                impl.setFullscreenElement(elImpl, cutoutModeState)
                            }
                            impl.setVisible(true)
                            impl.showWindow()
                        }
                    }
                }
            } else {
                hide()
            }
        }

    private var titleState: String = title
    private val windowTitleChangedListeners = mutableListOf<(String) -> Unit>()
    private val windowFocusChangedListeners = mutableListOf<(Boolean) -> Unit>()
    private var windowIconState: String? = null
    private var positionXState: Int = 0
    private var positionYState: Int = 0
    private var sizeState: Size = initialSize
    internal var lastWindowedNormalSize: Size = initialSize
        private set
    internal var lastWindowedNormalPositionX: Int = 0
        private set
    internal var lastWindowedNormalPositionY: Int = 0
        private set
    internal var hasLastWindowedNormalPosition: Boolean = false
        private set
    internal var hasExplicitPosition: Boolean = false
        private set
    internal var hasExplicitSize: Boolean = (initialSize != DEFAULT_INITIAL_SIZE)
        private set
    internal var hasExplicitFullscreen: Boolean = false
        private set
    internal var hasExplicitWindowState: Boolean = false
        private set
    internal var suppressExplicitTracking: Boolean = false
        private set
    private var minWindowSizeState: Size? = null
    private var aspectRatioState: Double? = null
    private var isApplyingAspectRatioClamp: Boolean = false
    private var lastIssuedAspectClampSize: Size? = null
    private var windowStateVal: WindowState = WindowState.NORMAL
    internal var stateBeforeFullscreen: WindowState = WindowState.NORMAL
        private set
    private var fullscreenState: Boolean = false
    private var cutoutModeState: DisplayCutoutMode = DisplayCutoutMode.DEFAULT

    var fullscreenElement: AbstractComponent? = null
        private set

    open override val isClosable: Boolean = true
    open override val isMinimizable: Boolean = true
    open override val isMaximizable: Boolean = true
    open override val isResizable: Boolean = true
    open override val isModal: Boolean = false
    open override val isToolWindow: Boolean = false
    private var alwaysOnTopState: Boolean = false
    open override val isAlwaysOnTop: Boolean
        get() = winImpl?.isAlwaysOnTop() ?: alwaysOnTopState
    private var undecoratedState: Boolean = false
    open override val isUndecorated: Boolean
        get() = winImpl?.isUndecorated() ?: undecoratedState

    private var titleBarInstructions: TitleBar? = null
    private var menuBar: MenuBar? = null
    private var navBar: Navbar? = null
    private var bottomNavigationBar: BottomNavigationBar? = null
    private var contentContainer: Container? = null
    private var _fragmentContainer: FragmentContainer? = null

    private var onResizeListener: ((WindowConfiguration) -> Unit)? = null
    private var onWindowStateChangedListener: ((WindowState) -> Unit)? = null
    private var lastObservedWindowState: WindowState = WindowState.NORMAL
    private var onFocusListener: ((Boolean) -> Unit)? = null
    private var onCloseRequestedListener: (() -> Boolean)? = null

    private val _attachedPanelWindows = mutableListOf<PanelWindow>()
    val attachedPanelWindows: List<PanelWindow>
        get() = _attachedPanelWindows

    private val _attachedDialogWindows = mutableListOf<DialogWindow>()
    val attachedDialogWindows: List<DialogWindow>
        get() = _attachedDialogWindows

    override val childWindows: List<IWindow>
        get() = _attachedPanelWindows + _attachedDialogWindows

    open override val titleCapabilities: TitleCapabilities
        get() = winImpl?.getTitleCapabilities()
            ?: (ExplicitFactory ?: GenericUI.activeFactory)?.defaultTitleCapabilities
            ?: TitleCapabilities()

    final override var isWindowFocused: Boolean = false
        private set

    internal override fun canAcceptFocusForIsFocused(): Boolean = true

    var frontOrderSequence: Long = 0L
        internal set

    private val winImpl: WindowNativeImpl<*>?
        get() = typedNative()

    internal fun registerAttachedPanelWindow(panelWindow: PanelWindow) {
        if (panelWindow !== this && panelWindow !in _attachedPanelWindows) {
            _attachedPanelWindows.add(panelWindow)
        }
    }

    internal fun unregisterAttachedPanelWindow(panelWindow: PanelWindow) {
        _attachedPanelWindows.remove(panelWindow)
    }

    internal fun registerAttachedDialogWindow(dialogWindow: DialogWindow) {
        if (dialogWindow !in _attachedDialogWindows) {
            _attachedDialogWindows.add(dialogWindow)
        }
    }

    internal fun unregisterAttachedDialogWindow(dialogWindow: DialogWindow) {
        _attachedDialogWindows.remove(dialogWindow)
    }

    open override fun bringToFront() {
        val impl = winImpl
        if (!isWindowShown || impl == null) {
            throw NativeUninitializedException("Window", "bringToFront")
        }
        frontOrderSequence = nextFrontSequence()
        impl.bringToFront()
        if (_attachedPanelWindows.isNotEmpty()) {
            bringAttachedPanelWindowsToFront()
        }
        if (_attachedDialogWindows.isNotEmpty()) {
            bringAttachedDialogWindowsToFront()
        }
    }

    fun bringAttachedPanelWindowsToFront() {
        for (panelWin in _attachedPanelWindows.toList()) {
            if (panelWin.isVisible && panelWin.isWindowShown) {
                panelWin.bringToFront()
            }
        }
    }

    fun bringAttachedDialogWindowsToFront() {
        for (dialogWin in _attachedDialogWindows.toList()) {
            if (dialogWin.isVisible && dialogWin.isWindowShown) {
                dialogWin.bringToFront()
            }
        }
    }

    open override fun focus() {
        val impl = winImpl
        if (!isWindowShown || impl == null) {
            throw NativeUninitializedException("Window", "focus")
        }
        dispatchWindowFocusChanged(true)
    }

    private var isDispatchingWindowFocus: Boolean = false

    fun dispatchWindowFocusChanged(focused: Boolean) {
        if (isDispatchingWindowFocus) return
        isDispatchingWindowFocus = true
        try {
            isFocused = focused
            isWindowFocused = focused
            if (focused) {
                frontOrderSequence = nextFrontSequence()
                winImpl?.bringToFront()
                bringAttachedPanelWindowsToFront()
                bringAttachedDialogWindowsToFront()
            }
            onFocusListener?.invoke(focused)
            for (listener in windowFocusChangedListeners.toList()) {
                listener(focused)
            }
        } finally {
            isDispatchingWindowFocus = false
        }
    }

    override fun resolveActiveFactory(): NativeFactory? {
        if (!isWindowShown && ExplicitFactory == null) return null
        return ExplicitFactory ?: GenericUI.activeFactory
    }

    private fun resolveCapabilityFactory(operationName: String): NativeFactory =
        ExplicitFactory ?: resolveActiveFactory() ?: GenericUI.activeFactory
            ?: throw NativeUninitializedException("Window", operationName)

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

    private fun isMobileBackendActive(): Boolean =
        (ExplicitFactory ?: GenericUI.activeFactory)?.backendTarget?.isMobile == true

    private fun shouldRestoreWindowedGeometryOnFullscreenExit(): Boolean =
        !isMobileBackendActive() || hasExplicitSize || aspectRatioState != null

    protected override fun createNativeImpl(factory: NativeFactory): WindowNativeImpl<*> =
        factory.createWindow()

    protected override fun resolveInitialNativeVisibility(): Boolean = windowVisibleState

    protected override fun onAttachNative(impl: NativeImpl<*>) {
        super.onAttachNative(impl)
        val wImpl = impl as? WindowNativeImpl<*> ?: return
        wImpl.setTitle(titleState)
        minWindowSizeState?.let { wImpl.setMinWindowSize(it) }
        wImpl.setUndecorated(undecoratedState)
        wImpl.setAlwaysOnTop(alwaysOnTopState)
        val isWindowedNormal = !fullscreenState && fullscreenElement == null && windowStateVal == WindowState.NORMAL
        wImpl.setAspectRatio(if (isWindowedNormal) aspectRatioState else null)
        if (hasExplicitPosition || hasLastWindowedNormalPosition) {
            wImpl.setWindowPosition(positionXState, positionYState)
        }
        wImpl.setWindowSize(sizeState)
        if (windowVisibleState && (fullscreenState || fullscreenElement != null)) {
            val target = fullscreenElement ?: contentContainer ?: this
            val elImpl = if (target === this) wImpl else target.ensureNativeImpl()
            wImpl.setFullscreenElement(elImpl, cutoutModeState)
        } else {
            val nonFullscreenState = if (windowStateVal == WindowState.FULLSCREEN) {
                stateBeforeFullscreen
            } else {
                windowStateVal
            }
            wImpl.setWindowState(nonFullscreenState)
        }
        installNativeWindowResizeForwarder(wImpl)
        wImpl.setOnWindowMoved { x, y ->
            if (!windowVisibleState) return@setOnWindowMoved
            positionXState = x
            positionYState = y
            if (!fullscreenState && fullscreenElement == null && windowStateVal == WindowState.NORMAL) {
                lastWindowedNormalPositionX = x
                lastWindowedNormalPositionY = y
                hasLastWindowedNormalPosition = true
                if (hasShownVisiblyOnce && !suppressExplicitTracking) {
                    onPersistentWindowStateChanged?.invoke(false)
                }
            }
        }
        syncTitleBarAndMenuBarToNative(wImpl)
        syncNavBarToNative(wImpl)
        syncBottomNavToNative(wImpl)
        wImpl.setOnWindowFocusChanged { focused ->
            dispatchWindowFocusChanged(focused)
        }
        wImpl.setOnWindowCloseRequested(onCloseRequestedListener)
        for (panelWin in _attachedPanelWindows.toList()) {
            panelWin.syncToolModalAttachmentToNative()
        }
    }

    private fun dispatchObservedWindowState(state: WindowState) {
        if (lastObservedWindowState != state) {
            lastObservedWindowState = state
            onWindowStateChangedListener?.invoke(state)
        }
    }

    private fun installNativeWindowResizeForwarder(wImpl: WindowNativeImpl<*>) {
        wImpl.setOnWindowStateChanged { state ->
            if (!fullscreenState && state != WindowState.FULLSCREEN) {
                windowStateVal = state
            }
            dispatchObservedWindowState(state)
        }
        wImpl.setOnWindowResize { config ->
            if (!windowVisibleState) return@setOnWindowResize
            val prevFullscreenState = fullscreenState
            val prevWindowState = windowStateVal
            val prevSizeState = sizeState
            var effectiveConfig = config
            var exitedFullscreen = false
            if (config.isFullscreen && !fullscreenState) {
                lastIssuedAspectClampSize = null
                if (windowStateVal == WindowState.NORMAL) {
                    lastWindowedNormalSize = sizeState
                    if (hasExplicitPosition || hasLastWindowedNormalPosition) {
                        lastWindowedNormalPositionX = positionXState
                        lastWindowedNormalPositionY = positionYState
                        hasLastWindowedNormalPosition = true
                    }
                }
                stateBeforeFullscreen = windowStateVal
                fullscreenState = true
                windowStateVal = WindowState.FULLSCREEN
                wImpl.setAspectRatio(null)
                if (fullscreenElement == null) {
                    val target = contentContainer ?: this
                    fullscreenElement = target
                    target.updateFullscreenElementState(true, cutoutModeState)
                    if (target !== this) {
                        updateFullscreenElementState(true, cutoutModeState)
                    }
                }
                syncNavBarToNative(wImpl)
                syncBottomNavToNative(wImpl)
            } else if (!config.isFullscreen && (fullscreenState || fullscreenElement != null)) {
                exitedFullscreen = true
                lastIssuedAspectClampSize = null
                val prev = fullscreenElement
                fullscreenElement = null
                fullscreenState = false
                if (windowStateVal == WindowState.FULLSCREEN) {
                    windowStateVal = if (stateBeforeFullscreen == WindowState.FULLSCREEN) {
                        WindowState.NORMAL
                    } else {
                        stateBeforeFullscreen
                    }
                }
                prev?.updateFullscreenElementState(false, cutoutModeState)
                updateFullscreenElementState(false, cutoutModeState)
                if (wImpl.getFullscreenElement() != null) {
                    wImpl.setFullscreenElement(null, cutoutModeState)
                }
                syncNavBarToNative(wImpl)
                syncBottomNavToNative(wImpl)
                if (windowStateVal == WindowState.NORMAL) {
                    wImpl.setAspectRatio(aspectRatioState)
                    if (hasLastWindowedNormalPosition) {
                        positionXState = lastWindowedNormalPositionX
                        positionYState = lastWindowedNormalPositionY
                        wImpl.setWindowPosition(lastWindowedNormalPositionX, lastWindowedNormalPositionY)
                    }
                }
            } else if (!config.isFullscreen && config.windowState != WindowState.MINIMIZED && config.windowState != WindowState.FULLSCREEN) {
                if (windowStateVal == WindowState.NORMAL && config.windowState == WindowState.MAXIMIZED) {
                    lastWindowedNormalSize = sizeState
                    if (hasExplicitPosition || hasLastWindowedNormalPosition) {
                        lastWindowedNormalPositionX = positionXState
                        lastWindowedNormalPositionY = positionYState
                        hasLastWindowedNormalPosition = true
                    }
                }
                windowStateVal = config.windowState
            }

            val incomingSize = Size(config.widthPx, config.heightPx)
            val currentState = wImpl.getWindowState()
            val isWindowedNormal = !config.isFullscreen && currentState == WindowState.NORMAL
            val ratio = aspectRatioState
            val targetRestoreSize = if (
                shouldRestoreWindowedGeometryOnFullscreenExit() &&
                exitedFullscreen &&
                isWindowedNormal &&
                lastWindowedNormalSize.width > 0 &&
                lastWindowedNormalSize.height > 0
            ) {
                if (ratio != null) {
                    WindowAspectRatioHelper.clampClientSize(
                        size = lastWindowedNormalSize,
                        aspectRatio = ratio,
                        minSize = minWindowSizeState
                    )
                } else {
                    lastWindowedNormalSize
                }
            } else {
                null
            }

            if (
                targetRestoreSize != null &&
                !isApplyingAspectRatioClamp &&
                incomingSize != targetRestoreSize &&
                targetRestoreSize != lastIssuedAspectClampSize
            ) {
                sizeState = targetRestoreSize
                lastWindowedNormalSize = targetRestoreSize
                lastIssuedAspectClampSize = targetRestoreSize
                isApplyingAspectRatioClamp = true
                try {
                    wImpl.setWindowSize(targetRestoreSize)
                } finally {
                    isApplyingAspectRatioClamp = false
                }
                effectiveConfig = config.copy(widthPx = targetRestoreSize.width, heightPx = targetRestoreSize.height)
            } else if (
                isWindowedNormal &&
                ratio != null &&
                !isApplyingAspectRatioClamp &&
                incomingSize.width > 0 &&
                incomingSize.height > 0 &&
                !WindowAspectRatioHelper.matchesAspectRatio(incomingSize, ratio, tolerancePx = 2)
            ) {
                val clamped = WindowAspectRatioHelper.enforceResizeDelta(
                    previousSize = sizeState,
                    requestedSize = incomingSize,
                    aspectRatio = ratio,
                    minSize = minWindowSizeState
                )
                if (clamped != lastIssuedAspectClampSize && clamped != incomingSize) {
                    sizeState = clamped
                    lastWindowedNormalSize = clamped
                    lastIssuedAspectClampSize = clamped
                    isApplyingAspectRatioClamp = true
                    try {
                        wImpl.setWindowSize(clamped)
                    } finally {
                        isApplyingAspectRatioClamp = false
                    }
                    effectiveConfig = config.copy(widthPx = clamped.width, heightPx = clamped.height)
                } else {
                    lastIssuedAspectClampSize = null
                    sizeState = incomingSize
                    lastWindowedNormalSize = incomingSize
                }
            } else {
                lastIssuedAspectClampSize = null
                sizeState = incomingSize
                if (isWindowedNormal) {
                    lastWindowedNormalSize = incomingSize
                }
            }
            if (hasShownVisiblyOnce && !suppressExplicitTracking) {
                val stateChanged = prevFullscreenState != fullscreenState || prevWindowState != windowStateVal
                val sizeChanged = prevSizeState != sizeState
                if (stateChanged) {
                    onPersistentWindowStateChanged?.invoke(true)
                } else if (sizeChanged && isWindowedNormal) {
                    onPersistentWindowStateChanged?.invoke(false)
                }
            }
            fragmentContainer?.onResize(effectiveConfig.widthPx, effectiveConfig.heightPx)
            fragmentContainer?.onConfigurationChanged(effectiveConfig)
            dispatchObservedWindowState(effectiveConfig.windowState)
            onResizeListener?.invoke(effectiveConfig)
        }
    }

    private fun syncTitleBarAndMenuBarToNative(impl: WindowNativeImpl<*>) {
        val tb = titleBarInstructions
        tb?.setHostEmbedsComponents(titleCapabilities.canEmbedComponentsInTitleBar)
        if ((impl.supportsCustomTitleBar || impl.supportsNavbarMenuBar || impl.supportsMobileNavbarMenuBar) && tb != null) {
            for (comp in tb.leadingComponents + tb.centerComponents + tb.trailingComponents) {
                if (comp.parent == null) {
                    comp.parent = this
                }
                if (comp.isVisible) comp.ensureNativeImpl()
            }
            impl.applyTitleBarInstructions(tb)
        } else {
            impl.applyTitleBarInstructions(null)
        }

        val mb = menuBar
        if (mb != null) {
            mb.parent = this
            val mbImpl = mb.ensureNativeImpl() as? com.nexustools.genericui.nativeimpl.shell.MenuBarNativeImpl<*>
            impl.setMenuBarNative(mbImpl)
        } else {
            impl.setMenuBarNative(null)
        }
    }

    private fun syncNavBarToNative(impl: WindowNativeImpl<*>) {
        val nb = navBar
        if (nb != null && !fullscreenState) {
            nb.parent = this
            impl.setNavBarNative(nb.ensureNativeImpl())
        } else {
            impl.setNavBarNative(null)
        }
    }

    private fun syncBottomNavToNative(impl: WindowNativeImpl<*>) {
        val bnb = bottomNavigationBar
        if (bnb != null && !fullscreenState) {
            bnb.parent = this
            impl.setBottomNavigationBarNative(bnb.ensureNativeImpl())
        } else {
            impl.setBottomNavigationBarNative(null)
        }
    }

    override fun setTitle(title: String) {
        titleState = title
        winImpl?.setTitle(title)
        for (listener in windowTitleChangedListeners.toList()) {
            listener(title)
        }
    }

    override fun addOnWindowTitleChanged(listener: (String) -> Unit) {
        if (!windowTitleChangedListeners.contains(listener)) {
            windowTitleChangedListeners.add(listener)
        }
    }

    override fun removeOnWindowTitleChanged(listener: (String) -> Unit) {
        windowTitleChangedListeners.remove(listener)
    }

    override fun getTitle(): String = winImpl?.getTitle() ?: titleState

    override fun getWindowIcon(): String? = windowIconState

    override fun setWindowIcon(iconPathOrSvg: String?) {
        windowIconState = iconPathOrSvg
    }

    override fun setWindowIcon(resId: com.nexustools.genericui.resources.ResourceID) {
        setWindowIcon(resId.toResourceUri())
    }

    open override fun setAlwaysOnTop(alwaysOnTop: Boolean) {
        alwaysOnTopState = alwaysOnTop
        winImpl?.setAlwaysOnTop(alwaysOnTop)
    }

    open override fun setUndecorated(undecorated: Boolean) {
        undecoratedState = undecorated
        winImpl?.setUndecorated(undecorated)
    }

    open override fun startSystemMove() {
        val impl = winImpl
        if (!isWindowShown || impl == null) {
            throw NativeUninitializedException("Window", "startSystemMove")
        }
        impl.startSystemMove()
    }

    override fun getAspectRatio(): Double? = aspectRatioState

    override fun setAspectRatio(aspectRatio: Double?) {
        WindowAspectRatioHelper.validateAspectRatio(aspectRatio)
        aspectRatioState = aspectRatio
        val isWindowedNormal = (getWindowState() == WindowState.NORMAL) && !isFullscreen()
        if (aspectRatio != null) {
            lastWindowedNormalSize = WindowAspectRatioHelper.clampClientSize(
                size = lastWindowedNormalSize,
                aspectRatio = aspectRatio,
                minSize = minWindowSizeState
            )
        }
        if (isWindowedNormal) {
            winImpl?.setAspectRatio(aspectRatio)
            if (aspectRatio != null) {
                val clamped = WindowAspectRatioHelper.clampClientSize(
                    size = sizeState,
                    aspectRatio = aspectRatio,
                    minSize = minWindowSizeState
                )
                lastWindowedNormalSize = clamped
                if (clamped != sizeState) {
                    sizeState = clamped
                    isApplyingAspectRatioClamp = true
                    try {
                        winImpl?.setWindowSize(clamped)
                    } finally {
                        isApplyingAspectRatioClamp = false
                    }
                }
            }
        } else {
            winImpl?.setAspectRatio(null)
        }
    }

    override fun setWindowSize(size: Size) {
        if (!suppressExplicitTracking) {
            hasExplicitSize = true
        }
        val min = minWindowSizeState
        val minClamped = if (min != null) {
            Size(maxOf(size.width, min.width), maxOf(size.height, min.height))
        } else {
            size
        }
        val isWindowedNormal = (getWindowState() == WindowState.NORMAL) && !isFullscreen()
        val finalSize = if (isWindowedNormal && aspectRatioState != null) {
            WindowAspectRatioHelper.clampClientSize(minClamped, aspectRatioState, min)
        } else {
            minClamped
        }
        sizeState = finalSize
        if (isWindowedNormal) {
            lastWindowedNormalSize = finalSize
        }
        isApplyingAspectRatioClamp = true
        try {
            winImpl?.setWindowSize(finalSize)
        } finally {
            isApplyingAspectRatioClamp = false
        }
        if (hasShownVisiblyOnce && windowVisibleState && !suppressExplicitTracking && isWindowedNormal) {
            onPersistentWindowStateChanged?.invoke(false)
        }
    }

    override fun getWindowSize(): Size = winImpl?.getWindowSize() ?: sizeState

    fun setWindowPosition(x: Int, y: Int) {
        if (!suppressExplicitTracking) {
            hasExplicitPosition = true
        }
        positionXState = x
        positionYState = y
        if (getWindowState() == WindowState.NORMAL && !isFullscreen()) {
            lastWindowedNormalPositionX = x
            lastWindowedNormalPositionY = y
            hasLastWindowedNormalPosition = true
        }
        winImpl?.setWindowPosition(x, y)
        if (hasShownVisiblyOnce && windowVisibleState && !suppressExplicitTracking && getWindowState() == WindowState.NORMAL && !isFullscreen()) {
            onPersistentWindowStateChanged?.invoke(false)
        }
    }

    override fun getMinWindowSize(): Size? = winImpl?.getMinWindowSize() ?: minWindowSizeState

    override fun setMinWindowSize(minSize: Size?) {
        minWindowSizeState = minSize
        winImpl?.setMinWindowSize(minSize)
        if (minSize != null) {
            val minClamped = Size(
                maxOf(sizeState.width, minSize.width),
                maxOf(sizeState.height, minSize.height)
            )
            val isWindowedNormal = (getWindowState() == WindowState.NORMAL) && !isFullscreen()
            val clamped = if (isWindowedNormal && aspectRatioState != null) {
                WindowAspectRatioHelper.clampClientSize(minClamped, aspectRatioState, minSize)
            } else {
                minClamped
            }
            if (isWindowedNormal && aspectRatioState != null) {
                lastWindowedNormalSize = WindowAspectRatioHelper.clampClientSize(
                    size = Size(
                        maxOf(lastWindowedNormalSize.width, minSize.width),
                        maxOf(lastWindowedNormalSize.height, minSize.height)
                    ),
                    aspectRatio = aspectRatioState,
                    minSize = minSize
                )
            }
            if (clamped != sizeState) {
                sizeState = clamped
                isApplyingAspectRatioClamp = true
                try {
                    winImpl?.setWindowSize(clamped)
                } finally {
                    isApplyingAspectRatioClamp = false
                }
            }
        }
    }

    override var windowBounds: Rect
        get() {
            val sz = getWindowSize()
            val impl = winImpl
            if (impl != null && windowVisibleState && impl.hasExplicitWindowPosition() &&
                !isFullscreen() && getWindowState() == WindowState.NORMAL
            ) {
                val pt = impl.getWindowPosition()
                positionXState = pt.x.toInt()
                positionYState = pt.y.toInt()
            }
            return Rect(positionXState, positionYState, sz.width, sz.height)
        }
        set(value) {
            setWindowPosition(value.x, value.y)
            setWindowSize(Size(value.width, value.height))
        }

    override fun setWindowState(state: WindowState) {
        if (state == WindowState.FULLSCREEN) {
            setFullscreen(true)
            return
        }
        if (!suppressExplicitTracking) {
            hasExplicitWindowState = true
        }
        val prevState = windowStateVal
        if (prevState == WindowState.NORMAL && !isFullscreen() && state != WindowState.NORMAL) {
            lastWindowedNormalSize = sizeState
            if (hasExplicitPosition || hasLastWindowedNormalPosition) {
                lastWindowedNormalPositionX = positionXState
                lastWindowedNormalPositionY = positionYState
                hasLastWindowedNormalPosition = true
            }
        }
        if (isFullscreen() && state != WindowState.FULLSCREEN) {
            stateBeforeFullscreen = state
            windowStateVal = state
            exitElementFullscreen()
            return
        }
        windowStateVal = state
        dispatchObservedWindowState(state)
        val isWindowedNormal = (state == WindowState.NORMAL) && !isFullscreen()
        if (!isWindowedNormal) {
            winImpl?.setAspectRatio(null)
        }
        winImpl?.setWindowState(state)
        if (isWindowedNormal) {
            winImpl?.setAspectRatio(aspectRatioState)
            val restored = if (aspectRatioState != null) {
                WindowAspectRatioHelper.clampClientSize(lastWindowedNormalSize, aspectRatioState, minWindowSizeState)
            } else {
                lastWindowedNormalSize
            }
            if (restored != sizeState && prevState == WindowState.MAXIMIZED) {
                sizeState = restored
                isApplyingAspectRatioClamp = true
                try {
                    winImpl?.setWindowSize(restored)
                } finally {
                    isApplyingAspectRatioClamp = false
                }
            }
        }
        if (hasShownVisiblyOnce && windowVisibleState && !suppressExplicitTracking) {
            onPersistentWindowStateChanged?.invoke(true)
        }
    }

    override fun getWindowState(): WindowState = winImpl?.getWindowState() ?: windowStateVal

    fun requestElementFullscreen(
        element: AbstractComponent,
        cutoutMode: DisplayCutoutMode = DisplayCutoutMode.SHORT_EDGES
    ) {
        val wasAlreadyFullscreen = fullscreenState && fullscreenElement === element && cutoutModeState == cutoutMode
        if (!suppressExplicitTracking) {
            hasExplicitFullscreen = true
        }
        val prev = fullscreenElement
        if (prev != null && prev !== element) {
            prev.updateFullscreenElementState(false, cutoutMode)
        }
        if (!fullscreenState) {
            stateBeforeFullscreen = windowStateVal
            if (windowStateVal == WindowState.NORMAL) {
                lastWindowedNormalSize = sizeState
                if (hasExplicitPosition || hasLastWindowedNormalPosition) {
                    lastWindowedNormalPositionX = positionXState
                    lastWindowedNormalPositionY = positionYState
                    hasLastWindowedNormalPosition = true
                }
            }
        }
        fullscreenElement = element
        fullscreenState = true
        windowStateVal = WindowState.FULLSCREEN
        cutoutModeState = cutoutMode
        if (windowVisibleState) {
            winImpl?.let { impl ->
                val elImpl = if (element === this) impl else element.ensureNativeImpl()
                impl.setAspectRatio(null)
                syncNavBarToNative(impl)
                syncBottomNavToNative(impl)
                impl.setFullscreenElement(elImpl, cutoutMode)
            }
        }
        dispatchObservedWindowState(windowStateVal)
        element.updateFullscreenElementState(true, cutoutMode)
        if (element !== this) {
            updateFullscreenElementState(true, cutoutMode)
        }
        if (windowVisibleState && !wasAlreadyFullscreen && hasShownVisiblyOnce && !suppressExplicitTracking) {
            onPersistentWindowStateChanged?.invoke(true)
        }
    }

    fun exitElementFullscreen() {
        val wasFullscreen = fullscreenState || fullscreenElement != null || windowStateVal == WindowState.FULLSCREEN || (winImpl?.isFullscreen() == true)
        if (!wasFullscreen) {
            if (!suppressExplicitTracking) {
                hasExplicitFullscreen = true
            }
            return
        }
        if (!suppressExplicitTracking) {
            hasExplicitFullscreen = true
        }
        val prev = fullscreenElement
        fullscreenElement = null
        fullscreenState = false
        if (windowStateVal == WindowState.FULLSCREEN) {
            windowStateVal = if (stateBeforeFullscreen == WindowState.FULLSCREEN) {
                WindowState.NORMAL
            } else {
                stateBeforeFullscreen
            }
        }
        val impl = winImpl
        if (impl != null && windowVisibleState) {
            syncNavBarToNative(impl)
            syncBottomNavToNative(impl)
            impl.setFullscreenElement(null, cutoutModeState)
            if (windowStateVal == WindowState.NORMAL) {
                impl.setAspectRatio(aspectRatioState)
                if (shouldRestoreWindowedGeometryOnFullscreenExit()) {
                    if (hasLastWindowedNormalPosition) {
                        positionXState = lastWindowedNormalPositionX
                        positionYState = lastWindowedNormalPositionY
                        impl.setWindowPosition(lastWindowedNormalPositionX, lastWindowedNormalPositionY)
                    }
                    val restored = if (aspectRatioState != null) {
                        WindowAspectRatioHelper.clampClientSize(lastWindowedNormalSize, aspectRatioState, minWindowSizeState)
                    } else {
                        lastWindowedNormalSize
                    }
                    sizeState = restored
                    isApplyingAspectRatioClamp = true
                    try {
                        impl.setWindowSize(restored)
                    } finally {
                        isApplyingAspectRatioClamp = false
                    }
                }
            } else {
                impl.setWindowState(windowStateVal)
            }
        } else if (shouldRestoreWindowedGeometryOnFullscreenExit() && windowStateVal == WindowState.NORMAL) {
            if (hasLastWindowedNormalPosition) {
                positionXState = lastWindowedNormalPositionX
                positionYState = lastWindowedNormalPositionY
                impl?.setWindowPosition(lastWindowedNormalPositionX, lastWindowedNormalPositionY)
            }
            sizeState = if (aspectRatioState != null) {
                WindowAspectRatioHelper.clampClientSize(lastWindowedNormalSize, aspectRatioState, minWindowSizeState)
            } else {
                lastWindowedNormalSize
            }
        }
        prev?.updateFullscreenElementState(false, cutoutModeState)
        updateFullscreenElementState(false, cutoutModeState)
        dispatchObservedWindowState(windowStateVal)
        if (wasFullscreen && hasShownVisiblyOnce && windowVisibleState && !suppressExplicitTracking) {
            onPersistentWindowStateChanged?.invoke(true)
        }
    }

    override fun setFullscreen(fullscreen: Boolean, cutoutMode: DisplayCutoutMode) {
        if (fullscreen) {
            requestElementFullscreen(fullscreenElement ?: contentContainer ?: this, cutoutMode)
        } else {
            exitElementFullscreen()
        }
    }

    override fun isFullscreen(): Boolean {
        val impl = winImpl
        return if (impl != null && windowVisibleState) {
            impl.isFullscreen()
        } else {
            fullscreenState || fullscreenElement != null
        }
    }

    open override fun setTitleBar(titleBar: TitleBar?) {
        val old = titleBarInstructions
        old?.onInstructionsChanged = null
        if (old !== titleBar) {
            old?.setHostEmbedsComponents(false)
        }
        titleBarInstructions = titleBar
        titleBar?.setHostEmbedsComponents(titleCapabilities.canEmbedComponentsInTitleBar)
        titleBar?.onInstructionsChanged = {
            winImpl?.let { syncTitleBarAndMenuBarToNative(it) }
        }
        winImpl?.let { syncTitleBarAndMenuBarToNative(it) }
    }

    override fun getTitleBar(): TitleBar? = titleBarInstructions

    fun setMenuBar(menuBar: MenuBar?) {
        this.menuBar = menuBar
        winImpl?.let { syncTitleBarAndMenuBarToNative(it) }
    }

    fun getMenuBar(): MenuBar? = menuBar

    fun setNavBar(navBar: Navbar?) {
        this.navBar = navBar
        winImpl?.let { syncNavBarToNative(it) }
    }

    fun getNavBar(): Navbar? = navBar

    fun setBottomNavigationBar(bottomNav: BottomNavigationBar?) {
        this.bottomNavigationBar = bottomNav
        winImpl?.let { syncBottomNavToNative(it) }
    }

    fun getBottomNavigationBar(): BottomNavigationBar? = bottomNavigationBar

    override fun setContent(container: Container) {
        contentContainer?.let { removeChild(it) }
        if (container is com.nexustools.genericui.component.fragment.Fragment) {
            container.htmlTag = "genericui:fragment"
        } else {
            container.htmlTag = "genericui:app"
        }
        contentContainer = container
        if (container is FragmentContainer) {
            container.hostWindow = this
            container.hostingIWindow = this
            _fragmentContainer = container
        }
        winImpl?.let { impl ->
            val currentSize = impl.getWindowSize()
            if (currentSize.width > 0 && currentSize.height > 0) {
                sizeState = currentSize
            }
        }
        addChild(container)
        if (sizeState.width > 0 && sizeState.height > 0) {
            val isFs = fullscreenState || windowStateVal == WindowState.FULLSCREEN
            val cfg = WindowConfiguration(
                widthPx = sizeState.width,
                heightPx = sizeState.height,
                windowState = windowStateVal,
                isFullscreen = isFs
            )
            fragmentContainer?.onResize(sizeState.width, sizeState.height)
            fragmentContainer?.onConfigurationChanged(cfg)
        }
    }

    override fun getContent(): Container? = contentContainer

    override val fragmentContainer: FragmentContainer?
        get() = (contentContainer as? FragmentContainer) ?: _fragmentContainer

    override fun getOrCreateFragmentContainer(): FragmentContainer {
        val existing = fragmentContainer
        if (existing != null) return existing
        val created = FragmentContainer(null, this).also {
            it.hostingIWindow = this
        }
        _fragmentContainer = created
        setContent(created)
        return created
    }

    fun setFragment(fragment: com.nexustools.genericui.component.fragment.Fragment) {
        getOrCreateFragmentContainer().setFragment(fragment)
    }

    fun pushFragment(fragment: com.nexustools.genericui.component.fragment.Fragment) {
        getOrCreateFragmentContainer().pushFragment(fragment)
    }

    fun popFragment(): Boolean =
        fragmentContainer?.popFragment() ?: false

    fun goForward(): Boolean =
        fragmentContainer?.goForward() ?: false

    fun replaceFragment(fragment: com.nexustools.genericui.component.fragment.Fragment) {
        getOrCreateFragmentContainer().replaceFragment(fragment)
    }

    open override fun show(factory: NativeFactory?) {
        if (factory != null) {
            ExplicitFactory = factory
        }
        val resolvedFactory = factory ?: ExplicitFactory ?: GenericUI.activeFactory
            ?: throw NativeUninitializedException("Window", "show")
        val startHiddenOnInitialRealize = !isWindowShown && factory != null && hideRequestedBeforeInitialShow
        hideRequestedBeforeInitialShow = false
        if (!startHiddenOnInitialRealize) {
            windowVisibleState = true
        }
        if (this !is PanelWindow && windowVisibleState && !resolvedFactory.supportsMultipleWindows) {
            val activeApp = Application.findApplicationForWindow(this) ?: Application.current
            val existingShown = shownWindows.firstOrNull { other ->
                other !== this &&
                    !other.isDisposed &&
                    other.isWindowShown &&
                    other.isVisible &&
                    (
                        other.realizedFactory === resolvedFactory ||
                            (activeApp != null && Application.findApplicationForWindow(other) === activeApp)
                        )
            }
            if (existingShown != null) {
                throw MultipleWindowsUnsupportedException(resolvedFactory.backendTarget.displayName)
            }
        }
        val hasAnyContent = children.isNotEmpty() ||
            contentContainer != null ||
            _fragmentContainer != null ||
            titleBarInstructions != null ||
            menuBar != null ||
            navBar != null ||
            bottomNavigationBar != null ||
            isToolWindow ||
            Application.findApplicationForWindow(this) != null
        if (!hasAnyContent) {
            throw IllegalStateException(
                "Cannot show Window '$titleState' with no content: add a root component or Fragment before calling show()."
            )
        }
        if (windowVisibleState && !hasShownVisiblyOnce) {
            hasShownVisiblyOnce = true
            onBeforeFirstVisibleShow?.invoke(resolvedFactory)
        }
        realizedFactory = resolvedFactory
        isWindowShown = true
        registerShownWindow(this)
        val impl = ensureNativeImpl() as? WindowNativeImpl<*>
            ?: throw NativeUninitializedException("Window", "show")
        installNativeWindowResizeForwarder(impl)
        titleBarInstructions?.setHostEmbedsComponents(titleCapabilities.canEmbedComponentsInTitleBar)
        fragmentContainer?.ensureActiveFragmentStartedAndResumed()
        if (windowVisibleState && (fullscreenState || fullscreenElement != null)) {
            val target = fullscreenElement ?: contentContainer ?: this
            val elImpl = if (target === this) impl else target.ensureNativeImpl()
            impl.setAspectRatio(null)
            syncNavBarToNative(impl)
            syncBottomNavToNative(impl)
            impl.setFullscreenElement(elImpl, cutoutModeState)
        }
        if (windowVisibleState) {
            impl.setVisible(true)
            impl.showWindow()
            frontOrderSequence = nextFrontSequence()
            for (panelWin in _attachedPanelWindows.toList()) {
                panelWin.syncToolModalAttachmentToNative()
                if (panelWin.isVisible && panelWin.isWindowShown) {
                    panelWin.bringToFront()
                }
            }
            if (_attachedDialogWindows.isNotEmpty()) {
                bringAttachedDialogWindowsToFront()
            }
        } else {
            impl.setVisible(false)
            impl.showWindow()
        }
    }

    open override fun hide() {
        if (hasShownVisiblyOnce && windowVisibleState) {
            onFlushPersistentWindowState?.invoke()
        }
        if (!isWindowShown) {
            hideRequestedBeforeInitialShow = true
        }
        windowVisibleState = false
        winImpl?.setVisible(false)
    }

    override fun triggerKeyDown(ev: com.nexustools.genericui.event.KeyEvent) {
        super.triggerKeyDown(ev)
        dispatchKeyDownToDescendants(this, ev)
    }

    override fun triggerKeyUp(ev: com.nexustools.genericui.event.KeyEvent) {
        super.triggerKeyUp(ev)
        dispatchKeyUpToDescendants(this, ev)
    }

    private fun dispatchKeyDownToDescendants(parent: AbstractContainer, ev: com.nexustools.genericui.event.KeyEvent) {
        for (child in parent.children.toList()) {
            if (!child.isVisible || !child.isEnabled || child.isDisposed) continue
            child.triggerKeyDown(ev)
            if (child is AbstractContainer) {
                dispatchKeyDownToDescendants(child, ev)
            }
        }
    }

    private fun dispatchKeyUpToDescendants(parent: AbstractContainer, ev: com.nexustools.genericui.event.KeyEvent) {
        for (child in parent.children.toList()) {
            if (!child.isVisible || !child.isEnabled || child.isDisposed) continue
            child.triggerKeyUp(ev)
            if (child is AbstractContainer) {
                dispatchKeyUpToDescendants(child, ev)
            }
        }
    }

    open override fun minimize() {
        if (isMinimizable) {
            setWindowState(WindowState.MINIMIZED)
        }
    }

    open override fun maximize() {
        if (isMaximizable) {
            setWindowState(WindowState.MAXIMIZED)
        }
    }

    open override fun restore() {
        setWindowState(WindowState.NORMAL)
    }

    open override fun close() {
        if (isDisposed) return
        if (hasShownVisiblyOnce && windowVisibleState) {
            onFlushPersistentWindowState?.invoke()
        }
        isWindowShown = false
        realizedFactory = null
        unregisterShownWindow(this)
        titleBarInstructions?.setHostEmbedsComponents(false)
        for (panelWin in _attachedPanelWindows.toList()) {
            panelWin.close()
        }
        _attachedPanelWindows.clear()
        for (dialogWin in _attachedDialogWindows.toList()) {
            dialogWin.close()
        }
        _attachedDialogWindows.clear()
        winImpl?.closeWindow()
        dispose()
        Application.current?.checkExitIfNoFragmentsOpen()
    }

    override fun dispose() {
        isWindowShown = false
        realizedFactory = null
        unregisterShownWindow(this)
        super.dispose()
    }

    override fun setOnWindowResize(listener: ((WindowConfiguration) -> Unit)?) {
        onResizeListener = listener
        winImpl?.let { installNativeWindowResizeForwarder(it) }
    }

    override fun setOnWindowStateChanged(listener: ((WindowState) -> Unit)?) {
        onWindowStateChangedListener = listener
        winImpl?.let { installNativeWindowResizeForwarder(it) }
    }

    override fun setOnWindowFocusChanged(listener: ((Boolean) -> Unit)?) {
        onFocusListener = listener
        winImpl?.setOnWindowFocusChanged { focused ->
            dispatchWindowFocusChanged(focused)
        }
    }

    override fun addOnWindowFocusChanged(listener: (Boolean) -> Unit) {
        if (!windowFocusChangedListeners.contains(listener)) {
            windowFocusChangedListeners.add(listener)
        }
        winImpl?.setOnWindowFocusChanged { focused ->
            dispatchWindowFocusChanged(focused)
        }
    }

    override fun removeOnWindowFocusChanged(listener: (Boolean) -> Unit) {
        windowFocusChangedListeners.remove(listener)
    }

    override fun setOnWindowCloseRequested(listener: (() -> Boolean)?) {
        onCloseRequestedListener = listener
        winImpl?.setOnWindowCloseRequested(listener)
    }

    internal inline fun <T> withSuppressedExplicitTracking(block: () -> T): T {
        val prev = suppressExplicitTracking
        suppressExplicitTracking = true
        return try {
            block()
        } finally {
            suppressExplicitTracking = prev
        }
    }

    internal fun updateLastWindowedNormalSize(size: Size) {
        lastWindowedNormalSize = size
    }

    internal fun updateLastWindowedNormalPosition(x: Int, y: Int) {
        lastWindowedNormalPositionX = x
        lastWindowedNormalPositionY = y
        hasLastWindowedNormalPosition = true
    }

    internal fun setStateBeforeFullscreenInternal(state: WindowState) {
        stateBeforeFullscreen = if (state == WindowState.FULLSCREEN || state == WindowState.MINIMIZED) {
            WindowState.NORMAL
        } else {
            state
        }
    }

    companion object {
        internal val DEFAULT_INITIAL_SIZE = Size(1280, 800)
        private var nextFrontOrderSeq: Long = 0L
        private var nextWinIdSeq: Long = 0L
        private val shownWindows = mutableSetOf<Window>()

        internal fun nextFrontSequence(): Long = ++nextFrontOrderSeq
        internal fun nextWindowId(): Long = ++nextWinIdSeq

        internal fun registerShownWindow(window: Window) {
            if (window !is PanelWindow) {
                shownWindows.add(window)
            }
        }

        internal fun unregisterShownWindow(window: Window) {
            shownWindows.remove(window)
        }

        internal fun clearShownWindowsForTests() {
            shownWindows.clear()
        }
    }
}
