package com.nexustools.genericui.component.content

import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.nativeimpl.content.TableNativeImpl

/**
 * Structured data table component (`<table class="table">`).
 */
class Table : AbstractContainer() {
    init {
        htmlTag = "table"
    }

    private val _columns = mutableListOf<TableColumn>()
    val columns: List<TableColumn> get() = _columns

    private val _rows = mutableListOf<TableRow>()
    val rows: List<TableRow> get() = _rows

    var isStriped: Boolean = true
        set(value) {
            field = value
            typedNative<TableNativeImpl<*>>()?.setStriped(value)
        }

    var isHoverable: Boolean = true
        set(value) {
            field = value
            typedNative<TableNativeImpl<*>>()?.setHoverable(value)
        }

    fun setColumns(cols: List<TableColumn>): Table = apply {
        _columns.clear()
        _columns.addAll(cols)
        typedNative<TableNativeImpl<*>>()?.setHeaders(_columns.map { it.headerText })
    }

    fun setRows(tableRows: List<TableRow>): Table = apply {
        _rows.clear()
        _rows.addAll(tableRows)
        typedNative<TableNativeImpl<*>>()?.setRows(_rows.map { r -> r.cells.map { it.text } })
    }

    override fun createNativeImpl(factory: NativeFactory): TableNativeImpl<*> =
        factory.createTable()

    override fun syncStateToNative() {
        super.syncStateToNative()
        val impl = typedNative<TableNativeImpl<*>>() ?: return
        impl.setHeaders(_columns.map { it.headerText })
        impl.setRows(_rows.map { r -> r.cells.map { it.text } })
        impl.setStriped(isStriped)
        impl.setHoverable(isHoverable)
    }
}
