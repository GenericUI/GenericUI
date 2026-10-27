package com.nexustools.genericui.enums.css

import com.nexustools.genericui.style.css.CssValue

/**
 * Standard CSS `visibility` property values (`visible`, `hidden`, `collapse`).
 */
enum class CssVisibility(val cssValue: String) : CssValue {
    VISIBLE("visible"),
    HIDDEN("hidden"),
    COLLAPSE("collapse");

    override fun toCssString(): String = cssValue

    companion object {
        fun fromCss(raw: String?): CssVisibility? {
            val cleaned = raw?.trim()?.lowercase() ?: return null
            return entries.firstOrNull { it.cssValue == cleaned }
        }
    }
}
