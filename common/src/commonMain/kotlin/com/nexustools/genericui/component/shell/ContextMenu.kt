package com.nexustools.genericui.component.shell

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.event.KeyShortcut
import com.nexustools.genericui.nativeimpl.shell.ContextMenuNativeImpl

class ContextMenu : AbstractContainer("dropdown-menu") {
    init {
        htmlTag = "genericui:contextmenu"
    }

    var isShowing: Boolean = false
        private set
    var lastShownX: Int = 0
        private set
    var lastShownY: Int = 0
        private set
    var lastAnchor: AbstractComponent? = null
        private set

    override fun createNativeImpl(factory: NativeFactory): ContextMenuNativeImpl<*> =
        factory.createContextMenu()

    fun addItem(item: MenuItem): ContextMenu {
        addChild(item)
        return this
    }

    fun addItem(item: AbstractComponent): ContextMenu {
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

    fun addSubmenu(submenu: Menu): ContextMenu {
        addChild(submenu)
        return this
    }

    fun addSeparator(): ContextMenu = addItem(MenuSeparator())

    fun showAt(anchor: AbstractComponent?, x: Int, y: Int) {
        isShowing = true
        lastShownX = x
        lastShownY = y
        lastAnchor = anchor
        val anchorFactory = anchor?.resolveActiveFactory()
        if (parent == null && anchor != null) {
            parent = anchor
        }
        if (anchorFactory != null) {
            val impl = ensureNativeImpl() as? ContextMenuNativeImpl<*>
            impl?.showAt(anchor.nativeImpl, x, y)
        }
    }

    fun hide() {
        isShowing = false
        typedNative<ContextMenuNativeImpl<*>>()?.hide()
    }
}
