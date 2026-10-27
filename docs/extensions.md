# GenericUI First-Party Extensions Guide

> **Root Repository**: [**GenericUI (`../README.md`)**](../README.md) | **Master Docs Index**: [**`docs/README.md`**](./README.md)  
> **Related Guides**: [Extensions Submodule (`../extensions/README.md`)](../extensions/README.md) · [Hybrid Layouts & Helpers (`layouts-and-helpers.md`)](./layouts-and-helpers.md) · [Gradle Plugin (`gradle-plugin.md`)](./gradle-plugin.md) · [SourceSets (`sourcesets.md`)](./sourcesets.md) · [Platform Backends (`backends.md`)](./backends.md) · [Media & Canvases (`media-and-canvases.md`)](./media-and-canvases.md)

GenericUI includes **15 First-Party Extension Modules** (plus companion **`:dsl` submodules**) in the [`extensions/` submodule](../extensions/README.md). When an extension is enabled in `genericui { extensions { ... } }`, the GenericUI Gradle plugin automatically resolves its transitive extension dependencies, wires the appropriate platform submodules into the [SourceSet Hierarchy](./sourcesets.md), and registers the extension's backend providers inside the generated `GenericUiAppDescriptor`—no manual provider initialization is ever required in consumer code.

---

## 1. Categorized Extension Index & Dedicated Documentation

### Category 1 — GPU Compute, Offscreen Image Processing, Overlays, & Hardware Canvases

| Extension Module | Companion DSL Module | Module Structure | Transitive Dependencies | Key Classes & Dedicated Guide |
| :--- | :--- | :--- | :--- | :--- |
| **`:extensions:gpu`** | *(Core GPU API)* | **Project-of-Projects** (`:common`, `:systemCommon`, `:desktop:nativeCommon`, `:desktop:swing`, `:mobile:common`, `:mobile:android`, `:mobile:ios`, `:web`) | `:common` | `GpuDevice`, `GpuOffscreenContext`, `VKContext` (Vulkan 1.2+ SPIR-V primary, MoltenVK on Apple), `GLContext` (OpenGL ES 3.0+ fallback, Google ANGLE on Windows).<br/>[GPU Extension Guide (`extensions/docs/gpu.md`)](../extensions/docs/gpu.md) |
| **`:extensions:imageprocessing`** | *(Fluent Pipeline API)* | **Project-of-Projects** (`:common`, `:systemCommon`, `:web`) | `:extensions:gpu` | `GpuImageProcessor`, `GpuImageFilterChain`, `GpuImageKernel`, `GpuHistogramAnalyzer` operating on `:common`'s `ImageBuffer`.<br/>[Image Processing Guide (`extensions/docs/imageprocessing.md`)](../extensions/docs/imageprocessing.md) |
| **`:extensions:overlay`** | **`:extensions:overlay:dsl`** | **Single KMP Project** | `:common` *(Zero other extension dependencies)* | 100% generic, domain-agnostic overlay architecture: `OverlayHost`, `OverlayHostBox`, `AbstractOverlay`, `PresetOverlay`, `MobileOverlayPreset`, `WebSystemOverlayPreset`, `OverlayPresetResolver`, `OverlayState`, `OverlayActions`, `AutoHideController`, `OverlayInputRouter`, `TrackSeekBar`, `SliderSeekBar`, `OverlayButtonBar`, `CenterTransportCluster`, `OverlayBadgeStrip`, `SubtitleCueView`.<br/>[Overlay Extension Guide (`extensions/docs/overlay.md`)](../extensions/docs/overlay.md) |
| **`:extensions:canvas2d`** | **`:extensions:canvas2d:dsl`** | **Project-of-Projects** (`:common`, `:systemCommon`, `:desktop:nativeCommon`, `:desktop:{windows,macos,gtk,qt,swing}`, `:mobile:common`, `:mobile:{android,ios}`, `:web`) | `:extensions:imageprocessing`, `:extensions:overlay` | `Canvas2D`, `ScrollCanvas2D`, `Canvas2DContext`, `CurveEditor`, `Canvas2DOverlayHost`, `ScrollCanvas2DOverlayHost`.<br/>[Canvas2D Extension Guide (`extensions/docs/canvas2d.md`)](../extensions/docs/canvas2d.md) |
| **`:extensions:canvas3d`** | **`:extensions:canvas3d:dsl`** | **Project-of-Projects** (`:common`, `:systemCommon`, `:desktop:nativeCommon`, `:desktop:{windows,macos,gtk,qt,swing}`, `:mobile:common`, `:mobile:{android,ios}`, `:web`) | `:extensions:gpu`, `:extensions:canvas2d`, `:extensions:overlay` | `GLCanvasExt`, `VKCanvasExt`, `ScrollCanvas3D`, `Canvas3DOverlayHost`, `ScrollCanvas3DOverlayHost`.<br/>[Canvas3D Extension Guide (`extensions/docs/canvas3d.md`)](../extensions/docs/canvas3d.md) |

---

### Category 2 — Multimedia (VLC HDR), System Media Controls, & Embedded Web Engines

> [!IMPORTANT]
> **Strict Isolation Rules**:
> 1. All VLC video/audio decoding, SMPTE ST 2084 PQ / HLG / Dolby Vision HDR tone-mapping, and FFT audio analysis live strictly inside [`:extensions:media`](../extensions/docs/media.md).
> 2. [`:extensions:mediacontrols`](../extensions/docs/mediacontrols.md) depends **only on `:common`** (zero dependency on `:extensions:media` or `:extensions:overlay`) so any application can hook hardware keyboard media keys and OS transport controls.

| Extension Module | Companion DSL Module | Module Structure | Transitive Dependencies | Key Classes & Dedicated Guide |
| :--- | :--- | :--- | :--- | :--- |
| **`:extensions:media`** | **`:extensions:media:dsl`** | **Project-of-Projects** (`:common`, `:systemCommon`, `:desktop:nativeCommon`, `:desktop:{windows,macos,gtk,qt,swing}`, `:mobile:common`, `:mobile:{android,ios}`, `:web`) | `:extensions:overlay` (`libvlc` in `:desktop:nativeCommon`, `libvlcj` in `:desktop:swing`, native on Mobile/Web) | `VideoPlayer`, `AudioPlayer`, `AudioAnalyzer`, `WebVttSrtParser`, `VideoOverlayHost`, `VideoPlayerOverlayBinder`, `VideoPlayer.attachVideoOverlay(...)`.<br/>[Media Extension Guide (`extensions/docs/media.md`)](../extensions/docs/media.md) |
| **`:extensions:mediacontrols`** | **`:extensions:mediacontrols:dsl`** | **Project-of-Projects** (`:common`, `:systemCommon`, `:desktop:nativeCommon`, `:desktop:{windows,macos,gtk,qt,swing}`, `:mobile:common`, `:mobile:{android,ios}`, `:web`) | `:common` **only** | `MediaControlsSession`, `MediaControlMetadata`, `MediaControlButtonConfig`, `MediaControlsCapabilities`, `MediaControlEvent`, `MediaControlAction` (Windows `ITaskbarList3` taskbar thumbnail preview buttons + SMTC + `WM_APPCOMMAND`/`VK_MEDIA_*` keyboard hooks; macOS `MPRemoteCommandCenter` + `MPNowPlayingInfoCenter` + `NSSystemDefined` media key hooks; Linux MPRIS2 / `XF86Audio*`; Swing `KeyEventDispatcher`; Web `navigator.mediaSession`; lightweight no-op on mobile so Android `Service` setups remain untouched).<br/>[Media Controls Guide (`extensions/docs/mediacontrols.md`)](../extensions/docs/mediacontrols.md) |
| **`:extensions:webview`** | **`:extensions:webview:dsl`** | **Project-of-Projects** (`:common`, `:systemCommon`, `:desktop:common`, `:desktop:nativeCommon`, `:desktop:{windows,macos,gtk,qt,swing}`, `:mobile:common`, `:mobile:{android,ios}`, `:web`) | `:common` (plus `lib/NJS` on Desktop/Swing only) | `WebView`, `WebViewController`, `WebViewJsBridge`. Uses **Chromium (`ChromiumBlinkEngine`) + [`:NJS`](../lib/NJS/README.md)** on Desktop/Swing, and native `android.webkit.WebView`, `WKWebView`, and `<iframe>` on Mobile/Web (never loading `NJS` on Mobile/Web).<br/>[WebView Extension Guide (`extensions/docs/webview.md`)](../extensions/docs/webview.md) |

---

### Category 3 — Self-Rendered Canvas UI, Code Editing, & Multi-Provider AI Agents

| Extension Module | Companion DSL Module | Module Structure | Transitive Dependencies | Key Classes & Dedicated Guide |
| :--- | :--- | :--- | :--- | :--- |
| **`:extensions:alien`** | **`:extensions:alien:dsl`** | **Project-of-Projects** (`:common`, `:systemCommon`, `:web`) | `:extensions:canvas2d` | Self-rendered Bootstrap 5.3 `Canvas2D` UI engine (`AlienFactory`, `AlienContainer`, `AlienFragment`, `AlienScrollFragment`, `UnalienContainer`, `AlienRepaintManager`) on system targets, with zero-overhead native DOM pass-through on `:web`. Precompiles `bootstrap.scss` into [`AlienPrecompiledBootstrapStyleSheet`](../extensions/alien/common/src/commonMain/kotlin/com/nexustools/genericui/ext/alien/AlienPrecompiledBootstrapStyleSheet.kt), implements [`IAlien`](../extensions/alien/common/src/commonMain/kotlin/com/nexustools/genericui/ext/alien/IAlien.kt) (`addStyleSheet` / `removeStyleSheet`, default Bootstrap stylesheet removable via `IAlien.BOOTSTRAP_STYLE_SHEET_ID`, and `insertApplicationStyleSheets: Int = 1` by default or `-1` to disable application stylesheets), and supports 100% of all `ComponentStyle`, `ComponentBootstrap`, and `PrecompiledStyleSheet` CSS properties and transitions.<br/>[Alien Extension Guide (`extensions/docs/alien.md`)](../extensions/docs/alien.md) |
| **`:extensions:codeeditor`** | **`:extensions:codeeditor:dsl`** | **Single KMP Project** | `:extensions:canvas2d` | `IdeCodeEditor` with syntax highlighting, line numbers, code folding, minimap, autocomplete, and diagnostics.<br/>[CodeEditor Extension Guide (`extensions/docs/codeeditor.md`)](../extensions/docs/codeeditor.md) |
| **`:extensions:aichat`** | **`:extensions:aichat:dsl`** | **Single KMP Project** | `:extensions:codeeditor` $\rightarrow$ `:extensions:canvas2d` | `AiChatContainer` (with single-placement `bindHeaderActionsToTitleBar`), `AIChatDrawer`, `AIChatConversationList`, `AIChatMessageView`, `AIChatAgentOrchestrator`.<br/>[AI Chat Extension Guide (`extensions/docs/aichat.md`)](../extensions/docs/aichat.md) |

---

### Category 4 — 3D Scene Graph, 2D/3D Physics, & Declarative Kotlin DSL

| Extension Module | Companion DSL Module | Module Structure | Transitive Dependencies | Key Classes & Dedicated Guide |
| :--- | :--- | :--- | :--- | :--- |
| **`:extensions:threejs`** | **`:extensions:threejs:dsl`** | **Single KMP Project** | `:extensions:canvas3d` $\rightarrow$ `:extensions:gpu` | `ThreeSceneCanvas`, `ThreeGLCanvas`, `ThreeVKCanvas`, `ThreeCamera`, `ThreeMesh`, `ThreeGeometry`, `ThreeMaterial`, `ThreeLight`, `ThreeOrbitControls`.<br/>[Three.js 3D Engine Guide (`extensions/docs/threejs.md`)](../extensions/docs/threejs.md) |
| **`:extensions:box2d`** | **`:extensions:box2d:dsl`** | **Single KMP Project** | `:extensions:canvas2d`, `:extensions:canvas3d` $\rightarrow$ `:extensions:gpu` | `Box2DWorld`, `Box2DBody`, `Box2DCollider`, `Box2DJoint`, `Box2DCanvas`.<br/>[Box2D Physics Guide (`extensions/docs/box2d.md`)](../extensions/docs/box2d.md) |
| **`:extensions:box3d`** | **`:extensions:box3d:dsl`** | **Single KMP Project** | `:extensions:threejs` $\rightarrow$ `:extensions:canvas3d` $\rightarrow$ `:extensions:gpu` | `Box3DWorld`, `Box3DBody`, `Box3DCollider`, `Box3DConstraint`, `Box3DThreeJsBridge`.<br/>[Box3D Physics Guide (`extensions/docs/box3d.md`)](../extensions/docs/box3d.md) |
| **`:extensions:dsl`** | Automatically enables `:extensions:<ext>:dsl` for every enabled UI extension | **Single KMP Project** + 12 Extension `:dsl` Submodules | `:common` | Declarative Kotlin UI, hybrid layout ([`HybridLayoutDsl.kt`](../extensions/dsl/src/commonMain/kotlin/com/nexustools/genericui/ext/dsl/HybridLayoutDsl.kt)), and style/Bootstrap builders ([`DslStyleAndBootstrapBuilders.kt`](../extensions/dsl/src/commonMain/kotlin/com/nexustools/genericui/ext/dsl/DslStyleAndBootstrapBuilders.kt): `style { ... }`, `bootstrap { ... }`, `important { ... }`, strictly isolated to `:extensions:dsl` and never in `:common`) + MVC helpers (`MvcModel`, `MvcView`, `MvcController`).<br/>[Declarative Kotlin DSL Guide (`extensions/docs/dsl.md`)](../extensions/docs/dsl.md) |

---

## 2. Enabling Extensions in `build.gradle.kts`

```kotlin
genericui {
    backends {
        allExceptSwing()
    }
    extensions {
        dsl()             // Enables :extensions:dsl + companion :extensions:<ext>:dsl submodules
        overlay()         // Enables :extensions:overlay + :extensions:overlay:dsl
        media()           // Enables :extensions:media + :extensions:overlay (+ :dsl submodules)
        mediaControls()   // Enables :extensions:mediacontrols + :extensions:mediacontrols:dsl
        imageProcessing() // Enables :extensions:imageprocessing + :extensions:gpu
        webView()         // Enables :extensions:webview + :extensions:webview:dsl
        aiChat()          // Enables :extensions:aichat, :extensions:codeeditor, :extensions:canvas2d
        threeJs()         // Enables :extensions:threejs, :extensions:canvas3d, :extensions:gpu
        box2d()           // Enables :extensions:box2d, :extensions:canvas2d, :extensions:canvas3d
        box3d()           // Enables :extensions:box3d, :extensions:threejs, :extensions:canvas3d
        alien()           // Enables :extensions:alien, :extensions:canvas2d
    }
}
```
