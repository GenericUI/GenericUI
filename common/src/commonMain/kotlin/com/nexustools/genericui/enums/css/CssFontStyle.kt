package com.nexustools.genericui.enums.css

import com.nexustools.genericui.style.css.CssValue

/**
 * Standard CSS `font-style` property values (`normal`, `italic`, `oblique`).
 */
enum class CssFontStyle(val cssValue: String) : CssValue {
    NORMAL("normal"),
    ITALIC("italic"),
    OBLIQUE("oblique");

    override fun toCssString(): String = cssValue

    companion object {
        fun fromCss(raw: String?): CssFontStyle? {
            val cleaned = raw?.trim()?.lowercase() ?: return null
            return entries.firstOrNull { it.cssValue == cleaned }
        }
    }
}
