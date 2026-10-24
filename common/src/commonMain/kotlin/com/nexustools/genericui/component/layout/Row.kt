package com.nexustools.genericui.component.layout

import com.nexustools.genericui.enums.bootstrap.DisplayMode
import com.nexustools.genericui.enums.bootstrap.FlexDirection

open class Row : Container("row") {
    init {
        setDisplay(DisplayMode.FLEX)
        setFlexDirection(FlexDirection.ROW)
    }
}
