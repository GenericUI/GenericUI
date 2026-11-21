# GenericUI `:apple` Shared Darwin / CoreFoundation / Security Module Guide

> **Root Repository**: [**GenericUI (`../README.md`)**](../README.md) | **Master Docs Index**: [**`docs/README.md`**](./README.md)  
> **Related Guides**: [iPadOS Backend (`ipados.md`)](./ipados.md) · [Core Architecture (`architecture.md`)](./architecture.md) · [Platform Backends (`backends.md`)](./backends.md) · [Hierarchical SourceSets (`sourcesets.md`)](./sourcesets.md) · [Shared JVM Module (`jvm.md`)](./jvm.md) · [Gradle Plugin (`gradle-plugin.md`)](./gradle-plugin.md)

The **`:apple`** module (`com.nexustools.genericui:apple`) provides a single shared Kotlin/Native Darwin foundation for **macOS (`BackendTarget.MacOS` in `:desktop`)**, **iOS (`BackendTarget.IOS` in `:mobile`)**, and **iPadOS (`BackendTarget.IPadOS` in `:mobile`)**—isolating all `Foundation`, `CoreFoundation`, `CoreGraphics`, `ImageIO`, `Network`, `Security`, `UniformTypeIdentifiers`, `UserNotifications`, Grand Central Dispatch (`darwin`), POSIX sockets, and `libsqlite3` bindings with zero AppKit (`NSView`/`NSWindow`) or UIKit (`UIView`/`UIWindow`) coupling.

---

## 1. Gradle Targets & SourceSet Architecture

Configured in [`apple/build.gradle.kts`](../apple/build.gradle.kts):

- **Targets**: `macosArm64()`, `iosArm64()`, `iosSimulatorArm64()` with `applyDefaultHierarchyTemplate()`, creating shared `appleMain` and `appleTest` source sets.
- **Dependencies**: `api(project(":system"))` (transitively exposing `:common` and shared Blink layout/CSS engines) + `kotlinx-coroutines-core`.
- **Direct Consumers**:
  - **`:desktop` (`macosMain`)**: Reuses `:apple` directly inside `MacosNativeFactory`, `MacosFileSystemNativeImpl`, `MacosNativeWindowHost`, `MacosGraphicsDrawUtil`, `MacosSystemTrayNativeImpl`, and desktop extensions.
  - **`:mobile` (`iosMain`)**: Reuses `:apple` directly inside both `IosNativeFactory` (`BackendTarget.IOS`) and `IpadosNativeFactory` (`BackendTarget.IPadOS`), `IosApplicationImpl`, `IosFileSystemImpl`, `IosFileBrowserDialogImpl`, and mobile extensions.
  - **Apple Extension Targets**: `:extensions:graphics:{gpu,canvas2d,canvas3d}`, `:extensions:media:{media,mediacontrols}`, `:extensions:ui:webview`, `:extensions:system:serviceworker`, `:extensions:game:gamepad`, and `:extensions:game:engine`.

---

## 2. Bound Apple Frameworks & Shared Subsystems (`com.nexustools.genericui.apple`)

All classes reside under [`apple/src/appleMain/kotlin/com/nexustools/genericui/apple`](../apple/src/appleMain/kotlin/com/nexustools/genericui/apple):

| Domain | Apple Frameworks Used | Classes / Files | Responsibility |
| :--- | :--- | :--- | :--- |
| **Async File I/O, Sandbox, & UTTypes** | `Foundation` (`NSFileManager`, `NSFileHandle`), `UniformTypeIdentifiers` (`UTType`), `CoreFoundation`, `darwin` (`dispatch_data_t`) | [`AppleFileSystemBase`](../apple/src/appleMain/kotlin/com/nexustools/genericui/apple/AppleFileSystemBase.kt)<br/>[`AppleFileReadableNativeImpl`](../apple/src/appleMain/kotlin/com/nexustools/genericui/apple/AppleFileReadableNativeImpl.kt)<br/>[`AppleFileWritableNativeImpl`](../apple/src/appleMain/kotlin/com/nexustools/genericui/apple/AppleFileWritableNativeImpl.kt)<br/>[`AppleUserDirectoryNativeImpl`](../apple/src/appleMain/kotlin/com/nexustools/genericui/apple/AppleUserDirectoryNativeImpl.kt)<br/>[`AppleDataBridge`](../apple/src/appleMain/kotlin/com/nexustools/genericui/apple/AppleDataBridge.kt)<br/>[`AppleStreamState`](../apple/src/appleMain/kotlin/com/nexustools/genericui/apple/AppleStreamState.kt)<br/>[`AppleUniformTypeHelper`](../apple/src/appleMain/kotlin/com/nexustools/genericui/apple/AppleUniformTypeHelper.kt) | Base async `IFileSystem`, `Readable`/`Writable` streams, `ConfigDirectory` / `CacheDirectory` (`UserDirectory`), `ByteArray <-> NSData / CFDataRef` zero-leak bridging, and `UTType` mapping (`MacosFileSystemNativeImpl`, `MacosFileBrowserDialogNativeImpl`, `IosFileSystemImpl`, `IosFileBrowserDialogImpl`). |
| **Networking, TLS (`Security`), & Connectivity** | `Network.framework` (`nw_connection`, `nw_path_monitor`), `Security` (`sec_protocol_options_set_tls_server_name`), `Foundation` (`NSURLSession`, `NSURLSessionWebSocketTask`), POSIX BSD sockets | [`AppleTcpSocketNativeImpl`](../apple/src/appleMain/kotlin/com/nexustools/genericui/apple/AppleTcpSocketNativeImpl.kt)<br/>[`AppleUdpSocketNativeImpl`](../apple/src/appleMain/kotlin/com/nexustools/genericui/apple/AppleUdpSocketNativeImpl.kt)<br/>[`AppleHttpClientNativeImpl`](../apple/src/appleMain/kotlin/com/nexustools/genericui/apple/AppleHttpClientNativeImpl.kt)<br/>[`AppleHttpConnectionNativeImpl`](../apple/src/appleMain/kotlin/com/nexustools/genericui/apple/AppleHttpConnectionNativeImpl.kt)<br/>[`AppleHttpRequestNativeImpl`](../apple/src/appleMain/kotlin/com/nexustools/genericui/apple/AppleHttpRequestNativeImpl.kt)<br/>[`AppleHttpResponseNativeImpl`](../apple/src/appleMain/kotlin/com/nexustools/genericui/apple/AppleHttpResponseNativeImpl.kt)<br/>[`AppleWebSocketNativeImpl`](../apple/src/appleMain/kotlin/com/nexustools/genericui/apple/AppleWebSocketNativeImpl.kt)<br/>[`AppleNetworkMonitorNativeImpl`](../apple/src/appleMain/kotlin/com/nexustools/genericui/apple/AppleNetworkMonitorNativeImpl.kt) | Duplex `TcpSocket : Stream` (with `Security` TLS SNI), BSD/POSIX UDP & multicast sockets, async streaming `NSURLSession` HTTP/HTTPS client, native `NSURLSessionWebSocketTask` supporting Socket.IO v4, and live `NWPathMonitor` offline/online state tracking (`IApplication.isOffline`). |
| **SQLite Database & Binary Plist Preferences** | System `libsqlite3`, `Foundation` (`NSUserDefaults`, `NSHomeDirectory`) | [`AppleSqliteBindings`](../apple/src/appleMain/kotlin/com/nexustools/genericui/apple/AppleSqliteBindings.kt)<br/>[`AppleDatabaseNativeImpl`](../apple/src/appleMain/kotlin/com/nexustools/genericui/apple/AppleDatabaseNativeImpl.kt)<br/>[`AppleDatabaseReadableNativeImpl`](../apple/src/appleMain/kotlin/com/nexustools/genericui/apple/AppleDatabaseReadableNativeImpl.kt)<br/>[`AppleDatabaseWritableNativeImpl`](../apple/src/appleMain/kotlin/com/nexustools/genericui/apple/AppleDatabaseWritableNativeImpl.kt)<br/>[`ApplePreferenceStoreNativeImpl`](../apple/src/appleMain/kotlin/com/nexustools/genericui/apple/ApplePreferenceStoreNativeImpl.kt) | Parameterized `libsqlite3` execution for GenericUI's structured `Database` AST plus binary `bplist00` `.plist` / `NSUserDefaults`-backed `PreferenceStore` persistence across macOS, iOS, and iPadOS. |
| **ImageIO Decoding & CoreGraphics SVG Rasterization** | `ImageIO` (`CGImageSource`, `CGImageDestination`), `CoreGraphics` (`CGBitmapContext`, `CGContext`) | [`AppleImageIoCodecNativeImpl`](../apple/src/appleMain/kotlin/com/nexustools/genericui/apple/AppleImageIoCodecNativeImpl.kt)<br/>[`AppleSvgCoreGraphicsRasterizer`](../apple/src/appleMain/kotlin/com/nexustools/genericui/apple/AppleSvgCoreGraphicsRasterizer.kt)<br/>[`AppleImagePixelUtils`](../apple/src/appleMain/kotlin/com/nexustools/genericui/apple/AppleImagePixelUtils.kt) | Hardware-native PNG/JPEG/WebP/GIF/APNG/TIFF/ICO decoding/encoding (`SRGB` & `DISPLAY_P3`) plus direct SVG vector rasterization into `CGContext` RGBA buffers (used by `MacosNativeWindowHost`, `MacosGraphicsDrawUtil`, `MacosSystemTrayNativeImpl`, and UIKit image/icon loading). |
| **Threading, Timers, Resources, & Notifications** | `darwin` (`dispatch_get_main_queue`, `dispatch_source_t`, `NSRunLoop`), `Foundation` (`NSBundle`, `NSRecursiveLock`), `UserNotifications` (`UNUserNotificationCenter`) | [`AppleUiThreadDispatcherNativeImpl`](../apple/src/appleMain/kotlin/com/nexustools/genericui/apple/AppleUiThreadDispatcherNativeImpl.kt)<br/>[`AppleUiTimerNativeImpl`](../apple/src/appleMain/kotlin/com/nexustools/genericui/apple/AppleUiTimerNativeImpl.kt)<br/>[`AppleReentrantLock`](../apple/src/appleMain/kotlin/com/nexustools/genericui/apple/AppleReentrantLock.kt)<br/>[`AppleResourceResolverNativeImpl`](../apple/src/appleMain/kotlin/com/nexustools/genericui/apple/AppleResourceResolverNativeImpl.kt)<br/>[`AppleNotificationManagerNativeImpl`](../apple/src/appleMain/kotlin/com/nexustools/genericui/apple/AppleNotificationManagerNativeImpl.kt) | Main-queue UI thread vs. GCD Logic thread dispatching, high-precision `dispatch_source_t` UI timers, `NSBundle` resource loading (`Resource.ResourceID`), and `UNUserNotificationCenter` desktop/mobile alerts. |

---

## 3. Desktop (`MacOS`) vs. Mobile (`IOS` / `IPadOS`) Platform Divergences

While `:apple` shares Foundation/CoreGraphics/Network primitives uniformly, macOS and UIKit diverge cleanly in three OS-governed areas:

1. **Embedded Resources**:
   - **macOS (`MacosResourceResolverImpl`)**: Resolves prefix-free `ResourceID.nativeResourceKey` symbols (`genericui_res_lookup_<group>` / `genericui_res_lookup_app`) embedded inside signed Mach-O `__TEXT,__const` sections (main binary and `Contents/Frameworks/lib<group>.dylib`) via `dlsym` before falling back to `NSBundle.mainBundle` (`AppleResourceResolverNativeImpl`) and `Resources.activeTable`. Packaging strips all loose `gui/`, `app/`, and `res/` directories from `Contents/Resources/` (retaining only `icon.icns` and optional `vulkan/icd.d/MoltenVK_icd.json`).
   - **iOS & iPadOS (`AppleResourceResolverNativeImpl`)**: Resolves bundled resources directly via `NSBundle.mainBundle` and `Resources.activeTable`.
2. **Metal 60+Hz Hardware `backdrop-filter` (`StyledButton` & `StyledContainer`, Excluding `TextBubble`)**:
   - **macOS (`MacosMetalBackdropBlurEngine`)**: Renders behind-the-surface backdrops through Metal-backed CoreImage (`MTLCreateSystemDefaultDevice` + `CIContext.contextWithMTLDevice` + clamped `CIGaussianBlur` + `CIColorControls`), plus stage HUD `ExpandingVolumeSlider` wave-alpha synchronization in `MacosStageOverlayRenderUtil`.
   - **iOS & iPadOS (`UikitBackdropBlurController`)**: Layers a Metal-backed `UIVisualEffectView` (`CABackdropLayer`) + paused `UIViewPropertyAnimator` `fractionComplete` radius scaling + `tintOverlayView` while keeping `hostView.backgroundColor = UIColor.clearColor`.
3. **`ServiceWorkerExtension` Support**:
   - **macOS (`BackendTarget.MacOS`)**: Fully supported via a secondary signed Mach-O binary (`Contents/MacOS/serviceworker`), `flock` singleton locking, `~/Library/LaunchAgents/<appId>.serviceworker.plist` autostart, `NSApplicationActivationPolicyAccessory` headless tray operation when no main window is visible, and an unwrapped menu-bar tray icon loaded from embedded Mach-O symbols (`lookupEmbeddedAppBytes`) while skipping dock squircle re-wrapping via `!MacosNativeWindowHost.isAlreadyWrappedAppleSquircleIcon(...)`.
   - **iOS & iPadOS (`BackendTarget.IOS`, `BackendTarget.IPadOS`)**: `AppleMobileServiceWorkerRuntime` sets **`ServiceWorkerExtension.isSupported = false`** and `isAutostartSupported = false` with **zero background worker simulation** and **zero `@MainServiceWorker` instantiation**.

---

## 4. Consumer Apple Variant Scope, Shared Apple Icons, & Testing

- **Consumer Variant Scope (`AppTarget.APPLE_COMMON`, rank `2`)**:
  - Target macOS, iOS, and iPadOS together using `@MainAppFragment(targets = [AppTarget.APPLE_COMMON])`, `genericui { apple { ... } }` (`appleCommon { ... }`), or `genericui { sourceSets { appleCommon { ... } } }` (`src/appleMain/kotlin`).
  - See [Apple Icon Configuration & Chrome Squircle Auto-Wrapping (`gradle-plugin.md`)](./gradle-plugin.md#4-apple-icon-pipeline--chrome-style-squircle-wrapping) for `appleIcon`, `macIcon`, `iosIcon`, and `ipadosIcon`.
- **Consolidated `:apple` Test Suite**:
  - Five shared Darwin test suites in `apple/src/appleTest/kotlin/com/nexustools/genericui/apple/` (`AppleFileSystemAndPreferencesTest`, `AppleImageIoAndSvgTest`, `AppleSocketsAndHttpTest`, `AppleSqliteDatabaseTest`, `AppleThreadingTimerAndSystemTest`).
  - Run on Apple hosts via `./gradlew :apple:macosArm64Test :apple:iosSimulatorArm64Test`.
