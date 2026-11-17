# GenericUI Gradle Plugin Guide (`:gradle-plugin`)

> **Root Repository**: [**GenericUI (`../README.md`)**](../README.md) | **Master Docs Index**: [**`docs/README.md`**](./README.md)  
> **Related Guides**: [iPadOS Backend (`ipados.md`)](./ipados.md) · [Shared Apple (`apple.md`)](./apple.md) · [Shared JVM (`jvm.md`)](./jvm.md) · [Fragments & Windows (`fragments-and-windows.md`)](./fragments-and-windows.md) · [Resources (`resources.md`)](./resources.md) · [Application & Theming (`application.md`)](./application.md) · [SourceSets (`sourcesets.md`)](./sourcesets.md) · [Platform Backends (`backends.md`)](./backends.md) · [Extensions (`extensions.md`)](./extensions.md)

The `:gradle-plugin` module provides `id("com.nexustools.genericui")` (`GenericUiGradlePlugin`) and `id("com.nexustools.genericui.settings")` (`GenericUiSettingsPlugin`) to build complete 9-target multiplatform applications from a **single `src/commonMain` source set** with zero per-backend boilerplate.

---

## 1. What the Plugin Automates

When `id("com.nexustools.genericui")` is applied:
1. **Annotation-Driven Fragment & Application Discovery**: Scans Kotlin sources (`GenericUiFragmentScanner`) for `@MainAppFragment`, `@AppFragment`, and `@AppApplication`.
2. **Target Specificity Resolution (`genericuiValidateApp`)**: Resolves the most specific `@MainAppFragment` for each enabled backend across all 7 [`AppTarget`](../common/src/commonMain/kotlin/com/nexustools/genericui/enums/system/AppTarget.kt) ranks (`6 → 5 → 4 → 3 → 2 → 1 → 0`), failing immediately on ties or missing targets.
3. **Generated Per-Backend Launchers & Descriptors (`generateGenericUiLaunchers`)**: Generates `GenericUiAppDescriptor` and launcher entry points (`WindowsLauncher`, `MacosLauncher`, `GtkLauncher`, `QtLauncher`, `SwingLauncher`, `AndroidLauncher`, `IosLauncher`, `IpadosLauncher`, `WebLauncher`), wiring window geometry (`alwaysOnTop`, windowed `aspectRatio`), Apple icon wrapping flags (`appleIconExplicitlyProvided`), `ThemeConfig`, `addStyleSheet(...)`, extension providers, and system CLI `FragmentBundle` arguments.
4. **Android 1:1 Activities, Material XML, & Xcode iOS/iPadOS App Generation**:
   - **Android (`generateGenericUiAndroidActivities`, `generateGenericUiAndroidManifest`)**: Synthesizes `<FragmentName>Activity : GenericUiFragmentActivity()`, `AndroidActivityRegistry`, `@style/Theme.GenericUI.App` XMLs, and `AndroidManifest.xml`.
   - **iOS & iPadOS (`generateGenericUiIosApp`, `generateGenericUiIpadosApp`)**: Generates dedicated Swift/UIKit Xcode projects (`build/genericui/iosApp` with `UIDeviceFamily = 1`, `build/genericui/ipadosApp` with `UIDeviceFamily = 2`) and composes `AppIcon` assets. When both `ios()` and `ipados()` are enabled, compiles disambiguated frameworks (`iosDebugFramework` / `ipadosDebugFramework`).
5. **Resource & Stylesheet Compilation (`compileGenericUiResources`, `compileGenericUiSass`)**: Generates `Resource` (`ResourceID`, `R`, `RC`) and translates resources + `.scss` stylesheets across all 9 targets.

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

    // 5. First-Party Extension Modules (19 Extensions + Companion :dsl Modules)
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
        media()            // :extensions:media:media -> overlay (+ :dsl)
        mediaControls()    // :extensions:media:mediacontrols (+ :dsl)
        webView()          // :extensions:ui:webview (+ :dsl)
        alien()            // :extensions:ui:alien -> canvas2d (+ :dsl)
        codeEditor()       // :extensions:ui:codeeditor -> canvas2d (+ :dsl)
        aiChat()           // :extensions:ui:aichat -> codeeditor -> canvas2d (+ :dsl)
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
  - When macOS sets the `NSApplication` Dock icon at runtime via `window.setIcon(...)` or descriptor metadata and `!GenericUI.appleIconExplicitlyProvided`, `MacosNativeWindowHost.composeRuntimeDockIconBuffer` calls `AppleIconTileComposer.composeRgba` / `composeImageBuffer` to wrap the live icon in the matching Chrome-style white squircle automatically!
  - Setting any explicit Apple icon (`appleIcon(...)`, `macIcon(...)`, `iosIcon(...)`, `ipadosIcon(...)`, `apple { icon = ... }`, `mac { icon = ... }`, etc.) marks `appleIconExplicitlyProvided = true` and bypasses auto-wrapping so custom Apple-designed icons render verbatim.

---

## 5. Registered Gradle Tasks

| Task Name | Implementation Class | Description |
| :--- | :--- | :--- |
| `genericuiValidateApp` | `GenericUiValidateAppTask` | Validates `@MainAppFragment` and `@AppFragment` classes across all enabled targets and verifies `AppTarget` specificity. |
| `generateGenericUiLaunchers` | `GenericUiGenerateLaunchersTask` | Generates `GenericUiAppDescriptor` (`alwaysOnTop`, `aspectRatio`, `appleIconExplicitlyProvided`, `ThemeConfig`, `addStyleSheet`), backend launchers, extension wiring, and visual test runners. |
| `compileGenericUiResources` | `GenericUiResourceCompilerTask` | Generates `Resource`, `R`, `RC` (`Resource.style.<name>`, etc.), translates drawables/values/raw/font/xml, compiles Android theme XMLs, and synthesizes Windows `.cur` cursors. |
| `compileGenericUiSass` | `GenericUiSassCompileTask` | Compiles `.scss`/`.sass` stylesheets into `.css` (Web) and zero-parse Kotlin `PrecompiledStyleSheet` objects (all 8 system targets). |
| `prepareGenericUiDesktopResources` | `GenericUiDesktopResourceTask` | Stages desktop icons/manifests and wraps non-explicit macOS icons using `GenericUiAppleIconTileComposer`. |
| `compileGenericUiWindowsResources` | `GenericUiCompileWindowsResourcesTask` | Generates Win32 `.rc`/headers, compiles `.res` via `windres`, and links into the Windows `.exe`. |
| `generateGenericUiAndroidActivities`<br/>`generateGenericUiAndroidManifest` | `GenericUiGenerateAndroidActivitiesTask`<br/>`GenericUiGenerateAndroidManifestTask` | Generates 1:1 `<FragmentName>Activity` classes and `AndroidManifest.xml` (`@style/Theme.GenericUI.App`, activities, services, permissions). |
| `generateGenericUiIosApp`<br/>`generateGenericUiIpadosApp` | `GenericUiGenerateIosAppTask` | Generates iPhone (`UIDeviceFamily = 1`) and iPad (`UIDeviceFamily = 2`) Xcode wrapper projects, `Info.plist`, SwiftUI host, and `AppIcon.appiconset`. |
| `bundleGenericUiAngle`<br/>`bundleGenericUiVulkan`<br/>`bundleGenericUiVlc`<br/>`bundleGenericUiWebViewProcess` | `AngleNativeBundleTask`<br/>`VulkanNativeBundleTask`<br/>`VlcNativeBundleTask`<br/>`WebViewNativeProcessBundleTask` | Stages opt-in ANGLE, Vulkan/MoltenVK/Shaderc, Desktop VLC, and out-of-process Desktop WebView helper artifacts when the corresponding extension is enabled. |
| `packageGenericUiWebDistribution`<br/>`packageGenericUiSwingDevelopmentJar` / `ProductionJar`<br/>`packageGenericUiAndroid*Apk` | `GenericUiWebDistributionTask`<br/>`GenericUiPackageSwingJarTask`<br/>`GenericUiPackageAndroidApkTask` | Packages Web Wasm/JS/theme/PWA distributions, Swing executable JARs, and Android APKs. |
| `development*` / `production*`<br/>(`Windows`, `Mac`/`Macos`, `LinuxGtk`, `LinuxQt`, `Swing`, `Android`, `Ios`, `Ipados`, `Web`) | `GenericUiMultiplatformConfigurator` | Per-target lifecycle development & production tasks across all 9 enabled backends. |
| `printGenericUiConfig` | `GenericUiGradlePlugin` | Prints resolved backends, extensions, Maven coordinates, and pipeline config. |
