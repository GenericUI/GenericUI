# GenericUI Android-Style Multi-Backend Resource System (`Resource` & `Resource.ResourceID`)

> **Root Repository**: [**GenericUI (`../README.md`)**](../README.md) | **Master Docs Index**: [**`docs/README.md`**](./README.md)  
> **Related Guides**: [Gradle Plugin (`gradle-plugin.md`)](./gradle-plugin.md) · [Fragments & Windows (`fragments-and-windows.md`)](./fragments-and-windows.md) · [Application (`application.md`)](./application.md) · [Utilities (`utils.md`)](./utils.md) · [Platform Backends (`backends.md`)](./backends.md)

GenericUI provides an Android-style resource compilation and runtime resolution pipeline (`com.nexustools.genericui.resources.*` and `com.nexustools.genericui.res.*`). Resources authored once in `src/commonMain/resources` (or in any variant source set or multiplatform library module) are compiled into a type-safe `Resource` (and `R` / `RC`) class exposing [`ResourceID`](../common/src/commonMain/kotlin/com/nexustools/genericui/resources/ResourceID.kt) constants and translated into native backend formats across all 8 target platforms.

---

## 1. Authoring Resources (`src/commonMain/resources`)

The GenericUI Gradle plugin (`GenericUiResourceScanner`, `GenericUiAndroidXmlResourceParser`, and `GenericUiAndroidXmlResourceGenerator`) supports both **standard Android resource directory layouts** and **flat project assets**:

```text
src/commonMain/resources/
├── drawable/
│   ├── icon_app.svg
│   ├── banner_hero.png
│   └── ic_play.xml              # Android VectorDrawable or SVG
├── mipmap/
│   └── ic_launcher.png
├── values/
│   ├── strings.xml              # <resources><string name="app_title">My App</string></resources>
│   ├── colors.xml               # <resources><color name="accent_primary">#2563EB</color></resources>
│   ├── dimens.xml               # <resources><dimen name="card_padding">16dp</dimen></resources>
│   ├── integers.xml             # <resources><integer name="max_columns">4</integer></resources>
│   └── bools.xml                # <resources><bool name="enable_ animations">true</bool></resources>
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
3. **Multi-Module Resource Merging**: Library modules applying `id("com.nexustools.genericui")` generate their own `Resource` class and packaged resource manifest; downstream application modules automatically merge transitive library resources into the final backend bundle without ID collisions.

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
        val banner_hero: ResourceID = ResourceID(0x7f010002, ResourceType.DRAWABLE, "banner_hero", "drawable/banner_hero.png")
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

    object raw {
        val default_preset: ResourceID = ResourceID(0x7f060001, ResourceType.RAW, "default_preset", "raw/default_preset.json")
    }
}

typealias R = Resource
```

---

## 3. Using `Resource.ResourceID` & `ResourceResolver` in `commonMain`

Both [`ResourceID`](../common/src/commonMain/kotlin/com/nexustools/genericui/resources/ResourceID.kt) extension functions and [`ResourceResolver`](../common/src/commonMain/kotlin/com/nexustools/genericui/resources/ResourceResolver.kt) resolve resources through the active backend's [`ResourceResolverNativeImpl`](../common/src/commonMain/kotlin/com/nexustools/genericui/resources/ResourceResolverNativeImpl.kt):

| API Call | Return Type | Description |
| :--- | :--- | :--- |
| `Resource.string.welcome_user.getString("Alex")`<br/>`ResourceResolver.getString(id, *formatArgs)` | `String` | Resolves a localized string and substitutes `%s`, `%d`, `%1$s`, `%1$d` format arguments. |
| `Resource.color.accent_primary.getColor()`<br/>`ResourceResolver.getColor(id)` | `RgbaColor` | Resolves a color resource into an `RgbaColor`. |
| `Resource.dimen.card_padding.getDimenPx()`<br/>`ResourceResolver.getDimenPx(id)` | `Float` | Resolves a dimension (`dp`, `sp`, `px`) into display pixels. |
| `Resource.drawable.icon_app.getUrl()`<br/>`ResourceResolver.getUrl(id)` | `String` | Resolves a file-backed resource into a backend URL/URI (e.g., `"assets/drawable/icon_app.svg"` on `:web`). |
| `Resource.raw.default_preset.getBytes()`<br/>`ResourceResolver.getBytes(id)` | `ByteArray?` | Loads raw binary bytes from the backend's native resource container (e.g., embedded `.exe` `RT_RCDATA` on Windows). |
| `Resource.drawable.banner_hero.getImageBuffer()`<br/>`ResourceResolver.getImageBuffer(id)` | `ImageBuffer?` | Loads and decodes a drawable resource directly into an in-memory `:common` `ImageBuffer`. |
| `Resource.drawable.icon_app.getDrawable()`<br/>`ResourceResolver.getDrawable(id)` | `DrawableResource?` | Returns the `DrawableResource` descriptor (vector SVG markup, path, or intrinsic size). |

### Passing `Resource.ResourceID` Directly to UI Components

UI components and window APIs accept `ResourceID` directly:

```kotlin
import com.example.myapp.res.Resource
import com.nexustools.genericui.component.media.Image
import com.nexustools.genericui.component.typography.Heading
import com.nexustools.genericui.resources.getColor
import com.nexustools.genericui.resources.getImageBuffer
import com.nexustools.genericui.resources.getString

// 1. Set window icon from ResourceID
hostingWindow?.setWindowIcon(Resource.drawable.icon_app)

// 2. Resolve localized strings and colors
val heading = Heading(Resource.string.welcome_user.getString("Kara"), level = 2)
val accent = Resource.color.accent_primary.getColor()

// 3. Display or decode an image from ResourceID
val heroImage = Image(src = Resource.drawable.banner_hero.getUrl())
val pixelBuffer = Resource.drawable.banner_hero.getImageBuffer()
```

---

## 4. Per-Backend Build Packaging & Runtime Translation

| Backend Target | Build-Time Resource Packaging | Runtime `Resource.ResourceID` Resolution (`ResourceResolverNativeImpl`) |
| :--- | :--- | :--- |
| **Web (`:web` — `wasmJs` & `js`)** | Copies drawables, fonts, and raw assets into `build/dist/web/assets/<type>/<name>.<ext>` and compiles `values/*.xml` into the generated resource table. | [`WebResourceResolverImpl`](../web/src/commonMain/kotlin/com/nexustools/genericui/web/WebResourceResolverImpl.kt) translates file-backed `ResourceID`s directly to **Web URLs** (`"assets/drawable/icon_app.svg"`) for DOM `<img>`, CSS `url(...)`, `<audio>`, `<video>`, and `fetch()`, and resolves strings/colors/dimens from the compiled table. |
| **Windows (`:desktop:windows`)** | [`GenericUiResourceCompilerTask`](../gradle-plugin/src/main/kotlin/com/nexustools/genericui/plugin/GenericUiResourceCompilerTask.kt) & [`GenericUiCompileWindowsResourcesTask`](../gradle-plugin/src/main/kotlin/com/nexustools/genericui/plugin/GenericUiCompileWindowsResourcesTask.kt) generate a Win32 `.rc` script (`generated_resources.rc` + `resource_ids.h`) mapping each `ResourceID.id` to `RT_RCDATA`, `RT_ICON`, `RT_GROUP_ICON`, or `RT_STRING`, compile it via `windres` to COFF `.res`, and link it directly into the `.exe` binary. | [`WindowsResourceResolverImpl`](../desktop/windows/src/commonMain/kotlin/com/nexustools/genericui/windows/WindowsResourceResolverImpl.kt) loads bytes, icons, and strings directly from the running `.exe` module in memory (`FindResourceW` / `LoadResource` / `LockResource` / `SizeofResource` / `LoadStringW`) with zero external loose files required. |
| **Android (`:mobile:android`)** | [`GenericUiResourceCompilerTask`](../gradle-plugin/src/main/kotlin/com/nexustools/genericui/plugin/GenericUiResourceCompilerTask.kt) merges/generates `res/drawable/`, `res/mipmap-*/`, `res/values/strings.xml`, `res/values/colors.xml`, `res/values/dimens.xml`, `res/raw/`, `res/font/`, and `res/xml/` in the synthesized Android application module. | [`AndroidResourceResolverImpl`](../mobile/android/src/androidMain/kotlin/com/nexustools/genericui/android/AndroidResourceResolverImpl.kt) maps `ResourceID` to the compiled Android `R.<type>.<name>` identifier via `Context.resources` (`getIdentifier`, `getString`, `getColor`, `getDimensionPixelSize`, `openRawResource`). |
| **macOS (`:desktop:macos`) & iOS (`:mobile:ios`)** | Packages assets into the Apple `.app` / `.framework` bundle (`Contents/Resources/` on macOS, `Bundle.main` on iOS, `Localizable.strings`, `Assets.xcassets`). | [`MacosResourceResolverImpl`](../desktop/macos/src/commonMain/kotlin/com/nexustools/genericui/macos/MacosResourceResolverImpl.kt) & [`IosResourceResolverImpl`](../mobile/ios/src/commonMain/kotlin/com/nexustools/genericui/ios/IosResourceResolverImpl.kt) resolve paths, URLs, and localized strings via `NSBundle.mainBundle`. |
| **Linux GTK (`:desktop:gtk`) & Qt (`:desktop:qt`)** | Emits `.gresource.xml` (GTK) and `.qrc` (Qt) descriptors plus embedded resource tables linked into the native executable. | [`GtkResourceResolverImpl`](../desktop/gtk/src/commonMain/kotlin/com/nexustools/genericui/gtk/GtkResourceResolverImpl.kt) (`GResource` `resource:///...`) and [`QtResourceResolverImpl`](../desktop/qt/src/commonMain/kotlin/com/nexustools/genericui/qt/QtResourceResolverImpl.kt) (`QResource` `:/...`) resolve bytes and URIs from memory. |
| **JVM Swing (`:desktop:swing`)** | Packages resources into the JVM JAR classpath alongside compiled value tables. | [`SwingResourceResolverImpl`](../desktop/swing/src/jvmMain/kotlin/com/nexustools/genericui/swing/SwingResourceResolverImpl.kt) resolves `ResourceID`s to `ClassLoader.getResource(...)` URLs and `getResourceAsStream(...)` byte streams. |

---

## 5. Configuring `resources { ... }` in `build.gradle.kts`

Resource generation is enabled by default whenever `id("com.nexustools.genericui")` is applied. You can customize the generated package name or add extra resource directories via `genericui { resources { ... } }`:

```kotlin
genericui {
    app {
        id = "com.example.myapp"
        name = "My Application"
    }

    resources {
        enabled = true
        packageName = "com.example.myapp.res"
        translateForAllBackends = true
        sourceDir("src/commonMain/resources")
    }
}
```
