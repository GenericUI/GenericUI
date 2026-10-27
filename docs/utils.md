# GenericUI Utilities, Binary Codecs, & PreferenceStore Reference

> **Root Repository**: [**GenericUI (`../README.md`)**](../README.md) | **Master Docs Index**: [**`docs/README.md`**](./README.md)  
> **Related Guides**: [Core Architecture (`architecture.md`)](./architecture.md) · [Resources (`resources.md`)](./resources.md) · [Fragments & Windows (`fragments-and-windows.md`)](./fragments-and-windows.md) · [Application (`application.md`)](./application.md) · [Backends Guide (`backends.md`)](./backends.md) · [Gradle Plugin (`gradle-plugin.md`)](./gradle-plugin.md)

This document catalogs all utility classes, binary serialization codecs (`BinaryJson` and `BinaryPlist`), OS user directory abstractions (`UserDirectory`, `ConfigDirectory`, and `CacheDirectory`), cross-platform persistent preferences (`PreferenceStore` and `PreferenceObjectCodec`), URL/resource image caching (`ImageCacheManager` and `ImageLoader`), general-purpose lifecycle-bound UI timers, geometry/style helpers, lazy diffing utilities, and platform foundation utilities across `:common`, `:systemCommon`, `:desktop:common`, `:desktop:nativeCommon`, `:desktop:linuxCommon`, `:mobile:common`, and `:web`.

---

## 1. Binary Serialization Utilities (`com.nexustools.genericui.util.*`)

### 1.1 `BinaryJson` (`.bson` Binary JSON Codec)

- **Source**: [`common/src/commonMain/kotlin/com/nexustools/genericui/util/BinaryJson.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/util/BinaryJson.kt)
- **Purpose**: Encodes and decodes structured values (`PreferenceElement` AST nodes, primitives, strings, lists, maps, and objects serialized via `PreferenceObjectCodec`) into a compact binary JSON byte stream using a **1-byte type tag (`0x00`..`0x0B`)** followed by **big-endian** length and content bytes.
- **Used By**: `PreferenceStore` on **Windows (`:desktop:windows`)**, **Linux GTK (`:desktop:gtk`)**, **Linux Qt (`:desktop:qt`)**, and **JVM Swing on Windows/Linux (`:desktop:swing`)** to persist per-key `.bson` files (`<configDir>/<appName>/<key>.bson`).

#### `BinaryJson` Wire Format (1-Byte Tag + Big-Endian Payload)

| Tag Byte | Constant | Payload Layout (Big-Endian) | Description |
| :--- | :--- | :--- | :--- |
| `0x00` | `TAG_NULL` | *(0 bytes)* | `null` (`PreferenceElement.Null`) |
| `0x01` | `TAG_FALSE` | *(0 bytes)* | Boolean `false` |
| `0x02` | `TAG_TRUE` | *(0 bytes)* | Boolean `true` |
| `0x03` | `TAG_BYTE` | `1 byte` (`Byte`) | Signed 8-bit integer |
| `0x04` | `TAG_SHORT` | `2 bytes` (`Short`, big-endian) | Signed 16-bit integer |
| `0x05` | `TAG_INT` | `4 bytes` (`Int`, big-endian) | Signed 32-bit integer |
| `0x06` | `TAG_LONG` | `8 bytes` (`Long`, big-endian) | Signed 64-bit integer |
| `0x07` | `TAG_FLOAT` | `4 bytes` (`Float` IEEE-754 raw bits, big-endian) | 32-bit floating-point number |
| `0x08` | `TAG_DOUBLE` | `8 bytes` (`Double` IEEE-754 raw bits, big-endian) | 64-bit floating-point number |
| `0x09` | `TAG_STRING` | `u32 byteLength` + `UTF-8 bytes` | UTF-8 encoded string |
| `0x0A` | `TAG_ARRAY` | `u32 elementCount` + `N encoded elements` | Ordered list/array of elements |
| `0x0B` | `TAG_OBJECT` | `u32 entryCount` + `N × (u32 keyByteLen + UTF-8 key + encoded value)` | Key-value map / object |

#### Key Methods

| Method | Description |
| :--- | :--- |
| `BinaryJson.encodeToBytes(value: Any?): ByteArray` | Converts `value` to a `PreferenceElement` via `PreferenceObjectCodec` and encodes it into `.bson` binary bytes. |
| `BinaryJson.decodeFromBytes<T : Any>(bytes: ByteArray, type: KClass<T>): T?` | Decodes `.bson` binary bytes into a `PreferenceElement` and deserializes it into `T`. |
| `BinaryJson.decodeFromBytes<T : Any>(bytes: ByteArray): T?` | Reified inline overload of `decodeFromBytes`. |
| `BinaryJson.encodeElement(element: PreferenceElement): ByteArray` | Encodes a `PreferenceElement` AST directly to `.bson` binary bytes. |
| `BinaryJson.decodeElement(bytes: ByteArray): PreferenceElement` | Decodes `.bson` binary bytes back into a `PreferenceElement` AST. |

```kotlin
import com.nexustools.genericui.util.BinaryJson

data class ViewportConfig(val zoom: Double = 1.0, val showGrid: Boolean = true, val tags: List<String> = emptyList())

val bytes: ByteArray = BinaryJson.encodeToBytes(ViewportConfig(zoom = 2.5, showGrid = false, tags = listOf("ui", "vector")))
val restored: ViewportConfig? = BinaryJson.decodeFromBytes<ViewportConfig>(bytes)
```

---

### 1.2 `BinaryPlist` (Apple `bplist00` Binary Property List Codec)

- **Source**: [`common/src/commonMain/kotlin/com/nexustools/genericui/util/BinaryPlist.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/util/BinaryPlist.kt)
- **Purpose**: Pure Kotlin Multiplatform encoder and decoder for Apple's **`bplist00`** binary property list format (compatible with macOS/iOS `NSPropertyListSerialization` and `plutil`). Strictly supports binary `bplist00` property lists (no XML plist dependency).
- **Used By**: `PreferenceStore` on **macOS (`:desktop:macos`)**, **iOS (`:mobile:ios`)**, and **JVM Swing on macOS (`:desktop:swing`)** to persist per-key `.plist` files (`<configDir>/<appName>/<key>.plist`).

#### Key Methods

| Method | Description |
| :--- | :--- |
| `BinaryPlist.encodeBinary(value: Any?): ByteArray` | Converts `value` to a `PreferenceElement` and encodes it as an Apple `bplist00` binary property list. |
| `BinaryPlist.decodeBinary<T : Any>(bytes: ByteArray, type: KClass<T>): T?` | Decodes a `bplist00` byte array and deserializes it into `T`. |
| `BinaryPlist.decodeBinary<T : Any>(bytes: ByteArray): T?` | Reified inline overload of `decodeBinary`. |
| `BinaryPlist.encodeElementBinary(root: PreferenceElement): ByteArray` | Encodes a `PreferenceElement` AST directly into `bplist00` bytes (with automatic string key deduplication). |
| `BinaryPlist.decodeElementBinary(bytes: ByteArray): PreferenceElement` | Decodes `bplist00` bytes into a `PreferenceElement` AST (with cycle detection). |
| `BinaryPlist.isBinaryPlist(bytes: ByteArray): Boolean` | Returns `true` if `bytes` begins with the 8-byte `"bplist00"` magic header and has at least 40 bytes (header + trailer). |

---

### 1.3 `UserDirectory`, `ConfigDirectory`, & `CacheDirectory` (`com.nexustools.genericui.util.*`)

- **Sources**:
  - [`UserDirectory.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/util/UserDirectory.kt) (`abstract class UserDirectory : Disposable`)
  - [`ConfigDirectory.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/util/ConfigDirectory.kt) (`open class ConfigDirectory : UserDirectory`)
  - [`CacheDirectory.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/util/CacheDirectory.kt) (`class CacheDirectory : ConfigDirectory`)
- **Native Contracts**:
  - [`UserDirectoryNativeImpl.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/nativeimpl/system/UserDirectoryNativeImpl.kt)
  - [`DefaultUserDirectoryNativeImpl.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/nativeimpl/system/DefaultUserDirectoryNativeImpl.kt)

#### Per-Backend `ConfigDirectory` & `CacheDirectory` Implementation Matrix

| Backend Module | `ConfigDirectory` Native Impl & OS Path | `CacheDirectory` Native Impl & OS Path |
| :--- | :--- | :--- |
| **`:desktop:windows`** | `WindowsConfigDirectoryImpl` $\rightarrow$ `%APPDATA%\<appName>[\<subdir>]` | `WindowsCacheDirectoryImpl` $\rightarrow$ `%LOCALAPPDATA%\<appName>\Cache[\<subdir>]` |
| **`:desktop:macos`** | `MacosConfigDirectoryImpl` $\rightarrow$ `$XDG_CONFIG_HOME/<appName>` or `~/.config/<appName>` | `MacosCacheDirectoryImpl` $\rightarrow$ `~/Library/Caches/<appName>[/<subdir>]` |
| **`:desktop:gtk` & `:desktop:qt`** (`:desktop:linuxCommon`) | `LinuxXdgConfigDirectoryImpl` $\rightarrow$ `$XDG_CONFIG_HOME/<appName>` or `~/.config/<appName>` | `LinuxXdgCacheDirectoryImpl` $\rightarrow$ `$XDG_CACHE_HOME/<appName>` or `~/.cache/<appName>` |
| **`:desktop:swing`** | `SwingUserDirectoryNativeImpl(isCache = false)` $\rightarrow$ Host OS config directory (`java.io.File`) | `SwingUserDirectoryNativeImpl(isCache = true)` $\rightarrow$ Host OS cache directory (`java.io.File`) |
| **`:mobile:android`** | `AndroidUserDirectoryNativeImpl(isCache = false)` $\rightarrow$ `<context.filesDir>/<appName>` | `AndroidUserDirectoryNativeImpl(isCache = true)` $\rightarrow$ `<context.cacheDir>/<appName>` |
| **`:mobile:ios`** | `IosUserDirectoryNativeImpl(isCache = false)` $\rightarrow$ `<Home>/Library/Application Support/<appName>` | `IosUserDirectoryNativeImpl(isCache = true)` $\rightarrow$ `<Home>/Library/Caches/<appName>` |
| **`:web`** | `WebUserDirectoryNativeImpl(scheme = "config")` $\rightarrow$ `localStorage` (`genericui:config:<appName>:...`) | `WebUserDirectoryNativeImpl(scheme = "cache")` $\rightarrow$ `localStorage` (`genericui:cache:<appName>:...`) |

---

## 2. Cross-Platform Preference Storage & Object Serialization (`com.nexustools.genericui.component.system.*`)

### 2.1 `PreferenceStore`

- **Source**: [`common/src/commonMain/kotlin/com/nexustools/genericui/component/system/PreferenceStore.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/system/PreferenceStore.kt)
- **Native Contract**: [`PreferenceStoreNativeImpl`](../common/src/commonMain/kotlin/com/nexustools/genericui/nativeimpl/system/PreferenceStoreNativeImpl.kt) & [`DefaultPreferenceStoreNativeImpl`](../common/src/commonMain/kotlin/com/nexustools/genericui/nativeimpl/system/DefaultPreferenceStoreNativeImpl.kt)
- **Purpose**: Unified, type-safe key-value preference store across all 8 platforms with automatic object serialization, custom type adapters, and live change notifications. Defaults to `GenericUI.applicationName` configured by `genericui { app { name = "..." } }`.

#### Platform Storage Matrix

| Backend Module | Storage Mechanism & Path | Wire Format | External Change Detection |
| :--- | :--- | :--- | :--- |
| **`:desktop:windows`** | `%APPDATA%\<appName>\<key>.bson` | `BinaryJson` (`.bson`) | Timestamp / content polling (`pollExternalChanges()`) |
| **`:desktop:macos`** | `~/Library/Preferences/<appName>/<key>.plist` | `BinaryPlist` (`bplist00` `.plist`) | Timestamp / content polling (`pollExternalChanges()`) |
| **`:desktop:gtk` & `:desktop:qt`** (`:desktop:linuxCommon`) | `$XDG_CONFIG_HOME/<appName>/<key>.bson` (or `~/.config/<appName>/<key>.bson`) | `BinaryJson` (`.bson`) | Timestamp / content polling (`pollExternalChanges()`) |
| **`:desktop:swing`** | macOS: `~/Library/Preferences/<appName>/<key>.plist`<br/>Windows: `%APPDATA%\<appName>\<key>.bson`<br/>Linux: `$XDG_CONFIG_HOME/<appName>/<key>.bson` | `BinaryPlist` (`.plist`) on macOS;<br/>`BinaryJson` (`.bson`) on Windows & Linux | Background daemon `java.nio.file.WatchService` + `pollExternalChanges()` |
| **`:mobile:android`** | Android `Context.getSharedPreferences(appName, Context.MODE_PRIVATE)` | Native primitives + JSON via `PreferenceObjectCodec` | `SharedPreferences.OnSharedPreferenceChangeListener` |
| **`:mobile:ios`** | `<NSHomeDirectory()>/Library/Preferences/<appName>/<key>.plist` | `BinaryPlist` (`bplist00` `.plist`) | Timestamp / content polling (`pollExternalChanges()`) |
| **`:web`** | Browser `window.localStorage.getItem(key)` / `setItem(key, json)` | JSON via `PreferenceObjectCodec` | Browser `window.addEventListener("storage", ...)` |

```kotlin
import com.nexustools.genericui.component.system.PreferenceStore

data class EditorTheme(val name: String = "Darcula", val fontSize: Int = 14)

val prefs = PreferenceStore()
prefs.set("ui.sidebarWidth", 280)
val sidebarWidth: Int = prefs.getInt("ui.sidebarWidth", defaultValue = 240)

prefs.set("ui.theme", EditorTheme(name = "HighContrast", fontSize = 16))
val theme: EditorTheme = prefs.get("ui.theme", EditorTheme())
```

---

### 2.2 `PreferenceObjectCodec` & Supporting Serialization Classes

| Class / Interface | Package | Responsibility |
| :--- | :--- | :--- |
| [`PreferenceObjectCodec`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/system/PreferenceObjectCodec.kt) | `com.nexustools.genericui.component.system` | Converts arbitrary Kotlin objects, primitives, `Enum`s, `List`s, `Set`s, `Array`s, and `Map`s to/from [`PreferenceElement`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/system/PreferenceElement.kt) AST nodes and JSON strings. |
| [`PreferenceElement`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/system/PreferenceElement.kt) | `com.nexustools.genericui.component.system` | Immutable AST node representing a preference value (`Null`, `ofBoolean`, `ofNumber`, `ofString`, `ofArray`, `ofMap`, `ofObject`). |
| [`PreferenceElementKind`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/system/PreferenceElementKind.kt) | `com.nexustools.genericui.component.system` | Enum of AST node kinds: `NULL`, `BOOLEAN`, `NUMBER`, `STRING`, `ARRAY`, `MAP`, `OBJECT`. |
| [`PreferenceTypeAdapter<T>`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/system/PreferenceTypeAdapter.kt) | `com.nexustools.genericui.component.system` | Custom serializer/deserializer interface registered via `PreferenceStore.registerTypeAdapter`. |
| [`PreferenceSerializable`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/system/PreferenceSerializable.kt) | `com.nexustools.genericui.component.system` | Optional interface (`toPreferenceMap()`, `fromPreferenceMap(map)`) for zero-reflection KMP serialization across all targets. |
| [`PreferenceChangeListener`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/system/PreferenceChangeListener.kt) | `com.nexustools.genericui.component.system` | `fun interface` invoked with `(key: String, newValue: PreferenceElement?)` when a key is updated or removed. |

---

## 3. Complete Catalog of Utilities Across GenericUI

### 3.1 Geometry, Event, Timer, & Core Utilities (`:common`)

| Utility Class | Package | Source File | Description |
| :--- | :--- | :--- | :--- |
| `Point` | `com.nexustools.genericui.geometry` | [`Point.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/geometry/Point.kt) | 2D `(x: Float, y: Float)` coordinate with vector translation and distance helpers. |
| `Size` | `com.nexustools.genericui.geometry` | [`Size.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/geometry/Size.kt) | 2D `(width: Float, height: Float)` dimension descriptor. |
| `Rect` | `com.nexustools.genericui.geometry` | [`Rect.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/geometry/Rect.kt) | Axis-aligned `(x, y, width, height)` bounding rectangle with intersection and hit-testing utilities. |
| `EdgeInsets` | `com.nexustools.genericui.geometry` | [`EdgeInsets.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/geometry/EdgeInsets.kt) | `(top, right, bottom, left)` padding, margin, and safe-area inset utility. |
| `KeyShortcut` | `com.nexustools.genericui.event` | [`KeyShortcut.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/event/KeyShortcut.kt) | Cross-platform keyboard accelerator parser and formatter (e.g. `"Ctrl+Shift+S"`, `"Cmd+N"`). |
| `UiTimerScheduler` | `com.nexustools.genericui.core` | [`UiTimerScheduler.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/core/UiTimerScheduler.kt) | General-purpose, lifecycle-bound one-shot and repeating UI-thread timer across all 8 backends (`scheduleOnce`, `scheduleRepeating`, `cancel`). |
| `Dialogs` | `com.nexustools.genericui.component.dialog` | [`Dialogs.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/dialog/Dialogs.kt) | Static convenience factory for showing standard alert, confirm, prompt, choice, color, and file/folder dialogs. |
| `FileTypeFilter` | `com.nexustools.genericui.component.dialog` | [`FileTypeFilter.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/dialog/FileTypeFilter.kt) | File extension, MIME type, and macOS/iOS `UTType` filter utility with `toWebAcceptAttribute()` helper. |

### 3.2 Styling, Color, Bootstrap, Custom Cursor, SASS/CSS, & Theme Utilities (`:common`)

| Utility Class | Package | Source File | Description |
| :--- | :--- | :--- | :--- |
| `ComponentStyle` | `com.nexustools.genericui.style.css` | [`ComponentStyle.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/style/css/ComponentStyle.kt) | Strongly-typed inline W3C CSS style object (`component.getStyle()`) with per-property `!important` flags (`important = true`, `setImportant`), `null` to clear, `accentColor`, and zero pseudo-states. |
| `ComponentBootstrap` | `com.nexustools.genericui.style.bootstrap` | [`ComponentBootstrap.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/style/bootstrap/ComponentBootstrap.kt) | Strongly-typed Bootstrap 5 utility object (`component.getBootstrap()`, defaulting to `BootstrapType.NONE`) strictly isolated from user custom CSS classes (`getClasses()` / `hasClass()`). |
| `CssCustomCursor` | `com.nexustools.genericui.style.css` | [`CssCustomCursor.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/style/css/CssCustomCursor.kt) | Custom cursor descriptor supporting PNG `ResourceID`, SVG `ResourceID`, inline `svgMarkup`, and `ImageBuffer` with `(hotspotX, hotspotY)` and `CssCursor` fallback; translated to `.cur` on Windows and `NSCursor` on macOS. |
| `PrecompiledStyleSheet` & `StyleSheetRegistry` | `com.nexustools.genericui.style.css` | [`PrecompiledStyleSheet.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/style/css/PrecompiledStyleSheet.kt), [`StyleSheetRegistry.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/style/css/StyleSheetRegistry.kt) | Build-time precompiled `.scss` stylesheet representation (`CompiledStyleRule`, `Resource.style.<name>`) and runtime registry used by `IApplication.addStyleSheet` / `removeStyleSheet`. |
| `SystemThemeDetector` | `com.nexustools.genericui.style.theme` | [`SystemThemeDetector.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/style/theme/SystemThemeDetector.kt) | Resolves `ThemeMode.AUTO` (default) to `ThemeMode.DARK` or `ThemeMode.LIGHT` using live 8-target OS dark/light theme detection. |
| `RgbaColor` | `com.nexustools.genericui.style.color` | [`RgbaColor.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/style/color/RgbaColor.kt) | RGBA color representation with hex (`#RRGGBB` / `#RRGGBBAA`), CSS `rgb()`/`rgba()`, alpha blending, and `lerp` helpers. |
| `BootstrapColorPalette` | `com.nexustools.genericui.style.color` | [`BootstrapColorPalette.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/style/color/BootstrapColorPalette.kt) | Standard Bootstrap 5 semantic color palette (`PRIMARY`, `SECONDARY`, `SUCCESS`, `DANGER`, `WARNING`, `INFO`, `LIGHT`, `DARK`). |
| `BootstrapClassMapper` | `com.nexustools.genericui.style.mapper` | [`BootstrapClassMapper.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/style/mapper/BootstrapClassMapper.kt) | Bidirectional mapper converting Bootstrap 5 enums (`BootstrapType`, `BootstrapSize`, `SpacingScale`, `FlexDirection`, etc.) to/from CSS class strings. |
| `SassCompiler` | `com.nexustools.genericui.style.sass` | [`SassCompiler.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/style/sass/SassCompiler.kt) | Compiles `.scss` variables, nested selectors, mixins, and Bootstrap theme tokens into `CssStyleSheet` rules. |
| `SassKotlinPrecompiler` | `com.nexustools.genericui.style.sass` | [`SassKotlinPrecompiler.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/style/sass/SassKotlinPrecompiler.kt) | Precompiles `.scss`/`.css` stylesheets into zero-parse Kotlin `PrecompiledStyleSheet` code at build time. |
| `FontRegistry` | `com.nexustools.genericui.style.font` | [`FontRegistry.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/style/font/FontRegistry.kt) | Cross-platform custom font registration and family lookup utility. |
| `CssTransitionController` | `com.nexustools.genericui.style.transition` | [`CssTransitionController.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/style/transition/CssTransitionController.kt) | Interpolates numeric, color, and 2D transform CSS properties across VBlank frames using cubic-Bezier curves. |

### 3.3 Lazy Viewport & Diffing Utilities (`:common`)

| Utility Class | Package | Source File | Description |
| :--- | :--- | :--- | :--- |
| `LazyDiffUtil` | `com.nexustools.genericui.component.lazy` | [`LazyDiffUtil.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/lazy/LazyDiffUtil.kt) | Computes minimal `DiffResult` updates between old and new data lists for `LazyList`, `LazyGrid`, and `TreeView`. |
| `LazyViewportTracker` | `com.nexustools.genericui.component.lazy` | [`LazyViewportTracker.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/lazy/LazyViewportTracker.kt) | Calculates the visible `[firstActiveIndex .. lastActiveIndex]` window from scroll offset, viewport extent, and overscan count. |
| `ViewHolderPool` | `com.nexustools.genericui.component.lazy` | [`ViewHolderPool.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/lazy/ViewHolderPool.kt) | Type-keyed recycling pool for unbound `ViewHolder` instances as items scroll out of the active viewport. |

### 3.4 Image, Cache, Resource, & Permission Utilities (`:common`)

| Utility Class | Package | Source File | Description |
| :--- | :--- | :--- | :--- |
| `ImageLoader` | `com.nexustools.genericui.image` | [`ImageLoader.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/image/ImageLoader.kt) | Multi-format image decoder, encoder, header inspector, and animated frame loader with automatic URL/resource caching via `ImageCacheManager`. |
| `ImageCacheManager` & `ImageCacheEntry` | `com.nexustools.genericui.image.cache` | [`ImageCacheManager.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/image/cache/ImageCacheManager.kt), [`ImageCacheEntry.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/image/cache/ImageCacheEntry.kt) | Persistent OS `CacheDirectory` + in-memory `ImageBuffer` LRU cache for `http://`, `https://`, `data:image/...`, and `res://` images. |
| `LibJpegCodec`, `LibPngCodec`, `LibWebpCodec` | `com.nexustools.genericui.image.codec` | [`LibJpegCodec.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/image/codec/LibJpegCodec.kt), [`LibPngCodec.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/image/codec/LibPngCodec.kt), [`LibWebpCodec.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/image/codec/LibWebpCodec.kt) | Multiplatform JPEG, PNG, and WebP codecs with native C interop hooks. |
| `ResourceID` & `ResourceResolver` | `com.nexustools.genericui.resources` | [`ResourceID.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/resources/ResourceID.kt), [`ResourceResolver.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/resources/ResourceResolver.kt) | Cross-platform resource identifier and runtime resolver backing generated `Resource`, `R`, and `RC` objects across all 8 backends. See [`resources.md`](./resources.md). |
| `Resources` & `ResourceTable` | `com.nexustools.genericui.res` | [`Resources.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/res/Resources.kt), [`ResourceTable.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/res/ResourceTable.kt) | In-memory resource registry and fallback table for strings, colors, dimensions, drawables, fonts, and raw bytes. |
| `PermissionManager` | `com.nexustools.genericui.permission` | [`PermissionManager.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/permission/PermissionManager.kt) | Cross-platform utility for checking and requesting OS permissions (`PermissionType`, `PermissionStatus`). |

### 3.5 Platform Foundation Utilities (`:systemCommon`, `:desktop:common`, `:desktop:nativeCommon`, `:desktop:linuxCommon`, `:mobile:common`, `:web`)

| Utility Class | Module | Source File | Description |
| :--- | :--- | :--- | :--- |
| `SystemStorageKeyHelper` | `:systemCommon` | [`SystemStorageKeyHelper.kt`](../systemCommon/src/commonMain/kotlin/com/nexustools/genericui/system/SystemStorageKeyHelper.kt) | Sanitizes preference and storage keys across all 7 system targets (`Windows`, `MacOS`, `Gtk`, `Qt`, `Swing`, `Android`, `IOS`). |
| `DesktopFileBrowserHelper` | `:desktop:common` | [`DesktopFileBrowserHelper.kt`](../desktop/common/src/commonMain/kotlin/com/nexustools/genericui/desktop/dialog/DesktopFileBrowserHelper.kt) | Formats `FileBrowserRequest` filters across desktop backends (`Windows`, `MacOS`, `Gtk`, `Qt`, `Swing`). |
| `DesktopLazyViewportEngine` | `:desktop:common` | [`DesktopLazyViewportEngine.kt`](../desktop/common/src/commonMain/kotlin/com/nexustools/genericui/desktop/lazy/DesktopLazyViewportEngine.kt) | Desktop viewport windowing engine for `LazyList`, `LazyGrid`, and `TreeView`. |
| `DesktopConfigDirectoryHelper` | `:desktop:nativeCommon` | [`DesktopConfigDirectoryHelper.kt`](../desktop/nativeCommon/src/commonMain/kotlin/com/nexustools/genericui/desktop/system/DesktopConfigDirectoryHelper.kt) | Resolves native desktop preference directories (`%APPDATA%\<appName>`, `~/Library/Preferences/<appName>`, `$XDG_CONFIG_HOME/<appName>`) and POSIX file I/O. |
| `DesktopMultiWindowDockCoordinator` | `:desktop:nativeCommon` | [`DesktopMultiWindowDockCoordinator.kt`](../desktop/nativeCommon/src/commonMain/kotlin/com/nexustools/genericui/desktop/dock/DesktopMultiWindowDockCoordinator.kt) | Coordinates GIMP-style Single-Window Mode vs. Multi-Window Mode floating utility windows across native desktop backends. |
| `LinuxXdgConfigDirectoryImpl`, `LinuxXdgCacheDirectoryImpl`, `LinuxXdgPreferenceStoreImpl` | `:desktop:linuxCommon` | [`LinuxXdgConfigDirectoryImpl.kt`](../desktop/linuxCommon/src/commonMain/kotlin/com/nexustools/genericui/linux/LinuxXdgConfigDirectoryImpl.kt), [`LinuxXdgCacheDirectoryImpl.kt`](../desktop/linuxCommon/src/commonMain/kotlin/com/nexustools/genericui/linux/LinuxXdgCacheDirectoryImpl.kt), [`LinuxXdgPreferenceStoreImpl.kt`](../desktop/linuxCommon/src/commonMain/kotlin/com/nexustools/genericui/linux/LinuxXdgPreferenceStoreImpl.kt) | Linux XDG Base Directory config, cache, and `.bson` preference storage shared by `:desktop:gtk` and `:desktop:qt`. |
| `MobileAdaptiveFragmentCoordinator` | `:mobile:common` | [`MobileAdaptiveFragmentCoordinator.kt`](../mobile/common/src/commonMain/kotlin/com/nexustools/genericui/mobile/layout/MobileAdaptiveFragmentCoordinator.kt) | Manages phone/tablet/foldable posture breakpoints, safe-area insets, and landscape fullscreen transitions on `:mobile:android` and `:mobile:ios`. |
| `MobileGestureRecognizer` & `MobileFlingPhysics` | `:mobile:common` | [`MobileGestureRecognizer.kt`](../mobile/common/src/commonMain/kotlin/com/nexustools/genericui/mobile/gesture/MobileGestureRecognizer.kt), [`MobileFlingPhysics.kt`](../mobile/common/src/commonMain/kotlin/com/nexustools/genericui/mobile/gesture/MobileFlingPhysics.kt) | Multi-touch pan/pinch/rotate/long-press gesture detection and spline deceleration fling physics. |
| `MobilePipController` | `:mobile:common` | [`MobilePipController.kt`](../mobile/common/src/commonMain/kotlin/com/nexustools/genericui/mobile/system/MobilePipController.kt) | Coordinates Picture-in-Picture aspect ratio and action controls on Android and iOS. |
| `MobileLazyViewportPool` | `:mobile:common` | [`MobileLazyViewportPool.kt`](../mobile/common/src/commonMain/kotlin/com/nexustools/genericui/mobile/system/MobileLazyViewportPool.kt) | Cell recycling pool and memory-pressure trimmer for `RecyclerView` and `UICollectionView`. |
| `WebBrowserDomBridge` | `:web` | [`WebBrowserDomBridge.kt`](../web/src/commonMain/kotlin/com/nexustools/genericui/web/WebBrowserDomBridge.kt) | Dual Kotlin/Wasm (`wasmJsMain`) and Kotlin/JS (`jsMain`) bridge for DOM element manipulation, `window.localStorage`, and `requestAnimationFrame`. |
