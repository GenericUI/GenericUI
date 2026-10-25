package com.nexustools.genericui.component.shell

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.event.KeyShortcut
import com.nexustools.genericui.nativeimpl.shell.MenuItemNativeImpl
import com.nexustools.genericui.res.ResourceId

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

    constructor(
        label: String,
        icon: String? = null,
        shortcutText: String?,
        checked: Boolean = false,
        disabled: Boolean = false,
        onClick: (() -> Unit)? = null
    ) : this(
        label = label,
        icon = icon,
        shortcut = KeyShortcut.parse(shortcutText),
        onClick = onClick
    ) {
        checkedState = checked
        if (disabled) {
            isEnabled = false
        }
    }

    constructor(
        label: String,
        iconRes: ResourceId,
        shortcut: KeyShortcut? = null,
        shortcutText: String? = null,
        checked: Boolean = false,
        disabled: Boolean = false,
        onClick: (() -> Unit)? = null
    ) : this(
        label = label,
        icon = iconRes.toResourceUri(),
        shortcut = shortcut ?: KeyShortcut.parse(shortcutText),
        onClick = onClick
    ) {
        checkedState = checked
        if (disabled) {
            isEnabled = false
        }
    }

    init {
        if (onClick != null) {
            setOnClick(onClick)
        }
    }

    override fun createNativeImpl(factory: NativeFactory): MenuItemNativeImpl<*> =
        factory.createMenuItem(checkable = checkedState, radio = false)

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

    fun setIconResource(resId: ResourceId) {
        setIcon(resId.toResourceUri())
    }

    fun getIcon(): String? = typedNative<MenuItemNativeImpl<*>>()?.getIcon() ?: iconState

    fun setShortcut(shortcut: KeyShortcut?) {
        shortcutState = shortcut
        typedNative<MenuItemNativeImpl<*>>()?.setShortcut(shortcut)
    }

    fun setShortcutText(shortcutText: String?) {
        setShortcut(KeyShortcut.parse(shortcutText))
    }

    fun getShortcut(): KeyShortcut? = typedNative<MenuItemNativeImpl<*>>()?.getShortcut() ?: shortcutState

    fun getShortcutText(): String? = getShortcut()?.toString()

    open fun setChecked(checked: Boolean) {
        checkedState = checked
        typedNative<MenuItemNativeImpl<*>>()?.setChecked(checked)
    }

    open fun isChecked(): Boolean = typedNative<MenuItemNativeImpl<*>>()?.isChecked() ?: checkedState
}
