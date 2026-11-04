package com.nexustools.genericui.component.layout

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.AbstractContainer
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
    host: AbstractContainer = Container("genericui:responsivebentogridlayout", "gui-responsive-bento-grid"),
    breakpointSpec: HybridBreakpointSpec = HybridBreakpointSpec.DEFAULT,
    var gapPx: Int = 16
) : Layout<ResponsiveBentoGridLayoutImpl>(host, breakpointSpec) {

    constructor(
        breakpointSpec: HybridBreakpointSpec,
        gapPx: Int = 16
    ) : this(
        host = Container("genericui:responsivebentogridlayout", "gui-responsive-bento-grid"),
        breakpointSpec = breakpointSpec,
        gapPx = gapPx
    )

    constructor(
        gapPx: Int,
        breakpointSpec: HybridBreakpointSpec = HybridBreakpointSpec.DEFAULT
    ) : this(
        host = Container("genericui:responsivebentogridlayout", "gui-responsive-bento-grid"),
        breakpointSpec = breakpointSpec,
        gapPx = gapPx
    )

    private class BentoCardEntry(
        var spec: BentoGridItemSpec,
        val component: AbstractComponent,
        val cellCol: Column,
        var activeColSpan: Int
    )

    private val cards = linkedMapOf<String, BentoCardEntry>()
    private var lastViewportWidthDp: Int = 1280
    private var lastViewportHeightDp: Int = 800
    private var lastSmallestScreenWidthDp: Int = 800

    val gridRow: ColumnContainer = ColumnContainer()

    var isSingleColumnFeed: Boolean = false
        private set

    val cardIds: List<String>
        get() = cards.keys.toList()

    val cardCount: Int
        get() = cards.size

    init {
        ensureMounted()
    }

    override fun createImpl(factory: LayoutFactory): ResponsiveBentoGridLayoutImpl =
        factory.createResponsiveBentoGridLayout(this)

    override fun onMount() {
        host.addClass("gui-responsive-bento-grid")
        host.setDisplay(DisplayMode.FLEX)
        host.setFlexDirection(FlexDirection.COLUMN)

        gridRow.setFlexWrap(FlexWrap.WRAP)
        gridRow.addClass("gui-bento-grid-row")
        if (!host.children.contains(gridRow)) {
            host.addChild(gridRow)
        }
        impl.syncGrid(lastViewportWidthDp.toFloat(), lastViewportHeightDp.toFloat())
    }

    /**
     * Adds or replaces a card in the bento grid using [spec] and [component].
     */
    fun addCard(spec: BentoGridItemSpec, component: AbstractComponent): ResponsiveBentoGridLayout = apply {
        if (cards.containsKey(spec.id)) {
            removeCard(spec.id)
        }
        val span = spec.resolveColSpan(lastViewportWidthDp, isSingleColumnFeed)
        val cell = Column(span = span).apply {
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
        impl.syncGrid(lastViewportWidthDp.toFloat(), lastViewportHeightDp.toFloat())
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

        impl.syncGrid(viewportWidthDp.toFloat(), viewportHeightDp.toFloat())
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

    override fun onConfigurationChanged(config: WindowConfiguration) {
        super.onConfigurationChanged(config)
        applyWindowConfiguration(config)
    }

    override fun onResize(viewportWidth: Int, viewportHeight: Int) {
        super.onResize(viewportWidth, viewportHeight)
        updateViewport(viewportWidth, viewportHeight)
    }

    /**
     * Applies the evaluated card spans to all items in [gridRow].
     */
    fun applyGridLayout(
        viewportWidthDp: Int = lastViewportWidthDp,
        viewportHeightDp: Int = lastViewportHeightDp
    ) {
        lastViewportWidthDp = viewportWidthDp
        lastViewportHeightDp = viewportHeightDp

        val backend = host.resolveActiveFactory()?.backendTarget
        val smallMobilePhone = impl.supportsDynamicViewportSwitching &&
            lastSmallestScreenWidthDp < breakpointSpec.tabletSmallestWidthDp &&
            viewportHeightDp < breakpointSpec.tabletSmallestWidthDp
        isSingleColumnFeed = if (backend != null && backend.isMobile) {
            breakpointSpec.isSmallScreenMobile(lastSmallestScreenWidthDp)
        } else {
            impl.supportsDynamicViewportSwitching &&
                (viewportWidthDp < breakpointSpec.mediumBreakpointDp || smallMobilePhone)
        }

        for (entry in cards.values) {
            val span = entry.spec.resolveColSpan(
                viewportWidthDp = lastViewportWidthDp,
                isSmallScreenMobile = isSingleColumnFeed
            )
            entry.activeColSpan = span
            entry.cellCol.span = span
            entry.cellCol.setClass("col-$span gui-bento-grid-item")
            entry.spec.minHeightPx?.let { entry.cellCol.minHeightPx = it }
        }
    }
}
