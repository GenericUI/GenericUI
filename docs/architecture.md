# GenericUI Architecture Guide

> **Root Repository**: [**GenericUI (`../README.md`)**](../README.md) | **Master Docs Index**: [**`docs/README.md`**](./README.md)  
> **Related Guides**: [Fragments & Windows (`fragments-and-windows.md`)](./fragments-and-windows.md) · [Application (`application.md`)](./application.md) · [Resources (`resources.md`)](./resources.md) · [SourceSets (`sourcesets.md`)](./sourcesets.md) · [Platform Backends (`backends.md`)](./backends.md) · [Extensions (`extensions.md`)](./extensions.md) · [Gradle Plugin (`gradle-plugin.md`)](./gradle-plugin.md)

`GenericUI` is a Kotlin Multiplatform (KMP) UI, windowing, docking, media, and hardware-accelerated GPU canvas framework engineered for native OS responsiveness and zero runtime bloat.

---

## 1. Why GenericUI? (Single-`commonMain` KMP with Native OS Widgets)

Desktop applications built on Chromium/NW.js or Electron bundle an entire browser engine per process, consuming hundreds of megabytes of baseline RAM and forcing every OS window, menu bar, and file dialog through web emulation.

GenericUI solves this by combining:
- **Single-`commonMain` Application Development**: Write `@MainAppFragment` and `@AppFragment` classes once in `src/commonMain/kotlin`. The GenericUI Gradle plugin generates per-backend launchers, 1:1 Android `AppCompatActivity` classes, `AndroidManifest.xml`, and `Resource.ResourceID` translations automatically.
- **True Native OS Controls** across 8 platform backends (`:desktop:windows`, `:desktop:macos`, `:desktop:gtk`, `:desktop:qt`, `:desktop:swing`, `:mobile:android`, `:mobile:ios`, `:web`).
- **Direct Hardware GPU Surfaces & Overlays** (`:extensions:gpu`, `:extensions:overlay`, `:extensions:canvas2d`, `:extensions:canvas3d`, `:extensions:media`, `:extensions:mediacontrols`).

---

## 2. Core & Multi-Backend Foundation Module Hierarchy

Every module in GenericUI is **either a direct backend target or a common target**, strictly following the **1 Public Class / Interface / Enum Per `.kt` File** standard:

```text
:common                     (All 8 backends: UI widgets, IWindow, TitleCapabilities, IApplication, BackendTarget,
                             AppTarget, IFragment, @MainAppFragment, @AppFragment, FragmentHostFragment,
                             FragmentHostSystem, Resource/ResourceID/ResourceResolver, PreferenceStore, Image API)
├── :web                    (Kotlin/Wasm primary + Kotlin/JS IR fallback, HTML5 DOM, WebGL2/WebGPU, #hash + History.state)
└── :systemCommon           (All 7 OS/system targets including JVM Swing: pure-Kotlin system utilities)
    ├── :desktop:common     (All 5 desktop targets: Windows, macOS, GTK, Qt, Swing)
    │   ├── :desktop:swing  (JVM AWT/Swing + optional LWJGL 3 OpenGL ES & Vulkan)
    │   └── :desktop:nativeCommon (All 4 native desktop targets: Windows, macOS, GTK, Qt)
    │       ├── :desktop:windows  (Win32 HWND, Direct2D, Google ANGLE, Vulkan, embedded .rc/.res resources)
    │       ├── :desktop:macos    (AppKit NSWindow/NSView, global NSMenu, CoreGraphics, MoltenVK)
    │       └── :desktop:linuxCommon (Linux XDG config/cache/preferences shared by GTK & Qt)
    │           ├── :desktop:gtk  (GTK4 / Libadwaita / Cairo / GResource)
    │           └── :desktop:qt   (Qt6 QMainWindow / QPainter / QVulkanWindow / QResource)
    └── :mobile:common      (Mobile posture, gestures, fling physics, PiP, lazy viewport pool)
        ├── :mobile:android (1:1 generated AppCompatActivity per @AppFragment/@MainAppFragment, Material 3, RecyclerView, SAF)
        └── :mobile:ios     (UIKit UIWindow/UIViewController, CADisplayLink, UIDocumentPicker, MoltenVK)
```

---

## 3. Modular Two-Tier Extension Architecture

To keep `:common` and core backend modules 100% free of extension-specific logic, specialized capabilities are isolated in `:extensions:*`:

1. **Project-of-Projects Extensions** (`:extensions:<name>:common`, `:systemCommon`, `:desktop:common`, `:desktop:nativeCommon`, `:desktop:{windows,macos,gtk,qt,swing}`, `:mobile:common`, `:mobile:{android,ios}`, `:web`, plus umbrella `:extensions:<name>`):
   - **`:extensions:gpu`**: Unified Vulkan 1.2+ (`VKContext` primary with SPIR-V & MoltenVK) and OpenGL ES 3.0+ (`GLContext` fallback with Google ANGLE on Windows) device, shader, and offscreen context foundation.
   - **`:extensions:imageprocessing`**: Offscreen GPU-accelerated image filters and layer compositing (`GpuImageProcessor`, `GpuImageFilterChain`, `GpuImageKernel`).
   - **`:extensions:canvas2d`**: Hardware-accelerated 2D canvas (`Canvas2D`, `ScrollCanvas2D`, `CurveEditor`, `Canvas2DOverlayHost`).
   - **`:extensions:canvas3d`**: Unified hardware-accelerated 3D/shader canvas (`Canvas3D`, `Canvas3DConfig`, `Canvas3DRegistry`, `ScrollCanvas3D`, `Canvas3DOverlayHost`).
   - **`:extensions:media`**: `VideoPlayer` (with `VideoOverlayHost`), `AudioPlayer`, `AudioAnalyzer`, `WebVttSrtParser`, native `libvlc` in `:extensions:media:desktop:nativeCommon`, and `libvlcj` in `:extensions:media:desktop:swing`.
   - **`:extensions:mediacontrols`**: Standalone OS media transport controls and hardware keyboard media key hooks (`MediaControlsSession` — depends **only on `:common`**).
   - **`:extensions:webview`**: Chromium (`ChromiumBlinkEngine`) + `NJS` (`lib/NJS` AsmJit/SLJIT Native JIT Engine) on Desktop/Swing, and native `android.webkit.WebView` / `WKWebView` / `<iframe>` on Mobile/Web.
   - **`:extensions:alien`**: Self-rendered Bootstrap 5.3 `Canvas2D` UI engine on system targets (`AlienFactory`, `AlienFragment`, `UnalienContainer`) with zero-overhead native DOM pass-through on `:web`.
2. **Single Multiplatform Project Extensions** (`:extensions:<name>`):
   - **`:extensions:overlay`** (depends **only on `:common`**), **`:extensions:codeeditor`**, **`:extensions:aichat`**, **`:extensions:threejs`**, **`:extensions:box2d`**, **`:extensions:box3d`**, and **`:extensions:dsl`**.

---

## 4. Deferred Upward `NativeImpl` Resolution

Widgets can be constructed freely in pure Kotlin **before** any `Window` or `Application` exists (`val btn = Button("Render")`). When a child is attached to a parent container (`parent.addChild(btn)`), GenericUI traverses **upward** (`parent -> ... -> RootContainer -> Window -> Application`) to resolve the active backend's `NativeFactory`:

```mermaid
flowchart TD
    A["1. Widget Constructed in Pure Kotlin<br/>val btn = Button('Render')<br/>(nativeImpl == null)"] --> B["2. Attached to Parent Container<br/>vstack.addChild(btn)"]
    B --> C{"3. Walk Upward:<br/>parent -> ... -> RootContainer -> Window -> Application"}
    C -- "Root is Realized (Attached to Window)" --> D["4a. Realize Immediately:<br/>Create NativeImpl via Factory,<br/>Flush State & Styles,<br/>Cascade Downward to Children"]
    C -- "Subtree is Still Detached" --> E["4b. Defer Realization:<br/>Store in parent.children.<br/>When Root Attaches to Window Later,<br/>Cascade Realization Downward"]
```

---

## 5. `TitleBar`, `TitleCapabilities`, & Single-Placement Components

Unlike web frameworks that render fake HTML titlebars inside the client area, GenericUI models [`TitleBar`](../common/src/commonMain/kotlin/com/nexustools/genericui/window/TitleBar.kt) as a window chrome descriptor inspected via [`TitleCapabilities`](../common/src/commonMain/kotlin/com/nexustools/genericui/window/TitleCapabilities.kt):
- **Single-Placement Guarantee (`TitleCapabilities.canEmbedComponentsInTitleBar`)**: When a window supports custom components in its titlebar (`canEmbedComponentsInTitleBar == true`), components added to `TitleBar` (`leadingComponents`, `centerComponents`, `trailingComponents`—such as `FragmentHostFragment`'s Back/Forward buttons or `AiChatContainer`'s action buttons) are marked `mountedInTitleBar = true` and rendered **only once** inside the titlebar, never duplicated inside the content body.
- **Conditional Chrome on Mobile & Web (`TitleCapabilities.isTitleBarRequired == false`)**:
  - **Android & iOS**: Setting a `TitleBar` **or** a `MenuBar` creates a top navbar; if neither is set, **no navbar is added to the view hierarchy**.
  - **Web**: `MenuBar` only renders a menubar; `TitleBar` only renders a navbar; `TitleBar` + `MenuBar` merges both into a single Bootstrap navbar (title/icon on the left, menu on the right); neither renders no navbar at all.

---

## 6. Strongly-Typed Zero-Allocation Styling, System Typography vs. Aesthetic Restraints, & Native Control Offscreen Tinting

### 6.1 Zero String-Keyed Style APIs & Bitmask-Driven `ComponentStyle` / `ComputedStyle`

GenericUI **never** exposes string-keyed style APIs (`setStyleProperty(String, String)`, `getStyleProperty`, `removeStyleProperty`, `clearStyleProperties`, `inlineStyle`, `setRawDeclaration`, `getRawDeclaration`, `fromDeclarations`, `rawDeclarations` are strictly prohibited in `:common`):
- **Zero-Allocation 128-Bit Bitmasks on System Targets**: [`ComponentStyle`](../common/src/commonMain/kotlin/com/nexustools/genericui/style/css/ComponentStyle.kt) (`component.getStyle()`) and [`ComputedStyle`](../common/src/commonMain/kotlin/com/nexustools/genericui/style/css/ComputedStyle.kt) store 100% strongly-typed Kotlin fields (`RgbaColor?`, `Float?`, `CssDimension?`, `CssEdgeInsets?`, `CssCornerRadii?`, `CssFontWeight?`, etc.) tracked via two 64-bit `Long` bitmasks (`activePropertiesBitmaskLow` / `activePropertiesBitmaskHigh` and `importantBitmaskLow` / `importantBitmaskHigh` indexed by [`CssProperty.ordinal`](../common/src/commonMain/kotlin/com/nexustools/genericui/enums/css/CssProperty.kt)). All 7 non-web system backends evaluate styles using zero string formatting and zero `Map<String, String>` allocations.
- **Transparent DOM CSS Translation on `:web` (`WebCssStyleTranslator`)**: Only `:web` formats CSS strings at the DOM boundary via [`WebCssStyleTranslator`](../web/src/commonMain/kotlin/com/nexustools/genericui/web/WebCssStyleTranslator.kt) (`translateStyleToCssDeclarationMap(style: ComponentStyle)` and `applyComponentStyleToElement(element, style, bootstrap)`).
- **Specificity Cascade**: Stylesheets compiled by `compileGenericUiSass` into [`PrecompiledStyleSheet`](../common/src/commonMain/kotlin/com/nexustools/genericui/style/css/PrecompiledStyleSheet.kt) (`Resource.style.<name>`) cascade onto components using deterministic specificity:
  1. **Bootstrap Enum Defaults** ([`ComponentBootstrap`](../common/src/commonMain/kotlin/com/nexustools/genericui/style/bootstrap/ComponentBootstrap.kt) — `BootstrapType`, `BootstrapSize`, `SpacingScale`, `BorderRadiusStyle`, `ShadowElevation`)
  2. **Type / Tag Selectors** (`Button`, `Label`, `VStack`)
  3. **Class Selectors** (`.toolbar-btn`, `.panel-card`)
  4. **ID Selectors** (`#main-canvas-toolbar`)
  5. **Inline `ComponentStyle` Properties** (`component.getStyle().setColor(RgbaColor.fromHex("#2A80FF"))` or `:extensions:dsl` `style { color = RgbaColor.fromHex("#2A80FF") }`)
  6. **`!important` Overrides** (`important = true` / `importantBitmaskLow` & `High`)

### 6.2 System Backend Typography vs. Aesthetic Style Restraints

On all 7 non-web system targets (`:desktop:windows`, `:desktop:macos`, `:desktop:gtk`, `:desktop:qt`, `:desktop:swing`, `:mobile:android`, `:mobile:ios`), standard UI controls cannot have their aesthetic styles redesigned to look alien without `:extensions:alien` (or [`StyledContainer`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/container/StyledContainer.kt) / [`StyledButton`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/action/StyledButton.kt)):

| Component Category | `acceptsTypographyStylesOnSystemBackend` | `acceptsAestheticSkinStylesOnSystemBackend` | System Backend Behavior (`Windows`, `MacOS`, `Gtk`, `Qt`, `Swing`, `Android`, `IOS`) |
| :--- | :---: | :---: | :--- |
| **Text / Typography Elements** (`Label`, `Text`, `Heading`, `Badge`, `Blockquote`, `CodeBlock`, `MarkdownView`) | `true` | `false` | Honor all typography & text-color properties on `ComponentStyle` (`color` / `textColor`, `fontFamily`, `fontSize`, `fontWeight`, `fontStyle`, `lineHeight`, `letterSpacing`, `textAlign`, `textDecoration`, `textTransform`, `textOverflow`, `whiteSpace`, `wordBreak`) and `ComponentBootstrap` (`textColorType`, `fontSize`, `fontWeight`, `textAlignment`). |
| **Buttons, Inputs, Controls, & Standard Containers** (`Button`, `IconButton`, `ToggleButton`, `CheckBox`, `RadioButton`, `Switch`, `Select`, `DropdownButton`, `TextField`, `TextArea`, `RangeSlider`, `ProgressBar`, `Spinner`, `TabContainer`, `Tab`, `Container`, `Card`, `VStack`, `HStack`, `FlexBox`, etc.) | `false` | `false` | Aesthetic skinning and typography overrides on `ComponentStyle` (`backgroundColor`, `background`, `color`, `accentColor`, `borderColor`, `borderWidth`, `borderStyle`, `borderRadius`, `boxShadow`, `textShadow`, `filter`, `backdropFilter`, `fontFamily`, `fontSize`, `fontWeight`, `fontStyle`) are **silently ignored**. Standard components can **ONLY** be tinted via `BootstrapType` (`bootstrap.type`, `bootstrap.backgroundType`, `bootstrap.textColorType`, `bootstrap.borderType`, `bootstrap.outline`). Layout/geometry properties (`width`, `height`, `padding`, `margin`, `gap`, `flexGrow`, `cursor`) are honored on all components. |
| **`StyledContainer` & `StyledButton` (and `:extensions:alien`)** | `true` | `true` | Explicitly opt-in custom-styled components that honor full `ComponentStyle` aesthetic (`backgroundColor`, `background` gradients, `borderColor`, `borderWidth`, `borderStyle`, `borderRadius`, `boxShadow`, `opacity`) and typography properties across all 8 backends. |

### 6.3 Native Control Rendering (`BootstrapType.NONE`) vs. Offscreen Sprite + `ThemeConfig` Tint + Text Overlay (`BootstrapType != NONE`)

On native desktop and mobile targets, standard controls never fall back to flat custom-painted rectangles:
1. **When `BootstrapType == BootstrapType.NONE` (Default)**: Renders the **100% unmodified genuine native OS component** (`uxtheme.dll` / `comctl32.dll` / `user32.dll` on Windows, AppKit `NSButtonCell` / `NSControl` on macOS, GTK4 `GtkSnapshot` on GTK, Qt6 `QApplication::style()` `QStyle` on Qt, system Look-And-Feel on Swing, Material/AppCompat `defaultNativeBackground` on Android, and UIKit `UIControl` on iOS).
2. **When `BootstrapType != BootstrapType.NONE` (`PRIMARY`, `SECONDARY`, `SUCCESS`, `DANGER`, `WARNING`, `INFO`, `LIGHT`, `DARK`)**:
   - **Step 1 (Empty-Text Native Sprite)**: Renders the genuine native OS control **with NO text** into an offscreen ARGB pixel buffer (`DrawThemeBackground` on Win32, `NSButtonCell` with `title = ""` on macOS, `gtk_widget_snapshot` with empty label on GTK4, `QStyle::drawControl` with empty `QStyleOptionButton.text` on Qt6).
   - **Step 2 (`ThemeConfig` Luminance-Preserving Pixel Tint)**: Color-tints the offscreen native control pixels using [`DesktopControlTintRenderUtil.tintNativeControlArgbBuffer`](../desktop/nativeCommon/src/commonMain/kotlin/com/nexustools/genericui/desktop/nativecommon/DesktopControlTintRenderUtil.kt) and the active [`ThemeConfig`](../common/src/commonMain/kotlin/com/nexustools/genericui/style/theme/ThemeConfig.kt) / [`BootstrapColorPalette`](../common/src/commonMain/kotlin/com/nexustools/genericui/style/bootstrap/BootstrapColorPalette.kt) color while preserving native 3D bevels, borders, rounded corners, specular highlights, and hover/pressed/disabled states.
   - **Step 3 (Blit & Contrasting Text Overlay)**: Blits the tinted native control sprite onto the destination surface and renders the contrasting text label (`BootstrapColorPalette.resolveOnFillTextColor` / `ThemeConfig`) and icon cleanly on top afterward.

### 6.4 Modular Per-Component `*NativeImpl` + Type-Grouped `*RenderUtil` Architecture

Instead of monolithic tree renderers, every native desktop backend and the `JestElectricRunner` snapshot engine use a strictly modular **1-Class-Per-File** architecture:
- **`:desktop:nativeCommon`**: [`DesktopRenderableNativeImpl`](../desktop/nativeCommon/src/commonMain/kotlin/com/nexustools/genericui/desktop/nativecommon/DesktopRenderableNativeImpl.kt) (`renderNativeComponent(...)`) and [`DesktopControlTintRenderUtil`](../desktop/nativeCommon/src/commonMain/kotlin/com/nexustools/genericui/desktop/nativecommon/DesktopControlTintRenderUtil.kt).
- **`:desktop:windows`**: 36 per-component `Windows*NativeImpl` classes delegating to 12 type-grouped `Win32*RenderUtil` classes (`Win32RenderContext`, `Win32GdiPrimitiveRenderUtil`, `Win32ActionControlRenderUtil`, `Win32FormControlRenderUtil`, `Win32FeedbackRenderUtil`, `Win32TypographyRenderUtil`, `Win32SurfaceRenderUtil`, `Win32ContainerLayoutRenderUtil`, `Win32DockAndMdiRenderUtil`, `Win32NavigationAndShellRenderUtil`, `Win32DataAndLazyRenderUtil`, `Win32CanvasAndMediaRenderUtil`).
- **`:desktop:macos`, `:desktop:gtk`, `:desktop:qt`**: Per-component `Macos*NativeImpl` + `MacosControlRenderUtil`, `Gtk*NativeImpl` + `GtkControlRenderUtil`, and `Qt*NativeImpl` + `QtControlRenderUtil`.
- **`:tests:common`**: 12 modular `JestElectric*RenderUtil` classes (`JestElectricRenderContext`, `JestElectricPrimitiveRenderUtil`, `JestElectricControlTintRenderUtil`, `JestElectricActionControlRenderUtil`, `JestElectricFormControlRenderUtil`, `JestElectricFeedbackRenderUtil`, `JestElectricTypographyRenderUtil`, `JestElectricSurfaceRenderUtil`, `JestElectricContainerLayoutRenderUtil`, `JestElectricDockAndMdiRenderUtil`, `JestElectricNavigationAndShellRenderUtil`, `JestElectricDataAndLazyRenderUtil`).
