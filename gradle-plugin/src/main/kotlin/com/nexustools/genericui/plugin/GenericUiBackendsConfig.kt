package com.nexustools.genericui.plugin

import org.gradle.api.Action

/**
 * DSL configuration block for selecting and configuring GenericUI target backends across the
 * consolidated module hierarchy:
 * - Shared system/tier modules: `:system`, `:jvm`, `:apple`, `:desktop`, `:mobile`.
 * - Native Desktop backends: `windows` (Win32 / Direct2D / ANGLE / Vulkan), `macos` / `mac`
 *   (AppKit / CoreGraphics / OpenGL ES / MoltenVK), `gtk` (GTK3/4 / Cairo / EGL / Vulkan), and
 *   `kde` (KF6 / Kirigami / QPainter / Vulkan).
 * - JVM Desktop backend: `swing` (AWT/Swing + optional LWJGL OpenGL ES & Vulkan).
 * - Mobile backends: `android`, `ios` (iPhone), and `ipados` / `ipad` (iPad).
 * - Web backend: `:web` (HTML5 DOM / Canvas2D / WebGL2 / WebGPU).
 */
open class GenericUiBackendsConfig(
    val webManifest: GenericUiWebManifestConfig = GenericUiWebManifestConfig()
) {
    var desktopEnabled: Boolean = false
    var mobileEnabled: Boolean = false
    var windowsEnabled: Boolean = false
    var macosEnabled: Boolean = false
    var gtkEnabled: Boolean = false
    var kdeEnabled: Boolean = false
    val swingConfig: SwingBackendConfig = SwingBackendConfig()
    var androidEnabled: Boolean = false
    var iosEnabled: Boolean = false
    var ipadosEnabled: Boolean = false
    var webEnabled: Boolean = false
    internal var onChanged: (() -> Unit)? = null

    /** Explicitly enables or disables the shared `:desktop` module (`windows`, `macos`, `gtk`, `kde`, `swing` include it automatically). */
    fun desktop(enabled: Boolean = true) {
        desktopEnabled = enabled
        onChanged?.invoke()
    }

    /** Explicitly enables or disables the shared `:mobile` module (`android`, `ios`, `ipados` include it automatically). */
    fun mobile(enabled: Boolean = true) {
        mobileEnabled = enabled
        onChanged?.invoke()
    }

    /** Enables or disables the native Windows (Win32 / Direct2D / ANGLE / Vulkan + `:desktop` VLC) backend. */
    fun windows(enabled: Boolean = true) {
        windowsEnabled = enabled
        onChanged?.invoke()
    }

    /** Enables or disables the native macOS (AppKit / CoreGraphics / MoltenVK + `:desktop` VLC) backend. */
    fun macos(enabled: Boolean = true) {
        macosEnabled = enabled
        onChanged?.invoke()
    }

    /** Alias for [macos]: enables or disables the native macOS backend. */
    fun mac(enabled: Boolean = true) {
        macosEnabled = enabled
        onChanged?.invoke()
    }

    /** Enables or disables the native GTK (GTK3/4 / Cairo / EGL / Vulkan + `:desktop` VLC) backend. */
    fun gtk(enabled: Boolean = true) {
        gtkEnabled = enabled
        onChanged?.invoke()
    }

    /** Enables or disables the native KDE (KF6 / Kirigami / QPainter / QVulkanWindow + `:desktop` VLC) backend. */
    fun kde(enabled: Boolean = true) {
        kdeEnabled = enabled
        onChanged?.invoke()
    }

    /** Enables the Swing (Java/JVM AWT & Swing) backend with default settings. */
    fun swing(enabled: Boolean = true) {
        swingConfig.enabled = enabled
        onChanged?.invoke()
    }

    /** Enables and configures the Swing backend (including optional `useLwjglGL` and `useVulkan`). */
    fun swing(action: Action<in SwingBackendConfig>) {
        swingConfig.enabled = true
        action.execute(swingConfig)
        onChanged?.invoke()
    }

    /** Enables or disables the Android backend (automatically includes `:mobile`, `:jvm`, `:system`). */
    fun android(enabled: Boolean = true) {
        androidEnabled = enabled
        onChanged?.invoke()
    }

    /** Enables or disables the iOS iPhone backend (UIKit / CoreGraphics / EAGL / MoltenVK; `UIDeviceFamily = 1`). */
    fun ios(enabled: Boolean = true) {
        iosEnabled = enabled
        onChanged?.invoke()
    }

    /** Enables or disables the iPadOS iPad backend (UIKit / CoreGraphics / EAGL / MoltenVK; `UIDeviceFamily = 2`). */
    fun ipados(enabled: Boolean = true) {
        ipadosEnabled = enabled
        onChanged?.invoke()
    }

    /** Alias for [ipados]: enables or disables the iPadOS iPad backend. */
    fun ipad(enabled: Boolean = true) {
        ipados(enabled)
    }

    /** Enables or disables the `:web` (HTML5 DOM / Canvas2D / WebGL2 / WebGPU) backend. */
    fun web(enabled: Boolean = true) {
        webEnabled = enabled
        onChanged?.invoke()
    }

    /**
     * Enables the `:web` backend and configures its W3C Progressive Web App (PWA) `manifest.webmanifest`
     * and standalone WebApp installation (`allowInstallAsWebApp`).
     */
    fun web(action: Action<in GenericUiWebManifestConfig>) {
        webEnabled = true
        webManifest.enabled = true
        action.execute(webManifest)
        onChanged?.invoke()
    }

    /**
     * Enables all GenericUI targets EXCEPT Swing (`windows`, `macos`, `gtk`, `kde`, `android`, `ios`, `ipados`, `web`).
     */
    fun allExceptSwing() {
        windows(true)
        macos(true)
        gtk(true)
        kde(true)
        swing(false)
        android(true)
        ios(true)
        ipados(true)
        web(true)
    }

    /**
     * Enables all native Desktop GenericUI targets EXCEPT Swing (`windows`, `macos`, `gtk`, `kde`),
     * leaving `swing`, `android`, `ios`, `ipados`, and `web` disabled.
     */
    fun allNativeDesktop() {
        windows(true)
        macos(true)
        gtk(true)
        kde(true)
        swing(false)
        android(false)
        ios(false)
        ipados(false)
        web(false)
    }

    /**
     * Alias for [allNativeDesktop]: enables all native Desktop GenericUI targets EXCEPT Swing
     * (`windows`, `macos`, `gtk`, `kde`).
     */
    fun allDesktopExceptSwing() {
        allNativeDesktop()
    }

    /**
     * Enables all Desktop GenericUI targets including Swing (`windows`, `macos`, `gtk`, `kde`, `swing`).
     */
    fun allDesktop() {
        windows(true)
        macos(true)
        gtk(true)
        kde(true)
        swing(true)
    }

    /**
     * Enables all Mobile GenericUI targets (`android`, `ios`, `ipados`).
     */
    fun allMobile() {
        android(true)
        ios(true)
        ipados(true)
    }

    /**
     * Enables all 9 GenericUI target backends (`windows`, `macos`, `gtk`, `kde`, `swing`, `android`, `ios`, `ipados`, `web`).
     */
    fun all() {
        windows(true)
        macos(true)
        gtk(true)
        kde(true)
        swing(true)
        android(true)
        ios(true)
        ipados(true)
        web(true)
    }

    /** True whenever any native desktop backend (`windows`, `macos`, `gtk`, `kde`) is enabled. */
    val requiresNativeDesktopCommonModule: Boolean
        get() = desktopEnabled || windowsEnabled || macosEnabled || gtkEnabled || kdeEnabled

    /** Alias for [requiresNativeDesktopCommonModule]. */
    val requiresSharedDesktopModule: Boolean
        get() = requiresNativeDesktopCommonModule

    /** True whenever `:desktop` is needed (native desktop or `swing`). */
    val requiresDesktopCommonModule: Boolean
        get() = requiresNativeDesktopCommonModule || swingConfig.enabled

    /** Alias for [requiresDesktopCommonModule]. */
    val requiresDesktopModule: Boolean
        get() = requiresDesktopCommonModule

    /** True whenever shared Linux code is needed (`gtk` or `kde`). */
    val requiresLinuxCommonModule: Boolean
        get() = gtkEnabled || kdeEnabled

    /** True whenever `:mobile` is needed (`android`, `ios`, or `ipados`). */
    val requiresMobileCommonModule: Boolean
        get() = mobileEnabled || androidEnabled || iosEnabled || ipadosEnabled

    /** Alias for [requiresMobileCommonModule]. */
    val requiresSharedMobileModule: Boolean
        get() = requiresMobileCommonModule

    /** Alias for [requiresMobileCommonModule]. */
    val requiresMobileModule: Boolean
        get() = requiresMobileCommonModule

    /** True whenever `:apple` is needed (`macos`, `ios`, or `ipados`). */
    val requiresAppleCommonModule: Boolean
        get() = macosEnabled || iosEnabled || ipadosEnabled

    /** Alias for [requiresAppleCommonModule]. */
    val requiresAppleModule: Boolean
        get() = requiresAppleCommonModule

    /** True whenever `:jvm` is needed (`swing` or `android`). */
    val requiresJvmCommonModule: Boolean
        get() = swingConfig.enabled || androidEnabled

    /** Alias for [requiresJvmCommonModule]. */
    val requiresJvmModule: Boolean
        get() = requiresJvmCommonModule

    /** True whenever `:system` is needed (any non-web desktop or mobile backend). */
    val requiresSystemCommonModule: Boolean
        get() = requiresDesktopCommonModule || requiresMobileCommonModule

    /** Alias for [requiresSystemCommonModule]. */
    val requiresSharedSystemModule: Boolean
        get() = requiresSystemCommonModule

    /** Alias for [requiresSystemCommonModule]. */
    val requiresSystemModule: Boolean
        get() = requiresSystemCommonModule

    /**
     * Returns the consolidated list of enabled GenericUI backend tier modules
     * (`:system`, `:jvm`, `:apple`, `:desktop`, `:mobile`, `:web`) derived from active targets.
     */
    fun enabledBackendModulePaths(): List<String> = buildList {
        if (requiresSystemModule) add(":system")
        if (requiresJvmModule) add(":jvm")
        if (requiresAppleModule) add(":apple")
        if (requiresDesktopModule) add(":desktop")
        if (requiresMobileModule) add(":mobile")
        if (webEnabled) add(":web")
    }
}
