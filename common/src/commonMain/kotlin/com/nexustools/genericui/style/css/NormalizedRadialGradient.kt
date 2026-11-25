package com.nexustools.genericui.style.css

import com.nexustools.genericui.style.color.RgbaColor

/**
 * Resolved pixel-space radial gradient representation produced by [GradientStopNormalizer.normalizeRadial]
 * for native platform painters (CoreGraphics, Direct2D, Cairo, Qt6, Skia, Swing).
 */
data class NormalizedRadialGradient(
    val positions: FloatArray,
    val colors: Array<RgbaColor>,
    val cx: Float,
    val cy: Float,
    val radiusX: Float,
    val radiusY: Float
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NormalizedRadialGradient) return false
        return positions.contentEquals(other.positions) &&
            colors.contentEquals(other.colors) &&
            cx == other.cx &&
            cy == other.cy &&
            radiusX == other.radiusX &&
            radiusY == other.radiusY
    }

    override fun hashCode(): Int {
        var result = positions.contentHashCode()
        result = 31 * result + colors.contentHashCode()
        result = 31 * result + cx.hashCode()
        result = 31 * result + cy.hashCode()
        result = 31 * result + radiusX.hashCode()
        result = 31 * result + radiusY.hashCode()
        return result
    }
}
