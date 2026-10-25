package com.nexustools.genericui.component.layout

import com.nexustools.genericui.enums.bootstrap.DisplayMode
import com.nexustools.genericui.enums.bootstrap.FlexDirection

open class Row : Container("genericui:row", "row") {
    init {
        htmlTag = "genericui:row"
        setDisplay(DisplayMode.FLEX)
        setFlexDirection(FlexDirection.ROW)
    }
}
