# `:gradle-plugin` — GenericUI Gradle Plugin (`com.nexustools.genericui`)

## Overview
`:gradle-plugin` implements the `com.nexustools.genericui` Gradle plugin, which enables GenericUI's **Single-`commonMain` Consumer Architecture**. Consumer projects write 100% of their application code in `src/commonMain/kotlin` and `src/commonMain/resources`, while the plugin automatically generates all platform launchers, Android manifests and `AppCompatActivity` subclasses, compiled native/mobile/web resource bundles, web Wasm/JS splash loaders, and visual test runners.

---

## Plugin Architecture & Tasks

| Class | Package | Responsibility |
|-------|---------|----------------|
| `GenericUiPlugin` | `com.nexustools.genericui.gradle` | Registers the `genericui { ... }` DSL extension and wires code/resource generation tasks into Kotlin Multiplatform target compilations |
| `GenericUiExtension` | `com.nexustools.genericui.gradle` | Type-safe DSL configuring `applicationId`, `appName`, `version`, `window { ... }`, `android { manifest { ... } services { ... } }`, `web { ... }`, and `testing { visualOnWindows = ... }` |
| `GenericUiGenerateLaunchersTask` | `com.nexustools.genericui.gradle` | Scans `src/commonMain/kotlin` for `@MainAppFragment` and `@AppFragment` annotations and generates platform launchers (`WindowsLauncher`, `MacosLauncher`, `GtkLauncher`, `QtLauncher`, `SwingLauncher`, `AndroidLauncher` + 1:1 `AppCompatActivity` subclasses + `AndroidManifest.xml`, `IosLauncher`, `WebLauncher`, and `GeneratedWindowsVisualTestLauncher`) |
| `GenericUiCompileResourcesTask` | `com.nexustools.genericui.gradle` | Compiles `src/commonMain/resources/` (`values/strings.xml`, `values-<lang>/strings.xml`, `values/colors.xml`, `values/dimens.xml`, `values/themes.xml`, `layout/*.xml`, `drawable/*`, `raw/*`, `font/*`) into typed `R.kt` accessors and native platform resource formats |
| `GenericUiGenerateSplashTask` | `com.nexustools.genericui.gradle` | Generates `index.html` and `genericui-loader.js` (Wasm-first with JS fallback and animated splash screen) for `:web` targets |

---

## Consumer `build.gradle.kts` Configuration

```kotlin
plugins {
    kotlin("multiplatform")
    id("com.nexustools.genericui")
}

genericui {
    applicationId = "com.example.myapp"
    appName = "My App"
    version = "1.0.0"

    window {
        width = 1280
        height = 800
        resizable = true
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

    web {
        splashTitle = "My App"
        splashSubtitle = "Initializing WebAssembly UI..."
        accentColor = "#3B82F6"
        backgroundColor = "#0F172A"
    }

    testing {
        visualOnWindows = false
    }
}
```

---

## Annotation-Driven Launcher Generation (`GenericUiGenerateLaunchersTask`)

`GenericUiGenerateLaunchersTask` discovers the single `@MainAppFragment` class and all `@AppFragment(id = "...")` classes in `src/commonMain/kotlin` and generates:

1. **Windows (`mingwX64Main`)**: Calls `WindowsLauncher.launch(...)` and registers `@AppFragment` factories with `WindowsAppFragmentLauncher`.
2. **macOS (`macosX64Main`, `macosArm64Main`)**: Calls `MacosLauncher.launch(...)`.
3. **Linux GTK (`linuxX64Main`)**: Calls `GtkLauncher.launch(...)`.
4. **Linux Qt (`linuxX64Main`)**: Calls `QtLauncher.launch(...)`.
5. **JVM Swing (`jvmMain`)**: Generates `@file:JvmName("GeneratedSwingMain")` calling `SwingLauncher.launch(...)`.
6. **Android (`androidMain` + `AndroidManifest.xml`)**:
   - Generates `AndroidManifest.xml` with permissions, features, metadata, services, `GenericUiGeneratedApplication`, and an `<activity>` entry for every `@MainAppFragment` and `@AppFragment`.
   - Generates a dedicated `<FragmentName>Activity : GenericUiFragmentActivity()` subclass for each `@MainAppFragment` and `@AppFragment`, registered in `AndroidActivityRegistry` so `launchFragment(...)` starts the corresponding `AppCompatActivity` via Android `Intent`.
7. **iOS (`iosX64Main`, `iosArm64Main`, `iosSimulatorArm64Main`)**: Generates `GeneratedIosMain` calling `IosLauncher.createRootViewController(...)` and `IosLauncher.launch(...)`.
8. **Web (`jsMain`, `wasmJsMain`)**: Calls `WebLauncher.launch(...)`, wiring `WebFragmentHistoryBridge` for `#hash` and `window.history.state` navigation.
9. **Windows Visual Test Runner (`mingwX64Test`)**: Generates `GeneratedWindowsVisualTestLauncher` when `testing.visualOnWindows` is enabled.

---

## Multiplatform Resource Compilation (`GenericUiCompileResourcesTask`)

`GenericUiCompileResourcesTask` processes Android-style resources under `src/commonMain/resources/` and outputs both common `R` accessors (`R.string.*`, `R.color.*`, `R.dimen.*`, `R.style.*`, `R.layout.*`, `R.drawable.*`, `R.raw.*`, `R.font.*`) and native platform bundles:

| Target Platform | Compiled Output Format | Runtime Resolver |
|-----------------|------------------------|------------------|
| Windows (`:desktop:windows`) | Win32 `genericui_resources.rc` $\rightarrow$ `windres` `.res` (`STRINGTABLE` + `RCDATA`) linked into `.exe` | `WindowsResourceResolverImpl` (`FindResourceW` / `LoadResource`) |
| macOS (`:desktop:macos`) | Apple `.app/Contents/Resources/` + `.lproj/Localizable.strings` | `MacosResourceResolverImpl` (`NSBundle.mainBundle`) |
| Linux GTK (`:desktop:gtk`) | `genericui.gresource.xml` $\rightarrow$ `glib-compile-resources` `.gresource` | `GtkResourceResolverImpl` (`GResource`) |
| Linux Qt (`:desktop:qt`) | `genericui_resources.qrc` $\rightarrow$ `rcc` `.rcc` bundle | `QtResourceResolverImpl` (`QResource`) |
| JVM Swing (`:desktop:swing`) | Classpath JAR resources | `SwingResourceResolverImpl` (`ClassLoader.getResource`) |
| Android (`:mobile:android`) | Standard Android `res/values*/`, `res/drawable/`, `res/raw/`, `res/font/` | `AndroidResourceResolverImpl` (`Context.resources`) |
| iOS (`:mobile:ios`) | Apple bundle resources + `.lproj/Localizable.strings` | `IosResourceResolverImpl` (`NSBundle.mainBundle`) |
| Web (`:web`) | `assets/<type>/<fileName>` static web bundle + resource tables | `WebResourceResolverImpl` (`assets/...` URLs) |
