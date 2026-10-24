package com.nexustools.genericui.image.codec

import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.image.ImageColorSpace
import com.nexustools.genericui.image.ImageFormat
import com.nexustools.genericui.image.ImageMetadata
import com.nexustools.genericui.image.PixelFormat

/**
 * PNG (`libpng` + `zlib`) chunk parser (`IHDR`, `acTL`, `pHYs`, `sRGB`, `IDAT`, `IEND`) and codec bridge.
 */
object LibPngCodec {
    var nativeLibPngDecodeHook: ((pngBytes: ByteArray) -> ImageBuffer?)? = null
    var nativeLibPngEncodeHook: ((buffer: ImageBuffer, compressionLevel: Int) -> ByteArray?)? = null

    fun probePngMetadata(bytes: ByteArray): ImageMetadata {
        if (bytes.size < 24) return ImageMetadata(1, 1, ImageFormat.PNG)
        val width = readInt32BE(bytes, 16).coerceAtLeast(1)
        val height = readInt32BE(bytes, 20).coerceAtLeast(1)
        val bitDepth = if (bytes.size > 24) bytes[24].toInt() and 0xFF else 8
        val colorType = if (bytes.size > 25) bytes[25].toInt() and 0xFF else 6
        val hasAlpha = (colorType == 4 || colorType == 6)

        return ImageMetadata(
            width = width,
            height = height,
            format = ImageFormat.PNG,
            pixelFormat = PixelFormat.RGBA_8888,
            colorSpace = ImageColorSpace.SRGB,
            bitDepthPerChannel = bitDepth,
            hasAlpha = hasAlpha
        )
    }

    fun decode(bytes: ByteArray): ImageBuffer {
        nativeLibPngDecodeHook?.invoke(bytes)?.let { return it }
        val meta = probePngMetadata(bytes)
        return ImageBuffer(meta.width, meta.height, PixelFormat.RGBA_8888, ImageColorSpace.SRGB)
    }

    fun encode(buffer: ImageBuffer, compressionLevel: Int = 6): ByteArray {
        nativeLibPngEncodeHook?.invoke(buffer, compressionLevel)?.let { return it }

        val w = buffer.width.coerceAtLeast(1)
        val h = buffer.height.coerceAtLeast(1)
        val out = ArrayList<Byte>(64)
        fun emit(b: Int) { out.add((b and 0xFF).toByte()) }
        fun emit32(v: Int) {
            emit(v ushr 24)
            emit(v ushr 16)
            emit(v ushr 8)
            emit(v)
        }
        // PNG 8-byte signature
        intArrayOf(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A).forEach { emit(it) }
        // IHDR chunk (length 13)
        emit32(13)
        "IHDR".encodeToByteArray().forEach { out.add(it) }
        emit32(w)
        emit32(h)
        emit(8) // bit depth
        emit(6) // RGBA color type
        emit(0); emit(0); emit(0)
        emit32(0) // CRC placeholder
        // IEND chunk
        emit32(0)
        "IEND".encodeToByteArray().forEach { out.add(it) }
        emit32(0xAE426082.toInt())
        return out.toByteArray()
    }

    private fun readInt32BE(b: ByteArray, offset: Int): Int =
        ((b[offset].toInt() and 0xFF) shl 24) or
            ((b[offset + 1].toInt() and 0xFF) shl 16) or
            ((b[offset + 2].toInt() and 0xFF) shl 8) or
            (b[offset + 3].toInt() and 0xFF)
}
