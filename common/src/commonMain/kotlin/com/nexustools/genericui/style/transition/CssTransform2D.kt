package com.nexustools.genericui.style.transition

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Parsed 2D CSS transform (`translate`, `scale`, `rotate`, `skew`, `matrix`) supporting
 * interpolation during CSS transitions and window-manager-style container buffer compositing.
 */
data class CssTransform2D(
    val translateX: Float = 0f,
    val translateXIsPercent: Boolean = false,
    val translateY: Float = 0f,
    val translateYIsPercent: Boolean = false,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotateDeg: Float = 0f,
    val skewXDeg: Float = 0f,
    val skewYDeg: Float = 0f,
    val hasMatrix: Boolean = false,
    val matrixA: Float = 1f,
    val matrixB: Float = 0f,
    val matrixC: Float = 0f,
    val matrixD: Float = 1f,
    val matrixE: Float = 0f,
    val matrixF: Float = 0f
) {
    fun interpolateTo(target: CssTransform2D, progress: Float): CssTransform2D {
        val t = progress.coerceIn(0f, 1f)
        return CssTransform2D(
            translateX = lerp(translateX, target.translateX, t),
            translateXIsPercent = if (t < 0.5f) translateXIsPercent else target.translateXIsPercent,
            translateY = lerp(translateY, target.translateY, t),
            translateYIsPercent = if (t < 0.5f) translateYIsPercent else target.translateYIsPercent,
            scaleX = lerp(scaleX, target.scaleX, t),
            scaleY = lerp(scaleY, target.scaleY, t),
            rotateDeg = lerp(rotateDeg, target.rotateDeg, t),
            skewXDeg = lerp(skewXDeg, target.skewXDeg, t),
            skewYDeg = lerp(skewYDeg, target.skewYDeg, t),
            hasMatrix = hasMatrix || target.hasMatrix,
            matrixA = lerp(matrixA, target.matrixA, t),
            matrixB = lerp(matrixB, target.matrixB, t),
            matrixC = lerp(matrixC, target.matrixC, t),
            matrixD = lerp(matrixD, target.matrixD, t),
            matrixE = lerp(matrixE, target.matrixE, t),
            matrixF = lerp(matrixF, target.matrixF, t)
        )
    }

    fun resolveTranslationPx(widthPx: Int, heightPx: Int): Pair<Float, Float> {
        val dx = (if (translateXIsPercent) (translateX / 100f) * widthPx else translateX) +
            if (hasMatrix) matrixE else 0f
        val dy = (if (translateYIsPercent) (translateY / 100f) * heightPx else translateY) +
            if (hasMatrix) matrixF else 0f
        return dx to dy
    }

    /**
     * Resolves the 2x3 affine linear transform `[a, b, c, d, tx, ty]` in local pixel space
     * (excluding percentage origin pivot; caller shifts around `transform-origin`).
     */
    fun resolveAffineMatrix(widthPx: Int, heightPx: Int): FloatArray {
        val rawTx = if (translateXIsPercent) (translateX / 100f) * widthPx else translateX
        val rawTy = if (translateYIsPercent) (translateY / 100f) * heightPx else translateY
        val rad = rotateDeg * (3.1415927f / 180f)
        val c = cos(rad)
        val s = sin(rad)
        val tanSkewX = if (skewXDeg != 0f) tan(skewXDeg * (3.1415927f / 180f)) else 0f
        val tanSkewY = if (skewYDeg != 0f) tan(skewYDeg * (3.1415927f / 180f)) else 0f
        // M = T(rawTx, rawTy) * R(rotate) * S(scaleX, scaleY) * Skew(skewX, skewY) * [matrix if any]
        val sa = scaleX
        val sb = scaleY * tanSkewY
        val sc = scaleX * tanSkewX
        val sd = scaleY
        val r00 = c * sa - s * sb
        val r01 = c * sc - s * sd
        val r10 = s * sa + c * sb
        val r11 = s * sc + c * sd
        return if (hasMatrix) {
            floatArrayOf(
                r00 * matrixA + r01 * matrixB,
                r10 * matrixA + r11 * matrixB,
                r00 * matrixC + r01 * matrixD,
                r10 * matrixC + r11 * matrixD,
                rawTx + r00 * matrixE + r01 * matrixF,
                rawTy + r10 * matrixE + r11 * matrixF
            )
        } else {
            floatArrayOf(r00, r10, r01, r11, rawTx, rawTy)
        }
    }

    fun toCssString(): String {
        val parts = mutableListOf<String>()
        if (translateX != 0f || translateY != 0f || translateXIsPercent || translateYIsPercent) {
            val xUnit = if (translateXIsPercent) "%" else "px"
            val yUnit = if (translateYIsPercent) "%" else "px"
            parts.add("translate(${formatFloat(translateX)}$xUnit, ${formatFloat(translateY)}$yUnit)")
        }
        if (scaleX != 1f || scaleY != 1f) {
            parts.add("scale(${formatFloat(scaleX)}, ${formatFloat(scaleY)})")
        }
        if (rotateDeg != 0f) {
            parts.add("rotate(${formatFloat(rotateDeg)}deg)")
        }
        if (skewXDeg != 0f || skewYDeg != 0f) {
            parts.add("skew(${formatFloat(skewXDeg)}deg, ${formatFloat(skewYDeg)}deg)")
        }
        if (hasMatrix) {
            parts.add(
                "matrix(${formatFloat(matrixA)}, ${formatFloat(matrixB)}, " +
                    "${formatFloat(matrixC)}, ${formatFloat(matrixD)}, " +
                    "${formatFloat(matrixE)}, ${formatFloat(matrixF)})"
            )
        }
        return if (parts.isEmpty()) "none" else parts.joinToString(" ")
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    private fun formatFloat(v: Float): String {
        val asInt = v.toInt()
        return if (v == asInt.toFloat()) asInt.toString() else v.toString()
    }

    companion object {
        val IDENTITY = CssTransform2D()

        fun parse(raw: String?): CssTransform2D {
            if (raw.isNullOrBlank()) return IDENTITY
            val cleaned = raw.replace("!important", "", ignoreCase = true).trim()
            if (cleaned.isEmpty() || cleaned.equals("none", ignoreCase = true)) return IDENTITY

            var tx = 0f
            var txPct = false
            var ty = 0f
            var tyPct = false
            var sx = 1f
            var sy = 1f
            var rot = 0f
            var skX = 0f
            var skY = 0f
            var hasMat = false
            var mA = 1f
            var mB = 0f
            var mC = 0f
            var mD = 1f
            var mE = 0f
            var mF = 0f

            val fnRegex = Regex("""([a-zA-Z0-9]+)\(([^)]*)\)""")
            for (match in fnRegex.findAll(cleaned)) {
                val fnName = match.groupValues[1].lowercase()
                val args = match.groupValues[2].split(',').map { it.trim() }.filter { it.isNotEmpty() }
                when (fnName) {
                    "translate", "translate3d" -> {
                        if (args.isNotEmpty()) {
                            val (v, pct) = parseOffset(args[0])
                            tx = v
                            txPct = pct
                        }
                        if (args.size >= 2) {
                            val (v, pct) = parseOffset(args[1])
                            ty = v
                            tyPct = pct
                        }
                    }
                    "translatex" -> if (args.isNotEmpty()) {
                        val (v, pct) = parseOffset(args[0])
                        tx = v
                        txPct = pct
                    }
                    "translatey" -> if (args.isNotEmpty()) {
                        val (v, pct) = parseOffset(args[0])
                        ty = v
                        tyPct = pct
                    }
                    "scale", "scale3d" -> {
                        if (args.isNotEmpty()) {
                            sx = args[0].toFloatOrNull() ?: 1f
                            sy = if (args.size >= 2) (args[1].toFloatOrNull() ?: sx) else sx
                        }
                    }
                    "scalex" -> if (args.isNotEmpty()) {
                        sx = args[0].toFloatOrNull() ?: 1f
                    }
                    "scaley" -> if (args.isNotEmpty()) {
                        sy = args[0].toFloatOrNull() ?: 1f
                    }
                    "rotate", "rotatez" -> if (args.isNotEmpty()) {
                        rot = parseAngleDeg(args[0])
                    }
                    "skew" -> {
                        if (args.isNotEmpty()) skX = parseAngleDeg(args[0])
                        if (args.size >= 2) skY = parseAngleDeg(args[1])
                    }
                    "skewx" -> if (args.isNotEmpty()) {
                        skX = parseAngleDeg(args[0])
                    }
                    "skewy" -> if (args.isNotEmpty()) {
                        skY = parseAngleDeg(args[0])
                    }
                    "matrix" -> if (args.size >= 6) {
                        hasMat = true
                        mA = args[0].toFloatOrNull() ?: 1f
                        mB = args[1].toFloatOrNull() ?: 0f
                        mC = args[2].toFloatOrNull() ?: 0f
                        mD = args[3].toFloatOrNull() ?: 1f
                        mE = args[4].toFloatOrNull() ?: 0f
                        mF = args[5].toFloatOrNull() ?: 0f
                    }
                }
            }

            return CssTransform2D(
                translateX = tx,
                translateXIsPercent = txPct,
                translateY = ty,
                translateYIsPercent = tyPct,
                scaleX = sx,
                scaleY = sy,
                rotateDeg = rot,
                skewXDeg = skX,
                skewYDeg = skY,
                hasMatrix = hasMat,
                matrixA = mA,
                matrixB = mB,
                matrixC = mC,
                matrixD = mD,
                matrixE = mE,
                matrixF = mF
            )
        }

        private fun parseAngleDeg(raw: String): Float {
            val a = raw.trim().lowercase()
            return when {
                a.endsWith("deg") -> a.removeSuffix("deg").trim().toFloatOrNull() ?: 0f
                a.endsWith("rad") -> ((a.removeSuffix("rad").trim().toFloatOrNull() ?: 0f) * 180f / 3.1415927f)
                a.endsWith("turn") -> (a.removeSuffix("turn").trim().toFloatOrNull() ?: 0f) * 360f
                a.endsWith("grad") -> (a.removeSuffix("grad").trim().toFloatOrNull() ?: 0f) * 0.9f
                else -> a.toFloatOrNull() ?: 0f
            }
        }

        private fun parseOffset(token: String): Pair<Float, Boolean> {
            val t = token.trim().lowercase()
            return when {
                t.endsWith("%") -> (t.removeSuffix("%").trim().toFloatOrNull() ?: 0f) to true
                t.endsWith("px") -> (t.removeSuffix("px").trim().toFloatOrNull() ?: 0f) to false
                else -> (t.toFloatOrNull() ?: 0f) to false
            }
        }
    }
}
