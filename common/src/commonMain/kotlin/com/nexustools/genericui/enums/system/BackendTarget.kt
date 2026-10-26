package com.nexustools.genericui.enums.system

/**
 * Identifies the active GenericUI platform backend target.
 *
 * Single-target checks use the enum value directly (e.g. `app.backendTarget == BackendTarget.Windows`,
 * `app.backendTarget == BackendTarget.Qt`, `app.backendTarget == BackendTarget.Gtk`); there are intentionally
 * no single-target booleans such as `isWindows` / `isAndroid`.
 * Multi-backend group properties ([isSystem], [isDesktop], [isNativeDesktop], [isLinux], [isMobile],
 * [isApple], [isUnix], [isPosix], [isNative], [isJvm], [isTouchPrimary], [isBrowser]) test capabilities
 * shared across multiple targets.
 */
enum class BackendTarget(
    /** Stable lowercase identifier (matches the backend module name, e.g. `"windows"`, `"gtk"`). */
    val id: String,
    /** Human-readable backend description. */
    val displayName: String,
    /** Operating system family name (e.g. `"Windows"`, `"macOS"`, `"Linux"`, `"Web Browser"`). */
    val osFamilyName: String
) {
    Windows("windows", "Windows (Win32)", "Windows"),
    MacOS("macos", "macOS (AppKit)", "macOS"),
    Gtk("gtk", "Linux (GTK4 / Libadwaita)", "Linux"),
    Qt("qt", "Linux / Desktop (Qt6)", "Linux"),
    Swing("swing", "Java Swing (AWT / Java2D)", "JVM Desktop"),
    Android("android", "Android", "Android"),
    IOS("ios", "iOS (UIKit)", "iOS"),
    Web("web", "Web (HTML5 / JS / Wasm)", "Web Browser");

    /** `true` for all installed OS/system targets (`Windows`, `MacOS`, `Gtk`, `Qt`, `Swing`, `Android`, `IOS`); `false` for `Web`. */
    val isSystem: Boolean
        get() = this != Web

    /** `true` for all desktop targets (`Windows`, `MacOS`, `Gtk`, `Qt`, `Swing`). */
    val isDesktop: Boolean
        get() = this == Windows || this == MacOS || this == Gtk || this == Qt || this == Swing

    /** `true` for native C/C++/Win32/ObjC desktop targets (`Windows`, `MacOS`, `Gtk`, `Qt`), excluding JVM `Swing`. */
    val isNativeDesktop: Boolean
        get() = this == Windows || this == MacOS || this == Gtk || this == Qt

    /** `true` for Linux desktop widget toolkits (`Gtk` and `Qt`). */
    val isLinux: Boolean
        get() = this == Gtk || this == Qt

    /** `true` for mobile targets (`Android` and `IOS`). */
    val isMobile: Boolean
        get() = this == Android || this == IOS

    /** `true` for Apple operating systems (`MacOS` and `IOS`). */
    val isApple: Boolean
        get() = this == MacOS || this == IOS

    /** `true` for Unix / Unix-like OS families (`MacOS`, `Gtk`, `Qt`, `Android`, `IOS`). */
    val isUnix: Boolean
        get() = this == MacOS || this == Gtk || this == Qt || this == Android || this == IOS

    /** `true` for Kotlin/Native POSIX-capable targets (`Windows`, `MacOS`, `Gtk`, `Qt`, `IOS`). */
    val isPosix: Boolean
        get() = this == Windows || this == MacOS || this == Gtk || this == Qt || this == IOS

    /** `true` for ahead-of-time native binary targets (`Windows`, `MacOS`, `Gtk`, `Qt`, `IOS`). */
    val isNative: Boolean
        get() = this == Windows || this == MacOS || this == Gtk || this == Qt || this == IOS

    /** `true` for JVM / ART bytecode targets (`Swing` and `Android`). */
    val isJvm: Boolean
        get() = this == Swing || this == Android

    /** `true` for touch-first mobile/tablet targets (`Android` and `IOS`). */
    val isTouchPrimary: Boolean
        get() = isMobile

    /** `true` when running inside a web browser environment (`Web`). */
    val isBrowser: Boolean
        get() = this == Web

    companion object {
        val WINDOWS: BackendTarget = Windows
        val MACOS: BackendTarget = MacOS
        val MAC: BackendTarget = MacOS
        val GTK: BackendTarget = Gtk
        val QT: BackendTarget = Qt
        val SWING: BackendTarget = Swing
        val ANDROID: BackendTarget = Android
        val WEB: BackendTarget = Web

        /**
         * Resolves a [BackendTarget] from a `NativeFactory.backendName` string, or returns `null` when the
         * name is not recognized (e.g. headless/mock test factories such as `"headless-marker"`).
         *
         * Recognized real backend names:
         * - `"Windows (Win32 / Direct2D / ANGLE / Vulkan / VLC)"` -> [Windows]
         * - `"macOS (AppKit / CoreGraphics / MoltenVK / VLC)"` -> [MacOS]
         * - `"GTK4 (Libadwaita / Cairo / GtkGLArea / Vulkan / VLC)"` -> [Gtk]
         * - `"Qt6 (QWidgets / QPainter / QOpenGLWidget / QVulkanWindow / VLC)"` -> [Qt]
         * - `"swing"` -> [Swing], `"android"` -> [Android], `"ios"` -> [IOS], `"web"` -> [Web]
         */
        fun fromBackendNameOrNull(name: String?): BackendTarget? {
            val lower = name?.trim()?.lowercase().orEmpty()
            if (lower.isEmpty()) return null
            entries.firstOrNull { it.id == lower }?.let { return it }
            return when {
                lower.startsWith("windows") || lower.startsWith("win32") || lower.startsWith("mingw") -> Windows
                lower.startsWith("macos") || lower.startsWith("appkit") || lower.startsWith("cocoa") ||
                    lower.startsWith("darwin") -> MacOS
                lower.startsWith("gtk") || lower.startsWith("adwaita") || lower.startsWith("libadwaita") -> Gtk
                lower.startsWith("qt") -> Qt
                lower.startsWith("swing") || lower.startsWith("java2d") || lower.startsWith("awt") -> Swing
                lower.startsWith("android") -> Android
                lower.startsWith("ios") || lower.startsWith("uikit") -> IOS
                lower.startsWith("web") || lower.startsWith("wasm") || lower.startsWith("html") ||
                    lower.startsWith("js") || lower.startsWith("browser") -> Web
                else -> null
            }
        }

        /**
         * Resolves a [BackendTarget] from a `NativeFactory.backendName` string (see [fromBackendNameOrNull]),
         * falling back to [defaultTarget] (default [Windows]) for unrecognized names.
         */
        fun fromBackendName(name: String?, defaultTarget: BackendTarget = Windows): BackendTarget =
            fromBackendNameOrNull(name) ?: defaultTarget
    }
}
