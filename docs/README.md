# GenericUI Master Documentation Index

Welcome to the **`GenericUI`** documentation hub. All documentation across the root repository and its submodules (`desktop`, `mobile`, `web`, `extensions`, `tests`, `lib/NJS`) is organized by category and cross-referenced.

---

## Category 1 — Core Architecture, Fragments, Application, Resources, SourceSets, & UI Components

| Document | Summary |
| :--- | :--- |
| [**Core Architecture Guide (`architecture.md`)**](architecture.md) | Multi-tier module hierarchy (`:common`, `:systemCommon`, `:desktop:common`, `:desktop:nativeCommon`, `:desktop:linuxCommon`, `:mobile:common`, `:web`), Deferred Upward `NativeImpl` Resolution, `TitleCapabilities` single-placement rules, and 1-Class-Per-File standard. |
| [**Fragments & Windows Guide (`fragments-and-windows.md`)**](fragments-and-windows.md) | `@MainAppFragment` & `@AppFragment`, `AppTarget` specificity resolution (`5 -> 0`), `FragmentBundle` CLI args on system targets, `IFragment`, `launchFragment(FragmentClass, LaunchArgs)` & `FragmentLaunchMode`, 1:1 Android `AppCompatActivity` generation, `IWindow` & `TitleCapabilities`, `FragmentHostFragment`, `FragmentHostSystem`, `FadeExpandShrink`, and Web `#hash` + `window.history.state`. |
| [**Application & `BackendTarget` Guide (`application.md`)**](application.md) | Zero-boilerplate `@MainAppFragment` bootstrapping, optional `@AppApplication`, `BackendTarget` enum & multi-target group checks (`isSystem`, `isDesktop`, `isNativeDesktop`, `isLinux`, `isMobile`, `isApple`, `isUnix`, `isPosix`, `isNative`, `isJvm`, `isTouchPrimary`, `isBrowser`), and `IApplication` capabilities. |
| [**Resource System Guide (`resources.md`)**](resources.md) | Android-style `Resource` (`Resource.drawable.*`, `Resource.string.*`, `Resource.raw.*`) & `Resource.ResourceID` generation across shared/library modules and translation across all 8 backends (`WebResourceResolverImpl` URLs, `WindowsResourceResolverImpl` embedded `.exe` Win32 `.rc`/`.res`, Android `R.*`, Apple `NSBundle`, GTK `GResource`, Qt `QResource`, Swing JAR classpath). |
| [**Hierarchical SourceSet Setup (`sourcesets.md`)**](sourcesets.md) | Single-`commonMain` default + optional hierarchical source sets (`commonMain` $\to$ `webMain` & `systemMain` $\to$ `desktopMain` (`swingMain`, `nativeDesktopMain` $\to$ `windowsMain`, `macosMain`, `linuxMain` $\to$ `gtkMain`, `qtMain`) & `mobileMain` (`androidMain`, `iosMain`)). |
| [**Components, Helper Fragments, Docking, & Styling (`components-and-docking.md`)**](components-and-docking.md) | 75+ components, `FragmentHostFragment`, `DockWorkspaceFragment`, `AdaptiveSplitTabFragment`, `FileBrowserDialog`, true lazy views, Dual-Mode Bootstrap Enums + SASS/CSS, and `LinearGradient` / `RadialGradient` background styling. |
| [**Utilities, Binary Codecs, & `PreferenceStore` (`utils.md`)**](utils.md) | Catalog of all GenericUI utilities: `BinaryJson` (`.bson`), `BinaryPlist` (`bplist00` `.plist`), cross-platform `PreferenceStore` & `PreferenceObjectCodec`, `UserDirectory` / `ConfigDirectory` / `CacheDirectory`, `UiTimer`, `ResourceID` / `ResourceResolver`, geometry, color, SASS/CSS, and lazy diffing. |
| [**Media & Hardware Canvases (`media-and-canvases.md`)**](media-and-canvases.md) | Base `:common` Image API, `AbstractCanvas` (`registerVBlankRender` with `deltaMs`), `:extensions:gpu` (Google ANGLE on Windows, MoltenVK on Apple), `:extensions:imageprocessing`, `:extensions:overlay`, `:extensions:canvas2d`, `:extensions:canvas3d`, `:extensions:media`, and `:extensions:mediacontrols`. |

---

## Category 2 — Build System, Gradle Plugin, & IDE Tooling

| Document | Summary |
| :--- | :--- |
| [**Gradle Plugin Guide (`gradle-plugin.md`)**](gradle-plugin.md) | Full reference for `id("com.nexustools.genericui")`: `app`, `window`, `theme`, `backends`, `extensions` (including `overlay()` & `mediaControls()`), variant override blocks (`common`, `systemCommon`, `desktopCommon`, `nativeDesktopCommon`, `linuxCommon`, `mobileCommon`, plus per-backend scopes), `android { manifest { ... }; services { ... } }`, `resources`, `sourceSets`, and registered tasks. |
| [**IntelliJ IDEA Plugin Guide (`idea-plugin.md`)**](idea-plugin.md) | New Project Wizard for single-`commonMain` `@MainAppFragment` projects and live `Manage GenericUI Targets & Extensions` checkbox dialog (`:idea-plugin`). |
| [**Agent & Developer Rules (`../AGENTS.md`)**](../AGENTS.md) | Authoritative engineering rules and architectural invariants for contributors and AI agents. |

---

## Category 3 — Platform Backends (8 Targets Across Common Foundation & 3 Submodules)

- [**Platform Backends Overview (`backends.md`)**](backends.md)
- [**Desktop Submodule (`../desktop/README.md`)**](../desktop/README.md) *(Zero VLC/media code; all media code is strictly in `:extensions:media`)*:
  - [`:desktop:common` — Pure-Kotlin Desktop Foundation (`../desktop/docs/common.md`)](../desktop/docs/common.md)
  - [`:desktop:nativeCommon` — Native C/C++/ObjC Desktop Foundation (`../desktop/docs/nativeCommon.md`)](../desktop/docs/nativeCommon.md)
  - [`:desktop:linuxCommon` — Linux XDG Desktop Foundation (`../desktop/docs/linuxCommon.md`)](../desktop/docs/linuxCommon.md)
  - [`:desktop:windows` — Windows Win32 / Direct2D / ANGLE / Vulkan (`../desktop/docs/windows.md`)](../desktop/docs/windows.md)
  - [`:desktop:macos` — macOS AppKit / CoreGraphics / MoltenVK (`../desktop/docs/macos.md`)](../desktop/docs/macos.md)
  - [`:desktop:gtk` — Linux GTK4 / Cairo / EGL / Vulkan (`../desktop/docs/gtk.md`)](../desktop/docs/gtk.md)
  - [`:desktop:qt` — Linux Qt6 / QPainter / QVulkanWindow (`../desktop/docs/qt.md`)](../desktop/docs/qt.md)
  - [`:desktop:swing` — JVM Swing / Java2D / Optional LWJGL GL & Vulkan (`../desktop/docs/swing.md`)](../desktop/docs/swing.md)
- [**Mobile Submodule (`../mobile/README.md`)**](../mobile/README.md):
  - [`:mobile:common` — Mobile Layout, Gestures, PiP, & Lazy Pool (`../mobile/docs/common.md`)](../mobile/docs/common.md)
  - [`:mobile:android` — Android 1:1 `AppCompatActivity` Generation / Material 3 / RecyclerView / SAF / Vulkan (`../mobile/docs/android.md`)](../mobile/docs/android.md)
  - [`:mobile:ios` — iOS UIKit / CADisplayLink / UIDocumentPicker / MoltenVK (`../mobile/docs/ios.md`)](../mobile/docs/ios.md)
- [**Web Submodule (`../web/README.md`)**](../web/README.md):
  - [`:web` — HTML5 DOM / Canvas2D / WebGL2 / WebGPU Backend (`../web/docs/web-backend.md`)](../web/docs/web-backend.md)
  - [`:web` — Wasm-Default + JS IR Fallback Loader, Splash Screen, & `.svg` Web Icon (`../web/docs/wasm-js-loader-and-splash.md`)](../web/docs/wasm-js-loader-and-splash.md)

---

## Category 4 — First-Party Extensions (15 Extensions + 9 `:dsl` Submodules)

- [**Extensions Overview (`extensions.md`)**](extensions.md) | [**Extensions Submodule Index (`../extensions/README.md`)**](../extensions/README.md)
- **GPU, Image Processing, Compositing Overlay, & Canvases**:
  - [`:extensions:gpu` — Cross-Platform Vulkan & OpenGL ES GPU Compute (`../extensions/docs/gpu.md`)](../extensions/docs/gpu.md)
  - [`:extensions:imageprocessing` — Offscreen GPU Filters & Layer Compositor (`../extensions/docs/imageprocessing.md`)](../extensions/docs/imageprocessing.md)
  - [`:extensions:overlay` — Cross-Platform Translucent GPU/Native Compositing Overlay (`../extensions/docs/overlay.md`)](../extensions/docs/overlay.md)
  - [`:extensions:canvas2d` & `:extensions:canvas2d:dsl` — Extended 2D Canvas & Curve Editor (`../extensions/docs/canvas2d.md`)](../extensions/docs/canvas2d.md)
  - [`:extensions:canvas3d` & `:extensions:canvas3d:dsl` — Extended OpenGL ES & Vulkan Canvases (`../extensions/docs/canvas3d.md`)](../extensions/docs/canvas3d.md)
- **Multimedia, Media Controls, Embedded Web, & Alien Views**:
  - [`:extensions:media` & `:extensions:media:dsl` — VideoPlayer, AudioPlayer, AudioAnalyzer, & Desktop VLC HDR Engine (`../extensions/docs/media.md`)](../extensions/docs/media.md)
  - [`:extensions:mediacontrols` — Reusable Transport Bar, Seek Scrubber, & Auto-Hiding OSD (`../extensions/docs/mediacontrols.md`)](../extensions/docs/mediacontrols.md)
  - [`:extensions:webview` & `:extensions:webview:dsl` — Embedded WebView & `:NJS` Bridge (`../extensions/docs/webview.md`)](../extensions/docs/webview.md)
  - [`:extensions:alien` — Escape-Hatch Raw Platform Widget Wrapper (`../extensions/docs/alien.md`)](../extensions/docs/alien.md)
  - [**`:NJS` — Nexus JavaScript & TypeScript Engine (`../lib/NJS/README.md`)**](../lib/NJS/README.md) | [**NJS Master Docs Index (`../lib/NJS/docs/README.md`)**](../lib/NJS/docs/README.md):
    - [NJS Core Architecture (`../lib/NJS/docs/architecture.md`)](../lib/NJS/docs/architecture.md)
    - [`:NJS:common` — Lexer, Parser, AST, & Bytecode IR (`../lib/NJS/docs/common.md`)](../lib/NJS/docs/common.md)
    - [`:NJS:native` — Native JIT Engine (`../lib/NJS/docs/native-jit.md`)](../lib/NJS/docs/native-jit.md)
    - [`:NJS:jvm` — JVM Bytecode Compiler (`../lib/NJS/docs/jvm-compiler.md`)](../lib/NJS/docs/jvm-compiler.md)
    - [`:NJS:runtime` — Runtime, Event Loop, & Promises (`../lib/NJS/docs/runtime.md`)](../lib/NJS/docs/runtime.md)
    - [`:NJS:compiler` — Optimizer & TypeScript Compiler (`../lib/NJS/docs/compiler.md`)](../lib/NJS/docs/compiler.md)
    - [`:NJS:nodejs` — Node.js Compatibility Layer (`../lib/NJS/docs/nodejs-compat.md`)](../lib/NJS/docs/nodejs-compat.md)
    - [`:NJS:app` — CLI & GenericUI WebView Embedding (`../lib/NJS/docs/cli-and-embedding.md`)](../lib/NJS/docs/cli-and-embedding.md)
    - [`:NJS:gradle-plugin` — NJS Gradle Plugin (`../lib/NJS/docs/gradle-plugin.md`)](../lib/NJS/docs/gradle-plugin.md)
- **Code Editor & AI Agents**:
  - [`:extensions:codeeditor` & `:extensions:codeeditor:dsl` — Syntax-Highlighted IDE Code Editor (`../extensions/docs/codeeditor.md`)](../extensions/docs/codeeditor.md)
  - [`:extensions:aichat` & `:extensions:aichat:dsl` — Multi-Provider AI Chat & Tool-Call Loop (`../extensions/docs/aichat.md`)](../extensions/docs/aichat.md)
- **3D Scene Graph & Physics**:
  - [`:extensions:threejs` & `:extensions:threejs:dsl` — Kotlin Multiplatform Three.js 3D Engine (`../extensions/docs/threejs.md`)](../extensions/docs/threejs.md)
  - [`:extensions:box2d` & `:extensions:box2d:dsl` — 2D Rigid-Body Physics Engine (`../extensions/docs/box2d.md`)](../extensions/docs/box2d.md)
  - [`:extensions:box3d` & `:extensions:box3d:dsl` — 3D Rigid-Body Physics Engine (`../extensions/docs/box3d.md`)](../extensions/docs/box3d.md)
- **Declarative Kotlin DSL**:
  - [`:extensions:dsl` & Extension `:dsl` Submodules (`../extensions/docs/dsl.md`)](../extensions/docs/dsl.md)

---

## Category 5 — Automated UI Testing (`JestElectricRunner`)

- [**Testing Overview (`testing.md`)**](testing.md) | [**Tests Submodule Index (`../tests/README.md`)**](../tests/README.md)
- [`JestElectricRunner` BDD & Robolectric API Guide (`../tests/docs/jest-electric-runner.md`)](../tests/docs/jest-electric-runner.md)
- [Platform Test Runners & Live Visual Windows Testing (`../tests/docs/platform-test-runners.md`)](../tests/docs/platform-test-runners.md)
