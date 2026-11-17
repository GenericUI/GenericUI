package com.nexustools.genericui.plugin

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Pure-JVM compositor that wraps application icon artwork inside a Chrome-style clean white
 * Apple icon tile:
 * - For **macOS** (`fullBleedSquare = false`): a centered white macOS squircle tile (exponent `4.6`,
 *   rounded corner ratio `0.2237`, tile occupies ~80.8% of canvas with soft downward drop shadow and
 *   subtle cool hairline rim).
 * - For **iOS / iPadOS App Store icons** (`fullBleedSquare = true`): an opaque solid white
 *   full-bleed square canvas with the artwork centered at the same balanced scale.
 *
 * Used whenever an Apple target falls back to the generic or default icon instead of an explicit
 * Apple-specific icon (`appleIcon` / `macIcon` / `iosIcon` / `ipadosIcon`).
 */
object GenericUiAppleIconTileComposer {

    const val DEFAULT_ARTWORK_SCALE: Float = 0.74f
    private const val TILE_FRACTION: Double = 0.808
    private const val TILE_CENTER_Y_FRACTION: Double = 0.492
    private const val CORNER_RATIO: Double = 0.2237
    private const val SQUIRCLE_EXPONENT: Double = 4.6
    private const val ALPHA_OPAQUE_THRESHOLD: Int = 8

    /**
     * Wraps [source] inside a Chrome-style white Apple icon tile of dimension [targetSize]×[targetSize].
     */
    fun composeImage(
        source: BufferedImage,
        targetSize: Int,
        fullBleedSquare: Boolean = false,
        artworkScaleFraction: Float = DEFAULT_ARTWORK_SCALE
    ): BufferedImage {
        val size = targetSize.coerceAtLeast(16)
        val rgbaSource = toArgbBufferedImage(source)
        val out = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)

        val clampedArtworkScale = artworkScaleFraction.coerceIn(0.4f, 0.92f).toDouble()
        val tileSize = if (fullBleedSquare) size.toDouble() else size * TILE_FRACTION
        val tileHalf = tileSize * 0.5
        val centerX = size * 0.5
        val centerY = if (fullBleedSquare) size * 0.5 else size * TILE_CENTER_Y_FRACTION
        val cornerRadius = tileSize * CORNER_RATIO

        val bounds = findOpaqueBounds(rgbaSource)
        val cropX = bounds[0]
        val cropY = bounds[1]
        val cropW = (bounds[2] - bounds[0]).coerceAtLeast(1)
        val cropH = (bounds[3] - bounds[1]).coerceAtLeast(1)

        val maxArtSide = tileSize * clampedArtworkScale
        val fitScale = min(maxArtSide / cropW.toDouble(), maxArtSide / cropH.toDouble())
        val artW = (cropW * fitScale).coerceAtLeast(1.0)
        val artH = (cropH * fitScale).coerceAtLeast(1.0)
        val artLeft = centerX - artW * 0.5
        val artTop = centerY - artH * 0.5

        val shadowCenterX = centerX
        val shadowCenterY = centerY + size * 0.018
        val shadowHalf = tileHalf * 0.995
        val blurSigma = (size * 0.028).coerceAtLeast(1.2)

        val srcW = rgbaSource.width
        val srcH = rgbaSource.height
        val rowPixels = IntArray(size)

        for (y in 0 until size) {
            val py = y + 0.5
            for (x in 0 until size) {
                val px = x + 0.5

                val tileAlpha: Double
                val shadowAlpha: Double
                var bgR: Double
                var bgG: Double
                var bgB: Double

                if (fullBleedSquare) {
                    tileAlpha = 1.0
                    shadowAlpha = 0.0
                    bgR = 255.0
                    bgG = 255.0
                    bgB = 255.0
                } else {
                    val sdf = squircleSdf(px - centerX, py - centerY, tileHalf, cornerRadius)
                    tileAlpha = (0.5 - sdf / 1.5).coerceIn(0.0, 1.0)

                    val shadowSdf = squircleSdf(px - shadowCenterX, py - shadowCenterY, shadowHalf, cornerRadius)
                    shadowAlpha = if (shadowSdf <= 0.0) {
                        0.22
                    } else {
                        val t = shadowSdf / blurSigma
                        (0.22 * exp(-t * t * 1.9)).coerceIn(0.0, 0.22)
                    }

                    val rimFactor = if (sdf in -1.15..0.35) {
                        (1.0 - abs(sdf + 0.3) / 0.85).coerceIn(0.0, 1.0) * 0.14
                    } else {
                        0.0
                    }
                    val baseWhite = 255.0 - rimFactor * 28.0
                    bgR = baseWhite - rimFactor * 43.0
                    bgG = baseWhite - rimFactor * 39.0
                    bgB = baseWhite - rimFactor * 32.0
                }

                // Sample artwork over tile surface if inside art bounding box
                if (tileAlpha > 0.001) {
                    val fx = (px - artLeft) / artW
                    val fy = (py - artTop) / artH
                    if (fx in 0.0..1.0 && fy in 0.0..1.0) {
                        val sx = cropX + fx * cropW - 0.5
                        val sy = cropY + fy * cropH - 0.5
                        val sampled = sampleBilinearPremultiplied(rgbaSource, srcW, srcH, sx, sy)
                        val srcA = sampled[3]
                        if (srcA > 0.0001) {
                            val invA = 1.0 - srcA
                            bgR = (sampled[0] * 255.0 + bgR * invA).coerceIn(0.0, 255.0)
                            bgG = (sampled[1] * 255.0 + bgG * invA).coerceIn(0.0, 255.0)
                            bgB = (sampled[2] * 255.0 + bgB * invA).coerceIn(0.0, 255.0)
                        }
                    }
                }

                val outPixel = if (fullBleedSquare) {
                    (255 shl 24) or
                        (bgR.roundToInt().coerceIn(0, 255) shl 16) or
                        (bgG.roundToInt().coerceIn(0, 255) shl 8) or
                        bgB.roundToInt().coerceIn(0, 255)
                } else {
                    // Composite white tile (R=bgR, G=bgG, B=bgB, A=tileAlpha) over black shadow (0, 0, 0, shadowAlpha)
                    val outA = tileAlpha + shadowAlpha * (1.0 - tileAlpha)
                    if (outA <= 0.0001) {
                        0
                    } else {
                        val outR = ((bgR * tileAlpha) / outA).roundToInt().coerceIn(0, 255)
                        val outG = ((bgG * tileAlpha) / outA).roundToInt().coerceIn(0, 255)
                        val outB = ((bgB * tileAlpha) / outA).roundToInt().coerceIn(0, 255)
                        val alphaInt = (outA * 255.0).roundToInt().coerceIn(0, 255)
                        (alphaInt shl 24) or (outR shl 16) or (outG shl 8) or outB
                    }
                }
                rowPixels[x] = outPixel
            }
            out.setRGB(0, y, size, 1, rowPixels, 0, size)
        }
        return out
    }

    /**
     * Composes [source] into a Chrome-style Apple icon PNG byte array of dimension [targetSize]×[targetSize].
     */
    fun composePngBytes(
        source: BufferedImage,
        targetSize: Int,
        fullBleedSquare: Boolean = false,
        artworkScaleFraction: Float = DEFAULT_ARTWORK_SCALE
    ): ByteArray {
        val composed = composeImage(
            source = source,
            targetSize = targetSize,
            fullBleedSquare = fullBleedSquare,
            artworkScaleFraction = artworkScaleFraction
        )
        val baos = ByteArrayOutputStream()
        ImageIO.write(composed, "PNG", baos)
        return baos.toByteArray()
    }

    private fun toArgbBufferedImage(src: BufferedImage): BufferedImage {
        if (src.type == BufferedImage.TYPE_INT_ARGB) return src
        val converted = BufferedImage(src.width.coerceAtLeast(1), src.height.coerceAtLeast(1), BufferedImage.TYPE_INT_ARGB)
        val g = converted.createGraphics()
        g.drawImage(src, 0, 0, null)
        g.dispose()
        return converted
    }

    private fun squircleSdf(dxRaw: Double, dyRaw: Double, half: Double, cornerRadius: Double): Double {
        val dx = abs(dxRaw)
        val dy = abs(dyRaw)
        val radius = cornerRadius.coerceAtLeast(1.0)
        val inner = (half - radius).coerceAtLeast(1.0)
        return if (dx > inner && dy > inner) {
            val nx = ((dx - inner) / radius).coerceAtLeast(0.0)
            val ny = ((dy - inner) / radius).coerceAtLeast(0.0)
            radius * ((nx.pow(SQUIRCLE_EXPONENT) + ny.pow(SQUIRCLE_EXPONENT)).pow(1.0 / SQUIRCLE_EXPONENT) - 1.0)
        } else {
            max(dx, dy) - half
        }
    }

    private fun findOpaqueBounds(img: BufferedImage): IntArray {
        val w = img.width
        val h = img.height
        var minX = w
        var minY = h
        var maxX = 0
        var maxY = 0
        val row = IntArray(w)
        for (y in 0 until h) {
            img.getRGB(0, y, w, 1, row, 0, w)
            for (x in 0 until w) {
                val a = (row[x] ushr 24) and 0xFF
                if (a > ALPHA_OPAQUE_THRESHOLD) {
                    if (x < minX) minX = x
                    if (y < minY) minY = y
                    if (x + 1 > maxX) maxX = x + 1
                    if (y + 1 > maxY) maxY = y + 1
                }
            }
        }
        return if (minX >= maxX || minY >= maxY) {
            intArrayOf(0, 0, w, h)
        } else {
            intArrayOf(minX, minY, maxX, maxY)
        }
    }

    private fun sampleBilinearPremultiplied(
        img: BufferedImage,
        w: Int,
        h: Int,
        fx: Double,
        fy: Double
    ): DoubleArray {
        val x0 = fx.toInt().let { if (fx < 0 && fx != it.toDouble()) it - 1 else it }.coerceIn(0, w - 1)
        val y0 = fy.toInt().let { if (fy < 0 && fy != it.toDouble()) it - 1 else it }.coerceIn(0, h - 1)
        val x1 = (x0 + 1).coerceIn(0, w - 1)
        val y1 = (y0 + 1).coerceIn(0, h - 1)
        val tx = (fx - x0).coerceIn(0.0, 1.0)
        val ty = (fy - y0).coerceIn(0.0, 1.0)

        val p00 = img.getRGB(x0, y0)
        val p10 = img.getRGB(x1, y0)
        val p01 = img.getRGB(x0, y1)
        val p11 = img.getRGB(x1, y1)

        val w00 = (1.0 - tx) * (1.0 - ty)
        val w10 = tx * (1.0 - ty)
        val w01 = (1.0 - tx) * ty
        val w11 = tx * ty

        fun channelPremul(argb: Int): DoubleArray {
            val a = ((argb ushr 24) and 0xFF) / 255.0
            val r = (((argb ushr 16) and 0xFF) / 255.0) * a
            val g = (((argb ushr 8) and 0xFF) / 255.0) * a
            val b = ((argb and 0xFF) / 255.0) * a
            return doubleArrayOf(r, g, b, a)
        }

        val c00 = channelPremul(p00)
        val c10 = channelPremul(p10)
        val c01 = channelPremul(p01)
        val c11 = channelPremul(p11)

        return doubleArrayOf(
            c00[0] * w00 + c10[0] * w10 + c01[0] * w01 + c11[0] * w11,
            c00[1] * w00 + c10[1] * w10 + c01[1] * w01 + c11[1] * w11,
            c00[2] * w00 + c10[2] * w10 + c01[2] * w01 + c11[2] * w11,
            (c00[3] * w00 + c10[3] * w10 + c01[3] * w01 + c11[3] * w11).coerceIn(0.0, 1.0)
        )
    }
}
