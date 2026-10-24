package com.nexustools.genericui.component.form

/**
 * Toggle switch control (`<div class="form-check form-switch">`).
 */
class Switch(
    label: String = "",
    checked: Boolean = false
) : CheckBox(
    label = label,
    checked = checked,
    switchStyle = true,
    radioStyle = false
)
