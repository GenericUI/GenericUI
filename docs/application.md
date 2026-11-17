# GenericUI Application, `IApplication`, Theming (`ThemeMode.AUTO`), Stylesheets, & `BackendTarget` Guide

> **Root Repository**: [**GenericUI (`../README.md`)**](../README.md) | **Master Docs Index**: [**`docs/README.md`**](./README.md)  
> **Related Guides**: [iPadOS Backend (`ipados.md`)](./ipados.md) · [Shared Apple (`apple.md`)](./apple.md) · [Shared JVM (`jvm.md`)](./jvm.md) · [Fragments & Windows (`fragments-and-windows.md`)](./fragments-and-windows.md) · [Hybrid Layouts & Helpers (`layouts-and-helpers.md`)](./layouts-and-helpers.md) · [Resources (`resources.md`)](./resources.md) · [Gradle Plugin (`gradle-plugin.md`)](./gradle-plugin.md) · [Platform Backends (`backends.md`)](./backends.md) · [SourceSets (`sourcesets.md`)](./sourcesets.md)

This guide covers [`IApplication`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/shell/IApplication.kt), [`Application`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/shell/Application.kt), [`FragmentApplication`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/shell/FragmentApplication.kt), [`GenericUI`](../common/src/commonMain/kotlin/com/nexustools/genericui/core/GenericUI.kt), optional custom `@AppApplication` classes, [`ThemeMode`](../common/src/commonMain/kotlin/com/nexustools/genericui/enums/ui/ThemeMode.kt) (`AUTO`, `DARK`, `LIGHT` with 9-target OS theme detection), global stylesheet management (`IApplication.addStyleSheet` / `removeStyleSheet`), [`BackendTarget`](../common/src/commonMain/kotlin/com/nexustools/genericui/enums/system/BackendTarget.kt) runtime target/group queries, and platform capability checks.

---

## 1. Automatic Application Bootstrapping vs. Custom `@AppApplication`

### 1.1 Default Zero-Boilerplate Bootstrapping (`@MainAppFragment` Only)

Most GenericUI applications need **only** a `@MainAppFragment`-annotated `Fragment` class in `src/commonMain/kotlin` and `genericui { app { ... } }` in `build.gradle.kts`. For every enabled backend target, the GenericUI Gradle plugin generates a `GenericUiAppDescriptor` and target launcher (`WindowsLauncher`, `MacosLauncher`, `GtkLauncher`, `QtLauncher`, `SwingLauncher`, `AndroidLauncher`, `IosLauncher`, `IpadosLauncher`, `WebLauncher`) that:
1. Initializes the platform's `NativeFactory` (`GenericUI.initialize(...)`) and sets Apple icon metadata (`GenericUI.appleIconExplicitlyProvided`) so macOS wraps generic/default Dock icons inside a Chrome-style rounded white squircle automatically when no Apple-specific override (`appleIcon` / `macIcon`) was supplied.
2. Registers all enabled extension providers automatically.
3. Registers the resolved `@MainAppFragment` and all matching `@AppFragment` classes in `FragmentRegistry`.
4. Constructs `FragmentApplication` with the resolved variant window geometry (`width`, `height`, `minWidth`, `minHeight`, `resizable`, `fullscreen`, `alwaysOnTop`, `aspectRatio`), title, icon, theme mode (defaulting to **`ThemeMode.AUTO`**), and `themeColor`.
5. Automatically calls `app.addStyleSheet(...)` in generated startup code whenever `styles { globalStyleSheet = "..." }` (or `theme { styleSheet = "..." }`) is configured in `build.gradle.kts`.
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
        setThemeMode(ThemeMode.AUTO)
        // Attach a precompiled .scss stylesheet (Resource.style.<name>):
        addStyleSheet(Resource.style.studio_theme)
    }
}
```

Just like `@MainAppFragment`, `@AppApplication(targets = [...])` supports `AppTarget` specificity resolution (`COMMON(0)`, `SYSTEM_COMMON(1)`, `APPLE_COMMON(2)`, `JVM_COMMON(2)`, `DESKTOP_COMMON(3)`, `MOBILE_COMMON(3)`, `NATIVE_DESKTOP_COMMON(4)`, `LINUX_COMMON(5)`, `IOS_COMMON(5)`, and all 9 concrete targets at rank `6`).

---

## 2. `IApplication` Theming (`ThemeMode.AUTO`, `DARK`, `LIGHT` — Default `AUTO`)

GenericUI's [`ThemeMode`](../common/src/commonMain/kotlin/com/nexustools/genericui/enums/ui/ThemeMode.kt) enum defines three modes and defaults to **`ThemeMode.AUTO`** across all applications:

| `ThemeMode` Entry | `bsThemeValue` | Behavior |
| :--- | :--- | :--- |
| **`ThemeMode.AUTO`** *(Default)* | `"auto"` | Automatically detects and tracks the host operating system or browser's active dark/light color scheme in real time via [`SystemThemeDetector`](../common/src/commonMain/kotlin/com/nexustools/genericui/style/theme/SystemThemeDetector.kt). |
| **`ThemeMode.DARK`** | `"dark"` | Forces dark mode across native OS controls (e.g. Win32 `UxTheme` `DarkMode_Explorer`), `:web` Bootstrap (`data-bs-theme="dark"`), and `:extensions:ui:alien`. |
| **`ThemeMode.LIGHT`** | `"light"` | Forces light mode across native OS controls (e.g. Win32 `UxTheme` `Explorer`), `:web` Bootstrap (`data-bs-theme="light"`), and `:extensions:ui:alien`. |

### 2.1 Theme & Preference API on `IApplication`

| Method | Return Type | Description |
| :--- | :--- | :--- |
| `app.setThemeMode(mode: ThemeMode)` | `Unit` | Sets the application theme mode (`ThemeMode.AUTO`, `ThemeMode.DARK`, or `ThemeMode.LIGHT`) and notifies all registered theme listeners and native windows. |
| `app.getThemeMode(): ThemeMode` | `ThemeMode` | Returns the configured `ThemeMode` (`AUTO`, `DARK`, or `LIGHT`). |
| `app.getResolvedTheme(): ThemeMode` | `ThemeMode` | Resolves `ThemeMode.AUTO` to the currently active concrete system theme (`ThemeMode.DARK` or `ThemeMode.LIGHT`). |
| `app.addThemeChangeListener(listener)`<br/>`app.removeThemeChangeListener(listener)` | `Unit` | Registers or unregisters a callback `(ThemeMode) -> Unit` invoked whenever the theme or underlying OS dark/light mode changes. |
| `app.setThemeColor(colorHex: String?)`<br/>`app.getThemeColor(): String?` | `Unit` / `String?` | Configures or queries the application's primary accent chrome color (`themeConfig.primaryColor`). |
| `app.setThemeConfig(config: ThemeConfig)`<br/>`app.getThemeConfig(): ThemeConfig` | `Unit` / `ThemeConfig` | Configures or queries the unified cross-platform [`ThemeConfig`](../common/src/commonMain/kotlin/com/nexustools/genericui/style/theme/ThemeConfig.kt) semantic palette and light/dark [`ThemeColorSet`](../common/src/commonMain/kotlin/com/nexustools/genericui/style/theme/ThemeColorSet.kt) overrides, updating [`BootstrapColorPalette.activeThemeConfig`](../common/src/commonMain/kotlin/com/nexustools/genericui/style/color/BootstrapColorPalette.kt) and propagating changes to all open windows and DOM stylesheets. |
| `app.getPreferenceStore()` | `PreferenceStore` | Returns the application's shared cross-platform [`PreferenceStore`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/system/PreferenceStore.kt) (also accessible on any `IFragment` via `fragment.getPreferenceStore()`). |

### 2.2 Unified Cross-Platform `ThemeConfig` & `ThemeColorSet`

[`ThemeConfig`](../common/src/commonMain/kotlin/com/nexustools/genericui/style/theme/ThemeConfig.kt) and [`ThemeColorSet`](../common/src/commonMain/kotlin/com/nexustools/genericui/style/theme/ThemeColorSet.kt) provide a single strongly-typed theme palette shared across Gradle build configuration (`genericui { theme { ... } }`), runtime `IApplication` APIs (`app.setThemeConfig(...)`), native control offscreen sprite tinting (`DesktopControlTintRenderUtil`, `JvmControlTintPixelUtil`, `IosControlTintRenderUtil`), Android Material XML generation, and Web CSS custom properties (`gui/genericui-theme.css` + `<style id="genericui-runtime-theme-css">`):

```kotlin
import com.nexustools.genericui.enums.ui.ThemeMode
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.theme.ThemeColorSet
import com.nexustools.genericui.style.theme.ThemeConfig

val customTheme = ThemeConfig(
    mode = ThemeMode.AUTO,
    primaryColor = RgbaColor.fromHex("#2563EB"),
    secondaryColor = RgbaColor.fromHex("#64748B"),
    accentColor = RgbaColor.fromHex("#3B82F6"),
    successColor = RgbaColor.fromHex("#10B981"),
    dangerColor = RgbaColor.fromHex("#EF4444"),
    warningColor = RgbaColor.fromHex("#F59E0B"),
    infoColor = RgbaColor.fromHex("#06B6D4"),
    lightColors = ThemeColorSet(
        backgroundColor = RgbaColor.fromHex("#F8FAFC"),
        surfaceColor = RgbaColor.fromHex("#FFFFFF"),
        textPrimaryColor = RgbaColor.fromHex("#0F172A"),
        textSecondaryColor = RgbaColor.fromHex("#475569"),
        borderColor = RgbaColor.fromHex("#E2E8F0")
    ),
    darkColors = ThemeColorSet(
        backgroundColor = RgbaColor.fromHex("#0F172A"),
        surfaceColor = RgbaColor.fromHex("#1E293B"),
        textPrimaryColor = RgbaColor.fromHex("#F8FAFC"),
        textSecondaryColor = RgbaColor.fromHex("#94A3B8"),
        borderColor = RgbaColor.fromHex("#334155")
    )
)

app.setThemeConfig(customTheme)
```

---

## 3. Global Stylesheet Management (`IApplication.addStyleSheet` & `removeStyleSheet`)

> [!IMPORTANT]
> **Application-Level Responsibility**: Global stylesheet registration (`addStyleSheet` / `removeStyleSheet`) lives on **`IApplication`** (`Application` and `FragmentApplication`) — **never** on `IFragment` (except for `:extensions:ui:alien`'s `AlienFragment` / `AlienContainer` implementing [`IAlien`](../extensions/ui/alien/src/commonMain/kotlin/com/nexustools/genericui/ext/alien/IAlien.kt)).

| Method | Description |
| :--- | :--- |
| `app.addStyleSheet(styleSheetResourceId: ResourceID)` | Registers and activates a global `.scss`/`.css` stylesheet by its compiled `Resource.style.<name>` (`R.style.<name>`) `ResourceID`. |
| `app.addStyleSheet(styleSheetPath: String)` | Registers and activates a global stylesheet by its resource path (e.g. `"styles/main.scss"` or `"styles/main.css"`). |
| `app.addStyleSheet(styleSheet: PrecompiledStyleSheet)` | Registers and activates a build-time precompiled [`PrecompiledStyleSheet`](../common/src/commonMain/kotlin/com/nexustools/genericui/style/css/PrecompiledStyleSheet.kt) object directly. |
| `app.removeStyleSheet(...)` | Removes a previously added global stylesheet (`ResourceID`, path, or `PrecompiledStyleSheet`) and triggers style recomputation across open windows. |
| `app.hasStyleSheet(...)` | Returns `true` if the specified stylesheet is currently active on the application. |
| `app.getStyleSheets()` / `app.getStyleSheetPaths()` | Returns the ordered list of active `PrecompiledStyleSheet` instances or stylesheet paths. |

---

## 4. `BackendTarget` (`com.nexustools.genericui.enums.system.BackendTarget`)

[`BackendTarget`](../common/src/commonMain/kotlin/com/nexustools/genericui/enums/system/BackendTarget.kt) identifies all 9 runtime platform backends and exposes multi-backend group properties so shared code never parses platform strings:

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
| **`BackendTarget.IPadOS`** | `"ipados"` | `"iPadOS (UIKit)"` | `"iPadOS"` |
| **`BackendTarget.Web`** | `"web"` | `"Web (HTML5 / JS / Wasm)"` | `"Web Browser"` |

### 4.2 Multi-Backend Target Group Matrix

| Group Property (on `BackendTarget` & `IApplication`) | `Windows` | `MacOS` | `Gtk` | `Qt` | `Swing` | `Android` | `IOS` | `IPadOS` | `Web` |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **`isSystem`** (`this != Web`) | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | — |
| **`isDesktop`** | ✅ | ✅ | ✅ | ✅ | ✅ | — | — | — | — |
| **`isNativeDesktop`** | ✅ | ✅ | ✅ | ✅ | — | — | — | — | — |
| **`isLinux`** | — | — | ✅ | ✅ | — | — | — | — | — |
| **`isMobile`** / **`isTouchPrimary`** | — | — | — | — | — | ✅ | ✅ | ✅ | — |
| **`isApple`** | — | ✅ | — | — | — | — | ✅ | ✅ | — |
| **`isIosFamily`** (`BackendTarget`) | — | — | — | — | — | — | ✅ | ✅ | — |
| **`isUnix`** | — | ✅ | ✅ | ✅ | — | ✅ | ✅ | ✅ | — |
| **`isPosix`** / **`isNative`** | ✅ | ✅ | ✅ | ✅ | — | — | ✅ | ✅ | — |
| **`isJvm`** | — | — | — | — | ✅ | ✅ | — | — | — |
| **`isBrowser`** | — | — | — | — | — | — | — | — | ✅ |

---

## 5. `IApplication` Runtime Capabilities & Connectivity (`isOffline` / `isOnline`)

| Property | Type | Description |
| :--- | :--- | :--- |
| `mainWindow` / `mainIWindow` | `Window` / `IWindow` | The primary application window. |
| `backendTarget` | `BackendTarget` | Active platform backend (`Windows`, `MacOS`, `Gtk`, `Qt`, `Swing`, `Android`, `IOS`, `IPadOS`, `Web`). |
| `backendName` / `systemOsName` | `String` | Descriptive backend name from `NativeFactory.backendName` and `backendTarget.osFamilyName`. |
| `titleCapabilities` | `TitleCapabilities` | `TitleCapabilities` of `mainWindow`. |
| `supportsMultipleWindows` / `supportsToolModalWindows` | `Boolean` | `true` on desktop backends and **iPadOS (`BackendTarget.IPadOS`)** (`IpadosNativeFactory`); `false` on single-window iPhone `IOS`, `Android`, and `Web`. |
| `supportsServices` | `Boolean` | `true` **only** on Android (`BackendTarget.Android`). |
| `supportsCustomTitleBar` / `supportsEmbeddedMenuBarInTitleBar` | `Boolean` | Delegates to `titleCapabilities`. |
| `supportsSystemMenuBar` | `Boolean` | `true` on **macOS** (`NSApplication.mainMenu`) and **iPadOS** (`UIMenuSystem.mainSystem`). |
| `supportsNavbarMenuBar` | `Boolean` | `true` on Android and iPhone iOS (`false` on iPadOS where `MenuBar` uses `UIMenuSystem.mainSystem`). |
| `supportsSystemTray` | `Boolean` | `true` on desktop targets (`backendTarget.isDesktop`). |
| `supportsDirectorySelection` / `supportsDirectFileSystemAccess` | `Boolean` | `true` on all 8 system targets (`backendTarget.isSystem`). |
| `supportsSystemThemeDetection` / `supportsThemeAccentColor` / `supportsHardwareGpuAcceleration` | `Boolean` | `true` across all 9 backends. |
| `isOffline` / `isOnline` | `Boolean` | Live network reachability via WinINet/`INetworkListManager` (Windows), `:apple`'s `NWPathMonitor` (macOS/iOS/iPadOS), GIO `GNetworkMonitor` (GTK), Qt `QNetworkInformation` (Qt), `NetworkInterface` (Swing), `ConnectivityManager` (Android), and `navigator.onLine` (Web), with `addOfflineListener`, `removeOfflineListener`, and `setOnOfflineChanged`. |
