package com.nexustools.genericui.component.dialog

import com.nexustools.genericui.component.shell.Window

/**
 * Declarative wrapper for displaying a native single-line text prompt dialog.
 */
class PromptDialog(
    var title: String,
    var message: String,
    var defaultValue: String = "",
    var placeholder: String = ""
) {
    fun show(ownerWindow: Window? = null, onResult: (String?) -> Unit) {
        Dialogs.prompt(title, message, defaultValue, placeholder, ownerWindow, onResult)
    }
}
