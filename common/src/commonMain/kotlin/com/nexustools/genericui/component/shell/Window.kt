package com.nexustools.genericui.component.shell

import com.nexustools.genericui.component.dialog.DialogWindow
import com.nexustools.genericui.component.dock.PanelWindow
import com.nexustools.genericui.component.fragment.FragmentContainer
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
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.nativeimpl.shell.WindowNativeImpl
import com.nexustools.genericui.window.IWindow
import com.nexustools.genericui.window.PipConfig
import com.nexustools.genericui.window.TitleBar
import com.nexustools.genericui.window.TitleCapabilities
import com.nexustools.genericui.window.WindowConfiguration

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
    open override val ownerWindow: IWindow? = null

    internal var ExplicitFactory: NativeFactory? = null
    final override var isWindowShown: Boolean = false
        private set

    private var titleState: String = title
    private var windowIconState: String? = null
    private var positionXState: Int = 0
    private var positionYState: Int = 0
    private var sizeState: Size = initialSize
    private var minWindowSizeState: Size? = null
    private var windowStateVal: WindowState = WindowState.NORMAL
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
    open override val isAlwaysOnTop: Boolean = false

    private var titleBarInstructions: TitleBar? = null
    private var menuBar: MenuBar? = null
    private var navBar: Navbar? = null
    private var bottomNavigationBar: BottomNavigationBar? = null
    private var contentContainer: Container? = null
    private var _fragmentContainer: FragmentContainer? = null

    private var onResizeListener: ((WindowConfiguration) -> Unit)? = null
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
        frontOrderSequence = nextFrontSequence()
        winImpl?.bringToFront()
        if (_attachedPanelWindows.isNotEmpty()) {
            bringAttachedPanelWindowsToFront()
        }
        if (_attachedDialogWindows.isNotEmpty()) {
            bringAttachedDialogWindowsToFront()
        }
    }

    fun bringAttachedPanelWindowsToFront() {
        for (panelWin in _attachedPanelWindows.toList()) {
            if (panelWin.isVisible) {
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
        dispatchWindowFocusChanged(true)
    }

    fun dispatchWindowFocusChanged(focused: Boolean) {
        isFocused = focused
        isWindowFocused = focused
        if (focused) {
            frontOrderSequence = nextFrontSequence()
            winImpl?.bringToFront()
            bringAttachedPanelWindowsToFront()
            bringAttachedDialogWindowsToFront()
        }
        onFocusListener?.invoke(focused)
    }

    override fun resolveActiveFactory(): NativeFactory? {
        if (!isWindowShown || !isVisible) return null
        return ExplicitFactory ?: GenericUI.activeFactory
    }

    protected override fun createNativeImpl(factory: NativeFactory): WindowNativeImpl<*> =
        factory.createWindow()

    protected override fun onAttachNative(impl: NativeImpl<*>) {
        super.onAttachNative(impl)
        val wImpl = impl as? WindowNativeImpl<*> ?: return
        wImpl.setTitle(titleState)
        minWindowSizeState?.let { wImpl.setMinWindowSize(it) }
        installNativeWindowResizeForwarder(wImpl)
        wImpl.setWindowSize(sizeState)
        wImpl.setWindowState(windowStateVal)
        if (fullscreenState || fullscreenElement != null) {
            val target = fullscreenElement ?: contentContainer ?: this
            val elImpl = if (target === this) wImpl else target.ensureNativeImpl()
            wImpl.setFullscreenElement(elImpl, cutoutModeState)
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

    private fun installNativeWindowResizeForwarder(wImpl: WindowNativeImpl<*>) {
        wImpl.setOnWindowResize { config ->
            sizeState = Size(config.widthPx, config.heightPx)
            if (!config.isFullscreen && (fullscreenState || fullscreenElement != null)) {
                val prev = fullscreenElement
                fullscreenElement = null
                fullscreenState = false
                prev?.updateFullscreenElementState(false, cutoutModeState)
                updateFullscreenElementState(false, cutoutModeState)
                syncNavBarToNative(wImpl)
                syncBottomNavToNative(wImpl)
            }
            fragmentContainer?.onResize(config.widthPx, config.heightPx)
            fragmentContainer?.onConfigurationChanged(config)
            onResizeListener?.invoke(config)
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
    }

    override fun getTitle(): String = winImpl?.getTitle() ?: titleState

    override fun getWindowIcon(): String? = windowIconState

    override fun setWindowIcon(iconPathOrSvg: String?) {
        windowIconState = iconPathOrSvg
    }

    override fun setWindowIcon(resId: com.nexustools.genericui.resources.ResourceID) {
        setWindowIcon(resId.toResourceUri())
    }

    override fun setWindowSize(size: Size) {
        val min = minWindowSizeState
        val clamped = if (min != null) {
            Size(maxOf(size.width, min.width), maxOf(size.height, min.height))
        } else {
            size
        }
        sizeState = clamped
        winImpl?.setWindowSize(clamped)
    }

    override fun getWindowSize(): Size = winImpl?.getWindowSize() ?: sizeState

    override fun getMinWindowSize(): Size? = winImpl?.getMinWindowSize() ?: minWindowSizeState

    override fun setMinWindowSize(minSize: Size?) {
        minWindowSizeState = minSize
        winImpl?.setMinWindowSize(minSize)
        if (minSize != null) {
            val clamped = Size(
                maxOf(sizeState.width, minSize.width),
                maxOf(sizeState.height, minSize.height)
            )
            if (clamped != sizeState) {
                sizeState = clamped
                winImpl?.setWindowSize(clamped)
            }
        }
    }

    override var windowBounds: Rect
        get() {
            val sz = getWindowSize()
            return Rect(positionXState, positionYState, sz.width, sz.height)
        }
        set(value) {
            positionXState = value.x
            positionYState = value.y
            setWindowSize(Size(value.width, value.height))
        }

    override fun setWindowState(state: WindowState) {
        windowStateVal = state
        winImpl?.setWindowState(state)
    }

    override fun getWindowState(): WindowState = winImpl?.getWindowState() ?: windowStateVal

    fun requestElementFullscreen(
        element: AbstractComponent,
        cutoutMode: DisplayCutoutMode = DisplayCutoutMode.SHORT_EDGES
    ) {
        val prev = fullscreenElement
        if (prev != null && prev !== element) {
            prev.updateFullscreenElementState(false, cutoutMode)
        }
        fullscreenElement = element
        fullscreenState = true
        cutoutModeState = cutoutMode
        element.updateFullscreenElementState(true, cutoutMode)
        if (element !== this) {
            updateFullscreenElementState(true, cutoutMode)
        }
        winImpl?.let { impl ->
            val elImpl = if (element === this) impl else element.ensureNativeImpl()
            syncNavBarToNative(impl)
            syncBottomNavToNative(impl)
            impl.setFullscreenElement(elImpl, cutoutMode)
        }
    }

    fun exitElementFullscreen() {
        val prev = fullscreenElement
        fullscreenElement = null
        fullscreenState = false
        prev?.updateFullscreenElementState(false, cutoutModeState)
        updateFullscreenElementState(false, cutoutModeState)
        winImpl?.let { impl ->
            syncNavBarToNative(impl)
            syncBottomNavToNative(impl)
            impl.setFullscreenElement(null, cutoutModeState)
        }
    }

    override fun setFullscreen(fullscreen: Boolean, cutoutMode: DisplayCutoutMode) {
        if (fullscreen) {
            requestElementFullscreen(fullscreenElement ?: contentContainer ?: this, cutoutMode)
        } else {
            exitElementFullscreen()
        }
    }

    override fun isFullscreen(): Boolean = winImpl?.isFullscreen() ?: (fullscreenState || fullscreenElement != null)

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
            val cfg = WindowConfiguration(widthPx = sizeState.width, heightPx = sizeState.height)
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

    fun enterPictureInPicture(config: PipConfig = PipConfig()): Boolean =
        winImpl?.enterPictureInPicture(config) ?: false

    fun exitPictureInPicture() {
        winImpl?.exitPictureInPicture()
    }

    fun isInPictureInPicture(): Boolean =
        winImpl?.isInPictureInPicture() ?: false

    open override fun show(factory: NativeFactory?) {
        if (factory != null) {
            ExplicitFactory = factory
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
        isWindowShown = true
        val impl = ensureNativeImpl() as? WindowNativeImpl<*>
            ?: throw IllegalStateException(
                "Cannot show Window '$titleState': no NativeImpl / NativeFactory is configured on the active backend."
            )
        installNativeWindowResizeForwarder(impl)
        titleBarInstructions?.setHostEmbedsComponents(titleCapabilities.canEmbedComponentsInTitleBar)
        impl.showWindow()
        frontOrderSequence = nextFrontSequence()
        for (panelWin in _attachedPanelWindows.toList()) {
            panelWin.syncToolModalAttachmentToNative()
            if (panelWin.isVisible) {
                panelWin.bringToFront()
            }
        }
        if (_attachedDialogWindows.isNotEmpty()) {
            bringAttachedDialogWindowsToFront()
        }
    }

    open override fun hide() {
        isVisible = false
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
        isWindowShown = false
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
    }

    override fun setOnWindowResize(listener: ((WindowConfiguration) -> Unit)?) {
        onResizeListener = listener
        winImpl?.let { installNativeWindowResizeForwarder(it) }
    }

    override fun setOnWindowFocusChanged(listener: ((Boolean) -> Unit)?) {
        onFocusListener = listener
        winImpl?.setOnWindowFocusChanged { focused ->
            dispatchWindowFocusChanged(focused)
        }
    }

    override fun setOnWindowCloseRequested(listener: (() -> Boolean)?) {
        onCloseRequestedListener = listener
        winImpl?.setOnWindowCloseRequested(listener)
    }

    companion object {
        private var nextFrontOrderSeq: Long = 0L
        private var nextWinIdSeq: Long = 0L

        internal fun nextFrontSequence(): Long = ++nextFrontOrderSeq
        internal fun nextWindowId(): Long = ++nextWinIdSeq
    }
}
