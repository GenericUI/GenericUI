package com.nexustools.genericui.style.css

import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.gradient.Gradient.GradientStop
import com.nexustools.genericui.style.gradient.Gradient.LinearGradient
import com.nexustools.genericui.style.gradient.Gradient.RadialGradient
import com.nexustools.genericui.style.gradient.Gradient.RadialGradientShape
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GradientStopNormalizerTest {

    @Test
    fun testLinearGradientAnglesAndCssGradientLineEndpoints() {
        val c0 = RgbaColor.RED
        val c1 = RgbaColor.BLUE

        val deg0 = GradientStopNormalizer.normalizeLinear(LinearGradient(0f, c0, c1), 200f, 100f)
        assertClose(100f, deg0.x1)
        assertClose(100f, deg0.y1)
        assertClose(100f, deg0.x2)
        assertClose(0f, deg0.y2)

        val deg90 = GradientStopNormalizer.normalizeLinear(LinearGradient(90f, c0, c1), 200f, 100f)
        assertClose(0f, deg90.x1)
        assertClose(50f, deg90.y1)
        assertClose(200f, deg90.x2)
        assertClose(50f, deg90.y2)

        val deg180 = GradientStopNormalizer.normalizeLinear(LinearGradient(180f, c0, c1), 200f, 100f)
        assertClose(100f, deg180.x1)
        assertClose(0f, deg180.y1)
        assertClose(100f, deg180.x2)
        assertClose(100f, deg180.y2)

        val deg270 = GradientStopNormalizer.normalizeLinear(LinearGradient(270f, c0, c1), 200f, 100f)
        assertClose(200f, deg270.x1)
        assertClose(50f, deg270.y1)
        assertClose(0f, deg270.x2)
        assertClose(50f, deg270.y2)

        // 45deg on 200x100: CSS gradient line places (0, 100) at t=0 and (200, 0) at t=1
        val deg45 = GradientStopNormalizer.normalizeLinear(LinearGradient(45f, c0, c1), 200f, 100f)
        assertClose(25f, deg45.x1)
        assertClose(125f, deg45.y1)
        assertClose(175f, deg45.x2)
        assertClose(-25f, deg45.y2)
    }

    @Test
    fun testRadialGradientCenterAndRadiiResolution() {
        val ellipse = GradientStopNormalizer.normalizeRadial(
            RadialGradient(
                centerX = 0.25f,
                centerY = 0.75f,
                radiusX = 0.5f,
                radiusY = 0.25f,
                shape = RadialGradientShape.ELLIPSE,
                stops = listOf(GradientStop(RgbaColor.WHITE), GradientStop(RgbaColor.BLACK))
            ),
            width = 200f,
            height = 100f
        )
        assertClose(50f, ellipse.cx)
        assertClose(75f, ellipse.cy)
        assertClose(100f, ellipse.radiusX)
        assertClose(25f, ellipse.radiusY)

        val circle = GradientStopNormalizer.normalizeRadial(
            RadialGradient(RadialGradientShape.CIRCLE, RgbaColor.WHITE, RgbaColor.BLACK),
            width = 200f,
            height = 100f
        )
        assertClose(100f, circle.cx)
        assertClose(50f, circle.cy)
        assertClose(100f, circle.radiusX)
        assertClose(100f, circle.radiusY)
    }

    @Test
    fun testStopNormalizationMonotonicFixupAndEdgeCases() {
        // Empty stops fallback
        val empty = GradientStopNormalizer.normalizeLinear(LinearGradient(180f, emptyList()), 100f, 100f)
        assertEquals(2, empty.positions.size)
        assertEquals(0f, empty.positions[0])
        assertEquals(1f, empty.positions[1])

        // Single stop expands to [0f, 1f]
        val single = GradientStopNormalizer.normalizeLinear(LinearGradient(180f, RgbaColor.GREEN), 100f, 100f)
        assertEquals(2, single.positions.size)
        assertEquals(RgbaColor.GREEN, single.colors[0])
        assertEquals(RgbaColor.GREEN, single.colors[1])

        // Mixed null positions, percent > 1, negative clamp, and non-monotonic clamp
        val messy = LinearGradient(
            angleDegrees = 90f,
            stops = listOf(
                GradientStop(RgbaColor.RED, -0.5f),
                GradientStop(RgbaColor.GREEN, 60f),
                GradientStop(RgbaColor.BLUE, 0.2f),
                GradientStop(RgbaColor.WHITE, null),
                GradientStop(RgbaColor.BLACK, 150f)
            )
        )
        val norm = GradientStopNormalizer.normalizeLinear(messy, 100f, 100f)
        assertEquals(5, norm.positions.size)
        assertClose(0f, norm.positions[0])
        assertClose(0.6f, norm.positions[1])
        assertClose(0.6f, norm.positions[2])
        assertClose(0.8f, norm.positions[3])
        assertClose(1f, norm.positions[4])
        for (i in 1 until norm.positions.size) {
            assertTrue(norm.positions[i] >= norm.positions[i - 1], "Positions must be monotonic")
        }
    }

    private fun assertClose(expected: Float, actual: Float, tol: Float = 0.01f) {
        assertTrue(abs(expected - actual) <= tol, "Expected $expected +- $tol, got $actual")
    }
}
