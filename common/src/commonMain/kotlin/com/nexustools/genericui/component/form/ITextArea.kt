package com.nexustools.genericui.component.form

/**
 * Common multi-line text area contract extending [ITextField], implemented by both standard system
 * [TextArea] and explicitly styled [com.nexustools.genericui.component.form.styled.StyledTextArea].
 */
interface ITextArea : ITextField {
    var rows: Int
}
