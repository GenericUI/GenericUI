# GenericUI Image API, GPU, Overlays, Canvases, & Media Guide

> **Root Repository**: [**GenericUI (`../README.md`)**](../README.md) | **Master Docs Index**: [**`docs/README.md`**](./README.md)  
> **Related Guides**: [Extensions Overview (`extensions.md`)](./extensions.md) · [Resources (`resources.md`)](./resources.md) · [Platform Backends (`backends.md`)](./backends.md) · [Overlay Extension (`../extensions/docs/overlay.md`)](../extensions/docs/overlay.md) · [Media Extension (`../extensions/docs/media.md`)](../extensions/docs/media.md) · [Media Controls Extension (`../extensions/docs/mediacontrols.md`)](../extensions/docs/mediacontrols.md)

GenericUI cleanly separates its base multi-format **Image API** (`com.nexustools.genericui.image.*`) and **`AbstractCanvas`** VBlank timing base class in `:common` from its modular extensions for GPU compute ([`:extensions:graphics:gpu`](../extensions/docs/gpu.md)), offscreen GPU image processing ([`:extensions:graphics:imageprocessing`](../extensions/docs/imageprocessing.md)), interactive overlays ([`:extensions:ui:overlay`](../extensions/docs/overlay.md)), 2D/3D hardware canvases ([`:extensions:graphics:canvas2d`](../extensions/docs/canvas2d.md), [`:extensions:graphics:canvas3d`](../extensions/docs/canvas3d.md)), media playback ([`:extensions:media:media`](../extensions/docs/media.md)), and OS/keyboard media controls ([`:extensions:media:mediacontrols`](../extensions/docs/mediacontrols.md)).

---

## 1. Base `:common` Full Image API (`com.nexustools.genericui.image.*`)

Every GenericUI application has out-of-the-box access to a complete, cross-platform Image API in `:common`:

| Class / Enum (`1 Class Per File`) | Responsibility |
| :--- | :--- |
| **`PixelFormat`** | `RGBA_8888`, `BGRA_8888`, `RGB_888`, `RGBA_F16`, `RGBA_F32`, `GRAY_8`, `GRAY_ALPHA_88` |
| **`ImageFormat`** | `PNG`, `JPEG`, `WEBP`, `GIF`, `APNG`, `BMP`, `ICO`, `TIFF`, `SVG` |
| **`ImageColorSpace`** | `SRGB`, `LINEAR_SRGB`, `DISPLAY_P3`, `REC_2020_PQ`, `REC_2020_HLG`, `ADOBE_RGB` |
| **`ImageMetadata`** | Dimensions, `ImageFormat`, `ImageColorSpace`, EXIF orientation, DPI, frame count, ICC profile bytes |
| **`ImageBuffer`** | CPU-accessible pixel buffer with width, height, stride, `PixelFormat`, `ImageColorSpace`, and pixel read/write helpers |
| **`AnimatedImage` & `ImageFrame`** | Multi-frame animated image container and per-frame timing/disposal descriptors |
| **`ImageLoader`** | Decode, encode, probe metadata, load from `Resource.ResourceID`, and cache remote URLs via `ImageCacheManager` |
| **`AppleIconTileComposer`** | Pure-Kotlin Chrome-style rounded white squircle compositor (`composeImageBuffer`, `composeRgba`) used at runtime on macOS when no explicit Apple icon is configured |
| **`LibJpegCodec` / `LibPngCodec` / `LibWebpCodec`** | Built-in multiplatform JPEG, PNG, WebP, and BMP/PPM codecs backing native platform loaders (`ImageIO`, `BitmapFactory`/`ImageDecoder`, Apple `ImageIO` + CoreGraphics SVG rasterization, `WIC`, `GdkPixbuf`, `QImage`, browser `createImageBitmap`) |

---

## 2. `:extensions:graphics:gpu` & Offscreen `:extensions:graphics:imageprocessing`

- **`:extensions:graphics:gpu`**: Unified GPU device, shader, and offscreen context foundation (`GpuDevice`, `GpuOffscreenContext`, `VKContext` primary with `#version 450` SPIR-V and **MoltenVK** on `:desktop` `macosMain` & `:mobile` `iosMain`, `GLContext` fallback with `#version 300 es` and **Google ANGLE** over Direct3D 11 on `:desktop` `mingwMain`).
- **`:extensions:graphics:imageprocessing`**: Processes `:common` `ImageBuffer` instances offscreen on the GPU via `GpuImageProcessor`, `GpuImageFilterChain`, and `GpuImageKernel`.

---

## 3. `:extensions:ui:overlay` — Universal Overlay Chrome Architecture

[`:extensions:ui:overlay`](../extensions/docs/overlay.md) (`com.nexustools.genericui.ext.overlay`) is a standalone, 100% generic KMP extension depending **only on `:common`**. It is automatically enabled whenever `media()`, `canvas2d()`, or `canvas3d()` is enabled in `build.gradle.kts`, and can also be enabled directly via `overlay()`:
- **Generic Layout Presets**:
  - **`MobileOverlayPreset`**: Mobile-optimized layout with top gradient scrim, title/subtitle + generic `OverlayBadge` chip slot, trailing action buttons, optional `CenterTransportCluster` (`−skip`, `Play/Pause` with loading spinner, `+skip`), bottom status pill, 20dp `SliderSeekBar` with drag-with-preview, `OverlayModalDialog`, `SubtitleCueView`, and `OverlayCurtain`.
  - **`WebSystemOverlayPreset`**: System + Web layout with top/bottom gradient scrims, metadata header + top status line, `TrackSeekBar` (6px track expanding to 10px on hover with time tooltip + drag-with-preview), bottom `OverlayButtonBar` (Play/Pause, hover-revealed Volume slider, Time label, custom leading/center/trailing slots, Fullscreen), `BELOW_HOST` `OverlayBadgeStrip` slot, anchored `OverlayPopoverPanel`, and immediate hide on `onPointerLeave`.
  - **`OverlayPresetResolver`**: Automatically selects `MobileOverlayPreset` on mobile backends (`Android`, `IOS`, `IPadOS`) and `WebSystemOverlayPreset` on Web and desktop backends (`Windows`, `MacOS`, `Gtk`, `Qt`, `Swing`, `Web`).
- **Shared Behavior Controllers**: `AutoHideController` (3500ms delay, 200ms fade, generic pin reasons, automatic hiding when `isInPictureInPictureMode == true`) and `OverlayInputRouter` (configurable app-wide keyboard shortcuts on all 9 backends, ignored when a text input has focus).

---

## 4. `:extensions:graphics:canvas2d` & `:extensions:graphics:canvas3d` (with Overlay Hosts & Fail-Fast Registries)

All canvases inherit from `:common`'s `AbstractCanvas` (`registerVBlankRender { deltaMs -> ... }`), attach optional overlays via `:extensions:ui:overlay` (`Canvas2DOverlayHost`, `ScrollCanvas2DOverlayHost`, `Canvas3DOverlayHost`, `ScrollCanvas3DOverlayHost`), and fail fast (`IllegalStateException` / `UnsupportedOperationException`) via [`Canvas2DExtension`](../extensions/graphics/canvas2d/src/commonMain/kotlin/com/nexustools/genericui/ext/canvas2d/Canvas2DExtension.kt) and [`Canvas3DRegistry`](../extensions/graphics/canvas3d/src/commonMain/kotlin/com/nexustools/genericui/ext/canvas3d/Canvas3DRegistry.kt) if no platform backend provider is registered:

| Extension & Classes | Windows (`mingwMain`) | macOS (`macosMain`) | Linux (`gtk` / `qt`) | JVM Swing (`jvmMain`) | Android (`androidMain`) | iOS & iPadOS (`iosMain`) | Web (`web`) |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **`:extensions:graphics:canvas2d`**<br/>`Canvas2D`, `ScrollCanvas2D`, `CurveEditor` | Direct2D (`ID2D1RenderTarget`) | CoreGraphics (`CGContext`) | Cairo / `QPainter` | `Java2D` (`Graphics2D`) | `android.graphics.Canvas` | CoreGraphics (`CGContext`) | HTML5 `CanvasRenderingContext2D` |
| **`:extensions:graphics:canvas3d`**<br/>`Canvas3D` (`GLContext`), `ScrollCanvas3D` | **Google ANGLE** (`libEGL.dll` + `libGLESv2.dll` $\rightarrow$ D3D11) | AppKit OpenGL / Metal GLES | Native EGL + `libGLESv2.so` / `QOpenGLWidget` | LWJGL 3 `JAWT` + `EGL` / `GLES` (`useLwjglGL`) | `EGLSurfaceView` (`GLES30`/`GLES31`) | `EAGLContext` (`CAEAGLLayer`) | `WebGL2RenderingContext` |
| **`:extensions:graphics:canvas3d`**<br/>`Canvas3D` (`VKContext`), `ScrollCanvas3D` | Native `vulkan-1.dll` (`VK_KHR_win32_surface`) | **MoltenVK** (`libMoltenVK.dylib` $\rightarrow$ Metal) | Native `libvulkan.so.1` / `QVulkanWindow` | LWJGL 3 `JAWT` + `lwjgl-vulkan` (`useVulkan`) | Native `libvulkan.so` (`VK_KHR_android_surface`) | **MoltenVK** (`VK_EXT_metal_surface`) | `WebGPU` (`GPUCanvasContext`) |

---

## 5. `:extensions:media:media` & `:extensions:media:mediacontrols`

### A. `:extensions:media:media` (`VideoPlayer`, `AudioPlayer`, `AudioAnalyzer`, `VideoOverlayHost`)
- **`VideoPlayer` & `VideoOverlayHost`**: Hardware-accelerated video player with uniform codec introspection (`getSupportedCodecs()`, `canPlayCodec(...)`), fail-fast provider validation in [`MediaExtension`](../extensions/media/media/src/commonMain/kotlin/com/nexustools/genericui/ext/media/MediaExtension.kt), and `VideoPlayer.attachVideoOverlay(...)` (`VideoPlayerOverlayBinder`) binding playback state, buffered ranges, tracks, and fullscreen directly to an `OverlayHost`.
- **Desktop `libvlc` & `libvlcj`**: Consolidated inside `:extensions:media:media`'s `nativeDesktopMain` (`libvlc` C interop for Windows, macOS, GTK, Qt) and `jvmMain` (`uk.co.caprica:vlcj`), plus native media engines on Android, iOS/iPadOS, and Web.

### B. `:extensions:media:mediacontrols` (`MediaControlsSession` & Default Real Factory)
- Standalone extension (`com.nexustools.genericui.ext.mediacontrols`) depending **only on `:common`**:
  - [`MediaControlsRegistry`](../extensions/media/mediacontrols/src/commonMain/kotlin/com/nexustools/genericui/ext/mediacontrols/MediaControlsRegistry.kt) initializes its factory property automatically via `internal expect fun defaultPlatformMediaControlsFactory()` ([`DefaultPlatformMediaControlsFactory.kt`](../extensions/media/mediacontrols/src/commonMain/kotlin/com/nexustools/genericui/ext/mediacontrols/DefaultPlatformMediaControlsFactory.kt)), with real platform `actual` implementations across **all targets** (Windows `ITaskbarList3` + SMTC + `WM_APPCOMMAND`/`VK_MEDIA_*`, macOS `MPRemoteCommandCenter` + `MPNowPlayingInfoCenter`, Linux MPRIS2 / `XF86Audio*`, JVM Swing keyboard/system controls, Android `MediaSession`, iOS & iPadOS `MPRemoteCommandCenter` / `MPNowPlayingInfoCenter`, and Web `navigator.mediaSession`).
  - Zero silent no-op fallback: clearing `MediaControlsRegistry.factory = null` causes `MediaControlsSession()` to fail fast with an `IllegalStateException`.
