# GenericUI IntelliJ IDEA Plugin (`idea-plugin`)

IntelliJ IDEA plugin and project scaffolding toolkit for creating and managing **GenericUI** Kotlin Multiplatform applications.

## Features

1. **New GenericUI Project Wizard (`GenericUI.NewProject`)**
   - Scaffolds a single-`commonMain` Kotlin Multiplatform project applying `id("com.nexustools.genericui.settings")` in `settings.gradle.kts` and `id("com.nexustools.genericui")` in `build.gradle.kts`.
   - Generates a `@MainAppFragment`-annotated `MainFragment.kt` in `src/commonMain/kotlin/<package>/MainFragment.kt` (no manual per-platform entry points or `Main.kt` files required).
   - Configures build-time SASS/CSS compilation (`src/commonMain/resources/styles/main.scss`) with pre-compiled stylesheet embedding (`globalStyleSheet = "styles/main.scss"`) and automatic OS dark/light theme synchronization (`theme { mode = "auto" }`).
   - Generates starter component styling code in `MainFragment.kt`:
     - Uses `bootstrap { ... }` and `style { ... }` builders when the `:extensions:dsl` module (`dsl()`) is enabled.
     - Uses core `getBootstrap().apply { ... }` and `getStyle().apply { ... }` accessors when `:extensions:dsl` is not enabled.

2. **Manage GenericUI Targets & Extensions Dialog (`GenericUI.ManageTargets`)**
   - Inspects an existing project's `build.gradle.kts` (`app`, `application`, `theme`, `backends`, `graphics`, `extensions`, `styles`) via `GenericUiBuildFileInspector`.
   - Provides quick-select target presets (`All Except Swing`, `Desktop Only (No Swing)` / `allNativeDesktop()`, `Mobile Only`, `Select All`, `Clear All`).
   - Automatically resolves transitive extension dependencies (e.g., selecting `threeJs()` automatically enables `canvas3d()`, `overlay()`, `canvas2d()`, and `gpu()`).
   - Updates `build.gradle.kts` in-place while preserving project metadata, theme mode, and global stylesheet settings.

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
