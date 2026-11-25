package com.nexustools.genericui.jvm.style

import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.CssFilter
import com.nexustools.genericui.style.css.CssLength
import com.nexustools.genericui.style.css.CssShadow
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JvmBackdropCapturePlannerTest {

    @Test
    fun capturePlanChoosesAdaptiveDownsampleAndHardwareEligibility() {
        val pureHeavyBlur = JvmBackdropCapturePlanner.planCapture(
            filter = CssFilter.blur(CssLength.px(20f)),
            targetWidth = 200,
            targetHeight = 160
        )
        assertEquals(4, pureHeavyBlur.downsampleScale)
        assertEquals(50, pureHeavyBlur.captureWidth)
        assertEquals(40, pureHeavyBlur.captureHeight)
        assertEquals(30, pureHeavyBlur.blurPaddingPx)
        assertTrue(pureHeavyBlur.supportsHardwareFastBlurOnly)
        assertEquals(5f, pureHeavyBlur.scaledFilter.blur?.resolvePx())

        val shadowWithBlur = JvmBackdropCapturePlanner.planCapture(
            filter = CssFilter(
                blur = CssLength.px(10f),
                dropShadow = CssShadow.px(offsetX = 12f, offsetY = 4f, blurRadius = 8f, color = RgbaColor(0, 0, 0, 180))
            ),
            targetWidth = 200,
            targetHeight = 120
        )
        assertEquals(1, shadowWithBlur.downsampleScale)
        assertTrue(shadowWithBlur.hasDropShadow)
        assertFalse(shadowWithBlur.supportsHardwareFastBlurOnly)
        assertTrue(shadowWithBlur.blurPaddingPx >= 24)
    }

    @Test
    fun androidColorMatrixScalesTranslationColumnBy255() {
        assertNull(JvmBackdropCapturePlanner.buildAndroidColorMatrix20(CssFilter.blur(CssLength.px(8f))))

        val invertMatrix = JvmBackdropCapturePlanner.buildAndroidColorMatrix20(CssFilter.invert(1f))
        assertNotNull(invertMatrix)
        assertEquals(20, invertMatrix.size)
        assertTrue(abs(invertMatrix[0] - (-1f)) < 1e-4f)
        assertTrue(abs(invertMatrix[4] - 255f) < 1e-3f)
        assertTrue(abs(invertMatrix[9] - 255f) < 1e-3f)
        assertTrue(abs(invertMatrix[14] - 255f) < 1e-3f)
    }
}
