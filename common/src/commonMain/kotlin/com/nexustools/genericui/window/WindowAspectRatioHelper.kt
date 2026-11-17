package com.nexustools.genericui.window

import com.nexustools.genericui.geometry.Size
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Shared client-viewport aspect-ratio calculation and clamping helper used by [Window]
 * and all platform `WindowNativeImpl` backends.
 */
object WindowAspectRatioHelper {

    fun validateAspectRatio(aspectRatio: Double?) {
        if (aspectRatio != null) {
            require(aspectRatio.isFinite() && aspectRatio > 0.0) {
                "Window aspect ratio must be finite and positive, got $aspectRatio"
            }
        }
    }

    /**
     * Adjusts [size] to match [aspectRatio] (`width / height`) while staying at or above [minSize].
     */
    fun clampClientSize(
        size: Size,
        aspectRatio: Double?,
        minSize: Size? = null,
        preferWidth: Boolean = true
    ): Size {
        val minW = (minSize?.width ?: 1).coerceAtLeast(1)
        val minH = (minSize?.height ?: 1).coerceAtLeast(1)
        var w = max(size.width, minW)
        var h = max(size.height, minH)
        val ratio = aspectRatio ?: return Size(w, h)
        validateAspectRatio(ratio)

        if (preferWidth) {
            h = max((w.toDouble() / ratio).roundToInt(), 1)
            if (h < minH) {
                h = minH
                w = max(ceil(h.toDouble() * ratio).toInt(), minW)
            }
        } else {
            w = max((h.toDouble() * ratio).roundToInt(), 1)
            if (w < minW) {
                w = minW
                h = max(ceil(w.toDouble() / ratio).toInt(), minH)
            }
        }
        return Size(w, h)
    }

    /**
     * Resolves an interactive resize from [previousSize] to [requestedSize] along whichever axis changed more,
     * preserving [aspectRatio] and [minSize].
     */
    fun enforceResizeDelta(
        previousSize: Size,
        requestedSize: Size,
        aspectRatio: Double?,
        minSize: Size? = null
    ): Size {
        val ratio = aspectRatio ?: return clampClientSize(requestedSize, null, minSize)
        validateAspectRatio(ratio)
        val dw = abs(requestedSize.width - previousSize.width)
        val dh = abs(requestedSize.height - previousSize.height)
        val preferWidth = when {
            dh > 0 && dw == 0 -> false
            dw > 0 && dh == 0 -> true
            else -> {
                val relW = if (previousSize.width > 0) dw.toDouble() / previousSize.width else dw.toDouble()
                val relH = if (previousSize.height > 0) dh.toDouble() / previousSize.height else dh.toDouble()
                relW >= relH
            }
        }
        return clampClientSize(requestedSize, ratio, minSize, preferWidth = preferWidth)
    }

    /**
     * Returns `true` when [size] matches [aspectRatio] within a small integer rounding tolerance ([tolerancePx]).
     */
    fun matchesAspectRatio(size: Size, aspectRatio: Double, tolerancePx: Int = 2): Boolean {
        if (size.width <= 0 || size.height <= 0) return false
        val expectedHeight = (size.width.toDouble() / aspectRatio).roundToInt()
        val expectedWidth = (size.height.toDouble() * aspectRatio).roundToInt()
        return abs(size.height - expectedHeight) <= tolerancePx || abs(size.width - expectedWidth) <= tolerancePx
    }
}
