# GenericUI Platform Backends Guide

> **Root Repository**: [**GenericUI (`../README.md`)**](../README.md) | **Master Docs Index**: [**`docs/README.md`**](./README.md)  
> **Related Guides**: [Architecture (`architecture.md`)](./architecture.md) · [SourceSets (`sourcesets.md`)](./sourcesets.md) · [Fragments & Windows (`fragments-and-windows.md`)](./fragments-and-windows.md) · [Application (`application.md`)](./application.md) · [Resources (`resources.md`)](./resources.md) · [Gradle Plugin (`gradle-plugin.md`)](./gradle-plugin.md) · [Extensions (`extensions.md`)](./extensions.md)

GenericUI provides **8 Native-First Platform Backends** (`Windows`, `MacOS`, `Gtk`, `Qt`, `Swing`, `Android`, `IOS`, `Web`) organized across common foundation modules (`:common`, `:systemCommon`) and three platform submodules ([`desktop`](../desktop/README.md), [`mobile`](../mobile/README.md), and [`web`](../web/README.md)). Every backend implements `:common`'s [`NativeFactory`](../common/src/commonMain/kotlin/com/nexustools/genericui/core/NativeFactory.kt) and symmetrical `NativeImpl` peer contracts using **real OS/platform widgets**, while providing a generated launcher (`<Backend>Launcher`) that boots directly into the application's resolved `@MainAppFragment`.

---

## 1. Core & Backend Module Hierarchy

### Category A — Multi-Backend Foundation Modules

| Module Coordinate | Variant Scope | Targets | Responsibility |
| :--- | :--- | :--- | :--- |
| **`:common`** | `common` | All 8 backends | Platform-agnostic UI widgets, `NativeImpl` contracts, `IWindow`, `TitleCapabilities`, `IApplication`, `ThemeMode` (`AUTO`, `DARK`, `LIGHT`), `BackendTarget`, `AppTarget`, `IFragment`, `@MainAppFragment`, `@AppFragment`, `FragmentHostFragment`, `FragmentHostSystem`, `DockWorkspace`, `LazyList`/`LazyGrid`/`TreeView`, `FileBrowserDialog`, `PreferenceStore`, `Resource`/`ResourceID`/`ResourceResolver`, Image API (`com.nexustools.genericui.image.*`), `AbstractCanvas`, `ComponentStyle` (`getStyle()`), `ComponentBootstrap` (`getBootstrap()`), `CssCustomCursor`, and `PrecompiledStyleSheet`. |
| **`:systemCommon`** | `systemCommon` | `Windows`, `MacOS`, `Gtk`, `Qt`, `Swing`, `Android`, `IOS` | Pure-Kotlin system-target helpers (`SystemStorageKeyHelper`, filesystem/storage key sanitization, and non-browser OS utilities across all 7 installed targets including JVM Swing). |

---

### Category B — Desktop Backends ([`desktop/` Submodule](../desktop/README.md))

> [!IMPORTANT]
> **Strict Media Separation Rule**: The `:desktop` submodule (`:desktop:common`, `:desktop:nativeCommon`, `:desktop:linuxCommon`, `:desktop:windows`, `:desktop:macos`, `:desktop:gtk`, `:desktop:qt`, `:desktop:swing`) contains **ZERO VLC or media playback code**. All VLC video/audio decoding, HDR tone-mapping, and FFT audio analysis reside strictly inside [`:extensions:media`](../extensions/docs/media.md).

| Module Coordinate | Variant Scope | Native Widget Toolkit, Windowing, & Graphics Stack | Dedicated Documentation |
| :--- | :--- | :--- | :--- |
| **`:desktop:common`** | `desktopCommon` | Pure-Kotlin desktop foundation shared by all 5 desktop backends (`Windows`, `MacOS`, `Gtk`, `Qt`, `Swing`): [`DesktopFileBrowserHelper`](../desktop/common/src/commonMain/kotlin/com/nexustools/genericui/desktop/dialog/DesktopFileBrowserHelper.kt), [`DesktopLazyViewportEngine`](../desktop/common/src/commonMain/kotlin/com/nexustools/genericui/desktop/lazy/DesktopLazyViewportEngine.kt), `DesktopUiTimerNativeImpl`, and desktop dialog/system-tray/notification implementations. | [Desktop Common (`desktop/docs/common.md`)](../desktop/docs/common.md) |
| **`:desktop:nativeCommon`** | `nativeDesktopCommon` | Native C/C++/ObjC desktop foundation shared by `Windows`, `MacOS`, `Gtk`, and `Qt`: [`DesktopBaseNativeImpl`](../desktop/nativeCommon/src/commonMain/kotlin/com/nexustools/genericui/desktop/core/DesktopBaseNativeImpl.kt), [`DesktopRenderableNativeImpl`](../desktop/nativeCommon/src/commonMain/kotlin/com/nexustools/genericui/desktop/nativecommon/DesktopRenderableNativeImpl.kt), [`DesktopControlTintRenderUtil`](../desktop/nativeCommon/src/commonMain/kotlin/com/nexustools/genericui/desktop/nativecommon/DesktopControlTintRenderUtil.kt), [`DesktopConfigDirectoryHelper`](../desktop/nativeCommon/src/commonMain/kotlin/com/nexustools/genericui/desktop/system/DesktopConfigDirectoryHelper.kt), [`DesktopMultiWindowDockCoordinator`](../desktop/nativeCommon/src/commonMain/kotlin/com/nexustools/genericui/desktop/dock/DesktopMultiWindowDockCoordinator.kt), and POSIX/native widget peers. | [Desktop NativeCommon (`desktop/docs/nativeCommon.md`)](../desktop/docs/nativeCommon.md) |
| **`:desktop:linuxCommon`** | `linuxCommon` | Linux XDG desktop foundation shared by `Gtk` and `Qt`: [`LinuxXdgConfigDirectoryImpl`](../desktop/linuxCommon/src/commonMain/kotlin/com/nexustools/genericui/linux/LinuxXdgConfigDirectoryImpl.kt), [`LinuxXdgCacheDirectoryImpl`](../desktop/linuxCommon/src/commonMain/kotlin/com/nexustools/genericui/linux/LinuxXdgCacheDirectoryImpl.kt), and [`LinuxXdgPreferenceStoreImpl`](../desktop/linuxCommon/src/commonMain/kotlin/com/nexustools/genericui/linux/LinuxXdgPreferenceStoreImpl.kt). | [Desktop LinuxCommon (`desktop/docs/linuxCommon.md`)](../desktop/docs/linuxCommon.md) |
| **`:desktop:windows`** | `windows` (`mingwX64`) | `WindowsLauncher`, Win32 `HWND` controls + **`UxTheme`** (`Explorer::BUTTON` / `DarkMode_Explorer::BUTTON`, `SetWindowTheme`, `DrawThemeBackground`), 36 per-component `Windows*NativeImpl` classes + 12 type-grouped `Win32*RenderUtil` classes, `WindowsCurCodec` (`.cur` binary synthesis from PNG/SVG/`ImageBuffer` `CssCustomCursor`), single-placement custom `TitleBar` (`Win32NativeWindowHost`), embedded `.exe` Win32 `.rc`/`.res` `Resource.ResourceID` loading (`WindowsResourceResolverImpl`), Direct2D, **Google ANGLE** (`libEGL.dll` / `libGLESv2.dll` over D3D11), Vulkan `VK_KHR_win32_surface`, COM `IFileOpenDialog`/`IFileSaveDialog`, and `WindowsPreferenceStoreImpl`. | [Windows Backend (`desktop/docs/windows.md`)](../desktop/docs/windows.md) |
| **`:desktop:macos`** | `macos` (`macosArm64`, `macosX64`) | `MacosLauncher`, AppKit `NSWindow`/`NSView`, per-component `Macos*NativeImpl` + `MacosControlRenderUtil` (`NSButtonCell` offscreen sprite tinting), `MacosCursorTranslator` (Retina `@1x`/`@2x` `NSImage` + `NSCursor` synthesis from PNG/SVG/`ImageBuffer` `CssCustomCursor`), automatic top-of-screen macOS system menu bar (`NSApplication.mainMenu`), `NSBundle` `Resource.ResourceID` resolution (`MacosResourceResolverImpl`), CoreGraphics, **`MoltenVK`** (`VK_EXT_metal_surface`), `NSOpenPanel`/`NSSavePanel`, and `MacosPreferenceStoreImpl`. | [macOS Backend (`desktop/docs/macos.md`)](../desktop/docs/macos.md) |
| **`:desktop:gtk`** | `gtk` (`linuxX64`, `linuxArm64`) | `GtkLauncher`, GTK4 / Libadwaita native widgets (`GtkWindow`, `GtkHeaderBar`), per-component `Gtk*NativeImpl` + `GtkControlRenderUtil` (`GtkSnapshot` offscreen sprite tinting), `GdkCursor` custom cursor translation, `GResource` `Resource.ResourceID` resolution (`GtkResourceResolverImpl`), Cairo, EGL/OpenGL ES 3.0, Vulkan `VK_KHR_wayland_surface` / `VK_KHR_xlib_surface`, `GtkFileDialog`, and `GtkPreferenceStoreImpl` (`.bson`). | [Linux GTK4 Backend (`desktop/docs/gtk.md`)](../desktop/docs/gtk.md) |
| **`:desktop:qt`** | `qt` (`linuxX64`, `linuxArm64`) | `QtLauncher`, Qt6 `QWidget` / `QMainWindow`, per-component `Qt*NativeImpl` + `QtControlRenderUtil` (`QStyle` offscreen sprite tinting), `QCursor` custom cursor translation, `QResource` `Resource.ResourceID` resolution (`QtResourceResolverImpl`), `QPainter`, `QOpenGLWidget`, `QVulkanWindow`, `QFileDialog`, and `QtPreferenceStoreImpl` (`.bson`). | [Linux Qt6 Backend (`desktop/docs/qt.md`)](../desktop/docs/qt.md) |
| **`:desktop:swing`** | `swing` (`jvm`) | `SwingLauncher`, JVM AWT/Swing `JFrame`/`JComponent`, `Toolkit.createCustomCursor` custom cursor translation, `JFileChooser`, JAR classpath `Resource.ResourceID` resolution (`SwingResourceResolverImpl`), `SwingPreferenceStoreImpl` (`.plist` on macOS, `.bson` on Windows/Linux + `WatchService`), and optional LWJGL 3 OpenGL ES (`useLwjglGL`) & Vulkan (`useVulkan`). | [JVM Swing Backend (`desktop/docs/swing.md`)](../desktop/docs/swing.md) |

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
| **`:web`** | `web` (`wasmJs` + `js`) | `WebLauncher`, dual **Kotlin/Wasm (`wasmJs`, primary default)** + **Kotlin/JS IR (`js`, automatic fallback)** compilation with `genericui-loader.js` & SHA-1 cache busting, `WebDomElement` (`enumClassList` + `userClassList` separation), `WebStyleSheetManager` (`<link rel="stylesheet">` injection in `document.head`), `data-bs-theme="light\|dark"` theme synchronization, Bootstrap 5 splash screen (`#genericui-splash`), URL `Resource.ResourceID` translation (`WebResourceResolverImpl` $\rightarrow$ `"assets/<type>/<name>.<ext>"`), `WebFragmentHistoryBridge` (`window.location.hash` + `window.history.state`), conditional Bootstrap navbar/menubar chrome (`TitleCapabilities`), and HTML5 Canvas2D / WebGL2 / WebGPU. | [Web Backend (`web/docs/web-backend.md`)](../web/docs/web-backend.md)<br/>[Wasm/JS Loader & Splash (`web/docs/wasm-js-loader-and-splash.md`)](../web/docs/wasm-js-loader-and-splash.md) |

---

## 2. Native System Control Rendering (`BootstrapType.NONE` Default), Empty-Text Offscreen Sprite + `ThemeConfig` Tinting (`BootstrapType != NONE`), & System Backend Style Restraints

GenericUI enforces a strict architectural separation between **native OS system backends** (`Windows`, `MacOS`, `Gtk`, `Qt`, `Swing`, `Android`, `IOS`), the **browser DOM backend** (`Web`), and the **custom GPU/Canvas self-rendered engine** ([`:extensions:alien`](../extensions/docs/alien.md)):

### 2.1 `BootstrapType.NONE` (`BootstrapType.None`) Default — 100% Unmodified Native OS Controls
- **`BootstrapType.NONE` is the default** across `Button`, `ComponentBootstrap` (`component.getBootstrap()`), and `BootstrapStyleSnapshot`.
- **Native system backends NEVER apply Bootstrap CSS styling**. When `component.getBootstrap().type == BootstrapType.NONE`, every native backend renders the **100% unmodified genuine OS system control** for the active `LIGHT` or `DARK` theme:
  - **Windows (`:desktop:windows`)**: Renders authentic Win32 **`UxTheme`** controls (`uxtheme.dll` / `comctl32.dll` / `user32.dll`) via [`Win32DwmUxThemeBridge`](../desktop/windows/src/commonMain/kotlin/com/nexustools/genericui/windows/Win32DwmUxThemeBridge.kt) (`SetWindowTheme(hwnd, "Explorer", null)` / `OpenThemeData(hwnd, "Explorer::BUTTON")` in `LIGHT` mode and `SetWindowTheme(hwnd, "DarkMode_Explorer", null)` / `OpenThemeData(hwnd, "DarkMode_Explorer::BUTTON")` in `DARK` mode, painting `BP_PUSHBUTTON`, `BP_CHECKBOX`, `BP_RADIOBUTTON`, and `PBS_NORMAL`..`PBS_DEFAULTED` states via `DrawThemeBackground` and `DrawThemeTextEx`).
  - **macOS (`:desktop:macos`)**: Renders untinted AppKit `NSButton`, `NSTextField`, `NSSlider`, and `NSSwitch` controls matching the active `NSAppearance`.
  - **Linux GTK4 (`:desktop:gtk`)**: Renders native GTK4 / Adwaita widgets following `gtk-application-prefer-dark-theme`.
  - **Linux Qt6 (`:desktop:qt`)**: Renders native `QStyle` / `QPalette` widgets following `QStyleHints::colorScheme`.
  - **JVM Swing (`:desktop:swing`)**: Renders standard Look-and-Feel `JComponent` peers adapted to the active theme palette.
  - **Android (`:mobile:android`) & iOS (`:mobile:ios`)**: Render standard Material/AppCompat (`defaultNativeBackground`) and UIKit (`UIControl`) system controls without color overrides.

### 2.2 `BootstrapType != BootstrapType.NONE` — Empty-Text Offscreen Native Sprite + `ThemeConfig` Pixel Tint + Contrasting Text Overlay
When a semantic `BootstrapType` (`PRIMARY`, `SECONDARY`, `SUCCESS`, `DANGER`, `WARNING`, `INFO`, `LIGHT`, `DARK`) is explicitly set via `component.getBootstrap().type` (or `bootstrap { type = BootstrapType.DANGER }`):
1. **Empty-Text Offscreen Native Control Render**: The native OS control is rendered **with NO text** into an offscreen ARGB pixel buffer (`DrawThemeBackground` on Windows, `NSButtonCell` with `title = ""` on macOS, `gtk_widget_snapshot` with empty label on GTK4, `QStyle::drawControl` with empty `QStyleOptionButton.text` on Qt6).
2. **`ThemeConfig` Luminance-Preserving Pixel Tint**: [`DesktopControlTintRenderUtil.tintNativeControlArgbBuffer`](../desktop/nativeCommon/src/commonMain/kotlin/com/nexustools/genericui/desktop/nativecommon/DesktopControlTintRenderUtil.kt) color-tints the offscreen native control pixels using the resolved [`ThemeConfig`](../common/src/commonMain/kotlin/com/nexustools/genericui/style/theme/ThemeConfig.kt) / [`BootstrapColorPalette`](../common/src/commonMain/kotlin/com/nexustools/genericui/style/bootstrap/BootstrapColorPalette.kt) color while preserving native 3D bevels, borders, specular highlights, and hover/pressed/disabled states (`outline = true/false`).
3. **Blit & Contrasting Text Overlay**: The tinted native control sprite is blitted to the destination surface, and the contrasting text label (`BootstrapColorPalette.resolveOnFillTextColor` / `ThemeConfig`) and icon are drawn cleanly on top afterward.
4. **Mobile & Web**: On `:mobile:android` and `:mobile:ios`, `ViewCompat.setBackgroundTintList` / `UIButton.Configuration` tints the native Material/UIKit control drawable while preserving native elevation, ripples, and states; on `:web`, `WebCssStyleTranslator` and `WebDomElement.enumClassList` emit the corresponding Bootstrap class (`btn-danger`, `btn-outline-primary`, etc.) and CSS variables.

### 2.3 System Backend Typography vs. Aesthetic Style Restraints (vs. `StyledContainer`, `StyledButton`, & `:extensions:alien`)
- **Zero String-Keyed Style APIs**: `GenericUI` does not expose `setStyleProperty(String, String)` or raw CSS maps; non-web backends operate on 100% strongly-typed `ComponentStyle` fields and two 64-bit `CssProperty` bitmasks (`activePropertiesBitmaskLow` / `High`, `importantBitmaskLow` / `High`) with zero string formatting or map allocations, while `:web` translates `ComponentStyle` and `ComponentBootstrap` to DOM CSS strings via [`WebCssStyleTranslator`](../web/src/commonMain/kotlin/com/nexustools/genericui/web/WebCssStyleTranslator.kt).
- **Text / Typography Elements (`Label`, `Text`, `Heading`, `Badge`, `Blockquote`, `CodeBlock`, `MarkdownView`)**: Honor typography and text-color properties (`color` / `textColor`, `fontFamily`, `fontSize`, `fontWeight`, `fontStyle`, `lineHeight`, `letterSpacing`, `textAlign`, `textDecoration`, `textTransform`, `textOverflow`, `whiteSpace`, `wordBreak`, plus `bootstrap.textColorType`, `bootstrap.fontSize`, `bootstrap.fontWeight`, `bootstrap.textAlignment`) across all 8 backends.
- **Buttons, Inputs, Controls, & Standard Containers (`Button`, `IconButton`, `ToggleButton`, `CheckBox`, `RadioButton`, `Switch`, `Select`, `DropdownButton`, `TextField`, `TextArea`, `RangeSlider`, `ProgressBar`, `Spinner`, `TabContainer`, `Tab`, `Container`, `Card`, etc.)**: On all 7 non-web system backends, aesthetic skinning and typography overrides on `ComponentStyle` (`backgroundColor`, `background`, `color`, `accentColor`, `borderColor`, `borderWidth`, `borderStyle`, `borderRadius`, `boxShadow`, `textShadow`, `filter`, `backdropFilter`, `fontFamily`, `fontSize`, `fontWeight`, `fontStyle`) are **silently ignored**. Standard components can **ONLY** be tinted via `BootstrapType` (`bootstrap.type`, `bootstrap.backgroundType`, `bootstrap.textColorType`, `bootstrap.borderType`, `bootstrap.outline`).
- **`StyledContainer` & `StyledButton` (and [`:extensions:alien`](../extensions/docs/alien.md))**: Honor full `ComponentStyle` aesthetic and typography properties across all 8 backends.

### 2.4 Modular Per-Component `*NativeImpl` + Type-Grouped `*RenderUtil` Architecture
Every native desktop backend delegates rendering from per-component `*NativeImpl` classes (`DesktopRenderableNativeImpl`) to type-grouped `*RenderUtil` classes (`1 Class Per .kt File`):
- **`:desktop:windows`**: 36 `Windows*NativeImpl` classes (`WindowsButtonNativeImpl`, `WindowsStyledButtonNativeImpl`, `WindowsStyledContainerNativeImpl`, `WindowsTextFieldNativeImpl`, `WindowsCheckBoxNativeImpl`, `WindowsRadioButtonNativeImpl`, `WindowsSwitchNativeImpl`, `WindowsSelectNativeImpl`, `WindowsRangeSliderNativeImpl`, `WindowsProgressBarNativeImpl`, `WindowsSpinnerNativeImpl`, `WindowsCardNativeImpl`, `WindowsTabContainerNativeImpl`, `WindowsMdiContainerNativeImpl`, `WindowsMdiWindowNativeImpl`, `WindowsDockWorkspaceNativeImpl`, `WindowsTableNativeImpl`, `WindowsLazyListNativeImpl`, `WindowsLazyGridNativeImpl`, `WindowsTreeViewNativeImpl`, `WindowsCanvasNativeImpl`, etc.) + 12 `Win32*RenderUtil` classes (`Win32RenderContext`, `Win32GdiPrimitiveRenderUtil`, `Win32ActionControlRenderUtil`, `Win32FormControlRenderUtil`, `Win32FeedbackRenderUtil`, `Win32TypographyRenderUtil`, `Win32SurfaceRenderUtil`, `Win32ContainerLayoutRenderUtil`, `Win32DockAndMdiRenderUtil`, `Win32NavigationAndShellRenderUtil`, `Win32DataAndLazyRenderUtil`, `Win32CanvasAndMediaRenderUtil`).
- **`:desktop:macos`, `:desktop:gtk`, `:desktop:qt`**: `Macos*NativeImpl` + `MacosControlRenderUtil`, `Gtk*NativeImpl` + `GtkControlRenderUtil`, and `Qt*NativeImpl` + `QtControlRenderUtil`.

---

## 3. Strict User CSS Class Isolation Across All 8 Backends

Across every backend, user-added CSS classes (`addClass`, `removeClass`, `hasClass`, `getClasses()`, `getClass()`, `setClass`) are **strictly isolated** from internal `ComponentBootstrap` enum classes:

| Backend Layer | User Classes (`getClasses()` / `hasClass()`) | `ComponentBootstrap` Enum Utilities (`getBootstrap()`) |
| :--- | :--- | :--- |
| **`:web` (`WebDomElement`)** | Stored in `WebDomElement.userClassList`; returned exclusively by `getClasses()`, `getClass()`, `hasClass()`, and `WebDomElement.className`. | Stored separately in `WebDomElement.enumClassList` (`setEnumClasses`). Merged with `userClassList` **only** inside `getEffectiveDomClassName()` when syncing the browser DOM `class` attribute. |
| **Native Desktop (`DesktopBaseNativeImpl`)** | Stored in `styleSnapshot.customClasses`; returned exclusively by `getClasses()`, `getClass()`, and `hasClass()`. | Evaluated directly by the native layout/rendering engines (`BootstrapStyleSnapshot`, `resolveBootstrapWidthFraction`, `resolveSemanticBootstrapColor`, etc.). |
| **JVM Swing (`SwingBaseNativeImpl`)** | Stored in `styleSnapshot.customClasses`. | Evaluated directly by the Swing layout and painting peers. |
| **Mobile (`MobileBaseNativeImpl` / Android / iOS)** | Stored in `styleSnapshot.customClasses`. | Evaluated directly by the Android `View` and iOS `UIView` layout/styling engines. |

---

## 4. Custom Cursor Translation (`CssCustomCursor`) Across Backends

`ComponentStyle.setCursor(...)` and precompiled `.scss` `cursor: url(...) x y, fallback` rules produce a [`CssCustomCursor`](../common/src/commonMain/kotlin/com/nexustools/genericui/style/css/CssCustomCursor.kt) supporting PNG `ResourceID`s, SVG `ResourceID`s / inline SVG markup, or raw `ImageBuffer`s with `(hotspotX, hotspotY)` coordinates and a standard `CssCursor` fallback:

| Backend | Custom Cursor Translation Pipeline |
| :--- | :--- |
| **Windows (`:desktop:windows`)** | **Runtime (`WindowsCurCodec`)**: Decodes PNG or rasterizes SVG to an RGBA `ImageBuffer` and encodes a standards-compliant **Windows `.cur` binary (`ByteArray`)** (`ICONDIR` with `idType = 2`, `ICONDIRENTRY` with `wXHotSpot` and `wYHotSpot`, 40-byte `BITMAPINFOHEADER`, 32-bit bottom-up BGRA XOR bitmap + 1-bpp AND mask) and creates a cached Win32 `HCURSOR` (`CreateIconIndirect` + 32-bpp ARGB DIB section) applied during `WM_SETCURSOR`.<br/>**Build-Time (`WindowsCurResourceGenerator`)**: Pre-converts `.png`/`.svg` cursor assets into `backends/windows/cursors/<name>.cur` binaries during `compileGenericUiResources`. |
| **macOS (`:desktop:macos`)** | **`MacosCursorTranslator`**: Resolves any PNG, SVG, or `ImageBuffer` `CssCustomCursor` into cached PNG bytes for a Retina-aware `@1x`/`@2x` `NSImage` (`[NSImage initWithData:]`) paired with `[NSCursor initWithImage:nsImage hotSpot:NSMakePoint(hotspotX, hotspotY)]` applied via AppKit `resetCursorRects` / `cursorUpdate:`. |
| **Linux GTK4 (`:desktop:gtk`)** | Rasterizes `CssCustomCursor` into a `GdkTexture` and constructs `gdk_cursor_new_from_texture(texture, hotspotX, hotspotY, fallbackCursor)`. |
| **Linux Qt6 (`:desktop:qt`)** | Rasterizes `CssCustomCursor` into a `QPixmap` and constructs `QCursor(pixmap, hotspotX, hotspotY)`. |
| **JVM Swing (`:desktop:swing`)** | Converts `CssCustomCursor` into a `BufferedImage` and constructs `Toolkit.getDefaultToolkit().createCustomCursor(image, Point(hotspotX, hotspotY), name)`. |
| **Web (`:web`)** | Emits standard CSS `cursor: url("assets/drawable/<name>.png") <hotspotX> <hotspotY>, <fallback>` onto `WebDomElement.style["cursor"]`. |

---

## 5. Build-Time `.scss` Precompilation & `IApplication.addStyleSheet` / `removeStyleSheet`

1. **`ResourceType.STYLE` (`R.style.<name>` / `Resource.style.<name>`)**:
   - Every `.scss`, `.sass`, or `.css` file under `src/commonMain/resources/**` (default `src/commonMain/resources/styles`) is registered as `ResourceType.STYLE` and exposed on the generated `R.style` / `Resource.style` object.
2. **Non-Web Targets (`PrecompiledStyleSheet` via `SassKotlinPrecompiler`)**:
   - At build time, `GenericUiSassCompileTask` (`SassKotlinPrecompiler`) compiles `.scss` files into zero-overhead Kotlin `PrecompiledStyleSheet` (`CompiledStyleRule`) objects in `com.nexustools.genericui.generated.styles`, resolving `@color/...`, `@drawable/...`, `@dimen/...`, `@font/...`, and `@string/...` references directly into typed Kotlin values and `ResourceID`s.
   - Calling `app.addStyleSheet(R.style.main)` or `app.removeStyleSheet(R.style.main)` attaches or detaches the `PrecompiledStyleSheet` at the `IApplication` level and triggers style recomputation across open windows.
3. **Web Target (`.css` Bundles + `WebStyleSheetManager`)**:
   - On `:web`, `GenericUiSassCompileTask` compiles `.scss` files into `.css` stylesheets bundled into the Web distribution.
   - Calling `app.addStyleSheet(...)` / `app.removeStyleSheet(...)` delegates to [`WebStyleSheetManager`](../web/src/commonMain/kotlin/com/nexustools/genericui/web/WebStyleSheetManager.kt), which dynamically inserts or removes `<link id="genericui-stylesheet-..." rel="stylesheet" href="styles/<name>.css">` elements in `document.head`.
4. **Automatic Startup Wiring in Generated Launchers**:
   - When `styles { globalStyleSheet = "styles/main.scss" }` (or `theme { styleSheet = "styles/main.scss" }`) is configured in `build.gradle.kts`, `GenericUiGenerateLaunchersTask` and `GenericUiGenerateAndroidActivitiesTask` automatically emit `app.addStyleSheet("styles/main.scss")` in the generated startup code across all 8 backends.

---

## 6. `IApplication` Theming (`ThemeMode.AUTO`, `DARK`, `LIGHT` — Default `AUTO`)

`IApplication` (and `Application`) defaults to **`ThemeMode.AUTO`** and exposes `setThemeMode(mode: ThemeMode)`, `getThemeMode(): ThemeMode`, and `getResolvedTheme(): ThemeMode` (`ThemeMode.LIGHT` or `ThemeMode.DARK`). Every backend implements live OS dark/light theme detection:

| Backend | Native System Theme Detection & Live Application Mechanism |
| :--- | :--- |
| **Windows (`:desktop:windows`)** | Reads `HKCU\Software\Microsoft\Windows\CurrentVersion\Themes\Personalize\AppsUseLightTheme` (and `SystemUsesLightTheme`) via `Win32DwmUxThemeBridge.isSystemDarkTheme()`, listens for live `WM_SETTINGCHANGE` (`"ImmersiveColorSet"`) messages, updates `DWMWA_USE_IMMERSIVE_DARK_MODE` on all `HWND` frames, and switches child controls between `"Explorer"` and `"DarkMode_Explorer"` via `SetWindowTheme`. |
| **macOS (`:desktop:macos`)** | Inspects `NSApp.effectiveAppearance` (`NSAppearanceNameDarkAqua` vs. `NSAppearanceNameAqua`), observes KVO / distributed appearance notifications, and sets `NSApp.appearance` when explicitly overridden to `LIGHT` or `DARK`. |
| **Linux GTK4 (`:desktop:gtk`)** | Queries `GtkSettings` (`gtk-application-prefer-dark-theme`) and the FreeDesktop portal `org.freedesktop.portal.Settings` (`color-scheme`), updating `gtk-application-prefer-dark-theme` when `ThemeMode` changes. |
| **Linux Qt6 (`:desktop:qt`)** | Queries `QGuiApplication::styleHints()->colorScheme()` (`Qt::ColorScheme::Dark` vs. `Light`), listens to `colorSchemeChanged`, and updates the application `QPalette`. |
| **JVM Swing (`:desktop:swing`)** | Queries the host OS dark-mode detector (`AppsUseLightTheme` on Windows, `defaults read -g AppleInterfaceStyle` on macOS, XDG/GTK settings on Linux) and updates the Swing Look-and-Feel palette and window decorations. |
| **Android (`:mobile:android`)** | Reads `resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK` (`UI_MODE_NIGHT_YES` vs. `UI_MODE_NIGHT_NO`), listens to `onConfigurationChanged`, and applies `AppCompatDelegate.setDefaultNightMode(...)`. |
| **iOS (`:mobile:ios`)** | Reads `UITraitCollection.currentTraitCollection.userInterfaceStyle` (`UIUserInterfaceStyleDark` vs. `UIUserInterfaceStyleLight`), observes `traitCollectionDidChange:`, and updates `UIWindow.overrideUserInterfaceStyle`. |
| **Web (`:web`)** | Queries `window.matchMedia("(prefers-color-scheme: dark)").matches`, registers a media-query `change` listener for `ThemeMode.AUTO`, and synchronizes `document.documentElement.setAttribute("data-bs-theme", "light" \| "dark")` so Bootstrap 5.3 color modes update immediately. |

---

## 7. Per-Backend Native Implementation Matrix: Async `Readable`/`Writable`, `IFileSystem`, `Database`, `Network`, `HttpClient`, `SocketIoClient`, & `isOffline`

All 8 backends implement `:common`'s 100% asynchronous, non-blocking coroutine I/O, storage, database, networking, and offline state contracts ([`docs/utils.md`](./utils.md)):

| Backend | Async `Readable` / `Writable` & `FileSystem` (`IFileSystem`) | `ConfigDirectory` & `CacheDirectory` (`UserDirectory : IFileSystem`) | `Database` (Zero-Parsing Structured AST) | `Network` (`TcpClient` & `UdpClient`) | `HttpClient` | `WebSocket` & `SocketIoClient` | `IApplication.isOffline` & Offline Support |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Windows (`:desktop:windows`)** | Win32 `CreateFileW` (`FILE_FLAG_OVERLAPPED`) + async I/O worker pool (`FileSystemAvailability.Always`). | `%APPDATA%\<appName>` & `%LOCALAPPDATA%\<appName>\Cache` delegating `IFileSystem` to `FileSystem`. | Native C **`libsqlite3`** (`WindowsDatabaseNativeImpl`). | WinSock2 (`WSAStartup`, non-blocking `SOCKET` TCP & `SOCK_DGRAM` UDP + multicast). | WinHTTP (`WinHttpOpen`, `WinHttpSendRequest`, `WinHttpReadData`). | WinHTTP WebSocket (`WinHttpWebSocketCompleteUpgrade`, `WinHttpWebSocketSend`/`Receive`). | WinINet `InternetGetConnectedState` / `INetworkListManager`. |
| **macOS (`:desktop:macos`)** | Grand Central Dispatch (`dispatch_io_t` / `NSFileHandle` async queues, `FileSystemAvailability.Always`). | `~/.config/<appName>` & `~/Library/Caches/<appName>` delegating `IFileSystem` to `FileSystem`. | System **`libsqlite3`** (`/usr/lib/libsqlite3.dylib`, `MacosDatabaseNativeImpl`). | Apple `Network.framework` (`NWConnection`) & BSD non-blocking `SOCK_DGRAM` UDP sockets. | Apple `NSURLSession` (`dataTaskWithRequest:` / `bytesForRequest:`). | Apple `NSURLSessionWebSocketTask` + Engine.IO v4 / Socket.IO v4. | Apple `NWPathMonitor` (`nw_path_status_satisfied`). |
| **Linux GTK4 (`:desktop:gtk`)** | GIO `GFile` (`g_file_read_async`, `g_file_replace_async`, `GInputStream`/`GOutputStream`, `FileSystemAvailability.Always`). | XDG `$XDG_CONFIG_HOME/<appName>` & `$XDG_CACHE_HOME/<appName>` delegating `IFileSystem` to `FileSystem`. | System **`libsqlite3`** (`GtkDatabaseNativeImpl`). | GIO `GSocketClient` / `GSocketConnection` (TCP/TLS) & `GSocket` (`G_SOCKET_TYPE_DATAGRAM` UDP + multicast). | `libsoup-3.0` (`SoupSession` `soup_session_send_and_read_async`) / `libcurl`. | `libsoup-3.0` `SoupWebsocketConnection` (`soup_session_websocket_connect_async`). | GIO `GNetworkMonitor` (`g_network_monitor_get_network_available`). |
| **Linux Qt6 (`:desktop:qt`)** | Qt `QFile` / `QSaveFile` + `QtConcurrent` / `QIODevice` non-blocking streams (`FileSystemAvailability.Always`). | XDG `$XDG_CONFIG_HOME/<appName>` & `$XDG_CACHE_HOME/<appName>` delegating `IFileSystem` to `FileSystem`. | **Qt 6 `QtSql`** (`QSqlDatabase` `"QSQLITE"` + parameterized `QSqlQuery`, `QtDatabaseNativeImpl`). | Qt 6 `QTcpSocket` / `QSslSocket` (TCP/TLS) & `QUdpSocket` (UDP + multicast). | Qt 6 `QNetworkAccessManager` (`QNetworkRequest` / `QNetworkReply`). | Qt 6 `QWebSocket` + Engine.IO v4 / Socket.IO v4. | Qt 6 `QNetworkInformation` (`reachability()` + `reachabilityChanged`). |
| **JVM Swing (`:desktop:swing`)** | Java NIO.2 `AsynchronousFileChannel` + `Dispatchers.IO` (`FileSystemAvailability.Always`). | Host OS config/cache directories delegating `IFileSystem` to `FileSystem`. | **`org.xerial:sqlite-jdbc`** (`jdbc:sqlite:<filePath>`, `SwingDatabaseNativeImpl`). | Java NIO `AsynchronousSocketChannel` / `SSLSocket` (TCP) & `DatagramChannel` / `MulticastSocket` (UDP). | `java.net.http.HttpClient` (`sendAsync`). | `java.net.http.WebSocket` + Engine.IO v4 / Socket.IO v4. | `java.net.NetworkInterface` active interface inspection. |
| **Android (`:mobile:android`)** | SAF `ContentResolver` (`ACTION_OPEN_DOCUMENT` / `ACTION_CREATE_DOCUMENT`, `FileSystemAvailability.WithPermission`) + app-sandbox `File`s. | `context.filesDir/<appName>` & `context.cacheDir/<appName>` delegating `IFileSystem` to `FileSystem` (`Always`). | **`androidx.sqlite:sqlite-ktx`** / `SQLiteDatabase` (`AndroidDatabaseNativeImpl`). | `java.nio.channels.SocketChannel` / `SSLSocket` (TCP) & `DatagramChannel` / `MulticastSocket` (UDP) on `Dispatchers.IO`. | `HttpURLConnection` / `OkHttp` on `Dispatchers.IO`. | `java.net.http.WebSocket` / OkHttp WebSocket + Socket.IO v4. | `ConnectivityManager` (`NetworkCapabilities.NET_CAPABILITY_INTERNET` + `NetworkCallback`). |
| **iOS (`:mobile:ios`)** | `UIDocumentPickerViewController` (`WithPermission`) + `NSFileManager` / `dispatch_io_t` sandbox files (`Always`). | `Library/Application Support/<appName>` & `Library/Caches/<appName>` delegating `IFileSystem` to `FileSystem` (`Always`). | iOS system **`libsqlite3`** (`IosDatabaseNativeImpl`). | Apple `Network.framework` (`NWConnection`) & BSD `SOCK_DGRAM` UDP sockets. | Apple `NSURLSession` (`dataTaskWithRequest:`). | Apple `NSURLSessionWebSocketTask` + Socket.IO v4. | Apple `NWPathMonitor` (`nw_path_status_satisfied`). |
| **Web (`:web`)** | Browser **File System Access API** (`showOpenFilePicker`, `showSaveFilePicker`, `showDirectoryPicker`, `WithPermission`) + `<input type="file">` fallback (**no `indexdb://` scheme**). | Browser **`window.indexedDB` directly** (`"genericui_user_dirs:<appName>"`, stores `"config"` and `"cache"`, `Always`) **without** using `Database` (`PreferenceStore` uses `localStorage`). | Browser **`window.indexedDB`** isolated in `"genericui_db:<databaseName>:<filePath>"` with `"tbl:<tableName>"` object stores (`WebDatabaseNativeImpl`). | `supportsTcp() = false`, `supportsUdp() = false`, `supportsMulticastUdp() = false`. | Browser `window.fetch()` + `ReadableStream` (`getReader()`). | Browser `window.WebSocket` + Engine.IO v4 / Socket.IO v4. | `!navigator.onLine` + `window` `"online"`/`"offline"` events + offline PWA **`manifest.webmanifest`** (`"offline_enabled": true`, `"resources": [...]`) & **`genericui-sw.js`** Service Worker cache. |

---

## 8. Selecting Backends in `build.gradle.kts`

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

## 9. Lifecycle Development & Production Build Tasks

| Platform Backend | Development Task | Production Task |
| :--- | :--- | :--- |
| **Windows (`:desktop:windows`)** | `./gradlew developmentWindows` | `./gradlew productionWindows` |
| **macOS (`:desktop:macos`)** | `./gradlew developmentMac` (`developmentMacos`) | `./gradlew productionMac` (`productionMacos`) |
| **Linux GTK4 (`:desktop:gtk`)** | `./gradlew developmentLinuxGtk` | `./gradlew productionLinuxGtk` |
| **Linux Qt6 (`:desktop:qt`)** | `./gradlew developmentLinuxQt` | `./gradlew productionLinuxQt` |
| **Java Swing (`:desktop:swing`)** | `./gradlew developmentSwing` | `./gradlew productionSwing` |
| **Android (`:mobile:android`)** | `./gradlew developmentAndroid` | `./gradlew productionAndroid` |
| **iOS (`:mobile:ios`)** | `./gradlew developmentIos` | `./gradlew productionIos` |
| **Web (`:web` Wasm + JS IR + Dist)** | `./gradlew developmentWeb` | `./gradlew productionWeb` (`packageGenericUiWebDistribution`) |
