package com.nexustools.genericui.component.layout

import com.nexustools.genericui.enums.bootstrap.ResponsiveBreakpoint
import com.nexustools.genericui.enums.system.BackendTarget
import com.nexustools.genericui.window.WindowConfiguration
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Shared screen-size (Mobile Phone vs. Tablet) and live viewport-size (Web & Desktop) breakpoint rules
 * used across all Phase 8 hybrid layout helpers (`DockWorkspace`, `HybridColumnLayout`, `HybridSplitNavigationLayout`,
 * `CollapsibleBottomDockLayout`, `ResponsiveMasterDetailLayout`, `AdaptiveNavigationScaffold`, `ResponsiveBentoGridLayout`).
 */
data class HybridBreakpointSpec(
    val compactDrawerBreakpointDp: Int = 600,
    val tabletSmallestWidthDp: Int = 600,
    val mediumBreakpointDp: Int = 600,
    val expandedNavigationBreakpointDp: Int = 840,
    val columnBreakpointDp: Int = 900,
    val extraWideBreakpointDp: Int = 1200
) {
    /**
     * Computes `viewportWidthDp` from raw pixels and density scale.
     */
    fun toDp(px: Int, densityScale: Float = 1.0f): Int {
        val safeDensity = if (densityScale > 0f) densityScale else 1.0f
        return (px / safeDensity).roundToInt()
    }

    /**
     * Evaluates whether the device/viewport qualifies as a tablet (`smallestScreenWidthDp >= tabletSmallestWidthDp`).
     */
    fun isTablet(smallestScreenWidthDp: Int): Boolean =
        smallestScreenWidthDp >= tabletSmallestWidthDp

    /**
     * Evaluates whether the device/viewport is a small-screen mobile phone (`smallestScreenWidthDp < tabletSmallestWidthDp`).
     */
    fun isSmallScreenMobile(smallestScreenWidthDp: Int): Boolean =
        smallestScreenWidthDp < tabletSmallestWidthDp

    /**
     * Evaluates whether `DockWorkspace` should use compact off-canvas edge drawers (`true`) or normal onscreen docks (`false`).
     * - On Mobile (`Android`, `IOS`): `true` on small-screen mobile (`smallestScreenWidthDp < compactDrawerBreakpointDp`),
     *   `false` on tablets (`smallestScreenWidthDp >= compactDrawerBreakpointDp`).
     * - On Web (`Web`) and resizable Desktop viewports: `true` when `viewportWidthDp < compactDrawerBreakpointDp`,
     *   `false` when `viewportWidthDp >= compactDrawerBreakpointDp`.
     */
    fun shouldUseCompactDockDrawers(
        viewportWidthDp: Int,
        viewportHeightDp: Int,
        smallestScreenWidthDp: Int = min(viewportWidthDp, viewportHeightDp),
        backendTarget: BackendTarget? = null
    ): Boolean {
        if (backendTarget != null && backendTarget.isMobile) {
            return smallestScreenWidthDp < compactDrawerBreakpointDp
        }
        return viewportWidthDp < compactDrawerBreakpointDp
    }

    /**
     * Evaluates whether [HybridColumnLayout] should display side-by-side columns ([HybridLayoutMode.COLUMNS])
     * or fold into a single interface ([HybridLayoutMode.SINGLE]).
     * - On Mobile (`Android`, `IOS`): `true` when on a tablet (`smallestScreenWidthDp >= tabletSmallestWidthDp`)
     *   in landscape (`viewportWidthDp > viewportHeightDp`).
     * - On Web & Desktop: `true` when `viewportWidthDp >= columnBreakpointDp`
     *   (or `smallestScreenWidthDp >= tabletSmallestWidthDp && viewportWidthDp >= mediumBreakpointDp && viewportWidthDp > viewportHeightDp`).
     */
    fun shouldUseWideColumns(
        viewportWidthDp: Int,
        viewportHeightDp: Int,
        smallestScreenWidthDp: Int = min(viewportWidthDp, viewportHeightDp),
        backendTarget: BackendTarget? = null
    ): Boolean {
        val isLandscape = viewportWidthDp > viewportHeightDp
        if (backendTarget != null && backendTarget.isMobile) {
            return isTablet(smallestScreenWidthDp) && isLandscape
        }
        if (viewportWidthDp >= columnBreakpointDp) {
            return true
        }
        return isTablet(smallestScreenWidthDp) && isLandscape && viewportWidthDp >= mediumBreakpointDp
    }

    /**
     * Resolves the Bootstrap 5 [ResponsiveBreakpoint] for the given `viewportWidthDp`.
     */
    fun resolveBootstrapBreakpoint(viewportWidthDp: Int): ResponsiveBreakpoint =
        ResponsiveBreakpoint.forWidthPx(viewportWidthDp)

    /**
     * Evaluates whether a [WindowConfiguration] should use wide columns.
     */
    fun shouldUseWideColumns(
        config: WindowConfiguration,
        backendTarget: BackendTarget? = null
    ): Boolean {
        val widthDp = toDp(config.widthPx, config.densityScale)
        val heightDp = toDp(config.heightPx, config.densityScale)
        return shouldUseWideColumns(
            viewportWidthDp = widthDp,
            viewportHeightDp = heightDp,
            smallestScreenWidthDp = config.smallestScreenWidthDp,
            backendTarget = backendTarget
        )
    }

    companion object {
        val DEFAULT = HybridBreakpointSpec()
    }
}
