# GenericUI IntelliJ IDEA Plugin (`idea-plugin`)

IntelliJ IDEA plugin and project scaffolding toolkit for creating and managing **GenericUI** Kotlin Multiplatform applications.

## Features

1. **New GenericUI Project Wizard (`GenericUI.NewProject`)**
   - Scaffolds a single-`commonMain` Kotlin Multiplatform project applying `id("com.nexustools.genericui.settings")` in `settings.gradle.kts` and `id("com.nexustools.genericui")` in `build.gradle.kts` across the consolidated core modules (`:common`, `:system`, `:jvm`, `:apple`, `:desktop`, `:mobile`, `:web`, and `:tests` `JestElectricRunner`).
   - Supports all 9 target backends (`windows`, `macos`, `gtk`, `qt`, `swing`, `android`, `ios`, `ipados`, `web`) with quick presets (`All Except Swing`, `Desktop Only (No Swing)`, `Mobile Only`, `Apple Only`, `Select All`, `Clear All`).
   - Generates a `@MainAppFragment`-annotated `MainFragment.kt` in `src/commonMain/kotlin/<package>/MainFragment.kt` (no manual per-platform entry points or `Main.kt` files required), plus a `MainFragmentTest.kt` `JestElectricRunner` suite in `src/commonTest/kotlin/<package>/MainFragmentTest.kt`.
   - Configures build-time SASS/CSS compilation (`src/commonMain/resources/styles/main.scss`) with pre-compiled stylesheet embedding (`globalStyleSheet = "styles/main.scss"`), automatic OS dark/light theme synchronization (`theme { mode = "auto" }`), optional `window { alwaysOnTop; aspectRatio(16, 9) }` defaults, and shared `appleIcon` / per-target Apple icon options (`macos { icon(...) }`, `ios { icon(...) }`, `ipados { icon(...) }`).
   - Generates starter component styling code in `MainFragment.kt`:
     - Uses `bootstrap { ... }` and `style { ... }` builders (and companion `:dsl` game/extension builders) when `:extensions:ui:dsl` (`dsl()`) is enabled.
     - Uses core `getBootstrap().apply { ... }` and `getStyle().apply { ... }` accessors when `:extensions:ui:dsl` is not enabled.

2. **Manage GenericUI Targets & Extensions Dialog (`GenericUI.ManageTargets`)**
   - Inspects an existing project's `build.gradle.kts` (`app`, `application`, `window`, `theme`, `backends`, `macos`/`ios`/`ipados` icons, `graphics`, `extensions`, `styles`, `testing`) via `GenericUiBuildFileInspector`.
   - Automatically resolves transitive dependencies across all 19 categorized extensions (`Graphics`, `Physics`, `Game & Input`, `Media`, `UI`, and companion `:dsl` modules).
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
