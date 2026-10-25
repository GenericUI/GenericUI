package com.nexustools.genericui.style.gradient

import com.nexustools.genericui.style.color.RgbaColor
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Radial gradient extending [Gradient] for component backgrounds in `style { ... }` and CSS stylesheets.
 *
 * @property centerX Normalized horizontal center in `[0.0f..1.0f]` (`0.5f` = center).
 * @property centerY Normalized vertical center in `[0.0f..1.0f]` (`0.5f` = center).
 * @property radiusX Normalized horizontal radius (`0.5f` = half component width).
 * @property radiusY Normalized vertical radius (`0.5f` = half component height).
 * @property shape [RadialGradientShape.CIRCLE] or [RadialGradientShape.ELLIPSE].
 */
class RadialGradient(
    val centerX: Float = 0.5f,
    val centerY: Float = 0.5f,
    val radiusX: Float = 0.5f,
    val radiusY: Float = 0.5f,
    val shape: RadialGradientShape = RadialGradientShape.ELLIPSE,
    override val stops: List<GradientStop>,
    override val repeating: Boolean = false
) : Gradient(stops, repeating) {

    constructor(vararg colors: RgbaColor) : this(
        centerX = 0.5f,
        centerY = 0.5f,
        radiusX = 0.5f,
        radiusY = 0.5f,
        shape = RadialGradientShape.ELLIPSE,
        stops = colors.map { GradientStop(it) },
        repeating = false
    )

    constructor(vararg stops: GradientStop, repeating: Boolean = false) : this(
        centerX = 0.5f,
        centerY = 0.5f,
        radiusX = 0.5f,
        radiusY = 0.5f,
        shape = RadialGradientShape.ELLIPSE,
        stops = stops.toList(),
        repeating = repeating
    )

    constructor(shape: RadialGradientShape, vararg colors: RgbaColor) : this(
        centerX = 0.5f,
        centerY = 0.5f,
        radiusX = 0.5f,
        radiusY = 0.5f,
        shape = shape,
        stops = colors.map { GradientStop(it) },
        repeating = false
    )

    constructor(shape: RadialGradientShape, vararg stops: GradientStop, repeating: Boolean = false) : this(
        centerX = 0.5f,
        centerY = 0.5f,
        radiusX = 0.5f,
        radiusY = 0.5f,
        shape = shape,
        stops = stops.toList(),
        repeating = repeating
    )

    constructor(centerX: Number, centerY: Number, radius: Number, vararg colors: RgbaColor) : this(
        centerX = centerX.toFloat(),
        centerY = centerY.toFloat(),
        radiusX = radius.toFloat(),
        radiusY = radius.toFloat(),
        shape = RadialGradientShape.CIRCLE,
        stops = colors.map { GradientStop(it) },
        repeating = false
    )

    constructor(centerX: Number, centerY: Number, radius: Number, vararg stops: GradientStop) : this(
        centerX = centerX.toFloat(),
        centerY = centerY.toFloat(),
        radiusX = radius.toFloat(),
        radiusY = radius.toFloat(),
        shape = RadialGradientShape.CIRCLE,
        stops = stops.toList(),
        repeating = false
    )

    constructor(centerX: Number, centerY: Number, radiusX: Number, radiusY: Number, vararg colors: RgbaColor) : this(
        centerX = centerX.toFloat(),
        centerY = centerY.toFloat(),
        radiusX = radiusX.toFloat(),
        radiusY = radiusY.toFloat(),
        shape = RadialGradientShape.ELLIPSE,
        stops = colors.map { GradientStop(it) },
        repeating = false
    )

    override fun sampleAt(u: Float, v: Float): RgbaColor {
        val rx = radiusX.coerceAtLeast(0.0001f)
        val ry = if (shape == RadialGradientShape.CIRCLE) rx else radiusY.coerceAtLeast(0.0001f)
        val dx = (u - centerX) / rx
        val dy = (v - centerY) / ry
        val dist = sqrt(dx * dx + dy * dy)
        return interpolateAt(dist)
    }

    override fun toCssString(): String {
        val fn = if (repeating) "repeating-radial-gradient" else "radial-gradient"
        val cxPct = (centerX * 100f).roundToInt()
        val cyPct = (centerY * 100f).roundToInt()
        val stopsStr = stops.joinToString(", ") { it.toCssString() }
        return "$fn(${shape.cssKeyword} at ${cxPct}% ${cyPct}%, $stopsStr)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RadialGradient) return false
        return centerX == other.centerX &&
            centerY == other.centerY &&
            radiusX == other.radiusX &&
            radiusY == other.radiusY &&
            shape == other.shape &&
            stops == other.stops &&
            repeating == other.repeating
    }

    override fun hashCode(): Int {
        var result = centerX.hashCode()
        result = 31 * result + centerY.hashCode()
        result = 31 * result + radiusX.hashCode()
        result = 31 * result + radiusY.hashCode()
        result = 31 * result + shape.hashCode()
        result = 31 * result + stops.hashCode()
        result = 31 * result + repeating.hashCode()
        return result
    }

    override fun toString(): String = toCssString()

    companion object {
        internal fun tryParseRadialHeader(token: String): Triple<Float, Float, RadialGradientShape>? {
            val lower = token.trim().lowercase()
            if (RgbaColor.fromCss(lower) != null) return null
            if (!lower.contains("circle") && !lower.contains("ellipse") && !lower.contains("at ")) {
                return null
            }
            val shape = if (lower.contains("circle")) RadialGradientShape.CIRCLE else RadialGradientShape.ELLIPSE
            var cx = 0.5f
            var cy = 0.5f
            val atIdx = lower.indexOf("at ")
            if (atIdx >= 0) {
                val coords = lower.substring(atIdx + 3).trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
                coords.getOrNull(0)?.let { cx = parsePositionToken(it, 0.5f) }
                coords.getOrNull(1)?.let { cy = parsePositionToken(it, 0.5f) }
            }
            return Triple(cx, cy, shape)
        }

        private fun parsePositionToken(tok: String, defaultVal: Float): Float = when (tok) {
            "left", "top" -> 0.0f
            "center" -> 0.5f
            "right", "bottom" -> 1.0f
            else -> if (tok.endsWith("%")) {
                tok.removeSuffix("%").toFloatOrNull()?.let { (it / 100f).coerceIn(0f, 1f) } ?: defaultVal
            } else {
                tok.toFloatOrNull()?.coerceIn(0f, 1f) ?: defaultVal
            }
        }
    }
}
