package com.nexustools.genericui.nativeimpl.form

import com.nexustools.genericui.core.NativeImpl

interface CheckboxNativeImpl<out W> : NativeImpl<W> {
    fun setLabel(label: String)
    fun getLabel(): String
    fun setChecked(checked: Boolean)
    fun isChecked(): Boolean
    fun setOnCheckedChanged(listener: ((Boolean) -> Unit)?)
}
