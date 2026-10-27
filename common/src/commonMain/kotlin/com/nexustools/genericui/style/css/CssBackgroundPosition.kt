package com.nexustools.genericui.style.css

import com.nexustools.genericui.enums.css.CssPositionEdge

/**
 * Strongly-typed CSS `background-position` value (`horizontalEdge [offsetX] verticalEdge [offsetY]`).
 */
data class CssBackgroundPosition(
    val horizontalEdge: CssPositionEdge = CssPositionEdge.CENTER,
    val verticalEdge: CssPositionEdge = CssPositionEdge.CENTER,
    val offsetX: CssLength? = null,
    val offsetY: CssLength? = null
) : CssValue {

    override fun toCssString(): String {
        val hPart = if (offsetX != null) "${horizontalEdge.toCssString()} ${offsetX.toCssString()}" else horizontalEdge.toCssString()
        val vPart = if (offsetY != null) "${verticalEdge.toCssString()} ${offsetY.toCssString()}" else verticalEdge.toCssString()
        return "$hPart $vPart"
    }

    companion object {
        val CENTER = CssBackgroundPosition(CssPositionEdge.CENTER, CssPositionEdge.CENTER)
        val TOP_LEFT = CssBackgroundPosition(CssPositionEdge.LEFT, CssPositionEdge.TOP)
        val TOP_CENTER = CssBackgroundPosition(CssPositionEdge.CENTER, CssPositionEdge.TOP)
        val TOP_RIGHT = CssBackgroundPosition(CssPositionEdge.RIGHT, CssPositionEdge.TOP)
        val BOTTOM_LEFT = CssBackgroundPosition(CssPositionEdge.LEFT, CssPositionEdge.BOTTOM)
        val BOTTOM_CENTER = CssBackgroundPosition(CssPositionEdge.CENTER, CssPositionEdge.BOTTOM)
        val BOTTOM_RIGHT = CssBackgroundPosition(CssPositionEdge.RIGHT, CssPositionEdge.BOTTOM)

        fun of(offsetX: CssLength, offsetY: CssLength = CssLength.ZERO): CssBackgroundPosition =
            CssBackgroundPosition(CssPositionEdge.LEFT, CssPositionEdge.TOP, offsetX, offsetY)

        fun parse(raw: String?): CssBackgroundPosition? {
            val cleaned = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val tokens = cleaned.split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (tokens.isEmpty()) return null
            if (tokens.size == 1) {
                val edge = CssPositionEdge.fromCss(tokens[0])
                if (edge != null) {
                    return when (edge) {
                        CssPositionEdge.TOP, CssPositionEdge.BOTTOM -> CssBackgroundPosition(CssPositionEdge.CENTER, edge)
                        else -> CssBackgroundPosition(edge, CssPositionEdge.CENTER)
                    }
                }
                val len = CssLength.parse(tokens[0]) ?: return null
                return of(len, CssLength.percent(50f))
            }
            if (tokens.size == 2) {
                val firstEdge = CssPositionEdge.fromCss(tokens[0])
                val secondEdge = CssPositionEdge.fromCss(tokens[1])
                if (firstEdge != null && secondEdge != null) {
                    return if (firstEdge == CssPositionEdge.TOP || firstEdge == CssPositionEdge.BOTTOM) {
                        CssBackgroundPosition(secondEdge, firstEdge)
                    } else {
                        CssBackgroundPosition(firstEdge, secondEdge)
                    }
                }
                val firstLen = CssLength.parse(tokens[0])
                val secondLen = CssLength.parse(tokens[1])
                if (firstLen != null && secondLen != null) {
                    return of(firstLen, secondLen)
                }
                if (firstEdge != null && secondLen != null) {
                    return CssBackgroundPosition(firstEdge, CssPositionEdge.TOP, null, secondLen)
                }
                if (firstLen != null && secondEdge != null) {
                    return CssBackgroundPosition(CssPositionEdge.LEFT, secondEdge, firstLen, null)
                }
            }
            if (tokens.size >= 4) {
                val hEdge = CssPositionEdge.fromCss(tokens[0]) ?: CssPositionEdge.LEFT
                val hOff = CssLength.parse(tokens[1])
                val vEdge = CssPositionEdge.fromCss(tokens[2]) ?: CssPositionEdge.TOP
                val vOff = CssLength.parse(tokens[3])
                return CssBackgroundPosition(hEdge, vEdge, hOff, vOff)
            }
            return CENTER
        }
    }
}
