package com.nexustools.genericui.jvm

import com.nexustools.genericui.image.AnimatedImage
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.image.ImageDecodeOptions
import com.nexustools.genericui.image.ImageEncodeOptions
import com.nexustools.genericui.image.ImageFormat
import com.nexustools.genericui.image.ImageFrame
import com.nexustools.genericui.image.ImageMetadata
import com.nexustools.genericui.image.ImageSource
import com.nexustools.genericui.image.codec.LibJpegCodec
import com.nexustools.genericui.image.codec.LibPngCodec
import com.nexustools.genericui.image.codec.LibWebpCodec
import com.nexustools.genericui.nativeimpl.image.ImageCodecNativeImpl

/**
 * Shared JVM and Android (`minSdk >= 24`) base implementation of [ImageCodecNativeImpl].
 *
 * Resolves bytes via [JvmImageSourceResolver], defers lossless `BMP`/`PPM` handling to
 * `ImageLoader`'s built-in codecs, delegates platform decoding/encoding to [decodePlatform],
 * [decodeAnimatedPlatform], and [encodePlatform], and falls back to [LibJpegCodec],
 * [LibPngCodec], and [LibWebpCodec].
 */
abstract class JvmImageCodecBase(
    installResolverHooks: Boolean = true
) : ImageCodecNativeImpl {

    init {
        if (installResolverHooks) {
            JvmImageSourceResolver.installHooks(
                classLoader = this::class.java.classLoader,
                extraReader = { uriOrPath -> readPlatformResourceBytes(uriOrPath) }
            )
        }
    }

    protected open fun resourceClassLoader(): ClassLoader? = this::class.java.classLoader

    protected open fun readPlatformResourceBytes(uriOrPath: String): ByteArray? = null

    protected open fun probeMetadataPlatform(
        bytes: ByteArray,
        detectedFormat: ImageFormat
    ): ImageMetadata? = null

    protected open fun decodePlatform(
        bytes: ByteArray,
        detectedFormat: ImageFormat?,
        options: ImageDecodeOptions
    ): ImageBuffer? = null

    protected open fun decodeAnimatedPlatform(
        source: ImageSource,
        options: ImageDecodeOptions
    ): AnimatedImage? = null

    protected open fun encodePlatform(
        buffer: ImageBuffer,
        options: ImageEncodeOptions
    ): ByteArray? = null

    protected open fun resolveSourceBytes(source: ImageSource): ByteArray? =
        JvmImageSourceResolver.resolveSourceBytes(
            source = source,
            classLoader = resourceClassLoader(),
            extraReader = { uriOrPath -> readPlatformResourceBytes(uriOrPath) }
        )

    override fun probeMetadata(source: ImageSource): ImageMetadata? {
        source.memoryBuffer?.let { buf ->
            return ImageMetadata(
                width = buf.width,
                height = buf.height,
                format = ImageFormat.RAW_RGBA,
                pixelFormat = buf.pixelFormat,
                colorSpace = buf.colorSpace
            )
        }
        val bytes = resolveSourceBytes(source) ?: return null
        if (bytes.isEmpty()) return null
        val detected = ImageFormat.detectFromHeader(bytes)
        runCatching { probeMetadataPlatform(bytes, detected) }.getOrNull()?.let { return it }
        return when (detected) {
            ImageFormat.JPEG -> LibJpegCodec.probeJpegMetadata(bytes)
            ImageFormat.PNG -> LibPngCodec.probePngMetadata(bytes)
            ImageFormat.WEBP -> LibWebpCodec.probeWebpMetadata(bytes)
            else -> null
        }
    }

    override fun decode(source: ImageSource, options: ImageDecodeOptions): ImageBuffer? {
        source.memoryBuffer?.let { return it.copy() }
        val bytes = resolveSourceBytes(source) ?: return null
        if (bytes.isEmpty()) return null

        val detected = ImageFormat.detectFromHeader(bytes)
        if (detected == ImageFormat.BMP || detected == ImageFormat.PPM) {
            // Let ImageLoader's built-in lossless BMP/PPM codecs handle these directly
            return null
        }

        runCatching { decodePlatform(bytes, detected, options) }.getOrNull()?.let { return it }

        return when (detected) {
            ImageFormat.JPEG -> LibJpegCodec.decode(bytes)
            ImageFormat.PNG -> LibPngCodec.decode(bytes)
            ImageFormat.WEBP -> LibWebpCodec.decode(bytes)
            else -> null
        }
    }

    override fun decodeAnimated(source: ImageSource, options: ImageDecodeOptions): AnimatedImage? {
        runCatching { decodeAnimatedPlatform(source, options) }.getOrNull()?.let { return it }
        val frame = decode(source, options) ?: return null
        return AnimatedImage(
            width = frame.width,
            height = frame.height,
            frames = listOf(ImageFrame(0, frame, durationMs = 100))
        )
    }

    override fun encode(buffer: ImageBuffer, options: ImageEncodeOptions): ByteArray? {
        if (options.format == ImageFormat.BMP || options.format == ImageFormat.PPM) {
            return null
        }
        if (options.format == ImageFormat.RAW_RGBA) {
            return buffer.toRgba8888().bytes
        }

        runCatching { encodePlatform(buffer, options) }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it }

        return when (options.format) {
            ImageFormat.JPEG -> LibJpegCodec.encode(buffer, options.quality)
            ImageFormat.PNG -> LibPngCodec.encode(buffer, options.compressionLevel)
            else -> null
        }
    }

    override fun dispose() {}
}
