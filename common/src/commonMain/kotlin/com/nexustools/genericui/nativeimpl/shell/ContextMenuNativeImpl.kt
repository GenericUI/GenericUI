package com.nexustools.genericui.nativeimpl.shell

import com.nexustools.genericui.core.AbstractContainerNativeImpl
import com.nexustools.genericui.core.NativeImpl

interface ContextMenuNativeImpl<out W> : AbstractContainerNativeImpl<W> {
    fun showAt(anchor: NativeImpl<*>?, x: Int, y: Int)
    fun hide()
}
