package com.nexustools.genericui.style.css

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Canonical row-major 4x5 RGBA color matrix builder for [CssFilter] color operations.
 *
 * The 20-element matrix `M` transforms normalized un-premultiplied `[r, g, b, a, 1]` channels in
 * `0f..1f` via `[r', g', b', a'] = M * [r, g, b, a, 1]` (columns `0..3` linear, column `4` bias),
 * matching CSS Filter Effects composition order and [FastBackdropBlurKernel] pixel math for
 * GPU/shader/CoreImage/Direct2D parity.
 */
object CssFilterColorMatrix {

    const val MATRIX_SIZE = 20

    /**
     * Builds a 20-element row-major 4x5 RGBA color matrix representing [filter]'s pixel operations
     * (`grayscale`, `sepia`, `saturate`, `hue-rotate`, `invert`, `brightness`, `contrast`, `opacity`).
     */
    fun buildMatrix4x5(filter: CssFilter): FloatArray {
        val m = floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
        if (filter.isNone || !filter.hasPixelOps) return m

        val tmp = FloatArray(MATRIX_SIZE)

        val gs = filter.grayscale
        if (gs != null && gs != 0f) {
            val gAmt = gs.coerceIn(0f, 1f)
            val inv = 1f - gAmt
            leftMultiplyRgb3x3(
                current = m,
                scratch = tmp,
                r0c0 = 0.2126f + 0.7874f * inv, r0c1 = 0.7152f * gAmt, r0c2 = 0.0722f * gAmt,
                r1c0 = 0.2126f * gAmt, r1c1 = 0.7152f + 0.2848f * inv, r1c2 = 0.0722f * gAmt,
                r2c0 = 0.2126f * gAmt, r2c1 = 0.7152f * gAmt, r2c2 = 0.0722f + 0.9278f * inv
            )
        }

        val sp = filter.sepia
        if (sp != null && sp != 0f) {
            val sAmt = sp.coerceIn(0f, 1f)
            val inv = 1f - sAmt
            leftMultiplyRgb3x3(
                current = m,
                scratch = tmp,
                r0c0 = 0.393f + 0.607f * inv, r0c1 = 0.769f * sAmt, r0c2 = 0.189f * sAmt,
                r1c0 = 0.349f * sAmt, r1c1 = 0.686f + 0.314f * inv, r1c2 = 0.168f * sAmt,
                r2c0 = 0.272f * sAmt, r2c1 = 0.534f * sAmt, r2c2 = 0.131f + 0.869f * inv
            )
        }

        val sat = filter.saturate
        if (sat != null && sat != 1f) {
            val st = sat.coerceAtLeast(0f)
            val oneMinus = 1f - st
            leftMultiplyRgb3x3(
                current = m,
                scratch = tmp,
                r0c0 = 0.2126f + 0.7874f * st, r0c1 = 0.7152f * oneMinus, r0c2 = 0.0722f * oneMinus,
                r1c0 = 0.2126f * oneMinus, r1c1 = 0.7152f + 0.2848f * st, r1c2 = 0.0722f * oneMinus,
                r2c0 = 0.2126f * oneMinus, r2c1 = 0.7152f * oneMinus, r2c2 = 0.0722f + 0.9278f * st
            )
        }

        val hueDeg = filter.hueRotateDeg
        if (hueDeg != null && hueDeg != 0f) {
            val hueRad = hueDeg * (PI.toFloat() / 180f)
            val cosH = cos(hueRad)
            val sinH = sin(hueRad)
            leftMultiplyRgb3x3(
                current = m,
                scratch = tmp,
                r0c0 = 0.213f + cosH * 0.787f - sinH * 0.213f,
                r0c1 = 0.715f - cosH * 0.715f - sinH * 0.715f,
                r0c2 = 0.072f - cosH * 0.072f + sinH * 0.928f,
                r1c0 = 0.213f - cosH * 0.213f + sinH * 0.143f,
                r1c1 = 0.715f + cosH * 0.285f + sinH * 0.140f,
                r1c2 = 0.072f - cosH * 0.072f - sinH * 0.283f,
                r2c0 = 0.213f - cosH * 0.213f - sinH * 0.787f,
                r2c1 = 0.715f - cosH * 0.715f + sinH * 0.715f,
                r2c2 = 0.072f + cosH * 0.928f + sinH * 0.072f
            )
        }

        val brVal = filter.brightness
        val invVal = filter.invert
        val brightnessBeforeInvert = brVal == 0f && invVal != null && invVal > 0f
        if (brightnessBeforeInvert) {
            scaleBiasRgbRows(m, scale = brVal!!.coerceAtLeast(0f), bias = 0f)
        }

        if (invVal != null && invVal != 0f) {
            val inv = invVal.coerceIn(0f, 1f)
            scaleBiasRgbRows(m, scale = 1f - 2f * inv, bias = inv)
        }

        if (!brightnessBeforeInvert && brVal != null && brVal != 1f) {
            scaleBiasRgbRows(m, scale = brVal.coerceAtLeast(0f), bias = 0f)
        }

        val ctVal = filter.contrast
        if (ctVal != null && ctVal != 1f) {
            val ct = ctVal.coerceAtLeast(0f)
            scaleBiasRgbRows(m, scale = ct, bias = 0.5f * (1f - ct))
        }

        val opVal = filter.opacity
        if (opVal != null && opVal != 1f) {
            val op = opVal.coerceIn(0f, 1f)
            m[15] *= op
            m[16] *= op
            m[17] *= op
            m[18] *= op
            m[19] *= op
        }

        return m
    }

    /**
     * Applies a 20-element 4x5 [matrix] built by [buildMatrix4x5] to normalized `0f..1f`
     * un-premultiplied `([r], [g], [b], [a])` channels and writes clamped results into [outRgba].
     */
    fun applyToRgba(
        matrix: FloatArray,
        r: Float,
        g: Float,
        b: Float,
        a: Float,
        outRgba: FloatArray
    ) {
        require(matrix.size >= MATRIX_SIZE) { "Color matrix must contain at least $MATRIX_SIZE elements, got ${matrix.size}" }
        require(outRgba.size >= 4) { "Output RGBA array must contain at least 4 elements, got ${outRgba.size}" }
        outRgba[0] = (matrix[0] * r + matrix[1] * g + matrix[2] * b + matrix[3] * a + matrix[4]).coerceIn(0f, 1f)
        outRgba[1] = (matrix[5] * r + matrix[6] * g + matrix[7] * b + matrix[8] * a + matrix[9]).coerceIn(0f, 1f)
        outRgba[2] = (matrix[10] * r + matrix[11] * g + matrix[12] * b + matrix[13] * a + matrix[14]).coerceIn(0f, 1f)
        outRgba[3] = (matrix[15] * r + matrix[16] * g + matrix[17] * b + matrix[18] * a + matrix[19]).coerceIn(0f, 1f)
    }

    private fun leftMultiplyRgb3x3(
        current: FloatArray,
        scratch: FloatArray,
        r0c0: Float, r0c1: Float, r0c2: Float,
        r1c0: Float, r1c1: Float, r1c2: Float,
        r2c0: Float, r2c1: Float, r2c2: Float
    ) {
        for (col in 0 until 5) {
            val c0 = current[col]
            val c1 = current[5 + col]
            val c2 = current[10 + col]
            scratch[col] = r0c0 * c0 + r0c1 * c1 + r0c2 * c2
            scratch[5 + col] = r1c0 * c0 + r1c1 * c1 + r1c2 * c2
            scratch[10 + col] = r2c0 * c0 + r2c1 * c1 + r2c2 * c2
        }
        for (i in 0 until 15) {
            current[i] = scratch[i]
        }
    }

    private fun scaleBiasRgbRows(matrix: FloatArray, scale: Float, bias: Float) {
        for (row in 0 until 3) {
            val base = row * 5
            matrix[base] *= scale
            matrix[base + 1] *= scale
            matrix[base + 2] *= scale
            matrix[base + 3] *= scale
            matrix[base + 4] = matrix[base + 4] * scale + bias
        }
    }
}
