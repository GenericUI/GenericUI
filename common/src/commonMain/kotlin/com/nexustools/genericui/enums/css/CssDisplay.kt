package com.nexustools.genericui.enums.css

import com.nexustools.genericui.style.css.CssValue

/**
 * Standard CSS `display` property values.
 */
enum class CssDisplay(val cssValue: String) : CssValue {
    NONE("none"),
    BLOCK("block"),
    INLINE("inline"),
    INLINE_BLOCK("inline-block"),
    FLEX("flex"),
    INLINE_FLEX("inline-flex"),
    GRID("grid"),
    INLINE_GRID("inline-grid"),
    CONTENTS("contents"),
    TABLE("table"),
    TABLE_ROW("table-row"),
    TABLE_CELL("table-cell");

    override fun toCssString(): String = cssValue

    companion object {
        fun fromCss(raw: String?): CssDisplay? {
            val cleaned = raw?.trim()?.lowercase() ?: return null
            return entries.firstOrNull { it.cssValue == cleaned }
        }
    }
}
