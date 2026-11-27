package com.nexustools.genericui.style.css

import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * High-throughput, zero-steady-state-allocation CPU backdrop and foreground filter kernel.
 *
 * Uses a lock-free multi-slot scratch buffer pool (`AtomicReference` ring) to eliminate per-frame
 * heap allocations, together with multi-stage `1/2` and `1/4` bilinear downsampling + 3-pass
 * sliding-window separable Gaussian-approximated box blur for radii `>= 4px` (and full-resolution
 * 3-pass separable blur for small radii `< 4px`), plus full `drop-shadow(...)` alpha-mask blur,
 * offset, tint, and source-over compositing.
 */
@OptIn(ExperimentalAtomicApi::class)
object FastBackdropBlurKernel {

    private const val SCRATCH_POOL_SIZE = 4
    private const val MAX_BLUR_RADIUS_PX = 128f
    private const val DOWNSAMPLE_2X_THRESHOLD_PX = 4f
    private const val DOWNSAMPLE_4X_THRESHOLD_PX = 10f

    private val scratchPool = Array<AtomicReference<ScratchBuffers?>>(SCRATCH_POOL_SIZE) {
        AtomicReference(null)
    }

    private class ScratchBuffers {
        private var fullCap = 0
        var rBuf = FloatArray(0)
        var gBuf = FloatArray(0)
        var bBuf = FloatArray(0)
        var aBuf = FloatArray(0)
        var tmpR = FloatArray(0)
        var tmpG = FloatArray(0)
        var tmpB = FloatArray(0)
        var tmpA = FloatArray(0)
        var shadowAlphaBuf = FloatArray(0)
        var shadowTmpBuf = FloatArray(0)

        private var halfCap = 0
        var hR = FloatArray(0)
        var hG = FloatArray(0)
        var hB = FloatArray(0)
        var hA = FloatArray(0)
        var hTmpR = FloatArray(0)
        var hTmpG = FloatArray(0)
        var hTmpB = FloatArray(0)
        var hTmpA = FloatArray(0)

        private var quarterCap = 0
        var qR = FloatArray(0)
        var qG = FloatArray(0)
        var qB = FloatArray(0)
        var qA = FloatArray(0)
        var qTmpR = FloatArray(0)
        var qTmpG = FloatArray(0)
        var qTmpB = FloatArray(0)
        var qTmpA = FloatArray(0)

        private var qIntCap = 0
        var qIR = IntArray(0)
        var qIG = IntArray(0)
        var qIB = IntArray(0)
        var qIA = IntArray(0)
        var qTmpIR = IntArray(0)
        var qTmpIG = IntArray(0)
        var qTmpIB = IntArray(0)
        var qTmpIA = IntArray(0)
        var qPacked = LongArray(0)

        private var upsampleLutCap = 0
        var upX0 = IntArray(0)
        var upX1 = IntArray(0)
        var upWx = IntArray(0)

        fun ensureQuarterInt(size: Int) {
            if (size <= qIntCap) return
            val next = nextCapacity(qIntCap, size)
            qIntCap = next
            qIR = IntArray(next)
            qIG = IntArray(next)
            qIB = IntArray(next)
            qIA = IntArray(next)
            qTmpIR = IntArray(next)
            qTmpIG = IntArray(next)
            qTmpIB = IntArray(next)
            qTmpIA = IntArray(next)
            qPacked = LongArray(next)
        }

        fun ensureUpsampleLut(width: Int) {
            if (width <= upsampleLutCap) return
            val next = nextCapacity(upsampleLutCap, width)
            upsampleLutCap = next
            upX0 = IntArray(next)
            upX1 = IntArray(next)
            upWx = IntArray(next)
        }

        fun ensureFull(size: Int) {
            if (size <= fullCap) return
            val next = nextCapacity(fullCap, size)
            fullCap = next
            rBuf = FloatArray(next)
            gBuf = FloatArray(next)
            bBuf = FloatArray(next)
            aBuf = FloatArray(next)
            tmpR = FloatArray(next)
            tmpG = FloatArray(next)
            tmpB = FloatArray(next)
            tmpA = FloatArray(next)
            shadowAlphaBuf = FloatArray(next)
            shadowTmpBuf = FloatArray(next)
        }

        fun ensureHalf(size: Int) {
            if (size <= halfCap) return
            val next = nextCapacity(halfCap, size)
            halfCap = next
            hR = FloatArray(next)
            hG = FloatArray(next)
            hB = FloatArray(next)
            hA = FloatArray(next)
            hTmpR = FloatArray(next)
            hTmpG = FloatArray(next)
            hTmpB = FloatArray(next)
            hTmpA = FloatArray(next)
        }

        fun ensureQuarter(size: Int) {
            if (size <= quarterCap) return
            val next = nextCapacity(quarterCap, size)
            quarterCap = next
            qR = FloatArray(next)
            qG = FloatArray(next)
            qB = FloatArray(next)
            qA = FloatArray(next)
            qTmpR = FloatArray(next)
            qTmpG = FloatArray(next)
            qTmpB = FloatArray(next)
            qTmpA = FloatArray(next)
        }

        private fun nextCapacity(current: Int, minNeeded: Int): Int {
            var cap = max(current, 4096)
            while (cap < minNeeded) {
                cap = cap shl 1
            }
            return cap
        }
    }

    private fun acquireScratch(): ScratchBuffers {
        for (i in 0 until SCRATCH_POOL_SIZE) {
            val popped = scratchPool[i].exchange(null)
            if (popped != null) return popped
        }
        return ScratchBuffers()
    }

    private fun releaseScratch(scratch: ScratchBuffers) {
        for (i in 0 until SCRATCH_POOL_SIZE) {
            if (scratchPool[i].compareAndSet(null, scratch)) return
        }
    }

    /**
     * Applies [filter] as an in-place CSS `backdrop-filter` onto a rectangular (optionally rounded)
     * region of an `0xAARRGGBB` [IntArray] framebuffer with zero steady-state allocations.
     */
    fun applyBackdropToArgbRegion(
        filter: CssFilter,
        argbPixels: IntArray,
        bufferWidth: Int,
        bufferHeight: Int,
        regionX: Int,
        regionY: Int,
        regionWidth: Int,
        regionHeight: Int,
        cornerRadiusPx: Float = 0f
    ): Boolean = applyBackdropToRegionInternal(
        filter = filter,
        pixels = argbPixels,
        strideWords = bufferWidth,
        bufferWidth = bufferWidth,
        bufferHeight = bufferHeight,
        regionX = regionX,
        regionY = regionY,
        regionWidth = regionWidth,
        regionHeight = regionHeight,
        cornerRadiusPx = cornerRadiusPx,
        componentOpacity = 1f,
        premultiplied = false
    )

    /**
     * Applies [filter] in-place onto a rectangular (optionally rounded) region of a little-endian
     * premultiplied `BGRA_8888` (`0xAARRGGBB` 32-bit int words) framebuffer with [strideWords] words
     * per row and optional [componentOpacity] blending, using zero steady-state allocations.
     */
    fun applyBackdropToPremulBgraRegion(
        filter: CssFilter,
        pixels: IntArray,
        strideWords: Int,
        bufferWidth: Int,
        bufferHeight: Int,
        regionX: Int,
        regionY: Int,
        regionWidth: Int,
        regionHeight: Int,
        cornerRadiusPx: Float = 0f,
        componentOpacity: Float = 1f
    ): Boolean = applyBackdropToRegionInternal(
        filter = filter,
        pixels = pixels,
        strideWords = strideWords,
        bufferWidth = bufferWidth,
        bufferHeight = bufferHeight,
        regionX = regionX,
        regionY = regionY,
        regionWidth = regionWidth,
        regionHeight = regionHeight,
        cornerRadiusPx = cornerRadiusPx,
        componentOpacity = componentOpacity,
        premultiplied = true
    )

    private fun applyBackdropToRegionInternal(
        filter: CssFilter,
        pixels: IntArray,
        strideWords: Int,
        bufferWidth: Int,
        bufferHeight: Int,
        regionX: Int,
        regionY: Int,
        regionWidth: Int,
        regionHeight: Int,
        cornerRadiusPx: Float,
        componentOpacity: Float,
        premultiplied: Boolean
    ): Boolean {
        if (!filter.hasActiveEffects || bufferWidth <= 0 || bufferHeight <= 0 || strideWords < bufferWidth) return false
        val effectiveOpacity = componentOpacity.coerceIn(0f, 1f)
        if (effectiveOpacity <= 0.004f) return false
        if (pixels.size < (bufferHeight - 1) * strideWords + bufferWidth) return false

        val x0 = regionX.coerceIn(0, bufferWidth)
        val y0 = regionY.coerceIn(0, bufferHeight)
        val x1 = (regionX + regionWidth).coerceIn(0, bufferWidth)
        val y1 = (regionY + regionHeight).coerceIn(0, bufferHeight)
        val clippedW = x1 - x0
        val clippedH = y1 - y0
        if (clippedW <= 0 || clippedH <= 0) return false

        val effectiveRadius = cornerRadiusPx.coerceIn(0f, min(regionWidth, regionHeight) * 0.5f)
        val checkRounded = effectiveRadius > 0.5f
        val blurPx = if (filter.hasBlur) (filter.blur?.resolvePx() ?: 0f).coerceIn(0f, MAX_BLUR_RADIUS_PX) else 0f
        val shadow = filter.dropShadow?.takeIf { filter.hasDropShadow }
        val op255 = (effectiveOpacity * 255f + 0.5f).toInt().coerceIn(0, 255)

        // Pure color filter with no blur and no drop-shadow needs no neighbor capture or scratch buffers.
        if (blurPx <= 0f && shadow == null) {
            applyPixelOpsInPlaceRegion(
                filter = filter,
                pixels = pixels,
                strideWords = strideWords,
                x0 = x0,
                y0 = y0,
                x1 = x1,
                y1 = y1,
                regionX = regionX,
                regionY = regionY,
                regionWidth = regionWidth,
                regionHeight = regionHeight,
                effectiveRadius = effectiveRadius,
                checkRounded = checkRounded,
                op255 = op255,
                premultiplied = premultiplied
            )
            return true
        }

        val blurPad = if (blurPx > 0f) ceil(blurPx * 1.5f).toInt().coerceIn(1, 96) else 0
        val shadowBlurPx = if (shadow != null) (shadow.blurRadius.resolvePx() ?: 0f).coerceIn(0f, MAX_BLUR_RADIUS_PX) else 0f
        val shadowOffX = shadow?.offsetX?.resolvePx() ?: 0f
        val shadowOffY = shadow?.offsetY?.resolvePx() ?: 0f
        val shadowBlurPad = if (shadow != null && shadowBlurPx > 0f) ceil(shadowBlurPx * 1.5f).toInt().coerceIn(1, 96) else 0
        val padLeft = if (shadow != null) max(blurPad, shadowBlurPad + ceil(max(0f, shadowOffX)).toInt().coerceAtMost(64)) else blurPad
        val padRight = if (shadow != null) max(blurPad, shadowBlurPad + ceil(max(0f, -shadowOffX)).toInt().coerceAtMost(64)) else blurPad
        val padTop = if (shadow != null) max(blurPad, shadowBlurPad + ceil(max(0f, shadowOffY)).toInt().coerceAtMost(64)) else blurPad
        val padBottom = if (shadow != null) max(blurPad, shadowBlurPad + ceil(max(0f, -shadowOffY)).toInt().coerceAtMost(64)) else blurPad

        val capX0 = (x0 - padLeft).coerceAtLeast(0)
        val capY0 = (y0 - padTop).coerceAtLeast(0)
        val capX1 = (x1 + padRight).coerceAtMost(bufferWidth)
        val capY1 = (y1 + padBottom).coerceAtMost(bufferHeight)
        val capW = capX1 - capX0
        val capH = capY1 - capY0
        if (capW <= 0 || capH <= 0) return false

        if (premultiplied && blurPx >= DOWNSAMPLE_4X_THRESHOLD_PX && !filter.hasPixelOps && shadow == null && capW >= 16 && capH >= 16) {
            return applyFastBlurOnlyPremulBgra4x(
                pixels = pixels,
                strideWords = strideWords,
                x0 = x0,
                y0 = y0,
                x1 = x1,
                y1 = y1,
                capX0 = capX0,
                capY0 = capY0,
                capX1 = capX1,
                capY1 = capY1,
                capW = capW,
                capH = capH,
                regionX = regionX,
                regionY = regionY,
                regionWidth = regionWidth,
                regionHeight = regionHeight,
                effectiveRadius = effectiveRadius,
                checkRounded = checkRounded,
                blurPx = blurPx,
                op255 = op255
            )
        }

        val pixelCount = capW * capH
        val scratch = acquireScratch()
        try {
            scratch.ensureFull(pixelCount)
            val rBuf = scratch.rBuf
            val gBuf = scratch.gBuf
            val bBuf = scratch.bBuf
            val aBuf = scratch.aBuf

            var p = 0
            if (premultiplied) {
                for (y in capY0 until capY1) {
                    val rowOffset = y * strideWords
                    for (x in capX0 until capX1) {
                        val px = pixels[rowOffset + x]
                        aBuf[p] = ((px ushr 24) and 0xFF) / 255f
                        rBuf[p] = ((px ushr 16) and 0xFF).toFloat()
                        gBuf[p] = ((px ushr 8) and 0xFF).toFloat()
                        bBuf[p] = (px and 0xFF).toFloat()
                        p++
                    }
                }
            } else {
                for (y in capY0 until capY1) {
                    val rowOffset = y * strideWords
                    for (x in capX0 until capX1) {
                        val px = pixels[rowOffset + x]
                        val a = ((px ushr 24) and 0xFF) / 255f
                        aBuf[p] = a
                        rBuf[p] = ((px ushr 16) and 0xFF) * a
                        gBuf[p] = ((px ushr 8) and 0xFF) * a
                        bBuf[p] = (px and 0xFF) * a
                        p++
                    }
                }
            }

            if (blurPx > 0f) {
                runAdaptiveBlur(scratch, capW, capH, blurPx)
            }

            val shadowAlpha = scratch.shadowAlphaBuf
            if (shadow != null) {
                val filterOp = if (premultiplied) (filter.opacity ?: 1f).coerceIn(0f, 1f) else 1f
                var sp = 0
                while (sp < pixelCount) {
                    shadowAlpha[sp] = (aBuf[sp].coerceIn(0f, 1f) * filterOp).coerceIn(0f, 1f)
                    sp++
                }
                if (shadowBlurPx > 0f) {
                    runAdaptiveAlphaBlur(scratch, capW, capH, shadowBlurPx, shadowAlpha, scratch.shadowTmpBuf)
                }
            }

            val hasOps = filter.hasPixelOps
            val filterOpacity255 = ((filter.opacity ?: 1f).coerceIn(0f, 1f) * 255f + 0.5f).toInt().coerceIn(0, 255)
            val argbBlendOp255 = if (!premultiplied) ((filterOpacity255 * op255 + 127) / 255).coerceIn(0, 255) else 255
            val hueDeg = filter.hueRotateDeg
            val hasHue = hueDeg != null && hueDeg != 0f
            val hueRad = if (hasHue) hueDeg!! * (PI.toFloat() / 180f) else 0f
            val cosH = if (hasHue) cos(hueRad) else 1f
            val sinH = if (hasHue) sin(hueRad) else 0f

            val scR = if (shadow != null) shadow.color.r / 255f else 0f
            val scG = if (shadow != null) shadow.color.g / 255f else 0f
            val scB = if (shadow != null) shadow.color.b / 255f else 0f
            val scA = if (shadow != null) shadow.color.alphaFloat else 0f

            for (y in y0 until y1) {
                val dstRow = y * strideWords
                val srcRow = (y - capY0) * capW
                val localY = (y - regionY).toFloat()
                for (x in x0 until x1) {
                    if (checkRounded && !isPointInsideRoundedRect(
                            x = (x - regionX).toFloat(),
                            y = localY,
                            width = regionWidth.toFloat(),
                            height = regionHeight.toFloat(),
                            radius = effectiveRadius
                        )
                    ) {
                        continue
                    }
                    val srcIdx = srcRow + (x - capX0)
                    val a = aBuf[srcIdx].coerceIn(0f, 1f)
                    var r = 0f
                    var g = 0f
                    var b = 0f
                    if (a > 0.0005f) {
                        val inv255A = 1f / (255f * a)
                        r = (rBuf[srcIdx] * inv255A).coerceIn(0f, 1f)
                        g = (gBuf[srcIdx] * inv255A).coerceIn(0f, 1f)
                        b = (bBuf[srcIdx] * inv255A).coerceIn(0f, 1f)
                    }
                    val filtWord = if (shadow != null) {
                        val sx = (x - capX0).toFloat() - shadowOffX
                        val sy = (y - capY0).toFloat() - shadowOffY
                        val maskA = sampleShadowAlpha(shadowAlpha, capW, capH, sx, sy)
                        if (hasOps) {
                            evaluateColorOps(filter, r, g, b, a, hasHue, cosH, sinH, applyOpacity = premultiplied) { fgR, fgG, fgB, fgA ->
                                compositeShadowAndPack(fgR, fgG, fgB, fgA, maskA, scR, scG, scB, scA, premultiplied)
                            }
                        } else {
                            compositeShadowAndPack(r, g, b, a, maskA, scR, scG, scB, scA, premultiplied)
                        }
                    } else if (hasOps) {
                        evaluateColorOps(filter, r, g, b, a, hasHue, cosH, sinH, applyOpacity = premultiplied) { fgR, fgG, fgB, fgA ->
                            packRgbaFloats(fgR, fgG, fgB, fgA, premultiplied)
                        }
                    } else {
                        packRgbaFloats(r, g, b, a, premultiplied)
                    }

                    pixels[dstRow + x] = if (premultiplied && op255 < 255) {
                        blendPremulWords(pixels[dstRow + x], filtWord, op255)
                    } else if (!premultiplied && argbBlendOp255 < 255) {
                        blendStraightArgbWords(pixels[dstRow + x], filtWord, argbBlendOp255)
                    } else {
                        filtWord
                    }
                }
            }
        } finally {
            releaseScratch(scratch)
        }
        return true
    }

    /**
     * Applies blur + pixel color adjustments + drop-shadow to an `RGBA_8888` byte array, returning
     * a new buffer (or the original [rgbaBytes] if inactive), using pooled scratch memory for all intermediates.
     */
    fun applyBackdropToRgbaBytes(filter: CssFilter, rgbaBytes: ByteArray, width: Int, height: Int): ByteArray {
        if (!filter.hasActiveEffects || width <= 0 || height <= 0) return rgbaBytes
        val totalBytes = width * height * 4
        if (rgbaBytes.size < totalBytes) return rgbaBytes
        val blurPx = if (filter.hasBlur) (filter.blur?.resolvePx() ?: 0f).coerceIn(0f, MAX_BLUR_RADIUS_PX) else 0f
        val shadow = filter.dropShadow?.takeIf { filter.hasDropShadow }
        if (blurPx <= 0f && shadow == null) {
            return applyColorOpsToRgbaBytes(filter, rgbaBytes, width, height)
        }

        val out = ByteArray(totalBytes)
        val pixelCount = width * height
        val scratch = acquireScratch()
        try {
            scratch.ensureFull(pixelCount)
            val rBuf = scratch.rBuf
            val gBuf = scratch.gBuf
            val bBuf = scratch.bBuf
            val aBuf = scratch.aBuf

            var idx = 0
            var p = 0
            while (p < pixelCount) {
                val a = (rgbaBytes[idx + 3].toInt() and 0xFF) / 255f
                aBuf[p] = a
                rBuf[p] = (rgbaBytes[idx].toInt() and 0xFF) * a
                gBuf[p] = (rgbaBytes[idx + 1].toInt() and 0xFF) * a
                bBuf[p] = (rgbaBytes[idx + 2].toInt() and 0xFF) * a
                idx += 4
                p++
            }

            if (blurPx > 0f) {
                runAdaptiveBlur(scratch, width, height, blurPx)
            }

            val shadowAlpha = scratch.shadowAlphaBuf
            val shadowBlurPx = if (shadow != null) (shadow.blurRadius.resolvePx() ?: 0f).coerceIn(0f, MAX_BLUR_RADIUS_PX) else 0f
            val shadowOffX = shadow?.offsetX?.resolvePx() ?: 0f
            val shadowOffY = shadow?.offsetY?.resolvePx() ?: 0f
            if (shadow != null) {
                val filterOp = (filter.opacity ?: 1f).coerceIn(0f, 1f)
                var sp = 0
                while (sp < pixelCount) {
                    shadowAlpha[sp] = (aBuf[sp].coerceIn(0f, 1f) * filterOp).coerceIn(0f, 1f)
                    sp++
                }
                if (shadowBlurPx > 0f) {
                    runAdaptiveAlphaBlur(scratch, width, height, shadowBlurPx, shadowAlpha, scratch.shadowTmpBuf)
                }
            }

            val hasOps = filter.hasPixelOps
            val hueDeg = filter.hueRotateDeg
            val hasHue = hueDeg != null && hueDeg != 0f
            val hueRad = if (hasHue) hueDeg!! * (PI.toFloat() / 180f) else 0f
            val cosH = if (hasHue) cos(hueRad) else 1f
            val sinH = if (hasHue) sin(hueRad) else 0f

            val scR = if (shadow != null) shadow.color.r / 255f else 0f
            val scG = if (shadow != null) shadow.color.g / 255f else 0f
            val scB = if (shadow != null) shadow.color.b / 255f else 0f
            val scA = if (shadow != null) shadow.color.alphaFloat else 0f

            idx = 0
            p = 0
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val a = aBuf[p].coerceIn(0f, 1f)
                    var r = 0f
                    var g = 0f
                    var b = 0f
                    if (a > 0.0005f) {
                        val inv255A = 1f / (255f * a)
                        r = (rBuf[p] * inv255A).coerceIn(0f, 1f)
                        g = (gBuf[p] * inv255A).coerceIn(0f, 1f)
                        b = (bBuf[p] * inv255A).coerceIn(0f, 1f)
                    }
                    val argb = if (shadow != null) {
                        val maskA = sampleShadowAlpha(
                            shadowAlpha = shadowAlpha,
                            w = width,
                            h = height,
                            sx = x.toFloat() - shadowOffX,
                            sy = y.toFloat() - shadowOffY
                        )
                        if (hasOps) {
                            evaluateColorOps(filter, r, g, b, a, hasHue, cosH, sinH) { fgR, fgG, fgB, fgA ->
                                compositeShadowAndPack(fgR, fgG, fgB, fgA, maskA, scR, scG, scB, scA, premultiplied = false)
                            }
                        } else {
                            compositeShadowAndPack(r, g, b, a, maskA, scR, scG, scB, scA, premultiplied = false)
                        }
                    } else if (hasOps) {
                        evaluateColorOps(filter, r, g, b, a, hasHue, cosH, sinH) { fgR, fgG, fgB, fgA ->
                            packRgbaFloats(fgR, fgG, fgB, fgA, premultiplied = false)
                        }
                    } else {
                        packRgbaFloats(r, g, b, a, premultiplied = false)
                    }
                    out[idx] = ((argb ushr 16) and 0xFF).toByte()
                    out[idx + 1] = ((argb ushr 8) and 0xFF).toByte()
                    out[idx + 2] = (argb and 0xFF).toByte()
                    out[idx + 3] = ((argb ushr 24) and 0xFF).toByte()
                    idx += 4
                    p++
                }
            }
        } finally {
            releaseScratch(scratch)
        }
        return out
    }

    /**
     * Applies only Gaussian-approximated blur to [rgbaBytes] (`RGBA_8888`), returning a new array
     * (or [rgbaBytes] if [radiusPx] is `<= 0`).
     */
    fun applyBlurToRgbaBytes(
        filter: CssFilter,
        rgbaBytes: ByteArray,
        width: Int,
        height: Int,
        radiusPx: Float = filter.blur?.resolvePx() ?: 0f
    ): ByteArray {
        if (filter.isNone || width <= 0 || height <= 0 || radiusPx <= 0f) return rgbaBytes
        val totalBytes = width * height * 4
        if (rgbaBytes.size < totalBytes) return rgbaBytes
        val clampedRadius = radiusPx.coerceIn(0f, MAX_BLUR_RADIUS_PX)
        if (clampedRadius <= 0f) return rgbaBytes

        val out = ByteArray(totalBytes)
        val pixelCount = width * height
        val scratch = acquireScratch()
        try {
            scratch.ensureFull(pixelCount)
            val rBuf = scratch.rBuf
            val gBuf = scratch.gBuf
            val bBuf = scratch.bBuf
            val aBuf = scratch.aBuf

            var idx = 0
            var p = 0
            while (p < pixelCount) {
                val a = (rgbaBytes[idx + 3].toInt() and 0xFF) / 255f
                aBuf[p] = a
                rBuf[p] = (rgbaBytes[idx].toInt() and 0xFF) * a
                gBuf[p] = (rgbaBytes[idx + 1].toInt() and 0xFF) * a
                bBuf[p] = (rgbaBytes[idx + 2].toInt() and 0xFF) * a
                idx += 4
                p++
            }

            runAdaptiveBlur(scratch, width, height, clampedRadius)

            idx = 0
            p = 0
            while (p < pixelCount) {
                val a = aBuf[p].coerceIn(0f, 1f)
                if (a > 0.0005f) {
                    val invA = 1f / a
                    out[idx] = (rBuf[p] * invA).roundToInt().coerceIn(0, 255).toByte()
                    out[idx + 1] = (gBuf[p] * invA).roundToInt().coerceIn(0, 255).toByte()
                    out[idx + 2] = (bBuf[p] * invA).roundToInt().coerceIn(0, 255).toByte()
                }
                out[idx + 3] = (a * 255f).roundToInt().coerceIn(0, 255).toByte()
                idx += 4
                p++
            }
        } finally {
            releaseScratch(scratch)
        }
        return out
    }

    /**
     * Applies color/filter operations without blur on [rgbaBytes] up to `width * height * 4` bytes.
     */
    fun applyColorOpsToRgbaBytes(
        filter: CssFilter,
        rgbaBytes: ByteArray,
        width: Int,
        height: Int
    ): ByteArray {
        if (filter.isNone || !filter.hasPixelOps || width <= 0 || height <= 0) return rgbaBytes
        val totalBytes = width * height * 4
        if (rgbaBytes.size < totalBytes) return rgbaBytes
        val out = rgbaBytes.copyOf()
        val hueDeg = filter.hueRotateDeg
        val hasHue = hueDeg != null && hueDeg != 0f
        val hueRad = if (hasHue) hueDeg!! * (PI.toFloat() / 180f) else 0f
        val cosH = if (hasHue) cos(hueRad) else 1f
        val sinH = if (hasHue) sin(hueRad) else 0f
        val opacity = filter.opacity

        var i = 0
        while (i < totalBytes) {
            val rawA = out[i + 3].toInt() and 0xFF
            if (rawA == 0 && (opacity == null || opacity == 1f)) {
                i += 4
                continue
            }
            val r = (out[i].toInt() and 0xFF) / 255f
            val g = (out[i + 1].toInt() and 0xFF) / 255f
            val b = (out[i + 2].toInt() and 0xFF) / 255f
            val a = rawA / 255f
            val argb = evaluateColorOps(filter, r, g, b, a, hasHue, cosH, sinH) { fgR, fgG, fgB, fgA ->
                packRgbaFloats(fgR, fgG, fgB, fgA, premultiplied = false)
            }
            out[i] = ((argb ushr 16) and 0xFF).toByte()
            out[i + 1] = ((argb ushr 8) and 0xFF).toByte()
            out[i + 2] = (argb and 0xFF).toByte()
            out[i + 3] = ((argb ushr 24) and 0xFF).toByte()
            i += 4
        }
        return out
    }

    private fun runAdaptiveBlur(
        scratch: ScratchBuffers,
        width: Int,
        height: Int,
        radiusPx: Float
    ) {
        if (radiusPx >= DOWNSAMPLE_4X_THRESHOLD_PX && width >= 16 && height >= 16) {
            val halfW = (width + 1) ushr 1
            val halfH = (height + 1) ushr 1
            val qW = (halfW + 1) ushr 1
            val qH = (halfH + 1) ushr 1
            scratch.ensureHalf(halfW * halfH)
            scratch.ensureQuarter(qW * qH)

            downsampleBox2x(scratch.rBuf, scratch.gBuf, scratch.bBuf, scratch.aBuf, width, height, scratch.hR, scratch.hG, scratch.hB, scratch.hA, halfW, halfH)
            downsampleBox2x(scratch.hR, scratch.hG, scratch.hB, scratch.hA, halfW, halfH, scratch.qR, scratch.qG, scratch.qB, scratch.qA, qW, qH)

            val radii = CssFilter.boxBlurRadiiForGaussianSigma((radiusPx * 0.25f).coerceAtLeast(1f))
            runThreePassBoxBlur(
                scratch.qR, scratch.qG, scratch.qB, scratch.qA,
                scratch.qTmpR, scratch.qTmpG, scratch.qTmpB, scratch.qTmpA,
                qW, qH, radii
            )

            bilinearUpsample(scratch.qR, scratch.qG, scratch.qB, scratch.qA, qW, qH, scratch.hR, scratch.hG, scratch.hB, scratch.hA, halfW, halfH)
            bilinearUpsample(scratch.hR, scratch.hG, scratch.hB, scratch.hA, halfW, halfH, scratch.rBuf, scratch.gBuf, scratch.bBuf, scratch.aBuf, width, height)
        } else if (radiusPx >= DOWNSAMPLE_2X_THRESHOLD_PX && width >= 8 && height >= 8) {
            val halfW = (width + 1) ushr 1
            val halfH = (height + 1) ushr 1
            scratch.ensureHalf(halfW * halfH)

            downsampleBox2x(scratch.rBuf, scratch.gBuf, scratch.bBuf, scratch.aBuf, width, height, scratch.hR, scratch.hG, scratch.hB, scratch.hA, halfW, halfH)

            val radii = CssFilter.boxBlurRadiiForGaussianSigma((radiusPx * 0.5f).coerceAtLeast(1f))
            runThreePassBoxBlur(
                scratch.hR, scratch.hG, scratch.hB, scratch.hA,
                scratch.hTmpR, scratch.hTmpG, scratch.hTmpB, scratch.hTmpA,
                halfW, halfH, radii
            )

            bilinearUpsample(scratch.hR, scratch.hG, scratch.hB, scratch.hA, halfW, halfH, scratch.rBuf, scratch.gBuf, scratch.bBuf, scratch.aBuf, width, height)
        } else {
            val radii = CssFilter.boxBlurRadiiForGaussianSigma(radiusPx)
            runThreePassBoxBlur(
                scratch.rBuf, scratch.gBuf, scratch.bBuf, scratch.aBuf,
                scratch.tmpR, scratch.tmpG, scratch.tmpB, scratch.tmpA,
                width, height, radii
            )
        }
    }

    private fun runAdaptiveAlphaBlur(
        scratch: ScratchBuffers,
        width: Int,
        height: Int,
        radiusPx: Float,
        buf: FloatArray,
        tmp: FloatArray
    ) {
        if (radiusPx >= DOWNSAMPLE_4X_THRESHOLD_PX && width >= 16 && height >= 16) {
            val halfW = (width + 1) ushr 1
            val halfH = (height + 1) ushr 1
            val qW = (halfW + 1) ushr 1
            val qH = (halfH + 1) ushr 1
            scratch.ensureHalf(halfW * halfH)
            scratch.ensureQuarter(qW * qH)

            downsampleBox2xSingle(buf, width, height, scratch.hA, halfW, halfH)
            downsampleBox2xSingle(scratch.hA, halfW, halfH, scratch.qA, qW, qH)

            val radii = CssFilter.boxBlurRadiiForGaussianSigma((radiusPx * 0.25f).coerceAtLeast(1f))
            runThreePassBoxBlurSingle(scratch.qA, scratch.qTmpA, qW, qH, radii)

            bilinearUpsampleSingle(scratch.qA, qW, qH, scratch.hA, halfW, halfH)
            bilinearUpsampleSingle(scratch.hA, halfW, halfH, buf, width, height)
        } else if (radiusPx >= DOWNSAMPLE_2X_THRESHOLD_PX && width >= 8 && height >= 8) {
            val halfW = (width + 1) ushr 1
            val halfH = (height + 1) ushr 1
            scratch.ensureHalf(halfW * halfH)

            downsampleBox2xSingle(buf, width, height, scratch.hA, halfW, halfH)

            val radii = CssFilter.boxBlurRadiiForGaussianSigma((radiusPx * 0.5f).coerceAtLeast(1f))
            runThreePassBoxBlurSingle(scratch.hA, scratch.hTmpA, halfW, halfH, radii)

            bilinearUpsampleSingle(scratch.hA, halfW, halfH, buf, width, height)
        } else {
            val radii = CssFilter.boxBlurRadiiForGaussianSigma(radiusPx)
            runThreePassBoxBlurSingle(buf, tmp, width, height, radii)
        }
    }

    private fun runThreePassBoxBlur(
        rBuf: FloatArray, gBuf: FloatArray, bBuf: FloatArray, aBuf: FloatArray,
        tmpR: FloatArray, tmpG: FloatArray, tmpB: FloatArray, tmpA: FloatArray,
        width: Int,
        height: Int,
        radii: IntArray
    ) {
        for (boxRadius in radii) {
            if (boxRadius <= 0) continue
            boxBlurHorizontal(rBuf, tmpR, width, height, boxRadius)
            boxBlurHorizontal(gBuf, tmpG, width, height, boxRadius)
            boxBlurHorizontal(bBuf, tmpB, width, height, boxRadius)
            boxBlurHorizontal(aBuf, tmpA, width, height, boxRadius)

            boxBlurVertical(tmpR, rBuf, width, height, boxRadius)
            boxBlurVertical(tmpG, gBuf, width, height, boxRadius)
            boxBlurVertical(tmpB, bBuf, width, height, boxRadius)
            boxBlurVertical(tmpA, aBuf, width, height, boxRadius)
        }
    }

    private fun runThreePassBoxBlurSingle(
        buf: FloatArray,
        tmp: FloatArray,
        width: Int,
        height: Int,
        radii: IntArray
    ) {
        for (boxRadius in radii) {
            if (boxRadius <= 0) continue
            boxBlurHorizontal(buf, tmp, width, height, boxRadius)
            boxBlurVertical(tmp, buf, width, height, boxRadius)
        }
    }

    private fun boxBlurHorizontal(src: FloatArray, dst: FloatArray, width: Int, height: Int, radius: Int) {
        val windowSize = radius + radius + 1
        val invWindow = 1f / windowSize.toFloat()
        for (y in 0 until height) {
            val rowOff = y * width
            val firstVal = src[rowOff]
            var acc = firstVal * (radius + 1)
            for (dx in 1..radius) {
                acc += src[rowOff + min(dx, width - 1)]
            }
            dst[rowOff] = acc * invWindow
            for (x in 1 until width) {
                val addIdx = min(x + radius, width - 1)
                val subIdx = max(x - radius - 1, 0)
                acc += src[rowOff + addIdx] - src[rowOff + subIdx]
                dst[rowOff + x] = acc * invWindow
            }
        }
    }

    private fun boxBlurVertical(src: FloatArray, dst: FloatArray, width: Int, height: Int, radius: Int) {
        val windowSize = radius + radius + 1
        val invWindow = 1f / windowSize.toFloat()
        for (x in 0 until width) {
            val firstVal = src[x]
            var acc = firstVal * (radius + 1)
            for (dy in 1..radius) {
                acc += src[min(dy, height - 1) * width + x]
            }
            dst[x] = acc * invWindow
            for (y in 1 until height) {
                val addRow = min(y + radius, height - 1) * width
                val subRow = max(y - radius - 1, 0) * width
                acc += src[addRow + x] - src[subRow + x]
                dst[y * width + x] = acc * invWindow
            }
        }
    }

    private fun downsampleBox2x(
        srcR: FloatArray, srcG: FloatArray, srcB: FloatArray, srcA: FloatArray,
        srcW: Int, srcH: Int,
        dstR: FloatArray, dstG: FloatArray, dstB: FloatArray, dstA: FloatArray,
        dstW: Int, dstH: Int
    ) {
        for (dy in 0 until dstH) {
            val sy0 = dy shl 1
            val sy1 = min(sy0 + 1, srcH - 1)
            val row0 = sy0 * srcW
            val row1 = sy1 * srcW
            val dstRow = dy * dstW
            for (dx in 0 until dstW) {
                val sx0 = dx shl 1
                val sx1 = min(sx0 + 1, srcW - 1)
                val i00 = row0 + sx0
                val i10 = row0 + sx1
                val i01 = row1 + sx0
                val i11 = row1 + sx1
                val di = dstRow + dx
                dstR[di] = (srcR[i00] + srcR[i10] + srcR[i01] + srcR[i11]) * 0.25f
                dstG[di] = (srcG[i00] + srcG[i10] + srcG[i01] + srcG[i11]) * 0.25f
                dstB[di] = (srcB[i00] + srcB[i10] + srcB[i01] + srcB[i11]) * 0.25f
                dstA[di] = (srcA[i00] + srcA[i10] + srcA[i01] + srcA[i11]) * 0.25f
            }
        }
    }

    private fun downsampleBox2xSingle(
        src: FloatArray,
        srcW: Int,
        srcH: Int,
        dst: FloatArray,
        dstW: Int,
        dstH: Int
    ) {
        for (dy in 0 until dstH) {
            val sy0 = dy shl 1
            val sy1 = min(sy0 + 1, srcH - 1)
            val row0 = sy0 * srcW
            val row1 = sy1 * srcW
            val dstRow = dy * dstW
            for (dx in 0 until dstW) {
                val sx0 = dx shl 1
                val sx1 = min(sx0 + 1, srcW - 1)
                dst[dstRow + dx] = (src[row0 + sx0] + src[row0 + sx1] + src[row1 + sx0] + src[row1 + sx1]) * 0.25f
            }
        }
    }

    private fun bilinearUpsample(
        srcR: FloatArray, srcG: FloatArray, srcB: FloatArray, srcA: FloatArray,
        srcW: Int, srcH: Int,
        dstR: FloatArray, dstG: FloatArray, dstB: FloatArray, dstA: FloatArray,
        dstW: Int, dstH: Int
    ) {
        val maxSx = (srcW - 1).toFloat()
        val maxSy = (srcH - 1).toFloat()
        for (y in 0 until dstH) {
            val sy = ((y + 0.5f) * 0.5f - 0.5f).coerceIn(0f, maxSy)
            val y0 = sy.toInt()
            val y1 = min(y0 + 1, srcH - 1)
            val fy = sy - y0
            val ify = 1f - fy
            val srcRow0 = y0 * srcW
            val srcRow1 = y1 * srcW
            val dstRow = y * dstW
            for (x in 0 until dstW) {
                val sx = ((x + 0.5f) * 0.5f - 0.5f).coerceIn(0f, maxSx)
                val x0 = sx.toInt()
                val x1 = min(x0 + 1, srcW - 1)
                val fx = sx - x0
                val ifx = 1f - fx

                val w00 = ifx * ify
                val w10 = fx * ify
                val w01 = ifx * fy
                val w11 = fx * fy

                val i00 = srcRow0 + x0
                val i10 = srcRow0 + x1
                val i01 = srcRow1 + x0
                val i11 = srcRow1 + x1
                val di = dstRow + x

                dstR[di] = srcR[i00] * w00 + srcR[i10] * w10 + srcR[i01] * w01 + srcR[i11] * w11
                dstG[di] = srcG[i00] * w00 + srcG[i10] * w10 + srcG[i01] * w01 + srcG[i11] * w11
                dstB[di] = srcB[i00] * w00 + srcB[i10] * w10 + srcB[i01] * w01 + srcB[i11] * w11
                dstA[di] = srcA[i00] * w00 + srcA[i10] * w10 + srcA[i01] * w01 + srcA[i11] * w11
            }
        }
    }

    private fun bilinearUpsampleSingle(
        src: FloatArray,
        srcW: Int,
        srcH: Int,
        dst: FloatArray,
        dstW: Int,
        dstH: Int
    ) {
        val maxSx = (srcW - 1).toFloat()
        val maxSy = (srcH - 1).toFloat()
        for (y in 0 until dstH) {
            val sy = ((y + 0.5f) * 0.5f - 0.5f).coerceIn(0f, maxSy)
            val y0 = sy.toInt()
            val y1 = min(y0 + 1, srcH - 1)
            val fy = sy - y0
            val ify = 1f - fy
            val srcRow0 = y0 * srcW
            val srcRow1 = y1 * srcW
            val dstRow = y * dstW
            for (x in 0 until dstW) {
                val sx = ((x + 0.5f) * 0.5f - 0.5f).coerceIn(0f, maxSx)
                val x0 = sx.toInt()
                val x1 = min(x0 + 1, srcW - 1)
                val fx = sx - x0
                val ifx = 1f - fx
                val w00 = ifx * ify
                val w10 = fx * ify
                val w01 = ifx * fy
                val w11 = fx * fy
                dst[dstRow + x] =
                    src[srcRow0 + x0] * w00 + src[srcRow0 + x1] * w10 + src[srcRow1 + x0] * w01 + src[srcRow1 + x1] * w11
            }
        }
    }

    private fun sampleShadowAlpha(
        shadowAlpha: FloatArray,
        w: Int,
        h: Int,
        sx: Float,
        sy: Float
    ): Float {
        val x0 = floor(sx).toInt()
        val y0 = floor(sy).toInt()
        val fx = sx - x0
        val fy = sy - y0
        val x1 = x0 + 1
        val y1 = y0 + 1
        val a00 = if (x0 in 0 until w && y0 in 0 until h) shadowAlpha[y0 * w + x0] else 0f
        if (fx == 0f && fy == 0f) return a00
        val a10 = if (x1 in 0 until w && y0 in 0 until h) shadowAlpha[y0 * w + x1] else 0f
        val a01 = if (x0 in 0 until w && y1 in 0 until h) shadowAlpha[y1 * w + x0] else 0f
        val a11 = if (x1 in 0 until w && y1 in 0 until h) shadowAlpha[y1 * w + x1] else 0f
        val top = a00 + (a10 - a00) * fx
        val bot = a01 + (a11 - a01) * fx
        return (top + (bot - top) * fy).coerceIn(0f, 1f)
    }

    private fun compositeShadowAndPack(
        fgR: Float,
        fgG: Float,
        fgB: Float,
        fgA: Float,
        maskA: Float,
        scR: Float,
        scG: Float,
        scB: Float,
        scA: Float,
        premultiplied: Boolean
    ): Int {
        val shA = (maskA * scA).coerceIn(0f, 1f)
        val invFgA = 1f - fgA
        val outA = (fgA + shA * invFgA).coerceIn(0f, 1f)
        val outRp = (fgR * fgA + scR * shA * invFgA).coerceIn(0f, 1f)
        val outGp = (fgG * fgA + scG * shA * invFgA).coerceIn(0f, 1f)
        val outBp = (fgB * fgA + scB * shA * invFgA).coerceIn(0f, 1f)
        val ai = (outA * 255f).roundToInt().coerceIn(0, 255)
        if (ai == 0) return 0
        return if (premultiplied) {
            val ri = (outRp * 255f).roundToInt().coerceIn(0, ai)
            val gi = (outGp * 255f).roundToInt().coerceIn(0, ai)
            val bi = (outBp * 255f).roundToInt().coerceIn(0, ai)
            (ai shl 24) or (ri shl 16) or (gi shl 8) or bi
        } else {
            val invOutA255 = 255f / outA
            val ri = (outRp * invOutA255).roundToInt().coerceIn(0, 255)
            val gi = (outGp * invOutA255).roundToInt().coerceIn(0, 255)
            val bi = (outBp * invOutA255).roundToInt().coerceIn(0, 255)
            (ai shl 24) or (ri shl 16) or (gi shl 8) or bi
        }
    }

    private fun applyPixelOpsInPlaceRegion(
        filter: CssFilter,
        pixels: IntArray,
        strideWords: Int,
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
        regionX: Int,
        regionY: Int,
        regionWidth: Int,
        regionHeight: Int,
        effectiveRadius: Float,
        checkRounded: Boolean,
        op255: Int,
        premultiplied: Boolean
    ) {
        val hueDeg = filter.hueRotateDeg
        val hasHue = hueDeg != null && hueDeg != 0f
        val hueRad = if (hasHue) hueDeg!! * (PI.toFloat() / 180f) else 0f
        val cosH = if (hasHue) cos(hueRad) else 1f
        val sinH = if (hasHue) sin(hueRad) else 0f
        val filterOpacity255 = ((filter.opacity ?: 1f).coerceIn(0f, 1f) * 255f + 0.5f).toInt().coerceIn(0, 255)
        val argbBlendOp255 = if (!premultiplied) ((filterOpacity255 * op255 + 127) / 255).coerceIn(0, 255) else 255

        for (y in y0 until y1) {
            val rowOff = y * strideWords
            val localY = (y - regionY).toFloat()
            for (x in x0 until x1) {
                if (checkRounded && !isPointInsideRoundedRect(
                        x = (x - regionX).toFloat(),
                        y = localY,
                        width = regionWidth.toFloat(),
                        height = regionHeight.toFloat(),
                        radius = effectiveRadius
                    )
                ) {
                    continue
                }
                val px = pixels[rowOff + x]
                val a255 = (px ushr 24) and 0xFF
                val a = a255 / 255f
                val r: Float
                val g: Float
                val b: Float
                if (premultiplied) {
                    if (a255 > 0) {
                        val invPremul = 1f / a255.toFloat()
                        r = (((px ushr 16) and 0xFF) * invPremul).coerceIn(0f, 1f)
                        g = (((px ushr 8) and 0xFF) * invPremul).coerceIn(0f, 1f)
                        b = ((px and 0xFF) * invPremul).coerceIn(0f, 1f)
                    } else {
                        r = 0f
                        g = 0f
                        b = 0f
                    }
                } else {
                    r = ((px ushr 16) and 0xFF) / 255f
                    g = ((px ushr 8) and 0xFF) / 255f
                    b = (px and 0xFF) / 255f
                }
                val filtWord = evaluateColorOps(filter, r, g, b, a, hasHue, cosH, sinH, applyOpacity = premultiplied) { fgR, fgG, fgB, fgA ->
                    packRgbaFloats(fgR, fgG, fgB, fgA, premultiplied)
                }
                pixels[rowOff + x] = if (premultiplied && op255 < 255) {
                    blendPremulWords(px, filtWord, op255)
                } else if (!premultiplied && argbBlendOp255 < 255) {
                    blendStraightArgbWords(px, filtWord, argbBlendOp255)
                } else {
                    filtWord
                }
            }
        }
    }

    private fun blendStraightArgbWords(orig: Int, filtered: Int, op255: Int): Int {
        if (op255 >= 255) return filtered
        if (op255 <= 0) return orig
        val invOp = 255 - op255
        val oa = (orig ushr 24) and 0xFF
        val fa = (filtered ushr 24) and 0xFF
        val outA = ((oa * invOp + fa * op255 + 127) / 255).coerceIn(0, 255)
        if (outA == 0) return 0
        val or = ((orig ushr 16) and 0xFF) * oa
        val og = ((orig ushr 8) and 0xFF) * oa
        val ob = (orig and 0xFF) * oa
        val fr = ((filtered ushr 16) and 0xFF) * fa
        val fg = ((filtered ushr 8) and 0xFF) * fa
        val fb = (filtered and 0xFF) * fa
        val denom = outA * 255
        val half = denom ushr 1
        val outR = ((or * invOp + fr * op255 + half) / denom).coerceIn(0, 255)
        val outG = ((og * invOp + fg * op255 + half) / denom).coerceIn(0, 255)
        val outB = ((ob * invOp + fb * op255 + half) / denom).coerceIn(0, 255)
        return (outA shl 24) or (outR shl 16) or (outG shl 8) or outB
    }

    private fun blendPremulWords(orig: Int, filtered: Int, op255: Int): Int {
        val invOp = 255 - op255
        val oa = (orig ushr 24) and 0xFF
        val or = (orig ushr 16) and 0xFF
        val og = (orig ushr 8) and 0xFF
        val ob = orig and 0xFF
        val fa = (filtered ushr 24) and 0xFF
        val fr = (filtered ushr 16) and 0xFF
        val fg = (filtered ushr 8) and 0xFF
        val fb = filtered and 0xFF
        val outA = ((oa * invOp + fa * op255 + 127) / 255).coerceIn(0, 255)
        val outR = ((or * invOp + fr * op255 + 127) / 255).coerceIn(0, outA)
        val outG = ((og * invOp + fg * op255 + 127) / 255).coerceIn(0, outA)
        val outB = ((ob * invOp + fb * op255 + 127) / 255).coerceIn(0, outA)
        return (outA shl 24) or (outR shl 16) or (outG shl 8) or outB
    }

    private fun packRgbaFloats(r: Float, g: Float, b: Float, a: Float, premultiplied: Boolean): Int {
        val ai = (a * 255f).roundToInt().coerceIn(0, 255)
        return if (premultiplied) {
            if (ai == 0) {
                0
            } else {
                val scale = a * 255f
                val ri = (r * scale).roundToInt().coerceIn(0, ai)
                val gi = (g * scale).roundToInt().coerceIn(0, ai)
                val bi = (b * scale).roundToInt().coerceIn(0, ai)
                (ai shl 24) or (ri shl 16) or (gi shl 8) or bi
            }
        } else {
            val ri = (r * 255f).roundToInt().coerceIn(0, 255)
            val gi = (g * 255f).roundToInt().coerceIn(0, 255)
            val bi = (b * 255f).roundToInt().coerceIn(0, 255)
            (ai shl 24) or (ri shl 16) or (gi shl 8) or bi
        }
    }

    private inline fun <T> evaluateColorOps(
        filter: CssFilter,
        inR: Float,
        inG: Float,
        inB: Float,
        inA: Float,
        hasHue: Boolean,
        cosH: Float,
        sinH: Float,
        applyOpacity: Boolean = true,
        onResult: (r: Float, g: Float, b: Float, a: Float) -> T
    ): T {
        var r = inR
        var g = inG
        var b = inB
        var a = inA

        val gs = filter.grayscale
        if (gs != null && gs != 0f) {
            val gAmt = gs.coerceIn(0f, 1f)
            val lum = 0.2126f * r + 0.7152f * g + 0.0722f * b
            r += (lum - r) * gAmt
            g += (lum - g) * gAmt
            b += (lum - b) * gAmt
        }

        val sp = filter.sepia
        if (sp != null && sp != 0f) {
            val sAmt = sp.coerceIn(0f, 1f)
            val sr = (0.393f * r + 0.769f * g + 0.189f * b).coerceIn(0f, 1f)
            val sg = (0.349f * r + 0.686f * g + 0.168f * b).coerceIn(0f, 1f)
            val sb = (0.272f * r + 0.534f * g + 0.131f * b).coerceIn(0f, 1f)
            r += (sr - r) * sAmt
            g += (sg - g) * sAmt
            b += (sb - b) * sAmt
        }

        val sat = filter.saturate
        if (sat != null && sat != 1f) {
            val st = sat.coerceAtLeast(0f)
            val lum = 0.2126f * r + 0.7152f * g + 0.0722f * b
            r = (lum + (r - lum) * st).coerceIn(0f, 1f)
            g = (lum + (g - lum) * st).coerceIn(0f, 1f)
            b = (lum + (b - lum) * st).coerceIn(0f, 1f)
        }

        if (hasHue) {
            val nr = (0.213f + cosH * 0.787f - sinH * 0.213f) * r +
                (0.715f - cosH * 0.715f - sinH * 0.715f) * g +
                (0.072f - cosH * 0.072f + sinH * 0.928f) * b
            val ng = (0.213f - cosH * 0.213f + sinH * 0.143f) * r +
                (0.715f + cosH * 0.285f + sinH * 0.140f) * g +
                (0.072f - cosH * 0.072f - sinH * 0.283f) * b
            val nb = (0.213f - cosH * 0.213f - sinH * 0.787f) * r +
                (0.715f - cosH * 0.715f + sinH * 0.715f) * g +
                (0.072f + cosH * 0.928f + sinH * 0.072f) * b
            r = nr.coerceIn(0f, 1f)
            g = ng.coerceIn(0f, 1f)
            b = nb.coerceIn(0f, 1f)
        }

        val brVal = filter.brightness
        val invVal = filter.invert
        val brightnessBeforeInvert = brVal == 0f && invVal != null && invVal > 0f
        if (brightnessBeforeInvert) {
            val br = brVal!!.coerceAtLeast(0f)
            r = (r * br).coerceIn(0f, 1f)
            g = (g * br).coerceIn(0f, 1f)
            b = (b * br).coerceIn(0f, 1f)
        }

        if (invVal != null && invVal != 0f) {
            val inv = invVal.coerceIn(0f, 1f)
            r += ((1f - r) - r) * inv
            g += ((1f - g) - g) * inv
            b += ((1f - b) - b) * inv
        }

        if (!brightnessBeforeInvert && brVal != null && brVal != 1f) {
            val br = brVal.coerceAtLeast(0f)
            r = (r * br).coerceIn(0f, 1f)
            g = (g * br).coerceIn(0f, 1f)
            b = (b * br).coerceIn(0f, 1f)
        }

        val ctVal = filter.contrast
        if (ctVal != null && ctVal != 1f) {
            val ct = ctVal.coerceAtLeast(0f)
            r = ((r - 0.5f) * ct + 0.5f).coerceIn(0f, 1f)
            g = ((g - 0.5f) * ct + 0.5f).coerceIn(0f, 1f)
            b = ((b - 0.5f) * ct + 0.5f).coerceIn(0f, 1f)
        }

        if (applyOpacity) {
            val opVal = filter.opacity
            if (opVal != null && opVal != 1f) {
                a = (a * opVal.coerceIn(0f, 1f)).coerceIn(0f, 1f)
            }
        }

        return onResult(r, g, b, a)
    }

    private fun isPointInsideRoundedRect(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        radius: Float
    ): Boolean {
        val px = x + 0.5f
        val py = y + 0.5f
        val cx = when {
            px < radius -> radius
            px > width - radius -> width - radius
            else -> return true
        }
        val cy = when {
            py < radius -> radius
            py > height - radius -> height - radius
            else -> return true
        }
        val dx = px - cx
        val dy = py - cy
        return (dx * dx + dy * dy) <= (radius * radius)
    }

    private fun applyFastBlurOnlyPremulBgra4x(
        pixels: IntArray,
        strideWords: Int,
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
        capX0: Int,
        capY0: Int,
        capX1: Int,
        capY1: Int,
        capW: Int,
        capH: Int,
        regionX: Int,
        regionY: Int,
        regionWidth: Int,
        regionHeight: Int,
        effectiveRadius: Float,
        checkRounded: Boolean,
        blurPx: Float,
        op255: Int
    ): Boolean {
        val qW = ((capW + 3) ushr 2).coerceAtLeast(1)
        val qH = ((capH + 3) ushr 2).coerceAtLeast(1)
        val clippedW = x1 - x0
        val scratch = acquireScratch()
        try {
            scratch.ensureQuarterInt(qW * qH)
            scratch.ensureUpsampleLut(clippedW)

            val qIR = scratch.qIR
            val qIG = scratch.qIG
            val qIB = scratch.qIB
            val qIA = scratch.qIA
            val qTmpR = scratch.qTmpIR
            val qTmpG = scratch.qTmpIG
            val qTmpB = scratch.qTmpIB
            val qTmpA = scratch.qTmpIA

            for (qy in 0 until qH) {
                val sy0 = capY0 + (qy shl 2)
                val sy1 = min(sy0 + 2, capY1 - 1)
                val row0 = sy0 * strideWords
                val row1 = sy1 * strideWords
                val qRow = qy * qW
                for (qx in 0 until qW) {
                    val sx0 = capX0 + (qx shl 2)
                    val sx1 = min(sx0 + 2, capX1 - 1)
                    val p00 = pixels[row0 + sx0]
                    val p10 = pixels[row0 + sx1]
                    val p01 = pixels[row1 + sx0]
                    val p11 = pixels[row1 + sx1]
                    val qi = qRow + qx
                    qIA[qi] = (((p00 ushr 24) and 0xFF) + ((p10 ushr 24) and 0xFF) + ((p01 ushr 24) and 0xFF) + ((p11 ushr 24) and 0xFF)) shl 2
                    qIR[qi] = (((p00 ushr 16) and 0xFF) + ((p10 ushr 16) and 0xFF) + ((p01 ushr 16) and 0xFF) + ((p11 ushr 16) and 0xFF)) shl 2
                    qIG[qi] = (((p00 ushr 8) and 0xFF) + ((p10 ushr 8) and 0xFF) + ((p01 ushr 8) and 0xFF) + ((p11 ushr 8) and 0xFF)) shl 2
                    qIB[qi] = ((p00 and 0xFF) + (p10 and 0xFF) + (p01 and 0xFF) + (p11 and 0xFF)) shl 2
                }
            }

            val radii = CssFilter.boxBlurRadiiForGaussianSigma((blurPx * 0.25f).coerceAtLeast(1f))
            for (boxRadius in radii) {
                if (boxRadius <= 0) continue
                boxBlurHorizontalInt(qIR, qTmpR, qW, qH, boxRadius)
                boxBlurHorizontalInt(qIG, qTmpG, qW, qH, boxRadius)
                boxBlurHorizontalInt(qIB, qTmpB, qW, qH, boxRadius)
                boxBlurHorizontalInt(qIA, qTmpA, qW, qH, boxRadius)
                boxBlurVerticalInt(qTmpR, qIR, qW, qH, boxRadius)
                boxBlurVerticalInt(qTmpG, qIG, qW, qH, boxRadius)
                boxBlurVerticalInt(qTmpB, qIB, qW, qH, boxRadius)
                boxBlurVerticalInt(qTmpA, qIA, qW, qH, boxRadius)
            }

            val qCount = qW * qH
            val qPacked = scratch.qPacked
            var qi = 0
            while (qi < qCount) {
                val aVal = ((qIA[qi] + 8) ushr 4).coerceIn(0, 255).toLong()
                val rVal = ((qIR[qi] + 8) ushr 4).coerceIn(0, 255).toLong()
                val gVal = ((qIG[qi] + 8) ushr 4).coerceIn(0, 255).toLong()
                val bVal = ((qIB[qi] + 8) ushr 4).coerceIn(0, 255).toLong()
                qPacked[qi] = (aVal shl 48) or (rVal shl 32) or (gVal shl 16) or bVal
                qi++
            }

            val maxQxf16 = (qW - 1) shl 4
            val maxQy = qH - 1
            val upX0 = scratch.upX0
            val upWx = scratch.upWx
            for (lx in 0 until clippedW) {
                val cx = (x0 + lx) - capX0
                val qxf16 = ((cx shl 2) - 6).coerceIn(0, maxQxf16)
                upX0[lx] = qxf16 ushr 4
                upWx[lx] = qxf16 and 0xF
            }

            val maxQyf16 = (qH - 1) shl 4
            val rSq = effectiveRadius * effectiveRadius
            val rRegionH = regionHeight.toFloat()
            val regRight = regionX + regionWidth
            val op256 = ((op255 * 256 + 127) / 255).coerceIn(0, 256).toLong()
            val inv256 = 256L - op256
            val opaque = op255 >= 255

            for (y in y0 until y1) {
                val cy = y - capY0
                val qyf16 = ((cy shl 2) - 6).coerceIn(0, maxQyf16)
                val iy0 = qyf16 ushr 4
                val iy1 = if (iy0 < maxQy) iy0 + 1 else iy0
                val fy = qyf16 and 0xF
                val ify = 16 - fy
                val qRow0 = iy0 * qW
                val qRow1 = iy1 * qW
                val dstRow = y * strideWords + x0

                var rowStart = 0
                var rowEnd = clippedW
                if (checkRounded) {
                    val py = (y - regionY).toFloat() + 0.5f
                    val dy = when {
                        py < effectiveRadius -> effectiveRadius - py
                        py > rRegionH - effectiveRadius -> py - (rRegionH - effectiveRadius)
                        else -> 0f
                    }
                    if (dy > 0f) {
                        val rem = rSq - dy * dy
                        if (rem <= 0f) continue
                        val maxDx = sqrt(rem)
                        val leftClipX = (regionX + (effectiveRadius - maxDx - 0.5f).let { if (it > 0f) ceil(it).toInt() else 0 })
                        val rightClipX = (regRight - (effectiveRadius - maxDx - 0.5f).let { if (it > 0f) floor(it).toInt() else 0 })
                        if (leftClipX > x0) rowStart = (leftClipX - x0).coerceAtMost(clippedW)
                        if (rightClipX < x1) rowEnd = (rightClipX - x0).coerceAtLeast(rowStart)
                    }
                }

                var cachedIx0 = -1
                var p00 = 0L
                var p10 = 0L
                var p01 = 0L
                var p11 = 0L
                val maxIx = qW - 1
                val topBlend0 = qPacked[qRow0]
                if (rowStart < rowEnd && topBlend0 >= 0L) {
                    // Enter cached horizontal span scan across quarter-res texels
                    for (lx in rowStart until rowEnd) {
                        val ix0 = upX0[lx]
                        if (ix0 != cachedIx0) {
                            cachedIx0 = ix0
                            val ix1 = if (ix0 < maxIx) ix0 + 1 else ix0
                            p00 = qPacked[qRow0 + ix0]
                            p10 = qPacked[qRow0 + ix1]
                            p01 = qPacked[qRow1 + ix0]
                            p11 = qPacked[qRow1 + ix1]
                        }
                        val fx = upWx[lx]
                        val ifx = 16 - fx
                        val top = p00 * ifx + p10 * fx
                        val bot = p01 * ifx + p11 * fx
                        val blended = ((top * ify + bot * fy + 0x0080008000800080L) ushr 8) and 0x00FF00FF00FF00FFL
                        val finalPacked = if (opaque) {
                            blended
                        } else {
                            val dst = pixels[dstRow + lx]
                            val dstSwar = (((dst ushr 24) and 0xFF).toLong() shl 48) or
                                (((dst ushr 16) and 0xFF).toLong() shl 32) or
                                (((dst ushr 8) and 0xFF).toLong() shl 16) or
                                (dst and 0xFF).toLong()
                            ((blended * op256 + dstSwar * inv256 + 0x0080008000800080L) ushr 8) and 0x00FF00FF00FF00FFL
                        }
                        pixels[dstRow + lx] = (((finalPacked ushr 48) and 0xFFL).toInt() shl 24) or
                            (((finalPacked ushr 32) and 0xFFL).toInt() shl 16) or
                            (((finalPacked ushr 16) and 0xFFL).toInt() shl 8) or
                            (finalPacked and 0xFFL).toInt()
                    }
                }
            }
        } finally {
            releaseScratch(scratch)
        }
        return true
    }

    private fun boxBlurHorizontalInt(src: IntArray, dst: IntArray, width: Int, height: Int, radius: Int) {
        val windowSize = radius + radius + 1
        val invFixed = (1 shl 16) / windowSize
        val halfFixed = 1 shl 15
        for (y in 0 until height) {
            val rowOff = y * width
            var acc = src[rowOff] * (radius + 1)
            for (dx in 1..radius) {
                acc += src[rowOff + min(dx, width - 1)]
            }
            dst[rowOff] = (acc * invFixed + halfFixed) ushr 16
            for (x in 1 until width) {
                val addIdx = min(x + radius, width - 1)
                val subIdx = max(x - radius - 1, 0)
                acc += src[rowOff + addIdx] - src[rowOff + subIdx]
                dst[rowOff + x] = (acc * invFixed + halfFixed) ushr 16
            }
        }
    }

    private fun boxBlurVerticalInt(src: IntArray, dst: IntArray, width: Int, height: Int, radius: Int) {
        val windowSize = radius + radius + 1
        val invFixed = (1 shl 16) / windowSize
        val halfFixed = 1 shl 15
        for (x in 0 until width) {
            var acc = src[x] * (radius + 1)
            for (dy in 1..radius) {
                acc += src[min(dy, height - 1) * width + x]
            }
            dst[x] = (acc * invFixed + halfFixed) ushr 16
            for (y in 1 until height) {
                val addRow = min(y + radius, height - 1) * width
                val subRow = max(y - radius - 1, 0) * width
                acc += src[addRow + x] - src[subRow + x]
                dst[y * width + x] = (acc * invFixed + halfFixed) ushr 16
            }
        }
    }
}
