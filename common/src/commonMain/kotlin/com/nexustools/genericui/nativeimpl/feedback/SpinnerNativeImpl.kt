package com.nexustools.genericui.nativeimpl.feedback

import com.nexustools.genericui.core.NativeImpl

interface SpinnerNativeImpl<out W> : NativeImpl<W> {
    fun setGrowStyle(grow: Boolean)
    fun setLabel(label: String?)
}
