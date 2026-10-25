package com.nexustools.genericui.component.layout

import com.nexustools.genericui.enums.bootstrap.DisplayMode
import com.nexustools.genericui.enums.bootstrap.FlexDirection

open class Col(val span: Int? = null, val weight: Float = 1.0f) : Container("genericui:col", if (span != null) "col-$span" else "col") {
    init {
        htmlTag = "genericui:col"
        setDisplay(DisplayMode.FLEX)
        setFlexDirection(FlexDirection.COLUMN)
    }
}
