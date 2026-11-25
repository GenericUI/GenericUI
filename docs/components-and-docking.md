# GenericUI Components, Fragments, Docking, & Styling Guide

> **Root Repository**: [**GenericUI (`../README.md`)**](../README.md) | **Master Docs Index**: [**`docs/README.md`**](./README.md)  
> **Related Guides**: [Fragments & Windows (`fragments-and-windows.md`)](./fragments-and-windows.md) · [Application (`application.md`)](./application.md) · [Resources (`resources.md`)](./resources.md) · [Platform Backends (`backends.md`)](./backends.md) · [Extensions (`extensions.md`)](./extensions.md)

This guide covers GenericUI's native widget catalog, helper fragments (`FragmentHostFragment`, `DockWorkspaceFragment`, `AdaptiveSplitTabFragment`), `FileBrowserDialog`, true viewport-window lazy views (`LazyList`, `LazyGrid`, `TreeView`), and Dual-Mode Bootstrap + SASS/CSS styling.

---

## 1. Complete Native Widget Catalog (`com.nexustools.genericui.component.*`)

All components inherit from `AbstractComponent` (or `Container` for layout parents) and resolve their platform `NativeImpl` lazily via **Deferred Upward `NativeImpl` Resolution**.

### 1.1 Layout, MDI, Table, & Navigation Containers
- **`VStack` / `HStack` (`VBox` / `HBox`) & `FlexBox`**: Vertical and horizontal linear flex containers with `SpacingScale` gap, alignment controls, and multi-line wrapping support (`FlexWrap.WRAP`, `FlexWrap.WRAP_REVERSE`, `FlexWrap.NOWRAP`). When `wrap = FlexWrap.WRAP` is set on `HStack` or `FlexBox`, child controls wrap cleanly onto multiple horizontal rows whenever their cumulative width exceeds the available container width.
- **`Grid` / `Row` / `Col`**: Bootstrap 12-column responsive grid layout containers.
- **`StyledContainer` (`com.nexustools.genericui.component.layout.StyledContainer`) & `StyledComponent` (`com.nexustools.genericui.component.surface.StyledComponent`)**: `StyledContainer` is a custom-styled container and `StyledComponent` (`StyledComponentNativeImpl`) is a pure stylesheet-driven non-container, non-button visual leaf for custom CSS/SCSS shapes and segments. Both explicitly honor full CSS/SCSS aesthetics (`backgroundColor`, `background` gradients, `borderColor`, `borderWidth`, `borderRadius`, `boxShadow`, `backdropFilter` / `setBackdropBlur(...)`, `opacity`, `pointer-events`, `transform`, `@keyframes`) across all 9 backends.
- **`Composite` (`com.nexustools.genericui.component.composite.Composite`)**: Extends `AbstractComponent` directly (intentionally **not** `AbstractContainer`) so custom multi-part widgets hide internal `compositeChildren` from consumers while rendering and styling uniformly across all 9 backends via `StyledContainerNativeImpl` with zero custom `*NativeImpl`.
- **`LayeredContainer` (`com.nexustools.genericui.component.layout.LayeredContainer`) & Proximal `position: relative` Parity**: Z-ordered multi-layer stack container supporting full-bleed and floating layers. Across `:web` and all 8 `:system` backends, `position: absolute` resolves against the **proximal ancestor with `position: relative`** (or positioned ancestor `position != static`, else the `Window` itself) with `top: 50%; transform: translate(0, -50%)`, `z-index` stacking, and `pointer-events: none` click-through.
- **`MdiContainer` & `MdiWindow` (`com.nexustools.genericui.component.mdi`)**: Multiple-Document Interface desktop container supporting floating, resizable, minimizable, and maximizable child `MdiWindow` frames, plus **`MdiContainer.tileWindows(columns: Int = 2, paddingPx: Int = 12, gapPx: Int = 12)`** and **`autoTileInitialWindows: Boolean`** (`true` by default) to automatically arrange non-positioned MDI windows into a clean, non-overlapping multi-column grid.
- **`Table` (`com.nexustools.genericui.component.data.Table`)**: Structured data table supporting headers, rows, striped/bordered/hover styles, and proportional column width weighting via **`Table.setColumnWeights(vararg weights: Float)`** and **`Table.getColumnWeights(): FloatArray`** so wide content columns (e.g. description or regex pattern columns) receive proportional horizontal space instead of equal-width truncation.
- **`FlowContainer` / `StackContainer` / `ScrollView` / `SplitPane`**: Wrapping flow layouts, Z-ordered stacks, hardware-accelerated scroll views, and resizable split panes.
- **`TabContainer` & `FragmentTabContainer` (`com.nexustools.genericui.component.tabs`)**: Native tabbed containers supporting `Tab`, `CloseableTab`, `BadgeTab`, `PinnedTab`, `EditableTab`, and custom `AbstractComponent` tab headers across all 9 backends.
- **`Navbar` / `BottomNavigationBar` / `Drawer` / `Accordion` / `Card`**: Navigation bars, mobile bottom tab bars, slide-out drawers, collapsible accordions, and elevated Bootstrap cards.
- **Pointer Enter/Leave & Lifecycle UI Timers**: Every `AbstractComponent` supports `onPointerEnter` and `onPointerLeave` callbacks across all 9 backends, paired with `:common`'s lifecycle-bound `UiTimer` (`setTimeout`, `setInterval`).

### 1.2 Typography, Feedback, & Media Display
- **`Heading` / `Text` / `Label` / `Badge` / `Blockquote` / `CodeBlock` / `MarkdownView`**: Typography components that honor full typography and text-color properties on `ComponentStyle` (`color` / `textColor`, `fontFamily`, `fontSize`, `fontWeight`, `fontStyle`, `lineHeight`, `letterSpacing`, `textAlign`, `textDecoration`, `textTransform`, `textOverflow`, `whiteSpace`, `wordBreak`) and `ComponentBootstrap` (`textColorType`, `fontSize`, `fontWeight`, `textAlignment`) across all 9 backends.
- **`ProgressBar` / `Spinner` / `Alert` / `Tooltip` / `Popover` / `Divider`**: Native determinate/indeterminate progress bars, loading spinners, contextual alerts, tooltips, popovers, and dividers.
- **`Image` / `Icon`**: Raster/vector image display (accepting `Resource.ResourceID` directly) and vector icons.

### 1.3 Action & Form Controls
- **`Button` / `IconButton` / `ToggleButton` / `FloatingActionButton`**: Native push, icon, toggle, and floating action buttons (`Button` and `Button` subclasses are the **ONLY** control components supporting `BootstrapType`: mounting unmodified genuine OS push buttons when `(type == null || type == BootstrapType.NONE) && !outline`, swapping live via `DesktopButtonNativeImpl` on native desktop to custom Bootstrap button widgets when `BootstrapType != NONE` or `outline == true`, and tinting bevel/drawable graphics on Swing and mobile).
- **`DropdownButton` / `SplitDropdownButton`**: Native dropdown / split-dropdown controls (NOT `Button` subclasses; MUST NOT support `BootstrapType`; `SplitDropdownButton` marks `data-control-variant="split-dropdown"`).
- **`StyledButton` (`com.nexustools.genericui.component.action.StyledButton`)**: Custom-styled `Button` subclass supporting both `BootstrapType` and full CSS/SCSS aesthetic (`backgroundColor`, `background` gradients, `borderColor`, `borderWidth`, `borderRadius`, `boxShadow`, `backdropFilter` / `setBackdropBlur(...)`) and typography overrides across all 9 backends.
- **`StyledSlider` (`com.nexustools.genericui.component.form.StyledSlider`)**: Pure `Composite` range slider (zero custom `*NativeImpl`; MUST NOT support `BootstrapType`) composing a clickable track `StyledButton` (`.styled-slider-track`, `z-index: 1`), `$accent`-colored progress `StyledComponent` (`.styled-slider-progress`, `z-index: 3`, `pointer-events: none`), and draggable knob `StyledButton` (`.styled-slider-knob`, `z-index: 4`, centered via `top: 50%; transform: translate(-50%, -50%)`), styled in `:common` `style.scss` + `theme.scss` with only runtime-dynamic fill/knob `%` updated in Kotlin.
- **`MenuBar` / `Menu` / `MenuItem` / `ContextMenu`**: Window menu bars, cascading submenus, keyboard shortcuts, and popup context menus.
- **`TitleBar` (`com.nexustools.genericui.window.TitleBar`)**: Window titlebar instruction descriptor supporting `leadingComponents`, `centerComponents`, `trailingComponents`, `embedMenuBar`, and single-placement deduplication via `TitleCapabilities.canEmbedComponentsInTitleBar`.
- **`TextInput` / `TextArea` / `PasswordInput` / `NumberInput` / `Select` / `ComboBox` / `Checkbox` / `Radio` / `Switch` / `RangeSlider` / `ColorPicker` / `DatePicker` / `TimePicker` / `SearchField`**: Native form inputs with two-way validation and change listeners (`ComboBox`, `DatePicker`, `TimePicker`, `ColorPicker`, `SearchField`, and `NumberField` stamp `data-control-variant` so native factories instantiate specialized OS controls cleanly).

---

## 2. Helper Fragments (`com.nexustools.genericui.component.fragment.helper`)

For complete coverage of `@MainAppFragment`, `@AppFragment`, `AppTarget` specificity resolution, `FragmentBundle` CLI arguments, `IFragment`, `IWindow`, and `TitleCapabilities`, see [**Fragments, Windows, & Navigation (`fragments-and-windows.md`)**](./fragments-and-windows.md).

### A. `FragmentHostFragment` & `FragmentHostSystem`
Multi-screen host `Fragment` implementing `FragmentHost`:
- Hosts child `Fragment`s (`setRootFragment`, `pushFragment`, `replaceFragment`, `popBackStack`, `goForward`, `registerRoute`, `navigateToRoute`, `resolveInitialDeepLink`).
- Supports customizable transitions via `FragmentTransitionSpec` (`None`, `Crossfade`, `FadeExpandShrink`, `SlideHorizontal`, or custom callbacks).
- On `:web`, synchronizes routes with `window.location.hash` (`#hash`) and persists each fragment's `FragmentBundle` in `window.history.state` so browser Back and Forward buttons work natively.
- Provides optional Back, Forward, and custom toolbar components (`FragmentNavigationChromeConfig`) embedded in the window's `TitleBar` when `titleCapabilities.canEmbedComponentsInTitleBar` is `true`, or in a fallback `Navbar` below the titlebar when enabled.

### B. `DockWorkspaceFragment`, `DockWorkspace`, & `PanelWindow`
Full IDE and creative-suite docking workspace inspired by **GIMP** and **JetBrains IntelliJ**:
- **GIMP Single-Window Mode (`DockWorkspaceMode.SINGLE_WINDOW`)**: All toolboxes, layer trees, property inspectors, and timelines dock into resizable `LEFT`, `RIGHT`, `TOP`, `BOTTOM`, and tabbed `CENTER` `DockArea`s inside a single `Window`.
- **GIMP Multi-Window Mode (`DockWorkspaceMode.MULTI_WINDOW`)**: Detached dock panels ([`PanelWindow`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/dock/PanelWindow.kt), defaulting to `isAlwaysOnTop = true` and `isAlwaysOnTopOfOwner = true`) are promoted into independent native OS utility windows coordinated by `DesktopMultiWindowDockCoordinator`. Top-level `Window` instances also support `setAlwaysOnTop(...)` and windowed-only client-viewport `setAspectRatio(...)` clamping (`WindowState.NORMAL && !isFullscreen()`).

### C. `AdaptiveSplitTabFragment`
Responsive multi-pane fragment that adapts across desktop/tablet multi-column split views, phone portrait tabbed views, and phone landscape fullscreen viewports.

### D. `TabbedFragment` (`TabbedChromeMode`)
Multi-tab document and browser host `Fragment` where every open tab (`TabbedDocumentTab`) owns its own independent `FragmentHostSystem` back/forward navigation stack (`maxHistoryPerTab = 50` by default):
- **`TabbedChromeMode.AUTO` (Default)**: Automatically renders horizontal desktop/tablet browser tabs (`DESKTOP_BROWSER_TABS`) on viewports $\ge 600\text{dp}$ and switches to a Chrome/Firefox-style compact mobile tab switcher pill (`MOBILE_SWITCHER_PILL`) with a `[N]` tab-count badge and full-screen tab switcher overlay on viewports $< 600\text{dp}$.

---

## 3. Native Dialogs & File/Folder Browser (`com.nexustools.genericui.component.dialog`)

GenericUI provides standard modal dialogs (`DialogWindow`, `Dialogs.alert`, `Dialogs.confirm`, `Dialogs.prompt`) and a cross-platform **Native File & Folder Browser (`FileBrowserDialog`)**:

### `FileBrowserDialog` Modes (`FileBrowserMode`)
- `OPEN_SINGLE_FILE`: Select a single existing file.
- `OPEN_MULTIPLE_FILES`: Select one or more existing files.
- `SAVE_FILE`: Choose a destination file path and name for saving/exporting.
- `SELECT_FOLDER`: Select a directory/folder.

| Platform Backend | Native Implementation Mechanism |
| :--- | :--- |
| **Desktop (`:desktop`) & Apple Mobile (`:mobile` `iosMain` iOS/iPadOS)** | Native OS file/folder dialog (`IFileOpenDialog`/`IFileSaveDialog` with `FOS_PICKFOLDERS`, `NSOpenPanel`/`NSSavePanel`, `GtkFileDialog`, KDE `KFileDialog`/`QFileDialog`, `JFileChooser`, `UIDocumentPickerViewController`). |
| **Web (`:web`)** | Dynamically creates an invisible `<input type="file">` element (`multiple`, `webkitdirectory`, `accept`), triggers `.click()`, and resolves selected files into `SelectedFile` entries. |
| **Android (`:mobile` `androidMain`)** | Launches the native Android Storage Access Framework picker (`Intent.ACTION_OPEN_DOCUMENT`, `ACTION_CREATE_DOCUMENT`, or `ACTION_OPEN_DOCUMENT_TREE`) via `GenericUiFragmentActivity`. |

---

## 4. True Lazy Views & Viewport Recycling (`com.nexustools.genericui.component.lazy`)

- **`LazyList<T>` (`LazyColumn<T>`, `LazyRow<T>`)**: 1D vertical or horizontal virtualized scrolling list.
- **`LazyGrid<T>` (`minColumnWidthPx`)**: 2D virtualized grid with fixed `columns` count or responsive auto-fit column sizing via **`minColumnWidthPx: Int`** (`0` by default; when `> 0`, dynamically computes `effectiveCols = min(columns, max(1, availableWidth / minColumnWidthPx))` so grid cells never squash below their readable minimum width).
- **`TreeView` & `TreeNode`**: Flattens expanded tree nodes into a virtualized visible node list so collapsed subtrees and off-screen nodes consume zero native widget handles.
- **`ViewHolderPool`**: Recycles detached item views (`onUnbind`) and re-binds them (`onBind`) as items enter `[firstActiveIndex .. lastActiveIndex]` across all 9 platforms (`RecyclerView` on Android, `UICollectionView`/`UITableView` on iOS & iPadOS, and `DesktopLazyViewportEngine` / virtualized DOM on desktop and web).

---

## 5. Strongly-Typed `ComponentStyle` & `ComponentBootstrap`, System Backend Restraints, & Native Control Tinting

### 5.1 `style.scss`-First Styling, Zero Direct Component Styles in Kotlin, & `theme.scss` Inheritance
- **Zero Direct Component Styles in Kotlin (`style.scss`-First)**: Static visuals, interactive state visuals (`:hover`, `:focus`, `:active`, `:disabled`), layouts (`position`, `top`, `transform`, `z-index`, `pointer-events`, `cursor`), transitions, and `@keyframes` animations MUST live in `src/commonMain/resources/styles/style.scss` / `theme.scss` for `:common`, each extension, GenericUI core, and consumer applications (`episode-caster/receiver`, `episode-caster/client`). Only genuinely runtime-dynamic per-frame/per-value attributes (such as `StyledSlider` fill/knob `%`, `SeekBar` preload `%`, or scrub bubble `left`) may be set directly in Kotlin. Backends and `:system` contain **zero** class/id/app-specific fast-paths.
- **Raw Library `theme.scss` Templates vs. Consumer-App-Only Compilation**: GenericUI libraries (`:common` and `:extensions:*`) are prebuilt/generic and **NEVER compile or integrate resulting `theme.scss` code into library `.jar`/`.klib` artifacts themselves**; they package **only raw uncompiled `src/commonMain/resources/styles/theme.scss` templates** in artifact metadata (`META-INF/genericui/themes/<scopeId>.scss` and `META-INF/genericui/module-resources/<scopeId>.json`) referencing `$primary`, `$accent`, `$surface`, `rgba($accent, ...)`, etc. **Only the consumer application's `:gradle-plugin` build** extracts those upstream templates, substitutes its `genericui { theme { ... } }` settings from `build.gradle.kts`, and integrates `GenericUiGeneratedThemeStyleSheet` (`:system`) and `gui/genericui-theme.css` (`:web`) solely into the consumer application (with `ThemeStyleSheetRegistry` handling runtime `app.setThemeConfig(...)` updates).
- **`.hidden` Overlay Fade & `:system` `Animator` `@keyframes`**: Fading overlays (`FadingOverlay` / `AutoHideController`) toggle solely `"hidden"` (`opacity: 0; pointer-events: none; cursor: none;`) + fade-in/out `@keyframes` in `style.scss`, driven by VBlank-synced `Animator` (`SystemCssAnimationController`) on `:system` and native browser CSS on `:web`.

### 5.2 Zero String-Keyed Style APIs
`GenericUI` **never** exposes string-keyed style APIs (`setStyleProperty(String, String)`, `getStyleProperty`, `removeStyleProperty`, `clearStyleProperties`, `inlineStyle`, `setRawDeclaration`, `getRawDeclaration`, `fromDeclarations`, `rawDeclarations` are prohibited). All styles are evaluated through strongly-typed [`ComponentStyle`](../common/src/commonMain/kotlin/com/nexustools/genericui/style/css/ComponentStyle.kt) (`component.getStyle()` or `:extensions:ui:dsl` `style { ... }` for dynamic attributes) and [`ComponentBootstrap`](../common/src/commonMain/kotlin/com/nexustools/genericui/style/bootstrap/ComponentBootstrap.kt) (`component.getBootstrap()` or `:extensions:ui:dsl` `bootstrap { ... }`), backed by two 64-bit `CssProperty` bitmasks (`activePropertiesBitmaskLow` / `High`, `importantBitmaskLow` / `High`) so non-web backends operate with zero string formatting or map allocations, while `:web` translates `ComponentStyle` and `ComponentBootstrap` transparently to DOM CSS strings via [`WebCssStyleTranslator`](../web/src/commonMain/kotlin/com/nexustools/genericui/web/style/WebCssStyleTranslator.kt).

### 5.3 System Backend Typography, Strictly `Button`-Only Control `BootstrapType`, & Aesthetic Style Restraints
On all 8 non-web system targets (`Windows`, `MacOS`, `Gtk`, `Kde`, `Swing`, `Android`, `IOS`, `IPadOS`), standard UI controls mount genuine OS widgets directly and cannot have their aesthetic styles redesigned to look alien without `:extensions:ui:alien` (or `StyledComponent`, `StyledContainer`, `StyledButton`, or `Composite` / `StyledSlider`):
- **Text / Typography Elements (`Label`, `Text`, `Heading`, `Blockquote`, `CodeBlock`, `MarkdownView`)**: Honor typography and text-color properties (`color` / `textColor`, `fontFamily`, `fontSize`, `fontWeight`, `fontStyle`, `lineHeight`, `letterSpacing`, `textAlign`, `textDecoration`, `textTransform`, `textOverflow`, `whiteSpace`, `wordBreak`, plus `bootstrap.textColorType`, `bootstrap.fontSize`, `bootstrap.fontWeight`, `bootstrap.textAlignment`).
- **Strictly `Button`-Only Control `BootstrapType` (`Button` & `Button` Subclasses ONLY: `Button`, `IconButton`, `ToggleButton`, `FloatingActionButton`, `StyledButton`)**: Among control components, **ONLY `Button` and subclasses of `Button`** support `BootstrapType` (`bootstrap.type`, `bootstrap.backgroundType`, `bootstrap.outline`). Standard native buttons mount unmodified push buttons when `BootstrapType.NONE && !outline` and swap live on native desktop to custom Bootstrap button widgets (or tinted graphics on Swing/mobile) when typed/outlined; `StyledButton` honors both `BootstrapType` and full CSS/SCSS styling.
- **Non-`Button` Controls & Standard Containers (`DropdownButton`, `SplitDropdownButton`, `ProgressBar`, `Spinner`, `StyledSlider`, `Composite`, `CheckBox`, `RadioButton`, `Switch`, `Select`, `ComboBox`, `TextField`, `TextArea`, `RangeSlider`, `Slider`, `TabContainer`, `Tab`, `ScrollView`, `Container`, `VStack`, `HStack`, etc.)**: **`DropdownButton`, `SplitDropdownButton`, `ProgressBar`, `Spinner`, `StyledSlider`, `Composite`, and all other non-`Button` controls MUST NOT support `BootstrapType`**, and standard controls/containers ignore aesthetic `ComponentStyle` skinning and typography overrides on system backends.
- **Bootstrap-Specific Non-Control Classes (`Badge`, `Alert`, `Banner`, `Toast`, `Card`, `ListGroup`, `ListGroupItem`, `Jumbotron`, `Well`)**: DO support semantic **`BootstrapType`** (`type`, `backgroundType`, `textColorType`, `borderType`) across all **9 backends**, mounting custom native semantic widgets styled via `:system`'s `SemanticSurfaceStyleResolver` (`SemanticSurfacePaintSpec`).
- **`StyledComponent`, `StyledContainer`, `StyledButton`, `Composite`, & `StyledSlider` (and `:extensions:ui:alien`)**: Honor full CSS/SCSS styling across all 9 backends (`StyledSlider` and `Composite` do NOT support `BootstrapType`), driven by `:system`'s `StyledWidgetVisualController`, `GradientStopNormalizer`, `:apple`/`linux/style`/`:jvm` surface helpers, and all 10 `CssFilter` / `backdrop-filter` ops (`CssFilterColorMatrix` + [`FastBackdropBlurKernel`](../common/src/commonMain/kotlin/com/nexustools/genericui/style/css/FastBackdropBlurKernel.kt) CPU fallback) under [`AestheticStylePolicy`](../common/src/commonMain/kotlin/com/nexustools/genericui/style/policy/AestheticStylePolicy.kt) (`supportsBackdropFilterBlur`).
