# GenericUI Image API, GPU, Canvases, & Media Extensions Guide

GenericUI cleanly separates its base multi-format **Image API** (`com.nexustools.genericui.image.*`) and **`AbstractCanvas`** VBlank timing base class in `:common` from its hardware-accelerated GPU, 2D/3D canvas, offscreen image processing, and media playback extensions ([`:extensions:gpu`](../extensions/docs/gpu.md), [`:extensions:imageprocessing`](../extensions/docs/imageprocessing.md), [`:extensions:canvas2d`](../extensions/docs/canvas2d.md), [`:extensions:canvas3d`](../extensions/docs/canvas3d.md), and [`:extensions:media`](../extensions/docs/media.md)).

- **Navigation**: [GenericUI README](../README.md) | [Master Documentation Index](README.md) | [Hierarchical SourceSet Setup](sourcesets.md) | [Platform Backends](backends.md) | [First-Party Extensions](extensions.md) | [Media Extension Guide](../extensions/docs/media.md)

---

## 1. Base `:common` Full Image API (`com.nexustools.genericui.image.*`)

Every GenericUI application has out-of-the-box access to a complete, cross-platform Image API in `:common` without needing any GPU or canvas extension:

| Class / Enum (`1 Class Per File`) | Responsibility |
| :--- | :--- |
| **`PixelFormat`** | `RGBA_8888`, `BGRA_8888`, `RGB_888`, `RGBA_F16`, `RGBA_F32`, `GRAY_8`, `GRAY_ALPHA_88` |
| **`ImageFormat`** | `PNG`, `JPEG`, `WEBP`, `GIF`, `APNG`, `BMP`, `ICO`, `TIFF`, `SVG` |
| **`ImageColorSpace`** | `SRGB`, `LINEAR_SRGB`, `DISPLAY_P3`, `REC_2020_PQ`, `REC_2020_HLG`, `ADOBE_RGB` |
| **`ImageMetadata`** | Dimensions, `ImageFormat`, `ImageColorSpace`, EXIF orientation, DPI, frame count, ICC profile bytes |
| **`ImageBuffer`** | CPU-accessible pixel buffer with width, height, stride, `PixelFormat`, `ImageColorSpace`, and pixel read/write helpers |
| **`ImageFrame`** | Single frame of an animation (`imageBuffer: ImageBuffer`, `durationMs: Int`, `disposalMode`, `blendMode`) |
| **`AnimatedImage`** | Multi-frame animated image container (`frames: List<ImageFrame>`, `loopCount: Int`, `metadata: ImageMetadata`) |
| **`ImageSource`** | Unified input descriptor (`fromBytes`, `fromFile`, `fromResource`, `fromUrl`, `fromBase64`) |
| **`ImageDecodeOptions`** | Target `PixelFormat`, premultiplied alpha toggle, max dimensions / downscale subsample factor, color space conversion |
| **`ImageEncodeOptions`** | Target `ImageFormat`, quality (`0..100`), lossless flag, metadata preservation |
| **`ImageLoader`** | Synchronous and suspending `decode(...)`, `decodeAnimated(...)`, `probeMetadata(...)`, and `encode(...)` |
| **`ImageCodecNativeImpl`** | Backend bridge delegating to native platform image loaders where possible and built-in `libjpeg`/`libpng`/`libwebp` codecs where needed |
| **`LibJpegCodec` / `LibPngCodec` / `LibWebpCodec`** | Direct C-interop / native codecs (`libjpeg`/`libjpeg-turbo`, `libpng`, `libwebp`, plus BMP/PPM) used whenever a platform loader is unavailable or lacks a requested format |

### Native Image Loader vs. Direct Codec Strategy (`ImageCodecNativeImpl`)
`ImageLoader` automatically uses the fastest **native platform image loader** where available, and seamlessly falls back to its own dedicated codecs (`LibJpegCodec`, `LibPngCodec`, `LibWebpCodec`, `BmpPpmCodec`) where needed:
- **Java / `:swing`**: `javax.imageio.ImageIO` & `java.awt.image.BufferedImage` (with `LibWebpCodec` / `LibJpegCodec` fallback for formats not registered in the current JRE's `ImageIO` SPI).
- **Android (`:android`)**: `android.graphics.BitmapFactory` and `android.graphics.ImageDecoder` (API 28+ hardware/animated decode).
- **Web (`:web`)**: Browser-native `createImageBitmap(blob)` + `OffscreenCanvas` / HTML5 `<canvas>` `getImageData` / `toBlob`.
- **macOS & iOS (`:macos`, `:ios`)**: Apple `ImageIO` (`CGImageSourceCreateWithData`, `CGImageDestinationCreateWithData`).
- **Windows, GTK, Qt, & Native Desktop (`:windows`, `:gtk`, `:qt`, `:desktop`)**: Windows Imaging Component (`IWICImagingFactory`), `GdkPixbuf`, and `QImage` / `QImageReader`, backed by direct `LibJpegCodec` (`libjpeg`/`libjpeg-turbo`), `LibPngCodec` (`libpng`), `LibWebpCodec` (`libwebp`), and `BmpPpmCodec` implementations.

---

## 2. `:extensions:gpu` & Offscreen `:extensions:imageprocessing`

### A. `:extensions:gpu` (Project-of-Projects: `:common`, `:desktop`, `:mobile`, `:swing`, `:web`)
`:extensions:gpu` is the foundational GPU extension relied upon by `:extensions:imageprocessing`, `:extensions:canvas3d`, `:extensions:media`, and `:extensions:threejs`.
- **Vulkan Primary (`VKContext`)**: Uses Vulkan 1.2+ (`#version 450` SPIR-V shaders compiled via `shaderc`, with **MoltenVK** on `:macos` and `:ios`) as the primary GPU compute and graphics backend.
- **OpenGL ES 3.0+ Fallback (`GLContext`)**: Automatically falls back to OpenGL ES 3.0/3.1 (`#version 300 es`), using **Google ANGLE (`libEGL.dll`, `libGLESv2.dll`, `d3dcompiler_47.dll` $\rightarrow$ Direct3D 11)** on Windows so shaders never encounter desktop WGL driver bugs.
- **Offscreen GPU Context (`GpuOffscreenContext`)**: Creates headless/offscreen GPU framebuffers and compute pipelines for background image processing and video frame conversion without requiring an on-screen window.

### B. `:extensions:imageprocessing` (Single KMP Project — Relies on `:extensions:gpu` + `:common` Image API)
`:extensions:imageprocessing` manipulates `:common` `ImageBuffer` instances offscreen in memory using `:extensions:gpu`:
- **`GpuImageProcessor`**: Uploads `ImageBuffer` textures to `GpuOffscreenContext`, executes shader/compute pipelines, and reads back the processed `ImageBuffer`.
- **`GpuImageFilterChain`**: Composable multi-pass GPU pipeline (Gaussian blur, unsharp mask, color matrix, LUT, levels/curves, resize/lanczos, morphological operations, blend modes).
- **`GpuImageKernel`**: Custom 2D convolution and compute shader kernels executing directly on the GPU.

---

## 3. `:extensions:canvas2d` & `:extensions:canvas3d` (Built on `AbstractCanvas`)

All canvases inherit from `:common`'s `AbstractCanvas` (viewport sizing, DPI scaling, pointer/key/wheel input, and VSync frame pacing via `registerVBlankRender { deltaMs: Double -> ... }`), while separating 2D and 3D into dedicated Project-of-Projects extensions:

| Extension & Canvas Class | Underlying Dependencies | Windows (`:windows`) | macOS (`:macos`) | Linux (`:gtk` / `:qt`) | JVM (`:swing`) | Android (`:android`) | iOS (`:ios`) | Web (`:web`) |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **`:extensions:canvas2d`**<br/>`Canvas2D`, `CurveEditor` | `:common` (`ImageBuffer`, `AbstractCanvas`) | Direct2D (`ID2D1RenderTarget`) | CoreGraphics (`CGContext`) | Cairo / `QPainter` | `Java2D` (`Graphics2D`) | `android.graphics.Canvas` | CoreGraphics (`CGContext`) | HTML5 `CanvasRenderingContext2D` |
| **`:extensions:canvas3d`**<br/>`GLCanvas` | `:extensions:gpu` + `:common` | **Google ANGLE** (`libEGL.dll` + `libGLESv2.dll` $\rightarrow$ D3D11) | AppKit OpenGL / Metal GLES | Native EGL + `libGLESv2.so` / `QOpenGLWidget` | LWJGL 3 `JAWT` + `EGL` / `GLES` (`useLwjglGL`) | `EGLSurfaceView` (`GLES30`/`GLES31`) | `EAGLContext` (`CAEAGLLayer`) | `WebGL2RenderingContext` |
| **`:extensions:canvas3d`**<br/>`VKCanvas` | `:extensions:gpu` + `:common` | Native `vulkan-1.dll` (`VK_KHR_win32_surface`) | **MoltenVK** (`libMoltenVK.dylib` $\rightarrow$ Metal) | Native `libvulkan.so.1` / `QVulkanWindow` | LWJGL 3 `JAWT` + `lwjgl-vulkan` (`useVulkan`) | Native `libvulkan.so` (`VK_KHR_android_surface`) | **MoltenVK** (`VK_EXT_metal_surface`) | `WebGPU` (`GPUCanvasContext`) |

---

## 4. `:extensions:media` — `VideoPlayer`, `AudioPlayer`, & `:extensions:media:desktop` VLC HDR Engine

`:extensions:media` is a **Project-of-Projects** extension (`:common`, `:desktop`, `:mobile`, `:swing`, `:web`) that **relies on `:extensions:gpu`** for hardware-accelerated video surface presentation and shader-based HDR tone-mapping.

### A. Cross-Platform Media Components (`:extensions:media:common`)
- **`VideoPlayer`**: Hardware-accelerated video player with uniform codec & container introspection (`getSupportedCodecs()`, `canPlayCodec(VideoCodec, VideoContainerFormat)`).
- **`AudioPlayer` & `AudioAnalyzer`**: Low-latency audio playback and real-time FFT spectrum/waveform extraction (feeding **Episode Caster**'s MilkDrop visualizer).
- **`WebVttSrtParser`**: Cross-platform `.vtt` and `.srt` subtitle parser and timed cue synchronizer.

### B. `:extensions:media:desktop:*` — Native `libvlc` (`:desktop:shared` + `:qt`, `:gtk`, `:windows`, `:macos`) & `libvlcj` (`:desktop:swing`)
- **`:extensions:media:desktop:shared` (`LibVlcBindings`, `LibVlcDynamicLoader`, `DesktopLibVlcVideoPlayerImpl`, `DesktopLibVlcAudioPlayerImpl`, `DesktopLibVlcAudioAnalyzerImpl`)**: Shared Kotlin/Native `libvlc` C API bindings (`libvlc.dll`, `libvlc.so.5`, `libvlc.5.dylib`) called by `:extensions:media:desktop:qt`, `:extensions:media:desktop:gtk`, `:extensions:media:desktop:windows`, and `:extensions:media:desktop:macos`.
- **`:extensions:media:desktop:swing` (`SwingVideoPlayerImpl`, `SwingAudioPlayerImpl`, `SwingAudioAnalyzerImpl`)**: Uses `libvlcj` (`uk.co.caprica:vlcj`) `CallbackMediaPlayerComponent`, `AudioPlayerComponent`, and `AudioCallbackAdapter` for Java/Swing media playback.

---

## 5. `:extensions:webview` — Chromium (`ChromiumBlinkEngine`) + `NJS` Native JIT Engine on Desktop & Native Platform WebView on Mobile/Web

On Desktop targets (`:desktop:windows`, `:desktop:macos`, `:desktop:gtk`, `:desktop:qt`, and `:desktop:swing`), [`:extensions:webview`](../extensions/docs/webview.md) pairs Chromium's Blink HTML/CSS rendering engine with the [**`NJS` (Nexus JavaScript & TypeScript Engine)**](../lib/NJS/README.md):
- **Native Desktop (`:windows`, `:macos`, `:gtk`, `:qt`)**: Executes JavaScript and TypeScript via [`:NJS:native`'s AsmJit/SLJIT Native JIT Engine (`lib/NJS/docs/native-jit.md`)](../lib/NJS/docs/native-jit.md) and [`:NJS:runtime` (`lib/NJS/docs/runtime.md`)](../lib/NJS/docs/runtime.md).
- **JVM Swing (`:desktop:swing`)**: Compiles JavaScript and TypeScript directly to JVM bytecode via [`:NJS:jvm` (`lib/NJS/docs/jvm-compiler.md`)](../lib/NJS/docs/jvm-compiler.md).
- **Mobile & Web (`:mobile:android`, `:mobile:ios`, `:web`)**: Uses the native platform implementation (`android.webkit.WebView`, `WKWebView`, HTML5 `<iframe>`) and never loads `NJS`.
- **Full NJS Documentation**: See [NJS Master Documentation Index (`lib/NJS/docs/README.md`)](../lib/NJS/docs/README.md) and [CLI & WebView Host Embedding (`lib/NJS/docs/cli-and-embedding.md`)](../lib/NJS/docs/cli-and-embedding.md).

`WebView` is provided as a dedicated **Project-of-Projects** extension (`:extensions:webview:common`, `:desktop`, `:mobile`, `:swing`, `:web`):
- **Desktop (`:desktop` / `:windows`, `:macos`, `:gtk`, `:qt`, and `:swing`)**: Uses the **Chromium** project (`ChromiumBlinkEngine`) for HTML/CSS layout & rendering coupled with **`NJS`** (`D:\IdeaProjects\NJS`, symlinked at `D:\IdeaProjects\GenericUI\lib\NJS` for live development and to become a git submodule later) for the JavaScript runtime (`mingwX64`, `linuxX64`, `linuxArm64`, `macosArm64`, `macosX64`, and `jvm`) powered by its **AsmJit / SLJIT Native JIT Engine** (`com.nexustools.njs.jit.*`).
- **Mobile (`:android`, `:ios`) & `:web` (STRICT RULE — DO NOT USE `NJS` ON iOS, ANDROID, OR WEB)**: Uses `android.webkit.WebView` on `:android`, `WKWebView` on `:ios`, and `<iframe>` on `:web`, and **strictly NEVER uses `NJS`**.
