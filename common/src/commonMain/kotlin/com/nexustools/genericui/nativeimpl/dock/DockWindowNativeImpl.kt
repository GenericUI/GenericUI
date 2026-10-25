package com.nexustools.genericui.nativeimpl.dock

import com.nexustools.genericui.core.AbstractContainerNativeImpl
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.nativeimpl.shell.WindowNativeImpl

/**
 * Native utility palette window contract for tool-modal detached dock panels
 * that stay on top of their owning main window without a taskbar entry or minimize button.
 */
interface DockWindowNativeImpl<out W> : AbstractContainerNativeImpl<W> {
    val isToolModalWindow: Boolean
        get() = true
    val hasTaskbarEntry: Boolean
        get() = false
    val isMinimizable: Boolean
        get() = false
    val isAlwaysOnTopOfOwner: Boolean
        get() = true

    fun setTitle(title: String)
    fun setWindowBounds(bounds: Rect)
    fun showDockWindow()
    fun hideDockWindow()
    fun setOnDockWindowCloseRequested(listener: (() -> Unit)?)
    fun attachToOwnerWindow(ownerWindow: WindowNativeImpl<*>?) {}
    fun bringToFront() {}
}
