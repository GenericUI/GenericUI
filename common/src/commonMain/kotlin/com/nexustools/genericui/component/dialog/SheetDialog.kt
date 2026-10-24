package com.nexustools.genericui.component.dialog

import com.nexustools.genericui.enums.dialog.DialogPresentationStyle

/**
 * Window-attached sheet dialog (native `NSWindow.beginSheet` on macOS, bottom/top sheet on mobile/web).
 */
class SheetDialog(
    title: String = ""
) : DialogWindow(
    title = title,
    modal = true,
    presentationStyle = DialogPresentationStyle.WINDOW_ATTACHED_SHEET
)
