package com.nexustools.genericui.util

import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.io.ByteBuffer
import com.nexustools.genericui.io.Readable
import com.nexustools.genericui.io.Writable
import com.nexustools.genericui.nativeimpl.system.UserDirectoryNativeImpl

/**
 * Cross-platform user configuration directory (`<userConfigDir>/<appName>[/<subdirectory>]`) extending [UserDirectory].
 *
 * Used by [com.nexustools.genericui.component.system.PreferenceStore] and application settings managers to
 * persist per-key `.bson`, `.plist`, or custom configuration files in the user's OS profile directory:
 * - **Windows (`:desktop:windows`)**: `%APPDATA%\<appName>`
 * - **macOS (`:desktop:macos`)**: `~/Library/Preferences/<appName>` (or `~/.config/<appName>`)
 * - **Linux (`:desktop:gtk`, `:desktop:kde`)**: `$XDG_CONFIG_HOME/<appName>` (or `~/.config/<appName>`)
 * - **Swing (`:desktop:swing`)**: Host OS user config directory (`%APPDATA%`, `~/Library/Preferences`, or `~/.config`)
 * - **Android (`:mobile:android`)**: `<context.filesDir>/<appName>`
 * - **iOS (`:mobile:ios`)**: `<Home>/Library/Application Support/<appName>`
 * - **Web (`:web`)**: Browser `window.indexedDB` store `"config"` (with `localStorage` read-through cache)
 */
open class ConfigDirectory internal constructor(
    appName: String,
    subdirectory: String,
    factory: NativeFactory?,
    nativeImplOverride: UserDirectoryNativeImpl?
) : UserDirectory(appName, subdirectory, factory, nativeImplOverride) {

    constructor(
        appName: String = GenericUI.applicationName,
        subdirectory: String = "",
        factory: NativeFactory? = null
    ) : this(appName, subdirectory, factory, null)

    override val fallbackScheme: String = "config"

    protected override fun createBackendImpl(activeFactory: NativeFactory): UserDirectoryNativeImpl =
        activeFactory.createConfigDirectory(appName, subdirectory)

    fun fileNameForKey(key: String, extensionWithDot: String): String {
        val cleanKey = encodePreferenceKeyToFileName(key)
        val ext = normalizeExtension(extensionWithDot)
        return "$cleanKey$ext"
    }

    fun decodeKeyFromFileName(fileName: String, extensionWithDot: String): String? {
        if (fileName.startsWith(".")) return null
        val ext = normalizeExtension(extensionWithDot)
        val stem = when {
            ext.isEmpty() -> fileName
            fileName.endsWith(ext) && fileName.length > ext.length -> fileName.removeSuffix(ext)
            else -> return null
        }
        return decodePreferenceKeyFromFileName(stem)
    }

    private fun normalizeExtension(extensionWithDot: String): String = when {
        extensionWithDot.isBlank() -> ""
        extensionWithDot.startsWith(".") -> extensionWithDot
        else -> ".$extensionWithDot"
    }

    fun resolveKeyPath(key: String, extensionWithDot: String): String =
        resolvePath(fileNameForKey(key, extensionWithDot))

    suspend fun readKey(key: String, extensionWithDot: String): ByteBuffer? =
        read(fileNameForKey(key, extensionWithDot))

    suspend fun writeKey(
        key: String,
        extensionWithDot: String,
        buffer: ByteBuffer,
        append: Boolean = false
    ): Boolean = write(fileNameForKey(key, extensionWithDot), buffer, append)

    suspend fun openKeyReadable(key: String, extensionWithDot: String): Readable? =
        openReadable(fileNameForKey(key, extensionWithDot))

    suspend fun openKeyWritable(
        key: String,
        extensionWithDot: String,
        append: Boolean = false
    ): Writable = openWritable(fileNameForKey(key, extensionWithDot), append)

    fun writeKeyBytes(key: String, extensionWithDot: String, bytes: ByteArray): Boolean =
        writeBytes(fileNameForKey(key, extensionWithDot), bytes)

    fun readKeyBytes(key: String, extensionWithDot: String): ByteArray? =
        readBytes(fileNameForKey(key, extensionWithDot))

    fun containsKey(key: String, extensionWithDot: String): Boolean =
        exists(fileNameForKey(key, extensionWithDot))

    fun removeKey(key: String, extensionWithDot: String): Boolean =
        delete(fileNameForKey(key, extensionWithDot))

    fun keysForExtension(extensionWithDot: String): Set<String> {
        val out = LinkedHashSet<String>()
        for (fileName in listFiles()) {
            decodeKeyFromFileName(fileName, extensionWithDot)?.let { out.add(it) }
        }
        return out
    }

    fun clearKeysWithExtension(extensionWithDot: String): Int {
        val matchingKeys = keysForExtension(extensionWithDot).toList()
        var removed = 0
        for (k in matchingKeys) {
            if (removeKey(k, extensionWithDot)) {
                removed++
            }
        }
        return removed
    }
}
