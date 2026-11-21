package com.nexustools.genericui.style.css

import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * High-throughput, zero-steady-state-allocation CPU backdrop filter kernel.
 *
 * Uses a lock-free multi-slot scratch buffer pool (`AtomicReference` ring) to eliminate per-frame
 * heap allocations, together with multi-stage `1/2` and `1/4` bilinear downsampling + 3-pass
 * sliding-window separable Gaussian-approximated box blur for radii `>= 4px` (and full-resolution
 * 3-pass separable blur for small radii `< 4px`).
 */
@OptIn(ExperimentalAtomicApi::class)
object FastBackdropBlurKernel {

    private const val SCRATCH_POOL_SIZE = 4
    private const val MAX_BLUR_RADIUS_PX = 48f
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
    ): Boolean {
        if (!filter.hasActiveEffects || bufferWidth <= 0 || bufferHeight <= 0) return false
        if (argbPixels.size < bufferWidth * bufferHeight) return false

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

        // Pure color filter with no blur needs no neighbor capture or scratch buffers.
        if (blurPx <= 0f) {
            applyPixelOpsInPlaceArgbRegion(
                filter = filter,
                argbPixels = argbPixels,
                bufferWidth = bufferWidth,
                x0 = x0,
                y0 = y0,
                x1 = x1,
                y1 = y1,
                regionX = regionX,
                regionY = regionY,
                regionWidth = regionWidth,
                regionHeight = regionHeight,
                effectiveRadius = effectiveRadius,
                checkRounded = checkRounded
            )
            return true
        }

        val pad = ceil(blurPx * 1.5f).toInt().coerceIn(1, 32)
        val capX0 = (x0 - pad).coerceAtLeast(0)
        val capY0 = (y0 - pad).coerceAtLeast(0)
        val capX1 = (x1 + pad).coerceAtMost(bufferWidth)
        val capY1 = (y1 + pad).coerceAtMost(bufferHeight)
        val capW = capX1 - capX0
        val capH = capY1 - capY0
        if (capW <= 0 || capH <= 0) return false

        val pixelCount = capW * capH
        val scratch = acquireScratch()
        try {
            scratch.ensureFull(pixelCount)
            val rBuf = scratch.rBuf
            val gBuf = scratch.gBuf
            val bBuf = scratch.bBuf
            val aBuf = scratch.aBuf

            var p = 0
            for (y in capY0 until capY1) {
                val rowOffset = y * bufferWidth
                for (x in capX0 until capX1) {
                    val px = argbPixels[rowOffset + x]
                    val a = ((px ushr 24) and 0xFF) / 255f
                    aBuf[p] = a
                    rBuf[p] = ((px ushr 16) and 0xFF) * a
                    gBuf[p] = ((px ushr 8) and 0xFF) * a
                    bBuf[p] = (px and 0xFF) * a
                    p++
                }
            }

            runAdaptiveBlur(scratch, capW, capH, blurPx)

            val hasOps = filter.hasPixelOps
            val hueDeg = filter.hueRotateDeg
            val hasHue = hueDeg != null && hueDeg != 0f
            val hueRad = if (hasHue) hueDeg!! * (PI.toFloat() / 180f) else 0f
            val cosH = if (hasHue) cos(hueRad) else 1f
            val sinH = if (hasHue) sin(hueRad) else 0f

            for (y in y0 until y1) {
                val dstRow = y * bufferWidth
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
                    var a = aBuf[srcIdx].coerceIn(0f, 1f)
                    var r = 0f
                    var g = 0f
                    var b = 0f
                    if (a > 0.0005f) {
                        val inv255A = 1f / (255f * a)
                        r = (rBuf[srcIdx] * inv255A).coerceIn(0f, 1f)
                        g = (gBuf[srcIdx] * inv255A).coerceIn(0f, 1f)
                        b = (bBuf[srcIdx] * inv255A).coerceIn(0f, 1f)
                    }
                    if (hasOps) {
                        val packed = applyColorOpsToNormalizedPixel(filter, r, g, b, a, hasHue, cosH, sinH)
                        argbPixels[dstRow + x] = packed
                    } else {
                        val ai = (a * 255f).roundToInt().coerceIn(0, 255)
                        val ri = (r * 255f).roundToInt().coerceIn(0, 255)
                        val gi = (g * 255f).roundToInt().coerceIn(0, 255)
                        val bi = (b * 255f).roundToInt().coerceIn(0, 255)
                        argbPixels[dstRow + x] = (ai shl 24) or (ri shl 16) or (gi shl 8) or bi
                    }
                }
            }
        } finally {
            releaseScratch(scratch)
        }
        return true
    }

    /**
     * Applies blur + pixel color adjustments to an `RGBA_8888` byte array, returning a new buffer
     * (or the original [rgbaBytes] if inactive), using pooled scratch memory for all intermediates.
     */
    fun applyBackdropToRgbaBytes(filter: CssFilter, rgbaBytes: ByteArray, width: Int, height: Int): ByteArray {
        if (!filter.hasActiveEffects || width <= 0 || height <= 0) return rgbaBytes
        val totalBytes = width * height * 4
        if (rgbaBytes.size < totalBytes) return rgbaBytes
        if (!filter.hasBlur) return applyColorOpsToRgbaBytes(filter, rgbaBytes, width, height)

        val blurPx = (filter.blur?.resolvePx() ?: 0f).coerceIn(0f, MAX_BLUR_RADIUS_PX)
        if (blurPx <= 0f) return applyColorOpsToRgbaBytes(filter, rgbaBytes, width, height)

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

            runAdaptiveBlur(scratch, width, height, blurPx)

            val hasOps = filter.hasPixelOps
            val hueDeg = filter.hueRotateDeg
            val hasHue = hueDeg != null && hueDeg != 0f
            val hueRad = if (hasHue) hueDeg!! * (PI.toFloat() / 180f) else 0f
            val cosH = if (hasHue) cos(hueRad) else 1f
            val sinH = if (hasHue) sin(hueRad) else 0f

            idx = 0
            p = 0
            while (p < pixelCount) {
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
                if (hasOps) {
                    val argb = applyColorOpsToNormalizedPixel(filter, r, g, b, a, hasHue, cosH, sinH)
                    out[idx] = ((argb ushr 16) and 0xFF).toByte()
                    out[idx + 1] = ((argb ushr 8) and 0xFF).toByte()
                    out[idx + 2] = (argb and 0xFF).toByte()
                    out[idx + 3] = ((argb ushr 24) and 0xFF).toByte()
                } else {
                    out[idx] = (r * 255f).roundToInt().coerceIn(0, 255).toByte()
                    out[idx + 1] = (g * 255f).roundToInt().coerceIn(0, 255).toByte()
                    out[idx + 2] = (b * 255f).roundToInt().coerceIn(0, 255).toByte()
                    out[idx + 3] = (a * 255f).roundToInt().coerceIn(0, 255).toByte()
                }
                idx += 4
                p++
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
            val argb = applyColorOpsToNormalizedPixel(filter, r, g, b, a, hasHue, cosH, sinH)
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

    private fun applyPixelOpsInPlaceArgbRegion(
        filter: CssFilter,
        argbPixels: IntArray,
        bufferWidth: Int,
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
        regionX: Int,
        regionY: Int,
        regionWidth: Int,
        regionHeight: Int,
        effectiveRadius: Float,
        checkRounded: Boolean
    ) {
        val hueDeg = filter.hueRotateDeg
        val hasHue = hueDeg != null && hueDeg != 0f
        val hueRad = if (hasHue) hueDeg!! * (PI.toFloat() / 180f) else 0f
        val cosH = if (hasHue) cos(hueRad) else 1f
        val sinH = if (hasHue) sin(hueRad) else 0f

        for (y in y0 until y1) {
            val rowOff = y * bufferWidth
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
                val px = argbPixels[rowOff + x]
                val a = ((px ushr 24) and 0xFF) / 255f
                val r = ((px ushr 16) and 0xFF) / 255f
                val g = ((px ushr 8) and 0xFF) / 255f
                val b = (px and 0xFF) / 255f
                argbPixels[rowOff + x] = applyColorOpsToNormalizedPixel(filter, r, g, b, a, hasHue, cosH, sinH)
            }
        }
    }

    private fun applyColorOpsToNormalizedPixel(
        filter: CssFilter,
        inR: Float,
        inG: Float,
        inB: Float,
        inA: Float,
        hasHue: Boolean,
        cosH: Float,
        sinH: Float
    ): Int {
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
        val brightnessBeforeInvert = brVal == 0f && invVal != null && invVal != 0f
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

        val opVal = filter.opacity
        if (opVal != null && opVal != 1f) {
            a = (a * opVal.coerceIn(0f, 1f)).coerceIn(0f, 1f)
        }

        val ai = (a * 255f).roundToInt().coerceIn(0, 255)
        val ri = (r * 255f).roundToInt().coerceIn(0, 255)
        val gi = (g * 255f).roundToInt().coerceIn(0, 255)
        val bi = (b * 255f).roundToInt().coerceIn(0, 255)
        return (ai shl 24) or (ri shl 16) or (gi shl 8) or bi
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
}
