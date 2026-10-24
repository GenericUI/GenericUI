package com.nexustools.genericui.component.system

import com.nexustools.genericui.component.shell.ContextMenu
import com.nexustools.genericui.core.Disposable
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.nativeimpl.shell.ContextMenuNativeImpl
import com.nexustools.genericui.nativeimpl.system.SystemTrayNativeImpl

/**
 * Desktop OS system tray / menu bar status item (`Shell_NotifyIconW`, `NSStatusItem`,
 * `StatusNotifierItem`, `QSystemTrayIcon`, `java.awt.SystemTray`).
 */
class SystemTrayIcon(
    iconPathOrUrl: String? = null,
    tooltip: String? = null
) : Disposable {
    private var nativeImpl: SystemTrayNativeImpl? = GenericUI.activeFactory?.createSystemTray()

    var iconPathOrUrl: String? = iconPathOrUrl
        set(value) {
            field = value
            nativeImpl?.setIcon(value)
        }

    var tooltip: String? = tooltip
        set(value) {
            field = value
            nativeImpl?.setTooltip(value)
        }

    var badgeText: String? = null
        set(value) {
            field = value
            nativeImpl?.setBadgeText(value)
        }

    var contextMenu: ContextMenu? = null
        set(value) {
            field = value
            nativeImpl?.setContextMenu(value?.nativeImpl as? ContextMenuNativeImpl<*>)
        }

    var onClick: (() -> Unit)? = null
        set(value) {
            field = value
            nativeImpl?.setOnTrayIconClicked(value)
        }

    init {
        nativeImpl?.setIcon(iconPathOrUrl)
        nativeImpl?.setTooltip(tooltip)
    }

    override fun dispose() {
        nativeImpl?.dispose()
        nativeImpl = null
    }
}
