package com.nexustools.genericui.enums.css

import com.nexustools.genericui.style.css.CssValue

/**
 * Standard CSS `background-clip` property values (`border-box`, `padding-box`, `content-box`, `text`).
 */
enum class CssBackgroundClip(val cssValue: String) : CssValue {
    BORDER_BOX("border-box"),
    PADDING_BOX("padding-box"),
    CONTENT_BOX("content-box"),
    TEXT("text");

    override fun toCssString(): String = cssValue

    companion object {
        fun fromCss(raw: String?): CssBackgroundClip? {
            val cleaned = raw?.trim()?.lowercase() ?: return null
            return entries.firstOrNull { it.cssValue == cleaned }
        }
    }
}
