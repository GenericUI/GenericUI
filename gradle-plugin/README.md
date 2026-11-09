# `:gradle-plugin` — GenericUI Gradle Plugin (`com.nexustools.genericui`)

## Overview
`:gradle-plugin` implements the `com.nexustools.genericui` Gradle plugin, which powers GenericUI's **Single-`commonMain` Consumer Architecture**, **Build-Time `.scss` Precompilation (`SassKotlinPrecompiler`)**, **Windows `.cur` Custom Cursor Generation (`WindowsCurResourceGenerator`)**, and **8-Backend Launcher & Resource Compilation Pipeline**.

---

## Plugin Architecture & Key Tasks

| Class | Package | Responsibility |
|-------|---------|----------------|
| `GenericUiGradlePlugin` | `com.nexustools.genericui.plugin` | Registers the `genericui { ... }` DSL extension and wires code, stylesheet, cursor, and resource generation tasks into Kotlin Multiplatform target compilations |
| `GenericUiExtension` | `com.nexustools.genericui.plugin` | Type-safe DSL configuring `app { ... }`, `backends { ... }`, `window { ... }`, `theme { mode = "auto" \| "dark" \| "light"; primaryColor = ...; light { ... }; dark { ... } }`, `styles { globalStyleSheet = "..." }`, `android { manifest { ... } services { ... } }`, `extensions { ... }`, and `testing { visualOnWindows = ... }` |
| `GenericUiSassCompileTask` & `SassKotlinPrecompiler` | `com.nexustools.genericui.plugin` | Compiles `.scss` / `.sass` / `.css` files in `src/commonMain/resources/**` into typed `PrecompiledStyleSheet` (`CompiledStyleRule`) `.kt` objects for non-web targets and `.css` bundles for `:web`, registering each stylesheet as `ResourceType.STYLE` (`R.style.<name>` / `Resource.style.<name>`) |
| `WindowsCurResourceGenerator` | `com.nexustools.genericui.plugin` | Converts `.png` and `.svg` cursor assets at build time into valid Windows `.cur` binary files (`ICONDIR` `idType = 2`, `ICONDIRENTRY` `wXHotSpot` & `wYHotSpot`, 32-bit BGRA DIB + 1-bpp AND mask) under `backends/windows/cursors/<name>.cur` |
| `GenericUiGenerateLaunchersTask` & `GenericUiGenerateAndroidActivitiesTask` | `com.nexustools.genericui.plugin` | Scans `src/commonMain/kotlin` for `@MainAppFragment` and `@AppFragment` annotations and generates platform launchers (`WindowsLauncher`, `MacosLauncher`, `GtkLauncher`, `QtLauncher`, `SwingLauncher`, `GeneratedAndroidApplication` + 1:1 `AppCompatActivity` subclasses + `AndroidManifest.xml`, `IosLauncher`, `WebLauncher`, and `GeneratedWindowsVisualTestLauncher`), automatically emitting `app.setThemeConfig(ThemeConfig(...))`, `app.addStyleSheet(...)`, and `app.setTheme(...)` when configured |
| `GenericUiCompileResourcesTask` & `GenericUiGenerateAndroidResourcesTask` | `com.nexustools.genericui.plugin` | Compiles `src/commonMain/resources/` (`values/strings.xml`, `values-<lang>/strings.xml`, `values/colors.xml`, `values/dimens.xml`, `values/themes.xml`, `styles/*.scss`, `layout/*.xml`, `drawable/*`, `raw/*`, `font/*`) into typed `R.kt` / `Resource.kt` accessors, Android Material theme XMLs (`res/values/genericui_theme_colors.xml`, `res/values/themes.xml`, `res/values-night/themes.xml` with `@style/Theme.GenericUI.App`), Web `gui/genericui-theme.css` (`<link id="genericui-build-theme-css">`), and native platform resource bundles |

---

## Consumer `build.gradle.kts` Configuration

```kotlin
plugins {
    id("com.nexustools.genericui")
}

genericui {
    app {
        id = "com.example.myapp"
        name = "My App"
        version = "1.0.0"
    }

    backends {
        all()
    }

    window {
        width = 1280
        height = 800
        resizable = true
    }

    theme {
        mode = "auto" // "auto" (default), "dark", or "light"
        primaryColor = "#2563EB"
        secondaryColor = "#64748B"
        accentColor = "#4F46E5"
        successColor = "#16A34A"
        dangerColor = "#DC2626"
        warningColor = "#F59E0B"
        infoColor = "#0EA5E9"
        lightColor = "#F8FAFC"
        darkColor = "#1E293B"
        backgroundColor = "#F8FAFC"
        surfaceColor = "#FFFFFF"
        textPrimaryColor = "#0F172A"
        textSecondaryColor = "#475569"
        borderColor = "#CBD5E1"
        light {
            backgroundColor = "#F8FAFC"
            surfaceColor = "#FFFFFF"
            textPrimaryColor = "#0F172A"
        }
        dark {
            primaryColor = "#3B82F6"
            backgroundColor = "#0F172A"
            surfaceColor = "#1E293B"
            textPrimaryColor = "#F8FAFC"
        }
    }

    styles {
        compileSass = true
        precompileToKotlin = true
        generateWebCss = true
        globalStyleSheet = "styles/main.scss"
    }

    extensions {
        dsl()
        alien()
    }

    android {
        minSdk = 24
        compileSdk = 34
        targetSdk = 34
        manifest {
            usesPermissions("android.permission.INTERNET")
            usesFeatures("android.hardware.touchscreen" to false)
            metaData("com.example.CHANNEL" to "production")
        }
        services {
            service(
                name = "com.example.myapp.BackgroundSyncService",
                exported = false,
                foregroundServiceType = "dataSync"
            )
        }
    }

    testing {
        visualOnWindows = false
    }
}
```

---

## Build-Time `.scss` Precompilation (`SassKotlinPrecompiler`) & `IApplication.addStyleSheet`

1. **`ResourceType.STYLE` (`R.style.<name>` / `Resource.style.<name>`)**:
   - Every `.scss`, `.sass`, or `.css` file placed in `src/commonMain/resources/styles/` (or any subdirectory of `src/commonMain/resources/`) is registered as `ResourceType.STYLE` and exposed as `R.style.<name>` / `Resource.style.<name>`.
2. **Non-Web Precompilation to `PrecompiledStyleSheet`**:
   - `GenericUiSassCompileTask` (`SassKotlinPrecompiler`) parses variables, nested selectors, pseudo-classes (`:hover`, `:focus`, `:active`, `:disabled`, `:checked`), `!important` flags, transitions, custom cursors (`cursor: url("@drawable/...") x y, pointer`), and `@color/...`, `@drawable/...`, `@dimen/...`, `@font/...`, and `@string/...` resource references at build time.
   - Emits a generated `<Name>PrecompiledStyleSheet` object implementing `PrecompiledStyleSheet` in `build/generated/genericui/kotlin/com/nexustools/genericui/generated/styles/` and registers it in `StyleSheetRegistry` so zero SASS/CSS text parsing occurs at runtime.
3. **Web Compilation to `.css`**:
   - Emits standard `.css` files into `build/generated/genericui/web-css/styles/<name>.css` and bundles them into the `:web` distribution, where `WebStyleSheetManager` links them via `<link rel="stylesheet" href="styles/<name>.css">` in `document.head`.
4. **Automatic Startup Wiring in Generated Launchers**:
   - When `styles { globalStyleSheet = "styles/main.scss" }` (or `theme { styleSheet = "styles/main.scss" }`) is configured, `GenericUiGenerateLaunchersTask` and `GenericUiGenerateAndroidActivitiesTask` (`Generated<Target>LauncherMain.kt`, `Generated<Target>AppDescriptor.kt`, `GeneratedAndroidApplication.kt`) automatically emit `app.addStyleSheet(...)` at startup.
   - At runtime, applications can dynamically attach or detach stylesheets via `app.addStyleSheet(R.style.main)` and `app.removeStyleSheet(R.style.main)`.

---

## Build-Time Windows `.cur` Cursor Generation (`WindowsCurResourceGenerator`)

During `compileGenericUiResources`, `WindowsCurResourceGenerator` converts `.png` and `.svg` cursor assets into standards-compliant Windows `.cur` binaries (`ICONDIR` with `idType = 2`, `ICONDIRENTRY` with `wXHotSpot` and `wYHotSpot`, 40-byte `BITMAPINFOHEADER`, 32-bit BGRA XOR bitmap + 1-bpp AND mask) output under `backends/windows/cursors/<name>.cur`. At runtime, `:desktop:windows` also includes `WindowsCurCodec` to synthesize `.cur` bytes and `HCURSOR` handles dynamically from any `CssCustomCursor`.

---

## Multiplatform Resource Compilation (`GenericUiCompileResourcesTask`)

| Target Platform | Compiled Output Format | Runtime Resolver |
|-----------------|------------------------|------------------|
| Windows (`:desktop:windows`) | Win32 `genericui_resources.rc` $\rightarrow$ `windres` `.res` (`STRINGTABLE` + `RCDATA`) + `.cur` cursors | `WindowsResourceResolverImpl` (`FindResourceW` / `LoadResource`) |
| macOS (`:desktop:macos`) | Apple `.app/Contents/Resources/` + `.lproj/Localizable.strings` | `MacosResourceResolverImpl` (`NSBundle.mainBundle`) |
| Linux GTK (`:desktop:gtk`) | `genericui.gresource.xml` $\rightarrow$ `glib-compile-resources` `.gresource` | `GtkResourceResolverImpl` (`GResource`) |
| Linux Qt (`:desktop:qt`) | `genericui_resources.qrc` $\rightarrow$ `rcc` `.rcc` bundle | `QtResourceResolverImpl` (`QResource`) |
| JVM Swing (`:desktop:swing`) | Classpath JAR resources | `SwingResourceResolverImpl` (`ClassLoader.getResource`) |
| Android (`:mobile:android`) | Standard Android `res/values*/`, `res/values/genericui_theme_colors.xml`, `res/values/themes.xml`, `res/values-night/themes.xml` (`@style/Theme.GenericUI.App`), `res/drawable/`, `res/raw/`, `res/font/` | `AndroidResourceResolverImpl` (`Context.resources`) + `AndroidApplicationImpl` |
| iOS (`:mobile:ios`) | Apple bundle resources + `.lproj/Localizable.strings` | `IosResourceResolverImpl` (`NSBundle.mainBundle`) |
| Web (`:web`) | `assets/<type>/<fileName>` + compiled `styles/<name>.css` + pre-generated `gui/genericui-theme.css` (`<link id="genericui-build-theme-css">`) | `WebResourceResolverImpl` (`assets/...` URLs) + `WebStyleSheetManager` (`<style id="genericui-runtime-theme-css">`) |
