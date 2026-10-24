package com.nexustools.genericui.nativeimpl.system

import com.nexustools.genericui.core.Disposable
import com.nexustools.genericui.nativeimpl.shell.ContextMenuNativeImpl

interface SystemTrayNativeImpl : Disposable {
    val isSupported: Boolean
    fun setIcon(iconPathOrUrl: String?)
    fun setTooltip(tooltip: String?)
    fun setBadgeText(badge: String?)
    fun setContextMenu(menuImpl: ContextMenuNativeImpl<*>?)
    fun setOnTrayIconClicked(listener: (() -> Unit)?)
}
