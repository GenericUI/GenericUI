package com.nexustools.genericui.component.dock

/**
 * Action button descriptor rendered in the header bar of a [DockPanel].
 */
data class DockHeaderAction(
    val id: String,
    val label: String,
    val iconSvgOrPath: String? = null,
    val tooltip: String? = null,
    val onClick: () -> Unit
)
