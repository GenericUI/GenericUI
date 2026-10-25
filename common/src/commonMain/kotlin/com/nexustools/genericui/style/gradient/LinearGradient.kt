package com.nexustools.genericui.style.gradient

import com.nexustools.genericui.style.color.RgbaColor
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Linear gradient extending [Gradient] for component backgrounds in `style { ... }` and CSS stylesheets.
 *
 * Follows the CSS `linear-gradient` angle convention:
 * - `0deg`: bottom to top
 * - `90deg`: left to right
 * - `180deg` (default): top to bottom
 * - `270deg`: right to left
 */
class LinearGradient(
    val angleDegrees: Float = 180f,
    override val stops: List<GradientStop>,
    override val repeating: Boolean = false
) : Gradient(stops, repeating) {

    constructor(vararg colors: RgbaColor) : this(
        angleDegrees = 180f,
        stops = colors.map { GradientStop(it) },
        repeating = false
    )

    constructor(angleDegrees: Number, vararg colors: RgbaColor) : this(
        angleDegrees = angleDegrees.toFloat(),
        stops = colors.map { GradientStop(it) },
        repeating = false
    )

    constructor(angleDegrees: Number, vararg stops: GradientStop, repeating: Boolean = false) : this(
        angleDegrees = angleDegrees.toFloat(),
        stops = stops.toList(),
        repeating = repeating
    )

    constructor(vararg stops: GradientStop, repeating: Boolean = false) : this(
        angleDegrees = 180f,
        stops = stops.toList(),
        repeating = repeating
    )

    constructor(direction: String, vararg colors: RgbaColor) : this(
        angleDegrees = tryParseDirectionOrAngle(direction) ?: 180f,
        stops = colors.map { GradientStop(it) },
        repeating = false
    )

    constructor(direction: String, vararg stops: GradientStop) : this(
        angleDegrees = tryParseDirectionOrAngle(direction) ?: 180f,
        stops = stops.toList(),
        repeating = false
    )

    override fun sampleAt(u: Float, v: Float): RgbaColor {
        val rad = (angleDegrees.toDouble() * PI) / 180.0
        val dirX = sin(rad).toFloat()
        val dirY = (-cos(rad)).toFloat()
        val maxExtent = (abs(dirX) + abs(dirY)).coerceAtLeast(0.0001f)
        val projected = ((u - 0.5f) * dirX + (v - 0.5f) * dirY) / maxExtent + 0.5f
        return interpolateAt(projected)
    }

    override fun toCssString(): String {
        val fn = if (repeating) "repeating-linear-gradient" else "linear-gradient"
        val roundedAngle = (angleDegrees * 10f).roundToInt() / 10f
        val angleStr = if (roundedAngle == roundedAngle.toInt().toFloat()) {
            "${roundedAngle.toInt()}deg"
        } else {
            "${roundedAngle}deg"
        }
        val stopsStr = stops.joinToString(", ") { it.toCssString() }
        return "$fn($angleStr, $stopsStr)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LinearGradient) return false
        return angleDegrees == other.angleDegrees &&
            stops == other.stops &&
            repeating == other.repeating
    }

    override fun hashCode(): Int {
        var result = angleDegrees.hashCode()
        result = 31 * result + stops.hashCode()
        result = 31 * result + repeating.hashCode()
        return result
    }

    override fun toString(): String = toCssString()

    companion object {
        internal fun tryParseDirectionOrAngle(token: String): Float? {
            val clean = token.trim().lowercase().replace(Regex("\\s+"), " ")
            return when {
                clean.endsWith("deg") -> clean.removeSuffix("deg").trim().toFloatOrNull()
                clean.endsWith("turn") -> clean.removeSuffix("turn").trim().toFloatOrNull()?.let { it * 360f }
                clean.endsWith("rad") -> clean.removeSuffix("rad").trim().toFloatOrNull()?.let { (it * 180f / PI.toFloat()) }
                clean == "to top" -> 0f
                clean == "to top right" || clean == "to right top" -> 45f
                clean == "to right" -> 90f
                clean == "to bottom right" || clean == "to right bottom" -> 135f
                clean == "to bottom" -> 180f
                clean == "to bottom left" || clean == "to left bottom" -> 225f
                clean == "to left" -> 270f
                clean == "to top left" || clean == "to left top" -> 315f
                else -> null
            }
        }
    }
}
