package com.nexustools.genericui.component.dock

import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.enums.dock.DockGroupMode
import com.nexustools.genericui.nativeimpl.dock.DockGroupNativeImpl

/**
 * Group of [DockPanel]s arranged as tabs, a vertical accordion stack, or a proportional split.
 */
class DockGroup(
    mode: DockGroupMode = DockGroupMode.TABS
) : AbstractContainer() {
    var mode: DockGroupMode = mode
        set(value) {
            field = value
            typedNative<DockGroupNativeImpl<*>>()?.setGroupMode(value)
        }

    var activePanelIndex: Int = 0
        set(value) {
            field = value
            typedNative<DockGroupNativeImpl<*>>()?.setActivePanelIndex(value)
        }

    private val _panels = mutableListOf<DockPanel>()
    val panels: List<DockPanel> get() = _panels

    fun addPanel(panel: DockPanel): DockGroup = apply {
        _panels.add(panel)
        addChild(panel)
    }

    fun removePanel(panel: DockPanel): Boolean {
        if (_panels.remove(panel)) {
            removeChild(panel)
            return true
        }
        return false
    }

    override fun createNativeImpl(factory: NativeFactory): DockGroupNativeImpl<*> =
        factory.createDockGroup()

    override fun syncStateToNative() {
        super.syncStateToNative()
        val impl = typedNative<DockGroupNativeImpl<*>>() ?: return
        impl.setGroupMode(mode)
        impl.setActivePanelIndex(activePanelIndex)
    }
}
