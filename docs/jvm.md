# GenericUI `:jvm` Shared Java Foundation Module Guide

> **Root Repository**: [**GenericUI (`../README.md`)**](../README.md) | **Master Docs Index**: [**`docs/README.md`**](./README.md)  
> **Related Guides**: [Core Architecture (`architecture.md`)](./architecture.md) · [Platform Backends (`backends.md`)](./backends.md) · [Hierarchical SourceSets (`sourcesets.md`)](./sourcesets.md) · [Shared Apple Module (`apple.md`)](./apple.md) · [Utilities & Async I/O (`utils.md`)](./utils.md)

The **`:jvm`** module (`com.nexustools.genericui:jvm`) consolidates all toolkit-agnostic Java / JDK platform implementations shared across **JVM Swing (`BackendTarget.Swing` in `:desktop`)** and **Android (`BackendTarget.Android` in `:mobile`)**, eliminating duplicated file I/O, HTTP, WebSocket, socket, SQLite, preference, resource, dispatcher, image, and pixel-tinting code between desktop Swing and mobile Android.

---

## 1. Gradle Targets & SourceSet Architecture

Configured in [`jvm/build.gradle.kts`](../jvm/build.gradle.kts):

- **Targets**:
  - `jvm` (`JvmTarget.JVM_11`) — consumed by `:desktop` (`jvmMain`) and JVM extensions (`:extensions:graphics:gpu`, `:extensions:graphics:canvas2d`, `:extensions:graphics:canvas3d`, `:extensions:media:media`, `:extensions:media:mediacontrols`, `:extensions:ui:webview`, `:extensions:game:gamepad`, `:extensions:game:engine`).
  - `android` (`namespace = "com.nexustools.genericui.jvm"`, `JvmTarget.JVM_11`, `withHostTest {}`) — consumed by `:mobile` (`androidMain`) and Android extension source sets.
- **Dependencies**: `api(project(":system"))` (transitively exposing `:common` and Blink-style layout/CSS engines) + `kotlinx-coroutines-core`.
- **Shared Source Directories**:
  - [`jvm/src/jvmSharedMain/kotlin`](../jvm/src/jvmSharedMain/kotlin/com/nexustools/genericui/jvm) — wired into both `jvmMain` and `androidMain` without duplicating files.
  - [`jvm/src/jvmSharedTest/kotlin`](../jvm/src/jvmSharedTest/kotlin/com/nexustools/genericui/jvm) — wired into both `jvmTest` and `androidHostTest` (`kotlin.testJunit`).

---

## 2. Shared Classes & Subsystems (`com.nexustools.genericui.jvm`)

| Domain | Classes / Files | Consumer Implementations & Responsibility |
| :--- | :--- | :--- |
| **Coroutine & UI Dispatching** | [`JvmIoExecutor`](../jvm/src/jvmSharedMain/kotlin/com/nexustools/genericui/jvm/JvmIoExecutor.kt)<br/>[`JvmUiThreadDispatcherBase`](../jvm/src/jvmSharedMain/kotlin/com/nexustools/genericui/jvm/JvmUiThreadDispatcherBase.kt) | `JvmIoExecutor.runOnIo` / `runOnIoSuspend` automatically dispatch blocking I/O onto `Dispatchers.IO` whenever invoked on a toolkit UI main thread or inside a `ContinuationInterceptor`, while running inline inside headless unit tests. `JvmUiThreadDispatcherBase` backs both `SwingUiThreadDispatcherNativeImpl` (AWT `EventQueue`) and `AndroidUiThreadDispatcherNativeImpl` (Main `Looper` + `Handler`), enforcing GenericUI's UI Thread vs. Logic Thread separation. |
| **Async File System & Streams** | [`JvmFileSystemBase`](../jvm/src/jvmSharedMain/kotlin/com/nexustools/genericui/jvm/JvmFileSystemBase.kt)<br/>[`JvmStreamFileReadableNativeImpl`](../jvm/src/jvmSharedMain/kotlin/com/nexustools/genericui/jvm/JvmStreamFileReadableNativeImpl.kt)<br/>[`JvmStreamFileWritableNativeImpl`](../jvm/src/jvmSharedMain/kotlin/com/nexustools/genericui/jvm/JvmStreamFileWritableNativeImpl.kt) | Non-blocking file reading, writing, appending, stat, mkdir, and `Readable` / `Writable` stream adapters shared across Swing NIO/filesystem (`SwingFileWritableNativeImpl`) and Android sandbox storage (`AndroidFileSystemNativeImpl`, `AndroidFileReadableNativeImpl`, `AndroidFileWritableNativeImpl`). |
| **Database & Preferences** | [`JvmDatabaseBase`](../jvm/src/jvmSharedMain/kotlin/com/nexustools/genericui/jvm/JvmDatabaseBase.kt)<br/>[`JvmPreferenceStoreBase`](../jvm/src/jvmSharedMain/kotlin/com/nexustools/genericui/jvm/JvmPreferenceStoreBase.kt) | `JvmDatabaseBase` implements GenericUI's zero-parsing structured AST execution (`DatabaseTableSchema`, `DatabaseQuery`, `DatabaseFilter` via `DatabaseQuerySqlTranslator`) and database path resolution (`resolveDatabaseFilePath`) inherited by `SwingDatabaseNativeImpl` (`sqlite-jdbc`) and `AndroidDatabaseNativeImpl` (`androidx.sqlite:sqlite-ktx`). `JvmPreferenceStoreBase` powers cross-platform preference encoding, change listeners, and external sync across `AndroidPreferenceStoreImpl` and Swing. |
| **Resources & Image Sources** | [`JvmResourceResolverBase`](../jvm/src/jvmSharedMain/kotlin/com/nexustools/genericui/jvm/JvmResourceResolverBase.kt)<br/>[`JvmImageCodecBase`](../jvm/src/jvmSharedMain/kotlin/com/nexustools/genericui/jvm/JvmImageCodecBase.kt)<br/>[`JvmImageSourceResolver`](../jvm/src/jvmSharedMain/kotlin/com/nexustools/genericui/jvm/JvmImageSourceResolver.kt) | Shared `ResourceResolverNativeImpl` base (`SwingResourceResolverImpl`, `AndroidResourceResolverImpl`), image decode/encode adapter base (`AndroidBitmapImageCodecImpl`, Swing `ImageIO`), and URI/classpath `ImageSource` loading (`SwingSystemTrayImpl`, Android/Swing drawables). |
| **Sockets, HTTP/S, TLS, & WebSockets** | [`JvmTcpSocketNativeImpl`](../jvm/src/jvmSharedMain/kotlin/com/nexustools/genericui/jvm/JvmTcpSocketNativeImpl.kt)<br/>[`JvmUdpSocketNativeImpl`](../jvm/src/jvmSharedMain/kotlin/com/nexustools/genericui/jvm/JvmUdpSocketNativeImpl.kt)<br/>[`JvmHttpUrlConnectionClient`](../jvm/src/jvmSharedMain/kotlin/com/nexustools/genericui/jvm/JvmHttpUrlConnectionClient.kt)<br/>[`JvmHttpConnectionNativeImpl`](../jvm/src/jvmSharedMain/kotlin/com/nexustools/genericui/jvm/JvmHttpConnectionNativeImpl.kt)<br/>[`JvmHttpRequestNativeImpl`](../jvm/src/jvmSharedMain/kotlin/com/nexustools/genericui/jvm/JvmHttpRequestNativeImpl.kt)<br/>[`JvmHttpResponseNativeImpl`](../jvm/src/jvmSharedMain/kotlin/com/nexustools/genericui/jvm/JvmHttpResponseNativeImpl.kt)<br/>[`JvmRfc6455WebSocketNativeImpl`](../jvm/src/jvmSharedMain/kotlin/com/nexustools/genericui/jvm/JvmRfc6455WebSocketNativeImpl.kt)<br/>[`JvmPermissiveX509TrustManager`](../jvm/src/jvmSharedMain/kotlin/com/nexustools/genericui/jvm/JvmPermissiveX509TrustManager.kt) | Full async NIO/socket TCP (`Socket` / `SSLSocket`), UDP (`DatagramSocket` / `MulticastSocket`), streaming HTTP (`HttpURLConnection` backing `HttpConnection` / `HttpRequest : Writable` / `HttpResponse : Readable`), RFC 6455 WebSocket (`JvmRfc6455WebSocketNativeImpl` supporting Socket.IO v4 / Engine.IO v4), and optional development TLS trust manager reused directly by `:desktop` Swing and `:mobile` Android (`AndroidTcpSocketNativeImpl`, `AndroidUdpSocketNativeImpl`, `AndroidHttpClientNativeImpl`, `AndroidWebSocketNativeImpl`). |
| **Native Control Sprite Tinting** | [`JvmControlTintPixelUtil`](../jvm/src/jvmSharedMain/kotlin/com/nexustools/genericui/jvm/JvmControlTintPixelUtil.kt) | Pure-Kotlin ARGB luminance-preserving pixel tinting, rounded-corner SDF clipping (`computeRoundedRectSdf`, `resolveCornerRadiusPx`), synthetic bevel fallback, and state shading (`hovered`, `pressed`, `disabled`, `outline`, `isLink`) used by both `SwingControlTintRenderUtil` and `AndroidControlTintRenderUtil`. |

---

## 3. Consumer Variant Scope & Testing

- **Application Variant Scope (`AppTarget.JVM_COMMON`, rank `2`)**:
  - Consumer apps can target both Swing and Android simultaneously via `@MainAppFragment(targets = [AppTarget.JVM_COMMON])`, `genericui { jvmCommon { ... } }`, or `genericui { sourceSets { jvmCommon { ... } } }` (`src/jvmCommon/kotlin` / `jvmCommonMain` when needed).
  - See [Hierarchical SourceSets (`sourcesets.md`)](./sourcesets.md) and [Gradle Plugin (`gradle-plugin.md`)](./gradle-plugin.md).
- **Consolidated `:jvm` Test Suite**:
  - Covered by 8 shared JUnit test suites under `jvm/src/jvmSharedTest/kotlin/com/nexustools/genericui/jvm/` (`JvmControlTintPixelUtilTest`, `JvmFileSystemAndStreamsTest`, `JvmHttpUrlConnectionClientTest`, `JvmImageSourceAndCodecTest`, `JvmPreferenceStoreBaseTest`, `JvmRfc6455WebSocketNativeImplTest`, `JvmTcpAndUdpSocketTest`, and `JvmUiThreadDispatcherAndDatabaseResourceTest`).
  - Run with `./gradlew :jvm:jvmTest` (plus Android host tests via `:jvm:testAndroidHostTest`).
