package com.nexustools.genericui.style.layout

import com.nexustools.genericui.component.action.Button
import com.nexustools.genericui.component.action.StyledButton
import com.nexustools.genericui.component.layout.LayerAlignment
import com.nexustools.genericui.component.lazy.LazyList
import com.nexustools.genericui.component.navigation.BottomNavigationBar
import com.nexustools.genericui.component.navigation.Navbar
import com.nexustools.genericui.component.surface.TextBubble
import com.nexustools.genericui.component.tabs.Tab
import com.nexustools.genericui.component.tabs.TabContainer
import com.nexustools.genericui.component.typography.Badge
import com.nexustools.genericui.component.typography.Heading
import com.nexustools.genericui.component.typography.Text
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.enums.bootstrap.AlignItems
import com.nexustools.genericui.enums.bootstrap.AlignSelf
import com.nexustools.genericui.enums.bootstrap.FlexDirection
import com.nexustools.genericui.enums.bootstrap.FlexWrap
import com.nexustools.genericui.enums.bootstrap.JustifyContent
import com.nexustools.genericui.enums.bootstrap.OverflowBehavior
import com.nexustools.genericui.enums.css.CssDisplay
import com.nexustools.genericui.enums.css.CssPosition
import com.nexustools.genericui.enums.css.CssUnit
import com.nexustools.genericui.enums.css.CssVisibility
import com.nexustools.genericui.geometry.EdgeInsets
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.style.css.CssLength
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Pure-Kotlin cross-backend CSS Box Model, Flexbox, Bootstrap 12-Column Grid, Aspect-Ratio,
 * and Absolute/Layered layout engine.
 *
 * Computes deterministic, browser-identical [ComputedLayoutBox] trees across all GenericUI
 * backends (`:windows`, `:macos`, `:gtk`, `:qt`, `:swing`, `:android`, `:ios`, `:web`).
 */
class BrowserLayoutEngine<N>(
    private val adapter: LayoutNodeAdapter<N>,
    private val measurer: IntrinsicSizeMeasurer<N>
) {

    /**
     * Computes the full [ComputedLayoutBox] hierarchy rooted at [root] within [viewportRect].
     */
    fun computeLayout(root: N, viewportRect: Rect): ComputedLayoutBox {
        val rootStyle = adapter.layoutStyleOf(root, viewportRect.width, viewportRect.height)
        if (rootStyle.display == CssDisplay.NONE) {
            return createHiddenBoxTree(root)
        }
        val rootWidth = resolveLengthPx(
            length = rootStyle.width,
            parentSizePx = viewportRect.width,
            viewportRect = viewportRect
        ) ?: viewportRect.width
        val rootHeight = resolveLengthPx(
            length = rootStyle.height,
            parentSizePx = viewportRect.height,
            viewportRect = viewportRect
        ) ?: if (rootStyle.aspectRatio != null && rootStyle.aspectRatio > 0f) {
            (rootWidth / rootStyle.aspectRatio).roundToInt()
        } else {
            viewportRect.height
        }
        val clampedW = clampDimension(rootWidth, rootStyle.minWidth, rootStyle.maxWidth, viewportRect.width, viewportRect)
        val clampedH = clampDimension(rootHeight, rootStyle.minHeight, rootStyle.maxHeight, viewportRect.height, viewportRect)
        val rootBorderBox = Rect(viewportRect.x, viewportRect.y, clampedW, clampedH)
        return layoutNodeWithAllocatedBox(
            node = root,
            style = rootStyle,
            borderBox = rootBorderBox,
            margin = resolveMarginInsets(rootStyle, viewportRect.width, viewportRect.height, viewportRect),
            viewportRect = viewportRect,
            inheritedZIndex = rootStyle.zIndex
        )
    }

    private fun createHiddenBoxTree(node: N): ComputedLayoutBox {
        val childrenBoxes = adapter.childrenOf(node).map { createHiddenBoxTree(it) }
        return ComputedLayoutBox(
            borderBox = Rect.ZERO,
            paddingBox = Rect.ZERO,
            contentBox = Rect.ZERO,
            isVisibleInLayout = false,
            children = childrenBoxes
        )
    }

    private fun layoutNodeWithAllocatedBox(
        node: N,
        style: LayoutStyleSnapshot,
        borderBox: Rect,
        margin: EdgeInsets,
        viewportRect: Rect,
        inheritedZIndex: Int
    ): ComputedLayoutBox {
        if (style.display == CssDisplay.NONE) {
            return createHiddenBoxTree(node)
        }

        val effectiveZIndex = if (style.zIndex != 0) style.zIndex else inheritedZIndex
        val visibleInLayout = style.visibility == CssVisibility.VISIBLE

        val paddingBox = Rect(
            x = borderBox.x + style.border.left,
            y = borderBox.y + style.border.top,
            width = (borderBox.width - style.border.left - style.border.right).coerceAtLeast(0),
            height = (borderBox.height - style.border.top - style.border.bottom).coerceAtLeast(0)
        )
        val contentBox = Rect(
            x = paddingBox.x + style.padding.left,
            y = paddingBox.y + style.padding.top,
            width = (paddingBox.width - style.padding.left - style.padding.right).coerceAtLeast(0),
            height = (paddingBox.height - style.padding.top - style.padding.bottom).coerceAtLeast(0)
        )

        val rawChildren = adapter.childrenOf(node)
        if (rawChildren.isEmpty()) {
            val clip = if (style.overflowX != OverflowBehavior.VISIBLE || style.overflowY != OverflowBehavior.VISIBLE) {
                paddingBox
            } else {
                null
            }
            return ComputedLayoutBox(
                borderBox = borderBox,
                paddingBox = paddingBox,
                contentBox = contentBox,
                margin = margin,
                padding = style.padding,
                border = style.border,
                clipRect = clip,
                scrollExtent = Size(contentBox.width, contentBox.height),
                effectiveZIndex = effectiveZIndex,
                isVisibleInLayout = visibleInLayout,
                children = emptyList()
            )
        }

        val childStyles = rawChildren.map { child ->
            adapter.layoutStyleOf(child, viewportRect.width, viewportRect.height)
        }
        val childResults = arrayOfNulls<ComputedLayoutBox>(rawChildren.size)

        val normalFlowIndices = mutableListOf<Int>()
        val positionedIndices = mutableListOf<Int>()

        for (i in rawChildren.indices) {
            val cs = childStyles[i]
            when {
                cs.display == CssDisplay.NONE -> {
                    childResults[i] = createHiddenBoxTree(rawChildren[i])
                }
                cs.position == CssPosition.ABSOLUTE || cs.position == CssPosition.FIXED || cs.layerRole != null -> {
                    positionedIndices.add(i)
                }
                else -> {
                    normalFlowIndices.add(i)
                }
            }
        }

        val normalizedTag = adapter.tagNameOf(node).trim().lowercase().removePrefix("genericui:")
        if (normalizedTag == "tabcontainer" || normalizedTag == "tab-container") {
            layoutTabContainerChildren(
                rawChildren = rawChildren,
                childStyles = childStyles,
                normalFlowIndices = normalFlowIndices,
                contentBox = contentBox,
                viewportRect = viewportRect,
                effectiveZIndex = effectiveZIndex,
                outResults = childResults
            )
        } else if (
            style.display == CssDisplay.FLEX ||
            style.display == CssDisplay.INLINE_FLEX ||
            style.display == CssDisplay.GRID ||
            style.isGridRow
        ) {
            layoutFlexChildren(
                parentStyle = style,
                rawChildren = rawChildren,
                childStyles = childStyles,
                normalFlowIndices = normalFlowIndices,
                contentBox = contentBox,
                viewportRect = viewportRect,
                effectiveZIndex = effectiveZIndex,
                outResults = childResults
            )
        } else {
            layoutBlockChildren(
                parentStyle = style,
                rawChildren = rawChildren,
                childStyles = childStyles,
                normalFlowIndices = normalFlowIndices,
                contentBox = contentBox,
                viewportRect = viewportRect,
                effectiveZIndex = effectiveZIndex,
                outResults = childResults
            )
        }

        for (idx in positionedIndices) {
            val child = rawChildren[idx]
            val cs = childStyles[idx]
            val containingBox = if (cs.position == CssPosition.FIXED) viewportRect else paddingBox
            childResults[idx] = layoutPositionedChild(
                child = child,
                childStyle = cs,
                containingBox = containingBox,
                viewportRect = viewportRect,
                parentZIndex = effectiveZIndex
            )
        }

        val finalChildren = childResults.mapIndexed { idx, box ->
            box ?: createHiddenBoxTree(rawChildren[idx])
        }

        var maxRight = contentBox.right
        var maxBottom = contentBox.bottom
        for (childBox in finalChildren) {
            if (childBox.isVisibleInLayout) {
                maxRight = max(maxRight, childBox.borderBox.right)
                maxBottom = max(maxBottom, childBox.borderBox.bottom)
            }
        }
        val scrollExtent = Size(
            width = (maxRight - contentBox.x).coerceAtLeast(contentBox.width),
            height = (maxBottom - contentBox.y).coerceAtLeast(contentBox.height)
        )
        val clipRect = if (style.overflowX != OverflowBehavior.VISIBLE || style.overflowY != OverflowBehavior.VISIBLE) {
            paddingBox
        } else {
            null
        }

        return ComputedLayoutBox(
            borderBox = borderBox,
            paddingBox = paddingBox,
            contentBox = contentBox,
            margin = margin,
            padding = style.padding,
            border = style.border,
            clipRect = clipRect,
            scrollExtent = scrollExtent,
            effectiveZIndex = effectiveZIndex,
            isVisibleInLayout = visibleInLayout,
            children = finalChildren
        )
    }

    private fun layoutTabContainerChildren(
        rawChildren: List<N>,
        childStyles: List<LayoutStyleSnapshot>,
        normalFlowIndices: List<Int>,
        contentBox: Rect,
        viewportRect: Rect,
        effectiveZIndex: Int,
        outResults: Array<ComputedLayoutBox?>
    ) {
        val tabIndices = mutableListOf<Int>()
        val contentIndices = mutableListOf<Int>()
        for (idx in normalFlowIndices) {
            val childTag = adapter.tagNameOf(rawChildren[idx]).trim().lowercase().removePrefix("genericui:")
            if (childTag == "tab" || childTag == "badgetab") {
                tabIndices.add(idx)
            } else {
                contentIndices.add(idx)
            }
        }
        val headerHeight = if (tabIndices.isNotEmpty()) min(44, contentBox.height.coerceAtLeast(44)) else 0
        var cursorX = contentBox.x
        val tabCount = tabIndices.size.coerceAtLeast(1)
        val defaultTabWidth = (contentBox.width / tabCount).coerceIn(72, 180)
        for (idx in tabIndices) {
            val child = rawChildren[idx]
            val cs = childStyles[idx]
            val intrinsic = measurer.measureIntrinsicSize(child, contentBox.width, headerHeight)
            val tabW = resolveLengthPx(cs.width, contentBox.width, viewportRect)
                ?: intrinsic.width.takeIf { it > 0 }
                ?: defaultTabWidth
            val tabRect = Rect(cursorX, contentBox.y, tabW, headerHeight)
            outResults[idx] = layoutNodeWithAllocatedBox(
                node = child,
                style = cs,
                borderBox = tabRect,
                margin = EdgeInsets.ZERO,
                viewportRect = viewportRect,
                inheritedZIndex = effectiveZIndex
            )
            cursorX += tabW
        }

        val bodyY = contentBox.y + headerHeight
        val bodyH = (contentBox.height - headerHeight).coerceAtLeast(0)
        for (idx in contentIndices) {
            val child = rawChildren[idx]
            val cs = childStyles[idx]
            val bodyRect = Rect(contentBox.x, bodyY, contentBox.width, bodyH)
            outResults[idx] = layoutNodeWithAllocatedBox(
                node = child,
                style = cs,
                borderBox = bodyRect,
                margin = EdgeInsets.ZERO,
                viewportRect = viewportRect,
                inheritedZIndex = effectiveZIndex
            )
        }
    }

    private fun layoutBlockChildren(
        parentStyle: LayoutStyleSnapshot,
        rawChildren: List<N>,
        childStyles: List<LayoutStyleSnapshot>,
        normalFlowIndices: List<Int>,
        contentBox: Rect,
        viewportRect: Rect,
        effectiveZIndex: Int,
        outResults: Array<ComputedLayoutBox?>
    ) {
        var cursorY = contentBox.y
        val rowGap = parentStyle.rowGapPx
        for ((pos, idx) in normalFlowIndices.withIndex()) {
            val child = rawChildren[idx]
            val cs = childStyles[idx]
            val mar = resolveMarginInsets(cs, contentBox.width, contentBox.height, viewportRect)
            val availableW = (contentBox.width - mar.left - mar.right).coerceAtLeast(0)

            val explicitW = resolveLengthPx(cs.width, contentBox.width, viewportRect)
            val rawW = when {
                explicitW != null -> explicitW
                cs.display == CssDisplay.INLINE || cs.display == CssDisplay.INLINE_BLOCK || cs.display == CssDisplay.INLINE_FLEX ->
                    measureAutoDimension(child, cs, availableW, contentBox.height, viewportRect).width
                else -> availableW
            }
            val childW = clampDimension(rawW, cs.minWidth, cs.maxWidth, contentBox.width, viewportRect)

            val explicitH = resolveLengthPx(cs.height, contentBox.height, viewportRect)
            val rawH = when {
                explicitH != null -> explicitH
                cs.aspectRatio != null && cs.aspectRatio > 0f -> (childW / cs.aspectRatio).roundToInt()
                else -> measureAutoDimension(child, cs, childW, contentBox.height, viewportRect).height
            }
            val childH = clampDimension(rawH, cs.minHeight, cs.maxHeight, contentBox.height, viewportRect)

            val childX = if (cs.marginLeft?.isAuto == true && cs.marginRight?.isAuto == true) {
                contentBox.x + ((contentBox.width - childW) / 2).coerceAtLeast(0)
            } else if (cs.marginLeft?.isAuto == true) {
                contentBox.x + (contentBox.width - childW - mar.right).coerceAtLeast(0)
            } else {
                contentBox.x + mar.left
            }
            val childY = cursorY + mar.top
            val childRect = Rect(childX, childY, childW, childH)

            outResults[idx] = layoutNodeWithAllocatedBox(
                node = child,
                style = cs,
                borderBox = childRect,
                margin = mar,
                viewportRect = viewportRect,
                inheritedZIndex = effectiveZIndex
            )

            cursorY = childRect.bottom + mar.bottom + if (pos < normalFlowIndices.lastIndex) rowGap else 0
        }
    }

    private class FlexItemRecord(
        val childIndex: Int,
        val style: LayoutStyleSnapshot,
        val margin: EdgeInsets,
        var baseMainSize: Int,
        var mainSize: Int,
        var crossSize: Int,
        var frozen: Boolean = false
    )

    private class FlexLine(
        val items: MutableList<FlexItemRecord> = mutableListOf(),
        var crossSize: Int = 0
    )

    private fun layoutFlexChildren(
        parentStyle: LayoutStyleSnapshot,
        rawChildren: List<N>,
        childStyles: List<LayoutStyleSnapshot>,
        normalFlowIndices: List<Int>,
        contentBox: Rect,
        viewportRect: Rect,
        effectiveZIndex: Int,
        outResults: Array<ComputedLayoutBox?>
    ) {
        if (normalFlowIndices.isEmpty()) return

        val isRow = parentStyle.flexDirection == FlexDirection.ROW ||
            parentStyle.flexDirection == FlexDirection.ROW_REVERSE
        val isReverse = parentStyle.flexDirection == FlexDirection.ROW_REVERSE ||
            parentStyle.flexDirection == FlexDirection.COLUMN_REVERSE

        val innerMainSize = if (isRow) contentBox.width else contentBox.height
        val innerCrossSize = if (isRow) contentBox.height else contentBox.width
        val mainGap = if (isRow) parentStyle.columnGapPx else parentStyle.rowGapPx
        val crossGap = if (isRow) parentStyle.rowGapPx else parentStyle.columnGapPx

        // Pre-count grid columns if all items fit in a single 12-col row so gap doesn't cause unwanted wrap
        val totalSpanIfRow = if (isRow) {
            normalFlowIndices.sumOf { childStyles[it].gridColSpan ?: 0 }
        } else 0
        val singleLineGridGapDeduction = if (isRow && totalSpanIfRow in 1..12 && normalFlowIndices.size > 1) {
            (normalFlowIndices.size - 1) * mainGap
        } else 0

        val records = normalFlowIndices.map { idx ->
            val child = rawChildren[idx]
            val cs = childStyles[idx]
            val mar = resolveMarginInsets(cs, contentBox.width, contentBox.height, viewportRect)
            val mainMargin = if (isRow) mar.left + mar.right else mar.top + mar.bottom
            val crossMargin = if (isRow) mar.top + mar.bottom else mar.left + mar.right

            // Determine preliminary cross size for aspect-ratio or child auto measurement
            val effectiveAlign = resolveItemAlignment(parentStyle.alignItems, cs.alignSelf)
            val explicitCross = resolveLengthPx(
                length = if (isRow) cs.height else cs.width,
                parentSizePx = innerCrossSize,
                viewportRect = viewportRect
            )
            val prelimCrossSize = when {
                explicitCross != null -> clampDimension(
                    value = explicitCross,
                    minLen = if (isRow) cs.minHeight else cs.minWidth,
                    maxLen = if (isRow) cs.maxHeight else cs.maxWidth,
                    parentSizePx = innerCrossSize,
                    viewportRect = viewportRect
                )
                effectiveAlign == AlignItems.STRETCH && innerCrossSize > 0 -> clampDimension(
                    value = (innerCrossSize - crossMargin).coerceAtLeast(0),
                    minLen = if (isRow) cs.minHeight else cs.minWidth,
                    maxLen = if (isRow) cs.maxHeight else cs.maxWidth,
                    parentSizePx = innerCrossSize,
                    viewportRect = viewportRect
                )
                else -> 0
            }

            // Determine flex base main size
            val basisLen = cs.flexBasis
            val mainPropLen = if (isRow) cs.width else cs.height
            val rawBaseMain = when {
                // Explicit flex-basis: 0% / 0px with flex-grow > 0 (e.g. HybridColumnLayout 8:4 columns)
                basisLen != null && !basisLen.isAuto && basisLen.value == 0f && cs.flexGrow > 0f -> 0
                // Bootstrap 12-column span in a row when flex-basis is not 0%
                isRow && cs.gridColSpan != null -> {
                    val distributable = (innerMainSize - singleLineGridGapDeduction).coerceAtLeast(0)
                    ((distributable * (cs.gridColSpan / 12f)).roundToInt() - mainMargin).coerceAtLeast(0)
                }
                basisLen != null && !basisLen.isAuto -> {
                    resolveLengthPx(basisLen, innerMainSize, viewportRect) ?: 0
                }
                mainPropLen != null && !mainPropLen.isAuto -> {
                    // In a flex-column container, if an item has aspect-ratio and no explicit CSS height, use width / aspectRatio
                    if (!isRow && cs.aspectRatio != null && cs.aspectRatio > 0f) {
                        val w = if (prelimCrossSize > 0) prelimCrossSize else contentBox.width
                        (w / cs.aspectRatio).roundToInt()
                    } else {
                        resolveLengthPx(mainPropLen, innerMainSize, viewportRect) ?: 0
                    }
                }
                !isRow && cs.aspectRatio != null && cs.aspectRatio > 0f -> {
                    val w = if (prelimCrossSize > 0) prelimCrossSize else (contentBox.width - crossMargin).coerceAtLeast(0)
                    (w / cs.aspectRatio).roundToInt()
                }
                isRow && cs.aspectRatio != null && cs.aspectRatio > 0f && prelimCrossSize > 0 -> {
                    (prelimCrossSize * cs.aspectRatio).roundToInt()
                }
                isRow && cs.isGridAutoCol && cs.flexGrow == 0f -> 0
                else -> {
                    val availW = if (isRow) (innerMainSize - mainMargin).coerceAtLeast(0) else if (prelimCrossSize > 0) prelimCrossSize else (innerCrossSize - crossMargin).coerceAtLeast(0)
                    val availH = if (isRow) (if (prelimCrossSize > 0) prelimCrossSize else innerCrossSize) else (innerMainSize - mainMargin).coerceAtLeast(0)
                    val autoSize = measureAutoDimension(child, cs, availW, availH, viewportRect)
                    if (isRow) autoSize.width else autoSize.height
                }
            }

            val clampedBaseMain = clampDimension(
                value = rawBaseMain,
                minLen = if (isRow) cs.minWidth else cs.minHeight,
                maxLen = if (isRow) cs.maxWidth else cs.maxHeight,
                parentSizePx = innerMainSize,
                viewportRect = viewportRect
            )

            FlexItemRecord(
                childIndex = idx,
                style = cs,
                margin = mar,
                baseMainSize = clampedBaseMain,
                mainSize = clampedBaseMain,
                crossSize = prelimCrossSize
            )
        }

        // Partition into flex lines (supporting NOWRAP and WRAP)
        val lines = mutableListOf<FlexLine>()
        if (parentStyle.flexWrap == FlexWrap.NOWRAP || innerMainSize <= 0) {
            lines.add(FlexLine(items = records.toMutableList()))
        } else {
            var currentLine = FlexLine()
            var usedMain = 0
            for (rec in records) {
                val itemOuterMain = rec.baseMainSize + if (isRow) (rec.margin.left + rec.margin.right) else (rec.margin.top + rec.margin.bottom)
                val gapIfAdded = if (currentLine.items.isEmpty()) 0 else mainGap
                if (currentLine.items.isNotEmpty() && usedMain + gapIfAdded + itemOuterMain > innerMainSize) {
                    lines.add(currentLine)
                    currentLine = FlexLine()
                    usedMain = 0
                }
                if (currentLine.items.isNotEmpty()) {
                    usedMain += mainGap
                }
                currentLine.items.add(rec)
                usedMain += itemOuterMain
            }
            if (currentLine.items.isNotEmpty()) {
                lines.add(currentLine)
            }
        }

        // Resolve flexible lengths (grow / shrink) and cross sizes per line
        for (line in lines) {
            val lineItems = line.items
            val lineGaps = if (lineItems.size > 1) (lineItems.size - 1) * mainGap else 0
            val totalMargins = lineItems.sumOf { rec ->
                if (isRow) rec.margin.left + rec.margin.right else rec.margin.top + rec.margin.bottom
            }
            val totalBaseMain = lineItems.sumOf { it.baseMainSize }
            val freeSpace = innerMainSize - totalBaseMain - totalMargins - lineGaps

            if (freeSpace > 0) {
                val totalGrow = lineItems.sumOf { rec ->
                    val effectiveGrow = if (rec.style.flexGrow > 0f) {
                        rec.style.flexGrow
                    } else if (isRow && rec.style.isGridAutoCol && rec.style.gridColSpan == null) {
                        1f
                    } else {
                        0f
                    }
                    effectiveGrow.toDouble()
                }.toFloat()

                if (totalGrow > 0f) {
                    var remainingSpace = freeSpace
                    var remainingGrow = totalGrow
                    for (rec in lineItems) {
                        val effectiveGrow = if (rec.style.flexGrow > 0f) {
                            rec.style.flexGrow
                        } else if (isRow && rec.style.isGridAutoCol && rec.style.gridColSpan == null) {
                            1f
                        } else {
                            0f
                        }
                        if (effectiveGrow > 0f && remainingGrow > 0f) {
                            val share = ((remainingSpace * (effectiveGrow / remainingGrow)).roundToInt())
                            val targetMain = clampDimension(
                                value = rec.baseMainSize + share,
                                minLen = if (isRow) rec.style.minWidth else rec.style.minHeight,
                                maxLen = if (isRow) rec.style.maxWidth else rec.style.maxHeight,
                                parentSizePx = innerMainSize,
                                viewportRect = viewportRect
                            )
                            val delta = targetMain - rec.baseMainSize
                            rec.mainSize = targetMain
                            remainingSpace = (remainingSpace - delta).coerceAtLeast(0)
                            remainingGrow = (remainingGrow - effectiveGrow).coerceAtLeast(0f)
                        }
                    }
                }
            } else if (freeSpace < 0) {
                // Do not shrink aspect-ratio items along the column axis unless explicitly requested
                val totalScaledShrink = lineItems.sumOf { rec ->
                    val canShrink = if (!isRow && rec.style.aspectRatio != null && rec.style.aspectRatio > 0f) {
                        0f
                    } else {
                        rec.style.flexShrink
                    }
                    (canShrink * rec.baseMainSize.coerceAtLeast(1)).toDouble()
                }.toFloat()

                if (totalScaledShrink > 0f) {
                    val deficit = -freeSpace
                    var remainingDeficit = deficit
                    var remainingScaled = totalScaledShrink
                    for (rec in lineItems) {
                        val canShrink = if (!isRow && rec.style.aspectRatio != null && rec.style.aspectRatio > 0f) {
                            0f
                        } else {
                            rec.style.flexShrink
                        }
                        val scaled = canShrink * rec.baseMainSize.coerceAtLeast(1)
                        if (scaled > 0f && remainingScaled > 0f) {
                            val reduction = (remainingDeficit * (scaled / remainingScaled)).roundToInt()
                            val targetMain = clampDimension(
                                value = (rec.baseMainSize - reduction).coerceAtLeast(0),
                                minLen = if (isRow) rec.style.minWidth else rec.style.minHeight,
                                maxLen = if (isRow) rec.style.maxWidth else rec.style.maxHeight,
                                parentSizePx = innerMainSize,
                                viewportRect = viewportRect
                            )
                            val actualReduced = rec.baseMainSize - targetMain
                            rec.mainSize = targetMain
                            remainingDeficit = (remainingDeficit - actualReduced).coerceAtLeast(0)
                            remainingScaled = (remainingScaled - scaled).coerceAtLeast(0f)
                        }
                    }
                }
            }

            // Compute each item's cross size and the line's cross size
            var maxLineCross = 0
            for (rec in lineItems) {
                val child = rawChildren[rec.childIndex]
                val cs = rec.style
                val crossMargin = if (isRow) rec.margin.top + rec.margin.bottom else rec.margin.left + rec.margin.right
                val explicitCross = resolveLengthPx(
                    length = if (isRow) cs.height else cs.width,
                    parentSizePx = innerCrossSize,
                    viewportRect = viewportRect
                )
                val rawCross = when {
                    explicitCross != null -> explicitCross
                    isRow && cs.aspectRatio != null && cs.aspectRatio > 0f -> (rec.mainSize / cs.aspectRatio).roundToInt()
                    !isRow && cs.aspectRatio != null && cs.aspectRatio > 0f && cs.width == null -> (rec.mainSize * cs.aspectRatio).roundToInt()
                    else -> {
                        val effectiveAlign = resolveItemAlignment(parentStyle.alignItems, cs.alignSelf)
                        if (lines.size == 1 && effectiveAlign == AlignItems.STRETCH && innerCrossSize > 0) {
                            (innerCrossSize - crossMargin).coerceAtLeast(0)
                        } else {
                            val availW = if (isRow) rec.mainSize else (innerCrossSize - crossMargin).coerceAtLeast(0)
                            val availH = if (isRow) (innerCrossSize - crossMargin).coerceAtLeast(0) else rec.mainSize
                            val measured = measureAutoDimension(child, cs, availW, availH, viewportRect)
                            if (isRow) measured.height else measured.width
                        }
                    }
                }
                rec.crossSize = clampDimension(
                    value = rawCross,
                    minLen = if (isRow) cs.minHeight else cs.minWidth,
                    maxLen = if (isRow) cs.maxHeight else cs.maxWidth,
                    parentSizePx = innerCrossSize,
                    viewportRect = viewportRect
                )
                maxLineCross = max(maxLineCross, rec.crossSize + crossMargin)
            }
            line.crossSize = if (lines.size == 1 && innerCrossSize > 0) {
                innerCrossSize
            } else {
                maxLineCross
            }
        }

        // Position items along main & cross axes for each line
        var crossCursor = if (isRow) contentBox.y else contentBox.x
        for ((lineIdx, line) in lines.withIndex()) {
            val orderedItems = if (isReverse) line.items.asReversed() else line.items
            val lineGaps = if (orderedItems.size > 1) (orderedItems.size - 1) * mainGap else 0
            val usedMain = orderedItems.sumOf { rec ->
                rec.mainSize + if (isRow) (rec.margin.left + rec.margin.right) else (rec.margin.top + rec.margin.bottom)
            } + lineGaps
            val leftoverMain = (innerMainSize - usedMain).coerceAtLeast(0)

            // Check auto margins on main axis
            val autoMarginCount = orderedItems.sumOf { rec ->
                var c = 0
                if (isRow) {
                    if (rec.style.marginLeft?.isAuto == true) c++
                    if (rec.style.marginRight?.isAuto == true) c++
                } else {
                    if (rec.style.marginTop?.isAuto == true) c++
                    if (rec.style.marginBottom?.isAuto == true) c++
                }
                c
            }

            var mainCursor = (if (isRow) contentBox.x else contentBox.y).toFloat()
            var extraBetween = 0f
            if (autoMarginCount == 0 && leftoverMain > 0) {
                when (parentStyle.justifyContent) {
                    JustifyContent.START -> Unit
                    JustifyContent.END -> mainCursor += leftoverMain
                    JustifyContent.CENTER -> mainCursor += leftoverMain / 2f
                    JustifyContent.BETWEEN -> {
                        if (orderedItems.size > 1) {
                            extraBetween = leftoverMain.toFloat() / (orderedItems.size - 1)
                        }
                    }
                    JustifyContent.AROUND -> {
                        val step = leftoverMain.toFloat() / orderedItems.size
                        mainCursor += step / 2f
                        extraBetween = step
                    }
                    JustifyContent.EVENLY -> {
                        val step = leftoverMain.toFloat() / (orderedItems.size + 1)
                        mainCursor += step
                        extraBetween = step
                    }
                }
            }

            val autoMarginShare = if (autoMarginCount > 0) leftoverMain.toFloat() / autoMarginCount else 0f

            for ((itemPos, rec) in orderedItems.withIndex()) {
                val cs = rec.style
                val mar = rec.margin
                val crossMarginStart = if (isRow) mar.top else mar.left
                val crossMarginEnd = if (isRow) mar.bottom else mar.right
                val effectiveAlign = resolveItemAlignment(parentStyle.alignItems, cs.alignSelf)

                if (effectiveAlign == AlignItems.STRETCH) {
                    val hasExplicitCross = if (isRow) cs.height != null else cs.width != null
                    val hasAspectRatioCross = isRow && cs.aspectRatio != null && cs.aspectRatio > 0f
                    if (!hasExplicitCross && !hasAspectRatioCross && line.crossSize > 0) {
                        rec.crossSize = clampDimension(
                            value = (line.crossSize - crossMarginStart - crossMarginEnd).coerceAtLeast(0),
                            minLen = if (isRow) cs.minHeight else cs.minWidth,
                            maxLen = if (isRow) cs.maxHeight else cs.maxWidth,
                            parentSizePx = innerCrossSize,
                            viewportRect = viewportRect
                        )
                    }
                }

                val leadMainMargin = if (isRow) {
                    if (cs.marginLeft?.isAuto == true) autoMarginShare else mar.left.toFloat()
                } else {
                    if (cs.marginTop?.isAuto == true) autoMarginShare else mar.top.toFloat()
                }
                val trailMainMargin = if (isRow) {
                    if (cs.marginRight?.isAuto == true) autoMarginShare else mar.right.toFloat()
                } else {
                    if (cs.marginBottom?.isAuto == true) autoMarginShare else mar.bottom.toFloat()
                }

                mainCursor += leadMainMargin
                val itemMainPos = mainCursor.roundToInt()

                val freeCross = (line.crossSize - rec.crossSize - crossMarginStart - crossMarginEnd).coerceAtLeast(0)
                val itemCrossPos = when (effectiveAlign) {
                    AlignItems.START, AlignItems.STRETCH, AlignItems.BASELINE -> crossCursor + crossMarginStart
                    AlignItems.END -> crossCursor + crossMarginStart + freeCross
                    AlignItems.CENTER -> crossCursor + crossMarginStart + (freeCross / 2)
                }

                val childBorderBox = if (isRow) {
                    Rect(itemMainPos, itemCrossPos, rec.mainSize, rec.crossSize)
                } else {
                    Rect(itemCrossPos, itemMainPos, rec.crossSize, rec.mainSize)
                }

                outResults[rec.childIndex] = layoutNodeWithAllocatedBox(
                    node = rawChildren[rec.childIndex],
                    style = cs,
                    borderBox = childBorderBox,
                    margin = mar,
                    viewportRect = viewportRect,
                    inheritedZIndex = effectiveZIndex
                )

                mainCursor += rec.mainSize + trailMainMargin
                if (itemPos < orderedItems.lastIndex) {
                    mainCursor += mainGap + extraBetween
                }
            }

            crossCursor += line.crossSize + if (lineIdx < lines.lastIndex) crossGap else 0
        }
    }

    private fun layoutPositionedChild(
        child: N,
        childStyle: LayoutStyleSnapshot,
        containingBox: Rect,
        viewportRect: Rect,
        parentZIndex: Int
    ): ComputedLayoutBox {
        val mar = resolveMarginInsets(childStyle, containingBox.width, containingBox.height, viewportRect)
        val isFillLayer = childStyle.layerRole == "fill" ||
            (childStyle.width?.unit == CssUnit.PERCENT && childStyle.width.value == 100f &&
                childStyle.height?.unit == CssUnit.PERCENT && childStyle.height.value == 100f &&
                (childStyle.top == null || childStyle.top.value == 0f) &&
                (childStyle.left == null || childStyle.left.value == 0f) &&
                childStyle.right == null && childStyle.bottom == null)

        if (isFillLayer) {
            val offsetX = resolveLengthPx(childStyle.left, containingBox.width, viewportRect) ?: childStyle.layerOffsetXPx
            val offsetY = resolveLengthPx(childStyle.top, containingBox.height, viewportRect) ?: childStyle.layerOffsetYPx
            val fillRect = Rect(
                x = containingBox.x + offsetX,
                y = containingBox.y + offsetY,
                width = containingBox.width,
                height = containingBox.height
            )
            return layoutNodeWithAllocatedBox(
                node = child,
                style = childStyle,
                borderBox = fillRect,
                margin = mar,
                viewportRect = viewportRect,
                inheritedZIndex = parentZIndex
            )
        }

        val leftPx = resolveLengthFloatPx(childStyle.left, containingBox.width, viewportRect)
        val rightPx = resolveLengthFloatPx(childStyle.right, containingBox.width, viewportRect)
        val topPx = resolveLengthFloatPx(childStyle.top, containingBox.height, viewportRect)
        val bottomPx = resolveLengthFloatPx(childStyle.bottom, containingBox.height, viewportRect)

        val explicitW = resolveLengthPx(childStyle.width, containingBox.width, viewportRect)
        val explicitH = resolveLengthPx(childStyle.height, containingBox.height, viewportRect)

        val intrinsic by lazy {
            measureAutoDimension(child, childStyle, containingBox.width, containingBox.height, viewportRect)
        }

        val rawW = when {
            explicitW != null -> explicitW
            leftPx != null && rightPx != null -> (containingBox.width - leftPx - rightPx - mar.left - mar.right).roundToInt().coerceAtLeast(0)
            else -> intrinsic.width
        }
        val childW = clampDimension(rawW, childStyle.minWidth, childStyle.maxWidth, containingBox.width, viewportRect)

        val rawH = when {
            explicitH != null -> explicitH
            childStyle.aspectRatio != null && childStyle.aspectRatio > 0f -> (childW / childStyle.aspectRatio).roundToInt()
            topPx != null && bottomPx != null -> (containingBox.height - topPx - bottomPx - mar.top - mar.bottom).roundToInt().coerceAtLeast(0)
            else -> intrinsic.height
        }
        val childH = clampDimension(rawH, childStyle.minHeight, childStyle.maxHeight, containingBox.height, viewportRect)

        var extraTranslateXPercent = childStyle.translateXPercent
        var extraTranslateYPercent = childStyle.translateYPercent
        val extraTranslateXPx = childStyle.translateXPx
        val extraTranslateYPx = childStyle.translateYPx

        val baseX: Float = when {
            leftPx != null -> containingBox.x + leftPx + mar.left
            rightPx != null -> containingBox.x + containingBox.width - childW - rightPx - mar.right
            childStyle.layerLeftPercent != null -> {
                containingBox.x + (containingBox.width * (childStyle.layerLeftPercent / 100f)) + childStyle.layerOffsetXPx
            }
            childStyle.layerRightPx != null -> {
                containingBox.x + containingBox.width - childW - childStyle.layerRightPx + childStyle.layerOffsetXPx.toFloat()
            }
            childStyle.layerAlignment != null -> {
                when (childStyle.layerAlignment) {
                    LayerAlignment.TOP_CENTER,
                    LayerAlignment.CENTER,
                    LayerAlignment.BOTTOM_CENTER -> {
                        if (extraTranslateXPercent == 0f) extraTranslateXPercent = -50f
                        containingBox.x + (containingBox.width * 0.5f) + childStyle.layerOffsetXPx
                    }
                    LayerAlignment.TOP_END,
                    LayerAlignment.CENTER_END,
                    LayerAlignment.BOTTOM_END -> {
                        containingBox.x + containingBox.width - childW + childStyle.layerOffsetXPx.toFloat()
                    }
                    else -> containingBox.x + childStyle.layerOffsetXPx.toFloat()
                }
            }
            else -> (containingBox.x + mar.left).toFloat()
        }

        val baseY: Float = when {
            topPx != null -> containingBox.y + topPx + mar.top
            bottomPx != null -> containingBox.y + containingBox.height - childH - bottomPx - mar.bottom
            childStyle.layerTopPercent != null -> {
                containingBox.y + (containingBox.height * (childStyle.layerTopPercent / 100f)) + childStyle.layerOffsetYPx
            }
            childStyle.layerBottomPx != null -> {
                containingBox.y + containingBox.height - childH - childStyle.layerBottomPx + childStyle.layerOffsetYPx.toFloat()
            }
            childStyle.layerAlignment != null -> {
                when (childStyle.layerAlignment) {
                    LayerAlignment.CENTER_START,
                    LayerAlignment.CENTER,
                    LayerAlignment.CENTER_END -> {
                        if (extraTranslateYPercent == 0f) extraTranslateYPercent = -50f
                        containingBox.y + (containingBox.height * 0.5f) + childStyle.layerOffsetYPx
                    }
                    LayerAlignment.BOTTOM_START,
                    LayerAlignment.BOTTOM_CENTER,
                    LayerAlignment.BOTTOM_END -> {
                        containingBox.y + containingBox.height - childH + childStyle.layerOffsetYPx.toFloat()
                    }
                    else -> containingBox.y + childStyle.layerOffsetYPx.toFloat()
                }
            }
            else -> (containingBox.y + mar.top).toFloat()
        }

        val finalX = (baseX + (extraTranslateXPercent / 100f) * childW + extraTranslateXPx).roundToInt()
        val finalY = (baseY + (extraTranslateYPercent / 100f) * childH + extraTranslateYPx).roundToInt()

        val childRect = Rect(finalX, finalY, childW, childH)
        return layoutNodeWithAllocatedBox(
            node = child,
            style = childStyle,
            borderBox = childRect,
            margin = mar,
            viewportRect = viewportRect,
            inheritedZIndex = parentZIndex
        )
    }

    private fun measureAutoDimension(
        node: N,
        style: LayoutStyleSnapshot,
        availableWidthPx: Int,
        availableHeightPx: Int,
        viewportRect: Rect
    ): Size {
        val normalizedTag = adapter.tagNameOf(node).trim().lowercase().removePrefix("genericui:")
        if (normalizedTag == "tabcontainer" || normalizedTag == "tab-container") {
            return Size(availableWidthPx.coerceAtLeast(160), 44)
        }
        if (normalizedTag == "navbar" || normalizedTag == "bottomnavigationbar" || normalizedTag == "bottom-navigation-bar") {
            val intrinsic = measurer.measureIntrinsicSize(node, availableWidthPx, availableHeightPx)
            return Size(
                width = availableWidthPx.coerceAtLeast(intrinsic.width.coerceAtLeast(240)),
                height = intrinsic.height.takeIf { it > 0 } ?: 56
            )
        }

        val children = adapter.childrenOf(node)
        val horizChrome = style.padding.left + style.padding.right + style.border.left + style.border.right
        val vertChrome = style.padding.top + style.padding.bottom + style.border.top + style.border.bottom

        if (children.isEmpty()) {
            val measured = measurer.measureIntrinsicSize(
                node,
                (availableWidthPx - horizChrome).coerceAtLeast(0),
                (availableHeightPx - vertChrome).coerceAtLeast(0)
            )
            val w = resolveLengthPx(style.width, availableWidthPx, viewportRect)
                ?: (measured.width + horizChrome)
            val h = resolveLengthPx(style.height, availableHeightPx, viewportRect)
                ?: if (style.aspectRatio != null && style.aspectRatio > 0f && w > 0) {
                    (w / style.aspectRatio).roundToInt()
                } else {
                    measured.height + vertChrome
                }
            return Size(w, h)
        }

        // Container with children: estimate content size from normal-flow children
        val innerAvailW = (availableWidthPx - horizChrome).coerceAtLeast(0)
        val innerAvailH = (availableHeightPx - vertChrome).coerceAtLeast(0)
        val isFlex = style.display == CssDisplay.FLEX ||
            style.display == CssDisplay.INLINE_FLEX ||
            style.display == CssDisplay.GRID ||
            style.isGridRow
        val isRow = isFlex && (style.flexDirection == FlexDirection.ROW || style.flexDirection == FlexDirection.ROW_REVERSE)

        var sumW = 0
        var maxW = 0
        var sumH = 0
        var maxH = 0
        var visibleFlowCount = 0

        for (child in children) {
            val cs = adapter.layoutStyleOf(child, viewportRect.width, viewportRect.height)
            if (cs.display == CssDisplay.NONE) continue
            if (cs.position == CssPosition.ABSOLUTE || cs.position == CssPosition.FIXED || cs.layerRole != null) continue
            val mar = resolveMarginInsets(cs, innerAvailW, innerAvailH, viewportRect)
            val cw = resolveLengthPx(cs.width, innerAvailW, viewportRect)
            val ch = resolveLengthPx(cs.height, innerAvailH, viewportRect)
            val sub = if (cw != null && ch != null) {
                Size(cw, ch)
            } else {
                val m = measureAutoDimension(child, cs, cw ?: innerAvailW, ch ?: innerAvailH, viewportRect)
                val resolvedW = cw ?: m.width
                val resolvedH = ch ?: if (cs.aspectRatio != null && cs.aspectRatio > 0f && resolvedW > 0) {
                    (resolvedW / cs.aspectRatio).roundToInt()
                } else {
                    m.height
                }
                Size(resolvedW, resolvedH)
            }
            val outerW = sub.width + mar.left + mar.right
            val outerH = sub.height + mar.top + mar.bottom
            sumW += outerW
            maxW = max(maxW, outerW)
            sumH += outerH
            maxH = max(maxH, outerH)
            visibleFlowCount++
        }

        val intrinsicFallback = if (visibleFlowCount == 0) {
            measurer.measureIntrinsicSize(node, innerAvailW, innerAvailH)
        } else {
            Size.ZERO
        }

        val contentW = when {
            visibleFlowCount == 0 -> intrinsicFallback.width
            isRow -> sumW + (visibleFlowCount - 1) * style.columnGapPx
            else -> maxW
        }
        val contentH = when {
            visibleFlowCount == 0 -> intrinsicFallback.height
            isRow -> maxH
            else -> sumH + (visibleFlowCount - 1) * style.rowGapPx
        }

        val totalW = resolveLengthPx(style.width, availableWidthPx, viewportRect)
            ?: (contentW + horizChrome)
        val totalH = resolveLengthPx(style.height, availableHeightPx, viewportRect)
            ?: if (style.aspectRatio != null && style.aspectRatio > 0f && totalW > 0) {
                (totalW / style.aspectRatio).roundToInt()
            } else {
                contentH + vertChrome
            }
        return Size(totalW, totalH)
    }

    private fun resolveItemAlignment(parentAlign: AlignItems, selfAlign: AlignSelf): AlignItems = when (selfAlign) {
        AlignSelf.AUTO -> parentAlign
        AlignSelf.START -> AlignItems.START
        AlignSelf.END -> AlignItems.END
        AlignSelf.CENTER -> AlignItems.CENTER
        AlignSelf.BASELINE -> AlignItems.BASELINE
        AlignSelf.STRETCH -> AlignItems.STRETCH
    }

    private fun resolveMarginInsets(
        style: LayoutStyleSnapshot,
        parentWidthPx: Int,
        parentHeightPx: Int,
        viewportRect: Rect
    ): EdgeInsets = EdgeInsets(
        top = resolveLengthPx(style.marginTop, parentHeightPx, viewportRect) ?: 0,
        right = resolveLengthPx(style.marginRight, parentWidthPx, viewportRect) ?: 0,
        bottom = resolveLengthPx(style.marginBottom, parentHeightPx, viewportRect) ?: 0,
        left = resolveLengthPx(style.marginLeft, parentWidthPx, viewportRect) ?: 0
    )

    private fun resolveLengthPx(
        length: CssLength?,
        parentSizePx: Int,
        viewportRect: Rect
    ): Int? = resolveLengthFloatPx(length, parentSizePx, viewportRect)?.roundToInt()

    private fun resolveLengthFloatPx(
        length: CssLength?,
        parentSizePx: Int,
        viewportRect: Rect
    ): Float? {
        if (length == null || length.isAuto || length.isNone) return null
        return length.resolvePx(
            referencePx = parentSizePx.toFloat(),
            viewportWidthPx = viewportRect.width.toFloat(),
            viewportHeightPx = viewportRect.height.toFloat()
        )
    }

    private fun clampDimension(
        value: Int,
        minLen: CssLength?,
        maxLen: CssLength?,
        parentSizePx: Int,
        viewportRect: Rect
    ): Int {
        var result = value.coerceAtLeast(0)
        val maxPx = resolveLengthPx(maxLen, parentSizePx, viewportRect)
        if (maxPx != null) {
            result = min(result, maxPx.coerceAtLeast(0))
        }
        val minPx = resolveLengthPx(minLen, parentSizePx, viewportRect)
        if (minPx != null) {
            result = max(result, minPx.coerceAtLeast(0))
        }
        return result
    }

    companion object {
        /**
         * Creates a [BrowserLayoutEngine] configured for GenericUI [AbstractComponent] hierarchies.
         */
        fun forComponents(
            measurer: IntrinsicSizeMeasurer<AbstractComponent> = defaultComponentMeasurer()
        ): BrowserLayoutEngine<AbstractComponent> = BrowserLayoutEngine(
            adapter = ComponentLayoutNodeAdapter(),
            measurer = measurer
        )

        /**
         * Default [IntrinsicSizeMeasurer] for [AbstractComponent] instances when no platform-specific
         * font/widget measurer is supplied.
         */
        fun defaultComponentMeasurer(): IntrinsicSizeMeasurer<AbstractComponent> =
            IntrinsicSizeMeasurer { component, availableWidthPx, availableHeightPx ->
                val explicitW = component.widthPx
                val explicitH = component.heightPx
                val pref = component.getPreferredSize()
                val baseSize = when (component) {
                    is Navbar -> Size(availableWidthPx.coerceAtLeast(320), 56)
                    is BottomNavigationBar -> Size(availableWidthPx.coerceAtLeast(320), 56)
                    is TabContainer -> Size(availableWidthPx.coerceAtLeast(180), 44)
                    is Tab -> Size((component.title.length * 8 + 32).coerceAtLeast(80), 38)
                    is StyledButton -> Size((component.text.length * 8 + 28).coerceAtLeast(44), 38)
                    is Button -> Size((component.text.length * 8 + 28).coerceAtLeast(44), 38)
                    is TextBubble -> Size((component.text.length * 8 + 24).coerceAtLeast(64), 34)
                    is Badge -> Size((component.text.length * 7 + 16).coerceAtLeast(32), 24)
                    is Heading -> Size((component.text.length * 10 + 8).coerceAtLeast(48), 32)
                    is Text -> Size((component.text.length * 8 + 8).coerceAtLeast(32), 24)
                    is LazyList -> Size(
                        availableWidthPx.coerceAtLeast(240),
                        (component.totalItemCount * component.estimatedItemExtentPx.roundToInt()).coerceAtLeast(240)
                    )
                    else -> Size(pref?.width ?: 0, pref?.height ?: 0)
                }
                Size(
                    width = explicitW ?: pref?.width?.takeIf { it > 0 } ?: baseSize.width,
                    height = explicitH ?: pref?.height?.takeIf { it > 0 } ?: baseSize.height
                )
            }
    }
}
