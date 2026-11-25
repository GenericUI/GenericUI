package com.nexustools.genericui.style.css

import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.image.PixelFormat
import com.nexustools.genericui.style.color.RgbaColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CssFilterPremulAndDropShadowTest {

    @Test
    fun testHasDropShadowAndHasActiveEffects() {
        assertFalse(CssFilter.NONE.hasDropShadow)
        assertFalse(CssFilter.NONE.hasActiveEffects)

        val zeroAlphaShadow = CssFilter(
            dropShadow = CssShadow.px(4f, 4f, 6f, color = RgbaColor(0, 0, 0, 0))
        )
        assertFalse(zeroAlphaShadow.hasDropShadow)
        assertFalse(zeroAlphaShadow.hasActiveEffects)

        val visibleShadow = CssFilter(
            dropShadow = CssShadow.px(4f, 4f, 6f, color = RgbaColor(0, 0, 0, 128))
        )
        assertTrue(visibleShadow.hasDropShadow)
        assertTrue(visibleShadow.hasActiveEffects)

        val suppressedByIsNone = visibleShadow.copy(isNone = true)
        assertFalse(suppressedByIsNone.hasDropShadow)
        assertFalse(suppressedByIsNone.hasActiveEffects)
    }

    @Test
    fun testApplyBackdropToPremulBgraRegionEarlyReturnsStrideAndRoundedClip() {
        val w = 24
        val stride = 32
        val h = 24
        val sentinel = 0x12345678
        val pixels = IntArray(stride * h) { idx ->
            val x = idx % stride
            if (x >= w) {
                sentinel
            } else if (x < w / 2) {
                0xFFFF0000.toInt()
            } else {
                0xFF0000FF.toInt()
            }
        }

        // Inactive filter returns false without modifying pixels
        val inactiveCopy = pixels.copyOf()
        assertFalse(
            CssFilter.NONE.applyBackdropToPremulBgraRegion(
                pixels = inactiveCopy,
                strideWords = stride,
                bufferWidth = w,
                bufferHeight = h,
                regionX = 2,
                regionY = 2,
                regionWidth = 20,
                regionHeight = 20
            )
        )
        assertTrue(pixels.contentEquals(inactiveCopy))

        // Effective opacity <= 0.004f returns false without modifying pixels
        val activeBlur = CssFilter.blur(CssLength.px(5f))
        assertFalse(
            activeBlur.applyBackdropToPremulBgraRegion(
                pixels = inactiveCopy,
                strideWords = stride,
                bufferWidth = w,
                bufferHeight = h,
                regionX = 2,
                regionY = 2,
                regionWidth = 20,
                regionHeight = 20,
                componentOpacity = 0.004f
            )
        )
        assertTrue(pixels.contentEquals(inactiveCopy))

        // Active blur with non-tight stride & rounded corners blurs seam, preserves outside corner & stride padding
        val work = pixels.copyOf()
        assertTrue(
            activeBlur.applyBackdropToPremulBgraRegion(
                pixels = work,
                strideWords = stride,
                bufferWidth = w,
                bufferHeight = h,
                regionX = 2,
                regionY = 2,
                regionWidth = 20,
                regionHeight = 20,
                cornerRadiusPx = 8f,
                componentOpacity = 1f
            )
        )
        // Seam pixel inside rounded region gains blue and drops red
        val seamLeft = work[12 * stride + 11]
        val seamR = (seamLeft ushr 16) and 0xFF
        val seamB = seamLeft and 0xFF
        assertTrue(seamR < 245, "Expected red at seam to attenuate, got $seamR")
        assertTrue(seamB > 15, "Expected blue to bleed across seam, got $seamB")

        // Corner outside rounded arc at (2, 2) stays pure red
        assertEquals(0xFFFF0000.toInt(), work[2 * stride + 2])
        // Stride padding words >= bufferWidth remain untouched
        for (y in 0 until h) {
            for (x in w until stride) {
                assertEquals(sentinel, work[y * stride + x])
            }
        }
    }

    @Test
    fun testApplyBackdropToPremulBgraRegionSupportsAllColorOpsAndPartialOpacity() {
        // 50% translucent red in premul BGRA: A=128, R=128, G=0, B=0
        val premulHalfRed = (128 shl 24) or (128 shl 16)
        val w = 20
        val h = 20

        // monocolorWhite ("brightness(0) invert(1)") -> 50% translucent pure white (A=128, R=128, G=128, B=128)
        val monoBuf = IntArray(w * h) { premulHalfRed }
        assertTrue(
            CssFilter.monocolorWhite().applyBackdropToPremulBgraRegion(
                pixels = monoBuf,
                strideWords = w,
                bufferWidth = w,
                bufferHeight = h,
                regionX = 0,
                regionY = 0,
                regionWidth = w,
                regionHeight = h
            )
        )
        val monoPx = monoBuf[10 * w + 10]
        assertEquals(128, (monoPx ushr 24) and 0xFF)
        assertEquals(128, (monoPx ushr 16) and 0xFF)
        assertEquals(128, (monoPx ushr 8) and 0xFF)
        assertEquals(128, monoPx and 0xFF)

        // Combined all color ops + 1/4 downsampled blur + partial componentOpacity
        val bigW = 64
        val bigH = 64
        val bigBuf = IntArray(bigW * bigH) { idx ->
            val x = idx % bigW
            if (x < bigW / 2) 0xFFCC4422.toInt() else 0xFF2244CC.toInt()
        }
        val complexFilter = CssFilter(
            blur = CssLength.px(12f),
            grayscale = 0.2f,
            sepia = 0.15f,
            saturate = 1.25f,
            hueRotateDeg = 25f,
            invert = 0.1f,
            brightness = 1.1f,
            contrast = 1.1f,
            opacity = 0.8f
        )
        assertTrue(
            complexFilter.applyBackdropToPremulBgraRegion(
                pixels = bigBuf,
                strideWords = bigW,
                bufferWidth = bigW,
                bufferHeight = bigH,
                regionX = 4,
                regionY = 4,
                regionWidth = bigW - 8,
                regionHeight = bigH - 8,
                cornerRadiusPx = 6f,
                componentOpacity = 0.6f
            )
        )
        // Verify premultiplied invariant (R,G,B <= A) everywhere and interior pixels changed
        for (px in bigBuf) {
            val a = (px ushr 24) and 0xFF
            val r = (px ushr 16) and 0xFF
            val g = (px ushr 8) and 0xFF
            val b = px and 0xFF
            assertTrue(r <= a && g <= a && b <= a, "Premultiplied invariant violated: a=$a r=$r g=$g b=$b")
        }
        assertTrue(bigBuf[32 * bigW + 16] != 0xFFCC4422.toInt())
        assertEquals(0xFFCC4422.toInt(), bigBuf[4 * bigW + 4])
    }

    @Test
    fun testDropShadowAcrossPremulArgbAndRgbaImageBufferPaths() {
        val w = 32
        val h = 32
        // Centered 10x10 opaque red square at (8..17, 8..17) on transparent background
        fun isSquare(x: Int, y: Int): Boolean = x in 8..17 && y in 8..17

        val sharpShadowFilter = CssFilter(
            dropShadow = CssShadow.px(
                offsetX = 4f,
                offsetY = 3f,
                blurRadius = 0f,
                color = RgbaColor(0, 0, 255, 255)
            )
        )

        // 1. Premul BGRA region path
        val premul = IntArray(w * h) { idx ->
            val x = idx % w
            val y = idx / w
            if (isSquare(x, y)) 0xFFFF0000.toInt() else 0x00000000
        }
        assertTrue(
            sharpShadowFilter.applyBackdropToPremulBgraRegion(
                pixels = premul,
                strideWords = w,
                bufferWidth = w,
                bufferHeight = h,
                regionX = 0,
                regionY = 0,
                regionWidth = w,
                regionHeight = h
            )
        )
        // Original square pixel stays opaque red
        assertEquals(0xFFFF0000.toInt(), premul[12 * w + 12])
        // Shifted shadow-only pixel at (17 + 4 = 21, 17 + 3 = 20) receives pure blue shadow
        assertEquals(0xFF0000FF.toInt(), premul[20 * w + 21])
        // Outside both square and shadow stays transparent
        assertEquals(0, premul[2 * w + 2])

        // 2. Straight ARGB region path
        val straight = IntArray(w * h) { idx ->
            val x = idx % w
            val y = idx / w
            if (isSquare(x, y)) 0xFFFF0000.toInt() else 0x00000000
        }
        assertTrue(sharpShadowFilter.applyBackdropToArgbRegion(straight, w, h, 0, 0, w, h))
        assertEquals(0xFFFF0000.toInt(), straight[12 * w + 12])
        assertEquals(0xFF0000FF.toInt(), straight[20 * w + 21])
        assertEquals(0, straight[2 * w + 2])

        // 3. Foreground ImageBuffer path with blurred semi-transparent shadow and translucent foreground
        val imgBytes = ByteArray(w * h * 4)
        for (y in 0 until h) {
            for (x in 0 until w) {
                if (isSquare(x, y)) {
                    val off = (y * w + x) * 4
                    imgBytes[off] = 255.toByte()
                    imgBytes[off + 1] = 0
                    imgBytes[off + 2] = 0
                    imgBytes[off + 3] = 128.toByte()
                }
            }
        }
        val blurredShadowFilter = CssFilter(
            dropShadow = CssShadow.px(
                offsetX = 4f,
                offsetY = 4f,
                blurRadius = 3f,
                color = RgbaColor(0, 255, 0, 255)
            )
        )
        val filteredImg = blurredShadowFilter.applyToImageBuffer(ImageBuffer(w, h, PixelFormat.RGBA_8888, bytes = imgBytes))
        // At (20, 20) (shifted +4,+4 from square interior), shadow shows strongly in green channel
        val shadowOff = (20 * w + 20) * 4
        val shG = filteredImg.bytes[shadowOff + 1].toInt() and 0xFF
        val shA = filteredImg.bytes[shadowOff + 3].toInt() and 0xFF
        // Source foreground alpha is 128 (~0.502), so blurred shadow alpha at (20,20) must be strictly bounded in (60..128)
        assertTrue(shA in 60..128, "Expected blurred translucent drop shadow alpha in 60..128 at (20,20), got $shA")
        assertTrue(shG > 200, "Expected straight-alpha green drop shadow channel at (20,20), got $shG")

        // At (14, 14) (corner of shifted shadow under alpha=0.502 red foreground), Porter-Duff composites both channels
        val overlapOff = (14 * w + 14) * 4
        val ovR = filteredImg.bytes[overlapOff].toInt() and 0xFF
        val ovG = filteredImg.bytes[overlapOff + 1].toInt() and 0xFF
        assertTrue(ovR > 150, "Expected foreground red over shadow at (14,14), got $ovR")
        assertTrue(ovG in 45..80, "Expected corner blurred shadow green in 45..80 at (14,14), got $ovG")
        // Interior overlap pixel (16, 16) receives stronger blurred shadow contribution than corner (14, 14)
        val innerOverlapOff = (16 * w + 16) * 4
        val innerG = filteredImg.bytes[innerOverlapOff + 1].toInt() and 0xFF
        assertTrue(innerG > ovG && innerG > 75, "Expected stronger interior shadow green at (16,16), got $innerG vs corner $ovG")
    }
}
