package com.nexustools.genericui.component.shell

import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.event.KeyShortcut
import com.nexustools.genericui.nativeimpl.shell.MenuItemNativeImpl

class CheckMenuItem(
    label: String = "",
    checked: Boolean = false,
    shortcut: KeyShortcut? = null,
    onToggled: ((Boolean) -> Unit)? = null
) : MenuItem(label = label, shortcut = shortcut) {

    init {
        checkedState = checked
        setOnClick {
            setChecked(!isChecked())
            onToggled?.invoke(isChecked())
        }
    }

    override fun createNativeImpl(factory: NativeFactory): MenuItemNativeImpl<*> =
        factory.createMenuItem(checkable = true, radio = false)

    override fun setChecked(checked: Boolean) {
        checkedState = checked
        typedNative<MenuItemNativeImpl<*>>()?.setChecked(checked)
    }

    override fun isChecked(): Boolean = typedNative<MenuItemNativeImpl<*>>()?.isChecked() ?: checkedState
}
