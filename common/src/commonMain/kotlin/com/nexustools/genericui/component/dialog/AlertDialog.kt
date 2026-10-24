package com.nexustools.genericui.component.dialog

import com.nexustools.genericui.component.shell.Window
import com.nexustools.genericui.enums.bootstrap.BootstrapType

/**
 * Declarative wrapper for displaying a native alert dialog.
 */
class AlertDialog(
    var title: String,
    var message: String,
    var type: BootstrapType = BootstrapType.INFO
) {
    fun show(ownerWindow: Window? = null, onDismiss: () -> Unit = {}) {
        Dialogs.alert(title, message, type, ownerWindow, onDismiss)
    }
}
