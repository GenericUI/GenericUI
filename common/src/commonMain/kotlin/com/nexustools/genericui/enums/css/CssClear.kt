package com.nexustools.genericui.enums.css

import com.nexustools.genericui.style.css.CssValue

/**
 * Standard CSS `clear` property values (`none`, `left`, `right`, `both`, `inline-start`, `inline-end`).
 */
enum class CssClear(val cssValue: String) : CssValue {
    NONE("none"),
    LEFT("left"),
    RIGHT("right"),
    BOTH("both"),
    INLINE_START("inline-start"),
    INLINE_END("inline-end");

    override fun toCssString(): String = cssValue

    companion object {
        fun fromCss(raw: String?): CssClear? {
            val cleaned = raw?.trim()?.lowercase() ?: return null
            return entries.firstOrNull { it.cssValue == cleaned }
        }
    }
}
