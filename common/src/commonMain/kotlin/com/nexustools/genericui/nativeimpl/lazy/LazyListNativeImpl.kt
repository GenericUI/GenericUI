package com.nexustools.genericui.nativeimpl.lazy

import com.nexustools.genericui.core.AbstractContainerNativeImpl
import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.enums.ui.Orientation

interface LazyListNativeImpl<out W> : AbstractContainerNativeImpl<W> {
    fun setOrientation(orientation: Orientation)
    fun setItemCount(count: Int, itemFactory: (index: Int) -> NativeImpl<*>)
    fun notifyItemRangeChanged(startIndex: Int, count: Int)
    fun scrollToIndex(index: Int)
    fun setEstimatedItemExtent(extentPx: Float, overscanItems: Int = 3) {}
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
