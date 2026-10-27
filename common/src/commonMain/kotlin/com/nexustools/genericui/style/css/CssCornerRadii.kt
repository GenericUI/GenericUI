package com.nexustools.genericui.style.css

/**
 * Strongly-typed 4-corner CSS `border-radius` specification (`topLeft`, `topRight`, `bottomRight`, `bottomLeft`).
 */
data class CssCornerRadii(
    val topLeft: CssLength,
    val topRight: CssLength = topLeft,
    val bottomRight: CssLength = topLeft,
    val bottomLeft: CssLength = topLeft
) : CssValue {

    val isUniform: Boolean
        get() = topLeft == topRight && topRight == bottomRight && bottomRight == bottomLeft

    override fun toCssString(): String = when {
        isUniform -> topLeft.toCssString()
        topLeft == bottomRight && topRight == bottomLeft -> "${topLeft.toCssString()} ${topRight.toCssString()}"
        topRight == bottomLeft -> "${topLeft.toCssString()} ${topRight.toCssString()} ${bottomRight.toCssString()}"
        else -> "${topLeft.toCssString()} ${topRight.toCssString()} ${bottomRight.toCssString()} ${bottomLeft.toCssString()}"
    }

    companion object {
        val ZERO = all(CssLength.ZERO)

        fun all(radius: CssLength): CssCornerRadii =
            CssCornerRadii(radius, radius, radius, radius)

        fun px(radiusPx: Number): CssCornerRadii =
            all(CssLength.px(radiusPx))

        fun px(topLeftPx: Number, topRightPx: Number, bottomRightPx: Number, bottomLeftPx: Number): CssCornerRadii =
            CssCornerRadii(
                topLeft = CssLength.px(topLeftPx),
                topRight = CssLength.px(topRightPx),
                bottomRight = CssLength.px(bottomRightPx),
                bottomLeft = CssLength.px(bottomLeftPx)
            )

        fun percent(radiusPercent: Number): CssCornerRadii =
            all(CssLength.percent(radiusPercent))

        fun parse(raw: String?): CssCornerRadii? {
            val cleaned = raw
                ?.replace(Regex("!\\s*important", RegexOption.IGNORE_CASE), "")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: return null
            val parts = cleaned.split(Regex("\\s+")).mapNotNull { CssLength.parse(it) }
            return when (parts.size) {
                1 -> all(parts[0])
                2 -> CssCornerRadii(topLeft = parts[0], topRight = parts[1], bottomRight = parts[0], bottomLeft = parts[1])
                3 -> CssCornerRadii(topLeft = parts[0], topRight = parts[1], bottomRight = parts[2], bottomLeft = parts[1])
                4 -> CssCornerRadii(topLeft = parts[0], topRight = parts[1], bottomRight = parts[2], bottomLeft = parts[3])
                else -> null
            }
        }
    }
}
