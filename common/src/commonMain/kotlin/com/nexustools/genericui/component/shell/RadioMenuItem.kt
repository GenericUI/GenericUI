package com.nexustools.genericui.component.shell

import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.nativeimpl.shell.MenuItemNativeImpl

class RadioMenuItem(
    label: String = "",
    checked: Boolean = false,
    onSelected: (() -> Unit)? = null
) : MenuItem(label = label, onClick = onSelected) {

    init {
        htmlTag = "genericui:radiomenuitem"
        checkedState = checked
    }

    override fun createNativeImpl(factory: NativeFactory): MenuItemNativeImpl<*> =
        factory.createMenuItem(checkable = true, radio = true)

    override fun setChecked(checked: Boolean) {
        checkedState = checked
        typedNative<MenuItemNativeImpl<*>>()?.setChecked(checked)
    }

    override fun isChecked(): Boolean = typedNative<MenuItemNativeImpl<*>>()?.isChecked() ?: checkedState
}
