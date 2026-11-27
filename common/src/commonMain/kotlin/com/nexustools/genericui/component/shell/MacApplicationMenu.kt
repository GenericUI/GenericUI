package com.nexustools.genericui.component.shell

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.event.KeyShortcut

/**
 * macOS-specific container for customizing items inside the system Application Menu
 * (placed between "About <App>" and "Hide <App>").
 *
 * Intentionally isolated from cross-platform [Menu] / [MenuBar] instances so per-window menus used
 * on Windows and other targets never mutate the macOS Application Menu.
 */
class MacApplicationMenu(label: String = "Application") {

    private val backingMenu: Menu = Menu(label = label)

    internal var onMutated: (() -> Unit)? = null
        set(value) {
            field = value
            backingMenu.onMenuMutated = value
        }

    val items: List<AbstractComponent>
        get() = backingMenu.children

    fun addItem(item: MenuItem): MenuItem {
        backingMenu.addItem(item)
        return item
    }

    fun addItem(
        label: String,
        icon: String? = null,
        shortcut: KeyShortcut? = null,
        onClick: () -> Unit
    ): MenuItem = backingMenu.addItem(
        label = label,
        icon = icon,
        shortcut = shortcut,
        onClick = onClick
    )

    fun addCheckItem(
        label: String,
        checked: Boolean = false,
        shortcut: KeyShortcut? = null,
        onToggled: (Boolean) -> Unit
    ): CheckMenuItem {
        val item = CheckMenuItem(
            label = label,
            checked = checked,
            shortcut = shortcut,
            onToggled = onToggled
        )
        backingMenu.addItem(item)
        return item
    }

    fun addSeparator(): MenuItem {
        val separator = MenuSeparator()
        backingMenu.addChild(separator)
        return MenuItem(label = "-")
    }

    fun addSubmenu(submenu: Menu): Menu = backingMenu.addSubmenu(submenu)

    fun clear() {
        backingMenu.clearChildren()
    }

    internal fun internalMenu(): Menu = backingMenu

    internal fun dispose() {
        onMutated = null
        backingMenu.onMenuMutated = null
        backingMenu.parent = null
        backingMenu.dispose()
    }
}
