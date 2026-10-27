package com.nexustools.genericui.enums.css

import com.nexustools.genericui.style.css.CssValue

/**
 * Standard CSS `user-select` property values (`auto`, `none`, `text`, `all`).
 */
enum class CssUserSelect(val cssValue: String) : CssValue {
    AUTO("auto"),
    NONE("none"),
    TEXT("text"),
    ALL("all");

    override fun toCssString(): String = cssValue

    companion object {
        fun fromCss(raw: String?): CssUserSelect? {
            val cleaned = raw?.trim()?.lowercase() ?: return null
            return entries.firstOrNull { it.cssValue == cleaned }
        }
    }
}
