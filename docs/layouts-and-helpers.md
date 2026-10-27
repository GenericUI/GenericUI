# GenericUI Hybrid Responsive Layouts & Fragment Helpers Guide (`layouts-and-helpers.md`)

> **Root Repository**: [**GenericUI (`../README.md`)**](../README.md) | **Master Docs Index**: [**`docs/README.md`**](./README.md)  
> **Related Guides**: [Layouts & Hybrid UI Overview (`layouts-and-hybrid-ui.md`)](./layouts-and-hybrid-ui.md) · [Components & Docking (`components-and-docking.md`)](./components-and-docking.md) · [Fragments & Windows (`fragments-and-windows.md`)](./fragments-and-windows.md) · [Application & Theming (`application.md`)](./application.md) · [Extensions & DSL (`extensions.md`)](./extensions.md)

GenericUI provides **7 first-party Hybrid Responsive Layout Components** (`com.nexustools.genericui.component.layout` & `com.nexustools.genericui.component.dock`) and **7 matching Fragment Helper classes** (`com.nexustools.genericui.component.fragment.helper`) that adapt automatically across **Desktop (`Windows`, `MacOS`, `Gtk`, `Qt`, `Swing`)**, **Tablet (`Android` & `IOS` with `smallestScreenWidthDp >= 600`)**, **Small-Screen Mobile Phones (`smallestScreenWidthDp < 600`)**, and **Responsive Web Viewports (`:web`)**.

Every layout helper works with both:
1. **Standard Kotlin APIs** in `:common` (configured via `component.getBootstrap()` and `component.getStyle()`).
2. **Declarative Builders** in `:extensions:dsl` ([`HybridLayoutDsl.kt`](../extensions/dsl/src/commonMain/kotlin/com/nexustools/genericui/ext/dsl/HybridLayoutDsl.kt) and [`DslStyleAndBootstrapBuilders.kt`](../extensions/dsl/src/commonMain/kotlin/com/nexustools/genericui/ext/dsl/DslStyleAndBootstrapBuilders.kt) with `bootstrap { ... }`, `style { ... }`, and `important { ... }`).

---

## 1. Shared Breakpoint & Layout Mode Architecture (`HybridBreakpointSpec` & `HybridLayoutMode`)

All 7 hybrid layout helpers share [`HybridBreakpointSpec`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/layout/HybridBreakpointSpec.kt) and [`HybridLayoutMode`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/layout/HybridLayoutMode.kt) so device posture (`smallestScreenWidthDp` on Android/iOS) and live window/viewport dimensions (`viewportWidthDp` / `viewportHeightDp` on Desktop and Web) evaluate identically across all 8 backends.

### 1.1 `HybridBreakpointSpec` Thresholds

| Property | Default (`dp`) | Purpose |
| :--- | :--- | :--- |
| `compactDrawerBreakpointDp` | `600` | Threshold below which `DockWorkspace` and `DockWorkspaceFragment` convert `LEFT`, `RIGHT`, `TOP`, and `BOTTOM` dock areas into off-canvas edge swipe-out drawer overlays. |
| `tabletSmallestWidthDp` | `600` | `smallestScreenWidthDp` threshold distinguishing small-screen mobile phones (`< 600dp`) from tablets (`>= 600dp`) on `:mobile:android` and `:mobile:ios`. |
| `mediumBreakpointDp` | `600` | Compact-to-medium viewport width threshold for `ResponsiveMasterDetailLayout`, `AdaptiveNavigationScaffold`, and `ResponsiveBentoGridLayout`. |
| `expandedNavigationBreakpointDp` | `840` | Medium-to-expanded threshold where `AdaptiveNavigationScaffold` promotes its vertical `NavigationRail` into a full `Sidebar`. |
| `columnBreakpointDp` | `900` | Viewport width threshold where `HybridColumnLayout` and `HybridSplitNavigationLayout` transition from `HybridLayoutMode.SINGLE` to `HybridLayoutMode.COLUMNS`. |
| `extraWideBreakpointDp` | `1200` | Extra-wide threshold where `ResponsiveMasterDetailLayout` reveals its 3rd inline `Inspector` pane and `ResponsiveBentoGridLayout` applies `colXl` column spans. |

### 1.2 `HybridLayoutMode` States

[`HybridLayoutMode`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/layout/HybridLayoutMode.kt) defines the 4 active presentation states of a hybrid multi-column container:
- **`HybridLayoutMode.SINGLE`**: Compact single-column flow (stacked or tabbed) used on phones, tablet portrait, and narrow desktop/web viewports (`< columnBreakpointDp`).
- **`HybridLayoutMode.COLUMNS`**: Side-by-side Bootstrap 12-column grid layout used on tablet landscape, wide web viewports, and desktops (`>= columnBreakpointDp`).
- **`HybridLayoutMode.FULLSCREEN`**: Full-bleed presentation of the target primary column (`fullscreenTargetColumnIndex`, defaulting to `0`) while hiding secondary columns and navigation chrome.
- **`HybridLayoutMode.PICTURE_IN_PICTURE`**: Compact Picture-in-Picture (PiP) presentation state.

---

## 2. Overview Matrix of All 7 Hybrid Layout & Fragment Helper Pairs

| # | Layout Component | Fragment Helper | Desktop / Wide Web / Tablet Behavior | Small-Screen Mobile / Narrow Web Behavior |
| :--- | :--- | :--- | :--- | :--- |
| **1** | [`HybridColumnLayout`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/layout/HybridColumnLayout.kt) | [`HybridColumnFragment`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/fragment/helper/HybridColumnFragment.kt) | Side-by-side 12-column row (`HybridLayoutMode.COLUMNS`) weighted by `HybridColumnSpec`. | Folds into `HybridLayoutMode.SINGLE` via `HybridFoldMode` (`PIN_FIRST_COLUMN_TOP`, `COLLAPSE_SECONDARY_COLUMNS_TO_TABS`, `COLLAPSE_ALL_COLUMNS_TO_TABS`, `STACK_VERTICAL`) and `HybridTabPlacement`. |
| **2** | [`DockWorkspace`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/dock/DockWorkspace.kt) | [`DockWorkspaceFragment`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/fragment/helper/DockWorkspaceFragment.kt) | Full multi-panel dock workspace (`LEFT`, `RIGHT`, `TOP`, `BOTTOM`, `CENTER`) with Single-Window vs. Multi-Window IDE mode. | Automatically converts `LEFT`, `RIGHT`, `TOP`, and `BOTTOM` docks into off-canvas edge swipe-out drawer overlays with scrim tap-to-close (`DockMobileDrawerPolicy`). |
| **3** | [`AdaptiveNavigationScaffold`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/layout/AdaptiveNavigationScaffold.kt) | [`AdaptiveNavigationScaffoldFragment`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/fragment/helper/AdaptiveNavigationScaffoldFragment.kt) | Full `Sidebar` on desktop/wide web (`>= 840dp`); vertical `NavigationRail` on tablet (`600..839dp`). | `BottomNavigationBar` + slide-out navigation drawer on phone/small web (`< 600dp`). |
| **4** | [`ResponsiveMasterDetailLayout`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/layout/ResponsiveMasterDetailLayout.kt) | [`ResponsiveMasterDetailFragment`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/fragment/helper/ResponsiveMasterDetailFragment.kt) | 2-pane (`Master` + `Detail`) at `>= 600dp` and 3-pane (`+ Inspector`) at `>= 1200dp`. | Single-pane drill-down stack with back button / `onBackPressed()` navigation (`< 600dp`). |
| **5** | [`HybridSplitNavigationLayout`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/layout/HybridSplitNavigationLayout.kt) | [`HybridSplitNavigationFragment`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/fragment/helper/HybridSplitNavigationFragment.kt) | Renders section switcher as a top `TabLayout` (`TabContainer`) in column mode (`inColumnMode == true`). | Switches to a bottom `BottomNavigationBar` in compact mode (`inColumnMode == false`) while preserving section state. |
| **6** | [`CollapsibleBottomDockLayout`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/layout/CollapsibleBottomDockLayout.kt) | [`CollapsibleBottomDockFragment`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/fragment/helper/CollapsibleBottomDockFragment.kt) | Main workspace content + persistent collapsible mini-player/bottom dock (`collapsedHeightPx` / `expandedHeightPx`). | Expands into a full bottom sheet on tap and auto-hides in fullscreen, PiP, or when the primary column is already visible. |
| **7** | [`ResponsiveBentoGridLayout`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/layout/ResponsiveBentoGridLayout.kt) | [`ResponsiveBentoGridFragment`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/fragment/helper/ResponsiveBentoGridFragment.kt) | Multi-column Bootstrap 12-grid dashboard (`colMd = 6`, `colLg = 4`, `colXl = 3`). | Reflows all `BentoGridItemSpec` cards into a 1-column (`col-12`) vertical feed (`< 600dp`). |

---

## 3. `HybridColumnLayout` & `HybridColumnFragment`

[`HybridColumnLayout`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/layout/HybridColumnLayout.kt) and [`HybridColumnFragment`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/fragment/helper/HybridColumnFragment.kt) provide a generalized multi-column layout that displays columns side-by-side in `HybridLayoutMode.COLUMNS` and folds gracefully into `HybridLayoutMode.SINGLE` on compact viewports.

### 3.1 Column Specifications (`HybridColumnSpec`)
Each column is registered with a [`HybridColumnSpec`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/layout/HybridColumnSpec.kt):
- `id: String`, `title: String`, `icon: String?`
- `weight: Float = 1.0f` and optional Bootstrap grid spans (`colSm`, `colMd`, `colLg`, `colXl`)
- `compactHeightPx: Int?` and `compactAspectRatio: Float?` (e.g. `16f / 9f` for a pinned top video/canvas surface when folded)
- `scrollableInColumnMode: Boolean` and `scrollableInSingleMode: Boolean`

### 3.2 Folding Modes (`HybridFoldMode`)
[`HybridFoldMode`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/layout/HybridFoldMode.kt) controls how columns fold when entering `HybridLayoutMode.SINGLE`:
1. **`HybridFoldMode.PIN_FIRST_COLUMN_TOP`** *(default)*:
   - Pins Column `0` (e.g., a video player or interactive canvas) at the top of the viewport using `compactAspectRatio` or `compactHeightPx`.
   - If there are 2 columns total, places Column `1` directly below Column `0`.
   - If there are 3 or more columns total, collapses secondary columns (`1..N`) into a tab switcher below Column `0`.
2. **`HybridFoldMode.COLLAPSE_SECONDARY_COLUMNS_TO_TABS`**:
   - Always keeps Column `0` pinned at the top and places all secondary columns (`1..N`) into a tab switcher (`TabLayout` or `BottomNavigationBar`).
3. **`HybridFoldMode.COLLAPSE_ALL_COLUMNS_TO_TABS`**:
   - Collapses **every** column (`0..N`, including Column `0`) into tabs in `HybridLayoutMode.SINGLE`, and automatically promotes Column `0` back into its own left column when expanding to `HybridLayoutMode.COLUMNS`.
4. **`HybridFoldMode.STACK_VERTICAL`**:
   - Stacks all columns (`0..N`) vertically in a single scrollable flow without tabs.

### 3.3 Tab Switcher Placement (`HybridTabPlacement`)
[`HybridTabPlacement`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/layout/HybridTabPlacement.kt) controls where tabs appear when columns are collapsed into tabs:
- **`HybridTabPlacement.TOP_TABS`**: Renders a top `TabLayout` (`TabContainer`) above the tabbed column content.
- **`HybridTabPlacement.BOTTOM_NAV`**: Renders a bottom `BottomNavigationBar` anchored at the bottom of the viewport (also aliased as `BOTTOM_NAVIGATION` in navigation helpers).
- **`HybridTabPlacement.ADAPTIVE_TOP_IN_COLUMNS_BOTTOM_IN_SINGLE`**: Uses `TOP_TABS` when in wide column mode and `BOTTOM_NAV` when folded into single mode.

### 3.4 Opt-In Fullscreen & Rotation Policy (`DynamicFullscreenController` & `HybridFullscreenPolicy`)
Fullscreen and rotation-triggered fullscreen are **disabled by default** (`HybridFullscreenPolicy.DISABLED`). You can opt into dynamic fullscreen via [`DynamicFullscreenController`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/layout/DynamicFullscreenController.kt) and [`HybridFullscreenPolicy`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/layout/HybridFullscreenPolicy.kt):
- **`HybridFullscreenPolicy.DISABLED`** *(default)*: No fullscreen controller is active.
- **`HybridFullscreenPolicy.MANUAL_ONLY`**: Fullscreen is toggled only when `setFullscreen(true/false)` or `toggleFullscreen()` is called explicitly.
- **`HybridFullscreenPolicy.AUTO_FULLSCREEN_ON_PHONE_LANDSCAPE`**: On small-screen mobile phones (`smallestScreenWidthDp < 600`), rotating the device to landscape automatically enters fullscreen on Column `0`, and rotating back to portrait exits fullscreen.
- **`HybridFullscreenPolicy.TABLET_COLUMNS_AND_LOCKED_FULLSCREEN`**: On tablets (`smallestScreenWidthDp >= 600`), rotating to landscape displays side-by-side columns (`HybridLayoutMode.COLUMNS`) without forcing fullscreen, while pressing the fullscreen button enters fullscreen and locks landscape orientation.
- **`HybridFullscreenPolicy.AUTO_PHONE_LANDSCAPE_AND_TABLET_LOCKED`**: Combines automatic phone landscape fullscreen with tablet multi-column landscape + locked manual fullscreen.

### 3.5 Standard Kotlin Example (`getBootstrap()` & `getStyle()`)

```kotlin
import com.nexustools.genericui.component.fragment.MainAppFragment
import com.nexustools.genericui.component.fragment.helper.HybridColumnFragment
import com.nexustools.genericui.component.layout.HybridColumnSpec
import com.nexustools.genericui.component.layout.HybridFoldMode
import com.nexustools.genericui.component.layout.HybridFullscreenPolicy
import com.nexustools.genericui.component.layout.HybridTabPlacement
import com.nexustools.genericui.component.layout.VStack
import com.nexustools.genericui.component.typography.Heading
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.enums.bootstrap.SpacingScale
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.CssCornerRadii

@MainAppFragment
class EpisodeStudioFragment : HybridColumnFragment(
    foldMode = HybridFoldMode.PIN_FIRST_COLUMN_TOP,
    tabPlacement = HybridTabPlacement.ADAPTIVE_TOP_IN_COLUMNS_BOTTOM_IN_SINGLE,
    fullscreenPolicy = HybridFullscreenPolicy.AUTO_PHONE_LANDSCAPE_AND_TABLET_LOCKED
) {
    override fun onConfigureColumns() {
        val playerSurface = VStack().apply {
            getBootstrap().apply {
                padding = SpacingScale.S3
                backgroundType = BootstrapType.DARK
            }
            getStyle().apply {
                setBorderRadius(CssCornerRadii.all(12f))
                setAccentColor(RgbaColor.fromHex("#2A80FF"))
            }
            addChild(Heading("16:9 Primary Player Viewport", level = 4))
        }

        val showBrowser = VStack().apply {
            getBootstrap().padding = SpacingScale.S3
            addChild(Heading("Shows & Chapters", level = 5))
        }

        val upNextQueue = VStack().apply {
            getBootstrap().padding = SpacingScale.S3
            addChild(Heading("Up Next Queue", level = 5))
        }

        addColumn(
            spec = HybridColumnSpec(
                id = "player",
                title = "Now Playing",
                colMd = 7,
                compactAspectRatio = 16f / 9f
            ),
            component = playerSurface
        )
        addColumn(
            spec = HybridColumnSpec(id = "shows", title = "Shows", colMd = 5, scrollableInSingleMode = true),
            component = showBrowser
        )
        addColumn(
            spec = HybridColumnSpec(id = "queue", title = "Queue", colMd = 5, scrollableInSingleMode = true),
            component = upNextQueue
        )
    }
}
```

---

## 4. `DockWorkspace` & `DockWorkspaceFragment` (Mobile Edge Swipe-Out Drawers)

[`DockWorkspace`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/dock/DockWorkspace.kt) and [`DockWorkspaceFragment`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/fragment/helper/DockWorkspaceFragment.kt) provide a complete IDE/creative studio docking workspace (`LEFT`, `RIGHT`, `TOP`, `BOTTOM`, and central document tabs) that automatically adapts to small-screen mobile and narrow web viewports.

### 4.1 Desktop, Tablet, & Wide Web Mode (`isCompactDrawerMode == false`)
- On Desktop (`Windows`, `MacOS`, `Gtk`, `Qt`, `Swing`), Tablets (`smallestScreenWidthDp >= 600`), and wide Web viewports (`viewportWidthDp >= 600`), all `LEFT`, `RIGHT`, `TOP`, and `BOTTOM` `DockArea`s are displayed onscreen around the central document `TabContainer`.
- Supports live switching between **`StudioWindowMode.SINGLE_WINDOW`** (GIMP single-window IDE layout) and **`StudioWindowMode.MULTI_WINDOW`** (floating native utility windows on desktop) as well as full-bleed 3D/2D canvas **`overlayMode = true`**.

### 4.2 Small-Screen Mobile & Narrow Web Edge Swipe-Out Drawers (`DockMobileDrawerPolicy`)
Controlled via [`DockMobileDrawerPolicy`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/dock/DockMobileDrawerPolicy.kt) (`AUTO` default, `ALWAYS_ENABLED`, `DISABLED`):
- When `isCompactDrawerMode == true` (`viewportWidthDp < 600` or `smallestScreenWidthDp < 600` under `DockMobileDrawerPolicy.AUTO`), the central canvas/document workspace expands to **100% of the viewport** and all peripheral docks (`LEFT`, `RIGHT`, `TOP`, `BOTTOM`) convert into **off-canvas edge swipe-out drawer overlays**.
- **Edge Swipe Gestures & Interactive Drag Fraction**:
  - Swiping inward from within `edgeSwipeDetectionZonePx` (`28px` default) along the left, right, top, or bottom screen edge tracks the pointer continuously (`activeMobileDrawerOpenFraction` in `0f..1f`) and snaps open once dragged past `drawerOpenThresholdFraction` (`0.3f` default).
  - Optional translucent edge handle pills (`showMobileEdgeHandles = true`) provide a visual affordance and tap target for each populated dock edge.
- **Scrim Tap-to-Close & Hardware Back Button**:
  - Tapping the translucent backdrop scrim (`dock-workspace-drawer-scrim`) or swiping the drawer back toward its edge closes the active drawer (`closeAllDockDrawers()`).
  - `DockWorkspaceFragment.onBackPressed()` automatically closes any open mobile dock drawer before popping the fragment back stack.
- **Programmatic Drawer Control**:
  - `openDockDrawer(DockPosition.LEFT)`, `closeDockDrawer(DockPosition.LEFT)`, `toggleDockDrawer(DockPosition.RIGHT)`, `closeAllDockDrawers()`, and `onMobileDrawerStateChanged: ((DockPosition?, Float) -> Unit)?`.

```kotlin
import com.nexustools.genericui.component.dock.DockMobileDrawerPolicy
import com.nexustools.genericui.component.fragment.MainAppFragment
import com.nexustools.genericui.component.fragment.helper.DockWorkspaceFragment
import com.nexustools.genericui.enums.dock.DockPosition
import com.nexustools.genericui.enums.dock.StudioWindowMode
import com.nexustools.genericui.window.TitleBar

@MainAppFragment
class CreativeStudioWorkspaceFragment : DockWorkspaceFragment(
    initialMode = StudioWindowMode.SINGLE_WINDOW
) {
    override fun onConfigureWorkspace() {
        dockWorkspace.mobileDrawerPolicy = DockMobileDrawerPolicy.AUTO
        dockWorkspace.edgeSwipeDetectionZonePx = 28
        dockWorkspace.showMobileEdgeHandles = true

        setTitleBar(TitleBar(title = "Creative Studio"))
        dockFragment("tools", "Toolbox", ToolboxFragment(), DockPosition.LEFT)
        dockFragment("canvas", "Main Artwork", CanvasViewportFragment(), DockPosition.CENTER)
        dockFragment("layers", "Layers & Filters", LayersFragment(), DockPosition.RIGHT)
        dockFragment("timeline", "Timeline", TimelineFragment(), DockPosition.BOTTOM)
    }
}
```

---

## 5. `AdaptiveNavigationScaffold` & `AdaptiveNavigationScaffoldFragment`

[`AdaptiveNavigationScaffold`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/layout/AdaptiveNavigationScaffold.kt) and [`AdaptiveNavigationScaffoldFragment`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/fragment/helper/AdaptiveNavigationScaffoldFragment.kt) automatically morph primary application navigation across 3 viewport tiers:
1. **Desktop & Wide Web (`>= 840dp`)**: Displays a full vertical **`Sidebar`** (`activeNavigationKind == "SIDEBAR"`) with icons, labels, and badges alongside the content host.
2. **Tablet & Medium Viewports (`600..839dp`)**: Displays a compact vertical **`NavigationRail`** (`activeNavigationKind == "NAVIGATION_RAIL"`).
3. **Phone & Narrow Web (`< 600dp`)**: Displays a **`BottomNavigationBar`** (`activeNavigationKind == "BOTTOM_NAV"`) plus an optional slide-out overflow drawer.

```kotlin
import com.nexustools.genericui.component.fragment.MainAppFragment
import com.nexustools.genericui.component.fragment.helper.AdaptiveNavigationScaffoldFragment

@MainAppFragment
class CloudDriveMainFragment : AdaptiveNavigationScaffoldFragment() {
    override fun onConfigureNavigation() {
        addFragmentDestination("files", "My Files", icon = "bi-folder", fragment = FilesBrowserFragment())
        addFragmentDestination("shared", "Shared", icon = "bi-people", badge = "3", fragment = SharedFilesFragment())
        addFragmentDestination("activity", "Activity", icon = "bi-clock-history", fragment = ActivityFeedFragment())
    }
}
```

---

## 6. `ResponsiveMasterDetailLayout` & `ResponsiveMasterDetailFragment`

[`ResponsiveMasterDetailLayout`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/layout/ResponsiveMasterDetailLayout.kt) and [`ResponsiveMasterDetailFragment`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/fragment/helper/ResponsiveMasterDetailFragment.kt) coordinate 2-pane (`Master` + `Detail`) and 3-pane (`+ Inspector`) responsive flows:
- **Tablet, Desktop, & Wide Web (`>= 600dp`)**: `isSideBySideMode == true`; renders `Master` (`colMd = 4`) and `Detail` (`colMd = 8`) side-by-side.
- **Extra-Wide Viewports (`>= 1200dp`)**: When an optional 3rd `Inspector` pane is set and `isInspectorOpen == true`, `isInspectorSideBySide == true` and all 3 panes (`Master`, `Detail`, `Inspector`) render side-by-side.
- **Small-Screen Mobile & Narrow Web (`< 600dp`)**: `isSideBySideMode == false`; renders a single-pane stack that starts on `Master`, pushes `Detail` when `openDetailInCompact()` is called, and navigates back to `Master` via `navigateBackToMaster()` or hardware/browser Back (`onBackPressed()`).

---

## 7. `HybridSplitNavigationLayout` & `HybridSplitNavigationFragment`

[`HybridSplitNavigationLayout`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/layout/HybridSplitNavigationLayout.kt) and [`HybridSplitNavigationFragment`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/fragment/helper/HybridSplitNavigationFragment.kt) switch section navigation chrome between column mode and compact mode while preserving section state and live child instances:
- **Column Mode (`inColumnMode == true`, `>= 900dp`)**: Displays section navigation as a top `TabLayout` (`TabContainer`) above the section content.
- **Compact Mode (`inColumnMode == false`, `< 900dp`)**: Hides the top tab bar and displays a `BottomNavigationBar` anchored at the bottom of the screen.

---

## 8. `CollapsibleBottomDockLayout` & `CollapsibleBottomDockFragment`

[`CollapsibleBottomDockLayout`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/layout/CollapsibleBottomDockLayout.kt) and [`CollapsibleBottomDockFragment`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/fragment/helper/CollapsibleBottomDockFragment.kt) pair a primary workspace container with a persistent collapsible mini-player or bottom dock bar:
- Transitions smoothly between collapsed peek height (`collapsedHeightPx = 60`, `isDockCollapsed == true`) and expanded sheet height (`expandedHeightPx = 320`, `isDockCollapsed == false`) via `collapseDock()`, `expandDock()`, and `toggleDockExpanded()`.
- Automatically hides the bottom dock when `isFullscreen == true`, `isInPictureInPictureMode == true`, or `hideWhenPrimaryColumnVisible && isPrimaryColumnVisible` is `true`.
- Pressing Back (`onBackPressed()`) when the bottom dock is expanded collapses it to its peek bar before navigating back.

---

## 9. `ResponsiveBentoGridLayout` & `ResponsiveBentoGridFragment`

[`ResponsiveBentoGridLayout`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/layout/ResponsiveBentoGridLayout.kt), [`BentoGridItemSpec`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/layout/BentoGridItemSpec.kt), and [`ResponsiveBentoGridFragment`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/fragment/helper/ResponsiveBentoGridFragment.kt) implement a responsive 12/6/4/3/1-column dashboard grid:
- Each card specifies `BentoGridItemSpec(id, title, colXs = 12, colSm = 12, colMd = 6, colLg = 4, colXl = 3, rowSpan = 1, minHeightPx = ...)`.
- On `< 600dp`, `isSingleColumnFeed == true` and all cards stack in a 1-column (`12/12`) vertical feed; on `>= 600dp`, `>= 900dp`, and `>= 1200dp`, cards automatically arrange into 2, 3, or 4 columns per 12-column row.

---

## 10. Declarative `:extensions:dsl` Examples (`hybridColumnLayout`, `adaptiveNavigationScaffold`, `style`, & `bootstrap`)

When `:extensions:dsl` is enabled (`extensions { dsl() }`), all hybrid layouts can be composed declaratively alongside `bootstrap { ... }`, `style { ... }`, and `important { ... }` (which live strictly in `:extensions:dsl` and never pollute `:common`):

```kotlin
import com.nexustools.genericui.component.layout.HybridFoldMode
import com.nexustools.genericui.component.layout.HybridFullscreenPolicy
import com.nexustools.genericui.component.layout.HybridTabPlacement
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.enums.bootstrap.BootstrapWidth
import com.nexustools.genericui.enums.bootstrap.SpacingScale
import com.nexustools.genericui.enums.css.CssCursor
import com.nexustools.genericui.ext.dsl.*
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.CssCornerRadii

val rootView = buildDiv {
    bootstrap {
        width = BootstrapWidth.W_100
        padding = SpacingScale.S2
    }

    adaptiveNavigationScaffold {
        destination(id = "watch", label = "Watch", icon = "bi-play-btn") {
            hybridColumnLayout(
                foldMode = HybridFoldMode.PIN_FIRST_COLUMN_TOP,
                tabPlacement = HybridTabPlacement.ADAPTIVE_TOP_IN_COLUMNS_BOTTOM_IN_SINGLE,
                fullscreenPolicy = HybridFullscreenPolicy.AUTO_PHONE_LANDSCAPE_AND_TABLET_LOCKED
            ) {
                column(
                    id = "stage",
                    title = "Video Stage",
                    weight = 1.75f,
                    colMd = 7,
                    compactAspectRatio = 16f / 9f
                ) {
                    bootstrap {
                        padding = SpacingScale.S3
                        backgroundType = BootstrapType.DARK
                    }
                    style {
                        cursor = CssCursor.POINTER
                        accentColor = RgbaColor.fromHex("#2A80FF")
                        important {
                            borderRadius = CssCornerRadii.all(10f)
                        }
                    }
                    h4("Live Stream Stage")
                }

                column(
                    id = "chat",
                    title = "Live Chat & Queue",
                    weight = 1.0f,
                    colMd = 5,
                    scrollableInSingleMode = true
                ) {
                    bootstrap {
                        padding = SpacingScale.S3
                    }
                    p("Interactive chat messages and upcoming queue items")
                }
            }
        }

        destination(id = "analytics", label = "Analytics", icon = "bi-bar-chart") {
            responsiveBentoGridLayout(gapPx = 16) {
                bentoCard(id = "kpi-viewers", title = "Concurrent Viewers", colMd = 6, colLg = 4, colXl = 3) {
                    bootstrap { padding = SpacingScale.S3 }
                    h3("14,820")
                }
                bentoCard(id = "kpi-retention", title = "Retention Curve", colMd = 6, colLg = 8, colXl = 9) {
                    bootstrap { padding = SpacingScale.S3 }
                    p("92% average watch completion")
                }
            }
        }
    }
}
```
