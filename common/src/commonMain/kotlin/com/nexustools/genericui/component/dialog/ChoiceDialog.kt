package com.nexustools.genericui.component.dialog

import com.nexustools.genericui.component.shell.Window

/**
 * Declarative wrapper for displaying a native option selection dialog.
 */
class ChoiceDialog(
    var title: String,
    var message: String,
    var choices: List<String>,
    var selectedIndex: Int = 0
) {
    fun show(ownerWindow: Window? = null, onResult: (Int?) -> Unit) {
        Dialogs.choice(title, message, choices, selectedIndex, ownerWindow, onResult)
    }
}
