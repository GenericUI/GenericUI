package com.nexustools.genericui.style.css

import com.nexustools.genericui.style.color.RgbaColor

/**
 * Resolved pixel-space linear gradient representation produced by [GradientStopNormalizer.normalizeLinear]
 * for native platform painters (CoreGraphics, Direct2D, Cairo, Qt6, Skia, Swing).
 */
data class NormalizedLinearGradient(
    val positions: FloatArray,
    val colors: Array<RgbaColor>,
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NormalizedLinearGradient) return false
        return positions.contentEquals(other.positions) &&
            colors.contentEquals(other.colors) &&
            x1 == other.x1 &&
            y1 == other.y1 &&
            x2 == other.x2 &&
            y2 == other.y2
    }

    override fun hashCode(): Int {
        var result = positions.contentHashCode()
        result = 31 * result + colors.contentHashCode()
        result = 31 * result + x1.hashCode()
        result = 31 * result + y1.hashCode()
        result = 31 * result + x2.hashCode()
        result = 31 * result + y2.hashCode()
        return result
    }
}
