# GenericUI Platform Backends Guide

`GenericUI` provides **8 Native-First Platform Backends** organized across three submodules ([`desktop`](../desktop/README.md), [`mobile`](../mobile/README.md), and [`web`](../web/README.md)). Every backend implements `:common`'s [`NativeFactory`](../common/src/commonMain/kotlin/com/nexustools/genericui/core/NativeFactory.kt) and the symmetrical 17-subpackage `NativeImpl` interfaces while using **real OS/platform widgets**—never emulating standard buttons, inputs, scrollbars, or menus on a raw canvas.

---

## Navigation & Related Documentation

- **Root Repository**: [GenericUI README](../README.md) | [Master Documentation Index](README.md)
- **Architecture & SourceSets**: [Architecture Guide](architecture.md) | [Hierarchical SourceSet Setup (`sourcesets.md`)](sourcesets.md)
- **Build & Testing**: [Gradle Plugin Guide](gradle-plugin.md) | [IntelliJ IDEA Plugin Guide](idea-plugin.md) | [Testing Guide (`JestElectricRunner`)](testing.md)
- **Extensions**: [Extensions Overview](extensions.md) | [Extensions Submodule](../extensions/README.md)

---

## 1. Backend Submodules & Dedicated Documentation Index

### Category A — Desktop Backends ([`desktop/` Submodule](../desktop/README.md))

> [!IMPORTANT]
> **Strict Media Separation Rule**: The `:desktop` submodule (`:desktop:shared`, `:desktop:windows`, `:desktop:macos`, `:desktop:gtk`, `:desktop:qt`, `:desktop:swing`) contains **ZERO VLC or media playback code**. All VLC video/audio decoding, SMPTE ST 2084 PQ / HLG / Dolby Vision HDR tone-mapping, playback clock drift correction, and FFT audio analysis reside strictly inside the [`:extensions:media` extension](../extensions/docs/media.md).

| Module Coordinate | SourceSet Tier | Native Widget Toolkit & Graphics Stack | Dedicated Backend Documentation |
| :--- | :--- | :--- | :--- |
| **`:desktop:shared`** | `desktopNativeCommon`<br/>(`nativeDesktopCommon`) | Shared native desktop widget hierarchy (`DesktopBaseNativeImpl`), `DesktopLazyViewportEngine`, `DesktopFileBrowserHelper`, `DesktopConfigDirectoryHelper`, and `DesktopMultiWindowDockCoordinator`. | [Desktop Shared Docs (`desktop/docs/shared.md`)](../desktop/docs/shared.md) |
| **`:desktop:windows`** | `windows`<br/>(`windowsMain` / `windowsTest`) | Win32 `HWND` controls, Direct2D `Canvas2D`, **Google ANGLE** (`libEGL.dll` / `libGLESv2.dll` over D3D11) for `GLCanvas`, Vulkan `VK_KHR_win32_surface` for `VKCanvas`, COM `IFileOpenDialog`/`IFileSaveDialog` (`FOS_PICKFOLDERS`), and `WindowsPreferenceStoreImpl` (`%APPDATA%\<appName>\<key>.bson` via `BinaryJson`). | [Windows Backend Docs (`desktop/docs/windows.md`)](../desktop/docs/windows.md) |
| **`:desktop:macos`** | `mac`<br/>(`macMain` / `macosMain`) | AppKit `NSWindow`/`NSView`, automatic top-of-screen macOS system menu bar integration via `TitleBar`, CoreGraphics `Canvas2D`, **`MoltenVK`** (`VK_EXT_metal_surface`), `NSOpenPanel`/`NSSavePanel`, and `MacosPreferenceStoreImpl` (`~/Library/Preferences/<appName>/<key>.plist` via `BinaryPlist`). | [macOS Backend Docs (`desktop/docs/macos.md`)](../desktop/docs/macos.md) |
| **`:desktop:gtk`** | `linuxCommon` $\to$ `gtk`<br/>(`gtkMain` / `gtkTest`) | GTK4 native widgets, Cairo `Canvas2D`, EGL/OpenGL ES 3.0 `GLCanvas`, Vulkan `VK_KHR_wayland_surface` / `VK_KHR_xlib_surface`, `GtkFileDialog`, and `GtkPreferenceStoreImpl` (`$XDG_CONFIG_HOME/<appName>/<key>.bson` via `BinaryJson`). | [Linux GTK4 Backend Docs (`desktop/docs/gtk.md`)](../desktop/docs/gtk.md) |
| **`:desktop:qt`** | `linuxCommon` $\to$ `qt`<br/>(`qtMain` / `qtTest`) | Qt6 `QWidget` / `QMainWindow`, `QPainter` `Canvas2D`, `QOpenGLWidget` `GLCanvas`, `QVulkanWindow` `VKCanvas`, `QFileDialog`, and `QtPreferenceStoreImpl` (`$XDG_CONFIG_HOME/<appName>/<key>.bson` via `BinaryJson`). | [Linux Qt6 Backend Docs (`desktop/docs/qt.md`)](../desktop/docs/qt.md) |
| **`:desktop:swing`** | `desktopCommon` $\to$ `swing`<br/>(`swingMain` / `swingTest`) | JVM AWT/Swing components, `Java2D` `Canvas2D`, `JFileChooser`, `JScrollPane` lazy viewport recycling, `SwingPreferenceStoreImpl` (`.plist` `BinaryPlist` on macOS, `.bson` `BinaryJson` on Windows/Linux + `WatchService`), and **optional** LWJGL 3 OpenGL ES (`useLwjglGL`) & Vulkan (`useVulkan`). | [JVM Swing Backend Docs (`desktop/docs/swing.md`)](../desktop/docs/swing.md) |

---

### Category B — Mobile Backends ([`mobile/` Submodule](../mobile/README.md))

| Module Coordinate | SourceSet Tier | Native Widget Toolkit & Graphics Stack | Dedicated Backend Documentation |
| :--- | :--- | :--- | :--- |
| **`:mobile:shared`** | `mobileCommon`<br/>(`mobileMain` / `mobileTest`) | Shared form-factor breakpoint detection (`MobileDeviceFormFactor`), gesture recognizer (`MobileGestureRecognizer`), fling spline physics, Picture-in-Picture (`MobilePipController`), document picker bridge, and `MobileLazyViewportPool`. | [Mobile Shared Docs (`mobile/docs/shared.md`)](../mobile/docs/shared.md) |
| **`:mobile:android`** | `android`<br/>(`androidMain` / `androidTest`) | `GenericUiFragmentActivity` (1:1 Android `Activity` lifecycle), Material 3 widgets, `RecyclerView` lazy windowing, `Intent.ACTION_OPEN_DOCUMENT` / `ACTION_OPEN_DOCUMENT_TREE` system file/folder browser, `AndroidPreferenceStoreImpl` (`SharedPreferences`), and native Vulkan 1.1+ / EGL GLES 3.0+. | [Android Backend Docs (`mobile/docs/android.md`)](../mobile/docs/android.md) |
| **`:mobile:ios`** | `ios`<br/>(`iosMain` / `iosTest`) | UIKit `UIViewController` / `UIScene`, `CADisplayLink` VBlank loop with `deltaMs`, `UIDocumentPickerViewController` file/folder browser, `IosPreferenceStoreImpl` (`~/Library/Preferences/<appName>/<key>.plist` via `BinaryPlist`), CoreGraphics `Canvas2D`, and **`MoltenVK`** Metal Vulkan + `EAGLContext`. | [iOS Backend Docs (`mobile/docs/ios.md`)](../mobile/docs/ios.md) |

---

### Category C — Web Backend ([`web/` Submodule](../web/README.md))

| Module Coordinate | SourceSet Tier | Native Widget Toolkit & Graphics Stack | Dedicated Backend Documentation |
| :--- | :--- | :--- | :--- |
| **`:web`** | `web`<br/>(`webMain` / `webTest` $\to$ `wasmJsMain`, `jsMain`) | Dual **Kotlin/Wasm (`wasmJs`, primary default)** + **Kotlin/JS IR (`js`, automatic fallback)** compilation, `genericui-loader.js` with SHA-1 cache busting, Bootstrap 5 splash screen (`#genericui-splash`, `64x64` icon) with progress bar & error stacktrace display, `.svg`-preferred `webIcon` favicon & splash icon, optional W3C/MDN `manifest.webmanifest` & installable WebApp HTML `<head>` tags (`allowInstallAsWebApp` for Android Chrome WebAPK installation), cross-backend `themeColor` (`<meta name="theme-color">`), HTML5 DOM widgets, `WebPreferenceStoreImpl` (direct `window.localStorage` keys + `storage` event listener), hidden `<input type="file" webkitdirectory>` browser, and Canvas2D / WebGL2 / WebGPU. | [Web Backend Docs (`web/docs/web-backend.md`)](../web/docs/web-backend.md)<br/>[Wasm/JS Loader, Splash, & PWA Manifest Docs (`web/docs/wasm-js-loader-and-splash.md`)](../web/docs/wasm-js-loader-and-splash.md) |

---

## 2. Selecting Backends in `build.gradle.kts`

The `genericui { backends { ... } }` block provides both individual backend toggles and presets:

```kotlin
genericui {
    backends {
        // Preset 1: Enable all 8 backends (Windows, macOS, GTK, Qt, Swing, Android, iOS, Web)
        all()

        // Preset 2: Enable all backends EXCEPT Swing (Windows, macOS, GTK, Qt, Android, iOS, Web)
        // allExceptSwing()

        // Preset 3: Enable all native desktop backends (Windows, macOS, GTK, Qt — no Swing)
        // allDesktopExceptSwing()

        // Preset 4: Enable all desktop backends including Swing
        // allDesktop()

        // Preset 5: Enable only mobile backends (Android, iOS)
        // allMobile()
    }
}
```

---

## 3. Lifecycle Development & Production Build Tasks

When `id("com.nexustools.genericui")` is applied, the plugin registers high-level Development and Production build tasks for every platform:

| Platform Backend | Development Task | Production Task |
| :--- | :--- | :--- |
| **Windows (`:desktop:windows`)** | `./gradlew windowsDevelopment` | `./gradlew windowsProduction` |
| **macOS (`:desktop:macos`)** | `./gradlew macDevelopment` (`macosDevelopment`) | `./gradlew macProduction` (`macosProduction`) |
| **Linux GTK4 (`:desktop:gtk`)** | `./gradlew linuxDevelopmentGtk` | `./gradlew linuxProductionGtk` |
| **Linux Qt6 (`:desktop:qt`)** | `./gradlew linuxDevelopmentQt` | `./gradlew linuxProductionQt` |
| **Java Swing (`:desktop:swing`)** | `./gradlew swingDevelopment` | `./gradlew swingProduction` |
| **Android (`:mobile:android`)** | `./gradlew androidDevelopment` | `./gradlew androidProduction` |
| **iOS (`:mobile:ios`)** | `./gradlew iosDevelopment` | `./gradlew iosProduction` |
| **Web (`:web` Wasm + JS IR + Dist)** | `./gradlew webDevelopment` | `./gradlew webProduction` (`packageGenericUiWebDistribution`) |
