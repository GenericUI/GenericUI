package com.nexustools.genericui.component.form

/**
 * Single radio button option (`<input type="radio">`).
 */
class RadioButton(
    val optionValue: String,
    label: String = optionValue,
    checked: Boolean = false
) : CheckBox(
    label = label,
    checked = checked,
    switchStyle = false,
    radioStyle = true
)
