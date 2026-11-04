package com.nexustools.genericui.image

import com.nexustools.genericui.enums.ui.ThemeMode
import com.nexustools.genericui.style.color.ImageTintMode
import com.nexustools.genericui.style.color.RgbaColor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Pixel-level image tinting and automatic monocolor icon detection/recoloring processor.
 *
 * Supports:
 * - [ImageTintMode.SRC_IN]: Replaces RGB with the tint color while preserving alpha.
 * - [ImageTintMode.MULTIPLY]: Multiplies RGB channels by the tint color.
 * - [ImageTintMode.MONOCOLOR_AUTO_THEME]: Inspects non-transparent pixels to determine if
 *   the image is a monocolor icon/glyph (low chromatic saturation and narrow luminance/channel
 *   variance across opaque pixels) and recolors it to match the active theme (or custom tint
 *   color) while leaving full-color images and thumbnails untouched.
 */
object MonocolorImageRecolorProcessor {

    /** Default light-on-dark foreground color used when Dark theme is active (`#F5F5F7`). */
    val DEFAULT_DARK_THEME_ICON_COLOR = RgbaColor(245, 245, 247, 1.0f)

    /** Default dark-on-light foreground color used when Light theme is active (`#1C1E21`). */
    val DEFAULT_LIGHT_THEME_ICON_COLOR = RgbaColor(28, 30, 33, 1.0f)

    /**
     * Returns `true` if the RGBA_8888 pixel buffer represents a monocolor icon or glyph
     * (such as a black or white toolbar icon with anti-aliased alpha edges).
     *
     * Scans non-transparent pixels (`alpha > 16`). If there are no visible pixels, returns `false`.
     * Checks whether `max(r, g, b) - min(r, g, b) <= maxChromaDelta` for at least `92%` of visible
     * pixels and whether opaque pixels (`alpha > 160`) cluster within a narrow luminance range.
     */
    fun isMonocolorRgba(
        rgbaBytes: ByteArray,
        width: Int,
        height: Int,
        maxChromaDelta: Int = 24
    ): Boolean {
        val totalBytes = width * height * 4
        if (width <= 0 || height <= 0 || rgbaBytes.size < totalBytes) return false

        var visibleCount = 0
        var lowChromaCount = 0
        var opaqueCount = 0
        var minLum = 255
        var maxLum = 0

        val step = max(1, (width * height) / 4096) * 4
        var i = 0
        while (i < totalBytes) {
            val a = rgbaBytes[i + 3].toInt() and 0xFF
            if (a > 16) {
                visibleCount++
                val r = rgbaBytes[i].toInt() and 0xFF
                val g = rgbaBytes[i + 1].toInt() and 0xFF
                val b = rgbaBytes[i + 2].toInt() and 0xFF

                val maxCh = max(r, max(g, b))
                val minCh = min(r, min(g, b))
                if (maxCh - minCh <= maxChromaDelta) {
                    lowChromaCount++
                }

                if (a > 160) {
                    opaqueCount++
                    val lum = (r * 77 + g * 150 + b * 29) shr 8
                    if (lum < minLum) minLum = lum
                    if (lum > maxLum) maxLum = lum
                }
            }
            i += step
        }

        if (visibleCount == 0) return false
        if (lowChromaCount * 100 < visibleCount * 92) return false
        if (opaqueCount == 0) return true

        val lumRange = maxLum - minLum
        return lumRange <= 64
    }

    /**
     * Resolves the effective target [RgbaColor] for the given [mode], explicit [tintColor],
     * and [isDarkTheme].
     */
    fun resolveTargetColor(
        mode: ImageTintMode,
        tintColor: RgbaColor?,
        isDarkTheme: Boolean
    ): RgbaColor? {
        return when (mode) {
            ImageTintMode.NONE -> null
            ImageTintMode.SRC_IN, ImageTintMode.MONOCOLOR_AUTO_THEME -> {
                tintColor ?: if (isDarkTheme) {
                    DEFAULT_DARK_THEME_ICON_COLOR
                } else {
                    DEFAULT_LIGHT_THEME_ICON_COLOR
                }
            }
            ImageTintMode.MULTIPLY -> tintColor ?: RgbaColor.WHITE
        }
    }

    /**
     * Returns a new RGBA_8888 [ByteArray] with [mode] applied, or a copy of [rgbaBytes]
     * if no modification is needed (for example when [mode] is [ImageTintMode.NONE] or when
     * [ImageTintMode.MONOCOLOR_AUTO_THEME] determines that the image is multi-colored).
     */
    fun recolorRgbaBytes(
        rgbaBytes: ByteArray,
        width: Int,
        height: Int,
        mode: ImageTintMode,
        tintColor: RgbaColor?,
        isDarkTheme: Boolean
    ): ByteArray {
        if (mode == ImageTintMode.NONE) return rgbaBytes.copyOf()
        val totalBytes = width * height * 4
        if (width <= 0 || height <= 0 || rgbaBytes.size < totalBytes) return rgbaBytes.copyOf()

        if (mode == ImageTintMode.MONOCOLOR_AUTO_THEME) {
            if (!isMonocolorRgba(rgbaBytes, width, height)) {
                return rgbaBytes.copyOf()
            }
        }

        val target = resolveTargetColor(mode, tintColor, isDarkTheme) ?: return rgbaBytes.copyOf()
        val out = rgbaBytes.copyOf()
        val tr = target.r.coerceIn(0, 255)
        val tg = target.g.coerceIn(0, 255)
        val tb = target.b.coerceIn(0, 255)
        val ta = (target.a.coerceIn(0, 255)) / 255f

        var i = 0
        when (mode) {
            ImageTintMode.SRC_IN, ImageTintMode.MONOCOLOR_AUTO_THEME -> {
                while (i < totalBytes) {
                    val a = out[i + 3].toInt() and 0xFF
                    if (a > 0) {
                        out[i] = tr.toByte()
                        out[i + 1] = tg.toByte()
                        out[i + 2] = tb.toByte()
                        if (ta < 0.999f) {
                            out[i + 3] = (a * ta).roundToInt().coerceIn(0, 255).toByte()
                        }
                    }
                    i += 4
                }
            }
            ImageTintMode.MULTIPLY -> {
                while (i < totalBytes) {
                    val a = out[i + 3].toInt() and 0xFF
                    if (a > 0) {
                        val r = out[i].toInt() and 0xFF
                        val g = out[i + 1].toInt() and 0xFF
                        val b = out[i + 2].toInt() and 0xFF
                        out[i] = ((r * tr) / 255).coerceIn(0, 255).toByte()
                        out[i + 1] = ((g * tg) / 255).coerceIn(0, 255).toByte()
                        out[i + 2] = ((b * tb) / 255).coerceIn(0, 255).toByte()
                        if (ta < 0.999f) {
                            out[i + 3] = (a * ta).roundToInt().coerceIn(0, 255).toByte()
                        }
                    }
                    i += 4
                }
            }
            ImageTintMode.NONE -> Unit
        }
        return out
    }

    /**
     * Applies [mode] and [tintColor] to an [ImageBuffer], returning a recolored [ImageBuffer]
     * (or the original [buffer] when [mode] is [ImageTintMode.NONE]).
     */
    fun processImageBuffer(
        buffer: ImageBuffer,
        mode: ImageTintMode,
        tintColor: RgbaColor?,
        isDarkTheme: Boolean
    ): ImageBuffer {
        if (mode == ImageTintMode.NONE) return buffer
        val rgba = buffer.asRgba8888()
        if (mode == ImageTintMode.MONOCOLOR_AUTO_THEME &&
            !isMonocolorRgba(rgba.bytes, rgba.width, rgba.height)
        ) {
            return buffer
        }
        val recolored = recolorRgbaBytes(
            rgbaBytes = rgba.bytes,
            width = rgba.width,
            height = rgba.height,
            mode = mode,
            tintColor = tintColor,
            isDarkTheme = isDarkTheme
        )
        return ImageBuffer(
            width = rgba.width,
            height = rgba.height,
            pixelFormat = PixelFormat.RGBA_8888,
            colorSpace = rgba.colorSpace,
            bytes = recolored
        )
    }

    fun processImageBuffer(
        buffer: ImageBuffer,
        mode: ImageTintMode,
        tintColor: RgbaColor?,
        resolvedTheme: ThemeMode
    ): ImageBuffer = processImageBuffer(
        buffer = buffer,
        mode = mode,
        tintColor = tintColor,
        isDarkTheme = resolvedTheme == ThemeMode.DARK
    )
}
