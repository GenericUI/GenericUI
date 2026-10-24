package com.nexustools.genericui.event

/**
 * Keyboard event payload for key down / key up listeners.
 */
data class KeyEvent(
    val key: String,
    val code: String = key,
    val keyCode: Int = 0,
    val shiftKey: Boolean = false,
    val ctrlKey: Boolean = false,
    val altKey: Boolean = false,
    val metaKey: Boolean = false,
    val isRepeat: Boolean = false
) {
    val isCommandOrCtrl: Boolean
        get() = ctrlKey || metaKey
}
