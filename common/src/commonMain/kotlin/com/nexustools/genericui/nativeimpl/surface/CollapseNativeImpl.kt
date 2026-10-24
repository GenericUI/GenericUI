package com.nexustools.genericui.nativeimpl.surface

import com.nexustools.genericui.core.AbstractContainerNativeImpl

interface CollapseNativeImpl<out W> : AbstractContainerNativeImpl<W> {
    fun setTitle(title: String)
    fun setExpanded(expanded: Boolean)
    fun isExpanded(): Boolean
    fun setOnExpandedChanged(listener: ((Boolean) -> Unit)?)
}
