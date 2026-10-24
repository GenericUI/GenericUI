package com.nexustools.genericui.nativeimpl.action

import com.nexustools.genericui.core.AbstractContainerNativeImpl

interface DropdownNativeImpl<out W> : AbstractContainerNativeImpl<W> {
    fun setLabel(label: String)
    fun setItems(items: List<Pair<String, String>>)
    fun setOnItemSelected(listener: ((String) -> Unit)?)
}
