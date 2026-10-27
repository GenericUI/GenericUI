package com.nexustools.genericui.enums.css

import com.nexustools.genericui.style.css.CssValue

/**
 * Standard CSS `align-content` property values.
 */
enum class CssAlignContent(val cssValue: String) : CssValue {
    STRETCH("stretch"),
    FLEX_START("flex-start"),
    FLEX_END("flex-end"),
    CENTER("center"),
    SPACE_BETWEEN("space-between"),
    SPACE_AROUND("space-around"),
    SPACE_EVENLY("space-evenly");

    override fun toCssString(): String = cssValue

    companion object {
        fun fromCss(raw: String?): CssAlignContent? {
            val cleaned = raw?.trim()?.lowercase() ?: return null
            if (cleaned == "start") return FLEX_START
            if (cleaned == "end") return FLEX_END
            return entries.firstOrNull { it.cssValue == cleaned }
        }
    }
}
