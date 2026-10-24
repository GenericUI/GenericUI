package com.nexustools.genericui.component.form

import com.nexustools.genericui.component.dialog.Dialogs
import com.nexustools.genericui.style.RgbaColor

/**
 * Inline color picker form control (`<input type="color" class="form-control form-control-color">`).
 */
class ColorPicker(
    initialColor: RgbaColor = RgbaColor.PRIMARY_BLUE
) : TextField(value = initialColor.toHexString()) {
    var selectedColor: RgbaColor = initialColor
        set(color) {
            field = color
            value = color.toHexString()
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
