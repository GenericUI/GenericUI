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
    private val suiteName: String? = "com.nexustools.genericui.${UserDirectory.sanitizeFileName(appName)}"
) : PreferenceStoreNativeImpl {
    private val lock = AppleReentrantLock()
    private val entries = LinkedHashMap<String, PreferenceElement>()
    private val dirtyKeys = LinkedHashSet<String>()
    private val listeners = ArrayList<PreferenceChangeListener>()
    private val userDefaults: NSUserDefaults? = suiteName?.takeIf { it.isNotBlank() }?.let { NSUserDefaults(suiteName = it) }
    private var disposedState = false

    override val storageLocationDescription: String
        get() = "AppleBinaryPlist+NSUserDefaults(${userDirectory.directoryPath}/*.plist)"

    override val usesNativeChangeListener: Boolean = false

    override val isDisposed: Boolean
        get() = lock.withLock { disposedState }

    init {
        loadPersistedEntries()
    }

    fun fileNameForKey(key: String): String =
        "${UserDirectory.encodePreferenceKeyToFileName(key)}.plist"

    fun backupFileNameForKey(key: String): String =
        "${fileNameForKey(key)}.old"

    override fun writeElement(key: String, element: PreferenceElement) {
        if (element.isNull) {
            remove(key)
            return
        }
        val encoded = BinaryPlist.encodeElement(element)
        val listenersSnapshot = lock.withLock {
            if (disposedState) return
            val persisted = writePlistWithOldBackup(key, encoded)
            entries[key] = element
            if (persisted) {
                dirtyKeys.remove(key)
            } else {
                dirtyKeys.add(key)
            }
            userDefaults?.setObject(
                AppleDataBridge.byteArrayToNSData(encoded),
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
            if (disposedState) return@withLock null
            val primaryName = fileNameForKey(key)
            val backupName = backupFileNameForKey(key)
            val primaryExists = userDirectory.exists(primaryName)
            val backupExists = userDirectory.exists(backupName)

            if (primaryExists) {
                val primaryBytes = userDirectory.readBytes(primaryName)
                val decodedPrimary = primaryBytes?.let { BinaryPlist.decodeElementOrNull(it) }
                if (decodedPrimary != null) {
                    if (backupExists) {
                        userDirectory.delete(backupName)
                    }
                    entries[key] = decodedPrimary
                    return@withLock decodedPrimary
                }
            }

            if (backupExists) {
                val backupBytes = userDirectory.readBytes(backupName)
                val decodedBackup = backupBytes?.let { BinaryPlist.decodeElementOrNull(it) }
                if (decodedBackup != null) {
                    if (userDirectory.writeBytes(primaryName, backupBytes)) {
                        val verified = userDirectory.readBytes(primaryName)
                        if (verified != null &&
                            verified.size == backupBytes.size &&
                            BinaryPlist.decodeElementOrNull(verified) != null
                        ) {
                            userDirectory.delete(backupName)
                        }
                    }
                    entries[key] = decodedBackup
                    return@withLock decodedBackup
                }
            }

            if (primaryExists || backupExists) {
                entries.remove(key)
                return@withLock null
            }

            val defaultsData = userDefaults?.objectForKey(defaultKeyFor(key)) as? NSData
            if (defaultsData != null) {
                val bytes = AppleDataBridge.nsDataToByteArray(defaultsData)
                val decoded = BinaryPlist.decodeElementOrNull(bytes)
                if (decoded != null) {
                    entries[key] = decoded
                    return@withLock decoded
                }
            }

            if (key in dirtyKeys) {
                return@withLock entries[key]
            }
            entries.remove(key)
            null
        }
    }

    override fun contains(key: String): Boolean {
        return lock.withLock {
            if (disposedState) return@withLock false
            val primaryName = fileNameForKey(key)
            val backupName = backupFileNameForKey(key)
            val primaryExists = userDirectory.exists(primaryName)
            val backupExists = userDirectory.exists(backupName)

            if (primaryExists) {
                val bytes = userDirectory.readBytes(primaryName)
                if (bytes != null && BinaryPlist.decodeElementOrNull(bytes) != null) {
                    return@withLock true
                }
            }
            if (backupExists) {
                val bytes = userDirectory.readBytes(backupName)
                if (bytes != null && BinaryPlist.decodeElementOrNull(bytes) != null) {
                    return@withLock true
                }
            }
            if (primaryExists || backupExists) {
                return@withLock false
            }

            val defaultsData = userDefaults?.objectForKey(defaultKeyFor(key)) as? NSData
            if (defaultsData != null) {
                val bytes = AppleDataBridge.nsDataToByteArray(defaultsData)
                if (BinaryPlist.decodeElementOrNull(bytes) != null) {
                    return@withLock true
                }
            }

            key in dirtyKeys && entries.containsKey(key)
        }
    }

    override fun keys(): Set<String> {
        return lock.withLock {
            if (disposedState) return@withLock emptySet()
            scanAllKeysUnlocked()
        }
    }

    override fun remove(key: String) {
        val listenersSnapshot = lock.withLock {
            if (disposedState) return
            val primaryName = fileNameForKey(key)
            val backupName = backupFileNameForKey(key)
            val legacyPrimaryName = legacySanitizedFileNameForKey(key)
            val legacyBackupName = legacyPrimaryName?.let { "$it.old" }
            val existed = entries.containsKey(key) ||
                userDirectory.exists(primaryName) ||
                userDirectory.exists(backupName) ||
                (legacyPrimaryName != null && userDirectory.exists(legacyPrimaryName)) ||
                (legacyBackupName != null && userDirectory.exists(legacyBackupName)) ||
                userDefaults?.objectForKey(defaultKeyFor(key)) != null
            entries.remove(key)
            dirtyKeys.remove(key)
            userDirectory.delete(primaryName)
            userDirectory.delete(backupName)
            if (legacyPrimaryName != null) {
                userDirectory.delete(legacyPrimaryName)
            }
            if (legacyBackupName != null) {
                userDirectory.delete(legacyBackupName)
            }
            userDefaults?.removeObjectForKey(defaultKeyFor(key))
            if (!existed) return
            listeners.toList()
        }
        for (listener in listenersSnapshot) {
            listener.onPreferenceChanged(appName, key, null)
        }
    }

    override fun clear() {
        val listenersSnapshot = lock.withLock {
            if (disposedState) return
            val existingKeys = scanAllKeysUnlocked()
            val allKnownKeys = LinkedHashSet<String>(existingKeys).apply {
                addAll(entries.keys)
            }
            val files = userDirectory.listFiles()
            var deletedFiles = false
            for (fileName in files) {
                if (fileName.startsWith(".")) continue
                if (fileName.endsWith(".plist") || fileName.endsWith(".plist.old")) {
                    if (userDirectory.delete(fileName)) {
                        deletedFiles = true
                    }
                }
            }
            for (k in allKnownKeys) {
                if (userDirectory.delete(fileNameForKey(k))) deletedFiles = true
                if (userDirectory.delete(backupFileNameForKey(k))) deletedFiles = true
                legacySanitizedFileNameForKey(k)?.let { legacyName ->
                    if (userDirectory.delete(legacyName)) deletedFiles = true
                    if (userDirectory.delete("$legacyName.old")) deletedFiles = true
                }
                userDefaults?.removeObjectForKey(defaultKeyFor(k))
            }
            entries.clear()
            dirtyKeys.clear()
            suiteName?.takeIf { it.isNotBlank() }?.let { userDefaults?.removePersistentDomainForName(it) }
            if (allKnownKeys.isEmpty() && !deletedFiles) return
            listeners.toList()
        }
        for (listener in listenersSnapshot) {
            listener.onPreferenceChanged(appName, null, null)
        }
    }

    override fun flush(): Boolean {
        return lock.withLock {
            if (disposedState) return@withLock false
            userDirectory.ensureDirectoryExists()
            for (k in dirtyKeys.toList()) {
                val elem = entries[k] ?: continue
                if (writePlistWithOldBackup(k, BinaryPlist.encodeElement(elem))) {
                    dirtyKeys.remove(k)
                }
            }
            userDefaults?.synchronize()
            dirtyKeys.isEmpty()
        }
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
        lock.withLock {
            if (disposedState) return
            if (dirtyKeys.isNotEmpty()) {
                userDirectory.ensureDirectoryExists()
                for (k in dirtyKeys.toList()) {
                    val elem = entries[k] ?: continue
                    if (writePlistWithOldBackup(k, BinaryPlist.encodeElement(elem))) {
                        dirtyKeys.remove(k)
                    }
                }
            }
            userDefaults?.synchronize()
            disposedState = true
            listeners.clear()
        }
    }

    private fun renameUserFile(fromFileName: String, toFileName: String): Boolean {
        if (userDirectory is AppleUserDirectoryNativeImpl) {
            return userDirectory.renameFile(fromFileName, toFileName)
        }
        val bytes = userDirectory.readBytes(fromFileName) ?: return false
        userDirectory.delete(toFileName)
        if (!userDirectory.writeBytes(toFileName, bytes)) return false
        userDirectory.delete(fromFileName)
        return true
    }

    private fun writePlistWithOldBackup(key: String, encoded: ByteArray): Boolean {
        userDirectory.ensureDirectoryExists()
        val primaryName = fileNameForKey(key)
        val backupName = backupFileNameForKey(key)

        if (userDirectory.exists(primaryName)) {
            val primaryValid = userDirectory.readBytes(primaryName)
                ?.let { BinaryPlist.decodeElementOrNull(it) != null } == true
            val backupValid = userDirectory.exists(backupName) &&
                (userDirectory.readBytes(backupName)?.let { BinaryPlist.decodeElementOrNull(it) != null } == true)
            if (!primaryValid && backupValid) {
                userDirectory.delete(primaryName)
            } else {
                if (userDirectory.exists(backupName)) {
                    userDirectory.delete(backupName)
                }
                renameUserFile(primaryName, backupName)
            }
        }

        if (!userDirectory.writeBytes(primaryName, encoded)) {
            return false
        }
        val written = userDirectory.readBytes(primaryName)
        val verified = written != null &&
            written.size == encoded.size &&
            BinaryPlist.decodeElementOrNull(written) != null
        if (!verified) {
            return false
        }
        if (userDirectory.exists(backupName)) {
            userDirectory.delete(backupName)
        }
        return true
    }

    private fun decodeKeyFromPlistFileName(fileName: String, suffix: String): String? {
        if (fileName.startsWith(".")) return null
        if (!fileName.endsWith(suffix) || fileName.length <= suffix.length) return null
        val encodedStem = fileName.removeSuffix(suffix)
        return UserDirectory.decodePreferenceKeyFromFileName(encodedStem)
    }

    private fun legacySanitizedFileNameForKey(key: String): String? {
        val legacyStem = UserDirectory.sanitizeFileName(key.trim().ifEmpty { "default" })
        val primaryName = fileNameForKey(key)
        val legacyName = "$legacyStem.plist"
        if (legacyName == primaryName) return null
        val isCanonicalBijectiveStem = !legacyStem.startsWith(".") &&
            UserDirectory.encodePreferenceKeyToFileName(
                UserDirectory.decodePreferenceKeyFromFileName(legacyStem)
            ) == legacyStem
        return if (isCanonicalBijectiveStem) null else legacyName
    }

    private fun scanAllKeysUnlocked(): Set<String> {
        val out = LinkedHashSet<String>()
        for (fileName in userDirectory.listFiles()) {
            val backupKey = decodeKeyFromPlistFileName(fileName, ".plist.old")
            if (backupKey != null) {
                val bytes = userDirectory.readBytes(fileName)
                if (bytes != null && BinaryPlist.decodeElementOrNull(bytes) != null) {
                    out.add(backupKey)
                }
                continue
            }
            val primaryKey = decodeKeyFromPlistFileName(fileName, ".plist")
            if (primaryKey != null) {
                out.add(primaryKey)
            }
        }
        out.addAll(dirtyKeys)
        return out.sorted().toCollection(LinkedHashSet())
    }

    private fun loadPersistedEntries() {
        migrateLegacyBundles()
        entries.clear()
        val files = userDirectory.listFiles()
        for (fileName in files) {
            val key = decodeKeyFromPlistFileName(fileName, ".plist.old") ?: continue
            val bytes = userDirectory.readBytes(fileName) ?: continue
            val decoded = BinaryPlist.decodeElementOrNull(bytes) ?: continue
            if (!entries.containsKey(key)) {
                entries[key] = decoded
            }
        }
        for (fileName in files) {
            val key = decodeKeyFromPlistFileName(fileName, ".plist") ?: continue
            val bytes = userDirectory.readBytes(fileName) ?: continue
            val decoded = BinaryPlist.decodeElementOrNull(bytes) ?: continue
            entries[key] = decoded
        }
    }

    private fun migrateLegacyBundles() {
        val defaultsSnapshot = userDefaults?.objectForKey("__genericui_bplist_snapshot__") as? NSData
        val snapshotBundle = defaultsSnapshot?.let {
            BinaryPlist.decodeElementOrNull(AppleDataBridge.nsDataToByteArray(it))
        }
        val existingFiles = userDirectory.listFiles()
        val hasOtherPerKeyPlists = existingFiles.any {
            !it.startsWith(".") && it.endsWith(".plist") && it != "preferences.plist" && it != "settings.plist"
        }
        val legacyNames = listOf("preferences.plist", "settings.plist")
        if (!hasOtherPerKeyPlists || snapshotBundle != null) {
            for (legacyName in legacyNames) {
                if (!userDirectory.exists(legacyName)) continue
                val bytes = userDirectory.readBytes(legacyName) ?: continue
                val decoded = BinaryPlist.decodeElementOrNull(bytes) ?: continue
                if (!(decoded.isMap || decoded.isObject) || decoded.mapEntries.isEmpty()) continue
                val legacyStem = legacyName.removeSuffix(".plist")
                val containsSelfKey = decoded.mapEntries.containsKey(legacyStem)
                if (snapshotBundle == null && containsSelfKey && decoded.mapEntries.size == 1) continue

                val extracted = LinkedHashMap<String, PreferenceElement>(decoded.mapEntries)
                userDirectory.delete(legacyName)
                for ((k, v) in extracted) {
                    if (v.isNull) continue
                    val targetFile = fileNameForKey(k)
                    if (!userDirectory.exists(targetFile)) {
                        writePlistWithOldBackup(k, BinaryPlist.encodeElement(v))
                    }
                }
            }
        }

        if (snapshotBundle != null && (snapshotBundle.isMap || snapshotBundle.isObject)) {
            for ((k, v) in snapshotBundle.mapEntries) {
                if (v.isNull) continue
                val targetFile = fileNameForKey(k)
                if (!userDirectory.exists(targetFile)) {
                    writePlistWithOldBackup(k, BinaryPlist.encodeElement(v))
                }
            }
            userDefaults.removeObjectForKey("__genericui_bplist_snapshot__")
        }
    }

    private fun defaultKeyFor(key: String): String = "genericui.pref.$key"
}
