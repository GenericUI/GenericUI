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
    label: String? = null,
    classPrefix: String = ""
) : AbstractComponent(
    componentClassPrefix = classPrefix,
    htmlTag = if (multilineMode) "textarea" else "input"
), ITextField {
    override val isTextInputComponent: Boolean
        get() = true

    override val supportsFocus: Boolean
        get() = true

    override val labelTargetComponent: AbstractComponent
        get() = this

    init {
        ignoreKeyEventsWhenTypingInTextInput = false
    }

    override var value: String = value
        set(newVal) {
            if (field == newVal) return
            field = newVal
            typedNative<TextInputNativeImpl<*>>()?.setValue(newVal)
        }

    override var placeholder: String = placeholder
        set(newVal) {
            field = newVal
            typedNative<TextInputNativeImpl<*>>()?.setPlaceholder(newVal)
        }

    override var label: String? = label
        set(newVal) {
            if (field == newVal) return
            field = newVal
            typedNative<TextInputNativeImpl<*>>()?.setLabel(newVal?.takeIf { it.isNotEmpty() })
        }

    override var isReadOnly: Boolean = false
        set(newVal) {
            field = newVal
            typedNative<TextInputNativeImpl<*>>()?.setReadOnly(newVal)
        }

    override var isEnabled: Boolean
        get() = super.isEnabled
        set(value) {
            super.isEnabled = value
        }

    override var onValueChanged: ((String) -> Unit)? = null
        set(newVal) {
            field = newVal
            typedNative<TextInputNativeImpl<*>>()?.let { bindOnValueChangedListener(it) }
        }

    override var onSubmit: ((String) -> Unit)? = null
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
