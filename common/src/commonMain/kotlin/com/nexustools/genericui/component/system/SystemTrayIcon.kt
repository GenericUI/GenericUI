package com.nexustools.genericui.component.system

import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.shell.ContextMenu
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.Disposable
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.exception.NativeUninitializedException
import com.nexustools.genericui.exception.SystemTrayUnsupportedException
import com.nexustools.genericui.nativeimpl.shell.ContextMenuNativeImpl
import com.nexustools.genericui.nativeimpl.system.SystemTrayNativeImpl

/**
 * Desktop OS system tray / menu bar status item (`Shell_NotifyIconW`, `NSStatusItem`,
 * `StatusNotifierItem`, `QSystemTrayIcon`, `java.awt.SystemTray`).
 */
class SystemTrayIcon(
    iconPathOrUrl: String? = null,
    tooltip: String? = null,
    macosIconPathOrUrl: String? = null
) : Disposable {
    private class TrayAnchorContainer(
        private var boundFactory: NativeFactory? = null
    ) : Container("tray-anchor") {
        fun bindFactory(factory: NativeFactory?) {
            if (factory != null) {
                boundFactory = factory
            }
        }

        override fun resolveActiveFactory(): NativeFactory? =
            boundFactory ?: GenericUI.activeFactory

        override fun resolveChildFactory(): NativeFactory? =
            resolveActiveFactory()

        override fun resolveInitialNativeVisibility(): Boolean = false
    }

    private var nativeImpl: SystemTrayNativeImpl? =
        GenericUI.activeFactory?.takeIf { it.supportsSystemTray }?.createSystemTray()
    private var anchorContainer: TrayAnchorContainer? = null

    var macosIconPathOrUrl: String? = macosIconPathOrUrl
        set(value) {
            field = value
            nativeImpl?.setMacosIcon(value)
        }

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
            if (value == null) {
                nativeImpl?.setContextMenu(null)
            } else {
                realizeAndBindContextMenu(value, GenericUI.activeFactory)
            }
        }

    var onClick: (() -> Unit)? = null
        set(value) {
            field = value
            nativeImpl?.setOnTrayIconClicked(value)
        }

    init {
        if (macosIconPathOrUrl != null) {
            nativeImpl?.setMacosIcon(macosIconPathOrUrl)
        }
        nativeImpl?.setIcon(iconPathOrUrl)
        nativeImpl?.setTooltip(tooltip)
    }

    /**
     * Realizes the native tray icon and any attached standalone [contextMenu] when a [NativeFactory]
     * becomes available (e.g., if the tray icon was configured prior to [GenericUI.initialize]).
     *
     * @throws NativeUninitializedException if no [NativeFactory] is initialized.
     * @throws SystemTrayUnsupportedException if the active backend does not support system tray icons.
     */
    internal fun attachTrayNativeImpl(factory: NativeFactory? = GenericUI.activeFactory): SystemTrayNativeImpl {
        val resolvedFactory = factory ?: GenericUI.activeFactory
            ?: throw NativeUninitializedException("SystemTrayIcon", "show")
        if (!resolvedFactory.supportsSystemTray) {
            throw SystemTrayUnsupportedException(resolvedFactory.backendTarget.displayName)
        }
        val impl = nativeImpl ?: resolvedFactory.createSystemTray().also { created ->
            nativeImpl = created
            if (macosIconPathOrUrl != null) {
                created.setMacosIcon(macosIconPathOrUrl)
            }
            created.setIcon(iconPathOrUrl)
            created.setTooltip(tooltip)
            if (badgeText != null) {
                created.setBadgeText(badgeText)
            }
            if (onClick != null) {
                created.setOnTrayIconClicked(onClick)
            }
        }
        if (!impl.isSupported) {
            throw SystemTrayUnsupportedException(resolvedFactory.backendTarget.displayName)
        }
        val activeMenu = contextMenu
        if (activeMenu != null) {
            realizeAndBindContextMenu(activeMenu, resolvedFactory)
        }
        return impl
    }

    /**
     * Ensures the tray icon native implementation and any standalone [contextMenu] are realized
     * against [GenericUI.activeFactory].
     *
     * @throws NativeUninitializedException if [GenericUI.activeFactory] has not been initialized.
     * @throws SystemTrayUnsupportedException if the active backend does not support system tray icons.
     */
    fun show() {
        attachTrayNativeImpl(GenericUI.activeFactory)
    }

    private fun realizeAndBindContextMenu(menu: ContextMenu, factory: NativeFactory?) {
        val resolvedFactory = factory ?: GenericUI.activeFactory
        if (menu.nativeImpl == null && resolvedFactory != null) {
            val anchor = anchorContainer ?: TrayAnchorContainer(resolvedFactory).also {
                anchorContainer = it
            }
            anchor.bindFactory(resolvedFactory)
            anchor.ensureNativeImpl()
            if (menu.parent == null) {
                menu.parent = anchor
            }
            realizeSubtree(menu)
        } else if (menu.nativeImpl != null) {
            realizeSubtree(menu)
        }
        nativeImpl?.setContextMenu(menu.nativeImpl as? ContextMenuNativeImpl<*>)
    }

    private fun realizeSubtree(component: AbstractComponent) {
        component.ensureNativeImpl()
        if (component is AbstractContainer) {
            for (child in component.children) {
                realizeSubtree(child)
            }
        }
    }

    override fun dispose() {
        nativeImpl?.dispose()
        nativeImpl = null
        anchorContainer?.dispose()
        anchorContainer = null
    }
}
