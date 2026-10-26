package com.nexustools.genericui.component.layout

/**
 * Placement of the tab switcher when [HybridColumnLayout] collapses columns into tabs
 * in [HybridLayoutMode.SINGLE] (or when 3+ columns are present).
 */
enum class HybridTabPlacement {
    /**
     * Renders a top [com.nexustools.genericui.component.navigation.TabLayout] above the tabbed column content.
     */
    TOP_TABS,

    /**
     * Renders a bottom [com.nexustools.genericui.component.navigation.BottomNavigationBar] anchored below the tabbed column content.
     */
    BOTTOM_NAV,

    /**
     * Uses [TOP_TABS] in wide/tablet column mode (`inColumnMode == true`) and [BOTTOM_NAV] in compact single mode (`inColumnMode == false`).
     */
    ADAPTIVE_TOP_IN_COLUMNS_BOTTOM_IN_SINGLE
}
