# GenericUI First-Party Extensions Guide

`GenericUI` ships with **12 First-Party Extension Modules** (plus **9 companion `:dsl` submodules**) in the [`extensions/` submodule](../extensions/README.md). Each extension is documented in its own dedicated `.md` guide inside [`extensions/docs/`](../extensions/docs/) with complete imperative and declarative DSL examples.

---

## Navigation & Related Documentation

- **Root Repository**: [GenericUI README](../README.md) | [Master Documentation Index](README.md)
- **Extensions Submodule**: [Extensions Submodule README (`extensions/README.md`)](../extensions/README.md)
- **SourceSets & Backends**: [Hierarchical SourceSet Setup (`sourcesets.md`)](sourcesets.md) | [Platform Backends Guide (`backends.md`)](backends.md)
- **Build, IDE, & Testing**: [Gradle Plugin Guide (`gradle-plugin.md`)](gradle-plugin.md) | [IntelliJ IDEA Plugin Guide (`idea-plugin.md`)](idea-plugin.md) | [Testing Guide (`testing.md`)](testing.md)

---

## 1. Categorized Extension Index & Dedicated Documentation

### Category 1 — GPU Compute, Offscreen Image Processing, & Hardware Canvases

| Extension Module | Companion DSL Module | Architecture Type | Transitive Dependencies | Dedicated Documentation & Examples |
| :--- | :--- | :--- | :--- | :--- |
| **`:extensions:gpu`** | *(Core GPU API)* | Project-of-Projects (`:common`, `:desktop`, `:mobile`, `:swing`, `:web`) | `:common` | [GPU Extension Guide (`extensions/docs/gpu.md`)](../extensions/docs/gpu.md) |
| **`:extensions:imageprocessing`** | *(Fluent Pipeline API)* | Single KMP Project | `:extensions:gpu` | [Image Processing Guide (`extensions/docs/imageprocessing.md`)](../extensions/docs/imageprocessing.md) |
| **`:extensions:canvas2d`** | **`:extensions:canvas2d:dsl`** | Project-of-Projects (`Canvas2D`, `ScrollCanvas2D`, `CurveEditorExt`) | `:common` | [Canvas2D Extension Guide (`extensions/docs/canvas2d.md`)](../extensions/docs/canvas2d.md) |
| **`:extensions:canvas3d`** | **`:extensions:canvas3d:dsl`** | Project-of-Projects (`GLCanvasExt`, `VKCanvasExt`, `ScrollCanvas3D`, `ScrollGLCanvas`, `ScrollVKCanvas`) | `:extensions:gpu` | [Canvas3D Extension Guide (`extensions/docs/canvas3d.md`)](../extensions/docs/canvas3d.md) |

---

### Category 2 — Multimedia (VLC HDR) & Embedded Web Engines

> [!IMPORTANT]
> **Strict VLC & Media Encapsulation**: All VLC video/audio decoding, SMPTE ST 2084 PQ / HLG / Dolby Vision HDR tone-mapping, CIE 1931 color matrices, playback clock drift correction, and FFT audio analysis live strictly inside [`:extensions:media`](../extensions/docs/media.md). The `:desktop` submodule contains zero VLC or media code.

| Extension Module | Companion DSL Module | Architecture Type | Transitive Dependencies | Dedicated Documentation & Examples |
| :--- | :--- | :--- | :--- | :--- |
| **`:extensions:media`** | **`:extensions:media:dsl`** | Project-of-Projects (`:common`, `:desktop`, `:mobile`, `:swing`, `:web`) | `:extensions:gpu` | [Media & VLC HDR Extension Guide (`extensions/docs/media.md`)](../extensions/docs/media.md) |
| **`:extensions:webview`** | **`:extensions:webview:dsl`** | Project-of-Projects (Chromium + [`:NJS`](../lib/NJS/README.md) [Native JIT](../lib/NJS/docs/native-jit.md) / [JVM Compiler](../lib/NJS/docs/jvm-compiler.md) on Desktop) | `:common` | [WebView Extension Guide (`extensions/docs/webview.md`)](../extensions/docs/webview.md)<br/>[NJS Engine Docs (`lib/NJS/docs/README.md`)](../lib/NJS/docs/README.md) |

---

### Category 3 — Code Editing & Multi-Provider AI Agents

| Extension Module | Companion DSL Module | Architecture Type | Transitive Dependencies | Dedicated Documentation & Examples |
| :--- | :--- | :--- | :--- | :--- |
| **`:extensions:codeeditor`** | **`:extensions:codeeditor:dsl`** | Single KMP Project | `:extensions:canvas2d` | [CodeEditor Extension Guide (`extensions/docs/codeeditor.md`)](../extensions/docs/codeeditor.md) |
| **`:extensions:aichat`** | **`:extensions:aichat:dsl`** | Single KMP Project | `:extensions:codeeditor` $\to$ `:extensions:canvas2d` | [AI Chat Extension Guide (`extensions/docs/aichat.md`)](../extensions/docs/aichat.md) |

---

### Category 4 — 3D Scene Graph & 2D/3D Rigid-Body Physics

| Extension Module | Companion DSL Module | Architecture Type | Transitive Dependencies | Dedicated Documentation & Examples |
| :--- | :--- | :--- | :--- | :--- |
| **`:extensions:threejs`** | **`:extensions:threejs:dsl`** | Single KMP Project | `:extensions:canvas3d` $\to$ `:extensions:gpu` | [Three.js 3D Engine Guide (`extensions/docs/threejs.md`)](../extensions/docs/threejs.md) |
| **`:extensions:box2d`** | **`:extensions:box2d:dsl`** | Single KMP Project | `:extensions:canvas2d`, `:extensions:canvas3d` $\to$ `:extensions:gpu` | [Box2D Physics Guide (`extensions/docs/box2d.md`)](../extensions/docs/box2d.md) |
| **`:extensions:box3d`** | **`:extensions:box3d:dsl`** | Single KMP Project | `:extensions:threejs` $\to$ `:extensions:canvas3d` $\to$ `:extensions:gpu` | [Box3D Physics Guide (`extensions/docs/box3d.md`)](../extensions/docs/box3d.md) |

---

### Category 5 — Declarative Kotlin UI & Style DSL

| Extension Module | Companion Submodules | Architecture Type | Transitive Dependencies | Dedicated Documentation & Examples |
| :--- | :--- | :--- | :--- | :--- |
| **`:extensions:dsl`** | Automatically enables `:extensions:<ext>:dsl` for every enabled UI extension | Single KMP Project + 9 Extension DSL Submodules | `:common` | [Declarative Kotlin DSL Guide (`extensions/docs/dsl.md`)](../extensions/docs/dsl.md) |

---

## 2. Enabling Extensions in `build.gradle.kts`

Inside `genericui { extensions { ... } }`, enabling any high-level extension automatically resolves its transitive extension dependencies AND wires the correct platform submodules into the [Hierarchical SourceSet Tree](sourcesets.md). When `dsl()` is enabled, the plugin also automatically adds `:extensions:<ext>:dsl` for every enabled extension that provides UI components:

```kotlin
genericui {
    backends {
        allExceptSwing()
    }
    extensions {
        dsl()             // Enables :extensions:dsl + automatic :extensions:<ext>:dsl submodules
        imageProcessing() // Automatically enables :extensions:gpu
        media()           // Automatically enables :extensions:gpu + :extensions:media:dsl
        webView()         // Automatically enables :extensions:webview + :extensions:webview:dsl
        aiChat()          // Automatically enables :extensions:codeeditor, :extensions:canvas2d, and their :dsl modules
        threeJs()         // Automatically enables :extensions:canvas3d, :extensions:gpu, and :extensions:threejs:dsl
        box2d()           // Automatically enables :extensions:canvas2d, :extensions:canvas3d, and :extensions:box2d:dsl
        box3d()           // Automatically enables :extensions:threejs, :extensions:canvas3d, and :extensions:box3d:dsl
    }
}
```
