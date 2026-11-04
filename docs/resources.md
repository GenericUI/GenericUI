# GenericUI Android-Style Multi-Backend Resource System (`Resource`, `Resource.style`, & Custom Cursors)

> **Root Repository**: [**GenericUI (`../README.md`)**](../README.md) | **Master Docs Index**: [**`docs/README.md`**](./README.md)  
> **Related Guides**: [Gradle Plugin (`gradle-plugin.md`)](./gradle-plugin.md) · [Fragments & Windows (`fragments-and-windows.md`)](./fragments-and-windows.md) · [Application & Stylesheets (`application.md`)](./application.md) · [Hybrid Layouts & Helpers (`layouts-and-helpers.md`)](./layouts-and-helpers.md) · [Utilities (`utils.md`)](./utils.md) · [Platform Backends (`backends.md`)](./backends.md)

GenericUI provides an Android-style resource compilation and runtime resolution pipeline (`com.nexustools.genericui.resources.*` and `com.nexustools.genericui.res.*`). Resources authored once in `src/commonMain/resources` (or in any variant source set or multiplatform library module) are compiled into a type-safe `Resource` (and `R` / `RC`) class exposing [`ResourceID`](../common/src/commonMain/kotlin/com/nexustools/genericui/resources/ResourceID.kt) constants—including precompiled `.scss` stylesheets (`Resource.style.<name>`) and custom cursor assets (`CssCustomCursor`)—and translated into native backend formats across all 8 target platforms.

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
3. **Build-Time `.scss` Precompilation (`Resource.style.<name>`)**: Every `.scss` / `.sass` / `.css` file in `src/commonMain/resources/**` is registered as `ResourceType.STYLE` (`Resource.style.<name>` / `R.style.<name>`). On non-web targets, `.scss` files precompile at build time into zero-parse `PrecompiledStyleSheet` (`CompiledStyleRule`) Kotlin objects with internal `@color/...`, `@drawable/...`, `@dimen/...`, `@font/...`, and `@string/...` references resolved to typed values and `ResourceID`s. On `:web`, `.scss` files compile at build time into `.css` files bundled into the Web distribution.
4. **Multi-Module Resource Merging**: Library modules applying `id("com.nexustools.genericui")` generate their own `Resource` class and packaged resource manifest; downstream application modules automatically merge transitive library resources into the final backend bundle without ID collisions.

---

## 2. Generated `Resource` & `Resource.ResourceID` Class Hierarchy

At build time, `generateGenericUiResources` (`GenericUiGenerateResourcesTask`) and `compileGenericUiResources` (`GenericUiResourceCompilerTask`) generate type-safe Kotlin resource objects into `build/generated/genericui/commonMain/kotlin`:

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

    fun openReadable(resId: ResourceID): com.nexustools.genericui.io.ResourceReadableNativeImpl =
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
| **`Resource.openReadable(Resource.raw.default_preset)`**<br/>**`Resource.raw.default_preset.openReadable()`**<br/>**`ResourceResolver.openReadable(id)`** | `ResourceReadableNativeImpl` (`Readable`) | Synchronously creates a non-blocking [`ResourceReadableNativeImpl`](../common/src/commonMain/kotlin/com/nexustools/genericui/io/ResourceReadableNativeImpl.kt) (`Readable`) handle for the given `ResourceID` whose `read`, `readAllBytes`, and `readAllText` operations execute asynchronously across all 8 backends. |
| **`Resource.openStream(Resource.raw.default_preset)`**<br/>**`Resource.raw.default_preset.openStream()`**<br/>**`ResourceResolver.openStream(id)`** | `Readable?` (`suspend`) | Asynchronously opens a non-blocking [`Readable`](../common/src/commonMain/kotlin/com/nexustools/genericui/io/Readable.kt) byte stream for the given `ResourceID` across all 8 backends without blocking the UI thread. |
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

### 3.2 `:web` Data-URL Resolution (`DataUrlCodec`) & Web Resource Preloader Stages

On `:web`, [`WebResourceResolverImpl`](../web/src/commonMain/kotlin/com/nexustools/genericui/web/WebResourceResolverImpl.kt) and [`DataUrlCodec`](../common/src/commonMain/kotlin/com/nexustools/genericui/res/DataUrlCodec.kt) work together with `genericui-loader.js` and `manifest.webmanifest` (`"resources": [...]`) to preload application resources into `window.__GENERICUI_PRELOADED_RESOURCES__` before the application starts:

1. **Five-Stage Web Preloader Lifecycle (`genericui-loader.js` & `#genericui-splash`)**:
   - **`"Initializing"`**: Boots `genericui-loader.js`, reads `manifest.webmanifest`, registers `genericui-sw.js` when `"offline_enabled": true`, and initializes the `#genericui-splash` progress UI.
   - **`"Loading Resources"`**: Preloads all resources listed in `manifest.webmanifest` (`"resources": [...]` — `.css` stylesheets, drawables, fonts, raw assets, and XMLs) into `window.__GENERICUI_PRELOADED_RESOURCES__` as Base64 Data-URLs (`data:<mimeType>;base64,...`) while updating the progress bar.
   - **`"Loading Application"`**: Downloads and instantiates the primary Kotlin/Wasm (`wasmJs`) binary (or automatically falls back to the Kotlin/JS IR bundle).
   - **`"Error"`**: Displayed with retry diagnostics if resource or bundle loading fails while offline and uncached.
   - **`"Ready"`**: Reached once all preloaded resources and the application bundle are initialized; fades out `#genericui-splash` and mounts the root `@MainAppFragment`.
2. **Immediate `data:<mimeType>;base64,...` Resolution via `DataUrlCodec`**:
   - [`DataUrlCodec`](../common/src/commonMain/kotlin/com/nexustools/genericui/res/DataUrlCodec.kt) (`encodeBytes`, `encodeUtf8Text`, `decodeBytes`, `decodeUtf8Text`, `extractMimeType`, `isDataUrl`, `guessMimeType`) encodes and decodes RFC 2397 Data-URLs across all resource types (`text/css`, `image/svg+xml`, `image/png`, `image/jpeg`, `image/webp`, `font/ttf`, `font/woff2`, `application/json`, `application/octet-stream`).
   - When a resource is preloaded on `:web`, `resId.getUrl()` (`WebResourceResolverImpl.resolveUrl`) returns its `data:<mimeType>;base64,...` Data-URL directly from memory (`WebBrowserDomBridge.getPreloadedResourceDataUrl`), `resId.getBytes()` / `resId.openStream()` reads the decoded bytes in-memory with zero extra network round-trips, and `WebStyleSheetManager` injects preloaded stylesheets as `data:text/css;base64,...` `<link rel="stylesheet">` tags.

### 3.3 Custom Cursors (`CssCustomCursor`) & Native `.cur` / `NSCursor` Synthesis

[`CssCustomCursor`](../common/src/commonMain/kotlin/com/nexustools/genericui/style/css/CssCustomCursor.kt) allows any PNG `ResourceID`, SVG `ResourceID`, inline SVG markup string, or in-memory `ImageBuffer` to be used as a custom hardware cursor with pixel-accurate `(hotspotX, hotspotY)` coordinates and a standard [`CssCursor`](../common/src/commonMain/kotlin/com/nexustools/genericui/enums/css/CssCursor.kt) fallback:
- **Windows (`:desktop:windows`)**: `WindowsCurCodec` automatically encodes the resolved RGBA `ImageBuffer` and `(hotspotX, hotspotY)` into a valid **Windows `.cur` binary** (`ICONDIR` with `idType = 2`, `ICONDIRENTRY` with `wXHotSpot` and `wYHotSpot`, 32-bit BGRA DIB + 1-bpp AND mask) and creates a native Win32 `HCURSOR` (`CreateIconFromResourceEx`).
- **macOS (`:desktop:macos`)**: `MacosCursorTranslator` automatically constructs a multi-representation `@1x`/`@2x` `NSImage` and `NSCursor(image: nsImage, hotSpot: NSMakePoint(hotspotX, hotspotY))`.
- **GTK4, Qt6, Swing, Web, & `:extensions:alien`**: Automatically translates `CssCustomCursor` into `GdkCursor`, `QCursor`, `Toolkit.createCustomCursor`, CSS `url('...') hotspotX hotspotY, fallback`, and Alien cursor rendering.

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
| **Web (`:web` — `wasmJs` & `js`)** | Copies drawables, fonts, and raw assets into `build/dist/web/assets/<type>/<name>.<ext>`, compiles `.scss` into `.css` files in `build/generated/genericui/web-css`, emits `manifest.webmanifest` (`"offline_enabled": true`, `"resources": [...]`) + `genericui-sw.js`, and compiles `values/*.xml` into the generated resource table. | [`WebResourceResolverImpl`](../web/src/commonMain/kotlin/com/nexustools/genericui/web/WebResourceResolverImpl.kt) resolves preloaded `ResourceID`s (`window.__GENERICUI_PRELOADED_RESOURCES__`, loaded during the `"Loading Resources"` preloader stage) directly to **`data:<mimeType>;base64,...` Data-URLs** via [`DataUrlCodec`](../common/src/commonMain/kotlin/com/nexustools/genericui/res/DataUrlCodec.kt) (falling back to `"assets/<type>/<name>.<ext>"`), supports `openStream(): Readable?`, and injects `<link rel="stylesheet">` for `Resource.style.*`. |
| **Windows (`:desktop:windows`)** | [`GenericUiResourceCompilerTask`](../gradle-plugin/src/main/kotlin/com/nexustools/genericui/plugin/GenericUiResourceCompilerTask.kt) & [`GenericUiCompileWindowsResourcesTask`](../gradle-plugin/src/main/kotlin/com/nexustools/genericui/plugin/GenericUiCompileWindowsResourcesTask.kt) generate a Win32 `.rc` script (`generated_resources.rc` + `resource_ids.h`) mapping each `ResourceID.id` to `RT_RCDATA`, `RT_ICON`, `RT_GROUP_ICON`, `RT_CURSOR`, or `RT_STRING`, precompile `.scss` to `PrecompiledStyleSheet`, compile via `windres` to COFF `.res`, and link directly into the `.exe` binary. | [`WindowsResourceResolverImpl`](../desktop/windows/src/commonMain/kotlin/com/nexustools/genericui/windows/WindowsResourceResolverImpl.kt) loads bytes, icons, `.cur` cursors (`WindowsCurCodec`), and strings directly from the running `.exe` module in memory (`FindResourceW` / `LoadResource` / `LockResource` / `SizeofResource` / `LoadStringW`) with zero external loose files required. |
| **Android (`:mobile:android`)** | [`GenericUiResourceCompilerTask`](../gradle-plugin/src/main/kotlin/com/nexustools/genericui/plugin/GenericUiResourceCompilerTask.kt) merges/generates `res/drawable/`, `res/mipmap-*/`, `res/values/strings.xml`, `res/values/colors.xml`, `res/values/dimens.xml`, `res/raw/`, `res/font/`, and `res/xml/` in the synthesized Android application module and precompiles `.scss` to `PrecompiledStyleSheet`. | [`AndroidResourceResolverImpl`](../mobile/android/src/androidMain/kotlin/com/nexustools/genericui/android/AndroidResourceResolverImpl.kt) maps `ResourceID` to the compiled Android `R.<type>.<name>` identifier via `Context.resources` (`getIdentifier`, `getString`, `getColor`, `getDimensionPixelSize`, `openRawResource`). |
| **macOS (`:desktop:macos`) & iOS (`:mobile:ios`)** | Packages assets into the Apple `.app` / `.framework` bundle (`Contents/Resources/` on macOS, `Bundle.main` on iOS, `Localizable.strings`, `Assets.xcassets`) and precompiles `.scss` to `PrecompiledStyleSheet`. | [`MacosResourceResolverImpl`](../desktop/macos/src/commonMain/kotlin/com/nexustools/genericui/macos/MacosResourceResolverImpl.kt) (with `MacosCursorTranslator` for `NSCursor`) & [`IosResourceResolverImpl`](../mobile/ios/src/commonMain/kotlin/com/nexustools/genericui/ios/IosResourceResolverImpl.kt) resolve paths, URLs, and localized strings via `NSBundle.mainBundle`. |
| **Linux GTK (`:desktop:gtk`) & Qt (`:desktop:qt`)** | Emits `.gresource.xml` (GTK) and `.qrc` (Qt) descriptors plus embedded resource tables and `PrecompiledStyleSheet` objects linked into the native executable. | [`GtkResourceResolverImpl`](../desktop/gtk/src/commonMain/kotlin/com/nexustools/genericui/gtk/GtkResourceResolverImpl.kt) (`GResource` `resource:///...`) and [`QtResourceResolverImpl`](../desktop/qt/src/commonMain/kotlin/com/nexustools/genericui/qt/QtResourceResolverImpl.kt) (`QResource` `:/...`) resolve bytes and URIs from memory. |
| **JVM Swing (`:desktop:swing`)** | Packages resources into the JVM JAR classpath alongside compiled value tables and `PrecompiledStyleSheet` classes. | [`SwingResourceResolverImpl`](../desktop/swing/src/jvmMain/kotlin/com/nexustools/genericui/swing/SwingResourceResolverImpl.kt) resolves `ResourceID`s to `ClassLoader.getResource(...)` URLs and `getResourceAsStream(...)` byte streams. |

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
