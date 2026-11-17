package com.nexustools.genericui.component.form

/**
 * Masked password input field (`<input type="password">`).
 */
class PasswordField(
    value: String = "",
    placeholder: String = "Password",
    label: String? = null
) : TextField(
    value = value,
    placeholder = placeholder,
    multilineMode = false,
    passwordMode = true,
    label = label
)
