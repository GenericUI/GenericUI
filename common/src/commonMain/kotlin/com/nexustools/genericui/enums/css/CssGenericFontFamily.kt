package com.nexustools.genericui.enums.css

import com.nexustools.genericui.style.css.CssValue

/**
 * Standard CSS generic `font-family` keywords (`sans-serif`, `serif`, `monospace`, `system-ui`, `cursive`, `fantasy`).
 */
enum class CssGenericFontFamily(val cssValue: String) : CssValue {
    SANS_SERIF("sans-serif"),
    SERIF("serif"),
    MONOSPACE("monospace"),
    SYSTEM_UI("system-ui"),
    CURSIVE("cursive"),
    FANTASY("fantasy");

    override fun toCssString(): String = cssValue

    companion object {
        fun fromCss(raw: String?): CssGenericFontFamily? {
            val cleaned = raw?.trim()?.removeSurrounding("\"")?.removeSurrounding("'")?.lowercase() ?: return null
            return entries.firstOrNull { it.cssValue == cleaned }
        }
    }
}
