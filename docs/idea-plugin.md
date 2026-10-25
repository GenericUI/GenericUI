# GenericUI IntelliJ IDEA Plugin Guide (`:idea-plugin`)

The `:idea-plugin` module (`D:\IdeaProjects\GenericUI\idea-plugin`) provides an IntelliJ IDEA integration for scaffolding new `GenericUI` multiplatform projects and managing target backends, extensions, and [hierarchical source sets](sourcesets.md) in existing projects using interactive checkbox dialogs.

---

## Navigation & Related Documentation

- **Root Repository**: [GenericUI README](../README.md) | [Master Documentation Index](README.md)
- **Gradle & SourceSets**: [Gradle Plugin Guide (`gradle-plugin.md`)](gradle-plugin.md) | [Hierarchical SourceSet Setup (`sourcesets.md`)](sourcesets.md)
- **Backends & Extensions**: [Platform Backends Guide (`backends.md`)](backends.md) | [First-Party Extensions Guide (`extensions.md`)](extensions.md)

---

## 1. Key Capabilities

1. **Interactive Target Backend Checkboxes (`GenericUiTargetCheckboxPanel`)**:
   - **Desktop Targets**: Windows (`:desktop:windows`), macOS (`:desktop:macos`), Linux GTK (`:desktop:gtk`), Linux Qt (`:desktop:qt`), and Java Swing (`:desktop:swing`, with sub-checkboxes for LWJGL OpenGL ES & Vulkan).
   - **Mobile Targets**: Android (`:mobile:android`) and iOS (`:mobile:ios`).
   - **Web Targets**: Web (`:web` — dual Kotlin/Wasm primary + Kotlin/JS IR fallback).
   - **Quick-Select Preset Buttons**: `"All Except Swing"`, `"Desktop Only (No Swing)"`, `"Mobile Only"`, `"Select All"`, `"Clear All"`.
2. **Transitive Extension Checkboxes (`GenericUiExtensionModule`)**:
   - Interactive checkboxes for `gpu`, `imageProcessing`, `canvas2d`, `canvas3d`, `media`, `webView`, `codeEditor`, `aiChat`, `threeJs`, `box2d`, `box3d`, and `dsl`.
   - Checking a dependent extension (e.g., `threeJs`) automatically checks its required transitive extensions (`canvas3d` and `gpu`).
3. **5 Starter Project Templates (`GenericUiProjectTemplateType`)**:
   - `STANDARD_FRAGMENT` — Standard `FragmentApplication` + `Fragment`
   - `DOCK_WORKSPACE` — `DockWorkspaceFragment` (GIMP Single-Window vs. Multi-Window IDE)
   - `OVERLAY_DOCK_3D` — `OverlayDockWorkspaceFragment` (Full-viewport Three.js 3D canvas with floating overlay panels)
   - `ADAPTIVE_SPLIT_TAB` — `AdaptiveSplitTabFragment` (Responsive Desktop/Tablet multi-column + Phone portrait tab flow)
   - `WIZARD_PAGE_SYSTEM` — `CenteredMenuPageFragment` & `ValidatedStepPageFragment` (Multi-step validated wizard)
4. **Live `build.gradle.kts` Inspector & In-Place Updater (`GenericUiBuildFileInspector`)**:
   - Parses an existing project's `build.gradle.kts`, pre-populates the checkboxes in `Tools -> GenericUI -> Manage GenericUI Targets & Extensions...`, and updates `build.gradle.kts` in-place.
