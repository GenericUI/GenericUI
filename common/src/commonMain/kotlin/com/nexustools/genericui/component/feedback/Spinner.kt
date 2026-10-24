package com.nexustools.genericui.component.feedback

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.nativeimpl.feedback.SpinnerNativeImpl

/**
 * Animated loading spinner (`spinner-border` or `spinner-grow`).
 */
class Spinner(
    var growStyle: Boolean = false,
    var label: String? = "Loading...",
    type: BootstrapType = BootstrapType.PRIMARY
) : AbstractComponent() {
    init {
        this.type = type
    }

    override fun createNativeImpl(factory: NativeFactory): SpinnerNativeImpl<*> =
        factory.createSpinner()

    override fun syncStateToNative() {
        super.syncStateToNative()
        val impl = typedNative<SpinnerNativeImpl<*>>() ?: return
        impl.setGrowStyle(growStyle)
        impl.setLabel(label)
    }
}
