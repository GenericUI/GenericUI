package com.nexustools.genericui.nativeimpl.content

import com.nexustools.genericui.core.AbstractContainerNativeImpl

interface TableNativeImpl<out W> : AbstractContainerNativeImpl<W> {
    fun setHeaders(headers: List<String>)
    fun setRows(rows: List<List<String>>)
    fun setStriped(striped: Boolean)
    fun setHoverable(hoverable: Boolean)
}
