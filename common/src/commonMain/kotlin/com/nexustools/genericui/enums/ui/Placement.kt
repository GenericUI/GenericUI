package com.nexustools.genericui.enums.ui

/**
 * Placement direction for Tooltips, Popovers, Dropdowns, ToastContainers, and Offcanvas panels.
 */
enum class Placement(val cssToken: String) {
    TOP("top"),
    BOTTOM("bottom"),
    START("start"),
    END("end"),
    TOP_START("top-start"),
    TOP_END("top-end"),
    BOTTOM_START("bottom-start"),
    BOTTOM_END("bottom-end")
}
