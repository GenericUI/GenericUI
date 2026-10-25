package com.nexustools.genericui.component.layout

import com.nexustools.genericui.enums.bootstrap.DisplayMode

open class FlexBox : Container("genericui:flexbox", "d-flex") {
    init {
        htmlTag = "genericui:flexbox"
        setDisplay(DisplayMode.FLEX)
    }
}
