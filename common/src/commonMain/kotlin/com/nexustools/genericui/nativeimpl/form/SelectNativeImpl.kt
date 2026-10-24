package com.nexustools.genericui.nativeimpl.form

import com.nexustools.genericui.core.NativeImpl

interface SelectNativeImpl<out W> : NativeImpl<W> {
    fun setOptions(options: List<Pair<String, String>>)
    fun setSelectedValue(value: String?)
    fun getSelectedValue(): String?
    fun setOnSelectionChanged(listener: ((String) -> Unit)?)
}
