package com.nexustools.genericui.nativeimpl.lazy

import com.nexustools.genericui.core.AbstractContainerNativeImpl
import com.nexustools.genericui.core.NativeImpl

interface LazyGridNativeImpl<out W> : AbstractContainerNativeImpl<W> {
    fun setColumns(columns: Int)
    fun setMinColumnWidthPx(minWidthPx: Int) {}
    fun setItemCount(count: Int, itemFactory: (index: Int) -> NativeImpl<*>)
    fun scrollToIndex(index: Int)
    fun setEstimatedRowExtent(rowHeightPx: Float, overscanRows: Int = 2) {}
    fun updateViewportMetrics(
        scrollOffsetPx: Float,
        viewportExtentPx: Float,
        firstActiveIndex: Int,
        lastActiveIndex: Int,
        leadingSpacerPx: Float,
        trailingSpacerPx: Float,
        totalContentExtentPx: Float
    ) {}
    fun setOnViewportScrollChanged(listener: ((scrollOffsetPx: Float, viewportExtentPx: Float) -> Unit)?) {}
}
