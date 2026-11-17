@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.apple

import com.nexustools.genericui.component.system.PreferenceChangeListener
import com.nexustools.genericui.component.system.PreferenceElement
import com.nexustools.genericui.nativeimpl.system.PreferenceStoreNativeImpl
import com.nexustools.genericui.nativeimpl.system.UserDirectoryNativeImpl
import com.nexustools.genericui.util.BinaryPlist
import com.nexustools.genericui.util.UserDirectory
import platform.Foundation.NSData
import platform.Foundation.NSUserDefaults

class ApplePreferenceStoreNativeImpl(
    override val appName: String = "GenericUI",
    private val userDirectory: UserDirectoryNativeImpl,
    private val fileName: String = "preferences.plist",
    private val suiteName: String? = "com.nexustools.genericui.${UserDirectory.sanitizeFileName(appName)}"
) : PreferenceStoreNativeImpl {
    private val lock = AppleReentrantLock()
    private val entries = LinkedHashMap<String, PreferenceElement>()
    private val listeners = ArrayList<PreferenceChangeListener>()
    private val userDefaults: NSUserDefaults? = suiteName?.takeIf { it.isNotBlank() }?.let { NSUserDefaults(suiteName = it) }
    private var disposedState = false

    override val storageLocationDescription: String
        get() = "AppleBinaryPlist+NSUserDefaults(${userDirectory.resolveFilePath(fileName)})"

    override val usesNativeChangeListener: Boolean = false

    override val isDisposed: Boolean
        get() = lock.withLock { disposedState }

    init {
        loadPersistedEntries()
    }

    override fun writeElement(key: String, element: PreferenceElement) {
        val listenersSnapshot = lock.withLock {
            if (disposedState) return
            entries[key] = element
            userDefaults?.setObject(
                AppleDataBridge.byteArrayToNSData(BinaryPlist.encodeElement(element)),
                forKey = defaultKeyFor(key)
            )
            listeners.toList()
        }
        val plain = element.toPlainValue()
        for (listener in listenersSnapshot) {
            listener.onPreferenceChanged(appName, key, plain)
        }
    }

    override fun readElement(key: String): PreferenceElement? {
        return lock.withLock {
            if (disposedState) return null
            entries[key]?.let { return it }
            val defaultsData = userDefaults?.objectForKey(defaultKeyFor(key)) as? NSData
            if (defaultsData != null) {
                val bytes = AppleDataBridge.nsDataToByteArray(defaultsData)
                if (BinaryPlist.isBinaryPlist(bytes)) {
                    val decoded = BinaryPlist.decodeElement(bytes)
                    entries[key] = decoded
                    return decoded
                }
            }
            null
        }
    }

    override fun contains(key: String): Boolean {
        return lock.withLock {
            if (disposedState) false else (entries.containsKey(key) || userDefaults?.objectForKey(defaultKeyFor(key)) != null)
        }
    }

    override fun keys(): Set<String> {
        return lock.withLock {
            if (disposedState) emptySet() else entries.keys.toSet()
        }
    }

    override fun remove(key: String) {
        val listenersSnapshot = lock.withLock {
            if (disposedState) return
            entries.remove(key)
            userDefaults?.removeObjectForKey(defaultKeyFor(key))
            listeners.toList()
        }
        for (listener in listenersSnapshot) {
            listener.onPreferenceChanged(appName, key, null)
        }
    }

    override fun clear() {
        val listenersSnapshot = lock.withLock {
            if (disposedState) return
            val existingKeys = entries.keys.toList()
            entries.clear()
            for (k in existingKeys) {
                userDefaults?.removeObjectForKey(defaultKeyFor(k))
            }
            suiteName?.takeIf { it.isNotBlank() }?.let { userDefaults?.removePersistentDomainForName(it) }
            listeners.toList()
        }
        for (listener in listenersSnapshot) {
            listener.onPreferenceChanged(appName, null, null)
        }
    }

    override fun flush(): Boolean {
        val snapshotBytes = lock.withLock {
            if (disposedState) return false
            val root = PreferenceElement.ofMap(entries.toMap())
            val encoded = BinaryPlist.encodeElement(root)
            userDefaults?.setObject(
                AppleDataBridge.byteArrayToNSData(encoded),
                forKey = "__genericui_bplist_snapshot__"
            )
            userDefaults?.synchronize()
            encoded
        }
        userDirectory.ensureDirectoryExists()
        return userDirectory.writeBytes(fileName, snapshotBytes)
    }

    override fun addChangeListener(listener: PreferenceChangeListener) {
        lock.withLock {
            if (!disposedState && !listeners.contains(listener)) {
                listeners.add(listener)
            }
        }
    }

    override fun removeChangeListener(listener: PreferenceChangeListener) {
        lock.withLock {
            listeners.remove(listener)
        }
    }

    override fun dispose() {
        val shouldFlush = lock.withLock {
            if (disposedState) false else {
                disposedState = true
                listeners.clear()
                true
            }
        }
        if (shouldFlush) {
            userDirectory.ensureDirectoryExists()
            val encoded = lock.withLock { BinaryPlist.encodeElement(PreferenceElement.ofMap(entries.toMap())) }
            userDirectory.writeBytes(fileName, encoded)
            userDefaults?.synchronize()
        }
    }

    private fun loadPersistedEntries() {
        val diskBytes = userDirectory.readBytes(fileName)
        if (diskBytes != null && BinaryPlist.isBinaryPlist(diskBytes)) {
            val decoded = BinaryPlist.decodeElement(diskBytes)
            if (decoded.isMap || decoded.isObject) {
                entries.clear()
                entries.putAll(decoded.mapEntries)
                return
            }
        }
        val defaultsSnapshot = userDefaults?.objectForKey("__genericui_bplist_snapshot__") as? NSData
        if (defaultsSnapshot != null) {
            val bytes = AppleDataBridge.nsDataToByteArray(defaultsSnapshot)
            if (BinaryPlist.isBinaryPlist(bytes)) {
                val decoded = BinaryPlist.decodeElement(bytes)
                if (decoded.isMap || decoded.isObject) {
                    entries.clear()
                    entries.putAll(decoded.mapEntries)
                }
            }
        }
    }

    private fun defaultKeyFor(key: String): String = "genericui.pref.$key"
}
