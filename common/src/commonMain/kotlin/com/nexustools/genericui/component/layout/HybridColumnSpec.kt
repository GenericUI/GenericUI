package com.nexustools.genericui.component.layout

/**
 * Specification for a column managed by [HybridColumnLayout] and [com.nexustools.genericui.component.fragment.helper.HybridColumnFragment].
 *
 * Supports both Bootstrap 12-grid column spans (`colSpan`, `colSm`, `colMd`, `colLg`, `colXl`) and
 * proportional flex weights (`weight`), as well as compact-mode sizing (`compactHeightPx`, `compactAspectRatio`)
 * and tab metadata (`title`, `icon`) when the column is collapsed into a tab in [HybridLayoutMode.SINGLE].
 */
data class HybridColumnSpec(
    val id: String,
    val title: String = id,
    val icon: String? = null,
    val weight: Float = 1.0f,
    val colSpan: Int? = null,
    val colSm: Int? = null,
    val colMd: Int? = null,
    val colLg: Int? = null,
    val colXl: Int? = null,
    val minWidthPx: Int? = null,
    val maxWidthPx: Int? = null,
    val compactHeightPx: Int? = null,
    val compactAspectRatio: Float? = null,
    val scrollableInColumnMode: Boolean = false,
    val scrollableInSingleMode: Boolean = false,
    val visible: Boolean = true,
    val canCollapseToTab: Boolean = true
) {
    /**
     * Resolves the effective Bootstrap 12-grid span (`1..12`) for the given `viewportWidthDp`, or `null` if weighted flex sizing is used.
     */
    fun resolveBootstrapColSpan(viewportWidthDp: Int): Int? {
        val raw = when {
            viewportWidthDp >= 1200 -> colXl ?: colLg ?: colMd ?: colSm ?: colSpan
            viewportWidthDp >= 992 -> colLg ?: colMd ?: colSm ?: colSpan
            viewportWidthDp >= 768 -> colMd ?: colSm ?: colSpan
            viewportWidthDp >= 576 -> colSm ?: colSpan
            else -> colSpan
        }
        return raw?.coerceIn(1, 12)
    }
}
