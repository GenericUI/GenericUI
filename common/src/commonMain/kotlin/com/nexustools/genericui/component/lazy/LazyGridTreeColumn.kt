package com.nexustools.genericui.component.lazy

/**
 * Column descriptor for a [LazyGridTree].
 *
 * @property id Unique column identifier (e.g., `"key"`, `"value"`, `"type"`, `"actions"`).
 * @property headerText Display title shown in the optional header row.
 * @property widthWeight Relative column width weight (`1f` by default).
 * @property minWidthPx Minimum column width in pixels.
 * @property isTreeColumn Whether this column renders hierarchical tree indentation and the
 *   branch expand/collapse toggle button (`▼` / `▶`). Normally `true` for column `0`.
 */
data class LazyGridTreeColumn(
    val id: String,
    val headerText: String,
    val widthWeight: Float = 1f,
    val minWidthPx: Int = 80,
    val isTreeColumn: Boolean = false
)
