package com.nexustools.genericui.component.fragment.helper

/**
 * Configuration options for [TabbedFragment].
 *
 * @property chromeMode Default [TabbedChromeMode] (`AUTO`, `DESKTOP_BROWSER_TABS`, or `MOBILE_SWITCHER_PILL`).
 * @property mobileBreakpointWidthPx Viewport width threshold (in pixels) below which [TabbedChromeMode.AUTO] switches to [TabbedChromeMode.MOBILE_SWITCHER_PILL].
 * @property showNewTabButton Whether to display the `+` new-tab action button in the tab strip and mobile chrome.
 * @property allowCloseLastTab Whether closing the last remaining tab is allowed (`false` by default).
 * @property persistLastActiveTabKey Optional [com.nexustools.genericui.component.system.PreferenceStore] key used to persist and restore the active `tabId`.
 */
data class TabbedFragmentConfig(
    val chromeMode: TabbedChromeMode = TabbedChromeMode.AUTO,
    val mobileBreakpointWidthPx: Int = 720,
    val showNewTabButton: Boolean = true,
    val allowCloseLastTab: Boolean = false,
    val persistLastActiveTabKey: String? = null
)
