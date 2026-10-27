# GenericUI Application, `IApplication`, Theming (`ThemeMode.AUTO`), Stylesheets, & `BackendTarget` Guide

> **Root Repository**: [**GenericUI (`../README.md`)**](../README.md) | **Master Docs Index**: [**`docs/README.md`**](./README.md)  
> **Related Guides**: [Fragments & Windows (`fragments-and-windows.md`)](./fragments-and-windows.md) · [Hybrid Layouts & Helpers (`layouts-and-helpers.md`)](./layouts-and-helpers.md) · [Resources (`resources.md`)](./resources.md) · [Gradle Plugin (`gradle-plugin.md`)](./gradle-plugin.md) · [Platform Backends (`backends.md`)](./backends.md) · [SourceSets (`sourcesets.md`)](./sourcesets.md)

This guide covers [`IApplication`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/shell/IApplication.kt), [`Application`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/shell/Application.kt), [`FragmentApplication`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/shell/FragmentApplication.kt), optional custom `@AppApplication` classes, [`ThemeMode`](../common/src/commonMain/kotlin/com/nexustools/genericui/enums/ui/ThemeMode.kt) (`AUTO`, `DARK`, `LIGHT` with 8-target OS theme detection), global stylesheet management (`IApplication.addStyleSheet` / `removeStyleSheet`), [`BackendTarget`](../common/src/commonMain/kotlin/com/nexustools/genericui/enums/system/BackendTarget.kt) runtime target/group queries, and platform capability checks.

---

## 1. Automatic Application Bootstrapping vs. Custom `@AppApplication`

### 1.1 Default Zero-Boilerplate Bootstrapping (`@MainAppFragment` Only)

Most GenericUI applications need **only** a `@MainAppFragment`-annotated `Fragment` class in `src/commonMain/kotlin` and `genericui { app { ... } }` in `build.gradle.kts`. For every enabled backend target, the GenericUI Gradle plugin generates a `GenericUiAppDescriptor` and target launcher (`WindowsLauncher`, `MacosLauncher`, `GtkLauncher`, `QtLauncher`, `SwingLauncher`, `AndroidLauncher`, `IosLauncher`, `WebLauncher`) that:
1. Initializes the platform's `NativeFactory` (`GenericUI.initialize(...)`).
2. Registers all enabled extension providers automatically.
3. Registers the resolved `@MainAppFragment` and all matching `@AppFragment` classes in `FragmentRegistry`.
4. Constructs `FragmentApplication` with the resolved variant window geometry, title, icon, theme mode (defaulting to **`ThemeMode.AUTO`**), and `themeColor`.
5. Automatically calls `app.addStyleSheet(...)` in the generated startup code whenever `styles { globalStyleSheet = "..." }` (or `theme { styleSheet = "..." }`) is configured in `build.gradle.kts`.
6. Instantiates the resolved `@MainAppFragment` and passes process command-line arguments in the initial `FragmentBundle` on system targets (`savedState?.getCommandLineArgs()`).

### 1.2 Optional Custom `@AppApplication` (`com.nexustools.genericui.component.fragment.AppApplication`)

When an application wants custom startup hooks, global menu bars, custom stylesheet registration, or custom `FragmentApplication` / `Application` state, annotate an `IApplication` subclass with [`@AppApplication`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/fragment/AppApplication.kt):

```kotlin
import com.example.studio.res.Resource
import com.nexustools.genericui.component.fragment.AppApplication
import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.fragment.MainAppFragment
import com.nexustools.genericui.component.shell.FragmentApplication
import com.nexustools.genericui.enums.system.AppTarget
import com.nexustools.genericui.enums.ui.ThemeMode

@MainAppFragment
class StudioMainFragment : Fragment()

@AppApplication(targets = [AppTarget.COMMON])
class StudioApplication : FragmentApplication(
    initialFragment = StudioMainFragment(),
    title = "GenericUI Studio",
    initialThemeColor = "#2563EB"
) {
    init {
        // Default is ThemeMode.AUTO; can be set to AUTO, DARK, or LIGHT:
        setTheme(ThemeMode.AUTO)
        // Attach a precompiled .scss stylesheet (Resource.style.<name>):
        addStyleSheet(Resource.style.studio_theme)
    }
}
```

Just like `@MainAppFragment`, `@AppApplication(targets = [...])` supports `AppTarget` specificity resolution (`COMMON`, `SYSTEM_COMMON`, `DESKTOP_COMMON`, `NATIVE_DESKTOP_COMMON`, `LINUX_COMMON`, `MOBILE_COMMON`, and all 8 concrete targets).

---

## 2. `IApplication` Theming (`ThemeMode.AUTO`, `DARK`, `LIGHT` — Default `AUTO`)

GenericUI's [`ThemeMode`](../common/src/commonMain/kotlin/com/nexustools/genericui/enums/ui/ThemeMode.kt) enum defines three modes and defaults to **`ThemeMode.AUTO`** across all applications:

| `ThemeMode` Entry | `bsThemeValue` | Behavior |
| :--- | :--- | :--- |
| **`ThemeMode.AUTO`** *(Default)* | `"auto"` | Automatically detects and tracks the host operating system or browser's active dark/light color scheme in real time via [`SystemThemeDetector`](../common/src/commonMain/kotlin/com/nexustools/genericui/style/theme/SystemThemeDetector.kt). (`ThemeMode.SYSTEM` is preserved as an alias for `ThemeMode.AUTO`.) |
| **`ThemeMode.DARK`** | `"dark"` | Forces dark mode across native OS controls (e.g. Win32 `UxTheme` `DarkMode_Explorer`), `:web` Bootstrap (`data-bs-theme="dark"`), and `:extensions:alien`. |
| **`ThemeMode.LIGHT`** | `"light"` | Forces light mode across native OS controls (e.g. Win32 `UxTheme` `Explorer`), `:web` Bootstrap (`data-bs-theme="light"`), and `:extensions:alien`. |

### 2.1 Theme API on `IApplication`

| Method | Return Type | Description |
| :--- | :--- | :--- |
| `app.setTheme(theme: ThemeMode)`<br/>`app.setThemeMode(mode: ThemeMode)` | `Unit` | Sets the application theme mode (`ThemeMode.AUTO`, `ThemeMode.DARK`, or `ThemeMode.LIGHT`) and notifies all registered theme listeners and native windows. |
| `app.getTheme(): ThemeMode`<br/>`app.getThemeMode(): ThemeMode` | `ThemeMode` | Returns the configured `ThemeMode` (`AUTO`, `DARK`, or `LIGHT`). |
| `app.getResolvedTheme(): ThemeMode` | `ThemeMode` | Resolves `ThemeMode.AUTO` to the currently active concrete system theme (`ThemeMode.DARK` or `ThemeMode.LIGHT`). |
| `app.addThemeChangeListener(listener)`<br/>`app.removeThemeChangeListener(listener)` | `Unit` | Registers or unregisters a callback `(ThemeMode) -> Unit` invoked whenever the theme or underlying OS dark/light mode changes. |
| `app.setThemeColor(colorHex: String?)`<br/>`app.getThemeColor(): String?` | `Unit` / `String?` | Configures or queries the application's primary accent chrome color. |

### 2.2 8-Target OS Dark/Light Theme Detection & `:web` `data-bs-theme` Switching
- **Windows (`:desktop:windows`)**: Reads `HKCU\Software\Microsoft\Windows\CurrentVersion\Themes\Personalize\AppsUseLightTheme`, listens to `WM_SETTINGCHANGE` (`"ImmersiveColorSet"`), toggles `DWMWA_USE_IMMERSIVE_DARK_MODE`, and switches Win32 `UxTheme` control themes (`"DarkMode_Explorer::BUTTON"` vs. `"Explorer::BUTTON"`).
- **macOS (`:desktop:macos`)**: Inspects `NSApp.effectiveAppearance` (`NSAppearanceNameDarkAqua` vs. `NSAppearanceNameAqua`) and observes `AppleInterfaceThemeChangedNotification`.
- **Linux GTK (`:desktop:gtk`) & Qt (`:desktop:qt`)**: Tracks `GtkSettings` (`gtk-application-prefer-dark-theme` / XDG Desktop Portal `color-scheme`) and `QStyleHints::colorSchemeChanged`.
- **JVM Swing (`:desktop:swing`)**: Detects host OS dark/light theme and updates Swing Look-and-Feel palette defaults.
- **Android (`:mobile:android`) & iOS (`:mobile:ios`)**: Tracks `Configuration.UI_MODE_NIGHT_MASK` (`AppCompatDelegate.setDefaultNightMode`) on Android and `UITraitCollection.userInterfaceStyle` on iOS.
- **Web (`:web`)**: Updates `document.documentElement.setAttribute("data-bs-theme", "light" | "dark")` for instant Bootstrap 5.3 dark/light switching and subscribes to `window.matchMedia("(prefers-color-scheme: dark)")` when `ThemeMode.AUTO` is active.

---

## 3. Global Stylesheet Management (`IApplication.addStyleSheet` & `removeStyleSheet`)

> [!IMPORTANT]
> **Application-Level Responsibility**: Global stylesheet registration (`addStyleSheet` / `removeStyleSheet`) lives on **`IApplication`** (`Application` and `FragmentApplication`) — **never** on `IFragment` (except for `:extensions:alien`'s `AlienFragment` / `AlienContainer` implementing [`IAlien`](../extensions/alien/common/src/commonMain/kotlin/com/nexustools/genericui/ext/alien/IAlien.kt)).

### 3.1 `IApplication` Stylesheet Methods

| Method | Description |
| :--- | :--- |
| `app.addStyleSheet(styleSheetResourceId: ResourceID)` | Registers and activates a global `.scss`/`.css` stylesheet by its compiled `Resource.style.<name>` (`R.style.<name>`) `ResourceID`. |
| `app.addStyleSheet(styleSheetPath: String)` | Registers and activates a global stylesheet by its resource path (e.g. `"styles/main.scss"` or `"styles/main.css"`). |
| `app.addStyleSheet(styleSheet: PrecompiledStyleSheet)` | Registers and activates a build-time precompiled [`PrecompiledStyleSheet`](../common/src/commonMain/kotlin/com/nexustools/genericui/style/css/PrecompiledStyleSheet.kt) object directly. |
| `app.removeStyleSheet(styleSheetResourceId: ResourceID): Boolean`<br/>`app.removeStyleSheet(styleSheetPath: String): Boolean`<br/>`app.removeStyleSheet(styleSheet: PrecompiledStyleSheet): Boolean` | Removes a previously added global stylesheet and triggers a style recomputation across open windows. |
| `app.hasStyleSheet(styleSheetResourceId: ResourceID): Boolean`<br/>`app.hasStyleSheet(styleSheetPath: String): Boolean` | Returns `true` if the specified stylesheet is currently active on the application. |
| `app.getStyleSheets(): List<PrecompiledStyleSheet>`<br/>`app.getStyleSheetPaths(): List<String>` | Returns the ordered list of active `PrecompiledStyleSheet` instances or stylesheet paths (`$ApplicationStyleSheets`). |

### 3.2 How Stylesheets Execute Across Targets
1. **Build-Time Precompilation on All 7 Non-Web Targets**: `.scss` files in `src/commonMain/resources/**` are compiled at build time into `Resource.style.<name>` (`R.style.<name>`) backed by typed `PrecompiledStyleSheet` (`CompiledStyleRule`) Kotlin objects with internal `@color/...`, `@drawable/...`, `@dimen/...`, `@font/...`, and `@string/...` references resolved to typed values and `ResourceID`s. Zero runtime SASS/CSS text parsing occurs.
2. **Native `.css` `<link>` Injection on `:web`**: On `:web`, `.scss` files compile to `.css` bundles in the web distribution, and `app.addStyleSheet(...)` / `app.removeStyleSheet(...)` adds or removes `<link rel="stylesheet" href="...">` elements in `document.head`.
3. **Automatic Injection in Generated `main()`**: When `styles { globalStyleSheet = "styles/main.scss" }` (or `theme { styleSheet = "styles/main.scss" }`) is configured in `build.gradle.kts`, `generateGenericUiLaunchers` and `generateGenericUiAndroidActivities` automatically emit `app.addStyleSheet("styles/main.scss")` in the generated launcher startup code.

---

## 4. `BackendTarget` (`com.nexustools.genericui.enums.system.BackendTarget`)

[`BackendTarget`](../common/src/commonMain/kotlin/com/nexustools/genericui/enums/system/BackendTarget.kt) identifies the active runtime platform backend and provides multi-backend group properties so common code never needs string parsing or brittle OS checks:

### 4.1 `BackendTarget` Entries

| Enum Entry | `id` | `displayName` | `osFamilyName` |
| :--- | :--- | :--- | :--- |
| **`BackendTarget.Windows`** | `"windows"` | `"Windows (Win32)"` | `"Windows"` |
| **`BackendTarget.MacOS`** | `"macos"` | `"macOS (AppKit)"` | `"macOS"` |
| **`BackendTarget.Gtk`** | `"gtk"` | `"Linux (GTK4 / Libadwaita)"` | `"Linux"` |
| **`BackendTarget.Qt`** | `"qt"` | `"Linux / Desktop (Qt6)"` | `"Linux"` |
| **`BackendTarget.Swing`** | `"swing"` | `"Java Swing (AWT / Java2D)"` | `"JVM Desktop"` |
| **`BackendTarget.Android`** | `"android"` | `"Android"` | `"Android"` |
| **`BackendTarget.IOS`** | `"ios"` | `"iOS (UIKit)"` | `"iOS"` |
| **`BackendTarget.Web`** | `"web"` | `"Web (HTML5 / JS / Wasm)"` | `"Web Browser"` |

### 4.2 Multi-Backend Target Group Matrix

| Group Property (on `BackendTarget` & `IApplication`) | `Windows` | `MacOS` | `Gtk` | `Qt` | `Swing` | `Android` | `IOS` | `Web` |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **`isSystem`** (`this != Web`) | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | — |
| **`isDesktop`** | ✅ | ✅ | ✅ | ✅ | ✅ | — | — | — |
| **`isNativeDesktop`** | ✅ | ✅ | ✅ | ✅ | — | — | — | — |
| **`isLinux`** | — | — | ✅ | ✅ | — | — | — | — |
| **`isMobile`** / **`isTouchPrimary`** | — | — | — | — | — | ✅ | ✅ | — |
| **`isApple`** | — | ✅ | — | — | — | — | ✅ | — |
| **`isUnix`** | — | ✅ | ✅ | ✅ | — | ✅ | ✅ | — |
| **`isPosix`** / **`isNative`** | ✅ | ✅ | ✅ | ✅ | — | — | ✅ | — |
| **`isJvm`** | — | — | — | — | ✅ | ✅ | — | — |
| **`isBrowser`** | — | — | — | — | — | — | — | ✅ |

---

## 5. `IApplication` Runtime Capabilities & Usage Example

### 5.1 Core Properties & Capability Queries

| Property | Type | Description |
| :--- | :--- | :--- |
| `mainWindow` / `mainIWindow` | `Window` / `IWindow` | The primary application window. |
| `backendTarget` | `BackendTarget` | Active platform backend enum (`Windows`, `MacOS`, `Gtk`, `Qt`, `Swing`, `Android`, `IOS`, `Web`). |
| `backendName` | `String` | Descriptive backend string from `NativeFactory.backendName`. |
| `systemOsName` | `String` | Operating system family name (`backendTarget.osFamilyName`). |
| `titleCapabilities` | `TitleCapabilities` | `TitleCapabilities` of `mainWindow`. |
| `supportsMultipleWindows` | `Boolean` | `true` on desktop backends (`backendTarget.isDesktop`). |
| `supportsToolModalWindows` | `Boolean` | `true` when floating `PanelWindow` utility windows are supported (`backendTarget.isDesktop`). |
| `supportsServices` | `Boolean` | `true` **only** on Android (`backendTarget == BackendTarget.Android`), where background `<service>` components can be declared under `genericui { android { services { ... } } }`. |
| `supportsCustomTitleBar` | `Boolean` | Delegates to `titleCapabilities.supportsCustomTitleBar`. |
| `supportsEmbeddedMenuBarInTitleBar` | `Boolean` | Delegates to `titleCapabilities.supportsEmbeddedMenuBar`. |
| `supportsSystemMenuBar` | `Boolean` | `true` on macOS (`backendTarget == BackendTarget.MacOS`). |
| `supportsNavbarMenuBar` | `Boolean` | `true` on Android and iOS (`titleCapabilities.supportsNavbarMenuBar`). |
| `supportsSystemTray` | `Boolean` | `true` on desktop targets (`backendTarget.isDesktop`). |
| `supportsDirectorySelection` | `Boolean` | `true` on system targets (`backendTarget.isSystem`). |
| `supportsDirectFileSystemAccess` | `Boolean` | `true` on system targets (`backendTarget.isSystem`). |
| `supportsSystemThemeDetection` | `Boolean` | `true` across all 8 backends (`ThemeMode.AUTO`). |
| `supportsThemeAccentColor` | `Boolean` | `true` across all backends (`setThemeColor`). |
| `supportsHardwareGpuAcceleration` | `Boolean` | `true` across all backends. |

### 5.2 Example: Runtime Theme Switching, Global Stylesheets, & Fragment Launching

```kotlin
import com.example.studio.res.Resource
import com.nexustools.genericui.component.fragment.FragmentBundle
import com.nexustools.genericui.enums.system.BackendTarget
import com.nexustools.genericui.enums.ui.ThemeMode
import com.nexustools.genericui.window.TitleBar

// 1. Inspect backend target and capabilities
if (app.isDesktop && app.supportsMultipleWindows) {
    // Open an auxiliary inspector PanelWindow
}

// 2. Theme mode defaults to ThemeMode.AUTO; switch dynamically between AUTO, DARK, and LIGHT
app.setTheme(ThemeMode.AUTO)
val effectiveTheme: ThemeMode = app.getResolvedTheme() // ThemeMode.DARK or ThemeMode.LIGHT
app.setThemeColor("#10B981")

// 3. Attach or remove global precompiled stylesheets on IApplication
app.addStyleSheet(Resource.style.studio_main)

// 4. Configure main window TitleBar and launch a registered @AppFragment
app.setTitleBar(TitleBar(showWindowTitle = true, embedMenuBar = true))
app.launchFragment(
    fragmentClass = PreferencesFragment::class,
    launchArgs = FragmentBundle.of("tab" to "appearance")
)
```
