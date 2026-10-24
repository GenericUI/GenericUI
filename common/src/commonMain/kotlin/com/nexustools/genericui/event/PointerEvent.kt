package com.nexustools.genericui.event

/**
 * High-precision pointer event (mouse, touch, stylus/pen) with pressure, tilt, button, and modifier flags.
 */
data class PointerEvent(
    val x: Float,
    val y: Float,
    val screenX: Float = x,
    val screenY: Float = y,
    val button: Int = 0,
    val buttonsMask: Int = 0,
    val pressure: Float = 0.5f,
    val tiltX: Float = 0f,
    val tiltY: Float = 0f,
    val pointerId: Int = 0,
    val isPrimary: Boolean = true,
    val shiftKey: Boolean = false,
    val ctrlKey: Boolean = false,
    val altKey: Boolean = false,
    val metaKey: Boolean = false,
    val clickCount: Int = 1
) {
    val isCommandOrCtrl: Boolean
        get() = ctrlKey || metaKey
}
