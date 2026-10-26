package com.nexustools.genericui.component.layout

import com.nexustools.genericui.component.dock.DockMobileDrawerPolicy
import com.nexustools.genericui.component.dock.DockPanel
import com.nexustools.genericui.component.dock.DockWorkspace
import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.fragment.helper.AdaptiveNavigationScaffoldFragment
import com.nexustools.genericui.component.fragment.helper.CollapsibleBottomDockFragment
import com.nexustools.genericui.component.fragment.helper.DockWorkspaceFragment
import com.nexustools.genericui.component.fragment.helper.HybridColumnFragment
import com.nexustools.genericui.component.fragment.helper.HybridSplitNavigationFragment
import com.nexustools.genericui.component.fragment.helper.ResponsiveBentoGridFragment
import com.nexustools.genericui.component.fragment.helper.ResponsiveMasterDetailFragment
import com.nexustools.genericui.enums.dock.DockPosition
import com.nexustools.genericui.enums.system.BackendTarget
import com.nexustools.genericui.enums.window.WindowState
import com.nexustools.genericui.window.WindowConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Comprehensive unit tests for all Phase 8 hybrid layout components, helper fragments,
 * [DockWorkspace] mobile off-canvas edge drawers, and opt-in [DynamicFullscreenController].
 */
class HybridLayoutsTest {

    @Test
    fun testDockWorkspaceTabletWideWebAndDesktopKeepOnscreenDocks() {
        val workspace = DockWorkspace()
        workspace.addDockPanel(DockPanel("tools", "Tools"), DockPosition.LEFT)
        workspace.addDockPanel(DockPanel("layers", "Layers"), DockPosition.RIGHT)
        workspace.addDockPanel(DockPanel("header", "Header"), DockPosition.TOP)
        workspace.addDockPanel(DockPanel("timeline", "Timeline"), DockPosition.BOTTOM)

        // Default desktop/wide viewport (1280x800, smallest 800dp)
        assertFalse(workspace.isCompactDrawerMode)
        assertTrue(workspace.leftArea.isVisible)
        assertTrue(workspace.rightArea.isVisible)
        assertTrue(workspace.topArea.isVisible)
        assertTrue(workspace.bottomArea.isVisible)
        assertFalse(workspace.mobileDrawerScrim.isVisible)

        // Tablet portrait (600x960, smallestScreenWidthDp = 600)
        workspace.updateViewportMetrics(widthPx = 600, heightPx = 960, densityScale = 1.0f, smallestWidthDp = 600)
        assertFalse(workspace.isCompactDrawerMode)
        assertTrue(workspace.leftArea.isVisible)
        assertTrue(workspace.rightArea.isVisible)
        assertTrue(workspace.topArea.isVisible)
        assertTrue(workspace.bottomArea.isVisible)

        // Wide Web / Desktop (1024x768)
        workspace.updateViewportMetrics(widthPx = 1024, heightPx = 768, densityScale = 1.0f, smallestWidthDp = 768)
        assertFalse(workspace.isCompactDrawerMode)
        assertTrue(workspace.leftArea.isVisible)
        assertTrue(workspace.rightArea.isVisible)
    }

    @Test
    fun testDockWorkspaceCompactMobileAndNarrowViewportOffCanvasDrawersAndEdgeSwipes() {
        val fragment = DockWorkspaceFragment()
        fragment.performCreate(null)
        val workspace = fragment.dockWorkspace

        workspace.addDockPanel(DockPanel("left-panel", "Tools"), DockPosition.LEFT)
        workspace.addDockPanel(DockPanel("right-panel", "Inspector"), DockPosition.RIGHT)
        workspace.addDockPanel(DockPanel("top-panel", "Ribbon"), DockPosition.TOP)
        workspace.addDockPanel(DockPanel("bottom-panel", "Timeline"), DockPosition.BOTTOM)

        // Switch to small-screen mobile / narrow viewport (390x844, smallestWidthDp = 390)
        fragment.onConfigurationChanged(
            WindowConfiguration(widthPx = 390, heightPx = 844, smallestScreenWidthDp = 390, densityScale = 1.0f)
        )
        assertTrue(workspace.isCompactDrawerMode)
        assertTrue(fragment.isCompactDrawerMode)
        assertNull(workspace.activeMobileDrawerPosition)
        assertFalse(workspace.leftArea.isVisible)
        assertEquals("translateX(-100%)", workspace.leftArea.transform)
        assertFalse(workspace.rightArea.isVisible)
        assertEquals("translateX(100%)", workspace.rightArea.transform)
        assertFalse(workspace.topArea.isVisible)
        assertEquals("translateY(-100%)", workspace.topArea.transform)
        assertFalse(workspace.bottomArea.isVisible)
        assertEquals("translateY(100%)", workspace.bottomArea.transform)
        assertFalse(workspace.mobileDrawerScrim.isVisible)

        // Edge handles should be visible when no drawer is open
        assertTrue(workspace.leftEdgeHandle.isVisible)
        assertTrue(workspace.rightEdgeHandle.isVisible)
        assertTrue(workspace.topEdgeHandle.isVisible)
        assertTrue(workspace.bottomEdgeHandle.isVisible)

        // 1. Edge-swipe from LEFT edge (startX = 8f <= 28px detection zone)
        val startedLeft = workspace.beginEdgeSwipe(8f, 400f)
        assertEquals(DockPosition.LEFT, startedLeft)
        val midLeftFraction = workspace.updateEdgeSwipe(140f, 400f)
        assertTrue(midLeftFraction > 0.3f)
        assertTrue(workspace.leftArea.isVisible)
        assertTrue(workspace.endEdgeSwipe())
        assertEquals(DockPosition.LEFT, workspace.activeMobileDrawerPosition)
        assertEquals(1f, workspace.activeMobileDrawerOpenFraction)
        assertTrue(workspace.leftArea.isVisible)
        assertEquals("translate(0%, 0%)", workspace.leftArea.transform)
        assertTrue(workspace.mobileDrawerScrim.isVisible)

        // Tapping the main workspace in compact mode closes the open LEFT drawer
        assertTrue(workspace.tapMainWorkspaceInCompactMode())
        assertNull(workspace.activeMobileDrawerPosition)
        assertEquals(0f, workspace.activeMobileDrawerOpenFraction)
        assertFalse(workspace.leftArea.isVisible)
        assertFalse(workspace.mobileDrawerScrim.isVisible)

        // 2. Edge-swipe from RIGHT edge (startX = 385f >= 390 - 28)
        val startedRight = workspace.beginEdgeSwipe(385f, 400f)
        assertEquals(DockPosition.RIGHT, startedRight)
        workspace.updateEdgeSwipe(200f, 400f)
        assertTrue(workspace.endEdgeSwipe())
        assertEquals(DockPosition.RIGHT, workspace.activeMobileDrawerPosition)
        assertTrue(workspace.rightArea.isVisible)

        // Tapping the scrim closes the open RIGHT drawer
        workspace.mobileDrawerScrim.onClick?.invoke()
        assertNull(workspace.activeMobileDrawerPosition)
        assertFalse(workspace.rightArea.isVisible)

        // 3. Edge-swipe from TOP edge (startY = 10f <= 28px)
        val startedTop = workspace.beginEdgeSwipe(195f, 10f)
        assertEquals(DockPosition.TOP, startedTop)
        workspace.updateEdgeSwipe(195f, 180f)
        assertTrue(workspace.endEdgeSwipe())
        assertEquals(DockPosition.TOP, workspace.activeMobileDrawerPosition)
        assertTrue(workspace.topArea.isVisible)

        // Swiping back toward the TOP edge closes the open drawer
        workspace.beginEdgeSwipe(195f, 180f)
        workspace.updateEdgeSwipe(195f, 10f)
        assertFalse(workspace.endEdgeSwipe())
        assertNull(workspace.activeMobileDrawerPosition)
        assertFalse(workspace.topArea.isVisible)

        // 4. Edge-swipe from BOTTOM edge (startY = 838f >= 844 - 28) and openMobileDockDrawer
        val startedBottom = workspace.beginEdgeSwipe(195f, 838f)
        assertEquals(DockPosition.BOTTOM, startedBottom)
        workspace.updateEdgeSwipe(195f, 650f)
        assertTrue(workspace.endEdgeSwipe())
        assertEquals(DockPosition.BOTTOM, workspace.activeMobileDrawerPosition)
        assertTrue(workspace.bottomArea.isVisible)

        // Pressing Back on DockWorkspaceFragment closes the open drawer and returns true
        assertTrue(fragment.onBackPressed())
        assertNull(fragment.activeMobileDrawerPosition)
        assertFalse(workspace.bottomArea.isVisible)

        // Explicit openMobileDockDrawer for all 4 sides
        for (pos in listOf(DockPosition.LEFT, DockPosition.RIGHT, DockPosition.TOP, DockPosition.BOTTOM)) {
            assertTrue(fragment.openMobileDockDrawer(pos))
            assertEquals(pos, fragment.activeMobileDrawerPosition)
            assertTrue(workspace.areaForPosition(pos).isVisible)
            assertTrue(fragment.closeMobileDockDrawer())
            assertNull(fragment.activeMobileDrawerPosition)
        }

        // Policy override check
        workspace.mobileDrawerPolicy = DockMobileDrawerPolicy.DISABLED
        assertFalse(workspace.isCompactDrawerMode)
        workspace.mobileDrawerPolicy = DockMobileDrawerPolicy.ALWAYS_ENABLED
        assertTrue(workspace.isCompactDrawerMode)
    }

    @Test
    fun testHybridColumnLayoutDefaultsToDisabledFullscreen() {
        val layout = HybridColumnLayout()
        assertEquals(HybridFullscreenPolicy.DISABLED, layout.fullscreenPolicy)
        assertNull(layout.fullscreenController)
        assertFalse(layout.isFullscreen)

        layout.addColumn("player", "Player", component = Container("div"))
        layout.addColumn("playlist", "Playlist", component = Container("div"))

        // Rotating a phone to landscape when fullscreenPolicy is DISABLED must NEVER trigger fullscreen
        layout.updateViewport(viewportWidthDp = 844, viewportHeightDp = 390, smallestScreenWidthDp = 390, isMobileTarget = true)
        assertFalse(layout.isFullscreen)
        assertEquals(HybridLayoutMode.SINGLE, layout.layoutMode)

        // HybridColumnFragment also defaults to DISABLED
        val fragment = HybridColumnFragment()
        assertEquals(HybridFullscreenPolicy.DISABLED, fragment.fullscreenPolicy)
        assertNull(fragment.fullscreenController)
    }

    @Test
    fun testHybridColumnLayoutPinFirstColumnTopAndSecondaryTabs() {
        val layout = HybridColumnLayout(
            foldMode = HybridFoldMode.PIN_FIRST_COLUMN_TOP,
            tabPlacement = HybridTabPlacement.TOP_TABS
        )
        val col0 = Container("div", "video-player")
        val col1 = Container("div", "episodes-list")
        layout.addColumn(
            HybridColumnSpec(id = "video", title = "Video", colMd = 7, compactAspectRatio = 16f / 9f),
            col0
        )
        layout.addColumn(
            HybridColumnSpec(id = "episodes", title = "Episodes", colMd = 5),
            col1
        )

        // Wide desktop / tablet landscape -> COLUMNS mode
        layout.updateViewport(viewportWidthDp = 1024, viewportHeightDp = 768, smallestScreenWidthDp = 768)
        assertEquals(HybridLayoutMode.COLUMNS, layout.layoutMode)
        assertTrue(layout.inColumnMode)
        assertTrue(layout.columnsRow.isVisible)
        assertFalse(layout.singleStack.isVisible)
        assertTrue(col0.isVisible)
        assertTrue(col1.isVisible)

        // Small-screen mobile portrait (2 columns) -> SINGLE mode with col0 pinned top and col1 below (no tabs needed)
        layout.updateViewport(viewportWidthDp = 360, viewportHeightDp = 800, smallestScreenWidthDp = 360)
        assertEquals(HybridLayoutMode.SINGLE, layout.layoutMode)
        assertFalse(layout.inColumnMode)
        assertTrue(layout.singleStack.isVisible)
        assertTrue(col0.isVisible)
        assertTrue(col1.isVisible)
        assertFalse(layout.topTabBar.isVisible)
        assertEquals(203, col0.heightPx) // 360 / (16/9) = 202.5 -> 203

        // Add a 3rd column -> secondary columns (1..2) collapse into tabs below pinned col0
        val col2 = Container("div", "comments-panel")
        layout.addColumn(HybridColumnSpec(id = "comments", title = "Comments", colMd = 4), col2)
        assertEquals(HybridLayoutMode.SINGLE, layout.layoutMode)
        assertTrue(col0.isVisible)
        assertTrue(layout.topTabBar.isVisible)
        assertEquals("episodes", layout.activeTabColumnId)
        assertTrue(col1.isVisible)
        assertFalse(col2.isVisible)

        assertTrue(layout.selectTab("comments"))
        assertEquals("comments", layout.activeTabColumnId)
        assertFalse(col1.isVisible)
        assertTrue(col2.isVisible)
    }

    @Test
    fun testHybridColumnLayoutCollapseSecondaryAndCollapseAllColumnsToTabs() {
        // 1. COLLAPSE_SECONDARY_COLUMNS_TO_TABS
        val secLayout = HybridColumnLayout(
            foldMode = HybridFoldMode.COLLAPSE_SECONDARY_COLUMNS_TO_TABS,
            tabPlacement = HybridTabPlacement.BOTTOM_NAV
        )
        val primary = Container("div", "primary")
        val secA = Container("div", "sec-a")
        val secB = Container("div", "sec-b")
        secLayout.addColumn("primary", "Primary", component = primary)
        secLayout.addColumn("secA", "Sec A", component = secA)
        secLayout.addColumn("secB", "Sec B", component = secB)

        secLayout.updateViewport(viewportWidthDp = 390, viewportHeightDp = 844, smallestScreenWidthDp = 390)
        assertEquals(HybridLayoutMode.SINGLE, secLayout.layoutMode)
        assertTrue(primary.isVisible)
        assertTrue(secLayout.bottomNavBar.isVisible)
        assertFalse(secLayout.topTabBar.isVisible)
        assertEquals("secA", secLayout.activeTabColumnId)
        assertTrue(secA.isVisible)
        assertFalse(secB.isVisible)

        // 2. COLLAPSE_ALL_COLUMNS_TO_TABS (Episode-Caster MusicScreen pattern)
        val allTabsLayout = HybridColumnLayout(
            foldMode = HybridFoldMode.COLLAPSE_ALL_COLUMNS_TO_TABS,
            tabPlacement = HybridTabPlacement.TOP_TABS
        )
        val visualizer = Container("div", "visualizer")
        val queue = Container("div", "queue")
        val lyrics = Container("div", "lyrics")
        allTabsLayout.addColumn("visualizer", "Visualizer", component = visualizer)
        allTabsLayout.addColumn("queue", "Queue", component = queue)
        allTabsLayout.addColumn("lyrics", "Lyrics", component = lyrics)

        // In SINGLE mode (e.g., phone portrait or tablet portrait), ALL columns (0..2) are in tabs
        allTabsLayout.updateViewport(viewportWidthDp = 400, viewportHeightDp = 820, smallestScreenWidthDp = 400)
        assertEquals(HybridLayoutMode.SINGLE, allTabsLayout.layoutMode)
        assertTrue(allTabsLayout.topTabBar.isVisible)
        assertEquals(3, allTabsLayout.topTabBar.entries.size)
        assertTrue(allTabsLayout.selectTab("visualizer"))
        assertEquals("visualizer", allTabsLayout.activeTabColumnId)
        assertTrue(visualizer.isVisible)
        assertFalse(queue.isVisible)
        assertFalse(lyrics.isVisible)

        // Transitioning to COLUMNS mode (tablet landscape / desktop) promotes Column 0 ("visualizer")
        // into the dedicated left column and automatically selects Column 1 ("queue") in the right-hand tab bar!
        allTabsLayout.updateViewport(viewportWidthDp = 1024, viewportHeightDp = 768, smallestScreenWidthDp = 768)
        assertEquals(HybridLayoutMode.COLUMNS, allTabsLayout.layoutMode)
        assertTrue(visualizer.isVisible)
        assertEquals("queue", allTabsLayout.activeTabColumnId)
        assertEquals(2, allTabsLayout.topTabBar.entries.size)
        assertTrue(queue.isVisible)
        assertFalse(lyrics.isVisible)
    }

    @Test
    fun testHybridColumnLayoutStackVertical() {
        val stackLayout = HybridColumnLayout(foldMode = HybridFoldMode.STACK_VERTICAL)
        val c1 = Container("div", "c1")
        val c2 = Container("div", "c2")
        val c3 = Container("div", "c3")
        stackLayout.addColumn(HybridColumnSpec("c1", "C1", compactHeightPx = 180), c1)
        stackLayout.addColumn(HybridColumnSpec("c2", "C2"), c2)
        stackLayout.addColumn(HybridColumnSpec("c3", "C3"), c3)

        stackLayout.updateViewport(viewportWidthDp = 375, viewportHeightDp = 812, smallestScreenWidthDp = 375)
        assertEquals(HybridLayoutMode.SINGLE, stackLayout.layoutMode)
        assertFalse(stackLayout.topTabBar.isVisible)
        assertFalse(stackLayout.bottomNavBar.isVisible)
        assertTrue(c1.isVisible)
        assertTrue(c2.isVisible)
        assertTrue(c3.isVisible)
        assertEquals(180, c1.heightPx)
    }

    @Test
    fun testDynamicFullscreenControllerOptInPhoneVsTabletBehavior() {
        val fragment = HybridColumnFragment()
        val playerCol = Container("div", "player")
        val listCol = Container("div", "list")
        fragment.addColumn("player", "Player", component = playerCol)
        fragment.addColumn("list", "List", component = listCol)
        fragment.performCreate(null)

        // Opt in to AUTO_PHONE_LANDSCAPE_AND_TABLET_LOCKED
        val controller = fragment.enableDynamicFullscreen(
            HybridFullscreenPolicy.AUTO_PHONE_LANDSCAPE_AND_TABLET_LOCKED
        )
        assertNotNull(fragment.fullscreenController)
        assertTrue(controller.isEnabled)

        // 1. Small-screen mobile phone (smallestScreenWidthDp = 390 < 600):
        // Portrait -> SINGLE mode, not fullscreen
        fragment.hybridColumnLayout.updateViewport(
            viewportWidthDp = 390,
            viewportHeightDp = 844,
            smallestScreenWidthDp = 390,
            isMobileTarget = true
        )
        assertFalse(controller.isTablet)
        assertFalse(controller.isFullscreen)
        assertEquals(HybridLayoutMode.SINGLE, fragment.layoutMode)

        // Rotate phone to Landscape (844x390, smallestScreenWidthDp = 390) -> auto-enters FULLSCREEN
        fragment.hybridColumnLayout.updateViewport(
            viewportWidthDp = 844,
            viewportHeightDp = 390,
            smallestScreenWidthDp = 390,
            isMobileTarget = true
        )
        assertTrue(controller.isFullscreen)
        assertEquals("USER", controller.requestedScreenOrientation)
        assertEquals(HybridLayoutMode.FULLSCREEN, fragment.layoutMode)
        assertTrue(playerCol.isVisible)
        assertFalse(listCol.isVisible)

        // Rotate phone back to Portrait (390x844) -> auto-exits FULLSCREEN back to SINGLE
        fragment.hybridColumnLayout.updateViewport(
            viewportWidthDp = 390,
            viewportHeightDp = 844,
            smallestScreenWidthDp = 390,
            isMobileTarget = true
        )
        assertFalse(controller.isFullscreen)
        assertEquals(HybridLayoutMode.SINGLE, fragment.layoutMode)

        // 2. Tablet (smallestScreenWidthDp = 768 >= 600):
        // Landscape rotation uses COLUMNS mode without forcing fullscreen
        fragment.hybridColumnLayout.updateViewport(
            viewportWidthDp = 1024,
            viewportHeightDp = 768,
            smallestScreenWidthDp = 768,
            isMobileTarget = true
        )
        assertTrue(controller.isTablet)
        assertFalse(controller.isFullscreen)
        assertEquals(HybridLayoutMode.COLUMNS, fragment.layoutMode)

        // Tablet portrait rotation switches to SINGLE mode without forcing fullscreen
        fragment.hybridColumnLayout.updateViewport(
            viewportWidthDp = 768,
            viewportHeightDp = 1024,
            smallestScreenWidthDp = 768,
            isMobileTarget = true
        )
        assertFalse(controller.isFullscreen)
        assertEquals(HybridLayoutMode.SINGLE, fragment.layoutMode)

        // Explicit fullscreen on tablet locks orientation to "LANDSCAPE"
        controller.setFullscreen(true)
        assertTrue(controller.isFullscreen)
        assertEquals("LANDSCAPE", controller.requestedScreenOrientation)
        assertEquals(HybridLayoutMode.FULLSCREEN, fragment.layoutMode)

        // Pressing Back on HybridColumnFragment exits fullscreen and unlocks orientation to "UNSPECIFIED"
        assertTrue(fragment.onBackPressed())
        assertFalse(controller.isFullscreen)
        assertEquals("UNSPECIFIED", controller.requestedScreenOrientation)
        assertEquals(HybridLayoutMode.SINGLE, fragment.layoutMode)
    }

    @Test
    fun testHybridSplitNavigationLayoutAndFragment() {
        val fragment = HybridSplitNavigationFragment(initialInColumnMode = false)
        val moviesView = Container("div", "movies-view")
        val showsView = Container("div", "shows-view")
        val musicView = Container("div", "music-view")

        fragment.addSection("movies", "Movies", icon = "movie", content = moviesView)
        fragment.addSection("shows", "TV Shows", icon = "tv", badge = "4", content = showsView)
        fragment.addSection("music", "Music", icon = "music", content = musicView)
        fragment.performCreate(null)

        val layout = fragment.splitNavigationLayout
        assertEquals("movies", fragment.selectedSectionId)
        assertFalse(fragment.inColumnMode)
        assertFalse(layout.topTabLayout.isVisible)
        assertTrue(layout.bottomNavigationBar.isVisible)

        // Select "shows" in compact bottom-nav mode
        assertTrue(fragment.selectSection("shows"))
        assertEquals("shows", fragment.selectedSectionId)
        assertEquals(showsView, layout.contentHost.children.firstOrNull())

        // Switch to wide column mode (1200x800) -> topTabLayout becomes visible, bottomNavigationBar hides,
        // and selectedSectionId ("shows") is preserved!
        fragment.onResize(1200, 800)
        assertTrue(fragment.inColumnMode)
        assertTrue(layout.topTabLayout.isVisible)
        assertFalse(layout.bottomNavigationBar.isVisible)
        assertEquals("shows", fragment.selectedSectionId)
        assertEquals(showsView, layout.contentHost.children.firstOrNull())

        // Switch back to compact mode (390x844) -> bottomNavigationBar becomes visible, preserving selection
        fragment.onResize(390, 844)
        assertFalse(fragment.inColumnMode)
        assertFalse(layout.topTabLayout.isVisible)
        assertTrue(layout.bottomNavigationBar.isVisible)
        assertEquals("shows", fragment.selectedSectionId)
    }

    @Test
    fun testCollapsibleBottomDockLayoutAndFragment() {
        val fragment = CollapsibleBottomDockFragment()
        val mainView = Container("div", "main-workspace")
        val miniPlayer = Container("div", "mini-player")
        val expandedQueue = Container("div", "expanded-queue")

        fragment.setMainContent(mainView)
        fragment.setDockContent(miniPlayer, expandedQueue)
        fragment.performCreate(null)

        val layout = fragment.bottomDockLayout
        layout.collapsedHeightPx = 64
        layout.expandedHeightPx = 360

        // Collapse to peek height
        fragment.collapseDock()
        assertTrue(layout.isDockCollapsed)
        assertTrue(layout.isDockCurrentlyVisible)
        assertEquals(64, layout.bottomDockContainer.heightPx)
        assertFalse(layout.expandedSheetSlot.isVisible)

        // Expand to full sheet height
        fragment.expandDock()
        assertFalse(layout.isDockCollapsed)
        assertEquals(360, layout.bottomDockContainer.heightPx)
        assertTrue(layout.expandedSheetSlot.isVisible)

        // Pressing Back when expanded collapses the dock to peek height first and returns true
        assertTrue(fragment.onBackPressed())
        assertTrue(layout.isDockCollapsed)
        assertEquals(64, layout.bottomDockContainer.heightPx)

        // Pressing Back again when already collapsed returns false
        assertFalse(fragment.onBackPressed())

        // Auto-hide in Fullscreen
        layout.isFullscreen = true
        assertFalse(layout.isDockCurrentlyVisible)
        assertFalse(layout.bottomDockContainer.isVisible)
        layout.isFullscreen = false
        assertTrue(layout.isDockCurrentlyVisible)

        // Auto-hide in Picture-in-Picture
        fragment.onPictureInPictureModeChanged(true)
        assertFalse(layout.isDockCurrentlyVisible)
        fragment.onPictureInPictureModeChanged(false)
        assertTrue(layout.isDockCurrentlyVisible)

        // Auto-hide when primary column is visible
        layout.hideWhenPrimaryColumnVisible = true
        fragment.onResize(1280, 800)
        assertTrue(layout.isPrimaryColumnVisible)
        assertFalse(layout.isDockCurrentlyVisible)
        fragment.onResize(390, 844)
        assertFalse(layout.isPrimaryColumnVisible)
        assertTrue(layout.isDockCurrentlyVisible)
    }

    @Test
    fun testResponsiveMasterDetailLayoutAndFragment() {
        val fragment = ResponsiveMasterDetailFragment()
        val masterPane = Container("div", "master-list")
        val detailPane = Container("div", "detail-view")
        val inspectorPane = Container("div", "inspector-view")

        fragment.setMasterPane(masterPane, colMd = 4)
        fragment.setDetailPane(detailPane, colMd = 8)
        fragment.setInspectorPane(inspectorPane, colXl = 3)
        fragment.performCreate(null)

        val layout = fragment.masterDetailLayout

        // 1. Extra-wide viewport (1440x900 >= 1200dp) -> 3-pane side-by-side (Master + Detail + Inspector)
        fragment.onResize(1440, 900)
        assertTrue(fragment.isSideBySideMode)
        assertTrue(fragment.isInspectorSideBySide)
        assertTrue(layout.masterHost.isVisible)
        assertTrue(layout.detailHost.isVisible)
        assertTrue(layout.inspectorHost.isVisible)

        // 2. Medium/Wide viewport (900x700, 600..1199dp) -> 2-pane side-by-side (Master + Detail, Inspector hidden inline)
        fragment.onResize(900, 700)
        assertTrue(fragment.isSideBySideMode)
        assertFalse(fragment.isInspectorSideBySide)
        assertTrue(layout.masterHost.isVisible)
        assertTrue(layout.detailHost.isVisible)
        assertFalse(layout.inspectorHost.isVisible)

        // 3. Compact viewport (390x844 < 600dp) -> single-pane drill-down starting on Master
        fragment.onResize(390, 844)
        assertFalse(fragment.isSideBySideMode)
        assertFalse(fragment.isDetailShowingInCompact)
        assertTrue(layout.masterHost.isVisible)
        assertFalse(layout.detailHost.isVisible)
        assertFalse(layout.inspectorHost.isVisible)

        // Drill down into Detail pane
        assertTrue(fragment.openDetailInCompact())
        assertTrue(fragment.isDetailShowingInCompact)
        assertFalse(layout.masterHost.isVisible)
        assertTrue(layout.detailHost.isVisible)

        // Pressing Back in compact Detail mode navigates back to Master and returns true
        assertTrue(fragment.onBackPressed())
        assertFalse(fragment.isDetailShowingInCompact)
        assertTrue(layout.masterHost.isVisible)
        assertFalse(layout.detailHost.isVisible)
    }

    @Test
    fun testAdaptiveNavigationScaffoldAndFragment() {
        val fragment = AdaptiveNavigationScaffoldFragment()
        val homeView = Container("div", "home")
        val searchView = Container("div", "search")
        val libraryView = Container("div", "library")

        fragment.addDestination("home", "Home", icon = "H", content = homeView)
        fragment.addDestination("search", "Search", icon = "S", content = searchView)
        fragment.addDestination("library", "Library", icon = "L", badge = "12", content = libraryView)
        fragment.performCreate(null)

        val scaffold = fragment.scaffold
        assertEquals("home", fragment.selectedDestinationId)

        // Select "library"
        assertTrue(fragment.selectDestination("library"))
        assertEquals("library", fragment.selectedDestinationId)

        // 1. Compact (< 600dp) -> BOTTOM_NAV
        fragment.onResize(412, 892)
        assertEquals("BOTTOM_NAV", fragment.activeNavigationKind)
        assertTrue(scaffold.bottomNavigationBar.isVisible)
        assertFalse(scaffold.navigationRail.isVisible)
        assertFalse(scaffold.sidebar.isVisible)
        assertEquals("library", fragment.selectedDestinationId)

        // 2. Medium (600..839dp) -> NAVIGATION_RAIL
        fragment.onResize(720, 1024)
        assertEquals("NAVIGATION_RAIL", fragment.activeNavigationKind)
        assertFalse(scaffold.bottomNavigationBar.isVisible)
        assertTrue(scaffold.navigationRail.isVisible)
        assertFalse(scaffold.sidebar.isVisible)
        assertEquals("library", fragment.selectedDestinationId)

        // 3. Expanded (>= 840dp) -> SIDEBAR
        fragment.onResize(1280, 800)
        assertEquals("SIDEBAR", fragment.activeNavigationKind)
        assertFalse(scaffold.bottomNavigationBar.isVisible)
        assertFalse(scaffold.navigationRail.isVisible)
        assertTrue(scaffold.sidebar.isVisible)
        assertEquals("library", fragment.selectedDestinationId)
    }

    @Test
    fun testResponsiveBentoGridLayoutAndFragment() {
        val fragment = ResponsiveBentoGridFragment()
        fragment.addCard(
            BentoGridItemSpec(id = "hero", title = "Hero", colXs = 12, colMd = 8, colLg = 6, colXl = 6),
            Container("div", "hero-card")
        )
        fragment.addCard(
            BentoGridItemSpec(id = "stats", title = "Stats", colXs = 12, colMd = 4, colLg = 3, colXl = 3),
            Container("div", "stats-card")
        )
        fragment.addCard(
            BentoGridItemSpec(id = "activity", title = "Activity", colXs = 12, colMd = 6, colLg = 3, colXl = 3),
            Container("div", "activity-card")
        )
        fragment.performCreate(null)

        // Extra-wide desktop (1400x900) -> colXl spans (6, 3, 3)
        fragment.onResize(1400, 900)
        assertFalse(fragment.isSingleColumnFeed)
        assertEquals(6, fragment.activeColumnsForItem("hero"))
        assertEquals(3, fragment.activeColumnsForItem("stats"))
        assertEquals(3, fragment.activeColumnsForItem("activity"))

        // Tablet / Medium (768x1024) -> colMd spans (8, 4, 6)
        fragment.onResize(768, 1024)
        assertFalse(fragment.isSingleColumnFeed)
        assertEquals(8, fragment.activeColumnsForItem("hero"))
        assertEquals(4, fragment.activeColumnsForItem("stats"))
        assertEquals(6, fragment.activeColumnsForItem("activity"))

        // Small-screen mobile / Narrow viewport (390x844 < 600dp) -> single-column feed (12, 12, 12)
        fragment.onResize(390, 844)
        assertTrue(fragment.isSingleColumnFeed)
        assertEquals(12, fragment.activeColumnsForItem("hero"))
        assertEquals(12, fragment.activeColumnsForItem("stats"))
        assertEquals(12, fragment.activeColumnsForItem("activity"))
    }
}
