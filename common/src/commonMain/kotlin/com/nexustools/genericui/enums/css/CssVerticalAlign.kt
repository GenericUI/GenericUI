package com.nexustools.genericui.enums.css

import com.nexustools.genericui.style.css.CssValue

/**
 * Standard CSS `vertical-align` property values.
 */
enum class CssVerticalAlign(val cssValue: String) : CssValue {
    BASELINE("baseline"),
    TOP("top"),
    MIDDLE("middle"),
    BOTTOM("bottom"),
    TEXT_TOP("text-top"),
    TEXT_BOTTOM("text-bottom"),
    SUB("sub"),
    SUPER("super");

    override fun toCssString(): String = cssValue

    companion object {
        fun fromCss(raw: String?): CssVerticalAlign? {
            val cleaned = raw?.trim()?.lowercase() ?: return null
            return entries.firstOrNull { it.cssValue == cleaned }
        }
    }
}
