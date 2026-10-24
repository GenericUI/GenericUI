package com.nexustools.genericui.nativeimpl.navigation

import com.nexustools.genericui.core.NativeImpl

interface BottomNavigationBarNativeImpl<out W> : NativeImpl<W> {
    fun setItems(items: List<Triple<String, String, String?>>)
    fun setSelectedItemId(itemId: String)
    fun getSelectedItemId(): String?
    fun setOnItemSelected(listener: ((String) -> Unit)?)
}
