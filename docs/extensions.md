# GenericUI First-Party Extensions Guide

> **Root Repository**: [**GenericUI (`../README.md`)**](../README.md) | **Master Docs Index**: [**`docs/README.md`**](./README.md)  
> **Related Guides**: [Gamepad & UI Navigation (`gamepad.md`)](./gamepad.md) · [2D & 3D Game Engines (`game-engines.md`)](./game-engines.md) · [Extensions Submodule (`../extensions/README.md`)](../extensions/README.md) · [Hybrid Layouts & Helpers (`layouts-and-helpers.md`)](./layouts-and-helpers.md) · [Gradle Plugin (`gradle-plugin.md`)](./gradle-plugin.md) · [SourceSets (`sourcesets.md`)](./sourcesets.md) · [Platform Backends (`backends.md`)](./backends.md) · [Media & Canvases (`media-and-canvases.md`)](./media-and-canvases.md)

GenericUI includes **19 First-Party Extension Modules** across 5 categories—plus **16 companion `:dsl` modules** and `:extensions:ui:dsl` (= 35 extension Gradle projects in the consolidated 45-project workspace). Every extension is a **single consolidated KMP project** (no nested Project-of-Projects subtrees).

When enabled in `genericui { extensions { ... } }`, the GenericUI Gradle plugin automatically resolves transitive extension dependencies, maps Maven/composite coordinates (`com.nexustools.genericui.extensions.<category>:<name>:0.1.0-SNAPSHOT`), wires platform source sets, and installs extension providers inside the generated `GenericUiAppDescriptor`—while **failing fast** (`IllegalStateException` / `UnsupportedOperationException`) whenever a required platform provider is absent rather than falling back to silent no-op stubs.

---

## 1. Categorized Extension Index & Documentation

### Category 1 — Graphics (`:extensions:graphics:*`)

| Extension Module | DSL Module | DSL Call | Transitive Dependencies | Key Classes & Dedicated Guide |
| :--- | :--- | :--- | :--- | :--- |
| **`:extensions:graphics:gpu`** | *(Core GPU API)* | `gpu()` | `:common` | `GpuDevice`, `GpuOffscreenContext`, `VKContext` (Vulkan 1.2+ SPIR-V primary, MoltenVK on Apple), `GLContext` (OpenGL ES 3.0+ fallback, Google ANGLE on Windows). [GPU Guide (`../extensions/docs/gpu.md`)](../extensions/docs/gpu.md) |
| **`:extensions:graphics:imageprocessing`** | *(Fluent Pipeline)* | `imageProcessing()` | `:extensions:graphics:gpu` | `GpuImageProcessor`, `GpuImageFilterChain`, `GpuImageKernel`, `GpuHistogramAnalyzer` operating on `:common`'s `ImageBuffer`. [Image Processing Guide (`../extensions/docs/imageprocessing.md`)](../extensions/docs/imageprocessing.md) |
| **`:extensions:graphics:canvas2d`** | **`:extensions:graphics:canvas2d:dsl`** | `canvas2d()` | `:extensions:graphics:gpu`, `imageprocessing`, `:extensions:ui:overlay` | `Canvas2D`, `ScrollCanvas2D`, `Canvas2DContext`, `CurveEditor`, `Canvas2DOverlayHost`, `ScrollCanvas2DOverlayHost`, fail-fast `Canvas2DExtension`. [Canvas2D Guide (`../extensions/docs/canvas2d.md`)](../extensions/docs/canvas2d.md) |
| **`:extensions:graphics:canvas3d`** | **`:extensions:graphics:canvas3d:dsl`** | `canvas3d()` | `:extensions:graphics:gpu`, `canvas2d`, `:extensions:ui:overlay` | `Canvas3D`, `Canvas3DConfig`, fail-fast `Canvas3DRegistry`, `ScrollCanvas3D`, `Canvas3DOverlayHost`. [Canvas3D Guide (`../extensions/docs/canvas3d.md`)](../extensions/docs/canvas3d.md) |
| **`:extensions:graphics:threejs`** | **`:extensions:graphics:threejs:dsl`** | `threeJs()` | `:extensions:graphics:gpu`, `canvas3d` | `Scene`, `PerspectiveCamera`, `WebGLRenderer`, `Mesh`, `BufferGeometry`, `MeshStandardMaterial`, `DirectionalLight`, `OrbitControls`. [Three.js Guide (`../extensions/docs/threejs.md`)](../extensions/docs/threejs.md) |

---

### Category 2 — Physics (`:extensions:physics:*`)

| Extension Module | DSL Module | DSL Call | Transitive Dependencies | Key Classes & Dedicated Guide |
| :--- | :--- | :--- | :--- | :--- |
| **`:extensions:physics:box2d`** | **`:extensions:physics:box2d:dsl`** | `box2d()` | `:extensions:graphics:canvas2d`, `canvas3d` | `World2D`, `Body2D`, `BodyDef2D`, `BodyType2D`, `Collider2D` / shapes, `Joint2D`, `Box2DDebugDrawer`. [Box2D Guide (`../extensions/docs/box2d.md`)](../extensions/docs/box2d.md) |
| **`:extensions:physics:box3d`** | **`:extensions:physics:box3d:dsl`** | `box3d()` | `:extensions:graphics:canvas3d`, `threejs` | `World3D`, `Body3D`, `Collider3D`, `Constraint3D`, `Box3DDebugRenderer`. [Box3D Guide (`../extensions/docs/box3d.md`)](../extensions/docs/box3d.md) |

---

### Category 3 — Game & Input (`:extensions:game:*`)

| Extension Module | DSL Module | DSL Call | Transitive Dependencies | Key Classes & Dedicated Guide |
| :--- | :--- | :--- | :--- | :--- |
| **`:extensions:game:gamepad`** | **`:extensions:game:gamepad:dsl`** | `gamepad()` | `:common` **only** | Unified Switch Pro/Joy-Con, DualSense/DualShock 4, & Xbox controller input (`GamepadManager`, `ControllerHardwareProfile`, `GamepadLayoutMapper`), `SixAxisSensorFusion` + bias calibration, Switch HD Rumble / Home LED, DualSense adaptive triggers / touchpad / lightbar, Xbox impulse triggers, and zero-config customizable `GamepadUiNavigator` (`GamepadUiNavigationStrategy`). [Gamepad Guide (`gamepad.md`)](./gamepad.md) |
| **`:extensions:game:engine`** | **`:extensions:game:engine:dsl`** | `engine()` | `:common`, `:extensions:game:gamepad` | `GameWorld`, `GameScene`, `SceneEntity`, `EntityBehavior`, dedicated `PlatformGameLogicThread`, `FixedStepAccumulator`, `VBlankAnimationDriver` + `AnimationScheduler` + 31 curves/splines/springs/waves, `Skeleton2D` + `HumanoidSkeleton2DFactory` + `TwoBoneIkSolver2D`, Xbox-mapped `InputActionMap`, and pure-UI `UiStageHost`. [Game Engines Guide (`game-engines.md`)](./game-engines.md) |
| **`:extensions:game:engine2d`** | **`:extensions:game:engine2d:dsl`** | `engine2d()` | `:extensions:game:engine`, `:extensions:graphics:canvas2d`, `imageprocessing`, `:extensions:physics:box2d` | `Game2DCanvasHost`, `Camera2DModule`, `Sprite2DModule`, `SpriteSheetAnimationModule`, `SvgCharacter2DModule`, `Tilemap2DModule`, `ParticleEmitter2DModule`, `PhysicsWorld2DSubsystem`. [Game Engines Guide (`game-engines.md`)](./game-engines.md) |
| **`:extensions:game:engine3d`** | **`:extensions:game:engine3d:dsl`** | `engine3d()` | `:extensions:game:engine`, `:extensions:graphics:canvas3d`, `threejs`, `:extensions:physics:box3d` | `Game3DCanvasHost`, `SceneGraph3DSubsystem`, `MeshRenderer3DModule`, `OrbitCamera3DModule`, `FollowCamera3DModule`, `PhysicsWorld3DSubsystem`, `CharacterController3DModule`, `ProceduralHumanoidRig3D`, `SkeletalModel3DModule`. [Game Engines Guide (`game-engines.md`)](./game-engines.md) |

---

### Category 4 — Media & System Controls (`:extensions:media:*`)

> [!IMPORTANT]
> **Strict Isolation & Out-of-the-Box Provider Rules**:
> 1. All VLC video/audio decoding, HDR tone-mapping, and FFT audio analysis live strictly inside [`:extensions:media:media`](../extensions/docs/media.md).
> 2. `:extensions:media:mediacontrols` depends **only on `:common`** and initializes real OS media transport controls across all targets out of the box via [`DefaultPlatformMediaControlsFactory`](../extensions/media/mediacontrols/src/commonMain/kotlin/com/nexustools/genericui/ext/mediacontrols/DefaultPlatformMediaControlsFactory.kt) while [`MediaControlsRegistry`](../extensions/media/mediacontrols/src/commonMain/kotlin/com/nexustools/genericui/ext/mediacontrols/MediaControlsRegistry.kt) fails fast if no factory is installed.

| Extension Module | DSL Module | DSL Call | Transitive Dependencies | Key Classes & Dedicated Guide |
| :--- | :--- | :--- | :--- | :--- |
| **`:extensions:media:media`** | **`:extensions:media:media:dsl`** | `media()` | `:extensions:ui:overlay` (`libvlc` on native desktop, `libvlcj` on JVM Swing, native on Mobile/Web) | `VideoPlayer`, `AudioPlayer`, `AudioAnalyzer`, `WebVttSrtParser`, `VideoOverlayHost`, `VideoPlayerOverlayBinder`, `MediaExtension`. [Media Guide (`../extensions/docs/media.md`)](../extensions/docs/media.md) |
| **`:extensions:media:mediacontrols`** | **`:extensions:media:mediacontrols:dsl`** | `mediaControls()` | `:common` **only** | `MediaControlsSession`, `MediaControlMetadata`, `MediaControlsRegistry`, `DefaultPlatformMediaControlsFactory` (Windows `ITaskbarList3` + SMTC + `WM_APPCOMMAND`, macOS `MPRemoteCommandCenter` + `MPNowPlayingInfoCenter`, Linux MPRIS2 / `XF86Audio*`, Swing, Android `MediaSession`, iOS/iPadOS `MPNowPlayingInfoCenter` / `MPRemoteCommandCenter`, Web `navigator.mediaSession`). [Media Controls Guide (`../extensions/docs/mediacontrols.md`)](../extensions/docs/mediacontrols.md) |

---

### Category 5 — UI, Overlays, WebView, Editor, & AI (`:extensions:ui:*`)

| Extension Module | DSL Module | DSL Call | Transitive Dependencies | Key Classes & Dedicated Guide |
| :--- | :--- | :--- | :--- | :--- |
| **`:extensions:ui:dsl`** | Enables companion `:dsl` modules for enabled extensions | `dsl()` | `:common` | Declarative Kotlin UI, hybrid layout ([`HybridLayoutDsl.kt`](../extensions/ui/dsl/src/commonMain/kotlin/com/nexustools/genericui/ext/dsl/HybridLayoutDsl.kt)), style/Bootstrap builders ([`DslStyleAndBootstrapBuilders.kt`](../extensions/ui/dsl/src/commonMain/kotlin/com/nexustools/genericui/ext/dsl/DslStyleAndBootstrapBuilders.kt)), and MVC helpers (`MvcModel`, `MvcView`, `MvcController`). [DSL Guide (`../extensions/docs/dsl.md`)](../extensions/docs/dsl.md) |
| **`:extensions:ui:overlay`** | **`:extensions:ui:overlay:dsl`** | `overlay()` | `:common` **only** | 100% generic overlay architecture: `OverlayHost`, `OverlayHostBox`, `AbstractOverlay`, `PresetOverlay`, `MobileOverlayPreset`, `WebSystemOverlayPreset`, `OverlayPresetResolver`, `AutoHideController`, `OverlayInputRouter`, `TrackSeekBar`, `SliderSeekBar`. [Overlay Guide (`../extensions/docs/overlay.md`)](../extensions/docs/overlay.md) |
| **`:extensions:ui:alien`** | **`:extensions:ui:alien:dsl`** | `alien()` | `:extensions:graphics:canvas2d`, `imageprocessing` | Self-rendered Bootstrap 5.3 `Canvas2D` UI engine (`AlienFactory`, `AlienContainer`, `AlienFragment`, `AlienScrollFragment`, `AlienFragmentHostFragment`, `UnalienContainer`, [`IAlien`](../extensions/ui/alien/src/commonMain/kotlin/com/nexustools/genericui/ext/alien/IAlien.kt), [`AlienPrecompiledBootstrapStyleSheet`](../extensions/ui/alien/src/commonMain/kotlin/com/nexustools/genericui/ext/alien/AlienPrecompiledBootstrapStyleSheet.kt)) with zero-overhead native DOM pass-through on `:web`. [Alien Guide (`../extensions/docs/alien.md`)](../extensions/docs/alien.md) |
| **`:extensions:ui:webview`** | **`:extensions:ui:webview:dsl`** | `webView()` | `:common` (+ `lib/NJS` on Desktop/Swing only) | `WebView`, `WebViewController`, `WebViewJsBridge`. Multi-process **Chromium + [`:NJS`](../lib/NJS/README.md)** on Desktop/Swing, and native `android.webkit.WebView` / `WKWebView` / `<iframe>` on Mobile/Web (never loading `NJS` on Mobile/Web). [WebView Guide (`../extensions/docs/webview.md`)](../extensions/docs/webview.md) |
| **`:extensions:ui:codeeditor`** | **`:extensions:ui:codeeditor:dsl`** | `codeEditor()` | `:extensions:graphics:canvas2d`, `imageprocessing`, `:extensions:ui:overlay` | `IdeCodeEditor` with syntax highlighting, line numbers, code folding, minimap, autocomplete, and diagnostics. [CodeEditor Guide (`../extensions/docs/codeeditor.md`)](../extensions/docs/codeeditor.md) |
| **`:extensions:ui:aichat`** | **`:extensions:ui:aichat:dsl`** | `aiChat()` | `:extensions:ui:codeeditor` ($\rightarrow$ `canvas2d`) | `AiChatContainer` (with single-placement `bindHeaderActionsToTitleBar`), `AIChatDrawer`, `AIChatConversationList`, `AIChatMessageView`, `AIChatAgentOrchestrator`. [AI Chat Guide (`../extensions/docs/aichat.md`)](../extensions/docs/aichat.md) |

---

## 2. Enabling Extensions in `build.gradle.kts`

```kotlin
genericui {
    backends {
        allExceptSwing()
    }
    extensions {
        dsl()             // Enables :extensions:ui:dsl + companion :dsl modules for enabled extensions
        overlay()         // Enables :extensions:ui:overlay + :dsl
        media()           // Enables :extensions:media:media + :extensions:ui:overlay (+ :dsl)
        mediaControls()   // Enables :extensions:media:mediacontrols (+ :dsl)
        imageProcessing() // Enables :extensions:graphics:imageprocessing + :extensions:graphics:gpu
        webView()         // Enables :extensions:ui:webview (+ :dsl)
        aiChat()          // Enables :extensions:ui:aichat -> codeeditor -> canvas2d
        threeJs()         // Enables :extensions:graphics:threejs -> canvas3d -> gpu
        box2d()           // Enables :extensions:physics:box2d -> canvas2d + canvas3d
        box3d()           // Enables :extensions:physics:box3d -> threejs + canvas3d
        gamepad()         // Enables :extensions:game:gamepad (+ :dsl)
        engine()          // Enables :extensions:game:engine -> gamepad (+ :dsl)
        engine2d()        // Enables :extensions:game:engine2d -> engine + canvas2d + box2d (+ :dsl)
        engine3d()        // Enables :extensions:game:engine3d -> engine + threejs + box3d (+ :dsl)
        alien()           // Enables :extensions:ui:alien -> canvas2d
    }
}
```
