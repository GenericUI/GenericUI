package com.nexustools.genericui.component.layout

/**
 * Bootstrap 12-column responsive card item specification for [ResponsiveBentoGridLayout]
 * and [com.nexustools.genericui.component.fragment.helper.ResponsiveBentoGridFragment].
 */
data class BentoGridItemSpec(
    val id: String,
    val title: String = "",
    val colXs: Int = 12,
    val colSm: Int = 12,
    val colMd: Int = 6,
    val colLg: Int = 4,
    val colXl: Int = 3,
    val rowSpan: Int = 1,
    val minHeightPx: Int? = null
) {
    /**
     * Resolves the active Bootstrap 12-grid column span (`1..12`) for the given [viewportWidthDp].
     * On small-screen mobile ([isSmallScreenMobile] == `true`) or narrow viewports (`< 600dp`),
     * returns [colXs] (default `12` for a single-column vertical feed).
     */
    fun resolveColSpan(viewportWidthDp: Int, isSmallScreenMobile: Boolean = false): Int {
        if (isSmallScreenMobile || viewportWidthDp < 600) {
            return colXs.coerceIn(1, 12)
        }
        val raw = when {
            viewportWidthDp >= 1200 -> colXl
            viewportWidthDp >= 992 -> colLg
            viewportWidthDp >= 600 -> colMd
            viewportWidthDp >= 576 -> colSm
            else -> colXs
        }
        return raw.coerceIn(1, 12)
    }
}
