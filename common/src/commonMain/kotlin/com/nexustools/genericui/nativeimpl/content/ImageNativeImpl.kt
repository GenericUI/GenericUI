package com.nexustools.genericui.nativeimpl.content

import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.enums.ui.ObjectFit

interface ImageNativeImpl<out W> : NativeImpl<W> {
    fun setSourceUrl(url: String?)
    fun getSourceUrl(): String?
    fun setFallbackText(fallback: String?)
    fun setObjectFit(fit: ObjectFit)
    fun getObjectFit(): ObjectFit
    fun setOnLoad(listener: (() -> Unit)?) {}
    fun setOnError(listener: (() -> Unit)?) {}
}
