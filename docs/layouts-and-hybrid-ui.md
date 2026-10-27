# GenericUI Responsive & Hybrid UI Layouts Guide (`layouts-and-hybrid-ui.md`)

> **Root Repository**: [**GenericUI (`../README.md`)**](../README.md) | **Master Docs Index**: [**`docs/README.md`**](./README.md)  
> **Comprehensive Reference**: [**Hybrid Layouts & Fragment Helpers Guide (`layouts-and-helpers.md`)**](./layouts-and-helpers.md)  
> **Related Guides**: [Components & Docking (`components-and-docking.md`)](./components-and-docking.md) · [Fragments & Windows (`fragments-and-windows.md`)](./fragments-and-windows.md) · [Extensions & DSL (`extensions.md`)](./extensions.md) · [Media & Canvases (`media-and-canvases.md`)](./media-and-canvases.md)

This guide summarizes GenericUI's **7 Hybrid & Responsive Layout Systems** (`com.nexustools.genericui.component.layout` & `com.nexustools.genericui.component.dock`), their **Fragment Helper counterparts** (`com.nexustools.genericui.component.fragment.helper`), shared breakpoint evaluation via [`HybridBreakpointSpec`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/layout/HybridBreakpointSpec.kt) and [`HybridLayoutMode`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/layout/HybridLayoutMode.kt), and declarative `kotlinx.html`-style DSL builders in `:extensions:dsl` ([`HybridLayoutDsl.kt`](../extensions/dsl/src/commonMain/kotlin/com/nexustools/genericui/ext/dsl/HybridLayoutDsl.kt)).

For full API tables, edge-swipe drawer gestures, and both standard (`getBootstrap()` / `getStyle()`) and `:extensions:dsl` (`bootstrap { ... }` / `style { ... }`) examples, see [**`layouts-and-helpers.md`**](./layouts-and-helpers.md).

---

## 1. Shared Responsive Breakpoint Architecture (`HybridBreakpointSpec` & `HybridLayoutMode`)

All 7 hybrid layout helpers share [`HybridBreakpointSpec`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/layout/HybridBreakpointSpec.kt) and [`HybridLayoutMode`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/layout/HybridLayoutMode.kt) (`SINGLE`, `COLUMNS`, `FULLSCREEN`, `PICTURE_IN_PICTURE`) so mobile screen-size rules (phone vs. tablet via `smallestScreenWidthDp`) and live desktop/web viewport-size rules (`viewportWidthDp` / `viewportHeightDp`) behave consistently across all 8 platform backends:

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
| **1** | [`DockWorkspace`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/dock/DockWorkspace.kt) | [`DockWorkspaceFragment`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/fragment/helper/DockWorkspaceFragment.kt) | Full onscreen `LEFT`/`RIGHT`/`TOP`/`BOTTOM` docks on Desktop, Tablet (`>= 600dp`), and wide Web (`>= 600dp`); off-canvas slide-out edge drawers with edge-swipe gestures and scrim tap-to-close on small-screen mobile (`< 600dp`) and narrow Web (`< 600dp`) via [`DockMobileDrawerPolicy`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/dock/DockMobileDrawerPolicy.kt) (`AUTO`, `ALWAYS_ENABLED`, `DISABLED`). |
| **2** | [`HybridColumnLayout`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/layout/HybridColumnLayout.kt) | [`HybridColumnFragment`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/fragment/helper/HybridColumnFragment.kt) | Side-by-side Bootstrap columns (`HybridLayoutMode.COLUMNS`) on tablet landscape, wide Web, and Desktop; folds into a single vertical/tabbed interface (`HybridLayoutMode.SINGLE`) on phones, tablet portrait, and narrow Web via [`HybridFoldMode`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/layout/HybridFoldMode.kt) and [`HybridTabPlacement`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/layout/HybridTabPlacement.kt). Optional opt-in [`DynamicFullscreenController`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/layout/DynamicFullscreenController.kt) (`HybridFullscreenPolicy.DISABLED` default). |
| **3** | [`HybridSplitNavigationLayout`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/layout/HybridSplitNavigationLayout.kt) | [`HybridSplitNavigationFragment`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/fragment/helper/HybridSplitNavigationFragment.kt) | Renders section navigation as a top `TabContainer` in wide/column mode (`inColumnMode == true`) and switches to a bottom `BottomNavigationBar` in compact mode (`inColumnMode == false`). |
| **4** | [`CollapsibleBottomDockLayout`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/layout/CollapsibleBottomDockLayout.kt) | [`CollapsibleBottomDockFragment`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/fragment/helper/CollapsibleBottomDockFragment.kt) | Hosts main content above a persistent collapsible bottom dock / mini-player bar (`collapsedHeightPx` vs. `expandedHeightPx`), auto-hiding in fullscreen, PiP, or when a primary column is already visible. |
| **5** | [`ResponsiveMasterDetailLayout`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/layout/ResponsiveMasterDetailLayout.kt) | [`ResponsiveMasterDetailFragment`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/fragment/helper/ResponsiveMasterDetailFragment.kt) | 2-pane (`Master` + `Detail`) or 3-pane (`+ Inspector` at `>= 1200dp`) side-by-side split on wide viewports; folds into a single-pane drill-down stack with back navigation on `< 600dp`. |
| **6** | [`AdaptiveNavigationScaffold`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/layout/AdaptiveNavigationScaffold.kt) | [`AdaptiveNavigationScaffoldFragment`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/fragment/helper/AdaptiveNavigationScaffoldFragment.kt) | Morphs navigation chrome between `"BOTTOM_NAV"` (`< 600dp`), `"NAVIGATION_RAIL"` (`600..839dp`), and `"SIDEBAR"` (`>= 840dp`). |
| **7** | [`ResponsiveBentoGridLayout`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/layout/ResponsiveBentoGridLayout.kt) | [`ResponsiveBentoGridFragment`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/fragment/helper/ResponsiveBentoGridFragment.kt) | Bootstrap 12-column bento dashboard grid (`colMd`/`colLg`/`colXl`) that automatically reflows into a single-column (`colXs = 12`) vertical feed on `< 600dp`. |

---

## 3. Declarative `:extensions:dsl` Builders (`HybridLayoutDsl.kt`)

When using `:extensions:dsl`, all hybrid layouts can be constructed declaratively inside any `AbstractContainer` alongside `bootstrap { ... }` and `style { ... }`:

```kotlin
import com.nexustools.genericui.component.layout.HybridFoldMode
import com.nexustools.genericui.enums.bootstrap.BootstrapWidth
import com.nexustools.genericui.enums.bootstrap.SpacingScale
import com.nexustools.genericui.ext.dsl.*
import com.nexustools.genericui.style.css.CssCornerRadii

val root = buildDiv {
    bootstrap {
        width = BootstrapWidth.W_100
        padding = SpacingScale.S2
    }
    adaptiveNavigationScaffold {
        destination(id = "player", label = "Now Playing", icon = "bi-play-circle") {
            hybridColumnLayout(foldMode = HybridFoldMode.PIN_FIRST_COLUMN_TOP) {
                column(id = "video", title = "Video", weight = 1.8f, colMd = 7, compactAspectRatio = 16f / 9f) {
                    style { borderRadius = CssCornerRadii.all(8f) }
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
