package com.nexustools.genericui.component.dock

import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.enums.dock.DockPosition
import com.nexustools.genericui.nativeimpl.dock.DockAreaNativeImpl

/**
 * Dockable zone at a specific [DockPosition] (`LEFT`, `RIGHT`, `TOP`, `BOTTOM`, `CENTER`)
 * inside a [DockWorkspace].
 */
class DockArea(
    val position: DockPosition
) : AbstractContainer() {
    init {
        htmlTag = "genericui:dockarea"
    }

    var isCollapsed: Boolean = false
        set(value) {
            field = value
            typedNative<DockAreaNativeImpl<*>>()?.setCollapsed(value)
        }

    private val _groups = mutableListOf<DockGroup>()
    val groups: List<DockGroup> get() = _groups

    fun addGroup(group: DockGroup): DockArea = apply {
        _groups.add(group)
        addChild(group)
    }

    fun addPanel(panel: DockPanel): DockArea = apply {
        val group = _groups.firstOrNull() ?: DockGroup().also { addGroup(it) }
        group.addPanel(panel)
    }

    override fun createNativeImpl(factory: NativeFactory): DockAreaNativeImpl<*> =
        factory.createDockArea()

    override fun syncStateToNative() {
        super.syncStateToNative()
        val impl = typedNative<DockAreaNativeImpl<*>>() ?: return
        impl.setPosition(position)
        impl.setCollapsed(isCollapsed)
    }
}
