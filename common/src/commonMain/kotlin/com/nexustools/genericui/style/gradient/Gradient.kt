package com.nexustools.genericui.style.gradient

import com.nexustools.genericui.style.color.RgbaColor
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Base class for component background gradients ([LinearGradient] and [RadialGradient]).
 *
 * Supports both CSS serialization ([toCssString]) / CSS parsing ([fromCss]) and direct
 * normalized `(u, v)` color evaluation ([sampleAt]) for native desktop/mobile/GPU rendering.
 */
abstract class Gradient(
    open val stops: List<GradientStop>,
    open val repeating: Boolean = false
) {
    /**
     * Normalized stop list with every stop position resolved to `[0.0f, 1.0f]` in ascending order.
     */
    val resolvedStops: List<Pair<Float, RgbaColor>> by lazy {
        if (stops.isEmpty()) {
            listOf(0f to RgbaColor.TRANSPARENT, 1f to RgbaColor.TRANSPARENT)
        } else if (stops.size == 1) {
            listOf(0f to stops[0].color, 1f to stops[0].color)
        } else {
            val n = stops.lastIndex.coerceAtLeast(1)
            stops.mapIndexed { idx, stop ->
                val defaultFrac = idx.toFloat() / n.toFloat()
                stop.normalizedPosition(defaultFrac) to stop.color
            }.sortedBy { it.first }
        }
    }

    /**
     * Evaluates the interpolated [RgbaColor] along the gradient ramp at progress [t] (`0.0f..1.0f`).
     */
    fun interpolateAt(t: Float): RgbaColor {
        val list = resolvedStops
        if (list.isEmpty()) return RgbaColor.TRANSPARENT
        if (list.size == 1) return list[0].second

        val effectiveT = if (repeating) {
            val firstPos = list.first().first
            val lastPos = list.last().first
            val span = (lastPos - firstPos).takeIf { it > 0.0001f } ?: 1f
            val wrapped = ((t - firstPos) / span) - floor((t - firstPos) / span)
            (firstPos + wrapped * span).coerceIn(0f, 1f)
        } else {
            t.coerceIn(0f, 1f)
        }

        if (effectiveT <= list.first().first) return list.first().second
        if (effectiveT >= list.last().first) return list.last().second

        for (i in 0 until list.lastIndex) {
            val (p0, c0) = list[i]
            val (p1, c1) = list[i + 1]
            if (effectiveT in p0..p1) {
                val span = (p1 - p0).takeIf { it > 0.00001f } ?: return c1
                val local = ((effectiveT - p0) / span).coerceIn(0f, 1f)
                return RgbaColor(
                    r = (c0.r + (c1.r - c0.r) * local).roundToInt().coerceIn(0, 255),
                    g = (c0.g + (c1.g - c0.g) * local).roundToInt().coerceIn(0, 255),
                    b = (c0.b + (c1.b - c0.b) * local).roundToInt().coerceIn(0, 255),
                    a = (c0.a + (c1.a - c0.a) * local).coerceIn(0f, 1f)
                )
            }
        }
        return list.last().second
    }

    /**
     * Evaluates the gradient color at normalized component coordinates `([u], [v])` in `[0.0f..1.0f]`.
     */
    abstract fun sampleAt(u: Float, v: Float): RgbaColor

    /**
     * Formats this gradient as a standard CSS `linear-gradient(...)` or `radial-gradient(...)` expression.
     */
    abstract fun toCssString(): String

    companion object {
        /**
         * Parses a CSS `linear-gradient(...)`, `repeating-linear-gradient(...)`,
         * `radial-gradient(...)`, or `repeating-radial-gradient(...)` expression into a [Gradient].
         */
        fun fromCss(cssValue: String): Gradient? {
            val trimmed = cssValue.trim()
            if (trimmed.isEmpty()) return null

            val gradientToken = extractGradientFunctionToken(trimmed) ?: return null
            val openParen = gradientToken.indexOf('(')
            val closeParen = gradientToken.lastIndexOf(')')
            if (openParen <= 0 || closeParen <= openParen) return null

            val fnName = gradientToken.substring(0, openParen).trim().lowercase()
            val inner = gradientToken.substring(openParen + 1, closeParen).trim()
            val args = splitTopLevelCommaArgs(inner)
            if (args.isEmpty()) return null

            return when (fnName) {
                "linear-gradient", "repeating-linear-gradient" -> {
                    val repeating = fnName.startsWith("repeating-")
                    val firstArg = args.first().trim()
                    val parsedAngle = LinearGradient.tryParseDirectionOrAngle(firstArg)
                    val stopStrings = if (parsedAngle != null) args.drop(1) else args
                    val stops = stopStrings.mapNotNull { parseGradientStop(it) }
                    if (stops.isEmpty()) null else LinearGradient(
                        angleDegrees = parsedAngle ?: 180f,
                        stops = stops,
                        repeating = repeating
                    )
                }
                "radial-gradient", "repeating-radial-gradient" -> {
                    val repeating = fnName.startsWith("repeating-")
                    val firstArg = args.first().trim()
                    val headerSpec = RadialGradient.tryParseRadialHeader(firstArg)
                    val stopStrings = if (headerSpec != null) args.drop(1) else args
                    val stops = stopStrings.mapNotNull { parseGradientStop(it) }
                    if (stops.isEmpty()) null else RadialGradient(
                        centerX = headerSpec?.first ?: 0.5f,
                        centerY = headerSpec?.second ?: 0.5f,
                        radiusX = 0.5f,
                        radiusY = 0.5f,
                        shape = headerSpec?.third ?: RadialGradientShape.ELLIPSE,
                        stops = stops,
                        repeating = repeating
                    )
                }
                else -> null
            }
        }

        private fun extractGradientFunctionToken(input: String): String? {
            val lower = input.lowercase()
            val prefixes = listOf(
                "repeating-linear-gradient(",
                "linear-gradient(",
                "repeating-radial-gradient(",
                "radial-gradient("
            )
            var matchStart = -1
            for (prefix in prefixes) {
                val idx = lower.indexOf(prefix)
                if (idx >= 0 && (matchStart < 0 || idx < matchStart)) {
                    matchStart = idx
                }
            }
            if (matchStart < 0) return null
            var depth = 0
            for (i in matchStart until input.length) {
                val ch = input[i]
                if (ch == '(') depth++
                else if (ch == ')') {
                    depth--
                    if (depth == 0) {
                        return input.substring(matchStart, i + 1)
                    }
                }
            }
            return null
        }

        internal fun splitTopLevelCommaArgs(inner: String): List<String> {
            val result = mutableListOf<String>()
            val current = StringBuilder()
            var depth = 0
            for (ch in inner) {
                when {
                    ch == '(' -> {
                        depth++
                        current.append(ch)
                    }
                    ch == ')' -> {
                        depth = (depth - 1).coerceAtLeast(0)
                        current.append(ch)
                    }
                    ch == ',' && depth == 0 -> {
                        val token = current.toString().trim()
                        if (token.isNotEmpty()) result.add(token)
                        current.clear()
                    }
                    else -> current.append(ch)
                }
            }
            val tail = current.toString().trim()
            if (tail.isNotEmpty()) result.add(tail)
            return result
        }

        internal fun parseGradientStop(rawStop: String): GradientStop? {
            val trimmed = rawStop.trim()
            if (trimmed.isEmpty()) return null

            // Check if the full token is just a color
            RgbaColor.fromCss(trimmed)?.let { return GradientStop(it, null) }

            // Otherwise split color and trailing percentage/fraction while preserving parens
            val lastSpace = findLastTopLevelSpace(trimmed)
            if (lastSpace <= 0) return null
            val colorPart = trimmed.substring(0, lastSpace).trim()
            val posPart = trimmed.substring(lastSpace + 1).trim()
            val color = RgbaColor.fromCss(colorPart) ?: return null
            val position = when {
                posPart.endsWith("%") -> posPart.removeSuffix("%").trim().toFloatOrNull()?.let { (it / 100f).coerceIn(0f, 1f) }
                else -> posPart.toFloatOrNull()?.coerceIn(0f, 1f)
            }
            return GradientStop(color, position)
        }

        private fun findLastTopLevelSpace(input: String): Int {
            var depth = 0
            var lastIdx = -1
            for (i in input.indices) {
                when (val ch = input[i]) {
                    '(' -> depth++
                    ')' -> depth = (depth - 1).coerceAtLeast(0)
                    else -> if (ch.isWhitespace() && depth == 0) {
                        lastIdx = i
                    }
                }
            }
            return lastIdx
        }
    }
}
