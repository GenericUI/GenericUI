package com.nexustools.genericui.component.layout

import com.nexustools.genericui.enums.system.BackendTarget

/**
 * Backend target identifier used by GenericUI's layout engine, visual style specifications,
 * and [com.nexustools.genericui.style.policy.AestheticStylePolicy] to apply platform-appropriate
 * rendering capabilities (such as enabling `backdrop-filter: blur(...)` on Web/Mobile while
 * falling back to translucent alpha fills on Desktop).
 */
enum class LayoutBackendTarget {
    WINDOWS,
    MACOS,
    GTK,
    QT,
    SWING,
    ANDROID,
    IOS,
    WEB;

    val isDesktop: Boolean
        get() = this == WINDOWS || this == MACOS || this == GTK || this == QT || this == SWING

    val isMobile: Boolean
        get() = this == ANDROID || this == IOS

    val isWeb: Boolean
        get() = this == WEB

    fun toBackendTarget(): BackendTarget = when (this) {
        WINDOWS -> BackendTarget.Windows
        MACOS -> BackendTarget.MacOS
        GTK -> BackendTarget.Gtk
        QT -> BackendTarget.Qt
        SWING -> BackendTarget.Swing
        ANDROID -> BackendTarget.Android
        IOS -> BackendTarget.IOS
        WEB -> BackendTarget.Web
    }

    companion object {
        fun fromBackendTarget(target: BackendTarget): LayoutBackendTarget = when (target) {
            BackendTarget.Windows -> WINDOWS
            BackendTarget.MacOS -> MACOS
            BackendTarget.Gtk -> GTK
            BackendTarget.Qt -> QT
            BackendTarget.Swing -> SWING
            BackendTarget.Android -> ANDROID
            BackendTarget.IOS -> IOS
            BackendTarget.Web -> WEB
        }

        fun fromId(id: String?): LayoutBackendTarget? {
            val normalized = id?.trim()?.lowercase() ?: return null
            return when (normalized) {
                "windows", "win32" -> WINDOWS
                "macos", "mac", "cocoa", "appkit" -> MACOS
                "gtk", "gtk3", "gtk4", "linux" -> GTK
                "qt", "qt5", "qt6" -> QT
                "swing", "awt", "jvm" -> SWING
                "android" -> ANDROID
                "ios", "uikit" -> IOS
                "web", "browser", "js", "wasm" -> WEB
                else -> entries.firstOrNull { it.name.equals(normalized, ignoreCase = true) }
            }
        }
    }
}
