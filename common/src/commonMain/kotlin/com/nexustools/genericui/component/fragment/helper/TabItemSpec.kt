package com.nexustools.genericui.component.fragment.helper

import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.media.Image
import com.nexustools.genericui.resources.ResourceID

/**
 * Specification for a single tab managed by [TabbedFragment].
 *
 * @property tabId Unique identifier for this tab within its [TabbedFragment].
 * @property title Display title shown in the tab strip, mobile header bar, and mobile overview card.
 * @property subtitle Optional subtitle or URL/context caption shown in the mobile tab overview card.
 * @property iconName Optional icon identifier or symbol rendered alongside the tab title.
 * @property badgeText Optional badge text rendered on the tab chip or card.
 * @property closable Whether the user can close this tab (`true` by default).
 * @property pinned Whether this tab is pinned (`false` by default; pinned tabs are non-closable unless forced).
 * @property fragmentFactory Lazy factory invoked once when the tab's [Fragment] is first created.
 * @property iconUrl Optional favicon/icon URL, data URI, or `res://` path rendered inside the tab icon slot.
 * @property iconRes Optional compiled drawable [ResourceID] rendered inside the tab icon slot.
 * @property iconImage Optional explicit [Image] instance rendered inside the tab icon slot.
 * @property tintIconToTheme Whether the tab icon [Image] should apply automatic monocolor theme recoloring (`false` by default).
 */
data class TabItemSpec(
    val tabId: String,
    val title: String,
    val subtitle: String = "",
    val iconName: String? = null,
    val badgeText: String? = null,
    val closable: Boolean = true,
    val pinned: Boolean = false,
    val fragmentFactory: () -> Fragment,
    val iconUrl: String? = null,
    val iconRes: ResourceID? = null,
    val iconImage: Image? = null,
    val tintIconToTheme: Boolean = false
)
