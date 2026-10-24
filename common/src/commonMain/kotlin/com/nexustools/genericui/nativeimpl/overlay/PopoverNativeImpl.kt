package com.nexustools.genericui.nativeimpl.overlay

import com.nexustools.genericui.core.AbstractContainerNativeImpl
import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.enums.ui.Placement

interface PopoverNativeImpl<out W> : AbstractContainerNativeImpl<W> {
    fun setTitle(title: String?)
    fun setPlacement(placement: Placement)
    fun showAnchored(anchor: NativeImpl<*>?, x: Int, y: Int)
    fun hide()
}
