package com.nexustools.genericui.component.form

/**
 * Editable/searchable dropdown combo box control.
 */
class ComboBox(
    options: List<SelectOption> = emptyList(),
    selectedValue: String? = options.firstOrNull()?.value,
    allowCustomInput: Boolean = true
) : Select(options = options, selectedValue = selectedValue) {
    var allowCustomInput: Boolean = allowCustomInput
        set(value) {
            field = value
            setAttribute("data-allow-custom-input", value.toString())
        }

    init {
        setAttribute("data-control-variant", "combobox")
        setAttribute("data-allow-custom-input", allowCustomInput.toString())
    }

    override fun syncStateToNative() {
        setAttribute("data-control-variant", "combobox")
        setAttribute("data-allow-custom-input", allowCustomInput.toString())
        super.syncStateToNative()
    }
}
