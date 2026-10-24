package com.nexustools.genericui.nativeimpl.form

import com.nexustools.genericui.core.NativeImpl

interface TextInputNativeImpl<out W> : NativeImpl<W> {
    fun setValue(value: String)
    fun getValue(): String
    fun setPlaceholder(placeholder: String)
    fun getPlaceholder(): String
    fun setReadOnly(readOnly: Boolean)
    fun setPasswordMode(password: Boolean)
    fun setMultiline(multiline: Boolean)
    fun setOnValueChanged(listener: ((String) -> Unit)?)
    fun setOnSubmit(listener: ((String) -> Unit)?)
}
