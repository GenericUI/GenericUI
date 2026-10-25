package com.nexustools.genericui.component.action

import com.nexustools.genericui.enums.bootstrap.BootstrapSize
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.res.ResourceId

/**
 * Compact icon-only action button supporting both string icon identifiers and typed `R.drawable.*` / `RC.drawable.*` [ResourceId]s.
 */
class IconButton(
    icon: String,
    tooltipText: String? = null,
    type: BootstrapType = BootstrapType.SECONDARY,
    size: BootstrapSize = BootstrapSize.SM
) : Button(
    text = "",
    icon = icon,
    type = type,
    size = size,
    outline = true,
    classPrefix = "btn btn-icon"
) {
    constructor(
        iconRes: ResourceId,
        tooltipText: String? = null,
        type: BootstrapType = BootstrapType.SECONDARY,
        size: BootstrapSize = BootstrapSize.SM
    ) : this(
        icon = iconRes.toResourceUri(),
        tooltipText = tooltipText,
        type = type,
        size = size
    ) {
        this.iconRes = iconRes
    }

    init {
        this.icon = icon
        this.tooltipText = tooltipText
    }
}
