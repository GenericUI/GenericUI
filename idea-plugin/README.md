# GenericUI IntelliJ IDEA Plugin (`idea-plugin`)

IntelliJ IDEA plugin and project scaffolding toolkit for creating and managing **GenericUI** Kotlin Multiplatform applications.

## Features

1. **New GenericUI Project Wizard (`GenericUI.NewProject`)**
   - Scaffolds a single-`commonMain` Kotlin Multiplatform project applying `id("com.nexustools.genericui.settings")` in `settings.gradle.kts` and `id("com.nexustools.genericui")` in `build.gradle.kts` across the consolidated core modules (`:common`, `:system`, `:jvm`, `:apple`, `:desktop`, `:mobile`, `:web`, and `:tests` `JestElectricRunner`).
   - Supports all 9 target backends (`windows`, `macos`, `gtk`, `kde`, `swing`, `android`, `ios`, `ipados`, `web`) with quick presets (`All Except Swing`, `Desktop Only (No Swing)`, `Mobile Only`, `Apple Only`, `Select All`, `Clear All`) and all **7** `GenericUiProjectTemplateType` templates (`STANDARD_FRAGMENT`, `DOCK_WORKSPACE`, `OVERLAY_DOCK_3D`, `ADAPTIVE_SPLIT_TAB`, `GAME_2D`, `GAME_3D`, `WIZARD_PAGE_SYSTEM`).
   - Generates `@MainAppFragment`-annotated `MainFragment.kt` in `src/commonMain/kotlin/<package>/MainFragment.kt` (no manual per-platform entry points or `Main.kt` files required), `AppServiceWorker.kt` when `serviceWorker()` is enabled (using core `:extensions:system:serviceworker` APIs directly), plus a `MainFragmentTest.kt` `JestElectricRunner` suite in `src/commonTest/kotlin/<package>/MainFragmentTest.kt`.
   - Configures build-time SASS/CSS compilation (`src/commonMain/resources/styles/main.scss`) with pre-compiled stylesheet embedding (`globalStyleSheet = "styles/main.scss"`), automatic OS dark/light theme synchronization (`theme { mode = "auto" }`), optional `window { alwaysOnTop; aspectRatio(16, 9) }` defaults, and shared `appleIcon` / per-target Apple icon options (`macos { icon(...) }`, `ios { icon(...) }`, `ipados { icon(...) }`).
   - Generates starter component styling code in `MainFragment.kt`:
     - Uses `bootstrap { ... }` and `style { ... }` builders (and companion `:dsl` extension builders where provided) when `:extensions:ui:dsl` (`dsl()`) is enabled.
     - Uses core `getBootstrap().apply { ... }` and `getStyle().apply { ... }` accessors when `:extensions:ui:dsl` is not enabled.

2. **Manage GenericUI Targets & Extensions Dialog (`GenericUI.ManageTargets`)**
   - Inspects an existing project's `build.gradle.kts` (`app`, `application`, `window`, `theme`, `backends`, `macos`/`ios`/`ipados` icons, `graphics`, `extensions` including `ipc()`, `svg()`, `pip()`, `schema()`, `serviceWorker()`, `alien()`, and `webView { ... }`, `styles`, `testing`) via `GenericUiBuildFileInspector`.
   - Automatically resolves transitive dependencies across **24** extension checkboxes organized under **7** categories (`Graphics` including `svg`, `Physics`, `Game & Input`, `Media`, **`Data`**, **`System`** including Desktop + Android-only `ipc` and `serviceWorker`, `UI` including `pip`, `alien`, and `webView` out-of-process Alien + SVG + Canvas2D + Canvas3D + Media + NJS + IPC rendering on Desktop / native WebView on Mobile / `<iframe>` on Web), plus companion `:alien` (`svg:alien`, `canvas2d:alien`, `canvas3d:alien`, `media:alien`) and `:dsl` modules (with no `:dsl` on `serviceWorker`, `mediaControls`, `pip`, `gamepad`, or `ipc`). Selecting **`schema()`** (`:extensions:data:schema`) or **`serviceWorker()`** (`:extensions:system:serviceworker`) activates `:gradle-plugin`'s compile-time `@SchemaData` descriptor codegen and `@MainServiceWorker` validation / `@ServiceWorkerMessage` IPC codec generation (no separate PSI `LocalInspectionTool` editor inspections live in `:idea-plugin`).
   - Updates `build.gradle.kts` in-place while preserving project metadata, theme mode, window defaults, and global stylesheet settings.

## Generated `genericui { ... }` DSL Example

```kotlin
genericui {
    app {
        id = "com.example.myapp"
        name = "MyApp"
        version = "1.0.0"
    }
    application {
        baseName = "MyApp"
    }
    theme {
        mode = "auto"
    }
    backends {
        allExceptSwing()
    }
    graphics {
        bundleAngleOnWindows = true
        bundleMoltenVkOnApple = true
        bundleShaderc = true
    }
    styles {
        compileSass = true
        globalStyleSheet = "styles/main.scss"
        sourceDir = "src/commonMain/resources/styles"
    }
}
```
