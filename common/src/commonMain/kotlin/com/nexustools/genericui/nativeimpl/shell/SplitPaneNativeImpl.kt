package com.nexustools.genericui.nativeimpl.shell

import com.nexustools.genericui.core.AbstractContainerNativeImpl
import com.nexustools.genericui.enums.ui.Orientation

interface SplitPaneNativeImpl<out W> : AbstractContainerNativeImpl<W> {
    fun setOrientation(orientation: Orientation)
    fun getOrientation(): Orientation
    fun setDividerPosition(ratio: Float)
    fun getDividerPosition(): Float
}
