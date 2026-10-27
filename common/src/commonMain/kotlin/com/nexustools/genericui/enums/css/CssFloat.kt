package com.nexustools.genericui.enums.css

import com.nexustools.genericui.style.css.CssValue

/**
 * Standard CSS `float` property values (`none`, `left`, `right`, `inline-start`, `inline-end`).
 */
enum class CssFloat(val cssValue: String) : CssValue {
    NONE("none"),
    LEFT("left"),
    RIGHT("right"),
    INLINE_START("inline-start"),
    INLINE_END("inline-end");

    override fun toCssString(): String = cssValue

    companion object {
        fun fromCss(raw: String?): CssFloat? {
            val cleaned = raw?.trim()?.lowercase() ?: return null
            return entries.firstOrNull { it.cssValue == cleaned }
        }
    }
}
