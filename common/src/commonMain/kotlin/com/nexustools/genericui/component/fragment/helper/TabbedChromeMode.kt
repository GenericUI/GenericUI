package com.nexustools.genericui.component.fragment.helper

/**
 * Adaptive tab chrome presentation mode for [TabbedFragment].
 */
enum class TabbedChromeMode {
    /** Automatically selects [DESKTOP_BROWSER_TABS] or [MOBILE_SWITCHER_PILL] based on viewport width and platform. */
    AUTO,

    /** Chrome/Firefox-style horizontal tab strip with per-tab close (`×`) and new-tab (`+`) buttons. */
    DESKTOP_BROWSER_TABS,

    /** Mobile Chrome/Firefox-style compact top bar with active tab title and `[N]` tab-count pill switcher. */
    MOBILE_SWITCHER_PILL
}
