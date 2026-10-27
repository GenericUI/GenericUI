package com.nexustools.genericui.enums.css

import com.nexustools.genericui.style.css.CssValue

/**
 * Standard CSS `background-repeat` property values.
 */
enum class CssBackgroundRepeat(val cssValue: String) : CssValue {
    REPEAT("repeat"),
    NO_REPEAT("no-repeat"),
    REPEAT_X("repeat-x"),
    REPEAT_Y("repeat-y"),
    SPACE("space"),
    ROUND("round");

    override fun toCssString(): String = cssValue

    companion object {
        fun fromCss(raw: String?): CssBackgroundRepeat? {
            val cleaned = raw?.trim()?.lowercase() ?: return null
            return entries.firstOrNull { it.cssValue == cleaned }
        }
    }
}
