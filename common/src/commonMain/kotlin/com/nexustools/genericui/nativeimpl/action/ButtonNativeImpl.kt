package com.nexustools.genericui.nativeimpl.action

import com.nexustools.genericui.core.NativeImpl

interface ButtonNativeImpl<out W> : NativeImpl<W> {
    fun setText(text: String)
    fun getText(): String
    fun setIcon(icon: String?)
    fun getIcon(): String?
}
