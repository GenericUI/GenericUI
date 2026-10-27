package com.nexustools.genericui.enums.css

import com.nexustools.genericui.style.css.CssValue

/**
 * Standard CSS `box-sizing` property values (`content-box`, `border-box`).
 */
enum class CssBoxSizing(val cssValue: String) : CssValue {
    CONTENT_BOX("content-box"),
    BORDER_BOX("border-box");

    override fun toCssString(): String = cssValue

    companion object {
        fun fromCss(raw: String?): CssBoxSizing? {
            val cleaned = raw?.trim()?.lowercase() ?: return null
            return entries.firstOrNull { it.cssValue == cleaned }
        }
    }
}
