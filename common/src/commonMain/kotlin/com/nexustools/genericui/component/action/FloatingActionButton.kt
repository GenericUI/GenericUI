package com.nexustools.genericui.component.action

import com.nexustools.genericui.enums.bootstrap.BootstrapSize
import com.nexustools.genericui.enums.bootstrap.BootstrapType

/**
 * Circular elevated Floating Action Button (FAB).
 */
class FloatingActionButton(
    icon: String,
    type: BootstrapType = BootstrapType.PRIMARY
) : Button(
    text = "",
    type = type,
    size = BootstrapSize.LG,
    classPrefix = "btn btn-fab"
) {
    init {
        this.icon = icon
    }
}
