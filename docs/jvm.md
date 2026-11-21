# GenericUI `:jvm` Shared Java Foundation Module Guide

> **Root Repository**: [**GenericUI (`../README.md`)**](../README.md) | **Master Docs Index**: [**`docs/README.md`**](./README.md)  
> **Related Guides**: [Core Architecture (`architecture.md`)](./architecture.md) · [Platform Backends (`backends.md`)](./backends.md) · [Hierarchical SourceSets (`sourcesets.md`)](./sourcesets.md) · [Shared Apple Module (`apple.md`)](./apple.md) · [Utilities & Async I/O (`utils.md`)](./utils.md) · [Testing Guide (`testing.md`)](./testing.md)

The **`:jvm`** module (`com.nexustools.genericui:jvm`) consolidates all toolkit-agnostic Java / JDK platform implementations shared across **JVM Swing (`BackendTarget.Swing` in `:desktop`)** and **Android (`BackendTarget.Android` in `:mobile`)**, eliminating duplicated file I/O, HTTP, WebSocket, socket, SQLite, preference, resource, JNI native-library loading, dispatcher, image, and pixel-tinting code between desktop Swing and mobile Android.

---

## 1. Gradle Targets & SourceSet Architecture

Configured in [`jvm/build.gradle.kts`](../jvm/build.gradle.kts):

- **Targets**:
  - `jvm` (`JvmTarget.JVM_11`) — consumed by `:desktop` (`jvmMain`) and JVM extensions (`:extensions:graphics:gpu`, `:extensions:graphics:canvas2d`, `:extensions:graphics:canvas3d`, `:extensions:media:media`, `:extensions:media:mediacontrols`, `:extensions:ui:webview`, `:extensions:system:serviceworker`, `:extensions:game:gamepad`, `:extensions:game:engine`).
  - `android` (`namespace = "com.nexustools.genericui.jvm"`, `JvmTarget.JVM_11`, `withHostTest {}`) — consumed by `:mobile` (`androidMain`) and Android extension source sets.
- **Dependencies**: `api(project(":system"))` (transitively exposing `:common` and Blink-style layout/CSS engines) + `kotlinx-coroutines-core`.
- **Shared Source Directories**:
  - [`jvm/src/jvmSharedMain/kotlin`](../jvm/src/jvmSharedMain/kotlin/com/nexustools/genericui/jvm) — wired into both `jvmMain` and `androidMain` without duplicating files.
  - [`jvm/src/jvmSharedTest/kotlin`](../jvm/src/jvmSharedTest/kotlin/com/nexustools/genericui/jvm) — wired into both `jvmTest` and `androidHostTest` (`kotlin.testJunit`).

---

## 2. Shared Classes & Subsystems (`com.nexustools.genericui.jvm`)

| Domain | Classes / Files | Consumer Implementations & Responsibility |
| :--- | :--- | :--- |
| **Coroutine, UI Thread, & Test Dispatching** | [`JvmIoExecutor`](../jvm/src/jvmSharedMain/kotlin/com/nexustools/genericui/jvm/JvmIoExecutor.kt)<br/>[`JvmUiThreadDispatcherBase`](../jvm/src/jvmSharedMain/kotlin/com/nexustools/genericui/jvm/JvmUiThreadDispatcherBase.kt) | `JvmIoExecutor.runOnIo` / `runOnIoSuspend` automatically dispatch blocking I/O onto `Dispatchers.IO` whenever invoked on a toolkit UI main thread or inside a `ContinuationInterceptor`, while running inline inside headless unit tests. `JvmUiThreadDispatcherBase` extends `DefaultUiThreadDispatcherNativeImpl` with an executor-backed Logic thread (`Executors.newSingleThreadExecutor`) and backs both `SwingUiThreadDispatcherNativeImpl` (AWT `EventQueue` + synchronous `CountDownLatch` `runOnUiBlocking`) and `AndroidUiThreadDispatcherNativeImpl` (Main `Looper` + `Handler`), automatically executing UI closures synchronously inline inside headless unit/Robolectric runs when no live UI loop is active. |
| **JNI Native Library Loader** | [`JniLibraryLoader`](../jvm/src/jvmSharedMain/kotlin/com/nexustools/genericui/jvm/JniLibraryLoader.kt) | Unified JNI native loading (`JniLibraryLoader.load(libraryName, classLoader)`). Tries `System.loadLibrary(libraryName)` first (picking up Android APK `lib/<abi>/` payloads aligned to 16KB ELF/ZIP page boundaries and desktop `java.library.path`), and on Swing desktop extracts `META-INF/native/<os>-<arch>/<mappedName>` (`windows-x86_64`, `macos-aarch64`, `linux-x86_64`, etc. bundled by `GenericUiPackageSwingJarTask`) into `<java.io.tmpdir>/genericui-jni/<os>-<arch>/<sha256-16>/<mappedName>` before calling `System.load(...)`. |
| **Async File System & Streams** | [`JvmFileSystemBase`](../jvm/src/jvmSharedMain/kotlin/com/nexustools/genericui/jvm/JvmFileSystemBase.kt)<br/>[`JvmStreamFileReadableNativeImpl`](../jvm/src/jvmSharedMain/kotlin/com/nexustools/genericui/jvm/JvmStreamFileReadableNativeImpl.kt)<br/>[`JvmStreamFileWritableNativeImpl`](../jvm/src/jvmSharedMain/kotlin/com/nexustools/genericui/jvm/JvmStreamFileWritableNativeImpl.kt) | Non-blocking file reading, writing, appending, stat, mkdir, and `Readable` / `Writable` stream adapters shared across Swing NIO/filesystem (`SwingFileWritableNativeImpl`) and Android sandbox storage (`AndroidFileSystemNativeImpl`, `AndroidFileReadableNativeImpl`, `AndroidFileWritableNativeImpl`). |
| **Database** | [`JvmDatabaseBase`](../jvm/src/jvmSharedMain/kotlin/com/nexustools/genericui/jvm/JvmDatabaseBase.kt) | Implements GenericUI's zero-parsing structured AST execution (`DatabaseTableSchema`, `DatabaseQuery`, `DatabaseFilter` via `DatabaseQuerySqlTranslator`) and database path resolution (`resolveDatabaseFilePath`) inherited by `SwingDatabaseNativeImpl` (`sqlite-jdbc`) and `AndroidDatabaseNativeImpl` (`androidx.sqlite:sqlite-ktx`). |
| **Resources & Image Sources** | [`JvmResourceResolverBase`](../jvm/src/jvmSharedMain/kotlin/com/nexustools/genericui/jvm/JvmResourceResolverBase.kt)<br/>[`JvmImageCodecBase`](../jvm/src/jvmSharedMain/kotlin/com/nexustools/genericui/jvm/JvmImageCodecBase.kt)<br/>[`JvmImageSourceResolver`](../jvm/src/jvmSharedMain/kotlin/com/nexustools/genericui/jvm/JvmImageSourceResolver.kt) | Shared `ResourceResolverNativeImpl` base (`SwingResourceResolverImpl` via JAR classpath, `AndroidResourceResolverImpl` via Android assets/`R.*`), image decode/encode adapter base (`AndroidBitmapImageCodecImpl`, Swing `ImageIO`), and URI/classpath `ImageSource` loading (`SwingSystemTrayImpl`, Android/Swing drawables). |
| **Sockets, HTTP/S, TLS, & WebSockets** | [`JvmTcpSocketNativeImpl`](../jvm/src/jvmSharedMain/kotlin/com/nexustools/genericui/jvm/JvmTcpSocketNativeImpl.kt)<br/>[`JvmUdpSocketNativeImpl`](../jvm/src/jvmSharedMain/kotlin/com/nexustools/genericui/jvm/JvmUdpSocketNativeImpl.kt)<br/>[`JvmHttpUrlConnectionClient`](../jvm/src/jvmSharedMain/kotlin/com/nexustools/genericui/jvm/JvmHttpUrlConnectionClient.kt)<br/>[`JvmHttpConnectionNativeImpl`](../jvm/src/jvmSharedMain/kotlin/com/nexustools/genericui/jvm/JvmHttpConnectionNativeImpl.kt)<br/>[`JvmHttpRequestNativeImpl`](../jvm/src/jvmSharedMain/kotlin/com/nexustools/genericui/jvm/JvmHttpRequestNativeImpl.kt)<br/>[`JvmHttpResponseNativeImpl`](../jvm/src/jvmSharedMain/kotlin/com/nexustools/genericui/jvm/JvmHttpResponseNativeImpl.kt)<br/>[`JvmRfc6455WebSocketNativeImpl`](../jvm/src/jvmSharedMain/kotlin/com/nexustools/genericui/jvm/JvmRfc6455WebSocketNativeImpl.kt)<br/>[`JvmPermissiveX509TrustManager`](../jvm/src/jvmSharedMain/kotlin/com/nexustools/genericui/jvm/JvmPermissiveX509TrustManager.kt) | Full async NIO/socket TCP (`Socket` / `SSLSocket`), UDP (`DatagramSocket` / `MulticastSocket` wrapped on Android by `AndroidUdpSocketNativeImpl` with `WifiManager.MulticastLock`), streaming HTTP (`HttpURLConnection`), RFC 6455 WebSocket (`JvmRfc6455WebSocketNativeImpl` supporting Socket.IO v4 / Engine.IO v4), and optional development TLS trust manager reused directly by `:desktop` Swing and `:mobile` Android. |
| **Native Control Sprite Tinting** | [`JvmControlTintPixelUtil`](../jvm/src/jvmSharedMain/kotlin/com/nexustools/genericui/jvm/JvmControlTintPixelUtil.kt) | Pure-Kotlin ARGB luminance-preserving pixel tinting, rounded-corner SDF clipping (`computeRoundedRectSdf`, `resolveCornerRadiusPx`), synthetic bevel fallback, and state shading (`hovered`, `pressed`, `disabled`, `outline`, `isLink`) used by both `SwingControlTintRenderUtil` and `AndroidControlTintRenderUtil`. |

---

## 3. Consumer Differences (`Swing` vs. `Android`)

- **Hardware 60+Hz `backdrop-filter` (`StyledButton` & `StyledContainer`, Excluding `TextBubble`)**:
  - **Swing (`desktop/jvmMain`)**: [`SwingHardwareBackdropBlurRenderer`](../desktop/src/jvmMain/kotlin/com/nexustools/genericui/swing/SwingHardwareBackdropBlurRenderer.kt) performs GPU Java2D `VolatileImage` bilinear ping-pong downsampling (`1/2 -> 1/4 -> 1/8`), falling back on headless JVMs or CSS color ops to `BufferedImage` + `FastBackdropBlurKernel`.
  - **Android (`mobile/androidMain`)**: [`AndroidHardwareBackdropBlurController`](../mobile/src/androidMain/kotlin/com/nexustools/genericui/android/AndroidHardwareBackdropBlurController.kt) walks to `android.R.id.content`/outermost root, renders pre-order preceding views (including live `TextureView` frames via `SurfaceTextureListener`), and runs API 31+ `RenderNode` / `RenderEffect.createBlurEffect` or API 24–30/Robolectric `Bitmap` + `FastBackdropBlurKernel`.
- **`ServiceWorker` Hosting**:
  - **Swing**: `GeneratedSwingServiceWorkerMain` compiled into the same fat JAR, guarded by `FileChannel.tryLock()`, driving `DesktopServiceWorkerHost` + `SwingSystemTrayImpl` headless loop wait.
  - **Android**: `GeneratedAndroidServiceWorkerService` + `AndroidServiceWorkerController` (`ServiceCompat.startForeground` ongoing notification with default `"Stop"` + `extendNotificationActions`/`replaceNotificationActions`, `AndroidServiceWorkerBinder` IPC, conditional `BOOT_COMPLETED` receiver) wired through `AndroidExtensionRuntimeBridge`.

---

## 4. Consumer Variant Scope & Testing

- **Application Variant Scope (`AppTarget.JVM_COMMON`, rank `2`)**:
  - Consumer apps can target both Swing and Android simultaneously via `@MainAppFragment(targets = [AppTarget.JVM_COMMON])`, `genericui { jvmCommon { ... } }`, or `genericui { sourceSets { jvmCommon { ... } } }` (`src/jvmCommon/kotlin` / `jvmCommonMain` when needed).
  - See [Hierarchical SourceSets (`sourcesets.md`)](./sourcesets.md) and [Gradle Plugin (`gradle-plugin.md`)](./gradle-plugin.md).
- **Consolidated `:jvm` Test Suite**:
  - Covered by shared JUnit test suites under `jvm/src/jvmSharedTest/kotlin/com/nexustools/genericui/jvm/` (`JvmControlTintPixelUtilTest`, `JvmFileSystemAndStreamsTest`, `JvmHttpUrlConnectionClientTest`, `JvmImageSourceAndCodecTest`, `JvmRfc6455WebSocketNativeImplTest`, `JvmTcpAndUdpSocketTest`, and `JvmUiThreadDispatcherAndDatabaseResourceTest`).
  - Run with `./gradlew :jvm:jvmTest` (plus Android host tests via `:jvm:testAndroidHostTest`).
