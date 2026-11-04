# GenericUI Utilities, Async Coroutine I/O (`ByteOrder`, `ByteBuffer`, `ReadableByteBuffer`, `WritableByteBuffer`, `Readable`, `Writable`, `Stream`, `IFileSystem`), `Database`, Networking (`Network`, `TcpSocket`, `UdpSocket`, `HttpConnection`, `HttpClient`, `SocketIoClient`), Binary Codecs, & `PreferenceStore` Reference

> **Root Repository**: [**GenericUI (`../README.md`)**](../README.md) | **Master Docs Index**: [**`docs/README.md`**](./README.md)  
> **Related Guides**: [Core Architecture (`architecture.md`)](./architecture.md) · [Resources (`resources.md`)](./resources.md) · [Fragments & Windows (`fragments-and-windows.md`)](./fragments-and-windows.md) · [Application (`application.md`)](./application.md) · [Backends Guide (`backends.md`)](./backends.md) · [Gradle Plugin (`gradle-plugin.md`)](./gradle-plugin.md)

This document catalogs GenericUI's **100% asynchronous, non-blocking coroutine I/O & storage APIs** (`ByteOrder`, `ByteBuffer`, `ReadableByteBuffer`, `WritableByteBuffer`, `Readable`, `Writable`, `Stream`, `StreamUtil`, `IFileSystem`, `FileSystemAvailability`, `FileSystem`, `UserDirectory`, `ConfigDirectory`, `CacheDirectory`, and zero-parsing structured `Database`), **100% asynchronous non-blocking networking APIs** (`Network`, `TcpSocket` / `TcpClient`, `UdpSocket` / `UdpClient`, `HttpConnection`, `HttpRequest`, `HttpResponse`, `HttpClient`, and `SocketIoClient`), streaming binary serialization codecs (`BinaryJson` and `BinaryPlist`), cross-platform persistent preferences (`IApplication.getPreferenceStore()`, `IFragment.getPreferenceStore()`, `PreferenceStore`, and `PreferenceObjectCodec`), URL/resource image caching (`ImageCacheManager` and `ImageLoader`), lifecycle-bound UI timers, geometry/style helpers, lazy diffing utilities, and platform foundation utilities across `:common`, `:systemCommon`, `:desktop:common`, `:desktop:nativeCommon`, `:desktop:linuxCommon`, `:mobile:common`, and `:web`.

---

## 1. 100% Async Non-Blocking Coroutine I/O, Streams, & File Systems (`com.nexustools.genericui.io.*` & `com.nexustools.genericui.util.*`)

All stream, file system, user directory, and database operations in GenericUI are **100% asynchronous, non-blocking Kotlin `suspend` functions** that never block the UI/main thread across all 8 platform backends.

### 1.1 `ByteOrder` & `ByteBuffer` (`com.nexustools.genericui.io.*`)

- **Sources**:
  - [`common/src/commonMain/kotlin/com/nexustools/genericui/io/ByteOrder.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/io/ByteOrder.kt) (`enum class ByteOrder { BIG_ENDIAN, LITTLE_ENDIAN }`)
  - [`common/src/commonMain/kotlin/com/nexustools/genericui/io/ByteBuffer.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/io/ByteBuffer.kt) (`class ByteBuffer`)
- **Purpose**: Cross-platform binary buffer wrapping a `ByteArray` with `position`, `limit`, `capacity`, `remaining`, `hasRemaining`, `ByteOrder`-parameterized (`BIG_ENDIAN` / `LITTLE_ENDIAN`) primitive `read*` and `write*` methods, `toString(encoding = "utf8")` string decoding, and zero-copy/sliced views. Used across `ReadableByteBuffer`, `WritableByteBuffer`, `Readable`, `Writable`, `Stream`, `IFileSystem`, `FileSystem`, `UserDirectory`, `ConfigDirectory`, `CacheDirectory`, `Database`, `TcpSocket`, `UdpSocket`, and `HttpClient`.

| Factory / Method | Signature / Return Type | Description |
| :--- | :--- | :--- |
| `ByteOrder.BIG_ENDIAN` / `LITTLE_ENDIAN` | `enum class ByteOrder` | Selects big-endian (network byte order, default) or little-endian byte ordering for multi-byte primitive reads and writes. |
| `ByteBuffer.allocate` | `allocate(capacity: Int): ByteBuffer` | Allocates a new zero-initialized `ByteBuffer` of `capacity` bytes. |
| `ByteBuffer.wrap` | `wrap(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset): ByteBuffer` | Wraps a `ByteArray` (or sub-range) into a `ByteBuffer`. |
| `ByteBuffer.fromUtf8` / `fromString` | `fromUtf8(text: String): ByteBuffer` / `fromString(text: String): ByteBuffer` | Encodes a UTF-8 `String` into a `ByteBuffer`. |
| `toString(encoding)` / `toUtf8String()` | `fun toString(encoding: String = "utf8"): String` | Decodes the buffer (`0 until limit`) using the specified character encoding (`"utf8"` / `"utf-8"`, `"ascii"`, `"latin1"` / `"iso-8859-1"`, `"utf-16be"`, `"utf-16le"`, `"hex"`, `"base64"`). |
| `flip()` / `rewind()` / `clear()` / `compact()` | `ByteBuffer` | Standard cursor state transitions (`flip` sets `limit = position; position = 0`; `rewind` resets `position = 0`; `clear` resets `position = 0; limit = capacity`). |
| `slice(startIndex, endIndex)` / `copy()` | `ByteBuffer` | Creates a sliced sub-buffer or independent deep copy. |
| `readByte()` / `writeByte(value)` | `Byte` / `ByteBuffer` | Reads or writes a single 8-bit signed byte at `position` (auto-growing capacity on `write*`). |
| `readShort(order)` / `writeShort(value, order)` | `fun readShort(order: ByteOrder = ByteOrder.BIG_ENDIAN): Short`<br/>`fun writeShort(value: Short, order: ByteOrder = ByteOrder.BIG_ENDIAN): ByteBuffer` | Reads or writes a 16-bit signed integer with configurable `ByteOrder`. |
| `readInt(order)` / `writeInt(value, order)` | `fun readInt(order: ByteOrder = ByteOrder.BIG_ENDIAN): Int`<br/>`fun writeInt(value: Int, order: ByteOrder = ByteOrder.BIG_ENDIAN): ByteBuffer` | Reads or writes a 32-bit signed integer with configurable `ByteOrder`. |
| `readLong(order)` / `writeLong(value, order)` | `fun readLong(order: ByteOrder = ByteOrder.BIG_ENDIAN): Long`<br/>`fun writeLong(value: Long, order: ByteOrder = ByteOrder.BIG_ENDIAN): ByteBuffer` | Reads or writes a 64-bit signed integer with configurable `ByteOrder`. |
| `readFloat(order)` / `writeFloat(value, order)` | `fun readFloat(order: ByteOrder = ByteOrder.BIG_ENDIAN): Float`<br/>`fun writeFloat(value: Float, order: ByteOrder = ByteOrder.BIG_ENDIAN): ByteBuffer` | Reads or writes a 32-bit IEEE-754 float with configurable `ByteOrder`. |
| `readDouble(order)` / `writeDouble(value, order)` | `fun readDouble(order: ByteOrder = ByteOrder.BIG_ENDIAN): Double`<br/>`fun writeDouble(value: Double, order: ByteOrder = ByteOrder.BIG_ENDIAN): ByteBuffer` | Reads or writes a 64-bit IEEE-754 double with configurable `ByteOrder`. |
| `readBytes(maxLength)` / `writeBytes(src, offset, length)` | `ByteArray` / `ByteBuffer` | Bulk byte array read/write advancing `position`. |
| `toByteArray()` / `remainingByteArray()` | `ByteArray` | Copies `0 until limit` or `position until limit` into a `ByteArray`. |

---

### 1.2 `Readable`, `Writable`, `Stream`, `ReadableByteBuffer`, `WritableByteBuffer`, & `StreamUtil` (`com.nexustools.genericui.io.*`)

- **Sources**:
  - [`Readable.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/io/Readable.kt) (`open class Readable : Disposable`)
  - [`Writable.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/io/Writable.kt) (`open class Writable : Disposable` — always spelled `Writable`, never `Writeable`)
  - [`Stream.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/io/Stream.kt) (`open class Stream : Readable, Writable`)
  - [`ReadableByteBuffer.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/io/ReadableByteBuffer.kt) (`class ReadableByteBuffer(val buffer: ByteBuffer) : Readable`)
  - [`WritableByteBuffer.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/io/WritableByteBuffer.kt) (`class WritableByteBuffer(initialCapacity: Int = 256) : Writable`)
  - [`ResourceReadableNativeImpl.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/io/ResourceReadableNativeImpl.kt) (`class ResourceReadableNativeImpl(val resourceId: ResourceID) : Readable`)
  - [`StreamUtil.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/io/StreamUtil.kt) (`object StreamUtil`)
- **Encapsulated Native Contracts** (`internal` / `protected` — never publicly exposed on user-facing classes):
  - [`ReadableNativeImpl`](../common/src/commonMain/kotlin/com/nexustools/genericui/nativeimpl/io/ReadableNativeImpl.kt) & [`MemoryReadableNativeImpl`](../common/src/commonMain/kotlin/com/nexustools/genericui/nativeimpl/io/MemoryReadableNativeImpl.kt)
  - [`WritableNativeImpl`](../common/src/commonMain/kotlin/com/nexustools/genericui/nativeimpl/io/WritableNativeImpl.kt) & [`MemoryWritableNativeImpl`](../common/src/commonMain/kotlin/com/nexustools/genericui/nativeimpl/io/MemoryWritableNativeImpl.kt)

#### `ReadableByteBuffer`, `WritableByteBuffer`, & `Stream`

| Class | Inheritance | Key API & Purpose |
| :--- | :--- | :--- |
| **`ReadableByteBuffer`** | `: Readable` | In-memory `Readable` stream backed directly by a [`ByteBuffer`](../common/src/commonMain/kotlin/com/nexustools/genericui/io/ByteBuffer.kt) (`ReadableByteBuffer(buffer)`, `ReadableByteBuffer.fromBytes(bytes)`, `ReadableByteBuffer.fromUtf8(text)`). Ideal for streaming binary decoders (`BinaryJson.decode`, `BinaryPlist.decode`, `PreferenceObjectCodec.decode`). |
| **`WritableByteBuffer`** | `: Writable` | In-memory `Writable` sink backed by an auto-expanding [`ByteBuffer`](../common/src/commonMain/kotlin/com/nexustools/genericui/io/ByteBuffer.kt) (`WritableByteBuffer(initialCapacity = 256)`, `toByteBuffer()`, `toByteArray()`, `toUtf8String()`, `size`). Ideal for streaming binary encoders (`BinaryJson.encode`, `BinaryPlist.encode`, `PreferenceObjectCodec.encode`). |
| **`Stream`** | `: Readable, Writable` | Full-duplex asynchronous stream combining `Readable` and `Writable` contracts (`read`, `readBuffer`, `write`, `writeText`, `flush`, `end`, `close`). Base class for [`TcpSocket`](../common/src/commonMain/kotlin/com/nexustools/genericui/net/TcpSocket.kt), [`UdpSocket`](../common/src/commonMain/kotlin/com/nexustools/genericui/net/UdpSocket.kt), and [`HttpConnection`](../common/src/commonMain/kotlin/com/nexustools/genericui/net/http/HttpConnection.kt). |

#### `Readable` Key Methods & Factories

| Method / Property | Signature / Return Type | Description |
| :--- | :--- | :--- |
| `read` | `suspend fun read(maxBytes: Int = 65536): ByteArray?` | Asynchronously reads up to `maxBytes`, or returns `null` on EOF. |
| `readBuffer` | `suspend fun readBuffer(maxBytes: Int = 65536): ByteBuffer?` | Asynchronously reads up to `maxBytes` into a `ByteBuffer`, or `null` on EOF. |
| `readText` | `suspend fun readText(maxBytes: Int = 65536): String?` | Asynchronously reads up to `maxBytes` and decodes as UTF-8, or `null` on EOF. |
| `readAllBytes` / `readAllBuffer` / `readAllText` | `suspend fun readAllBytes(): ByteArray`<br/>`suspend fun readAllBuffer(): ByteBuffer`<br/>`suspend fun readAllText(): String` | Asynchronously drains all remaining bytes from the stream into a `ByteArray`, `ByteBuffer`, or UTF-8 `String`. |
| `forEachChunk` / `forEachLine` | `suspend fun forEachChunk(chunkSize, consumer)`<br/>`suspend fun forEachLine(consumer)` | Asynchronously streams chunks or UTF-8 lines until EOF. |
| `pipe` | `suspend fun pipe(destination: Writable, endDestination: Boolean = true, chunkSize: Int = 65536): Long` | Asynchronously pipes all chunks into `destination` (`Writable`) and returns total bytes transferred. |
| `unshift` / `pause` / `resume` / `close` | `fun unshift(chunk: ByteArray)`<br/>`suspend fun close()` | Pushes bytes back onto the front of the read queue, pauses/resumes streaming, or closes the native stream. |
| `onData` / `onEnd` / `onError` / `onClose` | Event listener registration | NodeJS-style stream lifecycle hooks. |
| `Readable.fromBytes` / `fromBuffer` / `fromText` / `empty` | Factory methods | Creates an in-memory `Readable` stream from a `ByteArray`, `ByteBuffer`, or UTF-8 `String`. |

#### `Writable` Key Methods & Factories

| Method / Property | Signature / Return Type | Description |
| :--- | :--- | :--- |
| `write` (`ByteArray` / `ByteBuffer`) | `suspend fun write(chunk: ByteArray, offset: Int = 0, length: Int = chunk.size - offset): Boolean`<br/>`suspend fun write(buffer: ByteBuffer): Boolean` | Asynchronously writes bytes or a `ByteBuffer` to the underlying sink, notifying `onDrain` listeners when `highWaterMark` is reached. |
| `writeText` / `writeLine` | `suspend fun writeText(text: String): Boolean`<br/>`suspend fun writeLine(line: String = ""): Boolean` | Asynchronously encodes and writes UTF-8 text (or a newline-terminated line). |
| `flush` | `suspend fun flush()` | Asynchronously flushes buffered data to the native sink. |
| `end` / `endText` | `suspend fun end(finalChunk: ByteArray? = null)`<br/>`suspend fun end(finalBuffer: ByteBuffer)`<br/>`suspend fun endText(finalText: String)` | Optionally writes a final payload and asynchronously finalizes the stream, firing `onFinish` listeners. |
| `close` | `suspend fun close()` | Ends (if not already finished) and closes the underlying native sink. |
| `onDrain` / `onFinish` / `onError` / `onClose` | Event listener registration | NodeJS-style writable lifecycle hooks. |
| `Writable.toMemory` / `Writable.nullSink` | Factory methods | Creates an in-memory `Writable` buffer (with optional `onCommit` callback and `toByteArray()`) or a no-op null sink. |

#### `StreamUtil` Pipeline & Conversion Helpers

| Method | Signature | Description |
| :--- | :--- | :--- |
| `StreamUtil.pipeline` | `suspend fun pipeline(source: Readable, destination: Writable, closeBoth: Boolean = true): Long` | Pipes `source` to `destination` until EOF and guarantees both streams are closed in `finally` when `closeBoth = true`. |
| `StreamUtil.copy` | `suspend fun copy(source: Readable, destination: Writable, chunkSize: Int = 65536): Long` | Copies chunks from `source` to `destination` without closing either stream. |
| `StreamUtil.transform` | `suspend fun transform(source: Readable, destination: Writable, transformer: suspend (ByteArray) -> ByteArray): Long` | Streams chunks from `source`, transforms each chunk via `transformer`, writes to `destination`, and closes both ends. |
| `StreamUtil.toByteArray` / `toByteBuffer` / `toText` | `suspend fun toByteArray(source: Readable): ByteArray`<br/>`suspend fun toByteBuffer(source: Readable): ByteBuffer`<br/>`suspend fun toText(source: Readable): String` | Drains `source` into a `ByteArray`, `ByteBuffer`, or UTF-8 `String` and closes `source`. |
| `StreamUtil.fromByteArray` / `fromByteBuffer` / `fromString` | Factory helpers | Wraps a `ByteArray`, `ByteBuffer`, or `String` in a `Readable`. |

---

### 1.3 `IFileSystem` & `FileSystemAvailability` (`com.nexustools.genericui.io.*`)

- **Sources**:
  - [`IFileSystem.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/io/IFileSystem.kt) (`interface IFileSystem`)
  - [`FileSystemAvailability.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/io/FileSystemAvailability.kt) (`enum class FileSystemAvailability { Always, WithPermission, Unavailable }`)
- **Implemented By**: [`FileSystem`](../common/src/commonMain/kotlin/com/nexustools/genericui/io/FileSystem.kt), [`UserDirectory`](../common/src/commonMain/kotlin/com/nexustools/genericui/util/UserDirectory.kt), [`ConfigDirectory`](../common/src/commonMain/kotlin/com/nexustools/genericui/util/ConfigDirectory.kt), and [`CacheDirectory`](../common/src/commonMain/kotlin/com/nexustools/genericui/util/CacheDirectory.kt).

#### `FileSystemAvailability` Enum Values

| Enum Entry | Meaning |
| :--- | :--- |
| **`FileSystemAvailability.Always`** | Operations succeed immediately without interactive permission prompts (all `UserDirectory` / `ConfigDirectory` / `CacheDirectory` instances across all 8 backends, Desktop `FileSystem`, and app-sandbox paths on Mobile). |
| **`FileSystemAvailability.WithPermission`** | Operations require an OS runtime permission grant or interactive user file/directory picker (`FileSystem` on `:mobile:android` SAF, `:mobile:ios` `UIDocumentPickerViewController`, and `:web` File System Access API / `<input type="file">`). |
| **`FileSystemAvailability.Unavailable`** | Direct arbitrary path operations are unavailable in the current environment. |

#### Shared `IFileSystem` Contract

| Method | Signature | Description |
| :--- | :--- | :--- |
| `isAvailable()` | `fun isAvailable(): FileSystemAvailability` | Reports `Always`, `WithPermission`, or `Unavailable`. |
| `resolve` | `fun resolve(vararg segments: String): String` | Resolves a normalized path from one or more path segments relative to the file system root or `UserDirectory`. |
| `openDatabase` | `fun openDatabase(name: String = "database.db", version: Int = 1): Database` | Opens a structured [`Database`](#16-database-comnexustoolsgenericuiutildatabase--typealias-in-comnexustoolsgenericuiio) resolved inside this `IFileSystem` (`FileSystem`, `ConfigDirectory`, or `CacheDirectory`). |
| `read` | `suspend fun read(path: String): ByteBuffer?` | Asynchronously reads the file at `path` into a `ByteBuffer`, or returns `null` if not found. |
| `write` (`ByteBuffer` / `ByteArray` / `Readable`) | `suspend fun write(path: String, buffer: ByteBuffer, append: Boolean = false): Boolean`<br/>`suspend fun write(path: String, bytes: ByteArray, append: Boolean = false): Boolean`<br/>`suspend fun write(path: String, readable: Readable, append: Boolean = false): Boolean` | Asynchronously writes a `ByteBuffer`, `ByteArray`, or `Readable` stream to `path` (overwriting or appending when `append = true`). |
| `openReadable` | `suspend fun openReadable(path: String): Readable?` | Asynchronously opens a non-blocking `Readable` stream for `path`, or `null` if the file does not exist. |
| `openWritable` | `suspend fun openWritable(path: String, append: Boolean = false): Writable` | Asynchronously opens a non-blocking `Writable` stream for `path` (truncating or appending when `append = true`). |

---

### 1.4 `FileSystem` (`object FileSystem : IFileSystem`)

- **Source**: [`common/src/commonMain/kotlin/com/nexustools/genericui/io/FileSystem.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/io/FileSystem.kt)
- **Native Contract**: [`FileSystemNativeImpl`](../common/src/commonMain/kotlin/com/nexustools/genericui/nativeimpl/io/FileSystemNativeImpl.kt)
- **Design**:
  - NodeJS `fs.promises`-like 100% asynchronous, non-blocking cross-platform OS/browser file system singleton implementing `IFileSystem`.
  - Operates strictly on real OS/browser file handles and paths (**no `indexdb://` scheme**).
  - On **`:web`**, `FileSystem` uses the browser **File System Access API** (`window.showOpenFilePicker`, `window.showSaveFilePicker`, `window.showDirectoryPicker`, `FileSystemFileHandle.getFile()`, `FileSystemFileHandle.createWritable()`) with automatic fallback to `<input type="file">` and `<a download>` Blob downloads when the File System Access API is unsupported by the browser.

#### `FileSystem` Extended Methods (in addition to `IFileSystem`)

| Method | Signature | Description |
| :--- | :--- | :--- |
| `openReadableFromFileDialog` / `openReadablesFromFileDialog` | `suspend fun openReadableFromFileDialog(title, initialDirectory, filters): Pair<String, Readable>?`<br/>`suspend fun openReadablesFromFileDialog(title, initialDirectory, filters): List<Pair<String, Readable>>` | Opens a native/browser file-open picker and returns the selected path(s) paired with a non-blocking `Readable` stream. |
| `openWritableFromFileDialog` | `suspend fun openWritableFromFileDialog(title, initialDirectory, suggestedFileName, filters): Pair<String, Writable>?` | Opens a native/browser file-save picker and returns the selected path paired with a non-blocking `Writable` stream. |
| `createReadStream` / `createWriteStream` | `suspend fun createReadStream(path: String): Readable?`<br/>`suspend fun createWriteStream(path: String, append: Boolean = false): Writable`<br/>`suspend fun createWriteStream(path: String, mode: FileOpenMode): Writable` | Opens a `Readable` or `Writable` stream for `path`. |
| `readFile` / `readTextFile` | `suspend fun readFile(path: String): ByteArray?`<br/>`suspend fun readTextFile(path: String): String?` | Asynchronously reads all bytes or UTF-8 text from `path`. |
| `writeFile` / `writeTextFile` / `appendFile` / `appendTextFile` | `suspend fun writeFile(path: String, bytes: ByteArray): Boolean`<br/>`suspend fun writeTextFile(path: String, text: String): Boolean` | Asynchronously writes or appends raw bytes or UTF-8 text to `path`. |
| `exists` / `stat` | `suspend fun exists(path: String): Boolean`<br/>`suspend fun stat(path: String): FileStat?` | Checks existence or retrieves [`FileStat`](../common/src/commonMain/kotlin/com/nexustools/genericui/io/FileStat.kt) (`size`, `isFile`, `isDirectory`, `modifiedTimeMs`). |
| `readdir` / `mkdir` | `suspend fun readdir(path: String): List<String>`<br/>`suspend fun mkdir(path: String, recursive: Boolean = true): Boolean` | Lists directory entries or creates a directory (recursively by default). |
| `unlink` / `rmdir` / `rm` | `suspend fun unlink(path: String): Boolean`<br/>`suspend fun rmdir(path: String, recursive: Boolean = false): Boolean` | Deletes a file or removes a directory. |
| `rename` / `copyFile` | `suspend fun rename(oldPath: String, newPath: String): Boolean`<br/>`suspend fun copyFile(srcPath: String, dstPath: String, overwrite: Boolean = true): Boolean` | Atomically renames/moves or copies a file. |
| `hasPermission` / `requestPermission` / `requestDirectoryAccess` | Permission & SAF/directory picker helpers | Queries or requests read/write access on platforms with scoped storage (`FilePermissionStatus`). |

---

### 1.5 `UserDirectory`, `ConfigDirectory`, & `CacheDirectory` (`: UserDirectory : IFileSystem, Disposable`)

- **Sources**:
  - [`UserDirectory.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/util/UserDirectory.kt) (`abstract class UserDirectory : IFileSystem, Disposable`)
  - [`ConfigDirectory.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/util/ConfigDirectory.kt) (`open class ConfigDirectory : UserDirectory`)
  - [`CacheDirectory.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/util/CacheDirectory.kt) (`class CacheDirectory : ConfigDirectory`)
- **Architecture Across System Targets vs. `:web`**:
  - **`isAvailable()`**: Always returns `FileSystemAvailability.Always` across all 8 backends.
  - **System Targets (`windows`, `macos`, `gtk`, `qt`, `swing`, `android`, `ios`)**: Provide their base OS directory filepath (`directoryPath`) and delegate `IFileSystem` operations (`read`, `write`, `openReadable`, `openWritable`, `resolve`, `openDatabase`) to `FileSystem`.
  - **Web (`:web`)**: `ConfigDirectory` and `CacheDirectory` (`WebUserDirectoryNativeImpl`) use browser `window.indexedDB` **directly** (database `"genericui_user_dirs:<appName>"`, object stores `"config"` and `"cache"`) **without** using the `Database` class, while `PreferenceStore` remains on `window.localStorage`.

#### Per-Backend `ConfigDirectory` & `CacheDirectory` Implementation Matrix

| Backend Module | `ConfigDirectory` Native Impl & Backing | `CacheDirectory` Native Impl & Backing |
| :--- | :--- | :--- |
| **`:desktop:windows`** | `WindowsConfigDirectoryImpl` $\rightarrow$ `%APPDATA%\<appName>[\<subdir>]` (delegates `IFileSystem` to `FileSystem`) | `WindowsCacheDirectoryImpl` $\rightarrow$ `%LOCALAPPDATA%\<appName>\Cache[\<subdir>]` (delegates `IFileSystem` to `FileSystem`) |
| **`:desktop:macos`** | `MacosConfigDirectoryImpl` $\rightarrow$ `$XDG_CONFIG_HOME/<appName>` or `~/.config/<appName>` (delegates `IFileSystem` to `FileSystem`) | `MacosCacheDirectoryImpl` $\rightarrow$ `~/Library/Caches/<appName>[/<subdir>]` (delegates `IFileSystem` to `FileSystem`) |
| **`:desktop:gtk` & `:desktop:qt`** (`:desktop:linuxCommon`) | `LinuxXdgConfigDirectoryImpl` $\rightarrow$ `$XDG_CONFIG_HOME/<appName>` or `~/.config/<appName>` (delegates `IFileSystem` to `FileSystem`) | `LinuxXdgCacheDirectoryImpl` $\rightarrow$ `$XDG_CACHE_HOME/<appName>` or `~/.cache/<appName>` (delegates `IFileSystem` to `FileSystem`) |
| **`:desktop:swing`** | `SwingUserDirectoryNativeImpl(isCache = false)` $\rightarrow$ Host OS config directory (`java.nio.channels.AsynchronousFileChannel` / `FileSystem`) | `SwingUserDirectoryNativeImpl(isCache = true)` $\rightarrow$ Host OS cache directory (`java.nio.channels.AsynchronousFileChannel` / `FileSystem`) |
| **`:mobile:android`** | `AndroidUserDirectoryNativeImpl(isCache = false)` $\rightarrow$ `<context.filesDir>/<appName>` (delegates `IFileSystem` to `FileSystem`) | `AndroidUserDirectoryNativeImpl(isCache = true)` $\rightarrow$ `<context.cacheDir>/<appName>` (delegates `IFileSystem` to `FileSystem`) |
| **`:mobile:ios`** | `IosUserDirectoryNativeImpl(isCache = false)` $\rightarrow$ `<Home>/Library/Application Support/<appName>` (delegates `IFileSystem` to `FileSystem`) | `IosUserDirectoryNativeImpl(isCache = true)` $\rightarrow$ `<Home>/Library/Caches/<appName>` (delegates `IFileSystem` to `FileSystem`) |
| **`:web`** | `WebUserDirectoryNativeImpl(scheme = "config")` $\rightarrow$ Browser `window.indexedDB` **directly** (`"genericui_user_dirs:<appName>"`, store `"config"`, **not** using `Database`) | `WebUserDirectoryNativeImpl(scheme = "cache")` $\rightarrow$ Browser `window.indexedDB` **directly** (`"genericui_user_dirs:<appName>"`, store `"cache"`, **not** using `Database`) |

```kotlin
import com.nexustools.genericui.io.ByteBuffer
import com.nexustools.genericui.util.CacheDirectory
import com.nexustools.genericui.util.ConfigDirectory

val configDir = ConfigDirectory()
val cacheDir = CacheDirectory(subdirectory = "thumbnails")

// 100% async non-blocking IFileSystem operations on ConfigDirectory & CacheDirectory:
configDir.write("workspace/layout.json", ByteBuffer.fromString("""{"dock":"left"}"""))
val buffer: ByteBuffer? = configDir.read("workspace/layout.json")

// Stream large files into CacheDirectory without blocking the UI thread:
cacheDir.openWritable("render_preview.bin").use { writable ->
    writable.writeText("chunk-1")
    writable.end()
}
```

---

### 1.6 `Database` (`com.nexustools.genericui.util.Database` + `typealias` in `com.nexustools.genericui.io`)

- **Sources**:
  - [`common/src/commonMain/kotlin/com/nexustools/genericui/util/Database.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/util/Database.kt) (`class Database : Disposable`)
  - [`common/src/commonMain/kotlin/com/nexustools/genericui/io/DatabaseAlias.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/io/DatabaseAlias.kt) (`typealias Database = com.nexustools.genericui.util.Database`)
  - Structured AST Models (`com.nexustools.genericui.util.db.*`):
    - [`DatabaseTableSchema`](../common/src/commonMain/kotlin/com/nexustools/genericui/util/db/DatabaseTableSchema.kt), [`DatabaseColumnSpec`](../common/src/commonMain/kotlin/com/nexustools/genericui/util/db/DatabaseColumnSpec.kt), [`DatabaseColumnType`](../common/src/commonMain/kotlin/com/nexustools/genericui/util/db/DatabaseColumnType.kt) (`STRING`, `INT64`, `DOUBLE`, `BOOLEAN`, `BLOB`)
    - [`DatabaseRecord`](../common/src/commonMain/kotlin/com/nexustools/genericui/util/db/DatabaseRecord.kt), [`DatabaseValue`](../common/src/commonMain/kotlin/com/nexustools/genericui/util/db/DatabaseValue.kt)
    - [`DatabaseQuery`](../common/src/commonMain/kotlin/com/nexustools/genericui/util/db/DatabaseQuery.kt), [`DatabaseFilter`](../common/src/commonMain/kotlin/com/nexustools/genericui/util/db/DatabaseFilter.kt), [`DatabaseComparisonOp`](../common/src/commonMain/kotlin/com/nexustools/genericui/util/db/DatabaseComparisonOp.kt) (`EQUALS`, `NOT_EQUALS`, `LESS_THAN`, `LESS_THAN_OR_EQUAL`, `GREATER_THAN`, `GREATER_THAN_OR_EQUAL`, `STARTS_WITH`, `CONTAINS`, `IN_LIST`, `BETWEEN`, `IS_NULL`, `IS_NOT_NULL`), [`DatabaseLogicalOp`](../common/src/commonMain/kotlin/com/nexustools/genericui/util/db/DatabaseLogicalOp.kt) (`AND`, `OR`), [`DatabaseSortSpec`](../common/src/commonMain/kotlin/com/nexustools/genericui/util/db/DatabaseSortSpec.kt), [`DatabaseSortDirection`](../common/src/commonMain/kotlin/com/nexustools/genericui/util/db/DatabaseSortDirection.kt) (`ASCENDING`, `DESCENDING`)
- **Zero-Parsing Structured Query AST**:
  - `Database` uses an enum/class-based structured query & filter AST — **no raw SQL strings or string-parsed queries are ever accepted by the public API**.
  - SQLite backends translate `DatabaseTableSchema`, `DatabaseQuery`, and `DatabaseFilter` directly into parameterized prepared statements via [`DatabaseQuerySqlTranslator`](../common/src/commonMain/kotlin/com/nexustools/genericui/util/db/DatabaseQuerySqlTranslator.kt), while `:web` evaluates the AST directly against `IDBObjectStore` records.
- **Opening a `Database` (Explicit `filePath` or `fileSystem.openDatabase`)**:
  - Via any `IFileSystem` (`FileSystem`, `ConfigDirectory`, or `CacheDirectory`): `val db = configDir.openDatabase("projects.db", version = 1)`
  - Directly with an explicit `filePath` (or `setFilePath(path)`): `val db = Database(databaseName = "projects", filePath = "/path/to/projects.db", version = 1)`

#### Per-Backend Native `Database` Engines

| Backend Module | Native `DatabaseNativeImpl` | Underlying Database Engine |
| :--- | :--- | :--- |
| **`:desktop:qt`** | `QtDatabaseNativeImpl` | **Qt 6 `QtSql`** (`QSqlDatabase::addDatabase("QSQLITE", connectionName)`, `QSqlQuery` with positional bind values) |
| **`:desktop:windows`** | `WindowsDatabaseNativeImpl` | Native C **`libsqlite3`** (`sqlite3_open_v2`, `sqlite3_prepare_v2`, `sqlite3_bind_*`, `sqlite3_step`) |
| **`:desktop:macos`** | `MacosDatabaseNativeImpl` | System **`libsqlite3`** (`/usr/lib/libsqlite3.dylib` via C/Objective-C interop) |
| **`:desktop:gtk`** | `GtkDatabaseNativeImpl` | System **`libsqlite3`** (`sqlite3` C API on background GLib worker thread) |
| **`:desktop:swing`** | `SwingDatabaseNativeImpl` | **`org.xerial:sqlite-jdbc`** (`jdbc:sqlite:<filePath>` `PreparedStatement` on `Dispatchers.IO`) |
| **`:mobile:android`** | `AndroidDatabaseNativeImpl` | **`androidx.sqlite:sqlite-ktx`** / `android.database.sqlite.SQLiteDatabase` (`openOrCreateDatabase`) |
| **`:mobile:ios`** | `IosDatabaseNativeImpl` | iOS system **`libsqlite3`** (`sqlite3` C API) |
| **`:web`** | `WebDatabaseNativeImpl` | Browser **`window.indexedDB`** isolated in `"genericui_db:<databaseName>:<filePath>"` with `"tbl:<tableName>"` object stores so it **never** conflicts with `ConfigDirectory` or `CacheDirectory` (`"genericui_user_dirs:<appName>"`) |

```kotlin
import com.nexustools.genericui.util.ConfigDirectory
import com.nexustools.genericui.util.db.DatabaseColumnSpec
import com.nexustools.genericui.util.db.DatabaseColumnType
import com.nexustools.genericui.util.db.DatabaseFilter
import com.nexustools.genericui.util.db.DatabaseQuery
import com.nexustools.genericui.util.db.DatabaseRecord
import com.nexustools.genericui.util.db.DatabaseSortDirection
import com.nexustools.genericui.util.db.DatabaseTableSchema

val db = ConfigDirectory().openDatabase("studio_catalog.db", version = 1)

// 1. Define a structured table schema (zero raw SQL):
db.defineTable(
    DatabaseTableSchema(
        tableName = "assets",
        primaryKeyColumn = "key",
        columns = listOf(
            DatabaseColumnSpec("key", DatabaseColumnType.STRING, primaryKey = true, nullable = false),
            DatabaseColumnSpec("title", DatabaseColumnType.STRING, indexed = true),
            DatabaseColumnSpec("sizeBytes", DatabaseColumnType.INT64, indexed = true),
            DatabaseColumnSpec("favorite", DatabaseColumnType.BOOLEAN)
        )
    )
)

// 2. Insert or update structured records:
db.putRecord(
    table = "assets",
    record = DatabaseRecord.of(
        key = "asset_01",
        "title" to "Hero Sprite Sheet",
        "sizeBytes" to 245_760L,
        "favorite" to true
    )
)

// 3. Query with the enum/class-based AST (no string query parsing):
val results: List<DatabaseRecord> = db.query(
    DatabaseQuery.from("assets")
        .where(
            DatabaseFilter.eq("favorite", true) and
                DatabaseFilter.gte("sizeBytes", 100_000L) and
                DatabaseFilter.contains("title", "Sprite")
        )
        .orderBy("sizeBytes", DatabaseSortDirection.DESCENDING)
        .limit(25)
)
```

---

### 1.7 `ImageLoader` & `ImageCacheManager` (`CacheDirectory` on System Targets; Pass-Through on `:web`)

- **Sources**:
  - [`ImageLoader.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/image/ImageLoader.kt) (`object ImageLoader`)
  - [`ImageCacheManager.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/image/cache/ImageCacheManager.kt) (`object ImageCacheManager`)
  - [`ImageCacheEntry.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/image/cache/ImageCacheEntry.kt) (`data class ImageCacheEntry`)
- **Platform Behavior**:
  - **System Targets (`windows`, `macos`, `gtk`, `qt`, `swing`, `android`, `ios`)**: `ImageCacheManager` persists fetched/decoded image files inside `CacheDirectory(appName = GenericUI.applicationName, subdirectory = "images")` (`<userCacheDir>/<appName>/images`) using non-blocking `suspend` `IFileSystem` methods (`fetchAndCacheBuffer(url): ByteBuffer?`) alongside an in-memory LRU `ImageBuffer` cache (`maxMemoryEntries = 128`).
  - **Web (`:web`)**: `ImageCacheManager.isWebBrowserManaged` is `true`; the browser natively manages HTTP image loading and caching, so `ImageCacheManager` acts as a zero-overhead pass-through no-op without writing to `CacheDirectory` (`cachedFileCount == 0`, `cachedDiskByteSize == 0L`).

---

## 2. 100% Async Non-Blocking Network, HTTP, & Socket.IO APIs (`com.nexustools.genericui.net.*`)

All networking APIs in GenericUI are **100% asynchronous, non-blocking Kotlin `suspend` functions** backed by native OS sockets/HTTP clients on system targets and browser `fetch()` / `WebSocket` on `:web`.

### 2.1 `Network`, `TcpSocket : Stream` (`TcpClient`), `UdpSocket : Stream` (`UdpClient`), & `UdpDatagram`

- **Sources**:
  - [`Network.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/net/Network.kt) (`object Network`)
  - [`TcpSocket.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/net/TcpSocket.kt) (`class TcpSocket : Stream` & [`TcpClient.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/net/TcpClient.kt) `typealias TcpClient = TcpSocket`)
  - [`UdpSocket.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/net/UdpSocket.kt) (`class UdpSocket : Stream` & [`UdpClient.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/net/UdpClient.kt) `typealias UdpClient = UdpSocket`)
  - [`UdpDatagram.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/net/UdpDatagram.kt) (`data class UdpDatagram(val bytes: ByteArray, val host: String, val port: Int)`)

#### `Network` Capabilities & Factories

| Method | Signature | Description |
| :--- | :--- | :--- |
| `Network.supportsTcp()` | `fun supportsTcp(): Boolean` | Returns `true` on all 7 system targets (`windows`, `macos`, `gtk`, `qt`, `swing`, `android`, `ios`) and **`false` on `:web`** (where browsers disallow raw TCP sockets). |
| `Network.supportsUdp()` | `fun supportsUdp(): Boolean` | Returns `true` on all 7 system targets and **`false` on `:web`**. |
| `Network.supportsMulticastUdp()` | `fun supportsMulticastUdp(): Boolean` | Returns `true` when UDP multicast group join/leave is supported (`false` on `:web`). |
| `Network.openTcpSocket(...)` / `connectTcp(...)` | `suspend fun openTcpSocket(host: String, port: Int, timeoutMs: Long = 10_000L, tls: Boolean = false): TcpSocket?` | Creates and asynchronously connects a duplex `TcpSocket : Stream` (with optional TLS), or returns `null` if unsupported/unreachable. |
| `Network.openUdpSocket(...)` | `suspend fun openUdpSocket(localHost: String = "0.0.0.0", localPort: Int = 0, remoteHost: String? = null, remotePort: Int = 0): UdpSocket?` | Creates and asynchronously binds a duplex `UdpSocket : Stream` (optionally setting a default remote peer), or returns `null` if unsupported. |
| `Network.createTcpSocket()` / `createTcpClient()` | `fun createTcpSocket(): TcpSocket` | Creates a new unconnected non-blocking `TcpSocket : Stream`. |
| `Network.createUdpSocket()` / `createUdpClient()` | `fun createUdpSocket(): UdpSocket` | Creates a new unbound non-blocking `UdpSocket : Stream`. |

#### `TcpSocket` (`TcpClient`) & `UdpSocket` (`UdpClient`) Key Methods

| Class | Inheritance | Key Methods & Properties |
| :--- | :--- | :--- |
| **`TcpSocket` (`TcpClient`)** | `: Stream` (`Readable`, `Writable`) | `isConnected`, `remoteHost`, `remotePort`, `localPort`, `readable`, `writable`, `supports()`, `suspend fun connect(host, port, timeoutMs = 10_000L, tls = false): Boolean`, `suspend fun read(maxBytes = 65536): ByteArray?`, `suspend fun readBuffer(maxBytes): ByteBuffer?`, `suspend fun readText(maxBytes): String?`, `suspend fun write(bytes / buffer)` / `writeText(text)` / `writeLine(line)`, `suspend fun flush()`, `suspend fun close()`. |
| **`UdpSocket` (`UdpClient`)** | `: Stream` (`Readable`, `Writable`) | `isBound`, `localAddress`, `localPort`, `defaultRemoteHost`, `defaultRemotePort`, `broadcastEnabled`, `supports()`, `supportsMulticast()`, `fun connectPeer(remoteHost, remotePort)`, `suspend fun bind(localHost = "0.0.0.0", localPort = 0): Boolean`, `suspend fun joinMulticastGroup(multicastAddress, interfaceName = null): Boolean`, `suspend fun leaveMulticastGroup(multicastAddress, interfaceName = null): Boolean`, `suspend fun send(bytes / buffer, host, port): Int`, `suspend fun sendText(text, host, port): Int`, `suspend fun receive(maxBytes = 65536, timeoutMs = 0L): UdpDatagram?`, plus `Stream` `read`/`write` against the configured remote peer, `suspend fun close()`. |

---

### 2.2 `HttpClient`, `HttpConnection : Stream`, `HttpRequest : Writable`, `HttpResponse : Readable`, & `HttpMethod` (`com.nexustools.genericui.net.http.*`)

- **Sources**:
  - [`HttpClient.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/net/http/HttpClient.kt) (`class HttpClient : Disposable`)
  - [`HttpConnection.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/net/http/HttpConnection.kt) (`class HttpConnection : Stream`)
  - [`HttpRequest.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/net/http/HttpRequest.kt) (`class HttpRequest : Writable`)
  - [`HttpResponse.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/net/http/HttpResponse.kt) (`class HttpResponse : Readable`)
  - [`HttpMethod.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/net/http/HttpMethod.kt) (`enum class HttpMethod { GET, POST, PUT, PATCH, DELETE, HEAD, OPTIONS }`)
- **Purpose**: 100% asynchronous, non-blocking HTTP/HTTPS client and streaming connection architecture across all 8 backends (`WinHTTP` on Windows, `NSURLSession` on macOS/iOS, `libsoup`/`libcurl` on GTK, `QNetworkAccessManager` on Qt6, `java.net.http.HttpClient` on Swing/Android, and `window.fetch()` on `:web`). `HttpRequest` is a `Writable` stream for streaming request bodies, `HttpResponse` is a `Readable` stream for streaming response bodies, and `HttpConnection` is a full-duplex `Stream` combining both.

| Method | Signature | Description |
| :--- | :--- | :--- |
| `openConnection` | `fun openConnection(url: String, method: HttpMethod = HttpMethod.GET, headers: Map<String, String> = emptyMap(), timeoutMs: Long = defaultTimeoutMs): HttpConnection` | Opens a duplex `HttpConnection : Stream` where writes stream into `connection.request` (`HttpRequest : Writable`) and reads stream from `connection.awaitResponse()` (`HttpResponse : Readable`). |
| `get` / `getText` / `getBytes` / `getBuffer` | `suspend fun get(url, headers): HttpResponse`<br/>`suspend fun getText(url, headers): String`<br/>`suspend fun getBytes(url, headers): ByteArray`<br/>`suspend fun getBuffer(url, headers): ByteBuffer` | Performs an async `GET` request returning the `HttpResponse : Readable`, UTF-8 `bodyText`, `bodyBytes`, or `bodyBuffer` (`ByteBuffer`). |
| `post` / `postText` / `postBuffer` | `suspend fun post(url, body: ByteArray?, contentType, headers): HttpResponse`<br/>`suspend fun postText(url, body: String, contentType, headers): HttpResponse`<br/>`suspend fun postBuffer(url, body: ByteBuffer, contentType, headers): HttpResponse` | Performs an async `POST` request with raw bytes, UTF-8 text, or a `ByteBuffer`. |
| `put` / `patch` / `delete` / `head` | `suspend fun put(...)` / `patch(...)` / `delete(...)` / `head(...)` | Performs an async `PUT`, `PATCH`, `DELETE`, or `HEAD` request. |
| `request` | `suspend fun request(request: HttpRequest): HttpResponse` | Executes a custom `HttpRequest : Writable` (`url`, `method`, `headers`, `bodyBytes`, `timeoutMs`). |
| `openStream` | `suspend fun openStream(request: HttpRequest): Readable?` | Opens a non-blocking streaming HTTP response as a `Readable`. |
| `HttpResponse` (`: Readable`) | `statusCode: Int`, `isSuccess: Boolean` (`200..299`), `headers: Map<String, String>`, `bodyBytes: ByteArray`, `bodyBuffer: ByteBuffer`, `bodyText: String`, plus all `Readable` methods (`read`, `readBuffer`, `readAllBytes`, `readAllText`) | Structured HTTP response that is also directly a `Readable` stream. |

---

### 2.3 `SocketIoClient`, `SocketIoOptions`, & `SocketIoParser` (`com.nexustools.genericui.net.socketio.*`)

- **Sources**:
  - [`SocketIoClient.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/net/socketio/SocketIoClient.kt) (`class SocketIoClient : Disposable`)
  - [`SocketIoOptions.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/net/socketio/SocketIoOptions.kt) (`data class SocketIoOptions`)
  - [`SocketIoParser.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/net/socketio/SocketIoParser.kt) (`object SocketIoParser`)
- **Purpose**: 100% asynchronous, non-blocking **Socket.IO v4 / Engine.IO v4** client operating over the active backend's [`WebSocketNativeImpl`](../common/src/commonMain/kotlin/com/nexustools/genericui/nativeimpl/net/WebSocketNativeImpl.kt) transport (`WinHTTP` WebSocket on Windows, `NSURLSessionWebSocketTask` on macOS/iOS, `SoupWebsocketConnection` on GTK, `QWebSocket` on Qt6, `java.net.http.WebSocket` on Swing/Android, and browser `window.WebSocket` on `:web`). Implements the full Engine.IO v4 open handshake (`0{...}` $\rightarrow$ `40`), automatic heartbeat ping/pong (`2` $\rightarrow$ `3`), namespace routing (`40/ns,`), event emission/subscription (`42["event", ...]`), and coroutine acknowledgement callbacks (`43<id>[...]`).

| Method / Factory | Signature | Description |
| :--- | :--- | :--- |
| `SocketIoClient.io` | `fun io(url: String, options: SocketIoOptions = SocketIoOptions()): SocketIoClient` | Creates a `SocketIoClient` configured with `namespace`, `path` (`"/socket.io/"`), `query`, `headers`, `auth`, `reconnection`, and `timeoutMs`. |
| `connect()` / `disconnect()` | `suspend fun connect(): Boolean`<br/>`suspend fun disconnect()` | Asynchronously establishes the WebSocket + Engine.IO v4 / Socket.IO v4 namespace connection or cleanly disconnects (`41`). |
| `on` / `once` / `off` | `fun on(event: String, listener: (List<String>) -> Unit): SocketIoClient`<br/>`fun once(event: String, listener: (List<String>) -> Unit): SocketIoClient`<br/>`fun off(event: String)` | Subscribes (persistently or one-shot) or unsubscribes from a named Socket.IO event. |
| `emit` | `suspend fun emit(event: String, vararg args: Any?): Boolean` | Asynchronously encodes and sends a Socket.IO `EVENT` frame (`42[...]`). |
| `emitWithAck` | `suspend fun emitWithAck(event: String, vararg args: Any?, timeoutMs: Long = options.timeoutMs): List<String>?` | Asynchronously emits an event with an acknowledgement ID and suspends until the server responds with `ACK` (`43<id>[...]`) or `timeoutMs` elapses. |
| `onConnect` / `onDisconnect` / `onError` / `onReconnectAttempt` | Lifecycle callbacks | Registers callbacks for Socket.IO namespace connect, disconnect reason, transport error, and reconnect attempts. |

```kotlin
import com.nexustools.genericui.net.socketio.SocketIoClient
import com.nexustools.genericui.net.socketio.SocketIoOptions

val socket = SocketIoClient.io(
    url = "https://realtime.example.com",
    options = SocketIoOptions(namespace = "/studio", query = mapOf("room" to "alpha"))
)

socket.onConnect {
    println("Connected with Socket.IO sid=${socket.socketId}")
}
socket.on("cursorMoved") { args ->
    println("Remote cursor update: $args")
}

if (socket.connect()) {
    socket.emit("joinStage", mapOf("user" to "Kara"))
    val ackArgs = socket.emitWithAck("syncState", "layer-1")
}
```

---

## 3. Binary Serialization Utilities (`com.nexustools.genericui.util.*`)

### 3.1 `BinaryJson` (`.bson` Binary JSON Codec)

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

#### Key Methods (Streaming `Readable` / `Writable` & Byte Array Helpers)

| Method | Description |
| :--- | :--- |
| `BinaryJson.encode(value: Any?, writable: Writable)` | Converts `value` to a `PreferenceElement` via `PreferenceObjectCodec` and writes `.bson` binary bytes directly into `writable` (`WritableByteBuffer`, file `Writable`, or socket). |
| `BinaryJson.encodeElement(element: PreferenceElement, writable: Writable)` | Encodes a `PreferenceElement` AST directly into `writable`. |
| `suspend fun BinaryJson.decode<T : Any>(readable: Readable, type: KClass<T>): T?`<br/>`suspend inline fun <reified T : Any> BinaryJson.decode(readable: Readable): T?` | Asynchronously reads `.bson` binary bytes from `readable` (`ReadableByteBuffer`, file `Readable`, or socket) and deserializes into `T`. |
| `suspend fun BinaryJson.decodeElement(readable: Readable): PreferenceElement` | Asynchronously decodes a `PreferenceElement` AST from `readable`. |
| `BinaryJson.encodeToBytes(value: Any?): ByteArray`<br/>`BinaryJson.encodeElement(element: PreferenceElement): ByteArray` | Convenience helpers that encode into an in-memory `WritableByteBuffer` and return `toByteArray()`. |
| `BinaryJson.decodeFromBytes<T : Any>(bytes: ByteArray, type: KClass<T>): T?`<br/>`BinaryJson.decodeElement(bytes: ByteArray): PreferenceElement` | Convenience helpers that decode from an in-memory `ReadableByteBuffer`. |

```kotlin
import com.nexustools.genericui.io.ReadableByteBuffer
import com.nexustools.genericui.io.WritableByteBuffer
import com.nexustools.genericui.util.BinaryJson

data class ViewportConfig(val zoom: Double = 1.0, val showGrid: Boolean = true, val tags: List<String> = emptyList())

val writable = WritableByteBuffer()
BinaryJson.encode(ViewportConfig(zoom = 2.5, showGrid = false, tags = listOf("ui", "vector")), writable)
val restored: ViewportConfig? = BinaryJson.decode<ViewportConfig>(ReadableByteBuffer(writable.toByteBuffer()))
```

---

### 3.2 `BinaryPlist` (Apple `bplist00` Binary Property List Codec)

- **Source**: [`common/src/commonMain/kotlin/com/nexustools/genericui/util/BinaryPlist.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/util/BinaryPlist.kt)
- **Purpose**: Pure Kotlin Multiplatform streaming encoder and decoder for Apple's **`bplist00`** binary property list format (compatible with macOS/iOS `NSPropertyListSerialization` and `plutil`). Strictly supports binary `bplist00` property lists (no XML plist dependency).
- **Used By**: `PreferenceStore` on **macOS (`:desktop:macos`)**, **iOS (`:mobile:ios`)**, and **JVM Swing on macOS (`:desktop:swing`)** to persist per-key `.plist` files (`<configDir>/<appName>/<key>.plist`).

#### Key Methods (Streaming `Readable` / `Writable` & Byte Array Helpers)

| Method | Description |
| :--- | :--- |
| `BinaryPlist.encode(value: Any?, writable: Writable)` | Converts `value` to a `PreferenceElement` and writes an Apple `bplist00` binary property list directly into `writable`. |
| `BinaryPlist.encodeElement(root: PreferenceElement, writable: Writable)` | Encodes a `PreferenceElement` AST directly into `writable` in `bplist00` format (with automatic string key deduplication). |
| `suspend fun BinaryPlist.decode<T : Any>(readable: Readable, type: KClass<T>): T?`<br/>`suspend inline fun <reified T : Any> BinaryPlist.decode(readable: Readable): T?` | Asynchronously reads a `bplist00` stream from `readable` and deserializes it into `T`. |
| `suspend fun BinaryPlist.decodeElement(readable: Readable): PreferenceElement` | Asynchronously decodes a `bplist00` `Readable` stream into a `PreferenceElement` AST (with cycle detection). |
| `BinaryPlist.encodeBinary(value: Any?): ByteArray`<br/>`BinaryPlist.encodeElementBinary(root: PreferenceElement): ByteArray` | Convenience helpers that encode into an in-memory `WritableByteBuffer` and return `toByteArray()`. |
| `BinaryPlist.decodeBinary<T : Any>(bytes: ByteArray, type: KClass<T>): T?`<br/>`BinaryPlist.decodeElementBinary(bytes: ByteArray): PreferenceElement` | Convenience helpers that decode `bplist00` bytes from an in-memory `ReadableByteBuffer`. |
| `BinaryPlist.isBinaryPlist(bytes: ByteArray): Boolean` | Returns `true` if `bytes` begins with the 8-byte `"bplist00"` magic header and has at least 40 bytes (header + trailer). |

---

## 4. Cross-Platform Preference Storage & Object Serialization (`com.nexustools.genericui.component.system.*`)

### 4.1 `PreferenceStore` (`IApplication.getPreferenceStore()` & `IFragment.getPreferenceStore()`)

- **Source**: [`common/src/commonMain/kotlin/com/nexustools/genericui/component/system/PreferenceStore.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/system/PreferenceStore.kt)
- **Native Contract**: [`PreferenceStoreNativeImpl`](../common/src/commonMain/kotlin/com/nexustools/genericui/nativeimpl/system/PreferenceStoreNativeImpl.kt) & [`DefaultPreferenceStoreNativeImpl`](../common/src/commonMain/kotlin/com/nexustools/genericui/nativeimpl/system/DefaultPreferenceStoreNativeImpl.kt)
- **Purpose**: Unified, type-safe key-value preference store across all 8 platforms with automatic object serialization (`put(key, value)` / `set(key, value)` and `get(key, defaultValue)` / `get(key, targetClass)`), custom type adapters, and live change notifications. Accessible directly from any `IApplication` (`app.getPreferenceStore()`) or any `IFragment` (`fragment.getPreferenceStore()`), defaulting to `GenericUI.applicationName` configured by `genericui { app { name = "..." } }`.

#### Platform Storage Matrix

| Backend Module | Storage Mechanism & Path | Wire Format | External Change Detection |
| :--- | :--- | :--- | :--- |
| **`:desktop:windows`** | `%APPDATA%\<appName>\<key>.bson` | `BinaryJson` (`.bson`) | Timestamp / content polling (`pollExternalChanges()`) |
| **`:desktop:macos`** | `~/Library/Preferences/<appName>/<key>.plist` | `BinaryPlist` (`bplist00` `.plist`) | Timestamp / content polling (`pollExternalChanges()`) |
| **`:desktop:gtk` & `:desktop:qt`** (`:desktop:linuxCommon`) | `$XDG_CONFIG_HOME/<appName>/<key>.bson` (or `~/.config/<appName>/<key>.bson`) | `BinaryJson` (`.bson`) | Timestamp / content polling (`pollExternalChanges()`) |
| **`:desktop:swing`** | macOS: `~/Library/Preferences/<appName>/<key>.plist`<br/>Windows: `%APPDATA%\<appName>\<key>.bson`<br/>Linux: `$XDG_CONFIG_HOME/<appName>/<key>.bson` | `BinaryPlist` (`.plist`) on macOS;<br/>`BinaryJson` (`.bson`) on Windows & Linux | Background daemon `java.nio.file.WatchService` + `pollExternalChanges()` |
| **`:mobile:android`** | Android `Context.getSharedPreferences(appName, Context.MODE_PRIVATE)` | Native primitives + JSON via `PreferenceObjectCodec` | `SharedPreferences.OnSharedPreferenceChangeListener` |
| **`:mobile:ios`** | `<NSHomeDirectory()>/Library/Preferences/<appName>/<key>.plist` | `BinaryPlist` (`bplist00` `.plist`) | Timestamp / content polling (`pollExternalChanges()`) |
| **`:web`** | Browser `window.localStorage.getItem("genericui:$key")` / `setItem("genericui:$key", json)` (with `"genericui:"` key prefix to prevent collisions with JS libraries and browser addons, while `ConfigDirectory` & `CacheDirectory` use `window.indexedDB` directly) | JSON via `PreferenceObjectCodec` | Browser `window.addEventListener("storage", ...)` |

```kotlin
import com.nexustools.genericui.component.system.PreferenceStore

data class EditorTheme(val name: String = "Darcula", val fontSize: Int = 14)

// Obtain from IFragment.getPreferenceStore() or IApplication.getPreferenceStore():
val prefs: PreferenceStore = getPreferenceStore()
prefs.putInt("ui.sidebarWidth", 280)
val sidebarWidth: Int = prefs.getInt("ui.sidebarWidth", defaultValue = 240)

// Store and retrieve typed objects via canonical put(key, value) & get(key, defaultValue / KClass):
prefs.put("ui.theme", EditorTheme(name = "HighContrast", fontSize = 16))
val theme: EditorTheme = prefs.get("ui.theme", EditorTheme())
```

---

### 4.2 `PreferenceObjectCodec` & Supporting Serialization Classes

| Class / Interface | Package | Responsibility |
| :--- | :--- | :--- |
| [`PreferenceObjectCodec`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/system/PreferenceObjectCodec.kt) | `com.nexustools.genericui.component.system` | Converts arbitrary Kotlin objects, primitives, `Enum`s, `List`s, `Set`s, `Array`s, and `Map`s to/from [`PreferenceElement`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/system/PreferenceElement.kt) AST nodes, JSON strings, and streaming [`Readable`](../common/src/commonMain/kotlin/com/nexustools/genericui/io/Readable.kt) / [`Writable`](../common/src/commonMain/kotlin/com/nexustools/genericui/io/Writable.kt) buffers (`encode(value, writable)`, `encodeElement(element, writable)`, `suspend fun decode(readable, targetClass)`, `suspend fun decodeElement(readable)`). |
| [`PreferenceElement`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/system/PreferenceElement.kt) | `com.nexustools.genericui.component.system` | Immutable AST node representing a preference value (`Null`, `ofBoolean`, `ofNumber`, `ofString`, `ofArray`, `ofMap`, `ofObject`). |
| [`PreferenceElementKind`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/system/PreferenceElementKind.kt) | `com.nexustools.genericui.component.system` | Enum of AST node kinds: `NULL`, `BOOLEAN`, `NUMBER`, `STRING`, `ARRAY`, `MAP`, `OBJECT`. |
| [`PreferenceTypeAdapter<T>`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/system/PreferenceTypeAdapter.kt) | `com.nexustools.genericui.component.system` | Custom serializer/deserializer interface registered via `PreferenceStore.registerTypeAdapter`. |
| [`PreferenceSerializable`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/system/PreferenceSerializable.kt) | `com.nexustools.genericui.component.system` | Optional interface (`toPreferenceMap()`, `fromPreferenceMap(map)`) for zero-reflection KMP serialization across all targets. |
| [`PreferenceChangeListener`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/system/PreferenceChangeListener.kt) | `com.nexustools.genericui.component.system` | `fun interface` invoked with `(key: String, newValue: PreferenceElement?)` when a key is updated or removed. |

---

## 5. Complete Catalog of Utilities Across GenericUI

### 5.1 Geometry, Event, Timer, & Core Utilities (`:common`)

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

### 5.2 Styling, Color, Bootstrap, Custom Cursor, SASS/CSS, & Theme Utilities (`:common`)

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

### 5.3 Lazy Viewport & Diffing Utilities (`:common`)

| Utility Class | Package | Source File | Description |
| :--- | :--- | :--- | :--- |
| `LazyDiffUtil` | `com.nexustools.genericui.component.lazy` | [`LazyDiffUtil.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/lazy/LazyDiffUtil.kt) | Computes minimal `DiffResult` updates between old and new data lists for `LazyList`, `LazyGrid`, and `TreeView`. |
| `LazyViewportTracker` | `com.nexustools.genericui.component.lazy` | [`LazyViewportTracker.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/lazy/LazyViewportTracker.kt) | Calculates the visible `[firstActiveIndex .. lastActiveIndex]` window from scroll offset, viewport extent, and overscan count. |
| `ViewHolderPool` | `com.nexustools.genericui.component.lazy` | [`ViewHolderPool.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/lazy/ViewHolderPool.kt) | Type-keyed recycling pool for unbound `ViewHolder` instances as items scroll out of the active viewport. |

### 5.4 Image, Cache, Resource, Data-URL, & Permission Utilities (`:common`)

| Utility Class | Package | Source File | Description |
| :--- | :--- | :--- | :--- |
| `ImageLoader` | `com.nexustools.genericui.image` | [`ImageLoader.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/image/ImageLoader.kt) | Multi-format image decoder, encoder, header inspector, and animated frame loader with automatic URL/resource caching via `ImageCacheManager`. |
| `ImageCacheManager` & `ImageCacheEntry` | `com.nexustools.genericui.image.cache` | [`ImageCacheManager.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/image/cache/ImageCacheManager.kt), [`ImageCacheEntry.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/image/cache/ImageCacheEntry.kt) | Persistent OS `CacheDirectory` + in-memory `ImageBuffer` LRU cache on system targets; browser-managed pass-through no-op on `:web`. |
| `LibJpegCodec`, `LibPngCodec`, `LibWebpCodec` | `com.nexustools.genericui.image.codec` | [`LibJpegCodec.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/image/codec/LibJpegCodec.kt), [`LibPngCodec.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/image/codec/LibPngCodec.kt), [`LibWebpCodec.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/image/codec/LibWebpCodec.kt) | Multiplatform JPEG, PNG, and WebP codecs with native C interop hooks. |
| `DataUrlCodec` | `com.nexustools.genericui.res` | [`DataUrlCodec.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/res/DataUrlCodec.kt) | RFC 2397 `data:<mimeType>;base64,...` encoder, decoder, and MIME type resolver used by `:web` preloaded resources and image/stylesheet resolution. |
| `ResourceID` & `ResourceResolver` | `com.nexustools.genericui.resources` | [`ResourceID.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/resources/ResourceID.kt), [`ResourceResolver.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/resources/ResourceResolver.kt) | Cross-platform resource identifier and runtime resolver (including `openReadable(): ResourceReadableNativeImpl` and `suspend fun openStream(): Readable?`) backing generated `Resource`, `R`, and `RC` objects across all 8 backends. See [`resources.md`](./resources.md). |
| `Resources` & `ResourceTable` | `com.nexustools.genericui.res` | [`Resources.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/res/Resources.kt), [`ResourceTable.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/res/ResourceTable.kt) | In-memory resource registry and fallback table for strings, colors, dimensions, drawables, fonts, and raw bytes. |
| `PermissionManager` | `com.nexustools.genericui.permission` | [`PermissionManager.kt`](../common/src/commonMain/kotlin/com/nexustools/genericui/permission/PermissionManager.kt) | Cross-platform utility for checking and requesting OS permissions (`PermissionType`, `PermissionStatus`). |

### 5.5 Platform Foundation Utilities (`:systemCommon`, `:desktop:common`, `:desktop:nativeCommon`, `:desktop:linuxCommon`, `:mobile:common`, `:web`)

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
| `WebBrowserDomBridge` | `:web` | [`WebBrowserDomBridge.kt`](../web/src/commonMain/kotlin/com/nexustools/genericui/web/WebBrowserDomBridge.kt) | Dual Kotlin/Wasm (`wasmJsMain`) and Kotlin/JS (`jsMain`) bridge for DOM element manipulation, `window.indexedDB`, `window.localStorage`, preloaded `data:` URL resources, `navigator.onLine`, and `requestAnimationFrame`. |
