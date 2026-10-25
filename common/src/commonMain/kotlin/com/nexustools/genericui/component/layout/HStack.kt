package com.nexustools.genericui.component.layout

import com.nexustools.genericui.enums.bootstrap.DisplayMode
import com.nexustools.genericui.enums.bootstrap.FlexDirection
import com.nexustools.genericui.enums.bootstrap.SpacingScale

open class HStack(
    var gap: SpacingScale? = null
) : Container("hstack") {
    init {
        setDisplay(DisplayMode.FLEX)
        setFlexDirection(FlexDirection.ROW)
        gap?.let { addClass("gap-${it.token}") }
    }
}
