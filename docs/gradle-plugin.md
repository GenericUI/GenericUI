# GenericUI Gradle Plugin Guide (`:gradle-plugin`)

> **Root Repository**: [**GenericUI (`../README.md`)**](../README.md) | **Master Docs Index**: [**`docs/README.md`**](./README.md)  
> **Related Guides**: [iPadOS Backend (`ipados.md`)](./ipados.md) · [Shared Apple (`apple.md`)](./apple.md) · [Shared JVM (`jvm.md`)](./jvm.md) · [Fragments & Windows (`fragments-and-windows.md`)](./fragments-and-windows.md) · [Resources (`resources.md`)](./resources.md) · [Application & Theming (`application.md`)](./application.md) · [SourceSets (`sourcesets.md`)](./sourcesets.md) · [Platform Backends (`backends.md`)](./backends.md) · [Extensions (`extensions.md`)](./extensions.md)

The `:gradle-plugin` module provides `id("com.nexustools.genericui")` (`GenericUiGradlePlugin`) and `id("com.nexustools.genericui.settings")` (`GenericUiSettingsPlugin`) to build complete 9-target multiplatform applications from a **single `src/commonMain` source set** with zero per-backend boilerplate.

---

## 1. What the Plugin Automates

When `id("com.nexustools.genericui")` is applied:
1. **Annotation-Driven Fragment, Application, Schema, & Worker Discovery**: Scans Kotlin sources (`GenericUiFragmentScanner`) for `@MainAppFragment`, `@AppFragment`, and `@AppApplication`, plus extension-registered annotations (`@SchemaData`, `@MainServiceWorker`, `@ServiceWorkerMessage`) via `GenericUiExtensionContributor.annotationScanSpecs`.
2. **Target Specificity Resolution (`genericuiValidateApp`)**: Resolves the most specific `@MainAppFragment` for each enabled backend across all 7 [`AppTarget`](../common/src/commonMain/kotlin/com/nexustools/genericui/enums/system/AppTarget.kt) ranks (`6 → 5 → 4 → 3 → 2 → 1 → 0`), failing immediately on ties or missing targets, and invokes active `GenericUiExtensionContributor.validate(...)` checks.
3. **Extension Codegen (`generateGenericUiSchemas`, `generateGenericUiServiceWorkerCodecs`)**: Emits reflection-free `<ClassName>SchemaDescriptor.kt` + `GeneratedSchemaRegistrar.kt` for `@SchemaData` classes (`extensions { schema() }`), validates `@MainServiceWorker` constraints (<= 1 per enabled backend, `ServiceWorker` inheritance, zero-arg/all-default constructor), and generates `<Msg>ServiceWorkerCodec.kt` + `GeneratedServiceWorkerCodecRegistrar.kt` for `@ServiceWorkerMessage` classes (`extensions { serviceWorker { ... } }`).
4. **Generated Per-Backend Launchers & Descriptors (`generateGenericUiLaunchers`)**: Generates `GenericUiAppDescriptor` and launcher entry points (`WindowsLauncher`, `MacosLauncher`, `GtkLauncher`, `QtLauncher`, `SwingLauncher`, `AndroidLauncher`, `IosLauncher`, `IpadosLauncher`, `WebLauncher`) plus `SecondaryDesktopExecutableContribution` targets (e.g., `serviceworker` binaries & `-gtk`/`-qt` variants staged via `GenericUiMultiplatformConfigurator.registerBuildTargetTasks`), wiring window geometry (`alwaysOnTop`, windowed `aspectRatio`), Apple icon wrapping flags (`appleIconExplicitlyProvided`), `ThemeConfig`, `addStyleSheet(...)`, contributor `providerInstallerCalls`, and system CLI `FragmentBundle` arguments.
5. **Android 1:1 Activities, Material XML, ServiceWorker Components, & Xcode iOS/iPadOS App Generation**:
   - **Android (`generateGenericUiAndroidActivities`, `generateGenericUiAndroidManifest`)**: Synthesizes `<FragmentName>Activity : GenericUiFragmentActivity()`, `AndroidActivityRegistry`, `@style/Theme.GenericUI.App` XMLs, SPI-contributed `<service>`/`<receiver>`/`BOOT_COMPLETED` components & Activity hooks (`AndroidExtensionContribution`), and `AndroidManifest.xml`.
   - **iOS & iPadOS (`generateGenericUiIosApp`, `generateGenericUiIpadosApp`)**: Generates dedicated Swift/UIKit Xcode projects (`build/genericui/iosApp` with `UIDeviceFamily = 1`, `build/genericui/ipadosApp` with `UIDeviceFamily = 2`) and composes `AppIcon` assets. When both `ios()` and `ipados()` are enabled, compiles disambiguated frameworks (`iosDebugFramework` / `ipadosDebugFramework`).
6. **Three-Tier Resource & Stylesheet Compilation (`compileGenericUiResources`, `compileGenericUiSass`)**: Generates isolated scoped `R.kt` / `RC.kt` / `Resource.kt` / `GeneratedResourceRegistry.kt` declarations (`0x7f` consumer app IDs registered in `ResourceTable`), precompiles `.scss` stylesheets (`Resource.style.<name>`), and packages Tier 1 hierarchical blobs (`Web` + web-root `./serviceworker.js`, `Swing` JAR, Apple `.app`), Tier 2 flat blobs (`Android` `res/`, Apple `Assets.xcassets`), and Tier 3 prefix-free native binary/shared-library embeddings (`GenericUiNativeResourceEmbedder` on Windows `RT_RCDATA`, macOS Mach-O `__TEXT,__const` incl. unwrapped tray icons, and Linux GTK4/Qt6 ELF `.rodata`).
7. **Consumer Native / JNI / CInterop Build (`nativebuild/`)**: Fetches source archives (`fetchGenericUiNativeSources`), compiles C/C++/CMake JNI shared libraries (`compileGenericUiJniLibraries`) for 16KB-aligned Android ABIs (with zero-NDK Konan LLVM + `target-sysroot-1-android_ndk` fallback) and JVM host test/Swing JAR `META-INF/native/` bundling, and configures Kotlin/Native `cinterops` and `nativeLibraries` links.

---

## 2. Full `genericui { ... }` DSL Reference

```kotlin
import com.nexustools.genericui.plugin.OsArchClassifier

plugins {
    id("com.nexustools.genericui")
}

genericui {
    // 1. Application Identity & Metadata
    app {
        id = "com.example.studio"
        name = "GenericUI Studio"
        version = "1.0.0"
        vendor = "NexusTools"
        description = "Cross-platform creative studio built on a single commonMain"
        packageName = "com.example.studio"
    }

    // 2. Top-Level Window Defaults (inherited by variant scopes unless overridden)
    window {
        title = "GenericUI Studio"
        width = 1440
        height = 900
        minWidth = 960
        minHeight = 600
        resizable = true
        alwaysOnTop = false          // Pin floating utility/HUD windows above regular windows
        aspectRatio(16, 9)           // Client-viewport aspect ratio lock (WindowState.NORMAL only; unconstrained when maximized/fullscreen)
        // Or double form: aspectRatio = 16.0 / 9.0
        icon = "src/commonMain/resources/drawable/icon_app.svg"
    }

    // 3. Target-Specific & Apple Icon Overrides (auto-wrapped in white squircle/full-bleed tile when omitted)
    appleIcon("src/commonMain/resources/drawable/icon_apple.svg")
    // Or configure explicitly per Apple OS:
    // macIcon("src/commonMain/resources/drawable/icon_macos.svg")      // alias: macosIcon(...)
    // iosIcon("src/commonMain/resources/drawable/icon_iphone.svg")
    // ipadosIcon("src/commonMain/resources/drawable/icon_ipad.svg")    // alias: ipadIcon(...)

    theme {
        mode = "auto"               // "auto" (default, ThemeMode.AUTO), "dark", or "light"
        primaryColor = "#2563EB"
        secondaryColor = "#64748B"
        accentColor = "#4F46E5"
        successColor = "#16A34A"
        dangerColor = "#DC2626"
        warningColor = "#F59E0B"
        infoColor = "#0EA5E9"
        backgroundColor = "#F8FAFC"
        surfaceColor = "#FFFFFF"
        textPrimaryColor = "#0F172A"
        textSecondaryColor = "#475569"
        borderColor = "#CBD5E1"
        light { backgroundColor = "#F8FAFC" }
        dark { backgroundColor = "#0F172A"; surfaceColor = "#1E293B" }
        styleSheet = "styles/main.scss"
    }

    // 4. Target Platform Backends (9 targets)
    backends {
        all() // Enables windows, macos, gtk, qt, swing, android, ios, ipados, and web (wasmJs + js)
        // Or: allExceptSwing(), allDesktop(), allDesktopExceptSwing(), allMobile()
        swing {
            enabled = true
            useLwjglGL = true
            useVulkan = true
            lwjglVersion = "3.3.6"
            targetClassifiers.add(OsArchClassifier.WINDOWS_X64)
            targetClassifiers.add(OsArchClassifier.LINUX_X64)
            targetClassifiers.add(OsArchClassifier.MACOS_ARM64)
        }
    }

    // 5. First-Party Extension Modules (22 Extensions + 19 Companion :dsl Modules)
    extensions {
        gpu()              // :extensions:graphics:gpu
        imageProcessing()  // :extensions:graphics:imageprocessing -> gpu
        overlay()          // :extensions:ui:overlay (+ :dsl)
        canvas2d()         // :extensions:graphics:canvas2d -> gpu + imageprocessing + overlay (+ :dsl)
        canvas3d()         // :extensions:graphics:canvas3d -> gpu + canvas2d + overlay (+ :dsl)
        threeJs()          // :extensions:graphics:threejs -> gpu + canvas3d (+ :dsl)
        box2d()            // :extensions:physics:box2d -> canvas2d + canvas3d (+ :dsl)
        box3d()            // :extensions:physics:box3d -> threejs + canvas3d (+ :dsl)
        gamepad()          // :extensions:game:gamepad (+ :dsl)
        engine()           // :extensions:game:engine -> gamepad (+ :dsl)
        engine2d()         // :extensions:game:engine2d -> engine + canvas2d + box2d (+ :dsl)
        engine3d()         // :extensions:game:engine3d -> engine + canvas3d + threejs + box3d (+ :dsl)
        media()            // :extensions:media:media -> overlay + schema (+ :dsl)
        mediaControls()    // :extensions:media:mediacontrols (+ :dsl)
        schema()           // :extensions:data:schema (+ :dsl) — @SchemaData descriptor codegen
        serviceWorker {    // :extensions:system:serviceworker (+ :dsl) — @MainServiceWorker + @ServiceWorkerMessage
            autostart = false
            trayIcon = null // Optional drawable path/name (unwrapped in Mach-O on macOS)
            openWindowOnLaunch = true
            androidForegroundServiceType = "specialUse"
            androidSpecialUseFgsSubtype = "GenericUI background ServiceWorker execution"
        }
        webView()          // :extensions:ui:webview (+ :dsl)
        alien()            // :extensions:ui:alien -> canvas2d (+ :dsl)
        codeEditor()       // :extensions:ui:codeeditor -> canvas2d (+ :dsl)
        aiChat()           // :extensions:ui:aichat -> codeeditor -> canvas2d (+ :dsl)
        pip()              // :extensions:ui:pip -> overlay (+ :dsl)
        dsl()              // :extensions:ui:dsl + companion :dsl modules for enabled extensions
    }

    // 6. Hierarchical Variant Overrides (rank 0 -> 6)
    desktopCommon {
        window { width = 1600; height = 960 }
    }
    appleCommon {
        // Shared across macOS, iOS, and iPadOS
    }
    ipados {
        window { aspectRatio(16, 9) }
    }
    web {
        window { title = "GenericUI Studio (Web)" }
    }

    // 7. Android-Only Manifest & Service Configuration (valid ONLY inside android { ... })
    android {
        manifest {
            minSdk = 24
            compileSdk = 35
            targetSdk = 35
            supportsPictureInPicture = true
            permissions("android.permission.INTERNET")
        }
        services {
            service("com.example.studio.playback.MediaPlaybackService") {
                exported = false
                foregroundServiceType = "mediaPlayback"
            }
        }
    }

    // 8. Progressive Web App (PWA) & Native GPU/Media Bundling
    application {
        webIcon("src/commonMain/resources/drawable/icon_app.svg")
        webManifest {
            allowInstallAsWebApp = true
            shortName = "Studio"
            display = "standalone"
            backgroundColor = "#0F172A"
        }
    }

    graphics {
        bundleAngleOnWindows = true
        bundleMoltenVkOnApple = true
        bundleShaderc = true
        bundleVlcOnDesktop = true
    }

    styles {
        compileSass = true
        generateWebCss = true
        precompileToKotlin = true
        sourceDir = "src/commonMain/resources/styles"
        globalStyleSheet = "styles/main.scss"
    }

    resources {
        enabled = true
        packageName = "com.example.studio.res"
        translateForAllBackends = true
    }

    testing {
        enabled = true
        visualOnWindows = true
    }

    // 9. Consumer C / C++ / CMake JNI & Kotlin/Native CInterop Build Configuration
    jni {
        jniLibsDirs("src/androidMain/jniLibs")
        library("my_native") {
            sourceArchive("https://example.org/native-src.tar.gz", sha256 = "...")
            archiveSources("src/*.c")
            sources("src/native/cpp")
            archiveIncludeDirs("include")
            includeDirs("src/native/include")
            defines("FEATURE_FAST=1")
            cFlags("-O3")
            cppFlags("-O3")
            linkerFlags("-lm")
            androidLinkLibraries("log", "android")
            hostLinkLibraries()
            abis("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
            minApiLevel = 24
            pageSize16Kb = true
            generateJniHeaders = true
            buildHostLibraryForTests = true
            bundleInSwingJar = true
            buildAndroidLibraries = true
        }
        cmake("external_codec") {
            listsFile("src/native/CMakeLists.txt")
            arguments("-DBUILD_SHARED_LIBS=ON")
            targets("external_codec")
            pageSize16Kb = true
            buildType = "Release"
        }
    }

    cinterops {
        create("my_c_api") {
            defFile("src/native/my_c_api.def")
            packageName("com.example.studio.native")
            headers("my_c_api.h")
            includeDirs("src/native/include")
            compilerOpts("-DFOO=1")
            linkerOpts("-lmy_c_api")
            extraOpts()
        }
    }

    nativeLibraries {
        link("my_c_api", "-lz")
        libraryDirs("src/native/prebuilt")
        linkerOpts()
    }
}
```

---

## 3. Variant Scope Resolution Chain (`GenericUiVariantScope`, `0 → 6`)

Every variant block configures a [`GenericUiVariantConfig`](../gradle-plugin/src/main/kotlin/com/nexustools/genericui/plugin/GenericUiVariantConfig.kt) (`window { ... }`, `theme { ... }`, `icon`, `title`, `themeColor`, `extensionOverrides`). [`GenericUiVariantResolver`](../gradle-plugin/src/main/kotlin/com/nexustools/genericui/plugin/GenericUiVariantResolver.kt) merges settings from least specific (`common`) to most specific (concrete target):

| Target Backend | Variant Config Resolution Order (Rightmost Wins) |
| :--- | :--- |
| **`windows`** | `common` $\rightarrow$ `systemCommon` $\rightarrow$ `desktopCommon` $\rightarrow$ `nativeDesktopCommon` $\rightarrow$ `windows` |
| **`macos`** | `common` $\rightarrow$ `systemCommon` $\rightarrow$ `appleCommon` $\rightarrow$ `desktopCommon` $\rightarrow$ `nativeDesktopCommon` $\rightarrow$ `macos` |
| **`gtk`** | `common` $\rightarrow$ `systemCommon` $\rightarrow$ `desktopCommon` $\rightarrow$ `nativeDesktopCommon` $\rightarrow$ `linuxCommon` $\rightarrow$ `gtk` |
| **`qt`** | `common` $\rightarrow$ `systemCommon` $\rightarrow$ `desktopCommon` $\rightarrow$ `nativeDesktopCommon` $\rightarrow$ `linuxCommon` $\rightarrow$ `qt` |
| **`swing`** | `common` $\rightarrow$ `systemCommon` $\rightarrow$ `jvmCommon` $\rightarrow$ `desktopCommon` $\rightarrow$ `swing` |
| **`android`** | `common` $\rightarrow$ `systemCommon` $\rightarrow$ `jvmCommon` $\rightarrow$ `mobileCommon` $\rightarrow$ `android` |
| **`ios`** | `common` $\rightarrow$ `systemCommon` $\rightarrow$ `appleCommon` $\rightarrow$ `mobileCommon` $\rightarrow$ `iosCommon` $\rightarrow$ `ios` |
| **`ipados`** | `common` $\rightarrow$ `systemCommon` $\rightarrow$ `appleCommon` $\rightarrow$ `mobileCommon` $\rightarrow$ `iosCommon` $\rightarrow$ `ipados` |
| **`web`** | `common` $\rightarrow$ `web` |

---

## 4. Apple Icon Pipeline & Chrome-Style Squircle Auto-Wrapping

GenericUI models native Apple icon guidelines out of the box without forcing developers to maintain separate macOS squircle or iOS full-bleed PNG artwork unless desired:

### 4.1 Explicit Override Resolution Precedence (`GenericUiVariantResolver.resolveEffectiveIconAndExplicitFlag`)
For each Apple target (`macos`, `ios`, `ipados`), the plugin resolves icons in order and tracks `appleIconExplicitlyProvided: Boolean`:
1. **Target-specific icon** (`macIcon`/`macosIcon`/`macos { icon(...) }`, `iosIcon`/`ios { icon(...) }`, `ipadosIcon`/`ipadIcon`/`ipados { icon(...) }`) $\rightarrow$ `appleIconExplicitlyProvided = true`
2. **`iosCommon` variant icon** (for `ios` / `ipados`) $\rightarrow$ `appleIconExplicitlyProvided = true`
3. **Shared Apple icon** (`appleIcon(...)`, `apple { icon = ... }`, `appleCommon { icon(...) }`, or `icons(apple = ...)`) $\rightarrow$ `appleIconExplicitlyProvided = true`
4. **Generic/default fallback** (`window { icon }`, `application { icon }`, or GenericUI default icon) $\rightarrow$ `appleIconExplicitlyProvided = false`

### 4.2 Automatic Build-Time & Runtime Chrome-Style White Tile Wrapping
When **`appleIconExplicitlyProvided == false`**, GenericUI automatically wraps the artwork inside a clean Chrome-style white Apple icon tile instead of rendering raw transparent or oddly shaped icons:
- **Build-Time ([`GenericUiAppleIconTileComposer`](../gradle-plugin/src/main/kotlin/com/nexustools/genericui/plugin/GenericUiAppleIconTileComposer.kt))**:
  - **macOS (`fullBleedSquare = false`)**: Renders the artwork (`DEFAULT_ARTWORK_SCALE = 0.74f` inside the tile) onto a centered white continuous macOS superellipse squircle (exponent `4.6`, corner ratio `0.2237`, tile fraction `0.808` canvas, vertical offset `0.492`) with soft downward drop shadow and hairline rim inside `.icns` / app resources (`GenericUiDesktopResourceTask`).
  - **iOS & iPadOS (`fullBleedSquare = true`)**: Renders the artwork centered onto an opaque solid white `1024×1024` full-bleed square canvas (`Assets.xcassets/AppIcon.appiconset` in `GenericUiGenerateIosAppTask`) ready for Apple's App Store mask.
- **Runtime macOS Dock Icon ([`AppleIconTileComposer`](../common/src/commonMain/kotlin/com/nexustools/genericui/image/AppleIconTileComposer.kt) + [`MacosNativeWindowHost`](../desktop/src/macosMain/kotlin/com/nexustools/genericui/macos/MacosNativeWindowHost.kt))**:
  - When macOS sets the `NSApplication` Dock icon at runtime via `window.setIcon(...)` or descriptor metadata and `!GenericUI.appleIconExplicitlyProvided`, `MacosNativeWindowHost.composeRuntimeDockIconBuffer` checks `!MacosNativeWindowHost.isAlreadyWrappedAppleSquircleIcon(...)` before calling `AppleIconTileComposer.composeRgba` / `composeImageBuffer` to wrap the live icon in the matching Chrome-style white squircle automatically!
  - Conversely, macOS menu-bar tray icons (`tray_icon_png` / `tray_icon_svg`) are staged unwrapped (`wrapInAppleTile = false`) directly inside the signed Mach-O binary (`MacosSystemTrayNativeImpl`).
  - Setting any explicit Apple icon (`appleIcon(...)`, `macIcon(...)`, `iosIcon(...)`, `ipadosIcon(...)`, `apple { icon = ... }`, `mac { icon = ... }`, etc.) marks `appleIconExplicitlyProvided = true` and bypasses auto-wrapping so custom Apple-designed icons render verbatim.

---

## 5. Registered Gradle Tasks

| Task Name | Implementation Class | Description |
| :--- | :--- | :--- |
| `genericuiValidateApp` | `GenericUiValidateAppTask` | Validates `@MainAppFragment` and `@AppFragment` classes across all enabled targets, verifies `AppTarget` specificity, and runs `GenericUiExtensionContributor.validate(...)`. |
| `generateGenericUiSchemas`<br/>`generateGenericUiServiceWorkerCodecs` | `GenericUiGenerateSchemaSourcesTask`<br/>`GenericUiGenerateServiceWorkerCodecsTask` | Generates `<ClassName>SchemaDescriptor.kt` + `GeneratedSchemaRegistrar.kt` (`@SchemaData`) and validates `@MainServiceWorker` + emits `<Msg>ServiceWorkerCodec.kt` + `GeneratedServiceWorkerCodecRegistrar.kt` (`@ServiceWorkerMessage`). |
| `generateGenericUiLaunchers` | `GenericUiGenerateLaunchersTask` | Generates `GenericUiAppDescriptor`, backend launchers, SPI `generatedLauncherSources`, extension wiring, and visual test runners. |
| `compileGenericUiResources` | `GenericUiResourceCompilerTask` | Generates isolated `R.kt`/`RC.kt`/`Resource.kt`/`GeneratedResourceRegistry.kt`, embeds prefix-free `nativeResourceKey` tables (`GenericUiNativeResourceEmbedder`), translates Tier 1/2/3 assets, compiles Android theme XMLs, and synthesizes Windows `.cur` cursors. |
| `compileGenericUiSass` | `GenericUiSassCompileTask` | Compiles `.scss`/`.sass` stylesheets into `.css` (Web) and zero-parse Kotlin `PrecompiledStyleSheet` objects (all 8 system targets). |
| `prepareGenericUiDesktopResources` | `GenericUiDesktopResourceTask` | Stages desktop icons/manifests and wraps non-explicit macOS app/Dock icons using `GenericUiAppleIconTileComposer` while preserving unwrapped Mach-O tray icons. |
| `compileGenericUiWindowsResources` | `GenericUiCompileWindowsResourcesTask` | Generates Win32 `.rc`/headers, compiles `.res` via `windres`, and links into the Windows `.exe`. |
| `generateGenericUiAndroidActivities`<br/>`generateGenericUiAndroidManifest` | `GenericUiGenerateAndroidActivitiesTask`<br/>`GenericUiGenerateAndroidManifestTask` | Generates 1:1 `<FragmentName>Activity` classes and `AndroidManifest.xml` (merging SPI `<service>`/`<receiver>`/`<provider>`/`<uses-permission>`/`Activity` hooks). |
| `generateGenericUiIosApp`<br/>`generateGenericUiIpadosApp` | `GenericUiGenerateIosAppTask` | Generates iPhone (`UIDeviceFamily = 1`) and iPad (`UIDeviceFamily = 2`) Xcode wrapper projects, `Info.plist` (merging SPI entries), SwiftUI host, and `AppIcon.appiconset`. |
| `fetchGenericUiNativeSources`<br/>`compileGenericUiJniLibraries` | `GenericUiFetchNativeSourceArchiveTask`<br/>`GenericUiCompileJniTask` | Downloads verified native source archives and compiles consumer C/C++/CMake JNI shared libraries (`-Wl,-z,max-page-size=16384`, zero-NDK Konan LLVM + `target-sysroot-1-android_ndk` fallback, host test `.so`/`.dylib`/`.dll`, Swing JAR manifest). |
| `bundleGenericUiAngle`<br/>`bundleGenericUiVulkan`<br/>`bundleGenericUiVlc`<br/>`bundleGenericUiWebViewProcess` | `AngleNativeBundleTask`<br/>`VulkanNativeBundleTask`<br/>`VlcNativeBundleTask`<br/>`WebViewNativeProcessBundleTask` | Stages opt-in ANGLE, Vulkan/MoltenVK/Shaderc, Desktop VLC, and out-of-process Desktop WebView helper artifacts when the corresponding extension is enabled. |
| `buildGenericUiSharedLibsFor*` | `GenericUiBuildNativeSharedLibsTask`, `GenericUiKonanCompilerShim`, & `WindowsCoffObjectPartitioner` | Compiles core (`genericui`), native desktop backends (`genericui-{macos,windows,gtk,qt}`), `njs`, enabled extensions/DSL modules (`genericui-ext-*`), and `genericui-tests` as dynamic shared libraries (`PROGRAM` links only), partitioning MinGW COFF objects when exports exceed 65,535. |
| `packageGenericUiWebDistribution`<br/>`packageGenericUiSwingDevelopmentJar` / `ProductionJar`<br/>`packageGenericUiAndroid*Apk` | `GenericUiWebDistributionTask`<br/>`GenericUiPackageSwingJarTask`<br/>`GenericUiPackageAndroidApkTask` | Packages Web Wasm/JS/theme/`loader.js`/web-root `./serviceworker.js` (+ SPI `WebSwPluginScript` / `${resolvedBase}.sw.js\|wasm`), Swing fat JARs (`META-INF/native/<os>-<arch>/`), and 16KB-aligned Android APKs (`zipalign -f -P 16 4` + `src/androidMain/jniLibs`). |
| `development*` / `production*`<br/>(`Windows`, `Mac`/`Macos`, `LinuxGtk`, `LinuxQt`, `Swing`, `Android`, `Ios`, `Ipados`, `Web`) | `GenericUiMultiplatformConfigurator` | Per-target lifecycle development & production tasks (including `SecondaryDesktopExecutableContribution` binaries and `-gtk`/`-qt` compilation variants via `registerBuildTargetTasks`). |
| `printGenericUiConfig` | `GenericUiGradlePlugin` | Prints resolved backends, extensions, Maven coordinates, and pipeline config. |

---

## 6. Native Desktop Shared Library Linking & Staging

On native desktop targets (`macosArm64`, `linuxX64`, `linuxArm64`, `mingwX64`), executable and test binaries link GenericUI core, the active backend, and used extensions as dynamic shared libraries cached under `~/.gradle/caches/genericui-native-shared/`, while consumer app/test code compiles directly into the executable. **JVM Swing, Mobile (`Android`, `iOS`, `iPadOS`), and Web (`wasmJs` / `js`) remain integrated into their application packages as before.**

| Target Platform | Shared Library Names | Distribution Staging Layout |
| :--- | :--- | :--- |
| **macOS (`macosArm64`)** | `libgenericui.dylib` (`stdlib` + coroutines/atomicfu + `:common` + `:system` + `:apple`), `libgenericui-macos.dylib`, `libgenericui-ext-<ext>.dylib` (+ `-dsl`), `libnjs.dylib`, `libgenericui-tests.dylib` | `<AppName>.app/Contents/Frameworks/` (`-rpath @executable_path/../Frameworks`; zero `libgenericui*.dylib` in `Contents/MacOS/` and zero loose `.dylib`/`.kexe`/`.dSYM` files in `dist/mac/<mode>/`). |
| **Linux GTK4 (`linuxX64`, `linuxArm64`)** | `libgenericui.so`, `libgenericui-gtk.so`, `libgenericui-ext-<ext>.so` (+ `-dsl`), `libnjs.so`, `libgenericui-tests.so` | Flat alongside the executable in `dist/linux-gtk/<arch>/<mode>/` (`-Wl,-rpath,$ORIGIN`); per-file object partitioning guarantees `0` Qt symbols in `libgenericui-gtk.so`. |
| **Linux Qt6 (`linuxX64`, `linuxArm64`)** | `libgenericui.so`, `libgenericui-qt.so`, `libgenericui-ext-<ext>.so` (+ `-dsl`), `libnjs.so`, `libgenericui-tests.so` | Flat alongside the executable in `dist/linux-qt/<arch>/<mode>/` (`-Wl,-rpath,$ORIGIN`); per-file object partitioning guarantees `0` GTK symbols in `libgenericui-qt.so`. |
| **Windows (`mingwX64`)** | `genericui.dll`, `genericui-windows.dll`, `genericui-ext-<ext>.dll` (+ `-dsl`), `njs.dll`, `genericui-tests.dll` *(no `lib` prefix)* | Flat alongside `<app>.exe` in `dist/windows/x64/<mode>/` (zero `*.dll.a` import libraries staged). |

> **Linux `TEST` binaries:** Linux application distributions always link strictly `libgenericui-gtk.so` or `libgenericui-qt.so`. When a Linux project enables both GTK and Qt backends simultaneously, its single Linux `TEST` binary (`build/bin/<target>/debugTest/`) links `libgenericui-linux.so` alongside `libgenericui-tests.so` so both generated test bootstraps (`gtkTest` and `qtTest`) can run in the same test suite; single-backend Linux test binaries link `libgenericui-gtk.so` or `libgenericui-qt.so` with matching per-file partitioned `libgenericui-tests.so`. On Windows MinGW (`mingwX64`), `WindowsCoffObjectPartitioner` splits COFF object sets into linked partition DLLs automatically when exported symbols exceed the 65,535 PE export table limit (`GenericUiKonanCompilerShim` hooks `CachedLibraries$Companion`).

---

## 7. Extension Contributor SPI (`gradle-plugin/.../spi/`)

First-party (`PipExtensionContributor`, `SchemaExtensionContributor`, `ServiceWorkerExtensionContributor`) and third-party Gradle extensions extend codegen, manifests, and packaging cleanly without modifying core `:gradle-plugin` tasks:

- **Discovery (`GenericUiExtensionContributorRegistry`)**: Loads built-in contributors, `java.util.ServiceLoader<GenericUiExtensionContributor>`, and JSON descriptors under `META-INF/genericui/extensions/*.json` (`custom(...)` / `option(...)`).
- **Lifecycle & Hooks (`GenericUiExtensionContributor`)**:
  - `annotationScanSpecs` (`ExtensionAnnotationScanSpec`) & `validate(ExtensionBuildContext, List<GenericUiDiscoveredSymbol>)`: Declares annotations to scan (`@SchemaData`, `@MainServiceWorker`, `@ServiceWorkerMessage`) and validates consumer code cleanly with `GradleException`.
  - `providerInstallerCalls(...)` & `generatedLauncherSources(...)`: Registers extension hooks (`ApplicationLifecycleExtensionHook` on `GenericUiExtensionRegistry`) in generated launchers (on iOS/iPadOS, `ServiceWorkerExtensionContributor` emits only codec registration and does not instantiate `@MainServiceWorker`, keeping `ServiceWorkerExtension.isSupported == false` honestly without simulation).
  - `contributeAndroid(...)` $\rightarrow$ `AndroidExtensionContribution`: Contributes Android `<service>`, `<receiver>` (`BOOT_COMPLETED`), `<provider>`, `<uses-permission>`, `<meta-data>`, and `GenericUiFragmentActivity` lifecycle hooks.
  - `contributeDesktop(...)` $\rightarrow$ `DesktopExtensionContribution`: Contributes `SecondaryDesktopExecutableContribution` executables (staged as `serviceworker.exe`, macOS `Contents/MacOS/serviceworker`, Linux `serviceworker` with `-gtk`/`-qt` compilation variants via `GenericUiMultiplatformConfigurator.registerBuildTargetTasks`, and Swing `GeneratedSwingServiceWorkerMain`), `DesktopAutostartContribution`, and `macosInfoPlistEntries`.
  - `contributeAppleMobile(...)` $\rightarrow$ `AppleMobileExtensionContribution`: Contributes iOS/iPadOS `Info.plist` entries such as `UIBackgroundModes` (`audio` for `:extensions:ui:pip`).
  - `contributeWebServiceWorker(...)` $\rightarrow$ `WebServiceWorkerContribution`: Contributes `WebSwPluginScript` (`importScripts(...)` inside web-root `./serviceworker.js`), `WebWorkerBinarySpec` (`${resolvedBase}.sw.wasm` + `${resolvedBase}.sw.js`), and offline cache URLs staged by `GenericUiWebDistributionTask`.

---

## 8. Consumer Native / JNI / CInterop Build System (`gradle-plugin/.../nativebuild/`)

`genericui { jni { ... }; cinterops { ... }; nativeLibraries { ... } }` (available globally on `GenericUiExtension` and per variant scope on `GenericUiVariantConfig` / `GenericUiAndroidConfig`) automates cross-platform native C/C++/CMake/JNI and K/N CInterop builds:
- **`GenericUiFetchNativeSourceArchiveTask` (`fetchGenericUiNativeSources`)**: Downloads and caches remote `.tar.gz`/`.tgz`/`.zip` native sources with optional SHA-256 verification and leading path component stripping.
- **`GenericUiJniToolchainResolver` & `GenericUiCompileJniTask` (`compileGenericUiJniLibraries`)**: Compiles C/C++ `library(...)` and `cmake(...)` targets for Android ABIs (using configured NDK or zero-NDK Kotlin/Native Konan LLVM + `target-sysroot-1-android_ndk` fallback with `-Wl,-z,max-page-size=16384`) and host JVM/Swing shared libraries (`buildHostLibraryForTests` / `bundleInSwingJar`), injecting `-Djava.library.path` and `PATH`/`DYLD_LIBRARY_PATH`/`LD_LIBRARY_PATH` into `jvmTest` and `testAndroidHostTest`.
- **Android 16KB Alignment & Swing Bundling**: `GenericUiPackageAndroidApkTask` stages `src/androidMain/jniLibs`, `jniLibsDirs`, and compiled JNI `.so` files uncompressed on `16384`-byte (`16KB`) boundaries alongside `zipalign -f -P 16 4`; `GenericUiPackageSwingJarTask` bundles host `.dll`/`.dylib`/`.so` binaries into `META-INF/native/<os>-<arch>/` loaded via `:jvm`'s [`JniLibraryLoader`](../jvm/src/jvmSharedMain/kotlin/com/nexustools/genericui/jvm/JniLibraryLoader.kt).
- **`cinterops` & `nativeLibraries`**: Automatically attaches Kotlin/Native `cinterop` `.def` bindings (`defFile`, `packageName`, `headers`, `includeDirs`, `compilerOpts`, `linkerOpts`, `extraOpts`) and native linker flags/library directories to matching desktop and Apple targets.
