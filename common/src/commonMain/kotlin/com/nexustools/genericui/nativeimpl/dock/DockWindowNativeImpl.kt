package com.nexustools.genericui.nativeimpl.dock

import com.nexustools.genericui.core.AbstractContainerNativeImpl
import com.nexustools.genericui.geometry.Rect

/**
 * Native utility palette window (`QDockWidget` floating / `Qt::Tool` on `:qt`, utility `GtkWindow` on `:gtk`,
 * `WS_EX_TOOLWINDOW` `HWND` on `:windows`, `NSPanel` utility window on `:macos`, `JDialog(Window.Type.UTILITY)` on `:swing`)
 * hosting detached docks in Single-Window Mode or Multi-Window Mode.
 */
interface DockWindowNativeImpl<out W> : AbstractContainerNativeImpl<W> {
    fun setTitle(title: String)
    fun setWindowBounds(bounds: Rect)
    fun showDockWindow()
    fun hideDockWindow()
    fun setOnDockWindowCloseRequested(listener: (() -> Unit)?)
}
