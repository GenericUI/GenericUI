# GenericUI Fragments, Windows (`alwaysOnTop` & `aspectRatio`), & Navigation Guide

> **Root Repository**: [**GenericUI (`../README.md`)**](../README.md) | **Master Docs Index**: [**`docs/README.md`**](./README.md)  
> **Related Guides**: [Application, Theming, & Stylesheets (`application.md`)](./application.md) · [iPadOS Backend (`ipados.md`)](./ipados.md) · [Hybrid Layouts & Helpers (`layouts-and-helpers.md`)](./layouts-and-helpers.md) · [Gradle Plugin (`gradle-plugin.md`)](./gradle-plugin.md) · [SourceSet & Variant Scopes (`sourcesets.md`)](./sourcesets.md) · [Resources (`resources.md`)](./resources.md) · [Components & Docking (`components-and-docking.md`)](./components-and-docking.md)

This guide covers GenericUI's annotation-driven `Fragment` discovery (`@MainAppFragment` and `@AppFragment`), `AppTarget` specificity resolution (`0..6`), `FragmentBundle` command-line arguments on system targets, `IFragment`, `IWindow` (including **`isAlwaysOnTop` / `setAlwaysOnTop`** and windowed-only client-viewport **`aspectRatio`** locking), `TitleCapabilities`, `launchFragment(FragmentClass, LaunchArgs)`, 1:1 generated Android `AppCompatActivity` classes, and multi-screen navigation with `FragmentHostFragment` and `FragmentHostSystem`.

---

## 1. Annotation-Driven Fragment Discovery (`@MainAppFragment` & `@AppFragment`)

In GenericUI's single-`commonMain` architecture, `Fragment` subclasses are annotated directly with [`@MainAppFragment`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/fragment/MainAppFragment.kt) and [`@AppFragment`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/fragment/AppFragment.kt), registering constructor factories automatically in [`FragmentRegistry`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/fragment/FragmentRegistry.kt) (which throws `IllegalStateException` immediately if an unregistered fragment is requested). On Android, the Gradle plugin also emits 1:1 `<FragmentName>Activity : GenericUiFragmentActivity()` classes in `AndroidManifest.xml`.

### `AppTarget` Specificity Resolution (`0..6`) & Validation

[`AppTarget`](../common/src/commonMain/kotlin/com/nexustools/genericui/enums/system/AppTarget.kt) models multi-backend scopes and concrete backend targets across specificity ranks **`0..6`**:

| `AppTarget` Entry | Scope ID | `specificityRank` | Matching `BackendTarget`s |
| :--- | :--- | :---: | :--- |
| `AppTarget.COMMON` | `"common"` | `0` | All 9 backends (`Windows`, `MacOS`, `Gtk`, `Qt`, `Swing`, `Android`, `IOS`, `IPadOS`, `Web`) |
| `AppTarget.SYSTEM_COMMON` | `"systemCommon"` | `1` | All 8 OS/system backends (`Windows`, `MacOS`, `Gtk`, `Qt`, `Swing`, `Android`, `IOS`, `IPadOS`) |
| `AppTarget.APPLE_COMMON` | `"appleCommon"` | `2` | All 3 Apple Darwin targets (`MacOS`, `IOS`, `IPadOS`) |
| `AppTarget.JVM_COMMON` | `"jvmCommon"` | `2` | Both JVM targets (`Swing`, `Android`) |
| `AppTarget.DESKTOP_COMMON` | `"desktopCommon"` | `3` | All 5 desktop backends (`Windows`, `MacOS`, `Gtk`, `Qt`, `Swing`) |
| `AppTarget.MOBILE_COMMON` | `"mobileCommon"` | `3` | All 3 mobile backends (`Android`, `IOS`, `IPadOS`) |
| `AppTarget.NATIVE_DESKTOP_COMMON` | `"nativeDesktopCommon"` | `4` | All 4 native desktop backends (`Windows`, `MacOS`, `Gtk`, `Qt`) |
| `AppTarget.LINUX_COMMON` | `"linuxCommon"` | `5` | Both Linux desktop backends (`Gtk`, `Qt`) |
| `AppTarget.IOS_COMMON` | `"iosCommon"` | `5` | Both Apple UIKit mobile targets (`IOS`, `IPadOS`) |
| `WINDOWS`, `MACOS`, `GTK`, `QT`, `SWING`, `ANDROID`, `IOS`, `IPADOS`, `WEB` | Concrete ID | `6` | That exact single `BackendTarget` |

For every enabled backend target, `genericuiValidateApp` resolves `@MainAppFragment` along its specificity chain (failing immediately if two candidates tie at highest specificity or none match):

| Enabled Backend Target | Specificity Resolution Chain (`6 → 5 → 4 → 3 → 2 → 1 → 0`) |
| :--- | :--- |
| **`Windows`** | `WINDOWS (6)` $\rightarrow$ `NATIVE_DESKTOP_COMMON (4)` $\rightarrow$ `DESKTOP_COMMON (3)` $\rightarrow$ `SYSTEM_COMMON (1)` $\rightarrow$ `COMMON (0)` |
| **`MacOS`** | `MACOS (6)` $\rightarrow$ `NATIVE_DESKTOP_COMMON (4)` $\rightarrow$ `DESKTOP_COMMON (3)` $\rightarrow$ `APPLE_COMMON (2)` $\rightarrow$ `SYSTEM_COMMON (1)` $\rightarrow$ `COMMON (0)` |
| **`Gtk` / `Qt`** | `GTK / QT (6)` $\rightarrow$ `LINUX_COMMON (5)` $\rightarrow$ `NATIVE_DESKTOP_COMMON (4)` $\rightarrow$ `DESKTOP_COMMON (3)` $\rightarrow$ `SYSTEM_COMMON (1)` $\rightarrow$ `COMMON (0)` |
| **`Swing`** | `SWING (6)` $\rightarrow$ `DESKTOP_COMMON (3)` $\rightarrow$ `JVM_COMMON (2)` $\rightarrow$ `SYSTEM_COMMON (1)` $\rightarrow$ `COMMON (0)` |
| **`Android`** | `ANDROID (6)` $\rightarrow$ `MOBILE_COMMON (3)` $\rightarrow$ `JVM_COMMON (2)` $\rightarrow$ `SYSTEM_COMMON (1)` $\rightarrow$ `COMMON (0)` |
| **`IOS`** | `IOS (6)` $\rightarrow$ `IOS_COMMON (5)` $\rightarrow$ `MOBILE_COMMON (3)` $\rightarrow$ `APPLE_COMMON (2)` $\rightarrow$ `SYSTEM_COMMON (1)` $\rightarrow$ `COMMON (0)` |
| **`IPadOS`** | `IPADOS (6)` $\rightarrow$ `IOS_COMMON (5)` $\rightarrow$ `MOBILE_COMMON (3)` $\rightarrow$ `APPLE_COMMON (2)` $\rightarrow$ `SYSTEM_COMMON (1)` $\rightarrow$ `COMMON (0)` |
| **`Web`** | `WEB (6)` $\rightarrow$ `COMMON (0)` |

---

## 2. `FragmentBundle` & Command-Line Arguments on System Targets

[`FragmentBundle`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/fragment/FragmentBundle.kt) carries startup state and process CLI arguments across all 8 system targets (`FragmentBundle.KEY_COMMAND_LINE_ARGS = "genericui:command_line_args"`): `savedState.hasCommandLineArgs()`, `savedState.getCommandLineArgs()`, `savedState.getCommandLineArgsArray()`, `FragmentBundle.fromCommandLineArgs(args)`, and `FragmentBundle.of(...)`.

---

## 3. `IFragment`, `launchFragment`, & `FragmentLaunchMode`

Both `IFragment`, `IWindow`, and `IApplication` support `launchFragment(fragmentClass, launchArgs, addToBackStack, launchMode)` backed by [`FragmentLaunchCoordinator`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/fragment/FragmentLaunchCoordinator.kt) across `SAME_WINDOW`, `CLEAR_TASK`, `NEW_WINDOW`, `NEW_PANEL_WINDOW`, and `NEW_DIALOG_WINDOW`.

---

## 4. `IWindow`, `isAlwaysOnTop`, Client-Viewport `aspectRatio`, & `TitleCapabilities`

[`IWindow`](../common/src/commonMain/kotlin/com/nexustools/genericui/window/IWindow.kt) is the unified window interface implemented across all four GenericUI window types:
1. [`Window`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/shell/Window.kt) — Top-level OS/UIKit window or browser viewport (throws `IllegalStateException` on `show()` if no `NativeFactory` is active or no content is attached). When running background `:extensions:system:serviceworker` tray processes on macOS, headless tray daemons stay in `NSApplicationActivationPolicyAccessory` (`serviceWorker { openWindowOnLaunch = false }`) until a visible window opens.
2. [`PanelWindow`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/dock/PanelWindow.kt) — Floating tool-modal dock utility window (`isAlwaysOnTop = true` and `isAlwaysOnTopOfOwner = true` by default).
3. [`DialogWindow`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/dialog/DialogWindow.kt) — Modal or modeless dialog window.
4. [`MdiWindow`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/shell/MdiWindow.kt) — In-viewport Multi-Document Interface sub-window inside `MdiContainer`.

### 4.1 Window Always-On-Top (`isAlwaysOnTop` / `setAlwaysOnTop`)

[`IWindow`](../common/src/commonMain/kotlin/com/nexustools/genericui/window/IWindow.kt) exposes floating z-order control across desktop and multi-window targets:
- `val isAlwaysOnTop: Boolean` — returns whether the window stays pinned above normal application windows.
- `fun setAlwaysOnTop(alwaysOnTop: Boolean)` — enables or disables floating z-order (`HWND_TOPMOST` / `HWND_NOTOPMOST` on Windows, `NSFloatingWindowLevel` / `NSNormalWindowLevel` on macOS, `UIWindowLevelAlert` / `UIWindowLevelNormal` on UIKit iPadOS/iOS, `gtk_window` / `Qt::WindowStaysOnTopHint`, and `Window.setAlwaysOnTop` on Swing).
- **Defaults**: Standard [`Window`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/shell/Window.kt) instances default to `isAlwaysOnTop = false` (configurable in `build.gradle.kts` via `window { alwaysOnTop = true }`), whereas [`PanelWindow`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/dock/PanelWindow.kt) overrides **`isAlwaysOnTop = true`**, calls `setAlwaysOnTop(true)` during initialization, and sets `isAlwaysOnTopOfOwner = true`.

### 4.2 Client-Viewport Aspect Ratio Lock (`aspectRatio`, Normal Windowed Mode Only)

[`IWindow`](../common/src/commonMain/kotlin/com/nexustools/genericui/window/IWindow.kt), [`Window`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/shell/Window.kt), and [`WindowAspectRatioHelper`](../common/src/commonMain/kotlin/com/nexustools/genericui/window/WindowAspectRatioHelper.kt) provide client-viewport aspect-ratio locking (`width / height`, excluding native window frame/titlebar/taskbar chrome):
- `fun getAspectRatio(): Double?`
- `fun setAspectRatio(aspectRatio: Double?)` (`null` clears the constraint; positive finite values are validated by `WindowAspectRatioHelper.validateAspectRatio`)
- `fun setAspectRatio(widthRatio: Int, heightRatio: Int)` (convenience overload, e.g. `window.setAspectRatio(16, 9)` or Gradle `window { aspectRatio(16, 9) }`)
- **Strict Windowed-Only Enforcement (`WindowState.NORMAL && !isFullscreen()`)**:
  1. When `window.windowState == WindowState.NORMAL && !window.isFullscreen()`, calls to `setWindowSize` / native drag-resizes clamp the **client viewport dimensions** via `WindowAspectRatioHelper.clampClientSize` / `enforceResizeDelta` and pass `aspectRatio` to `WindowNativeImpl.setAspectRatio(...)` (`WM_SIZING` client-rect adjustment on Windows, `NSWindow.contentAspectRatio` on macOS, X11/GTK/Qt geometry hints, `JComponent` / content size on Swing, `UIWindowScene` `sizeRestrictions` on Stage Manager iPadOS).
  2. When transitioning to **maximized (`WindowState.MAXIMIZED`)** or **fullscreen (`isFullscreen() == true`)**, `Window` leaves fullscreen/maximized dimensions **completely unconstrained** and clears native aspect enforcement (`winImpl.setAspectRatio(null)`) so displays fill edge-to-edge without letterboxing bugs.
  3. Upon **unmaximizing or exiting fullscreen** back into `WindowState.NORMAL`, `Window` restores `lastWindowedNormalSize` and re-clamps it cleanly to `aspectRatio` before notifying `WindowNativeImpl`.

### 4.3 `TitleCapabilities` & Per-Platform Chrome Rules

| Platform | Neither `TitleBar` nor `MenuBar` Set | `TitleBar` Only | `MenuBar` Only | Both `TitleBar` and `MenuBar` Set |
| :--- | :--- | :--- | :--- | :--- |
| **Desktop (`:desktop` Win/GTK/Qt/Swing)** | Native OS window titlebar (`isTitleBarRequired = true`). | Native/custom titlebar with embedded leading/center/trailing components. | Native titlebar + `MenuBar`. | Native/custom titlebar + `MenuBar` (embedded when `embedMenuBar = true`). |
| **macOS (`:desktop` `macosMain`)** | Native `NSWindow` titlebar (`isTitleBarRequired = true`). | `NSWindow` / `NSToolbar` with custom titlebar items. | Global macOS top-of-screen `NSApplication.mainMenu`. | `NSWindow` titlebar + global macOS `NSApplication.mainMenu`. |
| **Android & iPhone iOS (`:mobile`)** | **No navbar added to view hierarchy.** | `MaterialToolbar` / native navbar with title & items. | Navbar with menu button/dropdown on the right. | Single navbar combining title and menu dropdown. |
| **iPadOS (`:mobile` `ipados/`)** | **No navbar added to view hierarchy.** | Inline UIKit navbar/titlebar (`titleBarRendersAsNavbar = true`). | Global iPadOS native menu bar (`UIMenuSystem.mainSystem`). | Inline `TitleBar` navbar + native `UIMenuSystem.mainSystem` menus. |
| **Web (`:web`)** | **No navbar or menubar added to DOM.** | Bootstrap `Navbar` at the top of viewport (`document.title` synced). | Standalone `MenuBar` strip only. | Single Bootstrap `Navbar` with title/icon on the left and `MenuBar` on the right. |

### 4.4 `Window` Resize & Configuration Change Propagation (`onResize` & `onConfigurationChanged`)

[`Window`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/shell/Window.kt) automatically propagates resize and [`WindowConfiguration`](../common/src/commonMain/kotlin/com/nexustools/genericui/window/WindowConfiguration.kt) events through `FragmentContainer`, [`FragmentHostFragment`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/fragment/helper/FragmentHostFragment.kt), and [`AlienFragmentHostFragment`](../extensions/ui/alien/src/commonMain/kotlin/com/nexustools/genericui/ext/alien/AlienFragmentHostFragment.kt) before invoking user `setOnWindowResize` listeners.

---

## 5. Multi-Screen Navigation (`FragmentHostFragment`, `FragmentTransitionSpec`, & Web History)

[`FragmentHostFragment`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/fragment/helper/FragmentHostFragment.kt) and [`FragmentHostSystem`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/fragment/helper/FragmentHostSystem.kt) support:
- **Transitions ([`FragmentTransitionSpec`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/fragment/helper/FragmentTransitionSpec.kt))**: `None`, `Crossfade`, `FadeExpandShrink`, and `SlideHorizontal`.
- **Optional Chrome ([`FragmentNavigationChromeConfig`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/fragment/helper/FragmentNavigationChromeConfig.kt))**: Zero chrome by default; embeds Back/Forward/custom buttons into `TitleBar` when `canEmbedComponentsInTitleBar == true` or falls back to `Navbar` when requested.
- **Web `#hash` & `window.history.state`**: [`WebFragmentHistoryBridge`](../web/src/commonMain/kotlin/com/nexustools/genericui/web/WebFragmentHistoryBridge.kt) syncs routes to `#hash` and persists `FragmentBundle` state across native Browser Back/Forward (`popstate`).

---

## 6. Styling, Global Stylesheets, `TabbedFragment`, & Hybrid Fragment Helpers

- **Component Styling**: Use `component.getBootstrap()` ([`ComponentBootstrap`](../common/src/commonMain/kotlin/com/nexustools/genericui/style/bootstrap/ComponentBootstrap.kt)) and `component.getStyle()` ([`ComponentStyle`](../common/src/commonMain/kotlin/com/nexustools/genericui/style/css/ComponentStyle.kt)), or `style { ... }` / `bootstrap { ... }` / `important { ... }` when `:extensions:ui:dsl` is enabled. On system backends, opt into custom aesthetic styling and 9-backend `backdropFilter` / `setBackdropBlur(...)` frosted-glass blur using `StyledContainer` or `StyledButton` (`AestheticStylePolicy`, explicitly excluding `TextBubble`).
- **Application-Level Stylesheets**: Global stylesheet registration (`addStyleSheet` / `removeStyleSheet`) lives on `IApplication` (only `:extensions:ui:alien`'s [`IAlien`](../extensions/ui/alien/src/commonMain/kotlin/com/nexustools/genericui/ext/alien/IAlien.kt) subclasses maintain per-Alien stylesheet cascades).
- **Multi-Tab & Responsive Helpers**: [`TabbedFragment`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/fragment/helper/TabbedFragment.kt) (`TabbedChromeMode.AUTO` switching between desktop tabs $\ge 600\text{dp}$ and compact `[N]` mobile pill switcher $< 600\text{dp}$) plus all 7 helpers in [**`layouts-and-helpers.md`**](./layouts-and-helpers.md).
