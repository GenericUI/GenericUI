package com.nexustools.genericui.enums.system

/**
 * Identifies the active GenericUI platform backend target.
 *
 * Single-target checks use the enum value directly (e.g. `app.backendTarget == BackendTarget.Windows`,
 * `app.backendTarget == BackendTarget.Kde`, `app.backendTarget == BackendTarget.Gtk`); there are intentionally
 * no single-target booleans such as `isWindows` / `isAndroid`.
 * Multi-backend group properties ([isSystem], [isDesktop], [isNativeDesktop], [isLinux], [isMobile],
 * [isApple], [isIosFamily], [isUnix], [isPosix], [isNative], [isJvm], [isTouchPrimary], [isBrowser])
 * test capabilities shared across multiple targets.
 */
enum class BackendTarget(
    /** Stable lowercase identifier (matches the backend module name, e.g. `"windows"`, `"gtk"`, `"kde"`). */
    val id: String,
    /** Human-readable backend description. */
    val displayName: String,
    /** Operating system family name (e.g. `"Windows"`, `"macOS"`, `"Linux"`, `"Web Browser"`). */
    val osFamilyName: String
) {
    Windows("windows", "Windows (Win32)", "Windows"),
    MacOS("macos", "macOS (AppKit)", "macOS"),
    Gtk("gtk", "Linux (GTK4 / Libadwaita)", "Linux"),
    Kde("kde", "Linux (KDE Plasma)", "Linux"),
    Swing("swing", "Java Swing (AWT / Java2D)", "JVM Desktop"),
    Android("android", "Android", "Android"),
    IOS("ios", "iOS (UIKit)", "iOS"),
    IPadOS("ipados", "iPadOS (UIKit)", "iPadOS"),
    Web("web", "Web (HTML5 / JS / Wasm)", "Web Browser");

    /** `true` for all installed OS/system targets (`Windows`, `MacOS`, `Gtk`, `Kde`, `Swing`, `Android`, `IOS`, `IPadOS`); `false` for `Web`. */
    val isSystem: Boolean
        get() = this != Web

    /** `true` for all desktop targets (`Windows`, `MacOS`, `Gtk`, `Kde`, `Swing`). */
    val isDesktop: Boolean
        get() = this == Windows || this == MacOS || this == Gtk || this == Kde || this == Swing

    /** `true` for native C/C++/Win32/ObjC desktop targets (`Windows`, `MacOS`, `Gtk`, `Kde`), excluding JVM `Swing`. */
    val isNativeDesktop: Boolean
        get() = this == Windows || this == MacOS || this == Gtk || this == Kde

    /** `true` for Linux desktop widget toolkits (`Gtk` and `Kde`). */
    val isLinux: Boolean
        get() = this == Gtk || this == Kde

    /** `true` for mobile targets (`Android`, `IOS`, and `IPadOS`). */
    val isMobile: Boolean
        get() = this == Android || this == IOS || this == IPadOS

    /** `true` for Apple operating systems (`MacOS`, `IOS`, and `IPadOS`). */
    val isApple: Boolean
        get() = this == MacOS || this == IOS || this == IPadOS

    /** `true` for Apple UIKit / iOS-family operating systems (`IOS` and `IPadOS`). */
    val isIosFamily: Boolean
        get() = this == IOS || this == IPadOS

    /** `true` for Unix / Unix-like OS families (`MacOS`, `Gtk`, `Kde`, `Android`, `IOS`, `IPadOS`). */
    val isUnix: Boolean
        get() = this == MacOS || this == Gtk || this == Kde || this == Android || this == IOS || this == IPadOS

    /** `true` for Kotlin/Native POSIX-capable targets (`Windows`, `MacOS`, `Gtk`, `Kde`, `IOS`, `IPadOS`). */
    val isPosix: Boolean
        get() = this == Windows || this == MacOS || this == Gtk || this == Kde || this == IOS || this == IPadOS

    /** `true` for ahead-of-time native binary targets (`Windows`, `MacOS`, `Gtk`, `Kde`, `IOS`, `IPadOS`). */
    val isNative: Boolean
        get() = this == Windows || this == MacOS || this == Gtk || this == Kde || this == IOS || this == IPadOS

    /** `true` for JVM / ART bytecode targets (`Swing` and `Android`). */
    val isJvm: Boolean
        get() = this == Swing || this == Android

    /** `true` for touch-first mobile/tablet targets (`Android`, `IOS`, and `IPadOS`). */
    val isTouchPrimary: Boolean
        get() = isMobile

    /** `true` when running inside a web browser environment (`Web`). */
    val isBrowser: Boolean
        get() = this == Web

    companion object {
        /**
         * Resolves a [BackendTarget] from its stable lowercase [id] (e.g. `"windows"`, `"ipados"`)
         * or enum name, or returns `null` if [id] does not match a known target.
         */
        fun fromId(id: String?): BackendTarget? {
            val normalized = id?.trim()?.lowercase() ?: return null
            return when (normalized) {
                "windows" -> Windows
                "macos" -> MacOS
                "gtk" -> Gtk
                "kde" -> Kde
                "swing" -> Swing
                "android" -> Android
                "ios" -> IOS
                "ipados" -> IPadOS
                "web" -> Web
                else -> entries.firstOrNull {
                    it.id.equals(normalized, ignoreCase = true) || it.name.equals(normalized, ignoreCase = true)
                }
            }
        }
    }
}
