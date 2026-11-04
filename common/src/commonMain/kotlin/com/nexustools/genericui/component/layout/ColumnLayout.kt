package com.nexustools.genericui.component.layout

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.enums.bootstrap.DisplayMode
import com.nexustools.genericui.enums.bootstrap.FlexDirection
import com.nexustools.genericui.enums.bootstrap.FlexWrap
import com.nexustools.genericui.enums.bootstrap.ResponsiveBreakpoint
import com.nexustools.genericui.enums.bootstrap.SpacingScale
import com.nexustools.genericui.window.WindowConfiguration

/**
 * Bootstrap 5 12-column responsive grid layout helper (`ColumnLayout`).
 *
 * Coordinates `.row` (`gutter`, `gutterX`, `gutterY`, `rowCols*`) on [host] and manages child [Column]s.
 */
open class ColumnLayout(
    host: AbstractContainer = Container("genericui:row", "row"),
    breakpointSpec: HybridBreakpointSpec = HybridBreakpointSpec.DEFAULT,
    gutter: SpacingScale? = null,
    gutterX: SpacingScale? = null,
    gutterY: SpacingScale? = null,
    rowCols: Int? = null,
    rowColsSm: Int? = null,
    rowColsMd: Int? = null,
    rowColsLg: Int? = null,
    rowColsXl: Int? = null,
    rowColsXxl: Int? = null
) : Layout<ColumnLayoutImpl>(host, breakpointSpec) {

    private val _columns = mutableListOf<Column>()
    private val managedHostClasses = mutableSetOf<String>()

    private var lastViewportWidthDp: Float = 1280f
    private var lastViewportHeightDp: Float = 800f

    val columns: List<Column>
        get() = _columns.toList()

    var activeBreakpoint: ResponsiveBreakpoint = ResponsiveBreakpoint.XL
        internal set

    var gutter: SpacingScale? = gutter
        set(value) {
            field = value
            if (isMounted) impl.syncLayout(lastViewportWidthDp, lastViewportHeightDp)
        }

    var gutterX: SpacingScale? = gutterX
        set(value) {
            field = value
            if (isMounted) impl.syncLayout(lastViewportWidthDp, lastViewportHeightDp)
        }

    var gutterY: SpacingScale? = gutterY
        set(value) {
            field = value
            if (isMounted) impl.syncLayout(lastViewportWidthDp, lastViewportHeightDp)
        }

    var rowCols: Int? = rowCols
        set(value) {
            field = value
            if (isMounted) impl.syncLayout(lastViewportWidthDp, lastViewportHeightDp)
        }

    var rowColsSm: Int? = rowColsSm
        set(value) {
            field = value
            if (isMounted) impl.syncLayout(lastViewportWidthDp, lastViewportHeightDp)
        }

    var rowColsMd: Int? = rowColsMd
        set(value) {
            field = value
            if (isMounted) impl.syncLayout(lastViewportWidthDp, lastViewportHeightDp)
        }

    var rowColsLg: Int? = rowColsLg
        set(value) {
            field = value
            if (isMounted) impl.syncLayout(lastViewportWidthDp, lastViewportHeightDp)
        }

    var rowColsXl: Int? = rowColsXl
        set(value) {
            field = value
            if (isMounted) impl.syncLayout(lastViewportWidthDp, lastViewportHeightDp)
        }

    var rowColsXxl: Int? = rowColsXxl
        set(value) {
            field = value
            if (isMounted) impl.syncLayout(lastViewportWidthDp, lastViewportHeightDp)
        }

    init {
        ensureMounted()
    }

    override fun createImpl(factory: LayoutFactory): ColumnLayoutImpl =
        factory.createColumnLayout(this)

    override fun onMount() {
        host.addClass("row")
        host.setDisplay(DisplayMode.FLEX)
        host.setFlexDirection(FlexDirection.ROW)
        host.setFlexWrap(FlexWrap.WRAP)
        impl.syncLayout(lastViewportWidthDp, lastViewportHeightDp)
    }

    /**
     * Adds an existing [column] to this layout and mounts it in [host].
     */
    fun addColumn(column: Column): Column {
        ensureMounted()
        if (!_columns.contains(column)) {
            _columns.add(column)
        }
        if (!host.children.contains(column)) {
            host.addChild(column)
        }
        impl.syncLayout(lastViewportWidthDp, lastViewportHeightDp)
        return column
    }

    /**
     * Creates and adds a [Column] configured with [spec], optional [content], and optional builder [block].
     */
    fun addColumn(
        spec: ColumnSpec = ColumnSpec(),
        content: AbstractComponent? = null,
        block: (Column.() -> Unit)? = null
    ): Column {
        val col = Column(spec)
        if (content != null) {
            col.addChild(content)
        }
        if (block != null) {
            col.block()
        }
        return addColumn(col)
    }

    /**
     * Creates and adds a [Column] with responsive Bootstrap 5 12-grid spans.
     */
    fun addColumn(
        span: Int?,
        sm: Int? = null,
        md: Int? = null,
        lg: Int? = null,
        xl: Int? = null,
        xxl: Int? = null,
        content: AbstractComponent? = null,
        block: (Column.() -> Unit)? = null
    ): Column = addColumn(
        spec = ColumnSpec(span = span, sm = sm, md = md, lg = lg, xl = xl, xxl = xxl),
        content = content,
        block = block
    )

    /**
     * Removes [column] from this layout and [host].
     */
    fun removeColumn(column: Column): Boolean {
        val removed = _columns.remove(column)
        host.removeChild(column)
        if (removed && isMounted) {
            impl.syncLayout(lastViewportWidthDp, lastViewportHeightDp)
        }
        return removed
    }

    /**
     * Removes all columns from this layout and [host].
     */
    fun clearColumns() {
        for (col in _columns.toList()) {
            host.removeChild(col)
        }
        _columns.clear()
        if (isMounted) {
            impl.syncLayout(lastViewportWidthDp, lastViewportHeightDp)
        }
    }

    internal fun registerDirectColumnChild(column: Column) {
        if (!_columns.contains(column)) {
            _columns.add(column)
            if (isMounted) {
                impl.syncLayout(lastViewportWidthDp, lastViewportHeightDp)
            }
        }
    }

    internal fun unregisterDirectColumnChild(column: Column) {
        if (_columns.remove(column) && isMounted) {
            impl.syncLayout(lastViewportWidthDp, lastViewportHeightDp)
        }
    }

    internal fun clearTrackedColumnsOnly() {
        _columns.clear()
    }

    /**
     * Synchronizes `.row`, `.g-*`, `.gx-*`, `.gy-*`, and `.row-cols-*` classes on [host].
     */
    fun syncHostGridClasses() {
        for (cls in managedHostClasses) {
            host.removeClass(cls)
        }
        managedHostClasses.clear()

        host.addClass("row")
        gutter?.let {
            val c = "g-${it.token}"
            host.addClass(c)
            managedHostClasses.add(c)
            host.getBootstrap().setGap(it)
        }
        gutterX?.let {
            val c = "gx-${it.token}"
            host.addClass(c)
            managedHostClasses.add(c)
        }
        gutterY?.let {
            val c = "gy-${it.token}"
            host.addClass(c)
            managedHostClasses.add(c)
        }
        rowCols?.let {
            val c = "row-cols-${it.coerceIn(1, 12)}"
            host.addClass(c)
            managedHostClasses.add(c)
        }
        rowColsSm?.let {
            val c = "row-cols-sm-${it.coerceIn(1, 12)}"
            host.addClass(c)
            managedHostClasses.add(c)
        }
        rowColsMd?.let {
            val c = "row-cols-md-${it.coerceIn(1, 12)}"
            host.addClass(c)
            managedHostClasses.add(c)
        }
        rowColsLg?.let {
            val c = "row-cols-lg-${it.coerceIn(1, 12)}"
            host.addClass(c)
            managedHostClasses.add(c)
        }
        rowColsXl?.let {
            val c = "row-cols-xl-${it.coerceIn(1, 12)}"
            host.addClass(c)
            managedHostClasses.add(c)
        }
        rowColsXxl?.let {
            val c = "row-cols-xxl-${it.coerceIn(1, 12)}"
            host.addClass(c)
            managedHostClasses.add(c)
        }
    }

    /**
     * Resolves the effective `row-cols-*` count for [tier] using Bootstrap 5 cascading rules.
     */
    fun resolveRowColsForBreakpoint(tier: ResponsiveBreakpoint): Int? {
        var resolved: Int? = rowCols?.coerceIn(1, 12)
        if (tier.ordinal >= ResponsiveBreakpoint.SM.ordinal && rowColsSm != null) {
            resolved = rowColsSm!!.coerceIn(1, 12)
        }
        if (tier.ordinal >= ResponsiveBreakpoint.MD.ordinal && rowColsMd != null) {
            resolved = rowColsMd!!.coerceIn(1, 12)
        }
        if (tier.ordinal >= ResponsiveBreakpoint.LG.ordinal && rowColsLg != null) {
            resolved = rowColsLg!!.coerceIn(1, 12)
        }
        if (tier.ordinal >= ResponsiveBreakpoint.XL.ordinal && rowColsXl != null) {
            resolved = rowColsXl!!.coerceIn(1, 12)
        }
        if (tier.ordinal >= ResponsiveBreakpoint.XXL.ordinal && rowColsXxl != null) {
            resolved = rowColsXxl!!.coerceIn(1, 12)
        }
        return resolved
    }

    fun updateViewport(viewportWidthDp: Float, viewportHeightDp: Float) {
        ensureMounted()
        lastViewportWidthDp = viewportWidthDp.coerceAtLeast(1f)
        lastViewportHeightDp = viewportHeightDp.coerceAtLeast(1f)
        impl.syncLayout(lastViewportWidthDp, lastViewportHeightDp)
    }

    fun updateViewport(viewportWidthDp: Int, viewportHeightDp: Int) {
        updateViewport(viewportWidthDp.toFloat(), viewportHeightDp.toFloat())
    }

    fun applyWindowConfiguration(config: WindowConfiguration) {
        onConfigurationChanged(config)
    }

    override fun onConfigurationChanged(config: WindowConfiguration) {
        super.onConfigurationChanged(config)
        val widthDp = breakpointSpec.toDp(config.widthPx, config.densityScale).toFloat()
        val heightDp = breakpointSpec.toDp(config.heightPx, config.densityScale).toFloat()
        updateViewport(widthDp, heightDp)
    }

    override fun onResize(viewportWidth: Int, viewportHeight: Int) {
        super.onResize(viewportWidth, viewportHeight)
        updateViewport(viewportWidth.toFloat(), viewportHeight.toFloat())
    }
}
