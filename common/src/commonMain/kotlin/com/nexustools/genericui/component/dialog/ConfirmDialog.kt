package com.nexustools.genericui.component.dialog

import com.nexustools.genericui.component.shell.Window

/**
 * Declarative wrapper for displaying a native confirmation dialog.
 */
class ConfirmDialog(
    var title: String,
    var message: String,
    var confirmLabel: String = "OK",
    var cancelLabel: String = "Cancel"
) {
    fun show(ownerWindow: Window? = null, onResult: (Boolean) -> Unit) {
        Dialogs.confirm(title, message, confirmLabel, cancelLabel, ownerWindow, onResult)
    }
}
