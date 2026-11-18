# GenericUI Platform Backends Guide

> **Root Repository**: [**GenericUI (`../README.md`)**](../README.md) | **Master Docs Index**: [**`docs/README.md`**](./README.md)  
> **Related Guides**: [Shared JVM (`jvm.md`)](./jvm.md) · [Shared Apple (`apple.md`)](./apple.md) · [iPadOS Backend (`ipados.md`)](./ipados.md) · [Architecture (`architecture.md`)](./architecture.md) · [SourceSets (`sourcesets.md`)](./sourcesets.md) · [Fragments & Windows (`fragments-and-windows.md`)](./fragments-and-windows.md) · [Application (`application.md`)](./application.md) · [Resources (`resources.md`)](./resources.md) · [Gradle Plugin (`gradle-plugin.md`)](./gradle-plugin.md) · [Extensions (`extensions.md`)](./extensions.md)

GenericUI provides **9 Native-First Platform Backends** (`Windows`, `MacOS`, `Gtk`, `Qt`, `Swing`, `Android`, `IOS`, `IPadOS`, `Web`) organized cleanly across consolidated core foundation modules (`:common`, `:system`, `:jvm`, `:apple`) and three platform modules ([`desktop`](../desktop/README.md), [`mobile`](../mobile/README.md), and [`web`](../web/README.md)). Every backend implements `:common`'s [`NativeFactory`](../common/src/commonMain/kotlin/com/nexustools/genericui/core/NativeFactory.kt) and symmetrical `NativeImpl` peer contracts using **real OS/platform widgets**, while a generated launcher (`<Backend>Launcher`) boots directly into the resolved `@MainAppFragment`.

---

## 1. Consolidated Core & Backend Module Hierarchy

### Category A — Multi-Backend Foundation Modules

| Module Coordinate | Variant Scope | Targets | Responsibility |
| :--- | :--- | :--- | :--- |
| **`:common`** | `common` | All 9 backends | Platform-agnostic UI widgets, `NativeImpl` contracts, `IWindow` (`isAlwaysOnTop`, windowed client `aspectRatio`), `TitleCapabilities`, `AppleIconTileComposer`, `IApplication`, `ThemeMode` (`AUTO`, `DARK`, `LIGHT`), `BackendTarget`, `AppTarget`, `IFragment`, `@MainAppFragment`, `@AppFragment`, `FragmentHostFragment`, `FragmentHostSystem`, `DockWorkspace`, `LazyList`/`LazyGrid`/`TreeView`, `FileBrowserDialog`, `PreferenceStore`, `Resource`/`ResourceID`/`ResourceResolver`, Image API (`com.nexustools.genericui.image.*`), `AbstractCanvas`, `ComponentStyle` (`getStyle()`), `ComponentBootstrap` (`getBootstrap()`), `CssCustomCursor`, and `PrecompiledStyleSheet`. |
| **`:system`** | `systemCommon` | `Windows`, `MacOS`, `Gtk`, `Qt`, `Swing`, `Android`, `IOS`, `IPadOS` | All 8 non-web OS targets: Blink-inspired CSS matcher & layout engine ([`BlinkStyleEngine`](../system/src/commonMain/kotlin/com/nexustools/genericui/system/style/BlinkStyleEngine.kt), [`BrowserLayoutEngine`](../system/src/commonMain/kotlin/com/nexustools/genericui/style/layout/BrowserLayoutEngine.kt)) plus pure-Kotlin storage key sanitization ([`SystemStorageKeyHelper`](../system/src/commonMain/kotlin/com/nexustools/genericui/system/SystemStorageKeyHelper.kt)). |
| **`:jvm`** | `jvmCommon` | `Swing`, `Android` | Shared Java/JDK 11 module ([`jvm.md`](./jvm.md)): `JvmIoExecutor`, `JvmUiThreadDispatcherBase`, `JvmFileSystemBase`, `JvmDatabaseBase`, `JvmPreferenceStoreBase`, `JvmResourceResolverBase`, `JvmTcpSocketNativeImpl`, `JvmUdpSocketNativeImpl`, `JvmHttpUrlConnectionClient`, `JvmRfc6455WebSocketNativeImpl`, and `JvmControlTintPixelUtil`. |
| **`:apple`** | `appleCommon` | `MacOS`, `IOS`, `IPadOS` | Shared Darwin Kotlin/Native module ([`apple.md`](./apple.md)): `CoreFoundation`, `Foundation`, `CoreGraphics` (`AppleSvgCoreGraphicsRasterizer`), `ImageIO` (`AppleImageIoCodecNativeImpl`), `Security` TLS SNI, `Network` (`NWConnection`, `NWPathMonitor`), `UniformTypeIdentifiers`, `UserNotifications`, `AppleSqliteBindings`, `AppleDatabaseNativeImpl`, `AppleFileSystemBase`, and `ApplePreferenceStoreNativeImpl`. |

---

### Category B — Desktop Backends ([`desktop/` Module](../desktop/README.md))

> [!IMPORTANT]
> **Strict Media Separation Rule**: The `:desktop` module contains **ZERO VLC or media playback code**. All VLC video/audio decoding, HDR tone-mapping, and FFT audio analysis reside strictly inside [`:extensions:media:media`](../extensions/docs/media.md).

| `:desktop` Source Set | Backend Target | Native Widget Toolkit, Windowing, & Graphics Stack |
| :--- | :--- | :--- |
| **`commonMain`** | `desktopCommon` (all 5) | Shared pure-Kotlin desktop helpers: [`DesktopFileBrowserHelper`](../desktop/src/commonMain/kotlin/com/nexustools/genericui/desktop/dialog/DesktopFileBrowserHelper.kt), [`DesktopLazyViewportEngine`](../desktop/src/commonMain/kotlin/com/nexustools/genericui/desktop/lazy/DesktopLazyViewportEngine.kt), and desktop dialog/tray/notification foundations. |
| **`nativeDesktopMain`** | `nativeDesktopCommon` (`Windows`, `MacOS`, `Gtk`, `Qt`) | Native C/C++/ObjC desktop foundation: [`DesktopBaseNativeImpl`](../desktop/src/nativeDesktopMain/kotlin/com/nexustools/genericui/desktop/core/DesktopBaseNativeImpl.kt), [`DesktopRenderableNativeImpl`](../desktop/src/nativeDesktopMain/kotlin/com/nexustools/genericui/desktop/render/DesktopRenderableNativeImpl.kt), [`DesktopControlTintRenderUtil`](../desktop/src/nativeDesktopMain/kotlin/com/nexustools/genericui/desktop/render/DesktopControlTintRenderUtil.kt), [`DesktopConfigDirectoryHelper`](../desktop/src/nativeDesktopMain/kotlin/com/nexustools/genericui/desktop/system/DesktopConfigDirectoryHelper.kt), and [`DesktopMultiWindowDockCoordinator`](../desktop/src/nativeDesktopMain/kotlin/com/nexustools/genericui/desktop/dock/DesktopMultiWindowDockCoordinator.kt). |
| **`mingwMain`** | **`Windows`** (`mingwX64`) | `WindowsLauncher`, Win32 `HWND` controls + **`UxTheme`** (`Explorer::BUTTON` / `DarkMode_Explorer::BUTTON`, `SetWindowTheme`, `DrawThemeBackground`), 37 per-component `Windows*NativeImpl` classes + 12 type-grouped `Win32*RenderUtil` classes, `WindowsCurCodec` (`.cur` custom cursors), embedded `.exe` Win32 `.rc`/`.res` `WindowsResourceResolverImpl`, Direct2D, **Google ANGLE** (`libEGL.dll` / `libGLESv2.dll` over D3D11), Vulkan `VK_KHR_win32_surface`, COM `IFileOpenDialog`/`IFileSaveDialog`, `libsqlite3`, WinSock2, and WinHTTP. |
| **`macosMain`** | **`MacOS`** (`macosArm64`) | `MacosLauncher`, AppKit `NSWindow`/`NSView`, `Macos*NativeImpl` + `MacosControlRenderUtil` (`NSButtonCell` tinting), `MacosCursorTranslator` (Retina `@1x`/`@2x` `NSCursor`), automatic top-of-screen global `NSApplication.mainMenu`, `NSBundle` + CoreGraphics `SVG` / `ImageIO` / `Network` / `libsqlite3` shared from **`:apple`**, runtime Chrome-style Dock icon squircle wrapping (`MacosNativeWindowHost`), and **`MoltenVK`** (`VK_EXT_metal_surface`). |
| **`linuxMain`** | **`Gtk`** & **`Qt`** (`linuxX64`, `linuxArm64`) + `linuxCommon` | Shared XDG layer ([`LinuxXdgConfigDirectoryImpl`](../desktop/src/linuxMain/kotlin/com/nexustools/genericui/linux/LinuxXdgConfigDirectoryImpl.kt), [`LinuxXdgCacheDirectoryImpl`](../desktop/src/linuxMain/kotlin/com/nexustools/genericui/linux/LinuxXdgCacheDirectoryImpl.kt), [`LinuxXdgPreferenceStoreImpl`](../desktop/src/linuxMain/kotlin/com/nexustools/genericui/linux/LinuxXdgPreferenceStoreImpl.kt), `LinuxResourceResolverImpl`), **`GtkLauncher`** (GTK4 / Libadwaita / Cairo / `GtkControlRenderUtil` / GIO / `libsoup-3.0` / `libsqlite3`), and **`QtLauncher`** (Qt6 `QMainWindow` / `QStyle` / `QtControlRenderUtil` / `QPainter` / `QVulkanWindow` / Qt `QtSql` / `QNetworkAccessManager`). |
| **`jvmMain`** | **`Swing`** (`jvm`) | `SwingLauncher`, JVM AWT/Swing `JFrame`/`JComponent`, [`SwingControlTintRenderUtil`](../desktop/src/jvmMain/kotlin/com/nexustools/genericui/swing/SwingControlTintRenderUtil.kt) (`JvmControlTintPixelUtil`), JAR `SwingResourceResolverImpl` (`JvmResourceResolverBase`), `JFileChooser`, `SwingDatabaseNativeImpl` (`sqlite-jdbc`), `SwingPreferenceStoreImpl` (`.plist`/`.bson` + `WatchService`), optional LWJGL 3 OpenGL ES & Vulkan, and shared **`:jvm`** I/O & HTTP/socket stack. |

---

### Category C — Mobile Backends ([`mobile/` Module](../mobile/README.md))

| `:mobile` Source Set | Backend Target | Native Widget Toolkit, Scene Lifecycle, & Stack |
| :--- | :--- | :--- |
| **`commonMain`** | `mobileCommon` (all 3) | Shared mobile posture, gesture, fling, PiP, and lazy pool helpers: [`MobileDeviceFormFactor`](../mobile/src/commonMain/kotlin/com/nexustools/genericui/mobile/layout/MobileDeviceFormFactor.kt), [`MobileAdaptiveFragmentCoordinator`](../mobile/src/commonMain/kotlin/com/nexustools/genericui/mobile/layout/MobileAdaptiveFragmentCoordinator.kt), [`MobileGestureRecognizer`](../mobile/src/commonMain/kotlin/com/nexustools/genericui/mobile/gesture/MobileGestureRecognizer.kt), [`MobileFlingPhysics`](../mobile/src/commonMain/kotlin/com/nexustools/genericui/mobile/gesture/MobileFlingPhysics.kt), [`MobilePipController`](../mobile/src/commonMain/kotlin/com/nexustools/genericui/mobile/system/MobilePipController.kt), and [`MobileLazyViewportPool`](../mobile/src/commonMain/kotlin/com/nexustools/genericui/mobile/system/MobileLazyViewportPool.kt). |
| **`androidMain`** | **`Android`** | 1:1 generated `AppCompatActivity` subclasses (`<FragmentName>Activity : GenericUiFragmentActivity()`) for every `@MainAppFragment` and `@AppFragment`, generated `AndroidManifest.xml` & `AndroidActivityRegistry`, conditional `MaterialToolbar` navbar, Android `R.*` [`AndroidResourceResolverImpl`](../mobile/src/androidMain/kotlin/com/nexustools/genericui/android/AndroidResourceResolverImpl.kt), [`AndroidControlTintRenderUtil`](../mobile/src/androidMain/kotlin/com/nexustools/genericui/android/AndroidControlTintRenderUtil.kt) + shared **`:jvm`** networking/streams/SQLite/preferences, `RecyclerView`, SAF picker, and Vulkan 1.1+ / EGL GLES 3.0+. |
| **`iosMain` (`ios/*`)** | **`IOS`** (`iosArm64`, `iosSimulatorArm64`; `UIDeviceFamily = 1`) | `IosLauncher`, single-window UIKit `UIWindow` / `GenericUiRootViewController`, conditional `UINavigationBar`, `CADisplayLink` VBlank loop, `UIDocumentPickerViewController`, shared **`:apple`** `NSBundle`/CoreGraphics/ImageIO/`Network`/`libsqlite3`/`bplist00` preferences, and **`MoltenVK`** Metal Vulkan + `EAGLContext`. |
| **`iosMain` (`ipados/*`)** | **`IPadOS`** (`iosArm64`, `iosSimulatorArm64`; `UIDeviceFamily = 2`) | [`IpadosLauncher`](../mobile/src/iosMain/kotlin/com/nexustools/genericui/ipados/IpadosLauncher.kt), multi-window [`IpadosSceneDelegate`](../mobile/src/iosMain/kotlin/com/nexustools/genericui/ipados/IpadosSceneDelegate.kt) (`UIWindowScene`, Stage Manager / Split View / Slide Over), [`IpadosWindowImpl`](../mobile/src/iosMain/kotlin/com/nexustools/genericui/ipados/IpadosWindowImpl.kt) (windowed aspect-ratio clamping, always-on-top window levels), **`UIMenuSystem.mainSystem`** (`IpadosMenuBarBridge`), and [`IpadosPointerAndKeyboardInstaller`](../mobile/src/iosMain/kotlin/com/nexustools/genericui/ipados/IpadosPointerAndKeyboardInstaller.kt). See [**iPadOS Guide (`ipados.md`)**](./ipados.md). |

---

### Category D — Web Backend ([`web/` Module](../web/README.md))

| Module Coordinate | Variant Scope | Browser DOM, Loader, History Bridge, & Graphics Stack | Dedicated Documentation |
| :--- | :--- | :--- | :--- |
| **`:web`** | `web` (`wasmJs` + `js`) | `WebLauncher`, dual **Kotlin/Wasm (`wasmJs`, primary default)** + **Kotlin/JS IR (`js`, automatic fallback)** compilation with `genericui-loader.js` & SHA-1 cache busting, `WebDomElement` (`enumClassList` + `userClassList` separation), `WebStyleSheetManager` (`<link rel="stylesheet">` in `<head>`), `data-bs-theme="light\|dark"` theme synchronization, Bootstrap 5 splash screen (`#genericui-splash`), `WebResourceResolverImpl`, `WebFragmentHistoryBridge` (`#hash` + `window.history.state`), conditional Bootstrap navbar/menubar chrome (`TitleCapabilities`), and HTML5 Canvas2D / WebGL2 / WebGPU. | [Web Backend (`../web/docs/web-backend.md`)](../web/docs/web-backend.md)<br/>[Wasm/JS Loader (`../web/docs/wasm-js-loader-and-splash.md`)](../web/docs/wasm-js-loader-and-splash.md) |

---

## 2. Native System Control Rendering (`BootstrapType.NONE` Default), Offscreen Tinting (`BootstrapType != NONE`), & Style Restraints

GenericUI enforces a strict separation between **native OS system backends** (`Windows`, `MacOS`, `Gtk`, `Qt`, `Swing`, `Android`, `IOS`, `IPadOS`), the **browser DOM backend** (`Web`), and the **custom GPU/Canvas self-rendered engine** ([`:extensions:ui:alien`](../extensions/docs/alien.md)):

1. **`BootstrapType.NONE` Default**: Native system backends render **100% unmodified genuine OS system controls** (`Win32 UxTheme` `Explorer` / `DarkMode_Explorer`, AppKit `NSControl`, GTK4 Adwaita, Qt6 `QStyle`, Swing system L&F, Material/AppCompat, UIKit `UIControl`).
2. **`BootstrapType != BootstrapType.NONE`**: Native controls render **without text** into an offscreen ARGB pixel buffer, receive luminance-preserving tinting via `ThemeConfig` / `BootstrapColorPalette` (`DesktopControlTintRenderUtil`, `JvmControlTintPixelUtil`, `IosControlTintRenderUtil`), blit onto the surface, and draw contrasting text and icon overlays on top.
3. **Typography vs. Aesthetic Style Restraints**:
   - **Text elements** (`Label`, `Text`, `Heading`, `Badge`, `Blockquote`, `CodeBlock`, `MarkdownView`) honor typography and color styles on all 9 backends.
   - **Standard controls & containers** (`Button`, `CheckBox`, `TextField`, `Select`, `Container`, `Card`, etc.) silently ignore aesthetic skinning (`backgroundColor`, `borderRadius`, `boxShadow`) on all 8 system backends—use `BootstrapType` for semantic tinting, or [`StyledContainer`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/layout/StyledContainer.kt) / [`StyledButton`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/action/StyledButton.kt) / `:extensions:ui:alien` for full custom chrome.

---

## 3. Strict User CSS Class Isolation Across All 9 Backends

User-added CSS classes (`addClass`, `removeClass`, `hasClass`, `getClasses()`, `getClass()`, `setClass`) are strictly isolated from internal `ComponentBootstrap` enums (`WebDomElement.userClassList` vs. `enumClassList` on `:web`; `styleSnapshot.customClasses` on native desktop, Swing, and mobile).

---

## 4. Custom Cursor Translation (`CssCustomCursor`) & Precompiled `.scss` Stylesheets

- **`CssCustomCursor`**: Translates PNG/SVG `ResourceID`s, inline SVG, and `ImageBuffer`s with `(hotspotX, hotspotY)` into Windows `.cur` (`WindowsCurCodec`), macOS Retina `@1x`/`@2x` `NSCursor` (`MacosCursorTranslator`), iPadOS `UIPointerInteraction` (`IpadosPointerAndKeyboardInstaller`), GTK4 `GdkCursor`, Qt6 `QCursor`, Swing `Toolkit.createCustomCursor`, and CSS `url(...)`.
- **`PrecompiledStyleSheet` & `IApplication.addStyleSheet`**: `.scss` assets compile at build time (`compileGenericUiSass`) into zero-parse `Resource.style.<name>` (`PrecompiledStyleSheet`) on all 8 system backends and `.css` bundles (`WebStyleSheetManager`) on `:web`.

---

## 5. Selecting Backends & Registered Build Tasks

```kotlin
genericui {
    backends {
        all() // Enables all 9 backends: windows, macos, gtk, qt, swing, android, ios, ipados, web
        // Or presets / toggles:
        // allExceptSwing()          // 8 native/mobile/web targets (excludes Swing)
        // allDesktop()              // Windows, macOS, GTK, Qt, Swing
        // allDesktopExceptSwing()   // Windows, macOS, GTK, Qt
        // allMobile()               // Android, iOS, iPadOS
        // ios(); ipados()           // Select iPhone and/or iPad explicitly
    }
}
```

| Platform Backend | Development Task | Production Task |
| :--- | :--- | :--- |
| **Windows** | `./gradlew developmentWindows` | `./gradlew productionWindows` |
| **macOS** | `./gradlew developmentMac` (`developmentMacos`) | `./gradlew productionMac` (`productionMacos`) |
| **Linux GTK4** | `./gradlew developmentLinuxGtk` | `./gradlew productionLinuxGtk` |
| **Linux Qt6** | `./gradlew developmentLinuxQt` | `./gradlew productionLinuxQt` |
| **JVM Swing** | `./gradlew developmentSwing` | `./gradlew productionSwing` |
| **Android** | `./gradlew developmentAndroid` | `./gradlew productionAndroid` |
| **iOS (iPhone)** | `./gradlew developmentIos` | `./gradlew productionIos` |
| **iPadOS (iPad)** | `./gradlew developmentIpados` | `./gradlew productionIpados` |
| **Web (`wasmJs` + `js`)** | `./gradlew developmentWeb` | `./gradlew productionWeb` |

Native desktop builds link shared libraries per layer (`libgenericui.{dylib,so}` / `genericui.dll`, `libgenericui-{macos,gtk,qt}.*` / `genericui-windows.dll`, and enabled `genericui-ext-*` / `njs` / `genericui-tests`) staged in `<App>.app/Contents/Frameworks/` on macOS and flat alongside the executable on Linux and Windows; Swing JARs, Android APKs, iOS/iPadOS frameworks, and Web bundles remain integrated in their application packages.
