package com.nexustools.genericui.nativeimpl.system

import com.nexustools.genericui.component.system.PreferenceChangeListener
import com.nexustools.genericui.component.system.PreferenceElement
import com.nexustools.genericui.core.Disposable

/**
 * Native backend contract for [com.nexustools.genericui.component.system.PreferenceStore].
 *
 * Each platform backend stores preference keys using its native per-key mechanism:
 * - **Windows / Linux (`:desktop:windows`, `:desktop:gtk`, `:desktop:qt`)**: `<configDir>/<appName>/<key>.bson` encoded with [com.nexustools.genericui.util.BinaryJson].
 * - **macOS / iOS (`:desktop:macos`, `:mobile:ios`)**: `<configDir>/<appName>/<key>.plist` encoded with [com.nexustools.genericui.util.BinaryPlist] (`bplist00`).
 * - **Swing (`:desktop:swing`)**: `<configDir>/<appName>/<key>.plist` (`BinaryPlist` on macOS) or `<configDir>/<appName>/<key>.bson` (`BinaryJson` on Windows/Linux) + Java NIO `WatchService`.
 * - **Android (`:mobile:android`)**: `android.content.SharedPreferences` (`context.getSharedPreferences(appName, Context.MODE_PRIVATE)`) + `OnSharedPreferenceChangeListener`.
 * - **Web (`:web`)**: `window.localStorage` storing `key` directly (`localStorage.getItem(key)` / `localStorage.setItem(key, json)`) + `window.addEventListener("storage", ...)`.
 */
interface PreferenceStoreNativeImpl : Disposable {
    val appName: String
    val storageLocationDescription: String
    val usesNativeChangeListener: Boolean

    fun writeElement(key: String, element: PreferenceElement)
    fun readElement(key: String): PreferenceElement?

    fun contains(key: String): Boolean
    fun keys(): Set<String>
    fun remove(key: String)
    fun clear()
    fun flush(): Boolean

    fun addChangeListener(listener: PreferenceChangeListener)
    fun removeChangeListener(listener: PreferenceChangeListener)
}
