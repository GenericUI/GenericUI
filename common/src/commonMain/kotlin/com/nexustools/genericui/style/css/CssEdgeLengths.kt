package com.nexustools.genericui.style.css

import com.nexustools.genericui.geometry.EdgeInsets
import kotlin.math.roundToInt

/**
 * Strongly-typed 4-edge CSS box length value (`margin`, `padding`, `border-width`).
 */
data class CssEdgeLengths(
    val top: CssLength,
    val right: CssLength = top,
    val bottom: CssLength = top,
    val left: CssLength = right
) : CssValue {

    fun toEdgeInsets(
        referenceWidthPx: Float = 0f,
        referenceHeightPx: Float = 0f,
        rootFontSizePx: Float = 16f
    ): EdgeInsets = EdgeInsets(
        top = (top.resolvePx(referenceHeightPx, rootFontSizePx) ?: 0f).roundToInt(),
        right = (right.resolvePx(referenceWidthPx, rootFontSizePx) ?: 0f).roundToInt(),
        bottom = (bottom.resolvePx(referenceHeightPx, rootFontSizePx) ?: 0f).roundToInt(),
        left = (left.resolvePx(referenceWidthPx, rootFontSizePx) ?: 0f).roundToInt()
    )

    override fun toCssString(): String = when {
        top == right && top == bottom && top == left -> top.toCssString()
        top == bottom && left == right -> "${top.toCssString()} ${right.toCssString()}"
        left == right -> "${top.toCssString()} ${right.toCssString()} ${bottom.toCssString()}"
        else -> "${top.toCssString()} ${right.toCssString()} ${bottom.toCssString()} ${left.toCssString()}"
    }

    companion object {
        val ZERO = all(CssLength.ZERO)

        fun all(length: CssLength): CssEdgeLengths =
            CssEdgeLengths(length, length, length, length)

        fun px(allPx: Number): CssEdgeLengths =
            all(CssLength.px(allPx))

        fun px(verticalPx: Number, horizontalPx: Number): CssEdgeLengths =
            symmetric(CssLength.px(verticalPx), CssLength.px(horizontalPx))

        fun px(topPx: Number, rightPx: Number, bottomPx: Number, leftPx: Number): CssEdgeLengths =
            CssEdgeLengths(
                top = CssLength.px(topPx),
                right = CssLength.px(rightPx),
                bottom = CssLength.px(bottomPx),
                left = CssLength.px(leftPx)
            )

        fun symmetric(vertical: CssLength, horizontal: CssLength): CssEdgeLengths =
            CssEdgeLengths(top = vertical, right = horizontal, bottom = vertical, left = horizontal)

        fun fromEdgeInsets(insets: EdgeInsets): CssEdgeLengths =
            px(insets.top, insets.right, insets.bottom, insets.left)

        fun parse(raw: String?): CssEdgeLengths? {
            val cleaned = raw
                ?.replace(Regex("!\\s*important", RegexOption.IGNORE_CASE), "")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: return null
            val parts = cleaned.split(Regex("\\s+")).mapNotNull { CssLength.parse(it) }
            return when (parts.size) {
                1 -> all(parts[0])
                2 -> symmetric(parts[0], parts[1])
                3 -> CssEdgeLengths(top = parts[0], right = parts[1], bottom = parts[2], left = parts[1])
                4 -> CssEdgeLengths(top = parts[0], right = parts[1], bottom = parts[2], left = parts[3])
                else -> null
            }
        }
    }
}
