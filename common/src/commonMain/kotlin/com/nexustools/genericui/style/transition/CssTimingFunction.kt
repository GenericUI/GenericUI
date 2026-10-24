package com.nexustools.genericui.style.transition

import kotlin.math.abs

/**
 * Cubic-Bezier CSS timing function (`linear`, `ease`, `ease-in`, `ease-out`, `ease-in-out`,
 * or `cubic-bezier(x1, y1, x2, y2)`) used to evaluate CSS transition progress.
 */
data class CssTimingFunction(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val isLinear: Boolean = false
) {
    /**
     * Evaluates the cubic-Bezier easing curve at normalized time [t] in `[0.0, 1.0]`.
     */
    fun evaluate(t: Float): Float {
        if (t <= 0f) return 0f
        if (t >= 1f) return 1f
        if (isLinear || (x1 == y1 && x2 == y2)) return t

        // Solve x(u) = t using Newton-Raphson with bisection fallback
        var u = t
        for (i in 0 until 8) {
            val xError = sampleCurveX(u) - t
            if (abs(xError) < 1e-5f) {
                return sampleCurveY(u)
            }
            val dx = sampleCurveDerivativeX(u)
            if (abs(dx) < 1e-5f) break
            u = (u - xError / dx).coerceIn(0f, 1f)
        }

        var lo = 0f
        var hi = 1f
        u = t
        for (i in 0 until 12) {
            val xVal = sampleCurveX(u)
            if (abs(xVal - t) < 1e-5f) break
            if (t > xVal) {
                lo = u
            } else {
                hi = u
            }
            u = (lo + hi) * 0.5f
        }
        return sampleCurveY(u)
    }

    private fun sampleCurveX(u: Float): Float {
        val inv = 1f - u
        return 3f * inv * inv * u * x1 + 3f * inv * u * u * x2 + u * u * u
    }

    private fun sampleCurveY(u: Float): Float {
        val inv = 1f - u
        return 3f * inv * inv * u * y1 + 3f * inv * u * u * y2 + u * u * u
    }

    private fun sampleCurveDerivativeX(u: Float): Float {
        val inv = 1f - u
        return 3f * inv * inv * x1 + 6f * inv * u * (x2 - x1) + 3f * u * u * (1f - x2)
    }

    companion object {
        val LINEAR = CssTimingFunction(0f, 0f, 1f, 1f, isLinear = true)
        val EASE = CssTimingFunction(0.25f, 0.1f, 0.25f, 1.0f)
        val EASE_IN = CssTimingFunction(0.42f, 0.0f, 1.0f, 1.0f)
        val EASE_OUT = CssTimingFunction(0.0f, 0.0f, 0.58f, 1.0f)
        val EASE_IN_OUT = CssTimingFunction(0.42f, 0.0f, 0.58f, 1.0f)

        fun parse(raw: String): CssTimingFunction {
            val cleaned = raw.trim().lowercase()
            return when {
                cleaned == "linear" -> LINEAR
                cleaned == "ease" -> EASE
                cleaned == "ease-in" -> EASE_IN
                cleaned == "ease-out" -> EASE_OUT
                cleaned == "ease-in-out" -> EASE_IN_OUT
                cleaned.startsWith("cubic-bezier(") && cleaned.endsWith(")") -> {
                    val args = cleaned.removePrefix("cubic-bezier(").removeSuffix(")")
                        .split(',')
                        .mapNotNull { it.trim().toFloatOrNull() }
                    if (args.size == 4) {
                        CssTimingFunction(
                            x1 = args[0].coerceIn(0f, 1f),
                            y1 = args[1],
                            x2 = args[2].coerceIn(0f, 1f),
                            y2 = args[3]
                        )
                    } else {
                        EASE
                    }
                }
                else -> EASE
            }
        }
    }
}
