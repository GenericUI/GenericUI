# GenericUI Utilities, Binary Codecs, & PreferenceStore Reference

> **Root Repository**: [**GenericUI (`../README.md`)**](../README.md) | **Master Docs Index**: [**`docs/README.md`**](./README.md)  
> **Related Guides**: [Core Architecture](./architecture.md) · [Backends Guide](./backends.md) · [Components & Docking](./components-and-docking.md) · [Gradle Plugin](./gradle-plugin.md)

This document catalogs all utility classes, binary serialization codecs (`BinaryJson` and `BinaryPlist`), OS user directory abstractions (`UserDirectory`, `ConfigDirectory`, and `CacheDirectory`), cross-platform persistent preferences (`PreferenceStore` and `PreferenceObjectCodec`), URL/resource image caching (`ImageCacheManager` and `ImageLoader`), geometry/style helpers, lazy diffing utilities, and shared platform utilities across `:common`, `:desktop:shared`, `:mobile:shared`, and `:web`.

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

#### `BinaryPlist` (`bplist00`) Wire Format

1. **8-Byte Header**: ASCII `"bplist00"` (`0x62 0x70 0x6C 0x69 0x73 0x74 0x30 0x30`).
2. **Object Table**: Variable-length objects identified by a 1-byte marker (`high nibble = object type`, `low nibble = size/subtype`):
   - `0x00`: `null`
   - `0x08`: `false`
   - `0x09`: `true`
   - `0x10..0x13`: Big-endian signed/unsigned integer (`1`, `2`, `4`, or `8` bytes: `2^(nibble)`)
   - `0x22` / `0x23`: Big-endian IEEE-754 `Float` (4 bytes) / `Double` (8 bytes)
   - `0x40..0x4F`: Raw `NSData` bytes
   - `0x50..0x5F`: ASCII string (`0x5F` followed by integer count for lengths $\ge 15$)
   - `0x60..0x6F`: UTF-16BE string (`0x6F` followed by character count for lengths $\ge 15$)
   - `0xA0..0xAF`: Array of `objectRef` indices
   - `0xD0..0xDF`: Dictionary of `keyRef` indices followed by `valueRef` indices
3. **Offset Table**: Big-endian offsets (`offsetIntSize` bytes each) pointing to every object in the Object Table.
4. **32-Byte Trailer**:
   - Bytes `0..5`: Unused (`0x00`)
   - Byte `6`: `offsetIntSize` (`1`, `2`, `4`, or `8`)
   - Byte `7`: `objectRefSize` (`1`, `2`, `4`, or `8`)
   - Bytes `8..15`: `numObjects` (8-byte big-endian `Long`)
   - Bytes `16..23`: `topObject` index (8-byte big-endian `Long`)
   - Bytes `24..31`: `offsetTableOffset` (8-byte big-endian `Long`)

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
- **Class Hierarchy**:
  - `UserDirectory` (abstract base class providing file read/write/delete/list/size/clear operations and automatic migration from the in-memory fallback when `GenericUI.activeFactory` is initialized)
    - $\longrightarrow$ `ConfigDirectory` (extends `UserDirectory`; adds key-based file helpers `fileNameForKey`, `resolveKeyPath`, `writeKeyBytes`, `readKeyBytes`, `containsKey`, `removeKey`, `keysForExtension`, and `clearKeysWithExtension` used by `PreferenceStore`)
      - $\longrightarrow$ `CacheDirectory` (extends `ConfigDirectory`; targets the OS user cache directory and adds deterministic FNV-1a URL/key hashing and cache management helpers `cacheFileNameForKey`, `putCachedBytes`, `getCachedBytes`, `hasCachedEntry`, `evictCachedEntry`, and `clearCache()`)

#### Per-Backend `ConfigDirectory` & `CacheDirectory` Implementation Matrix

| Backend Module | `ConfigDirectory` Native Impl & OS Path | `CacheDirectory` Native Impl & OS Path |
| :--- | :--- | :--- |
| **`:desktop:windows`** | `WindowsConfigDirectoryImpl` $\rightarrow$ `%APPDATA%\<appName>[\<subdir>]` | `WindowsCacheDirectoryImpl` $\rightarrow$ `%LOCALAPPDATA%\<appName>\Cache[\<subdir>]` |
| **`:desktop:macos`** | `MacosConfigDirectoryImpl` $\rightarrow$ `$XDG_CONFIG_HOME/<appName>` or `~/.config/<appName>` | `MacosCacheDirectoryImpl` $\rightarrow$ `~/Library/Caches/<appName>[/<subdir>]` |
| **`:desktop:gtk`** | `GtkConfigDirectoryImpl` $\rightarrow$ `$XDG_CONFIG_HOME/<appName>` or `~/.config/<appName>` | `GtkCacheDirectoryImpl` $\rightarrow$ `$XDG_CACHE_HOME/<appName>` or `~/.cache/<appName>` |
| **`:desktop:qt`** | `QtConfigDirectoryImpl` $\rightarrow$ `$XDG_CONFIG_HOME/<appName>` or `~/.config/<appName>` | `QtCacheDirectoryImpl` $\rightarrow$ `$XDG_CACHE_HOME/<appName>` or `~/.cache/<appName>` |
| **`:desktop:swing`** | `SwingUserDirectoryNativeImpl(isCache = false)` $\rightarrow$ Host OS config directory (`java.io.File`) | `SwingUserDirectoryNativeImpl(isCache = true)` $\rightarrow$ Host OS cache directory (`java.io.File`) |
| **`:mobile:android`** | `AndroidUserDirectoryNativeImpl(isCache = false)` $\rightarrow$ `<context.filesDir>/<appName>` | `AndroidUserDirectoryNativeImpl(isCache = true)` $\rightarrow$ `<context.cacheDir>/<appName>` |
| **`:mobile:ios`** | `IosUserDirectoryNativeImpl(isCache = false)` $\rightarrow$ `<Home>/Library/Application Support/<appName>` | `IosUserDirectoryNativeImpl(isCache = true)` $\rightarrow$ `<Home>/Library/Caches/<appName>` |
| **`:web`** | `WebUserDirectoryNativeImpl(scheme = "config")` $\rightarrow$ `localStorage` (`genericui:config:<appName>:...`) | `WebUserDirectoryNativeImpl(scheme = "cache")` $\rightarrow$ `localStorage` (`genericui:cache:<appName>:...`) |

#### Code Example

```kotlin
import com.nexustools.genericui.util.ConfigDirectory
import com.nexustools.genericui.util.CacheDirectory

// Access the user's OS configuration directory for the active application
val configDir = ConfigDirectory(subdirectory = "profiles")
configDir.writeText("keybindings.json", """{"save":"Ctrl+S"}""")
val bindingsJson: String? = configDir.readText("keybindings.json")

// Access the user's OS cache directory for temporary or downloaded assets
val cacheDir = CacheDirectory(subdirectory = "thumbnails")
val cachedPath: String = cacheDir.putCachedBytes("https://example.com/thumb.png", pngBytes)
val restoredBytes: ByteArray? = cacheDir.getCachedBytes("https://example.com/thumb.png")

// Clear all files in the cache directory ("Clear Cache" button)
val deletedFilesCount: Int = cacheDir.clearCache()
```

---

## 2. Cross-Platform Preference Storage & Object Serialization (`com.nexustools.genericui.component.system.*`)

### 2.1 `PreferenceStore`

- **Source**: [`common/src/commonMain/kotlin/com/nexustools/genericui/component/system/PreferenceStore.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/system/PreferenceStore.kt)
- **Native Contract**: [`PreferenceStoreNativeImpl`](../common/src/commonMain/kotlin/com/nexustools/genericui/nativeimpl/system/PreferenceStoreNativeImpl.kt) & [`DefaultPreferenceStoreNativeImpl`](../common/src/commonMain/kotlin/com/nexustools/genericui/nativeimpl/system/DefaultPreferenceStoreNativeImpl.kt)
- **Purpose**: Provides a unified, type-safe key-value preference store across all 8 platforms with automatic object serialization, custom type adapters, and live change notifications (including external file modifications and cross-tab browser `storage` events).
- **Default Application Name**: Defaults to `GenericUI.applicationName`, which is automatically populated from `genericui { application { appName = "..." } }` in `build.gradle.kts` by the GenericUI Gradle plugin.

#### Platform Storage Matrix

| Backend Module | Storage Mechanism & Path | Wire Format | External Change Detection |
| :--- | :--- | :--- | :--- |
| **`:desktop:windows`** | `%APPDATA%\<appName>\<key>.bson` | `BinaryJson` (`.bson`) | Timestamp / content polling (`pollExternalChanges()`) |
| **`:desktop:macos`** | `~/Library/Preferences/<appName>/<key>.plist` | `BinaryPlist` (`bplist00` `.plist`) | Timestamp / content polling (`pollExternalChanges()`) |
| **`:desktop:gtk`** | `$XDG_CONFIG_HOME/<appName>/<key>.bson` (or `~/.config/<appName>/<key>.bson`) | `BinaryJson` (`.bson`) | Timestamp / content polling (`pollExternalChanges()`) |
| **`:desktop:qt`** | `$XDG_CONFIG_HOME/<appName>/<key>.bson` (or `~/.config/<appName>/<key>.bson`) | `BinaryJson` (`.bson`) | Timestamp / content polling (`pollExternalChanges()`) |
| **`:desktop:swing`** | macOS: `~/Library/Preferences/<appName>/<key>.plist`<br/>Windows: `%APPDATA%\<appName>\<key>.bson`<br/>Linux: `$XDG_CONFIG_HOME/<appName>/<key>.bson` | `BinaryPlist` (`.plist`) on macOS;<br/>`BinaryJson` (`.bson`) on Windows & Linux | Background daemon `java.nio.file.WatchService` (`ENTRY_CREATE`, `ENTRY_MODIFY`, `ENTRY_DELETE`) + `pollExternalChanges()` |
| **`:mobile:android`** | Android `Context.getSharedPreferences(appName, Context.MODE_PRIVATE)` | Native booleans/numbers/strings + JSON via `PreferenceObjectCodec` for complex objects | Strong-referenced `SharedPreferences.OnSharedPreferenceChangeListener` |
| **`:mobile:ios`** | `<NSHomeDirectory()>/Library/Preferences/<appName>/<key>.plist` | `BinaryPlist` (`bplist00` `.plist`) | Timestamp / content polling (`pollExternalChanges()`) |
| **`:web`** | Browser `window.localStorage.getItem(key)` / `setItem(key, json)` (direct keys, no prefix) | JSON via `PreferenceObjectCodec` | Browser `window.addEventListener("storage", ...)` via `WebBrowserDomBridge` |

#### Code Example

```kotlin
import com.nexustools.genericui.component.system.PreferenceStore
import com.nexustools.genericui.component.system.PreferenceTypeAdapter
import com.nexustools.genericui.component.system.PreferenceElement

data class EditorTheme(val name: String = "Darcula", val fontSize: Int = 14)

// Uses GenericUI.applicationName configured by genericui { application { appName = "..." } }
val prefs = PreferenceStore()

// Store and retrieve primitives
prefs.set("ui.sidebarWidth", 280)
val sidebarWidth: Int = prefs.getInt("ui.sidebarWidth", defaultValue = 240)

// Store and retrieve complex objects automatically
prefs.set("ui.theme", EditorTheme(name = "HighContrast", fontSize = 16))
val theme: EditorTheme = prefs.get("ui.theme", EditorTheme())

// Listen for in-process or external changes (e.g. file edits on disk or another browser tab)
val listener = prefs.onChange { key, newValue ->
    println("Preference '$key' changed to: ${newValue?.asStringOrNull() ?: newValue}")
}
```

---

### 2.2 `PreferenceObjectCodec` & Supporting Serialization Classes

| Class / Interface | Package | Responsibility |
| :--- | :--- | :--- |
| [`PreferenceObjectCodec`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/system/PreferenceObjectCodec.kt) | `com.nexustools.genericui.component.system` | Converts arbitrary Kotlin objects, primitives, `Enum`s, `List`s, `Set`s, `Array`s, and `Map`s to/from [`PreferenceElement`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/system/PreferenceElement.kt) AST nodes and JSON strings (`toPreferenceElement`, `fromPreferenceElement`, `toJson`, `fromJson`, `parseJsonElement`, `stringifyElement`). |
| [`PreferenceElement`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/system/PreferenceElement.kt) | `com.nexustools.genericui.component.system` | Immutable AST node representing a preference value (`Null`, `ofBoolean`, `ofNumber`, `ofString`, `ofArray`, `ofMap`, `ofObject`). |
| [`PreferenceElementKind`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/system/PreferenceElementKind.kt) | `com.nexustools.genericui.component.system` | Enum of AST node kinds: `NULL`, `BOOLEAN`, `NUMBER`, `STRING`, `ARRAY`, `MAP`, `OBJECT`. |
| [`PreferenceTypeAdapter<T>`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/system/PreferenceTypeAdapter.kt) | `com.nexustools.genericui.component.system` | Custom serializer/deserializer interface (`serialize(value: T): PreferenceElement`, `deserialize(element: PreferenceElement): T`) registered via `PreferenceStore.registerTypeAdapter` or `PreferenceObjectCodec.registerTypeAdapter`. |
| [`PreferenceSerializable`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/system/PreferenceSerializable.kt) | `com.nexustools.genericui.component.system` | Optional interface (`toPreferenceMap(): Map<String, Any?>`, `fromPreferenceMap(map: Map<String, Any?>)`) for zero-reflection KMP serialization across all targets including Kotlin/Native. |
| [`PreferenceChangeListener`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/system/PreferenceChangeListener.kt) | `com.nexustools.genericui.component.system` | `fun interface` invoked with `(key: String, newValue: PreferenceElement?)` when a key is updated, externally modified, or removed (`newValue == null`). |
| [`PreferencePlatformReflector`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/system/PreferencePlatformReflector.kt) | `com.nexustools.genericui.component.system` | Internal `expect`/`actual` reflection helper (`jvmMain`, `androidMain`, `nativeMain`, `jsMain`, `wasmJsMain`) extracting object properties and invoking constructors on platforms with runtime reflection. |

---

## 3. Complete Catalog of Utilities Across GenericUI

### 3.1 Geometry, Event, & Core Utilities (`:common`)

| Utility Class | Package | Source File | Description |
| :--- | :--- | :--- | :--- |
| `Point` | `com.nexustools.genericui.geometry` | [`Point.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/geometry/Point.kt) | 2D `(x: Float, y: Float)` coordinate with vector translation and distance helpers. |
| `Size` | `com.nexustools.genericui.geometry` | [`Size.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/geometry/Size.kt) | 2D `(width: Float, height: Float)` dimension descriptor. |
| `Rect` | `com.nexustools.genericui.geometry` | [`Rect.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/geometry/Rect.kt) | Axis-aligned `(x, y, width, height)` bounding rectangle with intersection and hit-testing utilities. |
| `EdgeInsets` | `com.nexustools.genericui.geometry` | [`EdgeInsets.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/geometry/EdgeInsets.kt) | `(top, right, bottom, left)` padding, margin, and safe-area inset utility. |
| `KeyShortcut` | `com.nexustools.genericui.event` | [`KeyShortcut.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/event/KeyShortcut.kt) | Cross-platform keyboard accelerator parser and formatter (e.g. `"Ctrl+Shift+S"`, `"Cmd+N"`). |
| `Dialogs` | `com.nexustools.genericui.component.dialog` | [`Dialogs.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/dialog/Dialogs.kt) | Static convenience factory for showing standard alert, confirm, prompt, choice, color, and file/folder dialogs. |
| `FileTypeFilter` | `com.nexustools.genericui.component.dialog` | [`FileTypeFilter.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/dialog/FileTypeFilter.kt) | File extension, MIME type, and macOS/iOS `UTType` filter utility with `toWebAcceptAttribute()` helper. |

### 3.2 Styling, Color, SASS/CSS, & Transition Utilities (`:common`)

| Utility Class | Package | Source File | Description |
| :--- | :--- | :--- | :--- |
| `RgbaColor` | `com.nexustools.genericui.style.color` | [`RgbaColor.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/style/color/RgbaColor.kt) | RGBA color representation with hex (`#RRGGBB` / `#RRGGBBAA`), CSS `rgb()`/`rgba()`, alpha blending, and linear interpolation (`lerp`) helpers. |
| `BootstrapColorPalette` | `com.nexustools.genericui.style.color` | [`BootstrapColorPalette.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/style/color/BootstrapColorPalette.kt) | Standard Bootstrap 5 semantic color palette (`PRIMARY`, `SECONDARY`, `SUCCESS`, `DANGER`, `WARNING`, `INFO`, `LIGHT`, `DARK`) for light and dark themes. |
| `BootstrapClassMapper` | `com.nexustools.genericui.style.mapper` | [`BootstrapClassMapper.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/style/mapper/BootstrapClassMapper.kt) | Bidirectional mapper converting Bootstrap 5 enums (`BootstrapType`, `BootstrapSize`, `SpacingScale`, `FlexDirection`, etc.) to/from CSS class strings. |
| `BootstrapStyleSnapshot` | `com.nexustools.genericui.style.mapper` | [`BootstrapStyleSnapshot.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/style/mapper/BootstrapStyleSnapshot.kt) | Immutable snapshot of resolved Bootstrap utility classes and visual tokens for a widget. |
| `SassCompiler` | `com.nexustools.genericui.style.sass` | [`SassCompiler.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/style/sass/SassCompiler.kt) | Compiles `.scss` variables, nested selectors, mixins, and Bootstrap theme tokens into `CssStyleSheet` rules. |
| `SassKotlinPrecompiler` | `com.nexustools.genericui.style.sass` | [`SassKotlinPrecompiler.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/style/sass/SassKotlinPrecompiler.kt) | Precompiles `.scss`/`.css` stylesheets into zero-parse Kotlin `PrecompiledStyleSheet` code at build time. |
| `FontRegistry` | `com.nexustools.genericui.style.font` | [`FontRegistry.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/style/font/FontRegistry.kt) | Cross-platform custom font registration and family lookup utility. |
| `CssTransitionController` | `com.nexustools.genericui.style.transition` | [`CssTransitionController.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/style/transition/CssTransitionController.kt) | Interpolates numeric, color, and 2D transform CSS properties across VBlank frames using `CssTimingFunction` cubic-Bezier curves. |

### 3.3 Lazy Viewport & Diffing Utilities (`:common`)

| Utility Class | Package | Source File | Description |
| :--- | :--- | :--- | :--- |
| `LazyDiffUtil` | `com.nexustools.genericui.component.lazy` | [`LazyDiffUtil.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/lazy/LazyDiffUtil.kt) | Computes minimal `DiffResult` updates between old and new data lists for `LazyList`, `LazyGrid`, and `TreeView`. |
| `LazyViewportTracker` | `com.nexustools.genericui.component.lazy` | [`LazyViewportTracker.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/lazy/LazyViewportTracker.kt) | Calculates the visible `[firstActiveIndex .. lastActiveIndex]` window from scroll offset, viewport extent, and overscan count. |
| `ViewHolderPool` | `com.nexustools.genericui.component.lazy` | [`ViewHolderPool.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/lazy/ViewHolderPool.kt) | Type-keyed recycling pool for unbound `ViewHolder` instances as items scroll out of the active viewport. |

### 3.4 Image, Cache, Resource, & Permission Utilities (`:common`)

| Utility Class | Package | Source File | Description |
| :--- | :--- | :--- | :--- |
| `ImageLoader` | `com.nexustools.genericui.image` | [`ImageLoader.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/image/ImageLoader.kt) | High-level multi-format image decoder, encoder, header inspector, and animated frame loader with automatic URL/resource caching via `ImageCacheManager` and `ImageLoader.clearCache()`. |
| `ImageCacheManager` & `ImageCacheEntry` | `com.nexustools.genericui.image.cache` | [`ImageCacheManager.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/image/cache/ImageCacheManager.kt), [`ImageCacheEntry.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/image/cache/ImageCacheEntry.kt) | Persistent OS `CacheDirectory` (`<userCacheDir>/<appName>/images`) + in-memory `ImageBuffer` LRU cache for `http://`, `https://`, `data:image/...`, and `res://` images with `clearCache()`, `clearDiskCache()`, `clearMemoryCache()`, and `evict(url)`. |
| `LibJpegCodec`, `LibPngCodec`, `LibWebpCodec` | `com.nexustools.genericui.image.codec` | [`LibJpegCodec.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/image/codec/LibJpegCodec.kt), [`LibPngCodec.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/image/codec/LibPngCodec.kt), [`LibWebpCodec.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/image/codec/LibWebpCodec.kt) | Pure-Kotlin Multiplatform JPEG (`SOF0`/`DHT`/`DQT`/`SOS` + 8x8 2D IDCT), PNG (`IHDR`/`PLTE`/`tRNS`/`IDAT` RFC 1950 `zlib` + RFC 1951 `DEFLATE` + scanline unfiltering + Adam7), and WebP codecs with native C interop hooks. |
| `Resources` & `ResourceTable` | `com.nexustools.genericui.res` | [`Resources.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/res/Resources.kt), [`ResourceTable.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/res/ResourceTable.kt) | Runtime resource lookup backing generated `R` and `RC` resource accessors (strings, colors, dimensions, drawables, fonts, raw assets). |
| `PermissionManager` | `com.nexustools.genericui.permission` | [`PermissionManager.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/permission/PermissionManager.kt) | Cross-platform utility for checking and requesting OS permissions (`PermissionType`, `PermissionStatus`). |

#### URL Image Caching & Clearing Cache (`ImageLoader` / `ImageCacheManager`)

```kotlin
import com.nexustools.genericui.image.ImageLoader
import com.nexustools.genericui.image.cache.ImageCacheManager

// Automatically checks CacheDirectory("<appName>", "images") first, downloads on cache miss,
// stores the encoded file in the OS CacheDirectory, and decodes into an ImageBuffer:
val banner = ImageLoader.loadFromUrl("https://example.com/assets/banner.png")

// Inspect cache state
println("OS Image Cache Path: ${ImageLoader.cacheDirectoryPath}")
println("Cached disk bytes: ${ImageLoader.cachedDiskByteSize}")

// Evict a single URL or clear the entire image cache ("Clear Cache" user action)
ImageLoader.evictFromCache("https://example.com/assets/banner.png")
val removedFileCount: Int = ImageLoader.clearCache()
```

### 3.5 Platform-Shared Utilities (`:desktop:shared`, `:mobile:shared`, `:web`)

| Utility Class | Module | Source File | Description |
| :--- | :--- | :--- | :--- |
| `DesktopConfigDirectoryHelper` | `:desktop:shared` | [`DesktopConfigDirectoryHelper.kt`](../desktop/shared/src/commonMain/kotlin/com/nexustools/genericui/desktop/system/DesktopConfigDirectoryHelper.kt) | Resolves OS preference directories (`%APPDATA%\<appName>`, `~/Library/Preferences/<appName>`, `$XDG_CONFIG_HOME/<appName>`), sanitizes preference key filenames, and provides POSIX binary file read/write/delete helpers. |
| `DesktopFileBrowserHelper` | `:desktop:shared` | [`DesktopFileBrowserHelper.kt`](../desktop/shared/src/commonMain/kotlin/com/nexustools/genericui/desktop/dialog/DesktopFileBrowserHelper.kt) | Formats `FileBrowserRequest` filters for Win32 `COMDLG_FILTERSPEC`, macOS `NSOpenPanel`, GTK4 `GtkFileFilter`, and Qt6 `QFileDialog`. |
| `DesktopLazyViewportEngine` | `:desktop:shared` | [`DesktopLazyViewportEngine.kt`](../desktop/shared/src/commonMain/kotlin/com/nexustools/genericui/desktop/lazy/DesktopLazyViewportEngine.kt) | Shared native desktop viewport windowing engine for `LazyList`, `LazyGrid`, and `TreeView`. |
| `DesktopMultiWindowDockCoordinator` | `:desktop:shared` | [`DesktopMultiWindowDockCoordinator.kt`](../desktop/shared/src/commonMain/kotlin/com/nexustools/genericui/desktop/dock/DesktopMultiWindowDockCoordinator.kt) | Coordinates GIMP-style Single-Window Mode vs. Multi-Window Mode floating utility windows across native desktop backends. |
| `MobileAdaptiveFragmentCoordinator` | `:mobile:shared` | [`MobileAdaptiveFragmentCoordinator.kt`](../mobile/shared/src/commonMain/kotlin/com/nexustools/genericui/mobile/fragment/MobileAdaptiveFragmentCoordinator.kt) | Manages phone/tablet/foldable posture breakpoints, safe-area insets, and landscape fullscreen transitions on Android and iOS. |
| `MobileGestureRecognizer` & `MobileFlingPhysics` | `:mobile:shared` | [`MobileGestureRecognizer.kt`](../mobile/shared/src/commonMain/kotlin/com/nexustools/genericui/mobile/gesture/MobileGestureRecognizer.kt), [`MobileFlingPhysics.kt`](../mobile/shared/src/commonMain/kotlin/com/nexustools/genericui/mobile/gesture/MobileFlingPhysics.kt) | Multi-touch pan/pinch/rotate/long-press gesture detection and spline deceleration fling physics. |
| `MobilePipController` | `:mobile:shared` | [`MobilePipController.kt`](../mobile/shared/src/commonMain/kotlin/com/nexustools/genericui/mobile/pip/MobilePipController.kt) | Coordinates Picture-in-Picture aspect ratio and action controls on Android and iOS. |
| `MobileLazyViewportPool` | `:mobile:shared` | [`MobileLazyViewportPool.kt`](../mobile/shared/src/commonMain/kotlin/com/nexustools/genericui/mobile/lazy/MobileLazyViewportPool.kt) | Shared cell recycling pool and memory-pressure trimmer for `RecyclerView` and `UICollectionView`. |
| `WebBrowserDomBridge` | `:web` | [`WebBrowserDomBridge.kt`](../web/src/commonMain/kotlin/com/nexustools/genericui/web/WebBrowserDomBridge.kt) | Dual Kotlin/Wasm (`wasmJsMain`) and Kotlin/JS (`jsMain`) bridge for DOM element manipulation, `window.localStorage`, `window.addEventListener("storage", ...)`, and `requestAnimationFrame`. |
