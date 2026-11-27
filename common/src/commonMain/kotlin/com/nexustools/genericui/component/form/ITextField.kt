package com.nexustools.genericui.component.form

import com.nexustools.genericui.component.typography.ILabelTarget

/**
 * Common single-line or multi-line text input contract implemented by both standard system
 * [TextField] and explicitly styled [com.nexustools.genericui.component.form.styled.StyledTextField].
 */
interface ITextField : ILabelTarget {
    var value: String
    var placeholder: String
    var label: String?
    var isReadOnly: Boolean
    var isEnabled: Boolean
    var onValueChanged: ((String) -> Unit)?
    var onSubmit: ((String) -> Unit)?
}
