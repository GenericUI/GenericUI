package com.nexustools.genericui.enums.css

import com.nexustools.genericui.style.css.CssValue

/**
 * Standard CSS `position` property values (`static`, `relative`, `absolute`, `fixed`, `sticky`).
 */
enum class CssPosition(val cssValue: String) : CssValue {
    STATIC("static"),
    RELATIVE("relative"),
    ABSOLUTE("absolute"),
    FIXED("fixed"),
    STICKY("sticky");

    override fun toCssString(): String = cssValue

    companion object {
        fun fromCss(raw: String?): CssPosition? {
            val cleaned = raw?.trim()?.lowercase() ?: return null
            return entries.firstOrNull { it.cssValue == cleaned }
        }
    }
}
