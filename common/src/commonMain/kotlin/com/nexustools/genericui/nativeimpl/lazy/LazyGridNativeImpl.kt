package com.nexustools.genericui.nativeimpl.lazy

import com.nexustools.genericui.core.AbstractContainerNativeImpl
import com.nexustools.genericui.core.NativeImpl

interface LazyGridNativeImpl<out W> : AbstractContainerNativeImpl<W> {
    fun setColumns(columns: Int)
    fun setItemCount(count: Int, itemFactory: (index: Int) -> NativeImpl<*>)
    fun scrollToIndex(index: Int)
}
