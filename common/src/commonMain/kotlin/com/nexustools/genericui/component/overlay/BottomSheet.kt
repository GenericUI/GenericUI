package com.nexustools.genericui.component.overlay

import com.nexustools.genericui.component.dialog.DialogWindow
import com.nexustools.genericui.enums.dialog.DialogPresentationStyle

/**
 * Mobile/responsive bottom sheet modal (`BottomSheetDialogFragment` on Android, `UISheetPresentationController` on iOS).
 */
class BottomSheet(
    title: String = ""
) : DialogWindow(
    title = title,
    modal = true,
    presentationStyle = DialogPresentationStyle.BOTTOM_SHEET
) {
    init {
        htmlTag = "genericui:bottomsheet"
    }
}
