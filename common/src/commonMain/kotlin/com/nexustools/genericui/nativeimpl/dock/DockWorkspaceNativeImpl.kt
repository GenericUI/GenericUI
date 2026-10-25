package com.nexustools.genericui.nativeimpl.dock

import com.nexustools.genericui.core.AbstractContainerNativeImpl
import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.enums.dock.DockPosition

interface DockWorkspaceNativeImpl<out W> : AbstractContainerNativeImpl<W> {
    fun setCentralDocumentNative(centralImpl: NativeImpl<*>?)
    fun attachDockAreaNative(position: DockPosition, areaImpl: DockAreaNativeImpl<*>)
    fun setWindowModeNative(modeName: String) {}
}
