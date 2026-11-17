package com.nexustools.genericui.style.layout

import com.nexustools.genericui.geometry.EdgeInsets
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.geometry.Size

/**
 * Computed layout geometry for a single node produced by [BrowserLayoutEngine].
 */
data class ComputedLayoutBox(
    val borderBox: Rect,
    val paddingBox: Rect,
    val contentBox: Rect,
    val margin: EdgeInsets = EdgeInsets.ZERO,
    val padding: EdgeInsets = EdgeInsets.ZERO,
    val border: EdgeInsets = EdgeInsets.ZERO,
    val clipRect: Rect? = null,
    val scrollExtent: Size = Size.ZERO,
    val effectiveZIndex: Int = 0,
    val isVisibleInLayout: Boolean = true,
    val children: List<ComputedLayoutBox> = emptyList()
)
