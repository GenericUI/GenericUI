package com.nexustools.genericui.style.css

import com.nexustools.genericui.enums.css.CssUnit
import com.nexustools.genericui.resources.ResourceType
import com.nexustools.genericui.res.Resources
import com.nexustools.genericui.resources.ResourceID
import com.nexustools.genericui.resources.ResourceResolver

/**
 * Strongly-typed CSS length value (`16px`, `50%`, `1.5rem`, `100vh`, `auto`, `none`).
 */
data class CssLength(
    val value: Float = 0f,
    val unit: CssUnit = CssUnit.PX,
    val secondaryPxOffset: Float = 0f,
    val expression: String? = null
) : CssValue {

    val isAuto: Boolean
        get() = unit == CssUnit.AUTO

    val isNone: Boolean
        get() = unit == CssUnit.NONE

    val isZero: Boolean
        get() = !isAuto && !isNone && expression == null && value == 0f && secondaryPxOffset == 0f

    /**
     * Resolves this length to pixels (`Float`), or `null` when [isAuto] or [isNone].
     */
    fun resolvePx(
        referencePx: Float = 0f,
        rootFontSizePx: Float = 16f,
        parentFontSizePx: Float = 16f,
        viewportWidthPx: Float = 1280f,
        viewportHeightPx: Float = 800f
    ): Float? {
        if (expression != null) {
            return CssMathEvaluator.evaluateLengthPx(
                expression = expression,
                referencePx = referencePx,
                rootFontSizePx = rootFontSizePx,
                parentFontSizePx = parentFontSizePx,
                viewportWidthPx = viewportWidthPx,
                viewportHeightPx = viewportHeightPx
            )
        }
        return when (unit) {
            CssUnit.AUTO, CssUnit.NONE -> null
            CssUnit.PX, CssUnit.DP -> value
            CssUnit.PERCENT -> (referencePx * (value / 100f)) + secondaryPxOffset
            CssUnit.REM -> value * rootFontSizePx
            CssUnit.EM, CssUnit.NUMBER -> value * parentFontSizePx
            CssUnit.VW -> (value / 100f) * viewportWidthPx
            CssUnit.VH -> (value / 100f) * viewportHeightPx
            CssUnit.VMIN -> (value / 100f) * minOf(viewportWidthPx, viewportHeightPx)
            CssUnit.VMAX -> (value / 100f) * maxOf(viewportWidthPx, viewportHeightPx)
            CssUnit.PT -> value * (96f / 72f)
        }
    }

    override fun toCssString(): String {
        if (expression != null) return expression
        if (unit == CssUnit.AUTO) return "auto"
        if (unit == CssUnit.NONE) return "none"
        if (unit == CssUnit.NUMBER) return formatNumber(value)
        if (unit == CssUnit.PERCENT && secondaryPxOffset != 0f) {
            val op = if (secondaryPxOffset >= 0f) "+" else "-"
            val absOffset = kotlin.math.abs(secondaryPxOffset)
            return "calc(${formatNumber(value)}% $op ${formatNumber(absOffset)}px)"
        }
        return "${formatNumber(value)}${unit.symbol}"
    }

    companion object {
        val ZERO = CssLength(0f, CssUnit.PX)
        val AUTO = CssLength(0f, CssUnit.AUTO)
        val NONE = CssLength(0f, CssUnit.NONE)
        val PERCENT_100 = CssLength(100f, CssUnit.PERCENT)
        val PERCENT_50 = CssLength(50f, CssUnit.PERCENT)

        private val IMPORTANT_REGEX = Regex("!\\s*important", RegexOption.IGNORE_CASE)
        private val UNIT_CANDIDATES = arrayOf(
            "vmin" to CssUnit.VMIN,
            "vmax" to CssUnit.VMAX,
            "rem" to CssUnit.REM,
            "px" to CssUnit.PX,
            "dp" to CssUnit.DP,
            "em" to CssUnit.EM,
            "vw" to CssUnit.VW,
            "vh" to CssUnit.VH,
            "pt" to CssUnit.PT,
            "%" to CssUnit.PERCENT
        )

        private val CALC_PERCENT_PX_REGEX = Regex(
            "^calc\\(\\s*([+-]?[0-9]*\\.?[0-9]+)%\\s*([+-])\\s*([0-9]*\\.?[0-9]+)px\\s*\\)$",
            RegexOption.IGNORE_CASE
        )

        private fun formatNumber(num: Float): String =
            if (num == num.toInt().toFloat()) {
                num.toInt().toString()
            } else {
                num.toString()
            }

        fun px(value: Number): CssLength = CssLength(value.toFloat(), CssUnit.PX)
        fun percent(value: Number): CssLength = CssLength(value.toFloat(), CssUnit.PERCENT)
        fun calcPercentPx(percent: Float, offsetPx: Float): CssLength =
            CssLength(percent, CssUnit.PERCENT, offsetPx)
        fun rem(value: Number): CssLength = CssLength(value.toFloat(), CssUnit.REM)
        fun em(value: Number): CssLength = CssLength(value.toFloat(), CssUnit.EM)
        fun vw(value: Number): CssLength = CssLength(value.toFloat(), CssUnit.VW)
        fun vh(value: Number): CssLength = CssLength(value.toFloat(), CssUnit.VH)
        fun vmin(value: Number): CssLength = CssLength(value.toFloat(), CssUnit.VMIN)
        fun vmax(value: Number): CssLength = CssLength(value.toFloat(), CssUnit.VMAX)
        fun pt(value: Number): CssLength = CssLength(value.toFloat(), CssUnit.PT)
        fun dp(value: Number): CssLength = CssLength(value.toFloat(), CssUnit.DP)
        fun number(value: Number): CssLength = CssLength(value.toFloat(), CssUnit.NUMBER)

        fun fromResource(resId: ResourceID): CssLength =
            px(ResourceResolver.getDimenPx(resId))

        /**
         * Parses a CSS `line-height` value, treating bare numeric multipliers (e.g. `"1"`, `"1.38"`)
         * as [CssUnit.NUMBER] and delegating unit-suffixed or keyword values to [parse].
         */
        fun parseLineHeight(raw: String?): CssLength? {
            if (raw == null) return null
            val stripped = if ('!' in raw) raw.replace(IMPORTANT_REGEX, "") else raw
            val cleaned = stripped
                .trim()
                .lowercase()
                .takeIf { it.isNotEmpty() }
                ?: return null
            val bareNum = cleaned.toFloatOrNull()
            if (bareNum != null && bareNum.isFinite()) {
                return CssLength(bareNum, CssUnit.NUMBER)
            }
            return parse(raw)
        }

        fun parse(raw: String?): CssLength? {
            if (raw == null) return null
            val stripped = if ('!' in raw) raw.replace(IMPORTANT_REGEX, "") else raw
            val cleaned = stripped
                .trim()
                .lowercase()
                .takeIf { it.isNotEmpty() }
                ?: return null

            if (cleaned == "auto") return AUTO
            if (cleaned == "none") return NONE
            if (cleaned == "0") return ZERO

            if (CssMathEvaluator.isMathFunction(cleaned)) {
                if (cleaned.startsWith("calc(")) {
                    val calcMatch = CALC_PERCENT_PX_REGEX.matchEntire(cleaned)
                    if (calcMatch != null) {
                        val pct = calcMatch.groupValues[1].toFloatOrNull() ?: return null
                        val op = calcMatch.groupValues[2]
                        val pxOffset = calcMatch.groupValues[3].toFloatOrNull() ?: return null
                        val signedPx = if (op == "-") -pxOffset else pxOffset
                        return calcPercentPx(pct, signedPx)
                    }
                }
                if (CssMathEvaluator.isValidLengthExpression(cleaned)) {
                    if (CssMathEvaluator.isFixedPixelOnlyExpression(cleaned)) {
                        val fixedPx = CssMathEvaluator.evaluateLengthPx(cleaned)
                        if (fixedPx != null) {
                            return CssLength(fixedPx, CssUnit.PX)
                        }
                    }
                    return CssLength(0f, CssUnit.PX, 0f, expression = cleaned)
                }
            }

            if (cleaned.startsWith("@dimen/") || cleaned.startsWith("res://dimen/")) {
                val name = cleaned.removePrefix("@dimen/").removePrefix("res://dimen/")
                val resId = Resources.activeTable.findResourceId(ResourceType.DIMEN, name)
                if (resId != null) {
                    return px(ResourceResolver.getDimenPx(resId))
                }
            }

            for ((suffix, unit) in UNIT_CANDIDATES) {
                if (cleaned.endsWith(suffix)) {
                    val num = cleaned.removeSuffix(suffix).trim().toFloatOrNull() ?: return null
                    return CssLength(num, unit)
                }
            }

            val bareNum = cleaned.toFloatOrNull() ?: return null
            return CssLength(bareNum, CssUnit.PX)
        }
    }
}
