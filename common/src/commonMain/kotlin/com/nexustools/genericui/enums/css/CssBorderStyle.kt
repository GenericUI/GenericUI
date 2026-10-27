package com.nexustools.genericui.enums.css

import com.nexustools.genericui.style.css.CssValue

/**
 * Standard CSS `border-style` property values.
 */
enum class CssBorderStyle(val cssValue: String) : CssValue {
    NONE("none"),
    SOLID("solid"),
    DASHED("dashed"),
    DOTTED("dotted"),
    DOUBLE("double"),
    GROOVE("groove"),
    RIDGE("ridge"),
    INSET("inset"),
    OUTSET("outset"),
    HIDDEN("hidden");

    override fun toCssString(): String = cssValue

    companion object {
        fun fromCss(raw: String?): CssBorderStyle? {
            val cleaned = raw?.trim()?.lowercase() ?: return null
            return entries.firstOrNull { it.cssValue == cleaned }
        }
    }
}
