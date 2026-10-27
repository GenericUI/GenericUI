package com.nexustools.genericui.enums.css

import com.nexustools.genericui.style.css.CssValue

/**
 * Standard CSS `pointer-events` property values (`auto`, `none`).
 */
enum class CssPointerEvents(val cssValue: String) : CssValue {
    AUTO("auto"),
    NONE("none");

    val isEnabled: Boolean
        get() = this != NONE

    override fun toCssString(): String = cssValue

    companion object {
        fun fromCss(raw: String?): CssPointerEvents? {
            val cleaned = raw?.trim()?.lowercase() ?: return null
            return entries.firstOrNull { it.cssValue == cleaned }
        }
    }
}
