package com.nexustools.genericui.component.navigation

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.nativeimpl.navigation.BottomNavigationBarNativeImpl

/**
 * Mobile/adaptive bottom navigation bar (`BottomNavigationView` on Android, `UITabBar` on iOS).
 */
class BottomNavigationBar : AbstractComponent(htmlTag = "genericui:bottomnavigationbar") {
    private val _items = mutableListOf<Triple<String, String, String?>>()
    val items: List<Triple<String, String, String?>> get() = _items
    private val actionCallbacks = linkedMapOf<String, () -> Unit>()

    private var isUpdatingSelectionFromNative: Boolean = false
    private var isSettingSelectionProgrammatically: Boolean = false

    var selectedItemId: String? = null
        set(value) {
            if (value != null && actionCallbacks.containsKey(value)) {
                actionCallbacks[value]?.invoke()
                val current = field
                if (current != null) {
                    isSettingSelectionProgrammatically = true
                    try {
                        typedNative<BottomNavigationBarNativeImpl<*>>()?.setSelectedItemId(current)
                    } finally {
                        isSettingSelectionProgrammatically = false
                    }
                }
                return
            }
            if (field == value && (isUpdatingSelectionFromNative || isSettingSelectionProgrammatically)) {
                return
            }
            field = value
            if (value != null) {
                if (!isUpdatingSelectionFromNative && !isSettingSelectionProgrammatically) {
                    isSettingSelectionProgrammatically = true
                    try {
                        typedNative<BottomNavigationBarNativeImpl<*>>()?.setSelectedItemId(value)
                    } finally {
                        isSettingSelectionProgrammatically = false
                    }
                }
                onItemSelected?.invoke(value)
            }
        }

    var onItemSelected: ((String) -> Unit)? = null

    fun isActionItem(id: String): Boolean = actionCallbacks.containsKey(id)

    internal fun setActionCallbacks(callbacks: Map<String, () -> Unit>) {
        actionCallbacks.clear()
        actionCallbacks.putAll(callbacks)
    }

    fun setItems(newItems: List<Triple<String, String, String?>>) {
        _items.clear()
        _items.addAll(newItems)
        if ((selectedItemId == null || actionCallbacks.containsKey(selectedItemId)) && _items.isNotEmpty()) {
            selectedItemId = _items.firstOrNull { !actionCallbacks.containsKey(it.first) }?.first
        }
        typedNative<BottomNavigationBarNativeImpl<*>>()?.setItems(_items)
    }

    fun addItem(id: String, label: String, iconSvgOrPath: String? = null): BottomNavigationBar = apply {
        actionCallbacks.remove(id)
        _items.add(Triple(id, label, iconSvgOrPath))
        if (selectedItemId == null) selectedItemId = id
        typedNative<BottomNavigationBarNativeImpl<*>>()?.setItems(_items)
    }

    fun addActionItem(
        id: String,
        label: String,
        iconSvgOrPath: String? = null,
        onClick: () -> Unit
    ): BottomNavigationBar = apply {
        actionCallbacks[id] = onClick
        _items.removeAll { it.first == id }
        _items.add(Triple(id, label, iconSvgOrPath))
        typedNative<BottomNavigationBarNativeImpl<*>>()?.setItems(_items)
    }

    protected override fun createNativeImpl(factory: NativeFactory): BottomNavigationBarNativeImpl<*> =
        factory.createBottomNavigationBar()

    override fun syncStateToNative() {
        super.syncStateToNative()
        val impl = typedNative<BottomNavigationBarNativeImpl<*>>() ?: return
        impl.setItems(_items)
        isSettingSelectionProgrammatically = true
        try {
            selectedItemId?.let { impl.setSelectedItemId(it) }
        } finally {
            isSettingSelectionProgrammatically = false
        }
        impl.setOnItemSelected { id ->
            if (actionCallbacks.containsKey(id)) {
                actionCallbacks[id]?.invoke()
                val current = selectedItemId
                if (current != null) {
                    isSettingSelectionProgrammatically = true
                    try {
                        impl.setSelectedItemId(current)
                    } finally {
                        isSettingSelectionProgrammatically = false
                    }
                }
                return@setOnItemSelected
            }
            if (!isSettingSelectionProgrammatically && !isUpdatingSelectionFromNative && selectedItemId != id) {
                isUpdatingSelectionFromNative = true
                try {
                    selectedItemId = id
                } finally {
                    isUpdatingSelectionFromNative = false
                }
            }
        }
    }
}
