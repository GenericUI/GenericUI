package com.nexustools.genericui.style

import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.image.PixelFormat
import com.nexustools.genericui.style.css.CssFilter
import com.nexustools.genericui.style.css.CssLength
import com.nexustools.genericui.style.css.FastBackdropBlurKernel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CssFilterBackdropBlurTest {

    @Test
    fun testBlurAndBackdropRegionBlursSeamWhilePreservingOutsideCorners() {
        val w = 40
        val h = 40
        val argb = IntArray(w * h) { idx ->
            val x = idx % w
            if (x < w / 2) 0xFFFF0000.toInt() else 0xFF0000FF.toInt()
        }

        val filter = CssFilter.blur(CssLength.px(5f))
        assertTrue(filter.hasActiveEffects)
        val applied = filter.applyBackdropToArgbRegion(
            argbPixels = argb,
            bufferWidth = w,
            bufferHeight = h,
            regionX = 4,
            regionY = 4,
            regionWidth = 32,
            regionHeight = 32,
            cornerRadiusPx = 10f
        )
        assertTrue(applied)

        // Pixel right at the left-of-seam inside the blurred region should gain blue channel and lose pure red
        val seamLeft = argb[20 * w + 19]
        val seamLeftR = (seamLeft ushr 16) and 0xFF
        val seamLeftB = seamLeft and 0xFF
        assertTrue(seamLeftR < 245, "Expected red channel near center seam to blur, got $seamLeftR")
        assertTrue(seamLeftB > 15, "Expected blue channel to bleed across seam, got $seamLeftB")

        // Pixel just outside rounded corner arc (top-left of region at (4,4)) must remain pure red
        val outsideCorner = argb[4 * w + 4]
        assertEquals(0xFFFF0000.toInt(), outsideCorner)
    }

    @Test
    fun testBackdropBlurOrFilterCombinesLegacyBlurAndCssFilterColorOps() {
        val combined = CssFilter.backdropBlurOrFilter(
            backdropBlurPx = 4f,
            backdropFilter = CssFilter.brightness(1.2f)
        )
        assertNotNull(combined)
        assertEquals(4f, combined.blur?.resolvePx())
        assertEquals(1.2f, combined.brightness)

        assertNull(CssFilter.backdropBlurOrFilter(4f, CssFilter.NONE))
        assertFalse(CssFilter.NONE.hasActiveEffects)

        val buf = ImageBuffer(
            width = 12,
            height = 12,
            pixelFormat = PixelFormat.RGBA_8888,
            bytes = ByteArray(12 * 12 * 4) { i ->
                when (i % 4) {
                    0 -> 200.toByte()
                    1 -> 100.toByte()
                    2 -> 50.toByte()
                    else -> 255.toByte()
                }
            }
        )
        val sepiaFilter = CssFilter(blur = CssLength.px(2f), sepia = 1f)
        val result = sepiaFilter.applyBackdropToImageBufferRegion(buf, 0, 0, 12, 12)
        assertEquals(12, result.width)
        val g = result.bytes[(6 * 12 + 6) * 4 + 1].toInt() and 0xFF
        assertTrue(g != 100, "Expected sepia + blur to transform green channel")
    }

    @Test
    fun testFastBackdropBlurKernelDownsampledRadiiAndRepeatStability() {
        val w = 160
        val h = 100
        for (blurPx in listOf(6f, 16f)) {
            val argb = IntArray(w * h) { idx ->
                val x = idx % w
                if (x < w / 2) 0xFFFF1010.toInt() else 0xFF1010FF.toInt()
            }
            val filter = CssFilter(blur = CssLength.px(blurPx), saturate = 1.15f)
            // Warm up scratch pool + run repeated frames verifying consistent output
            repeat(5) { pass ->
                val work = argb.copyOf()
                assertTrue(
                    FastBackdropBlurKernel.applyBackdropToArgbRegion(
                        filter = filter,
                        argbPixels = work,
                        bufferWidth = w,
                        bufferHeight = h,
                        regionX = 10,
                        regionY = 10,
                        regionWidth = w - 20,
                        regionHeight = h - 20,
                        cornerRadiusPx = 12f
                    )
                )
                val seamPx = work[50 * w + (w / 2 - 1)]
                val r = (seamPx ushr 16) and 0xFF
                val b = seamPx and 0xFF
                assertTrue(b > 25, "Expected downsampled ${blurPx}px blur (pass=$pass) to pull blue across seam, got R=$r B=$b")
                assertTrue(r < 245, "Expected downsampled ${blurPx}px blur (pass=$pass) to attenuate red across seam, got R=$r B=$b")
                // Outside rounded corner arc at (10,10) stays untouched pure red
                assertEquals(0xFFFF1010.toInt(), work[10 * w + 10])
            }
        }
    }
}
