package com.nexustools.genericui.component.layout

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.enums.bootstrap.DisplayMode
import com.nexustools.genericui.enums.bootstrap.FlexDirection
import com.nexustools.genericui.enums.bootstrap.FlexWrap
import com.nexustools.genericui.window.WindowConfiguration
import kotlin.math.min

/**
 * Bootstrap 12-column responsive bento/card grid (`ResponsiveBentoGridLayout`) that renders multi-column
 * dashboard cards (`colMd`/`colLg`/`colXl`) on tablets, wide web, and desktop, and automatically reflows
 * into a single-column (`colXs = 12`) vertical feed on small-screen mobile (`< 600dp`) and narrow web viewports.
 */
open class ResponsiveBentoGridLayout(
    var breakpointSpec: HybridBreakpointSpec = HybridBreakpointSpec.DEFAULT,
    var gapPx: Int = 16
) : Container("genericui:responsivebentogridlayout", "gui-responsive-bento-grid") {

    private class BentoCardEntry(
        var spec: BentoGridItemSpec,
        val component: AbstractComponent,
        val cellCol: Col,
        var activeColSpan: Int
    )

    private val cards = linkedMapOf<String, BentoCardEntry>()
    private var lastViewportWidthDp: Int = 1280
    private var lastViewportHeightDp: Int = 800
    private var lastSmallestScreenWidthDp: Int = 800

    val gridRow: Row = Row()

    var isSingleColumnFeed: Boolean = false
        private set

    val cardIds: List<String>
        get() = cards.keys.toList()

    val cardCount: Int
        get() = cards.size

    init {
        htmlTag = "genericui:responsivebentogridlayout"
        setDisplay(DisplayMode.FLEX)
        setFlexDirection(FlexDirection.COLUMN)

        gridRow.setFlexWrap(FlexWrap.WRAP)
        gridRow.addClass("gui-bento-grid-row")
        addChild(gridRow)
    }

    /**
     * Adds or replaces a card in the bento grid using [spec] and [component].
     */
    fun addCard(spec: BentoGridItemSpec, component: AbstractComponent): ResponsiveBentoGridLayout = apply {
        if (cards.containsKey(spec.id)) {
            removeCard(spec.id)
        }
        val span = spec.resolveColSpan(lastViewportWidthDp, isSingleColumnFeed)
        val cell = Col(span = span, weight = span / 12f).apply {
            addClass("gui-bento-grid-item")
            spec.minHeightPx?.let { minHeightPx = it }
            addChild(component)
        }
        val entry = BentoCardEntry(
            spec = spec,
            component = component,
            cellCol = cell,
            activeColSpan = span
        )
        cards[spec.id] = entry
        gridRow.addChild(cell)
        syncCardSpans()
    }

    /**
     * Convenience overload to add a card with [id], optional [title], responsive column spans, and [component].
     */
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
    ): ResponsiveBentoGridLayout = addCard(
        spec = BentoGridItemSpec(
            id = id,
            title = title,
            colXs = colXs,
            colSm = colSm,
            colMd = colMd,
            colLg = colLg,
            colXl = colXl,
            rowSpan = rowSpan,
            minHeightPx = minHeightPx
        ),
        component = component
    )

    /**
     * Removes the card with [id] from the bento grid.
     */
    fun removeCard(id: String): Boolean {
        val removed = cards.remove(id) ?: return false
        removed.cellCol.clearChildren()
        gridRow.removeChild(removed.cellCol)
        return true
    }

    /**
     * Returns the [BentoGridItemSpec] for card [id], or `null` if not found.
     */
    fun getCardSpec(id: String): BentoGridItemSpec? = cards[id]?.spec

    /**
     * Returns the [AbstractComponent] mounted for card [id], or `null` if not found.
     */
    fun getCardComponent(id: String): AbstractComponent? = cards[id]?.component

    /**
     * Returns the active Bootstrap 12-grid column span (`1..12`) currently applied to card [id],
     * or `0` if [id] is not present.
     */
    fun activeColumnsForItem(id: String): Int = cards[id]?.activeColSpan ?: 0

    /**
     * Updates the responsive grid spans for the given viewport dimensions in dp.
     */
    fun updateViewport(
        viewportWidthDp: Int,
        viewportHeightDp: Int,
        smallestScreenWidthDp: Int = min(viewportWidthDp, viewportHeightDp)
    ) {
        lastViewportWidthDp = viewportWidthDp
        lastViewportHeightDp = viewportHeightDp
        lastSmallestScreenWidthDp = smallestScreenWidthDp

        val backend = resolveActiveFactory()?.backendTarget
        val smallMobilePhone = smallestScreenWidthDp < breakpointSpec.tabletSmallestWidthDp &&
            viewportHeightDp < breakpointSpec.tabletSmallestWidthDp
        isSingleColumnFeed = if (backend != null && backend.isMobile) {
            breakpointSpec.isSmallScreenMobile(smallestScreenWidthDp)
        } else {
            viewportWidthDp < breakpointSpec.mediumBreakpointDp || smallMobilePhone
        }

        syncCardSpans()
    }

    /**
     * Applies a [WindowConfiguration] snapshot to reflow the bento grid.
     */
    fun applyWindowConfiguration(config: WindowConfiguration) {
        val widthDp = breakpointSpec.toDp(config.widthPx, config.densityScale)
        val heightDp = breakpointSpec.toDp(config.heightPx, config.densityScale)
        updateViewport(
            viewportWidthDp = widthDp,
            viewportHeightDp = heightDp,
            smallestScreenWidthDp = config.smallestScreenWidthDp
        )
    }

    private fun syncCardSpans() {
        for (entry in cards.values) {
            val span = entry.spec.resolveColSpan(
                viewportWidthDp = lastViewportWidthDp,
                isSmallScreenMobile = isSingleColumnFeed
            )
            entry.activeColSpan = span
            entry.cellCol.setClass("col-$span gui-bento-grid-item")
            entry.spec.minHeightPx?.let { entry.cellCol.minHeightPx = it }
        }
    }
}
