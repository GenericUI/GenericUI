# GenericUI — System Common Submodule (`:systemCommon`)

> **Root Repository**: [**GenericUI (`../README.md`)**](../README.md) | **Root Documentation**: [SourceSet Hierarchy](../docs/sourcesets.md) · [Backends Guide](../docs/backends.md) · [Fragments & Windows](../docs/fragments-and-windows.md) · [Application & Targets](../docs/application.md) · [Resources](../docs/resources.md) · [Gradle Plugin](../docs/gradle-plugin.md)

The **System Common Submodule (`:systemCommon`)** of **GenericUI** (`com.nexustools.genericui.system.*`) is the pure-Kotlin foundation for all non-browser system targets:
- **Desktop**: `:desktop:windows` (`mingwX64`), `:desktop:macos` (`macosArm64`, `macosX64`), `:desktop:gtk` (`linuxX64`, `linuxArm64`), `:desktop:qt` (`linuxX64`, `linuxArm64`), and `:desktop:swing` (`jvm`).
- **Mobile**: `:mobile:android` (`android`) and `:mobile:ios` (`iosArm64`, `iosSimulatorArm64`).

Because `:systemCommon` includes both Kotlin/Native targets and JVM/Android targets, it contains 100% pure Kotlin code with no browser/DOM dependencies.

---

## Position in the Phase 3 Module & SourceSet Hierarchy

```mermaid
flowchart TD
    common[":common (commonMain)<br/>All 8 Backends"]
    web[":web (webMain)<br/>Kotlin/Wasm + Kotlin/JS IR"]
    systemCommon[":systemCommon (systemCommonMain)<br/>All 7 System Backends (Desktop + Mobile)"]
    desktopCommon[":desktop:common (desktopCommonMain)<br/>All 5 Desktop Backends"]
    mobileCommon[":mobile:common (mobileCommonMain)<br/>Android + iOS"]
    swing[":desktop:swing (swingMain)<br/>JVM AWT / Swing"]
    nativeDesktopCommon[":desktop:nativeCommon (nativeDesktopCommonMain)<br/>Windows + macOS + GTK + Qt"]
    windows[":desktop:windows"]
    macos[":desktop:macos"]
    linuxCommon[":desktop:linuxCommon (linuxCommonMain)<br/>GTK + Qt"]
    gtk[":desktop:gtk"]
    qt[":desktop:qt"]
    android[":mobile:android"]
    ios[":mobile:ios"]

    common --> web
    common --> systemCommon
    systemCommon --> desktopCommon
    systemCommon --> mobileCommon
    desktopCommon --> swing
    desktopCommon --> nativeDesktopCommon
    nativeDesktopCommon --> windows
    nativeDesktopCommon --> macos
    nativeDesktopCommon --> linuxCommon
    linuxCommon --> gtk
    linuxCommon --> qt
    mobileCommon --> android
    mobileCommon --> ios
```

---

## Core Responsibilities

### 1. Filesystem & Preference Key Sanitization (`SystemStorageKeyHelper`)
`SystemStorageKeyHelper` (`com.nexustools.genericui.system.SystemStorageKeyHelper`) provides deterministic, cross-OS path normalization and storage key sanitization used by `PreferenceStore`, `ConfigDirectory`, and `CacheDirectory` implementations across `:desktop:nativeCommon`, `:desktop:linuxCommon`, `:desktop:swing`, `:mobile:android`, and `:mobile:ios`:

| Method | Signature | Description |
| :--- | :--- | :--- |
| `sanitizeAppDirectoryName` | `fun sanitizeAppDirectoryName(appName: String): String` | Sanitizes an application directory name by replacing invalid filesystem characters (`\`, `/`, `:`, `*`, `?`, `"`, `<`, `>`, `\|`) with `_`, defaulting to `"GenericUI"` when blank. |
| `sanitizeKey` | `fun sanitizeKey(key: String, fallback: String = "default"): String` | Sanitizes a preference or cache key into a safe single-segment filename. |
| `encodeKeySet` | `fun encodeKeySet(keys: Set<String>): ByteArray` | Encodes a set of preference keys into a newline-delimited UTF-8 `ByteArray`. |
| `decodeKeySet` | `fun decodeKeySet(bytes: ByteArray): LinkedHashSet<String>` | Decodes a newline-delimited UTF-8 `ByteArray` (or `String`) into an ordered `LinkedHashSet<String>`. |
| `normalizePath` | `fun normalizePath(path: String, separator: Char = '/'): String` | Normalizes all `/` and `\` path separators to the target OS `separator`. |
| `validateRelativePath` | `fun validateRelativePath(relativePath: String, separator: Char = '/'): String` | Strips leading/trailing slashes and rejects any `..` parent-directory traversal segments. |
| `joinPath` | `fun joinPath(baseDir: String, childPath: String, separator: Char = '/'): String` | Safely joins a base directory and relative child path using `separator`. |

### 2. Command-Line Arguments in `FragmentBundle` on System Targets
On all 7 system targets (`BackendTarget.isSystem == true`: `Windows`, `MacOS`, `Gtk`, `Qt`, `Swing`, `Android`, and `IOS`), the generated platform launcher (`WindowsLauncher`, `MacosLauncher`, `GtkLauncher`, `QtLauncher`, `SwingLauncher`, `AndroidLauncher`, `IosLauncher`) automatically populates the initial `FragmentBundle` passed to your `@MainAppFragment`'s `onCreate(savedState: FragmentBundle?)` with the process command-line arguments (`FragmentBundle.KEY_COMMAND_LINE_ARGS = "genericui:command_line_args"`):

```kotlin
package com.example.app

import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.fragment.FragmentBundle
import com.nexustools.genericui.component.fragment.MainAppFragment
import com.nexustools.genericui.enums.system.AppTarget

@MainAppFragment(targets = [AppTarget.COMMON], label = "Studio App")
class StudioMainFragment : Fragment() {
    override fun onCreate(savedState: FragmentBundle?) {
        super.onCreate(savedState)
        if (savedState?.hasCommandLineArgs() == true) {
            val cliArgs: List<String> = savedState.getCommandLineArgs()
            // Inspect CLI flags on Windows, macOS, GTK, Qt, Swing, Android, or iOS
        }
    }
}
```

### 3. Target-Specific `@MainAppFragment` & Variant Scope (`AppTarget.SYSTEM_COMMON`)
In a single-`commonMain` project, you can target all 7 system backends simultaneously using `AppTarget.SYSTEM_COMMON` (specificity rank `1`, which overrides `AppTarget.COMMON` at rank `0` on all desktop and mobile targets while leaving `:web` on the `AppTarget.COMMON` or `AppTarget.WEB` fragment) or configure system-wide settings in `build.gradle.kts`:

```kotlin
genericui {
    app {
        id = "com.example.studio"
        name = "Studio"
        version = "1.0.0"
    }

    backends { all() }

    // Applies to Windows, macOS, GTK, Qt, Swing, Android, and iOS:
    systemCommon {
        window {
            title = "Studio (System Edition)"
        }
    }
}
```

### 4. System-Target Styling Invariants (Typography vs. Aesthetic Style Restraints & Zero String-Keyed Style APIs)

All 7 non-web system targets (`Windows`, `MacOS`, `Gtk`, `Qt`, `Swing`, `Android`, and `IOS`) enforce strict native look-and-feel and zero-allocation styling rules:
1. **Zero String-Keyed Style APIs**: `ComponentStyle` and `ComputedStyle` operate exclusively on strongly-typed Kotlin properties and two 64-bit bitmasks (`activePropertiesBitmaskLow` / `High`, `importantBitmaskLow` / `High`) with zero `Map<String, String>` allocations or CSS string formatting (`setStyleProperty(String, String)`, `getStyleProperty`, `removeStyleProperty`, `clearStyleProperties`, `inlineStyle`, `setRawDeclaration`, `getRawDeclaration`, `fromDeclarations`, `rawDeclarations` do not exist).
2. **Text / Typography Elements (`Label`, `Text`, `Heading`, `Badge`, `Blockquote`, `CodeBlock`, `MarkdownView`)**: Honor typography and text-color properties (`color` / `textColor`, `fontFamily`, `fontSize`, `fontWeight`, `fontStyle`, `lineHeight`, `letterSpacing`, `textAlign`, `textDecoration`, `textTransform`, `textOverflow`, `whiteSpace`, `wordBreak`, plus `bootstrap.textColorType`, `bootstrap.fontSize`, `bootstrap.fontWeight`, `bootstrap.textAlignment`).
3. **Buttons, Inputs, Controls, & Standard Containers (`Button`, `IconButton`, `ToggleButton`, `CheckBox`, `RadioButton`, `Switch`, `Select`, `DropdownButton`, `TextField`, `TextArea`, `RangeSlider`, `ProgressBar`, `Spinner`, `TabContainer`, `Tab`, `Container`, `Card`, etc.)**: Aesthetic skinning and typography overrides on `ComponentStyle` (`backgroundColor`, `background`, `color`, `accentColor`, `borderColor`, `borderWidth`, `borderStyle`, `borderRadius`, `boxShadow`, `textShadow`, `filter`, `backdropFilter`, `fontFamily`, `fontSize`, `fontWeight`, `fontStyle`) are **silently ignored** on all 7 system backends. Standard controls can **ONLY** be tinted via `BootstrapType` (`bootstrap.type`, `bootstrap.backgroundType`, `bootstrap.textColorType`, `bootstrap.borderType`, `bootstrap.outline`):
   - `BootstrapType == BootstrapType.NONE`: Renders the 100% unmodified genuine native OS component.
   - `BootstrapType != BootstrapType.NONE`: Renders the native OS control with **no text** into an offscreen ARGB buffer, color-tints the native control pixels via `DesktopControlTintRenderUtil` / `ThemeConfig` while preserving native 3D bevels, borders, and hover/pressed/disabled states, blits the tinted sprite, and renders contrasting text and icons on top afterward.
4. **`StyledContainer`, `StyledButton`, & `:extensions:alien`**: Explicitly opt-in custom-styled components that honor full `ComponentStyle` aesthetic and typography properties across all 8 backends.

---

## Related Documentation

- **Desktop Submodules**: [`:desktop`](../desktop/README.md) · [`:desktop:common`](../desktop/common/README.md) · [`:desktop:nativeCommon`](../desktop/nativeCommon/README.md) · [`:desktop:linuxCommon`](../desktop/linuxCommon/README.md)
- **Mobile Submodules**: [`:mobile`](../mobile/README.md) · [`:mobile:common`](../mobile/common/README.md) · [`:mobile:android`](../mobile/docs/android.md) · [`:mobile:ios`](../mobile/docs/ios.md)
- **Web Submodule**: [`:web`](../web/README.md)
- **Gradle Plugin**: [`:gradle-plugin`](../gradle-plugin/README.md)
