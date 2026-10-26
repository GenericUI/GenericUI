# GenericUI Platform Backends Guide

> **Root Repository**: [**GenericUI (`../README.md`)**](../README.md) | **Master Docs Index**: [**`docs/README.md`**](./README.md)  
> **Related Guides**: [Architecture (`architecture.md`)](./architecture.md) · [SourceSets (`sourcesets.md`)](./sourcesets.md) · [Fragments & Windows (`fragments-and-windows.md`)](./fragments-and-windows.md) · [Application (`application.md`)](./application.md) · [Resources (`resources.md`)](./resources.md) · [Gradle Plugin (`gradle-plugin.md`)](./gradle-plugin.md) · [Extensions (`extensions.md`)](./extensions.md)

GenericUI provides **8 Native-First Platform Backends** (`Windows`, `MacOS`, `Gtk`, `Qt`, `Swing`, `Android`, `IOS`, `Web`) organized across common foundation modules (`:common`, `:systemCommon`) and three platform submodules ([`desktop`](../desktop/README.md), [`mobile`](../mobile/README.md), and [`web`](../web/README.md)). Every backend implements `:common`'s [`NativeFactory`](../common/src/commonMain/kotlin/com/nexustools/genericui/core/NativeFactory.kt) and symmetrical `NativeImpl` peer contracts using **real OS/platform widgets**, while providing a generated launcher (`<Backend>Launcher`) that boots directly into the application's resolved `@MainAppFragment`.

---

## 1. Core & Backend Module Hierarchy

### Category A — Multi-Backend Foundation Modules

| Module Coordinate | Variant Scope | Targets | Responsibility |
| :--- | :--- | :--- | :--- |
| **`:common`** | `common` | All 8 backends | Platform-agnostic UI widgets, `NativeImpl` contracts, `IWindow`, `TitleCapabilities`, `IApplication`, `BackendTarget`, `AppTarget`, `IFragment`, `@MainAppFragment`, `@AppFragment`, `FragmentHostFragment`, `FragmentHostSystem`, `DockWorkspace`, `LazyList`/`LazyGrid`/`TreeView`, `FileBrowserDialog`, `PreferenceStore`, `Resource`/`ResourceID`/`ResourceResolver`, Image API (`com.nexustools.genericui.image.*`), `AbstractCanvas`, and Bootstrap/SASS/CSS styling. |
| **`:systemCommon`** | `systemCommon` | `Windows`, `MacOS`, `Gtk`, `Qt`, `Swing`, `Android`, `IOS` | Pure-Kotlin system-target helpers (`SystemStorageKeyHelper`, filesystem/storage key sanitization, and non-browser OS utilities across all 7 installed targets including JVM Swing). |

---

### Category B — Desktop Backends ([`desktop/` Submodule](../desktop/README.md))

> [!IMPORTANT]
> **Strict Media Separation Rule**: The `:desktop` submodule (`:desktop:common`, `:desktop:nativeCommon`, `:desktop:linuxCommon`, `:desktop:windows`, `:desktop:macos`, `:desktop:gtk`, `:desktop:qt`, `:desktop:swing`) contains **ZERO VLC or media playback code**. All VLC video/audio decoding, HDR tone-mapping, and FFT audio analysis reside strictly inside [`:extensions:media`](../extensions/docs/media.md).

| Module Coordinate | Variant Scope | Native Widget Toolkit, Windowing, & Graphics Stack | Dedicated Documentation |
| :--- | :--- | :--- | :--- |
| **`:desktop:common`** | `desktopCommon` | Pure-Kotlin desktop foundation shared by all 5 desktop backends (`Windows`, `MacOS`, `Gtk`, `Qt`, `Swing`): [`DesktopFileBrowserHelper`](../desktop/common/src/commonMain/kotlin/com/nexustools/genericui/desktop/dialog/DesktopFileBrowserHelper.kt), [`DesktopLazyViewportEngine`](../desktop/common/src/commonMain/kotlin/com/nexustools/genericui/desktop/lazy/DesktopLazyViewportEngine.kt), `DesktopUiTimerNativeImpl`, and desktop dialog/system-tray/notification implementations. | [Desktop Common (`desktop/docs/common.md`)](../desktop/docs/common.md) |
| **`:desktop:nativeCommon`** | `nativeDesktopCommon` | Native C/C++/ObjC desktop foundation shared by `Windows`, `MacOS`, `Gtk`, and `Qt`: [`DesktopBaseNativeImpl`](../desktop/nativeCommon/src/commonMain/kotlin/com/nexustools/genericui/desktop/core/DesktopBaseNativeImpl.kt), [`DesktopConfigDirectoryHelper`](../desktop/nativeCommon/src/commonMain/kotlin/com/nexustools/genericui/desktop/system/DesktopConfigDirectoryHelper.kt), [`DesktopMultiWindowDockCoordinator`](../desktop/nativeCommon/src/commonMain/kotlin/com/nexustools/genericui/desktop/dock/DesktopMultiWindowDockCoordinator.kt), and POSIX/native widget peers. | [Desktop NativeCommon (`desktop/docs/nativeCommon.md`)](../desktop/docs/nativeCommon.md) |
| **`:desktop:linuxCommon`** | `linuxCommon` | Linux XDG desktop foundation shared by `Gtk` and `Qt`: [`LinuxXdgConfigDirectoryImpl`](../desktop/linuxCommon/src/commonMain/kotlin/com/nexustools/genericui/linux/LinuxXdgConfigDirectoryImpl.kt), [`LinuxXdgCacheDirectoryImpl`](../desktop/linuxCommon/src/commonMain/kotlin/com/nexustools/genericui/linux/LinuxXdgCacheDirectoryImpl.kt), and [`LinuxXdgPreferenceStoreImpl`](../desktop/linuxCommon/src/commonMain/kotlin/com/nexustools/genericui/linux/LinuxXdgPreferenceStoreImpl.kt). | [Desktop LinuxCommon (`desktop/docs/linuxCommon.md`)](../desktop/docs/linuxCommon.md) |
| **`:desktop:windows`** | `windows` (`mingwX64`) | `WindowsLauncher`, Win32 `HWND` controls, single-placement custom `TitleBar` (`Win32NativeWindowHost`), embedded `.exe` Win32 `.rc`/`.res` `Resource.ResourceID` loading (`WindowsResourceResolverImpl`), Direct2D, **Google ANGLE** (`libEGL.dll` / `libGLESv2.dll` over D3D11), Vulkan `VK_KHR_win32_surface`, COM `IFileOpenDialog`/`IFileSaveDialog`, and `WindowsPreferenceStoreImpl` (`%APPDATA%\<appName>\<key>.bson` via `BinaryJson`). | [Windows Backend (`desktop/docs/windows.md`)](../desktop/docs/windows.md) |
| **`:desktop:macos`** | `macos` (`macosArm64`, `macosX64`) | `MacosLauncher`, AppKit `NSWindow`/`NSView`, automatic top-of-screen macOS system menu bar (`NSApplication.mainMenu`), `NSBundle` `Resource.ResourceID` resolution (`MacosResourceResolverImpl`), CoreGraphics, **`MoltenVK`** (`VK_EXT_metal_surface`), `NSOpenPanel`/`NSSavePanel`, and `MacosPreferenceStoreImpl` (`~/Library/Preferences/<appName>/<key>.plist` via `BinaryPlist`). | [macOS Backend (`desktop/docs/macos.md`)](../desktop/docs/macos.md) |
| **`:desktop:gtk`** | `gtk` (`linuxX64`, `linuxArm64`) | `GtkLauncher`, GTK4 / Libadwaita native widgets (`GtkWindow`, `GtkHeaderBar`), `GResource` `Resource.ResourceID` resolution (`GtkResourceResolverImpl`), Cairo, EGL/OpenGL ES 3.0, Vulkan `VK_KHR_wayland_surface` / `VK_KHR_xlib_surface`, `GtkFileDialog`, and `GtkPreferenceStoreImpl` (`.bson`). | [Linux GTK4 Backend (`desktop/docs/gtk.md`)](../desktop/docs/gtk.md) |
| **`:desktop:qt`** | `qt` (`linuxX64`, `linuxArm64`) | `QtLauncher`, Qt6 `QWidget` / `QMainWindow`, `QResource` `Resource.ResourceID` resolution (`QtResourceResolverImpl`), `QPainter`, `QOpenGLWidget`, `QVulkanWindow`, `QFileDialog`, and `QtPreferenceStoreImpl` (`.bson`). | [Linux Qt6 Backend (`desktop/docs/qt.md`)](../desktop/docs/qt.md) |
| **`:desktop:swing`** | `swing` (`jvm`) | `SwingLauncher`, JVM AWT/Swing `JFrame`/`JComponent`, `JFileChooser`, JAR classpath `Resource.ResourceID` resolution (`SwingResourceResolverImpl`), `SwingPreferenceStoreImpl` (`.plist` on macOS, `.bson` on Windows/Linux + `WatchService`), and optional LWJGL 3 OpenGL ES (`useLwjglGL`) & Vulkan (`useVulkan`). | [JVM Swing Backend (`desktop/docs/swing.md`)](../desktop/docs/swing.md) |

---

### Category C — Mobile Backends ([`mobile/` Submodule](../mobile/README.md))

| Module Coordinate | Variant Scope | Native Widget Toolkit, Activities, & Graphics Stack | Dedicated Documentation |
| :--- | :--- | :--- | :--- |
| **`:mobile:common`** | `mobileCommon` | Mobile foundation shared by `Android` and `IOS`: [`MobileDeviceFormFactor`](../mobile/common/src/commonMain/kotlin/com/nexustools/genericui/mobile/layout/MobileDeviceFormFactor.kt), [`MobileAdaptiveFragmentCoordinator`](../mobile/common/src/commonMain/kotlin/com/nexustools/genericui/mobile/layout/MobileAdaptiveFragmentCoordinator.kt), [`MobileGestureRecognizer`](../mobile/common/src/commonMain/kotlin/com/nexustools/genericui/mobile/gesture/MobileGestureRecognizer.kt), [`MobileFlingPhysics`](../mobile/common/src/commonMain/kotlin/com/nexustools/genericui/mobile/gesture/MobileFlingPhysics.kt), [`MobilePipController`](../mobile/common/src/commonMain/kotlin/com/nexustools/genericui/mobile/system/MobilePipController.kt), and [`MobileLazyViewportPool`](../mobile/common/src/commonMain/kotlin/com/nexustools/genericui/mobile/system/MobileLazyViewportPool.kt). | [Mobile Common (`mobile/docs/common.md`)](../mobile/docs/common.md) |
| **`:mobile:android`** | `android` | 1:1 generated `AppCompatActivity` subclasses (`<FragmentName>Activity : GenericUiFragmentActivity()`) for every `@MainAppFragment` and `@AppFragment`, generated `AndroidManifest.xml` & `AndroidActivityRegistry`, conditional `MaterialToolbar` navbar (rendered **only** when a `TitleBar` or `MenuBar` is set), Android `R.*` `Resource.ResourceID` translation (`AndroidResourceResolverImpl`), `RecyclerView` lazy views, SAF `ACTION_OPEN_DOCUMENT` / `ACTION_OPEN_DOCUMENT_TREE`, `SharedPreferences`, and Vulkan 1.1+ / EGL GLES 3.0+. | [Android Backend (`mobile/docs/android.md`)](../mobile/docs/android.md) |
| **`:mobile:ios`** | `ios` (`iosArm64`, `iosSimulatorArm64`) | `IosLauncher`, UIKit `UIWindow` / `UIViewController`, native iOS navigation bar (rendered **only** when a `TitleBar` or `MenuBar` is set), `CADisplayLink` VBlank loop, `UIDocumentPickerViewController`, `NSBundle` `Resource.ResourceID` resolution (`IosResourceResolverImpl`), `IosPreferenceStoreImpl` (`bplist00` `.plist`), and **`MoltenVK`** Metal Vulkan + `EAGLContext`. | [iOS Backend (`mobile/docs/ios.md`)](../mobile/docs/ios.md) |

---

### Category D — Web Backend ([`web/` Submodule](../web/README.md))

| Module Coordinate | Variant Scope | Browser DOM, Loader, History Bridge, & Graphics Stack | Dedicated Documentation |
| :--- | :--- | :--- | :--- |
| **`:web`** | `web` (`wasmJs` + `js`) | `WebLauncher`, dual **Kotlin/Wasm (`wasmJs`, primary default)** + **Kotlin/JS IR (`js`, automatic fallback)** compilation with `genericui-loader.js` & SHA-1 cache busting, Bootstrap 5 splash screen (`#genericui-splash`) with `.svg`-preferred `webIcon`, URL `Resource.ResourceID` translation (`WebResourceResolverImpl` $\rightarrow$ `"assets/<type>/<name>.<ext>"`), `WebFragmentHistoryBridge` (`window.location.hash` + `window.history.state` `FragmentBundle` persistence for `FragmentHostSystem`), conditional Bootstrap navbar/menubar chrome (`TitleCapabilities`), `WebPreferenceStoreImpl` (`window.localStorage`), hidden `<input type="file" webkitdirectory>`, and HTML5 Canvas2D / WebGL2 / WebGPU. | [Web Backend (`web/docs/web-backend.md`)](../web/docs/web-backend.md)<br/>[Wasm/JS Loader & Splash (`web/docs/wasm-js-loader-and-splash.md`)](../web/docs/wasm-js-loader-and-splash.md) |

---

## 2. Selecting Backends in `build.gradle.kts`

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
