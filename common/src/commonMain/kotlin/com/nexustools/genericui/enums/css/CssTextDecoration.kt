package com.nexustools.genericui.enums.css

import com.nexustools.genericui.style.css.CssValue

/**
 * Standard CSS `text-decoration` property values (`none`, `underline`, `overline`, `line-through`).
 */
enum class CssTextDecoration(val cssValue: String) : CssValue {
    NONE("none"),
    UNDERLINE("underline"),
    OVERLINE("overline"),
    LINE_THROUGH("line-through");

    override fun toCssString(): String = cssValue

    companion object {
        fun fromCss(raw: String?): CssTextDecoration? {
            val cleaned = raw?.trim()?.lowercase() ?: return null
            return entries.firstOrNull { it.cssValue == cleaned }
        }
    }
}
