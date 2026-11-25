package com.nexustools.genericui.style.css

import com.nexustools.genericui.style.transition.CssTransform2D

/**
 * Strongly-typed W3C CSS `transform` representation supporting signed percentage/pixel
 * translations (`translate`, `translateX`, `translateY`), uniform and non-uniform scaling
 * (`scale`, `scaleX`, `scaleY`), and rotation (`rotate`).
 */
data class CssTransform(
    val translateX: Float = 0f,
    val translateXIsPercent: Boolean = false,
    val translateY: Float = 0f,
    val translateYIsPercent: Boolean = false,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotateDeg: Float = 0f
) {
    val isIdentity: Boolean
        get() = translateX == 0f && translateY == 0f && scaleX == 1f && scaleY == 1f && rotateDeg == 0f

    /**
     * Resolves `(dx, dy)` in pixels against the element's own laid-out [widthPx] and [heightPx].
     */
    fun resolveTranslationPx(widthPx: Int, heightPx: Int): Pair<Float, Float> {
        val dx = if (translateXIsPercent) (translateX / 100f) * widthPx else translateX
        val dy = if (translateYIsPercent) (translateY / 100f) * heightPx else translateY
        return dx to dy
    }

    /**
     * Interpolates linearly toward [target] at normalized progress [progress] (`0f..1f`), preserving
     * non-zero axis percentage units cleanly when interpolating from or to `0`.
     */
    fun interpolateTo(target: CssTransform, progress: Float): CssTransform {
        val t = progress.coerceIn(0f, 1f)
        val xIsPercent = when {
            translateX == 0f && target.translateX != 0f -> target.translateXIsPercent
            target.translateX == 0f && translateX != 0f -> translateXIsPercent
            t < 0.5f -> translateXIsPercent
            else -> target.translateXIsPercent
        }
        val yIsPercent = when {
            translateY == 0f && target.translateY != 0f -> target.translateYIsPercent
            target.translateY == 0f && translateY != 0f -> translateYIsPercent
            t < 0.5f -> translateYIsPercent
            else -> target.translateYIsPercent
        }
        return CssTransform(
            translateX = lerp(translateX, target.translateX, t),
            translateXIsPercent = xIsPercent,
            translateY = lerp(translateY, target.translateY, t),
            translateYIsPercent = yIsPercent,
            scaleX = lerp(scaleX, target.scaleX, t),
            scaleY = lerp(scaleY, target.scaleY, t),
            rotateDeg = lerp(rotateDeg, target.rotateDeg, t)
        )
    }

    fun toTransform2D(): CssTransform2D = CssTransform2D(
        translateX = translateX,
        translateXIsPercent = translateXIsPercent,
        translateY = translateY,
        translateYIsPercent = translateYIsPercent,
        scaleX = scaleX,
        scaleY = scaleY,
        rotateDeg = rotateDeg
    )

    fun toCssString(): String {
        val parts = mutableListOf<String>()
        if (translateX != 0f || translateY != 0f || translateXIsPercent || translateYIsPercent) {
            val xUnit = if (translateXIsPercent) "%" else "px"
            val yUnit = if (translateYIsPercent) "%" else "px"
            parts.add("translate(${formatFloat(translateX)}$xUnit, ${formatFloat(translateY)}$yUnit)")
        }
        if (scaleX != 1f || scaleY != 1f) {
            if (scaleX == scaleY) {
                parts.add("scale(${formatFloat(scaleX)})")
            } else {
                parts.add("scale(${formatFloat(scaleX)}, ${formatFloat(scaleY)})")
            }
        }
        if (rotateDeg != 0f) {
            parts.add("rotate(${formatFloat(rotateDeg)}deg)")
        }
        return if (parts.isEmpty()) "none" else parts.joinToString(" ")
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    private fun formatFloat(v: Float): String {
        val asInt = v.toInt()
        return if (v == asInt.toFloat()) asInt.toString() else v.toString()
    }

    companion object {
        val IDENTITY = CssTransform()

        private val TRANSFORM_FN_REGEX = Regex("""([a-zA-Z0-9_-]+)\(([^)]*)\)""")
        private const val RAD_TO_DEG = 57.29578f

        fun fromTransform2D(transform: CssTransform2D): CssTransform = CssTransform(
            translateX = transform.translateX,
            translateXIsPercent = transform.translateXIsPercent,
            translateY = transform.translateY,
            translateYIsPercent = transform.translateYIsPercent,
            scaleX = transform.scaleX,
            scaleY = transform.scaleY,
            rotateDeg = transform.rotateDeg
        )

        /**
         * Parses a CSS `transform` value such as `"translate(0, -50%)"`, `"translate(-50%, -50%)"`,
         * `"translateX(12px) scale(1.2)"`, or `"none"`, returning `null` if blank or invalid.
         */
        fun parse(raw: String): CssTransform? {
            val cleaned = raw.replace("!important", "", ignoreCase = true).trim()
            if (cleaned.isEmpty()) return null
            if (cleaned.equals("none", ignoreCase = true)) return IDENTITY

            var tx = 0f
            var txPct = false
            var ty = 0f
            var tyPct = false
            var sx = 1f
            var sy = 1f
            var rot = 0f
            var recognizedCount = 0

            for (match in TRANSFORM_FN_REGEX.findAll(cleaned)) {
                val fnName = match.groupValues[1].lowercase()
                val args = match.groupValues[2].split(',').map { it.trim() }.filter { it.isNotEmpty() }
                when (fnName) {
                    "translate", "translate3d" -> {
                        val first = args.getOrNull(0)?.let(::parseOffset) ?: return null
                        tx = first.first
                        txPct = first.second
                        if (args.size >= 2) {
                            val second = parseOffset(args[1]) ?: return null
                            ty = second.first
                            tyPct = second.second
                        }
                        recognizedCount++
                    }
                    "translatex" -> {
                        val first = args.getOrNull(0)?.let(::parseOffset) ?: return null
                        tx = first.first
                        txPct = first.second
                        recognizedCount++
                    }
                    "translatey" -> {
                        val first = args.getOrNull(0)?.let(::parseOffset) ?: return null
                        ty = first.first
                        tyPct = first.second
                        recognizedCount++
                    }
                    "scale", "scale3d" -> {
                        val firstVal = args.getOrNull(0)?.toFloatOrNull() ?: return null
                        val secondVal = if (args.size >= 2) (args[1].toFloatOrNull() ?: return null) else firstVal
                        sx = firstVal
                        sy = secondVal
                        recognizedCount++
                    }
                    "scalex" -> {
                        sx = args.getOrNull(0)?.toFloatOrNull() ?: return null
                        recognizedCount++
                    }
                    "scaley" -> {
                        sy = args.getOrNull(0)?.toFloatOrNull() ?: return null
                        recognizedCount++
                    }
                    "rotate", "rotatez" -> {
                        val rawAngle = args.getOrNull(0)?.lowercase() ?: return null
                        rot = when {
                            rawAngle.endsWith("deg") -> rawAngle.removeSuffix("deg").trim().toFloatOrNull() ?: return null
                            rawAngle.endsWith("rad") ->
                                (rawAngle.removeSuffix("rad").trim().toFloatOrNull() ?: return null) * RAD_TO_DEG
                            rawAngle.endsWith("turn") ->
                                (rawAngle.removeSuffix("turn").trim().toFloatOrNull() ?: return null) * 360f
                            else -> rawAngle.toFloatOrNull() ?: return null
                        }
                        recognizedCount++
                    }
                }
            }

            if (recognizedCount == 0) return null
            return CssTransform(
                translateX = tx,
                translateXIsPercent = txPct,
                translateY = ty,
                translateYIsPercent = tyPct,
                scaleX = sx,
                scaleY = sy,
                rotateDeg = rot
            )
        }

        private fun parseOffset(token: String): Pair<Float, Boolean>? {
            val t = token.trim().lowercase()
            if (t.isEmpty()) return null
            return when {
                t.endsWith("%") -> (t.removeSuffix("%").trim().toFloatOrNull() ?: return null) to true
                t.endsWith("px") -> (t.removeSuffix("px").trim().toFloatOrNull() ?: return null) to false
                t.endsWith("rem") -> ((t.removeSuffix("rem").trim().toFloatOrNull() ?: return null) * 16f) to false
                t.endsWith("em") -> ((t.removeSuffix("em").trim().toFloatOrNull() ?: return null) * 16f) to false
                else -> (t.toFloatOrNull() ?: return null) to false
            }
        }
    }
}
