package com.nexustools.genericui.component.layout

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.window.WindowConfiguration
import kotlin.math.min

/**
 * [LayoutContainer] hosting a [ResponsiveBentoGridLayout].
 */
open class ResponsiveBentoGridContainer(
    breakpointSpec: HybridBreakpointSpec = HybridBreakpointSpec.DEFAULT,
    gapPx: Int = 16
) : LayoutContainer<ResponsiveBentoGridLayoutImpl, ResponsiveBentoGridLayout>(
    "genericui:responsivebentogridlayout",
    "gui-responsive-bento-grid"
) {

    override val layout: ResponsiveBentoGridLayout = ResponsiveBentoGridLayout(
        host = this,
        breakpointSpec = breakpointSpec,
        gapPx = gapPx
    )

    var breakpointSpec: HybridBreakpointSpec
        get() = layout.breakpointSpec
        set(value) {
            layout.breakpointSpec = value
        }

    var gapPx: Int
        get() = layout.gapPx
        set(value) {
            layout.gapPx = value
        }

    val gridRow: ColumnContainer
        get() = layout.gridRow

    val isSingleColumnFeed: Boolean
        get() = layout.isSingleColumnFeed

    val cardIds: List<String>
        get() = layout.cardIds

    val cardCount: Int
        get() = layout.cardCount

    init {
        htmlTag = "genericui:responsivebentogridlayout"
        initializeLayout()
    }

    fun addCard(spec: BentoGridItemSpec, component: AbstractComponent): ResponsiveBentoGridContainer = apply {
        layout.addCard(spec, component)
    }

    fun addCard(
        id: String,
        title: String = "",
        colXs: Int = 12,
        colSm: Int = 12,
        colMd: Int = 6,
        colLg: Int = 4,
        colXl: Int = 3,
        rowSpan: Int = 1,
        minHeightPx: Int? = null,
        component: AbstractComponent
    ): ResponsiveBentoGridContainer = apply {
        layout.addCard(
            id = id,
            title = title,
            colXs = colXs,
            colSm = colSm,
            colMd = colMd,
            colLg = colLg,
            colXl = colXl,
            rowSpan = rowSpan,
            minHeightPx = minHeightPx,
            component = component
        )
    }

    fun removeCard(id: String): Boolean = layout.removeCard(id)

    fun getCardSpec(id: String): BentoGridItemSpec? = layout.getCardSpec(id)

    fun getCardComponent(id: String): AbstractComponent? = layout.getCardComponent(id)

    fun activeColumnsForItem(id: String): Int = layout.activeColumnsForItem(id)

    fun updateViewport(
        viewportWidthDp: Int,
        viewportHeightDp: Int,
        smallestScreenWidthDp: Int = min(viewportWidthDp, viewportHeightDp)
    ) = layout.updateViewport(viewportWidthDp, viewportHeightDp, smallestScreenWidthDp)

    fun applyWindowConfiguration(config: WindowConfiguration) = layout.applyWindowConfiguration(config)
}
