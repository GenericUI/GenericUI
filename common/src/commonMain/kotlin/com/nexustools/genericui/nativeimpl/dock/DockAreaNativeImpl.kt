package com.nexustools.genericui.nativeimpl.dock

import com.nexustools.genericui.core.AbstractContainerNativeImpl
import com.nexustools.genericui.enums.dock.DockPosition

interface DockAreaNativeImpl<out W> : AbstractContainerNativeImpl<W> {
    fun setPosition(position: DockPosition)
    fun setCollapsed(collapsed: Boolean)
    fun isCollapsed(): Boolean
}
