package com.nexustools.genericui.component.form

/**
 * Multi-line text input area (`<textarea class="form-control">`).
 */
open class TextArea(
    value: String = "",
    placeholder: String = "",
    rows: Int = 4,
    label: String? = null
) : TextField(
    value = value,
    placeholder = placeholder,
    multilineMode = true,
    passwordMode = false,
    label = label
), ITextArea {
    override var rows: Int = rows.coerceAtLeast(1)
        set(value) {
            val coerced = value.coerceAtLeast(1)
            if (field == coerced) return
            field = coerced
            setAttribute("rows", coerced.toString())
        }

    init {
        htmlTag = "textarea"
        ignoreKeyEventsWhenTypingInTextInput = false
        setAttribute("rows", this.rows.toString())
    }

    override val isTextInputComponent: Boolean
        get() = true

    override fun syncStateToNative() {
        setAttribute("rows", rows.toString())
        super.syncStateToNative()
    }
}
