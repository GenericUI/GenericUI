package com.nexustools.genericui.component.layout

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.enums.bootstrap.ResponsiveBreakpoint
import com.nexustools.genericui.enums.bootstrap.SpacingScale
import com.nexustools.genericui.window.WindowConfiguration

/**
 * Bootstrap 5 12-column `.row` container (`<genericui:row>`) backed by [ColumnLayout].
 */
open class ColumnContainer(
    gutter: SpacingScale? = null,
    gutterX: SpacingScale? = null,
    gutterY: SpacingScale? = null,
    rowCols: Int? = null,
    rowColsSm: Int? = null,
    rowColsMd: Int? = null,
    rowColsLg: Int? = null,
    rowColsXl: Int? = null,
    rowColsXxl: Int? = null,
    breakpointSpec: HybridBreakpointSpec = HybridBreakpointSpec.DEFAULT
) : LayoutContainer<ColumnLayoutImpl, ColumnLayout>("genericui:row", "row") {

    override val layout: ColumnLayout = ColumnLayout(
        host = this,
        breakpointSpec = breakpointSpec,
        gutter = gutter,
        gutterX = gutterX,
        gutterY = gutterY,
        rowCols = rowCols,
        rowColsSm = rowColsSm,
        rowColsMd = rowColsMd,
        rowColsLg = rowColsLg,
        rowColsXl = rowColsXl,
        rowColsXxl = rowColsXxl
    )

    val columns: List<Column>
        get() = layout.columns

    val activeBreakpoint: ResponsiveBreakpoint
        get() = layout.activeBreakpoint

    var gutter: SpacingScale?
        get() = layout.gutter
        set(value) {
            layout.gutter = value
        }

    var gutterX: SpacingScale?
        get() = layout.gutterX
        set(value) {
            layout.gutterX = value
        }

    var gutterY: SpacingScale?
        get() = layout.gutterY
        set(value) {
            layout.gutterY = value
        }

    var rowCols: Int?
        get() = layout.rowCols
        set(value) {
            layout.rowCols = value
        }

    var rowColsSm: Int?
        get() = layout.rowColsSm
        set(value) {
            layout.rowColsSm = value
        }

    var rowColsMd: Int?
        get() = layout.rowColsMd
        set(value) {
            layout.rowColsMd = value
        }

    var rowColsLg: Int?
        get() = layout.rowColsLg
        set(value) {
            layout.rowColsLg = value
        }

    var rowColsXl: Int?
        get() = layout.rowColsXl
        set(value) {
            layout.rowColsXl = value
        }

    var rowColsXxl: Int?
        get() = layout.rowColsXxl
        set(value) {
            layout.rowColsXxl = value
        }

    init {
        initializeLayout()
    }

    override fun addChild(child: AbstractComponent, index: Int) {
        super.addChild(child, index)
        if (child is Column) {
            layout.registerDirectColumnChild(child)
        }
    }

    override fun removeChild(child: AbstractComponent): Boolean {
        val removed = super.removeChild(child)
        if (child is Column) {
            layout.unregisterDirectColumnChild(child)
        }
        return removed
    }

    override fun clearChildren() {
        layout.clearTrackedColumnsOnly()
        super.clearChildren()
    }

    fun addColumn(column: Column): Column = layout.addColumn(column)

    fun addColumn(
        spec: ColumnSpec = ColumnSpec(),
        content: AbstractComponent? = null,
        block: (Column.() -> Unit)? = null
    ): Column = layout.addColumn(spec = spec, content = content, block = block)

    fun addColumn(
        span: Int?,
        sm: Int? = null,
        md: Int? = null,
        lg: Int? = null,
        xl: Int? = null,
        xxl: Int? = null,
        content: AbstractComponent? = null,
        block: (Column.() -> Unit)? = null
    ): Column = layout.addColumn(
        span = span,
        sm = sm,
        md = md,
        lg = lg,
        xl = xl,
        xxl = xxl,
        content = content,
        block = block
    )

    fun removeColumn(column: Column): Boolean = layout.removeColumn(column)

    fun clearColumns() {
        layout.clearColumns()
    }

    fun updateViewport(viewportWidthDp: Float, viewportHeightDp: Float) {
        layout.updateViewport(viewportWidthDp, viewportHeightDp)
    }

    fun updateViewport(viewportWidthDp: Int, viewportHeightDp: Int) {
        layout.updateViewport(viewportWidthDp, viewportHeightDp)
    }

    fun applyWindowConfiguration(config: WindowConfiguration) {
        layout.applyWindowConfiguration(config)
    }
}
