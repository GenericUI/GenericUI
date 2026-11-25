package com.nexustools.genericui.jvm.style

import com.nexustools.genericui.style.css.CssFilter
import com.nexustools.genericui.style.css.CssFilterColorMatrix
import com.nexustools.genericui.style.css.CssLength
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

/**
 * Shared JVM/Android backdrop-filter capture planner and `CssFilterColorMatrix` adapter used by
 * `SwingHardwareBackdropBlurRenderer`, `AndroidHardwareBackdropBlurController`, and
 * `AndroidImageImpl`.
 *
 * Unifies adaptive downsample factor, blur and drop-shadow halo padding, scaled filter cloning,
 * hardware-only eligibility checks, and 4x5 color matrix conversion (`0..255` translation scale
 * for `android.graphics.ColorMatrix` and normalized `0..1` translation scale for `CssFilterColorMatrix`).
 */
object JvmBackdropCapturePlanner {

    private const val MAX_BLUR_PX = 48f
    private const val MAX_DROP_SHADOW_PAD_PX = 64

    fun planCapture(
        filter: CssFilter,
        targetWidth: Int,
        targetHeight: Int,
        minHardwareBlurPx: Float = 4f
    ): JvmBackdropCapturePlan {
        val blurPx = (filter.blur?.resolvePx() ?: 0f).coerceIn(0f, MAX_BLUR_PX)
        val hasPixelOps = filter.hasPixelOps
        val hasDropShadow = filter.hasDropShadow

        val dsScale = when {
            hasDropShadow -> {
                if (blurPx >= 16f && targetWidth >= 128 && targetHeight >= 128) 2 else 1
            }
            blurPx >= 16f && targetWidth >= 96 && targetHeight >= 96 -> 4
            blurPx >= 6f && targetWidth >= 48 && targetHeight >= 48 -> 2
            else -> 1
        }
        val capW = max(1, (targetWidth + dsScale - 1) / dsScale)
        val capH = max(1, (targetHeight + dsScale - 1) / dsScale)

        val blurPad = if (blurPx > 0f) ceil(blurPx * 1.5f).toInt().coerceIn(0, 48) else 0
        val shadowPad = filter.dropShadow?.takeIf { hasDropShadow }?.let { shadow ->
            val dx = abs(shadow.offsetX.resolvePx() ?: 0f)
            val dy = abs(shadow.offsetY.resolvePx() ?: 0f)
            val sBlur = (shadow.blurRadius.resolvePx() ?: 0f).coerceAtLeast(0f)
            val spread = abs(shadow.spreadRadius.resolvePx() ?: 0f)
            ceil(max(dx, dy) + sBlur * 1.5f + spread).toInt().coerceIn(0, MAX_DROP_SHADOW_PAD_PX)
        } ?: 0
        val paddingPx = max(blurPad, shadowPad)

        val scaledFilter = scaleFilterForDownsample(filter, blurPx, dsScale)
        val supportsHardwareFastBlurOnly =
            !hasPixelOps &&
                !hasDropShadow &&
                blurPx >= minHardwareBlurPx &&
                targetWidth >= 8 &&
                targetHeight >= 8

        return JvmBackdropCapturePlan(
            blurPx = blurPx,
            downsampleScale = dsScale,
            captureWidth = capW,
            captureHeight = capH,
            blurPaddingPx = paddingPx,
            scaledFilter = scaledFilter,
            supportsHardwareFastBlurOnly = supportsHardwareFastBlurOnly,
            hasPixelOps = hasPixelOps,
            hasDropShadow = hasDropShadow
        )
    }

    /**
     * Scales blur and drop-shadow lengths inside [filter] for a downsampled capture buffer.
     */
    fun scaleFilterForDownsample(
        filter: CssFilter,
        resolvedBlurPx: Float = (filter.blur?.resolvePx() ?: 0f).coerceIn(0f, MAX_BLUR_PX),
        downsampleScale: Int
    ): CssFilter {
        if (downsampleScale <= 1) return filter
        val scaleInv = 1f / downsampleScale.toFloat()
        val scaledBlur = if (resolvedBlurPx > 0f) {
            CssLength.px((resolvedBlurPx * scaleInv).coerceAtLeast(1f))
        } else {
            filter.blur
        }
        val scaledShadow = filter.dropShadow?.let { shadow ->
            shadow.copy(
                offsetX = CssLength.px((shadow.offsetX.resolvePx() ?: 0f) * scaleInv),
                offsetY = CssLength.px((shadow.offsetY.resolvePx() ?: 0f) * scaleInv),
                blurRadius = CssLength.px(((shadow.blurRadius.resolvePx() ?: 0f) * scaleInv).coerceAtLeast(0f)),
                spreadRadius = CssLength.px((shadow.spreadRadius.resolvePx() ?: 0f) * scaleInv)
            )
        }
        return filter.copy(
            blur = scaledBlur,
            dropShadow = scaledShadow
        )
    }

    /**
     * Converts [CssFilterColorMatrix.buildMatrix4x5] into an `android.graphics.ColorMatrix`-ready
     * 20-float row-major array whose column-4 translation offsets are scaled to `[0..255]`, or
     * returns `null` when [filter] has no color matrix pixel operations.
     */
    fun buildAndroidColorMatrix20(filter: CssFilter): FloatArray? {
        if (!filter.hasPixelOps) return null
        val matrix = CssFilterColorMatrix.buildMatrix4x5(filter)
        val out = matrix.copyOf()
        out[4] *= 255f
        out[9] *= 255f
        out[14] *= 255f
        out[19] *= 255f
        return out
    }

    /**
     * Returns the normalized 20-float `[0..1]` translation row-major color matrix from
     * [CssFilterColorMatrix.buildMatrix4x5], or `null` when [filter] has no color matrix ops.
     */
    fun buildNormalizedColorMatrix20(filter: CssFilter): FloatArray? {
        if (!filter.hasPixelOps) return null
        return CssFilterColorMatrix.buildMatrix4x5(filter)
    }
}
