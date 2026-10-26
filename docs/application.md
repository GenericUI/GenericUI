# GenericUI Application, `IApplication`, & `BackendTarget` Guide

> **Root Repository**: [**GenericUI (`../README.md`)**](../README.md) | **Master Docs Index**: [**`docs/README.md`**](./README.md)  
> **Related Guides**: [Fragments & Windows (`fragments-and-windows.md`)](./fragments-and-windows.md) · [Gradle Plugin (`gradle-plugin.md`)](./gradle-plugin.md) · [Platform Backends (`backends.md`)](./backends.md) · [SourceSets (`sourcesets.md`)](./sourcesets.md)

This guide covers [`IApplication`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/shell/IApplication.kt), [`Application`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/shell/Application.kt), [`FragmentApplication`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/shell/FragmentApplication.kt), optional custom `@AppApplication` classes, [`BackendTarget`](../common/src/commonMain/kotlin/com/nexustools/genericui/enums/system/BackendTarget.kt) runtime target and group queries, and platform capability checks.

---

## 1. Automatic Application Bootstrapping vs. Custom `@AppApplication`

### 1.1 Default Zero-Boilerplate Bootstrapping (`@MainAppFragment` Only)

Most GenericUI applications need **only** a `@MainAppFragment`-annotated `Fragment` class in `src/commonMain/kotlin` and `genericui { app { ... } }` in `build.gradle.kts`. For every enabled backend target, the GenericUI Gradle plugin generates a `GenericUiAppDescriptor` and target launcher (`WindowsLauncher`, `MacosLauncher`, `GtkLauncher`, `QtLauncher`, `SwingLauncher`, `AndroidLauncher`, `IosLauncher`, `WebLauncher`) that:
1. Initializes the platform's `NativeFactory` (`GenericUI.initialize(...)`).
2. Registers all enabled extension providers automatically.
3. Registers the resolved `@MainAppFragment` and all matching `@AppFragment` classes in `FragmentRegistry`.
4. Constructs `FragmentApplication` with the resolved variant window geometry, title, icon, theme mode, and `themeColor`.
5. Instantiates the resolved `@MainAppFragment` and passes process command-line arguments in the initial `FragmentBundle` on system targets (`savedState?.getCommandLineArgs()`).

### 1.2 Optional Custom `@AppApplication` (`com.nexustools.genericui.component.fragment.AppApplication`)

When an application wants custom startup hooks, global menu bars, or custom `FragmentApplication` / `Application` state, annotate an `IApplication` subclass with [`@AppApplication`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/fragment/AppApplication.kt):

```kotlin
import com.nexustools.genericui.component.fragment.AppApplication
import com.nexustools.genericui.component.fragment.MainAppFragment
import com.nexustools.genericui.component.fragment.Fragment
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
        setThemeMode(ThemeMode.DARK)
    }
}
```

Just like `@MainAppFragment`, `@AppApplication(targets = [...])` supports `AppTarget` specificity resolution (`COMMON`, `SYSTEM_COMMON`, `DESKTOP_COMMON`, `NATIVE_DESKTOP_COMMON`, `LINUX_COMMON`, `MOBILE_COMMON`, and all 8 concrete targets).

---

## 2. `BackendTarget` (`com.nexustools.genericui.enums.system.BackendTarget`)

[`BackendTarget`](../common/src/commonMain/kotlin/com/nexustools/genericui/enums/system/BackendTarget.kt) identifies the active runtime platform backend and provides multi-backend group properties so common code never needs string parsing or brittle OS checks:

### 2.1 `BackendTarget` Entries

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

> [!TIP]
> **Single-Target vs. Multi-Target Checks**: Use `app.backendTarget == BackendTarget.Windows` (or `when (app.backendTarget)`) when checking a single backend, and use the multi-backend boolean group properties below when checking capabilities shared by a family of targets.

### 2.2 Multi-Backend Target Group Matrix

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

## 3. `IApplication` Interface & Runtime Capabilities

[`IApplication`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/shell/IApplication.kt) is implemented by [`Application`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/shell/Application.kt) and [`FragmentApplication`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/shell/FragmentApplication.kt).

### 3.1 Core Properties & Capability Queries

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
| `supportsNativeNotifications` | `Boolean` | `true` across all backends. |
| `supportsNativeFileDialogs` | `Boolean` | `true` across all backends. |
| `supportsPictureInPicture` | `Boolean` | `true` across all backends. |
| `supportsFullscreen` | `Boolean` | `true` across all backends. |
| `supportsSystemThemeDetection` | `Boolean` | `true` across all backends (`ThemeMode.SYSTEM`). |
| `supportsThemeAccentColor` | `Boolean` | `true` across all backends (`setThemeColor`). |
| `supportsHardwareGpuAcceleration` | `Boolean` | `true` across all backends. |

### 3.2 Window Chrome, Theme, & Fragment Navigation Methods on `IApplication`

```kotlin
// 1. Inspect backend target and capabilities
if (app.isDesktop && app.supportsMultipleWindows) {
    // Open an auxiliary inspector PanelWindow
}
if (app.backendTarget == BackendTarget.MacOS && app.supportsSystemMenuBar) {
    // macOS global menu bar is active
}

// 2. Configure theme mode and cross-backend accent color at runtime
app.setThemeMode(ThemeMode.DARK)
app.setThemeColor("#10B981")

// 3. Configure main window TitleBar and MenuBar
app.setTitleBar(TitleBar(showWindowTitle = true, embedMenuBar = true))
app.setMenuBar(myMenuBar)

// 4. Launch a registered @AppFragment into mainWindow
app.launchFragment(
    fragmentClass = PreferencesFragment::class,
    launchArgs = FragmentBundle.of("tab" to "shortcuts")
)
```
