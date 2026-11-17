package com.nexustools.genericui.image

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Pure-Kotlin RGBA compositor that wraps an application icon inside an Apple-style Chrome/Antigravity
 * white rounded squircle tile for macOS Dock (`fullBleedSquare = false`) or full-bleed white background
 * for iOS / iPadOS `AppIcon` assets (`fullBleedSquare = true`).
 */
object AppleIconTileComposer {

    /**
     * Composites [source] centered onto a white Apple icon tile of size [targetSize]x[targetSize].
     */
    fun composeImageBuffer(
        source: ImageBuffer,
        targetSize: Int = maxOf(source.width, source.height).coerceIn(128, 1024),
        fullBleedSquare: Boolean = false,
        artworkScaleFraction: Float = 0.74f
    ): ImageBuffer {
        val rgba = source.toRgba8888()
        val composedBytes = composeRgba(
            sourceRgba = rgba.bytes,
            sourceWidth = rgba.width,
            sourceHeight = rgba.height,
            targetSize = targetSize,
            fullBleedSquare = fullBleedSquare,
            artworkScaleFraction = artworkScaleFraction
        )
        return ImageBuffer(
            width = targetSize,
            height = targetSize,
            pixelFormat = PixelFormat.RGBA_8888,
            colorSpace = ImageColorSpace.SRGB,
            bytes = composedBytes
        )
    }

    /**
     * Composites [sourceRgba] (`sourceWidth * sourceHeight * 4` bytes, non-premultiplied RGBA8888)
     * into a newly allocated [targetSize]x[targetSize] RGBA8888 byte array.
     */
    fun composeRgba(
        sourceRgba: ByteArray,
        sourceWidth: Int,
        sourceHeight: Int,
        targetSize: Int = 512,
        fullBleedSquare: Boolean = false,
        artworkScaleFraction: Float = 0.74f
    ): ByteArray {
        val size = targetSize.coerceAtLeast(16)
        val out = ByteArray(size * size * 4)

        val tileSize = if (fullBleedSquare) size.toDouble() else size * 0.808
        val tileHalf = tileSize * 0.5
        val centerX = size * 0.5
        val centerY = if (fullBleedSquare) size * 0.5 else size * 0.492
        val cornerRadius = tileSize * 0.2237

        // Step 1: Paint tile background (full-bleed white for iOS/iPadOS AppIcon, squircle + soft shadow for macOS)
        for (py in 0 until size) {
            val y = py + 0.5
            for (px in 0 until size) {
                val x = px + 0.5
                val idx = (py * size + px) * 4

                if (fullBleedSquare) {
                    out[idx] = 255.toByte()
                    out[idx + 1] = 255.toByte()
                    out[idx + 2] = 255.toByte()
                    out[idx + 3] = 255.toByte()
                    continue
                }

                val dx = abs(x - centerX)
                val dy = abs(y - centerY)
                val sdf = squircleDistance(dx, dy, tileHalf, cornerRadius)
                val fillCov = (0.7 - sdf).coerceIn(0.0, 1.0)

                // Soft downward ambient drop shadow beneath the white squircle tile
                val shadowDx = abs(x - centerX)
                val shadowDy = abs(y - (centerY + size * 0.018))
                val shadowSdf = squircleDistance(shadowDx, shadowDy, tileHalf * 0.995, cornerRadius)
                val shadowBlur = (size * 0.028).coerceAtLeast(1.2)
                val shadowAlpha = if (shadowSdf > -shadowBlur) {
                    val t = max(0.0, shadowSdf) / shadowBlur
                    0.22 * exp(-t * t * 1.9)
                } else {
                    0.0
                }

                // Subtle hairline outer rim (#D4D8DF) so white tile edges remain defined on white wallpaper
                val rimFactor = if (sdf in -1.15..0.35) {
                    val band = 1.0 - abs(sdf + 0.3) / 0.85
                    (band.coerceIn(0.0, 1.0)) * 0.14
                } else {
                    0.0
                }

                val baseWhite = (255.0 - rimFactor * 28.0).coerceIn(0.0, 255.0)
                val outAlpha = (fillCov + shadowAlpha * (1.0 - fillCov)).coerceIn(0.0, 1.0)
                if (outAlpha > 1e-4) {
                    val premulR = baseWhite * fillCov
                    val r = (premulR / outAlpha).roundToInt().coerceIn(0, 255)
                    val a = (outAlpha * 255.0).roundToInt().coerceIn(0, 255)
                    out[idx] = r.toByte()
                    out[idx + 1] = r.toByte()
                    out[idx + 2] = r.toByte()
                    out[idx + 3] = a.toByte()
                }
            }
        }

        if (sourceWidth <= 0 || sourceHeight <= 0 || sourceRgba.size < sourceWidth * sourceHeight * 4) {
            return out
        }

        // Step 2: Detect non-transparent bounding box so icons with pre-existing transparent padding don't double-shrink
        var minX = sourceWidth
        var minY = sourceHeight
        var maxX = -1
        var maxY = -1
        for (sy in 0 until sourceHeight) {
            for (sx in 0 until sourceWidth) {
                val a = sourceRgba[(sy * sourceWidth + sx) * 4 + 3].toInt() and 0xFF
                if (a > 8) {
                    if (sx < minX) minX = sx
                    if (sx > maxX) maxX = sx
                    if (sy < minY) minY = sy
                    if (sy > maxY) maxY = sy
                }
            }
        }
        val cropX0: Double
        val cropY0: Double
        val cropW: Double
        val cropH: Double
        if (maxX >= minX && maxY >= minY) {
            cropX0 = minX.toDouble()
            cropY0 = minY.toDouble()
            cropW = (maxX - minX + 1).toDouble()
            cropH = (maxY - minY + 1).toDouble()
        } else {
            cropX0 = 0.0
            cropY0 = 0.0
            cropW = sourceWidth.toDouble()
            cropH = sourceHeight.toDouble()
        }

        // Target artwork box inside the white tile
        val maxArtDim = tileSize * artworkScaleFraction.toDouble().coerceIn(0.4, 0.92)
        val scale = min(maxArtDim / cropW, maxArtDim / cropH)
        val dstW = cropW * scale
        val dstH = cropH * scale
        val dstLeft = centerX - dstW * 0.5
        val dstTop = centerY - dstH * 0.5

        val xStart = max(0, floor(dstLeft).toInt() - 1)
        val xEnd = min(size - 1, floor(dstLeft + dstW).toInt() + 1)
        val yStart = max(0, floor(dstTop).toInt() - 1)
        val yEnd = min(size - 1, floor(dstTop + dstH).toInt() + 1)

        // Step 3: Premultiplied bilinear sampling + Porter-Duff source-over composition onto white tile
        for (py in yStart..yEnd) {
            val uY = (py + 0.5 - dstTop) / dstH
            if (uY < -0.5 / dstH || uY > 1.0 + 0.5 / dstH) continue
            val sy = cropY0 + uY * cropH - 0.5
            for (px in xStart..xEnd) {
                val uX = (px + 0.5 - dstLeft) / dstW
                if (uX < -0.5 / dstW || uX > 1.0 + 0.5 / dstW) continue
                val sx = cropX0 + uX * cropW - 0.5

                val (srcR, srcG, srcB, srcA) = sampleBilinearPremul(
                    sourceRgba,
                    sourceWidth,
                    sourceHeight,
                    sx,
                    sy
                )
                if (srcA <= 1e-4) continue

                val idx = (py * size + px) * 4
                val dstA = (out[idx + 3].toInt() and 0xFF) / 255.0
                if (dstA <= 1e-4) continue
                val dstR = (out[idx].toInt() and 0xFF).toDouble()
                val dstG = (out[idx + 1].toInt() and 0xFF).toDouble()
                val dstB = (out[idx + 2].toInt() and 0xFF).toDouble()

                // Composite source icon inside tile bounds (clipping to tile alpha)
                val maskedSrcA = srcA * dstA
                val outR = srcR * dstA + dstR * dstA * (1.0 - srcA)
                val outG = srcG * dstA + dstG * dstA * (1.0 - srcA)
                val outB = srcB * dstA + dstB * dstA * (1.0 - srcA)

                out[idx] = (outR / dstA).roundToInt().coerceIn(0, 255).toByte()
                out[idx + 1] = (outG / dstA).roundToInt().coerceIn(0, 255).toByte()
                out[idx + 2] = (outB / dstA).roundToInt().coerceIn(0, 255).toByte()
                out[idx + 3] = ((maskedSrcA + dstA * (1.0 - srcA)).coerceAtLeast(dstA) * 255.0)
                    .roundToInt()
                    .coerceIn(0, 255)
                    .toByte()
            }
        }

        return out
    }

    /**
     * Continuous Apple-style superellipse squircle signed distance (0 on perimeter, negative inside).
     */
    private fun squircleDistance(dx: Double, dy: Double, halfSize: Double, cornerRadius: Double): Double {
        val inner = (halfSize - cornerRadius).coerceAtLeast(1.0)
        val cx = max(dx - inner, 0.0)
        val cy = max(dy - inner, 0.0)
        if (cx <= 0.0 && cy <= 0.0) {
            return max(dx, dy) - halfSize
        }
        val exponent = 4.6
        val nx = (cx / cornerRadius).pow(exponent)
        val ny = (cy / cornerRadius).pow(exponent)
        return cornerRadius * ((nx + ny).pow(1.0 / exponent) - 1.0)
    }

    private data class PremulSample(val r: Double, val g: Double, val b: Double, val a: Double)

    private fun sampleBilinearPremul(
        rgba: ByteArray,
        w: Int,
        h: Int,
        sx: Double,
        sy: Double
    ): PremulSample {
        val x0 = floor(sx).toInt()
        val y0 = floor(sy).toInt()
        val fx = sx - x0
        val fy = sy - y0

        fun pxSample(px: Int, py: Int): PremulSample {
            if (px < 0 || py < 0 || px >= w || py >= h) return PremulSample(0.0, 0.0, 0.0, 0.0)
            val i = (py * w + px) * 4
            val a = (rgba[i + 3].toInt() and 0xFF) / 255.0
            if (a <= 0.0) return PremulSample(0.0, 0.0, 0.0, 0.0)
            val r = (rgba[i].toInt() and 0xFF) * a
            val g = (rgba[i + 1].toInt() and 0xFF) * a
            val b = (rgba[i + 2].toInt() and 0xFF) * a
            return PremulSample(r, g, b, a)
        }

        val s00 = pxSample(x0, y0)
        val s10 = pxSample(x0 + 1, y0)
        val s01 = pxSample(x0, y0 + 1)
        val s11 = pxSample(x0 + 1, y0 + 1)

        val w00 = (1.0 - fx) * (1.0 - fy)
        val w10 = fx * (1.0 - fy)
        val w01 = (1.0 - fx) * fy
        val w11 = fx * fy

        return PremulSample(
            r = s00.r * w00 + s10.r * w10 + s01.r * w01 + s11.r * w11,
            g = s00.g * w00 + s10.g * w10 + s01.g * w01 + s11.g * w11,
            b = s00.b * w00 + s10.b * w10 + s01.b * w01 + s11.b * w11,
            a = s00.a * w00 + s10.a * w10 + s01.a * w01 + s11.a * w11
        )
    }
}
