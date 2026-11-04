package com.nexustools.genericui.component.dialog

import com.nexustools.genericui.enums.bootstrap.BootstrapSize
import com.nexustools.genericui.enums.bootstrap.BootstrapType

/**
 * Specification for a single selectable action button inside an [OptionsDialog].
 */
data class OptionDialogItem(
    val text: String,
    val type: BootstrapType = BootstrapType.SECONDARY,
    val outline: Boolean = true,
    val size: BootstrapSize = BootstrapSize.SM,
    val closeOnClick: Boolean = true,
    val onClick: () -> Unit
)
