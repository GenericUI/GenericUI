# GenericUI — Agent & Engineer Architectural Guidelines (`AGENTS.md`)

This document defines the **mandatory architectural rules, file layout invariants, and runtime contracts** for AI coding agents and software engineers working in the `GenericUI` repository.

- **Related Documentation**: [GenericUI README](README.md) | [Master Documentation Index](docs/README.md) | [Hierarchical SourceSet Setup](docs/sourcesets.md) | [Platform Backends](docs/backends.md) | [First-Party Extensions](docs/extensions.md) | [Gradle Plugin](docs/gradle-plugin.md) | [Fragments & Windows](docs/fragments-and-windows.md) | [Resources (`Resource.ResourceID`)](docs/resources.md) | [Application & Targets](docs/application.md) | [Testing (`JestElectricRunner`)](docs/testing.md)

---

## 1. Strict "1 Class Per `.kt` File" Rule (NON-NEGOTIABLE)

Every `.kt` source file across all core modules (`:common`, `:systemCommon`, `:desktop:common`, `:desktop:nativeCommon`, `:desktop:linuxCommon`, `:mobile:common`), all 8 platform backend modules (`:desktop:windows`, `:desktop:macos`, `:desktop:gtk`, `:desktop:qt`, `:desktop:swing`, `:mobile:android`, `:mobile:ios`, `:web`), all first-party extension modules (`:extensions:alien`, `:extensions:gpu`, `:extensions:imageprocessing`, `:extensions:overlay`, `:extensions:canvas2d`, `:extensions:canvas3d`, `:extensions:media`, `:extensions:mediacontrols`, `:extensions:webview`, `:extensions:codeeditor`, `:extensions:aichat`, `:extensions:threejs`, `:extensions:box2d`, `:extensions:box3d`, `:extensions:dsl`), `:tests`, `:gradle-plugin`, and `:idea-plugin` **MUST export at most ONE public `class`, `interface`, `object`, or `enum class`**.

- **Filename Match**: The filename MUST exactly match the single public type declared inside it (`<TypeName>.kt`).
- **No Bundled Enums or Data Classes**: Never place helper enums, data classes, listener interfaces, or configuration types in the same `.kt` file as another class. Extract every public type into its own dedicated `.kt` file in the appropriate domain subpackage.

---

## 2. Core Module Hierarchy, Single-`commonMain` Consumer Architecture, & Domain Subpackages

### A. Core & Shared Backend Module Hierarchy
GenericUI backends and extensions are organized as separate Gradle subprojects along the target hierarchy (while consumer applications use a single `src/commonMain/kotlin` source set with optional target-specific source sets such as `src/androidMain/kotlin` when platform-specific classes like Android `Service`s are needed):
- **`:common`**: 100% platform- and backend-agnostic contracts, components, fragments (`IFragment`, `Fragment`, `FragmentHostFragment`, `FragmentHostSystem`, `@AppFragment`, `@MainAppFragment`, `@AppApplication`), windows (`IWindow`, `TitleCapabilities`), applications (`IApplication`, `FragmentApplication`, `BackendTarget`, `AppTarget`), preferences (`PreferenceStore`), resources (`ResourceID`, `ResourceResolver`), styling, and image APIs.
- **`:systemCommon`**: Shared across all 7 non-Web system targets (`Windows`, `MacOS`, `Gtk`, `Qt`, `Swing`, `Android`, `IOS`), including JVM and native targets.
- **`:desktop:common`**: Shared across all 5 desktop targets (`Windows`, `MacOS`, `Gtk`, `Qt`, `Swing`).
- **`:desktop:nativeCommon`**: Shared across all 4 Kotlin/Native desktop targets (`Windows`, `MacOS`, `Gtk`, `Qt`).
- **`:desktop:linuxCommon`**: Shared between `:desktop:gtk` and `:desktop:qt`.
- **`:mobile:common`**: Shared between `:mobile:android` and `:mobile:ios`.
- **Concrete Backends**: `:desktop:windows`, `:desktop:macos`, `:desktop:gtk`, `:desktop:qt`, `:desktop:swing`, `:mobile:android`, `:mobile:ios`, `:web`.

### B. Annotation-Driven Fragment & Application Discovery, 1:1 Android `AppCompatActivity` Codegen, & `Resource.ResourceID`
1. **`@MainAppFragment`, `@AppFragment`, & `@AppApplication` Discovery**:
   - Consumer projects annotate their fragments in `src/commonMain/kotlin` with `@MainAppFragment(targets = [AppTarget...])` and `@AppFragment`, and optionally annotate a custom `FragmentApplication` subclass with `@AppApplication` (or let the Gradle plugin automatically generate `<FragmentName>Application : FragmentApplication` from the resolved `@MainAppFragment`).
   - Consumer projects configure application metadata, window defaults, and theme in `build.gradle.kts` via `genericui { app { ... } window { ... } theme { ... } backends { all() } }` without writing per-backend `*Main` directories or manual `main()` entry points.
   - `AppTarget` includes all common target scopes (`COMMON`, `SYSTEM_COMMON`, `DESKTOP_COMMON`, `NATIVE_DESKTOP_COMMON`, `LINUX_COMMON`, `MOBILE_COMMON`) and all 8 concrete backend targets (`WINDOWS`, `MACOS`, `GTK`, `QT`, `SWING`, `ANDROID`, `IOS`, `WEB`). The Gradle plugin resolves the most specific `@MainAppFragment` per target (`backend (5) > linuxCommon (4) > nativeDesktopCommon (3) > desktopCommon / mobileCommon (2) > systemCommon (1) > common (0)`) and fails the build if more than one `@MainAppFragment` ties at the highest specificity for any enabled target.
2. **1:1 Generated Android `AppCompatActivity` Classes**:
   - On `:mobile:android`, the Gradle plugin (`GenericUiGenerateAndroidActivitiesTask`) generates a concrete `AppCompatActivity` subclass (`class <FragmentName>Activity : GenericUiFragmentActivity()`, where `GenericUiFragmentActivity` extends `androidx.appcompat.app.AppCompatActivity`) mapped **1:1** to every `@AppFragment` and `@MainAppFragment`, plus the corresponding `<activity>` entries in `AndroidManifest.xml` (with `MAIN`/`LAUNCHER` on the resolved Android `@MainAppFragment`).
3. **`FragmentBundle` Command-Line Arguments on System Targets**:
   - On all system targets, the initial `FragmentBundle` passed to `onCreate(savedState: FragmentBundle?)` contains the process command-line arguments (`KEY_COMMAND_LINE_ARGS = "genericui:command_line_args"`), queryable via `savedState.hasCommandLineArgs()`, `savedState.getCommandLineArgs()`, and `savedState.getCommandLineArgsArray()`.
4. **Android-Style `Resource` / `Resource.ResourceID` System**:
   - The Gradle plugin supports creating and translating Android resource files (`drawable/`, `mipmap/`, `values/strings.xml`, `values/colors.xml`, `values/dimens.xml`, `raw/`, `font/`, `xml/`) in both shared modules and app modules, generating `<package>.Resource` with `Resource.ResourceID` (`com.nexustools.genericui.resources.ResourceID`), which translates to Web URLs on `:web`, embedded `.exe` Win32 `.rc`/`.res` resources on `:desktop:windows`, `R.*` IDs on `:mobile:android`, `NSBundle` resources on `:desktop:macos` & `:mobile:ios`, `GResource`/`QResource` on `:desktop:gtk` & `:desktop:qt`, and JAR classpath resources on `:desktop:swing`.

### C. Core `:common` Domain Subpackages
All 8 platform backend modules mirror the subpackage hierarchy of `com.nexustools.genericui` in `:common`:

| # | Subpackage (`com.nexustools.genericui.*`) | Domain Responsibility |
| :- | :--- | :--- |
| 1 | `core` & `component.shell` | `IApplication`, `Application`, `FragmentApplication`, `IWindow`, `Window`, `MdiWindow`, `MdiContainer`, `AbstractComponent`, `NativeFactory`, `NativeRegistry` |
| 2 | `component.fragment` & `component.fragment.helper` | `IFragment`, `Fragment`, `FragmentBundle` (with CLI args on system targets), `FragmentRegistry`, `FragmentLaunchCoordinator`, `@AppFragment`, `@MainAppFragment`, `@AppApplication`, **`FragmentHostFragment`**, **`FragmentHostSystem`**, `FragmentTransitionSpec` (including `FadeExpandShrink`), `FragmentNavigationChromeConfig`, `AdaptiveSplitTabFragment`, `DockWorkspaceFragment`, `OverlayDockWorkspaceFragment`, `CenteredMenuPageFragment` |
| 3 | `window` & `enums.system` | `TitleCapabilities`, `TitleBar`, `WindowConfiguration`, `BackendTarget`, `AppTarget` |
| 4 | `resources` & `component.system` | `ResourceID`, `ResourceType`, `ResourceResolver`, `PreferenceStore`, `PreferenceChangeListener`, `PreferenceCodec`, `PreferenceObjectCodec` |
| 5 | `nativeimpl` | `WindowNativeImpl`, `ContainerNativeImpl`, `PreferenceStoreNativeImpl`, `ResourceResolverNativeImpl`, and per-component `*NativeImpl` peer interfaces |
| 6 | `enums.bootstrap` & `style` | Type-safe Bootstrap tokens, `CssStyleSheet`, `PrecompiledStyleSheet`, `ComputedStyle`, `BootstrapClassMapper`, `LiveScssStyleEngine` |
| 7 | `event` | `UiEvent`, `ClickEvent`, `PointerEvent` (including `onPointerEnter` / `onPointerLeave`), `KeyEvent`, `WheelEvent`, `FocusEvent`, `ResizeEvent`, `WindowEvent` |
| 8 | `component.layout`, `component.surface`, `component.htmlcompat` | `Container`, `Box`, `Row`, `Col`, `FlexBox`, `HStack`, `VStack`, `ScrollView`, `SplitPane`, `Card`, `Accordion`, and HTML tag wrapper classes (`Div`, `Header`, `Footer`, `Main`, `Section`, `H1`..`H6`, `Span`, `P`, etc.) |
| 9 | `component.typography`, `component.content`, `component.feedback` | `Heading`, `Text`, `Label`, `Badge`, `ImageView`, `Icon`, `VectorGraphicView`, `MarkdownView`, `Table`, `Alert`, `ProgressBar`, `Spinner`, `Toast` |
| 10 | `component.action`, `component.navigation`, `component.tabs` | `Button`, `IconButton`, `ToggleButton`, `Toolbar`, `MenuBar`, `Menu`, `MenuItem`, `ContextMenu`, `Navbar`, `TabContainer`, `Tab`, `CloseableTab`, `BottomNavigationBar` |
| 11 | `component.form` | `TextField`, `TextArea`, `PasswordField`, `NumberField`, `SearchField`, `CheckBox`, `RadioButton`, `RadioGroup`, `Switch`, `RangeSlider`, `Select`, `ColorPicker`, `DatePicker` |
| 12 | `component.lazy` | True Lazy Views (`LazyList`, `LazyColumn`, `LazyRow`, `LazyGrid`, `LazyGridTree`, `ViewHolderPool`), `TreeView`, `CategorizedTreeView` |
| 13 | `component.dock` | `DockWorkspace`, `DockArea`, `DockGroup`, `DockPanel`, `PanelWindow`, `ToolPalette`, `ColorSwatchBox` |
| 14 | `component.dialog` | `Dialogs`, `DialogWindow`, `Modal`, `FileBrowserRequest`, `FileTypeFilter`, `SelectedFile` |
| 15 | `image` & `canvas` | **Base Full Image API** (`ImageBuffer`, `AnimatedImage`, `ImageLoader`) & `AbstractCanvas` |

---

## 3. Modular Two-Tier Extension Architecture (Project-of-Projects vs. Single Project)

Extensions in GenericUI (`:extensions:{alien,gpu,imageprocessing,overlay,canvas2d,canvas3d,media,mediacontrols,webview,codeeditor,aichat,threejs,box2d,box3d,dsl}`) are structured in one of two ways depending on whether they require platform-specific native/JVM/Web implementations:

### A. Project-of-Projects Extensions (`:extensions:<name>:{common,systemCommon,desktop:common,desktop:nativeCommon,desktop:<backend>,mobile:common,mobile:<backend>,web}`)
Used when an extension requires native C/C++/ObjC (`:desktop:*`, `:mobile:*`), Java/LWJGL (`:desktop:swing`), or Web DOM/WebGL/WebGPU (`:web`) implementations:
1. **`:extensions:gpu`** (`:extensions:gpu:{common,systemCommon,desktop:nativeCommon,desktop:swing,mobile:common,mobile:{android,ios},web}`):
   - Provides unified GPU context, shader, VBlank synchronization (`VBlankWatcher`), and offscreen compute/render management (`GpuDevice`, `GpuOffscreenContext`, `VKContext` primary with SPIR-V & MoltenVK on Apple, `GLContext` fallback with Google ANGLE on Windows).
   - Relied upon by `:extensions:imageprocessing`, `:extensions:canvas3d`, and `:extensions:threejs`.
2. **`:extensions:imageprocessing`** (`:extensions:imageprocessing:{common,systemCommon,web}`):
   - Relies on `:extensions:gpu` + base `:common` Image API. Provides `ImageBuffer2DContext` and offscreen in-memory GPU-accelerated image manipulation (`GpuImageProcessor`, `GpuImageFilterChain`, `GpuImageKernel`, `ImageHistogramResult`).
3. **`:extensions:canvas2d`** (`:extensions:canvas2d:{common,systemCommon,desktop:nativeCommon,desktop:{windows,macos,gtk,qt,swing},mobile:common,mobile:{android,ios},web}` + `:extensions:canvas2d:dsl`):
   - Relies on `:extensions:imageprocessing` and `:extensions:overlay`. Provides `Canvas2D`, `Canvas2DContext`, `Canvas2DExtSurface`, `CurveEditorExt`, `Canvas2DOverlayHost`, and `ScrollCanvas2DOverlayHost` (extending `:common`'s `AbstractCanvas`).
4. **`:extensions:canvas3d`** (`:extensions:canvas3d:{common,systemCommon,desktop:nativeCommon,desktop:{windows,macos,gtk,qt,swing},mobile:common,mobile:{android,ios},web}` + `:extensions:canvas3d:dsl`):
   - Relies on `:extensions:gpu`, `:extensions:canvas2d`, and `:extensions:overlay`. Provides `GLCanvas` (OpenGL ES 3.0+ with Google ANGLE on Windows), `VKCanvas` (Vulkan 1.2+ with MoltenVK on Apple), `Canvas3DOverlayHost`, and `ScrollCanvas3DOverlayHost`.
5. **`:extensions:media`** (`:extensions:media:{common,systemCommon,desktop:nativeCommon,desktop:{windows,macos,gtk,qt,swing},mobile:common,mobile:{android,ios},web}` + `:extensions:media:dsl`):
   - Depends **only** on `:common` and `:extensions:overlay` (zero dependencies on `:extensions:gpu` or `:extensions:canvas2d`). Provides `VideoPlayer` (with `VideoOverlayHost`, `VideoPlayerOverlayBinder`, `attachVideoOverlay`, and uniform codec capability reporting), `AudioPlayer` (`AudioPlayerExt`), `AudioAnalyzer` (`AudioAnalyzerExt`), and `WebVttSrtParser`.
   - `:extensions:media:desktop:nativeCommon` provides shared native `libvlc` bindings (`LibVlcBindings`, `DesktopLibVlcVideoPlayerImpl`, `DesktopLibVlcAudioPlayerImpl`, `DesktopLibVlcAudioAnalyzerImpl`) used by `:extensions:media:desktop:{qt,gtk,windows,macos}`, `:extensions:media:desktop:swing` uses `libvlcj` (`uk.co.caprica:vlcj`), and `:extensions:media:mobile:{android,ios}` & `:extensions:media:web` use native platform media components.
6. **`:extensions:mediacontrols`** (`:extensions:mediacontrols:{common,systemCommon,desktop:nativeCommon,desktop:{windows,macos,gtk,qt,swing},mobile:common,mobile:{android,ios},web}` + `:extensions:mediacontrols:dsl`):
   - Standalone system media controls & hardware keyboard media key hooks extension (`com.nexustools.genericui.ext.mediacontrols`), opted into via `extensions { mediaControls() }`.
   - **Zero Extension Dependencies (`:common` Only)**: Does **not** depend on `:extensions:media`, `:extensions:overlay`, or any other extension so it can be used for any purpose.
   - **System Targets Only — Never Implements an Android `Service` or Mobile Helpers**:
     - **Windows (`:extensions:mediacontrols:desktop:windows`)**: Displays transport buttons (`Previous`, `Play/Pause`, `Next`) directly in the Windows Taskbar hover thumbnail preview via `ITaskbarList3` (`ThumbBarAddButtons` / `ThumbBarUpdateButtons`), updates Windows System Media Transport Controls (SMTC), and hooks `WM_APPCOMMAND` / `VK_MEDIA_*` hardware keyboard media buttons.
     - **macOS (`:extensions:mediacontrols:desktop:macos`)**: Integrates with `MPRemoteCommandCenter` + `MPNowPlayingInfoCenter` (Control Center, Now Playing widget, Touch Bar) and hooks `NSEvent` `NSSystemDefined` (`NX_KEYTYPE_PLAY` / `NEXT` / `PREVIOUS`) keyboard media keys.
     - **Linux (`:extensions:mediacontrols:desktop:{gtk,qt}`), `:extensions:mediacontrols:desktop:swing`, & `:extensions:mediacontrols:web`**: Hooks `XF86Audio*` / multimedia keys, MPRIS, and `navigator.mediaSession`.
     - **Mobile (`:extensions:mediacontrols:mobile:{android,ios}`)**: Lightweight no-op stub (mobile apps manage their own Android `Service` / `MediaSession` in `androidMain`).
7. **`:extensions:webview`** (`:extensions:webview:{common,systemCommon,desktop:common,desktop:nativeCommon,desktop:{windows,macos,gtk,qt,swing},mobile:common,mobile:{android,ios},web}` + `:extensions:webview:dsl`):
   - Dedicated Project-of-Projects `WebView` extension.
   - On **Desktop builds** (`:extensions:webview:desktop:{windows,macos,gtk,qt,swing}`), uses the **Chromium** project (`ChromiumBlinkEngine`) for HTML/CSS layout & rendering coupled with **`NJS`** (`lib/NJS`) with its **AsmJit / SLJIT Native JIT Engine** (`com.nexustools.njs.jit.*`).
   - On **Mobile (`:extensions:webview:mobile:android` `android.webkit.WebView`, `:extensions:webview:mobile:ios` `WKWebView`) and `:extensions:webview:web` (`<iframe>`)**, uses the native platform implementation and **NEVER uses `NJS`**.
8. **`:extensions:alien`** (`:extensions:alien:{common,systemCommon,web}` + `:extensions:alien:dsl`):
   - Self-rendered Bootstrap 5.3 `Canvas2D` UI engine (`AlienFactory`, `AlienContainer`, `AlienFragment`, `AlienScrollFragment`, `UnalienContainer`, `AlienRepaintManager` with dirty update regions) on System targets (`:extensions:alien:systemCommon`), and zero-overhead native DOM pass-through on Web (`:extensions:alien:web`). Relies on `:extensions:canvas2d`.

### B. Single Multiplatform Project Extensions (`:extensions:<name>` + optional `:extensions:<name>:dsl`)
Used when an extension is implemented in 100% shared KMP code atop `:common` and other extensions:
9. **`:extensions:overlay`** (depends **only** on `:common`; auto-enabled by `media()`, `canvas2d()`, and `canvas3d()`): Shared customizable overlay framework (`OverlayHost`, `OverlayHostBox`, `AbstractOverlay`, `PresetOverlay`, `OverlaySlot`, `OverlayState`, `OverlayActions`, `AutoHideController`, `OverlayInputRouter`, `MobileOverlayPreset`, `WebSystemOverlayPreset`, `OverlayPresetResolver`) plus `:extensions:overlay:dsl`.
10. **`:extensions:codeeditor`** (relies on `:extensions:canvas2d`): Extension-only IDE-style code editor (`IdeCodeEditor`, `CodeLanguage`, `CodeEditorTheme`, `CodeDiagnostic`, `ScssLanguageSupport`) plus `:extensions:codeeditor:dsl`.
11. **`:extensions:aichat`** (relies on `:extensions:codeeditor` $\rightarrow$ `canvas2d`): Reusable AI chat drawer and multi-agent orchestrator (`AiChatContainer`, `AIChatDrawer`, `AIChatAgentOrchestrator`) plus `:extensions:aichat:dsl`.
12. **`:extensions:threejs`** (relies on `:extensions:gpu` + `:extensions:canvas3d`): Three.js-style 3D scene graph (`ThreeSceneCanvas`, `ThreeGLCanvas`, `ThreeVKCanvas`) plus `:extensions:threejs:dsl`.
13. **`:extensions:box2d`** (relies on `:extensions:canvas2d` + `:extensions:canvas3d` $\rightarrow$ `gpu`): 2D rigid-body physics engine (`World2D`, `Body2D`, `Box2DDebugDrawer`, `Box2DCanvas`) plus `:extensions:box2d:dsl`.
14. **`:extensions:box3d`** (relies on `:extensions:canvas3d` + `:extensions:threejs` $\rightarrow$ `gpu`): 3D rigid-body physics engine (`Box3DWorld`, `Box3DThreeJsBridge`) plus `:extensions:box3d:dsl`.
15. **`:extensions:dsl`** (relies **only** on `:common`): `kotlinx.html`-style component builders and MVC classes (`MvcModel`, `MvcView`, `MvcController`).

---

## 4. Base `:common` Full Image API (`ImageCodecNativeImpl` + `libjpeg`/`libpng`/`libwebp`) & Offscreen GPU `:extensions:imageprocessing`

- **Base `:common` Image API (`com.nexustools.genericui.image.*`)**:
  - `ImageLoader` decodes and encodes still and animated images (`PNG`, `JPEG`, `WEBP`, `GIF`, `APNG`, `BMP`, `ICO`, `TIFF`, `SVG`, `PPM`) into `ImageBuffer` / `AnimatedImage` (`List<ImageFrame>`) with full `PixelFormat` (`RGBA_8888`, `BGRA_8888`, `RGB_888`, `RGBA_F16`, `RGBA_F32`, `GRAY_8`), `ImageColorSpace` (`SRGB`, `DISPLAY_P3`, `REC_2020_PQ`, `REC_2020_HLG`, `LINEAR_SRGB`), and `ImageMetadata` (EXIF orientation, DPI, ICC profile).
  - **Native Image Loader First, Dedicated `libjpeg`/`libpng`/`libwebp` Codecs Where Needed (`ImageCodecNativeImpl`)**:
    - **Java / `:desktop:swing`**: Uses `javax.imageio.ImageIO` & `BufferedImage` (`BufferedImage.TYPE_INT_ARGB` / `Raster`).
    - **Android (`:mobile:android`)**: Uses `android.graphics.BitmapFactory` and `android.graphics.ImageDecoder` (including animated WebP/GIF `AnimatedImageDrawable`).
    - **Web (`:web`)**: Uses browser-native `window.createImageBitmap`, `HTMLImageElement`, and `OffscreenCanvas` / `<canvas>` pixel extraction.
    - **macOS & iOS (`:desktop:macos`, `:mobile:ios`)**: Uses Apple `ImageIO` (`CGImageSource`, `CGImageDestination`, `CGBitmapContext`).
    - **Desktop (`:desktop:windows`, `:desktop:gtk`, `:desktop:qt`, `:desktop:nativeCommon`) & Fallback Codecs**: Uses OS native loaders (`WIC` `IWICImagingFactory` on Windows, `GdkPixbuf` on GTK, `QImage`/`QImageReader` on Qt) and provides dedicated built-in codecs (`LibJpegCodec` via `libjpeg`/`libjpeg-turbo`, `LibPngCodec` via `libpng`, `LibWebpCodec` via `libwebp`, and `BmpPpmCodec`) whenever a format or headless/raw environment requires direct codec execution.
- **Offscreen GPU Image Processing (`:extensions:imageprocessing`)**:
  - Uses `:extensions:gpu`'s `GpuOffscreenContext` (Vulkan compute/fragment primary, OpenGL ES fallback) to process `ImageBuffer` instances offscreen in memory without requiring an on-screen window or canvas.

---

## 5. Deferred Upward `NativeImpl` Resolution Contract

1. **Pure Kotlin Construction**: Calling `val button = Button("Save")` or `val box = VStack()` initializes state in pure common Kotlin (`nativeImpl == null`). It MUST NOT throw an exception if no `Application` or `Window` is active yet.
2. **Upward Factory Discovery**: When `parent.addChild(child)` is invoked, `child` calls `resolveBackendFactoryUpward()`, walking up `parent -> parent.parent -> ... -> RootContainer -> Window -> Application` (with fallback to `NativeBackendRegistry.activeFactory`).
3. **Downward Realization Cascade**: When a detached subtree is attached to a live `Window` or `Fragment`, `realizeNativeTree(factory)` cascades **downward** across the entire subtree in pre-order.

---

## 6. `TitleBar`, `TitleCapabilities`, & Single-Placement Custom Components

- `TitleBar` (`com.nexustools.genericui.window.TitleBar`) is applied via `window.setTitleBar(titleBar)` or `fragment.setTitleBar(titleBar)`.
- `IWindow.titleCapabilities` (`TitleCapabilities`) reports whether a titlebar is supported (`isSupported`), required (`isTitleBarRequired`: `true` on native desktop, `false` on Android, iOS, and Web), and whether custom components can be embedded directly into the titlebar (`canEmbedComponentsInTitleBar`).
- **Single-Placement Rule**: Components mounted inside a `TitleBar` (`mountedInTitleBar = true`) MUST only be rendered once (never duplicated in both the titlebar and body container).
- **Platform Chrome Rules**:
  - **Android / iOS (`:mobile:android`, `:mobile:ios`)**: Setting a `TitleBar` **or** a `MenuBar` creates a navbar (native iOS layout on `:mobile:ios`); if neither is set, no navbar is added.
  - **Web (`:web`)**: `MenuBar` only $\rightarrow$ menubar; `TitleBar` only $\rightarrow$ navbar; `TitleBar` + `MenuBar` $\rightarrow$ Bootstrap navbar with title/icon on the left and menu on the right; neither $\rightarrow$ no navbar.
  - **macOS Global System Menu Bar Rule (`:desktop:macos`)**: On `:desktop:macos`, `MenuBar` / `TitleBar.menus` MUST be bound to the **macOS system menu bar** (`NSApplication.sharedApplication.mainMenu`) at the top of the display.

---

## 7. `IFragment`, `FragmentHostFragment`, `FragmentHostSystem`, & Helper Fragments

- `Fragment` implements `IFragment` and mirrors Android's `Activity`/`Fragment` lifecycle (`onCreate`, `onStart`, `onResume`, `onPause`, `onStop`, `onSaveInstanceState`, `onDestroy`), with `launchFragment(FragmentClass, LaunchArgs)` delivering `LaunchArgs` (`FragmentBundle`) to `onCreate(savedState: FragmentBundle?)`.
- **`FragmentHostFragment` & `FragmentHostSystem` (`com.nexustools.genericui.component.fragment.helper`)**:
  - Standardizes multi-page and multi-screen host fragments.
  - Supports **fully customizable transitions** (`FragmentTransitionSpec` / `FragmentTransitionController`), including `FadeExpandShrink` (`fadein/expand` + `fadeout/shrink` with outgoing/incoming co-mounting).
  - On **`:web`**, `FragmentHostSystem` tracks pages via URL `#hashes` (`window.location.hash`) and saves/restores fragment states (`FragmentBundle`) via the browser's native page state API (`window.history.state` + `popstate`) so the browser Back/Forward buttons work natively.
  - On **desktop platforms supporting custom titlebars** (`titleCapabilities.canEmbedComponentsInTitleBar`), optional Back/Forward buttons and custom components are embedded in the `TitleBar`; otherwise, an optional `Navbar` is added below the titlebar.
- Additional helper fragments (`AdaptiveSplitTabFragment`, `DockWorkspaceFragment`, `OverlayDockWorkspaceFragment`, `CenteredMenuPageFragment`) standardize responsive and dockable workspaces.

---

## 8. Native File & Folder Browser (`FileBrowserDialog`), `PreferenceStore`, & True Lazy Views Across All 8 Platforms

- **`FileBrowserDialog` & `Dialogs`**: Supports `OPEN_SINGLE_FILE`, `OPEN_MULTIPLE_FILES`, `SAVE_FILE`, and `SELECT_FOLDER` with `FileTypeFilter` and `FileBrowserCallback` (`onFilesSelected` / `onCancelled`). On `:web`, uses an invisible, un-interactable `<input type="file">` (`webkitdirectory` for folder mode, `accept` for `FileTypeFilter`); on `:mobile:android`, launches the native Android system file browser (`ACTION_OPEN_DOCUMENT`, `ACTION_CREATE_DOCUMENT`, `ACTION_OPEN_DOCUMENT_TREE`) via `GenericUiFragmentActivity` `ActivityResultLauncher`.
- **`PreferenceStore`**: Cross-platform preference utility (`com.nexustools.genericui.component.system.PreferenceStore`) backed by Binary JSON (`.bson`) / JSON config files on Windows/Linux, `bplist00` `.plist` on macOS/iOS, `SharedPreferences` on Android, `java.util.prefs.Preferences` + JSON on Swing, and `localStorage` on Web, with native change listeners (`PreferenceChangeListener`). All GenericUI apps use `PreferenceStore` instead of custom preference storage.
- **True Lazy Views (`LazyList`, `LazyColumn`, `LazyRow`, `LazyGrid`, `LazyGridTree`, `TreeView`)**: Dynamically load and bind items (`onBind`) only within `[firstVisibleIndex .. lastVisibleIndex]` and immediately unload/recycle (`onUnbind` $\rightarrow$ `ViewHolderPool`) items as they scroll out of view on all 8 platforms.

---

## 9. `:extensions:webview`, `lib/NJS` Submodule Workflow, & Strict Desktop-Only `NJS` Rule

1. **`WebView` Is an Extension-Only Module (`:extensions:webview`)**:
   - `WebView` (`WebView`, `WebViewController`, `WebViewJsBridge`) lives in `:extensions:webview` and is opted into via `genericui { extensions { webView() } }`.
2. **STRICT RULE — DO NOT USE `NJS` ON iOS, ANDROID, OR WEB**:
   - **Mobile (`:mobile:android`, `:mobile:ios`) & `:web`**: `:extensions:webview:mobile:{common,android,ios}` and `:extensions:webview:web` MUST use the native platform implementation (`android.webkit.WebView` on `:mobile:android`, `WKWebView` on `:mobile:ios`, and `<iframe>` on `:web`) and **MUST NEVER depend on or invoke `NJS`**.
   - **Desktop (`:desktop:{common,nativeCommon,windows,macos,gtk,qt,swing}`)**: `:extensions:webview:desktop:{common,nativeCommon,windows,macos,gtk,qt,swing}` use the **Chromium** project (`ChromiumBlinkEngine`) for HTML/CSS layout & rendering coupled with **`NJS`** (`lib/NJS`, included via `includeBuild("lib/NJS")`) for the JavaScript runtime.
   - `NJS` supports Desktop Native targets (`mingwX64`, `linuxX64`, `linuxArm64`, `macosArm64`, `macosX64`) alongside `jvm` (and strictly NOT iOS, Android, or Web):
     - **Desktop Native Builds (`nativeMain`)**: `NJS` uses its **AsmJit / SLJIT Native JIT Engine** (`com.nexustools.njs.jit.*` in `nativeMain` only).
     - **Desktop JVM Builds (`jvmMain` / `:desktop:swing`)**: `NJS` uses its own **Java Compiler System** (`JavaSourceTranspiler` + `JavaTranspiler` backed by `javax.tools.ToolProvider.getSystemJavaCompiler()`) with no custom JIT engine needed on JVM.
   - Preserve the `lib/NJS` directory symlink/submodule during development.

---

## 10. `:extensions:dsl` (`kotlinx.html`-Style MVC & Component DSL) Boundary Rule

1. **`:common`-Only Scope**:
   - `:extensions:dsl` (`com.nexustools.genericui.ext.dsl`) depends strictly on `:common` and provides `MvcModel`, `MvcView`, `MvcController`, and `kotlinx.html`-style in-place component builders (`div`, `row`, `col`, `h1`..`h6`, `p`, `span`, `button`, `select { option(...) }`, `table { tr { th(...); td(...) } }`, etc.) for `:common` components only.
   - **Never** add dependencies or builders for other extensions inside `:extensions:dsl`; other extensions provide their own `:extensions:<name>:dsl` extension modules (including `:extensions:overlay:dsl`, `:extensions:canvas2d:dsl`, `:extensions:canvas3d:dsl`, `:extensions:media:dsl`, `:extensions:mediacontrols:dsl`, `:extensions:webview:dsl`, `:extensions:alien:dsl`, `:extensions:codeeditor:dsl`, `:extensions:aichat:dsl`, `:extensions:threejs:dsl`, `:extensions:box2d:dsl`, and `:extensions:box3d:dsl`).

---

## 11. Strict Extension & Backend Isolation (NON-NEGOTIABLE)

All agents, subagents, and engineers MUST strictly enforce **Extension Isolation** and **Backend Isolation** across the entire `GenericUI` repository:

1. **Zero Extension Leakage into Core Modules (`:common`, `:systemCommon`, `:desktop`, `:mobile`, `:web`, `:gradle-plugin`)**:
   - `:common`, `:systemCommon`, `:desktop` (`:desktop:common`, `:desktop:nativeCommon`, `:desktop:linuxCommon`, `:desktop:windows`, `:desktop:macos`, `:desktop:gtk`, `:desktop:qt`, `:desktop:swing`), `:mobile` (`:mobile:common`, `:mobile:android`, `:mobile:ios`), and `:web` **MUST NEVER** import, reference, hardcode, or include any classes, tag names, CSS selectors, or logic belonging to any extension (`:extensions:alien`, `:extensions:gpu`, `:extensions:imageprocessing`, `:extensions:overlay`, `:extensions:canvas2d`, `:extensions:canvas3d`, `:extensions:media`, `:extensions:mediacontrols`, `:extensions:webview`, `:extensions:codeeditor`, `:extensions:aichat`, `:extensions:threejs`, `:extensions:box2d`, `:extensions:box3d`, `:extensions:dsl`).
   - For example:
     - **No Alien logic/tags in `:common`, `:systemCommon`, `:desktop`, `:mobile`, or `:web`**: `AlienFragment`, `AlienContainer`, `UnalienContainer`, `AlienImpl`, `AlienBootstrapPainter`, `genericui:alien-fragment`, `genericui:alien-container`, and `genericui:unalien-container` belong strictly inside `:extensions:alien`.
     - **No VLC/Media or MediaControls logic in `:desktop` or `:common`**: All VLC/media decoding, shaders, and playback pipelines belong strictly inside `:extensions:media`, and all OS media transport / taskbar preview / keyboard media key hooks belong strictly inside `:extensions:mediacontrols`.
     - **No VBlank/GPU or 2D/3D Canvas rendering pipelines in `:common` or core backends**: `VBlankWatcher` lives in `:extensions:gpu`; `ImageBuffer2DContext` lives in `:extensions:imageprocessing`; `Canvas2D` and `Canvas3D` live in `:extensions:canvas2d` and `:extensions:canvas3d`.
2. **Strict Backend Isolation**:
   - `:common` MUST remain 100% platform- and backend-agnostic. Never place platform-specific code or backend-specific assumptions inside `:common`; communicate between `:common` and backends strictly via generic capability properties and methods on `*NativeImpl` and `NativeFactory` interfaces.
   - Each backend module (`:desktop:windows`, `:desktop:macos`, `:desktop:gtk`, `:desktop:qt`, `:desktop:swing`, `:mobile:android`, `:mobile:ios`, `:web`) MUST remain strictly isolated within its own module and shared parent (`:systemCommon`, `:desktop:common`, `:desktop:nativeCommon`, `:desktop:linuxCommon`, or `:mobile:common`), never referencing or coupling to another sibling backend.
3. **Strict Extension-to-Extension Isolation**:
   - Each extension in `:extensions:<name>` MUST only depend on `:common` and its explicitly declared upstream extension dependencies (e.g., `:extensions:overlay` and `:extensions:mediacontrols` depend **only** on `:common`; `:extensions:media` depends only on `:common` and `:extensions:overlay`; `:extensions:canvas2d` depends on `:extensions:imageprocessing` and `:extensions:overlay`; `:extensions:canvas3d` depends on `:extensions:gpu`, `:extensions:canvas2d`, and `:extensions:overlay`; `:extensions:alien` $\rightarrow$ `:extensions:canvas2d` $\rightarrow$ `:extensions:imageprocessing` $\rightarrow$ `:extensions:gpu`).
   - Within Project-of-Projects extensions, `:extensions:<name>:common` MUST remain 100% platform-agnostic, while platform-specific bridges stay strictly inside their respective target submodules.
4. **Subagent Coordination Rule**:
   - Whenever subagents are spawned to work on `GenericUI`, each subagent MUST be explicitly instructed on and bound by Extension & Backend Isolation, with non-overlapping module/directory scopes so extension logic and backend logic never cross-contaminate.
