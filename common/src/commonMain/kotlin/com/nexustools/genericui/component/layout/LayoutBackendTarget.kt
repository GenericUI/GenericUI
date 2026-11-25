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
    KDE,
    SWING,
    ANDROID,
    IOS,
    IPADOS,
    WEB;

    val isDesktop: Boolean
        get() = this == WINDOWS || this == MACOS || this == GTK || this == KDE || this == SWING

    val isMobile: Boolean
        get() = this == ANDROID || this == IOS || this == IPADOS

    val isWeb: Boolean
        get() = this == WEB

    fun toBackendTarget(): BackendTarget = when (this) {
        WINDOWS -> BackendTarget.Windows
        MACOS -> BackendTarget.MacOS
        GTK -> BackendTarget.Gtk
        KDE -> BackendTarget.Kde
        SWING -> BackendTarget.Swing
        ANDROID -> BackendTarget.Android
        IOS -> BackendTarget.IOS
        IPADOS -> BackendTarget.IPadOS
        WEB -> BackendTarget.Web
    }

    companion object {
        fun fromBackendTarget(target: BackendTarget): LayoutBackendTarget = when (target) {
            BackendTarget.Windows -> WINDOWS
            BackendTarget.MacOS -> MACOS
            BackendTarget.Gtk -> GTK
            BackendTarget.Kde -> KDE
            BackendTarget.Swing -> SWING
            BackendTarget.Android -> ANDROID
            BackendTarget.IOS -> IOS
            BackendTarget.IPadOS -> IPADOS
            BackendTarget.Web -> WEB
        }

        fun fromId(id: String?): LayoutBackendTarget? {
            val normalized = id?.trim()?.lowercase() ?: return null
            return when (normalized) {
                "windows", "win32" -> WINDOWS
                "macos", "mac", "cocoa", "appkit" -> MACOS
                "gtk", "gtk3", "gtk4", "linux" -> GTK
                "kde", "kde6", "plasma" -> KDE
                "swing", "awt", "jvm" -> SWING
                "android" -> ANDROID
                "ios", "uikit" -> IOS
                "ipados" -> IPADOS
                "web", "browser", "js", "wasm" -> WEB
                else -> entries.firstOrNull { it.name.equals(normalized, ignoreCase = true) }
            }
        }
    }
}
