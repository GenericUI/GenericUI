package com.nexustools.genericui.component.form

/**
 * Editable/searchable dropdown combo box control.
 */
class ComboBox(
    options: List<SelectOption> = emptyList(),
    selectedValue: String? = options.firstOrNull()?.value,
    var allowCustomInput: Boolean = true
) : Select(options = options, selectedValue = selectedValue)
