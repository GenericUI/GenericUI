package com.nexustools.genericui.enums.css

import com.nexustools.genericui.style.css.CssValue

/**
 * Standard CSS `word-break` property values (`normal`, `break-all`, `keep-all`, `break-word`).
 */
enum class CssWordBreak(val cssValue: String) : CssValue {
    NORMAL("normal"),
    BREAK_ALL("break-all"),
    KEEP_ALL("keep-all"),
    BREAK_WORD("break-word");

    override fun toCssString(): String = cssValue

    companion object {
        fun fromCss(raw: String?): CssWordBreak? {
            val cleaned = raw?.trim()?.lowercase() ?: return null
            return entries.firstOrNull { it.cssValue == cleaned }
        }
    }
}
