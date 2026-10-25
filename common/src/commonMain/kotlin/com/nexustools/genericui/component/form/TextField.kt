package com.nexustools.genericui.component.form

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.nativeimpl.form.TextInputNativeImpl

/**
 * Single-line or multi-line text input field (`<input class="form-control">` / `<textarea>`).
 */
open class TextField(
    value: String = "",
    placeholder: String = "",
    protected val multilineMode: Boolean = false,
    protected val passwordMode: Boolean = false
) : AbstractComponent() {
    var value: String = value
        set(newVal) {
            if (field == newVal) return
            field = newVal
            typedNative<TextInputNativeImpl<*>>()?.setValue(newVal)
        }

    var placeholder: String = placeholder
        set(newVal) {
            field = newVal
            typedNative<TextInputNativeImpl<*>>()?.setPlaceholder(newVal)
        }

    var isReadOnly: Boolean = false
        set(newVal) {
            field = newVal
            typedNative<TextInputNativeImpl<*>>()?.setReadOnly(newVal)
        }

    var onValueChanged: ((String) -> Unit)? = null
    var onSubmit: ((String) -> Unit)? = null

    override fun createNativeImpl(factory: NativeFactory): TextInputNativeImpl<*> =
        factory.createTextInput(multilineMode)

    override fun syncStateToNative() {
        super.syncStateToNative()
        val impl = typedNative<TextInputNativeImpl<*>>() ?: return
        impl.setMultiline(multilineMode)
        impl.setPasswordMode(passwordMode)
        impl.setValue(value)
        impl.setPlaceholder(placeholder)
        impl.setReadOnly(isReadOnly)
        impl.setOnValueChanged { updated ->
            value = updated
            onValueChanged?.invoke(updated)
        }
        impl.setOnSubmit(onSubmit)
    }
}
