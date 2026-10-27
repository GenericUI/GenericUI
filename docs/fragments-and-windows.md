# GenericUI Fragments, Windows, & Navigation Guide

> **Root Repository**: [**GenericUI (`../README.md`)**](../README.md) | **Master Docs Index**: [**`docs/README.md`**](./README.md)  
> **Related Guides**: [Application, Theming, & Stylesheets (`application.md`)](./application.md) · [Hybrid Layouts & Fragment Helpers (`layouts-and-helpers.md`)](./layouts-and-helpers.md) · [Gradle Plugin (`gradle-plugin.md`)](./gradle-plugin.md) · [SourceSet & Variant Scopes (`sourcesets.md`)](./sourcesets.md) · [Resources (`resources.md`)](./resources.md) · [Components & Docking (`components-and-docking.md`)](./components-and-docking.md)

This guide covers GenericUI's annotation-driven `Fragment` discovery (`@MainAppFragment` and `@AppFragment`), `AppTarget` specificity resolution, `FragmentBundle` command-line arguments on system targets, `IFragment` and `IWindow`, `TitleCapabilities`, `launchFragment(FragmentClass, LaunchArgs)`, 1:1 generated Android `AppCompatActivity` classes, and multi-screen navigation with `FragmentHostFragment` and `FragmentHostSystem`.

---

## 1. Annotation-Driven Fragment Discovery (`@MainAppFragment` & `@AppFragment`)

In GenericUI's single-`commonMain` architecture, consumer projects do not write per-backend `main()` functions or list fragments in `build.gradle.kts`. Instead, `Fragment` subclasses are annotated directly with [`@MainAppFragment`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/fragment/MainAppFragment.kt) and [`@AppFragment`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/fragment/AppFragment.kt):

### 1.1 `@MainAppFragment` (`com.nexustools.genericui.component.fragment.MainAppFragment`)

```kotlin
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class MainAppFragment(
    val targets: Array<AppTarget> = [AppTarget.COMMON],
    val label: String = "",
    val route: String = "",
    val exported: Boolean = true,
    val screenOrientation: String = ""
)
```

- Marks a `Fragment` subclass as the initial root fragment launched when the application starts on the specified [`AppTarget`](../common/src/commonMain/kotlin/com/nexustools/genericui/enums/system/AppTarget.kt) scopes.
- Automatically registers the fragment's constructor factory in [`FragmentRegistry`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/fragment/FragmentRegistry.kt).
- On **Android (`:mobile:android`)**, generates a concrete 1:1 `AppCompatActivity` subclass (`<FragmentName>Activity : GenericUiFragmentActivity()`) and marks the resolved Android main fragment's `<activity>` with the `MAIN` / `LAUNCHER` intent filter in the generated `AndroidManifest.xml`.

### 1.2 `@AppFragment` (`com.nexustools.genericui.component.fragment.AppFragment`)

```kotlin
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class AppFragment(
    val targets: Array<AppTarget> = [AppTarget.COMMON],
    val label: String = "",
    val route: String = "",
    val exported: Boolean = false,
    val screenOrientation: String = ""
)
```

- Marks an additional application `Fragment` for the specified `AppTarget` scopes.
- Registers the fragment in `FragmentRegistry` so it can be instantiated by class reference via `launchFragment(SettingsFragment::class, args)` or route navigation (`navigateToRoute("settings")`).
- On **Android (`:mobile:android`)**, generates a concrete 1:1 `AppCompatActivity` subclass (`<FragmentName>Activity : GenericUiFragmentActivity()`) and adds its `<activity>` declaration to `AndroidManifest.xml`.

### 1.3 `AppTarget` Specificity Resolution & Build-Time Validation

[`AppTarget`](../common/src/commonMain/kotlin/com/nexustools/genericui/enums/system/AppTarget.kt) models both multi-backend common scopes and concrete backend targets, each with a `specificityRank` (`0..5`):

| `AppTarget` Entry | Scope ID | `specificityRank` | Matching `BackendTarget`s |
| :--- | :--- | :--- | :--- |
| `AppTarget.COMMON` | `"common"` | `0` | All 8 backends (`Windows`, `MacOS`, `Gtk`, `Qt`, `Swing`, `Android`, `IOS`, `Web`) |
| `AppTarget.SYSTEM_COMMON` | `"systemCommon"` | `1` | All 7 OS/system backends (`Windows`, `MacOS`, `Gtk`, `Qt`, `Swing`, `Android`, `IOS`) |
| `AppTarget.DESKTOP_COMMON` | `"desktopCommon"` | `2` | All 5 desktop backends (`Windows`, `MacOS`, `Gtk`, `Qt`, `Swing`) |
| `AppTarget.MOBILE_COMMON` | `"mobileCommon"` | `2` | Both mobile backends (`Android`, `IOS`) |
| `AppTarget.NATIVE_DESKTOP_COMMON` | `"nativeDesktopCommon"` | `3` | All 4 native desktop backends (`Windows`, `MacOS`, `Gtk`, `Qt`) |
| `AppTarget.LINUX_COMMON` | `"linuxCommon"` | `4` | Both Linux desktop backends (`Gtk`, `Qt`) |
| `WINDOWS`, `MACOS`, `GTK`, `QT`, `SWING`, `ANDROID`, `IOS`, `WEB` | Concrete backend ID | `5` | That exact single `BackendTarget` |

For every enabled backend target, the Gradle plugin (`genericuiValidateApp`) resolves `@MainAppFragment` along the target's specificity chain (most specific wins):

| Enabled Backend Target | Specificity Resolution Chain (`5 → 4 → 3 → 2 → 1 → 0`) |
| :--- | :--- |
| **`Windows`** | `WINDOWS (5)` $\rightarrow$ `NATIVE_DESKTOP_COMMON (3)` $\rightarrow$ `DESKTOP_COMMON (2)` $\rightarrow$ `SYSTEM_COMMON (1)` $\rightarrow$ `COMMON (0)` |
| **`MacOS`** | `MACOS (5)` $\rightarrow$ `NATIVE_DESKTOP_COMMON (3)` $\rightarrow$ `DESKTOP_COMMON (2)` $\rightarrow$ `SYSTEM_COMMON (1)` $\rightarrow$ `COMMON (0)` |
| **`Gtk` / `Qt`** | `GTK / QT (5)` $\rightarrow$ `LINUX_COMMON (4)` $\rightarrow$ `NATIVE_DESKTOP_COMMON (3)` $\rightarrow$ `DESKTOP_COMMON (2)` $\rightarrow$ `SYSTEM_COMMON (1)` $\rightarrow$ `COMMON (0)` |
| **`Swing`** | `SWING (5)` $\rightarrow$ `DESKTOP_COMMON (2)` $\rightarrow$ `SYSTEM_COMMON (1)` $\rightarrow$ `COMMON (0)` |
| **`Android`** | `ANDROID (5)` $\rightarrow$ `MOBILE_COMMON (2)` $\rightarrow$ `SYSTEM_COMMON (1)` $\rightarrow$ `COMMON (0)` |
| **`IOS`** | `IOS (5)` $\rightarrow$ `MOBILE_COMMON (2)` $\rightarrow$ `SYSTEM_COMMON (1)` $\rightarrow$ `COMMON (0)` |
| **`Web`** | `WEB (5)` $\rightarrow$ `COMMON (0)` |

- **Target-Specific Overrides**: Declaring `@MainAppFragment(targets = [AppTarget.COMMON])` on `DesktopAndWebHostFragment` and `@MainAppFragment(targets = [AppTarget.MOBILE_COMMON])` on `MobileHostFragment` automatically selects `MobileHostFragment` on Android and iOS (`rank 2 > rank 0`) and `DesktopAndWebHostFragment` on all other targets.
- **Ambiguity & Missing Target Diagnostics**: The Gradle build fails immediately during `genericuiValidateApp` if two or more `@MainAppFragment` classes tie at the highest specificity rank for an enabled target, or if zero `@MainAppFragment` classes match an enabled target.

---

## 2. `FragmentBundle` & Command-Line Arguments on System Targets

[`FragmentBundle`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/fragment/FragmentBundle.kt) is a key-value state and argument bundle passed into `onCreate(savedState: FragmentBundle?)`, `onSaveInstanceState(outState: FragmentBundle)`, and `onRestoreInstanceState(savedState: FragmentBundle)`.

On all **system targets** (`Windows`, `MacOS`, `Gtk`, `Qt`, `Swing`, `Android`, and `IOS`), the generated launcher automatically populates the initial `FragmentBundle` passed to the resolved `@MainAppFragment`'s `onCreate(savedState)` with the process command-line arguments under `FragmentBundle.KEY_COMMAND_LINE_ARGS` (`"genericui:command_line_args"`):

| Method / Property | Description |
| :--- | :--- |
| `savedState.hasCommandLineArgs(): Boolean` | Returns `true` if command-line arguments are present in the bundle (`false` on `:web` or when none were supplied). |
| `savedState.getCommandLineArgs(): List<String>` | Returns the command-line arguments as an ordered `List<String>`, or `emptyList()`. |
| `savedState.getCommandLineArgsArray(): Array<String>` | Returns the command-line arguments as a typed `Array<String>`. |
| `savedState.putCommandLineArgs(args)` | Stores `Array<String>` or `List<String>` under `KEY_COMMAND_LINE_ARGS`. |
| `FragmentBundle.fromCommandLineArgs(args)` | Factory method creating a `FragmentBundle` initialized with `args`. |
| `FragmentBundle.of("key" to value, ...)` | Factory method creating a `FragmentBundle` from key-value pairs. |

```kotlin
import com.nexustools.genericui.component.fragment.AppFragment
import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.fragment.FragmentBundle
import com.nexustools.genericui.component.fragment.MainAppFragment
import com.nexustools.genericui.enums.system.AppTarget

@MainAppFragment(targets = [AppTarget.COMMON], label = "Studio Workspace")
class WorkspaceMainFragment : Fragment() {
    override fun onCreate(savedState: FragmentBundle?) {
        super.onCreate(savedState)
        if (savedState?.hasCommandLineArgs() == true) {
            val cliArgs: List<String> = savedState.getCommandLineArgs()
            // Open initial project path or flags passed on the command line
        }
    }
}
```

---

## 3. `IFragment`, `launchFragment(FragmentClass, LaunchArgs)`, & 1:1 Android `AppCompatActivity` Generation

[`IFragment`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/fragment/IFragment.kt) is implemented by [`Fragment`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/fragment/Fragment.kt) and all fragment subclasses (`ScrollFragment`, `PageFragment`, `FragmentHostFragment`, `DockWorkspaceFragment`, `AdaptiveSplitTabFragment`, `AlienFragment`, etc.).

### 3.1 Launching Fragments (`launchFragment`)

Both `IFragment`, `IWindow`, and `IApplication` provide `launchFragment` overloads backed by [`FragmentLaunchCoordinator`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/fragment/FragmentLaunchCoordinator.kt) and [`FragmentRegistry`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/fragment/FragmentRegistry.kt):

```kotlin
// 1. Launch by KClass with FragmentBundle launchArgs
launchFragment(
    fragmentClass = ProjectSettingsFragment::class,
    launchArgs = FragmentBundle.of("projectId" to "apollo", "readOnly" to false),
    addToBackStack = true,
    launchMode = FragmentLaunchMode.SAME_WINDOW
)

// 2. Convenience vararg Pair overload
launchFragment(
    ProjectSettingsFragment::class,
    "projectId" to "apollo",
    "readOnly" to false,
    launchMode = FragmentLaunchMode.NEW_DIALOG_WINDOW
)
```

### 3.2 `FragmentLaunchMode` (`com.nexustools.genericui.enums.fragment.FragmentLaunchMode`)

| `FragmentLaunchMode` | Behavior on Desktop & Web | Behavior on Android (`:mobile:android`) |
| :--- | :--- | :--- |
| **`SAME_WINDOW`** | Mounts the fragment inside the current `IWindow`'s `FragmentContainer` (pushing onto the back stack when `addToBackStack == true`). | Looks up the 1:1 generated `<FragmentName>Activity::class.java` in `AndroidActivityRegistry`, serializes `LaunchArgs` into `Intent` extras, and invokes `startActivity(intent)`. |
| **`CLEAR_TASK`** | Clears the window's fragment back stack and mounts the fragment as the new root. | Starts `<FragmentName>Activity` with `FLAG_ACTIVITY_CLEAR_TASK or FLAG_ACTIVITY_NEW_TASK`. |
| **`NEW_WINDOW`** | Creates and shows a new top-level `Window` hosting the fragment. | Starts `<FragmentName>Activity` in a new task. |
| **`NEW_PANEL_WINDOW`** | Opens a floating utility `PanelWindow` owned by the current window. | Starts a dialog-themed `AppCompatActivity` hosting the fragment. |
| **`NEW_DIALOG_WINDOW`** | Opens a modal `DialogWindow` hosting the fragment. | Starts a dialog-themed `AppCompatActivity` hosting the fragment. |

---

## 4. `IWindow` & `TitleCapabilities`

[`IWindow`](../common/src/commonMain/kotlin/com/nexustools/genericui/window/IWindow.kt) is the unified window interface implemented across all four GenericUI window types:
1. [`Window`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/shell/Window.kt) — Top-level native OS window (or browser viewport on `:web` / `AppCompatActivity` window on `:mobile:android` / `UIWindow` on `:mobile:ios`).
2. [`PanelWindow`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/dock/PanelWindow.kt) — Floating tool-modal dock utility window.
3. [`DialogWindow`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/dialog/DialogWindow.kt) — Modal or modeless dialog window.
4. [`MdiWindow`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/shell/MdiWindow.kt) — In-viewport Multi-Document Interface sub-window inside `MdiContainer`.

### 4.1 `TitleCapabilities` (`com.nexustools.genericui.window.TitleCapabilities`)

Every `IWindow` (and `IFragment.titleCapabilities`) exposes [`TitleCapabilities`](../common/src/commonMain/kotlin/com/nexustools/genericui/window/TitleCapabilities.kt) so components and fragments can inspect how titlebars, custom controls, and menu bars are rendered on the active platform:

| Property / Method | Meaning |
| :--- | :--- |
| `isSupported: Boolean` | `true` if the window supports displaying a titlebar or navbar. |
| `isEmulated: Boolean` | `true` when the titlebar/navbar is custom-rendered (`MdiWindow`, `DockPanel`, `DialogWindow`, `:mobile:android` Material navbar, `:mobile:ios` navigation bar); `false` for native OS window manager chrome (`Win32` DWM, macOS `NSWindow`, GTK `GtkHeaderBar`). |
| `canEmbedComponentsInTitleBar: Boolean` | Computed property (`isSupported && supportsCustomComponents && (supportsLeadingComponents \|\| supportsCenterComponents \|\| supportsTrailingComponents)`). When `true`, components added to `TitleBar` are marked `mountedInTitleBar = true` and rendered **only once** inside the titlebar (never duplicated in the body). |
| `isTitleBarRequired: Boolean` | `true` on native desktop windows (which always have a window frame); `false` on **Android**, **iOS**, and **Web** (where chrome is added only if a `TitleBar` or `MenuBar` is set). |
| `titleBarRendersAsNavbar: Boolean` | `true` on Android, iOS, and Web where setting a `TitleBar` renders a top navigation bar. |
| `menuBarRendersAsNavbar: Boolean` | `true` on Android and iOS where setting a `MenuBar` alone creates a navbar with a menu dropdown; `false` on Web where a lone `MenuBar` renders as just a menubar. |
| `menuBarMergesIntoTitleNavbar: Boolean` | `true` on Android, iOS, and Web when setting both a `TitleBar` and a `MenuBar` combines them into a single navbar (title/icon on the left, menu on the right). |
| `requiresChrome(hasTitleBar, hasMenuBar)` | Returns `isTitleBarRequired \|\| hasTitleBar \|\| hasMenuBar`. When `false`, no navbar or header view is added to the view hierarchy at all. |

### 4.2 Per-Platform `TitleBar` & `MenuBar` Chrome Rules

| Platform | Neither `TitleBar` nor `MenuBar` Set | `TitleBar` Only | `MenuBar` Only | Both `TitleBar` and `MenuBar` Set |
| :--- | :--- | :--- | :--- | :--- |
| **Desktop (`:desktop:*`)** | Native OS window titlebar (`isTitleBarRequired = true`). | Native/custom window titlebar with embedded leading/center/trailing components. | Native window titlebar + `MenuBar` (or global macOS menu bar on `:desktop:macos`). | Native/custom window titlebar + `MenuBar` (embedded when `embedMenuBar = true`). |
| **Android (`:mobile:android`)** | **No navbar added to view hierarchy.** | Material `Navbar` (`MaterialToolbar`) with title & custom components. | Material `Navbar` with menu dropdown on the right. | Single Material `Navbar` with title/components on the left and menu dropdown on the right. |
| **iOS (`:mobile:ios`)** | **No navbar added to view hierarchy.** | Native iOS navigation-bar layout with title & items. | Native iOS navigation bar with menu button on the right. | Native iOS navigation bar with title on the left/center and menu on the right. |
| **Web (`:web`)** | **No navbar or menubar added to DOM.** | Bootstrap `Navbar` at the top of the viewport. | Standalone `MenuBar` strip only. | Single Bootstrap `Navbar` with title/icon on the left and `MenuBar` on the right. |

---

## 5. Multi-Screen Navigation with `FragmentHostFragment` & `FragmentHostSystem`

[`FragmentHostFragment`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/fragment/helper/FragmentHostFragment.kt) and [`FragmentHostSystem`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/fragment/helper/FragmentHostSystem.kt) (`com.nexustools.genericui.component.fragment.helper`) provide a complete multi-screen router, back/forward history stack, animated transition engine, `.loader` spinner overlay, and Web `#hash` + browser `window.history.state` bridge.

### 5.1 Customizable Fragment Transitions (`FragmentTransitionSpec`)

[`FragmentTransitionSpec`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/fragment/helper/FragmentTransitionSpec.kt) configures how outgoing and incoming child fragments transition inside `FragmentHostFragment`:

- **`FragmentTransitionSpec.None`**: Immediate switch (`durationMs = 0`).
- **`FragmentTransitionSpec.Crossfade(durationMs = 250, activationDelayMs = 30)`**: Co-mounts incoming and outgoing fragments (`keepOutgoingMountedDuringExit = true`) and cross-fades `opacity` between `0f` and `1f`.
- **`FragmentTransitionSpec.FadeExpandShrink(durationMs = 500, activationDelayMs = 50)`**:
  - **Incoming (`fadein/expand`)**: Mounts with `.created`, `opacity = 0f`, `zIndex = 0`, `pointerEventsEnabled = false`, and calls `fragment.onBeginEnterTransition(durationMs)` (which shrinks `.menu` to `translate(-50%, -50%) scale(0, 0)` on `CenteredMenuPageFragment`). After `activationDelayMs` (`50ms`), removes `.created`, sets `zIndex = 5`, `pointerEventsEnabled = true`, transitions `opacity` to `1f`, and calls `fragment.onActivateEnterTransition(durationMs)` (expanding `.menu` to `translate(-50%, -50%) scale(s, s)`).
  - **Outgoing (`fadeout/shrink`)**: Adds `.destroyed`, sets `pointerEventsEnabled = false`, `zIndex = 0`, transitions `opacity` to `0f`, calls `fragment.onBeginExitTransition(durationMs)` (shrinking `.menu` to `scale(0, 0)`), and keeps the outgoing fragment mounted in `stageContainer` for the full `500ms` before `performDestroy()` and child removal.
- **`FragmentTransitionSpec.SlideHorizontal(durationMs = 300, activationDelayMs = 30)`**: Horizontal `translateX` slide with opacity cross-fade.

### 5.2 Optional Navigation Chrome (`FragmentNavigationChromeConfig`)

[`FragmentNavigationChromeConfig`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/fragment/helper/FragmentNavigationChromeConfig.kt) controls whether Back, Forward, and custom toolbar components are displayed:
- **Zero Chrome When Disabled**: When `showBackButton = false`, `showForwardButton = false`, and `leadingComponents`/`centerComponents`/`trailingComponents` are empty (the default), `FragmentHostFragment` adds **neither** titlebar buttons nor a fallback `Navbar`.
- **Single-Placement Desktop TitleBar Embedding**: When navigation items are enabled and `titleCapabilities.canEmbedComponentsInTitleBar && preferTitleBarNavigation` is `true`, `FragmentHostFragment` embeds the Back/Forward buttons and custom components directly into the hosting `IWindow`'s `TitleBar` (`mountedInTitleBar = true`).
- **Automatic `Navbar` Fallback**: When the platform does not embed custom components in the titlebar (or `preferTitleBarNavigation = false` and `showNavbarFallback = true`), `FragmentHostFragment` mounts a `Navbar` directly below the titlebar containing the Back/Forward buttons, active fragment title, and custom components.

### 5.3 Web `#hash` Routing & Native Browser `window.history.state`

On `:web`, `FragmentHostSystem` automatically binds to [`WebFragmentHistoryBridge`](../web/src/commonMain/kotlin/com/nexustools/genericui/web/WebFragmentHistoryBridge.kt):
1. **URL `#hash` Tracking**: Navigating to a route or pushing a fragment updates `window.location.hash` (e.g., `#main-menu`, `#character-builder`, `#catalog`).
2. **`FragmentBundle` Serialization in `window.history.state`**: Before leaving a fragment, `FragmentHostSystem` invokes `onSaveInstanceState(outState: FragmentBundle)` and stores the serialized `FragmentBundle` alongside `route` and `historyIndex` inside the browser's native `window.history.state` (`pushState` / `replaceState`).
3. **Native Browser Back & Forward Buttons**: Clicking the browser's **Back** or **Forward** button triggers `popstate`, pops or advances `FragmentHostSystem` to the matching `Fragment`, and restores its saved `FragmentBundle` via `onCreate(savedState)` / `onRestoreInstanceState(savedState)`.

### 5.4 Example: Multi-Screen Application with `FragmentHostFragment`

```kotlin
import com.nexustools.genericui.component.action.Button
import com.nexustools.genericui.component.fragment.AppFragment
import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.fragment.FragmentBundle
import com.nexustools.genericui.component.fragment.MainAppFragment
import com.nexustools.genericui.component.fragment.helper.FragmentHostFragment
import com.nexustools.genericui.component.fragment.helper.FragmentNavigationChromeConfig
import com.nexustools.genericui.component.fragment.helper.FragmentTransitionSpec

@AppFragment(label = "Home", route = "home")
class HomeScreenFragment(private val onOpenCatalog: () -> Unit = {}) : Fragment() {
    init {
        pageTitle = "Home"
        addChild(Button("Browse Catalog").apply { setOnClick { onOpenCatalog() } })
    }
}

@AppFragment(label = "Catalog", route = "catalog")
class CatalogScreenFragment : Fragment() {
    private var selectedCategory: String = "featured"

    init {
        pageTitle = "Catalog"
    }

    override fun onCreate(savedState: FragmentBundle?) {
        super.onCreate(savedState)
        selectedCategory = savedState?.getString("category") ?: "featured"
    }

    override fun onSaveInstanceState(outState: FragmentBundle) {
        super.onSaveInstanceState(outState)
        outState.putString("category", selectedCategory)
    }
}

@MainAppFragment(label = "Media Portal")
class PortalHostFragment : FragmentHostFragment(
    appTitle = "Media Portal",
    transitionSpec = FragmentTransitionSpec.FadeExpandShrink(durationMs = 500, activationDelayMs = 50),
    navigationChrome = FragmentNavigationChromeConfig(
        showBackButton = true,
        showForwardButton = true
    )
) {
    override fun onCreate(savedState: FragmentBundle?) {
        super.onCreate(savedState)
        registerRoute("home", HomeScreenFragment::class, title = "Home") {
            HomeScreenFragment(onOpenCatalog = {
                navigateToRoute("catalog", FragmentBundle.of("category" to "movies"))
            })
        }
        registerRoute("catalog", CatalogScreenFragment::class, title = "Catalog") {
            CatalogScreenFragment()
        }

        // Automatically resolves initial #hash on Web, or opens "home" by default
        resolveInitialDeepLink(defaultRoute = "home", defaultArgs = savedState)
    }
}
```

---

## 6. Styling (`getStyle()` & `getBootstrap()`), Global Stylesheets (`IApplication`), & Hybrid Fragment Helpers

### 6.1 Component Styling Inside Fragments (`getStyle()` & `getBootstrap()`)
- **`component.getBootstrap(): ComponentBootstrap`**: Configures strongly-typed Bootstrap 5 design-system utilities (`type = BootstrapType.NONE` default, `size`, `outline`, `width`, `height`, directional `SpacingScale` margins/paddings/gaps including `SpacingScale.AUTO`, `borderWidth`, `borderRadius`, `shadow`, `opacity`, `fontSize`, `lineHeight`, `flexGrow`, `flexShrink`, `floatSide`, `userSelect`, `pointerEvents`, `objectFit`, `aspectRatio`, `visibility`). All enum-managed classes remain 100% invisible to `getClasses()` / `hasClass()`.
- **`component.getStyle(): ComponentStyle`**: Configures strongly-typed W3C inline CSS properties (`backgroundColor`, `backgroundImage`, `color`, `accentColor`, `borderRadius`, `boxShadow`, `cursor` including [`CssCustomCursor`](../common/src/commonMain/kotlin/com/nexustools/genericui/style/css/CssCustomCursor.kt), etc.) with per-property `important = true` (`!important`) support and `null` to clear. Inline `ComponentStyle` contains zero pseudo-states (`:hover`, `:focus`, `:active` belong in `.scss` stylesheets).
- **`:extensions:dsl` Builders**: When `:extensions:dsl` is enabled, `style { ... }`, `bootstrap { ... }`, and `important { ... }` are available on all components inside DSL blocks.

### 6.2 Global Stylesheets & Theme Mode Live on `IApplication` (Not `IFragment`)
- Global stylesheet attachment (`addStyleSheet` / `removeStyleSheet` accepting `Resource.style.<name>`, path, or `PrecompiledStyleSheet`) and application theme mode (`setTheme(ThemeMode.AUTO | DARK | LIGHT)`, `getTheme()`, `getResolvedTheme()`) live on **`IApplication`** (`hostingWindow?.application` / `app`), **never** on `IFragment`.
- Only `:extensions:alien`'s `AlienFragment` and `AlienContainer` (which implement [`IAlien`](../extensions/alien/common/src/commonMain/kotlin/com/nexustools/genericui/ext/alien/IAlien.kt)) maintain their own per-Alien stylesheet cascade (`IAlien.BOOTSTRAP_STYLE_SHEET_ID` at index `0` and `insertApplicationStyleSheets = 1` by default).

### 6.3 Hybrid Responsive Fragment Helpers
See the [**Hybrid Layouts & Fragment Helpers Guide (`layouts-and-helpers.md`)**](./layouts-and-helpers.md) for full documentation of all 7 responsive fragment helpers:
- [`HybridColumnFragment`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/fragment/helper/HybridColumnFragment.kt) (`HybridColumnLayout`, `HybridFoldMode`, `HybridTabPlacement`, `DynamicFullscreenController`, `HybridFullscreenPolicy`)
- [`DockWorkspaceFragment`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/fragment/helper/DockWorkspaceFragment.kt) (`DockWorkspace` with automatic compact mobile edge swipe-out drawers via `DockMobileDrawerPolicy`)
- [`AdaptiveNavigationScaffoldFragment`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/fragment/helper/AdaptiveNavigationScaffoldFragment.kt) (`Sidebar` $\leftrightarrow$ `NavigationRail` $\leftrightarrow$ `BottomNavigationBar`)
- [`ResponsiveMasterDetailFragment`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/fragment/helper/ResponsiveMasterDetailFragment.kt) (2/3-pane split $\leftrightarrow$ compact drill-down stack)
- [`HybridSplitNavigationFragment`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/fragment/helper/HybridSplitNavigationFragment.kt) (top `TabLayout` $\leftrightarrow$ bottom `BottomNavigationBar`)
- [`CollapsibleBottomDockFragment`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/fragment/helper/CollapsibleBottomDockFragment.kt) (main workspace + collapsible mini-player/bottom dock)
- [`ResponsiveBentoGridFragment`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/fragment/helper/ResponsiveBentoGridFragment.kt) (`BentoGridItemSpec` 12-column bento dashboard grid)
