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

    private var selectedValueState: String? = selectedValue

    var selectedValue: String?
        get() = selectedValueState
        set(value) {
            if (selectedValueState == value) return
            selectedValueState = value
            typedNative<SelectNativeImpl<*>>()?.setSelectedValue(value)
        }

    var onSelectionChanged: ((String) -> Unit)? = null

    fun setOptions(newOptions: List<SelectOption>): Select = apply {
        _options.clear()
        _options.addAll(newOptions)
        if (selectedValueState == null || _options.none { it.value == selectedValueState }) {
            selectedValueState = _options.firstOrNull()?.value
        }
        typedNative<SelectNativeImpl<*>>()?.setOptions(_options.map { it.value to it.label })
        typedNative<SelectNativeImpl<*>>()?.setSelectedValue(selectedValueState)
    }

    fun addOption(value: String, label: String = value): Select = apply {
        _options.add(SelectOption(value, label))
        if (selectedValueState == null) {
            selectedValueState = value
        }
        typedNative<SelectNativeImpl<*>>()?.setOptions(_options.map { it.value to it.label })
        typedNative<SelectNativeImpl<*>>()?.setSelectedValue(selectedValueState)
    }

    protected override fun createNativeImpl(factory: NativeFactory): SelectNativeImpl<*> =
        factory.createSelect()

    override fun syncStateToNative() {
        super.syncStateToNative()
        val impl = typedNative<SelectNativeImpl<*>>() ?: return
        impl.setOptions(_options.map { it.value to it.label })
        impl.setSelectedValue(selectedValueState)
        impl.setOnSelectionChanged { updated ->
            selectedValueState = updated
            onSelectionChanged?.invoke(updated)
        }
    }
}
