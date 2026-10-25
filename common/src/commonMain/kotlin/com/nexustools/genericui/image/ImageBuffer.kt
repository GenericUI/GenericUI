package com.nexustools.genericui.image

import com.nexustools.genericui.style.RgbaColor

/**
 * In-memory pixel buffer used throughout GenericUI (`ImageView`, [ImageLoader], and
 * `:extensions:imageprocessing` for offscreen GPU-accelerated image manipulation).
 */
open class ImageBuffer(
    width: Int,
    height: Int,
    val pixelFormat: PixelFormat = PixelFormat.RGBA_8888,
    val colorSpace: ImageColorSpace = ImageColorSpace.SRGB,
    bytes: ByteArray = ByteArray(width.coerceAtLeast(1) * height.coerceAtLeast(1) * pixelFormat.bytesPerPixel)
) {
    open var width: Int = width.coerceAtLeast(1)
        protected set

    open var height: Int = height.coerceAtLeast(1)
        protected set

    open var bytes: ByteArray = bytes
        protected set

    val strideBytes: Int
        get() = width * pixelFormat.bytesPerPixel

    val pixelCount: Int
        get() = width * height

    protected fun updateDimensionsAndBytes(newWidth: Int, newHeight: Int, newBytes: ByteArray) {
        this.width = newWidth.coerceAtLeast(1)
        this.height = newHeight.coerceAtLeast(1)
        this.bytes = newBytes
    }

    /**
     * Reads the color at `(x, y)` as an [RgbaColor].
     */
    open fun getPixel(x: Int, y: Int): RgbaColor {
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
    open fun setPixel(x: Int, y: Int, color: RgbaColor) {
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
    open fun fill(color: RgbaColor): ImageBuffer = apply {
        val alphaByte = color.a.coerceIn(0, 255).toByte()
        if (color.r == 0 && color.g == 0 && color.b == 0 && alphaByte == 0.toByte()) {
            bytes.fill(0)
            return@apply
        }
        when (pixelFormat) {
            PixelFormat.RGBA_8888 -> {
                val rB = color.r.toByte()
                val gB = color.g.toByte()
                val bB = color.b.toByte()
                var i = 0
                val limit = bytes.size - 3
                while (i < limit) {
                    bytes[i] = rB
                    bytes[i + 1] = gB
                    bytes[i + 2] = bB
                    bytes[i + 3] = alphaByte
                    i += 4
                }
            }
            PixelFormat.BGRA_8888 -> {
                val rB = color.r.toByte()
                val gB = color.g.toByte()
                val bB = color.b.toByte()
                var i = 0
                val limit = bytes.size - 3
                while (i < limit) {
                    bytes[i] = bB
                    bytes[i + 1] = gB
                    bytes[i + 2] = rB
                    bytes[i + 3] = alphaByte
                    i += 4
                }
            }
            else -> {
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        setPixel(x, y, color)
                    }
                }
            }
        }
    }

    /**
     * Returns `true` if every pixel in this buffer has zero alpha (`a == 0`).
     */
    fun isFullyTransparent(): Boolean {
        if (pixelFormat != PixelFormat.RGBA_8888 && pixelFormat != PixelFormat.BGRA_8888) {
            return false
        }
        var i = 3
        val size = bytes.size
        while (i < size) {
            if (bytes[i] != 0.toByte()) return false
            i += 4
        }
        return true
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
     * Returns `this` buffer directly without copying when already [PixelFormat.RGBA_8888],
     * or converts to [PixelFormat.RGBA_8888] otherwise.
     */
    fun asRgba8888(): ImageBuffer =
        if (pixelFormat == PixelFormat.RGBA_8888) this else toRgba8888()

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
