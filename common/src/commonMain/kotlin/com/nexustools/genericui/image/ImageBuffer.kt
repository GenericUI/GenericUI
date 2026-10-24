package com.nexustools.genericui.image

import com.nexustools.genericui.style.RgbaColor

/**
 * In-memory pixel buffer used throughout GenericUI (`ImageView`, [ImageLoader], and
 * `:extensions:imageprocessing` for offscreen GPU-accelerated image manipulation).
 */
class ImageBuffer(
    val width: Int,
    val height: Int,
    val pixelFormat: PixelFormat = PixelFormat.RGBA_8888,
    val colorSpace: ImageColorSpace = ImageColorSpace.SRGB,
    val bytes: ByteArray = ByteArray(width.coerceAtLeast(1) * height.coerceAtLeast(1) * pixelFormat.bytesPerPixel)
) {
    val strideBytes: Int
        get() = width * pixelFormat.bytesPerPixel

    val pixelCount: Int
        get() = width * height

    /**
     * Reads the color at `(x, y)` as an [RgbaColor].
     */
    fun getPixel(x: Int, y: Int): RgbaColor {
        if (x !in 0 until width || y !in 0 until height) return RgbaColor.TRANSPARENT
        val idx = (y * width + x) * pixelFormat.bytesPerPixel
        return when (pixelFormat) {
            PixelFormat.RGBA_8888 -> RgbaColor(
                r = bytes[idx].toInt() and 0xFF,
                g = bytes[idx + 1].toInt() and 0xFF,
                b = bytes[idx + 2].toInt() and 0xFF,
                a = (bytes[idx + 3].toInt() and 0xFF) / 255f
            )
            PixelFormat.BGRA_8888 -> RgbaColor(
                r = bytes[idx + 2].toInt() and 0xFF,
                g = bytes[idx + 1].toInt() and 0xFF,
                b = bytes[idx].toInt() and 0xFF,
                a = (bytes[idx + 3].toInt() and 0xFF) / 255f
            )
            PixelFormat.RGB_888 -> RgbaColor(
                r = bytes[idx].toInt() and 0xFF,
                g = bytes[idx + 1].toInt() and 0xFF,
                b = bytes[idx + 2].toInt() and 0xFF,
                a = 1f
            )
            PixelFormat.GRAY_8 -> {
                val lum = bytes[idx].toInt() and 0xFF
                RgbaColor(lum, lum, lum, 1f)
            }
            PixelFormat.GRAY_ALPHA_88 -> {
                val lum = bytes[idx].toInt() and 0xFF
                val alpha = (bytes[idx + 1].toInt() and 0xFF) / 255f
                RgbaColor(lum, lum, lum, alpha)
            }
            else -> RgbaColor(
                r = bytes[idx].toInt() and 0xFF,
                g = bytes[idx + 1].toInt() and 0xFF,
                b = bytes[idx + 2].toInt() and 0xFF,
                a = (bytes[idx + 3].toInt() and 0xFF) / 255f
            )
        }
    }

    /**
     * Writes an [RgbaColor] at `(x, y)`.
     */
    fun setPixel(x: Int, y: Int, color: RgbaColor) {
        if (x !in 0 until width || y !in 0 until height) return
        val idx = (y * width + x) * pixelFormat.bytesPerPixel
        val alphaByte = color.a.coerceIn(0, 255).toByte()
        when (pixelFormat) {
            PixelFormat.RGBA_8888 -> {
                bytes[idx] = color.r.toByte()
                bytes[idx + 1] = color.g.toByte()
                bytes[idx + 2] = color.b.toByte()
                bytes[idx + 3] = alphaByte
            }
            PixelFormat.BGRA_8888 -> {
                bytes[idx] = color.b.toByte()
                bytes[idx + 1] = color.g.toByte()
                bytes[idx + 2] = color.r.toByte()
                bytes[idx + 3] = alphaByte
            }
            PixelFormat.RGB_888 -> {
                bytes[idx] = color.r.toByte()
                bytes[idx + 1] = color.g.toByte()
                bytes[idx + 2] = color.b.toByte()
            }
            PixelFormat.GRAY_8 -> {
                val lum = ((color.r * 77 + color.g * 150 + color.b * 29) shr 8).coerceIn(0, 255)
                bytes[idx] = lum.toByte()
            }
            PixelFormat.GRAY_ALPHA_88 -> {
                val lum = ((color.r * 77 + color.g * 150 + color.b * 29) shr 8).coerceIn(0, 255)
                bytes[idx] = lum.toByte()
                bytes[idx + 1] = alphaByte
            }
            else -> {
                bytes[idx] = color.r.toByte()
                bytes[idx + 1] = color.g.toByte()
                bytes[idx + 2] = color.b.toByte()
                bytes[idx + 3] = alphaByte
            }
        }
    }

    /**
     * Fills the entire buffer with [color].
     */
    fun fill(color: RgbaColor): ImageBuffer = apply {
        for (y in 0 until height) {
            for (x in 0 until width) {
                setPixel(x, y, color)
            }
        }
    }

    /**
     * Returns a deep copy of this [ImageBuffer].
     */
    fun copy(): ImageBuffer = ImageBuffer(
        width = width,
        height = height,
        pixelFormat = pixelFormat,
        colorSpace = colorSpace,
        bytes = bytes.copyOf()
    )

    /**
     * Converts this buffer to [PixelFormat.RGBA_8888] (or returns a copy if already RGBA_8888).
     */
    fun toRgba8888(): ImageBuffer {
        if (pixelFormat == PixelFormat.RGBA_8888) return copy()
        val dst = ImageBuffer(width, height, PixelFormat.RGBA_8888, colorSpace)
        for (y in 0 until height) {
            for (x in 0 until width) {
                dst.setPixel(x, y, getPixel(x, y))
            }
        }
        return dst
    }

    /**
     * Extracts a rectangular sub-region as a new [ImageBuffer].
     */
    fun crop(cropX: Int, cropY: Int, cropWidth: Int, cropHeight: Int): ImageBuffer {
        val safeW = cropWidth.coerceAtLeast(1)
        val safeH = cropHeight.coerceAtLeast(1)
        val dst = ImageBuffer(safeW, safeH, pixelFormat, colorSpace)
        for (dy in 0 until safeH) {
            for (dx in 0 until safeW) {
                dst.setPixel(dx, dy, getPixel(cropX + dx, cropY + dy))
            }
        }
        return dst
    }

    /**
     * Returns a horizontally mirrored copy of this [ImageBuffer].
     */
    fun flipHorizontal(): ImageBuffer {
        val dst = ImageBuffer(width, height, pixelFormat, colorSpace)
        for (y in 0 until height) {
            for (x in 0 until width) {
                dst.setPixel(width - 1 - x, y, getPixel(x, y))
            }
        }
        return dst
    }

    /**
     * Returns a vertically mirrored copy of this [ImageBuffer].
     */
    fun flipVertical(): ImageBuffer {
        val dst = ImageBuffer(width, height, pixelFormat, colorSpace)
        for (y in 0 until height) {
            for (x in 0 until width) {
                dst.setPixel(x, height - 1 - y, getPixel(x, y))
            }
        }
        return dst
    }

    /**
     * Returns a copy rotated 90 degrees clockwise (`width` and `height` swapped).
     */
    fun rotate90Clockwise(): ImageBuffer {
        val dst = ImageBuffer(height, width, pixelFormat, colorSpace)
        for (y in 0 until height) {
            for (x in 0 until width) {
                dst.setPixel(height - 1 - y, x, getPixel(x, y))
            }
        }
        return dst
    }
}
