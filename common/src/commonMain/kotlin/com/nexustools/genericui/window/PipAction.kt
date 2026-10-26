package com.nexustools.genericui.window

/**
 * Remote transport action button displayed inside a Picture-in-Picture (PiP) window
 * (e.g., Play/Pause, Skip Next).
 */
data class PipAction(
    val id: String,
    val title: String,
    val iconName: String? = null,
    val onAction: () -> Unit
)
