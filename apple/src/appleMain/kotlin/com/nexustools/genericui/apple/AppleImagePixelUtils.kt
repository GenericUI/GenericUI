package com.nexustools.genericui.apple

import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.image.ImageColorSpace
import com.nexustools.genericui.image.PixelFormat
import kotlin.math.min

internal object AppleImagePixelUtils {
    fun unpremultiplyRgba(src: ByteArray): ByteArray {
        val out = src.copyOf()
        var i = 0
        while (i + 3 < out.size) {
            val a = out[i + 3].toInt() and 0xFF
            if (a in 1..254) {
                out[i] = min(255, ((out[i].toInt() and 0xFF) * 255 + a / 2) / a).toByte()
                out[i + 1] = min(255, ((out[i + 1].toInt() and 0xFF) * 255 + a / 2) / a).toByte()
                out[i + 2] = min(255, ((out[i + 2].toInt() and 0xFF) * 255 + a / 2) / a).toByte()
            }
            i += 4
        }
        return out
    }

    fun convertRgbaToTargetFormat(rgbaBuffer: ImageBuffer, targetFormat: PixelFormat): ImageBuffer {
        if (targetFormat == PixelFormat.RGBA_8888) return rgbaBuffer
        val w = rgbaBuffer.width
        val h = rgbaBuffer.height
        val src = rgbaBuffer.bytes
        val pxCount = w * h
        return when (targetFormat) {
            PixelFormat.BGRA_8888 -> {
                val dst = ByteArray(pxCount * 4)
                var i = 0
                while (i + 3 < dst.size) {
                    dst[i] = src[i + 2]
                    dst[i + 1] = src[i + 1]
                    dst[i + 2] = src[i]
                    dst[i + 3] = src[i + 3]
                    i += 4
                }
                ImageBuffer(w, h, PixelFormat.BGRA_8888, rgbaBuffer.colorSpace, dst)
            }
            PixelFormat.RGB_888 -> {
                val dst = ByteArray(pxCount * 3)
                var s = 0
                var d = 0
                while (s + 3 < src.size && d + 2 < dst.size) {
                    dst[d] = src[s]
                    dst[d + 1] = src[s + 1]
                    dst[d + 2] = src[s + 2]
                    s += 4
                    d += 3
                }
                ImageBuffer(w, h, PixelFormat.RGB_888, rgbaBuffer.colorSpace, dst)
            }
            PixelFormat.GRAY_8 -> {
                val dst = ByteArray(pxCount)
                var s = 0
                var d = 0
                while (s + 3 < src.size && d < dst.size) {
                    val r = src[s].toInt() and 0xFF
                    val g = src[s + 1].toInt() and 0xFF
                    val b = src[s + 2].toInt() and 0xFF
                    dst[d] = ((77 * r + 150 * g + 29 * b) ushr 8).toByte()
                    s += 4
                    d++
                }
                ImageBuffer(w, h, PixelFormat.GRAY_8, rgbaBuffer.colorSpace, dst)
            }
            PixelFormat.GRAY_ALPHA_88 -> {
                val dst = ByteArray(pxCount * 2)
                var s = 0
                var d = 0
                while (s + 3 < src.size && d + 1 < dst.size) {
                    val r = src[s].toInt() and 0xFF
                    val g = src[s + 1].toInt() and 0xFF
                    val b = src[s + 2].toInt() and 0xFF
                    dst[d] = ((77 * r + 150 * g + 29 * b) ushr 8).toByte()
                    dst[d + 1] = src[s + 3]
                    s += 4
                    d += 2
                }
                ImageBuffer(w, h, PixelFormat.GRAY_ALPHA_88, rgbaBuffer.colorSpace, dst)
            }
            else -> rgbaBuffer
        }
    }

    fun toPremultipliedRgba8888(buffer: ImageBuffer): ByteArray {
        val rgba = if (buffer.pixelFormat == PixelFormat.RGBA_8888) {
            buffer.bytes.copyOf()
        } else {
            buffer.toRgba8888().bytes
        }
        var i = 0
        while (i + 3 < rgba.size) {
            val a = rgba[i + 3].toInt() and 0xFF
            if (a in 0..254) {
                rgba[i] = (((rgba[i].toInt() and 0xFF) * a + 127) / 255).toByte()
                rgba[i + 1] = (((rgba[i + 1].toInt() and 0xFF) * a + 127) / 255).toByte()
                rgba[i + 2] = (((rgba[i + 2].toInt() and 0xFF) * a + 127) / 255).toByte()
            }
            i += 4
        }
        return rgba
    }
}
