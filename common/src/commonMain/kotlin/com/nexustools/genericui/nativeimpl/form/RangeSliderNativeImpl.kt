package com.nexustools.genericui.nativeimpl.form

import com.nexustools.genericui.core.NativeImpl

interface RangeSliderNativeImpl<out W> : NativeImpl<W> {
    fun setRange(min: Double, max: Double, step: Double)
    fun setValue(value: Double)
    fun getValue(): Double
    fun setOnValueChanged(listener: ((Double) -> Unit)?)
}
