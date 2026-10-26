# GenericUI Responsive & Hybrid UI Layouts Guide

> **Root Repository**: [**GenericUI (`../README.md`)**](../README.md) | **Master Docs Index**: [**`docs/README.md`**](./README.md)  
> **Related Guides**: [Components & Docking (`components-and-docking.md`)](./components-and-docking.md) · [Fragments & Windows (`fragments-and-windows.md`)](./fragments-and-windows.md) · [Extensions & DSL (`extensions.md`)](./extensions.md) · [Media & Canvases (`media-and-canvases.md`)](./media-and-canvases.md)

This guide covers GenericUI's **7 Hybrid & Responsive Layout Systems** (`com.nexustools.genericui.component.layout`), their **Fragment Helper counterparts** (`com.nexustools.genericui.component.fragment.helper`), shared breakpoint evaluation via [`HybridBreakpointSpec`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/layout/HybridBreakpointSpec.kt), and declarative `kotlinx.html`-style DSL builders in `:extensions:dsl` ([`HybridLayoutDsl.kt`](../extensions/dsl/src/commonMain/kotlin/com/nexustools/genericui/ext/dsl/HybridLayoutDsl.kt)).

---

## 1. Shared Responsive Breakpoint Architecture (`HybridBreakpointSpec`)

All 7 hybrid layout helpers share [`HybridBreakpointSpec`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/layout/HybridBreakpointSpec.kt) so mobile screen-size rules (phone vs. tablet via `smallestScreenWidthDp`) and live desktop/web viewport-size rules (`viewportWidthDp` / `viewportHeightDp`) behave consistently across all 8 platform backends:

| Property | Default (`dp`) | Role |
| :--- | :--- | :--- |
| `compactDrawerBreakpointDp` | `600` | Threshold below which `DockWorkspace` collapses `LEFT`, `RIGHT`, `TOP`, and `BOTTOM` dock areas into off-canvas slide-out edge drawers. |
| `tabletSmallestWidthDp` | `600` | Android/iOS `smallestScreenWidthDp` threshold distinguishing small-screen mobile phones (`< 600dp`) from tablets (`>= 600dp`). |
| `mediumBreakpointDp` | `600` | Compact-to-medium threshold for `ResponsiveMasterDetailLayout`, `AdaptiveNavigationScaffold`, and `ResponsiveBentoGridLayout`. |
| `expandedNavigationBreakpointDp` | `840` | Medium-to-expanded threshold where `AdaptiveNavigationScaffold` promotes its vertical `navigationRail` into a full `sidebar`. |
| `columnBreakpointDp` | `900` | Desktop/web viewport width threshold where `HybridColumnLayout` and `HybridSplitNavigationLayout` switch from folded single-flow mode to multi-column mode. |
| `extraWideBreakpointDp` | `1200` | Extra-wide threshold where `ResponsiveMasterDetailLayout` reveals its 3rd inline `Inspector` pane and `ResponsiveBentoGridLayout` applies `colXl` spans. |

---

## 2. Overview of the 7 Hybrid Layout & Fragment Pairs

| # | Layout Component (`component.layout` / `component.dock`) | Fragment Helper (`component.fragment.helper`) | Primary Responsive Adaptation |
| :--- | :--- | :--- | :--- |
| **1** | [`DockWorkspace`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/dock/DockWorkspace.kt) | [`DockWorkspaceFragment`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/fragment/helper/DockWorkspaceFragment.kt) | Full onscreen `LEFT`/`RIGHT`/`TOP`/`BOTTOM` docks on Desktop, Tablet (`>= 600dp`), and wide Web (`>= 600dp`); off-canvas slide-out edge drawers with scrim backdrop on small-screen mobile (`< 600dp`) and narrow Web (`< 600dp`). |
| **2** | [`HybridColumnLayout`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/layout/HybridColumnLayout.kt) | [`HybridColumnFragment`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/fragment/helper/HybridColumnFragment.kt) | Side-by-side Bootstrap columns (`HybridLayoutMode.COLUMNS`) on tablet landscape, wide Web, and Desktop; folds into a single vertical/tabbed interface (`HybridLayoutMode.SINGLE`) on phones, tablet portrait, and narrow Web. Optional opt-in `DynamicFullscreenController`. |
| **3** | [`HybridSplitNavigationLayout`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/layout/HybridSplitNavigationLayout.kt) | [`HybridSplitNavigationFragment`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/fragment/helper/HybridSplitNavigationFragment.kt) | Renders section navigation as a top `TabContainer` in wide/column mode (`inColumnMode == true`) and switches to a bottom `BottomNavigationBar` in compact mode (`inColumnMode == false`). |
| **4** | [`CollapsibleBottomDockLayout`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/layout/CollapsibleBottomDockLayout.kt) | [`CollapsibleBottomDockFragment`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/fragment/helper/CollapsibleBottomDockFragment.kt) | Hosts main content above a persistent collapsible bottom dock / mini-player bar (`collapsedHeightPx` vs. `expandedHeightPx`), auto-hiding in fullscreen, PiP, or when a primary column is already visible. |
| **5** | [`ResponsiveMasterDetailLayout`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/layout/ResponsiveMasterDetailLayout.kt) | [`ResponsiveMasterDetailFragment`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/fragment/helper/ResponsiveMasterDetailFragment.kt) | 2-pane (`Master` + `Detail`) or 3-pane (`+ Inspector` at `>= 1200dp`) side-by-side split on wide viewports; folds into a single-pane drill-down stack with back navigation on `< 600dp`. |
| **6** | [`AdaptiveNavigationScaffold`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/layout/AdaptiveNavigationScaffold.kt) | [`AdaptiveNavigationScaffoldFragment`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/fragment/helper/AdaptiveNavigationScaffoldFragment.kt) | Morphs navigation chrome between `"BOTTOM_NAV"` (`< 600dp`), `"NAVIGATION_RAIL"` (`600..839dp`), and `"SIDEBAR"` (`>= 840dp`). |
| **7** | [`ResponsiveBentoGridLayout`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/layout/ResponsiveBentoGridLayout.kt) | [`ResponsiveBentoGridFragment`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/fragment/helper/ResponsiveBentoGridFragment.kt) | Bootstrap 12-column bento dashboard grid (`colMd`/`colLg`/`colXl`) that automatically reflows into a single-column (`colXs = 12`) vertical feed on `< 600dp`. |

---

## 3. `DockWorkspace` & `DockWorkspaceFragment` (Compact Edge Drawers)

[`DockWorkspace`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/dock/DockWorkspace.kt) and [`DockWorkspaceFragment`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/fragment/helper/DockWorkspaceFragment.kt) support [`DockMobileDrawerPolicy`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/dock/DockMobileDrawerPolicy.kt) (`AUTO_COMPACT_VIEWPORT`, `ALWAYS_DRAWERS`, `NEVER_DRAWERS`):

- **Desktop, Tablet (`smallestScreenWidthDp >= 600`), & Wide Web (`>= 600dp`)**: `isCompactDrawerMode == false`. All `LEFT`, `RIGHT`, `TOP`, and `BOTTOM` `DockArea`s render inline alongside the central canvas/document tabs.
- **Small-Screen Mobile (`smallestScreenWidthDp < 600`) & Narrow Web (`< 600dp`)**: `isCompactDrawerMode == true`. The central canvas occupies 100% of the viewport while edge dock areas collapse into off-canvas slide-out drawers controlled via `openDockDrawer(position)`, `closeDockDrawer(position)`, `toggleDockDrawer(position)`, and `closeAllDockDrawers()`.
- **Back Navigation**: `DockWorkspaceFragment.onBackPressed()` automatically closes any open compact dock drawer before delegating back navigation.

---

## 4. `HybridColumnLayout`, `HybridColumnFragment`, & `DynamicFullscreenController`

[`HybridColumnLayout`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/layout/HybridColumnLayout.kt) and [`HybridColumnFragment`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/fragment/helper/HybridColumnFragment.kt) generalize multi-column media, studio, and dashboard layouts across mobile, tablet, web, and desktop:

### 4.1 Column Folding Modes (`HybridFoldMode`)
- `HybridFoldMode.PIN_FIRST_COLUMN_TOP`: Pins Column 0 at the top of the vertical stack (using `compactHeightPx` or `compactAspectRatio`) and places Column 1 below it (or collapses Columns `1..N` into tabs when 3+ columns are registered).
- `HybridFoldMode.COLLAPSE_SECONDARY_COLUMNS_TO_TABS`: Keeps Column 0 pinned while collapsing all secondary columns (`1..N`) into a switchable tab bar (`HybridTabPlacement.TOP_TABS`, `BOTTOM_NAV`, or `ADAPTIVE_TOP_IN_COLUMNS_BOTTOM_IN_SINGLE`).
- `HybridFoldMode.COLLAPSE_ALL_COLUMNS_TO_TABS`: Collapses **all** columns (`0..N`, including Column 0) into tabs in `HybridLayoutMode.SINGLE`, and automatically promotes Column 0 out of the tab bar into its own left column when entering `HybridLayoutMode.COLUMNS`.
- `HybridFoldMode.STACK_VERTICAL`: Stacks all columns vertically in order (`0..N`).

### 4.2 Opt-In Dynamic Fullscreen (`DynamicFullscreenController` & `HybridFullscreenPolicy`)
Fullscreen and rotation-driven fullscreen are **disabled by default** (`HybridFullscreenPolicy.DISABLED`) and can be explicitly enabled via `enableDynamicFullscreen(policy, window)`:
- `HybridFullscreenPolicy.MANUAL_ONLY`: Manual `setFullscreen(true/false)` and `toggleFullscreen()` only.
- `HybridFullscreenPolicy.AUTO_FULLSCREEN_ON_PHONE_LANDSCAPE`: On small-screen mobile (`< 600dp`), rotating to landscape automatically enters fullscreen and rotating to portrait exits fullscreen.
- `HybridFullscreenPolicy.TABLET_COLUMNS_AND_LOCKED_FULLSCREEN`: On tablets (`>= 600dp`), landscape shows side-by-side columns (`HybridLayoutMode.COLUMNS`) without forcing fullscreen, while explicit fullscreen locks landscape orientation.
- `HybridFullscreenPolicy.AUTO_PHONE_LANDSCAPE_AND_TABLET_LOCKED`: Combines phone auto-landscape fullscreen and tablet multi-column landscape + locked fullscreen.

---

## 5. `HybridSplitNavigationLayout` & `HybridSplitNavigationFragment`

[`HybridSplitNavigationLayout`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/layout/HybridSplitNavigationLayout.kt) and [`HybridSplitNavigationFragment`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/fragment/helper/HybridSplitNavigationFragment.kt) switch section navigation chrome based on `inColumnMode`:

- **Column / Wide Mode (`inColumnMode == true`)**: Shows `topTabLayout` (`TabContainer`) at the top and hides `bottomNavigationBar`.
- **Compact Mode (`inColumnMode == false`)**: Hides `topTabLayout` and shows `bottomNavigationBar` (`BottomNavigationBar`) anchored at the bottom.
- Preserves `selectedSectionId` and existing section component/fragment instances across mode and tab switches.

```kotlin
val splitFragment = HybridSplitNavigationFragment()
splitFragment.addFragmentSection("podcasts", "Podcasts", icon = "bi-mic", fragment = PodcastsFragment())
splitFragment.addFragmentSection("music", "Music", icon = "bi-music-note", fragment = MusicFragment())
splitFragment.addFragmentSection("radio", "Radio", icon = "bi-broadcast", badge = "LIVE", fragment = RadioFragment())
```

---

## 6. `CollapsibleBottomDockLayout` & `CollapsibleBottomDockFragment`

[`CollapsibleBottomDockLayout`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/layout/CollapsibleBottomDockLayout.kt) and [`CollapsibleBottomDockFragment`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/fragment/helper/CollapsibleBottomDockFragment.kt) pair a primary workspace area (`mainContentHost`) with a persistent collapsible bottom dock (`bottomDockContainer`):

- Toggles between collapsed peek height (`collapsedHeightPx = 60`, `isDockCollapsed == true`) and expanded sheet height (`expandedHeightPx = 320`, `isDockCollapsed == false`) via `collapseDock()`, `expandDock()`, and `toggleDockExpanded()`.
- Automatically computes `isDockCurrentlyVisible` and hides the dock when `isDockHidden`, `isFullscreen`, or `isInPictureInPictureMode` is `true`, or when `hideWhenPrimaryColumnVisible && isPrimaryColumnVisible` is `true`.
- `CollapsibleBottomDockFragment.onBackPressed()` collapses an expanded bottom dock to peek height first before popping navigation stacks.

---

## 7. `ResponsiveMasterDetailLayout` & `ResponsiveMasterDetailFragment`

[`ResponsiveMasterDetailLayout`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/layout/ResponsiveMasterDetailLayout.kt) and [`ResponsiveMasterDetailFragment`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/fragment/helper/ResponsiveMasterDetailFragment.kt) manage 2-pane (`Master` + `Detail`) and 3-pane (`+ Inspector`) responsive flows:

- **Wide (`>= 600dp`)**: `isSideBySideMode == true`; renders `Master` (`colMd = 4`) and `Detail` (`colMd = 8`) side-by-side.
- **Extra-Wide (`>= 1200dp`)**: When an inspector pane is configured and `isInspectorOpen == true`, `isInspectorSideBySide == true` and all 3 panes (`Master`, `Detail`, `Inspector`) render side-by-side.
- **Compact (`< 600dp`)**: `isSideBySideMode == false`; shows `Master` initially, switches to `Detail` on `openDetailInCompact()`, and pops back to `Master` on `navigateBackToMaster()` / `onBackPressed()`.

---

## 8. `AdaptiveNavigationScaffold` & `AdaptiveNavigationScaffoldFragment`

[`AdaptiveNavigationScaffold`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/layout/AdaptiveNavigationScaffold.kt) and [`AdaptiveNavigationScaffoldFragment`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/fragment/helper/AdaptiveNavigationScaffoldFragment.kt) automatically morph application navigation chrome across 3 viewport tiers:

- **Compact (`< 600dp`)**: `activeNavigationKind == "BOTTOM_NAV"` (`bottomNavigationBar.isVisible == true`).
- **Medium (`600..839dp`)**: `activeNavigationKind == "NAVIGATION_RAIL"` (`navigationRail.isVisible == true`).
- **Expanded (`>= 840dp`)**: `activeNavigationKind == "SIDEBAR"` (`sidebar.isVisible == true`).

---

## 9. `ResponsiveBentoGridLayout`, `BentoGridItemSpec`, & `ResponsiveBentoGridFragment`

[`BentoGridItemSpec`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/layout/BentoGridItemSpec.kt), [`ResponsiveBentoGridLayout`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/layout/ResponsiveBentoGridLayout.kt), and [`ResponsiveBentoGridFragment`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/fragment/helper/ResponsiveBentoGridFragment.kt) implement a Bootstrap 12-column responsive bento card grid:

- Cards declare `colXs` (default `12`), `colSm` (`12`), `colMd` (`6`), `colLg` (`4`), and `colXl` (`3`).
- On small-screen mobile (`< 600dp`) and narrow web viewports (`< 600dp`), `isSingleColumnFeed == true` and all cards reflow to `col-12` (`activeColumnsForItem(id) == 12`).
- On tablet, wide web, and desktop viewports, cards automatically size to their `colMd`, `colLg`, or `colXl` 12-grid column spans.

---

## 10. Declarative `:extensions:dsl` Builders (`HybridLayoutDsl.kt`)

When using `:extensions:dsl`, all hybrid layouts can be constructed declaratively inside any `AbstractContainer`:

```kotlin
import com.nexustools.genericui.component.layout.HybridFoldMode
import com.nexustools.genericui.ext.dsl.*

val root = buildDiv {
    adaptiveNavigationScaffold {
        destination(id = "player", label = "Now Playing", icon = "bi-play-circle") {
            hybridColumnLayout(foldMode = HybridFoldMode.PIN_FIRST_COLUMN_TOP) {
                column(id = "video", title = "Video", weight = 1.8f, colMd = 7, compactAspectRatio = 16f / 9f) {
                    +"Primary Video / Visualizer Surface"
                }
                column(id = "queue", title = "Up Next", weight = 1.0f, colMd = 5, scrollableInSingleMode = true) {
                    +"Episode Queue & Chapters"
                }
            }
        }
        destination(id = "dashboard", label = "Overview", icon = "bi-grid") {
            responsiveBentoGridLayout {
                bentoCard(id = "stats", title = "Listeners", colMd = 6, colLg = 4, colXl = 3) {
                    +"Active Listeners Card"
                }
                bentoCard(id = "recent", title = "Recent Episodes", colMd = 6, colLg = 8, colXl = 9) {
                    +"Recent Episodes Feed"
                }
            }
        }
    }
}
```
