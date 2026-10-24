package com.nexustools.genericui.nativeimpl.typography

import com.nexustools.genericui.core.NativeImpl

interface TextNativeImpl<out W> : NativeImpl<W> {
    fun setText(text: String)
    fun getText(): String
    fun setMaxLines(maxLines: Int?)
    fun getMaxLines(): Int?
}
