package com.nexustools.genericui.event

/**
 * Cross-platform keyboard accelerator shortcut (e.g., `Ctrl+S` / `Cmd+S`, `Ctrl+Shift+Z`, `V`, `Delete`).
 */
data class KeyShortcut(
    val key: String,
    val ctrlOrCmd: Boolean = true,
    val shift: Boolean = false,
    val alt: Boolean = false,
    val displayText: String? = null
) {
    override fun toString(): String {
        if (!displayText.isNullOrBlank()) {
            return displayText
        }
        val parts = mutableListOf<String>()
        if (ctrlOrCmd) parts.add("Ctrl")
        if (shift) parts.add("Shift")
        if (alt) parts.add("Alt")
        parts.add(key.uppercase())
        return parts.joinToString("+")
    }

    companion object {
        fun parse(raw: String?): KeyShortcut? {
            val trimmed = raw?.trim().orEmpty()
            if (trimmed.isEmpty()) return null
            if (trimmed == "+") {
                return KeyShortcut(key = "+", ctrlOrCmd = false, shift = false, alt = false, displayText = "+")
            }
            val tokens = trimmed.split('+').map { it.trim() }.filter { it.isNotEmpty() }
            if (tokens.isEmpty()) return null
            var ctrl = false
            var shift = false
            var alt = false
            var keyToken = tokens.last()
            for (i in 0 until tokens.size - 1) {
                when (tokens[i].lowercase()) {
                    "ctrl", "control", "cmd", "command", "meta" -> ctrl = true
                    "shift" -> shift = true
                    "alt", "option" -> alt = true
                }
            }
            return KeyShortcut(
                key = keyToken,
                ctrlOrCmd = ctrl,
                shift = shift,
                alt = alt,
                displayText = trimmed
            )
        }
    }
}
