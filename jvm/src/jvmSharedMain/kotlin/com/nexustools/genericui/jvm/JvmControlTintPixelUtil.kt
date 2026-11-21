package com.nexustools.genericui.jvm

import com.nexustools.genericui.enums.bootstrap.BorderRadiusStyle
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.style.color.BootstrapColorPalette
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.theme.ThemeConfig
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Shared ARGB pixel tinting, rounded-corner SDF clipping, synthetic bevel surface fallback,
 * state shading (`hovered`, `pressed`, `disabled`, `outline`, `isLink`), and contrasting foreground
 * color resolution for Swing and Android native control rendering.
 */
object JvmControlTintPixelUtil {

    /**
     * Resolves the corner radius in pixels for [borderRadiusStyle] on a `(width x height)` surface.
     */
    fun resolveCornerRadiusPx(
        width: Int,
        height: Int,
        borderRadiusStyle: BorderRadiusStyle?,
        density: Float = 1f
    ): Float {
        val safeDensity = density.coerceAtLeast(0.5f)
        val maxR = min(width, height) / 2f
        return when (borderRadiusStyle) {
            BorderRadiusStyle.NONE -> 0f
            BorderRadiusStyle.PILL, BorderRadiusStyle.CIRCLE -> maxR
            null -> min(maxR, 4f * safeDensity)
            else -> min(maxR, borderRadiusStyle.radiusPx * safeDensity)
        }
    }

    /**
     * Signed distance from `(x + 0.5, y + 0.5)` to the boundary of a rounded rectangle centered
     * in `(width x height)` with corner radius [radiusPx]. Negative values are inside.
     */
    fun computeRoundedRectSdf(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        radiusPx: Float
    ): Float {
        val hx = width / 2f
        val hy = height / 2f
        val rad = radiusPx.coerceIn(0f, min(hx, hy))
        val ix = (hx - rad).coerceAtLeast(0f)
        val iy = (hy - rad).coerceAtLeast(0f)
        val dx = abs((x + 0.5f) - hx) - ix
        val dy = abs((y + 0.5f) - hy) - iy
        val outsideDist = hypot(max(dx, 0f), max(dy, 0f))
        val insideDist = min(max(dx, dy), 0f)
        return outsideDist + insideDist - rad
    }

    /**
     * Applies antialiased rounded-rectangle alpha clipping directly to ARGB [pixels].
     */
    fun clipPixelsToRoundedRect(
        pixels: IntArray,
        width: Int,
        height: Int,
        borderRadiusStyle: BorderRadiusStyle?,
        density: Float = 1f
    ) {
        if (pixels.isEmpty() || width <= 0 || height <= 0) return
        val radiusPx = resolveCornerRadiusPx(width, height, borderRadiusStyle, density)
        if (radiusPx <= 0.5f) return
        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                val argb = pixels[idx]
                val a = (argb ushr 24) and 0xFF
                if (a == 0) continue
                val sdf = computeRoundedRectSdf(x, y, width, height, radiusPx)
                val cov = (0.5f - sdf).coerceIn(0f, 1f)
                if (cov <= 0f) {
                    pixels[idx] = 0x00000000
                } else if (cov < 1f) {
                    val newA = (a * cov).roundToInt().coerceIn(0, 255)
                    pixels[idx] = (newA shl 24) or (argb and 0x00FFFFFF)
                }
            }
        }
    }

    /**
     * Populates a neutral grayscale native-bevel fallback button surface into [pixels] when an
     * offscreen native paint yields transparent pixels (e.g. headless toolkits or unmeasured host tests).
     */
    fun synthesizeFallbackNativeButtonSurface(
        pixels: IntArray,
        width: Int,
        height: Int,
        isDark: Boolean,
        borderRadiusStyle: BorderRadiusStyle? = null,
        density: Float = 1f,
        roundedClip: Boolean = (borderRadiusStyle != null)
    ) {
        if (width <= 0 || height <= 0 || pixels.size < width * height) return
        if (!roundedClip && borderRadiusStyle == null) {
            val topLum = if (isDark) 62 else 248
            val bottomLum = if (isDark) 44 else 228
            val borderLum = if (isDark) 82 else 184
            for (y in 0 until height) {
                val ratio = if (height > 1) y.toFloat() / (height - 1).toFloat() else 0f
                val rowLum = (topLum + (bottomLum - topLum) * ratio).roundToInt().coerceIn(0, 255)
                val rowOffset = y * width
                for (x in 0 until width) {
                    val isBorder = x == 0 || x == width - 1 || y == 0 || y == height - 1
                    val c = if (isBorder) borderLum else rowLum
                    pixels[rowOffset + x] = (0xFF shl 24) or (c shl 16) or (c shl 8) or c
                }
            }
            return
        }

        val baseGray = if (isDark) 56 else 232
        val borderGray = if (isDark) 92 else 184
        val radiusPx = resolveCornerRadiusPx(width, height, borderRadiusStyle, density)
        val borderBandPx = max(1f, 1f * density.coerceAtLeast(0.5f))
        for (y in 0 until height) {
            val vGrad = ((0.5f - (y.toFloat() / height.coerceAtLeast(1).toFloat())) * 16f).roundToInt()
            for (x in 0 until width) {
                val sdf = computeRoundedRectSdf(x, y, width, height, radiusPx)
                val outerCov = (0.5f - sdf).coerceIn(0f, 1f)
                if (outerCov <= 0f) {
                    pixels[y * width + x] = 0x00000000
                    continue
                }
                val isBorder = sdf >= -borderBandPx
                val g = (if (isBorder) borderGray else (baseGray + vGrad)).coerceIn(0, 255)
                val outA = (outerCov * 255f).roundToInt().coerceIn(0, 255)
                pixels[y * width + x] = (outA shl 24) or (g shl 16) or (g shl 8) or g
            }
        }
    }

    /**
     * Tints an offscreen native control ARGB [pixels] buffer toward [tint] while preserving native
     * bevel/highlight/border shading and handling [outline], [hovered], [pressed], [disabled], [isLink],
     * and optional [borderRadiusStyle] SDF clipping.
     */
    fun tintNativeControlPixels(
        pixels: IntArray,
        width: Int,
        height: Int,
        tint: RgbaColor,
        outline: Boolean = false,
        hovered: Boolean = false,
        pressed: Boolean = false,
        disabled: Boolean = false,
        isLink: Boolean = false,
        darkMode: Boolean = false,
        borderRadiusStyle: BorderRadiusStyle? = null,
        density: Float = 1f,
        clipToRoundedShape: Boolean = (borderRadiusStyle != null)
    ) {
        val totalPixels = width * height
        if (width <= 0 || height <= 0 || pixels.size < totalPixels) return

        if (isLink) {
            for (i in 0 until totalPixels) {
                pixels[i] = 0x00000000
            }
            return
        }

        if (tint.a <= 0) {
            if (clipToRoundedShape && borderRadiusStyle != null && borderRadiusStyle != BorderRadiusStyle.NONE) {
                clipPixelsToRoundedRect(pixels, width, height, borderRadiusStyle, density)
            }
            return
        }

        val insetX = (width / 5).coerceAtLeast(1)
        val insetY = (height / 5).coerceAtLeast(1)
        var interiorLumSum = 0L
        var interiorCount = 0
        var visibleLumSum = 0L
        var visibleCount = 0

        for (y in 0 until height) {
            val rowOffset = y * width
            val inInteriorY = y >= insetY && y < height - insetY
            for (x in 0 until width) {
                val argb = pixels[rowOffset + x]
                val a = (argb ushr 24) and 0xFF
                if (a < 32) continue
                val r = (argb ushr 16) and 0xFF
                val g = (argb ushr 8) and 0xFF
                val b = argb and 0xFF
                val lum = (r * 299 + g * 587 + b * 114) / 1000
                visibleLumSum += lum
                visibleCount++
                if (inInteriorY && x >= insetX && x < width - insetX && a >= 160) {
                    interiorLumSum += lum
                    interiorCount++
                }
            }
        }

        if (visibleCount == 0 && !clipToRoundedShape) return
        val baseLuminance = when {
            interiorCount > 0 -> (interiorLumSum / interiorCount).toInt().coerceIn(1, 254)
            visibleCount > 0 -> (visibleLumSum / visibleCount).toInt().coerceIn(1, 254)
            else -> if (darkMode) 64 else 217
        }

        val effectiveTint = adjustTintForState(
            tint = tint,
            hovered = hovered,
            pressed = pressed,
            disabled = disabled
        )
        val preserveInteriorForOutline = outline && !hovered && !pressed

        if (clipToRoundedShape) {
            tintWithRoundedSdf(
                pixels = pixels,
                width = width,
                height = height,
                effectiveTint = effectiveTint,
                baseLuminance = baseLuminance,
                preserveInteriorForOutline = preserveInteriorForOutline,
                borderRadiusStyle = borderRadiusStyle,
                density = density
            )
            return
        }

        tintPreservingNativeSilhouette(
            pixels = pixels,
            width = width,
            height = height,
            effectiveTint = effectiveTint,
            baseLuminance = baseLuminance,
            preserveInteriorForOutline = preserveInteriorForOutline,
            pressed = pressed
        )
    }

    /**
     * Adjusts [tint] for [hovered], [pressed], and [disabled] control interaction states.
     */
    fun adjustTintForState(
        tint: RgbaColor,
        hovered: Boolean = false,
        pressed: Boolean = false,
        disabled: Boolean = false
    ): RgbaColor {
        if (disabled) {
            val neutral = 150
            return RgbaColor(
                r = ((tint.r * 55 + neutral * 45) / 100).coerceIn(0, 255),
                g = ((tint.g * 55 + neutral * 45) / 100).coerceIn(0, 255),
                b = ((tint.b * 55 + neutral * 45) / 100).coerceIn(0, 255),
                a = max(140, (tint.a * 75) / 100)
            )
        }
        if (pressed) {
            return RgbaColor(
                r = (tint.r * 86 / 100).coerceIn(0, 255),
                g = (tint.g * 86 / 100).coerceIn(0, 255),
                b = (tint.b * 86 / 100).coerceIn(0, 255),
                a = tint.a
            )
        }
        if (hovered) {
            return RgbaColor(
                r = (tint.r + (255 - tint.r) * 10 / 100).coerceIn(0, 255),
                g = (tint.g + (255 - tint.g) * 10 / 100).coerceIn(0, 255),
                b = (tint.b + (255 - tint.b) * 10 / 100).coerceIn(0, 255),
                a = tint.a
            )
        }
        return tint
    }

    /**
     * Resolves a readable text/icon foreground [RgbaColor] against a [type] control surface.
     */
    fun resolveContrastingForeground(
        type: BootstrapType,
        darkMode: Boolean,
        outline: Boolean = false,
        hovered: Boolean = false,
        pressed: Boolean = false,
        themeConfig: ThemeConfig = BootstrapColorPalette.activeThemeConfig
    ): RgbaColor = when {
        type == BootstrapType.NONE -> {
            val colors = themeConfig.resolveEffectiveColors(darkMode)
            colors.onSurface ?: colors.onBackground ?: if (darkMode) RgbaColor(235, 235, 240) else RgbaColor(28, 28, 30)
        }
        type == BootstrapType.LINK -> BootstrapColorPalette.resolveColor(BootstrapType.PRIMARY, darkMode, themeConfig)
        outline && !hovered && !pressed -> BootstrapColorPalette.resolveColor(type, darkMode, themeConfig)
        else -> BootstrapColorPalette.resolveForegroundOn(type, darkMode, themeConfig)
    }

    private fun tintWithRoundedSdf(
        pixels: IntArray,
        width: Int,
        height: Int,
        effectiveTint: RgbaColor,
        baseLuminance: Int,
        preserveInteriorForOutline: Boolean,
        borderRadiusStyle: BorderRadiusStyle?,
        density: Float
    ) {
        val safeDensity = density.coerceAtLeast(0.5f)
        val radiusPx = resolveCornerRadiusPx(width, height, borderRadiusStyle, safeDensity)
        val borderThicknessPx = (1.5f * safeDensity).coerceIn(1.5f, 6f)

        for (y in 0 until height) {
            val rowOffset = y * width
            for (x in 0 until width) {
                val idx = rowOffset + x
                val sdf = computeRoundedRectSdf(x, y, width, height, radiusPx)
                val outerCov = (0.5f - sdf).coerceIn(0f, 1f)
                if (outerCov <= 0f) {
                    pixels[idx] = 0x00000000
                    continue
                }

                val argb = pixels[idx]
                val a = (argb ushr 24) and 0xFF
                val delta = if (a > 32) {
                    val r = (argb ushr 16) and 0xFF
                    val g = (argb ushr 8) and 0xFF
                    val b = argb and 0xFF
                    val lum = (r * 299 + g * 587 + b * 114) / 1000
                    ((lum - baseLuminance) / 255f) * 0.55f
                } else {
                    0f
                }

                if (preserveInteriorForOutline) {
                    val strokeCov = (sdf + borderThicknessPx + 0.5f).coerceIn(0f, 1f)
                    val interiorAlpha = (0.08f * effectiveTint.a).roundToInt().coerceIn(0, 26)
                    val blendedA = ((interiorAlpha + (effectiveTint.a - interiorAlpha) * strokeCov) * outerCov)
                        .roundToInt()
                        .coerceIn(0, 255)
                    val nr = (effectiveTint.r + (delta * 60f * strokeCov).roundToInt()).coerceIn(0, 255)
                    val ng = (effectiveTint.g + (delta * 60f * strokeCov).roundToInt()).coerceIn(0, 255)
                    val nb = (effectiveTint.b + (delta * 60f * strokeCov).roundToInt()).coerceIn(0, 255)
                    pixels[idx] = (blendedA shl 24) or (nr shl 16) or (ng shl 8) or nb
                } else {
                    val isBorderBand = sdf >= -borderThicknessPx
                    val shadeFactor = if (isBorderBand) -0.14f else delta
                    val nr = (effectiveTint.r + (shadeFactor * 180f).roundToInt()).coerceIn(0, 255)
                    val ng = (effectiveTint.g + (shadeFactor * 180f).roundToInt()).coerceIn(0, 255)
                    val nb = (effectiveTint.b + (shadeFactor * 180f).roundToInt()).coerceIn(0, 255)
                    val outA = (outerCov * effectiveTint.a).roundToInt().coerceIn(0, 255)
                    pixels[idx] = (outA shl 24) or (nr shl 16) or (ng shl 8) or nb
                }
            }
        }
    }

    private fun tintPreservingNativeSilhouette(
        pixels: IntArray,
        width: Int,
        height: Int,
        effectiveTint: RgbaColor,
        baseLuminance: Int,
        preserveInteriorForOutline: Boolean,
        pressed: Boolean
    ) {
        val tintR = effectiveTint.r
        val tintG = effectiveTint.g
        val tintB = effectiveTint.b
        val tintOpacity = (effectiveTint.a / 255f).coerceIn(0f, 1f)
        val borderBandPx = if (min(width, height) >= 28) 2 else 1

        for (y in 0 until height) {
            val rowOffset = y * width
            val distY = min(y, height - 1 - y)
            val verticalRatio = if (height > 1) y.toFloat() / (height - 1).toFloat() else 0.5f
            val subtleVerticalShade = (0.5f - verticalRatio) * 0.06f

            for (x in 0 until width) {
                val idx = rowOffset + x
                val argb = pixels[idx]
                val a = (argb ushr 24) and 0xFF
                if (a == 0) continue

                val r = (argb ushr 16) and 0xFF
                val g = (argb ushr 8) and 0xFF
                val b = argb and 0xFF
                val lum = (r * 299 + g * 587 + b * 114) / 1000
                val lumDelta = lum - baseLuminance
                val edgeDist = min(min(x, width - 1 - x), distY)

                val isOuterFrame = edgeDist <= borderBandPx || (edgeDist <= borderBandPx + 1 && a < 245)
                val isHighContrastEdge = edgeDist <= borderBandPx + 2 && abs(lumDelta) >= 16

                if (preserveInteriorForOutline) {
                    if (!isOuterFrame && !isHighContrastEdge) {
                        val interiorAlpha = ((0.08f * effectiveTint.a) * (a / 255f)).roundToInt().coerceIn(0, 26)
                        pixels[idx] = (interiorAlpha shl 24) or (tintR shl 16) or (tintG shl 8) or tintB
                        continue
                    }
                    val borderWeight = when {
                        edgeDist <= borderBandPx -> 0.92f
                        isHighContrastEdge -> (abs(lumDelta) / 64f).coerceIn(0.45f, 0.88f)
                        else -> 0.65f
                    } * tintOpacity
                    val invWeight = 1f - borderWeight
                    val shadedR = (tintR * (1f + (lumDelta / 255f) * 0.25f)).roundToInt().coerceIn(0, 255)
                    val shadedG = (tintG * (1f + (lumDelta / 255f) * 0.25f)).roundToInt().coerceIn(0, 255)
                    val shadedB = (tintB * (1f + (lumDelta / 255f) * 0.25f)).roundToInt().coerceIn(0, 255)
                    val outR = (r * invWeight + shadedR * borderWeight).roundToInt().coerceIn(0, 255)
                    val outG = (g * invWeight + shadedG * borderWeight).roundToInt().coerceIn(0, 255)
                    val outB = (b * invWeight + shadedB * borderWeight).roundToInt().coerceIn(0, 255)
                    pixels[idx] = (a shl 24) or (outR shl 16) or (outG shl 8) or outB
                    continue
                }

                val deltaNorm = (lumDelta / 255f).coerceIn(-0.65f, 0.65f) + subtleVerticalShade
                val borderFactor = when {
                    edgeDist == 0 -> if (pressed) 0.72f else 0.80f
                    edgeDist == 1 && lumDelta < -8 -> 0.86f
                    else -> 1.0f
                }

                val mappedR: Int
                val mappedG: Int
                val mappedB: Int
                if (deltaNorm >= 0f) {
                    val specWeight = (deltaNorm * 0.78f).coerceIn(0f, 0.52f)
                    val baseR = tintR * borderFactor
                    val baseG = tintG * borderFactor
                    val baseB = tintB * borderFactor
                    mappedR = (baseR + (255f - baseR) * specWeight).roundToInt().coerceIn(0, 255)
                    mappedG = (baseG + (255f - baseG) * specWeight).roundToInt().coerceIn(0, 255)
                    mappedB = (baseB + (255f - baseB) * specWeight).roundToInt().coerceIn(0, 255)
                } else {
                    val shadeScale = ((1f + deltaNorm * 0.88f) * borderFactor).coerceIn(0.32f, 1.0f)
                    mappedR = (tintR * shadeScale).roundToInt().coerceIn(0, 255)
                    mappedG = (tintG * shadeScale).roundToInt().coerceIn(0, 255)
                    mappedB = (tintB * shadeScale).roundToInt().coerceIn(0, 255)
                }

                val outR = if (tintOpacity >= 0.999f) mappedR else (r * (1f - tintOpacity) + mappedR * tintOpacity).roundToInt().coerceIn(0, 255)
                val outG = if (tintOpacity >= 0.999f) mappedG else (g * (1f - tintOpacity) + mappedG * tintOpacity).roundToInt().coerceIn(0, 255)
                val outB = if (tintOpacity >= 0.999f) mappedB else (b * (1f - tintOpacity) + mappedB * tintOpacity).roundToInt().coerceIn(0, 255)
                pixels[idx] = (a shl 24) or (outR shl 16) or (outG shl 8) or outB
            }
        }
    }
}
