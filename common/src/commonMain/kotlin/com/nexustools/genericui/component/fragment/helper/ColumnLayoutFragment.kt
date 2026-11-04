package com.nexustools.genericui.component.fragment.helper

import com.nexustools.genericui.component.layout.Column
import com.nexustools.genericui.component.layout.ColumnContainer
import com.nexustools.genericui.component.layout.ColumnLayout
import com.nexustools.genericui.component.layout.ColumnLayoutImpl
import com.nexustools.genericui.component.layout.ColumnSpec
import com.nexustools.genericui.component.layout.HybridBreakpointSpec
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.enums.bootstrap.SpacingScale

/**
 * Helper [LayoutFragment] hosting a Bootstrap 5 12-column [ColumnLayout].
 */
open class ColumnLayoutFragment(
    gutter: SpacingScale? = null,
    gutterX: SpacingScale? = null,
    gutterY: SpacingScale? = null,
    rowCols: Int? = null,
    rowColsSm: Int? = null,
    rowColsMd: Int? = null,
    rowColsLg: Int? = null,
    rowColsXl: Int? = null,
    rowColsXxl: Int? = null,
    breakpointSpec: HybridBreakpointSpec = HybridBreakpointSpec.DEFAULT,
    id: String? = null
) : LayoutFragment<ColumnLayoutImpl, ColumnLayout>(id = id) {

    val columnContainer: ColumnContainer = ColumnContainer(
        gutter = gutter,
        gutterX = gutterX,
        gutterY = gutterY,
        rowCols = rowCols,
        rowColsSm = rowColsSm,
        rowColsMd = rowColsMd,
        rowColsLg = rowColsLg,
        rowColsXl = rowColsXl,
        rowColsXxl = rowColsXxl,
        breakpointSpec = breakpointSpec
    )

    override val layout: ColumnLayout = columnContainer.layout

    val columnLayout: ColumnLayout
        get() = layout

    val columns: List<Column>
        get() = layout.columns

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

    init {
        addChild(columnContainer)
        initializeLayout()
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

    fun clearColumns() {
        layout.clearColumns()
    }
}
