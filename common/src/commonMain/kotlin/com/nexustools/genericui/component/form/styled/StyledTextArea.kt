package com.nexustools.genericui.component.form.styled

import com.nexustools.genericui.component.form.ITextArea

/**
 * Cross-platform styled multi-line text input area (`<textarea class="styled-textarea">`)
 * extending [StyledTextField] with [rows] control and full visual/state styling across all 9 backends.
 */
open class StyledTextArea(
    value: String = "",
    placeholder: String = "",
    rows: Int = 4,
    label: String? = null,
    styledClassPrefix: String = DEFAULT_CLASS_PREFIX
) : StyledTextField(
    value = value,
    placeholder = placeholder,
    multilineMode = true,
    passwordMode = false,
    label = label,
    styledClassPrefix = styledClassPrefix
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

    companion object {
        const val DEFAULT_CLASS_PREFIX: String = "styled-textarea"
    }
}
