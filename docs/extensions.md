# GenericUI First-Party Extensions Guide

> **Root Repository**: [**GenericUI (`../README.md`)**](../README.md) | **Master Docs Index**: [**`docs/README.md`**](./README.md)  
> **Related Guides**: [Gamepad & UI Navigation (`gamepad.md`)](./gamepad.md) · [2D & 3D Game Engines (`game-engines.md`)](./game-engines.md) · [Extensions Submodule (`../extensions/README.md`)](../extensions/README.md) · [Hybrid Layouts & Helpers (`layouts-and-helpers.md`)](./layouts-and-helpers.md) · [Gradle Plugin (`gradle-plugin.md`)](./gradle-plugin.md) · [SourceSets (`sourcesets.md`)](./sourcesets.md) · [Platform Backends (`backends.md`)](./backends.md) · [Media & Canvases (`media-and-canvases.md`)](./media-and-canvases.md)

GenericUI includes **21 First-Party Extension Modules** across **7 categories**—plus **18 companion `:dsl` modules** and `:extensions:ui:dsl` (`= 40 extension Gradle projects`). Every extension is a **Single Consolidated KMP Project** (no nested Project-of-Projects subtrees).

When enabled in `genericui { extensions { ... } }`, the GenericUI Gradle plugin automatically resolves transitive extension dependencies, maps Maven/composite coordinates (`com.nexustools.genericui.extensions.<category>:<name>:0.1.0-SNAPSHOT`), wires platform source sets, executes compile-time codegen SPI contributors (`SchemaExtensionContributor`, `ServiceWorkerExtensionContributor`, `PipExtensionContributor`), and installs extension providers inside the generated `GenericUiAppDescriptor`—while **failing fast** (`IllegalStateException` / `UnsupportedOperationException`) whenever a required runtime capability or platform provider is absent rather than falling back to silent stubs.

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
| **`:extensions:physics:box2d`** | **`:extensions:physics:box2d:dsl`** | `box2d()` | `:extensions:graphics:canvas2d`, `canvas3d` | `World2D`, `Body2D`, `BodyDef2D`, `BodyType2D`, shapes, `Joint2D`, `Box2DDebugDrawer`. [Box2D Guide (`../extensions/docs/box2d.md`)](../extensions/docs/box2d.md) |
| **`:extensions:physics:box3d`** | **`:extensions:physics:box3d:dsl`** | `box3d()` | `:extensions:graphics:canvas3d`, `threejs` | `World3D`, `Body3D`, `Collider3D`, `Constraint3D`, `Box3DDebugRenderer`. [Box3D Guide (`../extensions/docs/box3d.md`)](../extensions/docs/box3d.md) |

---

### Category 3 — Game & Input (`:extensions:game:*`)

| Extension Module | DSL Module | DSL Call | Transitive Dependencies | Key Classes & Dedicated Guide |
| :--- | :--- | :--- | :--- | :--- |
| **`:extensions:game:gamepad`** | **`:extensions:game:gamepad:dsl`** | `gamepad()` | `:common` **only** | Unified Switch Pro/Joy-Con, DualSense/DualShock 4, & Xbox controller input (`GamepadManager`, `ControllerHardwareProfile`, `GamepadLayoutMapper`), `SixAxisSensorFusion` + bias calibration, Switch HD Rumble / Home LED, DualSense adaptive triggers / touchpad / lightbar, Xbox impulse triggers, and zero-config `GamepadUiNavigator`. [Gamepad Guide (`gamepad.md`)](./gamepad.md) |
| **`:extensions:game:engine`** | **`:extensions:game:engine:dsl`** | `engine()` | `:common`, `:extensions:game:gamepad` | `GameWorld`, `GameScene`, `SceneEntity`, `EntityBehavior`, dedicated `PlatformGameLogicThread`, `FixedStepAccumulator`, `VBlankAnimationDriver` + `AnimationScheduler` + 31 curves/splines/springs/waves, `Skeleton2D` + `HumanoidSkeleton2DFactory` + `TwoBoneIkSolver2D`, and pure-UI `UiStageHost`. [Game Engines Guide (`game-engines.md`)](./game-engines.md) |
| **`:extensions:game:engine2d`** | **`:extensions:game:engine2d:dsl`** | `engine2d()` | `:extensions:game:engine`, `:extensions:graphics:canvas2d`, `imageprocessing`, `:extensions:physics:box2d` | `Game2DCanvasHost`, `Camera2DModule`, `Sprite2DModule`, `SpriteSheetAnimationModule`, `SvgCharacter2DModule`, `Tilemap2DModule`, `ParticleEmitter2DModule`, `PhysicsWorld2DSubsystem`. [Game Engines Guide (`game-engines.md`)](./game-engines.md) |
| **`:extensions:game:engine3d`** | **`:extensions:game:engine3d:dsl`** | `engine3d()` | `:extensions:game:engine`, `:extensions:graphics:canvas3d`, `threejs`, `:extensions:physics:box3d` | `Game3DCanvasHost`, `SceneGraph3DSubsystem`, `MeshRenderer3DModule`, `OrbitCamera3DModule`, `FollowCamera3DModule`, `PhysicsWorld3DSubsystem`, `CharacterController3DModule`, `ProceduralHumanoidRig3D`, `SkeletalModel3DModule`. [Game Engines Guide (`game-engines.md`)](./game-engines.md) |

---

### Category 4 — Media & System Controls (`:extensions:media:*`)

> [!IMPORTANT]
> **Strict Isolation & Out-of-the-Box Provider Rules**:
> 1. All VLC video/audio decoding, transparent codec queries (`MediaExtension.isVideoCodecSupported(...)`, `isAudioCodecSupported(...)`), `VlcConfiguration` schema settings (`MediaExtension.getPlayerSettingsSchema()`), and FFT audio analysis live strictly inside [`:extensions:media:media`](../extensions/docs/media.md).
> 2. `:extensions:media:mediacontrols` depends **only on `:common`** and initializes real OS media transport controls across all targets out of the box via [`DefaultPlatformMediaControlsFactory`](../extensions/media/mediacontrols/src/commonMain/kotlin/com/nexustools/genericui/ext/mediacontrols/DefaultPlatformMediaControlsFactory.kt) while [`MediaControlsRegistry`](../extensions/media/mediacontrols/src/commonMain/kotlin/com/nexustools/genericui/ext/mediacontrols/MediaControlsRegistry.kt) fails fast if no factory is installed.

| Extension Module | DSL Module | DSL Call | Transitive Dependencies | Key Classes & Dedicated Guide |
| :--- | :--- | :--- | :--- | :--- |
| **`:extensions:media:media`** | **`:extensions:media:media:dsl`** | `media()` | `:extensions:ui:overlay`, `:extensions:data:schema` (`libvlc` on native desktop, `libvlcj` on JVM Swing, native on Mobile/Web) | `VideoPlayer`, `VideoFragment : OverlayStageProvider`, `AudioPlayer`, `AudioAnalyzer`, **`SeekBar : StyledSlider`** (zero `SeekBarNativeImpl`, appends `.seek-bar-preload` `StyledComponent`), **`ExpandingVolumeSlider : Composite`** (mute/volume `StyledButton` + `StyledSlider` + 5-SVG `MediaVolumeIcons` in `com.nexustools.genericui.component.media.res.R`), `media/style.scss` + raw `theme.scss` template (compiled solely in consumer app into `GenericUiGeneratedThemeStyleSheet` / `gui/genericui-theme.css`), `MediaCodecSupportProvider`, `MediaExtension`, `VlcConfiguration` (`@SchemaData`), `WebVttSrtParser`, `VideoOverlayHost`, `VideoPlayerOverlayBinder`, `YoutubeMediaOverlayHelper`. [Media Guide (`../extensions/docs/media.md`)](../extensions/docs/media.md) |
| **`:extensions:media:mediacontrols`** | **`:extensions:media:mediacontrols:dsl`** | `mediaControls()` | `:common` **only** | `MediaControlsSession`, `MediaControlMetadata`, `MediaControlsRegistry`, `DefaultPlatformMediaControlsFactory`. [Media Controls Guide (`../extensions/docs/mediacontrols.md`)](../extensions/docs/mediacontrols.md) |

---

### Category 5 — Data & Meta-Schema (`:extensions:data:*`)

| Extension Module | DSL Module | DSL Call | Transitive Dependencies | Key Classes & Dedicated Guide |
| :--- | :--- | :--- | :--- | :--- |
| **`:extensions:data:schema`** | **`:extensions:data:schema:dsl`** | `schema()` | `:common` **only** | `ObjectSchema`, 12 typed field specs + groups (`SchemaAsyncGroupSpec`), `visibleWhen`/`enabledWhen`, `SchemaPreset`, `SchemaLayoutMode` (`STACKED`, `RESPONSIVE_GRID`, `COMPACT_INLINE`, `TABBED`), `SchemaForm`, `SchemaModal`, `@SchemaData` + `@SchemaField(..., legacyKeys = ...)` + `@SchemaVisibleWhen` codegen (`SchemaExtensionContributor`), `SchemaCustomFieldRenderer` / `SchemaRegistry`, `SchemaJsonCodec`, `SchemaPreferenceCodec`. [Schema Guide (`../extensions/docs/schema.md`)](../extensions/docs/schema.md) |

---

### Category 6 — System & Background Services (`:extensions:system:*`)

| Extension Module | DSL Module | DSL Call | Transitive Dependencies | Key Classes & Dedicated Guide |
| :--- | :--- | :--- | :--- | :--- |
| **`:extensions:system:serviceworker`** | **`:extensions:system:serviceworker:dsl`** | `serviceWorker()` / `serviceWorker { ... }` | `:common` **only** | `ServiceWorker`, `@MainServiceWorker`, `ServiceWorkerOptions`, `ServiceWorkerExtension` (**honestly `isSupported = false` on iOS & iPadOS with zero simulation**), `@ServiceWorkerMessage` codegen + `ServiceWorkerCodecRegistry`, `ServiceWorkerChannel`, desktop secondary `serviceworker` executable + tray/autostart/`openWindowOnLaunch`, Android `GeneratedAndroidServiceWorkerService` (`ServiceCompat.startForeground` + `"Stop"` button + notification actions + conditional boot receiver), and Web `./serviceworker.js` + `${appBaseName}.sw.js`/`${appBaseName}.sw.wasm`. [Service Worker Guide (`../extensions/docs/serviceworker.md`)](../extensions/docs/serviceworker.md) |

---

### Category 7 — UI, Overlays, PiP, WebView, Editor, & AI (`:extensions:ui:*`)

| Extension Module | DSL Module | DSL Call | Transitive Dependencies | Key Classes & Dedicated Guide |
| :--- | :--- | :--- | :--- | :--- |
| **`:extensions:ui:dsl`** | Enables companion `:dsl` modules for enabled extensions | `dsl()` | `:common` | Declarative Kotlin UI, hybrid layout (`HybridLayoutDsl.kt`), style/Bootstrap builders (`DslStyleAndBootstrapBuilders.kt`), hardware `backdropFilter` on `StyledButton` + `StyledContainer`, and MVC helpers (`MvcModel`, `MvcView`, `MvcController`). [DSL Guide (`../extensions/docs/dsl.md`)](../extensions/docs/dsl.md) |
| **`:extensions:ui:overlay`** | **`:extensions:ui:overlay:dsl`** | `overlay()` | `:common` **only** | Pure background-free `LayeredContainer` overlay chrome + `overlay/style.scss` & raw `theme.scss` template (packaged uncompiled in library metadata and compiled solely in consumer app against `genericui { theme { ... } }` `$accent`): `OverlayHost`, `OverlayHostBox`, `AbstractOverlay`, `FadingOverlay` (`.hidden` CSS `@keyframes` fade via `SystemCssAnimationController` / `Animator`), `Overlay`, `OverlayIcons` (9 pure-Bezier `.svg`s under `com.nexustools.genericui.ext.overlay.res.R`), `OverlayControlButton`, `OverlayFullscreenButton`, `OverlayControlButtons`, `YoutubePlayerOverlay`, `HybridOverlayDifferenceController`, `AutoHideController`, `OverlayInputRouter`. [Overlay Guide (`../extensions/docs/overlay.md`)](../extensions/docs/overlay.md) |
| **`:extensions:ui:pip`** | **`:extensions:ui:pip:dsl`** | `pip()` | `:extensions:ui:overlay`, `:common` | `@PiPCompatible`, `FragmentPipHelper`, `FragmentPipRegistry`, `PipRegistry`, `PipControlOverlay` (`pip/style.scss` `.hidden` fade + `OverlayInputRouter`), `ComponentReparentSlot`, `AndroidPipBackend` (+ `PipAndroidActivityExtensionContributor`), `IosPipBackend` (`AVPictureInPictureController`), `DesktopFloatingPipBackend` / `SwingFloatingPipBackend`, and `WebPipBackend`. [PiP Guide (`../extensions/docs/pip.md`)](../extensions/docs/pip.md) |
| **`:extensions:ui:alien`** | **`:extensions:ui:alien:dsl`** | `alien()` | `:extensions:graphics:canvas2d`, `imageprocessing` | Self-rendered Bootstrap 5.3 `Canvas2D` UI engine (`AlienFactory`, `AlienContainer`, `AlienFragment`, `AlienScrollFragment`, `AlienFragmentHostFragment`, `UnalienContainer`, [`IAlien`](../extensions/ui/alien/src/commonMain/kotlin/com/nexustools/genericui/ext/alien/IAlien.kt) w/ `com.nexustools.genericui.ext.alien.res.R.style.bootstrap`, [`AlienPrecompiledBootstrapStyleSheet`](../extensions/ui/alien/src/commonMain/kotlin/com/nexustools/genericui/ext/alien/AlienPrecompiledBootstrapStyleSheet.kt)) with zero-overhead native DOM pass-through on `:web`. [Alien Guide (`../extensions/docs/alien.md`)](../extensions/docs/alien.md) |
| **`:extensions:ui:webview`** | **`:extensions:ui:webview:dsl`** | `webView()` | `:common` (+ `lib/NJS` on Desktop/Swing only) | `WebView`, `WebViewController`, `WebViewJsBridge`. Multi-process **Chromium + [`:NJS`](../lib/NJS/README.md)** on Desktop/Swing, and native `android.webkit.WebView` / `WKWebView` / `<iframe>` on Mobile/Web. [WebView Guide (`../extensions/docs/webview.md`)](../extensions/docs/webview.md) |
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
        pip()             // Enables :extensions:ui:pip -> overlay (+ :dsl)
        schema()          // Enables :extensions:data:schema (+ :dsl) + @SchemaData codegen
        serviceWorker {   // Enables :extensions:system:serviceworker (+ :dsl) + @MainServiceWorker codegen
            autostart = true
            openWindowOnLaunch = true
        }
        media()           // Enables :extensions:media:media -> overlay + schema (+ :dsl)
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

On native desktop targets (`macosArm64`, `linuxX64`, `linuxArm64`, `mingwX64`), each enabled extension (`libgenericui-ext-<ext>.{dylib,so}` / `genericui-ext-<ext>.dll`), companion `:dsl` module (`libgenericui-ext-<ext>-dsl.*` / `genericui-ext-dsl.*`), and `libnjs` / `njs.dll` (only when `webView()` or `threeJs()` is enabled) is linked as an independent shared library and staged only when enabled. On Swing, Android, iOS, iPadOS, and Web, extensions are linked into the application package automatically (and desktop/web Service Workers emit separate secondary executables / `.sw.wasm` bundles when `@MainServiceWorker` is present).
