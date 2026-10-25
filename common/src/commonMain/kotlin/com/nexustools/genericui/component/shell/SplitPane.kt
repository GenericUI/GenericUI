package com.nexustools.genericui.component.shell

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.enums.ui.Orientation
import com.nexustools.genericui.nativeimpl.shell.SplitPaneNativeImpl

class SplitPane(
    orientation: Orientation = Orientation.HORIZONTAL,
    dividerPosition: Float = 0.5f
) : AbstractContainer("split-pane") {
    init {
        htmlTag = "genericui:splitpane"
    }

    private var orientationState: Orientation = orientation
    private var dividerRatioState: Float = dividerPosition.coerceIn(0.05f, 0.95f)

    override fun createNativeImpl(factory: NativeFactory): SplitPaneNativeImpl<*> =
        factory.createSplitPane()

    override fun onAttachNative(impl: NativeImpl<*>) {
        super.onAttachNative(impl)
        val splitImpl = impl as? SplitPaneNativeImpl<*> ?: return
        splitImpl.setOrientation(orientationState)
        splitImpl.setDividerPosition(dividerRatioState)
    }

    fun setOrientation(orientation: Orientation) {
        orientationState = orientation
        typedNative<SplitPaneNativeImpl<*>>()?.setOrientation(orientation)
    }

    fun getOrientation(): Orientation = typedNative<SplitPaneNativeImpl<*>>()?.getOrientation() ?: orientationState

    fun setDividerPosition(ratio: Float) {
        dividerRatioState = ratio.coerceIn(0.05f, 0.95f)
        typedNative<SplitPaneNativeImpl<*>>()?.setDividerPosition(dividerRatioState)
    }

    fun getDividerPosition(): Float = typedNative<SplitPaneNativeImpl<*>>()?.getDividerPosition() ?: dividerRatioState

    fun setPanes(first: AbstractComponent, second: AbstractComponent) {
        clearChildren()
        addChild(first)
        addChild(second)
    }
}
