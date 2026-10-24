package com.nexustools.genericui.event

/**
 * Scroll wheel / trackpad pinch-zoom event.
 */
data class WheelEvent(
    val x: Float,
    val y: Float,
    val deltaX: Float,
    val deltaY: Float,
    val deltaZ: Float = 0f,
    val shiftKey: Boolean = false,
    val ctrlKey: Boolean = false,
    val altKey: Boolean = false,
    val metaKey: Boolean = false
)
