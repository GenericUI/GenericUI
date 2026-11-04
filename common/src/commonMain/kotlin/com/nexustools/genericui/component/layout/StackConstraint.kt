package com.nexustools.genericui.component.layout

import com.nexustools.genericui.enums.bootstrap.AlignSelf

/**
 * Per-child layout constraint for [VStack] and [HStack].
 *
 * Supports expanding weights (`flex-grow`), shrink factors (`flex-shrink`), flex basis,
 * aspect ratio constraints (e.g. `16:9` video panes), cross-axis stretch (`fillCrossAxis`),
 * explicit sizes, and min/max size bounds across Mobile, Web, and Desktop backends.
 */
data class StackConstraint(
    val weight: Float? = null,
    val flexShrink: Float? = null,
    val flexBasis: String? = null,
    val aspectRatio: Float? = null,
    val fillCrossAxis: Boolean = true,
    val shrinkCrossAxis: Boolean = false,
    val alignSelf: AlignSelf? = null,
    val widthPx: Int? = null,
    val heightPx: Int? = null,
    val minWidthPx: Int? = null,
    val minHeightPx: Int? = null,
    val maxWidthPx: Int? = null,
    val maxHeightPx: Int? = null
) {
    val isExpanding: Boolean
        get() = (weight ?: 0f) > 0f

    companion object {
        /**
         * Fixed-size stack item that neither grows nor shrinks along the main axis.
         */
        val FIXED: StackConstraint = StackConstraint(
            weight = 0f,
            flexShrink = 0f,
            flexBasis = "auto",
            fillCrossAxis = true
        )

        /**
         * Expanding stack item with unit weight (`1f`) that fills remaining main-axis space.
         */
        val EXPAND: StackConstraint = StackConstraint(
            weight = 1f,
            flexShrink = 1f,
            flexBasis = "0%",
            minWidthPx = 0,
            minHeightPx = 0,
            fillCrossAxis = true
        )

        /**
         * Fixed 16:9 aspect-ratio item that stretches across the cross axis without shrinking.
         */
        val ASPECT_16_9: StackConstraint = StackConstraint(
            weight = 0f,
            flexShrink = 0f,
            flexBasis = "auto",
            aspectRatio = 16f / 9f,
            fillCrossAxis = true
        )

        /**
         * Creates an expanding constraint with the specified [weight].
         */
        fun weight(
            weight: Float,
            fillCrossAxis: Boolean = true,
            minMainAxisPx: Int = 0,
            shrinkCrossAxis: Boolean = false
        ): StackConstraint {
            val coerced = weight.coerceAtLeast(0f)
            return if (coerced > 0f) {
                StackConstraint(
                    weight = coerced,
                    flexShrink = 1f,
                    flexBasis = "0%",
                    minWidthPx = minMainAxisPx,
                    minHeightPx = minMainAxisPx,
                    fillCrossAxis = fillCrossAxis,
                    shrinkCrossAxis = shrinkCrossAxis
                )
            } else {
                FIXED.copy(fillCrossAxis = fillCrossAxis, shrinkCrossAxis = shrinkCrossAxis)
            }
        }

        /**
         * Creates a fixed-size constraint with optional explicit [widthPx] and [heightPx].
         */
        fun fixed(
            widthPx: Int? = null,
            heightPx: Int? = null,
            fillCrossAxis: Boolean = false,
            shrinkCrossAxis: Boolean = false
        ): StackConstraint = StackConstraint(
            weight = 0f,
            flexShrink = 0f,
            flexBasis = "auto",
            fillCrossAxis = fillCrossAxis,
            shrinkCrossAxis = shrinkCrossAxis,
            widthPx = widthPx,
            heightPx = heightPx,
            minWidthPx = widthPx,
            minHeightPx = heightPx
        )

        /**
         * Creates a fixed aspect-ratio constraint (width / height) that does not grow or shrink.
         */
        fun aspectRatio(
            ratio: Float,
            fillCrossAxis: Boolean = true,
            shrinkCrossAxis: Boolean = false
        ): StackConstraint = StackConstraint(
            weight = 0f,
            flexShrink = 0f,
            flexBasis = "auto",
            aspectRatio = ratio.coerceAtLeast(0.01f),
            fillCrossAxis = fillCrossAxis,
            shrinkCrossAxis = shrinkCrossAxis
        )

        /**
         * Creates a constraint whose cross-axis size shrinks to match the largest non-shrinking
         * sibling in the stack rather than expanding the stack to this child's intrinsic cross-axis size.
         */
        fun shrinkCrossAxis(
            weight: Float? = null,
            aspectRatio: Float? = null
        ): StackConstraint = StackConstraint(
            weight = weight,
            flexShrink = if ((weight ?: 0f) > 0f) 1f else 0f,
            flexBasis = if ((weight ?: 0f) > 0f) "0%" else "auto",
            aspectRatio = aspectRatio,
            fillCrossAxis = true,
            shrinkCrossAxis = true
        )
    }
}
