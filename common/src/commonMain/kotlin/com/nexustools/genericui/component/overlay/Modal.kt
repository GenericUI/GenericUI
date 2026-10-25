package com.nexustools.genericui.component.overlay

import com.nexustools.genericui.component.dialog.DialogWindow
import com.nexustools.genericui.enums.dialog.DialogPresentationStyle

/**
 * Centered modal overlay dialog (`<div class="modal">`).
 */
open class Modal(
    title: String = ""
) : DialogWindow(
    title = title,
    modal = true,
    presentationStyle = DialogPresentationStyle.MODAL_CENTERED
) {
    init {
        htmlTag = "genericui:modal"
    }
}
