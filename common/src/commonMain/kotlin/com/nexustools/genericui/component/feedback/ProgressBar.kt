package com.nexustools.genericui.component.feedback

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.nativeimpl.feedback.ProgressBarNativeImpl

/**
 * Determinate or indeterminate progress bar (`<div class="progress">`).
 */
class ProgressBar(
    progress: Float = 0f,
    indeterminate: Boolean = false,
    type: BootstrapType = BootstrapType.PRIMARY
) : AbstractComponent(htmlTag = "genericui:progressbar") {
    var progress: Float = progress.coerceIn(0f, 1f)
        set(value) {
            field = value.coerceIn(0f, 1f)
            typedNative<ProgressBarNativeImpl<*>>()?.setProgress(field)
        }

    var isIndeterminate: Boolean = indeterminate
        set(value) {
            field = value
            typedNative<ProgressBarNativeImpl<*>>()?.setIndeterminate(value)
        }

    var label: String? = null
        set(value) {
            field = value
            typedNative<ProgressBarNativeImpl<*>>()?.setLabel(value)
        }

    init {
        this.type = type
    }

    override fun createNativeImpl(factory: NativeFactory): ProgressBarNativeImpl<*> =
        factory.createProgressBar()

    override fun syncStateToNative() {
        super.syncStateToNative()
        val impl = typedNative<ProgressBarNativeImpl<*>>() ?: return
        impl.setProgress(progress)
        impl.setIndeterminate(isIndeterminate)
        impl.setLabel(label)
    }
}
