package com.nexustools.genericui.enums.css

import com.nexustools.genericui.style.css.CssValue

/**
 * Standard CSS `white-space` property values (`normal`, `nowrap`, `pre`, `pre-wrap`, `pre-line`, `break-spaces`).
 */
enum class CssWhiteSpace(val cssValue: String) : CssValue {
    NORMAL("normal"),
    NOWRAP("nowrap"),
    PRE("pre"),
    PRE_WRAP("pre-wrap"),
    PRE_LINE("pre-line"),
    BREAK_SPACES("break-spaces");

    override fun toCssString(): String = cssValue

    companion object {
        fun fromCss(raw: String?): CssWhiteSpace? {
            val cleaned = raw?.trim()?.lowercase() ?: return null
            return entries.firstOrNull { it.cssValue == cleaned }
        }
    }
}
