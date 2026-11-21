# GenericUI Platform Backends Guide

> **Root Repository**: [**GenericUI (`../README.md`)**](../README.md) | **Master Docs Index**: [**`docs/README.md`**](./README.md)  
> **Related Guides**: [Shared JVM (`jvm.md`)](./jvm.md) · [Shared Apple (`apple.md`)](./apple.md) · [iPadOS Backend (`ipados.md`)](./ipados.md) · [Architecture (`architecture.md`)](./architecture.md) · [SourceSets (`sourcesets.md`)](./sourcesets.md) · [Fragments & Windows (`fragments-and-windows.md`)](./fragments-and-windows.md) · [Application (`application.md`)](./application.md) · [Resources (`resources.md`)](./resources.md) · [Gradle Plugin (`gradle-plugin.md`)](./gradle-plugin.md) · [Extensions (`extensions.md`)](./extensions.md)

GenericUI provides **9 Native-First Platform Backends** (`Windows`, `MacOS`, `Gtk`, `Qt`, `Swing`, `Android`, `IOS`, `IPadOS`, `Web`) organized across consolidated core foundation modules (`:common`, `:system`, `:jvm`, `:apple`) and three platform modules ([`desktop`](../desktop/README.md), [`mobile`](../mobile/README.md), and [`web`](../web/README.md)). Every backend implements `:common`'s [`NativeFactory`](../common/src/commonMain/kotlin/com/nexustools/genericui/core/NativeFactory.kt) and symmetrical `NativeImpl` peer contracts using **real OS/platform widgets**, while a generated launcher (`<Backend>Launcher`) boots directly into the resolved `@MainAppFragment` (or headless worker entry point when configured).

---

## 1. Consolidated Core & Backend Module Hierarchy

### Category A — Multi-Backend Foundation Modules

| Module Coordinate | Variant Scope | Targets | Responsibility |
| :--- | :--- | :--- | :--- |
| **`:common`** | `common` | All 9 backends | Platform-agnostic UI widgets, `NativeImpl` contracts, `IWindow` (`isAlwaysOnTop`, windowed client `aspectRatio`), `TitleCapabilities`, `AppleIconTileComposer`, `IApplication`, `ThemeMode` (`AUTO`, `DARK`, `LIGHT`), `BackendTarget`, `AppTarget`, `IFragment`, `@MainAppFragment`, `@AppFragment`, `FragmentHostFragment`, `FragmentHostSystem`, `DockWorkspace`, `LazyList`/`LazyGrid`/`TreeView`, `FileBrowserDialog`, `PreferenceStore`, `Resource`/`ResourceID`/`ResourceResolver` (prefix-free `nativeResourceKey` & `nativeGroupLookupSymbol`), [`FastBackdropBlurKernel`](../common/src/commonMain/kotlin/com/nexustools/genericui/style/css/FastBackdropBlurKernel.kt), Image API (`com.nexustools.genericui.image.*`), `AbstractCanvas`, `ComponentStyle`, `ComponentBootstrap`, `CssCustomCursor`, and `PrecompiledStyleSheet`. |
| **`:system`** | `systemCommon` | `Windows`, `MacOS`, `Gtk`, `Qt`, `Swing`, `Android`, `IOS`, `IPadOS` | All 8 non-web OS targets: Blink-inspired CSS matcher & layout engine ([`BlinkStyleEngine`](../system/src/commonMain/kotlin/com/nexustools/genericui/system/style/BlinkStyleEngine.kt), [`BrowserLayoutEngine`](../system/src/commonMain/kotlin/com/nexustools/genericui/style/layout/BrowserLayoutEngine.kt)) plus pure-Kotlin storage key sanitization ([`SystemStorageKeyHelper`](../system/src/commonMain/kotlin/com/nexustools/genericui/system/SystemStorageKeyHelper.kt)). |
| **`:jvm`** | `jvmCommon` | `Swing`, `Android` | Shared Java/JDK 11 module ([`jvm.md`](./jvm.md)): `JvmIoExecutor`, `JvmUiThreadDispatcherBase`, [`JniLibraryLoader`](../jvm/src/jvmSharedMain/kotlin/com/nexustools/genericui/jvm/JniLibraryLoader.kt) (`System.loadLibrary` + Swing `META-INF/native/<os>-<arch>/` extraction), `JvmFileSystemBase`, `JvmDatabaseBase`, `JvmResourceResolverBase`, `JvmTcpSocketNativeImpl`, `JvmUdpSocketNativeImpl`, `JvmHttpUrlConnectionClient`, `JvmRfc6455WebSocketNativeImpl`, and `JvmControlTintPixelUtil`. |
| **`:apple`** | `appleCommon` | `MacOS`, `IOS`, `IPadOS` | Shared Darwin Kotlin/Native module ([`apple.md`](./apple.md)): `CoreFoundation`, `Foundation`, `CoreGraphics` (`AppleSvgCoreGraphicsRasterizer`), `ImageIO` (`AppleImageIoCodecNativeImpl`), `Security` TLS SNI, `Network` (`NWConnection`, `NWPathMonitor`), `UniformTypeIdentifiers`, `UserNotifications`, `AppleSqliteBindings`, `AppleDatabaseNativeImpl`, `AppleFileSystemBase`, `AppleResourceResolverNativeImpl`, and `ApplePreferenceStoreNativeImpl`. |

---

### Category B — Desktop Backends ([`desktop/` Module](../desktop/README.md))

> [!IMPORTANT]
> **Strict Media Separation Rule**: The `:desktop` module contains **ZERO VLC or media playback code**. All VLC video/audio decoding, HDR tone-mapping, and FFT audio analysis reside strictly inside [`:extensions:media:media`](../extensions/docs/media.md).

| `:desktop` Source Set | Backend Target | Native Widget Toolkit, Windowing, Hardware Blur, Resources, & ServiceWorker Stack |
| :--- | :--- | :--- |
| **`commonMain`** | `desktopCommon` (all 5) | Shared pure-Kotlin desktop helpers: [`DesktopFileBrowserHelper`](../desktop/src/commonMain/kotlin/com/nexustools/genericui/desktop/dialog/DesktopFileBrowserHelper.kt), [`DesktopLazyViewportEngine`](../desktop/src/commonMain/kotlin/com/nexustools/genericui/desktop/lazy/DesktopLazyViewportEngine.kt), and desktop dialog/tray/notification foundations. |
| **`nativeDesktopMain`** | `nativeDesktopCommon` (`Windows`, `MacOS`, `Gtk`, `Qt`) | Native C/C++/ObjC desktop foundation: [`DesktopBaseNativeImpl`](../desktop/src/nativeDesktopMain/kotlin/com/nexustools/genericui/desktop/core/DesktopBaseNativeImpl.kt), [`DesktopRenderableNativeImpl`](../desktop/src/nativeDesktopMain/kotlin/com/nexustools/genericui/desktop/render/DesktopRenderableNativeImpl.kt), [`DesktopControlTintRenderUtil`](../desktop/src/nativeDesktopMain/kotlin/com/nexustools/genericui/desktop/render/DesktopControlTintRenderUtil.kt), `DesktopStyledContainerNativeImpl`, `DesktopStyledButtonNativeImpl`, [`DesktopConfigDirectoryHelper`](../desktop/src/nativeDesktopMain/kotlin/com/nexustools/genericui/desktop/system/DesktopConfigDirectoryHelper.kt), and [`DesktopMultiWindowDockCoordinator`](../desktop/src/nativeDesktopMain/kotlin/com/nexustools/genericui/desktop/dock/DesktopMultiWindowDockCoordinator.kt). |
| **`mingwMain`** | **`Windows`** (`mingwX64`) | `WindowsLauncher`, Win32 `HWND` controls + **`UxTheme`**, 37 per-component `Windows*NativeImpl` classes + 12 type-grouped `Win32*RenderUtil` classes, `Win32Direct2dBackdropBlurEngine` (`D3D11`/`D2D1` 60+Hz hardware Gaussian blur + stage HUD `ExpandingVolumeSlider` wave-alpha sync), `WindowsResourceResolverImpl` (prefix-free `RT_RCDATA` lookup from `<group>.dll` / `.exe`), `serviceworker.exe` (`CreateMutexW` lock, Startup `.lnk`, `WindowsHeadlessTrayLoop`), **Google ANGLE**, Vulkan `VK_KHR_win32_surface`, `libsqlite3`, WinSock2, and WinHTTP. |
| **`macosMain`** | **`MacOS`** (`macosArm64`) | `MacosLauncher`, AppKit `NSWindow`/`NSView`, `MacosMetalBackdropBlurEngine` (`Metal` `CIContext` + clamped `CIGaussianBlur` + stage HUD `ExpandingVolumeSlider` wave-alpha sync), `MacosResourceResolverImpl` (embedded Mach-O `genericui_res_lookup_*` / `genericui_res_lookup_app` symbols via `dlsym` with zero loose `gui/`/`app/` bundle files), `Contents/MacOS/serviceworker` (`flock`, `NSApplicationActivationPolicyAccessory`, `LaunchAgents` plist, unwrapped Mach-O menu-bar tray icon), Retina `!MacosNativeWindowHost.isAlreadyWrappedAppleSquircleIcon(...)` dock icon wrapping, global `NSApplication.mainMenu`, shared **`:apple`** frameworks, and **`MoltenVK`**. |
| **`linuxMain`** | **`Gtk`** & **`Qt`** (`linuxX64`, `linuxArm64`) + `linuxCommon` | Shared XDG layer ([`LinuxXdgConfigDirectoryImpl`](../desktop/src/linuxMain/kotlin/com/nexustools/genericui/linux/LinuxXdgConfigDirectoryImpl.kt), [`LinuxXdgCacheDirectoryImpl`](../desktop/src/linuxMain/kotlin/com/nexustools/genericui/linux/LinuxXdgCacheDirectoryImpl.kt), [`LinuxXdgPreferenceStoreImpl`](../desktop/src/linuxMain/kotlin/com/nexustools/genericui/linux/LinuxXdgPreferenceStoreImpl.kt)), `LinuxResourceResolverImpl` (ELF `.rodata` `genericui_res_lookup_*` / `genericui_res_lookup_app` via `dlsym`), **`GtkLauncher`** (GTK4 / Libadwaita / `Gtk4DynamicBindings` GSK blur + Cairo `FastBackdropBlurKernel` + `StatusNotifierItem` D-Bus tray), **`QtLauncher`** (Qt6 `QMainWindow` / `QStyle` / `Qt6DynamicBindings` `QGraphicsBlurEffect` + `FastBackdropBlurKernel` + `QSystemTrayIcon`), and `serviceworker` (`fcntl`/`flock` + XDG `.desktop` autostart). |
| **`jvmMain`** | **`Swing`** (`jvm`) | `SwingLauncher`, JVM AWT/Swing `JFrame`/`JComponent`, [`SwingHardwareBackdropBlurRenderer`](../desktop/src/jvmMain/kotlin/com/nexustools/genericui/swing/SwingHardwareBackdropBlurRenderer.kt) (GPU `VolatileImage` bilinear ping-pong blur + `FastBackdropBlurKernel` fallback), [`SwingControlTintRenderUtil`](../desktop/src/jvmMain/kotlin/com/nexustools/genericui/swing/SwingControlTintRenderUtil.kt), `SwingResourceResolverImpl` + `:jvm` `JniLibraryLoader` (`META-INF/native/` JAR extraction), `GeneratedSwingServiceWorkerMain` (`FileChannel.tryLock()`, `SwingSystemTrayImpl` headless wait loop), `SwingDatabaseNativeImpl`, and shared **`:jvm`** I/O & HTTP/socket stack. |

---

### Category C — Mobile Backends ([`mobile/` Module](../mobile/README.md))

| `:mobile` Source Set | Backend Target | Native Widget Toolkit, Hardware Blur, Scene Lifecycle, & ServiceWorker Stack |
| :--- | :--- | :--- |
| **`commonMain`** | `mobileCommon` (all 3) | Shared mobile posture, gesture, fling, lifecycle, and lazy pool helpers: [`MobileDeviceFormFactor`](../mobile/src/commonMain/kotlin/com/nexustools/genericui/mobile/layout/MobileDeviceFormFactor.kt), [`MobileAdaptiveFragmentCoordinator`](../mobile/src/commonMain/kotlin/com/nexustools/genericui/mobile/layout/MobileAdaptiveFragmentCoordinator.kt), [`MobileGestureRecognizer`](../mobile/src/commonMain/kotlin/com/nexustools/genericui/mobile/gesture/MobileGestureRecognizer.kt), [`MobileFlingPhysics`](../mobile/src/commonMain/kotlin/com/nexustools/genericui/mobile/gesture/MobileFlingPhysics.kt), [`MobileLifecycleBridge`](../mobile/src/commonMain/kotlin/com/nexustools/genericui/mobile/system/MobileLifecycleBridge.kt), and [`MobileLazyViewportPool`](../mobile/src/commonMain/kotlin/com/nexustools/genericui/mobile/system/MobileLazyViewportPool.kt). |
| **`androidMain`** | **`Android`** | 1:1 generated `AppCompatActivity` subclasses + `AndroidExtensionRuntimeBridge`, `AndroidHardwareBackdropBlurController` (API 31+ `RenderNode`/`RenderEffect` over `TextureView` + API 24–30/Robolectric fallback), `GeneratedAndroidServiceWorkerService` / `AndroidServiceWorkerController` (`ServiceCompat.startForeground` ongoing notification + default `"Stop"` + `extendNotificationActions`/`replaceNotificationActions`, `AndroidServiceWorkerBinder` IPC, conditional `BOOT_COMPLETED`), consumer `.so` 16KB ELF & ZIP page-size alignment, `AndroidLazyListImpl`, `AndroidUdpSocketNativeImpl` (`WifiManager.MulticastLock`), `AndroidBaseContainerNativeImpl`, `AndroidViewHandle`, `AndroidResourceResolverImpl`, shared **`:jvm`**, and Vulkan 1.1+ / EGL GLES 3.0+. |
| **`iosMain` (`ios/*`)** | **`IOS`** (`iosArm64`, `iosSimulatorArm64`; `UIDeviceFamily = 1`) | `IosLauncher`, single-window UIKit `UIWindow` / `GenericUiRootViewController`, `UikitBackdropBlurController` (`CABackdropLayer` + Metal `UIVisualEffectView` + `UIViewPropertyAnimator`), `ServiceWorkerExtension.isSupported == false` (zero simulation, `@MainServiceWorker` never instantiated), conditional `UINavigationBar`, `CADisplayLink`, shared **`:apple`** frameworks, and **`MoltenVK`**. |
| **`iosMain` (`ipados/*`)** | **`IPadOS`** (`iosArm64`, `iosSimulatorArm64`; `UIDeviceFamily = 2`) | [`IpadosLauncher`](../mobile/src/iosMain/kotlin/com/nexustools/genericui/ipados/IpadosLauncher.kt), multi-window [`IpadosSceneDelegate`](../mobile/src/iosMain/kotlin/com/nexustools/genericui/ipados/IpadosSceneDelegate.kt) (`UIWindowScene`, Stage Manager / Split View / Slide Over), [`IpadosWindowImpl`](../mobile/src/iosMain/kotlin/com/nexustools/genericui/ipados/IpadosWindowImpl.kt), shared `UikitBackdropBlurController` (`CABackdropLayer` + Metal `UIVisualEffectView`), `ServiceWorkerExtension.isSupported == false` (zero simulation), **`UIMenuSystem.mainSystem`** (`IpadosMenuBarBridge`), and [`IpadosPointerAndKeyboardInstaller`](../mobile/src/iosMain/kotlin/com/nexustools/genericui/ipados/IpadosPointerAndKeyboardInstaller.kt). See [**iPadOS Guide (`ipados.md`)**](./ipados.md). |

---

### Category D — Web Backend ([`web/` Module](../web/README.md))

| Module Coordinate | Variant Scope | Browser DOM, Loader, Service Worker, & Graphics Stack | Dedicated Documentation |
| :--- | :--- | :--- | :--- |
| **`:web`** | `web` (`wasmJs` + `js`) | `WebLauncher`, dual **Kotlin/Wasm (`wasmJs`, primary default)** + **Kotlin/JS IR (`js`, fallback)** with **`loader.js`** (post-preload `./serviceworker.js` registration + `genericui-offline-<buildHash>` `CacheStorage` seeding + `${appname}.sw.wasm` / `${appname}.sw.js` dual worker bundles), single-copy `gui/` / `gui/ext/<extcat>/<ext>/` / `app/` layout, `WebBackdropFilterUtil` CSS `backdrop-filter` compositor promotion, `WebWorkerGlobalBridge` (`self.GenericUiServiceWorker`) & `WebServiceWorkerClientBridge`, `WebDomElement` (`enumClassList` + `userClassList`), `WebStyleSheetManager`, `#genericui-splash`, `WebFragmentHistoryBridge`, and Canvas2D / WebGL2 / WebGPU. | [Web Backend (`../web/docs/web-backend.md`)](../web/docs/web-backend.md)<br/>[Wasm/JS Loader (`../web/docs/wasm-js-loader-and-splash.md`)](../web/docs/wasm-js-loader-and-splash.md) |

---

## 2. 60+Hz Hardware `backdrop-filter` (`StyledButton` & `StyledContainer`, Excluding `TextBubble`)

When an app attaches CSS `backdropFilter` (e.g., `blur(...)`, `saturate(...)`, `brightness(...)`) to [`StyledButton`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/action/StyledButton.kt) or [`StyledContainer`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/layout/StyledContainer.kt) — including live stage-control HUD overlays — all 9 backends execute hardware-accelerated behind-the-surface sampling at 60+Hz (while [`TextBubble`](../extensions/docs/aichat.md) explicitly strips `backdropFilter` via `isTextBubbleComponent()` -> `withoutBackdropFilter()`):

| Backend | Hardware Blur Pipeline & Live Video / Stage HUD Support |
| :--- | :--- |
| **Web (`:web`)** | `WebBackdropFilterUtil.applyToElement` sets CSS `backdrop-filter` / `-webkit-backdrop-filter` and promotes GPU compositor layers (`transform: translateZ(0)`, `data-backdrop-compositor-promoted`). |
| **Android (`:mobile`)** | `AndroidHardwareBackdropBlurController` walks up to the nearest backdrop root (`android.R.id.content` or outermost host above `DecorView`), renders strictly pre-order preceding branches (respecting `layerZIndex`), captures live `TextureView` surfaces via `SurfaceTextureListener` (`onSurfaceTextureUpdated` -> `postInvalidateOnAnimation()`), and applies API 31+ `RenderNode`/`RenderEffect.createBlurEffect` (with API 24–30/Robolectric downsampled `Bitmap` + `FastBackdropBlurKernel` fallback). |
| **iOS & iPadOS (`:mobile`)** | `UikitBackdropBlurController` layers a Metal-backed `UIVisualEffectView` (`CABackdropLayer`) + paused `UIViewPropertyAnimator` `fractionComplete` radius scaling + `tintOverlayView` while keeping `hostView.backgroundColor = UIColor.clearColor`. |
| **Windows (`:desktop`)** | `Win32Direct2dBackdropBlurEngine` (`d3d11.dll` + `d2d1.dll` `ID2D1DeviceContext` `CLSID_D2D1GaussianBlur` + `CLSID_D2D1ColorMatrix`, falling back to `FastBackdropBlurKernel`) invoked by `Win32NativeWindowHost.applyBackdropFilterRect`, paired with stage HUD `ExpandingVolumeSlider` (`ROLE_EXPANDING_SLIDER_PILL`) wave-alpha synchronization (`hudAlpha * resolveVolumeLayerAlpha(...)`). |
| **macOS (`:desktop`)** | `MacosMetalBackdropBlurEngine` (Metal `MTLCreateSystemDefaultDevice` + `CIContext.contextWithMTLDevice` + clamped `CIGaussianBlur` + `CIColorControls`, falling back to `FastBackdropBlurKernel`) invoked via `MacosGraphicsDrawUtil.applyBackdropFilterRoundedRect`, paired with stage HUD `ExpandingVolumeSlider` wave-alpha sync in `MacosStageOverlayRenderUtil`. |
| **Linux GTK4 (`:desktop`)** | `Gtk4DynamicBindings` (`hasHardwareGskBlur()`, `applyGskBlurNodeToCairoSurface`, `gtk_snapshot_push_blur` / `gsk_blur_node_new`) paired with `cairoApplyBackdropFilterRegion` (`FastBackdropBlurKernel` over Cairo `ARGB32`/`RGB24`). |
| **Linux Qt6 (`:desktop`)** | `QtControlRenderUtil.applyBackdropFilterRegion` (`FastBackdropBlurKernel` over `QtRenderContext` `QImage(Format_ARGB32)`) and `Qt6DynamicBindings` (`QGraphicsBlurEffect` with `PerformanceHint`). |
| **JVM Swing (`:desktop`)** | `SwingHardwareBackdropBlurRenderer` walks to `JRootPane`/outermost `Container`, renders pre-order preceding siblings in reverse Z-order, and runs GPU `VolatileImage` bilinear downsample/upscale ping-pong (`1/2 -> 1/4 -> 1/8`), falling back to `BufferedImage` + `FastBackdropBlurKernel` in headless mode or when pixel color ops are active. |

---

## 3. Embedded Native Resources, `JniLibraryLoader`, & `ServiceWorker` Matrix

- **Prefix-Free Embedded Resources (`ResourceID`)**: `ResourceID.nativeResourceKey` strips `"gui/"`, `"gui/ext/<cat>/<ext>/"`, and `"app/"` so native backends resolve prefix-free keys without copying loose files beside desktop executables:
  - **Windows**: `WindowsResourceResolverImpl` reads Win32 `RT_RCDATA` resources directly from `<nativeLibraryGroupId>.dll` or the host `.exe` (`FindResourceW`/`LoadResource`/`LockResource`), falling back to legacy numeric resource IDs (`2000..65535`).
  - **macOS**: `MacosResourceResolverImpl` resolves Mach-O `__TEXT,__const` lookup functions (`genericui_res_lookup_<group>` / `genericui_res_lookup_app`) via `dlsym` inside the signed Mach-O binary or `Contents/Frameworks/lib<group>.dylib`; `.app/Contents/Resources/` contains only `icon.icns` (and optional `MoltenVK_icd.json`) with zero loose `gui/` or `app/` folders.
  - **Linux (`Gtk` & `Qt`)**: `LinuxResourceResolverImpl` resolves ELF `.rodata` `genericui_res_lookup_*` / `genericui_res_lookup_app` symbols via `dlsym` inside the running ELF executable or `lib<group>.so`.
  - **JVM (`Swing` & `Android`)**: [`JniLibraryLoader`](../jvm/src/jvmSharedMain/kotlin/com/nexustools/genericui/jvm/JniLibraryLoader.kt) tries `System.loadLibrary(...)` first (Android `lib/<abi>/` with 16KB ELF/ZIP alignment & `java.library.path`) and extracts `META-INF/native/<os>-<arch>/` native libraries from Swing JARs on demand.
- **`ServiceWorker`, Tray, Autostart, & `openWindowOnLaunch = false`**:
  - **Desktop (`Windows`, `MacOS`, `Gtk`, `Qt`)**: Dedicated secondary executable `serviceworker.exe` / `serviceworker` (`Contents/MacOS/serviceworker` on macOS) with OS singleton locks (`CreateMutexW`, `flock`, `fcntl`), loopback `127.0.0.1:0` IPC + 32-byte hex auth token, OS autostart (`Startup/*.lnk`, `LaunchAgents/*.plist`, XDG `autostart/*.desktop`), and headless tray event loops (`WindowsHeadlessTrayLoop`, macOS `NSApplicationActivationPolicyAccessory` + unwrapped embedded tray icon, GTK `StatusNotifierItem`, Qt `QSystemTrayIcon`).
  - **Swing (`jvm`)**: `GeneratedSwingServiceWorkerMain` compiled into the same Swing JAR using `FileChannel.tryLock()` + `SwingSystemTrayImpl` headless loop wait.
  - **Android**: `AndroidExtensionRuntimeBridge`, `GeneratedAndroidServiceWorkerService`, `AndroidServiceWorkerController`, `ServiceCompat.startForeground` ongoing notification (default `"Stop"` button + `updateNotificationText` + `extendNotificationActions` / `replaceNotificationActions`), `AndroidServiceWorkerBinder` IPC, and conditional `BOOT_COMPLETED` receiver when `autostart = true`.
  - **iOS & iPadOS**: `ServiceWorkerExtension.isSupported == false` (`AppleMobileServiceWorkerRuntime`) — zero simulation, `@MainServiceWorker` never instantiated.
  - **Web**: Unified root `./serviceworker.js` registered by `loader.js` after resource preload completes, single-copy `gui/`/`gui/ext/<extcat>/<ext>/`/`app/`, `self.GenericUiServiceWorker` (`WebWorkerGlobalBridge` / `WebServiceWorkerClientBridge`), and dual `${appname}.sw.wasm` + `${appname}.sw.js` worker bundles.

---

## 4. Native System Control Rendering (`BootstrapType.NONE` Default) & Style Restraints

1. **`BootstrapType.NONE` Default**: Native system backends render **100% unmodified genuine OS system controls** (`Win32 UxTheme`, AppKit `NSControl`, GTK4 Adwaita, Qt6 `QStyle`, Swing system L&F, Material/AppCompat, UIKit `UIControl`).
2. **`BootstrapType != BootstrapType.NONE`**: Native controls render **without text** into an offscreen ARGB buffer, receive luminance-preserving tinting via `ThemeConfig` / `BootstrapColorPalette` (`DesktopControlTintRenderUtil`, `JvmControlTintPixelUtil`, `IosControlTintRenderUtil`), blit onto the surface, and draw contrasting text and icon overlays afterward.
3. **Typography vs. Aesthetic Style Restraints**:
   - **Text elements** (`Label`, `Text`, `Heading`, `Badge`, `Blockquote`, `CodeBlock`, `MarkdownView`) honor typography and color styles on all 9 backends.
   - **Standard controls & containers** (`Button`, `CheckBox`, `TextField`, `Select`, `Container`, `Card`, etc.) silently ignore aesthetic skinning (`backgroundColor`, `borderRadius`, `boxShadow`) on all 8 system backends—use `BootstrapType` for semantic tinting, or `StyledContainer` / `StyledButton` / `:extensions:ui:alien` for full custom chrome (including 60+Hz `backdrop-filter`).

---

## 5. Strict User CSS Class Isolation, `CssCustomCursor`, & Precompiled `.scss`

- **User CSS Classes**: User classes (`addClass`, `removeClass`, `hasClass`, `getClasses()`, `getClass()`, `setClass`) remain strictly isolated from `ComponentBootstrap` enums (`WebDomElement.userClassList` vs. `enumClassList` on `:web`; `styleSnapshot.customClasses` on system backends).
- **`CssCustomCursor`**: Translates PNG/SVG `ResourceID`s, inline SVG, and `ImageBuffer`s with `(hotspotX, hotspotY)` into Windows `.cur` (`WindowsCurCodec`), macOS Retina `@1x`/`@2x` `NSCursor` (`MacosCursorTranslator`), iPadOS `UIPointerInteraction` (`IpadosPointerAndKeyboardInstaller`), GTK4 `GdkCursor`, Qt6 `QCursor`, Swing `Toolkit.createCustomCursor`, and CSS `url(...)`.
- **`PrecompiledStyleSheet`**: `.scss` assets compile at build time (`compileGenericUiSass`) into zero-parse `Resource.style.<name>` (`PrecompiledStyleSheet`) on all 8 system backends and `.css` bundles (`WebStyleSheetManager`) on `:web`.

---

## 6. Selecting Backends & Registered Build Tasks

```kotlin
genericui {
    backends {
        all() // Enables all 9 backends: windows, macos, gtk, qt, swing, android, ios, ipados, web
        // allExceptSwing(), allDesktop(), allDesktopExceptSwing(), allMobile(), ios(), ipados()
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

Native desktop builds link shared libraries per layer (`libgenericui.{dylib,so}` / `genericui.dll`, `libgenericui-{macos,gtk,qt}.*` / `genericui-windows.dll`, and enabled `genericui-ext-*` / `njs` / `genericui-tests`) staged in `<App>.app/Contents/Frameworks/` on macOS and flat alongside the executables (`main` + optional `serviceworker`) on Linux and Windows; Swing JARs, Android APKs (16KB page-aligned `.so` payloads), iOS/iPadOS frameworks, and Web bundles remain integrated in their application packages.
