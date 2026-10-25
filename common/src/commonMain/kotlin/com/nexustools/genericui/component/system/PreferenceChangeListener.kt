package com.nexustools.genericui.component.system

/**
 * Listener notified whenever a preference key is added, updated, removed, or cleared in a [PreferenceStore].
 * Uses the platform's native change listener when available (such as Android's
 * `SharedPreferences.OnSharedPreferenceChangeListener`, Web's `window.addEventListener("storage", ...)`,
 * JVM's `WatchService`, or desktop/mobile config file observers).
 */
fun interface PreferenceChangeListener {
    /**
     * Called when a preference changes.
     *
     * @param storeName Name of the application / [PreferenceStore].
     * @param key The changed preference key, or `null` when the entire store was cleared.
     * @param newValue The updated value or [PreferenceElement], or `null` if the key was removed or cleared.
     */
    fun onPreferenceChanged(storeName: String, key: String?, newValue: Any?)
}
