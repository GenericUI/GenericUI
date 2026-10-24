# GenericUI

**GenericUI** (`com.nexustools.genericui`) is a **Native-First, True Cross-Platform UI, Docking, Image, Media, & GPU Framework** built with **Kotlin Multiplatform (KMP)**.

Designed to power professional desktop, mobile, and web applications—including **AICore**, **HAEditor**, and **Episode Caster**—GenericUI eliminates the multi-hundred-megabyte RAM and CPU overhead of Electron/NW.js on desktop while providing 100% shared application logic across **Windows (Win32)**, **macOS (AppKit)**, **Linux (GTK & Qt)**, **Java/JVM (Swing/AWT)**, **Android**, **iOS (UIKit)**, and **Web (HTML5 DOM / WebGL2 / WebGPU)**.

---

## Core Architectural Pillars

1. **Native-First OS Widgets (Zero Emulated Bloat)**
   Every UI component (`Button`, `TextInput`, `SelectDropdown`, `LazyList`, `LazyGrid`, `TreeView`, `DataTable`, `SplitPane`, `Dialog`, `FileBrowserDialog`) delegates to a true native OS widget via a strict `NativeImpl` peer contract—Win32 `HWND` controls on `:windows`, AppKit `NSView`/`NSControl` on `:macos`, `GtkWidget` on `:gtk`, `QWidget` on `:qt`, `JComponent` on `:swing`, `android.view.View` on `:android`, `UIView` on `:ios`, and semantic HTML5 DOM elements on `:web`.
2. **Deferred Upward `NativeImpl` Resolution**
   Widgets can be constructed freely in pure common Kotlin **before** any `Window` or `Application` exists (`val btn = Button("Render")`). When a child is attached to a parent container (`parent.addChild(btn)`), GenericUI traverses **upward** (`parent -> ... -> RootContainer -> Window -> Application`) to resolve the active backend's `NativeBackendFactory`. If the tree is not yet attached to a realized root, native peer creation is deferred and automatically cascades **downward** the instant the root branch attaches to a live `Window` or `Fragment`.
3. **`TitleBar` Instruction Class (Not a Widget)**
   `TitleBar` is a declarative **instruction descriptor** passed via `window.setTitleBar(titleBar)` or `fragment.setNavBar(titleBar)`. It is **never** added as a child widget. Each backend translates `TitleBar` into authentic platform chrome:
   - **`:macos`**: Populates the **global macOS system menu bar** (`NSApplication.sharedApplication.mainMenu`) and native `NSToolbar` action items.
   - **`:windows` / `:gtk` / `:qt` / `:swing`**: Configures the native window title bar, `HMENU` / `GtkHeaderBar` / `QMenuBar` / `JMenuBar`, and action toolbar buttons.
   - **`:android` / `:ios` / `:web`**: Configures the native `Toolbar`/`ActionBar`, `UINavigationBar`, or responsive application header bar.
4. **1:1 Android `Activity`-Compatible `Fragment` System & Helper Fragments**
   `FragmentApplication` and `Fragment` provide an Android `Activity`-inspired lifecycle (`onCreate`, `onCreateView`, `onStart`, `onResume`, `onPause`, `onStop`, `onDestroy`), back-stack navigation (`startFragment`, `finish`), and **per-fragment `setNavBar(TitleBar)`**—allowing nested child fragments and docked panels to render their own local contextual header bars and menus. Built-in helper fragments (`AdaptiveSplitTabFragment` and `DockWorkspaceFragment`) standardize complex responsive and dockable layouts out of the box.
5. **Base Full Image API (`com.nexustools.genericui.image.*`) + Native Loaders & `libjpeg`/`libpng`/`libwebp` Codecs + Offscreen GPU `:extensions:imageprocessing`**
   Base `:common` ships with a complete multi-format Image API (`ImageLoader`, `ImageCodecNativeImpl`, `ImageBuffer`, `ImageFrame`, `AnimatedImage`, `ImageMetadata`, `ImageSource`, `PixelFormat`, `ImageFormat`, `ImageColorSpace`, `ImageDecodeOptions`, `ImageEncodeOptions`). `ImageLoader` delegates to the **native platform image loader where possible** (`javax.imageio.ImageIO` & `BufferedImage` on Java/`:swing`, `BitmapFactory` & `ImageDecoder` on `:android`, `createImageBitmap` & offscreen `<canvas>` on `:web`, `CGImageSource` on `:macos`/`:ios`, and `WIC` / `GdkPixbuf` / `QImage` on native desktop) and **implements its own native/multiplatform codecs (`LibJpegCodec` via `libjpeg`/`libjpeg-turbo`, `LibPngCodec` via `libpng`, `LibWebpCodec` via `libwebp`, and BMP/PPM) where needed**. For heavy offscreen pixel manipulation, `:extensions:imageprocessing` (`GpuImageProcessor`, `GpuImageFilterChain`, `GpuImageKernel`) processes `ImageBuffer`s in memory using hardware GPU compute/fragment pipelines provided by `:extensions:gpu`.
6. **Modular Two-Tier Extension Architecture (Project-of-Projects vs. Single Multiplatform Project)**
   To keep base `:common` lean, GPU contexts, 2D/3D canvases, media players, image processing, code editors, AI chat, and physics engines are modularized into dedicated extensions:
   - **Project-of-Projects Extensions** (`:extensions:<name>:common`, `:desktop`, `:mobile`, `:swing`, `:web` + umbrella `:extensions:<name>`) for extensions requiring native C/C++/ObjC, Java/LWJGL, and Web platform-specific implementations:
     - **`:extensions:gpu`**: Unified Vulkan (`#version 450` SPIR-V primary, MoltenVK on Apple) and OpenGL ES 3.0+ (`#version 300 es` fallback, Google ANGLE on Windows) device, shader, and offscreen context management (`GpuDevice`, `GpuOffscreenContext`, `VKContext`, `GLContext`).
     - **`:extensions:canvas2d`**: Hardware-accelerated 2D vector/raster canvas (`Canvas2D`, `Canvas2DContext`, `CurveEditor`) extending `:common`'s `AbstractCanvas`.
     - **`:extensions:canvas3d`**: Hardware-accelerated 3D/shader canvases (`GLCanvas`, `VKCanvas`) built atop `:extensions:gpu` and `:common`'s `AbstractCanvas` (`registerVBlankRender { deltaMs -> ... }`).
     - **`:extensions:media`**: Hardware-accelerated `VideoPlayer` (with uniform codec capability reporting), `AudioPlayer`, `AudioAnalyzer`, `WebVttSrtParser`, `:extensions:media:desktop:shared`'s native **`libvlc` Bindings** (`LibVlcBindings`, `DesktopLibVlcVideoPlayerImpl`, `DesktopLibVlcAudioPlayerImpl`, `DesktopLibVlcAudioAnalyzerImpl`) shared by `:extensions:media:desktop:{qt,gtk,windows,macos}`, and `:extensions:media:desktop:swing`'s **`libvlcj`** integration (`uk.co.caprica:vlcj`).
     - **`:extensions:webview`**: Dedicated Project-of-Projects `WebView` extension (`:common`, `:desktop`, `:mobile`, `:swing`, `:web`). On **Desktop builds** (`:desktop` / `:windows`, `:macos`, `:gtk`, `:qt`, and `:swing`), `:extensions:webview` uses the **Chromium** project (`ChromiumBlinkEngine`) for HTML/CSS layout & rendering coupled with **`NJS`** (`D:\IdeaProjects\NJS`, symlinked at `D:\IdeaProjects\GenericUI\lib\NJS` for live development and to become a git submodule later) with a high-performance **AsmJit / SLJIT Native JIT Engine** (`com.nexustools.njs.jit.*`). On **Mobile (`:android` `android.webkit.WebView`, `:ios` `WKWebView`) and `:web` (`<iframe>`)**, it uses the native platform implementation and **NEVER uses `NJS`**.
   - **Single Multiplatform Project Extensions** (`:extensions:<name>`) built purely on `:common` and other extensions:
     - **`:extensions:imageprocessing`**, **`:extensions:codeeditor`**, **`:extensions:aichat`**, **`:extensions:threejs`**, **`:extensions:box2d`**, and **`:extensions:box3d`**.
7. **True Lazy Views Across All 8 Platforms & Native File/Folder Browser**
   `LazyList` (`LazyColumn`, `LazyRow`), `LazyGrid`, and `TreeView` dynamically load and bind (`onBind`) only the items inside the visible viewport window (`[firstActiveIndex .. lastActiveIndex]`) and unload/recycle (`onUnbind` $\rightarrow$ `ViewHolderPool`) items as they scroll out of view. `FileBrowserDialog` supports both file and folder selection (`OPEN_SINGLE_FILE`, `OPEN_MULTIPLE_FILES`, `SAVE_FILE`, `SELECT_FOLDER`) across all platforms—including an invisible `<input type="file">` (`webkitdirectory`, `accept`) on `:web` and native Android Storage Access Framework (`ACTION_OPEN_DOCUMENT`, `ACTION_CREATE_DOCUMENT`, `ACTION_OPEN_DOCUMENT_TREE`) via `GenericUiFragmentActivity` `ActivityResultLauncher` on `:android`.
8. **Dual-Mode Styling: Type-Safe Bootstrap Enums + SASS/CSS Cascade**
   Apply styles rapidly in Kotlin using type-safe Bootstrap enums (`BootstrapType.PRIMARY`, `BootstrapSize.LG`, `SpacingScale.P_3`, `BorderRadiusStyle.ROUNDED_LG`, `ShadowElevation.MD`) or load compiled `.scss`/`.css` stylesheets with deterministic specificity (`Bootstrap Enum Defaults` $\rightarrow$ `CSS Tag/Class/ID Selectors` $\rightarrow$ `Inline StyleProperty Overrides`).

---

## Complete Module & Extension Architecture Overview

Every `.kt` file across all modules strictly adheres to the **1 Public Class / Interface / Enum Per File** standard.

### A. Core & Platform Backend Modules

| Module | Target / Type | Responsibility & Native Backing |
| :--- | :--- | :--- |
| **`:common`** | KMP (`commonMain`, `jvmMain`, `androidMain`, `jsMain`) | Platform-agnostic UI widgets, `NativeImpl` contracts, `Application` / `FragmentApplication`, `Fragment` & helper fragments, docking (`DockWorkspace`, `DockArea`, `DockNode`), true lazy views (`LazyList`, `LazyGrid`, `TreeView`, `ViewHolderPool`), `FileBrowserDialog`, **Full Image API (`com.nexustools.genericui.image.*`)**, `AbstractCanvas` (`registerVBlankRender` + `VBlankTimer`), Bootstrap enums, and SASS/CSS `ThemeEngine`. |
| **`:desktop:shared`** | Shared Native Desktop KMP (`desktop/shared`) | Automatically included by `:desktop:windows`, `:desktop:macos`, `:desktop:gtk`, and `:desktop:qt`. Shared native desktop windowing, high-precision VBlank timers, file dialogs, and native desktop utilities. |
| **`:desktop:windows`** | Native Win32 / C-Interop + `:desktop:shared` (`desktop/windows`) | Native Win32 (`HWND` / Common Controls / DWM / `IFileOpenDialog` / `IFileSaveDialog`). |
| **`:desktop:macos`** | Native AppKit / Objective-C + `:desktop:shared` (`desktop/macos`) | Native macOS AppKit (`NSWindow`, `NSView`, global system `NSMenu`, `NSOpenPanel`/`NSSavePanel`). |
| **`:desktop:gtk`** | Native Linux/Cross-Desktop GTK3/4 + `:desktop:shared` (`desktop/gtk`) | Native GTK (`GtkWindow`, `GtkHeaderBar`, `GtkWidget`, `GtkFileChooserNative`). |
| **`:desktop:qt`** | Native C++ Linux/Cross-Desktop Qt5/6 + `:desktop:shared` (`desktop/qt`) | Native Qt (`QMainWindow`, `QMenuBar`, `QDockWidget`, `QWidget`, `QFileDialog`). |
| **`:desktop:swing`** | JVM AWT / Swing (`desktop/swing`) | Pure JVM `JFrame`/`JComponent` + `JFileChooser`, with optional LWJGL 3 `JAWT` native surface integration when `:extensions:gpu` / `:extensions:canvas3d` are enabled (`useLwjglGL = true`, `useVulkan = true`). |
| **`:mobile:shared`** | Shared Mobile KMP (`mobile/shared`) | Automatically included by `:mobile:android` and `:mobile:ios`. Houses shared mobile posture/safe-area coordination (`MobileAdaptiveFragmentCoordinator`), multi-touch gesture & fling physics (`MobileGestureRecognizer`, `MobileFlingPhysics`), PiP (`MobilePipController`), media sessions (`MobileMediaSessionBridge`), and `MobileLazyViewportPool`. |
| **`:mobile:android`** | Android SDK (API 24+) + `:mobile:shared` (`mobile/android`) | Android Views, Material `Toolbar`, `RecyclerView`-backed lazy views, and `GenericUiFragmentActivity` SAF file/folder picker (`ACTION_OPEN_DOCUMENT`, `ACTION_CREATE_DOCUMENT`, `ACTION_OPEN_DOCUMENT_TREE`). |
| **`:mobile:ios`** | Apple iOS UIKit + `:mobile:shared` (`mobile/ios`) | Native UIKit (`UIWindow`, `UINavigationBar`, `UIView`, `UICollectionView`/`UITableView` cell reuse, `UIDocumentPickerViewController`). |
| **`:web`** | Kotlin/JS & Wasm Browser (`web`) | Semantic HTML5 DOM widgets, virtualized DOM lazy views, and invisible `<input type="file">` (`webkitdirectory`, `accept`) file/folder dialog. |
| **`:gradle-plugin`** | Gradle Kotlin DSL Plugin (`gradle-plugin`) | Plugin `id("com.nexustools.genericui")` providing the `genericui { ... }` DSL for backend selection, Project-of-Projects vs. Single-Project extension wiring, optional LWJGL OpenGL ES / Vulkan injection, Google ANGLE & MoltenVK staging, and SASS compilation. |

### B. First-Party Extension Modules (`:extensions:*`)

| Extension | Structure Type | Transitive Dependencies | Responsibility & Key Classes |
| :--- | :--- | :--- | :--- |
| **`:extensions:gpu`** | **Project-of-Projects** (`:common`, `:desktop`, `:mobile`, `:swing`, `:web`) | `:common` | Unified GPU device, shader, and offscreen context foundation (`GpuDevice`, `GpuOffscreenContext`, `VKContext` primary with SPIR-V & MoltenVK, `GLContext` fallback with Google ANGLE on Windows). |
| **`:extensions:imageprocessing`** | **Single KMP Project** | `:extensions:gpu` | Offscreen in-memory GPU-accelerated image manipulation & filter pipelines (`GpuImageProcessor`, `GpuImageFilterChain`, `GpuImageKernel`, `GpuHistogramAnalyzer`) operating on `:common`'s `ImageBuffer`. |
| **`:extensions:canvas2d`** | **Project-of-Projects** (`:common`, `:desktop`, `:mobile`, `:swing`, `:web`) | `:common` | Dedicated 2D vector/raster canvas (`Canvas2D`, `Canvas2DContext`, `CurveEditor`) backed by Direct2D, CoreGraphics, Cairo, QPainter, Java2D, Android Canvas, and HTML5 2D Context. |
| **`:extensions:canvas3d`** | **Project-of-Projects** (`:common`, `:desktop`, `:mobile`, `:swing`, `:web`) | `:extensions:gpu` | Dedicated OpenGL ES 3.0+ (`GLCanvas` with ANGLE on Windows) and Vulkan 1.2+ (`VKCanvas` with MoltenVK on Apple) interactive GPU surfaces. |
| **`:extensions:media`** | **Project-of-Projects** (`:common`, `:desktop`, `:mobile`, `:swing`, `:web`) | `:extensions:gpu` | `VideoPlayer` (with uniform codec reporting), `AudioPlayer`, `AudioAnalyzer`, `WebVttSrtParser`, and **`:extensions:media:desktop`'s VLC Video Playback, Clock Sync, Vulkan (`#version 450`) Primary + OpenGL ES (`#version 300 es`) Fallback HDR Tone-Mapping, & HW/SW Decode Engine**. |
| **`:extensions:webview`** | **Project-of-Projects** (`:common`, `:desktop`, `:mobile`, `:swing`, `:web`) | `:common` (plus `lib/NJS` on Desktop/Swing only) | Dedicated `WebView` extension (`WebView`, `WebViewController`, `WebViewJsBridge`). Uses **Chromium (`ChromiumBlinkEngine`) + `NJS` (`lib/NJS` AsmJit/SLJIT Native JIT Engine)** on Desktop (`:desktop` & `:swing`), and native `android.webkit.WebView` (`:android`), `WKWebView` (`:ios`), and `<iframe>` (`:web`) — **strictly NEVER using `NJS` on iOS, Android, or Web**. |
| **`:extensions:codeeditor`** | **Single KMP Project** | `:extensions:canvas2d` | Extension-only IDE-style code editor (`IdeCodeEditor`) with syntax highlighting, line numbers, code folding, minimap, autocomplete, diagnostics, and `Ctrl+Click` Go-To-Definition. |
| **`:extensions:aichat`** | **Single KMP Project** | `:extensions:codeeditor` ($\rightarrow$ `canvas2d`) | Reusable AI Assistant UI & orchestration suite (`AiChatContainer`, `AIChatDrawer`, `AIChatConversationList`, `AIChatMessageView`, `AIChatPromptComposer`, `AIChatAgentOrchestrator`). |
| **`:extensions:threejs`** | **Single KMP Project** | `:extensions:gpu`, `:extensions:canvas3d` | Three.js-inspired 3D scene graph (`ThreeSceneCanvas`, `ThreeGLCanvas`, `ThreeVKCanvas`, `ThreeCamera`, `ThreeMesh`, `ThreeGeometry`, `ThreeMaterial`, `ThreeLight`, `ThreeOrbitControls`). |
| **`:extensions:box2d`** | **Single KMP Project** | `:extensions:canvas2d`, `:extensions:canvas3d` ($\rightarrow$ `gpu`) | Pure KMP 2D rigid-body & particle physics engine (`Box2DWorld`, `Box2DBody`, `Box2DCollider`, `Box2DJoint`, `Box2DCanvas`). |
| **`:extensions:box3d`** | **Single KMP Project** | `:extensions:canvas3d`, `:extensions:threejs` ($\rightarrow$ `gpu`) | Pure KMP 3D rigid-body physics engine (`Box3DWorld`, `Box3DBody`, `Box3DCollider`, `Box3DConstraint`, `Box3DThreeJsBridge`). |

---

## Quick Start: Custom Gradle Plugin (`com.nexustools.genericui`)

Apply `id("com.nexustools.genericui")` in `build.gradle.kts`. When you enable an extension in `extensions { ... }`, the plugin automatically resolves its transitive extension dependencies and wires either its **Project-of-Projects** platform submodules (`:common`, `:desktop`, `:mobile`, `:swing`, `:web`) or its **Single Multiplatform Project**:

```kotlin
plugins {
    kotlin("multiplatform")
    id("com.nexustools.genericui")
}

genericui {
    backends {
        windows()
        macos()
        gtk()
        qt()
        swing {
            useLwjglGL = true
            useVulkan = true
            lwjglVersion = "3.3.6"
            targetAllDesktopClassifiers()
        }
        android()
        ios()
        web()
    }

    graphics {
        bundleAngleOnWindows = true          // Google ANGLE (libEGL.dll, libGLESv2.dll, d3dcompiler_47.dll) -> D3D11
        angleBackendRenderer = "d3d11"
        bundleMoltenVkOnApple = true         // Khronos MoltenVK (Vulkan 1.2 -> Apple Metal)
        bundleShaderc = true                 // Runtime GLSL -> SPIR-V shader compiler
        preferOpenGLES31 = true
        bundleVlcOnDesktop = true            // :extensions:media:desktop VLC engine on :windows, :macos, :gtk, :qt
        vlcPrimaryShaderBackend = "vulkan"   // Primary VLC HDR & YUV shader pipeline (#version 450 SPIR-V)
        vlcFallbackShaderBackend = "opengles" // Automatic fallback (#version 300 es, via ANGLE on Windows)
        enableVlcHdrToneMapping = true       // HDR10 PQ / HLG / Dolby Vision tone-mapping
        enableVlcHardwareDecode = true       // D3D11VA, DXVA2, NVDEC, VAAPI, VDPAU, VideoToolbox + SW fallback
    }

    extensions {
        gpu()              // :extensions:gpu (:common, :desktop, :mobile, :swing, :web)
        imageProcessing()  // :extensions:imageprocessing (transitively enables gpu)
        canvas2d()         // :extensions:canvas2d (:common, :desktop, :mobile, :swing, :web)
        canvas3d()         // :extensions:canvas3d (transitively enables gpu)
        media()            // :extensions:media (transitively enables gpu)
        webView()          // :extensions:webview (:common, :desktop, :mobile, :swing, :web; Chromium + NJS JIT on Desktop)
        codeEditor()       // :extensions:codeeditor (transitively enables canvas2d)
        aiChat()           // :extensions:aichat (transitively enables codeEditor -> canvas2d)
        threeJs()          // :extensions:threejs (transitively enables gpu + canvas3d)
        box2d()            // :extensions:box2d (transitively enables canvas2d + canvas3d -> gpu)
        box3d()            // :extensions:box3d (transitively enables canvas3d + threeJs -> gpu)
    }

    styles {
        compileSass = true
        sourceDir = "src/commonMain/resources/styles"
        minify = false
        includeBootstrapVariables = true
    }
}
```

---

## Code Examples

### 1. Base `:common` Image API (`ImageLoader`, `ImageBuffer`) & Offscreen GPU `:extensions:imageprocessing`

Base `:common` provides a full Image API (`com.nexustools.genericui.image.*`) for loading, decoding, inspecting, and encoding still and animated images (`PNG`, `JPEG`, `WEBP`, `GIF`, `APNG`, `BMP`, `ICO`, `TIFF`, `SVG`). Pair it with `:extensions:imageprocessing` (`com.nexustools.genericui.ext.imageprocessing.*`, powered by `:extensions:gpu`) to run offscreen GPU compute/fragment filter chains in memory:

```kotlin
import com.nexustools.genericui.image.ImageDecodeOptions
import com.nexustools.genericui.image.ImageFormat
import com.nexustools.genericui.image.ImageLoader
import com.nexustools.genericui.image.ImageSource
import com.nexustools.genericui.image.PixelFormat
import com.nexustools.genericui.ext.imageprocessing.GpuImageFilterChain
import com.nexustools.genericui.ext.imageprocessing.GpuImageKernel
import com.nexustools.genericui.ext.imageprocessing.GpuImageProcessor

// 1. Decode an image into an in-memory ImageBuffer using the base :common Image API
val sourceBuffer = ImageLoader.decode(
    source = ImageSource.fromFile("/path/to/photo.webp"),
    options = ImageDecodeOptions(targetPixelFormat = PixelFormat.RGBA_8888, premultiplyAlpha = true)
)

// 2. Process the ImageBuffer offscreen in memory on the GPU (:extensions:imageprocessing + :extensions:gpu)
val gpuProcessor = GpuImageProcessor.createOffscreen()
val filterChain = GpuImageFilterChain()
    .addGaussianBlur(radiusPx = 6.0f)
    .addExposureContrast(exposureEv = 0.35f, contrast = 1.15f)
    .addCustomKernel(GpuImageKernel.sharpen3x3(amount = 0.5f))

val outputBuffer = gpuProcessor.process(sourceBuffer, filterChain)
val encodedPngBytes = ImageLoader.encode(outputBuffer, ImageFormat.PNG)
```

---

### 2. Helper Fragments for **Episode Caster** (`AdaptiveSplitTabFragment`) & **HAEditor** (`DockWorkspaceFragment`)

```kotlin
import com.nexustools.genericui.core.fragment.AdaptiveSplitTabFragment
import com.nexustools.genericui.core.fragment.DockWorkspaceFragment
import com.nexustools.genericui.docking.DockPosition
import com.nexustools.genericui.docking.WorkspaceWindowMode
import com.nexustools.genericui.widget.action.ActionItem
import com.nexustools.genericui.widget.action.TitleBar

// Episode Caster: Multi-column split on tablet/desktop, fullscreen player in phone landscape, tabs in portrait
class EpisodeCasterMainFragment : AdaptiveSplitTabFragment() {
    override fun onConfigureAdaptiveLayout() {
        setNavBar(TitleBar(title = "Episode Caster", subtitle = "Streaming & Local Media"))
        setPrimaryMediaFragment(PlayerViewportFragment())
        addSecondarySlot(id = "shows", title = "Shows & Podcasts", fragment = ShowBrowserFragment())
        addSecondarySlot(id = "queue", title = "Up Next Queue", fragment = PlaybackQueueFragment())
        isFullscreenPrimaryInLandscape = true
    }
}

// HAEditor: Live runtime switching between GIMP Single-Window Mode and GIMP Multi-Window Mode
class HaEditorWorkspaceFragment : DockWorkspaceFragment() {
    override fun onConfigureWorkspace() {
        setNavBar(
            TitleBar(
                title = "HAEditor — Hybrid Vector & Raster Studio",
                actions = listOf(
                    ActionItem(id = "workspace.toggleMode", label = "Toggle Single/Multi Window") {
                        setWorkspaceWindowMode(
                            if (windowMode == WorkspaceWindowMode.SINGLE_WINDOW) WorkspaceWindowMode.MULTI_WINDOW
                            else WorkspaceWindowMode.SINGLE_WINDOW
                        )
                    }
                )
            )
        )
        dockFragment("toolbox", "Tools & Brushes", ToolboxFragment(), DockPosition.LEFT)
        dockFragment("canvas-doc-1", "hero-character.hacanvas", CanvasEditorFragment(), DockPosition.CENTER)
        dockFragment("layers", "Layers & Channels", LayersPanelFragment(), DockPosition.RIGHT)
        dockFragment("timeline", "Keyframe & Cel Timeline", TimelineFragment(), DockPosition.BOTTOM)
    }
}
```

---

### 3. `:extensions:canvas2d` (`Canvas2D`) & `:extensions:canvas3d` (`GLCanvas`, `VKCanvas`) with `registerVBlankRender(deltaMs)`

`AbstractCanvas` in `:common` provides display-synchronized VBlank scheduling (`registerVBlankRender { deltaMs -> ... }`), while `:extensions:canvas2d` and `:extensions:canvas3d` (backed by `:extensions:gpu`) provide the dedicated 2D and 3D rendering surfaces:

```kotlin
import com.nexustools.genericui.ext.canvas2d.Canvas2D
import com.nexustools.genericui.ext.canvas3d.GLCanvas
import com.nexustools.genericui.ext.canvas3d.VKCanvas

// 1. 2D Vector/Raster Canvas (:extensions:canvas2d)
val canvas2d = Canvas2D(width = 800, height = 600).apply {
    var angle = 0.0
    registerVBlankRender { deltaMs ->
        angle += deltaMs * 0.002
        clear("#181A1F")
    }
}

// 2. OpenGL ES 3.0+ Canvas (:extensions:canvas3d + :extensions:gpu, Google ANGLE -> D3D11 on Windows)
val glCanvas = GLCanvas(width = 1280, height = 720, preferES31 = true).apply {
    registerVBlankRender { deltaMs ->
        glContext?.clearColorBuffer(0.08f, 0.09f, 0.12f, 1.0f)
    }
}

// 3. Vulkan 1.2+ Canvas (:extensions:canvas3d + :extensions:gpu, MoltenVK -> Metal on macOS & iOS)
val vkCanvas = VKCanvas(width = 1280, height = 720, enableValidation = true).apply {
    registerVBlankRender { deltaMs ->
        // Record command buffer for current swapchain image...
    }
}
```

---

### 4. `:extensions:media` — `VideoPlayer`, Uniform Codec Reporting, Native `libvlc` (`:desktop:shared`), & `libvlcj` (`:desktop:swing`)

`:extensions:media` provides `VideoPlayer`, `AudioPlayer`, `AudioAnalyzer`, and `WebVttSrtParser`. On native desktop (`:windows`, `:macos`, `:gtk`, `:qt`), `:extensions:media:desktop:{windows,macos,gtk,qt}` call into `:extensions:media:desktop:shared`'s native **`libvlc` bindings (`LibVlcBindings`, `DesktopLibVlcVideoPlayerImpl`)**, while on Java/Swing `:extensions:media:desktop:swing` uses **`libvlcj` (`uk.co.caprica:vlcj`)**:

```kotlin
import com.nexustools.genericui.ext.media.VideoCodec
import com.nexustools.genericui.ext.media.VideoContainerFormat
import com.nexustools.genericui.ext.media.VideoPlayer

val player = VideoPlayer().apply {
    val capabilities = getSupportedCodecs()
    println("Hardware HEVC supported: ${capabilities.supportsHardwareAccelerated(VideoCodec.HEVC_H265)}")
    println("AV1 in MKV supported: ${canPlayCodec(VideoCodec.AV1, VideoContainerFormat.MKV)}")

    setSourceUrl("https://cdn.example.com/episodes/ep01-hdr10-master.m3u8")
    setSubtitleTrackUrl("https://cdn.example.com/episodes/ep01.en.vtt")
    play()
}
```

---

### 5. True Lazy Views (`LazyList`, `LazyColumn`, `LazyRow`, `LazyGrid`, `TreeView`) & Native `FileBrowserDialog`

- **True Lazy Views**: Dynamically load/bind items (`onBind`) only within `[firstActiveIndex .. lastActiveIndex]` and unload/recycle (`onUnbind` $\rightarrow$ `ViewHolderPool`) items as they scroll out of view on all 8 platforms.
- **`FileBrowserDialog`**: Supports `OPEN_SINGLE_FILE`, `OPEN_MULTIPLE_FILES`, `SAVE_FILE`, and `SELECT_FOLDER` with `FileTypeFilter` and `FileBrowserCallback` (`onFilesSelected` / `onCancelled`)—using native OS pickers on desktop/iOS, an invisible un-interactable `<input type="file">` (`webkitdirectory`, `accept`) on `:web`, and Android Storage Access Framework (`ACTION_OPEN_DOCUMENT`, `ACTION_CREATE_DOCUMENT`, `ACTION_OPEN_DOCUMENT_TREE`) on `:android`.

---

## Documentation Suite

- [**AGENTS.md**](./AGENTS.md) — Authoritative architectural rules & coding standards for AI agents and human engineers.
- [**docs/architecture.md**](./docs/architecture.md) — Deep dive into the core & extension module hierarchy, Project-of-Projects vs. Single-Project extensions, base Image API, Deferred Upward `NativeImpl` Resolution, `TitleBar` instructions, and Dual-Mode Styling.
- [**docs/gradle-plugin.md**](./docs/gradle-plugin.md) — Complete reference for `id("com.nexustools.genericui")`, transitive extension resolution, LWJGL OpenGL ES / Vulkan dependency wiring, Google ANGLE, MoltenVK, and SASS compilation.
- [**docs/components-and-docking.md**](./docs/components-and-docking.md) — Comprehensive guide to widgets, `Fragment`s, `Fragment.setNavBar`, `AdaptiveSplitTabFragment`, `DockWorkspaceFragment`, `FileBrowserDialog`, and True Lazy Views.
- [**docs/media-and-canvases.md**](./docs/media-and-canvases.md) — Complete reference for Base `:common` Image API, `:extensions:gpu`, `:extensions:imageprocessing`, `:extensions:canvas2d`, `:extensions:canvas3d`, `:extensions:media` (including `:extensions:media:desktop` VLC HDR & HW/SW decode), and `:extensions:webview` (Chromium + `NJS` Native JIT Engine on Desktop).
- [**docs/extensions.md**](./docs/extensions.md) — Guide to all 11 first-party extensions (`:extensions:gpu`, `:extensions:imageprocessing`, `:extensions:canvas2d`, `:extensions:canvas3d`, `:extensions:media`, `:extensions:webview`, `:extensions:codeeditor`, `:extensions:aichat`, `:extensions:threejs`, `:extensions:box2d`, `:extensions:box3d`).
