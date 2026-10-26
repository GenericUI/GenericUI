# GenericUI Gradle Plugin Guide (`:gradle-plugin`)

> **Root Repository**: [**GenericUI (`../README.md`)**](../README.md) | **Master Docs Index**: [**`docs/README.md`**](./README.md)  
> **Related Guides**: [Fragments & Windows (`fragments-and-windows.md`)](./fragments-and-windows.md) · [Resources (`resources.md`)](./resources.md) · [Application (`application.md`)](./application.md) · [SourceSets (`sourcesets.md`)](./sourcesets.md) · [Platform Backends (`backends.md`)](./backends.md) · [Extensions (`extensions.md`)](./extensions.md)

The `:gradle-plugin` module provides `id("com.nexustools.genericui")` (and the companion settings plugin `id("com.nexustools.genericui.settings")`) to build complete multiplatform applications from a **single `src/commonMain` source set** with zero per-backend boilerplate.

---

## 1. What the Plugin Automates

When `id("com.nexustools.genericui")` is applied to a project:
1. **Annotation-Driven Fragment & Application Discovery**: Scans Kotlin sources (`GenericUiFragmentScanner`) for [`@MainAppFragment`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/fragment/MainAppFragment.kt), [`@AppFragment`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/fragment/AppFragment.kt), and optional [`@AppApplication`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/fragment/AppApplication.kt) classes.
2. **Intelligent `@MainAppFragment` Target Specificity Resolution (`genericuiValidateApp`)**: Resolves the most specific `@MainAppFragment` for each enabled backend target along its [`AppTarget`](../common/src/commonMain/kotlin/com/nexustools/genericui/enums/system/AppTarget.kt) specificity chain (`5 → 4 → 3 → 2 → 1 → 0`), failing the build with a descriptive diagnostic if two `@MainAppFragment` classes tie at the highest specificity rank or if an enabled target has no matching `@MainAppFragment`.
3. **Generated Per-Backend Launchers & `FragmentBundle` CLI Arguments (`generateGenericUiLaunchers`)**: Generates the `GenericUiAppDescriptor` and launcher bootstrap for every enabled target (`WindowsLauncher`, `MacosLauncher`, `GtkLauncher`, `QtLauncher`, `SwingLauncher`, `AndroidLauncher`, `IosLauncher`, `WebLauncher`), automatically registering all enabled extension providers and injecting process command-line arguments into the initial `FragmentBundle` on system targets (`savedState?.hasCommandLineArgs()`, `savedState?.getCommandLineArgs()`).
4. **1:1 Generated Android `AppCompatActivity` Classes, `AndroidManifest.xml`, & Services**:
   - Synthesizes an Android application wrapper project via `GenericUiSettingsPlugin` when building Android APKs.
   - Generates a concrete 1:1 **`AppCompatActivity` subclass** (`class <FragmentName>Activity : GenericUiFragmentActivity()`) for every `@MainAppFragment` and `@AppFragment` applicable to Android (`generateGenericUiAndroidActivities`), registering each mapping in `AndroidActivityRegistry`.
   - Generates `AndroidManifest.xml` (`generateGenericUiAndroidManifest`) with `<application>`, `<activity>` entries (with `MAIN`/`LAUNCHER` on the resolved Android `@MainAppFragment`), permissions, orientation, Picture-in-Picture flags, and `<service>` declarations from `android { services { ... } }`.
5. **Android-Style `Resource` / `Resource.ResourceID` Generation & Multi-Backend Translation**: Generates `<package>.Resource` (and `R` / `RC`) with [`ResourceID`](../common/src/commonMain/kotlin/com/nexustools/genericui/resources/ResourceID.kt) constants and translates resources into Web asset URLs, embedded Win32 `.rc`/`.res` `.exe` resources, Android `res/` XMLs & `R.*` IDs, Apple `NSBundle` assets, Linux `GResource`/`QResource` bundles, and JVM JAR resources. See the [**Resources Guide (`resources.md`)**](./resources.md).

---

## 2. Full `genericui { ... }` DSL Reference

```kotlin
import com.nexustools.genericui.plugin.OsArchClassifier

plugins {
    id("com.nexustools.genericui")
}

genericui {
    // 1. Core Application Identity & Metadata
    app {
        id = "com.example.studio"
        name = "GenericUI Studio"
        version = "1.0.0"
        vendor = "NexusTools"
        description = "Cross-platform creative studio built on a single commonMain"
        packageName = "com.example.studio"
    }

    // 2. Top-Level Window & Theme Defaults (inherited by all variant scopes unless overridden)
    window {
        title = "GenericUI Studio"
        width = 1440
        height = 900
        minWidth = 960
        minHeight = 600
        resizable = true
        icon = "src/commonMain/resources/drawable/icon_app.svg"
    }

    theme {
        mode = "DARK"       // "LIGHT", "DARK", or "SYSTEM"
        color = "#2563EB"   // Propagated to Web meta/PWA, Android colors.xml, Apple AccentColor, Win32 .rc, Linux .desktop
    }

    // 3. Target Platform Backends
    backends {
        all() // Enables windows, macos, gtk, qt, swing, android, ios, and web (wasmJs + js fallback)
        // Or presets: allExceptSwing(), allDesktop(), allDesktopExceptSwing(), allMobile()
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

    // 4. First-Party Extension Modules (Transitive Dependencies & Backend Providers Wired Automatically)
    extensions {
        gpu()              // :extensions:gpu
        imageProcessing()  // :extensions:imageprocessing (transitively enables gpu)
        overlay()          // :extensions:overlay (also enabled automatically by media, canvas2d, and canvas3d)
        canvas2d()         // :extensions:canvas2d (transitively enables imageProcessing + overlay)
        canvas3d()         // :extensions:canvas3d (transitively enables gpu + canvas2d + overlay)
        media()            // :extensions:media (transitively enables overlay)
        mediaControls()    // :extensions:mediacontrols (standalone OS media session & hardware media key hooks)
        webView()          // :extensions:webview (Chromium + NJS Native JIT on Desktop; native on Mobile/Web)
        codeEditor()       // :extensions:codeeditor (transitively enables canvas2d)
        aiChat()           // :extensions:aichat (transitively enables codeEditor -> canvas2d)
        threeJs()          // :extensions:threejs (transitively enables gpu + canvas3d)
        box2d()            // :extensions:box2d (transitively enables canvas2d + canvas3d -> gpu)
        box3d()            // :extensions:box3d (transitively enables canvas3d + threeJs -> gpu)
        alien()            // :extensions:alien (transitively enables canvas2d)
        dsl()              // :extensions:dsl + companion :extensions:<ext>:dsl modules
    }

    // 5. Variant Scope Overrides (common, systemCommon, desktopCommon, nativeDesktopCommon,
    //    linuxCommon, mobileCommon, windows, macos, gtk, qt, swing, android, ios, web)
    desktopCommon {
        window {
            width = 1600
            height = 960
        }
    }

    web {
        window {
            title = "GenericUI Studio (Web)"
        }
    }

    // 6. Android-Specific Manifest & Service Configuration (valid ONLY inside android { ... })
    android {
        manifest {
            minSdk = 24
            compileSdk = 35
            targetSdk = 35
            screenOrientation = "unspecified"
            supportsPictureInPicture = true
            permissions(
                "android.permission.INTERNET",
                "android.permission.FOREGROUND_SERVICE"
            )
        }
        services {
            service("com.example.studio.playback.MediaPlaybackService") {
                exported = false
                foregroundServiceType = "mediaPlayback"
            }
        }
    }

    // 7. Progressive Web App (PWA) Manifest & Desktop Packaging Metadata
    application {
        webIcon("src/commonMain/resources/drawable/icon_app.svg")
        webManifest {
            allowInstallAsWebApp = true
            shortName = "Studio"
            display = "standalone"
            backgroundColor = "#0F172A"
            categories("productivity", "graphics")
        }
    }

    // 8. Native GPU Drivers, Translation Layers, Shader Compilers, & Desktop VLC
    graphics {
        bundleAngleOnWindows = true               // Google ANGLE (libEGL.dll, libGLESv2.dll -> D3D11)
        angleBackendRenderer = "d3d11"
        bundleMoltenVkOnApple = true              // MoltenVK (Vulkan 1.2 -> Metal on macOS & iOS)
        bundleShaderc = true
        preferOpenGLES31 = true
        bundleVlcOnDesktop = true                 // :extensions:media desktop libvlc engine
        vlcPrimaryShaderBackend = "vulkan"
        vlcFallbackShaderBackend = "opengles"
        enableVlcHdrToneMapping = true
        enableVlcHardwareDecode = true
    }

    // 9. SASS / CSS Compilation Pipeline
    styles {
        compileSass = true
        sourceDir = "src/commonMain/resources/styles"
        minify = false
        includeBootstrapVariables = true
    }

    // 10. Android-Style Resource & Resource.ResourceID Compiler
    resources {
        enabled = true
        packageName = "com.example.studio.res"
        translateForAllBackends = true
    }

    // 11. JestElectricRunner Automated UI Testing & Generated Visual Test Runners
    testing {
        enabled = true
        visualOnWindows = true
    }

    // 12. Optional Variant SourceSet Dependencies (created only when configured or directory exists)
    sourceSets {
        common {
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
        }
        nativeDesktopCommon {
            // Optional libraries shared across Windows, macOS, GTK, and Qt
        }
        android {
            // Optional Android-only dependencies (e.g., for Android Service implementations in src/androidMain)
        }
    }
}
```

---

## 3. Variant Scope Resolution Chain (`GenericUiVariantScope`)

Every variant block (`common`, `systemCommon`, `desktopCommon`, `nativeDesktopCommon`, `linuxCommon`, `mobileCommon`, `windows`, `macos`, `gtk`, `qt`, `swing`, `android`, `ios`, `web`) configures a [`GenericUiVariantConfig`](../gradle-plugin/src/main/kotlin/com/nexustools/genericui/plugin/GenericUiVariantConfig.kt) (`window { ... }`, `theme { ... }`, `icon`, `title`, `themeColor`, `extensionOverrides`).

For each enabled backend, [`GenericUiVariantResolver`](../gradle-plugin/src/main/kotlin/com/nexustools/genericui/plugin/GenericUiVariantResolver.kt) merges settings from least specific (`top-level` $\rightarrow$ `common`) to most specific (the concrete backend block):

| Target Backend | Variant Config Resolution Order (Rightmost Wins) |
| :--- | :--- |
| **`windows`** | `top-level` $\rightarrow$ `common` $\rightarrow$ `systemCommon` $\rightarrow$ `desktopCommon` $\rightarrow$ `nativeDesktopCommon` $\rightarrow$ `windows` |
| **`macos`** | `top-level` $\rightarrow$ `common` $\rightarrow$ `systemCommon` $\rightarrow$ `desktopCommon` $\rightarrow$ `nativeDesktopCommon` $\rightarrow$ `macos` |
| **`gtk`** | `top-level` $\rightarrow$ `common` $\rightarrow$ `systemCommon` $\rightarrow$ `desktopCommon` $\rightarrow$ `nativeDesktopCommon` $\rightarrow$ `linuxCommon` $\rightarrow$ `gtk` |
| **`qt`** | `top-level` $\rightarrow$ `common` $\rightarrow$ `systemCommon` $\rightarrow$ `desktopCommon` $\rightarrow$ `nativeDesktopCommon` $\rightarrow$ `linuxCommon` $\rightarrow$ `qt` |
| **`swing`** | `top-level` $\rightarrow$ `common` $\rightarrow$ `systemCommon` $\rightarrow$ `desktopCommon` $\rightarrow$ `swing` |
| **`android`** | `top-level` $\rightarrow$ `common` $\rightarrow$ `systemCommon` $\rightarrow$ `mobileCommon` $\rightarrow$ `android` |
| **`ios`** | `top-level` $\rightarrow$ `common` $\rightarrow$ `systemCommon` $\rightarrow$ `mobileCommon` $\rightarrow$ `ios` |
| **`web`** | `top-level` $\rightarrow$ `common` $\rightarrow$ `web` |

> [!IMPORTANT]
> **Android-Only `manifest { ... }` and `services { ... }` Enforcement**: Calling `manifest { ... }` or `services { ... }` inside any variant scope other than `android { ... }` throws a `GradleException` at configuration time.

---

## 4. Registered Gradle Tasks

| Task Name | Implementation Class | Description |
| :--- | :--- | :--- |
| `genericuiValidateApp` | `GenericUiValidateAppTask` | Validates `@MainAppFragment` and `@AppFragment` classes across all enabled targets, verifying specificity resolution and checking for conflicting `@MainAppFragment` declarations. |
| `generateGenericUiLaunchers` | `GenericUiGenerateLaunchersTask` | Generates per-target launcher entry points, `GenericUiAppDescriptor` objects, `FragmentRegistry` registrations, provider registrations, and visual test runners under `build/generated/genericui/<target>/kotlin`. |
| `generateGenericUiResources`<br/>`compileGenericUiResources` | `GenericUiGenerateResourcesTask`<br/>`GenericUiResourceCompilerTask` | Generates `<package>.Resource` (`Resource.ResourceID`), `R`, and `RC`, and translates `drawable/`, `values/`, `raw/`, `font/`, and `xml/` resources across all enabled backends. |
| `compileGenericUiWindowsResources` | `GenericUiCompileWindowsResourcesTask` | Generates Win32 `.rc` and header files (`GenericUiWin32RcGenerator`), compiles them via `windres` into COFF `.res`, and links them into the Windows `.exe`. |
| `generateGenericUiAndroidResources` | `GenericUiGenerateAndroidResourcesTask` | Translates and generates Android `res/` directories (`drawable/`, `mipmap-*/`, `values/*.xml`, `raw/`, `font/`, `xml/`). |
| `generateGenericUiAndroidActivities` | `GenericUiGenerateAndroidActivitiesTask` | Generates 1:1 `AppCompatActivity` subclasses (`<FragmentName>Activity : GenericUiFragmentActivity()`) for every `@MainAppFragment` and `@AppFragment` applicable to Android. |
| `generateGenericUiAndroidManifest` | `GenericUiGenerateAndroidManifestTask` | Generates `AndroidManifest.xml` with `<application>`, 1:1 `<activity>` entries, `<service>` entries, permissions, orientation, and PiP attributes. |
| `compileGenericUiSass` | `GenericUiSassCompileTask` | Compiles `.scss` / `.sass` stylesheets into `.css` bundles and zero-parse Kotlin `PrecompiledStyleSheet` objects. |
| `bundleGenericUiAngle` | `AngleNativeBundleTask` | Stages Google ANGLE (`libEGL.dll`, `libGLESv2.dll`, `d3dcompiler_47.dll`) for Windows D3D11 OpenGL ES translation. |
| `bundleGenericUiVulkan` | `VulkanNativeBundleTask` | Stages Vulkan runtime manifests, `MoltenVK_icd.json` on Apple targets, and `shaderc`. |
| `packageGenericUiWebDistribution` | `GenericUiWebDistributionTask` | Packages the Kotlin/Wasm (`wasmJs`) primary bundle, Kotlin/JS IR (`js`) fallback bundle, `genericui-loader.js`, splash screen, `.svg` web icon, `manifest.webmanifest`, and SHA-1 cache-busted `index.html` into `build/dist/web`. |
