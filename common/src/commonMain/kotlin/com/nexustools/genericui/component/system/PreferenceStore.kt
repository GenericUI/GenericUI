package com.nexustools.genericui.component.system

import com.nexustools.genericui.core.Disposable
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.nativeimpl.system.DefaultPreferenceStoreNativeImpl
import com.nexustools.genericui.nativeimpl.system.PreferenceStoreNativeImpl
import com.nexustools.genericui.util.ConfigDirectory
import kotlin.reflect.KClass

/**
 * Cross-platform object and preference persistence store (`com.nexustools.genericui.component.system.PreferenceStore`).
 *
 * Accepts arbitrary objects, `data class`es, [Map]s, [Collection]s, arrays, enums, and primitives directly
 * via [put] / [save] and reconstructs them via [get] / [load] across all 8 GenericUI backends:
 * - **Windows (`:desktop:windows`) & Linux (`:desktop:gtk`, `:desktop:qt`)**: Stores each `key` in its own
 *   `<userConfigDir>/<appName>/<key>.bson` file encoded with [com.nexustools.genericui.util.BinaryJson].
 * - **macOS (`:desktop:macos`) & iOS (`:mobile:ios`)**: Stores each `key` in its own
 *   `<userConfigDir>/<appName>/<key>.plist` file encoded with [com.nexustools.genericui.util.BinaryPlist] (`bplist00`).
 * - **Swing (`:desktop:swing`)**: Stores each `key` in `<userConfigDir>/<appName>/<key>.plist` (`BinaryPlist` on macOS)
 *   or `<userConfigDir>/<appName>/<key>.bson` (`BinaryJson` on Windows/Linux) with Java NIO `WatchService` change notifications.
 * - **Android (`:mobile:android`)**: Uses native `android.content.SharedPreferences` (`context.getSharedPreferences(appName, Context.MODE_PRIVATE)`)
 *   and `SharedPreferences.OnSharedPreferenceChangeListener`.
 * - **Web (`:web`)**: Uses `window.localStorage` storing each `key` directly (`localStorage.getItem(key)` / `localStorage.setItem(key, json)`)
 *   and `window.addEventListener("storage", ...)`.
 */
class PreferenceStore(
    val appName: String = GenericUI.applicationName,
    private val factory: NativeFactory? = null
) : Disposable {

    private var boundFactory: NativeFactory? = factory ?: GenericUI.activeFactory
    private var backingNativeImpl: PreferenceStoreNativeImpl =
        boundFactory?.createPreferenceStore(appName)
            ?: DefaultPreferenceStoreNativeImpl(appName)
    private val registeredListeners = mutableListOf<PreferenceChangeListener>()

    /**
     * The [ConfigDirectory] associated with this [PreferenceStore]'s [appName].
     */
    val configDirectory: ConfigDirectory = ConfigDirectory(appName = appName, factory = factory)

    val nativeImpl: PreferenceStoreNativeImpl
        get() {
            if (factory == null) {
                val active = GenericUI.activeFactory
                if (active != null && active !== boundFactory) {
                    val previous = backingNativeImpl
                    boundFactory = active
                    val upgraded = active.createPreferenceStore(appName)
                    for (listener in registeredListeners) {
                        upgraded.addChangeListener(listener)
                    }
                    if (previous is DefaultPreferenceStoreNativeImpl) {
                        for (k in previous.keys()) {
                            if (!upgraded.contains(k)) {
                                previous.readElement(k)?.let { upgraded.writeElement(k, it) }
                            }
                        }
                    }
                    backingNativeImpl = upgraded
                }
            }
            return backingNativeImpl
        }

    val storageLocation: String
        get() = nativeImpl.storageLocationDescription

    val usesNativeChangeListener: Boolean
        get() = nativeImpl.usesNativeChangeListener

    override val isDisposed: Boolean
        get() = nativeImpl.isDisposed

    /**
     * Serializes and persists [value] under [key]. Passing `null` removes [key].
     */
    fun <T : Any> put(key: String, value: T?): PreferenceStore {
        if (value == null) {
            nativeImpl.remove(key)
        } else {
            val element = PreferenceObjectCodec.toElement(value)
            nativeImpl.writeElement(key, element)
        }
        return this
    }

    /**
     * Alias for [put]: serializes and persists [value] under [key].
     */
    fun <T : Any> save(key: String, value: T?): PreferenceStore = put(key, value)

    /**
     * Alias for [put]: serializes and persists structured object [value] under [key].
     */
    fun <T : Any> putObject(key: String, value: T?): PreferenceStore = put(key, value)

    /**
     * Reads and deserializes the object or primitive stored under [key] into an instance of [targetClass],
     * or returns [defaultValue] if [key] does not exist.
     */
    fun <T : Any> get(key: String, targetClass: KClass<T>, defaultValue: T? = null): T? {
        val element = nativeImpl.readElement(key) ?: return defaultValue
        return PreferenceObjectCodec.fromElement(element, targetClass, defaultValue)
    }

    /**
     * Alias for [get]: reads and deserializes the object or primitive stored under [key] into [targetClass].
     */
    fun <T : Any> load(key: String, targetClass: KClass<T>, defaultValue: T? = null): T? =
        get(key, targetClass, defaultValue)

    /**
     * Alias for [get]: reads and deserializes the structured object stored under [key] into [targetClass].
     */
    fun <T : Any> getObject(key: String, targetClass: KClass<T>, defaultValue: T? = null): T? =
        get(key, targetClass, defaultValue)

    /**
     * Reified overload of [get] that deserializes the value stored under [key] into `T`.
     */
    inline fun <reified T : Any> get(key: String, defaultValue: T? = null): T? =
        get(key, T::class, defaultValue)

    /**
     * Reified overload of [getObject] that deserializes the object stored under [key] into `T`.
     */
    inline fun <reified T : Any> getObject(key: String, defaultValue: T? = null): T? =
        get(key, T::class, defaultValue)

    /**
     * Reads the value stored under [key] into `T`, returning non-null [defaultValue] if absent.
     */
    inline fun <reified T : Any> getOrDefault(key: String, defaultValue: T): T =
        get(key, T::class, defaultValue) ?: defaultValue

    /**
     * Reified overload of [load] returning non-null [defaultValue] if absent.
     */
    inline fun <reified T : Any> load(key: String, defaultValue: T): T =
        getOrDefault(key, defaultValue)

    /**
     * Reads the raw [PreferenceElement] AST stored under [key], or `null` if absent.
     */
    fun getElement(key: String): PreferenceElement? =
        nativeImpl.readElement(key)

    /**
     * Writes a [PreferenceElement] AST directly under [key].
     */
    fun putElement(key: String, element: PreferenceElement): PreferenceStore {
        nativeImpl.writeElement(key, element)
        return this
    }

    // --- Typed Primitive Convenience Helpers ---

    fun getString(key: String, defaultValue: String? = null): String? =
        nativeImpl.readElement(key)?.asString(defaultValue) ?: defaultValue

    fun getStringOrDefault(key: String, defaultValue: String = ""): String =
        getString(key, defaultValue) ?: defaultValue

    fun putString(key: String, value: String?): PreferenceStore {
        if (value == null) {
            nativeImpl.remove(key)
        } else {
            nativeImpl.writeElement(key, PreferenceElement.ofString(value))
        }
        return this
    }

    fun getInt(key: String, defaultValue: Int = 0): Int =
        nativeImpl.readElement(key)?.asInt(defaultValue) ?: defaultValue

    fun putInt(key: String, value: Int): PreferenceStore {
        nativeImpl.writeElement(key, PreferenceElement.ofInt(value))
        return this
    }

    fun getLong(key: String, defaultValue: Long = 0L): Long =
        nativeImpl.readElement(key)?.asLong(defaultValue) ?: defaultValue

    fun putLong(key: String, value: Long): PreferenceStore {
        nativeImpl.writeElement(key, PreferenceElement.ofLong(value))
        return this
    }

    fun getFloat(key: String, defaultValue: Float = 0f): Float =
        nativeImpl.readElement(key)?.asFloat(defaultValue) ?: defaultValue

    fun putFloat(key: String, value: Float): PreferenceStore {
        nativeImpl.writeElement(key, PreferenceElement.ofFloat(value))
        return this
    }

    fun getDouble(key: String, defaultValue: Double = 0.0): Double =
        nativeImpl.readElement(key)?.asDouble(defaultValue) ?: defaultValue

    fun putDouble(key: String, value: Double): PreferenceStore {
        nativeImpl.writeElement(key, PreferenceElement.ofDouble(value))
        return this
    }

    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean =
        nativeImpl.readElement(key)?.asBoolean(defaultValue) ?: defaultValue

    fun putBoolean(key: String, value: Boolean): PreferenceStore {
        nativeImpl.writeElement(key, PreferenceElement.ofBoolean(value))
        return this
    }

    fun getStringSet(key: String, defaultValue: Set<String> = emptySet()): Set<String> {
        val elem = nativeImpl.readElement(key) ?: return defaultValue
        if (!elem.isArray) return defaultValue
        val out = LinkedHashSet<String>(elem.arrayElements.size)
        for (item in elem.arrayElements) {
            item.asString()?.let { out.add(it) }
        }
        return out
    }

    fun putStringSet(key: String, values: Set<String>): PreferenceStore {
        nativeImpl.writeElement(
            key,
            PreferenceElement.ofArray(values.map { PreferenceElement.ofString(it) })
        )
        return this
    }

    fun getStringList(key: String, defaultValue: List<String> = emptyList()): List<String> {
        val elem = nativeImpl.readElement(key) ?: return defaultValue
        if (!elem.isArray) return defaultValue
        return elem.arrayElements.mapNotNull { it.asString() }
    }

    fun putStringList(key: String, values: List<String>): PreferenceStore {
        nativeImpl.writeElement(
            key,
            PreferenceElement.ofArray(values.map { PreferenceElement.ofString(it) })
        )
        return this
    }

    // --- Key Management ---

    fun contains(key: String): Boolean = nativeImpl.contains(key)

    fun keys(): Set<String> = nativeImpl.keys()

    fun getAll(): Map<String, Any?> {
        val allKeys = nativeImpl.keys()
        val out = LinkedHashMap<String, Any?>(allKeys.size)
        for (k in allKeys) {
            out[k] = nativeImpl.readElement(k)?.toPlainValue()
        }
        return out
    }

    fun remove(key: String): PreferenceStore {
        nativeImpl.remove(key)
        return this
    }

    fun clear(): PreferenceStore {
        nativeImpl.clear()
        return this
    }

    fun flush(): Boolean = nativeImpl.flush()

    inline fun edit(block: PreferenceStore.() -> Unit): Boolean {
        this.block()
        return flush()
    }

    // --- Native Change Listener Registration ---

    fun addChangeListener(listener: PreferenceChangeListener) {
        if (listener !in registeredListeners) {
            registeredListeners.add(listener)
        }
        nativeImpl.addChangeListener(listener)
    }

    fun removeChangeListener(listener: PreferenceChangeListener) {
        registeredListeners.remove(listener)
        nativeImpl.removeChangeListener(listener)
    }

    fun registerChangeListener(listener: PreferenceChangeListener) {
        addChangeListener(listener)
    }

    fun unregisterChangeListener(listener: PreferenceChangeListener) {
        removeChangeListener(listener)
    }

    override fun dispose() {
        registeredListeners.clear()
        nativeImpl.dispose()
    }

    companion object {
        const val DEFAULT_APP_NAME: String = "GenericUI"

        fun open(
            appName: String = GenericUI.applicationName,
            factory: NativeFactory? = GenericUI.activeFactory
        ): PreferenceStore = PreferenceStore(appName, factory)

        fun <T : Any> registerTypeAdapter(targetClass: KClass<T>, adapter: PreferenceTypeAdapter<T>) {
            PreferenceObjectCodec.registerTypeAdapter(targetClass, adapter)
        }

        inline fun <reified T : Any> registerTypeAdapter(adapter: PreferenceTypeAdapter<T>) {
            PreferenceObjectCodec.registerTypeAdapter(T::class, adapter)
        }

        fun <T : Any> registerObjectFactory(
            targetClass: KClass<T>,
            factory: (Map<String, PreferenceElement>) -> T
        ) {
            PreferenceObjectCodec.registerObjectFactory(targetClass, factory)
        }

        inline fun <reified T : Any> registerObjectFactory(
            noinline factory: (Map<String, PreferenceElement>) -> T
        ) {
            PreferenceObjectCodec.registerObjectFactory(T::class, factory)
        }
    }
}
