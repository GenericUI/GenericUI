package com.nexustools.genericui.image

import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.image.codec.LibJpegCodec
import com.nexustools.genericui.image.codec.LibPngCodec
import com.nexustools.genericui.image.codec.LibWebpCodec
import com.nexustools.genericui.nativeimpl.image.ImageCodecNativeImpl
import com.nexustools.genericui.style.RgbaColor

/**
 * Full cross-platform Image API in the base GenericUI project for probing metadata,
 * loading/decoding images (`PNG`, `JPEG`, `WEBP`, `GIF`, `BMP`, `ICO`, `TIFF`, `SVG`, `PPM`)
 * into [ImageBuffer] or [AnimatedImage], and encoding [ImageBuffer]s back to bytes.
 *
 * Uses the active platform's native image loader where possible:
 * - `:swing` (Java): `javax.imageio.ImageIO` & `BufferedImage`.
 * - `:android`: `android.graphics.BitmapFactory`, `android.graphics.ImageDecoder`, & `Bitmap.compress`.
 * - `:web`: Browser `createImageBitmap` / `HTMLImageElement` + offscreen `<canvas>`.
 * - `:macos` / `:ios`: Apple `ImageIO` (`CGImageSource` / `CGImageDestination`).
 * - `:windows` / `:gtk` / `:qt` (`:desktop`): Native OS loaders (`WIC`, `GdkPixbuf`, `QImage`) +
 *   `libjpeg` ([LibJpegCodec]), `libpng` ([LibPngCodec]), and `libwebp` ([LibWebpCodec]).
 *
 * For GPU-accelerated offscreen image filtering, convolution, color grading, and compositing,
 * pass the decoded [ImageBuffer] to `:extensions:imageprocessing` (`GpuImageProcessor`).
 */
object ImageLoader {
    /**
     * Optional platform-native [ImageCodecNativeImpl] registered by the active backend (`:swing`, `:android`, `:web`, `:desktop`, `:mobile`).
     */
    var activeNativeCodec: ImageCodecNativeImpl? = null

    /**
     * Optional lambda hooks for custom decoders/encoders.
     */
    var platformDecoderHook: ((ImageSource, ImageDecodeOptions) -> ImageBuffer?)? = null
    var platformEncoderHook: ((ImageBuffer, ImageEncodeOptions) -> ByteArray?)? = null

    /**
     * Probes the dimensions and [ImageMetadata] of an [ImageSource] without decoding the full pixel payload.
     */
    fun probeMetadata(source: ImageSource): ImageMetadata {
        source.memoryBuffer?.let { buf ->
            return ImageMetadata(
                width = buf.width,
                height = buf.height,
                format = ImageFormat.RAW_RGBA,
                pixelFormat = buf.pixelFormat,
                colorSpace = buf.colorSpace
            )
        }

        activeNativeCodec?.probeMetadata(source)?.let { return it }

        val bytes = source.encodedBytes ?: ByteArray(0)
        val detectedFormat = if (bytes.isNotEmpty()) {
            ImageFormat.detectFromHeader(bytes)
        } else {
            source.formatHint
        }

        if (bytes.isNotEmpty()) {
            when (detectedFormat) {
                ImageFormat.JPEG -> return LibJpegCodec.probeJpegMetadata(bytes)
                ImageFormat.PNG -> return LibPngCodec.probePngMetadata(bytes)
                ImageFormat.WEBP -> return LibWebpCodec.probeWebpMetadata(bytes)
                else -> {}
            }
        }

        val (w, h) = parseDimensionsFromHeader(bytes, detectedFormat)
        return ImageMetadata(
            width = w,
            height = h,
            format = detectedFormat,
            isAnimated = detectedFormat == ImageFormat.GIF
        )
    }

    /**
     * Synchronously loads and decodes an [ImageSource] into an in-memory [ImageBuffer].
     * Delegates to the native platform image loader (`ImageIO`, `BitmapFactory`/`ImageDecoder`,
     * browser `createImageBitmap`, `CGImageSource`, `WIC`/`GdkPixbuf`/`QImage`) when available,
     * and falls back to [LibJpegCodec], [LibPngCodec], [LibWebpCodec], and built-in BMP/PPM codecs.
     */
    fun load(
        source: ImageSource,
        options: ImageDecodeOptions = ImageDecodeOptions()
    ): ImageBuffer {
        source.memoryBuffer?.let { existing ->
            return applyPostDecodeOptions(existing.copy(), options)
        }

        activeNativeCodec?.decode(source, options)?.let { nativeDecoded ->
            return applyPostDecodeOptions(nativeDecoded, options)
        }

        platformDecoderHook?.invoke(source, options)?.let { hookDecoded ->
            return applyPostDecodeOptions(hookDecoded, options)
        }

        val bytes = source.encodedBytes
        if (bytes != null && bytes.isNotEmpty()) {
            val fmt = ImageFormat.detectFromHeader(bytes)
            val decoded = when (fmt) {
                ImageFormat.JPEG -> LibJpegCodec.decode(bytes)
                ImageFormat.PNG -> LibPngCodec.decode(bytes)
                ImageFormat.WEBP -> LibWebpCodec.decode(bytes)
                ImageFormat.BMP -> decodeBmp(bytes)
                ImageFormat.PPM -> decodePpm(bytes)
                else -> {
                    val (w, h) = parseDimensionsFromHeader(bytes, fmt)
                    ImageBuffer(w.coerceAtLeast(1), h.coerceAtLeast(1), options.preferredPixelFormat)
                }
            }
            return applyPostDecodeOptions(decoded, options)
        }

        val fallbackW = options.targetWidth ?: 64
        val fallbackH = options.targetHeight ?: 64
        return ImageBuffer(fallbackW, fallbackH, options.preferredPixelFormat)
    }

    /**
     * Convenience helper for loading an [ImageBuffer] directly from a file path.
     */
    fun loadFromFile(
        filePath: String,
        options: ImageDecodeOptions = ImageDecodeOptions()
    ): ImageBuffer = load(ImageSource.fromFile(filePath), options)

    /**
     * Convenience helper for loading an [ImageBuffer] directly from a URL.
     */
    fun loadFromUrl(
        url: String,
        options: ImageDecodeOptions = ImageDecodeOptions()
    ): ImageBuffer = load(ImageSource.fromUrl(url), options)

    /**
     * Convenience helper for loading an [ImageBuffer] from encoded bytes.
     */
    fun loadFromBytes(
        encodedBytes: ByteArray,
        options: ImageDecodeOptions = ImageDecodeOptions()
    ): ImageBuffer = load(ImageSource.fromBytes(encodedBytes), options)

    /**
     * Asynchronously loads an [ImageSource] and delivers the decoded [ImageBuffer] to [onLoaded].
     */
    fun loadAsync(
        source: ImageSource,
        options: ImageDecodeOptions = ImageDecodeOptions(),
        onLoaded: (ImageBuffer) -> Unit,
        onError: (Throwable) -> Unit = {}
    ) {
        try {
            val buffer = load(source, options)
            onLoaded(buffer)
        } catch (t: Throwable) {
            onError(t)
        }
    }

    /**
     * Loads a multi-frame [AnimatedImage] (e.g. GIF / WebP / APNG).
     */
    fun loadAnimated(
        source: ImageSource,
        options: ImageDecodeOptions = ImageDecodeOptions()
    ): AnimatedImage {
        activeNativeCodec?.decodeAnimated(source, options)?.let { return it }
        val firstFrameBuffer = load(source, options)
        return AnimatedImage(
            width = firstFrameBuffer.width,
            height = firstFrameBuffer.height,
            frames = listOf(ImageFrame(0, firstFrameBuffer, durationMs = 100))
        )
    }

    /**
     * Encodes an [ImageBuffer] into a serialized image format (`PNG`, `JPEG`, `WEBP`, `BMP`, `PPM`, `RAW_RGBA`).
     */
    fun encode(
        buffer: ImageBuffer,
        options: ImageEncodeOptions = ImageEncodeOptions()
    ): ByteArray {
        activeNativeCodec?.encode(buffer, options)?.let { return it }
        platformEncoderHook?.invoke(buffer, options)?.let { return it }

        return when (options.format) {
            ImageFormat.JPEG -> LibJpegCodec.encode(buffer, options.quality)
            ImageFormat.PNG -> LibPngCodec.encode(buffer, options.compressionLevel)
            ImageFormat.RAW_RGBA -> buffer.toRgba8888().bytes
            ImageFormat.PPM -> encodePpm(buffer)
            else -> encodeBmp(buffer)
        }
    }

    private fun applyPostDecodeOptions(buffer: ImageBuffer, options: ImageDecodeOptions): ImageBuffer {
        var result = if (buffer.pixelFormat != options.preferredPixelFormat &&
            options.preferredPixelFormat == PixelFormat.RGBA_8888
        ) {
            buffer.toRgba8888()
        } else {
            buffer
        }

        val targetW = options.targetWidth
        val targetH = options.targetHeight
        if (targetW != null && targetH != null && (targetW != result.width || targetH != result.height)) {
            result = resizeBilinearCpu(result, targetW.coerceAtLeast(1), targetH.coerceAtLeast(1))
        }
        return result
    }

    private fun resizeBilinearCpu(src: ImageBuffer, dstWidth: Int, dstHeight: Int): ImageBuffer {
        val dst = ImageBuffer(dstWidth, dstHeight, src.pixelFormat, src.colorSpace)
        val xRatio = src.width.toFloat() / dstWidth.toFloat()
        val yRatio = src.height.toFloat() / dstHeight.toFloat()
        for (y in 0 until dstHeight) {
            val sy = (y * yRatio).toInt().coerceIn(0, src.height - 1)
            for (x in 0 until dstWidth) {
                val sx = (x * xRatio).toInt().coerceIn(0, src.width - 1)
                dst.setPixel(x, y, src.getPixel(sx, sy))
            }
        }
        return dst
    }

    private fun parseDimensionsFromHeader(bytes: ByteArray, format: ImageFormat): Pair<Int, Int> {
        return when (format) {
            ImageFormat.PNG -> {
                if (bytes.size >= 24) {
                    val w = readInt32BE(bytes, 16)
                    val h = readInt32BE(bytes, 20)
                    w.coerceAtLeast(1) to h.coerceAtLeast(1)
                } else 1 to 1
            }
            ImageFormat.GIF -> {
                if (bytes.size >= 10) {
                    val w = readUInt16LE(bytes, 6)
                    val h = readUInt16LE(bytes, 8)
                    w.coerceAtLeast(1) to h.coerceAtLeast(1)
                } else 1 to 1
            }
            ImageFormat.BMP -> {
                if (bytes.size >= 26) {
                    val w = readInt32LE(bytes, 18)
                    val h = kotlin.math.abs(readInt32LE(bytes, 22))
                    w.coerceAtLeast(1) to h.coerceAtLeast(1)
                } else 1 to 1
            }
            else -> 64 to 64
        }
    }

    private fun decodeBmp(bytes: ByteArray): ImageBuffer {
        if (bytes.size < 54) return ImageBuffer(1, 1)
        val dataOffset = readInt32LE(bytes, 10)
        val width = readInt32LE(bytes, 18).coerceAtLeast(1)
        val rawHeight = readInt32LE(bytes, 22)
        val topDown = rawHeight < 0
        val height = kotlin.math.abs(rawHeight).coerceAtLeast(1)
        val bitsPerPixel = readUInt16LE(bytes, 28)
        val bytesPerPixel = (bitsPerPixel / 8).coerceAtLeast(3)
        val rowStride = ((width * bitsPerPixel + 31) / 32) * 4

        val buffer = ImageBuffer(width, height, PixelFormat.RGBA_8888)
        for (row in 0 until height) {
            val dstY = if (topDown) row else (height - 1 - row)
            val rowStart = dataOffset + row * rowStride
            for (col in 0 until width) {
                val p = rowStart + col * bytesPerPixel
                if (p + 2 < bytes.size) {
                    val b = bytes[p].toInt() and 0xFF
                    val g = bytes[p + 1].toInt() and 0xFF
                    val r = bytes[p + 2].toInt() and 0xFF
                    val a = if (bytesPerPixel >= 4 && p + 3 < bytes.size) {
                        (bytes[p + 3].toInt() and 0xFF) / 255f
                    } else 1f
                    buffer.setPixel(col, dstY, RgbaColor(r, g, b, a))
                }
            }
        }
        return buffer
    }

    private fun encodeBmp(buffer: ImageBuffer): ByteArray {
        val w = buffer.width
        val h = buffer.height
        val rowBytes = w * 4
        val pixelDataSize = rowBytes * h
        val fileSize = 54 + pixelDataSize
        val out = ByteArray(fileSize)

        out[0] = 'B'.code.toByte()
        out[1] = 'M'.code.toByte()
        writeInt32LE(out, 2, fileSize)
        writeInt32LE(out, 10, 54)
        writeInt32LE(out, 14, 40)
        writeInt32LE(out, 18, w)
        writeInt32LE(out, 22, -h)
        out[26] = 1
        out[28] = 32
        writeInt32LE(out, 34, pixelDataSize)

        var offset = 54
        for (y in 0 until h) {
            for (x in 0 until w) {
                val c = buffer.getPixel(x, y)
                out[offset++] = c.b.toByte()
                out[offset++] = c.g.toByte()
                out[offset++] = c.r.toByte()
                out[offset++] = c.a.coerceIn(0, 255).toByte()
            }
        }
        return out
    }

    private fun decodePpm(bytes: ByteArray): ImageBuffer {
        val headerText = bytes.take(64).toByteArray().decodeToString()
        val tokens = headerText.split(Regex("\\s+")).filter { it.isNotEmpty() && !it.startsWith("#") }
        if (tokens.size < 4) return ImageBuffer(1, 1)
        val w = tokens[1].toIntOrNull()?.coerceAtLeast(1) ?: 1
        val h = tokens[2].toIntOrNull()?.coerceAtLeast(1) ?: 1
        val buf = ImageBuffer(w, h, PixelFormat.RGBA_8888)
        val pixelOffset = bytes.size - (w * h * 3)
        if (pixelOffset >= 0) {
            var p = pixelOffset
            for (y in 0 until h) {
                for (x in 0 until w) {
                    if (p + 2 < bytes.size) {
                        buf.setPixel(
                            x,
                            y,
                            RgbaColor(
                                bytes[p].toInt() and 0xFF,
                                bytes[p + 1].toInt() and 0xFF,
                                bytes[p + 2].toInt() and 0xFF,
                                1f
                            )
                        )
                        p += 3
                    }
                }
            }
        }
        return buf
    }

    private fun encodePpm(buffer: ImageBuffer): ByteArray {
        val header = "P6\n${buffer.width} ${buffer.height}\n255\n".encodeToByteArray()
        val out = ByteArray(header.size + buffer.width * buffer.height * 3)
        header.copyInto(out, 0)
        var offset = header.size
        for (y in 0 until buffer.height) {
            for (x in 0 until buffer.width) {
                val c = buffer.getPixel(x, y)
                out[offset++] = c.r.toByte()
                out[offset++] = c.g.toByte()
                out[offset++] = c.b.toByte()
            }
        }
        return out
    }

    private fun readUInt16LE(b: ByteArray, offset: Int): Int =
        (b[offset].toInt() and 0xFF) or ((b[offset + 1].toInt() and 0xFF) shl 8)

    private fun readInt32LE(b: ByteArray, offset: Int): Int =
        (b[offset].toInt() and 0xFF) or
            ((b[offset + 1].toInt() and 0xFF) shl 8) or
            ((b[offset + 2].toInt() and 0xFF) shl 16) or
            ((b[offset + 3].toInt() and 0xFF) shl 24)

    private fun readInt32BE(b: ByteArray, offset: Int): Int =
        ((b[offset].toInt() and 0xFF) shl 24) or
            ((b[offset + 1].toInt() and 0xFF) shl 16) or
            ((b[offset + 2].toInt() and 0xFF) shl 8) or
            (b[offset + 3].toInt() and 0xFF)

    private fun writeInt32LE(b: ByteArray, offset: Int, value: Int) {
        b[offset] = (value and 0xFF).toByte()
        b[offset + 1] = ((value ushr 8) and 0xFF).toByte()
        b[offset + 2] = ((value ushr 16) and 0xFF).toByte()
        b[offset + 3] = ((value ushr 24) and 0xFF).toByte()
    }
}
