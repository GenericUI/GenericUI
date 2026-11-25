package com.nexustools.genericui.style.css

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CssFilterColorMatrixTest {

    @Test
    fun testIdentityAndMonocolorOrder() {
        val out = FloatArray(4)

        val identity = CssFilterColorMatrix.buildMatrix4x5(CssFilter.NONE)
        assertEquals(20, identity.size)
        CssFilterColorMatrix.applyToRgba(identity, 0.25f, 0.5f, 0.75f, 0.8f, out)
        assertClose(0.25f, out[0])
        assertClose(0.5f, out[1])
        assertClose(0.75f, out[2])
        assertClose(0.8f, out[3])

        // brightness(0) invert(1) must yield pure white (1, 1, 1, a)
        val whiteMatrix = CssFilterColorMatrix.buildMatrix4x5(CssFilter.monocolorWhite())
        CssFilterColorMatrix.applyToRgba(whiteMatrix, 0.1f, 0.4f, 0.9f, 0.6f, out)
        assertClose(1f, out[0])
        assertClose(1f, out[1])
        assertClose(1f, out[2])
        assertClose(0.6f, out[3])

        // Non-zero brightness(0.5) with invert(1) applies invert before brightness: (1 - x) * 0.5
        val dimInverted = CssFilterColorMatrix.buildMatrix4x5(CssFilter(brightness = 0.5f, invert = 1f))
        CssFilterColorMatrix.applyToRgba(dimInverted, 0f, 0.5f, 1f, 1f, out)
        assertClose(0.5f, out[0])
        assertClose(0.25f, out[1])
        assertClose(0f, out[2])
        assertClose(1f, out[3])
    }

    @Test
    fun testParityWithFastBackdropBlurKernel() {
        val filters = listOf(
            CssFilter(grayscale = 1f),
            CssFilter(sepia = 0.7f),
            CssFilter(saturate = 1.6f),
            CssFilter(hueRotateDeg = 90f),
            CssFilter(invert = 0.75f),
            CssFilter(brightness = 1.2f),
            CssFilter(contrast = 1.3f),
            CssFilter(opacity = 0.65f),
            CssFilter(
                grayscale = 0.25f,
                sepia = 0.1f,
                saturate = 1.2f,
                hueRotateDeg = 45f,
                invert = 0.15f,
                brightness = 1.05f,
                contrast = 1.15f,
                opacity = 0.8f
            )
        )
        val samplePixels = listOf(
            intArrayOf(80, 140, 200, 255),
            intArrayOf(180, 90, 60, 200),
            intArrayOf(128, 128, 128, 180)
        )
        val matOut = FloatArray(4)
        for (filter in filters) {
            val matrix = CssFilterColorMatrix.buildMatrix4x5(filter)
            for (px in samplePixels) {
                val rgba = byteArrayOf(
                    px[0].toByte(),
                    px[1].toByte(),
                    px[2].toByte(),
                    px[3].toByte()
                )
                val cpuOut = FastBackdropBlurKernel.applyColorOpsToRgbaBytes(filter, rgba, 1, 1)
                CssFilterColorMatrix.applyToRgba(
                    matrix = matrix,
                    r = px[0] / 255f,
                    g = px[1] / 255f,
                    b = px[2] / 255f,
                    a = px[3] / 255f,
                    outRgba = matOut
                )
                for (c in 0 until 4) {
                    val cpuChan = (cpuOut[c].toInt() and 0xFF) / 255f
                    assertTrue(
                        abs(cpuChan - matOut[c]) <= 0.02f,
                        "Mismatch on channel $c for $filter: cpu=$cpuChan matrix=${matOut[c]}"
                    )
                }
            }
        }
    }

    private fun assertClose(expected: Float, actual: Float, tol: Float = 0.001f) {
        assertTrue(abs(expected - actual) <= tol, "Expected $expected +- $tol, got $actual")
    }
}
