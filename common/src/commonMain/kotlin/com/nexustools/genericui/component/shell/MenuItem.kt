package com.nexustools.genericui.component.shell

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.event.KeyShortcut
import com.nexustools.genericui.nativeimpl.shell.MenuItemNativeImpl

open class MenuItem(
    label: String = "",
    icon: String? = null,
    shortcut: KeyShortcut? = null,
    onClick: (() -> Unit)? = null
) : AbstractComponent("dropdown-item") {

    private var labelState: String = label
    private var iconState: String? = icon
    private var shortcutState: KeyShortcut? = shortcut
    protected var checkedState: Boolean = false

    init {
        if (onClick != null) {
            setOnClick(onClick)
        }
    }

    override fun createNativeImpl(factory: NativeFactory): MenuItemNativeImpl<*> =
        factory.createMenuItem(checkable = false, radio = false)

    override fun onAttachNative(impl: NativeImpl<*>) {
        super.onAttachNative(impl)
        val menuImpl = impl as? MenuItemNativeImpl<*> ?: return
        menuImpl.setLabel(labelState)
        menuImpl.setIcon(iconState)
        menuImpl.setShortcut(shortcutState)
        menuImpl.setChecked(checkedState)
    }

    fun setLabel(label: String) {
        labelState = label
        typedNative<MenuItemNativeImpl<*>>()?.setLabel(label)
    }

    fun getLabel(): String = typedNative<MenuItemNativeImpl<*>>()?.getLabel() ?: labelState

    fun setIcon(icon: String?) {
        iconState = icon
        typedNative<MenuItemNativeImpl<*>>()?.setIcon(icon)
    }

    fun setShortcut(shortcut: KeyShortcut?) {
        shortcutState = shortcut
        typedNative<MenuItemNativeImpl<*>>()?.setShortcut(shortcut)
    }
}
