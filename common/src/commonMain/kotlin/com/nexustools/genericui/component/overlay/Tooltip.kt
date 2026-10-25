package com.nexustools.genericui.component.overlay

import com.nexustools.genericui.enums.ui.Placement

/**
 * Hover/focus informational tooltip overlay (`<div class="tooltip">`).
 */
class Tooltip(
    text: String,
    placement: Placement = Placement.TOP
) : Popover(title = text, placement = placement) {
    init {
        htmlTag = "genericui:tooltip"
    }
}
