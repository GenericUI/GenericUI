# GenericUI IntelliJ IDEA Plugin Guide (`:idea-plugin`)

The `:idea-plugin` module (`idea-plugin/`) provides an IntelliJ IDEA integration for scaffolding new `GenericUI` multiplatform projects with the single-`commonMain` `@MainAppFragment` architecture and managing target backends, extensions, window policies, and icons in existing projects using interactive dialogs.

---

## Navigation & Related Documentation

- **Root Repository**: [GenericUI README](../README.md) | [Master Documentation Index](README.md) | [Idea Plugin README](../idea-plugin/README.md)
- **Fragments, Windows, & Application**: [Fragments & Windows Guide (`fragments-and-windows.md`)](fragments-and-windows.md) | [Application & BackendTarget Guide (`application.md`)](application.md) | [iPadOS Guide (`ipados.md`)](ipados.md)
- **Gradle & SourceSets**: [Gradle Plugin Guide (`gradle-plugin.md`)](gradle-plugin.md) | [Hierarchical SourceSet Setup (`sourcesets.md`)](sourcesets.md)
- **Backends & Extensions**: [Platform Backends Guide (`backends.md`)](backends.md) | [First-Party Extensions Guide (`extensions.md`)](extensions.md) · [Gamepad (`gamepad.md`)](gamepad.md) · [Game Engines (`game-engines.md`)](game-engines.md)

---

## 1. Key Capabilities

1. **Zero-Boilerplate Single-`commonMain` Project Scaffolding (`GenericUI.NewProject`)**:
   - Generates a clean `build.gradle.kts` with `id("com.nexustools.genericui")` (and `id("com.nexustools.genericui.settings")` in `settings.gradle.kts`), configuring `app`, `window` (optional `alwaysOnTop`, `aspectRatio`), `theme { mode = "auto" }`, Apple icons (`appleIcon`, `macos { icon(...) }`, `ios { icon(...) }`, `ipados { icon(...) }`), `backends`, `extensions`, and `styles { globalStyleSheet = "styles/main.scss" }`, plus `@MainAppFragment` `MainFragment.kt` and `JestElectricRunner` `MainFragmentTest.kt`.
   - Generates `bootstrap { ... }` / `style { ... }` builders when `dsl()` (`:extensions:ui:dsl`) is checked, or `getBootstrap().apply { ... }` / `getStyle().apply { ... }` accessors otherwise.
   - No manual platform source sets or per-backend `main()` files are generated; the Gradle plugin generates all 9 platform launchers in `build/generated/genericui/` at build time.
2. **Interactive Target Backend Checkboxes (`GenericUiTargetCheckboxPanel` & `GenericUiTargetBackend`)**:
   - **Desktop Targets**: Windows (`windows`), macOS (`macos`), Linux GTK4 (`gtk`), Linux Qt6 (`qt`), and Java Swing (`swing`, with sub-checkboxes for LWJGL OpenGL ES & Vulkan).
   - **Mobile Targets**: Android (`android`), iOS (`ios` — iPhone), and **iPadOS (`ipados` — iPad)**.
   - **Web Targets**: Web (`web` — dual Kotlin/Wasm primary + Kotlin/JS IR fallback).
   - **Quick-Select Preset Buttons**: `"All Except Swing"`, `"Desktop Only (No Swing)"`, `"Mobile Only"`, `"Apple Only"`, `"Select All"`, `"Clear All"`.
3. **Transitive Extension Checkboxes (`GenericUiExtensionModule` — All 19 Categorized Extensions)**:
   - Interactive checkboxes across all 5 categories (`Graphics`, `Physics`, `Game & Input`, `Media`, `UI`):
     - **Graphics**: `gpu`, `imageProcessing`, `canvas2d`, `canvas3d`, `threeJs`
     - **Physics**: `box2d`, `box3d`
     - **Game & Input**: `gamepad`, `engine`, `engine2d`, `engine3d`
     - **Media**: `media`, `mediaControls`
     - **UI**: `dsl`, `overlay`, `alien`, `webView`, `codeEditor`, `aiChat`
   - Checking a dependent extension (e.g., `engine3d`, `box3d`, `media`, `aiChat`) automatically checks its required transitive extensions.
4. **Starter Project Templates (`GenericUiProjectTemplateType`)**:
   - `STANDARD_FRAGMENT` — Zero-boilerplate `@MainAppFragment` with `FragmentHostFragment` and `TitleBar`
   - `DOCK_WORKSPACE` — `@MainAppFragment` hosting `DockWorkspaceFragment` (GIMP Single-Window vs. Multi-Window IDE)
   - `OVERLAY_DOCK_3D` — `@MainAppFragment` hosting `OverlayDockWorkspaceFragment` (Full-viewport Three.js 3D canvas with `:extensions:ui:overlay` floating panels)
   - `ADAPTIVE_SPLIT_TAB` — `@MainAppFragment` hosting `AdaptiveSplitTabFragment` (Responsive Desktop/Tablet multi-column + Phone portrait tab flow)
   - `WIZARD_PAGE_SYSTEM` — `@MainAppFragment` hosting `CenteredMenuPageFragment` & `ValidatedStepPageFragment` (Multi-step validated wizard)
5. **Live `build.gradle.kts` Inspector & In-Place Updater (`GenericUiBuildFileInspector` & `GenericUI.ManageTargets`)**:
   - Parses an existing project's `build.gradle.kts` (`app`, `application`, `window`, `theme`, `backends`, Apple icons, `graphics`, `extensions`, `styles`, `testing`), pre-populates checkboxes in `Tools -> GenericUI -> Manage GenericUI Targets & Extensions...`, and updates `build.gradle.kts` cleanly in place.
