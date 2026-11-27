package com.nexustools.genericui.component.fragment.helper

/**
 * Configuration options for [TabbedFragment].
 *
 * @property chromeMode Default [TabbedChromeMode] (`AUTO`, `DESKTOP_BROWSER_TABS`, or `MOBILE_SWITCHER_PILL`).
 * @property mobileBreakpointWidthPx Viewport width threshold (in pixels) below which [TabbedChromeMode.AUTO] switches to [TabbedChromeMode.MOBILE_SWITCHER_PILL].
 * @property showNewTabButton Whether to display the `+` new-tab action button in the tab strip and mobile chrome.
 * @property allowCloseLastTab Whether closing the last remaining tab is allowed (`false` by default).
 * @property persistLastActiveTabKey Optional [com.nexustools.genericui.component.system.PreferenceStore] key used to persist and restore the active `tabId`.
 * @property chromeStyle Visual chrome style (`CLASSIC` by default, or `CHROMIUM` for browser-style tabs with optional native `TitleBar` embedding).
 * @property showTabSearchButton Whether to show a far-left circular tab-search/switcher button (`⌄`) in Chromium mode.
 * @property connectActiveTabToNavbar Whether the active Chromium tab removes its bottom margin/radii and shares the navbar surface color so it fuses cleanly into the navbar below.
 * @property showTitleBarWindowIcon Whether the managed `TitleBar` auto-created by [TabbedFragment] displays the host window icon (`false` by default for Chromium browser tabs).
 */
data class TabbedFragmentConfig(
    val chromeMode: TabbedChromeMode = TabbedChromeMode.AUTO,
    val mobileBreakpointWidthPx: Int = 720,
    val showNewTabButton: Boolean = true,
    val allowCloseLastTab: Boolean = false,
    val persistLastActiveTabKey: String? = null,
    val chromeStyle: TabbedChromeStyle = TabbedChromeStyle.CLASSIC,
    val showTabSearchButton: Boolean = false,
    val connectActiveTabToNavbar: Boolean = true,
    val showTitleBarWindowIcon: Boolean = false
)
