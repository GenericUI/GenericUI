package com.nexustools.genericui.component.layout

import com.nexustools.genericui.enums.bootstrap.DisplayMode
import com.nexustools.genericui.enums.bootstrap.FlexDirection
import com.nexustools.genericui.enums.bootstrap.SpacingScale

open class VStack(
    var gap: SpacingScale? = null
) : Container("genericui:vstack", "vstack") {
    init {
        htmlTag = "genericui:vstack"
        setDisplay(DisplayMode.FLEX)
        setFlexDirection(FlexDirection.COLUMN)
        gap?.let { addClass("gap-${it.token}") }
    }
}
