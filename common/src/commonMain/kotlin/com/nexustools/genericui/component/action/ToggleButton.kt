package com.nexustools.genericui.component.action

import com.nexustools.genericui.enums.bootstrap.BootstrapSize
import com.nexustools.genericui.enums.bootstrap.BootstrapType

/**
 * Two-state toggle button (`isSelected`).
 */
class ToggleButton(
    text: String = "",
    selected: Boolean = false,
    type: BootstrapType = BootstrapType.PRIMARY,
    size: BootstrapSize = BootstrapSize.MD
) : Button(text = text, type = type, size = size, outline = !selected) {
    var isSelected: Boolean = selected
        set(value) {
            field = value
            this.outline = !value
            onToggleChanged?.invoke(value)
        }

    var onToggleChanged: ((Boolean) -> Unit)? = null

    init {
        setOnClick {
            isSelected = !isSelected
        }
    }
}
