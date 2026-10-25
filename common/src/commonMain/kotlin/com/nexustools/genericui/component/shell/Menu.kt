package com.nexustools.genericui.component.shell

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.event.KeyShortcut
import com.nexustools.genericui.nativeimpl.shell.MenuNativeImpl
import com.nexustools.genericui.res.ResourceId

class Menu(
    label: String = "",
    icon: String? = null,
    iconRes: ResourceId? = null
) : AbstractContainer("nav-item") {
    init {
        htmlTag = "genericui:menu"
    }

    private var labelState: String = label
    private var iconState: String? = icon ?: iconRes?.toResourceUri()

    override fun createNativeImpl(factory: NativeFactory): MenuNativeImpl<*> =
        factory.createMenu()

    override fun onAttachNative(impl: NativeImpl<*>) {
        super.onAttachNative(impl)
        val menuImpl = impl as? MenuNativeImpl<*> ?: return
        menuImpl.setLabel(labelState)
        menuImpl.setIcon(iconState)
    }

    fun setLabel(label: String) {
        labelState = label
        typedNative<MenuNativeImpl<*>>()?.setLabel(label)
    }

    fun getLabel(): String = typedNative<MenuNativeImpl<*>>()?.getLabel() ?: labelState

    fun setIcon(icon: String?) {
        iconState = icon
        typedNative<MenuNativeImpl<*>>()?.setIcon(icon)
    }

    fun setIconResource(resId: ResourceId) {
        setIcon(resId.toResourceUri())
    }

    fun getIcon(): String? = typedNative<MenuNativeImpl<*>>()?.getIcon() ?: iconState

    fun addItem(item: MenuItem): Menu {
        addChild(item)
        return this
    }

    fun addItem(item: AbstractComponent): Menu {
        addChild(item)
        return this
    }

    fun addItem(
        label: String,
        icon: String? = null,
        shortcut: KeyShortcut? = null,
        onClick: (() -> Unit)? = null
    ): MenuItem {
        val item = MenuItem(label = label, icon = icon, shortcut = shortcut, onClick = onClick)
        addChild(item)
        return item
    }

    fun addSubmenu(submenu: Menu): Menu {
        addChild(submenu)
        return this
    }

    fun addSeparator(): Menu {
        addChild(MenuSeparator())
        return this
    }
}
