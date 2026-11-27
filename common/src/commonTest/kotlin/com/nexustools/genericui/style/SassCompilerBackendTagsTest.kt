package com.nexustools.genericui.style

import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.shell.Application
import com.nexustools.genericui.component.surface.styled.StyledComponent
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
import com.nexustools.genericui.nativeimpl.form.StyledTextInputNativeImpl
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
import com.nexustools.genericui.style.css.CssStyleSheet
import com.nexustools.genericui.style.sass.LiveScssStyleEngine
import com.nexustools.genericui.style.sass.SassCompiler
import com.nexustools.genericui.style.sass.SassKotlinPrecompiler
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SassCompilerBackendTagsTest {

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
        override fun createStyledTextInput(multiline: Boolean, classPrefix: String): StyledTextInputNativeImpl<*> = unused()
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

    @AfterTest
    fun tearDown() {
        GenericUI.resetForTests()
    }

    @Test
    fun testTopLevelNestedSelectorAndIntersectionCompilation() {
        val scss = """
            ${'$'}base-pad: 6px;
            .card {
              padding: ${'$'}base-pad;
              @macos {
                border-radius: 12px;
                &:hover { opacity: 0.9; }
              }
              @backend(desktop and not macos) {
                border-radius: 4px;
              }
              @desktop {
                @apple {
                  z-index: 5;
                }
                @mobile {
                  z-index: 999;
                }
              }
            }
            @web {
              .web-only { display: flex; }
            }
        """.trimIndent()

        // Unfiltered output keeps canonical directives, intersects desktop & apple -> @macos, and prunes desktop & mobile
        val genericCss = SassCompiler.compileToCss(scss)
        assertTrue(genericCss.contains("@macos"))
        assertTrue(genericCss.contains("@backend(windows, gtk, kde, swing)"))
        assertTrue(genericCss.contains("@web"))
        assertFalse(genericCss.contains("999"), "Disjoint @desktop > @mobile rule must be pruned")

        // Target-specific output unwraps matching rules and strips directives
        val macCss = SassCompiler.compileToCss(scss, BackendTarget.MacOS)
        assertFalse(macCss.contains("@macos"))
        assertFalse(macCss.contains("@backend"))
        assertFalse(macCss.contains("@web"))
        assertTrue(macCss.contains("border-radius: 12px"))
        assertTrue(macCss.contains(".card:hover"))
        assertTrue(macCss.contains("z-index: 5"))
        assertFalse(macCss.contains("border-radius: 4px"))

        val winCss = SassCompiler.compileToCss(scss, BackendTarget.Windows)
        assertFalse(winCss.contains("@macos"))
        assertFalse(winCss.contains("@backend"))
        assertTrue(winCss.contains("border-radius: 4px"))
        assertFalse(winCss.contains("border-radius: 12px"))
        assertFalse(winCss.contains("z-index: 5"))

        val webCss = SassCompiler.compileToWebCss(scss)
        assertFalse(BackendStyleMask.containsGenericUiBackendDirective(webCss))
        assertTrue(webCss.contains(".web-only"))
        assertTrue(webCss.contains("padding: 6px"))
        assertFalse(webCss.contains("border-radius: 12px"))
    }

    @Test
    fun testMediaCompositionInBothOrdersAndMixinsAndKeyframes() {
        val scss = """
            @mixin mac-glass(${'$'}radius: 10px) {
              @macos {
                border-radius: ${'$'}radius;
              }
            }

            .panel {
              @include mac-glass(14px);
              @media (min-width: 768px) {
                @linux {
                  width: 300px;
                }
              }
            }

            @windows {
              @media (max-width: 1024px) {
                .panel { width: 240px; }
              }
              @keyframes winPulse {
                from { opacity: 0.4; }
                to { opacity: 1; }
              }
            }

            @web {
              @keyframes webSpin {
                from { opacity: 0; }
                to { opacity: 1; }
              }
            }
        """.trimIndent()

        val compiled = SassCompiler.compileToCss(scss)
        val sheet = CssStyleSheet.parse(compiled)

        val macRule = sheet.rules.single { it.declarations["border-radius"] == "14px" }
        assertEquals(BackendStyleMask.MACOS, macRule.backendMask)

        val linuxMediaRule = sheet.rules.single { it.declarations["width"] == "300px" }
        assertEquals(BackendStyleMask.LINUX, linuxMediaRule.backendMask)
        assertEquals(768, linuxMediaRule.mediaMinWidthPx)

        val winMediaRule = sheet.rules.single { it.declarations["width"] == "240px" }
        assertEquals(BackendStyleMask.WINDOWS, winMediaRule.backendMask)
        assertEquals(1024, winMediaRule.mediaMaxWidthPx)

        assertTrue("winPulse" in sheet.keyframes)
        assertTrue("webSpin" in sheet.keyframes)

        val winSheet = sheet.filterForBackend(BackendTarget.Windows)
        assertTrue("winPulse" in winSheet.keyframes)
        assertFalse("webSpin" in winSheet.keyframes)
        assertEquals(1, winSheet.rules.size)
        assertSame(winSheet, sheet.filterForBackend(BackendTarget.Windows))

        val webCss = sheet.toWebCss()
        assertFalse(BackendStyleMask.containsGenericUiBackendDirective(webCss))
        assertTrue(webCss.contains("webSpin"))
        assertFalse(webCss.contains("winPulse"))
        assertFalse(webCss.contains("300px"))
        assertFalse(webCss.contains("240px"))

        val compiledWeb = SassCompiler.compileToWebCss(scss)
        assertTrue(compiledWeb.contains("webSpin"))
        assertFalse(compiledWeb.contains("winPulse"))
        assertFalse(compiledWeb.contains("@media"))
    }

    @Test
    fun testValidateScssReportsLineAndColumnOnInvalidBackendDirective() {
        val badScss = """
            .valid { color: #111111; }
            @backend(macos and ) {
              .broken { color: #222222; }
            }
            @platform {
              .missing-parens { color: #333333; }
            }
        """.trimIndent()

        val errors = SassCompiler.validateScss(badScss)
        assertEquals(2, errors.size)
        assertEquals(2, errors[0].line)
        assertEquals(1, errors[0].column)
        assertEquals(5, errors[1].line)
        assertEquals(1, errors[1].column)

        val detailed = SassCompiler.compileDetailed(badScss)
        assertFalse(detailed.isSuccess)
        assertEquals(2, detailed.errors.size)
    }

    @Test
    fun testPrecompilerAndLiveEnginePropagateBackendMasksAndWebCss() {
        val scss = """
            .chip {
              padding: 4px;
              @macos { background-color: #ff0000; }
              @web { background-color: #00ff00; }
              @backend(ios, ipados) { background-color: #0000ff; }
            }
        """.trimIndent()

        val precompiled = SassKotlinPrecompiler.precompile(scss, name = "chips", resourcePath = "styles/chips.css")
        assertFalse(BackendStyleMask.containsGenericUiBackendDirective(precompiled.rawCss))
        assertTrue(precompiled.rawCss.contains("#00ff00"))
        assertFalse(precompiled.rawCss.contains("#ff0000"))

        val macRules = precompiled.rulesForBackend(BackendTarget.MacOS)
        assertEquals(2, macRules.size)
        assertEquals(2, precompiled.styleSheetForBackend(BackendTarget.MacOS).rules.size)
        val macRaw = precompiled.rawCssForBackend(BackendTarget.MacOS)
        assertTrue(macRaw.contains("rgb(255, 0, 0)"))
        assertFalse(macRaw.contains("#00ff00") || macRaw.contains("0, 255, 0"))

        val generatedKt = SassKotlinPrecompiler.generateKotlinSource(
            scssSource = scss,
            packageName = "com.example.styles",
            objectName = "ChipsStyle"
        )
        assertTrue(generatedKt.contains("import com.nexustools.genericui.style.css.BackendStyleMask"))
        assertTrue(generatedKt.contains("backendMask = BackendStyleMask.MACOS"))
        assertTrue(generatedKt.contains("backendMask = BackendStyleMask.WEB"))
        assertTrue(generatedKt.contains("backendMask = BackendStyleMask.IOS_FAMILY"))

        val engine = LiveScssStyleEngine(scss, targetBackend = BackendTarget.Gtk)
        assertTrue(engine.lastCompileResult.isSuccess)
        assertFalse(engine.compiledCss.contains("#ff0000"))
        assertFalse(engine.compiledCss.contains("@"))
        assertTrue(engine.compiledWebCss.contains("#00ff00"))
        assertFalse(engine.compiledWebCss.contains("@"))
    }

    @Test
    fun testRuntimeStyleResolutionAndMediaInvalidationAcrossSimulatedBackends() {
        val sheet = SassCompiler.compileToStyleSheet(
            """
            .badge {
              padding: 4px;
              @macos { background-color: #112233; }
              @windows { background-color: #445566; }
              @web { background-color: #778899; }
              @media (min-width: 900px) {
                @macos { width: 200px; }
              }
              @media (min-width: 700px) {
                @windows { width: 150px; }
              }
            }
            .root-box .item {
              @macos { opacity: 0.5; }
              @windows { opacity: 0.8; }
            }
            """.trimIndent()
        )

        // When no factory and no Application are active, resolveRuntimeBackendTarget() uses host default
        GenericUI.resetForTests()
        val hostDefault = BackendStyleMask.resolveRuntimeBackendTarget()
        val uninitialized = sheet.resolveStyleForClasses(setOf("badge"), viewportWidthPx = 800)
        assertEquals("4px", uninitialized.padding?.top?.toCssString())
        when (hostDefault) {
            BackendTarget.MacOS -> assertEquals(RgbaColor.fromHex("#112233"), uninitialized.backgroundColor)
            BackendTarget.Windows -> assertEquals(RgbaColor.fromHex("#445566"), uninitialized.backgroundColor)
            BackendTarget.Web -> assertEquals(RgbaColor.fromHex("#778899"), uninitialized.backgroundColor)
            else -> assertNull(uninitialized.backgroundColor)
        }

        try {
            GenericUI.initialize(FakeBackendFactory(BackendTarget.MacOS))
            val macStyle = sheet.resolveStyleForClasses(setOf("badge"), viewportWidthPx = 800)
            assertEquals(RgbaColor.fromHex("#112233"), macStyle.backgroundColor)
            assertNull(macStyle.width)
            val macWide = sheet.resolveStyleForClasses(setOf("badge"), viewportWidthPx = 950)
            assertEquals("200px", macWide.width)

            val macBucketBefore = sheet.resolveMediaQueryBucket(650)
            val macBucketAfter = sheet.resolveMediaQueryBucket(750)
            assertEquals(macBucketBefore, macBucketAfter, "Windows-only 700px breakpoint must not change macOS bucket")

            // Switching to Windows must NOT reuse macOS cached styleLookupCache entry
            GenericUI.initialize(FakeBackendFactory(BackendTarget.Windows))
            val winStyle = sheet.resolveStyleForClasses(setOf("badge"), viewportWidthPx = 800)
            assertEquals(RgbaColor.fromHex("#445566"), winStyle.backgroundColor)
            assertEquals("150px", winStyle.width)

            val mediaClasses = linkedSetOf<String>()
            val mediaIds = linkedSetOf<String>()
            val mediaTags = linkedSetOf<String>()
            val invMode = sheet.collectMediaInvalidation(850, 950, mediaClasses, mediaIds, mediaTags)
            assertEquals(CssStyleSheet.DESCENDANT_INVALIDATION_NONE, invMode, "macOS-only 900px breakpoint must not invalidate on Windows")

            // Descendant invalidation respects activeTarget
            val rootBox = Container().apply { addClass("root-box") }
            val item = StyledComponent("item").apply { addClass("item") }
            rootBox.addChild(item)
            val app = Application(initialContent = rootBox)
            assertNotNull(app)
            val resolvedWinItem = sheet.resolveStyleForComponent(item)
            assertEquals(0.8f, resolvedWinItem.opacity)

            GenericUI.initialize(FakeBackendFactory(BackendTarget.MacOS))
            val resolvedMacItem = sheet.resolveStyleForComponent(item)
            assertEquals(0.5f, resolvedMacItem.opacity)

            GenericUI.initialize(FakeBackendFactory(BackendTarget.Web))
            val webStyle = sheet.resolveStyleForClasses(setOf("badge"), viewportWidthPx = 1000)
            assertEquals(RgbaColor.fromHex("#778899"), webStyle.backgroundColor)
            assertNull(webStyle.width)
        } finally {
            GenericUI.resetForTests()
        }
    }
}
