# GenericUI — Agent & Engineer Architectural Guidelines (`AGENTS.md`)

This document defines the **mandatory architectural rules, file layout invariants, and runtime contracts** for AI coding agents and software engineers working in the `GenericUI` repository.

- **Related Documentation**: [GenericUI README](README.md) | [Master Documentation Index](docs/README.md) | [Shared JVM (`:jvm`)](docs/jvm.md) | [Shared Apple (`:apple`)](docs/apple.md) | [iPadOS Backend](docs/ipados.md) | [Gamepad (`:extensions:game:gamepad`)](docs/gamepad.md) | [Game Engines (`:extensions:game:engine*`)](docs/game-engines.md) | [Hybrid Layouts & Helpers](docs/layouts-and-helpers.md) | [Hierarchical SourceSet Setup](docs/sourcesets.md) | [Platform Backends](docs/backends.md) | [First-Party Extensions](docs/extensions.md) | [Gradle Plugin](docs/gradle-plugin.md) | [Fragments & Windows](docs/fragments-and-windows.md) | [Resources (`Resource.ResourceID`)](docs/resources.md) | [Application & Targets](docs/application.md) | [Testing (`JestElectricRunner`)](docs/testing.md)

---

## 1. Strict "1 Class Per `.kt` File" Rule (NON-NEGOTIABLE)

Every `.kt` source file across all consolidated core/backend modules (`:common`, `:system`, `:jvm`, `:apple`, `:desktop`, `:mobile`, `:web`) covering all **9 platform backends** (`Windows`, `MacOS`, `Gtk`, `Qt`, `Swing`, `Android`, `IOS`, `IPadOS`, `Web`), all **19 categorized first-party extension modules** (+ 16 `:dsl` submodules: `:extensions:graphics:{gpu,imageprocessing,canvas2d(+dsl),canvas3d(+dsl),threejs(+dsl)}`, `:extensions:physics:{box2d(+dsl),box3d(+dsl)}`, `:extensions:game:{gamepad(+dsl),engine(+dsl),engine2d(+dsl),engine3d(+dsl)}`, `:extensions:media:{media(+dsl),mediacontrols(+dsl)}`, `:extensions:ui:{dsl,overlay(+dsl),alien(+dsl),webview(+dsl),codeeditor(+dsl),aichat(+dsl)}`), `:tests`, `:gradle-plugin`, and `:idea-plugin` **MUST export at most ONE public `class`, `interface`, `object`, or `enum class`**.

- **Filename Match**: The filename MUST exactly match the single public type declared inside it (`<TypeName>.kt`).
- **No Bundled Enums or Data Classes**: Never place helper enums, data classes, listener interfaces, or configuration types in the same `.kt` file as another class. Extract every public type into its own dedicated `.kt` file in the appropriate domain subpackage.
- **No Unnecessary Aliases & No Backwards-Compatibility Shims (Zero Alias Hacks)**:
  - Never create redundant alias properties, methods, `typealias` declarations, or duplicate wrapper classes (e.g. adding `isOk` / `ok` alongside `isSuccessful`, `decodeToString` alongside `toString(encoding = "utf8")`, `UdpSocket` alongside `UdpClient`, `Row`/`Col` alongside `ColumnLayout`/`Column`, or duplicate wrapper getters/functions) that clutter and confuse the codebase.
  - Because `GenericUI` is a beta project actively designing its canonical API and layout architecture, **never create or retain backwards-compatibility shims, deprecated wrappers, or legacy class names when refactoring unless explicitly instructed by the user**. Always update all call sites directly to the single canonical API.
- **No Public `*NativeImpl` Exposure (Strict NativeImpl Encapsulation)**: `*NativeImpl` interfaces, classes, properties (`nativeImpl`, `webSocketNativeImpl`, `customNativeImpl`, `nativeImplOverride`, etc.), and methods (`openBodyReadableNativeImpl`, `openStreamNative`, `resolveNativeImpl`, etc.) **MUST NEVER be publicly accessible** on user-facing classes. All `*NativeImpl` references on user-facing components, streams, I/O utilities, databases, network clients, and extensions must be `internal`, `protected`, or `private`.
- **Fail-Fast Runtime Error When a Requested Native Has No `NativeImpl` on the Active Backend**:
  - If any requested component, extension widget, or native capability has no `NativeImpl` on the active backend (or if an extension's backend provider has not been registered, or if a `Window` / `Application` is launched without an active `NativeFactory` or without content), it **MUST throw an immediate runtime exception (`UnsupportedOperationException` or `IllegalStateException`)** instead of silently falling back to a no-op stub or running with no content.
- **`Readable`, `Writable`, & `Stream` Interfaces, `Network` (`openTcpSocket` / `openUdpSocket`), `TcpSocket` & `UdpSocket`, `HttpConnection` (`HttpRequest` & `HttpResponse`), Purpose-Specific `*NativeImpl`s, `ByteBuffer`, `ReadableByteBuffer`, & `WritableByteBuffer` Contract**:
  - `Readable`, `Writable`, and `Stream` (`com.nexustools.genericui.io.Readable`, `com.nexustools.genericui.io.Writable`, and `com.nexustools.genericui.io.Stream`) are **interfaces** (`interface Readable : Disposable`, `interface Writable : Disposable`, `interface Stream : Readable, Writable`) mimicking the Node.js stream API (`suspend fun read(maxBytes: Int): ByteBuffer?`, `fun onData(listener: suspend (ByteBuffer) -> Unit): Readable`, `fun write(buffer: ByteBuffer): Boolean`, `fun onWrite(listener: (ByteBuffer) -> Unit): Writable`, `suspend fun pipe(destination: Writable, ...): Long`) AND providing binary primitive read/write methods (`readByte`, `readUByte`, `readBoolean`, `readShort`, `readUShort`, `readInt`, `readUInt`, `readLong`, `readULong`, `readFloat`, `readDouble` / `writeByte`, `writeUByte`, `writeBoolean`, `writeShort`, `writeUShort`, `writeInt`, `writeUInt`, `writeLong`, `writeULong`, `writeFloat`, `writeDouble`) supporting either endianness (`ByteOrder.LITTLE_ENDIAN` default, `ByteOrder.BIG_ENDIAN`) in the same way as `ByteBuffer`. Because `Readable` and `Writable` support byte-based read/write methods directly, standalone `ByteReader` and `ByteWriter` classes are not used.
  - **`Network`, `TcpSocket : Stream`, & `UdpSocket : Stream`**: `Network` (`com.nexustools.genericui.net.Network`) provides `openTcpSocket(...)` and `openUdpSocket(...)`, and checks `GenericUI.activeFactory?.supportsTcp()`, `supportsUdp()`, and `supportsMulticastUdp()` directly (never instantiating/disposing a socket just to check support, and never checking `BackendTarget` in `:common`; each concrete `<Backend>NativeFactory` returns constant `true` or `false` directly). Because HTTP, WebSocket, and `Database` are always supported on all 9 backends, there are no `supportsHttp()`, `supportsWebSocket()`, or `Database.supports()` methods. On backends that do not support raw TCP/UDP (such as `:web`), `NativeFactory.createTcpSocket()` and `NativeFactory.createUdpSocket()` throw `UnsupportedOperationException` directly. `TcpSocket` (`com.nexustools.genericui.net.TcpSocket`) and `UdpSocket` (`com.nexustools.genericui.net.UdpSocket`) are **interfaces** extending `Stream` (`Readable` + `Writable`), backed by `TcpSocketNativeImpl` (`<Backend>TcpSocketNativeImpl`) and `UdpSocketNativeImpl` (`<Backend>UdpSocketNativeImpl`) on supported backends (never `TcpClient` or `UdpClient`).
  - **`HttpClient`, `HttpConnection`, `HttpRequest : Writable`, & `HttpResponse : Readable`**: `HttpClient` (`com.nexustools.genericui.net.http.HttpClient`) provides `request`, `get`, `post`, `put`, `patch`, `delete`, `head`, and `options` returning an `HttpConnection` (`interface HttpConnection : Stream` in `com.nexustools.genericui.net.http.HttpConnection`) that holds `val request: HttpRequest` (`interface HttpRequest : Writable`) and `val response: HttpResponse` (`interface HttpResponse : Readable`), backed by purpose-specific `HttpConnectionNativeImpl`, `HttpRequestNativeImpl`, and `HttpResponseNativeImpl`.
  - **Purpose-Specific Stream `*NativeImpl`s**: Native stream implementations MUST be specific to their purpose rather than generic `ReadableNativeImpl`/`WritableNativeImpl`: `FileReadableNativeImpl`, `FileWritableNativeImpl` (and per-backend `<Backend>FileReadableNativeImpl` / `<Backend>FileWritableNativeImpl`), `HttpConnectionNativeImpl`, `HttpRequestNativeImpl`, `HttpResponseNativeImpl`, `TcpSocketNativeImpl`, `UdpSocketNativeImpl`, `DatabaseReadableNativeImpl`, `DatabaseWritableNativeImpl`, `MemoryReadableNativeImpl`, and `MemoryWritableNativeImpl` (`ResourceResolver.openReadable(...)` returns `suspend fun ...: Readable?`).
  - `ByteBuffer` (`com.nexustools.genericui.io.ByteBuffer`) supports `fun toString(encoding: String = "utf8"): String` (never `decodeToString`) and indexed read/write for primitive types (`Byte`, `UByte`, `Boolean`, `Short`, `UShort`, `Int`, `UInt`, `Long`, `ULong`, `Float`, `Double`) with either endianness (`ByteOrder.LITTLE_ENDIAN` or `ByteOrder.BIG_ENDIAN`), defaulting to the most common endianness (`ByteOrder.LITTLE_ENDIAN`).
  - `ReadableByteBuffer` (`com.nexustools.genericui.io.ReadableByteBuffer` in `ReadableByteBuffer.kt`) takes a `ByteBuffer`, keeps track of `position`, and implements `Readable` from it (used for `openReadable` on `Resource` / `ResourceID` / `Resources` on binary/preloaded resource backends and `Readable.fromBuffer`).
  - `WritableByteBuffer` (`com.nexustools.genericui.io.WritableByteBuffer` in `WritableByteBuffer.kt`) implements `Writable` with **no index parameters**, appending bytes, strings, and endian-aware primitives (`Byte`, `UByte`, `Boolean`, `Short`, `UShort`, `Int`, `UInt`, `Long`, `ULong`, `Float`, `Double`) to the end and automatically expanding its internal buffer, and converts to `ByteBuffer` (`toByteBuffer()`) or `ByteArray` (`toByteArray()`).
  - **`BinaryJson` (BSON), `BinaryPlist` (`bplist00`), & `PreferenceObjectCodec`**: Accept `Readable` and `Writable` directly as their primary encoding/decoding abstractions (`encode(value, writable: Writable)`, `encodeElement(element, writable: Writable)`, `suspend fun decode(readable: Readable)`, `suspend fun decodeElement(readable: Readable)`, `suspend fun decodeAs(readable: Readable, ...)`), which automatically supports `WritableByteBuffer`, `ReadableByteBuffer`, `FileWritableNativeImpl`, `FileReadableNativeImpl`, `TcpSocket`, `HttpRequest`, `HttpResponse`, `DatabaseWritableNativeImpl`, `DatabaseReadableNativeImpl`, and any custom `Readable` / `Writable`. Buffer/byte-array convenience helpers (`encodeToBuffer`, `encode`) instantiate a `WritableByteBuffer` and pass it to `encode(value, writable)`.

---

## 2. Consolidated 45-Project Core Hierarchy, Single-`commonMain` Consumer Architecture, & Domain Subpackages

### A. Consolidated Core & Shared Backend Module Hierarchy
GenericUI is organized into **45 consolidated Gradle subprojects** (single-project modules with internal platform source sets—zero nested backend or extension Project-of-Projects subtrees), while consumer applications use a single `src/commonMain/kotlin` source set (with optional target source sets such as `src/androidMain/kotlin`, `src/appleMain/kotlin`, or `src/ipadosMain/kotlin` when needed):
- **`:common`**: 100% platform- and backend-agnostic contracts, components, fragments (`IFragment`, `Fragment`, `FragmentHostFragment`, `FragmentHostSystem`, `@AppFragment`, `@MainAppFragment`, `@AppApplication`), windows (`IWindow` incl. `isAlwaysOnTop`/`setAlwaysOnTop` & client `aspectRatio`, `TitleCapabilities`, `PanelWindow`), `AppleIconTileComposer`, applications (`IApplication`, `FragmentApplication`, `BackendTarget`, `AppTarget`), preferences (`PreferenceStore`), resources (`ResourceID`, `ResourceResolver`), styling, and image APIs.
- **`:system`**: Shared across all **8 non-Web system targets** (`Windows`, `MacOS`, `Gtk`, `Qt`, `Swing`, `Android`, `IOS`, `IPadOS`), housing Blink-style layout & CSS matching (`BrowserLayoutEngine`, `BlinkStyleEngine`) and `SystemStorageKeyHelper`.
- **`:jvm`**: Shared JDK 11 module (`jvmSharedMain` / `jvmSharedTest`) reused by both JVM Swing (`:desktop` `jvmMain`) and Android (`:mobile` `androidMain`)—containing `JvmIoExecutor`, `JvmUiThreadDispatcherBase`, `JvmFileSystemBase`, `JvmDatabaseBase`, `JvmPreferenceStoreBase`, `JvmResourceResolverBase`, NIO/HTTP/WebSocket clients, and `JvmControlTintPixelUtil`.
- **`:apple`**: Shared Darwin module (`appleMain` / `appleTest`) reused by macOS (`:desktop` `macosMain`), iOS, and iPadOS (`:mobile` `iosMain`)—binding `Foundation`, `CoreFoundation`, `Security` TLS SNI, `Network`, `ImageIO`, `CoreGraphics`, `UniformTypeIdentifiers`, `UserNotifications`, and `libsqlite3` (`AppleDatabaseNativeImpl`) with zero AppKit or UIKit coupling.
- **`:desktop`**: Consolidated 5-target desktop backends (`commonMain`, `nativeDesktopMain`, `mingwMain` Win32, `macosMain` AppKit, `linuxMain` GTK4 + Qt6 + XDG, `jvmMain` Swing).
- **`:mobile`**: Consolidated 3-target mobile backends (`commonMain`, `androidMain` Material 3 / 1:1 `AppCompatActivity`, `iosMain` housing both iPhone `Ios*` [`BackendTarget.IOS`, `UIDeviceFamily = 1`] and iPad **`Ipados*`** [`BackendTarget.IPadOS`, `UIDeviceFamily = 2`, Stage Manager `IpadosSceneDelegate`, `UIMenuSystem.mainSystem`, `IpadosPointerAndKeyboardInstaller`]).
- **`:web`**: Dual Kotlin/Wasm (`wasmJs`) primary + Kotlin/JS IR (`js`) fallback browser backend.

### B. Annotation-Driven Fragment Discovery, 1:1 Android Activities, Apple Icons, & Window Contracts
1. **`@MainAppFragment`, `@AppFragment`, & `@AppApplication` Discovery (`AppTarget` Ranks `0..6`)**:
   - Consumer projects annotate fragments in `src/commonMain/kotlin` with `@MainAppFragment(targets = [AppTarget...])` and `@AppFragment`.
   - `AppTarget` includes common scopes (`COMMON(0)`, `SYSTEM_COMMON(1)`, `APPLE_COMMON(2)`, `JVM_COMMON(2)`, `DESKTOP_COMMON(3)`, `MOBILE_COMMON(3)`, `NATIVE_DESKTOP_COMMON(4)`, `LINUX_COMMON(5)`, `IOS_COMMON(5)`) and all **9 concrete backend targets (`rank 6`)**: `WINDOWS`, `MACOS`, `GTK`, `QT`, `SWING`, `ANDROID`, `IOS`, `IPADOS`, `WEB`. The Gradle plugin resolves the highest-specificity `@MainAppFragment` per enabled target and fails immediately on ties or missing targets.
2. **1:1 Android `AppCompatActivity` & Dual iOS/iPadOS Xcode App Generation**:
   - On `:mobile` (`androidMain`), `GenericUiGenerateAndroidActivitiesTask` emits 1:1 `<FragmentName>Activity : GenericUiFragmentActivity()` classes in `AndroidManifest.xml`.
   - On `:mobile` (`iosMain`), `GenericUiGenerateIosAppTask` (`generateGenericUiIosApp` / `generateGenericUiIpadosApp`) generates distinct Xcode apps for iPhone (`UIDeviceFamily = 1`) and iPadOS (`UIDeviceFamily = 2`), disambiguating frameworks (`iosDebugFramework` / `ipadosDebugFramework`) when both are enabled.
3. **`IWindow.alwaysOnTop`, Windowed Client-Viewport `aspectRatio` Lock, & Apple Icon Squircle Pipeline**:
   - `IWindow.isAlwaysOnTop` / `setAlwaysOnTop(Boolean)` controls floating z-order (`PanelWindow` defaults to `isAlwaysOnTop = true` & `isAlwaysOnTopOfOwner = true`).
   - `IWindow.getAspectRatio()` / `setAspectRatio(...)` + `WindowAspectRatioHelper` lock the **client viewport** aspect ratio **only when `WindowState.NORMAL && !isFullscreen()`**—never constraining maximized or fullscreen windows, and re-clamping restored windowed bounds upon unmaximizing/exiting fullscreen.
   - `appleIcon` (`apple { icon }`), `macIcon`, `iosIcon`, `ipadosIcon` track `appleIconExplicitlyProvided`. Whenever no Apple override is provided (`!appleIconExplicitlyProvided`), `GenericUiAppleIconTileComposer` (build-time) and `AppleIconTileComposer` / `MacosNativeWindowHost` (runtime macOS Dock icon) wrap artwork automatically in a Chrome-style clean white rounded squircle (macOS) or full-bleed white square (iOS/iPadOS).
4. **`FragmentBundle` Command-Line Arguments & Android-Style `Resource` / `Resource.ResourceID`**:
   - Generated launchers on all 8 system targets pass CLI arguments in startup `FragmentBundle` (`KEY_COMMAND_LINE_ARGS`) and compile cross-platform resources (`Resource`, `R`, `RC`) across all 9 backends.

### C. Core `:common` Domain Subpackages
All 9 platform backends mirror the subpackage hierarchy of `com.nexustools.genericui` in `:common`:

| # | Subpackage (`com.nexustools.genericui.*`) | Domain Responsibility |
| :- | :--- | :--- |
| 1 | `core` & `component.shell` | `IApplication`, `Application`, `FragmentApplication`, `IWindow`, `Window`, `MdiWindow`, `MdiContainer`, `AbstractComponent`, `NativeFactory`, `NativeRegistry` |
| 2 | `component.fragment` & `component.fragment.helper` | `IFragment`, `Fragment`, `FragmentBundle` (with CLI args on system targets), `FragmentRegistry`, `FragmentLaunchCoordinator`, `@AppFragment`, `@MainAppFragment`, `@AppApplication`, **`LayoutFragment`**, **`CardLayoutFragment`**, **`ColumnLayoutFragment`**, **`FragmentHostFragment`**, **`FragmentHostSystem`**, **`TabbedFragment`** (`TabbedFragmentConfig`, `TabItemSpec`, `TabbedChromeMode`), `FragmentTransitionSpec` (including `FadeExpandShrink`), `FragmentNavigationChromeConfig`, `HybridColumnFragment`, `HybridSplitNavigationFragment`, `CollapsibleBottomDockFragment`, `ResponsiveMasterDetailFragment`, `ResponsiveBentoGridFragment`, `AdaptiveNavigationScaffoldFragment`, `DockWorkspaceFragment`, `OverlayDockWorkspaceFragment`, `CenteredMenuPageFragment` |
| 3 | `window` & `enums.system` | `TitleCapabilities`, `TitleBar`, `WindowConfiguration`, `BackendTarget`, `AppTarget` |
| 4 | `resources` & `component.system` | `ResourceID`, `ResourceType`, `ResourceResolver`, `PreferenceStore`, `PreferenceChangeListener`, `PreferenceCodec`, `PreferenceObjectCodec` |
| 5 | `nativeimpl` | `WindowNativeImpl`, `ContainerNativeImpl`, `PreferenceStoreNativeImpl`, `ResourceResolverNativeImpl`, and per-component `*NativeImpl` peer interfaces |
| 6 | `enums.bootstrap`, `enums.css`, & `style` | `ComponentStyle` (`getStyle()`), `ComponentBootstrap` (`getBootstrap()`), `CssCustomCursor`, `PrecompiledStyleSheet`, `CompiledStyleRule`, `CssStyleSheet`, `ComputedStyle`, `BootstrapClassMapper`, `SystemThemeDetector` |
| 7 | `event` | `UiEvent`, `ClickEvent`, `PointerEvent` (including `onPointerEnter` / `onPointerLeave`), `KeyEvent`, `WheelEvent`, `FocusEvent`, `ResizeEvent`, `WindowEvent` |
| 8 | `component.layout`, `component.surface`, `component.htmlcompat` | `Layout`, `LayoutImpl`, `LayoutFactory`, `LayoutContainer`, `Container`, `Box`, **`ColumnLayout`** (`ColumnContainer`, `Column`, `ColumnSpec` — mapping to Bootstrap `.row` and `.col-*` on `:web`), **`AdaptiveCardLayout`** (`AdaptiveCardContainer`, `AdaptiveCardMode`), `FlexBox`, `HStack`, `VStack`, `ScrollView`, `SplitPane` (`SplitPaneLayout`), `Card`, `Accordion`, `CollapsiblePanel` (`CollapsiblePanelLayout`), **Hybrid Responsive Layouts** (`HybridColumnLayout`, `AdaptiveNavigationScaffoldLayout`, `ResponsiveMasterDetailLayout`, `HybridSplitNavigationLayout`, `CollapsibleBottomDockLayout`, `ResponsiveBentoGridLayout`, `HybridBreakpointSpec`, `HybridLayoutMode`), and HTML tag wrapper classes (`Div`, `Header`, `Footer`, `Main`, `Section`, `H1`..`H6`, `Span`, `P`, etc.) |
| 9 | `component.typography`, `component.content`, `component.feedback` | `Heading`, `Text`, `Label`, `Badge`, `ImageView`, `Icon`, `VectorGraphicView`, `MarkdownView`, `Table`, `Alert`, `ProgressBar`, `Spinner`, `Toast` |
| 10 | `component.action`, `component.navigation`, `component.tabs` | `Button`, `IconButton`, `ToggleButton`, `Toolbar`, `MenuBar`, `Menu`, `MenuItem`, `ContextMenu`, `Navbar`, `TabContainer`, `Tab`, `CloseableTab`, `BottomNavigationBar` |
| 11 | `component.form` | `TextField`, `TextArea`, `PasswordField`, `NumberField`, `SearchField`, `CheckBox`, `RadioButton`, `RadioGroup`, `Switch`, `RangeSlider`, `Select`, `ColorPicker`, `DatePicker` |
| 12 | `component.lazy` | True Lazy Views (`LazyList`, `LazyColumn`, `LazyRow`, `LazyGrid`, `LazyGridTree`, `ViewHolderPool`), `TreeView`, `CategorizedTreeView` |
| 13 | `component.dock` | `DockWorkspace`, `DockArea`, `DockGroup`, `DockPanel`, `PanelWindow`, `ToolPalette`, `ColorSwatchBox` |
| 14 | `component.dialog` | `Dialogs`, `DialogWindow`, `Modal`, `FileBrowserRequest`, `FileTypeFilter`, `SelectedFile` |
| 15 | `image` & `canvas` | **Base Full Image API** (`ImageBuffer`, `AnimatedImage`, `ImageLoader`) & `AbstractCanvas` |

---

## 3. Categorized 19-Extension + 16-`:dsl` Consolidated Architecture (`:extensions:{graphics,physics,game,media,ui}:*`)

Rather than exploding into separate Gradle subprojects per platform, extensions in GenericUI live as consolidated KMP library modules under five domain categories (`:extensions:graphics:*`, `:extensions:physics:*`, `:extensions:game:*`, `:extensions:media:*`, `:extensions:ui:*`) using shared source sets (`commonMain`, `systemMain`, `appleMain`, `jvmMain`, `desktopMain`, `nativeDesktopMain`, `linuxMain`, `mingwMain`, `macosMain`, `mobileMain`, `androidMain`, `iosMain`, `webMain`, `jsMain`, `wasmJsMain`) — plus companion pure-Kotlin `:dsl` submodules:

### A. Categorized Extensions with Native / JVM / Apple / Web Source Sets
1. **`:extensions:graphics:gpu`**:
   - Unified GPU context, shader, VBlank synchronization (`VBlankWatcher`), and offscreen compute/render management (`GpuDevice`, `GpuOffscreenContext`, `VKContext` primary with SPIR-V & MoltenVK on Apple, `GLContext` fallback with Google ANGLE on Windows).
   - Relied upon by `:extensions:graphics:imageprocessing`, `:extensions:graphics:canvas2d`, `:extensions:graphics:canvas3d`, and `:extensions:graphics:threejs`.
2. **`:extensions:graphics:imageprocessing`**:
   - Relies on `:extensions:graphics:gpu` + base `:common` Image API (`systemMain` + `webMain`). Provides `ImageBuffer2DContext` and offscreen in-memory GPU-accelerated image manipulation (`GpuImageProcessor`, `GpuImageFilterChain`, `GpuImageKernel`, `ImageHistogramResult`).
3. **`:extensions:graphics:canvas2d`** (+ **`:extensions:graphics:canvas2d:dsl`**):
   - Relies on `:extensions:graphics:gpu`, `:extensions:graphics:imageprocessing`, and `:extensions:ui:overlay`. Provides `Canvas2D`, `Canvas2DContext`, `Canvas2DExtSurface`, `CurveEditorExt`, `Canvas2DOverlayHost`, and `ScrollCanvas2DOverlayHost` (extending `:common`'s `AbstractCanvas`). `Canvas2DExtension` fails fast if no backend provider is registered.
4. **`:extensions:graphics:canvas3d`** (+ **`:extensions:graphics:canvas3d:dsl`**):
   - Relies on `:extensions:graphics:gpu`, `:extensions:graphics:canvas2d`, and `:extensions:ui:overlay`. Provides unified `Canvas3D` (`Canvas3DConfig`, `Canvas3DRegistry`, `GLContext` with OpenGL ES 3.0+ & Google ANGLE on Windows, `VKContext` with Vulkan 1.2+, MoltenVK on Apple, & WebGPU on Web), `ScrollCanvas3D`, `Canvas3DOverlayHost`, and `ScrollCanvas3DOverlayHost`. `Canvas3DRegistry` fails fast when no backend provider is active.
5. **`:extensions:media:media`** (+ **`:extensions:media:media:dsl`**):
   - Depends **only** on `:common` and `:extensions:ui:overlay` (plus core `:system` / `:jvm` / `:apple` / `:desktop` / `:mobile` / `:web` per target; zero dependencies on `:extensions:graphics:*`). Provides `VideoPlayer` (with `VideoOverlayHost`, `VideoPlayerOverlayBinder`, `attachVideoOverlay`, and uniform codec capability reporting), `AudioPlayer` (`AudioPlayerExt`), `AudioAnalyzer` (`AudioAnalyzerExt`), and `WebVttSrtParser`.
   - `desktop` native targets (`linuxMain`, `mingwMain`, `macosMain`) share `libvlc` bindings; desktop JVM (`jvmMain`) uses `libvlcj`; Android/Apple/Web use native platform media components. `MediaExtension` fails fast if no platform provider is registered.
6. **`:extensions:media:mediacontrols`** (+ **`:extensions:media:mediacontrols:dsl`**):
   - Standalone OS media transport & hardware keyboard media key extension (`com.nexustools.genericui.ext.mediacontrols`), opted into via `extensions { mediaControls() }`.
   - **Zero Extension Dependencies (`:common` Only)** — no silent no-op stubs! `MediaControlsRegistry.factory` initializes via `internal expect fun defaultPlatformMediaControlsFactory()` to a real platform factory on every target (with fail-fast error behavior if unconfigured):
     - **Windows (`mingwMain`)**: `WindowsMediaControlsImpl` + `Win32MediaThumbIconFactory` display Taskbar thumbnail transport buttons via `ITaskbarList3`, update SMTC, and hook `WM_APPCOMMAND` / `VK_MEDIA_*` keys.
     - **macOS (`macosMain` + `appleMain`)**: `MacosMediaControlsImpl` + `AppleNowPlayingBridge` integrate with `MPRemoteCommandCenter` / `MPNowPlayingInfoCenter` and `NSEvent` `NSSystemDefined` keyboard media keys.
     - **Linux (`linuxMain`)**: `GtkMediaControlsImpl` / `QtMediaControlsImpl` + `LinuxMprisDbusBridge` integrate MPRIS D-Bus transport metadata and multimedia keys.
     - **Swing (`jvmMain`), Android (`androidMain`), iOS/iPadOS (`iosMain` + `appleMain`), & Web (`webMain`)**: `SwingMediaControlsImpl`, `AndroidMediaControlsImpl`, `IosMediaControlsImpl` (shared across iOS & iPadOS via `AppleNowPlayingBridge`), and `WebMediaControlsImpl` (`navigator.mediaSession`).
7. **`:extensions:ui:webview`** (+ **`:extensions:ui:webview:dsl`**):
   - Consolidated multi-target `WebView` extension (`WebView` throws fail-fast if no provider is registered).
   - On **Desktop targets** (`desktop` / `jvm`), uses the **Chromium** project (`ChromiumBlinkEngine`) for HTML/CSS layout & rendering coupled with **`NJS`** (`lib/NJS`) with its **AsmJit / SLJIT Native JIT Engine** (`com.nexustools.njs.jit.*`).
   - On **Mobile (`androidMain` `android.webkit.WebView`, `iosMain` `WKWebView` for iOS & iPadOS) and `webMain` (`<iframe>`)**, uses the native platform implementation and **NEVER uses `NJS`**.
8. **`:extensions:ui:alien`** (+ **`:extensions:ui:alien:dsl`**):
   - Self-rendered Bootstrap 5.3 `Canvas2D` UI engine (`AlienFactory`, `AlienContainer`, `AlienFragment`, `AlienScrollFragment`, `AlienFragmentHostFragment`, `UnalienContainer`, `AlienRepaintManager` with dirty update regions) on System targets (`systemMain`), and zero-overhead native DOM pass-through on Web (`webMain`). Relies on `:extensions:graphics:canvas2d` and `:extensions:graphics:imageprocessing`.
   - Precompiles `bootstrap.scss` into `AlienPrecompiledBootstrapStyleSheet`, implements `IAlien` (`addStyleSheet` / `removeStyleSheet`, default Bootstrap stylesheet removable via `IAlien.BOOTSTRAP_STYLE_SHEET_ID`, and `insertApplicationStyleSheets: Int = 1` by default or `-1` to disable application stylesheets), uses `<genericui:fragment>` as the canonical custom element tag, and supports 100% of all `ComponentStyle`, `ComponentBootstrap`, and `PrecompiledStyleSheet` CSS properties and transitions.
9. **`:extensions:game:gamepad`** (+ **`:extensions:game:gamepad:dsl`**):
   - Depends on `:common` (plus `:system`/`:jvm`/`:apple`/`:desktop`/`:mobile`/`:web` platform sourcesets). Unifies Nintendo **Switch Pro / Joy-Con**, Sony **DualSense / DualShock 4**, and **Xbox** controllers via `GamepadManager`, `ControllerFamily`, `ControllerHardwareProfile`, `ControllerButtonLabels`, vendor features (HD Rumble/AMI/NFC/IR notes, DualSense adaptive triggers/touchpad/lightbar/haptics, Xbox impulse triggers/guide/share), `SixAxisSensorFusion` + calibration, 7 `PlatformGamepadBackend` transports, and zero-config customizable `GamepadUiNavigator` (`GamepadUiNavigationStrategy`, `DefaultSpatialGamepadUiNavigationStrategy`). See [`docs/gamepad.md`](./docs/gamepad.md).

### B. Pure Common Multiplatform Extensions (+ Companion `:dsl` Projects)
Implemented in 100% shared `commonMain` code atop `:common` and upstream extensions:
10. **`:extensions:ui:dsl`** (depends **only** on `:common`): `kotlinx.html`-style component builders (`div`, `row`, `col`, `h1`..`h6`, `button`, `table`, etc.), hybrid layout DSL, `style { }` / `bootstrap { }` / `important { }` builders, and MVC classes (`MvcModel`, `MvcView`, `MvcController`).
11. **`:extensions:ui:overlay`** (+ **`:extensions:ui:overlay:dsl`**; depends **only** on `:common`): Shared customizable overlay framework (`OverlayHost`, `OverlayHostBox`, `AbstractOverlay`, `PresetOverlay`, `OverlaySlot`, `OverlayState`, `OverlayActions`, `AutoHideController`, `OverlayInputRouter`, `MobileOverlayPreset`, `WebSystemOverlayPreset`, `OverlayPresetResolver`).
12. **`:extensions:ui:codeeditor`** (+ **`:extensions:ui:codeeditor:dsl`**; depends on `canvas2d`, `imageprocessing`, `overlay`): Extension-only IDE-style code editor (`IdeCodeEditor`, `CodeLanguage`, `CodeEditorTheme`, `CodeDiagnostic`, `ScssLanguageSupport`).
13. **`:extensions:ui:aichat`** (+ **`:extensions:ui:aichat:dsl`**; depends on `codeeditor`, `canvas2d`): Reusable AI chat drawer and multi-agent orchestrator (`AiChatContainer`, `AIChatDrawer`, `AIChatAgentOrchestrator`).
14. **`:extensions:graphics:threejs`** (+ **`:extensions:graphics:threejs:dsl`**; depends on `gpu`, `canvas3d`): Three.js-style 3D scene graph (`Scene`, `PerspectiveCamera`, `WebGLRenderer`, `OrbitControls`, `Canvas3D`).
15. **`:extensions:physics:box2d`** (+ **`:extensions:physics:box2d:dsl`**; depends on `canvas2d`, `canvas3d`): 2D rigid-body physics engine (`World2D`, `Body2D`, `Box2DDebugDrawer`, `Box2DCanvas`).
16. **`:extensions:physics:box3d`** (+ **`:extensions:physics:box3d:dsl`**; depends on `canvas3d`, `threejs`): 3D rigid-body physics engine (`Box3DWorld`, `Box3DThreeJsBridge`).
17. **`:extensions:game:engine`** (+ **`:extensions:game:engine:dsl`**; depends on `:common`, `:extensions:game:gamepad`): Dedicated `PlatformGameLogicThread`, `GameLoopScheduler`, `FixedStepAccumulator`, `VBlankAnimationDriver` + `AnimationScheduler`, 31 `EasingFunction`s, spline/spring/wave curves, SVG `Skeleton2D` + `TwoBoneIkSolver2D`, and `UiStageHost`. See [`docs/game-engines.md`](./docs/game-engines.md).
18. **`:extensions:game:engine2d`** (+ **`:extensions:game:engine2d:dsl`**; depends on `engine`, `canvas2d`, `imageprocessing`, `box2d`): `Game2DCanvasHost`, `SvgCharacter2DModule`, and Box2D 2D game world integration.
19. **`:extensions:game:engine3d`** (+ **`:extensions:game:engine3d:dsl`**; depends on `engine`, `canvas3d`, `threejs`, `box3d`): `Game3DCanvasHost`, `ProceduralHumanoidRig3D`, `CharacterController3DModule`, and Three.js + Box3D integration.

---

## 4. Base `:common` Full Image API (`ImageCodecNativeImpl` + `libjpeg`/`libpng`/`libwebp`) & Offscreen GPU `:extensions:graphics:imageprocessing`

- **Base `:common` Image API (`com.nexustools.genericui.image.*`)**:
  - `ImageLoader` decodes and encodes still and animated images (`PNG`, `JPEG`, `WEBP`, `GIF`, `APNG`, `BMP`, `ICO`, `TIFF`, `SVG`, `PPM`) into `ImageBuffer` / `AnimatedImage` (`List<ImageFrame>`) with full `PixelFormat` (`RGBA_8888`, `BGRA_8888`, `RGB_888`, `RGBA_F16`, `RGBA_F32`, `GRAY_8`), `ImageColorSpace` (`SRGB`, `DISPLAY_P3`, `REC_2020_PQ`, `REC_2020_HLG`, `LINEAR_SRGB`), `ImageMetadata`, and `AppleIconTileComposer` for runtime macOS squircle wrapping.
  - **Native Image Loader First, Dedicated `libjpeg`/`libpng`/`libwebp` Codecs Where Needed (`ImageCodecNativeImpl`)**:
    - **Swing (`:desktop` `jvmMain` + `:jvm`)**: `SwingImageIoCodecNativeImpl` uses `javax.imageio.ImageIO` & `BufferedImage`.
    - **Android (`:mobile` `androidMain`)**: `AndroidBitmapImageCodecNativeImpl` uses `android.graphics.BitmapFactory` and `android.graphics.ImageDecoder`.
    - **Web (`:web`)**: `WebImageCodecNativeImpl` uses browser-native `window.createImageBitmap`, `HTMLImageElement`, and `OffscreenCanvas` / `<canvas>` pixel extraction.
    - **Apple (`:apple`: macOS, iOS, & iPadOS)**: `AppleImageIoCodecNativeImpl` uses Apple `ImageIO` (`CGImageSource`, `CGImageDestination`, `CGBitmapContext`) + `AppleSvgCoreGraphicsRasterizer`.
    - **Native Desktop (`:desktop` `nativeDesktopMain`, `mingwMain`, `linuxMain`)**: Uses OS native loaders (`WIC` on Windows, `GdkPixbuf` on GTK, `QImage`/`QImageReader` on Qt) plus built-in codecs (`LibJpegCodec`, `LibPngCodec`, `LibWebpCodec`, `BmpPpmCodec`).
- **Offscreen GPU Image Processing (`:extensions:graphics:imageprocessing`)**:
  - Uses `:extensions:graphics:gpu`'s `GpuOffscreenContext` (Vulkan compute/fragment primary, OpenGL ES fallback) to process `ImageBuffer` instances offscreen in memory without requiring an on-screen window or canvas.

---

## 5. Deferred Upward `NativeImpl` Resolution, Fail-Fast Missing `NativeImpl` Contract, & `LayoutFactory` Architecture

1. **Pure Kotlin Construction**: Calling `val button = Button("Save")` or `val box = VStack()` initializes state in pure common Kotlin (`nativeImpl == null`). It MUST NOT throw an exception if no `Application` or `Window` is active yet.
2. **Upward Factory Discovery**: When `parent.addChild(child)` is invoked, `child` calls `resolveBackendFactoryUpward()`, walking up `parent -> parent.parent -> ... -> RootContainer -> Window -> Application` (with fallback to `NativeBackendRegistry.activeFactory`).
3. **Downward Realization Cascade**: When a detached subtree is attached to a live `Window` or `Fragment`, `realizeNativeTree(factory)` cascades **downward** across the entire subtree in pre-order.
4. **Fail-Fast Runtime Error on Missing `NativeImpl` / Provider**:
   - When an `Application` is launched or a `Window` is shown (`isWindowShown == true`), an active `NativeFactory` MUST be present and the window MUST have content (`fragmentContainer`, `contentContainer`, or child components); otherwise it throws an `IllegalStateException`.
   - When any visible component or extension widget is attached to a live `Window`/`Application`, if its requested `NativeImpl` or extension provider (`Canvas2DExtension`, `Canvas3DRegistry`, `MediaExtension`, `WebView`, `MediaControlsRegistry`, `FragmentRegistry`, etc.) is not implemented/registered for the active backend, it **MUST throw an immediate runtime exception (`UnsupportedOperationException` or `IllegalStateException`)** rather than falling back to a silent no-op stub.
5. **Shared `Layout<I : LayoutImpl>` Helper System & `LayoutFactory` (`NativeFactory.layoutFactory`)**:
   - **Layouts Are Shared Helper Classes, Not Duplicate Component Hierarchies**: Both `LayoutContainer` and `LayoutFragment` subclasses delegate to the same `Layout` helper class.
   - **`LayoutFactory` Per Backend Group**:
     - **`MobileLayoutFactory` (`:mobile` `commonMain` $\rightarrow$ Android, iOS, & iPadOS)**: Dynamic hybrid `LayoutImpl`s scaling from small-screen phones / ~320pt iPad Slide Over to tablet & iPadOS Stage Manager layouts.
     - **`WebLayoutFactory` (`:web`)**: Dynamic responsive browser viewport layouts and Bootstrap 5 `.row`/`.col-*` mapping.
     - **`DesktopLayoutFactory` (`:desktop` `commonMain`)**: Large-screen layouts + `window.setMinWindowSize(...)` enforcement.

---

## 6. `TitleBar`, `TitleCapabilities`, & Single-Placement Custom Components

- `TitleBar` (`com.nexustools.genericui.window.TitleBar`) is applied via `window.setTitleBar(titleBar)` or `fragment.setTitleBar(titleBar)`.
- `IWindow.titleCapabilities` (`TitleCapabilities`) reports `isSupported`, `isTitleBarRequired` (`true` on native desktop, `false` on Android, iOS, iPadOS, and Web), and `canEmbedComponentsInTitleBar`.
- **Single-Placement Rule**: Components mounted inside a `TitleBar` (`mountedInTitleBar = true`) MUST only be rendered once (never duplicated in body containers).
- **Platform Chrome Rules**:
  - **Android & iPhone iOS (`:mobile`)**: Setting a `TitleBar` **or** a `MenuBar` creates a navbar; if neither is set, no navbar is added.
  - **iPadOS (`:mobile` `ipados/`)**: `TitleBar` renders as an inline navigation/header bar (`titleBarRendersAsNavbar = true`), while `MenuBar` binds directly to **`UIMenuSystem.mainSystem`** (`IpadosMenuBarBridge`) instead of an in-viewport button bar.
  - **Web (`:web`)**: `MenuBar` only $\rightarrow$ menubar; `TitleBar` only $\rightarrow$ navbar; `TitleBar` + `MenuBar` $\rightarrow$ Bootstrap navbar with title/icon on the left and menu on the right; neither $\rightarrow$ no navbar.
  - **macOS (`:desktop` `macosMain`)**: `MenuBar` / `TitleBar.menus` MUST bind to the global **macOS system menu bar** (`NSApplication.sharedApplication.mainMenu`).

---

## 7. `IFragment`, `FragmentHostFragment`, `FragmentHostSystem`, `<genericui:app>`, `<genericui:fragment>`, & Helper Fragments

- `Fragment` implements `IFragment` and mirrors Android's `Activity`/`Fragment` lifecycle (`onCreate`, `onStart`, `onResume`, `onPause`, `onStop`, `onSaveInstanceState`, `onDestroy`), with `launchFragment(FragmentClass, LaunchArgs)` delivering `LaunchArgs` (`FragmentBundle`) to `onCreate(savedState: FragmentBundle?)`.
- **Canonical `<genericui:app>` & `<genericui:fragment>` Custom Element Tags**:
  - **`<genericui:app>`**: Root application DOM element on `:web`. Zero `<link>`, `<style>`, or `<script>` tags are ever injected inside `<genericui:app>`; all stylesheets and scripts are mounted strictly in `document.head` (`<head>`) and deduplicated against any links already present in the HTML template.
  - **`<genericui:fragment>`**: Canonical custom element tag (`htmlTag = "genericui:fragment"`) used uniformly across all `Fragment` subclasses (`Fragment`, `ScrollFragment`, `FragmentHostFragment`, `AlienFragment`, `AlienScrollFragment`, and `AlienFragmentHostFragment`).
- **`FragmentHostFragment` & `FragmentHostSystem` (`com.nexustools.genericui.component.fragment.helper`)**:
  - Standardizes multi-page and multi-screen host fragments.
  - Supports **fully customizable transitions** (`FragmentTransitionSpec` / `FragmentTransitionController`), including `FadeExpandShrink` (`fadein/expand` + `fadeout/shrink` with outgoing/incoming co-mounting).
  - On **`:web`**, `FragmentHostSystem` tracks pages via URL `#hashes` (`window.location.hash`) and saves/restores fragment states (`FragmentBundle`) via the browser's native page state API (`window.history.state` + `popstate`) so the browser Back/Forward buttons work natively.
  - On **desktop platforms supporting custom titlebars** (`titleCapabilities.canEmbedComponentsInTitleBar`), optional Back/Forward buttons and custom components are embedded in the `TitleBar`; otherwise, an optional `Navbar` is added below the titlebar.
- **`TabbedFragment` (`com.nexustools.genericui.component.fragment.helper.TabbedFragment`)**:
  - Provides a customizable, uniform multi-tab fragment architecture (`TabbedFragment`, `TabbedFragmentConfig`, `TabItemSpec`, `TabbedChromeMode`) that works with the **exact same API** (`addTab`, `removeTab`, `selectTab`, `updateTab`, `titleBarLeadingSlot`, `tabStripBar`, `titleBarTrailingSlot`, `subHeaderBar`, `setRightSidePanel`, `statusBar`) across both Desktop and Mobile:
  - **Desktop / TitleBar-Embedded Mode**: When supported by the OS (`titleCapabilities.canEmbedComponentsInTitleBar && !isMobileLayoutActive`), embeds the horizontal tab strip (`<` / `>` overflow scroll buttons, pinned icon-only tabs, closable/modified `•` tabs, `+` new tab button) and trailing titlebar actions (such as a `✦ Ask Gemini` AI toggle) directly into the window `TitleBar`, with `subHeaderBar` (menus/formatting toolbar or browser navigation/omnibox bar) immediately below the titlebar and an optional right-side panel (`rightSidePanelContainer`) beside the active tab's body.
  - **Mobile Mode**: Automatically adapts the same tabs and slots into a compact mobile top bar (`[Home/Leading Button] [Pill Omnibox / Active Title] [[N] Tab Counter Badge Button] [Trailing / ⋮ Menu]`) with an interactive **Mobile Tab Switcher** card view toggled by the `[N]` tab counter button.
- Additional helper fragments (`HybridColumnFragment`, `DockWorkspaceFragment` with compact mobile edge swipe-out drawers via `DockMobileDrawerPolicy`, `AdaptiveNavigationScaffoldFragment`, `ResponsiveMasterDetailFragment`, `HybridSplitNavigationFragment`, `CollapsibleBottomDockFragment`, `ResponsiveBentoGridFragment`, `AdaptiveSplitTabFragment`, `OverlayDockWorkspaceFragment`, `CenteredMenuPageFragment`) standardize responsive and dockable workspaces across desktop, tablet, mobile, and web. See [`docs/layouts-and-helpers.md`](docs/layouts-and-helpers.md).

---

## 7B. `ComponentStyle` (`getStyle()`), `ComponentBootstrap` (`getBootstrap()`), Zero String-Keyed Style APIs, Native Control Rendering & `BootstrapType` Tinting, Unified `ThemeConfig`, & Per-Component `*NativeImpl` / `*RenderUtil` Architecture

1. **Zero String-Keyed Style APIs (`ComponentStyle` & `ComponentBootstrap` Only; String Translation Exclusively on `:web`)**:
   - GenericUI **MUST NOT** provide any string-keyed style API (`setStyleProperty(String, String)`, `getStyleProperty(String)`, `removeStyleProperty(String)`, `clearStyleProperties()`, `inlineStyle: String`, `setRawDeclaration(String, String?)`, `getRawDeclaration(String)`, or `rawDeclarations: Map<String, String>`).
   - Every `AbstractComponent` exposes strongly-typed `getStyle(): ComponentStyle` (`val style: ComponentStyle`) and `getBootstrap(): ComponentBootstrap` (`val bootstrap: ComponentBootstrap`), with DSL builders (`style { ... }`, `bootstrap { ... }`, `important { ... }`) in `:extensions:ui:dsl`.
   - On all 8 non-Web system targets (`Windows`, `MacOS`, `Gtk`, `Qt`, `Swing`, `Android`, `IOS`, `IPadOS`) and `:extensions:ui:alien`, `ComponentStyle`, `ComputedStyle`, and `ComponentBootstrap` operate on **100% strongly-typed fields and primitive 64-bit bitmasks** (`activePropertiesBitmaskLow`/`High`, `importantBitmaskLow`/`High`) with **zero string keys, zero CSS string serialization, and zero `Map<String, String>` allocations**.
   - String-based CSS property names and values (`element.style.setProperty(...)`) are translated **transparently and exclusively inside the `:web` backend** (`WebBaseNativeImpl` / `WebCssStyleTranslator`).
   - **Zero Pseudo-States on Inline `ComponentStyle`**: Inline `ComponentStyle` never contains `:hover`, `:focus`, `:active`, `:disabled`, or `:checked` subblocks (pseudo-classes belong strictly in `.scss`/`.css` stylesheets).
2. **`component.getBootstrap(): ComponentBootstrap` & Strict User CSS Class Isolation**:
   - Every `AbstractComponent` exposes `getBootstrap(): ComponentBootstrap` (`val bootstrap: ComponentBootstrap`), encapsulating all Bootstrap properties and `Bootstrap*` utility enums: `BootstrapType` (defaulting to `BootstrapType.NONE` / `BootstrapType.None`), `BootstrapSize`, `BootstrapWidth`, `BootstrapHeight`, directional `SpacingScale` margins/paddings/gaps (including `SpacingScale.AUTO`), `BootstrapBorderWidth`, `BootstrapOpacity`, `BootstrapFontSize`, `BootstrapLineHeight`, `BootstrapFlexGrow`, `BootstrapFlexShrink`, `BootstrapFloat`, `BootstrapUserSelect`, `BootstrapPointerEvents`, `BootstrapObjectFit`, `BootstrapAspectRatio`, `BootstrapVisibility`.
   - **Strict User CSS Class Isolation**: `addClass`, `removeClass`, `hasClass`, `getClasses()`, and `getClass()` operate strictly on user-added custom classes (`customClasses`), keeping enum-added classes 100% invisible.
3. **Strict `:extensions:ui:dsl` Isolation**:
   - DSL builder functions (`style { ... }`, `bootstrap { ... }`, `important { ... }`) live exclusively in `:extensions:ui:dsl` (`DslStyleAndBootstrapBuilders.kt`) and are **never** part of `:common`.
4. **System Backend Aesthetic Style Rules (Typography on Text Elements vs. Silently Ignored Aesthetic Styles on Standard Controls)**:
   - On all 8 non-Web system targets, you cannot redesign the aesthetic UI styles of standard controls to make them look alien without using `:extensions:ui:alien` (or `StyledContainer` / `StyledButton`):
     - **Text / Typography Elements (`Label`, `Text`, `Heading`, `Badge`, `Blockquote`, `CodeBlock`, `MarkdownView` $\rightarrow$ `TextNativeImpl` & `MarkdownViewNativeImpl`)**: Typography and text-color properties (`color` / `textColor`, `fontFamily`, `fontSize`, `fontWeight`, `fontStyle`, `lineHeight`, `letterSpacing`, `textAlign`, `textDecoration`, `textTransform`, `textOverflow`, `whiteSpace`, `wordBreak`, plus `bootstrap.textColorType`, `bootstrap.fontSize`, `bootstrap.fontWeight`, `bootstrap.textAlignment`) **work on all system targets**.
     - **Buttons, Inputs, Controls, & Standard Containers (`Button`, `IconButton`, `ToggleButton`, `CheckBox`, `RadioButton`, `Switch`, `Select`, `DropdownButton`, `TextField`, `TextArea`, `RangeSlider`, `ProgressBar`, `Spinner`, `TabContainer`, `Tab`, `Container`, `Card`, etc.)**: Aesthetic skinning/chrome and typography overrides on `ComponentStyle` (`backgroundColor`, `background` / `backgroundImage` gradients, `color` / `textColor`, `accentColor`, `borderColor`, `borderWidth`, `borderStyle`, `borderRadius`, `boxShadow`, `textShadow`, `filter`, `backdropFilter`, `fontFamily`, `fontSize`, `fontWeight`, `fontStyle`) are **silently ignored**. Standard components can **ONLY** be tinted via `BootstrapType` (`bootstrap.type`, `bootstrap.backgroundType`, `bootstrap.textColorType`, `bootstrap.borderType`, `bootstrap.outline`).
     - **`StyledContainer` & `StyledButton` (and `:extensions:ui:alien`)**: Explicitly opt into honoring full `ComponentStyle` aesthetic and typography properties.
5. **Native-Only System Control Rendering (`BootstrapType.NONE`) & Empty-Text Native Render + Color-Tint + Text Overlay (`BootstrapType != NONE`)**:
   - Unless `:extensions:ui:alien` or `:web` is used, `GenericUI` itself **MUST ALWAYS render genuine native OS components** on all 8 system targets:
     - **`BootstrapType.NONE` (default)**: Renders the 100% unmodified native OS component using the OS's native control libraries (`uxtheme.dll` / `comctl32.dll` / `user32.dll` on Windows, AppKit `NSControl` on macOS, GTK4 `GtkWidget` on GTK, Qt6 `QStyle` on Qt, system Look-And-Feel `JComponent` on Swing, default Android/Material `View` on Android, UIKit `UIControl` on iOS & iPadOS).
     - **`BootstrapType != BootstrapType.NONE` (`PRIMARY`, `SECONDARY`, `SUCCESS`, `INFO`, `WARNING`, `DANGER`, `LIGHT`, `DARK`)**: Renders the exact same native OS component with **no text** into an offscreen buffer (when the OS toolkit does not provide a direct bevel-preserving native tint API), **color-tints the native control pixels** to match the resolved `ThemeConfig` color for that `BootstrapType` (preserving 100% of native bevels, borders, highlights, and hover/pressed/disabled states), blits it, and **renders the contrasting text and icon on top afterward**.
   - `:extensions:ui:alien` precompiles `bootstrap.scss` into `AlienPrecompiledBootstrapStyleSheet` and implements `IAlien` on `AlienContainer`, `AlienFragment`, `AlienScrollFragment`, and `AlienFragmentHostFragment` (`IAlien.BOOTSTRAP_STYLE_SHEET_ID` active at index `0` by default and removable via `removeStyleSheet(IAlien.BOOTSTRAP_STYLE_SHEET_ID)`, plus `insertApplicationStyleSheets: Int = 1` by default or `-1` to disable application stylesheets).
6. **No Centralized Render-Tree `when`-Block Monoliths (Per-Component `*NativeImpl` & Type-Grouped `*RenderUtil` Classes Only)**:
   - Backends (`:desktop` [`windows`, `macos`, `gtk`, `qt`, `swing`], `:mobile` [`android`, `ios`, `ipados`], and `:tests`) **MUST NOT** use massive centralized `when (impl)` / `when (comp)` render-tree monolith files. Component-specific rendering must live inside each component's `<Backend><Component>NativeImpl` class, and any rendering logic shared across multiple `*NativeImpl` classes must be split into type-grouped `*RenderUtil` classes (strictly 1 class per `.kt` file).
7. **Unified `ThemeConfig` System (Gradle Plugin DSL, `:common` `ThemeConfig`, Android Material Theme XML, Desktop, & Web Pre-Generated + Runtime CSS)**:
   - `ThemeConfig` and `ThemeColorSet` (`com.nexustools.genericui.style.theme`) and `genericui { theme { ... } }` in `:gradle-plugin` allow overriding Bootstrap and Material primary/secondary/accent/semantic colors (`primary`, `secondary`, `accent`, `info`, `success`, `warning`, `danger`, `light`, `dark`, `background`, `surface`, `statusBar`, plus `light { ... }` and `dark { ... }` mode sets):
     - **Android (`:mobile` `androidMain`)**: Generates `res/values/genericui_theme_colors.xml`, `res/values/themes.xml`, and `res/values-night/themes.xml` (`@style/Theme.GenericUI.App`) producing Material Design components with the specified theme colors.
     - **Windows & All System Backends**: Uses `ThemeConfig` to tint native `BootstrapType` components.
     - **Web (`:web`)**: Pre-generates `gui/genericui-theme.css` at build time when configured in `build.gradle.kts` AND generates/updates `<style id="genericui-runtime-theme-css">` at runtime (`WebStyleSheetManager.applyThemeConfig`) to override Bootstrap's `--bs-*`, `.btn-*`, `.bg-*`, and component colors.
8. **Custom Cursors (`CssCustomCursor`) & Apple Icon Pipeline**:
   - `CssCustomCursor` (from PNG `ResourceID`, SVG `ResourceID`, inline `svgMarkup`, or `ImageBuffer` with `hotspotX` / `hotspotY` and `CssCursor` fallback) is automatically translated to a valid Windows `.cur` binary on Windows (`WindowsCurCodec`) and `NSCursor` on macOS (`MacosCursorTranslator`).
   - Generic `app.icon` / `appIcon` inputs with no Apple override are automatically wrapped in Chrome-style rounded white squircles on macOS (`GenericUiAppleIconTileComposer` + `AppleIconTileComposer` / `MacosNativeWindowHost.composeRuntimeDockIconBuffer`) and full-bleed white AppIcon tiles on iOS/iPadOS.
9. **Build-Time `.scss` Precompilation, Local NPM Bootstrap Packaging, `Resources.BOOTSTRAP` / `Resources.GENERICUI`, & `IApplication.addStyleSheet` / `removeStyleSheet`**:
   - `.scss` files in `src/commonMain/resources/**` compile at build time to `Resource.style.<name>` (`R.style.<name>`) backed by typed `PrecompiledStyleSheet` (`CompiledStyleRule`) on all 8 non-web targets and `.css` files on `:web`.
   - **Local NPM Bootstrap Packaging & Built-In `Resources.*` IDs**: Instead of loading Bootstrap from an external CDN, `:web` packages local NPM Bootstrap (`npm i bootstrap`) bundling `bootstrap.min.css` and `bootstrap.bundle.min.js` directly into resources alongside `genericui.css`. `Resources` (`com.nexustools.genericui.resources.Resources`) exposes the built-in `Resources.BOOTSTRAP`, `Resources.BOOTSTRAP_JS`, `Resources.BOOTSTRAP_ICONS`, and `Resources.GENERICUI` `ResourceID` constants. On `:web`, all stylesheets are mounted in `<head>` (deduplicated against HTML template `<link>` tags, with zero `<link>`, `<style>`, or `<script>` tags inside `<genericui:app>`) and can be removed from an `Application`'s stylesheets via `app.removeStyleSheet(Resources.BOOTSTRAP)` or `app.removeStyleSheet(Resources.GENERICUI)`.
   - `IApplication.addStyleSheet` and `IApplication.removeStyleSheet` live on `IApplication` (NOT `IFragment`), and the Gradle plugin automatically injects `app.addStyleSheet(...)` in generated `main()` startup code when `styles { globalStyleSheet = "..." }` is configured in `build.gradle.kts`.
10. **`IApplication` Theming (`ThemeMode.AUTO`, `DARK`, `LIGHT` — Default `AUTO`)**:
   - `IApplication.setTheme(ThemeMode)`, `getTheme()`, `getResolvedTheme()`, `setThemeConfig(ThemeConfig)`, and `getThemeConfig()` default to `ThemeMode.AUTO` with live 9-target OS dark/light theme detection (`SystemThemeDetector`) and `:web` Bootstrap `data-bs-theme="light|dark"` switching.

---

## 8. Native File & Folder Browser (`FileBrowserDialog`), `PreferenceStore`, & True Lazy Views Across All 9 Platforms

- **`FileBrowserDialog` & `Dialogs`**: Supports `OPEN_SINGLE_FILE`, `OPEN_MULTIPLE_FILES`, `SAVE_FILE`, and `SELECT_FOLDER` with `FileTypeFilter` and `FileBrowserCallback` (`onFilesSelected` / `onCancelled`). On `:web`, uses an invisible, un-interactable `<input type="file">` (`webkitdirectory` for folder mode, `accept` for `FileTypeFilter`); on Android (`:mobile`), launches the native Android system file browser (`ACTION_OPEN_DOCUMENT`, `ACTION_CREATE_DOCUMENT`, `ACTION_OPEN_DOCUMENT_TREE`) via `GenericUiFragmentActivity` `ActivityResultLauncher`.
- **`PreferenceStore` & `IApplication.getPreferenceStore()` / `IFragment.getPreferenceStore()`**: Cross-platform preference utility (`com.nexustools.genericui.component.system.PreferenceStore`) backed by Binary JSON (`.bson`, using `ByteBuffer`) / JSON config files on Windows/Linux, `bplist00` `.plist` via `:apple` on macOS/iOS/iPadOS, `SharedPreferences` on Android, `.plist`/`.bson` on Swing, and `localStorage` on Web, with native change listeners (`PreferenceChangeListener`). Both `IApplication` and `IFragment` provide `getPreferenceStore(): PreferenceStore`, which lazily creates a default `PreferenceStore` on the first call and reuses the same instance on subsequent calls. All GenericUI apps use `getPreferenceStore()` instead of custom preference storage.
- **True Lazy Views (`LazyList`, `LazyColumn`, `LazyRow`, `LazyGrid`, `LazyGridTree`, `TreeView`)**: Dynamically load and bind items (`onBind`) only within `[firstVisibleIndex .. lastVisibleIndex]` and immediately unload/recycle (`onUnbind` $\rightarrow$ `ViewHolderPool`) items as they scroll out of view on all 9 platforms.

---

## 9. `:extensions:ui:webview`, `lib/NJS` Submodule Workflow, & Strict Desktop-Only `NJS` Rule

1. **`WebView` Is an Extension-Only Module (`:extensions:ui:webview`)**:
   - `WebView` (`WebView`, `WebViewController`, `WebViewJsBridge`) lives in `:extensions:ui:webview` and is opted into via `genericui { extensions { webView() } }`.
2. **STRICT RULE — DO NOT USE `NJS` ON iOS, iPadOS, ANDROID, OR WEB**:
   - **Mobile (`:mobile` `androidMain` & `iosMain` for iOS/iPadOS) & `:web`**: `:extensions:ui:webview` MUST use the native platform implementation (`android.webkit.WebView` on Android, `WKWebView` on iOS/iPadOS, and `<iframe>` on Web) and **MUST NEVER depend on or invoke `NJS`**.
   - **Desktop (`:desktop` — Windows, macOS, GTK4, Qt6, Swing)**: `:extensions:ui:webview` uses the **Chromium** project (`ChromiumBlinkEngine`) for HTML/CSS layout & rendering coupled with **`NJS`** (`lib/NJS`, included via `includeBuild("lib/NJS")`) for the JavaScript runtime.
   - `NJS` supports Desktop Native targets (`mingwX64`, `linuxX64`, `linuxArm64`, `macosArm64`, `macosX64`) alongside `jvm` (and strictly NOT iOS, iPadOS, Android, or Web):
     - **Desktop Native Builds (`nativeDesktopMain`)**: `NJS` uses its **AsmJit / SLJIT Native JIT Engine** (`com.nexustools.njs.jit.*`).
     - **Desktop JVM Builds (`jvmMain`)**: `NJS` uses its own **Java Compiler System** (`JavaSourceTranspiler` + `JavaTranspiler` backed by `javax.tools.ToolProvider.getSystemJavaCompiler()`) with no custom JIT engine needed on JVM.
   - Preserve the `lib/NJS` directory symlink/submodule during development.

---

## 10. `:extensions:ui:dsl` (`kotlinx.html`-Style MVC, Hybrid Layout, & Style/Bootstrap DSL) Boundary Rule

1. **`:common`-Only Scope & Strict DSL Isolation**:
   - `:extensions:ui:dsl` (`com.nexustools.genericui.ext.dsl`) depends strictly on `:common` and provides `MvcModel`, `MvcView`, `MvcController`, `kotlinx.html`-style in-place component builders (`div`, `row`, `col`, `h1`..`h6`, `p`, `span`, `button`, `select { option(...) }`, `table { tr { th(...); td(...) } }`, etc.), hybrid layout builders (`HybridLayoutDsl.kt`: `hybridColumnLayout`, `adaptiveNavigationScaffold`, `responsiveMasterDetailLayout`, `hybridSplitNavigationLayout`, `collapsibleBottomDockLayout`, `responsiveBentoGridLayout`), and style/Bootstrap DSL builders (`DslStyleAndBootstrapBuilders.kt`: `style { ... }`, `bootstrap { ... }`, `important { ... }`) for `:common` components only.
   - `:common` MUST NEVER define `style { ... }` or `bootstrap { ... }` DSL functions; all DSL builders live strictly in `:extensions:ui:dsl` and companion `:extensions:<category>:<name>:dsl` modules.
   - **Never** add dependencies or builders for other extensions inside `:extensions:ui:dsl`; other extensions provide their own companion `:dsl` modules (16 companion `:dsl` projects across `graphics`, `physics`, `game`, `media`, and `ui`).

---

## 11. Strict Extension & Backend Isolation (NON-NEGOTIABLE)

All agents, subagents, and engineers MUST strictly enforce **Extension Isolation** and **Backend Isolation** across the entire `GenericUI` repository:

1. **Zero Extension Leakage into Core Modules (`:common`, `:system`, `:jvm`, `:apple`, `:desktop`, `:mobile`, `:web`, `:gradle-plugin`)**:
   - `:common`, `:system`, `:jvm`, `:apple`, `:desktop`, `:mobile`, and `:web` **MUST NEVER** import, reference, hardcode, or include any classes, tag names, CSS selectors, or logic belonging to any of the 19 extension modules or 16 `:dsl` submodules.
   - For example:
     - **No Alien logic/tags in core modules**: `AlienFragment`, `AlienScrollFragment`, `AlienFragmentHostFragment`, `AlienContainer`, `UnalienContainer`, `AlienImpl`, `AlienBootstrapPainter`, `genericui:alien-container`, and `genericui:unalien-container` belong strictly inside `:extensions:ui:alien` (while all `Fragment` subclasses share the canonical `genericui:fragment` custom element tag).
     - **No VLC/Media or MediaControls logic in `:desktop` or `:common`**: All VLC/media decoding, shaders, and playback pipelines belong strictly inside `:extensions:media:media`, and all OS media transport / taskbar preview / keyboard media key hooks belong strictly inside `:extensions:media:mediacontrols`.
     - **No VBlank/GPU or 2D/3D Canvas rendering pipelines in `:common` or core backends**: `VBlankWatcher` lives in `:extensions:graphics:gpu`; `ImageBuffer2DContext` lives in `:extensions:graphics:imageprocessing`; `Canvas2D` and `Canvas3D` live in `:extensions:graphics:canvas2d` and `:extensions:graphics:canvas3d`.
     - **No Gamepad / Game Engine / Physics logic in `:common` or `:desktop`/`:mobile`/`:web`**: Gamepad HID/platform polling belongs in `:extensions:game:gamepad`; dedicated `PlatformGameLogicThread`, fixed-step loops, curves, and IK skeletons belong in `:extensions:game:engine*`; rigid-body physics belongs in `:extensions:physics:box*`.
2. **Strict Backend Isolation (Zero `BackendTarget` Branching or String Parsing in `:common`)**:
   - `:common` MUST remain 100% platform- and backend-agnostic. Never place platform-specific code, `BackendTarget` branching (`backendTarget != BackendTarget.Web`, `backendTarget == BackendTarget.Android`, `when (backendTarget)`), or string parsing (`BackendTarget.fromId(...)`) inside `:common`.
   - Instead, each concrete backend (`<Backend>NativeFactory` / `<Backend>*NativeImpl`) MUST return its `BackendTarget` enum, `TitleCapabilities`, and capability booleans (`supportsTcp()`, `supportsUdp()`, `usesBrowserManagedImageLoading`, etc.) directly as constant values with zero runtime logic, and features that are always supported across all 9 backends (`HTTP`, `WebSocket`, `Database`) must not have redundant `supports*()` methods.
   - Each backend implementation (`Windows`, `MacOS`, `Gtk`, `Qt`, `Swing`, `Android`, `IOS`, `IPadOS`, `Web`) MUST remain strictly isolated from sibling backends (sharing only upward through `:common`, `:system`, `:jvm`, `:apple`, `desktop/nativeDesktopMain`, `desktop/linuxMain`, or `mobile/iosMain`).
3. **Strict Extension-to-Extension Isolation**:
   - Each extension in `:extensions:<category>:<name>` MUST only depend on `:common` and its explicitly declared upstream extension dependencies (e.g., `:extensions:ui:overlay` and `:extensions:media:mediacontrols` depend **only** on `:common`; `:extensions:media:media` depends only on `:common` and `:extensions:ui:overlay`; `:extensions:graphics:canvas2d` depends on `:extensions:graphics:gpu`, `:extensions:graphics:imageprocessing`, and `:extensions:ui:overlay`; `:extensions:graphics:canvas3d` depends on `:extensions:graphics:gpu`, `:extensions:graphics:canvas2d`, and `:extensions:ui:overlay`; `:extensions:game:engine` depends on `:extensions:game:gamepad`; `:extensions:game:engine2d` / `engine3d` build cleanly atop `:extensions:game:engine` + graphics + physics).
   - Shared-module extensions keep common contracts in `commonMain` and isolate platform bridges in `systemMain` / `jvmMain` / `appleMain` / `desktop*Main` / `mobile*Main` / `webMain`.
4. **Subagent Coordination Rule**:
   - Whenever subagents are spawned to work on `GenericUI`, each subagent MUST be explicitly instructed on and bound by Extension & Backend Isolation, with non-overlapping module/directory scopes so extension logic and backend logic never cross-contaminate.

---

## 12. No Default Tooltips & Global Pointer Drag Capture (`PointerDragManager`)

1. **Zero Default Tooltips in `GenericUI` (Consumer-Driven Tooltips Only)**:
   - `GenericUI` core components (`:common`), backend renderers (`:desktop`, `:mobile`, `:web`), and extensions (`:extensions:ui:overlay`, `:extensions:media:media`, `:extensions:media:mediacontrols`, `:extensions:ui:aichat`, etc.) MUST NEVER set default or fallback tooltips (such as defaulting `tooltip = name`, `?: impl.getText()`, `"Play"`, `"Close"`, `"Volume"`, or `"Expand / Collapse"`).
   - Tooltips (`tooltipText`) default to `null` and are strictly opt-in by consumer applications. Furthermore, native renderers must never display a tooltip that merely duplicates the control's visible text label.
2. **Global Pointer Drag Capture System (`PointerDragManager` & `PointerDragSession`)**:
   - Interactive draggable controls (`SeekBar`, `TrackSeekBar`, `SliderSeekBar`, `ExpandingVolumeSlider`, etc.) use `AbstractComponent.startPointerDrag(...)` backed by `PointerDragManager` (`com.nexustools.genericui.event.PointerDragManager`) and backend window-level pointer capture (`Win32NativeWindowHost` `SetCapture` / `HitRegion.onRelease` and `WebBrowserDomBridge` `window` `pointermove`/`pointerup`/`mousemove`/`mouseup` listeners).
   - Once a drag starts on a knob or track, pointer movement continues updating the drag when the cursor leaves the boundary of the element, and `pointerup` / `mouseup` outside the boundary of the element reliably ends and commits the drag session without auto-hiding active media overlays mid-drag.

---

## 13. Dual-Thread UI vs. Logic Thread Separation (`GenericUiThreading`) & Game Logic Thread (`PlatformGameLogicThread`)

1. **Strict Separation of UI Thread vs. Logic Thread Across All 8 Non-Web Backends**:
   - On all 8 non-Web backends (`Windows`, `MacOS`, `Gtk`, `Qt`, `Swing`, `Android`, `IOS`, `IPadOS`), `GenericUiThreading.isSplitThreadingEnabled` is `true` (and `false` on `:web`):
     - **UI Thread (`GenericUiThreading.uiDispatcher` / `runOnUi` / `runOnUiSync`)**: Exclusively performs native widget mutations, layout passes, style application, and rendering.
     - **Logic Thread (`GenericUiThreading.logicDispatcher` / `runOnLogic` / `runOnLogicAsync`)**: Executes user event callbacks (`onClick`, `onPointer*`, `onKeyDown`, etc.), `UiTimerScheduler` callbacks, and business logic with `isOnLogicThread() == true`.
   - Inside `:extensions:game:engine`, `GameLoopScheduler` and `PlatformGameLogicThread` run fixed-step / tick simulation on a dedicated **Game Logic Tick Thread** separate from both VBlank animation evaluation (`VBlankAnimationDriver`) and UI thread marshaling (`GenericUiThreading.runOnUi`).
2. **Per-Backend `UiThreadDispatcherNativeImpl` Implementations**:
   - Implemented cleanly across `:jvm` (`JvmUiThreadDispatcherBase` for Swing & Android), `:apple` (`AppleUiThreadDispatcherNativeImpl` for macOS, iOS, & iPadOS), `:desktop` (`mingwMain`, `linuxMain`), and `:web` (`WebUiThreadDispatcherNativeImpl`). `:common` never branches on `BackendTarget`.

---

## 14. Multi-Process Desktop `WebView` Architecture & Extension Isolation (`:extensions:ui:webview`)

- On desktop targets (`:desktop`), `:extensions:ui:webview` runs a Chrome-style multi-process `ChromiumBlinkEngine` + `NJS` (`lib/NJS` AsmJit/SLJIT JIT on native desktop; JVM compiler on Swing) architecture with dedicated `genericui-webview-helper` bundling (`bundleGenericUiWebViewProcess`).
- Mobile (`androidMain`, `iosMain`) and `:web` use native platform `android.webkit.WebView`, `WKWebView`, and `<iframe>` and **NEVER** depend on `NJS` or `genericui-webview-helper`.

---

## 15. Strict Core vs. Extension Isolation Rules (Summary)

- Core modules (`:common`, `:system`, `:jvm`, `:apple`, `:desktop`, `:mobile`, `:web`) **MUST NEVER** import or reference extension symbols or tags (`Alien*`, `libvlc`, `VBlankWatcher`, `ImageBuffer2DContext`, `Canvas2D`/`Canvas3D`, `GamepadManager`, `GameScene`, etc.) and **MUST NEVER** branch on `BackendTarget` strings inside `:common`.
- **No Linux GTK/Qt Cross-Dependencies & Hook Decoupling**: Extension modules (including `:extensions:graphics:canvas2d`) must communicate with `:desktop` GTK/Qt backends through `nativeDesktopMain` hook registries (e.g., `DesktopCanvasWidgetHooks`) rather than importing `com.nexustools.genericui.gtk.*` or `com.nexustools.genericui.qt.*`, so `libgenericui-gtk.so` and `libgenericui-qt.so` stay 100% isolated.
- **Native Desktop Dynamic Shared Libraries (`PROGRAM` binaries only)**: `:gradle-plugin` (`GenericUiBuildNativeSharedLibsTask` + `GenericUiKonanCompilerShim`) links core (`libgenericui.*`/`genericui.dll`), backend (`libgenericui-{macos,gtk,qt}.*`/`genericui-windows.dll`), `njs`, enabled extensions (`libgenericui-ext-<ext>.*` + `-dsl`), and tests (`genericui-tests`) as shared libraries staged in `.app/Contents/Frameworks/` (macOS) or flat alongside executables (Linux/Windows without `.dll.a` import libs), while Swing, Android, iOS/iPadOS, and Web remain integrated into their application packages.
- `:extensions:ui:dsl` depends **only on `:common`**; each other extension supplies its own companion `:dsl` module (e.g., `:extensions:game:gamepad:dsl`, `:extensions:game:engine2d:dsl`).
- Consumer apps using `:extensions:ui:alien` / `:extensions:ui:dsl` / `:extensions:game:*` opt in cleanly via `genericui { extensions { ... } }`, and opt-in native runtime bundles (ANGLE, MoltenVK, Shaderc, LibVLC, WebView helper) are staged strictly when the corresponding extension is enabled.

