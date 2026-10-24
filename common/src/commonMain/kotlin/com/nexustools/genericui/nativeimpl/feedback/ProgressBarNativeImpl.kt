package com.nexustools.genericui.nativeimpl.feedback

import com.nexustools.genericui.core.NativeImpl

interface ProgressBarNativeImpl<out W> : NativeImpl<W> {
    fun setProgress(progress: Float)
    fun getProgress(): Float
    fun setIndeterminate(indeterminate: Boolean)
    fun isIndeterminate(): Boolean
    fun setLabel(label: String?)
}
