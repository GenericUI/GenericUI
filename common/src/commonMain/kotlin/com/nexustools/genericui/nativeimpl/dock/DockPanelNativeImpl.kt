package com.nexustools.genericui.nativeimpl.dock

import com.nexustools.genericui.component.dock.DockHeaderAction
import com.nexustools.genericui.core.AbstractContainerNativeImpl

interface DockPanelNativeImpl<out W> : AbstractContainerNativeImpl<W> {
    fun setTitle(title: String)
    fun getTitle(): String
    fun setIcon(icon: String?)
    fun setCollapsed(collapsed: Boolean)
    fun isCollapsed(): Boolean
    fun setOnCollapsedChanged(listener: ((Boolean) -> Unit)?) = Unit
    fun setFloating(floating: Boolean)
    fun isFloating(): Boolean
    fun setHeaderActions(actions: List<DockHeaderAction>)
}
