package com.nexustools.genericui.component.form

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.nativeimpl.form.RangeSliderNativeImpl

/**
 * Continuous or stepped numeric slider control (`<input type="range" class="form-range">`).
 */
open class Slider(
    value: Double = 0.0,
    min: Double = 0.0,
    max: Double = 100.0,
    step: Double = 1.0
) : AbstractComponent(htmlTag = "input") {
    var min: Double = min
        set(newVal) {
            field = newVal
            typedNative<RangeSliderNativeImpl<*>>()?.setRange(field, max, step)
        }

    var max: Double = max
        set(newVal) {
            field = newVal
            typedNative<RangeSliderNativeImpl<*>>()?.setRange(min, field, step)
        }

    var step: Double = step
        set(newVal) {
            field = newVal
            typedNative<RangeSliderNativeImpl<*>>()?.setRange(min, max, field)
        }

    private var isDispatchingValueChange: Boolean = false

    var value: Double = value.coerceIn(min, max)
        set(newVal) {
            val clamped = newVal.coerceIn(min, max)
            if (kotlin.math.abs(field - clamped) <= 1e-4) return
            field = clamped
            if (!isDispatchingValueChange) {
                isDispatchingValueChange = true
                try {
                    typedNative<RangeSliderNativeImpl<*>>()?.setValue(field)
                } finally {
                    isDispatchingValueChange = false
                }
            }
        }

    var onValueChanged: ((Double) -> Unit)? = null

    fun dispatchUserValueChange(newVal: Double) {
        val clamped = newVal.coerceIn(min, max)
        value = clamped
        onValueChanged?.invoke(clamped)
    }

    protected override fun createNativeImpl(factory: NativeFactory): RangeSliderNativeImpl<*> =
        factory.createRangeSlider()

    override fun syncStateToNative() {
        super.syncStateToNative()
        val impl = typedNative<RangeSliderNativeImpl<*>>() ?: return
        isDispatchingValueChange = true
        try {
            impl.setRange(min, max, step)
            impl.setValue(value)
        } finally {
            isDispatchingValueChange = false
        }
        impl.setOnValueChanged { updated ->
            if (!isDispatchingValueChange) {
                isDispatchingValueChange = true
                try {
                    value = updated
                } finally {
                    isDispatchingValueChange = false
                }
                onValueChanged?.invoke(updated)
            }
        }
    }
}
