package com.nexustools.genericui.component.action

import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.nativeimpl.action.DropdownNativeImpl

/**
 * Dropdown menu button (`<div class="dropdown">`).
 */
open class DropdownButton(
    label: String = "",
    type: BootstrapType = BootstrapType.PRIMARY
) : AbstractContainer() {
    var label: String = label
        set(value) {
            field = value
            typedNative<DropdownNativeImpl<*>>()?.setLabel(value)
        }

    private val _items = mutableListOf<Pair<String, String>>()
    val items: List<Pair<String, String>> get() = _items

    var onItemSelected: ((String) -> Unit)? = null

    init {
        this.type = type
    }

    fun addItem(id: String, title: String): DropdownButton = apply {
        _items.add(id to title)
        typedNative<DropdownNativeImpl<*>>()?.setItems(_items)
    }

    fun setItems(entries: List<Pair<String, String>>): DropdownButton = apply {
        _items.clear()
        _items.addAll(entries)
        typedNative<DropdownNativeImpl<*>>()?.setItems(_items)
    }

    override fun createNativeImpl(factory: NativeFactory): DropdownNativeImpl<*> =
        factory.createDropdown()

    override fun syncStateToNative() {
        super.syncStateToNative()
        val impl = typedNative<DropdownNativeImpl<*>>() ?: return
        impl.setLabel(label)
        impl.setItems(_items)
        impl.setOnItemSelected(onItemSelected)
    }
}
