package com.nexustools.genericui.component.layout

import com.nexustools.genericui.enums.bootstrap.DisplayMode

open class FlexBox : Container("d-flex") {
    init {
        setDisplay(DisplayMode.FLEX)
    }
}
