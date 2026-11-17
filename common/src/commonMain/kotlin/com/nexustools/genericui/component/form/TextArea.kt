package com.nexustools.genericui.component.form

/**
 * Multi-line text input area (`<textarea class="form-control">`).
 */
class TextArea(
    value: String = "",
    placeholder: String = "",
    var rows: Int = 4,
    label: String? = null
) : TextField(
    value = value,
    placeholder = placeholder,
    multilineMode = true,
    passwordMode = false,
    label = label
) {
    init {
        htmlTag = "textarea"
        ignoreKeyEventsWhenTypingInTextInput = false
    }

    override val isTextInputComponent: Boolean
        get() = true
}
