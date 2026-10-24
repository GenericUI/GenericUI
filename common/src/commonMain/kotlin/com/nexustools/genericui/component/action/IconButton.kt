package com.nexustools.genericui.component.action

import com.nexustools.genericui.enums.bootstrap.BootstrapSize
import com.nexustools.genericui.enums.bootstrap.BootstrapType

/**
 * Compact icon-only action button.
 */
class IconButton(
    icon: String,
    tooltipText: String? = null,
    type: BootstrapType = BootstrapType.SECONDARY,
    size: BootstrapSize = BootstrapSize.SM
) : Button(
    text = "",
    type = type,
    size = size,
    outline = true,
    classPrefix = "btn btn-icon"
) {
    init {
        this.icon = icon
        this.tooltipText = tooltipText
    }
}
