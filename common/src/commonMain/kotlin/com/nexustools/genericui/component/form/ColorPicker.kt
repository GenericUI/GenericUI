package com.nexustools.genericui.component.form

import com.nexustools.genericui.component.dialog.Dialogs
import com.nexustools.genericui.style.color.RgbaColor

/**
 * Inline color picker form control (`<input type="color" class="form-control form-control-color">`).
 */
class ColorPicker(
    initialColor: RgbaColor = RgbaColor.PRIMARY_BLUE
) : TextField(value = initialColor.toHex()) {
    var selectedColor: RgbaColor = initialColor
        set(color) {
            field = color
            value = color.toHex()
            onColorChanged?.invoke(color)
        }

    var onColorChanged: ((RgbaColor) -> Unit)? = null

    fun openNativeColorDialog() {
        Dialogs.chooseColor(initialColor = selectedColor) { chosen ->
            if (chosen != null) {
                selectedColor = chosen
            }
        }
    }
}
