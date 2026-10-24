package com.nexustools.genericui.nativeimpl.shell

import com.nexustools.genericui.core.AbstractContainerNativeImpl
import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.enums.window.DisplayCutoutMode
import com.nexustools.genericui.enums.window.WindowState
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.window.PipConfig
import com.nexustools.genericui.window.TitleBar
import com.nexustools.genericui.window.WindowConfiguration

/**
 * Native implementation contract for a top-level [com.nexustools.genericui.component.shell.Window].
 *
 * Platform-specific [TitleBar] & `MenuBar` behavior:
 * - [supportsCustomTitleBar]: `true` on `:windows`, `:macos`, `:gtk`, `:qt`, `:swing`; `false` on `:web`, `:android`, `:ios`.
 * - [supportsEmbeddedMenuBarInTitleBar]: `true` on `:windows`, `:gtk`, `:qt`, `:swing`; **`false` on `:macos`**
 *   (because macOS always manages the menu bar via `NSApplication.mainMenu`) and `false` on `:web`, `:android`, `:ios`.
 */
interface WindowNativeImpl<out W> : AbstractContainerNativeImpl<W> {
    val supportsCustomTitleBar: Boolean
    val supportsEmbeddedMenuBarInTitleBar: Boolean
    val supportsMultiWindow: Boolean

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
}
