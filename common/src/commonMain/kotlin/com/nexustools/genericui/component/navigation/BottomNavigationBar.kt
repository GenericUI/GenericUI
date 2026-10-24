package com.nexustools.genericui.component.navigation

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.nativeimpl.navigation.BottomNavigationBarNativeImpl

/**
 * Mobile/adaptive bottom navigation bar (`BottomNavigationView` on Android, `UITabBar` on iOS).
 */
class BottomNavigationBar : AbstractComponent() {
    private val _items = mutableListOf<Triple<String, String, String?>>()
    val items: List<Triple<String, String, String?>> get() = _items

    var selectedItemId: String? = null
        set(value) {
            field = value
            if (value != null) {
                typedNative<BottomNavigationBarNativeImpl<*>>()?.setSelectedItemId(value)
                onItemSelected?.invoke(value)
            }
        }

    var onItemSelected: ((String) -> Unit)? = null

    fun setItems(newItems: List<Triple<String, String, String?>>) {
        _items.clear()
        _items.addAll(newItems)
        if (selectedItemId == null && _items.isNotEmpty()) {
            selectedItemId = _items.first().first
        }
        typedNative<BottomNavigationBarNativeImpl<*>>()?.setItems(_items)
    }

    fun addItem(id: String, label: String, iconSvgOrPath: String? = null): BottomNavigationBar = apply {
        _items.add(Triple(id, label, iconSvgOrPath))
        if (selectedItemId == null) selectedItemId = id
        typedNative<BottomNavigationBarNativeImpl<*>>()?.setItems(_items)
    }

    override fun createNativeImpl(factory: NativeFactory): BottomNavigationBarNativeImpl<*> =
        factory.createBottomNavigationBar()

    override fun syncStateToNative() {
        super.syncStateToNative()
        val impl = typedNative<BottomNavigationBarNativeImpl<*>>() ?: return
        impl.setItems(_items)
        selectedItemId?.let { impl.setSelectedItemId(it) }
        impl.setOnItemSelected { id ->
            selectedItemId = id
        }
    }
}
