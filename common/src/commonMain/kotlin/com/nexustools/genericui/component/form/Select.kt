package com.nexustools.genericui.component.form

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.nativeimpl.form.SelectNativeImpl

/**
 * Dropdown select control (`<select class="form-select">`).
 */
open class Select(
    options: List<SelectOption> = emptyList(),
    selectedValue: String? = options.firstOrNull()?.value
) : AbstractComponent(htmlTag = "select") {
    private val _options = options.toMutableList()
    val options: List<SelectOption> get() = _options

    var selectedValue: String? = selectedValue
        set(value) {
            if (field == value) return
            field = value
            typedNative<SelectNativeImpl<*>>()?.setSelectedValue(value)
        }

    var onSelectionChanged: ((String) -> Unit)? = null

    fun setOptions(newOptions: List<SelectOption>): Select = apply {
        _options.clear()
        _options.addAll(newOptions)
        if (selectedValue == null) {
            selectedValue = _options.firstOrNull()?.value
        }
        typedNative<SelectNativeImpl<*>>()?.setOptions(_options.map { it.value to it.label })
        typedNative<SelectNativeImpl<*>>()?.setSelectedValue(selectedValue)
    }

    fun addOption(value: String, label: String = value): Select = apply {
        _options.add(SelectOption(value, label))
        if (selectedValue == null) {
            selectedValue = value
        }
        typedNative<SelectNativeImpl<*>>()?.setOptions(_options.map { it.value to it.label })
        typedNative<SelectNativeImpl<*>>()?.setSelectedValue(selectedValue)
    }

    override fun createNativeImpl(factory: NativeFactory): SelectNativeImpl<*> =
        factory.createSelect()

    override fun syncStateToNative() {
        super.syncStateToNative()
        val impl = typedNative<SelectNativeImpl<*>>() ?: return
        impl.setOptions(_options.map { it.value to it.label })
        impl.setSelectedValue(selectedValue)
        impl.setOnSelectionChanged { updated ->
            selectedValue = updated
            onSelectionChanged?.invoke(updated)
        }
    }
}
