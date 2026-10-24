package com.nexustools.genericui.component.form

/**
 * Dual-endpoint or single-endpoint range slider control.
 */
class RangeSlider(
    minValue: Double = 0.0,
    maxValue: Double = 100.0,
    min: Double = 0.0,
    max: Double = 100.0,
    step: Double = 1.0
) : Slider(value = minValue, min = min, max = max, step = step) {
    var upperValue: Double = maxValue.coerceIn(min, max)
}
