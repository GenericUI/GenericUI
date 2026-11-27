package com.nexustools.genericui.component.fragment.helper

/**
 * Visual presentation styles for [TabbedFragment]'s tab chrome.
 */
enum class TabbedChromeStyle {
    /**
     * Classic content-header strip using bootstrap-style tab buttons inside [TabbedFragment].
     */
    CLASSIC,

    /**
     * Chromium-style browser tab strip with rounded-top active tabs seamlessly connected to the
     * navbar below, and native [com.nexustools.genericui.window.TitleBar] embedding on desktop
     * backends that support embedded components.
     */
    CHROMIUM
}
