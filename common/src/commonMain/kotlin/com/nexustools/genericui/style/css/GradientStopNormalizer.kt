package com.nexustools.genericui.style.css

import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.gradient.Gradient.GradientStop
import com.nexustools.genericui.style.gradient.Gradient.LinearGradient
import com.nexustools.genericui.style.gradient.Gradient.RadialGradient
import com.nexustools.genericui.style.gradient.Gradient.RadialGradientShape
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Normalizes [LinearGradient] and [RadialGradient] stops into monotonic `0f..1f` ramps and
 * resolves CSS gradient angles, start/end vectors, centers, and pixel radii for `[width] x [height]`.
 */
object GradientStopNormalizer {

    /**
     * Resolves [gradient] into pixel endpoints `(x1, y1) -> (x2, y2)` inside a `[width] x [height]`
     * box following CSS Images Level 3 gradient line geometry and monotonic color-stop fixup.
     */
    fun normalizeLinear(gradient: LinearGradient, width: Float, height: Float): NormalizedLinearGradient {
        val safeW = width.coerceAtLeast(0f)
        val safeH = height.coerceAtLeast(0f)
        val rad = gradient.angleDegrees.toDouble() * PI / 180.0
        val dirX = sin(rad).toFloat()
        val dirY = (-cos(rad)).toFloat()
        val halfLen = 0.5f * (abs(safeW * dirX) + abs(safeH * dirY))
        val midX = safeW * 0.5f
        val midY = safeH * 0.5f
        val (positions, colors) = normalizeStops(gradient.stops)
        return NormalizedLinearGradient(
            positions = positions,
            colors = colors,
            x1 = midX - dirX * halfLen,
            y1 = midY - dirY * halfLen,
            x2 = midX + dirX * halfLen,
            y2 = midY + dirY * halfLen
        )
    }

    /**
     * Resolves [gradient] into pixel center `(cx, cy)` and radii `(radiusX, radiusY)` inside a
     * `[width] x [height]` box with monotonic `0f..1f` stop positions.
     */
    fun normalizeRadial(gradient: RadialGradient, width: Float, height: Float): NormalizedRadialGradient {
        val safeW = width.coerceAtLeast(0f)
        val safeH = height.coerceAtLeast(0f)
        val cx = gradient.centerX * safeW
        val cy = gradient.centerY * safeH
        val rxNorm = gradient.radiusX.coerceAtLeast(0f)
        val ryNorm = gradient.radiusY.coerceAtLeast(0f)
        val rx: Float
        val ry: Float
        if (gradient.shape == RadialGradientShape.CIRCLE) {
            val circleRadius = max(rxNorm * safeW, ryNorm * safeH)
            rx = circleRadius
            ry = circleRadius
        } else {
            rx = rxNorm * safeW
            ry = ryNorm * safeH
        }
        val (positions, colors) = normalizeStops(gradient.stops)
        return NormalizedRadialGradient(
            positions = positions,
            colors = colors,
            cx = cx,
            cy = cy,
            radiusX = rx,
            radiusY = ry
        )
    }

    private fun normalizeStops(stops: List<GradientStop>): Pair<FloatArray, Array<RgbaColor>> {
        if (stops.isEmpty()) {
            return floatArrayOf(0f, 1f) to arrayOf(RgbaColor.TRANSPARENT, RgbaColor.TRANSPARENT)
        }
        if (stops.size == 1) {
            val c = stops[0].color
            return floatArrayOf(0f, 1f) to arrayOf(c, c)
        }

        val count = stops.size
        val positions = FloatArray(count) { idx ->
            val raw = stops[idx].position
            if (raw == null) {
                Float.NaN
            } else if (raw > 1f) {
                (raw / 100f).coerceIn(0f, 1f)
            } else {
                raw.coerceIn(0f, 1f)
            }
        }
        if (positions[0].isNaN()) positions[0] = 0f
        if (positions[count - 1].isNaN()) positions[count - 1] = 1f

        var maxSoFar = positions[0]
        for (i in 1 until count) {
            val p = positions[i]
            if (!p.isNaN()) {
                if (p < maxSoFar) {
                    positions[i] = maxSoFar
                } else {
                    maxSoFar = p
                }
            }
        }

        var i = 0
        while (i < count - 1) {
            if (!positions[i].isNaN() && positions[i + 1].isNaN()) {
                var endIdx = i + 1
                while (endIdx < count && positions[endIdx].isNaN()) {
                    endIdx++
                }
                val pStart = positions[i]
                val pEnd = positions[endIdx]
                val spanCount = (endIdx - i).toFloat()
                for (k in (i + 1) until endIdx) {
                    positions[k] = (pStart + (pEnd - pStart) * ((k - i).toFloat() / spanCount)).coerceIn(0f, 1f)
                }
                i = endIdx
            } else {
                i++
            }
        }

        val colors = Array(count) { idx -> stops[idx].color }
        return positions to colors
    }
}
