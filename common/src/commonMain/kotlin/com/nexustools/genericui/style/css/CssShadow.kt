package com.nexustools.genericui.style.css

import com.nexustools.genericui.style.RgbaColor

/**
 * Strongly-typed single CSS shadow layer (`[inset] offsetX offsetY [blurRadius] [spreadRadius] color`)
 * used by `box-shadow`, `text-shadow`, and `filter: drop-shadow(...)`.
 */
data class CssShadow(
    val offsetX: CssLength,
    val offsetY: CssLength,
    val blurRadius: CssLength = CssLength.ZERO,
    val spreadRadius: CssLength = CssLength.ZERO,
    val color: RgbaColor = RgbaColor(0, 0, 0, 128),
    val inset: Boolean = false
) : CssValue {

    override fun toCssString(): String {
        val parts = mutableListOf<String>()
        if (inset) parts.add("inset")
        parts.add(offsetX.toCssString())
        parts.add(offsetY.toCssString())
        parts.add(blurRadius.toCssString())
        if (spreadRadius != CssLength.ZERO) {
            parts.add(spreadRadius.toCssString())
        }
        parts.add(color.toCssRgba())
        return parts.joinToString(" ")
    }

    companion object {
        fun px(
            offsetX: Float,
            offsetY: Float,
            blurRadius: Float = 0f,
            spreadRadius: Float = 0f,
            color: RgbaColor = RgbaColor(0, 0, 0, 128),
            inset: Boolean = false
        ): CssShadow = CssShadow(
            offsetX = CssLength.px(offsetX),
            offsetY = CssLength.px(offsetY),
            blurRadius = CssLength.px(blurRadius),
            spreadRadius = CssLength.px(spreadRadius),
            color = color,
            inset = inset
        )

        fun parse(raw: String?): CssShadow? {
            val cleaned = raw?.trim()?.takeIf { it.isNotEmpty() && !it.equals("none", ignoreCase = true) } ?: return null
            val tokens = tokenizePreservingParens(cleaned)
            if (tokens.size < 2) return null

            var isInset = false
            var parsedColor: RgbaColor? = null
            val lengths = mutableListOf<CssLength>()

            for (token in tokens) {
                when {
                    token.equals("inset", ignoreCase = true) -> isInset = true
                    else -> {
                        val colorCandidate = RgbaColor.fromCss(token)
                        if (colorCandidate != null) {
                            parsedColor = colorCandidate
                        } else {
                            CssLength.parse(token)?.let { lengths.add(it) }
                        }
                    }
                }
            }
            if (lengths.size < 2) return null
            return CssShadow(
                offsetX = lengths[0],
                offsetY = lengths[1],
                blurRadius = lengths.getOrNull(2) ?: CssLength.ZERO,
                spreadRadius = lengths.getOrNull(3) ?: CssLength.ZERO,
                color = parsedColor ?: RgbaColor(0, 0, 0, 128),
                inset = isInset
            )
        }

        internal fun tokenizePreservingParens(input: String): List<String> {
            val result = mutableListOf<String>()
            val current = StringBuilder()
            var depth = 0
            for (ch in input) {
                when {
                    ch == '(' -> {
                        depth++
                        current.append(ch)
                    }
                    ch == ')' -> {
                        depth = (depth - 1).coerceAtLeast(0)
                        current.append(ch)
                    }
                    ch.isWhitespace() && depth == 0 -> {
                        if (current.isNotEmpty()) {
                            result.add(current.toString())
                            current.clear()
                        }
                    }
                    else -> current.append(ch)
                }
            }
            if (current.isNotEmpty()) {
                result.add(current.toString())
            }
            return result
        }
    }
}
