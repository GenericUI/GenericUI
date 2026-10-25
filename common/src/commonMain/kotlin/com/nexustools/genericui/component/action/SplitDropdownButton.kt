package com.nexustools.genericui.component.action

import com.nexustools.genericui.enums.bootstrap.BootstrapType

/**
 * Split action button with a primary action click handler and a secondary dropdown caret menu.
 */
class SplitDropdownButton(
    label: String = "",
    type: BootstrapType = BootstrapType.PRIMARY,
    var onPrimaryAction: (() -> Unit)? = null
) : DropdownButton(label = label, type = type) {
    init {
        htmlTag = "button"
    }
}
