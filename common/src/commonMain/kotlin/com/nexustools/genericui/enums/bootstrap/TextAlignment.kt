package com.nexustools.genericui.enums.bootstrap

/**
 * Bootstrap 5 text alignment utilities (`text-start`, `text-center`, `text-end`, `text-justify`).
 */
enum class TextAlignment(val cssClass: String) {
    START("text-start"),
    CENTER("text-center"),
    END("text-end"),
    JUSTIFY("text-justify");

    companion object {
        fun fromCssClass(cssClass: String): TextAlignment? {
            val normalized = cssClass.trim().lowercase()
            return when (normalized) {
                "text-left" -> START
                "text-right" -> END
                else -> entries.firstOrNull { it.cssClass.equals(normalized, ignoreCase = true) }
            }
        }
    }
}
