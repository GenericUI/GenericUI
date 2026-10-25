package com.nexustools.genericui.component.form

import com.nexustools.genericui.component.layout.HStack
import com.nexustools.genericui.component.typography.Label
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Dual-number compound unit input group (e.g., Feet + Inches with `subdivisionsPerPrimary = 12.0`
 * or Pounds + Ounces with `subdivisionsPerPrimary = 16.0`, matching `DCGame`'s `CharGenStep11`).
 */
open class CompoundUnitInput(
    primaryUnitSuffix: String = "ft.",
    secondaryUnitSuffix: String = "in.",
    val subdivisionsPerPrimary: Double = 12.0,
    minTotal: Double = 0.0,
    maxTotal: Double = 1000.0
) : HStack() {

    val primaryField: NumberField = NumberField(numericValue = 0.0, minValue = 0.0, maxValue = maxTotal, step = 1.0)
    val primaryUnitLabel: Label = Label(primaryUnitSuffix, "span", "input-group-text")
    val secondaryField: NumberField = NumberField(
        numericValue = 0.0,
        minValue = 0.0,
        maxValue = (subdivisionsPerPrimary - 1.0).coerceAtLeast(1.0),
        step = 1.0
    )
    val secondaryUnitLabel: Label = Label(secondaryUnitSuffix, "span", "input-group-text")

    var minTotalValue: Double = minTotal
    var maxTotalValue: Double = maxTotal

    var onTotalValueChanged: ((Double) -> Unit)? = null

    var totalValue: Double
        get() {
            val p = floor(primaryField.numericValue)
            val s = secondaryField.numericValue.coerceIn(0.0, subdivisionsPerPrimary - 0.0001)
            return (p + s / subdivisionsPerPrimary).coerceIn(minTotalValue, maxTotalValue)
        }
        set(value) {
            val clamped = value.coerceIn(minTotalValue, maxTotalValue)
            val wholePrimary = floor(clamped)
            val remainderSub = ((clamped - wholePrimary) * subdivisionsPerPrimary).roundToInt().toDouble()
            if (remainderSub >= subdivisionsPerPrimary) {
                primaryField.numericValue = wholePrimary + 1.0
                secondaryField.numericValue = 0.0
            } else {
                primaryField.numericValue = wholePrimary
                secondaryField.numericValue = remainderSub
            }
        }

    init {
        htmlTag = "genericui:compoundunitinput"
        addClass("input-group")
        addChild(primaryField)
        addChild(primaryUnitLabel)
        addChild(secondaryField)
        addChild(secondaryUnitLabel)

        primaryField.onValueChanged = { onTotalValueChanged?.invoke(totalValue) }
        secondaryField.onValueChanged = { onTotalValueChanged?.invoke(totalValue) }
    }
}
