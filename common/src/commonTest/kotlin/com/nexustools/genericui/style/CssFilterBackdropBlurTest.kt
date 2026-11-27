package com.nexustools.genericui.style

import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.image.PixelFormat
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.CssFilter
import com.nexustools.genericui.style.css.CssLength
import com.nexustools.genericui.style.css.CssShadow
import com.nexustools.genericui.style.css.FastBackdropBlurKernel
import kotlin.math.abs
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

    @Test
    fun testParseNestedDropShadowGradHueRotateAndCombinedTenOpChain() {
        val nestedShadow = CssFilter.parse("drop-shadow(0 2px 4px rgba(0, 0, 0, 0.5))")
        assertNotNull(nestedShadow)
        val ds = assertNotNull(nestedShadow.dropShadow)
        assertEquals(0f, ds.offsetX.resolvePx())
        assertEquals(2f, ds.offsetY.resolvePx())
        assertEquals(4f, ds.blurRadius.resolvePx())
        assertEquals(0, ds.color.r)
        assertEquals(0, ds.color.g)
        assertEquals(0, ds.color.b)
        assertTrue(ds.color.a in 127..128)

        val gradHue = CssFilter.parse("hue-rotate(100grad)")
        assertNotNull(gradHue)
        assertEquals(90f, gradHue.hueRotateDeg)

        val turnHue = CssFilter.parse("hue-rotate(0.5turn)")
        assertNotNull(turnHue)
        assertEquals(180f, turnHue.hueRotateDeg)

        val radHue = CssFilter.parse("hue-rotate(3.1415927rad)")
        assertNotNull(radHue)
        assertTrue(abs((radHue.hueRotateDeg ?: 0f) - 180f) < 0.01f)

        val chained = CssFilter.parse(
            "blur(64px) brightness(1.1) contrast(1.2) grayscale(0.2) hue-rotate(100grad) " +
                "invert(0.1) opacity(0.8) saturate(1.5) sepia(0.3) drop-shadow(0 2px 4px rgba(0, 0, 0, 0.5))"
        )
        assertNotNull(chained)
        assertEquals(64f, chained.blur?.resolvePx())
        assertEquals(1.1f, chained.brightness)
        assertEquals(1.2f, chained.contrast)
        assertEquals(0.2f, chained.grayscale)
        assertEquals(90f, chained.hueRotateDeg)
        assertEquals(0.1f, chained.invert)
        assertEquals(0.8f, chained.opacity)
        assertEquals(1.5f, chained.saturate)
        assertEquals(0.3f, chained.sepia)
        assertNotNull(chained.dropShadow)
    }

    @Test
    fun testBlurAbove48pxAllTenOpsAndOpacityBlendingBehavior() {
        assertTrue(
            CssFilter.boxBlurRadiiForGaussianSigma(96f).sum() >
                CssFilter.boxBlurRadiiForGaussianSigma(48f).sum()
        )

        val w = 240
        val h = 160
        val argbSplit = IntArray(w * h) { idx ->
            val x = idx % w
            if (x < w / 2) 0xFFFF0000.toInt() else 0xFF0000FF.toInt()
        }
        val largeBlur = CssFilter.blur(CssLength.px(96f))
        assertTrue(
            largeBlur.applyBackdropToArgbRegion(
                argbPixels = argbSplit,
                bufferWidth = w,
                bufferHeight = h,
                regionX = 20,
                regionY = 20,
                regionWidth = 200,
                regionHeight = 120
            )
        )
        val farFromSeam = argbSplit[80 * w + (w / 2 - 40)]
        val farBlue = farFromSeam and 0xFF
        assertTrue(farBlue > 25, "Expected 96px blur to bleed blue 40px past seam, got B=$farBlue")

        // Verify opacity(...) blends proportionally over backing pixels in applyBackdropToArgbRegion
        // without creating low-alpha holes, while premul/rgba offscreen outputs keep exact filtered alpha.
        val opacityFilter = CssFilter(brightness = 0.5f, opacity = 0.5f)
        val argbOpaqueWhite = IntArray(16 * 16) { 0xFFFFFFFF.toInt() }
        assertTrue(opacityFilter.applyBackdropToArgbRegion(argbOpaqueWhite, 16, 16, 2, 2, 12, 12))
        val blendedPx = argbOpaqueWhite[8 * 16 + 8]
        assertEquals(255, (blendedPx ushr 24) and 0xFF)
        assertTrue(((blendedPx ushr 16) and 0xFF) in 188..194)

        val premulOpaqueWhite = IntArray(16 * 16) { 0xFFFFFFFF.toInt() }
        assertTrue(opacityFilter.applyBackdropToPremulBgraRegion(premulOpaqueWhite, 16, 16, 16, 2, 2, 12, 12))
        val premulPx = premulOpaqueWhite[8 * 16 + 8]
        assertTrue(((premulPx ushr 24) and 0xFF) in 126..130)
        assertTrue(((premulPx ushr 16) and 0xFF) in 62..66)

        val rgbaOpaqueWhite = ByteArray(16 * 16 * 4) { 0xFF.toByte() }
        val rgbaFiltered = opacityFilter.applyBackdropToRgbaBytes(rgbaOpaqueWhite, 16, 16)
        val rgbaIdx = (8 * 16 + 8) * 4
        assertTrue((rgbaFiltered[rgbaIdx].toInt() and 0xFF) in 126..130)
        assertTrue((rgbaFiltered[rgbaIdx + 3].toInt() and 0xFF) in 126..130)

        // All 10 ops combined
        val allTen = CssFilter(
            blur = CssLength.px(12f),
            brightness = 1.1f,
            contrast = 1.15f,
            grayscale = 0.25f,
            hueRotateDeg = 90f,
            invert = 0.1f,
            opacity = 0.75f,
            saturate = 1.3f,
            sepia = 0.2f,
            dropShadow = CssShadow.px(2f, 3f, 4f, color = RgbaColor(0, 0, 0, 128))
        )
        val argbTen = IntArray(32 * 32) { 0xFF336699.toInt() }
        assertTrue(allTen.applyBackdropToArgbRegion(argbTen, 32, 32, 4, 4, 24, 24))
        assertEquals(255, (argbTen[16 * 32 + 16] ushr 24) and 0xFF)

        val premulTen = IntArray(32 * 32) { 0xFF336699.toInt() }
        assertTrue(allTen.applyBackdropToPremulBgraRegion(premulTen, 32, 32, 32, 4, 4, 24, 24))
        assertTrue(((premulTen[16 * 32 + 16] ushr 24) and 0xFF) in 180..220)

        val rgbaTenInput = ByteArray(32 * 32 * 4) { idx ->
            when (idx % 4) {
                0 -> 0x33
                1 -> 0x66
                2 -> 0x99.toByte()
                else -> 0xFF.toByte()
            }
        }
        val rgbaTenOut = allTen.applyBackdropToRgbaBytes(rgbaTenInput, 32, 32)
        assertTrue((rgbaTenOut[(16 * 32 + 16) * 4 + 3].toInt() and 0xFF) in 180..220)
    }
}
