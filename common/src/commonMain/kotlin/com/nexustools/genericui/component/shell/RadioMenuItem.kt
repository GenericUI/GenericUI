package com.nexustools.genericui.component.shell

import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.nativeimpl.shell.MenuItemNativeImpl

class RadioMenuItem(
    label: String = "",
    checked: Boolean = false,
    onSelected: (() -> Unit)? = null
) : MenuItem(label = label, onClick = onSelected) {

    init {
        checkedState = checked
    }

    override fun createNativeImpl(factory: NativeFactory): MenuItemNativeImpl<*> =
        factory.createMenuItem(checkable = true, radio = true)

    fun setChecked(checked: Boolean) {
        checkedState = checked
        typedNative<MenuItemNativeImpl<*>>()?.setChecked(checked)
    }

    fun isChecked(): Boolean = typedNative<MenuItemNativeImpl<*>>()?.isChecked() ?: checkedState
}
