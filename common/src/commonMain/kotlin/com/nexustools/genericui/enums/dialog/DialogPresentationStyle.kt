package com.nexustools.genericui.enums.dialog

/**
 * Presentation mode for custom dialogs (`WINDOW_MODAL`, `SHEET_PULL_DOWN`, `APPLICATION_MODAL`, `MODELESS`, `BOTTOM_SHEET`).
 * On macOS, `SHEET_PULL_DOWN` uses native `NSWindow.beginSheet`; on other platforms it renders a smooth top-of-window pull-down sheet.
 */
enum class DialogPresentationStyle {
    WINDOW_MODAL,
    SHEET_PULL_DOWN,
    APPLICATION_MODAL,
    MODELESS,
    BOTTOM_SHEET;

    companion object {
        val MODAL_CENTERED: DialogPresentationStyle get() = WINDOW_MODAL
        val MODAL_WINDOW: DialogPresentationStyle get() = WINDOW_MODAL
        val WINDOW_ATTACHED_SHEET: DialogPresentationStyle get() = SHEET_PULL_DOWN
    }
}
