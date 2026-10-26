# GenericUI Image API, GPU, Overlays, Canvases, & Media Guide

> **Root Repository**: [**GenericUI (`../README.md`)**](../README.md) | **Master Docs Index**: [**`docs/README.md`**](./README.md)  
> **Related Guides**: [Extensions Overview (`extensions.md`)](./extensions.md) · [Resources (`resources.md`)](./resources.md) · [Platform Backends (`backends.md`)](./backends.md) · [Overlay Extension (`../extensions/docs/overlay.md`)](../extensions/docs/overlay.md) · [Media Extension (`../extensions/docs/media.md`)](../extensions/docs/media.md) · [Media Controls Extension (`../extensions/docs/mediacontrols.md`)](../extensions/docs/mediacontrols.md)

GenericUI cleanly separates its base multi-format **Image API** (`com.nexustools.genericui.image.*`) and **`AbstractCanvas`** VBlank timing base class in `:common` from its modular extensions for GPU compute ([`:extensions:gpu`](../extensions/docs/gpu.md)), offscreen GPU image processing ([`:extensions:imageprocessing`](../extensions/docs/imageprocessing.md)), interactive overlays ([`:extensions:overlay`](../extensions/docs/overlay.md)), 2D/3D hardware canvases ([`:extensions:canvas2d`](../extensions/docs/canvas2d.md), [`:extensions:canvas3d`](../extensions/docs/canvas3d.md)), media playback ([`:extensions:media`](../extensions/docs/media.md)), and OS/keyboard media controls ([`:extensions:mediacontrols`](../extensions/docs/mediacontrols.md)).

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
| **`LibJpegCodec` / `LibPngCodec` / `LibWebpCodec`** | Built-in multiplatform JPEG, PNG, WebP, and BMP/PPM codecs backing native platform loaders (`ImageIO`, `BitmapFactory`/`ImageDecoder`, `CGImageSource`, `WIC`, `GdkPixbuf`, `QImage`, browser `createImageBitmap`) |

---

## 2. `:extensions:gpu` & Offscreen `:extensions:imageprocessing`

- **`:extensions:gpu`**: Unified GPU device, shader, and offscreen context foundation (`GpuDevice`, `GpuOffscreenContext`, `VKContext` primary with `#version 450` SPIR-V and **MoltenVK** on `:desktop:macos` & `:mobile:ios`, `GLContext` fallback with `#version 300 es` and **Google ANGLE** over Direct3D 11 on `:desktop:windows`).
- **`:extensions:imageprocessing`**: Processes `:common` `ImageBuffer` instances offscreen on the GPU via `GpuImageProcessor`, `GpuImageFilterChain`, and `GpuImageKernel`.

---

## 3. `:extensions:overlay` — Universal Overlay Chrome Architecture

[`:extensions:overlay`](../extensions/docs/overlay.md) (`com.nexustools.genericui.ext.overlay`) is a standalone, 100% generic KMP extension depending **only on `:common`**. It is automatically enabled whenever `media()`, `canvas2d()`, or `canvas3d()` is enabled in `build.gradle.kts`, and can also be enabled directly via `overlay()`:
- **Generic Layout Presets**:
  - **`MobileOverlayPreset`**: Mobile-optimized layout with top gradient scrim, title/subtitle + generic `OverlayBadge` chip slot, trailing action buttons, optional `CenterTransportCluster` (`−skip`, `Play/Pause` with loading spinner, `+skip`), bottom status pill, 20dp `SliderSeekBar` with drag-with-preview, `OverlayModalDialog`, `SubtitleCueView`, and `OverlayCurtain`.
  - **`WebSystemOverlayPreset`**: System + Web layout with top/bottom gradient scrims, metadata header + top status line, `TrackSeekBar` (6px track expanding to 10px on hover with time tooltip + drag-with-preview), bottom `OverlayButtonBar` (Play/Pause, hover-revealed Volume slider, Time label, custom leading/center/trailing slots, Fullscreen), `BELOW_HOST` `OverlayBadgeStrip` slot, anchored `OverlayPopoverPanel`, and immediate hide on `onPointerLeave`.
  - **`OverlayPresetResolver`**: Automatically selects `MobileOverlayPreset` on mobile backends (`Android`, `IOS`) and `WebSystemOverlayPreset` on Web and desktop backends (`Windows`, `MacOS`, `Gtk`, `Qt`, `Swing`, `Web`).
- **Shared Behavior Controllers**: `AutoHideController` (3500ms delay, 200ms fade, generic pin reasons, automatic hiding when `isInPictureInPictureMode == true`) and `OverlayInputRouter` (configurable app-wide keyboard shortcuts on all 8 backends, ignored when a text input has focus).

---

## 4. `:extensions:canvas2d` & `:extensions:canvas3d` (with Overlay Hosts)

All canvases inherit from `:common`'s `AbstractCanvas` (`registerVBlankRender { deltaMs -> ... }`) and support optional overlay attachment via `:extensions:overlay` (`Canvas2DOverlayHost`, `ScrollCanvas2DOverlayHost`, `Canvas3DOverlayHost`, `ScrollCanvas3DOverlayHost`—covering only the viewport area on scroll canvases, excluding scrollbars):

| Extension & Classes | Windows (`:desktop:windows`) | macOS (`:desktop:macos`) | Linux (`:desktop:gtk` / `:qt`) | JVM (`:desktop:swing`) | Android (`:mobile:android`) | iOS (`:mobile:ios`) | Web (`:web`) |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **`:extensions:canvas2d`**<br/>`Canvas2D`, `ScrollCanvas2D`, `CurveEditor` | Direct2D (`ID2D1RenderTarget`) | CoreGraphics (`CGContext`) | Cairo / `QPainter` | `Java2D` (`Graphics2D`) | `android.graphics.Canvas` | CoreGraphics (`CGContext`) | HTML5 `CanvasRenderingContext2D` |
| **`:extensions:canvas3d`**<br/>`GLCanvasExt`, `ScrollGLCanvas` | **Google ANGLE** (`libEGL.dll` + `libGLESv2.dll` $\rightarrow$ D3D11) | AppKit OpenGL / Metal GLES | Native EGL + `libGLESv2.so` / `QOpenGLWidget` | LWJGL 3 `JAWT` + `EGL` / `GLES` (`useLwjglGL`) | `EGLSurfaceView` (`GLES30`/`GLES31`) | `EAGLContext` (`CAEAGLLayer`) | `WebGL2RenderingContext` |
| **`:extensions:canvas3d`**<br/>`VKCanvasExt`, `ScrollVKCanvas` | Native `vulkan-1.dll` (`VK_KHR_win32_surface`) | **MoltenVK** (`libMoltenVK.dylib` $\rightarrow$ Metal) | Native `libvulkan.so.1` / `QVulkanWindow` | LWJGL 3 `JAWT` + `lwjgl-vulkan` (`useVulkan`) | Native `libvulkan.so` (`VK_KHR_android_surface`) | **MoltenVK** (`VK_EXT_metal_surface`) | `WebGPU` (`GPUCanvasContext`) |

---

## 5. `:extensions:media` & `:extensions:mediacontrols`

### A. `:extensions:media` (`VideoPlayer`, `AudioPlayer`, `AudioAnalyzer`, `VideoOverlayHost`)
- **`VideoPlayer` & `VideoOverlayHost`**: Hardware-accelerated video player with uniform codec introspection (`getSupportedCodecs()`, `canPlayCodec(...)`) and `VideoPlayer.attachVideoOverlay(...)` / `VideoPlayer.withOverlay(...)` (`VideoPlayerOverlayBinder`) binding playback state, buffered ranges, tracks, and fullscreen directly to an `OverlayHost`.
- **Desktop `libvlc` (`:extensions:media:desktop:nativeCommon`) & `libvlcj` (`:extensions:media:desktop:swing`)**: Shared Kotlin/Native `libvlc` bindings for `:desktop:windows`, `:desktop:macos`, `:desktop:gtk`, and `:desktop:qt`, and `uk.co.caprica:vlcj` on `:desktop:swing`.

### B. `:extensions:mediacontrols` (`MediaControlsSession`)
- Standalone extension (`com.nexustools.genericui.ext.mediacontrols`) depending **only on `:common`**:
  - **Windows (`:extensions:mediacontrols:desktop:windows`)**: Windows Taskbar thumbnail hover preview buttons (`ITaskbarList3` `ThumbBarAddButtons` / `ThumbBarUpdateButtons`), System Media Transport Controls (SMTC), and `WM_APPCOMMAND` / `VK_MEDIA_*` hardware media key hooks.
  - **macOS (`:extensions:mediacontrols:desktop:macos`)**: `MPRemoteCommandCenter`, `MPNowPlayingInfoCenter` (Control Center, Now Playing bar, Touch Bar), and `NSEvent` `NSSystemDefined` hardware media key hooks.
  - **Linux (`:desktop:gtk`, `:desktop:qt`), Swing (`:desktop:swing`), & Web (`:web`)**: MPRIS2 / `XF86Audio*` keys, AWT `KeyEventDispatcher`, and `navigator.mediaSession`.
  - **Mobile (`:mobile:android`, `:mobile:ios`)**: Lightweight no-op implementation (`supportsSystemTransportControls = false`) so Android applications keep their own `androidMain` foreground `Service` setup untouched.
