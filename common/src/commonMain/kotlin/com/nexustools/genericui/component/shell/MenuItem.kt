package com.nexustools.genericui.component.shell

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.event.KeyShortcut
import com.nexustools.genericui.nativeimpl.shell.MenuItemNativeImpl
import com.nexustools.genericui.resources.ResourceID

open class MenuItem(
    label: String = "",
    icon: String? = null,
    shortcut: KeyShortcut? = null,
    onClick: (() -> Unit)? = null
) : AbstractComponent("dropdown-item", htmlTag = "genericui:menuitem") {

    private var labelState: String = label
    private var iconState: String? = icon
    private var shortcutState: KeyShortcut? = shortcut
    protected var checkedState: Boolean = false
    internal var onMenuMutated: (() -> Unit)? = null

    internal fun notifyMenuMutated() {
        onMenuMutated?.invoke()
        (parent as? Menu)?.notifyMenuMutated()
    }

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
        iconRes: ResourceID,
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

    protected override fun createNativeImpl(factory: NativeFactory): MenuItemNativeImpl<*> =
        factory.createMenuItem(checkable = checkedState, radio = false)

    protected override fun onAttachNative(impl: NativeImpl<*>) {
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
        notifyMenuMutated()
    }

    fun getLabel(): String = typedNative<MenuItemNativeImpl<*>>()?.getLabel() ?: labelState

    fun setIcon(icon: String?) {
        iconState = icon
        typedNative<MenuItemNativeImpl<*>>()?.setIcon(icon)
        notifyMenuMutated()
    }

    fun setIconResource(resId: ResourceID) {
        setIcon(resId.toResourceUri())
    }

    fun getIcon(): String? = typedNative<MenuItemNativeImpl<*>>()?.getIcon() ?: iconState

    fun setShortcut(shortcut: KeyShortcut?) {
        shortcutState = shortcut
        typedNative<MenuItemNativeImpl<*>>()?.setShortcut(shortcut)
        notifyMenuMutated()
    }

    fun setShortcutText(shortcutText: String?) {
        setShortcut(KeyShortcut.parse(shortcutText))
    }

    fun getShortcut(): KeyShortcut? = typedNative<MenuItemNativeImpl<*>>()?.getShortcut() ?: shortcutState

    fun getShortcutText(): String? = getShortcut()?.toString()

    open fun setChecked(checked: Boolean) {
        checkedState = checked
        typedNative<MenuItemNativeImpl<*>>()?.setChecked(checked)
        notifyMenuMutated()
    }

    open fun isChecked(): Boolean = typedNative<MenuItemNativeImpl<*>>()?.isChecked() ?: checkedState

    override var isVisible: Boolean
        get() = super.isVisible
        set(value) {
            val changed = super.isVisible != value
            super.isVisible = value
            if (changed) {
                notifyMenuMutated()
            }
        }

    override var isEnabled: Boolean
        get() = super.isEnabled
        set(value) {
            val changed = super.isEnabled != value
            super.isEnabled = value
            if (changed) {
                notifyMenuMutated()
            }
        }
}
