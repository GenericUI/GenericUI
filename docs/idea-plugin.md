# GenericUI IntelliJ IDEA Plugin Guide (`:idea-plugin`)

The `:idea-plugin` module (`idea-plugin/`) provides an IntelliJ IDEA integration for scaffolding new `GenericUI` multiplatform projects with the single-`commonMain` `@MainAppFragment` architecture and managing target backends, extensions, and [hierarchical source sets](sourcesets.md) in existing projects using interactive checkbox dialogs.

---

## Navigation & Related Documentation

- **Root Repository**: [GenericUI README](../README.md) | [Master Documentation Index](README.md)
- **Fragments, Windows, & Application**: [Fragments & Windows Guide (`fragments-and-windows.md`)](fragments-and-windows.md) | [Application & BackendTarget Guide (`application.md`)](application.md) | [Resource System Guide (`resources.md`)](resources.md)
- **Gradle & SourceSets**: [Gradle Plugin Guide (`gradle-plugin.md`)](gradle-plugin.md) | [Hierarchical SourceSet Setup (`sourcesets.md`)](sourcesets.md)
- **Backends & Extensions**: [Platform Backends Guide (`backends.md`)](backends.md) | [First-Party Extensions Guide (`extensions.md`)](extensions.md)

---

## 1. Key Capabilities

1. **Zero-Boilerplate Single-`commonMain` Project Scaffolding**:
   - Generates a clean `build.gradle.kts` with `id("com.nexustools.genericui")`, an `app { ... }`, `window { ... }`, `theme { ... }`, `backends { ... }`, and `extensions { ... }` configuration block, plus an annotated `@MainAppFragment` class in `src/commonMain/kotlin` and `src/commonMain/resources/` directories (`drawable/`, `values/strings.xml`, `raw/`).
   - No manual platform source sets or launcher files are generated on disk unless optional platform overrides are requested; the Gradle plugin generates all 8 platform launchers in `build/generated/genericui/` at build time.
2. **Interactive Target Backend Checkboxes (`GenericUiTargetCheckboxPanel`)**:
   - **Desktop Targets**: Windows (`:desktop:windows`), macOS (`:desktop:macos`), Linux GTK (`:desktop:gtk`), Linux Qt (`:desktop:qt`), and Java Swing (`:desktop:swing`, with sub-checkboxes for LWJGL OpenGL ES & Vulkan).
   - **Mobile Targets**: Android (`:mobile:android`) and iOS (`:mobile:ios`).
   - **Web Targets**: Web (`:web` — dual Kotlin/Wasm primary + Kotlin/JS IR fallback).
   - **Quick-Select Preset Buttons**: `"All Except Swing"`, `"Desktop Only (No Swing)"`, `"Mobile Only"`, `"Select All"`, `"Clear All"`.
3. **Transitive Extension Checkboxes (`GenericUiExtensionModule`)**:
   - Interactive checkboxes for all 15 first-party extensions: `gpu`, `imageProcessing`, `overlay`, `canvas2d`, `canvas3d`, `media`, `mediaControls`, `webView`, `alien`, `codeEditor`, `aiChat`, `threeJs`, `box2d`, `box3d`, and `dsl`.
   - Checking a dependent extension (e.g., `mediaControls` or `threeJs`) automatically checks its required transitive extensions (`media` + `overlay` + `gpu`, or `canvas3d` + `gpu`).
4. **Starter Project Templates (`GenericUiProjectTemplateType`)**:
   - `STANDARD_FRAGMENT` — Zero-boilerplate `@MainAppFragment` with `FragmentHostFragment` and `IWindow.titleBar` / `IFragment.navBar`
   - `DOCK_WORKSPACE` — `@MainAppFragment` hosting `DockWorkspaceFragment` (GIMP Single-Window vs. Multi-Window IDE)
   - `OVERLAY_DOCK_3D` — `@MainAppFragment` hosting `OverlayDockWorkspaceFragment` (Full-viewport Three.js 3D canvas with `:extensions:overlay` floating panels)
   - `ADAPTIVE_SPLIT_TAB` — `@MainAppFragment` hosting `AdaptiveSplitTabFragment` (Responsive Desktop/Tablet multi-column + Phone portrait tab flow)
   - `WIZARD_PAGE_SYSTEM` — `@MainAppFragment` hosting `CenteredMenuPageFragment` & `ValidatedStepPageFragment` (Multi-step validated wizard)
5. **Live `build.gradle.kts` Inspector & In-Place Updater (`GenericUiBuildFileInspector`)**:
   - Parses an existing project's `build.gradle.kts`, pre-populates the checkboxes in `Tools -> GenericUI -> Manage GenericUI Targets & Extensions...`, and updates `build.gradle.kts` in-place.
