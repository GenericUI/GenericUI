# GenericUI Components, Fragments, Docking, & Styling Guide

> **Root Repository**: [**GenericUI (`../README.md`)**](../README.md) | **Master Docs Index**: [**`docs/README.md`**](./README.md)  
> **Related Guides**: [Fragments & Windows (`fragments-and-windows.md`)](./fragments-and-windows.md) · [Application (`application.md`)](./application.md) · [Resources (`resources.md`)](./resources.md) · [Platform Backends (`backends.md`)](./backends.md) · [Extensions (`extensions.md`)](./extensions.md)

This guide covers GenericUI's native widget catalog, helper fragments (`FragmentHostFragment`, `DockWorkspaceFragment`, `AdaptiveSplitTabFragment`), `FileBrowserDialog`, true viewport-window lazy views (`LazyList`, `LazyGrid`, `TreeView`), and Dual-Mode Bootstrap + SASS/CSS styling.

---

## 1. Complete Native Widget Catalog (`com.nexustools.genericui.component.*`)

All components inherit from `AbstractComponent` (or `Container` for layout parents) and resolve their platform `NativeImpl` lazily via **Deferred Upward `NativeImpl` Resolution**.

### 1.1 Layout & Navigation Containers
- **`VStack` / `HStack` (`VBox` / `HBox`)**: Vertical and horizontal linear flex containers with `SpacingScale` gap and alignment controls.
- **`Grid` / `Row` / `Col`**: Bootstrap 12-column responsive grid layout containers.
- **`FlowContainer` / `StackContainer` / `ScrollView` / `SplitPane`**: Wrapping flow layouts, Z-ordered stacks, hardware-accelerated scroll views, and resizable split panes.
- **`TabContainer` & `FragmentTabContainer` (`com.nexustools.genericui.component.tabs`)**: Native tabbed containers supporting `Tab`, `CloseableTab`, `BadgeTab`, `PinnedTab`, `EditableTab`, and custom `AbstractComponent` tab headers across all 8 backends.
- **`Navbar` / `BottomNavigationBar` / `Drawer` / `Accordion` / `Card`**: Navigation bars, mobile bottom tab bars, slide-out drawers, collapsible accordions, and elevated Bootstrap cards.
- **Pointer Enter/Leave & Lifecycle UI Timers**: Every `AbstractComponent` supports `onPointerEnter` and `onPointerLeave` callbacks across all 8 backends, paired with `:common`'s lifecycle-bound `UiTimer` (`setTimeout`, `setInterval`).

### 1.2 Typography, Feedback, & Media Display
- **`Heading` / `Text` / `Label` / `Badge`**: Typography components styled via `BootstrapType`, `FontWeight`, and `TextAlignment`.
- **`ProgressBar` / `Spinner` / `Alert` / `Tooltip` / `Popover` / `Divider`**: Native determinate/indeterminate progress bars, loading spinners, contextual alerts, tooltips, popovers, and dividers.
- **`Image` / `Icon` / `MarkdownView`**: Raster/vector image display (accepting `Resource.ResourceID` directly) and rich Markdown rendering.

### 1.3 Action & Form Controls
- **`Button` / `IconButton` / `ToggleButton` / `Dropdown`**: Native push, icon, toggle, and dropdown buttons.
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
- **`LazyGrid<T>`**: 2D virtualized grid with fixed or adaptive column counts.
- **`TreeView` & `TreeNode`**: Flattens expanded tree nodes into a virtualized visible node list so collapsed subtrees and off-screen nodes consume zero native widget handles.
- **`ViewHolderPool`**: Recycles detached item views (`onUnbind`) and re-binds them (`onBind`) as items enter `[firstActiveIndex .. lastActiveIndex]` across all 8 platforms (`RecyclerView` on `:mobile:android`, `UICollectionView`/`UITableView` on `:mobile:ios`, and `DesktopLazyViewportEngine` / virtualized DOM on desktop and web).
