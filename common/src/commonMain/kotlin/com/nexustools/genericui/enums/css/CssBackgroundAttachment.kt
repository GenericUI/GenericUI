package com.nexustools.genericui.enums.css

import com.nexustools.genericui.style.css.CssValue

/**
 * Standard CSS `background-attachment` property values (`scroll`, `fixed`, `local`).
 */
enum class CssBackgroundAttachment(val cssValue: String) : CssValue {
    SCROLL("scroll"),
    FIXED("fixed"),
    LOCAL("local");

    override fun toCssString(): String = cssValue

    companion object {
        fun fromCss(raw: String?): CssBackgroundAttachment? {
            val cleaned = raw?.trim()?.lowercase() ?: return null
            return entries.firstOrNull { it.cssValue == cleaned }
        }
    }
}
