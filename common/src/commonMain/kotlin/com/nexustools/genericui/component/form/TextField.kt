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
    protected val passwordMode: Boolean = false,
    label: String? = null
) : AbstractComponent(htmlTag = if (multilineMode) "textarea" else "input") {
    override val isTextInputComponent: Boolean
        get() = true

    init {
        ignoreKeyEventsWhenTypingInTextInput = false
    }

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

    var label: String? = label
        set(newVal) {
            if (field == newVal) return
            field = newVal
            typedNative<TextInputNativeImpl<*>>()?.setLabel(newVal?.takeIf { it.isNotEmpty() })
        }

    var isReadOnly: Boolean = false
        set(newVal) {
            field = newVal
            typedNative<TextInputNativeImpl<*>>()?.setReadOnly(newVal)
        }

    var onValueChanged: ((String) -> Unit)? = null
        set(newVal) {
            field = newVal
            typedNative<TextInputNativeImpl<*>>()?.let { bindOnValueChangedListener(it) }
        }

    var onSubmit: ((String) -> Unit)? = null
        set(newVal) {
            field = newVal
            typedNative<TextInputNativeImpl<*>>()?.setOnSubmit(field)
        }

    protected override fun createNativeImpl(factory: NativeFactory): TextInputNativeImpl<*> =
        factory.createTextInput(multilineMode)

    private fun bindOnValueChangedListener(impl: TextInputNativeImpl<*>) {
        impl.setOnValueChanged { updated ->
            value = updated
            onValueChanged?.invoke(updated)
        }
    }

    override fun syncStateToNative() {
        super.syncStateToNative()
        val impl = typedNative<TextInputNativeImpl<*>>() ?: return
        impl.setMultiline(multilineMode)
        impl.setPasswordMode(passwordMode)
        impl.setValue(value)
        impl.setLabel(label?.takeIf { it.isNotEmpty() })
        impl.setPlaceholder(placeholder)
        impl.setReadOnly(isReadOnly)
        bindOnValueChangedListener(impl)
        impl.setOnSubmit(onSubmit)
    }
}
