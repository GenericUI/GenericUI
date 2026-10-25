package com.nexustools.genericui.nativeimpl.shell

import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.event.KeyShortcut

interface MenuItemNativeImpl<out W> : NativeImpl<W> {
    fun setLabel(label: String)
    fun getLabel(): String
    fun setIcon(icon: String?)
    fun getIcon(): String? = null
    fun setShortcut(shortcut: KeyShortcut?)
    fun getShortcut(): KeyShortcut? = null
    fun setChecked(checked: Boolean)
    fun isChecked(): Boolean
}
