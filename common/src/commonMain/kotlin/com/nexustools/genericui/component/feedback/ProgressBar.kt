package com.nexustools.genericui.component.feedback

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.nativeimpl.feedback.ProgressBarNativeImpl

/**
 * Determinate or indeterminate progress bar (`<div class="progress">`).
 */
class ProgressBar(
    progress: Float = 0f,
    indeterminate: Boolean = false,
    busy: Boolean = false,
    label: String? = null
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

    var isBusy: Boolean = busy
        set(value) {
            field = value
            typedNative<ProgressBarNativeImpl<*>>()?.setBusy(value)
        }

    fun setBusy(busy: Boolean): ProgressBar = apply {
        this.isBusy = busy
    }

    var label: String? = label
        set(value) {
            field = value
            typedNative<ProgressBarNativeImpl<*>>()?.setLabel(value)
        }

    init {
        if (busy) {
            typedNative<ProgressBarNativeImpl<*>>()?.setBusy(true)
        }
    }

    protected override fun createNativeImpl(factory: NativeFactory): ProgressBarNativeImpl<*> =
        factory.createProgressBar()

    override fun syncStateToNative() {
        super.syncStateToNative()
        val impl = typedNative<ProgressBarNativeImpl<*>>() ?: return
        impl.setProgress(progress)
        impl.setIndeterminate(isIndeterminate)
        impl.setBusy(isBusy)
        impl.setLabel(label)
    }
}
