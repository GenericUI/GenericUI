package com.nexustools.genericui.util

import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.nativeimpl.system.UserDirectoryNativeImpl

/**
 * Cross-platform user configuration directory (`<userConfigDir>/<appName>[/<subdirectory>]`) extending [UserDirectory].
 *
 * Used by [com.nexustools.genericui.component.system.PreferenceStore] and application settings managers to
 * persist per-key `.bson`, `.plist`, or custom configuration files in the user's OS profile directory:
 * - **Windows (`:desktop:windows`)**: `%APPDATA%\<appName>`
 * - **macOS (`:desktop:macos`)**: `~/Library/Preferences/<appName>` (or `~/.config/<appName>`)
 * - **Linux (`:desktop:gtk`, `:desktop:qt`)**: `$XDG_CONFIG_HOME/<appName>` (or `~/.config/<appName>`)
 * - **Swing (`:desktop:swing`)**: Host OS user config directory (`%APPDATA%`, `~/Library/Preferences`, or `~/.config`)
 * - **Android (`:mobile:android`)**: `<context.filesDir>/<appName>`
 * - **iOS (`:mobile:ios`)**: `<Home>/Library/Application Support/<appName>`
 * - **Web (`:web`)**: `window.localStorage` namespace `genericui:config:<appName>`
 */
open class ConfigDirectory(
    appName: String = GenericUI.applicationName,
    subdirectory: String = "",
    factory: NativeFactory? = null,
    nativeImplOverride: UserDirectoryNativeImpl? = null
) : UserDirectory(appName, subdirectory, factory, nativeImplOverride) {

    override val fallbackScheme: String = "config"

    override fun createBackendImpl(activeFactory: NativeFactory): UserDirectoryNativeImpl =
        activeFactory.createConfigDirectory(appName, subdirectory)

    fun fileNameForKey(key: String, extensionWithDot: String): String {
        val cleanKey = sanitizeFileName(key.trim().ifEmpty { "default" })
        val ext = when {
            extensionWithDot.isBlank() -> ""
            extensionWithDot.startsWith(".") -> extensionWithDot
            else -> ".$extensionWithDot"
        }
        return "$cleanKey$ext"
    }

    fun resolveKeyPath(key: String, extensionWithDot: String): String =
        resolvePath(fileNameForKey(key, extensionWithDot))

    fun writeKeyBytes(key: String, extensionWithDot: String, bytes: ByteArray): Boolean =
        writeBytes(fileNameForKey(key, extensionWithDot), bytes)

    fun readKeyBytes(key: String, extensionWithDot: String): ByteArray? =
        readBytes(fileNameForKey(key, extensionWithDot))

    fun containsKey(key: String, extensionWithDot: String): Boolean =
        exists(fileNameForKey(key, extensionWithDot))

    fun removeKey(key: String, extensionWithDot: String): Boolean =
        delete(fileNameForKey(key, extensionWithDot))

    fun keysForExtension(extensionWithDot: String): Set<String> {
        val ext = when {
            extensionWithDot.isBlank() -> ""
            extensionWithDot.startsWith(".") -> extensionWithDot
            else -> ".$extensionWithDot"
        }
        val out = LinkedHashSet<String>()
        for (fileName in listFiles()) {
            if (fileName.startsWith(".")) continue
            if (ext.isEmpty()) {
                out.add(fileName)
            } else if (fileName.endsWith(ext) && fileName.length > ext.length) {
                out.add(fileName.removeSuffix(ext))
            }
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
