# GenericUI Master Documentation Index

Welcome to the **`GenericUI`** documentation hub. All documentation across the **54-project** root repository (`52` `KNOWN_GENERICUI_MODULES` plus `:gradle-plugin` & `:idea-plugin`, and the `lib/NJS` composite build) and its core, backend, extension, test, and IDE modules is organized by category and cross-referenced.

---

## Category 1 — Core Architecture, Foundations (`:jvm` / `:apple`), Fragments, Windows, Application, & Utilities

| Document | Summary |
| :--- | :--- |
| [**Core Architecture Guide (`architecture.md`)**](architecture.md) | Consolidated 54-project module hierarchy (`:common`, `:system`, `:jvm`, `:apple`, `:desktop`, `:mobile`, `:web`, `:tests`, categorized `extensions/{graphics,physics,game,media,data,system,ui}/*`, `:gradle-plugin`, `:idea-plugin`), zero-stub fail-fast registries, `GenericUiExtensionRegistry` & `GenericUiExtensionContributor` SPI, Deferred Upward `NativeImpl` Resolution, pure retained native desktop widget hierarchies, `TitleCapabilities` single-placement rules, zero-allocation strongly-typed `ComponentStyle` (`CssProperty` bitmasks + `WebCssStyleTranslator`), system backend style restraints, 9-backend 10-op `backdropFilter` across all `StyledComponent`, `StyledContainer`, & `StyledButton` instances, custom desktop Bootstrap button widgets (`Swing`/mobile tinting), and 1-Class-Per-File standard. |
| [**Shared JVM Foundation (`jvm.md`)**](jvm.md) | The `:jvm` module (`com.nexustools.genericui:jvm`, `jvmSharedMain` / `jvmSharedTest`) consolidating Swing and Android file I/O (`JvmIoExecutor`), UI dispatchers, NIO/sockets/HTTP/WebSockets, JDBC/SQLite (`JvmDatabaseBase`), preferences, resource resolution, `JniLibraryLoader`, `JvmBackdropCapturePlanner`, and pixel tinting (`JvmControlTintPixelUtil`). |
| [**Shared Apple Foundation (`apple.md`)**](apple.md) | The `:apple` module (`com.nexustools.genericui:apple`, `appleMain` / `appleTest`) consolidating Darwin `AppleStyledSurfaceHelper`, `AppleBackdropFilterController`, `Foundation`, `CoreFoundation`, `Security` TLS SNI, `Network`, `ImageIO`, `CoreGraphics` SVG rasterization (`AppleSvgCoreGraphicsRasterizer`), `UniformTypeIdentifiers`, `UserNotifications`, and `libsqlite3` across macOS, iOS, and iPadOS. |
| [**Hybrid Responsive Layouts & Fragment Helpers (`layouts-and-helpers.md`)**](layouts-and-helpers.md) | 7 Hybrid Responsive Layout Components & Fragment Helpers (`HybridColumnLayout`/`HybridColumnFragment`, `DockWorkspace`/`DockWorkspaceFragment` with compact mobile edge drawers, `AdaptiveNavigationScaffold`, `ResponsiveMasterDetailLayout`, `HybridSplitNavigationLayout`, `CollapsibleBottomDockLayout`, `ResponsiveBentoGridLayout`), `FlexWrap.WRAP`, `MdiContainer.tileWindows`, `Table.setColumnWeights`, `LazyGrid.minColumnWidthPx`, and `:extensions:ui:dsl` builders ([`layouts-and-hybrid-ui.md`](layouts-and-hybrid-ui.md)). |
| [**Fragments, Windows (`alwaysOnTop` & `aspectRatio`), & Navigation (`fragments-and-windows.md`)**](fragments-and-windows.md) | `@MainAppFragment` & `@AppFragment`, `AppTarget` specificity resolution (`6 -> 0`), `FragmentBundle` CLI args on system targets, `IFragment`, `IWindow.isAlwaysOnTop`/`setAlwaysOnTop` & `PanelWindow` defaults, windowed client-viewport `aspectRatio` locking (`WindowAspectRatioHelper`, unconstrained when maximized/fullscreen), `TitleCapabilities`, `TabbedFragment`, `launchFragment`, 1:1 Android `AppCompatActivity` generation, `FragmentHostFragment`, and Web `#hash` + `window.history.state`. |
| [**Application, `ThemeConfig`, & `BackendTarget` Guide (`application.md`)**](application.md) | Zero-boilerplate `@MainAppFragment` bootstrapping, optional `@AppApplication`, `ThemeMode.AUTO` (default) / `DARK` / `LIGHT`, unified `ThemeConfig` & `ThemeColorSet`, `app.getPreferenceStore()`, `GenericUiExtensionRegistry` lifecycle hooks, `BackendTarget` enum & multi-target group checks across all 9 backends (`IPadOS`, `isApple`, `isIosFamily`, `isSystem`, etc.), `GenericUI.appleIconExplicitlyProvided`, and `IApplication` capabilities. |
| [**Resource System Guide (`resources.md`)**](resources.md) | Three-tier resource packaging & module-scoped `R`/`RC`/`Resource`/`GeneratedResourceRegistry` (`com.nexustools.genericui.res.R`, `com.nexustools.genericui.ext.overlay.res.R`, `com.nexustools.genericui.component.media.res.R`, `com.nexustools.genericui.ext.alien.res.R`, consumer app `R`), partitioned IDs & `ResourceTable`, web-root `./serviceworker.js` + `loader.js` cache pre-seeding, Mach-O/ELF/`RT_RCDATA` binary resource embedding, unwrapped Mach-O tray icons, Chrome-style Apple icon auto-wrapping, `CssCustomCursor`, and 9-backend translation. |
| [**Hierarchical SourceSet Setup (`sourcesets.md`)**](sourcesets.md) | Single-`commonMain` default + optional hierarchical source sets (`common` $\to$ `web` & `systemCommon` $\to$ `appleCommon`, `jvmCommon`, `desktopCommon` (`swing`, `nativeDesktopCommon` $\to$ `windows`, `macos`, `linuxCommon` $\to$ `gtk`, `kde`), & `mobileCommon` (`android`, `iosCommon` $\to$ `ios`, `ipados`)), plus generated schema/serviceworker source sets & `jni`/`cinterops` target wiring. |
| [**Components, Helper Fragments, Docking, & Styling (`components-and-docking.md`)**](components-and-docking.md) | 75+ components, `FragmentHostFragment`, `TabbedFragment`, `DockWorkspaceFragment`, `PanelWindow`, `FileBrowserDialog`, true lazy views, `ComponentStyle` (`getStyle()`) + `ComponentBootstrap` (`getBootstrap()`), `AestheticStylePolicy` 9-backend `backdropFilter` across all `StyledContainer` & `StyledButton` instances, and `LinearGradient` / `RadialGradient` background styling. |
| [**SCSS, `style.scss` vs. `theme.scss`, & Backend `@` Directives (`scss-and-styling.md`)**](scss-and-styling.md) | Authoritative guide to `style.scss` vs. raw library `theme.scss` templates, all 9 concrete (`@windows`, `@macos`, `@gtk`, `@kde`, `@swing`, `@android`, `@ios`, `@ipados`, `@web`) and 13 group (`@system`, `@desktop`, `@native-desktop`, `@linux`, `@mobile`, `@apple`, `@ios-family`/`@uikit`, `@unix`, `@posix`, `@native`, `@jvm`, `@touch`/`@touch-primary`, `@browser`) directives, `@backend(...)`/`@platform(...)`/`@target(...)` boolean expressions, automatic Web CSS stripping/unwrapping, and O(1) `BackendStyleMask` + `BlinkRuleBucketMap.forBackend(...)` partitioning. |
| [**Utilities, Async Coroutine I/O, `Database`, Networking, & `PreferenceStore` (`utils.md`)**](utils.md) | Catalog of GenericUI's 100% async non-blocking `ByteOrder`, `ByteBuffer`, `ReadableByteBuffer`, `WritableByteBuffer`, `Readable`, `Writable`, `Stream`, `StreamUtil`, `IFileSystem`, zero-parsing structured `Database`, `Network` (`openTcpSocket`, `openUdpSocket`), `HttpConnection` (`HttpRequest`, `HttpResponse`), `HttpClient`, `SocketIoClient`, streaming `BinaryJson` / `BinaryPlist`, `PreferenceStore`, `ImageCacheManager`, `DataUrlCodec`, `JniLibraryLoader`, `FastBackdropBlurKernel`, and platform utilities across `:system`, `:jvm`, `:apple`, `:desktop`, `:mobile`, and `:web`. |
| [**Media & Hardware Canvases (`media-and-canvases.md`)**](media-and-canvases.md) | Base `:common` Image API, `AbstractCanvas` (`registerVBlankRender`), `:extensions:graphics:gpu` (ANGLE / MoltenVK), `imageprocessing`, `:extensions:ui:overlay`, `canvas2d`, `canvas3d`, `:extensions:media:media`, and fail-fast `:extensions:media:mediacontrols` (`DefaultPlatformMediaControlsFactory`). |

---

## Category 2 — Build System, Gradle Plugin, & IDE Tooling

| Document | Summary |
| :--- | :--- |
| [**Gradle Plugin Guide (`gradle-plugin.md`)**](gradle-plugin.md) | Full reference for `id("com.nexustools.genericui")`: `app`, `window` (`alwaysOnTop`, `aspectRatio`), Apple icon overrides + Chrome-style squircle/full-bleed auto-wrapping, `theme`, all 9 `backends`, all 24 `extensions` (`svg()`, `ipc()`, `schema()`, `serviceWorker { ... }`, `webView { ... }`, `pip()`), `jni`/`cinterops`/`nativeLibraries` native build system, `@SchemaData`, `@BinarySerializable`, & `@MainServiceWorker`/`@ServiceWorkerMessage` codegen, `GenericUiExtensionContributor` SPI, `WindowsCoffObjectPartitioner` & `GenericUiKonanCompilerShim`, variant overrides (`6 -> 0`), `android { manifest; services }`, 3-tier `resources`, `sourceSets`, and registered tasks. |
| [**IntelliJ IDEA Plugin Guide (`idea-plugin.md`)**](idea-plugin.md) | New Project Wizard (`GenericUI.NewProject`) across 7 project templates and `Manage GenericUI Targets & Extensions` (`GenericUI.ManageTargets`) across all 9 backends and 7 extension categories (`Data`, `System`, `Graphics`, `Physics`, `Game & Input`, `Media`, `UI`, wiring `schema()` / `@SchemaData` and `serviceWorker()` / `@MainServiceWorker` + `@ServiceWorkerMessage` Gradle codegen). |
| [**Agent & Developer Rules (`../AGENTS.md`)**](../AGENTS.md) | Authoritative engineering rules and architectural invariants for contributors and AI agents. |

---

## Category 3 — Platform Backends (9 Native Targets Across `:desktop`, `:mobile`, & `:web`)

- [**Platform Backends Overview (`backends.md`)**](backends.md) · [**iPadOS Backend Guide (`ipados.md`)**](ipados.md) · [**`:apple` Shared Darwin Module (`apple.md`)**](apple.md) · [**`:jvm` Shared Java Module (`jvm.md`)**](jvm.md)
- [**Desktop Module (`../desktop/README.md`)**](../desktop/README.md) *(Contains zero VLC/media code; consolidated source sets for `Windows`, `MacOS`, `Gtk`, `Kde`, and `Swing`)*:
  - [`desktop/docs/common.md`](../desktop/docs/common.md) · [`desktop/docs/nativeCommon.md`](../desktop/docs/nativeCommon.md) · [`desktop/docs/linuxCommon.md`](../desktop/docs/linuxCommon.md)
  - [`desktop/docs/windows.md`](../desktop/docs/windows.md) · [`desktop/docs/macos.md`](../desktop/docs/macos.md) · [`desktop/docs/gtk.md`](../desktop/docs/gtk.md) · [`desktop/docs/kde.md`](../desktop/docs/kde.md) · [`desktop/docs/swing.md`](../desktop/docs/swing.md)
- [**Mobile Module (`../mobile/README.md`)**](../mobile/README.md) *(Android 1:1 `AppCompatActivity` generation, iOS `UIDeviceFamily = 1`, & iPadOS `UIDeviceFamily = 2` Stage Manager / `UIMenuSystem`)*:
  - [**iPadOS Backend Guide (`ipados.md`)**](ipados.md) · [`mobile/docs/common.md`](../mobile/docs/common.md) · [`mobile/docs/android.md`](../mobile/docs/android.md) · [`mobile/docs/ios.md`](../mobile/docs/ios.md)
- [**Web Module (`../web/README.md`)**](../web/README.md):
  - [`:web` — HTML5 DOM / Canvas2D / WebGL2 / WebGPU Backend (`../web/docs/web-backend.md`)](../web/docs/web-backend.md)
  - [`:web` — Wasm-Default + JS IR Fallback Loader, Splash Screen, & `.svg` Web Icon (`../web/docs/wasm-js-loader-and-splash.md`)](../web/docs/wasm-js-loader-and-splash.md)

---

## Category 4 — First-Party Extensions (24 Extensions + 16 Companion `:dsl` + 4 Companion `:alien` Modules)

- [**Extensions Overview (`extensions.md`)**](extensions.md) | [**Extensions Module Index (`../extensions/README.md`)**](../extensions/README.md)
- **Graphics (`:extensions:graphics:*`)**:
  - [`:extensions:graphics:gpu` (`../extensions/docs/gpu.md`)](../extensions/docs/gpu.md)
  - [`:extensions:graphics:imageprocessing` (`../extensions/docs/imageprocessing.md`)](../extensions/docs/imageprocessing.md)
  - [`:extensions:graphics:svg`, `:dsl`, & `:alien` (`../extensions/docs/svg.md`)](../extensions/docs/svg.md)
  - [`:extensions:graphics:canvas2d`, `:dsl`, & `:alien` (`../extensions/docs/canvas2d.md`)](../extensions/docs/canvas2d.md)
  - [`:extensions:graphics:canvas3d`, `:dsl`, & `:alien` (`../extensions/docs/canvas3d.md`)](../extensions/docs/canvas3d.md)
  - [`:extensions:graphics:threejs` & `:dsl` (`../extensions/docs/threejs.md`)](../extensions/docs/threejs.md)
- **Physics (`:extensions:physics:*`)**:
  - [`:extensions:physics:box2d` & `:dsl` (`../extensions/docs/box2d.md`)](../extensions/docs/box2d.md)
  - [`:extensions:physics:box3d` & `:dsl` (`../extensions/docs/box3d.md`)](../extensions/docs/box3d.md)
- **Game & Input (`:extensions:game:*`)**:
  - [**`:extensions:game:gamepad` — Unified Switch/PlayStation/Xbox, 6-Axis IMU, & UI Navigator (`gamepad.md`)**](gamepad.md)
  - [**`:extensions:game:engine`, `engine2d`, `engine3d` & `:dsl` — Dedicated Game Thread, VBlank Curves, SVG 2-Bone IK, 2D & 3D Game Engines (`game-engines.md`)**](game-engines.md)
- **Media (`:extensions:media:*`)**:
  - [`:extensions:media:media`, `:dsl`, & `:alien` — VideoPlayer, AudioPlayer, AudioAnalyzer, & Desktop VLC HDR Engine (`../extensions/docs/media.md`)](../extensions/docs/media.md)
  - [`:extensions:media:mediacontrols` — Default Real Platform Transport Controls & Keyboard Keys (`../extensions/docs/mediacontrols.md`)](../extensions/docs/mediacontrols.md)
- **Data (`:extensions:data:*`)**:
  - [`:extensions:data:schema` & `:dsl` — Reflection-free `@SchemaData` codegen (`../extensions/docs/schema.md`)](../extensions/docs/schema.md)
- **System (`:extensions:system:*`)**:
  - [`:extensions:system:ipc` — Target-Scoped Desktop Pipes/Sockets/XPC & Android `Service` Binder IPC (`../extensions/docs/ipc.md`)](../extensions/docs/ipc.md)
  - [`:extensions:system:serviceworker` — Background `@MainServiceWorker`, `ServiceWorkerFragmentBinding`, & `@ServiceWorkerMessage` IPC (`../extensions/docs/serviceworker.md`)](../extensions/docs/serviceworker.md)
- **UI, Overlays, WebView, Editor, PiP, & AI (`:extensions:ui:*`)**:
  - [`:extensions:ui:dsl` & Companion `:dsl` Modules (`../extensions/docs/dsl.md`)](../extensions/docs/dsl.md)
  - [`:extensions:ui:overlay` & `:dsl` (`../extensions/docs/overlay.md`)](../extensions/docs/overlay.md)
  - [`:extensions:ui:alien` & `:dsl` (`../extensions/docs/alien.md`)](../extensions/docs/alien.md)
  - [`:extensions:ui:webview` & `:dsl` — Isolated Alien + `:NJS` Subprocess on Desktop; Native on Mobile & Web (`../extensions/docs/webview.md`)](../extensions/docs/webview.md)
  - [`:extensions:ui:codeeditor` & `:dsl` (`../extensions/docs/codeeditor.md`)](../extensions/docs/codeeditor.md)
  - [`:extensions:ui:aichat` & `:dsl` (`../extensions/docs/aichat.md`)](../extensions/docs/aichat.md)
  - [`:extensions:ui:pip` — Cross-platform Picture-in-Picture (`../extensions/docs/pip.md`)](../extensions/docs/pip.md)
  - [**`:NJS` — Nexus JavaScript & TypeScript Engine (`../lib/NJS/README.md`)**](../lib/NJS/README.md) ([Docs Index](../lib/NJS/docs/README.md))

---

## Category 5 — Automated UI Testing (`:tests` `JestElectricRunner`) & Example Applications

- [**Testing Overview (`testing.md`)**](testing.md) | [**Tests Module Index (`../tests/README.md`)**](../tests/README.md)
- [`JestElectricRunner` BDD & Robolectric API Guide (`../tests/docs/jest-electric-runner.md`)](../tests/docs/jest-electric-runner.md)
- [Platform Test Runners & Live Visual Windows Testing (`../tests/docs/platform-test-runners.md`)](../tests/docs/platform-test-runners.md)
- [**Standalone Example Applications (`../examples/README.md`)**](../examples/README.md) — Cross-platform KMP examples across `basics/`, `layouts-and-data/`, `canvas-and-gpu/` (including `genericui-paint`), `tools-and-extensions/` (including `genericui-pad` and `genericui-browser`), and `games/` (`ui-dungeon-crawler`, `svg-skeletal-platformer-2d`, `skeletal-arena-3d`).
