package com.nexustools.genericui.system.style

import com.nexustools.genericui.component.layout.StyledContainer
import com.nexustools.genericui.component.surface.StyledComponent
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.enums.system.BackendTarget
import com.nexustools.genericui.nativeimpl.action.ButtonNativeImpl
import com.nexustools.genericui.nativeimpl.action.DropdownNativeImpl
import com.nexustools.genericui.nativeimpl.action.StyledButtonNativeImpl
import com.nexustools.genericui.nativeimpl.content.ImageNativeImpl
import com.nexustools.genericui.nativeimpl.content.TableNativeImpl
import com.nexustools.genericui.nativeimpl.dialog.DialogWindowNativeImpl
import com.nexustools.genericui.nativeimpl.dialog.FileBrowserDialogNativeImpl
import com.nexustools.genericui.nativeimpl.dialog.StandardDialogsNativeImpl
import com.nexustools.genericui.nativeimpl.dock.DockAreaNativeImpl
import com.nexustools.genericui.nativeimpl.dock.DockGroupNativeImpl
import com.nexustools.genericui.nativeimpl.dock.DockPanelNativeImpl
import com.nexustools.genericui.nativeimpl.dock.DockWindowNativeImpl
import com.nexustools.genericui.nativeimpl.dock.DockWorkspaceNativeImpl
import com.nexustools.genericui.nativeimpl.dock.ToolPaletteNativeImpl
import com.nexustools.genericui.nativeimpl.document.BookContainerNativeImpl
import com.nexustools.genericui.nativeimpl.feedback.ProgressBarNativeImpl
import com.nexustools.genericui.nativeimpl.feedback.SpinnerNativeImpl
import com.nexustools.genericui.nativeimpl.form.CheckboxNativeImpl
import com.nexustools.genericui.nativeimpl.form.RangeSliderNativeImpl
import com.nexustools.genericui.nativeimpl.form.SelectNativeImpl
import com.nexustools.genericui.nativeimpl.form.TextInputNativeImpl
import com.nexustools.genericui.nativeimpl.layout.ContainerNativeImpl
import com.nexustools.genericui.nativeimpl.layout.DividerNativeImpl
import com.nexustools.genericui.nativeimpl.layout.ScrollViewNativeImpl
import com.nexustools.genericui.nativeimpl.layout.StyledContainerNativeImpl
import com.nexustools.genericui.nativeimpl.lazy.LazyGridNativeImpl
import com.nexustools.genericui.nativeimpl.lazy.LazyListNativeImpl
import com.nexustools.genericui.nativeimpl.lazy.TreeViewNativeImpl
import com.nexustools.genericui.nativeimpl.navigation.BottomNavigationBarNativeImpl
import com.nexustools.genericui.nativeimpl.navigation.NavbarNativeImpl
import com.nexustools.genericui.nativeimpl.overlay.PopoverNativeImpl
import com.nexustools.genericui.nativeimpl.shell.ApplicationNativeImpl
import com.nexustools.genericui.nativeimpl.shell.ContextMenuNativeImpl
import com.nexustools.genericui.nativeimpl.shell.MenuBarNativeImpl
import com.nexustools.genericui.nativeimpl.shell.MenuItemNativeImpl
import com.nexustools.genericui.nativeimpl.shell.MenuNativeImpl
import com.nexustools.genericui.nativeimpl.shell.WindowNativeImpl
import com.nexustools.genericui.nativeimpl.system.NetworkMonitorNativeImpl
import com.nexustools.genericui.nativeimpl.system.NotificationManagerNativeImpl
import com.nexustools.genericui.nativeimpl.system.PermissionManagerNativeImpl
import com.nexustools.genericui.nativeimpl.system.SystemTrayNativeImpl
import com.nexustools.genericui.nativeimpl.tabs.TabContainerNativeImpl
import com.nexustools.genericui.nativeimpl.tabs.TabNativeImpl
import com.nexustools.genericui.nativeimpl.typography.MarkdownViewNativeImpl
import com.nexustools.genericui.nativeimpl.typography.TextNativeImpl
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.BackendStyleMask
import com.nexustools.genericui.style.css.CompiledSelectorSegment
import com.nexustools.genericui.style.css.CompiledStyleRule
import com.nexustools.genericui.style.css.ComponentStyle
import com.nexustools.genericui.style.css.CssCombinator
import com.nexustools.genericui.style.css.CssStyleSheet
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class BlinkBackendMaskPartitionTest {

    private class FakeBackendFactory(
        override val backendTarget: BackendTarget
    ) : NativeFactory {
        private fun unused(): Nothing = throw UnsupportedOperationException("Unused in test")
        override fun createApplication(): ApplicationNativeImpl<*> = unused()
        override fun createWindow(): WindowNativeImpl<*> = unused()
        override fun createMenuBar(): MenuBarNativeImpl<*> = unused()
        override fun createMenu(): MenuNativeImpl<*> = unused()
        override fun createMenuItem(checkable: Boolean, radio: Boolean): MenuItemNativeImpl<*> = unused()
        override fun createContextMenu(): ContextMenuNativeImpl<*> = unused()
        override fun createDockWorkspace(): DockWorkspaceNativeImpl<*> = unused()
        override fun createDockArea(): DockAreaNativeImpl<*> = unused()
        override fun createDockGroup(): DockGroupNativeImpl<*> = unused()
        override fun createDockPanel(): DockPanelNativeImpl<*> = unused()
        override fun createDockWindow(): DockWindowNativeImpl<*> = unused()
        override fun createToolPalette(): ToolPaletteNativeImpl<*> = unused()
        override fun createContainer(classPrefix: String): ContainerNativeImpl<*> = unused()
        override fun createStyledContainer(classPrefix: String, tagName: String): StyledContainerNativeImpl<*> = unused()
        override fun createBookContainer(): BookContainerNativeImpl<*> = unused()
        override fun createScrollView(): ScrollViewNativeImpl<*> = unused()
        override fun createDivider(): DividerNativeImpl<*> = unused()
        override fun createText(tagName: String, classPrefix: String): TextNativeImpl<*> = unused()
        override fun createMarkdownView(): MarkdownViewNativeImpl<*> = unused()
        override fun createImage(): ImageNativeImpl<*> = unused()
        override fun createTable(): TableNativeImpl<*> = unused()
        override fun createButton(classPrefix: String): ButtonNativeImpl<*> = unused()
        override fun createStyledButton(classPrefix: String): StyledButtonNativeImpl<*> = unused()
        override fun createDropdown(): DropdownNativeImpl<*> = unused()
        override fun createTextInput(multiline: Boolean): TextInputNativeImpl<*> = unused()
        override fun createSelect(): SelectNativeImpl<*> = unused()
        override fun createCheckbox(switchStyle: Boolean, radioStyle: Boolean): CheckboxNativeImpl<*> = unused()
        override fun createRangeSlider(): RangeSliderNativeImpl<*> = unused()
        override fun createTab(closable: Boolean): TabNativeImpl<*> = unused()
        override fun createTabContainer(): TabContainerNativeImpl<*> = unused()
        override fun createNavbar(): NavbarNativeImpl<*> = unused()
        override fun createBottomNavigationBar(): BottomNavigationBarNativeImpl<*> = unused()
        override fun createProgressBar(): ProgressBarNativeImpl<*> = unused()
        override fun createSpinner(): SpinnerNativeImpl<*> = unused()
        override fun createPopover(): PopoverNativeImpl<*> = unused()
        override fun createDialogWindow(): DialogWindowNativeImpl<*> = unused()
        override fun createFileBrowserDialog(): FileBrowserDialogNativeImpl = unused()
        override fun createStandardDialogs(): StandardDialogsNativeImpl = unused()
        override fun createLazyList(): LazyListNativeImpl<*> = unused()
        override fun createLazyGrid(): LazyGridNativeImpl<*> = unused()
        override fun createTreeView(): TreeViewNativeImpl<*> = unused()
        override fun createPermissionManager(): PermissionManagerNativeImpl = unused()
        override fun createSystemTray(): SystemTrayNativeImpl = unused()
        override fun createNotificationManager(): NotificationManagerNativeImpl = unused()
        override fun createNetworkMonitor(): NetworkMonitorNativeImpl = unused()
        override fun supportsTcp(): Boolean = false
        override fun supportsUdp(): Boolean = false
    }

    @BeforeTest
    fun setUp() {
        GenericUI.resetForTests()
        BlinkStyleEngine.clear()
        BlinkStyleEngine.ensureInstalled()
    }

    @AfterTest
    fun tearDown() {
        BlinkStyleEngine.clear()
        GenericUI.resetForTests()
    }

    @Test
    fun testForBackendZeroAllocationWhenAllRulesMatchAllBackends() {
        val compiled = listOf(
            CompiledStyleRule(
                selector = "#main-id",
                segments = CompiledSelectorSegment.parseChain("#main-id"),
                style = ComponentStyle().apply { setOpacity(0.9f) }
            ),
            CompiledStyleRule(
                selector = ".shared-card",
                segments = CompiledSelectorSegment.parseChain(".shared-card"),
                style = ComponentStyle().apply { setBorderRadiusPx(8f) }
            )
        )
        val bucketMap = BlinkRuleBucketMap.fromCompiledStyleRules(compiled)
        assertFalse(bucketMap.hasBackendSpecificRules)
        assertSame(bucketMap, bucketMap.forBackend(null))
        for (target in BackendTarget.entries) {
            assertSame(bucketMap, bucketMap.forBackend(target))
        }
    }

    @Test
    fun testForBackendExcludesForeignBackendRulesAcrossBucketsInvalidationAndBreakpoints() {
        val rules = listOf(
            CompiledStyleRule(
                selector = "#mac-header",
                segments = CompiledSelectorSegment.parseChain("#mac-header"),
                style = ComponentStyle().apply { setHeightPx(38) },
                backendMask = BackendStyleMask.MACOS
            ),
            CompiledStyleRule(
                selector = "#win-header",
                segments = CompiledSelectorSegment.parseChain("#win-header"),
                style = ComponentStyle().apply { setHeightPx(32) },
                backendMask = BackendStyleMask.WINDOWS
            ),
            CompiledStyleRule(
                selector = ".mac-pill",
                segments = CompiledSelectorSegment.parseChain(".mac-pill"),
                style = ComponentStyle().apply { setBorderRadiusPx(12f) },
                backendMask = BackendStyleMask.MACOS
            ),
            CompiledStyleRule(
                selector = ".win-pill",
                segments = CompiledSelectorSegment.parseChain(".win-pill"),
                style = ComponentStyle().apply { setBorderRadiusPx(4f) },
                mediaMinWidthPx = 960,
                backendMask = BackendStyleMask.WINDOWS
            ),
            CompiledStyleRule(
                selector = "button",
                segments = CompiledSelectorSegment.parseChain("button"),
                style = ComponentStyle().apply { setOpacity(0.95f) },
                backendMask = BackendStyleMask.IOS
            ),
            CompiledStyleRule(
                selector = "div",
                segments = CompiledSelectorSegment.parseChain("div"),
                style = ComponentStyle().apply { setOpacity(0.85f) },
                backendMask = BackendStyleMask.WINDOWS
            ),
            CompiledStyleRule(
                selector = "*",
                segments = CompiledSelectorSegment.parseChain("*"),
                style = ComponentStyle().apply { setOpacity(0.75f) },
                backendMask = BackendStyleMask.WEB
            ),
            CompiledStyleRule(
                selector = ".win-anc .shared-leaf",
                segments = listOf(
                    CompiledSelectorSegment(classes = setOf("win-anc"), combinatorToNext = CssCombinator.DESCENDANT),
                    CompiledSelectorSegment(classes = setOf("shared-leaf"))
                ),
                style = ComponentStyle().apply { setOpacity(0.5f) },
                backendMask = BackendStyleMask.WINDOWS
            ),
            CompiledStyleRule(
                selector = ".mac-anc .shared-leaf",
                segments = listOf(
                    CompiledSelectorSegment(classes = setOf("mac-anc"), combinatorToNext = CssCombinator.DESCENDANT),
                    CompiledSelectorSegment(classes = setOf("shared-leaf"))
                ),
                style = ComponentStyle().apply { setOpacity(0.6f) },
                mediaMaxWidthPx = 720,
                backendMask = BackendStyleMask.MACOS
            )
        )

        val fullMap = BlinkRuleBucketMap.fromCompiledStyleRules(rules)
        assertTrue(fullMap.hasBackendSpecificRules)
        assertSame(fullMap, fullMap.forBackend(null))

        val macMap = fullMap.forBackend(BackendTarget.MacOS)
        assertNotSame(fullMap, macMap)
        assertSame(macMap, fullMap.forBackend(BackendTarget.MacOS))
        assertSame(macMap, macMap.forBackend(BackendTarget.MacOS))

        // ID, class, tag, universal buckets exclude foreign-backend rules
        assertTrue("mac-header" in macMap.idBuckets)
        assertFalse("win-header" in macMap.idBuckets)
        assertTrue("mac-pill" in macMap.classBuckets)
        assertFalse("win-pill" in macMap.classBuckets)
        assertFalse("button" in macMap.tagBuckets)
        assertFalse("div" in macMap.tagBuckets)
        assertEquals(0, macMap.universalBucket.size)

        // Subject & ancestor class tracking excludes foreign-backend rules
        assertTrue("mac-pill" in macMap.referencedSubjectClasses)
        assertFalse("win-pill" in macMap.referencedSubjectClasses)
        assertTrue("mac-anc" in macMap.referencedAncestorClasses)
        assertFalse("win-anc" in macMap.referencedAncestorClasses)

        // Descendant invalidation excludes foreign-backend rules
        val macOutClasses = linkedSetOf<String>()
        val macOutIds = linkedSetOf<String>()
        val macOutTags = linkedSetOf<String>()
        val winAncInvalidationOnMac = macMap.collectDescendantInvalidation(
            changedClasses = listOf("win-anc"),
            ancestorNormalizedTag = "genericui:container",
            ancestorId = null,
            ancestorClasses = setOf("win-anc"),
            outSubjectClasses = macOutClasses,
            outSubjectIds = macOutIds,
            outSubjectTags = macOutTags
        )
        assertEquals(CssStyleSheet.DESCENDANT_INVALIDATION_NONE, winAncInvalidationOnMac)
        assertTrue(macOutClasses.isEmpty())

        val winMap = fullMap.forBackend(BackendTarget.Windows)
        val winOutClasses = linkedSetOf<String>()
        val winAncInvalidationOnWin = winMap.collectDescendantInvalidation(
            changedClasses = listOf("win-anc"),
            ancestorNormalizedTag = "genericui:container",
            ancestorId = null,
            ancestorClasses = setOf("win-anc"),
            outSubjectClasses = winOutClasses,
            outSubjectIds = linkedSetOf(),
            outSubjectTags = linkedSetOf()
        )
        assertEquals(CssStyleSheet.DESCENDANT_INVALIDATION_TARGETED, winAncInvalidationOnWin)
        assertTrue("shared-leaf" in winOutClasses)

        // Media breakpoints exclude foreign-backend rules
        assertEquals(listOf(720), macMap.mediaBreakpoints.toList())
        assertEquals(listOf(960), winMap.mediaBreakpoints.toList())
        assertEquals(macMap.resolveMediaQueryBucket(900), macMap.resolveMediaQueryBucket(1100))

        // Unmatched backend reuses BlinkRuleBucketMap.EMPTY
        assertSame(BlinkRuleBucketMap.EMPTY, fullMap.forBackend(BackendTarget.Android))

        // Direct targetBackend prefiltering on fromCompiledStyleRules
        val iosDirect = BlinkRuleBucketMap.fromCompiledStyleRules(rules, targetBackend = BackendTarget.IOS)
        assertTrue("button" in iosDirect.tagBuckets)
        assertFalse("mac-header" in iosDirect.idBuckets)
        assertFalse("win-pill" in iosDirect.classBuckets)

        // Round-trip backendMask via BlinkCompiledRule <-> CompiledStyleRule
        val macRule = macMap.allRules.first { it.selector == "#mac-header" }
        assertTrue(macRule.matchesBackend(BackendTarget.MacOS))
        assertFalse(macRule.matchesBackend(BackendTarget.Windows))
        assertEquals(BackendStyleMask.MACOS, macRule.toCompiledStyleRule().backendMask)
    }

    @Test
    fun testBlinkStyleEngineResolvesDistinctStylesAcrossBackendsAndComposedSheets() {
        try {
            val baseRules = listOf(
                CompiledStyleRule(
                    selector = ".titlebar",
                    style = ComponentStyle().apply {
                        setHeightPx(38)
                        setBackgroundColorHex("#112233")
                    },
                    backendMask = BackendStyleMask.MACOS
                ),
                CompiledStyleRule(
                    selector = ".titlebar",
                    style = ComponentStyle().apply {
                        setHeightPx(32)
                        setBackgroundColorHex("#223344")
                    },
                    backendMask = BackendStyleMask.WINDOWS
                ),
                CompiledStyleRule(
                    selector = ".titlebar",
                    style = ComponentStyle().apply {
                        setHeightPx(44)
                        setBackgroundColorHex("#334455")
                    },
                    backendMask = BackendStyleMask.IOS
                ),
                CompiledStyleRule(
                    selector = ".titlebar",
                    style = ComponentStyle().apply {
                        setHeightPx(48)
                        setBackgroundColorHex("#445566")
                    },
                    backendMask = BackendStyleMask.WEB
                ),
                CompiledStyleRule(
                    selector = ".btn",
                    style = ComponentStyle().apply {
                        setBorderRadiusPx(6f)
                    },
                    backendMask = BackendStyleMask.ALL
                )
            )
            val overrideRules = listOf(
                CompiledStyleRule(
                    selector = ".btn",
                    style = ComponentStyle().apply { setBorderRadiusPx(12f) },
                    backendMask = BackendStyleMask.MACOS
                ),
                CompiledStyleRule(
                    selector = ".btn",
                    style = ComponentStyle().apply { setBorderRadiusPx(2f) },
                    backendMask = BackendStyleMask.WINDOWS
                ),
                CompiledStyleRule(
                    selector = ".btn",
                    style = ComponentStyle().apply { setBorderRadiusPx(20f) },
                    backendMask = BackendStyleMask.IOS
                ),
                CompiledStyleRule(
                    selector = ".btn",
                    style = ComponentStyle().apply { setBorderRadiusPx(8f) },
                    backendMask = BackendStyleMask.WEB
                )
            )

            val baseBlinkSheet = object : BlinkStyleSheet() {
                override val name: String = "BaseBackendSheet"
                override val cssResourcePath: String = "test/base.css"
                override val ruleSet: BlinkRuleBucketMap = BlinkRuleBucketMap.fromCompiledStyleRules(baseRules)
            }
            val overrideBlinkSheet = object : BlinkStyleSheet() {
                override val name: String = "OverrideBackendSheet"
                override val cssResourcePath: String = "test/override.css"
                override val ruleSet: BlinkRuleBucketMap = BlinkRuleBucketMap.fromCompiledStyleRules(overrideRules)
            }

            BlinkStyleEngine.registerStyleSheet(baseBlinkSheet)
            BlinkStyleEngine.registerStyleSheet(overrideBlinkSheet)

            val combinedSheet = CssStyleSheet.combineCascade(
                listOf(baseBlinkSheet.styleSheet, overrideBlinkSheet.styleSheet)
            )

            data class ExpectedBackendStyle(
                val target: BackendTarget,
                val height: String,
                val bg: RgbaColor,
                val btnRadius: Float
            )

            val cases = listOf(
                ExpectedBackendStyle(BackendTarget.MacOS, "38px", RgbaColor(0x11, 0x22, 0x33, 255), 12f),
                ExpectedBackendStyle(BackendTarget.Windows, "32px", RgbaColor(0x22, 0x33, 0x44, 255), 2f),
                ExpectedBackendStyle(BackendTarget.IOS, "44px", RgbaColor(0x33, 0x44, 0x55, 255), 20f),
                ExpectedBackendStyle(BackendTarget.Web, "48px", RgbaColor(0x44, 0x55, 0x66, 255), 8f)
            )

            // Verify both composedRuleSetCache path (combinedSheet.applyToTree) and resolveTree path without clearing caches between switches
            val sharedTitlebar = StyledContainer("titlebar").apply { addClass("titlebar") }
            val sharedBtn = StyledComponent("btn").apply { addClass("btn") }
            sharedTitlebar.addChild(sharedBtn)

            for (case in cases) {
                GenericUI.activeFactory = FakeBackendFactory(case.target)
                combinedSheet.applyToTree(sharedTitlebar)
                assertEquals(case.height, sharedTitlebar.computedStyle?.height, "Height mismatch for ${case.target}")
                assertEquals(case.bg, sharedTitlebar.computedStyle?.backgroundColor, "Bg mismatch for ${case.target}")
                assertEquals(case.btnRadius, sharedBtn.computedStyle?.borderRadiusPx, "Radius mismatch for ${case.target}")

                val treeTitlebar = StyledContainer("titlebar").apply { addClass("titlebar") }
                val treeBtn = StyledComponent("btn").apply { addClass("btn") }
                treeTitlebar.addChild(treeBtn)
                BlinkStyleEngine.resolveTree(treeTitlebar)
                assertEquals(case.height, treeTitlebar.computedStyle?.height, "resolveTree height mismatch for ${case.target}")
                assertEquals(case.bg, treeTitlebar.computedStyle?.backgroundColor, "resolveTree bg mismatch for ${case.target}")
                assertEquals(case.btnRadius, treeBtn.computedStyle?.borderRadiusPx, "resolveTree radius mismatch for ${case.target}")
            }
        } finally {
            BlinkStyleEngine.clear()
            GenericUI.resetForTests()
        }
    }
}
