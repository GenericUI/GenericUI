package com.nexustools.genericui.plugin

/**
 * Configuration block (`genericUi { extensions { serviceWorker { ... } } }`) for the
 * cross-platform GenericUI background Service Worker extension (`:extensions:system:serviceworker`).
 */
open class GenericUiServiceWorkerExtensionSpec {

    /**
     * When `true`, requests OS autostart / boot registration for the desktop background
     * `serviceworker` binary and emits Android `RECEIVE_BOOT_COMPLETED` permission + boot receiver.
     */
    var autostart: Boolean = false

    /**
     * Optional custom tray / status bar / foreground notification icon path or resource URI.
     * On macOS, setting [trayIcon] overrides the tray icon (`tray_icon.png` / `tray_icon.svg`)
     * rendered unwrapped (`wrapInAppleTile = false`) while keeping the main app's Dock icon intact.
     */
    var trayIcon: String? = null

    /**
     * When `true` (default), launching the desktop main executable opens the main window and ensures
     * the background `serviceworker` process is active. When `false`, launching the main executable
     * without `--open-window` or `--settings-only` ensures the singleton `serviceworker` process is
     * started and immediately exits `0` without opening a window.
     */
    var openWindowOnLaunch: Boolean = true

    /**
     * Android `<service android:foregroundServiceType="...">` value emitted when a `@MainServiceWorker`
     * applies to Android (e.g. `"specialUse"`, `"mediaPlayback"`, `"dataSync"`, `"microphone"`).
     */
    var androidForegroundServiceType: String = "specialUse"

    /**
     * Value for `<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" .../>`
     * emitted on the generated Android service when [androidForegroundServiceType] includes `"specialUse"`.
     */
    var androidSpecialUseFgsSubtype: String = "GenericUI background ServiceWorker execution"
}
