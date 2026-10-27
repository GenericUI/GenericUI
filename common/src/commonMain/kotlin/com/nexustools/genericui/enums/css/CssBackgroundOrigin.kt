package com.nexustools.genericui.enums.css

import com.nexustools.genericui.style.css.CssValue

/**
 * Standard CSS `background-origin` property values (`padding-box`, `border-box`, `content-box`).
 */
enum class CssBackgroundOrigin(val cssValue: String) : CssValue {
    PADDING_BOX("padding-box"),
    BORDER_BOX("border-box"),
    CONTENT_BOX("content-box");

    override fun toCssString(): String = cssValue

    companion object {
        fun fromCss(raw: String?): CssBackgroundOrigin? {
            val cleaned = raw?.trim()?.lowercase() ?: return null
            return entries.firstOrNull { it.cssValue == cleaned }
        }
    }
}
