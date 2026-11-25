# GenericUI Android-Style Multi-Backend Resource System (`Resource`, `Resource.style`, & Custom Cursors)

> **Root Repository**: [**GenericUI (`../README.md`)**](../README.md) | **Master Docs Index**: [**`docs/README.md`**](./README.md)  
> **Related Guides**: [SCSS & Backend Directives (`scss-and-styling.md`)](./scss-and-styling.md) · [Gradle Plugin (`gradle-plugin.md`)](./gradle-plugin.md) · [Fragments & Windows (`fragments-and-windows.md`)](./fragments-and-windows.md) · [Application & Stylesheets (`application.md`)](./application.md) · [Hybrid Layouts & Helpers (`layouts-and-helpers.md`)](./layouts-and-helpers.md) · [Utilities (`utils.md`)](./utils.md) · [Platform Backends (`backends.md`)](./backends.md)

GenericUI provides an Android-style resource compilation and runtime resolution pipeline (`com.nexustools.genericui.resources.*` and `com.nexustools.genericui.res.*`). Resources authored once in `src/commonMain/resources` (or in any variant source set or multiplatform library module) are compiled into a type-safe `Resource` (and `R` / `RC`) class exposing [`ResourceID`](../common/src/commonMain/kotlin/com/nexustools/genericui/resources/ResourceID.kt) constants—including precompiled `.scss` stylesheets (`Resource.style.<name>`), custom cursor assets (`CssCustomCursor`), and Chrome-style Apple icon auto-wrapping ([`GenericUiAppleIconTileComposer`](../gradle-plugin/src/main/kotlin/com/nexustools/genericui/plugin/GenericUiAppleIconTileComposer.kt) / [`AppleIconTileComposer`](../common/src/commonMain/kotlin/com/nexustools/genericui/image/AppleIconTileComposer.kt))—and translated into native backend formats across all 9 target platforms.

---

## 1. Authoring Resources (`src/commonMain/resources`)

The GenericUI Gradle plugin (`GenericUiResourceScanner`, `GenericUiAndroidXmlResourceParser`, and `GenericUiAndroidXmlResourceGenerator`) supports both **standard Android resource directory layouts** and **project `.scss` stylesheets**:

```text
src/commonMain/resources/
├── drawable/
│   ├── icon_app.svg
│   ├── cursor_brush.svg         # Usable as a drawable OR a custom cursor (CssCustomCursor)
│   ├── banner_hero.png
│   └── ic_play.xml              # Android VectorDrawable or SVG
├── mipmap/
│   └── ic_launcher.png
├── styles/
│   ├── main.scss                # Compiled into Resource.style.main (PrecompiledStyleSheet + Web .css)
│   └── studio_theme.scss        # Compiled into Resource.style.studio_theme
├── values/
│   ├── strings.xml              # <resources><string name="app_title">My App</string></resources>
│   ├── colors.xml               # <resources><color name="accent_primary">#2563EB</color></resources>
│   ├── dimens.xml               # <resources><dimen name="card_padding">16dp</dimen></resources>
│   ├── integers.xml             # <resources><integer name="max_columns">4</integer></resources>
│   └── bools.xml                # <resources><bool name="enable_animations">true</bool></resources>
├── font/
│   └── inter_regular.ttf
├── raw/
│   └── default_preset.json
└── xml/
    └── shortcuts.xml
```

### 1.1 Translating & Creating Android Resource Files

1. **Translating Existing Android Resource Files**: Reads standard Android XML and asset directories (`drawable/`, `mipmap/`, `values/strings.xml`, `values/colors.xml`, `values/dimens.xml`, `values/styles.xml`, `raw/`, `font/`, `xml/`, plus qualifier folders such as `values-night/` or `drawable-xhdpi/`) and translates them into every enabled backend's native resource bundle.
2. **Creating Android Resource Files Automatically**: Generates compliant Android `res/` XML and drawable assets (`build/generated/genericui/android/res/{drawable,mipmap-*,values,raw,font,xml}`) from flat or common resources—including converting `.svg` icons into Android `VectorDrawable` XMLs and emitting `values/colors.xml` (`theme_color`, `colorPrimary`) and `values/strings.xml` from `genericui { app { ... }; theme { ... } }`.
3. **Build-Time `.scss` Precompilation (`Resource.style.<name>`), Backend `@` Directives, & Raw Library `theme.scss` Templates vs. Consumer-App Theme Compilation**: Every `.scss` / `.sass` / `.css` file in `src/commonMain/resources/**` is registered as `ResourceType.STYLE` (`Resource.style.<name>` / `R.style.<name>`). Static visuals, interactive state visuals (`:hover`, `:focus`, `:active`, `:disabled`), layouts (`position`, `top`, `transform`, `z-index`, `pointer-events`, `cursor`), and `@keyframes` animations live in `style.scss` across `:common`, UI extensions (`:extensions:ui:overlay`, `:extensions:media:media`, `:extensions:ui:pip`, etc.), and consumer apps (zero direct component styles in Kotlin except runtime-dynamic `%` offsets). Shared `.scss` stylesheets can scope rules to specific platforms using backend `@` directives (`@windows`, `@macos`, `@gtk`, `@kde`, `@swing`, `@android`, `@ios`, `@ipados`, `@web`, group directives such as `@desktop`, `@mobile`, `@system`, `@apple`, `@linux`, and boolean `@backend(...)` / `@platform(...)` / `@target(...)` expressions — see [**`docs/scss-and-styling.md`**](./scss-and-styling.md)). By contrast, `src/commonMain/resources/styles/theme.scss` files inside prebuilt GenericUI libraries (`:common` and `:extensions:*`) are generic **uncompiled source templates** — libraries **NEVER** compile or integrate resulting `theme.scss` code into library `.jar`/`.klib` artifacts themselves, and instead package only the raw template text inside artifact metadata (`META-INF/genericui/themes/<scopeId>.scss` and `META-INF/genericui/module-resources/<scopeId>.json`). **Only the consumer application's build (`:gradle-plugin`)** extracts those upstream `theme.scss` templates, substitutes the consumer app's `genericui { theme { ... } }` settings from its `build.gradle.kts`, and integrates the resulting compiled theme stylesheet (`GenericUiGeneratedThemeStyleSheet` on `:system` and `gui/genericui-theme.css` on `:web`, backed by `ThemeStyleSheetRegistry` for dynamic runtime `app.setThemeConfig(...)` updates) solely into the consumer application. On non-web targets, `style.scss` / app `.scss` files precompile at build time into zero-parse `PrecompiledStyleSheet` (`CompiledStyleRule`) Kotlin objects carrying `BackendStyleMask` bitmasks with internal `@color/...`, `@drawable/...`, `@dimen/...`, `@font/...`, and `@string/...` references resolved to typed values and `ResourceID`s. On `:web`, `.scss` files compile at build time into `.css` files with non-Web backend blocks stripped and `@web` / `@browser` blocks unwrapped.
4. **Three-Tier Scoped Module Resources, `ResourceTable`, & `nativeResourceKey`**: Each resource-bearing module (`:common`, `:extensions:*`, and the consumer application) compiles its own isolated `R.kt`, `RC.kt`, `Resource.kt`, and `GeneratedResourceRegistry.kt` (`com.nexustools.genericui.res.R` for `:common` `0x7e`, extension packages such as `com.nexustools.genericui.ext.overlay.res.R`, `com.nexustools.genericui.component.media.res.R`, and `com.nexustools.genericui.ext.alien.res.R` across `0x60..0x7d`, and `<appPkg>.res.R` for the consumer app `0x7f`) and emits `META-INF/genericui/module-resources/<scopeId>.json`. Every generated module `R`/`Resource` calls `GeneratedResourceRegistry.ensureInitialized()`, registering all entries into [`ResourceTable`](../common/src/commonMain/kotlin/com/nexustools/genericui/resources/ResourceTable.kt) (`byNumericId` + `byCanonicalPath` + app-preferring `byTypeAndName`). Consumer app `R` classes expose **only** the app's own resources (never upstream core or extension resources). Each [`ResourceID`](../common/src/commonMain/kotlin/com/nexustools/genericui/resources/ResourceID.kt) carries `nativeLibraryGroupId` (`"genericui"` for `0x7E`, `"genericui-ext-<ext>"` for extensions, `""` for app `0x7F`), prefix-free `nativeResourceKey` (`<resource_path>_<resource_name>_<ext>`, e.g., `drawable_loading_svg`), `canonicalRelativePath` (`gui/...`, `gui/ext/<cat>/<ext>/...`, `app/...`), and `flatName` (`gui_*`, `gui_ext_*`, `app_*`).

---

## 2. Generated `R` / `RC` / `Resource` & `ResourceID` Hierarchy

At build time, `generateModuleResources` (on `:common` and extensions) and `compileGenericUiResources` (`GenericUiResourceCompilerTask` on consumer apps) emit strictly 1 public object per `.kt` file (`R.kt`, `RC.kt`, `Resource.kt`, `GeneratedResourceRegistry.kt`) under `<moduleRootPkg>.res`:

```kotlin
package com.example.myapp.res

import com.nexustools.genericui.res.ResourceType

object Resource {
    typealias ResourceID = com.nexustools.genericui.resources.ResourceID

    object drawable {
        val icon_app: ResourceID = ResourceID(0x7f010001, ResourceType.DRAWABLE, "icon_app", "drawable/icon_app.svg")
        val cursor_brush: ResourceID = ResourceID(0x7f010002, ResourceType.DRAWABLE, "cursor_brush", "drawable/cursor_brush.svg")
        val banner_hero: ResourceID = ResourceID(0x7f010003, ResourceType.DRAWABLE, "banner_hero", "drawable/banner_hero.png")
    }

    object string {
        val app_title: ResourceID = ResourceID(0x7f020001, ResourceType.STRING, "app_title", "values/strings.xml#app_title")
        val welcome_user: ResourceID = ResourceID(0x7f020002, ResourceType.STRING, "welcome_user", "values/strings.xml#welcome_user")
    }

    object color {
        val accent_primary: ResourceID = ResourceID(0x7f030001, ResourceType.COLOR, "accent_primary", "values/colors.xml#accent_primary")
    }

    object dimen {
        val card_padding: ResourceID = ResourceID(0x7f040001, ResourceType.DIMEN, "card_padding", "values/dimens.xml#card_padding")
    }

    object font {
        val inter_regular: ResourceID = ResourceID(0x7f050001, ResourceType.FONT, "inter_regular", "font/inter_regular.ttf")
    }

    object style {
        val main: ResourceID = ResourceID(0x7f060001, ResourceType.STYLE, "main", "styles/main.scss")
        val studio_theme: ResourceID = ResourceID(0x7f060002, ResourceType.STYLE, "studio_theme", "styles/studio_theme.scss")
    }

    object raw {
        val default_preset: ResourceID = ResourceID(0x7f070001, ResourceType.RAW, "default_preset", "raw/default_preset.json")
    }

    suspend fun openReadable(resId: ResourceID): com.nexustools.genericui.io.Readable? =
        com.nexustools.genericui.resources.ResourceResolver.openReadable(resId)

    suspend fun openStream(resId: ResourceID): com.nexustools.genericui.io.Readable? =
        com.nexustools.genericui.resources.ResourceResolver.openStream(resId)
}

typealias R = Resource
```

---

## 3. Using `Resource.ResourceID`, `openStream()`, `Resource.style`, & `CssCustomCursor` in `commonMain`

Both [`ResourceID`](../common/src/commonMain/kotlin/com/nexustools/genericui/resources/ResourceID.kt) extension functions and [`ResourceResolver`](../common/src/commonMain/kotlin/com/nexustools/genericui/resources/ResourceResolver.kt) resolve resources through the active backend's [`ResourceResolverNativeImpl`](../common/src/commonMain/kotlin/com/nexustools/genericui/resources/ResourceResolverNativeImpl.kt):

| API Call | Return Type | Description |
| :--- | :--- | :--- |
| `Resource.string.welcome_user.getString("Alex")`<br/>`ResourceResolver.getString(id, *formatArgs)` | `String` | Resolves a localized string and substitutes `%s`, `%d`, `%1$s`, `%1$d` format arguments. |
| `Resource.color.accent_primary.getColor()`<br/>`ResourceResolver.getColor(id)` | `RgbaColor` | Resolves a color resource into an `RgbaColor`. |
| `Resource.dimen.card_padding.getDimenPx()`<br/>`ResourceResolver.getDimenPx(id)` | `Float` | Resolves a dimension (`dp`, `sp`, `px`) into display pixels. |
| `Resource.drawable.icon_app.getUrl()`<br/>`ResourceResolver.getUrl(id)` | `String` | Resolves a file-backed resource into a backend URL/URI (or a preloaded `data:<mimeType>;base64,...` Data-URL via [`DataUrlCodec`](../common/src/commonMain/kotlin/com/nexustools/genericui/res/DataUrlCodec.kt) on `:web`). |
| `Resource.raw.default_preset.getBytes()`<br/>`ResourceResolver.getBytes(id)` | `ByteArray?` | Loads raw binary bytes from the backend's native resource container (e.g., embedded `.exe` `RT_RCDATA` on Windows). |
| **`Resource.openReadable(Resource.raw.default_preset)`**<br/>**`Resource.raw.default_preset.openReadable()`**<br/>**`ResourceResolver.openReadable(id)`** | `Readable?` (`suspend`) | Asynchronously opens a non-blocking [`Readable`](../common/src/commonMain/kotlin/com/nexustools/genericui/io/Readable.kt) handle (backed by `MemoryReadableNativeImpl` / `FileReadableNativeImpl` on each target) for the given `ResourceID`. |
| **`Resource.openStream(Resource.raw.default_preset)`**<br/>**`Resource.raw.default_preset.openStream()`**<br/>**`ResourceResolver.openStream(id)`** | `Readable?` (`suspend`) | Asynchronously opens a non-blocking [`Readable`](../common/src/commonMain/kotlin/com/nexustools/genericui/io/Readable.kt) byte stream for the given `ResourceID` across all 9 backends without blocking the UI thread. |
| `Resource.drawable.banner_hero.getImageBuffer()`<br/>`ResourceResolver.getImageBuffer(id)` | `ImageBuffer?` | Loads and decodes a drawable resource directly into an in-memory `:common` `ImageBuffer`. |
| `Resource.drawable.icon_app.getDrawable()`<br/>`ResourceResolver.getDrawable(id)` | `DrawableResource?` | Returns the `DrawableResource` descriptor (vector SVG markup, path, or intrinsic size). |
| `app.addStyleSheet(Resource.style.main)`<br/>`app.removeStyleSheet(Resource.style.main)` | `Unit` / `Boolean` | Attaches or removes a precompiled `.scss` stylesheet (`PrecompiledStyleSheet` on non-web, `<link rel="stylesheet">` or `data:text/css;base64,...` on `:web`). |

### 3.1 Non-Blocking Resource Streaming (`Resource.openStream(resId)` & `ResourceID.openStream()`)

Any resource (`raw`, `drawable`, `font`, `xml`, `style`, etc.) can be streamed asynchronously as a [`Readable`](../common/src/commonMain/kotlin/com/nexustools/genericui/io/Readable.kt) via `suspend fun Resource.openStream(resId): Readable?`, `suspend fun ResourceID.openStream(): Readable?`, or `suspend fun ResourceResolver.openStream(resId): Readable?`:

```kotlin
import com.example.myapp.res.Resource
import com.nexustools.genericui.io.Readable
import com.nexustools.genericui.resources.openStream

// 1. Open a non-blocking Readable stream via Resource.openStream(resId) or resId.openStream():
val stream: Readable? = Resource.openStream(Resource.raw.default_preset)
val presetJson: String = stream?.readAllText().orEmpty()
stream?.close()

// 2. Or stream chunks / lines directly from the ResourceID extension:
Resource.raw.default_preset.openStream()?.forEachLine { line ->
    println("Preset line: $line")
}
```

### 3.2 `:web` Data-URL Resolution (`DataUrlCodec`) & Web Resource Preloader (`loader.js`)

On `:web`, [`WebResourceResolverImpl`](../web/src/commonMain/kotlin/com/nexustools/genericui/web/WebResourceResolverImpl.kt) and [`DataUrlCodec`](../common/src/commonMain/kotlin/com/nexustools/genericui/res/DataUrlCodec.kt) work together with `loader.js`, `./serviceworker.js`, and `manifest.webmanifest` (`"resources": [...]`) to preload application resources into `window.__GENERICUI_PRELOADED_RESOURCES__` before the application starts:

1. **Five-Stage Web Preloader Lifecycle (`loader.js` & `#genericui-splash`)**:
   - **`"Initializing"`**: Boots `loader.js`, reads `manifest.webmanifest`, unregisters any stale subpath service-worker registrations, and initializes `#genericui-splash`.
   - **`"Loading Resources"`**: Preloads resources listed in `manifest.webmanifest` (`gui/...`, `gui/ext/...`, `app/...`) into `window.__GENERICUI_PRELOADED_RESOURCES__` (hitting or seeding content-hashed `genericui-offline-<buildHash>` cache) as Base64 Data-URLs (`data:<mimeType>;base64,...`).
   - **`"Loading Application"`**: Downloads and instantiates the primary Kotlin/Wasm (`wasmJs`) binary (or automatically falls back to the Kotlin/JS IR bundle).
   - **`"Error"`**: Displayed with retry diagnostics if resource or bundle loading fails while offline and uncached.
   - **`"Ready"`**: Reached once all preloaded resources and the application bundle are initialized; fades out `#genericui-splash`, registers unified web-root `./serviceworker.js` post-preload when `"offline_enabled": true`, and mounts the root `@MainAppFragment`.
2. **Immediate `data:<mimeType>;base64,...` Resolution via `DataUrlCodec`**:
   - [`DataUrlCodec`](../common/src/commonMain/kotlin/com/nexustools/genericui/res/DataUrlCodec.kt) (`encodeBytes`, `encodeUtf8Text`, `decodeBytes`, `decodeUtf8Text`, `extractMimeType`, `isDataUrl`, `guessMimeType`) encodes and decodes RFC 2397 Data-URLs across all resource types (`text/css`, `image/svg+xml`, `image/png`, `image/jpeg`, `image/webp`, `font/ttf`, `font/woff2`, `application/json`, `application/octet-stream`).
   - When a resource is preloaded on `:web`, `resId.getUrl()` (`WebResourceResolverImpl.resolveUrl`) returns its `data:<mimeType>;base64,...` Data-URL directly from memory (`WebBrowserDomBridge.getPreloadedResourceDataUrl`), `resId.getBytes()` / `resId.openStream()` reads the decoded bytes in-memory with zero extra network round-trips, and `WebStyleSheetManager` injects preloaded stylesheets as `data:text/css;base64,...` `<link rel="stylesheet">` tags.

### 3.3 Custom Cursors (`CssCustomCursor`) & Native `.cur` / `NSCursor` Synthesis

[`CssCustomCursor`](../common/src/commonMain/kotlin/com/nexustools/genericui/style/css/CssCustomCursor.kt) allows any PNG `ResourceID`, SVG `ResourceID`, inline SVG markup string, or in-memory `ImageBuffer` to be used as a custom hardware cursor with pixel-accurate `(hotspotX, hotspotY)` coordinates and a standard [`CssCursor`](../common/src/commonMain/kotlin/com/nexustools/genericui/enums/css/CssCursor.kt) fallback:
- **Windows (`:desktop` `mingwMain`)**: `WindowsCurCodec` automatically encodes the resolved RGBA `ImageBuffer` and `(hotspotX, hotspotY)` into a valid **Windows `.cur` binary** (`ICONDIR` with `idType = 2`, `ICONDIRENTRY` with `wXHotSpot` and `wYHotSpot`, 32-bit BGRA DIB + 1-bpp AND mask) and creates a native Win32 `HCURSOR` (`CreateIconFromResourceEx`).
- **macOS (`:desktop` `macosMain`)**: `MacosCursorTranslator` automatically constructs a multi-representation `@1x`/`@2x` `NSImage` and `NSCursor(image: nsImage, hotSpot: NSMakePoint(hotspotX, hotspotY))`.
- **iPadOS (`:mobile` `iosMain`)**: [`IpadosPointerAndKeyboardInstaller`](../mobile/src/iosMain/kotlin/com/nexustools/genericui/ipados/IpadosPointerAndKeyboardInstaller.kt) wires `UIPointerInteraction` (`UikitPointerInteractionDelegate`) and trackpad hover cursor styling onto UIKit views.
- **GTK4, KDE, Swing, Web, & `:extensions:ui:alien`**: Automatically translate `CssCustomCursor` into `GdkCursor`, `QCursor`, `Toolkit.createCustomCursor`, CSS `url('...') hotspotX hotspotY, fallback`, and Alien cursor rendering.

```kotlin
import com.example.myapp.res.Resource
import com.nexustools.genericui.component.media.Image
import com.nexustools.genericui.component.typography.Heading
import com.nexustools.genericui.enums.css.CssCursor
import com.nexustools.genericui.resources.getColor
import com.nexustools.genericui.resources.getImageBuffer
import com.nexustools.genericui.resources.getString
import com.nexustools.genericui.style.css.CssCustomCursor

// 1. Attach a precompiled .scss stylesheet via Resource.style.<name>
app.addStyleSheet(Resource.style.main)

// 2. Set window icon from ResourceID
hostingWindow?.setWindowIcon(Resource.drawable.icon_app)

// 3. Configure a custom cursor from an SVG or PNG ResourceID (synthesized into .cur on Windows and NSCursor on macOS)
canvasWidget.getStyle().setCursor(
    CssCustomCursor.fromResource(
        resourceId = Resource.drawable.cursor_brush,
        hotspotX = 4,
        hotspotY = 28,
        widthPx = 32,
        heightPx = 32,
        fallback = CssCursor.CROSSHAIR
    )
)

// 4. Resolve localized strings, colors, and images from ResourceID
val heading = Heading(Resource.string.welcome_user.getString("Kara"), level = 2)
val accent = Resource.color.accent_primary.getColor()
val heroImage = Image(src = Resource.drawable.banner_hero.getUrl())
val pixelBuffer = Resource.drawable.banner_hero.getImageBuffer()
```

---

## 4. Per-Backend Build Packaging & Runtime Translation

| Backend Target | Build-Time Resource Packaging | Runtime `Resource.ResourceID` Resolution (`ResourceResolverNativeImpl`) |
| :--- | :--- | :--- |
| **Web (`:web` — `wasmJs` & `js`)** *(Tier 1 Hierarchical)* | Stages `gui/...`, `gui/ext/<cat>/<ext>/...`, and consumer `app/...` inside `build/dist/web/<mode>/` (and `generated/genericui/resources/backends/web/{gui,app}`), compiles `.scss` to `.css`, and emits `manifest.webmanifest` (`"offline_enabled": true`, `"resources": [...]`) + unified root `./serviceworker.js` with `BUILD_HASH`. | [`WebResourceResolverImpl`](../web/src/commonMain/kotlin/com/nexustools/genericui/web/WebResourceResolverImpl.kt) resolves `ResourceID`s from `window.__GENERICUI_PRELOADED_RESOURCES__` Data-URLs (`resId.canonicalRelativePath` / `resId.relativePath`) via [`DataUrlCodec`](../common/src/commonMain/kotlin/com/nexustools/genericui/res/DataUrlCodec.kt), supports `openStream(): Readable?`, and injects `<link rel="stylesheet">` for `Resource.style.*`. |
| **Windows (`:desktop` `mingwMain`)** *(Tier 3 Binary Embedded)* | [`GenericUiBuildNativeSharedLibsTask`](../gradle-plugin/src/main/kotlin/com/nexustools/genericui/plugin/GenericUiBuildNativeSharedLibsTask.kt) embeds core/extension `META-INF/genericui/module-resources/*.json` assets into `genericui.dll` & `genericui-ext-<ext>.dll` as string-named `RT_RCDATA` (`resId.nativeResourceKey`, zero `gui_`/`app_` prefix), while [`GenericUiResourceCompilerTask`](../gradle-plugin/src/main/kotlin/com/nexustools/genericui/plugin/GenericUiResourceCompilerTask.kt) embeds only consumer app resources into `<app>.exe` (`genericui_resources.o`). Zero loose `gui/`/`app/` files staged. | [`WindowsResourceResolverImpl`](../desktop/src/mingwMain/kotlin/com/nexustools/genericui/windows/WindowsResourceResolverImpl.kt) queries `GetModuleHandleW("${resId.nativeLibraryGroupId}.dll")` (or `null` for app `EXE`) + `FindResourceW(..., wide(resId.nativeResourceKey), RT_RCDATA)` / `LoadStringW` and returns `"${moduleName}:${resId.nativeResourceKey}"`. |
| **Android (`:mobile` `androidMain`)** *(Tier 2 Flat)* | Flattens resources using `resId.flatName` (`gui_*`, `gui_ext_*`, `app_*`) into `android/res/{drawable,mipmap-*,values,raw,font,xml}` (omitting raw nested source dirs) alongside `PrecompiledStyleSheet`. | [`AndroidResourceResolverImpl`](../mobile/src/androidMain/kotlin/com/nexustools/genericui/android/AndroidResourceResolverImpl.kt) resolves `resId.flatName` (falling back to `resId.name`) via `Resources.getIdentifier`. |
| **iOS & iPadOS (`:mobile` `iosMain` + `:apple`)** *(Tier 1 + Tier 2)* | Stages hierarchical `Resources/gui` + `Resources/app` folders alongside flat `Assets.xcassets` inside the generated `.app`/Xcode project (`GenericUiGenerateIosAppTask`) plus `PrecompiledStyleSheet`. | Shared [`AppleResourceResolverNativeImpl`](../apple/src/appleMain/kotlin/com/nexustools/genericui/apple/AppleResourceResolverNativeImpl.kt) resolves `resId.canonicalRelativePath` and `resId.flatName` via `NSBundle.mainBundle`. |
| **macOS (`:desktop` `macosMain`)** *(Tier 3 Mach-O Embedded)* | Embeds shared-lib C const `__TEXT,__const` tables (`genericui_res_lookup_<group>`) into `libgenericui.dylib` & `libgenericui-ext-<ext>.dylib` (sealed by `codesign`) and consumer app C table (`genericui_res_lookup_app`, including unwrapped `tray_icon_png` / `tray_icon_svg`) directly into `<app>` Mach-O. `Contents/Resources/` contains only `icon.icns` (+ optional MoltenVK ICD). | [`MacosResourceResolverImpl`](../desktop/src/macosMain/kotlin/com/nexustools/genericui/macos/MacosResourceResolverImpl.kt) looks up `genericui_res_lookup_<group>` / `genericui_res_lookup_app` symbols via `dlsym` inside `lib<group>.dylib` or the host binary. [`MacosSystemTrayNativeImpl`](../desktop/src/macosMain/kotlin/com/nexustools/genericui/macos/MacosSystemTrayNativeImpl.kt) loads unwrapped embedded tray artwork (`wrapInAppleTile = false`) and sets `isTemplate = true` only for monochrome alpha masks; `MacosNativeWindowHost.isAlreadyWrappedAppleSquircleIcon(...)` prevents double-wrapping app/Dock icons. |
| **Linux GTK4 & KDE (`:desktop` `linuxMain`)** *(Tier 3 ELF Embedded)* | Embeds shared-lib C `.rodata` tables (`genericui_res_lookup_<group>`) into `libgenericui.so` & `libgenericui-ext-<ext>.so` and consumer app C `.rodata` table (`genericui_res_lookup_app`) into `<app>` ELF with `--export-dynamic-symbol=genericui_res_lookup_app`. Zero loose `gui/`/`app/` dirs in `dist/linux-{gtk,kde}/`. | Shared [`LinuxResourceResolverImpl`](../desktop/src/linuxMain/kotlin/com/nexustools/genericui/linux/LinuxResourceResolverImpl.kt) resolves `genericui_res_lookup_<group>` / `genericui_res_lookup_app` symbols via `dlsym` inside `lib<group>.so` or the host ELF binary. |
| **JVM Swing (`:desktop` `jvmMain` + `:jvm`)** *(Tier 1 Hierarchical)* | Stages `gui/<resourcepath>`, `gui/ext/<cat>/<ext>/<resourcepath>`, and `app/<resourcepath>` cleanly inside the fat JAR alongside compiled value tables and `PrecompiledStyleSheet` classes. | [`SwingResourceResolverImpl`](../desktop/src/jvmMain/kotlin/com/nexustools/genericui/swing/SwingResourceResolverImpl.kt) (extending [`JvmResourceResolverBase`](../jvm/src/jvmSharedMain/kotlin/com/nexustools/genericui/jvm/JvmResourceResolverBase.kt)) resolves `resId.canonicalRelativePath` via `ClassLoader.getResource(...)` URLs and streams. |

---

## 5. Configuring `resources { ... }` & `styles { ... }` in `build.gradle.kts`

Resource generation and `.scss` precompilation are enabled by default whenever `id("com.nexustools.genericui")` is applied:

```kotlin
genericui {
    app {
        id = "com.example.myapp"
        name = "My Application"
    }

    styles {
        compileSass = true
        generateWebCss = true
        precompileToKotlin = true
        sourceDir = "src/commonMain/resources/styles"
        globalStyleSheet = "styles/main.scss" // Automatically calls app.addStyleSheet("styles/main.scss") in generated main()
    }

    resources {
        enabled = true
        packageName = "com.example.myapp.res"
        translateForAllBackends = true
        sourceDir("src/commonMain/resources")
    }
}
```
