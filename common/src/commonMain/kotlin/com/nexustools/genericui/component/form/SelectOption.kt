package com.nexustools.genericui.component.form

/**
 * Option entry inside a [Select] or [ComboBox] dropdown.
 */
data class SelectOption(
    val value: String,
    val label: String = value,
    val disabled: Boolean = false
)
