# `:gradle-plugin` — GenericUI Gradle Plugin (`com.nexustools.genericui`)

## Overview
`:gradle-plugin` implements the `com.nexustools.genericui` Gradle plugin, which powers GenericUI's **Single-`commonMain` Consumer Architecture**, **Build-Time `.scss` Precompilation (`SassKotlinPrecompiler`)**, **Windows `.cur` Custom Cursor Generation (`WindowsCurResourceGenerator`)**, **Extension Contributor SPI (`GenericUiExtensionContributor`)**, **Consumer Native / JNI / CInterop Builds (`nativebuild/`)**, and **9-Backend Launcher, Shared Library, & Three-Tier Resource Compilation Pipeline**.

---

## Plugin Architecture & Key Tasks

| Class / Package | Package | Responsibility |
|-------|---------|----------------|
| `GenericUiGradlePlugin` | `com.nexustools.genericui.plugin` | Registers the `genericui { ... }` DSL extension (tracking 49 core/extension/test modules in `KNOWN_GENERICUI_MODULES` of 51 root subprojects) and wires code, schema, serviceworker, stylesheet, cursor, JNI/CInterop, shared-lib, and resource tasks into KMP compilations |
| `GenericUiExtension` & `GenericUiVariantConfig` | `com.nexustools.genericui.plugin` | Type-safe DSL configuring `app`, `window` (`alwaysOnTop`, `aspectRatio`), `theme`, `styles`, `backends` (all 9 targets), `extensions` (all 22 extensions + `custom`/`option`), `android { manifest; services }`, `jni`, `cinterops`, `nativeLibraries`, and per-variant scopes (`0 -> 6`) |
| `GenericUiGenerateSchemaSourcesTask` & `GenericUiGenerateServiceWorkerCodecsTask` | `com.nexustools.genericui.plugin` | Scans consumer Kotlin sources for `@SchemaData` (`<ClassName>SchemaDescriptor.kt` + `GeneratedSchemaRegistrar.kt`) and validates `@MainServiceWorker` + emits `<Msg>ServiceWorkerCodec.kt` + `GeneratedServiceWorkerCodecRegistrar.kt` (`@ServiceWorkerMessage`) |
| `GenericUiExtensionContributor` & `GenericUiExtensionContributorRegistry` | `com.nexustools.genericui.plugin.spi` | Extensible SPI (built-in `PipExtensionContributor`, `SchemaExtensionContributor`, `ServiceWorkerExtensionContributor`, `ServiceLoader`, `META-INF/genericui/extensions/*.json`) contributing Android manifest/service/receiver hooks, Desktop secondary executables (`serviceworker` + `-gtk`/`-qt` variants), Apple mobile `Info.plist`, and Web `./serviceworker.js` hooks |
| `GenericUiSassCompileTask` & `SassKotlinPrecompiler` | `com.nexustools.genericui.plugin` | Compiles `.scss` / `.sass` / `.css` files in `src/commonMain/resources/**` into typed `PrecompiledStyleSheet` (`CompiledStyleRule`) `.kt` objects for non-web targets and `.css` bundles for `:web`, registering each stylesheet as `ResourceType.STYLE` (`R.style.<name>` / `Resource.style.<name>`) |
| `WindowsCurResourceGenerator` | `com.nexustools.genericui.plugin` | Converts `.png` and `.svg` cursor assets at build time into valid Windows `.cur` binary files (`ICONDIR` `idType = 2`, `ICONDIRENTRY` `wXHotSpot` & `wYHotSpot`, 32-bit BGRA DIB + 1-bpp AND mask) |
| `GenericUiGenerateLaunchersTask` & `GenericUiGenerateAndroidActivitiesTask` | `com.nexustools.genericui.plugin` | Scans `src/commonMain/kotlin` for `@MainAppFragment` and `@AppFragment` annotations and generates platform launchers (`WindowsLauncher`, `MacosLauncher`, `GtkLauncher`, `QtLauncher`, `SwingLauncher`, `GeneratedAndroidApplication` + 1:1 `AppCompatActivity` subclasses + `AndroidManifest.xml`, `IosLauncher`, `IpadosLauncher`, `WebLauncher`, and `GeneratedWindowsVisualTestLauncher`), wiring `ThemeConfig`, `addStyleSheet(...)`, SPI installers, and `SecondaryDesktopExecutableContribution` binaries |
| `GenericUiResourceCompilerTask` & `GenericUiNativeResourceEmbedder` | `com.nexustools.genericui.plugin` | Compiles `src/commonMain/resources/` into isolated consumer `R.kt`/`RC.kt`/`Resource.kt`/`GeneratedResourceRegistry.kt` (`0x7f`), Android flat `res/` (`gui_*`/`gui_ext_*`/`app_*`), Swing/Web/Apple hierarchical trees (`gui/`/`gui/ext/<cat>/<ext>/`/`app/` + web-root `./serviceworker.js`), and prefix-free native binary/shared-lib resource tables (`RT_RCDATA`, Mach-O `__TEXT,__const`, ELF `.rodata`) |
| `GenericUiBuildNativeSharedLibsTask`, `GenericUiKonanCompilerShim`, & `WindowsCoffObjectPartitioner` | `com.nexustools.genericui.plugin` | Compiles core, native desktop backend, `njs`, enabled extension/DSL, and test shared libraries (partitioning MinGW COFF objects when exports exceed 65,535 symbols) |
| `GenericUiFetchNativeSourceArchiveTask`, `GenericUiCompileJniTask`, & `GenericUiJniToolchainResolver` | `com.nexustools.genericui.plugin.nativebuild` | Fetches verified source archives and compiles consumer C/C++/CMake JNI libraries (`-Wl,-z,max-page-size=16384` Android NDK or zero-NDK Konan LLVM + `target-sysroot-1-android_ndk` + JVM host `.so`/`.dylib`/`.dll` + Swing `META-INF/native/<os>-<arch>/`), paired with `cinterops` and `nativeLibraries` linking |

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
        schema()
        serviceWorker {
            autostart = false
            trayIcon = null
            openWindowOnLaunch = true
            androidForegroundServiceType = "specialUse"
            androidSpecialUseFgsSubtype = "GenericUI background ServiceWorker execution"
        }
    }

    jni {
        library("my_native") {
            sources("src/native/cpp")
            abis("arm64-v8a", "x86_64")
            buildHostLibraryForTests = true
        }
    }

    cinterops {
        create("my_c_api") {
            defFile("src/native/my_c_api.def")
            packageName("com.example.myapp.native")
        }
    }

    nativeLibraries {
        link("mylib")
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

## Three-Tier Multiplatform Resource Compilation (`GenericUiResourceCompilerTask`)

| Target Platform | Compiled Output Format | Runtime Resolver |
|-----------------|------------------------|------------------|
| Windows (`:desktop:windows`) *(Tier 3)* | `GenericUiNativeResourceEmbedder` (`RT_RCDATA`, `resId.nativeResourceKey`) embedded directly inside `genericui.dll`, `genericui-ext-<ext>.dll`, & `<app>.exe` (`WindowsResourceResolverImpl`) + `.cur` cursors | `WindowsResourceResolverImpl` (`GetModuleHandleW` + `FindResourceW` / `LoadStringW`) |
| macOS (`:desktop:macos`) *(Tier 3)* | Mach-O `__TEXT,__const` tables (`genericui_res_lookup_<group>` / `genericui_res_lookup_app`, including unwrapped `tray_icon_png`/`tray_icon_svg`) sealed inside signed `.dylib`s & `<app>` (`Contents/Resources/` contains only `icon.icns` + optional MoltenVK ICD) | `MacosResourceResolverImpl` (`dlsym`) |
| Linux GTK4 & Qt6 (`:desktop:gtk`, `:desktop:qt`) *(Tier 3)* | ELF `.rodata` tables (`genericui_res_lookup_<group>` / `genericui_res_lookup_app`) embedded directly inside `libgenericui*.so` & `<app>` (`--export-dynamic-symbol=genericui_res_lookup_app`) | `LinuxResourceResolverImpl` (`dlsym`) |
| JVM Swing (`:desktop:swing`) *(Tier 1)* | Hierarchical `gui/...`, `gui/ext/<cat>/<ext>/...`, & `app/...` staged in fat JAR (+ `META-INF/native/<os>-<arch>/` for host JNI libs loaded via `JniLibraryLoader`) | `SwingResourceResolverImpl` (`ClassLoader.getResource`) |
| Android (`:mobile:android`) *(Tier 2)* | Flat Android `res/values*/`, `res/values/genericui_theme_colors.xml`, `res/values/themes.xml`, `res/values-night/themes.xml` (`@style/Theme.GenericUI.App`), flat `gui_*` / `gui_ext_*` / `app_*` assets, & 16KB-aligned `lib/<abi>/*.so` | `AndroidResourceResolverImpl` (`Context.resources`) + `AndroidApplicationImpl` |
| iOS & iPadOS (`:mobile:ios`) *(Tier 1 + Tier 2)* | Hierarchical `Resources/gui` + `Resources/app` folders alongside flat `Assets.xcassets` + `.lproj/Localizable.strings` inside generated `.app`/Xcode bundles | `AppleResourceResolverNativeImpl` (`NSBundle.mainBundle`) |
| Web (`:web`) *(Tier 1)* | Hierarchical `gui/...`, `gui/ext/<cat>/<ext>/...`, `app/...`, `styles/<name>.css`, `gui/genericui-theme.css`, `loader.js`, `manifest.webmanifest`, & web-root `./serviceworker.js` (+ SPI `${resolvedBase}.sw.js\|wasm` & `WebSwPluginScript`) | `WebResourceResolverImpl` (`DataUrlCodec` preloaded Data-URLs) + `WebStyleSheetManager` |
