@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.apple

import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.image.ImageColorSpace
import com.nexustools.genericui.image.ImageDecodeOptions
import com.nexustools.genericui.image.ImageEncodeOptions
import com.nexustools.genericui.image.ImageFormat
import com.nexustools.genericui.image.ImageSource
import com.nexustools.genericui.image.PixelFormat
import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.DoubleVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataCreateMutable
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDictionaryCreate
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFNumberCreate
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.kCFNumberDoubleType
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.CoreGraphics.CGColorRenderingIntent
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGDataProviderCreateWithCFData
import platform.CoreGraphics.CGDataProviderRelease
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageCreate
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.kCGBitmapByteOrder32Big
import platform.ImageIO.CGImageDestinationAddImage
import platform.ImageIO.CGImageDestinationCreateWithData
import platform.ImageIO.CGImageDestinationFinalize
import platform.ImageIO.kCGImagePropertyAPNGDelayTime
import platform.ImageIO.kCGImagePropertyGIFDelayTime
import platform.ImageIO.kCGImagePropertyGIFDictionary
import platform.ImageIO.kCGImagePropertyPNGDictionary
import platform.posix.memcpy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AppleImageIoAndSvgTest {

    private fun solidBuffer(w: Int, h: Int, r: Int, g: Int, b: Int, a: Int = 255): ImageBuffer {
        val bytes = ByteArray(w * h * 4)
        var i = 0
        while (i + 3 < bytes.size) {
            bytes[i] = r.toByte()
            bytes[i + 1] = g.toByte()
            bytes[i + 2] = b.toByte()
            bytes[i + 3] = a.toByte()
            i += 4
        }
        return ImageBuffer(w, h, PixelFormat.RGBA_8888, ImageColorSpace.SRGB, bytes)
    }

    private fun buildAnimatedContainer(
        uti: String,
        dictKey: platform.CoreFoundation.CFStringRef?,
        delayKey: platform.CoreFoundation.CFStringRef?,
        frames: List<Pair<ImageBuffer, Double>>
    ): ByteArray {
        val mutableData = CFDataCreateMutable(null, 0L)!!
        val utiCf = CFStringCreateWithCString(null, uti, kCFStringEncodingUTF8)!!
        val dest = CGImageDestinationCreateWithData(mutableData, utiCf, frames.size.toULong(), null)!!
        CFRelease(utiCf)

        try {
            for ((buf, delaySec) in frames) {
                val cfData = buf.bytes.usePinned { pinned ->
                    CFDataCreate(null, pinned.addressOf(0).reinterpret(), buf.bytes.size.toLong())
                }!!
                val provider = CGDataProviderCreateWithCFData(cfData)!!
                CFRelease(cfData)
                val cs = CGColorSpaceCreateDeviceRGB()!!
                val bitmapInfo = kCGBitmapByteOrder32Big or CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value
                val cgImg = CGImageCreate(
                    buf.width.toULong(),
                    buf.height.toULong(),
                    8uL,
                    32uL,
                    (buf.width * 4).toULong(),
                    cs,
                    bitmapInfo,
                    provider,
                    null,
                    true,
                    CGColorRenderingIntent.kCGRenderingIntentDefault
                )!!
                CGDataProviderRelease(provider)
                CGColorSpaceRelease(cs)

                val frameProps: CFDictionaryRef? = memScoped {
                    val delayVal = alloc<DoubleVar>()
                    delayVal.value = delaySec
                    val numRef = CFNumberCreate(null, kCFNumberDoubleType, delayVal.ptr)!!

                    val innerKeys = allocArray<COpaquePointerVar>(1)
                    val innerVals = allocArray<COpaquePointerVar>(1)
                    innerKeys[0] = delayKey
                    innerVals[0] = numRef
                    val innerDict = CFDictionaryCreate(
                        null,
                        innerKeys,
                        innerVals,
                        1L,
                        kCFTypeDictionaryKeyCallBacks.ptr,
                        kCFTypeDictionaryValueCallBacks.ptr
                    )!!
                    CFRelease(numRef)

                    val outerKeys = allocArray<COpaquePointerVar>(1)
                    val outerVals = allocArray<COpaquePointerVar>(1)
                    outerKeys[0] = dictKey
                    outerVals[0] = innerDict
                    val outerDict = CFDictionaryCreate(
                        null,
                        outerKeys,
                        outerVals,
                        1L,
                        kCFTypeDictionaryKeyCallBacks.ptr,
                        kCFTypeDictionaryValueCallBacks.ptr
                    )
                    CFRelease(innerDict)
                    outerDict
                }

                try {
                    CGImageDestinationAddImage(dest, cgImg, frameProps)
                } finally {
                    if (frameProps != null) CFRelease(frameProps)
                    CGImageRelease(cgImg)
                }
            }
            assertTrue(CGImageDestinationFinalize(dest))
            val len = CFDataGetLength(mutableData).toInt()
            val ptr = CFDataGetBytePtr(mutableData)!!
            val out = ByteArray(len)
            out.usePinned { pinned ->
                memcpy(pinned.addressOf(0), ptr, len.toULong())
            }
            return out
        } finally {
            CFRelease(dest)
            CFRelease(mutableData)
        }
    }

    @Test
    fun encodeAndDecodePngJpegBmpTiffAndProbeMetadata() {
        val codec = AppleImageIoCodecNativeImpl()
        try {
            val src = ImageBuffer(16, 12, PixelFormat.RGBA_8888, ImageColorSpace.SRGB)
            for (y in 0 until 12) {
                for (x in 0 until 16) {
                    val off = (y * 16 + x) * 4
                    src.bytes[off] = if (x < 8) 220.toByte() else 30.toByte()
                    src.bytes[off + 1] = 140.toByte()
                    src.bytes[off + 2] = 60.toByte()
                    src.bytes[off + 3] = 255.toByte()
                }
            }

            val pngBytes = codec.encode(src, ImageEncodeOptions(format = ImageFormat.PNG))
            assertNotNull(pngBytes)
            assertTrue(pngBytes.size > 20)

            val pngMeta = codec.probeMetadata(ImageSource.fromBytes(pngBytes))
            assertNotNull(pngMeta)
            assertEquals(ImageFormat.PNG, pngMeta.format)
            assertEquals(16, pngMeta.width)
            assertEquals(12, pngMeta.height)
            assertEquals(1, pngMeta.frameCount)

            val decodedPng = codec.decode(ImageSource.fromBytes(pngBytes), ImageDecodeOptions())
            assertNotNull(decodedPng)
            assertEquals(16, decodedPng.width)
            assertEquals(12, decodedPng.height)
            val leftPx = decodedPng.getPixel(2, 2)
            assertTrue(leftPx.r > 180)

            val scaledPng = codec.decode(
                ImageSource.fromBytes(pngBytes),
                ImageDecodeOptions(targetWidth = 8, targetHeight = 6, preferredPixelFormat = PixelFormat.BGRA_8888)
            )
            assertNotNull(scaledPng)
            assertEquals(8, scaledPng.width)
            assertEquals(6, scaledPng.height)
            assertEquals(PixelFormat.BGRA_8888, scaledPng.pixelFormat)

            for (fmt in listOf(ImageFormat.JPEG, ImageFormat.BMP, ImageFormat.TIFF, ImageFormat.GIF)) {
                val encoded = codec.encode(src, ImageEncodeOptions(format = fmt, quality = 90))
                assertNotNull(encoded, "Expected non-null encoding for $fmt")
                val meta = codec.probeMetadata(ImageSource.fromBytes(encoded))
                assertNotNull(meta)
                assertEquals(fmt, meta.format)
                val dec = codec.decode(ImageSource.fromBytes(encoded), ImageDecodeOptions())
                assertNotNull(dec)
                assertEquals(16, dec.width)
                assertEquals(12, dec.height)
            }
        } finally {
            codec.dispose()
        }
    }

    @Test
    fun rasterizeSvgWithShapesPathsAndTargets() {
        val svg = """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100" width="100" height="100">
              <rect x="0" y="0" width="100" height="100" fill="#00FF00" />
              <path d="M 10 10 L 40 10 L 40 40 L 10 40 Z" fill="rgb(255, 0, 0)" />
              <circle cx="75" cy="75" r="15" fill="#0000FF" />
            </svg>
        """.trimIndent().encodeToByteArray()

        val codec = AppleImageIoCodecNativeImpl()
        try {
            val meta = codec.probeMetadata(ImageSource.fromBytes(svg))
            assertNotNull(meta)
            assertEquals(ImageFormat.SVG, meta.format)
            assertEquals(100, meta.width)
            assertEquals(100, meta.height)

            val rendered = codec.decode(ImageSource.fromBytes(svg), ImageDecodeOptions())
            assertNotNull(rendered)
            assertEquals(100, rendered.width)
            assertEquals(100, rendered.height)

            // Verify red square at (20, 20), blue circle at (75, 75), green background at (80, 20)
            val redPx = rendered.getPixel(20, 20)
            assertTrue(redPx.r > 220 && redPx.g < 30 && redPx.b < 30, "Unexpected red pixel: $redPx")

            val bluePx = rendered.getPixel(75, 75)
            assertTrue(bluePx.b > 220 && bluePx.r < 30 && bluePx.g < 30, "Unexpected blue pixel: $bluePx")

            val greenPx = rendered.getPixel(80, 20)
            assertTrue(greenPx.g > 220 && greenPx.r < 30 && greenPx.b < 30, "Unexpected green pixel: $greenPx")

            val halfSize = codec.decode(
                ImageSource.fromBytes(svg),
                ImageDecodeOptions(targetWidth = 50, targetHeight = 50)
            )
            assertNotNull(halfSize)
            assertEquals(50, halfSize.width)
            assertEquals(50, halfSize.height)
        } finally {
            codec.dispose()
        }
    }

    @Test
    fun decodeAnimatedGifAndApngExtractsRealPerFrameDelays() {
        val codec = AppleImageIoCodecNativeImpl()
        try {
            val frames = listOf(
                solidBuffer(8, 8, 255, 0, 0) to 0.04,
                solidBuffer(8, 8, 0, 255, 0) to 0.12,
                solidBuffer(8, 8, 0, 0, 255) to 0.25
            )
            val animatedGifBytes = buildAnimatedContainer(
                uti = AppleUniformTypeHelper.utiForImageFormat(ImageFormat.GIF)!!,
                dictKey = kCGImagePropertyGIFDictionary,
                delayKey = kCGImagePropertyGIFDelayTime,
                frames = frames
            )

            val gifMeta = codec.probeMetadata(ImageSource.fromBytes(animatedGifBytes))
            assertNotNull(gifMeta)
            assertEquals(ImageFormat.GIF, gifMeta.format)
            assertTrue(gifMeta.isAnimated)
            assertEquals(3, gifMeta.frameCount)

            val decodedGif = codec.decodeAnimated(ImageSource.fromBytes(animatedGifBytes), ImageDecodeOptions())
            assertNotNull(decodedGif)
            assertEquals(3, decodedGif.frames.size)
            assertEquals(40, decodedGif.frames[0].durationMs)
            assertEquals(120, decodedGif.frames[1].durationMs)
            assertEquals(250, decodedGif.frames[2].durationMs)
            assertTrue(decodedGif.frames[0].buffer.getPixel(2, 2).r > 200)
            assertTrue(decodedGif.frames[1].buffer.getPixel(2, 2).g > 200)
            assertTrue(decodedGif.frames[2].buffer.getPixel(2, 2).b > 200)
            assertEquals(1, decodedGif.frameAtTimeMs(50).frameIndex)

            val animatedApngBytes = buildAnimatedContainer(
                uti = AppleUniformTypeHelper.utiForImageFormat(ImageFormat.PNG)!!,
                dictKey = kCGImagePropertyPNGDictionary,
                delayKey = kCGImagePropertyAPNGDelayTime,
                frames = listOf(
                    solidBuffer(6, 6, 255, 255, 0) to 0.06,
                    solidBuffer(6, 6, 0, 255, 255) to 0.18
                )
            )
            val decodedApng = codec.decodeAnimated(ImageSource.fromBytes(animatedApngBytes), ImageDecodeOptions())
            assertNotNull(decodedApng)
            assertEquals(2, decodedApng.frames.size)
            assertEquals(60, decodedApng.frames[0].durationMs)
            assertEquals(180, decodedApng.frames[1].durationMs)
        } finally {
            codec.dispose()
        }
    }
}
