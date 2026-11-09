package com.nexustools.genericui.component.layout

import com.nexustools.genericui.enums.bootstrap.DisplayMode
import com.nexustools.genericui.enums.bootstrap.FlexWrap
import com.nexustools.genericui.enums.css.CssDisplay

open class FlexBox : Container("genericui:flexbox", "d-flex") {
    init {
        htmlTag = "genericui:flexbox"
        setDisplay(DisplayMode.FLEX)
        getStyle().setDisplay(CssDisplay.FLEX)
    }

    override fun setFlexWrap(wrap: FlexWrap?) {
        super.setFlexWrap(wrap)
        getStyle().setFlexWrap(wrap)
    }

    override fun getFlexWrap(): FlexWrap? =
        super.getFlexWrap() ?: getStyle().getFlexWrap()
}
