# GenericUI Master Documentation Index

Welcome to the **`GenericUI`** documentation hub. All documentation across the root repository and its Git submodules (`desktop`, `mobile`, `web`, `extensions`, `tests`, `NJS`) is organized by category and cross-referenced.

---

## Category 1 — Core Architecture, SourceSets, & UI Components

| Document | Summary |
| :--- | :--- |
| [**Core Architecture Guide (`architecture.md`)**](architecture.md) | Deferred Upward `NativeImpl` Resolution, Native-First widget philosophy, 1-Class-Per-File standard, symmetrical 17-subpackage structure, and non-unified canvases. |
| [**Hierarchical SourceSet Setup (`sourcesets.md`)**](sourcesets.md) | Complete guide to the multi-tier KMP sourceSet tree: `common` $\to$ `web` & `systemCommon` $\to$ `desktopCommon` (`swing`, `desktopNativeCommon` $\to$ `windows`, `mac`, `linuxCommon` $\to$ `gtk`, `qt`) & `mobileCommon` (`android`, `ios`). |
| [**Components, Fragments, Docking, & Styling (`components-and-docking.md`)**](components-and-docking.md) | 75+ components, `FragmentApplication`, 1:1 Android `Activity`-compatible `Fragment`s, Helper Fragments (`DockWorkspaceFragment`, `OverlayDockWorkspaceFragment`, `AdaptiveSplitTabFragment`), Dual-Mode Bootstrap Enums + SASS/CSS, and `LinearGradient` / `RadialGradient` background styling. |
| [**Media & Hardware Canvases (`media-and-canvases.md`)**](media-and-canvases.md) | `AbstractCanvas` (`Canvas2D`, `GLCanvas`, `VKCanvas`), `registerVBlankRender` (`deltaMs`), Google ANGLE on Windows, MoltenVK on Apple, and `:extensions:media` VLC HDR video/audio. |

---

## Category 2 — Build System, Gradle Plugin, & IDE Tooling

| Document | Summary |
| :--- | :--- |
| [**Gradle Plugin Guide (`gradle-plugin.md`)**](gradle-plugin.md) | Full reference for `id("com.nexustools.genericui")`: `backends`, `application` (`webIcon` `.svg` preference), `graphics`, `extensions`, `styles` (SASS $\to$ CSS & precompiled Kotlin), `resources` (`R.kt` / `RC.kt`), `sourceSets`, and `packageGenericUiWebDistribution`. |
| [**IntelliJ IDEA Plugin Guide (`idea-plugin.md`)**](idea-plugin.md) | New Project Wizard and live `Manage GenericUI Targets & Extensions` checkbox dialog (`:idea-plugin`). |
| [**Agent & Developer Rules (`../AGENTS.md`)**](../AGENTS.md) | Authoritative engineering rules and architectural invariants for contributors and AI agents. |

---

## Category 3 — Platform Backends (8 Targets Across 3 Submodules)

- [**Platform Backends Overview (`backends.md`)**](backends.md)
- [**Desktop Submodule (`../desktop/README.md`)**](../desktop/README.md) *(Zero VLC/media code; all media code is strictly in `:extensions:media`)*:
  - [`:desktop:shared` — Shared Native Desktop Core (`../desktop/docs/shared.md`)](../desktop/docs/shared.md)
  - [`:desktop:windows` — Windows Win32 / Direct2D / ANGLE / Vulkan (`../desktop/docs/windows.md`)](../desktop/docs/windows.md)
  - [`:desktop:macos` — macOS AppKit / CoreGraphics / MoltenVK (`../desktop/docs/macos.md`)](../desktop/docs/macos.md)
  - [`:desktop:gtk` — Linux GTK4 / Cairo / EGL / Vulkan (`../desktop/docs/gtk.md`)](../desktop/docs/gtk.md)
  - [`:desktop:qt` — Linux Qt6 / QPainter / QVulkanWindow (`../desktop/docs/qt.md`)](../desktop/docs/qt.md)
  - [`:desktop:swing` — JVM Swing / Java2D / Optional LWJGL GL & Vulkan (`../desktop/docs/swing.md`)](../desktop/docs/swing.md)
- [**Mobile Submodule (`../mobile/README.md`)**](../mobile/README.md):
  - [`:mobile:shared` — Shared Mobile Layout, Gestures, PiP, & Lazy Pool (`../mobile/docs/shared.md`)](../mobile/docs/shared.md)
  - [`:mobile:android` — Android Material 3 / RecyclerView / SAF / Vulkan (`../mobile/docs/android.md`)](../mobile/docs/android.md)
  - [`:mobile:ios` — iOS UIKit / CADisplayLink / UIDocumentPicker / MoltenVK (`../mobile/docs/ios.md`)](../mobile/docs/ios.md)
- [**Web Submodule (`../web/README.md`)**](../web/README.md):
  - [`:web` — HTML5 DOM / Canvas2D / WebGL2 / WebGPU Backend (`../web/docs/web-backend.md`)](../web/docs/web-backend.md)
  - [`:web` — Wasm-Default + JS IR Fallback Loader, Splash Screen, & `.svg` Web Icon (`../web/docs/wasm-js-loader-and-splash.md`)](../web/docs/wasm-js-loader-and-splash.md)

---

## Category 4 — First-Party Extensions (12 Extensions + 9 `:dsl` Submodules)

- [**Extensions Overview (`extensions.md`)**](extensions.md) | [**Extensions Submodule Index (`../extensions/README.md`)**](../extensions/README.md)
- **GPU, Image Processing, & Canvases**:
  - [`:extensions:gpu` — Cross-Platform Vulkan & OpenGL ES GPU Compute (`../extensions/docs/gpu.md`)](../extensions/docs/gpu.md)
  - [`:extensions:imageprocessing` — Offscreen GPU Filters & Layer Compositor (`../extensions/docs/imageprocessing.md`)](../extensions/docs/imageprocessing.md)
  - [`:extensions:canvas2d` & `:extensions:canvas2d:dsl` — Extended 2D Canvas & Curve Editor (`../extensions/docs/canvas2d.md`)](../extensions/docs/canvas2d.md)
  - [`:extensions:canvas3d` & `:extensions:canvas3d:dsl` — Extended OpenGL ES & Vulkan Canvases (`../extensions/docs/canvas3d.md`)](../extensions/docs/canvas3d.md)
- **Multimedia & Embedded Web**:
  - [`:extensions:media` & `:extensions:media:dsl` — VideoPlayer, AudioPlayer, AudioAnalyzer, & Desktop VLC HDR Engine (`../extensions/docs/media.md`)](../extensions/docs/media.md)
  - [`:extensions:webview` & `:extensions:webview:dsl` — Embedded WebView & `:NJS` Bridge (`../extensions/docs/webview.md`)](../extensions/docs/webview.md)
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
