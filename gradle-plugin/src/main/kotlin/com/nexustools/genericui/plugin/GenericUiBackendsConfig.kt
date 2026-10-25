package com.nexustools.genericui.plugin

import org.gradle.api.Action

/**
 * DSL configuration block for selecting and configuring GenericUI target backends:
 * - Native Desktop backends sharing `:desktop` (VLC video playback, HDR tone-mapping, HW/SW decoding):
 *   `:windows` (Win32 / Direct2D / ANGLE / Vulkan), `:macos` (AppKit / CoreGraphics / OpenGL ES / MoltenVK),
 *   `:gtk` (GTK3/4 / Cairo / EGL / Vulkan), and `:qt` (Qt5/6 / QPainter / QOpenGLWidget / QVulkanWindow).
 * - JVM Desktop backend: `:swing` (AWT/Swing + optional LWJGL OpenGL ES & Vulkan).
 * - Mobile backends sharing `:mobile` (adaptive layout, gestures/fling physics, PiP, media sessions, lazy pools):
 *   `:android` and `:ios`.
 * - Web backend: `:web` (HTML5 DOM / Canvas2D / WebGL2 / WebGPU).
 */
open class GenericUiBackendsConfig {
    var desktopEnabled: Boolean = false
    var mobileEnabled: Boolean = false
    var windowsEnabled: Boolean = false
    var macosEnabled: Boolean = false
    var gtkEnabled: Boolean = false
    var qtEnabled: Boolean = false
    val swingConfig: SwingBackendConfig = SwingBackendConfig()
    var androidEnabled: Boolean = false
    var iosEnabled: Boolean = false
    var webEnabled: Boolean = false

    /** Explicitly enables or disables the shared `:desktop` module (`:windows`, `:macos`, `:gtk`, `:qt` include it automatically). */
    fun desktop(enabled: Boolean = true) {
        desktopEnabled = enabled
    }

    /** Explicitly enables or disables the shared `:mobile` module (`:android` and `:ios` include it automatically). */
    fun mobile(enabled: Boolean = true) {
        mobileEnabled = enabled
    }

    /** Enables or disables the native `:windows` (Win32 / Direct2D / ANGLE / Vulkan + `:desktop` VLC) backend. */
    fun windows(enabled: Boolean = true) {
        windowsEnabled = enabled
    }

    /** Enables or disables the native `:macos` (AppKit / CoreGraphics / MoltenVK + `:desktop` VLC) backend. */
    fun macos(enabled: Boolean = true) {
        macosEnabled = enabled
    }

    /** Alias for [macos]: enables or disables the native `:macos` backend. */
    fun mac(enabled: Boolean = true) {
        macosEnabled = enabled
    }

    /** Enables or disables the native `:gtk` (GTK3/4 / Cairo / EGL / Vulkan + `:desktop` VLC) backend. */
    fun gtk(enabled: Boolean = true) {
        gtkEnabled = enabled
    }

    /** Enables or disables the native `:qt` (Qt5/6 / QPainter / QOpenGLWidget / QVulkanWindow + `:desktop` VLC) backend. */
    fun qt(enabled: Boolean = true) {
        qtEnabled = enabled
    }

    /** Enables the `:swing` (Java/JVM AWT & Swing) backend with default settings. */
    fun swing(enabled: Boolean = true) {
        swingConfig.enabled = enabled
    }

    /** Enables and configures the `:swing` backend (including optional `useLwjglGL` and `useVulkan`). */
    fun swing(action: Action<in SwingBackendConfig>) {
        swingConfig.enabled = true
        action.execute(swingConfig)
    }

    /** Enables or disables the `:android` backend (automatically includes shared `:mobile`). */
    fun android(enabled: Boolean = true) {
        androidEnabled = enabled
    }

    /** Enables or disables the `:ios` (UIKit / CoreGraphics / EAGL / MoltenVK + shared `:mobile`) backend. */
    fun ios(enabled: Boolean = true) {
        iosEnabled = enabled
    }

    /** Enables or disables the `:web` (HTML5 DOM / Canvas2D / WebGL2 / WebGPU) backend. */
    fun web(enabled: Boolean = true) {
        webEnabled = enabled
    }

    /**
     * Enables all GenericUI targets EXCEPT Swing (`windows`, `macos`, `gtk`, `qt`, `android`, `ios`, `web`).
     */
    fun allExceptSwing() {
        windows(true)
        macos(true)
        gtk(true)
        qt(true)
        swing(false)
        android(true)
        ios(true)
        web(true)
    }

    /**
     * Enables all native Desktop GenericUI targets EXCEPT Swing (`windows`, `macos`, `gtk`, `qt`),
     * leaving `swing`, `android`, `ios`, and `web` disabled.
     */
    fun allNativeDesktop() {
        windows(true)
        macos(true)
        gtk(true)
        qt(true)
        swing(false)
        android(false)
        ios(false)
        web(false)
    }

    /**
     * Alias for [allNativeDesktop]: enables all native Desktop GenericUI targets EXCEPT Swing
     * (`windows`, `macos`, `gtk`, `qt`), leaving `swing`, `android`, `ios`, and `web` disabled.
     */
    fun allDesktopExceptSwing() {
        allNativeDesktop()
    }

    /**
     * Enables all Desktop GenericUI targets including Swing (`windows`, `macos`, `gtk`, `qt`, `swing`).
     */
    fun allDesktop() {
        windows(true)
        macos(true)
        gtk(true)
        qt(true)
        swing(true)
    }

    /**
     * Enables both Mobile GenericUI targets (`android`, `ios`).
     */
    fun allMobile() {
        android(true)
        ios(true)
    }

    /**
     * Enables all 8 GenericUI target backends (`windows`, `macos`, `gtk`, `qt`, `swing`, `android`, `ios`, `web`).
     */
    fun all() {
        windows(true)
        macos(true)
        gtk(true)
        qt(true)
        swing(true)
        android(true)
        ios(true)
        web(true)
    }

    /** True whenever `:desktop` or any of the 4 native desktop backends (`:windows`, `:macos`, `:gtk`, `:qt`) is enabled. */
    val requiresSharedDesktopModule: Boolean
        get() = desktopEnabled || windowsEnabled || macosEnabled || gtkEnabled || qtEnabled

    /** True whenever `:mobile` or either mobile backend (`:android`, `:ios`) is enabled. */
    val requiresSharedMobileModule: Boolean
        get() = mobileEnabled || androidEnabled || iosEnabled

    /**
     * Returns the list of enabled GenericUI backend module paths, automatically including
     * `:desktop:shared` when `:desktop:windows`, `:desktop:macos`, `:desktop:gtk`, or `:desktop:qt` is enabled,
     * and `:mobile:shared` when `:mobile:android` or `:mobile:ios` is enabled.
     */
    fun enabledBackendModulePaths(): List<String> = buildList {
        if (requiresSharedDesktopModule) add(":desktop:shared")
        if (requiresSharedMobileModule) add(":mobile:shared")
        if (windowsEnabled) add(":desktop:windows")
        if (macosEnabled) add(":desktop:macos")
        if (gtkEnabled) add(":desktop:gtk")
        if (qtEnabled) add(":desktop:qt")
        if (swingConfig.enabled) add(":desktop:swing")
        if (androidEnabled) add(":mobile:android")
        if (iosEnabled) add(":mobile:ios")
        if (webEnabled) add(":web")
    }
}
