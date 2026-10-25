package com.nexustools.genericui.style.gradient

import com.nexustools.genericui.style.color.RgbaColor
import kotlin.math.roundToInt

/**
 * Single color stop within a [Gradient] ([LinearGradient] or [RadialGradient]).
 *
 * @property color The RGBA color at this stop.
 * @property position Optional stop position. Values in `0.0f..1.0f` represent normalized fractions
 *   (`0%..100%`), while values `> 1.0f` (up to `100.0f`) are interpreted as percentages.
 *   When `null`, the stop position is distributed evenly between neighboring stops.
 */
data class GradientStop(
    val color: RgbaColor,
    val position: Float? = null
) {
    constructor(color: RgbaColor, position: Double) : this(color, position.toFloat())
    constructor(position: Number, color: RgbaColor) : this(color, position.toFloat())

    /**
     * Resolves this stop's position in `[0.0f, 1.0f]`, using [defaultFraction] if [position] is `null`.
     */
    fun normalizedPosition(defaultFraction: Float): Float {
        val raw = position ?: return defaultFraction.coerceIn(0f, 1f)
        return if (raw > 1.0f) {
            (raw / 100f).coerceIn(0f, 1f)
        } else {
            raw.coerceIn(0f, 1f)
        }
    }

    /**
     * Formats this stop as a CSS color-stop token (e.g. `rgba(255, 0, 0, 1.0) 50%`).
     */
    fun toCssString(): String {
        val colorCss = color.toCssRgbaString()
        val pos = position ?: return colorCss
        val pct = if (pos > 1.0f) pos.coerceIn(0f, 100f) else (pos * 100f).coerceIn(0f, 100f)
        val rounded = (pct * 10f).roundToInt() / 10f
        val pctStr = if (rounded == rounded.toInt().toFloat()) {
            "${rounded.toInt()}%"
        } else {
            "$rounded%"
        }
        return "$colorCss $pctStr"
    }
}
