package com.nexustools.genericui.nativeimpl.content

import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.enums.ui.ObjectFit
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.style.color.ImageTintMode
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.CssFilter

interface ImageNativeImpl<out W> : NativeImpl<W> {
    fun setSourceUrl(url: String?)
    fun getSourceUrl(): String?
    fun setImageBuffer(buffer: ImageBuffer?) {}
    fun setPlaceholderUrl(url: String?) {}
    fun getPlaceholderUrl(): String? = null
    fun setFallbackText(fallback: String?)
    fun setObjectFit(fit: ObjectFit)
    fun getObjectFit(): ObjectFit
    fun setAntialiasing(enabled: Boolean) {}
    fun isAntialiasing(): Boolean = true
    fun isLoading(): Boolean = false
    fun setTintColor(color: RgbaColor?) {}
    fun getTintColor(): RgbaColor? = null
    fun setTintMode(mode: ImageTintMode) {}
    fun getTintMode(): ImageTintMode = ImageTintMode.NONE
    fun setCssFilter(filter: CssFilter?) {}
    fun getCssFilter(): CssFilter? = null
    fun setOnLoad(listener: (() -> Unit)?) {}
    fun setOnError(listener: (() -> Unit)?) {}
}
