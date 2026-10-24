package com.nexustools.genericui.component.fragment

/**
 * Key-value state bundle for saving and restoring [Fragment] instance state (`onSaveInstanceState` / `onCreate`).
 * Directly mirrors Android's `Bundle` across all 8 backends.
 */
class FragmentBundle {
    private val map = linkedMapOf<String, Any?>()

    fun putString(key: String, value: String?) { map[key] = value }
    fun getString(key: String, defaultValue: String? = null): String? = map[key] as? String ?: defaultValue

    fun putInt(key: String, value: Int) { map[key] = value }
    fun getInt(key: String, defaultValue: Int = 0): Int = map[key] as? Int ?: defaultValue

    fun putLong(key: String, value: Long) { map[key] = value }
    fun getLong(key: String, defaultValue: Long = 0L): Long = map[key] as? Long ?: defaultValue

    fun putDouble(key: String, value: Double) { map[key] = value }
    fun getDouble(key: String, defaultValue: Double = 0.0): Double = map[key] as? Double ?: defaultValue

    fun putBoolean(key: String, value: Boolean) { map[key] = value }
    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean = map[key] as? Boolean ?: defaultValue

    fun containsKey(key: String): Boolean = map.containsKey(key)
    fun keys(): Set<String> = map.keys.toSet()
    fun clear() = map.clear()
}
