package com.nexustools.genericui.style.css

import com.nexustools.genericui.enums.css.CssUnit
import com.nexustools.genericui.res.ResourceType
import com.nexustools.genericui.res.Resources
import com.nexustools.genericui.resources.ResourceID
import com.nexustools.genericui.resources.ResourceResolver

/**
 * Strongly-typed CSS length value (`16px`, `50%`, `1.5rem`, `100vh`, `auto`, `none`).
 */
data class CssLength(
    val value: Float = 0f,
    val unit: CssUnit = CssUnit.PX
) : CssValue {

    val isAuto: Boolean
        get() = unit == CssUnit.AUTO

    val isNone: Boolean
        get() = unit == CssUnit.NONE

    val isZero: Boolean
        get() = !isAuto && !isNone && value == 0f

    /**
     * Resolves this length to pixels (`Float`), or `null` when [isAuto] or [isNone].
     */
    fun resolvePx(
        referencePx: Float = 0f,
        rootFontSizePx: Float = 16f,
        parentFontSizePx: Float = 16f,
        viewportWidthPx: Float = 1280f,
        viewportHeightPx: Float = 800f
    ): Float? = when (unit) {
        CssUnit.AUTO, CssUnit.NONE -> null
        CssUnit.PX, CssUnit.DP -> value
        CssUnit.PERCENT -> (value / 100f) * referencePx
        CssUnit.REM -> value * rootFontSizePx
        CssUnit.EM -> value * parentFontSizePx
        CssUnit.VW -> (value / 100f) * viewportWidthPx
        CssUnit.VH -> (value / 100f) * viewportHeightPx
        CssUnit.VMIN -> (value / 100f) * minOf(viewportWidthPx, viewportHeightPx)
        CssUnit.VMAX -> (value / 100f) * maxOf(viewportWidthPx, viewportHeightPx)
        CssUnit.PT -> value * (96f / 72f)
    }

    override fun toCssString(): String {
        if (unit == CssUnit.AUTO) return "auto"
        if (unit == CssUnit.NONE) return "none"
        val formatted = if (value == value.toInt().toFloat()) {
            value.toInt().toString()
        } else {
            value.toString()
        }
        return "$formatted${unit.symbol}"
    }

    companion object {
        val ZERO = CssLength(0f, CssUnit.PX)
        val AUTO = CssLength(0f, CssUnit.AUTO)
        val NONE = CssLength(0f, CssUnit.NONE)

        fun px(value: Number): CssLength = CssLength(value.toFloat(), CssUnit.PX)
        fun percent(value: Number): CssLength = CssLength(value.toFloat(), CssUnit.PERCENT)
        fun rem(value: Number): CssLength = CssLength(value.toFloat(), CssUnit.REM)
        fun em(value: Number): CssLength = CssLength(value.toFloat(), CssUnit.EM)
        fun vw(value: Number): CssLength = CssLength(value.toFloat(), CssUnit.VW)
        fun vh(value: Number): CssLength = CssLength(value.toFloat(), CssUnit.VH)
        fun vmin(value: Number): CssLength = CssLength(value.toFloat(), CssUnit.VMIN)
        fun vmax(value: Number): CssLength = CssLength(value.toFloat(), CssUnit.VMAX)
        fun pt(value: Number): CssLength = CssLength(value.toFloat(), CssUnit.PT)
        fun dp(value: Number): CssLength = CssLength(value.toFloat(), CssUnit.DP)

        fun fromResource(resId: ResourceID): CssLength =
            px(ResourceResolver.getDimenPx(resId))

        fun parse(raw: String?): CssLength? {
            val cleaned = raw
                ?.replace(Regex("!\\s*important", RegexOption.IGNORE_CASE), "")
                ?.trim()
                ?.lowercase()
                ?.takeIf { it.isNotEmpty() }
                ?: return null

            if (cleaned == "auto") return AUTO
            if (cleaned == "none") return NONE
            if (cleaned == "0") return ZERO

            if (cleaned.startsWith("@dimen/") || cleaned.startsWith("res://dimen/")) {
                val name = cleaned.removePrefix("@dimen/").removePrefix("res://dimen/")
                val resId = Resources.activeTable.findResourceId(ResourceType.DIMEN, name)
                if (resId != null) {
                    return px(ResourceResolver.getDimenPx(resId))
                }
            }

            val unitCandidates = listOf(
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
            for ((suffix, unit) in unitCandidates) {
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
