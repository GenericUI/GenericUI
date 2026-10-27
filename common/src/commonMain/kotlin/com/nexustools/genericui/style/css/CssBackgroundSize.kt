package com.nexustools.genericui.style.css

import com.nexustools.genericui.enums.css.CssBackgroundSizeKeyword

/**
 * Strongly-typed CSS `background-size` value (`auto`, `cover`, `contain`, or explicit `[width] [height]`).
 */
data class CssBackgroundSize(
    val keyword: CssBackgroundSizeKeyword? = null,
    val width: CssLength? = null,
    val height: CssLength? = null
) : CssValue {

    override fun toCssString(): String {
        keyword?.let { return it.toCssString() }
        val w = width?.toCssString() ?: "auto"
        val h = height?.toCssString()
        return if (h != null) "$w $h" else w
    }

    companion object {
        val AUTO = CssBackgroundSize(keyword = CssBackgroundSizeKeyword.AUTO)
        val COVER = CssBackgroundSize(keyword = CssBackgroundSizeKeyword.COVER)
        val CONTAIN = CssBackgroundSize(keyword = CssBackgroundSizeKeyword.CONTAIN)

        fun of(width: CssLength, height: CssLength? = null): CssBackgroundSize =
            CssBackgroundSize(width = width, height = height)

        fun px(widthPx: Number, heightPx: Number = widthPx): CssBackgroundSize =
            CssBackgroundSize(width = CssLength.px(widthPx), height = CssLength.px(heightPx))

        fun parse(raw: String?): CssBackgroundSize? {
            val cleaned = raw
                ?.replace(Regex("!\\s*important", RegexOption.IGNORE_CASE), "")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: return null

            CssBackgroundSizeKeyword.fromCss(cleaned)?.let { return CssBackgroundSize(keyword = it) }

            val parts = cleaned.split(Regex("\\s+")).filter { it.isNotEmpty() }
            val w = parts.getOrNull(0)?.let { CssLength.parse(it) } ?: return null
            val h = parts.getOrNull(1)?.let { CssLength.parse(it) }
            return CssBackgroundSize(width = w, height = h)
        }
    }
}
