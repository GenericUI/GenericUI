package com.nexustools.genericui.nativeimpl.system

import com.nexustools.genericui.component.system.PreferenceChangeListener
import com.nexustools.genericui.component.system.PreferenceElement
import com.nexustools.genericui.util.BinaryJson

/**
 * Cross-platform in-memory fallback implementation of [PreferenceStoreNativeImpl] that stores each
 * preference key as a [BinaryJson]-encoded [ByteArray] and dispatches [PreferenceChangeListener] events.
 */
class DefaultPreferenceStoreNativeImpl(
    override val appName: String = "GenericUI"
) : PreferenceStoreNativeImpl {

    override val storageLocationDescription: String = "memory://$appName"
    override val usesNativeChangeListener: Boolean = true

    override var isDisposed: Boolean = false
        private set

    private val entries = LinkedHashMap<String, ByteArray>()
    private val listeners = mutableListOf<PreferenceChangeListener>()

    override fun writeElement(key: String, element: PreferenceElement) {
        if (element.isNull) {
            remove(key)
            return
        }
        entries[key] = BinaryJson.encodeElement(element)
        notifyListeners(key, element.toPlainValue())
    }

    override fun readElement(key: String): PreferenceElement? {
        val bytes = entries[key] ?: return null
        return BinaryJson.decodeElement(bytes)
    }

    override fun contains(key: String): Boolean = entries.containsKey(key)

    override fun keys(): Set<String> = LinkedHashSet(entries.keys)

    override fun remove(key: String) {
        if (entries.remove(key) != null) {
            notifyListeners(key, null)
        }
    }

    override fun clear() {
        if (entries.isNotEmpty()) {
            entries.clear()
            notifyListeners(null, null)
        }
    }

    override fun flush(): Boolean = !isDisposed

    override fun addChangeListener(listener: PreferenceChangeListener) {
        if (listener !in listeners) {
            listeners.add(listener)
        }
    }

    override fun removeChangeListener(listener: PreferenceChangeListener) {
        listeners.remove(listener)
    }

    override fun dispose() {
        isDisposed = true
        listeners.clear()
    }

    private fun notifyListeners(key: String?, newValue: Any?) {
        val snapshot = listeners.toList()
        for (listener in snapshot) {
            listener.onPreferenceChanged(appName, key, newValue)
        }
    }
}
