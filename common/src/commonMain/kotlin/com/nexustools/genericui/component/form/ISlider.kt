package com.nexustools.genericui.component.form

import com.nexustools.genericui.component.typography.ILabelTarget

/**
 * Common numeric slider contract implemented by both standard system [Slider] / [RangeSlider]
 * and explicitly styled [com.nexustools.genericui.component.form.styled.StyledSlider].
 */
interface ISlider : ILabelTarget {
    var min: Double
    var max: Double
    var step: Double
    var value: Double
    var isEnabled: Boolean
    var onValueChanged: ((Double) -> Unit)?
}
