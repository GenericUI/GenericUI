package com.nexustools.genericui.nativeimpl.shell

import com.nexustools.genericui.core.AbstractContainerNativeImpl
import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.enums.window.DisplayCutoutMode
import com.nexustools.genericui.enums.window.WindowState
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.window.PipConfig
import com.nexustools.genericui.window.TitleBar
import com.nexustools.genericui.window.TitleCapabilities
import com.nexustools.genericui.window.WindowConfiguration

/**
 * Native implementation contract for a top-level [com.nexustools.genericui.component.shell.Window].
 *
 * Platform-specific [TitleBar] & `MenuBar` capability flags:
 * - [supportsCustomTitleBar]: `true` when the host window renders custom [TitleBar] components directly.
 * - [supportsEmbeddedMenuBarInTitleBar]: `true` when the `MenuBar` can be embedded directly into the window titlebar.
 * - [supportsNavbarMenuBar]: `true` when the `MenuBar` is rendered as a top navigation bar that can also host
 *   custom [TitleBar] components alongside the menu trigger.
 */
interface WindowNativeImpl<out W> : AbstractContainerNativeImpl<W> {
    val supportsCustomTitleBar: Boolean
    val supportsEmbeddedMenuBarInTitleBar: Boolean
    val supportsMultiWindow: Boolean
    val supportsNavbarMenuBar: Boolean
        get() = false
    val supportsMobileNavbarMenuBar: Boolean
        get() = supportsNavbarMenuBar

    fun setTitle(title: String)
    fun getTitle(): String

    fun setWindowSize(size: Size)
    fun getWindowSize(): Size

    fun setWindowState(state: WindowState)
    fun getWindowState(): WindowState

    fun setFullscreen(fullscreen: Boolean, cutoutMode: DisplayCutoutMode = DisplayCutoutMode.SHORT_EDGES)
    fun isFullscreen(): Boolean

    fun applyTitleBarInstructions(titleBar: TitleBar?)
    fun setMenuBarNative(menuBarImpl: MenuBarNativeImpl<*>?)
    fun setNavBarNative(navBarImpl: NativeImpl<*>?)
    fun setBottomNavigationBarNative(bottomNavImpl: NativeImpl<*>?)

    fun enterPictureInPicture(config: PipConfig): Boolean
    fun exitPictureInPicture()
    fun isInPictureInPicture(): Boolean

    fun showWindow()
    fun closeWindow()

    fun setOnWindowResize(listener: ((WindowConfiguration) -> Unit)?)
    fun setOnWindowFocusChanged(listener: ((Boolean) -> Unit)?)
    fun setOnWindowCloseRequested(listener: (() -> Boolean)?)

    fun configureAsToolModalWindow(
        ownerWindow: WindowNativeImpl<*>?,
        showInTaskbar: Boolean = false,
        minimizable: Boolean = false,
        alwaysOnTopOfOwner: Boolean = true
    ) {}

    fun bringToFront() {}

    /**
     * Titlebar capabilities of this native window on the active backend/OS.
     * Default: [TitleCapabilities] defaults; backends override with accurate platform values.
     */
    fun getTitleCapabilities(): TitleCapabilities = TitleCapabilities()
}
