package com.nexustools.genericui.component.tabs

import com.nexustools.genericui.component.media.Image
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.nativeimpl.tabs.TabNativeImpl
import com.nexustools.genericui.resources.ResourceID

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
    customComponent: AbstractComponent? = null,
    iconRes: ResourceID? = null,
    image: Image? = null,
    tintImageToTheme: Boolean = true,
    iconSizePx: Int = image?.widthPx ?: image?.heightPx ?: 20,
    iconPlacement: TabIconPlacement = TabIconPlacement.START
) : Tab(
    tabId = tabId,
    title = title,
    icon = icon,
    tooltip = tooltip,
    customComponent = customComponent,
    iconRes = iconRes,
    image = image,
    tintImageToTheme = tintImageToTheme,
    iconSizePx = iconSizePx,
    iconPlacement = iconPlacement
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

    init {
        htmlTag = "genericui:badgetab"
    }

    override fun syncStateToNative() {
        super.syncStateToNative()
        typedNative<TabNativeImpl<*>>()?.setBadgeNative(badgeText, badgeVariant.name.lowercase())
    }
}
