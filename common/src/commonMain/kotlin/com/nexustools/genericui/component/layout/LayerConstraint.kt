package com.nexustools.genericui.component.layout

/**
 * Positioning and layering constraints for a child component inside a [LayeredLayout].
 */
data class LayerConstraint(
    val sizingMode: LayerSizingMode = LayerSizingMode.FILL,
    val alignment: LayerAlignment = LayerAlignment.TOP_START,
    val offsetXPx: Int = 0,
    val offsetYPx: Int = 0,
    val leftPercent: Float? = null,
    val topPercent: Float? = null,
    val rightPx: Int? = null,
    val bottomPx: Int? = null,
    val zIndex: Int = 0,
    val passThroughPointerEvents: Boolean = false
) {
    companion object {
        fun fill(
            zIndex: Int = 0,
            passThroughPointerEvents: Boolean = false
        ): LayerConstraint = LayerConstraint(
            sizingMode = LayerSizingMode.FILL,
            zIndex = zIndex,
            passThroughPointerEvents = passThroughPointerEvents
        )

        fun float(
            alignment: LayerAlignment = LayerAlignment.TOP_START,
            offsetXPx: Int = 0,
            offsetYPx: Int = 0,
            leftPercent: Float? = null,
            topPercent: Float? = null,
            rightPx: Int? = null,
            bottomPx: Int? = null,
            zIndex: Int = 10,
            passThroughPointerEvents: Boolean = false
        ): LayerConstraint = LayerConstraint(
            sizingMode = LayerSizingMode.FLOAT,
            alignment = alignment,
            offsetXPx = offsetXPx,
            offsetYPx = offsetYPx,
            leftPercent = leftPercent,
            topPercent = topPercent,
            rightPx = rightPx,
            bottomPx = bottomPx,
            zIndex = zIndex,
            passThroughPointerEvents = passThroughPointerEvents
        )
    }
}
