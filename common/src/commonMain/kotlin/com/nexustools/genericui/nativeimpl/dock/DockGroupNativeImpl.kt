package com.nexustools.genericui.nativeimpl.dock

import com.nexustools.genericui.core.AbstractContainerNativeImpl
import com.nexustools.genericui.enums.dock.DockGroupMode

interface DockGroupNativeImpl<out W> : AbstractContainerNativeImpl<W> {
    fun setGroupMode(mode: DockGroupMode)
    fun getGroupMode(): DockGroupMode
    fun setActivePanelIndex(index: Int)
    fun getActivePanelIndex(): Int
}
