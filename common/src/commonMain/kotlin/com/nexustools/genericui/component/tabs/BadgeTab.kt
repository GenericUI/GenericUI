package com.nexustools.genericui.component.tabs

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.nativeimpl.tabs.TabNativeImpl

/**
 * A [Tab] displaying a native count/status badge (e.g. unread messages, compiler diagnostics, or queue size)
 * alongside the tab title and optional icon.
 */
open class BadgeTab(
    tabId: String = "badge_tab_${nextTabCounter()}",
    title: String = "",
    badgeText: String? = null,
    badgeVariant: BootstrapType = BootstrapType.PRIMARY,
    icon: String? = null,
    tooltip: String? = null,
    customComponent: AbstractComponent? = null
) : Tab(
    tabId = tabId,
    title = title,
    icon = icon,
    tooltip = tooltip,
    customComponent = customComponent
) {

    var badgeText: String? = badgeText
        set(value) {
            field = value
            typedNative<TabNativeImpl<*>>()?.setBadgeNative(value, badgeVariant.name.lowercase())
            ownerNotifyUpdated?.invoke(this)
        }

    var badgeVariant: BootstrapType = badgeVariant
        set(value) {
            field = value
            typedNative<TabNativeImpl<*>>()?.setBadgeNative(badgeText, value.name.lowercase())
            ownerNotifyUpdated?.invoke(this)
        }

    override fun syncStateToNative() {
        super.syncStateToNative()
        typedNative<TabNativeImpl<*>>()?.setBadgeNative(badgeText, badgeVariant.name.lowercase())
    }
}
