package com.nexustools.genericui.enums.css

import com.nexustools.genericui.style.css.CssValue

/**
 * Standard CSS `background-size` keyword values (`auto`, `cover`, `contain`).
 */
enum class CssBackgroundSizeKeyword(val cssValue: String) : CssValue {
    AUTO("auto"),
    COVER("cover"),
    CONTAIN("contain");

    override fun toCssString(): String = cssValue

    companion object {
        fun fromCss(raw: String?): CssBackgroundSizeKeyword? {
            val cleaned = raw?.trim()?.lowercase() ?: return null
            return entries.firstOrNull { it.cssValue == cleaned }
        }
    }
}
