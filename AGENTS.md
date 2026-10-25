# GenericUI — Agent & Engineer Architectural Guidelines (`AGENTS.md`)

This document defines the **mandatory architectural rules, file layout invariants, and runtime contracts** for AI coding agents and software engineers working in the `GenericUI` (`D:\IdeaProjects\GenericUI`) repository.

- **Related Documentation**: [GenericUI README](README.md) | [Master Documentation Index](docs/README.md) | [Hierarchical SourceSet Setup](docs/sourcesets.md) | [Platform Backends](docs/backends.md) | [First-Party Extensions](docs/extensions.md) | [Gradle Plugin](docs/gradle-plugin.md) | [Testing (`JestElectricRunner`)](docs/testing.md)

---

## 1. Strict "1 Class Per `.kt` File" Rule (NON-NEGOTIABLE)

Every `.kt` source file across all core modules (`:common`, `:desktop:shared`, `:mobile:shared`), all 8 platform backend modules (`:desktop:windows`, `:desktop:macos`, `:desktop:gtk`, `:desktop:qt`, `:desktop:swing`, `:mobile:android`, `:mobile:ios`, `:web`), all 11 first-party extension modules (`:extensions:gpu`, `:extensions:imageprocessing`, `:extensions:canvas2d`, `:extensions:canvas3d`, `:extensions:media`, `:extensions:webview`, `:extensions:codeeditor`, `:extensions:aichat`, `:extensions:threejs`, `:extensions:box2d`, `:extensions:box3d`), and `:gradle-plugin` **MUST export at most ONE public `class`, `interface`, or `enum class`**.

- **Filename Match**: The filename MUST exactly match the single public type declared inside it (`<TypeName>.kt`).
- **No Bundled Enums or Data Classes**: Never place helper enums, data classes, listener interfaces, or configuration types in the same `.kt` file as another class. Extract every public type into its own dedicated `.kt` file in the appropriate domain subpackage.

---

## 2. Core `:common` Domain Subpackages & Symmetrical Backend Layout

All 8 platform backend modules (`:desktop:windows`, `:desktop:macos`, `:desktop:gtk`, `:desktop:qt`, `:desktop:swing`, `:mobile:android`, `:mobile:ios`, `:web`) mirror the subpackage hierarchy of `com.nexustools.genericui` in `:common`:

| # | Subpackage (`com.nexustools.genericui.*`) | Domain Responsibility |
| :- | :--- | :--- |
| 1 | `core` | `Application`, `FragmentApplication`, `Window`, `Fragment`, `Widget`, `Container`, `RootContainer`, `Bundle`, `LifecycleState` |
| 2 | `core.fragment` | Helper `Fragment` base classes: `AdaptiveSplitTabFragment`, `DockWorkspaceFragment`, `MasterDetailFragment`, `NavigationDrawerFragment`, `BottomNavigationFragment`, `WizardStepperFragment`, `CollapsibleHeaderFragment` |
| 3 | `nativeimpl` | `NativeBackendFactory`, `NativeBackendRegistry`, `NativeWidgetImpl`, `NativeContainerImpl`, `NativeWindowImpl`, and per-widget `*NativeImpl` peer interfaces |
| 4 | `enums.bootstrap` | Type-safe Bootstrap tokens: `BootstrapType`, `BootstrapSize`, `SpacingScale`, `FontWeight`, `BorderRadiusStyle`, `ShadowElevation`, `DisplayMode`, `TextAlignment`, `ResponsiveBreakpoint` |
| 5 | `style` | `StyleSheet`, `StyleRule`, `StyleProperty`, `CssSelector`, `ThemeEngine`, `ComputedStyle` |
| 6 | `event` | `UiEvent`, `ClickEvent`, `PointerEvent`, `KeyEvent`, `WheelEvent`, `FocusEvent`, `ResizeEvent`, `WindowEvent` |
| 7 | `widget.layout` | `VBox`, `HBox`, `GridPane`, `FlowPane`, `StackPane`, `ScrollPane`, `SplitPane`, `TabPane`, `Accordion`, `CardContainer`, `DrawerContainer` |
| 8 | `widget.display` | `Label`, `Badge`, `ProgressBar`, `ImageView`, `IconView`, `Tooltip`, `AlertBanner`, `Divider`, `StatusIndicator` |
| 9 | `widget.action` | `Button`, `IconButton`, `ToggleButton`, `Toolbar`, `ContextMenu`, `MenuDescriptor`, `ActionItem`, and the `TitleBar` instruction class |
| 10 | `widget.input` | `TextInput`, `TextArea`, `PasswordInput`, `NumberSpinner`, `CheckBox`, `RadioButton`, `SwitchToggle`, `Slider`, `SelectDropdown`, `ColorPicker`, `DatePicker`, `FilePickerButton` |
| 11 | `widget.data` | True Lazy Views (`LazyList`, `LazyColumn`, `LazyRow`, `LazyGrid`, `ViewHolderPool`), `ListView`, `TreeView`, `TreeItem`, `DataTable`, `DataColumn`, `PropertyGrid`, `PaginationBar` |
| 12 | `docking` | `DockWorkspace`, `DockArea`, `DockNode`, `DockTab`, `FloatingDockWindow`, `DockPosition`, `WorkspaceWindowMode`, `DockLayoutSerializer` |
| 13 | `dialog` | `Dialog`, `MessageDialog`, `ConfirmDialog`, `PromptDialog`, `FileDialog`, `DialogResult`, `FileBrowserDialog`, `FileBrowserMode`, `FileTypeFilter`, `SelectedFile`, `FileBrowserCallback` |
| 14 | `image` | **Base Full Image API**: `PixelFormat`, `ImageFormat`, `ImageColorSpace`, `ImageMetadata`, `ImageBuffer`, `ImageFrame`, `AnimatedImage`, `ImageSource`, `ImageDecodeOptions`, `ImageEncodeOptions`, `ImageLoader` |
| 15 | `canvas` | `AbstractCanvas`, `VBlankCallback`, `VBlankTimer`, `CanvasInputState` (concrete 2D/3D canvases live in `:extensions:canvas2d` and `:extensions:canvas3d`) |
| 16 | `component.tabs` | **Unified Native Tab System**: `TabContainer`, `FragmentTabContainer`, `Tab`, `CloseableTab`, `BadgeTab`, `PinnedTab`, `EditableTab`, `TabEntry`, `FragmentTabEntry`, `TabPosition`, `TabBarStyle` (all translated to native tab widgets via `TabContainerNativeImpl` & `TabNativeImpl`) |

---

## 3. Modular Two-Tier Extension Architecture (Project-of-Projects vs. Single Project)

Extensions in GenericUI can be structured in one of two ways depending on whether they require platform-specific native/JVM/Web implementations:

### A. Project-of-Projects Extensions (`:extensions:<name>:{common,desktop,mobile,swing,web}`)
Used when an extension requires native C/C++/ObjC (`:desktop`, `:mobile`), Java/LWJGL (`:swing`), and Web DOM/WebGL/WebGPU (`:web`) implementations:
1. **`:extensions:gpu`**:
   - Provides unified GPU context, shader, and offscreen compute/render management (`GpuDevice`, `GpuOffscreenContext`, `VKContext` primary with SPIR-V & MoltenVK on Apple, `GLContext` fallback with Google ANGLE on Windows).
   - Relied upon by `:extensions:imageprocessing`, `:extensions:canvas3d`, `:extensions:media`, and `:extensions:threejs`.
2. **`:extensions:canvas2d`**:
   - Provides `Canvas2D`, `Canvas2DContext`, and `CurveEditor` (extending `:common`'s `AbstractCanvas`).
3. **`:extensions:canvas3d`**:
   - Relies on `:extensions:gpu`. Provides `GLCanvas` (OpenGL ES 3.0+ with Google ANGLE on Windows) and `VKCanvas` (Vulkan 1.2+ with MoltenVK on Apple), both extending `:common`'s `AbstractCanvas` (`registerVBlankRender { deltaMs -> ... }`).
4. **`:extensions:media`**:
   - Relies on `:extensions:gpu`. Provides `VideoPlayer` (with uniform codec capability reporting), `AudioPlayer`, `AudioAnalyzer`, and `WebVttSrtParser`.
   - `:extensions:media:desktop:shared` provides shared native `libvlc` bindings (`LibVlcBindings`, `DesktopLibVlcVideoPlayerImpl`, `DesktopLibVlcAudioPlayerImpl`, `DesktopLibVlcAudioAnalyzerImpl`) used by `:extensions:media:desktop:{qt,gtk,windows,macos}`, while `:extensions:media:desktop:swing` uses `libvlcj` (`uk.co.caprica:vlcj`).
5. **`:extensions:webview`**:
   - Dedicated Project-of-Projects `WebView` extension (`:extensions:webview:common`, `:desktop`, `:mobile`, `:swing`, `:web`).
   - On **Desktop builds** (`:desktop` / `:windows`, `:macos`, `:gtk`, `:qt`, and `:swing`), uses the **Chromium** project (`ChromiumBlinkEngine`) for HTML/CSS layout & rendering coupled with **`NJS`** (`D:\IdeaProjects\NJS`, symlinked at `D:\IdeaProjects\GenericUI\lib\NJS`) with its **AsmJit / SLJIT Native JIT Engine** (`com.nexustools.njs.jit.*`).
   - On **Mobile (`:android` `android.webkit.WebView`, `:ios` `WKWebView`) and `:web` (`<iframe>`)**, uses the native platform implementation and **NEVER uses `NJS`**.

### B. Single Multiplatform Project Extensions (`:extensions:<name>`)
Used when an extension is implemented in 100% shared KMP code atop `:common` and other extensions:
6. **`:extensions:imageprocessing`** (relies on `:extensions:gpu` + base `:common` Image API): Offscreen in-memory GPU-accelerated image manipulation (`GpuImageProcessor`, `GpuImageFilterChain`, `GpuImageKernel`).
7. **`:extensions:codeeditor`** (relies on `:extensions:canvas2d`): Extension-only IDE-style code editor (`IdeCodeEditor`).
8. **`:extensions:aichat`** (relies on `:extensions:codeeditor` $\rightarrow$ `canvas2d`): Reusable AI chat drawer and multi-agent orchestrator (`AiChatContainer`, `AIChatDrawer`, `AIChatAgentOrchestrator`).
9. **`:extensions:threejs`** (relies on `:extensions:gpu` + `:extensions:canvas3d`): Three.js-style 3D scene graph (`ThreeSceneCanvas`, `ThreeGLCanvas`, `ThreeVKCanvas`).
10. **`:extensions:box2d`** (relies on `:extensions:canvas2d` + `:extensions:canvas3d` $\rightarrow$ `gpu`): 2D rigid-body physics engine (`Box2DWorld`, `Box2DCanvas`).
11. **`:extensions:box3d`** (relies on `:extensions:canvas3d` + `:extensions:threejs` $\rightarrow$ `gpu`): 3D rigid-body physics engine (`Box3DWorld`, `Box3DThreeJsBridge`).

---

## 4. Base `:common` Full Image API (`ImageCodecNativeImpl` + `libjpeg`/`libpng`/`libwebp`) & Offscreen GPU `:extensions:imageprocessing`

- **Base `:common` Image API (`com.nexustools.genericui.image.*`)**:
  - `ImageLoader` decodes and encodes still and animated images (`PNG`, `JPEG`, `WEBP`, `GIF`, `APNG`, `BMP`, `ICO`, `TIFF`, `SVG`, `PPM`) into `ImageBuffer` / `AnimatedImage` (`List<ImageFrame>`) with full `PixelFormat` (`RGBA_8888`, `BGRA_8888`, `RGB_888`, `RGBA_F16`, `RGBA_F32`, `GRAY_8`), `ImageColorSpace` (`SRGB`, `DISPLAY_P3`, `REC_2020_PQ`, `REC_2020_HLG`, `LINEAR_SRGB`), and `ImageMetadata` (EXIF orientation, DPI, ICC profile).
  - **Native Image Loader First, Dedicated `libjpeg`/`libpng`/`libwebp` Codecs Where Needed (`ImageCodecNativeImpl`)**:
    - **Java / `:swing`**: Uses `javax.imageio.ImageIO` & `BufferedImage` (`BufferedImage.TYPE_INT_ARGB` / `Raster`).
    - **Android (`:android`)**: Uses `android.graphics.BitmapFactory` and `android.graphics.ImageDecoder` (including animated WebP/GIF `AnimatedImageDrawable`).
    - **Web (`:web`)**: Uses browser-native `window.createImageBitmap`, `HTMLImageElement`, and `OffscreenCanvas` / `<canvas>` pixel extraction.
    - **macOS & iOS (`:macos`, `:ios`)**: Uses Apple `ImageIO` (`CGImageSource`, `CGImageDestination`, `CGBitmapContext`).
    - **Desktop (`:windows`, `:gtk`, `:qt`, `:desktop`) & Fallback Codecs**: Uses OS native loaders (`WIC` `IWICImagingFactory` on Windows, `GdkPixbuf` on GTK, `QImage`/`QImageReader` on Qt) and provides dedicated built-in codecs (`LibJpegCodec` via `libjpeg`/`libjpeg-turbo`, `LibPngCodec` via `libpng`, `LibWebpCodec` via `libwebp`, and `BmpPpmCodec`) whenever a format or headless/raw environment requires direct codec execution.
- **Offscreen GPU Image Processing (`:extensions:imageprocessing`)**:
  - Uses `:extensions:gpu`'s `GpuOffscreenContext` (Vulkan compute/fragment primary, OpenGL ES fallback) to process `ImageBuffer` instances offscreen in memory without requiring an on-screen window or canvas.

---

## 5. Deferred Upward `NativeImpl` Resolution Contract

1. **Pure Kotlin Construction**: Calling `val button = Button("Save")` or `val box = VBox()` initializes state in pure common Kotlin (`nativeImpl == null`). It MUST NOT throw an exception if no `Application` or `Window` is active yet.
2. **Upward Factory Discovery**: When `parent.addChild(child)` is invoked, `child` calls `resolveBackendFactoryUpward()`, walking up `parent -> parent.parent -> ... -> RootContainer -> Window -> Application` (with fallback to `NativeBackendRegistry.activeFactory`).
3. **Downward Realization Cascade**: When a detached subtree is attached to a live `Window` or `Fragment`, `realizeNativeTree(factory)` cascades **downward** across the entire subtree in pre-order.

---

## 6. `TitleBar` Is an Instruction Descriptor — NEVER a `Widget`

- `TitleBar` (`com.nexustools.genericui.widget.action.TitleBar`) **does NOT extend `Widget`**.
- Always apply `TitleBar` via `window.setTitleBar(titleBar)` or `fragment.setNavBar(titleBar)`.
- **macOS Global System Menu Bar Rule**: On `:macos`, `TitleBar.menus` (`List<MenuDescriptor>`) MUST be bound to the **macOS system menu bar** (`NSApplication.sharedApplication.mainMenu`) at the very top of the display.

---

## 7. `Fragment`, `Fragment.setNavBar`, & Helper Fragments

- `Fragment` mirrors Android's `Activity`/`Fragment` lifecycle 1:1 (`onCreate`, `onCreateView`, `onStart`, `onResume`, `onPause`, `onStop`, `onSaveInstanceState`, `onDestroy`).
- `Fragment.setNavBar(titleBar)` updates the host window/screen navigation bar when the fragment is the root view, or renders a local contextual header bar when the fragment is nested inside a split pane or dock panel.
- Helper fragments (`AdaptiveSplitTabFragment` for **Episode Caster**, `DockWorkspaceFragment` for **HAEditor** Single-Window vs. Multi-Window Mode) standardize responsive and dockable workspaces.

---

## 8. Native File & Folder Browser (`FileBrowserDialog`) & True Lazy Views Across All 8 Platforms

- **`FileBrowserDialog`**: Supports `OPEN_SINGLE_FILE`, `OPEN_MULTIPLE_FILES`, `SAVE_FILE`, and `SELECT_FOLDER` with `FileTypeFilter` and `FileBrowserCallback` (`onFilesSelected` / `onCancelled`). On `:web`, uses an invisible, un-interactable `<input type="file">` (`webkitdirectory` for folder mode, `accept` for `FileTypeFilter`); on `:android`, launches the native Android system file browser (`ACTION_OPEN_DOCUMENT`, `ACTION_CREATE_DOCUMENT`, `ACTION_OPEN_DOCUMENT_TREE`) via `GenericUiFragmentActivity` `ActivityResultLauncher`.
- **True Lazy Views (`LazyList`, `LazyColumn`, `LazyRow`, `LazyGrid`, `TreeView`)**: Dynamically load and bind items (`onBind`) only within `[firstActiveIndex .. lastActiveIndex]` and immediately unload/recycle (`onUnbind` $\rightarrow$ `ViewHolderPool`) items as they scroll out of view on all 8 platforms.

---

## 9. `:extensions:webview`, `lib/NJS` Symlink / Git Submodule Workflow, & Strict Desktop-Only `NJS` Rule

1. **`WebView` Is an Extension-Only Module (`:extensions:webview`)**:
   - `WebView` (`WebView`, `WebViewController`, `WebViewJsBridge`) lives in `:extensions:webview` (`:extensions:webview:common`, `:desktop`, `:mobile`, `:swing`, `:web`) and is opted into via `genericui { extensions { webView() } }`.
2. **STRICT RULE — DO NOT USE `NJS` ON iOS, ANDROID, OR WEB**:
   - **Mobile (`:android`, `:ios`) & `:web`**: `:extensions:webview:mobile` and `:extensions:webview:web` MUST use the native platform implementation (`android.webkit.WebView` on `:android`, `WKWebView` on `:ios`, and `<iframe>` on `:web`) and **MUST NEVER depend on or invoke `NJS`**.
   - **Desktop (`:desktop` / `:windows`, `:macos`, `:gtk`, `:qt`, and `:swing`)**: `:extensions:webview:desktop` and `:extensions:webview:swing` use the **Chromium** project (`ChromiumBlinkEngine`) for HTML/CSS layout & rendering coupled with **`NJS`** (`D:\IdeaProjects\NJS`, symlinked at `D:\IdeaProjects\GenericUI\lib\NJS` for live development and to become a git submodule later) for the JavaScript runtime.
   - `NJS` supports Desktop Native targets (`mingwX64`, `linuxX64`, `linuxArm64`, `macosArm64`, `macosX64`) alongside `jvm` (and strictly NOT iOS, Android, or Web):
     - **Desktop Native Builds (`nativeMain`)**: `NJS` uses its **AsmJit / SLJIT Native JIT Engine** (`com.nexustools.njs.jit.*` in `nativeMain` only).
     - **Desktop JVM Builds (`jvmMain` / `:swing`)**: `NJS` uses its own **Java Compiler System** (`JavaSourceTranspiler` + `JavaTranspiler` backed by `javax.tools.ToolProvider.getSystemJavaCompiler()`) with no custom JIT engine needed on JVM.
   - Preserve the directory junction `D:\IdeaProjects\GenericUI\lib\NJS` $\longrightarrow$ `D:\IdeaProjects\NJS` during development until it is converted into a Git submodule.

---

## 10. `:extensions:dsl` (`kotlinx.html`-Style MVC & Component DSL) Boundary Rule

1. **`:common`-Only Scope**:
   - `:extensions:dsl` (`com.nexustools.genericui.ext.dsl`) depends strictly on `:common` and provides `MvcModel`, `MvcView`, `MvcController`, and `kotlinx.html`-style in-place component builders (`div`, `row`, `col`, `h1`..`h6`, `p`, `span`, `button`, `select { option(...) }`, `table { tr { th(...); td(...) } }`, etc.) for `:common` components only.
   - **Never** add dependencies or builders for other extensions inside `:extensions:dsl`; other extensions can provide their own DSL extension modules.
