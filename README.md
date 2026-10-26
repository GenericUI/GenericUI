<p align="center">
  <img src="./logo.svg" alt="GenericUI Banner Logo" width="100%" />
</p>

# GenericUI

**GenericUI** (`com.nexustools.genericui`) is a **Native-First, True Cross-Platform UI, Docking, Image, Media, & GPU Framework** built with **Kotlin Multiplatform (KMP)**.

Designed to power professional desktop, mobile, and web applications from a **single `commonMain` source set**, GenericUI eliminates the multi-hundred-megabyte RAM and CPU overhead of Electron/NW.js on desktop while providing 100% shared application logic across **Windows (Win32)**, **macOS (AppKit)**, **Linux (GTK4 & Qt6)**, **Java/JVM (Swing/AWT)**, **Android**, **iOS (UIKit)**, and **Web (HTML5 DOM / WebGL2 / WebGPU)**.

---

## Core Architectural Pillars

1. **Zero-Boilerplate Single-`commonMain` Architecture (`@MainAppFragment` & `@AppFragment`)**
   Consumer applications write all UI, state, and business logic in `src/commonMain/kotlin` by annotating their root fragment with [`@MainAppFragment`](./common/src/commonMain/kotlin/com/nexustools/genericui/component/fragment/MainAppFragment.kt) and secondary screens with [`@AppFragment`](./common/src/commonMain/kotlin/com/nexustools/genericui/component/fragment/AppFragment.kt). At build time, the `:gradle-plugin` automatically generates all 8 platform launchers (`WindowsLauncher`, `MacosLauncher`, `GtkLauncher`, `QtLauncher`, `SwingLauncher`, `IosLauncher`, `WebLauncher`, and 1:1 Android `AppCompatActivity` classes), wires extension factories automatically, and resolves target-specific overrides using deterministic [`AppTarget`](./common/src/commonMain/kotlin/com/nexustools/genericui/enums/system/AppTarget.kt) specificity (`5` Single Backend $\rightarrow$ `4` `Linux` $\rightarrow$ `3` `NativeDesktop` / `Mobile` $\rightarrow$ `2` `Desktop` $\rightarrow$ `1` `System` $\rightarrow$ `0` `Common`).
2. **Command-Line Arguments on System Targets (`FragmentBundle`)**
   On all system targets (`Windows`, `MacOS`, `Gtk`, `Qt`, `Swing`, `Android`, `IOS`), the generated launcher captures process command-line arguments (`Array<String>`) and injects them into the startup [`FragmentBundle`](./common/src/commonMain/kotlin/com/nexustools/genericui/component/fragment/FragmentBundle.kt) passed to `@MainAppFragment.onCreate(savedInstanceState: FragmentBundle?)`—accessible via `hasCommandLineArgs()`, `getCommandLineArgs()`, `getCommandLineArgsArray()`, and `putCommandLineArgs(...)`.
3. **1:1 Android `AppCompatActivity` Generation, `FragmentHostFragment`, & `FragmentHostSystem`**
   - **Android 1:1 Activities**: On `:mobile:android`, every `@MainAppFragment` and `@AppFragment` generates a dedicated `AppCompatActivity` subclass (`<FragmentName>Activity : GenericUiFragmentActivity()`) registered in the generated `AndroidManifest.xml`, mapping `launchFragment(FragmentClass, LaunchArgs)` and [`FragmentLaunchMode`](./common/src/commonMain/kotlin/com/nexustools/genericui/enums/fragment/FragmentLaunchMode.kt) (`STANDARD`, `SINGLE_TOP`, `CLEAR_TOP`, `NEW_WINDOW`) to native Android `Intent`s.
   - **In-Window Navigation (`FragmentHostFragment` & `FragmentHostSystem`)**: [`FragmentHostFragment`](./common/src/commonMain/kotlin/com/nexustools/genericui/component/fragment/helper/FragmentHostFragment.kt) and [`FragmentHostSystem`](./common/src/commonMain/kotlin/com/nexustools/genericui/component/fragment/helper/FragmentHostSystem.kt) provide animated in-window fragment stacks with [`FragmentTransitionSpec.FadeExpandShrink`](./common/src/commonMain/kotlin/com/nexustools/genericui/component/fragment/helper/FragmentTransitionSpec.kt) (and `SlideHorizontal`, `SlideVertical`, `CrossFade`), optional Back/Forward/custom navigation buttons in the `TitleBar` or `Navbar` via [`FragmentNavigationChromeConfig`](./common/src/commonMain/kotlin/com/nexustools/genericui/component/fragment/helper/FragmentNavigationChromeConfig.kt), and automatic Web URL `#hash` + `window.history.state` synchronization.
4. **`IWindow` & `TitleCapabilities` Single-Placement Chrome Rules**
   [`TitleBar`](./common/src/commonMain/kotlin/com/nexustools/genericui/window/TitleBar.kt) and [`MenuBar`](./common/src/commonMain/kotlin/com/nexustools/genericui/component/shell/MenuBar.kt) are declarative instruction descriptors inspected via [`TitleCapabilities`](./common/src/commonMain/kotlin/com/nexustools/genericui/window/TitleCapabilities.kt) (`window.titleCapabilities`) and **never** rendered in two places simultaneously:
   - **`:desktop:windows` / `:desktop:gtk` / `:desktop:qt` / `:desktop:swing`**: Rendered once in the native window frame/chrome (replacing standard Win32 caption chrome when custom controls are present).
   - **`:desktop:macos`**: `MenuBar` attaches to the global top-of-screen macOS system menu bar (`NSApplication.sharedApplication.mainMenu`) while `TitleBar` configures `NSWindow` / `NSToolbar`.
   - **`:mobile:android` / `:mobile:ios`**: Rendered as a native top `Navbar` (`MaterialToolbar` / `UINavigationBar`) **only** when a `TitleBar` or `MenuBar` is set; omitted for full-bleed content otherwise.
   - **`:web`**: Title text updates `document.title` (browser tab title) with `supportsCustomTitleBar = false`, while in-viewport `Navbar` rendering is opt-in (`useWebViewportNavBar = true`).
5. **Android-Style `Resource` (`Resource.ResourceID`) System Across All 8 Backends & Shared Libraries**
   Place assets in `src/commonMain/resources/` (`drawable/`, `values/strings.xml`, `values/colors.xml`, `raw/`, `font/`, `layout/`) across application and shared library modules. The Gradle plugin generates a type-safe `Resource` class (`Resource.drawable.ic_logo`, `Resource.string.app_title`, `Resource.raw.default_config`) where every entry is a [`Resource.ResourceID`](./common/src/commonMain/kotlin/com/nexustools/genericui/resources/ResourceID.kt) translated natively by each backend:
   - **`:web`** (`WebResourceResolverImpl`): Translates `ResourceID` into URL strings (`./assets/drawable/...`).
   - **`:desktop:windows`** (`WindowsResourceResolverImpl`): Compiles resources into a Win32 `.rc`/`.res` object linked directly into the `.exe` binary (`FindResourceW` / `LoadResource` / `LockResource`).
   - **`:mobile:android`** (`AndroidResourceResolverImpl`): Maps `ResourceID` directly to Android `R.drawable.*`, `R.string.*`, and `R.raw.*`.
   - **`:desktop:macos` & `:mobile:ios`**: Resolves via `NSBundle.mainBundle`.
   - **`:desktop:gtk` & `:desktop:qt`**: Compiles and resolves via `GResource` (`g_resources_lookup_data`) and `QResource` (`:/genericui/...`).
   - **`:desktop:swing`**: Resolves from the JVM JAR classpath (`ClassLoader.getResourceAsStream`).
6. **`IApplication` & `BackendTarget` Multi-Target Group Checks**
   [`IApplication`](./common/src/commonMain/kotlin/com/nexustools/genericui/component/shell/IApplication.kt) (`app.backendTarget`) exposes [`BackendTarget`](./common/src/commonMain/kotlin/com/nexustools/genericui/enums/system/BackendTarget.kt) (`Windows`, `MacOS`, `Gtk`, `Qt`, `Swing`, `Android`, `IOS`, `Web`) with rich multi-target group properties (`isSystem`, `isDesktop`, `isNativeDesktop`, `isLinux`, `isMobile`, `isApple`, `isUnix`, `isPosix`, `isNative`, `isJvm`, `isTouchPrimary`, `isBrowser`) and an optional [`@AppApplication`](./common/src/commonMain/kotlin/com/nexustools/genericui/component/fragment/AppApplication.kt) lifecycle hook.
7. **Native-First OS Widgets & Deferred Upward `NativeImpl` Resolution**
   Every UI component (`Button`, `TextInput`, `SelectDropdown`, `LazyList`, `LazyGrid`, `TreeView`, `DataTable`, `SplitPane`, `Dialog`, `FileBrowserDialog`) delegates to a true native OS widget (`HWND`, `NSView`, `GtkWidget`, `QWidget`, `JComponent`, `android.view.View`, `UIView`, or HTML5 DOM). Widgets can be instantiated freely in pure common Kotlin before any window exists; attaching a widget traverses upward (`parent -> ... -> RootContainer -> Window -> Application`) to resolve `NativeFactory` and cascades peer creation downward automatically.
8. **Modular 15-Extension Ecosystem & Strict Core Isolation**
   Core modules (`:common`, `:systemCommon`, `:desktop:*`, `:mobile:*`, `:web`) are 100% isolated from extension internals. GPU compute (`:extensions:gpu`), offscreen image processing (`:extensions:imageprocessing`), translucent GPU/native compositing overlays (`:extensions:overlay`), 2D/3D canvases (`:extensions:canvas2d`, `:extensions:canvas3d`), VLC HDR video/audio (`:extensions:media`), reusable transport controls (`:extensions:mediacontrols`), embedded WebViews (`:extensions:webview`), escape-hatch raw platform views (`:extensions:alien`), code editors (`:extensions:codeeditor`), AI chat (`:extensions:aichat`), 3D scene graphs (`:extensions:threejs`), 2D/3D physics (`:extensions:box2d`, `:extensions:box3d`), and declarative DSLs (`:extensions:dsl`) are opt-in via `extensions { ... }`.

---

## Complete Module & Extension Architecture Overview

Every `.kt` file across all modules strictly adheres to the **1 Public Class / Interface / Enum Per File** standard.

### A. Core & Platform Backend Modules

| Module | Variant Scope | Responsibility & Native Backing |
| :--- | :--- | :--- |
| **`:common`** | `common` | Platform-agnostic UI widgets, `NativeImpl` contracts, `IWindow`, `TitleCapabilities`, `IApplication`, `BackendTarget`, `AppTarget`, `IFragment`, `@MainAppFragment`, `@AppFragment`, `FragmentBundle`, `FragmentHostFragment`, `FragmentHostSystem`, `DockWorkspace`, true lazy views (`LazyList`, `LazyGrid`, `TreeView`), `FileBrowserDialog`, `PreferenceStore`, `Resource`/`ResourceID`/`ResourceResolver`, **Full Image API (`com.nexustools.genericui.image.*`)**, `AbstractCanvas`, Bootstrap enums, and SASS/CSS `ThemeEngine`. |
| **`:systemCommon`** | `systemCommon` | Pure-Kotlin system-target helpers (`SystemStorageKeyHelper`) shared across all 7 non-browser OS targets (`Windows`, `MacOS`, `Gtk`, `Qt`, `Swing`, `Android`, `IOS`). |
| **`:desktop:common`** | `desktopCommon` | Pure-Kotlin desktop foundation shared by all 5 desktop backends (`Windows`, `MacOS`, `Gtk`, `Qt`, `Swing`): `DesktopFileBrowserHelper`, `DesktopLazyViewportEngine`, `DesktopUiTimerNativeImpl`, and desktop dialog/tray/notification helpers. |
| **`:desktop:nativeCommon`** | `nativeDesktopCommon` | Native C/C++/ObjC desktop foundation shared by `Windows`, `MacOS`, `Gtk`, and `Qt`: `DesktopBaseNativeImpl`, `DesktopConfigDirectoryHelper`, `DesktopMultiWindowDockCoordinator`, and POSIX/native widget peers. |
| **`:desktop:linuxCommon`** | `linuxCommon` | Linux XDG desktop foundation shared by `Gtk` and `Qt`: `LinuxXdgConfigDirectoryImpl`, `LinuxXdgCacheDirectoryImpl`, and `LinuxXdgPreferenceStoreImpl`. |
| **`:desktop:windows`** | `windows` | `WindowsLauncher`, Win32 (`HWND` / Common Controls / DWM / `IFileOpenDialog` / `IFileSaveDialog`), embedded `.exe` Win32 `.rc`/`.res` `WindowsResourceResolverImpl`, Google ANGLE, and Vulkan. |
| **`:desktop:macos`** | `macos` | `MacosLauncher`, macOS AppKit (`NSWindow`, `NSView`, global system `NSMenu`, `NSOpenPanel`/`NSSavePanel`), `NSBundle` `MacosResourceResolverImpl`, CoreGraphics, and MoltenVK. |
| **`:desktop:gtk`** | `gtk` | `GtkLauncher`, Linux GTK4 (`GtkWindow`, `GtkHeaderBar`, `GtkWidget`, `GtkFileDialog`), `GResource` `GtkResourceResolverImpl`, Cairo, EGL, and Vulkan. |
| **`:desktop:qt`** | `qt` | `QtLauncher`, Linux Qt6 (`QMainWindow`, `QMenuBar`, `QDockWidget`, `QWidget`, `QFileDialog`), `QResource` `QtResourceResolverImpl`, `QPainter`, and `QVulkanWindow`. |
| **`:desktop:swing`** | `swing` | `SwingLauncher`, JVM `JFrame`/`JComponent` + `JFileChooser`, JAR classpath `SwingResourceResolverImpl`, and optional LWJGL 3 `JAWT` OpenGL ES & Vulkan (`useLwjglGL`, `useVulkan`). |
| **`:mobile:common`** | `mobileCommon` | Mobile foundation shared by `Android` and `IOS`: `MobileDeviceFormFactor`, `MobileAdaptiveFragmentCoordinator`, `MobileGestureRecognizer`, `MobileFlingPhysics`, `MobilePipController`, and `MobileLazyViewportPool`. |
| **`:mobile:android`** | `android` | 1:1 generated `AppCompatActivity` classes (`<FragmentName>Activity : GenericUiFragmentActivity()`), conditional `MaterialToolbar` navbar, Android `R.*` `AndroidResourceResolverImpl`, `RecyclerView` lazy views, and SAF file/folder picker. |
| **`:mobile:ios`** | `ios` | `IosLauncher`, UIKit (`UIWindow`, conditional `UINavigationBar`, `UIView`, `UICollectionView`/`UITableView`, `UIDocumentPickerViewController`), `NSBundle` `IosResourceResolverImpl`, and MoltenVK. |
| **`:web`** | `web` | `WebLauncher`, Kotlin/Wasm (`wasmJs`, default) + Kotlin/JS IR (`js`, automatic fallback) via `genericui-loader.js`, URL-based `WebResourceResolverImpl`, `#hash` + `window.history.state` fragment routing, Bootstrap splash screen (`#genericui-splash`), semantic HTML5 DOM widgets, and `<input type="file">` dialog. |
| **`:gradle-plugin`** | Gradle Plugin | Plugin `id("com.nexustools.genericui")` providing the `genericui { ... }` DSL, automatic launcher & Android `AppCompatActivity` codegen, `Resource` & Win32 `.rc`/`.res` compilation, and extension wiring. |

### B. First-Party Extension Modules (`:extensions:*`)

| Extension | Structure Type | Transitive Dependencies | Responsibility & Key Classes |
| :--- | :--- | :--- | :--- |
| **`:extensions:gpu`** | **Project-of-Projects** (`:common`, `:systemCommon`, `:desktop:*`, `:mobile:*`, `:web`) | `:common` | Unified GPU device, shader, and offscreen context foundation (`GpuDevice`, `GpuOffscreenContext`, `VKContext` primary with SPIR-V & MoltenVK, `GLContext` fallback with Google ANGLE on Windows). |
| **`:extensions:imageprocessing`** | **Single KMP Project** | `:extensions:gpu` | Offscreen in-memory GPU-accelerated image manipulation & filter pipelines (`GpuImageProcessor`, `GpuImageFilterChain`, `GpuImageKernel`, `GpuHistogramAnalyzer`) operating on `:common`'s `ImageBuffer`. |
| **`:extensions:overlay`** | **Project-of-Projects** (`:common`, `:systemCommon`, `:desktop:*`, `:mobile:*`, `:web`) | `:extensions:gpu` | Cross-platform translucent GPU/native compositing surface (`OverlayView`, `OverlaySurface`, `OverlayController`) for rendering alpha-blended UI layers over hardware video, 2D/3D canvases, and native surfaces. |
| **`:extensions:canvas2d`** | **Project-of-Projects** (`:common`, `:systemCommon`, `:desktop:*`, `:mobile:*`, `:web`) | `:common` | Dedicated 2D vector/raster canvas (`Canvas2D`, `Canvas2DContext`, `CurveEditor`) backed by Direct2D, CoreGraphics, Cairo, QPainter, Java2D, Android Canvas, and HTML5 2D Context. |
| **`:extensions:canvas3d`** | **Project-of-Projects** (`:common`, `:systemCommon`, `:desktop:*`, `:mobile:*`, `:web`) | `:extensions:gpu` | Dedicated OpenGL ES 3.0+ (`GLCanvas` with ANGLE on Windows) and Vulkan 1.2+ (`VKCanvas` with MoltenVK on Apple) interactive GPU surfaces. |
| **`:extensions:media`** | **Project-of-Projects** (`:common`, `:systemCommon`, `:desktop:*`, `:mobile:*`, `:web`) | `:extensions:gpu` | `VideoPlayer` (with uniform codec reporting), `AudioPlayer`, `AudioAnalyzer`, `WebVttSrtParser`, native **`libvlc` bindings** in `:extensions:media:desktop:nativeCommon`, and **`libvlcj`** in `:extensions:media:desktop:swing`. |
| **`:extensions:mediacontrols`** | **Single KMP Project** | `:extensions:media`, `:extensions:overlay` ($\rightarrow$ `gpu`) | Reusable media transport bar (`MediaControlBar`, `VideoSeekBar`, `VolumePopupButton`, `QualitySelectorMenu`, `PlaybackSpeedMenu`, `SubtitleTrackMenu`) composited over `VideoPlayer` via `:extensions:overlay`. |
| **`:extensions:webview`** | **Project-of-Projects** (`:common`, `:systemCommon`, `:desktop:*`, `:mobile:*`, `:web`) | `:common` (plus `lib/NJS` on Desktop/Swing only) | Dedicated `WebView` extension (`WebView`, `WebViewController`, `WebViewJsBridge`). Uses **Chromium (`ChromiumBlinkEngine`) + `NJS` (`lib/NJS` AsmJit/SLJIT Native JIT Engine)** on Desktop, and native `android.webkit.WebView` (`:android`), `WKWebView` (`:ios`), and `<iframe>` (`:web`) — **never using `NJS` on Mobile or Web**. |
| **`:extensions:alien`** | **Project-of-Projects** (`:common`, `:systemCommon`, `:desktop:*`, `:mobile:*`, `:web`) | `:common` | Escape-hatch `AlienView` (`AlienViewHost`, `AlienHandle`) for embedding raw platform-native widgets (`HWND`, `NSView`, `GtkWidget`, `QWidget`, `JComponent`, `android.view.View`, `UIView`, `HTMLElement`) inside a GenericUI layout tree. |
| **`:extensions:codeeditor`** | **Single KMP Project** | `:extensions:canvas2d` | Extension-only IDE-style code editor (`IdeCodeEditor`) with syntax highlighting, line numbers, code folding, minimap, autocomplete, diagnostics, and `Ctrl+Click` Go-To-Definition. |
| **`:extensions:aichat`** | **Single KMP Project** | `:extensions:codeeditor` ($\rightarrow$ `canvas2d`) | Reusable AI Assistant UI & orchestration suite (`AiChatContainer`, `AIChatDrawer`, `AIChatConversationList`, `AIChatMessageView`, `AIChatPromptComposer`, `AIChatAgentOrchestrator`). |
| **`:extensions:threejs`** | **Single KMP Project** | `:extensions:gpu`, `:extensions:canvas3d` | Three.js-inspired 3D scene graph (`ThreeSceneCanvas`, `ThreeGLCanvas`, `ThreeVKCanvas`, `ThreeCamera`, `ThreeMesh`, `ThreeGeometry`, `ThreeMaterial`, `ThreeLight`, `ThreeOrbitControls`). |
| **`:extensions:box2d`** | **Single KMP Project** | `:extensions:canvas2d`, `:extensions:canvas3d` ($\rightarrow$ `gpu`) | Pure KMP 2D rigid-body & particle physics engine (`Box2DWorld`, `Box2DBody`, `Box2DCollider`, `Box2DJoint`, `Box2DCanvas`). |
| **`:extensions:box3d`** | **Single KMP Project** | `:extensions:canvas3d`, `:extensions:threejs` ($\rightarrow$ `gpu`) | Pure KMP 3D rigid-body physics engine (`Box3DWorld`, `Box3DBody`, `Box3DCollider`, `Box3DConstraint`, `Box3DThreeJsBridge`). |
| **`:extensions:dsl`** | **Single KMP Project** (+ 9 `:extensions:<name>:dsl` submodules) | `:common` | Declarative Kotlin UI & styling DSL (`ui { ... }`, `style { ... }`, `titleBar { ... }`) plus extension DSLs (`canvas2d`, `canvas3d`, `media`, `webview`, `codeeditor`, `aichat`, `threejs`, `box2d`, `box3d`). |

---

## Quick Start: Single-`commonMain` Project (`com.nexustools.genericui`)

Apply `id("com.nexustools.genericui")` in `build.gradle.kts`. No manual entry-point configuration or platform launcher files are needed—the plugin discovers `@MainAppFragment` and `@AppFragment` classes automatically and generates all platform launchers and `Resource` tables:

```kotlin
plugins {
    kotlin("multiplatform")
    id("com.nexustools.genericui")
}

genericui {
    app {
        id = "com.example.studio"
        name = "GenericUI Studio"
        version = "1.0.0"
        icon = "drawable/ic_studio_logo.svg"
    }

    window {
        title = "GenericUI Studio"
        width = 1440
        height = 900
        minWidth = 960
        minHeight = 600
        resizable = true
    }

    theme {
        primaryColor = "#2A80FF"
        backgroundColor = "#121519"
        darkMode = true
    }

    backends {
        windows()
        macos()
        gtk()
        qt()
        swing {
            useLwjglGL = true
            useVulkan = true
        }
        android {
            minSdk = 26
            targetSdk = 35
        }
        ios()
        web() // Compiles both wasmJs (default) and js(IR) (fallback) with genericui-loader.js
    }

    // Optional hierarchical variant overrides (common -> systemCommon -> desktopCommon -> nativeDesktopCommon -> linuxCommon, mobileCommon, or per-backend):
    desktopCommon {
        window {
            width = 1600
            height = 960
        }
    }

    extensions {
        gpu()              // :extensions:gpu
        imageProcessing()  // :extensions:imageprocessing (transitively enables gpu)
        overlay()          // :extensions:overlay (transitively enables gpu)
        canvas2d()         // :extensions:canvas2d
        canvas3d()         // :extensions:canvas3d (transitively enables gpu)
        media()            // :extensions:media (transitively enables gpu)
        mediaControls()    // :extensions:mediacontrols (transitively enables media + overlay -> gpu)
        webView()          // :extensions:webview (Chromium + NJS JIT on Desktop; native on Mobile/Web)
        alien()            // :extensions:alien
        codeEditor()       // :extensions:codeeditor (transitively enables canvas2d)
        aiChat()           // :extensions:aichat (transitively enables codeEditor -> canvas2d)
        threeJs()          // :extensions:threejs (transitively enables gpu + canvas3d)
        box2d()            // :extensions:box2d (transitively enables canvas2d + canvas3d -> gpu)
        box3d()            // :extensions:box3d (transitively enables canvas3d + threeJs -> gpu)
        dsl()              // :extensions:dsl + enabled extension :dsl submodules
    }

    resources {
        generateResourceClass = true
        embedInWindowsExe = true // Compiles Resource.ResourceID entries into a Win32 .rc/.res linked into the .exe
    }
}
```

---

## Code Examples (`src/commonMain/kotlin`)

### 1. `@MainAppFragment`, `@AppFragment`, `FragmentBundle` CLI Args, `FragmentHostFragment`, & `Resource.ResourceID`

```kotlin
package com.example.studio

import com.nexustools.genericui.core.Component
import com.nexustools.genericui.core.fragment.AppFragment
import com.nexustools.genericui.core.fragment.Fragment
import com.nexustools.genericui.core.fragment.FragmentBundle
import com.nexustools.genericui.core.fragment.FragmentHostFragment
import com.nexustools.genericui.core.fragment.FragmentNavigationChromeConfig
import com.nexustools.genericui.core.fragment.FragmentTransitionSpec
import com.nexustools.genericui.core.fragment.MainAppFragment
import com.nexustools.genericui.widget.action.ActionItem
import com.nexustools.genericui.widget.action.TitleBar
import com.nexustools.genericui.widget.basic.Button
import com.nexustools.genericui.widget.basic.Image
import com.nexustools.genericui.widget.basic.Label
import com.nexustools.genericui.widget.layout.VBox

@MainAppFragment
class StudioRootFragment : FragmentHostFragment(
    initialFragmentFactory = { ProjectDashboardFragment() },
    defaultTransition = FragmentTransitionSpec.FadeExpandShrink(durationMs = 240),
    navigationChromeConfig = FragmentNavigationChromeConfig(
        showBackButton = true,
        showForwardButton = true,
        syncWithWebHistory = true // Syncs URL #hash and window.history.state on :web
    )
) {
    override fun onCreate(savedInstanceState: FragmentBundle?) {
        super.onCreate(savedInstanceState)

        // Read command-line arguments on system targets (Windows, MacOS, Gtk, Qt, Swing, Android, IOS):
        if (savedInstanceState?.hasCommandLineArgs() == true) {
            val cliArgs = savedInstanceState.getCommandLineArgs()
            println("Launched on ${app?.backendTarget} with CLI args: $cliArgs")
        }

        window?.titleBar = TitleBar(
            title = resources.getString(Resource.string.app_title),
            actions = listOf(
                ActionItem(id = "nav.settings", label = "Settings") {
                    fragmentHost.push(SettingsFragment(), route = "settings")
                }
            )
        )
    }
}

@AppFragment(route = "dashboard")
class ProjectDashboardFragment : Fragment() {
    override fun onCreateView(): Component = VBox(spacing = 12).apply {
        addChild(Image(Resource.drawable.ic_studio_logo))
        addChild(Label(resources.getString(Resource.string.welcome_heading)))
        addChild(
            Button("Open Settings").apply {
                setOnClickListener {
                    launchFragment(SettingsFragment::class, FragmentBundle().apply {
                        putString("section", "appearance")
                    })
                }
            }
        )
    }
}

@AppFragment(route = "settings")
class SettingsFragment : Fragment() {
    override fun onCreateView(): Component = VBox(spacing = 8).apply {
        val section = arguments.getString("section", "general")
        addChild(Label("Settings — Section: $section"))
    }
}
```

---

### 2. Base `:common` Image API (`ImageLoader`, `ImageBuffer`) & Offscreen GPU `:extensions:imageprocessing`

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
    source = ImageSource.fromBytes(resources.readBytes(Resource.raw.sample_photo)),
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

### 3. Helper Fragments (`AdaptiveSplitTabFragment` & `DockWorkspaceFragment`)

```kotlin
import com.nexustools.genericui.core.fragment.AdaptiveSplitTabFragment
import com.nexustools.genericui.core.fragment.DockWorkspaceFragment
import com.nexustools.genericui.core.fragment.MainAppFragment
import com.nexustools.genericui.docking.DockPosition
import com.nexustools.genericui.docking.WorkspaceWindowMode
import com.nexustools.genericui.widget.action.ActionItem
import com.nexustools.genericui.widget.action.TitleBar

// Multi-column split on tablet/desktop, fullscreen player in phone landscape, tabs in portrait
@MainAppFragment
class MediaSuiteMainFragment : AdaptiveSplitTabFragment() {
    override fun onConfigureAdaptiveLayout() {
        setNavBar(TitleBar(title = "Media Suite", subtitle = "Streaming & Local Media"))
        setPrimaryMediaFragment(PlayerViewportFragment())
        addSecondarySlot(id = "shows", title = "Shows & Podcasts", fragment = ShowBrowserFragment())
        addSecondarySlot(id = "queue", title = "Up Next Queue", fragment = PlaybackQueueFragment())
        isFullscreenPrimaryInLandscape = true
    }
}

// Live runtime switching between GIMP Single-Window Mode and GIMP Multi-Window Mode
@MainAppFragment
class StudioDockWorkspaceFragment : DockWorkspaceFragment() {
    override fun onConfigureWorkspace() {
        setNavBar(
            TitleBar(
                title = "Hybrid Vector & Raster Studio",
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

### 4. `:extensions:canvas2d` (`Canvas2D`) & `:extensions:canvas3d` (`GLCanvas`, `VKCanvas`) with `registerVBlankRender(deltaMs)`

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

### 5. `:extensions:media` & `:extensions:mediacontrols` — `VideoPlayer`, `MediaControlBar`, & Translucent `:extensions:overlay`

```kotlin
import com.nexustools.genericui.ext.media.VideoCodec
import com.nexustools.genericui.ext.media.VideoContainerFormat
import com.nexustools.genericui.ext.media.VideoPlayer
import com.nexustools.genericui.ext.mediacontrols.MediaControlBar
import com.nexustools.genericui.ext.overlay.OverlayView

val player = VideoPlayer().apply {
    val capabilities = getSupportedCodecs()
    println("Hardware HEVC supported: ${capabilities.supportsHardwareAccelerated(VideoCodec.HEVC_H265)}")
    println("AV1 in MKV supported: ${canPlayCodec(VideoCodec.AV1, VideoContainerFormat.MKV)}")

    setSourceUrl("https://cdn.example.com/episodes/ep01-hdr10-master.m3u8")
    setSubtitleTrackUrl("https://cdn.example.com/episodes/ep01.en.vtt")
    play()
}

// Translucent auto-hiding transport controls composited over the hardware video surface via :extensions:overlay
val playerWithOverlayControls = OverlayView(
    baseContent = player,
    overlayContent = MediaControlBar(player = player, autoHideDelayMs = 3000L)
)
```

---

## Categorized Documentation & Submodule Directory

All documentation across `GenericUI` and its submodules is organized by category and cross-referenced. Start with the [**Master Documentation Index (`docs/README.md`)**](./docs/README.md) or browse by category below:

### Category 1 — Core Architecture, Fragments, Application, Resources, SourceSets, & UI Components
- [**Master Documentation Index (`docs/README.md`)**](./docs/README.md) — Central navigation hub for all guides across the framework and its submodules.
- [**Core Architecture (`docs/architecture.md`)**](./docs/architecture.md) — Multi-tier module hierarchy (`:common`, `:systemCommon`, `:desktop:common`, `:desktop:nativeCommon`, `:desktop:linuxCommon`, `:mobile:common`, `:web`), Deferred Upward `NativeImpl` Resolution, `TitleCapabilities` single-placement rules, and 1-Class-Per-File standard.
- [**Fragments & Windows (`docs/fragments-and-windows.md`)**](./docs/fragments-and-windows.md) — `@MainAppFragment`, `@AppFragment`, `AppTarget` specificity resolution (`5 -> 0`), `FragmentBundle` CLI args on system targets, `IFragment`, `launchFragment(FragmentClass, LaunchArgs)` & `FragmentLaunchMode`, 1:1 Android `AppCompatActivity` generation, `IWindow` & `TitleCapabilities`, `FragmentHostFragment`, `FragmentHostSystem`, `FadeExpandShrink`, and Web `#hash` + `window.history.state`.
- [**Application & `BackendTarget` (`docs/application.md`)**](./docs/application.md) — Zero-boilerplate `@MainAppFragment` bootstrapping, optional `@AppApplication`, `BackendTarget` enum & multi-target group checks (`isSystem`, `isDesktop`, `isNativeDesktop`, `isLinux`, `isMobile`, `isApple`, `isUnix`, `isPosix`, `isNative`, `isJvm`, `isTouchPrimary`, `isBrowser`), and `IApplication` capabilities.
- [**Resource System (`docs/resources.md`)**](./docs/resources.md) — Android-style `Resource` (`Resource.drawable.*`, `Resource.string.*`, `Resource.raw.*`) & `Resource.ResourceID` generation across shared/library modules and translation across all 8 backends (`WebResourceResolverImpl` URLs, `WindowsResourceResolverImpl` embedded `.exe` Win32 `.rc`/`.res`, Android `R.*`, Apple `NSBundle`, GTK `GResource`, Qt `QResource`, Swing JAR classpath).
- [**Hierarchical SourceSet Setup (`docs/sourcesets.md`)**](./docs/sourcesets.md) — Single-`commonMain` default + optional multi-tier KMP sourceSet tree (`commonMain` $\to$ `webMain` & `systemMain` $\to$ `desktopMain` (`swingMain`, `nativeDesktopMain` $\to$ `windowsMain`, `macosMain`, `linuxMain` $\to$ `gtkMain`, `qtMain`) & `mobileMain` (`androidMain`, `iosMain`)).
- [**Components, Helper Fragments, Docking, & Styling (`docs/components-and-docking.md`)**](./docs/components-and-docking.md) — 75+ widgets, `FragmentHostFragment`, `DockWorkspaceFragment`, `AdaptiveSplitTabFragment`, `FileBrowserDialog`, true lazy views, Dual-Mode Bootstrap Enums + SASS/CSS, and `LinearGradient` / `RadialGradient` background styling.
- [**Utilities, Binary Codecs (`BinaryJson` `.bson` & `BinaryPlist` `.plist`), & `PreferenceStore` (`docs/utils.md`)**](./docs/utils.md) — Complete reference for `BinaryJson` (`.bson`), `BinaryPlist` (`bplist00` `.plist`), cross-platform `PreferenceStore` & `PreferenceObjectCodec`, `UserDirectory` / `ConfigDirectory` / `CacheDirectory`, `UiTimer`, `ResourceID` / `ResourceResolver`, geometry, color, SASS/CSS, and lazy diffing.
- [**Media & Hardware Canvases (`docs/media-and-canvases.md`)**](./docs/media-and-canvases.md) — Base `:common` Image API, `AbstractCanvas` (`registerVBlankRender` with `deltaMs`), `:extensions:gpu` (Google ANGLE on Windows, MoltenVK on Apple), `:extensions:imageprocessing`, `:extensions:overlay`, `:extensions:canvas2d`, `:extensions:canvas3d`, `:extensions:media`, and `:extensions:mediacontrols`.
- [**Agent & Developer Guidelines (`AGENTS.md`)**](./AGENTS.md) — Authoritative engineering rules and invariants.

### Category 2 — Build System, Gradle Plugin, & IDE Tooling
- [**Gradle Plugin Guide (`docs/gradle-plugin.md`)**](./docs/gradle-plugin.md) — Full `genericui { ... }` DSL reference (`app`, `window`, `theme`, `backends`, `extensions`, hierarchical variant scopes, `android { manifest { ... }; services { ... } }`, `resources`, `sourceSets`, and registered tasks).
- [**IntelliJ IDEA Plugin Guide (`docs/idea-plugin.md`)**](./docs/idea-plugin.md) — New Project Wizard for single-`commonMain` `@MainAppFragment` projects and live `Manage GenericUI Targets & Extensions` checkbox dialog (`:idea-plugin`).

### Category 3 — Platform Backends (8 Backends Across Common Foundation & 3 Submodules)
- [**Platform Backends Overview (`docs/backends.md`)**](./docs/backends.md) — Feature comparison, module hierarchy, and build task reference across all 8 backends.
- [**Desktop Submodule (`desktop/README.md`)**](./desktop/README.md) *(Contains zero VLC/media code; all VLC/media code is strictly in `:extensions:media`)*:
  - [`:desktop:common` — Pure-Kotlin Desktop Foundation (`desktop/docs/common.md`)](./desktop/docs/common.md)
  - [`:desktop:nativeCommon` — Native C/C++/ObjC Desktop Foundation (`desktop/docs/nativeCommon.md`)](./desktop/docs/nativeCommon.md)
  - [`:desktop:linuxCommon` — Linux XDG Desktop Foundation (`desktop/docs/linuxCommon.md`)](./desktop/docs/linuxCommon.md)
  - [`:desktop:windows` — Windows Win32 / Direct2D / Google ANGLE / Vulkan (`desktop/docs/windows.md`)](./desktop/docs/windows.md)
  - [`:desktop:macos` — macOS AppKit / CoreGraphics / MoltenVK (`desktop/docs/macos.md`)](./desktop/docs/macos.md)
  - [`:desktop:gtk` — Linux GTK4 / Cairo / EGL / Vulkan (`desktop/docs/gtk.md`)](./desktop/docs/gtk.md)
  - [`:desktop:qt` — Linux Qt6 / QPainter / QVulkanWindow (`desktop/docs/qt.md`)](./desktop/docs/qt.md)
  - [`:desktop:swing` — JVM Swing / Java2D / Optional LWJGL OpenGL ES & Vulkan (`desktop/docs/swing.md`)](./desktop/docs/swing.md)
- [**Mobile Submodule (`mobile/README.md`)**](./mobile/README.md):
  - [`:mobile:common` — Mobile Layout, Gestures, PiP, & Lazy Pool (`mobile/docs/common.md`)](./mobile/docs/common.md)
  - [`:mobile:android` — Android 1:1 `AppCompatActivity` Generation / Material 3 / RecyclerView / SAF / Vulkan (`mobile/docs/android.md`)](./mobile/docs/android.md)
  - [`:mobile:ios` — iOS UIKit / CADisplayLink / UIDocumentPicker / MoltenVK (`mobile/docs/ios.md`)](./mobile/docs/ios.md)
- [**Web Submodule (`web/README.md`)**](./web/README.md):
  - [`:web` — HTML5 DOM / Canvas2D / WebGL2 / WebGPU Backend (`web/docs/web-backend.md`)](./web/docs/web-backend.md)
  - [`:web` — Wasm-Default + JS IR Fallback Loader, Bootstrap Splash Screen, SHA-1 Hashing, & `.svg` Web Icon (`web/docs/wasm-js-loader-and-splash.md`)](./web/docs/wasm-js-loader-and-splash.md)

### Category 4 — First-Party Extensions (15 Extensions + 9 `:dsl` Submodules)
- [**Extensions Overview (`docs/extensions.md`)**](./docs/extensions.md) | [**Extensions Submodule Index (`extensions/README.md`)**](./extensions/README.md)
- **GPU Compute, Image Processing, Compositing Overlay, & Canvases**:
  - [`:extensions:gpu` — Cross-Platform Vulkan & OpenGL ES GPU Compute (`extensions/docs/gpu.md`)](./extensions/docs/gpu.md)
  - [`:extensions:imageprocessing` — Offscreen GPU Filters & Layer Compositor (`extensions/docs/imageprocessing.md`)](./extensions/docs/imageprocessing.md)
  - [`:extensions:overlay` — Cross-Platform Translucent GPU/Native Compositing Overlay (`extensions/docs/overlay.md`)](./extensions/docs/overlay.md)
  - [`:extensions:canvas2d` & `:extensions:canvas2d:dsl` — Extended 2D Canvas & Curve Editor (`extensions/docs/canvas2d.md`)](./extensions/docs/canvas2d.md)
  - [`:extensions:canvas3d` & `:extensions:canvas3d:dsl` — Extended OpenGL ES & Vulkan Canvases (`extensions/docs/canvas3d.md`)](./extensions/docs/canvas3d.md)
- **Multimedia, Media Controls, Embedded Web Engines, & Alien Views**:
  - [`:extensions:media` & `:extensions:media:dsl` — VideoPlayer, AudioPlayer, AudioAnalyzer, & Desktop VLC HDR Engine (`extensions/docs/media.md`)](./extensions/docs/media.md)
  - [`:extensions:mediacontrols` — Reusable Transport Bar, Seek Scrubber, & Auto-Hiding OSD (`extensions/docs/mediacontrols.md`)](./extensions/docs/mediacontrols.md)
  - [`:extensions:webview` & `:extensions:webview:dsl` — Embedded WebView & `:NJS` Bridge (`extensions/docs/webview.md`)](./extensions/docs/webview.md)
  - [`:extensions:alien` — Escape-Hatch Raw Platform Widget Wrapper (`extensions/docs/alien.md`)](./extensions/docs/alien.md)
  - [**`:NJS` — Nexus JavaScript & TypeScript Engine (`lib/NJS/README.md`)**](./lib/NJS/README.md) | [**NJS Master Docs Index (`lib/NJS/docs/README.md`)**](./lib/NJS/docs/README.md):
    - [NJS Core Architecture (`lib/NJS/docs/architecture.md`)](./lib/NJS/docs/architecture.md)
    - [`:NJS:common` — Lexer, Parser, ES2024+/TS AST, & Bytecode IR (`lib/NJS/docs/common.md`)](./lib/NJS/docs/common.md)
    - [`:NJS:native` — AsmJit / SLJIT Native JIT Engine (`lib/NJS/docs/native-jit.md`)](./lib/NJS/docs/native-jit.md)
    - [`:NJS:jvm` — Java/JVM Bytecode Compiler System (`lib/NJS/docs/jvm-compiler.md`)](./lib/NJS/docs/jvm-compiler.md)
    - [`:NJS:runtime` — Execution Contexts, Event Loop, & Promises (`lib/NJS/docs/runtime.md`)](./lib/NJS/docs/runtime.md)
    - [`:NJS:compiler` — Optimizer, TypeScript Stripper, & Bundler (`lib/NJS/docs/compiler.md`)](./lib/NJS/docs/compiler.md)
    - [`:NJS:nodejs` — Node.js Standard Library Compatibility (`lib/NJS/docs/nodejs-compat.md`)](./lib/NJS/docs/nodejs-compat.md)
    - [`:NJS:app` — CLI REPL & GenericUI `:extensions:webview` Embedding (`lib/NJS/docs/cli-and-embedding.md`)](./lib/NJS/docs/cli-and-embedding.md)
    - [`:NJS:gradle-plugin` — NJS Gradle Plugin (`lib/NJS/docs/gradle-plugin.md`)](./lib/NJS/docs/gradle-plugin.md)
- **Code Editing & Multi-Provider AI Agents**:
  - [`:extensions:codeeditor` & `:extensions:codeeditor:dsl` — Syntax-Highlighted IDE Code Editor (`extensions/docs/codeeditor.md`)](./extensions/docs/codeeditor.md)
  - [`:extensions:aichat` & `:extensions:aichat:dsl` — Multi-Provider AI Chat & Tool-Call Loop (`extensions/docs/aichat.md`)](./extensions/docs/aichat.md)
- **3D Scene Graph & 2D/3D Rigid-Body Physics**:
  - [`:extensions:threejs` & `:extensions:threejs:dsl` — Kotlin Multiplatform Three.js 3D Engine (`extensions/docs/threejs.md`)](./extensions/docs/threejs.md)
  - [`:extensions:box2d` & `:extensions:box2d:dsl` — 2D Rigid-Body Physics Engine (`extensions/docs/box2d.md`)](./extensions/docs/box2d.md)
  - [`:extensions:box3d` & `:extensions:box3d:dsl` — 3D Rigid-Body Physics Engine (`extensions/docs/box3d.md`)](./extensions/docs/box3d.md)
- **Declarative Kotlin UI & Style DSL**:
  - [`:extensions:dsl` & Extension `:dsl` Submodules (`extensions/docs/dsl.md`)](./extensions/docs/dsl.md)

### Category 5 — Automated Cross-Platform UI Testing (`JestElectricRunner`)
- [**Testing Overview (`docs/testing.md`)**](./docs/testing.md) | [**Tests Submodule Index (`tests/README.md`)**](./tests/README.md)
- [`JestElectricRunner` BDD & Robolectric API Guide (`tests/docs/jest-electric-runner.md`)](./tests/docs/jest-electric-runner.md)
- [Platform Test Runners & Live Visual Windows Testing (`tests/docs/platform-test-runners.md`)](./tests/docs/platform-test-runners.md)
