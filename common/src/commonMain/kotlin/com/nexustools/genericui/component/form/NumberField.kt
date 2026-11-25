package com.nexustools.genericui.component.form

/**
 * Numeric input field with optional min, max, and step constraints.
 */
class NumberField(
    numericValue: Double = 0.0,
    var minValue: Double = -Double.MAX_VALUE,
    var maxValue: Double = Double.MAX_VALUE,
    var step: Double = 1.0,
    label: String? = null
) : TextField(value = numericValue.toString(), label = label) {
    init {
        setAttribute("data-control-variant", "number")
    }

    var numericValue: Double
        get() = value.toDoubleOrNull()?.coerceIn(minValue, maxValue) ?: 0.0
        set(newVal) {
            value = newVal.coerceIn(minValue, maxValue).toString()
        }

    override fun syncStateToNative() {
        setAttribute("data-control-variant", "number")
        super.syncStateToNative()
    }
}
