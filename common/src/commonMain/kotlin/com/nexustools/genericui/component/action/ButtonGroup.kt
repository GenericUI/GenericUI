package com.nexustools.genericui.component.action

import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.enums.ui.Orientation
import com.nexustools.genericui.nativeimpl.layout.ContainerNativeImpl

/**
 * Horizontal or vertical group of [Button]s (`btn-group` / `btn-group-vertical`).
 */
class ButtonGroup(
    val orientation: Orientation = Orientation.HORIZONTAL
) : AbstractContainer() {
    override fun createNativeImpl(factory: NativeFactory): ContainerNativeImpl<*> =
        factory.createContainer(if (orientation == Orientation.VERTICAL) "btn-group-vertical" else "btn-group")
}
