package com.nexustools.genericui.component.tabs

/**
 * Placement of the tab header strip relative to the tab content pane.
 */
enum class TabPosition(val cssClass: String) {
    TOP("tabs-top"),
    BOTTOM("tabs-bottom"),
    LEFT("tabs-left"),
    RIGHT("tabs-right")
}
