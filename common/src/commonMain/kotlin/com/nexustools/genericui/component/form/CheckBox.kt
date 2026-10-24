package com.nexustools.genericui.component.form

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.nativeimpl.form.CheckboxNativeImpl

/**
 * Boolean checkbox input control (`<input type="checkbox" class="form-check-input">`).
 */
open class CheckBox(
    label: String = "",
    checked: Boolean = false,
    protected val switchStyle: Boolean = false,
    protected val radioStyle: Boolean = false
) : AbstractComponent() {
    var label: String = label
        set(value) {
            field = value
            typedNative<CheckboxNativeImpl<*>>()?.setLabel(value)
        }

    var isChecked: Boolean = checked
        set(value) {
            field = value
            typedNative<CheckboxNativeImpl<*>>()?.setChecked(value)
        }

    var onCheckedChanged: ((Boolean) -> Unit)? = null

    override fun createNativeImpl(factory: NativeFactory): CheckboxNativeImpl<*> =
        factory.createCheckbox(switchStyle = switchStyle, radioStyle = radioStyle)

    override fun syncStateToNative() {
        super.syncStateToNative()
        val impl = typedNative<CheckboxNativeImpl<*>>() ?: return
        impl.setLabel(label)
        impl.setChecked(isChecked)
        impl.setOnCheckedChanged { updated ->
            isChecked = updated
            onCheckedChanged?.invoke(updated)
        }
    }
}
