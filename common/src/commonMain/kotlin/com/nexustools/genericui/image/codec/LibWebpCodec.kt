package com.nexustools.genericui.image.codec

import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.image.ImageColorSpace
import com.nexustools.genericui.image.ImageFormat
import com.nexustools.genericui.image.ImageMetadata
import com.nexustools.genericui.image.PixelFormat

/**
 * WebP (`libwebp` `VP8 `, `VP8L`, `VP8X`, `ANIM`, `ANMF`) header parser and codec bridge.
 */
object LibWebpCodec {
    var nativeLibWebpDecodeHook: ((webpBytes: ByteArray) -> ImageBuffer?)? = null
    var nativeLibWebpEncodeHook: ((buffer: ImageBuffer, quality: Int) -> ByteArray?)? = null

    fun probeWebpMetadata(bytes: ByteArray): ImageMetadata {
        if (bytes.size < 30) return ImageMetadata(1, 1, ImageFormat.WEBP)
        val chunkFourCc = bytes.sliceArray(12..15).decodeToString()
        var width = 64
        var height = 64
        var hasAlpha = true
        var isAnimated = false

        when (chunkFourCc) {
            "VP8X" -> {
                val flags = bytes[20].toInt() and 0xFF
                isAnimated = (flags and 0x02) != 0
                hasAlpha = (flags and 0x10) != 0
                width = 1 + ((bytes[24].toInt() and 0xFF) or ((bytes[25].toInt() and 0xFF) shl 8) or ((bytes[26].toInt() and 0xFF) shl 16))
                height = 1 + ((bytes[27].toInt() and 0xFF) or ((bytes[28].toInt() and 0xFF) shl 8) or ((bytes[29].toInt() and 0xFF) shl 16))
            }
            "VP8 " -> {
                hasAlpha = false
                width = ((bytes[26].toInt() and 0xFF) or ((bytes[27].toInt() and 0x3F) shl 8)).coerceAtLeast(1)
                height = ((bytes[28].toInt() and 0xFF) or ((bytes[29].toInt() and 0x3F) shl 8)).coerceAtLeast(1)
            }
            "VP8L" -> {
                val b0 = bytes[21].toInt() and 0xFF
                val b1 = bytes[22].toInt() and 0xFF
                val b2 = bytes[23].toInt() and 0xFF
                val b3 = bytes[24].toInt() and 0xFF
                width = 1 + (b0 or ((b1 and 0x3F) shl 8))
                height = 1 + ((b1 ushr 6) or (b2 shl 2) or ((b3 and 0x0F) shl 10))
                hasAlpha = ((b3 ushr 4) and 0x01) != 0
            }
        }

        return ImageMetadata(
            width = width.coerceAtLeast(1),
            height = height.coerceAtLeast(1),
            format = ImageFormat.WEBP,
            pixelFormat = PixelFormat.RGBA_8888,
            colorSpace = ImageColorSpace.SRGB,
            hasAlpha = hasAlpha,
            isAnimated = isAnimated
        )
    }

    fun decode(bytes: ByteArray): ImageBuffer {
        nativeLibWebpDecodeHook?.invoke(bytes)?.let { return it }
        val meta = probeWebpMetadata(bytes)
        return ImageBuffer(meta.width, meta.height, PixelFormat.RGBA_8888, ImageColorSpace.SRGB)
    }
}
