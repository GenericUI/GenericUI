package com.nexustools.genericui.component.dialog

import com.nexustools.genericui.component.shell.Window
import com.nexustools.genericui.style.RgbaColor

/**
 * Declarative wrapper for displaying a native color chooser dialog.
 */
class ColorChooserDialog(
    var title: String = "Select Color",
    var initialColor: RgbaColor = RgbaColor.WHITE,
    var allowAlpha: Boolean = true
) {
    fun show(ownerWindow: Window? = null, onResult: (RgbaColor?) -> Unit) {
        Dialogs.chooseColor(title, initialColor, allowAlpha, ownerWindow, onResult)
    }
}
