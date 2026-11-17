package com.nexustools.genericui.jvm

import com.nexustools.genericui.component.system.PreferenceChangeListener
import com.nexustools.genericui.component.system.PreferenceElement
import com.nexustools.genericui.nativeimpl.system.PreferenceStoreNativeImpl
import com.nexustools.genericui.util.BinaryJson
import com.nexustools.genericui.util.BinaryPlist
import java.io.File

/**
 * Shared JVM and Android (`minSdk >= 24`, API-1 `java.io.File`) base implementation of
 * [PreferenceStoreNativeImpl] persisting each preference key as a separate binary `.bson`
 * ([BinaryJson]) or `.plist` ([BinaryPlist]) file inside `<configDir>/<appName>/` and
 * dispatching [PreferenceChangeListener] notifications.
 */
open class JvmPreferenceStoreBase(
    override val appName: String = "GenericUI",
    private val customDirectory: File? = null
) : PreferenceStoreNativeImpl {

    val isMacOsHost: Boolean =
        System.getProperty("os.name", "").lowercase().let { it.contains("mac") || it.contains("darwin") }

    open val useBinaryPlist: Boolean
        get() = isMacOsHost

    open val fileExtension: String
        get() = if (useBinaryPlist) ".plist" else ".bson"

    val storageDirectory: File by lazy {
        (customDirectory ?: resolveDefaultDirectory(appName)).apply {
            mkdirs()
            onDirectoryInitialized(this)
        }
    }

    override val storageLocationDescription: String
        get() = "${storageDirectory.absolutePath}${File.separator}*$fileExtension (${if (useBinaryPlist) "BinaryPlist" else "BinaryJson"})"

    override val usesNativeChangeListener: Boolean = true

    override var isDisposed: Boolean = false
        protected set

    private val lock = Any()
    private val listeners = mutableListOf<PreferenceChangeListener>()

    protected open fun resolveDefaultDirectory(appName: String): File =
        resolveHostAppConfigDirectory(appName)

    protected open fun onDirectoryInitialized(directory: File) {}

    protected open fun onFlushPoll() {}

    protected open fun onDisposeResources() {}

    fun fileForKey(key: String): File {
        val cleanKey = sanitizeKey(key)
        return File(storageDirectory, "$cleanKey$fileExtension")
    }

    override fun writeElement(key: String, element: PreferenceElement) {
        if (element.isNull) {
            remove(key)
            return
        }
        val bytes = if (useBinaryPlist) {
            BinaryPlist.encodeElement(element)
        } else {
            BinaryJson.encodeElement(element)
        }
        synchronized(lock) {
            if (isDisposed) return
            storageDirectory.mkdirs()
            fileForKey(key).writeBytes(bytes)
        }
        notifyListeners(key, element.toPlainValue())
    }

    override fun readElement(key: String): PreferenceElement? {
        val bytes = synchronized(lock) {
            if (isDisposed) return null
            val file = fileForKey(key)
            if (!file.isFile || !file.exists()) return null
            runCatching { file.readBytes() }.getOrNull()
        } ?: return null
        if (bytes.isEmpty()) return null
        return if (useBinaryPlist || BinaryPlist.isBinaryPlist(bytes)) {
            BinaryPlist.decodeElement(bytes)
        } else {
            BinaryJson.decodeElement(bytes)
        }
    }

    override fun contains(key: String): Boolean = synchronized(lock) {
        if (isDisposed) return false
        val file = fileForKey(key)
        file.isFile && file.exists()
    }

    override fun keys(): Set<String> = synchronized(lock) {
        if (isDisposed) return emptySet()
        val ext = fileExtension
        val children = storageDirectory.listFiles() ?: return emptySet()
        val out = LinkedHashSet<String>()
        for (child in children) {
            val name = child.name
            if (child.isFile && !name.startsWith(".") && name.endsWith(ext) && name.length > ext.length) {
                out.add(name.removeSuffix(ext))
            }
        }
        out.sorted().toCollection(LinkedHashSet())
    }

    override fun remove(key: String) {
        val removed = synchronized(lock) {
            if (isDisposed) return
            val file = fileForKey(key)
            file.exists() && file.delete()
        }
        if (removed) {
            notifyListeners(key, null)
        }
    }

    override fun clear() {
        val removedAny = synchronized(lock) {
            if (isDisposed) return
            val ext = fileExtension
            val children = storageDirectory.listFiles() ?: return
            var deletedCount = 0
            for (child in children) {
                val name = child.name
                if (child.isFile && !name.startsWith(".") && name.endsWith(ext) && name.length > ext.length) {
                    if (child.delete()) {
                        deletedCount++
                    }
                }
            }
            deletedCount > 0
        }
        if (removedAny) {
            notifyListeners(null, null)
        }
    }

    override fun flush(): Boolean {
        onFlushPoll()
        return !isDisposed
    }

    override fun addChangeListener(listener: PreferenceChangeListener) {
        synchronized(lock) {
            if (listener !in listeners) {
                listeners.add(listener)
            }
        }
    }

    override fun removeChangeListener(listener: PreferenceChangeListener) {
        synchronized(lock) {
            listeners.remove(listener)
        }
    }

    override fun dispose() {
        synchronized(lock) {
            if (isDisposed) return
            isDisposed = true
            listeners.clear()
        }
        onDisposeResources()
    }

    protected fun notifyListeners(key: String?, newValue: Any?) {
        val snapshot = synchronized(lock) { listeners.toList() }
        for (listener in snapshot) {
            listener.onPreferenceChanged(appName, key, newValue)
        }
    }

    companion object {
        fun sanitizeName(raw: String): String {
            val trimmed = raw.trim().trim('/', '\\').ifEmpty { "GenericUI" }
            return trimmed.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        }

        fun sanitizeKey(raw: String): String {
            val trimmed = raw.trim().trim('/', '\\').ifEmpty { "default" }
            return trimmed.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        }

        fun resolveHostAppConfigDirectory(appName: String): File {
            val safeApp = sanitizeName(appName)
            val osName = System.getProperty("os.name", "").lowercase()
            val userHome = System.getProperty("user.home", ".")
            if (osName.contains("win")) {
                val appData = System.getenv("APPDATA")?.trim()?.takeIf { it.isNotEmpty() }
                val baseDir = if (appData != null) File(appData) else File(userHome, "AppData/Roaming")
                return File(baseDir, safeApp)
            }
            val xdg = System.getenv("XDG_CONFIG_HOME")?.trim()?.takeIf { it.isNotEmpty() }
            val baseDir = if (xdg != null) File(xdg) else File(userHome, ".config")
            return File(baseDir, safeApp)
        }
    }
}
