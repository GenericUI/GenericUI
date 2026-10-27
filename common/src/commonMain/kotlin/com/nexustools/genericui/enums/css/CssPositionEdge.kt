package com.nexustools.genericui.enums.css

import com.nexustools.genericui.style.css.CssValue

/**
 * Standard CSS position edge keywords (`left`, `center`, `right`, `top`, `bottom`).
 */
enum class CssPositionEdge(val cssValue: String) : CssValue {
    LEFT("left"),
    CENTER("center"),
    RIGHT("right"),
    TOP("top"),
    BOTTOM("bottom");

    override fun toCssString(): String = cssValue

    companion object {
        fun fromCss(raw: String?): CssPositionEdge? {
            val cleaned = raw?.trim()?.lowercase() ?: return null
            return entries.firstOrNull { it.cssValue == cleaned }
        }
    }
}
