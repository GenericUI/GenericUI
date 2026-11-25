@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.apple.style

import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.CssCornerRadii
import com.nexustools.genericui.style.css.CssFilter
import com.nexustools.genericui.style.css.CssLength
import com.nexustools.genericui.style.css.CssShadow
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGBitmapContextCreateImage
import platform.CoreGraphics.CGContextFillRect
import platform.CoreGraphics.CGContextSetFillColorWithColor
import platform.CoreGraphics.CGImageRef
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGRectMake
import platform.CoreImage.CIImage
import platform.Foundation.NSNumber
import platform.Foundation.valueForKey
import platform.QuartzCore.CALayer
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AppleSurfaceAndBackdropFilterTest {

    @Test
    fun cornerRadiiClampingScalesProportionalOverflow() {
        val clamped = AppleStyledSurfaceHelper.resolveCornerRadiiPx(
            radii = CssCornerRadii.px(60, 60, 60, 60),
            fallbackUniformPx = 0f,
            width = 40.0,
            height = 20.0
        )
        assertEquals(4, clamped.size)
        // Minimum side ratio = 20 / 120 = 1/6 -> 60 * (1/6) = 10
        assertTrue(abs(clamped[0] - 10.0) < 0.01)
        assertTrue(abs(clamped[2] - 10.0) < 0.01)

        val layer = CALayer()
        val appliedRadius = AppleStyledSurfaceHelper.applyLayerCornerRadii(
            layer = layer,
            radii = CssCornerRadii.px(12, 12, 0, 0),
            fallbackUniformPx = 0f,
            boundsWidth = 100.0,
            boundsHeight = 60.0,
            clipsToBounds = true
        )
        assertEquals(12.0, appliedRadius)
        assertEquals(12.0, layer.cornerRadius)

        val zeroWidthLayer = CALayer()
        val zeroWidthRadius = AppleStyledSurfaceHelper.applyLayerCornerRadii(
            layer = zeroWidthLayer,
            radii = CssCornerRadii.px(999, 999, 999, 999),
            fallbackUniformPx = 999f,
            boundsWidth = 0.0,
            boundsHeight = 4.0,
            clipsToBounds = true
        )
        assertEquals(0.0, zeroWidthRadius)
        assertEquals(0.0, zeroWidthLayer.cornerRadius)
    }

    @Test
    fun coreImageFilterBuilderCoversAllTenW3cFunctions() {
        val fullFilter = CssFilter(
            blur = CssLength.px(10f),
            brightness = 1.2f,
            contrast = 1.1f,
            grayscale = 0.5f,
            hueRotateDeg = 45f,
            invert = 0.2f,
            opacity = 0.9f,
            saturate = 1.5f,
            sepia = 0.3f,
            dropShadow = CssShadow.px(offsetX = 4f, offsetY = 6f, blurRadius = 8f, color = RgbaColor(0, 0, 0, 180))
        )
        assertTrue(AppleBackdropFilterController.requiresFullFilterPipeline(fullFilter))
        val chain = AppleBackdropFilterController.buildCoreImageFilters(fullFilter)
        val names = chain.map { it.name }
        assertTrue("CIAffineClamp" in names)
        assertTrue("CIGaussianBlur" in names)
        assertTrue("CIColorMatrix" in names)
        assertTrue("CISourceOverCompositing" in names)

        val blurFilter = chain.firstOrNull { it.name == "CIGaussianBlur" }
        assertNotNull(blurFilter)
        assertEquals(10.0, (blurFilter.valueForKey("inputRadius") as? NSNumber)?.doubleValue)
    }

    @Test
    fun premulBgraAndCoreImageRenderProduceFilteredPixels() {
        val width = 16
        val height = 16
        val pixels = IntArray(width * height)
        var createdCgImage: CGImageRef? = null
        val painted = AppleBackdropFilterController.renderPremulBgraToCgContext(pixels, width, height) { ctx ->
            AppleStyledSurfaceHelper.withCgColor(RgbaColor(200, 40, 20, 255)) { cgColor ->
                CGContextSetFillColorWithColor(ctx, cgColor)
                CGContextFillRect(ctx, CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()))
            }
            createdCgImage = CGBitmapContextCreateImage(ctx)
        }
        assertTrue(painted)
        val cgImage = createdCgImage
        assertNotNull(cgImage)
        try {
            val grayscaleFilter = CssFilter(grayscale = 1.0f, brightness = 1.0f)
            val filtered = AppleBackdropFilterController.renderCgImageWithFilterToPremulBgra(
                source = cgImage,
                filter = grayscaleFilter,
                width = width,
                height = height
            )
            assertNotNull(filtered)
            val centerPixel = filtered[(height / 2) * width + (width / 2)]
            val r = (centerPixel ushr 16) and 0xFF
            val g = (centerPixel ushr 8) and 0xFF
            val b = centerPixel and 0xFF
            // Grayscale collapses distinct R/G/B into nearly identical channel luminance values
            assertTrue(kotlin.math.abs(r - g) <= 2 && kotlin.math.abs(g - b) <= 2)

            val ciFiltered = AppleBackdropFilterController.applyToCiImage(
                source = CIImage.imageWithCGImage(cgImage),
                filter = CssFilter(
                    blur = CssLength.px(3f),
                    invert = 1.0f,
                    dropShadow = CssShadow.px(1f, 2f, 2f, color = RgbaColor(0, 0, 0, 200))
                )
            )
            assertTrue(ciFiltered.extent.useContents { size.width > 0.0 && size.height > 0.0 })
        } finally {
            CGImageRelease(cgImage)
        }

        assertTrue(
            AppleBackdropFilterController.applyBackdropToPremulBgraRegion(
                filter = CssFilter(blur = CssLength.px(4f), saturate = 1.4f),
                pixels = pixels,
                strideWords = width,
                bufferWidth = width,
                bufferHeight = height,
                regionX = 0,
                regionY = 0,
                regionWidth = width,
                regionHeight = height,
                cornerRadiusPx = 4f,
                componentOpacity = 1f
            )
        )
    }
}
