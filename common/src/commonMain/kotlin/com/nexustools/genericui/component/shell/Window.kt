package com.nexustools.genericui.component.shell

import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.navigation.BottomNavigationBar
import com.nexustools.genericui.component.navigation.Navbar
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.enums.window.DisplayCutoutMode
import com.nexustools.genericui.enums.window.WindowState
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.nativeimpl.shell.WindowNativeImpl
import com.nexustools.genericui.window.PipConfig
import com.nexustools.genericui.window.TitleBar
import com.nexustools.genericui.window.WindowConfiguration

/**
 * Top-level OS or browser window (`Window`).
 *
 * **Deferred Upward `NativeImpl` Resolution Root**:
 * - When [show] is called (or when the window is attached to a running [Application]), [isWindowShown] becomes `true`
 *   and [resolveActiveFactory] provides the active [NativeFactory] to all visible descendant components.
 * - While a [Window] is not yet shown, its children remain in lightweight Kotlin state (`nativeImpl == null`).
 *
 * **`TitleBar` Instruction Class & Platform-Aware `MenuBar` Handling**:
 * - [setTitleBar] accepts a [TitleBar] instruction object (not a component).
 * - On platforms where `supportsCustomTitleBar == false` (`:web`, `:android`, `:ios`), [setTitleBar] is a 0-CPU no-op,
 *   and components inside [TitleBar] remain unattached (`nativeImpl == null`).
 * - When `titleBar.embedMenuBar == true`:
 *   - On `:windows`, `:gtk`, `:qt`, `:swing` (`supportsEmbeddedMenuBarInTitleBar == true`), the `MenuBar` is embedded
 *     into the custom window titlebar.
 *   - On **`:macos`** (`supportsEmbeddedMenuBarInTitleBar == false`), the `MenuBar` is **never** embedded into the
 *     window titlebar and is always installed in the macOS system menu bar (`NSApplication.mainMenu`).
 */
open class Window(
    title: String = "GenericUI",
    initialSize: Size = Size(1280, 800)
) : AbstractContainer("window") {

    internal var ExplicitFactory: NativeFactory? = null
    var isWindowShown: Boolean = false
        private set

    private var titleState: String = title
    private var sizeState: Size = initialSize
    private var windowStateVal: WindowState = WindowState.NORMAL
    private var fullscreenState: Boolean = false
    private var cutoutModeState: DisplayCutoutMode = DisplayCutoutMode.DEFAULT

    private var titleBarInstructions: TitleBar? = null
    private var menuBar: MenuBar? = null
    private var navBar: Navbar? = null
    private var bottomNavigationBar: BottomNavigationBar? = null
    private var contentContainer: Container? = null

    private var onResizeListener: ((WindowConfiguration) -> Unit)? = null
    private var onFocusListener: ((Boolean) -> Unit)? = null
    private var onCloseRequestedListener: (() -> Boolean)? = null

    private val winImpl: WindowNativeImpl<*>?
        get() = typedNative()

    override fun resolveActiveFactory(): NativeFactory? {
        if (!isWindowShown || !isVisible) return null
        return ExplicitFactory ?: GenericUI.activeFactory
    }

    override fun createNativeImpl(factory: NativeFactory): WindowNativeImpl<*> =
        factory.createWindow()

    override fun onAttachNative(impl: NativeImpl<*>) {
        super.onAttachNative(impl)
        val wImpl = impl as? WindowNativeImpl<*> ?: return
        wImpl.setTitle(titleState)
        wImpl.setWindowSize(sizeState)
        wImpl.setWindowState(windowStateVal)
        if (fullscreenState) {
            wImpl.setFullscreen(true, cutoutModeState)
        }
        syncTitleBarAndMenuBarToNative(wImpl)
        syncNavBarToNative(wImpl)
        syncBottomNavToNative(wImpl)
        wImpl.setOnWindowResize(onResizeListener)
        wImpl.setOnWindowFocusChanged(onFocusListener)
        wImpl.setOnWindowCloseRequested(onCloseRequestedListener)
    }

    private fun syncTitleBarAndMenuBarToNative(impl: WindowNativeImpl<*>) {
        val tb = titleBarInstructions
        if (impl.supportsCustomTitleBar && tb != null) {
            for (comp in tb.leadingComponents + tb.centerComponents + tb.trailingComponents) {
                comp.parent = this
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

    fun setTitle(title: String) {
        titleState = title
        winImpl?.setTitle(title)
    }

    fun getTitle(): String = winImpl?.getTitle() ?: titleState

    fun setWindowSize(size: Size) {
        sizeState = size
        winImpl?.setWindowSize(size)
    }

    fun getWindowSize(): Size = winImpl?.getWindowSize() ?: sizeState

    fun setWindowState(state: WindowState) {
        windowStateVal = state
        winImpl?.setWindowState(state)
    }

    fun getWindowState(): WindowState = winImpl?.getWindowState() ?: windowStateVal

    fun setFullscreen(fullscreen: Boolean, cutoutMode: DisplayCutoutMode = DisplayCutoutMode.SHORT_EDGES) {
        fullscreenState = fullscreen
        cutoutModeState = cutoutMode
        winImpl?.let {
            it.setFullscreen(fullscreen, cutoutMode)
            syncNavBarToNative(it)
            syncBottomNavToNative(it)
        }
    }

    fun isFullscreen(): Boolean = winImpl?.isFullscreen() ?: fullscreenState

    fun setTitleBar(titleBar: TitleBar?) {
        titleBarInstructions?.onInstructionsChanged = null
        titleBarInstructions = titleBar
        titleBar?.onInstructionsChanged = {
            winImpl?.let { syncTitleBarAndMenuBarToNative(it) }
        }
        winImpl?.let { syncTitleBarAndMenuBarToNative(it) }
    }

    fun getTitleBar(): TitleBar? = titleBarInstructions

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

    fun setContent(container: Container) {
        contentContainer?.let { removeChild(it) }
        contentContainer = container
        addChild(container)
    }

    fun getContent(): Container? = contentContainer

    fun enterPictureInPicture(config: PipConfig = PipConfig()): Boolean =
        winImpl?.enterPictureInPicture(config) ?: false

    fun exitPictureInPicture() {
        winImpl?.exitPictureInPicture()
    }

    fun isInPictureInPicture(): Boolean =
        winImpl?.isInPictureInPicture() ?: false

    fun show(factory: NativeFactory? = null) {
        if (factory != null) {
            ExplicitFactory = factory
        }
        isWindowShown = true
        val impl = ensureNativeImpl() as? WindowNativeImpl<*>
        impl?.showWindow()
    }

    fun close() {
        isWindowShown = false
        winImpl?.closeWindow()
        dispose()
    }

    fun setOnWindowResize(listener: ((WindowConfiguration) -> Unit)?) {
        onResizeListener = listener
        winImpl?.setOnWindowResize(listener)
    }

    fun setOnWindowFocusChanged(listener: ((Boolean) -> Unit)?) {
        onFocusListener = listener
        winImpl?.setOnWindowFocusChanged(listener)
    }

    fun setOnWindowCloseRequested(listener: (() -> Boolean)?) {
        onCloseRequestedListener = listener
        winImpl?.setOnWindowCloseRequested(listener)
    }
}
