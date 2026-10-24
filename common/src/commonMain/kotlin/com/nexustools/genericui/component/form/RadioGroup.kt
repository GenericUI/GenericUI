package com.nexustools.genericui.component.form

import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.nativeimpl.layout.ContainerNativeImpl

/**
 * Mutually exclusive group of [RadioButton] controls.
 */
class RadioGroup : AbstractContainer() {
    private val _buttons = mutableListOf<RadioButton>()
    val buttons: List<RadioButton> get() = _buttons

    var selectedValue: String? = null
        set(value) {
            field = value
            for (btn in _buttons) {
                btn.isChecked = (btn.optionValue == value)
            }
            if (value != null) onSelectionChanged?.invoke(value)
        }

    var onSelectionChanged: ((String) -> Unit)? = null

    fun addRadioButton(button: RadioButton): RadioGroup = apply {
        _buttons.add(button)
        addChild(button)
        if (button.isChecked) {
            selectedValue = button.optionValue
        }
        button.onCheckedChanged = { checked ->
            if (checked) {
                selectedValue = button.optionValue
            }
        }
    }

    override fun createNativeImpl(factory: NativeFactory): ContainerNativeImpl<*> =
        factory.createContainer("form-radio-group")
}
