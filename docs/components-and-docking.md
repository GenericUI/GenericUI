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
- **`StyledContainer` (`com.nexustools.genericui.component.container.StyledContainer`)**: Custom-styled container subclass that explicitly opts into honoring full `ComponentStyle` aesthetic properties (`backgroundColor`, `background` gradients, `borderColor`, `borderWidth`, `borderRadius`, `boxShadow`, `opacity`) across all 8 backends (including all 7 non-web system targets where standard `Container`s ignore aesthetic skinning overrides).
- **`MdiContainer` & `MdiWindow` (`com.nexustools.genericui.component.mdi`)**: Multiple-Document Interface desktop container supporting floating, resizable, minimizable, and maximizable child `MdiWindow` frames, plus **`MdiContainer.tileWindows(columns: Int = 2, paddingPx: Int = 12, gapPx: Int = 12)`** and **`autoTileInitialWindows: Boolean`** (`true` by default) to automatically arrange non-positioned MDI windows into a clean, non-overlapping multi-column grid.
- **`Table` (`com.nexustools.genericui.component.data.Table`)**: Structured data table supporting headers, rows, striped/bordered/hover styles, and proportional column width weighting via **`Table.setColumnWeights(vararg weights: Float)`** and **`Table.getColumnWeights(): FloatArray`** so wide content columns (e.g. description or regex pattern columns) receive proportional horizontal space instead of equal-width truncation.
- **`FlowContainer` / `StackContainer` / `ScrollView` / `SplitPane`**: Wrapping flow layouts, Z-ordered stacks, hardware-accelerated scroll views, and resizable split panes.
- **`TabContainer` & `FragmentTabContainer` (`com.nexustools.genericui.component.tabs`)**: Native tabbed containers supporting `Tab`, `CloseableTab`, `BadgeTab`, `PinnedTab`, `EditableTab`, and custom `AbstractComponent` tab headers across all 8 backends.
- **`Navbar` / `BottomNavigationBar` / `Drawer` / `Accordion` / `Card`**: Navigation bars, mobile bottom tab bars, slide-out drawers, collapsible accordions, and elevated Bootstrap cards.
- **Pointer Enter/Leave & Lifecycle UI Timers**: Every `AbstractComponent` supports `onPointerEnter` and `onPointerLeave` callbacks across all 8 backends, paired with `:common`'s lifecycle-bound `UiTimer` (`setTimeout`, `setInterval`).

### 1.2 Typography, Feedback, & Media Display
- **`Heading` / `Text` / `Label` / `Badge` / `Blockquote` / `CodeBlock` / `MarkdownView`**: Typography components that honor full typography and text-color properties on `ComponentStyle` (`color` / `textColor`, `fontFamily`, `fontSize`, `fontWeight`, `fontStyle`, `lineHeight`, `letterSpacing`, `textAlign`, `textDecoration`, `textTransform`, `textOverflow`, `whiteSpace`, `wordBreak`) and `ComponentBootstrap` (`textColorType`, `fontSize`, `fontWeight`, `textAlignment`) across all 8 backends.
- **`ProgressBar` / `Spinner` / `Alert` / `Tooltip` / `Popover` / `Divider`**: Native determinate/indeterminate progress bars, loading spinners, contextual alerts, tooltips, popovers, and dividers.
- **`Image` / `Icon`**: Raster/vector image display (accepting `Resource.ResourceID` directly) and vector icons.

### 1.3 Action & Form Controls
- **`Button` / `IconButton` / `ToggleButton` / `Dropdown`**: Native push, icon, toggle, and dropdown buttons (rendered as genuine OS controls when `BootstrapType.NONE`, or offscreen-tinted native sprites + contrasting text overlay when `BootstrapType != NONE`).
- **`StyledButton` (`com.nexustools.genericui.component.action.StyledButton`)**: Custom-styled button subclass that explicitly opts into honoring full `ComponentStyle` aesthetic (`backgroundColor`, `background` gradients, `borderColor`, `borderWidth`, `borderRadius`, `boxShadow`) and typography overrides across all 8 backends.
- **`MenuBar` / `Menu` / `MenuItem` / `ContextMenu`**: Window menu bars, cascading submenus, keyboard shortcuts, and popup context menus.
- **`TitleBar` (`com.nexustools.genericui.window.TitleBar`)**: Window titlebar instruction descriptor supporting `leadingComponents`, `centerComponents`, `trailingComponents`, `embedMenuBar`, and single-placement deduplication via `TitleCapabilities.canEmbedComponentsInTitleBar`.
- **`TextInput` / `TextArea` / `PasswordInput` / `NumberInput` / `Select` / `Checkbox` / `Radio` / `Switch` / `RangeSlider` / `ColorPicker` / `DatePicker`**: Native form inputs with two-way validation and change listeners.

---

## 2. Helper Fragments (`com.nexustools.genericui.component.fragment.helper`)

For complete coverage of `@MainAppFragment`, `@AppFragment`, `AppTarget` specificity resolution, `FragmentBundle` CLI arguments, `IFragment`, `IWindow`, and `TitleCapabilities`, see [**Fragments, Windows, & Navigation (`fragments-and-windows.md`)**](./fragments-and-windows.md).

### A. `FragmentHostFragment` & `FragmentHostSystem`
Multi-screen host `Fragment` implementing `FragmentHost`:
- Hosts child `Fragment`s (`setRootFragment`, `pushFragment`, `replaceFragment`, `popBackStack`, `goForward`, `registerRoute`, `navigateToRoute`, `resolveInitialDeepLink`).
- Supports customizable transitions via `FragmentTransitionSpec` (`None`, `Crossfade`, `FadeExpandShrink`, `SlideHorizontal`, or custom callbacks).
- On `:web`, synchronizes routes with `window.location.hash` (`#hash`) and persists each fragment's `FragmentBundle` in `window.history.state` so browser Back and Forward buttons work natively.
- Provides optional Back, Forward, and custom toolbar components (`FragmentNavigationChromeConfig`) embedded in the window's `TitleBar` when `titleCapabilities.canEmbedComponentsInTitleBar` is `true`, or in a fallback `Navbar` below the titlebar when enabled.

### B. `DockWorkspaceFragment` & `DockWorkspace`
Full IDE and creative-suite docking workspace inspired by **GIMP** and **JetBrains IntelliJ**:
- **GIMP Single-Window Mode (`DockWorkspaceMode.SINGLE_WINDOW`)**: All toolboxes, layer trees, property inspectors, and timelines dock into resizable `LEFT`, `RIGHT`, `TOP`, `BOTTOM`, and tabbed `CENTER` `DockArea`s inside a single `Window`.
- **GIMP Multi-Window Mode (`DockWorkspaceMode.MULTI_WINDOW`)**: Detached dock panels (`PanelWindow`) are promoted into independent native OS utility windows coordinated by `DesktopMultiWindowDockCoordinator`.

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
| **Desktop (`:desktop:*`) & `:mobile:ios`** | Native OS file/folder dialog (`IFileOpenDialog`/`IFileSaveDialog` with `FOS_PICKFOLDERS`, `NSOpenPanel`/`NSSavePanel`, `GtkFileDialog`, `QFileDialog`, `JFileChooser`, `UIDocumentPickerViewController`). |
| **Web (`:web`)** | Dynamically creates an invisible `<input type="file">` element (`multiple`, `webkitdirectory`, `accept`), triggers `.click()`, and resolves selected files into `SelectedFile` entries. |
| **Android (`:mobile:android`)** | Launches the native Android Storage Access Framework picker (`Intent.ACTION_OPEN_DOCUMENT`, `ACTION_CREATE_DOCUMENT`, or `ACTION_OPEN_DOCUMENT_TREE`) via `GenericUiFragmentActivity`. |

---

## 4. True Lazy Views & Viewport Recycling (`com.nexustools.genericui.component.lazy`)

- **`LazyList<T>` (`LazyColumn<T>`, `LazyRow<T>`)**: 1D vertical or horizontal virtualized scrolling list.
- **`LazyGrid<T>` (`minColumnWidthPx`)**: 2D virtualized grid with fixed `columns` count or responsive auto-fit column sizing via **`minColumnWidthPx: Int`** (`0` by default; when `> 0`, dynamically computes `effectiveCols = min(columns, max(1, availableWidth / minColumnWidthPx))` so grid cells never squash below their readable minimum width).
- **`TreeView` & `TreeNode`**: Flattens expanded tree nodes into a virtualized visible node list so collapsed subtrees and off-screen nodes consume zero native widget handles.
- **`ViewHolderPool`**: Recycles detached item views (`onUnbind`) and re-binds them (`onBind`) as items enter `[firstActiveIndex .. lastActiveIndex]` across all 8 platforms (`RecyclerView` on `:mobile:android`, `UICollectionView`/`UITableView` on `:mobile:ios`, and `DesktopLazyViewportEngine` / virtualized DOM on desktop and web).

---

## 5. Strongly-Typed `ComponentStyle` & `ComponentBootstrap`, System Backend Restraints, & Native Control Tinting

### 5.1 Zero String-Keyed Style APIs
`GenericUI` **never** exposes string-keyed style APIs (`setStyleProperty(String, String)`, `getStyleProperty`, `removeStyleProperty`, `clearStyleProperties`, `inlineStyle`, `setRawDeclaration`, `getRawDeclaration`, `fromDeclarations`, `rawDeclarations` are prohibited). All styles are configured through strongly-typed [`ComponentStyle`](../common/src/commonMain/kotlin/com/nexustools/genericui/style/css/ComponentStyle.kt) (`component.getStyle()` or `:extensions:dsl` `style { ... }`) and [`ComponentBootstrap`](../common/src/commonMain/kotlin/com/nexustools/genericui/style/bootstrap/ComponentBootstrap.kt) (`component.getBootstrap()` or `:extensions:dsl` `bootstrap { ... }`), backed by two 64-bit `CssProperty` bitmasks (`activePropertiesBitmaskLow` / `High`, `importantBitmaskLow` / `High`) so non-web backends operate with zero string formatting or map allocations, while `:web` translates `ComponentStyle` and `ComponentBootstrap` transparently to DOM CSS strings via [`WebCssStyleTranslator`](../web/src/commonMain/kotlin/com/nexustools/genericui/web/WebCssStyleTranslator.kt).

### 5.2 System Backend Typography vs. Aesthetic Style Restraints
On all 7 non-web system targets (`:desktop:windows`, `:desktop:macos`, `:desktop:gtk`, `:desktop:qt`, `:desktop:swing`, `:mobile:android`, `:mobile:ios`), standard UI controls cannot have their aesthetic styles redesigned to look alien without `:extensions:alien` (or `StyledContainer` / `StyledButton`):
- **Text / Typography Elements (`Label`, `Text`, `Heading`, `Badge`, `Blockquote`, `CodeBlock`, `MarkdownView`)**: Honor typography and text-color properties (`color` / `textColor`, `fontFamily`, `fontSize`, `fontWeight`, `fontStyle`, `lineHeight`, `letterSpacing`, `textAlign`, `textDecoration`, `textTransform`, `textOverflow`, `whiteSpace`, `wordBreak`, plus `bootstrap.textColorType`, `bootstrap.fontSize`, `bootstrap.fontWeight`, `bootstrap.textAlignment`).
- **Buttons, Inputs, Controls, & Standard Containers (`Button`, `IconButton`, `ToggleButton`, `CheckBox`, `RadioButton`, `Switch`, `Select`, `DropdownButton`, `TextField`, `TextArea`, `RangeSlider`, `ProgressBar`, `Spinner`, `TabContainer`, `Tab`, `Container`, `Card`, etc.)**: Aesthetic skinning and typography overrides on `ComponentStyle` (`backgroundColor`, `background`, `color`, `accentColor`, `borderColor`, `borderWidth`, `borderStyle`, `borderRadius`, `boxShadow`, `textShadow`, `filter`, `backdropFilter`, `fontFamily`, `fontSize`, `fontWeight`, `fontStyle`) are **silently ignored** on system backends. Standard components can **ONLY** be tinted via `BootstrapType` (`bootstrap.type`, `bootstrap.backgroundType`, `bootstrap.textColorType`, `bootstrap.borderType`, `bootstrap.outline`).
- **`StyledContainer` & `StyledButton` (and `:extensions:alien`)**: Honor full `ComponentStyle` aesthetic and typography properties across all 8 backends.
