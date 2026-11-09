package com.nexustools.genericui.nativeimpl.feedback

import com.nexustools.genericui.core.NativeImpl

interface ProgressBarNativeImpl<out W> : NativeImpl<W> {
    fun setProgress(progress: Float)
    fun getProgress(): Float
    fun setIndeterminate(indeterminate: Boolean)
    fun isIndeterminate(): Boolean
    fun setBusy(busy: Boolean) {}
    fun isBusy(): Boolean = false
    fun setLabel(label: String?)
}
