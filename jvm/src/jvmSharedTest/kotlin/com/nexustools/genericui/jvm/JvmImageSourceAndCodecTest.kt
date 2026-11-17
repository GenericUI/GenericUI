package com.nexustools.genericui.jvm

import com.nexustools.genericui.image.AnimatedImage
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.image.ImageDecodeOptions
import com.nexustools.genericui.image.ImageEncodeOptions
import com.nexustools.genericui.image.ImageFormat
import com.nexustools.genericui.image.ImageFrame
import com.nexustools.genericui.image.ImageSource
import com.nexustools.genericui.image.PixelFormat
import com.nexustools.genericui.image.cache.ImageCacheManager
import com.nexustools.genericui.image.codec.LibPngCodec
import com.nexustools.genericui.style.color.RgbaColor
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JvmImageSourceAndCodecTest {

    @AfterTest
    fun tearDown() {
        ImageCacheManager.localResourceReaderHook = null
        ImageCacheManager.urlFetcherHook = null
        JvmImageSourceResolver.platformResourceReaderHook = null
        ImageCacheManager.clearCache()
    }

    @Test
    fun imageSourceResolverReadsLocalFilesFileUrisAndExtraHook() {
        val tempDir = File(System.getProperty("java.io.tmpdir", "."), "gui_img_res_${System.nanoTime()}").apply {
            mkdirs()
        }
        try {
            val imgFile = File(tempDir, "sample.png")
            val buffer = ImageBuffer(2, 2, PixelFormat.RGBA_8888).apply {
                setPixel(0, 0, RgbaColor(255, 10, 20, 255))
                setPixel(1, 1, RgbaColor(10, 20, 255, 255))
            }
            val pngBytes = LibPngCodec.encode(buffer, 6)
            imgFile.writeBytes(pngBytes)

            val direct = JvmImageSourceResolver.readLocalOrResourceBytes(imgFile.absolutePath)
            assertNotNull(direct)
            assertContentEquals(pngBytes, direct)

            val viaUri = JvmImageSourceResolver.readLocalOrResourceBytes(imgFile.toURI().toString() + "?cache=1#frag")
            assertNotNull(viaUri)
            assertContentEquals(pngBytes, viaUri)

            val tripleSlash = "file://" + imgFile.toURI().rawPath
            val viaTripleSlash = JvmImageSourceResolver.readLocalOrResourceBytes(tripleSlash)
            assertNotNull(viaTripleSlash)
            assertContentEquals(pngBytes, viaTripleSlash)

            val customPayload = byteArrayOf(7, 8, 9)
            val viaHook = JvmImageSourceResolver.readLocalOrResourceBytes(
                uriOrPath = "content://media/external/images/42",
                extraReader = { uri -> if (uri == "content://media/external/images/42") customPayload else null }
            )
            assertNotNull(viaHook)
            assertContentEquals(customPayload, viaHook)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun imageCodecBaseHandlesMemoryPngJpegRawAndBmpDeferral() {
        var platformDecodeCalled = false
        var platformEncodeCalled = false
        val codec = object : JvmImageCodecBase(installResolverHooks = true) {
            override val codecBackendName: String = "test-jvm-codec"

            override fun decodePlatform(
                bytes: ByteArray,
                detectedFormat: ImageFormat?,
                options: ImageDecodeOptions
            ): ImageBuffer? {
                platformDecodeCalled = true
                return null
            }

            override fun encodePlatform(
                buffer: ImageBuffer,
                options: ImageEncodeOptions
            ): ByteArray? {
                platformEncodeCalled = true
                return null
            }

            override fun decodeAnimatedPlatform(
                source: ImageSource,
                options: ImageDecodeOptions
            ): AnimatedImage? {
                val frame = decode(source, options) ?: return null
                return AnimatedImage(
                    width = frame.width,
                    height = frame.height,
                    frames = listOf(ImageFrame(0, frame, durationMs = 50))
                )
            }
        }

        val srcBuf = ImageBuffer(4, 3, PixelFormat.RGBA_8888).apply {
            fill(RgbaColor(40, 120, 200, 255))
        }

        // Memory buffer probe & decode
        val memSrc = ImageSource.fromBuffer(srcBuf)
        val memMeta = codec.probeMetadata(memSrc)
        assertNotNull(memMeta)
        assertEquals(4, memMeta.width)
        assertEquals(3, memMeta.height)
        assertEquals(ImageFormat.RAW_RGBA, memMeta.format)
        assertEquals(4, codec.decode(memSrc, ImageDecodeOptions())?.width)

        // Encode PNG & RAW_RGBA, defer BMP/PPM
        val encodedPng = codec.encode(srcBuf, ImageEncodeOptions(format = ImageFormat.PNG))
        assertNotNull(encodedPng)
        assertTrue(platformEncodeCalled)
        assertEquals(ImageFormat.PNG, ImageFormat.detectFromHeader(encodedPng))
        assertNull(codec.encode(srcBuf, ImageEncodeOptions(format = ImageFormat.BMP)))
        assertNull(codec.encode(srcBuf, ImageEncodeOptions(format = ImageFormat.PPM)))
        assertContentEquals(srcBuf.bytes, codec.encode(srcBuf, ImageEncodeOptions(format = ImageFormat.RAW_RGBA)))

        // Probe & decode PNG fallback via LibPngCodec
        val pngSrc = ImageSource.fromBytes(encodedPng)
        val pngMeta = codec.probeMetadata(pngSrc)
        assertNotNull(pngMeta)
        assertEquals(4, pngMeta.width)
        assertEquals(3, pngMeta.height)
        assertEquals(ImageFormat.PNG, pngMeta.format)
        val decoded = codec.decode(pngSrc, ImageDecodeOptions())
        assertNotNull(decoded)
        assertTrue(platformDecodeCalled)
        assertEquals(40, decoded.getPixel(1, 1).r)
        assertEquals(120, decoded.getPixel(1, 1).g)
        assertEquals(200, decoded.getPixel(1, 1).b)

        // Animated fallback wrapper
        val anim = codec.decodeAnimated(pngSrc, ImageDecodeOptions())
        assertNotNull(anim)
        assertEquals(1, anim.frames.size)
        assertEquals(50, anim.frames[0].durationMs)

        // BMP header defers decode to ImageLoader
        val bmpHeader = byteArrayOf('B'.code.toByte(), 'M'.code.toByte(), 0, 0, 0, 0)
        assertNull(codec.decode(ImageSource.fromBytes(bmpHeader), ImageDecodeOptions()))

        // JPEG encode/decode roundtrip via LibJpegCodec fallback
        val encodedJpeg = codec.encode(srcBuf, ImageEncodeOptions(format = ImageFormat.JPEG, quality = 90))
        assertNotNull(encodedJpeg)
        assertEquals(ImageFormat.JPEG, ImageFormat.detectFromHeader(encodedJpeg))
        val jpegMeta = codec.probeMetadata(ImageSource.fromBytes(encodedJpeg))
        assertNotNull(jpegMeta)
        assertEquals(4, jpegMeta.width)
        assertEquals(3, jpegMeta.height)
        assertNotNull(ImageCacheManager.localResourceReaderHook)
        assertNotNull(ImageCacheManager.urlFetcherHook)
    }
}
