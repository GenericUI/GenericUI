# GenericUI Gradle Plugin Guide (`:gradle-plugin`)

The `:gradle-plugin` module provides the official Gradle plugin (`id("com.nexustools.genericui")`) for configuring GenericUI multiplatform projects, native GPU acceleration libraries, first-party extensions, and build-time SASS/CSS stylesheet compilation.

---

## 1. Applying the Plugin

In your application module's `build.gradle.kts`:

```kotlin
plugins {
    kotlin("multiplatform") // or kotlin("jvm")
    id("com.nexustools.genericui")
}
```

---

## 2. Full `genericui { ... }` DSL Reference

```kotlin
import com.nexustools.genericui.plugin.OsArchClassifier

genericui {
    // 1. Target Platform Backends
    backends {
        windows(true)
        macos(true)
        gtk(true)
        qt(true)
        swing {
            enabled = true
            // Automatically injects LWJGL 3 OpenGL ES + EGL + JAWT + native classifiers
            useLwjglGL = true
            // Automatically injects LWJGL 3 Vulkan + Shaderc + VMA + MoltenVK + native classifiers
            useVulkan = true
            lwjglVersion = "3.3.6"
            // Defaults to OsArchClassifier.detectHost(); call targetAllDesktopClassifiers() for cross-OS builds
            targetClassifiers.add(OsArchClassifier.WINDOWS_X64)
            targetClassifiers.add(OsArchClassifier.LINUX_X64)
            targetClassifiers.add(OsArchClassifier.MACOS_ARM64)
        }
        android(true)
        ios(true)
        web(true) // Compiles both Kotlin/Wasm (wasmJs, default) and Kotlin/JS IR (js, automatic fallback)
    }

    // 2. Application Identity, Platform Icons, & Web Icon (.svg Preferred for Web)
    application {
        appName = "My GenericUI App"
        // The Web splash screen (#genericui-splash) and index.html favicon use the project icons,
        // with `webIcon` overriding the Web icon (.svg vector icons are preferred for the Web icon):
        webIcon("icon.svg")
        icons(
            windows = "icon.ico",
            mac = "icon.icns",
            linux = "icon.png",
            web = "icon.svg", // Preferred: .svg (image/svg+xml) for crisp favicon & splash rendering
            common = "icon.png"
        )
    }

    // 3. Native GPU Drivers, Translation Layers, Shader Compilers, & :extensions:media VLC HDR
    graphics {
        bundleAngleOnWindows = true               // Stage Google ANGLE (libEGL.dll, libGLESv2.dll, d3dcompiler_47.dll)
        angleBackendRenderer = "d3d11"            // "d3d11", "d3d12", or "vulkan"
        bundleMoltenVkOnApple = true              // Stage MoltenVK (libMoltenVK.dylib / MoltenVK_icd.json)
        bundleShaderc = true                      // Include Shaderc for runtime GLSL -> SPIR-V compilation
        enableVulkanValidationLayersInDebug = true
        preferOpenGLES31 = true
        bundleVlcOnDesktop = true                 // Enable :extensions:media:desktop VLC engine on :windows, :macos, :gtk, :qt
        vlcPrimaryShaderBackend = "vulkan"        // Primary VLC YUV & HDR tone-mapping pipeline (#version 450 SPIR-V)
        vlcFallbackShaderBackend = "opengles"     // Automatic fallback pipeline (#version 300 es, via ANGLE on Windows)
        enableVlcHdrToneMapping = true            // Enable ST 2084 PQ / HLG / Dolby Vision tone-mapping
        enableVlcHardwareDecode = true            // Probe D3D11VA, DXVA2, NVDEC, VAAPI, VDPAU, VideoToolbox + SW fallback
    }

    // 3. First-Party Extension Modules (Transitive Dependencies & Submodules Resolved Automatically)
    extensions {
        gpu()              // Project-of-Projects: :extensions:gpu (:common, :desktop, :mobile, :swing, :web)
        imageProcessing()  // Single KMP Project:  :extensions:imageprocessing (transitively enables gpu)
        canvas2d()         // Project-of-Projects: :extensions:canvas2d (:common, :desktop, :mobile, :swing, :web)
        canvas3d()         // Project-of-Projects: :extensions:canvas3d (transitively enables gpu)
        media()            // Project-of-Projects: :extensions:media (transitively enables gpu)
        webView()          // Project-of-Projects: :extensions:webview (:common, :desktop, :mobile, :swing, :web; Chromium + NJS JIT on Desktop)
        codeEditor()       // Single KMP Project:  :extensions:codeeditor (transitively enables canvas2d)
        aiChat()           // Single KMP Project:  :extensions:aichat (transitively enables codeEditor -> canvas2d)
        threeJs()          // Single KMP Project:  :extensions:threejs (transitively enables gpu + canvas3d)
        box2d()            // Single KMP Project:  :extensions:box2d (transitively enables canvas2d + canvas3d -> gpu)
        box3d()            // Single KMP Project:  :extensions:box3d (transitively enables canvas3d + threeJs -> gpu)
    }

    // 5. SASS / CSS Compilation Pipeline
    styles {
        compileSass = true
        sourceDir = "src/commonMain/resources/styles"
        outputDir = "generated/genericui/styles"
        minify = false
        includeBootstrapVariables = true
    }

    // 6. Hierarchical Multiplatform SourceSet Configuration
    sourceSets {
        common {
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
        }
        systemCommon {
            // Shared across all OS/system targets (Desktop + Mobile, excluding Web)
        }
        desktopCommon {
            // Shared across all Desktop targets (Swing + Native Desktop)
        }
        desktopNativeCommon {
            // Shared across all Native Desktop targets (Windows, macOS, Linux/GTK/Qt)
        }
        linuxCommon {
            // Shared across Linux GTK and Qt targets (linuxCommon / linuxMain / linuxTest)
        }
        mobileCommon {
            // Shared across Mobile targets (Android, iOS)
        }
        web {
            // Shared across Web targets (Kotlin/Wasm + Kotlin/JS IR)
        }
    }
}
```

---

## 3. Hierarchical SourceSet Tree

`GenericUiMultiplatformConfigurator` automatically constructs and wires the following sourceSet hierarchy so shared code can be placed at the exact level of granularity needed:

```text
- common (common / commonMain / commonTest)
  - web (web / webMain / webTest -> wasmJsMain, jsMain / wasmJsTest, jsTest)
  - systemCommon (systemCommon / systemMain / systemTest)
    - desktopCommon (desktopCommon / desktopMain / desktopTest)
      - swing (swing / swingMain / swingTest -> jvmMain / jvmTest)
      - desktopNativeCommon (desktopNativeCommon / desktopNativeMain / desktopNativeTest, alias: nativeDesktopCommon)
        - windows (windows / windowsMain / windowsTest -> mingwX64Main / mingwX64Test)
        - mac (mac / macMain / macosMain / macTest / macosTest -> macosArm64Main, macosX64Main)
        - linuxCommon (linuxCommon / linuxMain / linuxTest -> linuxX64Main, linuxArm64Main)
          - gtk (gtk / gtkMain / gtkTest)
          - qt (qt / qtMain / qtTest)
    - mobileCommon (mobileCommon / mobileMain / mobileTest)
      - android (android / androidMain / androidTest)
      - ios (ios / iosMain / iosTest -> iosArm64Main, iosSimulatorArm64Main)
```

---

## 4. Optional LWJGL OpenGL ES & Vulkan Wiring on `:swing`

By design, `:swing` has **zero mandatory native dependencies** when only 2D widgets and `Canvas2D` (`Java2D` / `Graphics2D`) are used. When hardware-accelerated `GLCanvas` or `VKCanvas` is desired on Java/Swing, enabling `useLwjglGL` or `useVulkan` inside `backends { swing { ... } }` instructs `GenericUiGradlePlugin` to wire the exact LWJGL 3 artifacts and native classifiers:

| Flag | Injected JVM Implementation Artifacts | Injected Runtime Native Classifiers (`targetClassifiers`) |
| :--- | :--- | :--- |
| **`useLwjglGL = true`** | `org.lwjgl:lwjgl`<br/>`org.lwjgl:lwjgl-jawt`<br/>`org.lwjgl:lwjgl-egl`<br/>`org.lwjgl:lwjgl-opengles`<br/>`org.lwjgl:lwjgl-opengl` | `lwjgl:<ver>:<classifier>`<br/>`lwjgl-egl:<ver>:<classifier>`<br/>`lwjgl-opengles:<ver>:<classifier>`<br/>`lwjgl-opengl:<ver>:<classifier>` |
| **`useVulkan = true`** | `org.lwjgl:lwjgl`<br/>`org.lwjgl:lwjgl-jawt`<br/>`org.lwjgl:lwjgl-vulkan`<br/>`org.lwjgl:lwjgl-shaderc`<br/>`org.lwjgl:lwjgl-vma` | `lwjgl:<ver>:<classifier>`<br/>`lwjgl-shaderc:<ver>:<classifier>`<br/>`lwjgl-vma:<ver>:<classifier>`<br/>`lwjgl-vulkan:<ver>:natives-macos*` (MoltenVK on macOS) |

### Supported `OsArchClassifier` Entries
- `OsArchClassifier.WINDOWS_X64` (`"natives-windows"`)
- `OsArchClassifier.WINDOWS_ARM64` (`"natives-windows-arm64"`)
- `OsArchClassifier.LINUX_X64` (`"natives-linux"`)
- `OsArchClassifier.LINUX_ARM64` (`"natives-linux-arm64"`)
- `OsArchClassifier.MACOS_X64` (`"natives-macos"`)
- `OsArchClassifier.MACOS_ARM64` (`"natives-macos-arm64"`)

---

## 4. Registered Cacheable Gradle Tasks

| Task Name | Implementation Class | Output Directory | Description |
| :--- | :--- | :--- | :--- |
| `bundleGenericUiAngle` | `AngleNativeBundleTask` | `build/generated/genericui/angle` | Generates `angle-runtime-manifest.json` and per-architecture descriptors for `libEGL.dll`, `libGLESv2.dll`, and `d3dcompiler_47.dll` (`EGL_PLATFORM_ANGLE_TYPE_D3D11_ANGLE`). |
| `bundleGenericUiVulkan` | `VulkanNativeBundleTask` | `build/generated/genericui/vulkan` | Generates `vulkan-runtime-manifest.json` and `MoltenVK_icd.json` for Vulkan 1.2+ swapchains, MoltenVK Metal translation, and `shaderc`. |
| `compileGenericUiSass` | `GenericUiSassCompileTask` | `build/generated/genericui/styles` | Compiles `.scss` and `.sass` files into `.css` bundles, substitutes `$variables`, injects `:root` Bootstrap 5 CSS custom properties, optionally minifies CSS, and outputs `styles-manifest.json`. |
| `packageGenericUiWebDistribution` | `GenericUiWebDistributionTask` | `build/dist/web` | Packages the Wasm-primary (`wasmJs`) and JS-IR-fallback (`js`) Web distribution alongside `genericui-loader.js`, the resolved Web icon (`.svg` preferred for favicon and splash icon), and `index.html` with SHA-1 hashes appended to `<script type="loader-wasm">`, `<script type="loader-js">`, and `#genericui-loader-script`. |

---

## 5. Module Class Structure (`com.nexustools.genericui.plugin`)

In strict compliance with GenericUI's **1-Class-Per-File** standard, `:gradle-plugin` is organized into 11 focused classes:
1. `OsArchClassifier.kt` — Host OS/CPU detector and LWJGL native classifier enum.
2. `SwingBackendConfig.kt` — Configuration model for `:swing` (`enabled`, `useLwjglGL`, `useVulkan`, `lwjglVersion`, `targetClassifiers`).
3. `GenericUiBackendsConfig.kt` — DSL block for enabling `:windows`, `:macos`, `:gtk`, `:qt`, `:swing`, `:android`, `:ios`, and `:web`.
4. `GenericUiGraphicsConfig.kt` — DSL block for Google ANGLE, MoltenVK, and Shaderc bundling.
5. `GenericUiExtensionsConfig.kt` — DSL block for all 11 first-party extensions (`gpu`, `imageProcessing`, `canvas2d`, `canvas3d`, `media`, `webView`, `codeEditor`, `aiChat`, `threeJs`, `box2d`, `box3d`) with transitive dependency resolution.
6. `GenericUiStylesConfig.kt` — DSL block for SASS/CSS stylesheet compilation.
7. `GenericUiExtension.kt` — Top-level `genericui` extension aggregating `backends`, `graphics`, `extensions`, and `styles`.
8. `AngleNativeBundleTask.kt` — `@CacheableTask` for Google ANGLE staging on Windows.
9. `VulkanNativeBundleTask.kt` — `@CacheableTask` for Vulkan, MoltenVK, and Shaderc staging.
10. `GenericUiSassCompileTask.kt` — `@CacheableTask` for `.scss`/`.sass` to `.css` compilation.
11. `GenericUiGradlePlugin.kt` — Entry-point `Plugin<Project>` implementation that automatically resolves Project-of-Projects extension submodules (`:common`, `:desktop`, `:mobile`, `:swing`, `:web`) or Single-Project extensions (`:extensions:<name>`) based on the enabled backends.
