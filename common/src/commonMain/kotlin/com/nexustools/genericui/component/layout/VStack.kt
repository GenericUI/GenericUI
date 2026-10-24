package com.nexustools.genericui.component.layout

import com.nexustools.genericui.enums.bootstrap.DisplayMode
import com.nexustools.genericui.enums.bootstrap.FlexDirection

open class VStack : Container("vstack") {
    init {
        setDisplay(DisplayMode.FLEX)
        setFlexDirection(FlexDirection.COLUMN)
    }
}
