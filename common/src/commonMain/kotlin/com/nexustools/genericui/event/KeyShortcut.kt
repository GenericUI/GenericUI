package com.nexustools.genericui.event

/**
 * Cross-platform keyboard accelerator shortcut (e.g., `Ctrl+S` / `Cmd+S`, `Ctrl+Shift+Z`).
 */
data class KeyShortcut(
    val key: String,
    val ctrlOrCmd: Boolean = true,
    val shift: Boolean = false,
    val alt: Boolean = false
) {
    override fun toString(): String {
        val parts = mutableListOf<String>()
        if (ctrlOrCmd) parts.add("Ctrl")
        if (shift) parts.add("Shift")
        if (alt) parts.add("Alt")
        parts.add(key.uppercase())
        return parts.joinToString("+")
    }
}
