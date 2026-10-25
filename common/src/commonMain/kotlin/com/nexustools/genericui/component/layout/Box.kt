package com.nexustools.genericui.component.layout

import com.nexustools.genericui.enums.bootstrap.PositionMode

/**
 * Stacked overlay container (`Box`) used for layering components on top of each other
 * (e.g., `VideoPlayer` + transport overlay in Episode Caster, or base `Canvas2D` + pass-through overlay `Canvas2D` in HAEditor).
 */
open class Box : Container("genericui:box", "position-relative") {
    init {
        htmlTag = "genericui:box"
        setPosition(PositionMode.RELATIVE)
    }
}
