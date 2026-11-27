package com.nexustools.genericui.window

import com.nexustools.genericui.component.action.Button
import com.nexustools.genericui.component.dialog.DialogWindow
import com.nexustools.genericui.component.dock.DockPanel
import com.nexustools.genericui.component.dock.PanelWindow
import com.nexustools.genericui.component.fragment.AppFragment
import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.fragment.FragmentBundle
import com.nexustools.genericui.component.fragment.FragmentRegistry
import com.nexustools.genericui.component.fragment.IFragment
import com.nexustools.genericui.component.fragment.MainAppFragment
import com.nexustools.genericui.component.layout.LayoutBackendTarget
import com.nexustools.genericui.component.layout.styled.StyledContainer
import com.nexustools.genericui.component.shell.FragmentApplication
import com.nexustools.genericui.component.shell.IApplication
import com.nexustools.genericui.component.shell.MdiContainer
import com.nexustools.genericui.component.shell.MdiWindow
import com.nexustools.genericui.component.shell.Window
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.enums.dock.DockPosition
import com.nexustools.genericui.enums.system.AppTarget
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
import com.nexustools.genericui.style.css.CssFilter
import com.nexustools.genericui.style.css.CssLength
import com.nexustools.genericui.style.css.StyledVisualSpec
import com.nexustools.genericui.style.policy.AestheticStylePolicy
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Unit tests for [TitleCapabilities], [IWindow], [IApplication], [IFragment],
 * [BackendTarget], [AppTarget], [LayoutBackendTarget], [AestheticStylePolicy],
 * [StyledVisualSpec], [AppFragment], [MainAppFragment], [FragmentBundle]
 * CLI argument helpers, and single-placement [TitleBar] `mountedInTitleBar` marking.
 */
class WindowAndApplicationCapabilitiesTest {

    @MainAppFragment(targets = [AppTarget.COMMON], label = "Home", route = "home")
    private class SampleMainFragment : Fragment() {
        var createdBundle: FragmentBundle? = null
        override fun onCreate(savedState: FragmentBundle?) {
            super.onCreate(savedState)
            createdBundle = savedState
        }
    }

    @AppFragment(targets = [AppTarget.DESKTOP_COMMON, AppTarget.WEB], label = "Inspector", route = "inspector")
    private class SampleDetailFragment : Fragment()

    @Test
    fun testBackendTargetGroupPredicatesAndLookup() {
        assertEquals(9, BackendTarget.entries.size)

        assertTrue(BackendTarget.Windows.isSystem)
        assertTrue(BackendTarget.Windows.isDesktop)
        assertTrue(BackendTarget.Windows.isNativeDesktop)
        assertFalse(BackendTarget.Windows.isLinux)
        assertFalse(BackendTarget.Windows.isMobile)
        assertFalse(BackendTarget.Windows.isIosFamily)
        assertFalse(BackendTarget.Windows.isBrowser)

        assertTrue(BackendTarget.Gtk.isLinux)
        assertTrue(BackendTarget.Kde.isLinux)
        assertTrue(BackendTarget.Swing.isDesktop)
        assertFalse(BackendTarget.Swing.isNativeDesktop)
        assertTrue(BackendTarget.Swing.isJvm)

        assertTrue(BackendTarget.Android.isMobile)
        assertTrue(BackendTarget.Android.isTouchPrimary)
        assertTrue(BackendTarget.Android.isJvm)
        assertFalse(BackendTarget.Android.isIosFamily)

        assertTrue(BackendTarget.IOS.isMobile)
        assertTrue(BackendTarget.IOS.isApple)
        assertTrue(BackendTarget.IOS.isIosFamily)
        assertTrue(BackendTarget.MacOS.isApple)
        assertFalse(BackendTarget.MacOS.isIosFamily)

        assertTrue(BackendTarget.IPadOS.isSystem)
        assertTrue(BackendTarget.IPadOS.isMobile)
        assertTrue(BackendTarget.IPadOS.isApple)
        assertTrue(BackendTarget.IPadOS.isIosFamily)
        assertTrue(BackendTarget.IPadOS.isUnix)
        assertTrue(BackendTarget.IPadOS.isPosix)
        assertTrue(BackendTarget.IPadOS.isNative)
        assertTrue(BackendTarget.IPadOS.isTouchPrimary)
        assertFalse(BackendTarget.IPadOS.isDesktop)
        assertFalse(BackendTarget.IPadOS.isNativeDesktop)
        assertFalse(BackendTarget.IPadOS.isLinux)
        assertFalse(BackendTarget.IPadOS.isJvm)
        assertFalse(BackendTarget.IPadOS.isBrowser)

        assertFalse(BackendTarget.Web.isSystem)
        assertTrue(BackendTarget.Web.isBrowser)

        assertEquals("windows", BackendTarget.Windows.id)
        assertEquals("macos", BackendTarget.MacOS.id)
        assertEquals("gtk", BackendTarget.Gtk.id)
        assertEquals("kde", BackendTarget.Kde.id)
        assertEquals("swing", BackendTarget.Swing.id)
        assertEquals("android", BackendTarget.Android.id)
        assertEquals("ios", BackendTarget.IOS.id)
        assertEquals("ipados", BackendTarget.IPadOS.id)
        assertEquals("web", BackendTarget.Web.id)

        assertEquals(BackendTarget.Kde, BackendTarget.fromId("kde"))
        assertEquals(BackendTarget.IPadOS, BackendTarget.fromId("ipados"))
        assertEquals(BackendTarget.IPadOS, BackendTarget.fromId("  IPadOS "))
        assertEquals(BackendTarget.IOS, BackendTarget.fromId("ios"))
        assertEquals(BackendTarget.MacOS, BackendTarget.fromId("macos"))
        assertNull(BackendTarget.fromId("qt"))
        assertNull(BackendTarget.fromId("unknown-target"))
        assertNull(BackendTarget.fromId(null))
    }

    @Test
    fun testAppTargetSpecificityChainAndMatching() {
        assertEquals(0, AppTarget.COMMON.specificityRank)
        assertEquals(1, AppTarget.SYSTEM_COMMON.specificityRank)
        assertEquals(2, AppTarget.APPLE_COMMON.specificityRank)
        assertEquals(2, AppTarget.JVM_COMMON.specificityRank)
        assertEquals(3, AppTarget.DESKTOP_COMMON.specificityRank)
        assertEquals(3, AppTarget.MOBILE_COMMON.specificityRank)
        assertEquals(4, AppTarget.NATIVE_DESKTOP_COMMON.specificityRank)
        assertEquals(5, AppTarget.LINUX_COMMON.specificityRank)
        assertEquals(5, AppTarget.IOS_COMMON.specificityRank)
        assertEquals(6, AppTarget.IPADOS.specificityRank)

        assertEquals(AppTarget.KDE, AppTarget.fromBackendTarget(BackendTarget.Kde))
        assertEquals(AppTarget.IPADOS, AppTarget.fromBackendTarget(BackendTarget.IPadOS))

        assertEquals(
            listOf(
                AppTarget.KDE,
                AppTarget.LINUX_COMMON,
                AppTarget.NATIVE_DESKTOP_COMMON,
                AppTarget.DESKTOP_COMMON,
                AppTarget.SYSTEM_COMMON,
                AppTarget.COMMON
            ),
            AppTarget.specificityChainFor(BackendTarget.Kde)
        )
        assertEquals(
            listOf(
                AppTarget.GTK,
                AppTarget.LINUX_COMMON,
                AppTarget.NATIVE_DESKTOP_COMMON,
                AppTarget.DESKTOP_COMMON,
                AppTarget.SYSTEM_COMMON,
                AppTarget.COMMON
            ),
            AppTarget.specificityChainFor(BackendTarget.Gtk)
        )
        assertEquals(
            listOf(
                AppTarget.MACOS,
                AppTarget.NATIVE_DESKTOP_COMMON,
                AppTarget.DESKTOP_COMMON,
                AppTarget.APPLE_COMMON,
                AppTarget.SYSTEM_COMMON,
                AppTarget.COMMON
            ),
            AppTarget.specificityChainFor(BackendTarget.MacOS)
        )
        assertEquals(
            listOf(
                AppTarget.SWING,
                AppTarget.DESKTOP_COMMON,
                AppTarget.JVM_COMMON,
                AppTarget.SYSTEM_COMMON,
                AppTarget.COMMON
            ),
            AppTarget.specificityChainFor(BackendTarget.Swing)
        )
        assertEquals(
            listOf(
                AppTarget.ANDROID,
                AppTarget.MOBILE_COMMON,
                AppTarget.JVM_COMMON,
                AppTarget.SYSTEM_COMMON,
                AppTarget.COMMON
            ),
            AppTarget.specificityChainFor(BackendTarget.Android)
        )
        assertEquals(
            listOf(
                AppTarget.IOS,
                AppTarget.IOS_COMMON,
                AppTarget.MOBILE_COMMON,
                AppTarget.APPLE_COMMON,
                AppTarget.SYSTEM_COMMON,
                AppTarget.COMMON
            ),
            AppTarget.specificityChainFor(BackendTarget.IOS)
        )
        assertEquals(
            listOf(
                AppTarget.IPADOS,
                AppTarget.IOS_COMMON,
                AppTarget.MOBILE_COMMON,
                AppTarget.APPLE_COMMON,
                AppTarget.SYSTEM_COMMON,
                AppTarget.COMMON
            ),
            AppTarget.specificityChainFor(BackendTarget.IPadOS)
        )
        assertEquals(
            listOf(AppTarget.WEB, AppTarget.COMMON),
            AppTarget.specificityChainFor(BackendTarget.Web)
        )

        for (backend in BackendTarget.entries) {
            val chain = AppTarget.specificityChainFor(backend)
            val ranks = chain.map { it.specificityRank }
            assertEquals(ranks.distinct(), ranks, "Chain for $backend must have unique ranks: $chain")
            for (i in 0 until ranks.lastIndex) {
                assertTrue(ranks[i] > ranks[i + 1], "Chain for $backend must be strictly descending: $chain")
            }
            val matching = AppTarget.entries.filter { it.matches(backend) }.toSet()
            assertEquals(chain.toSet(), matching, "Chain for $backend must match all matching AppTargets")
        }

        val desktopAndCommon = arrayOf(AppTarget.COMMON, AppTarget.DESKTOP_COMMON)
        assertEquals(3, AppTarget.resolveHighestSpecificityRank(desktopAndCommon, BackendTarget.Windows))
        assertEquals(0, AppTarget.resolveHighestSpecificityRank(desktopAndCommon, BackendTarget.Android))
        assertEquals(6, AppTarget.resolveHighestSpecificityRank(arrayOf(AppTarget.WINDOWS), BackendTarget.Windows))
        assertEquals(6, AppTarget.resolveHighestSpecificityRank(arrayOf(AppTarget.IPADOS, AppTarget.IOS_COMMON), BackendTarget.IPadOS))
        assertEquals(5, AppTarget.resolveHighestSpecificityRank(arrayOf(AppTarget.IPADOS, AppTarget.IOS_COMMON), BackendTarget.IOS))
        assertEquals(2, AppTarget.resolveHighestSpecificityRank(arrayOf(AppTarget.APPLE_COMMON, AppTarget.COMMON), BackendTarget.IPadOS))
        assertEquals(2, AppTarget.resolveHighestSpecificityRank(arrayOf(AppTarget.JVM_COMMON, AppTarget.COMMON), BackendTarget.Swing))
        assertNull(AppTarget.resolveHighestSpecificityRank(arrayOf(AppTarget.WINDOWS), BackendTarget.Web))
    }

    @Test
    fun testLayoutBackendTargetAndPolicyAndStyledVisualSpecForIPadOS() {
        assertEquals(9, LayoutBackendTarget.entries.size)
        assertTrue(LayoutBackendTarget.IPADOS.isMobile)
        assertFalse(LayoutBackendTarget.IPADOS.isDesktop)
        assertFalse(LayoutBackendTarget.IPADOS.isWeb)
        assertEquals(BackendTarget.IPadOS, LayoutBackendTarget.IPADOS.toBackendTarget())
        assertEquals(LayoutBackendTarget.IPADOS, LayoutBackendTarget.fromBackendTarget(BackendTarget.IPadOS))
        assertEquals(LayoutBackendTarget.IPADOS, LayoutBackendTarget.fromId("ipados"))
        assertEquals(BackendTarget.Kde, LayoutBackendTarget.KDE.toBackendTarget())
        assertEquals(LayoutBackendTarget.KDE, LayoutBackendTarget.fromBackendTarget(BackendTarget.Kde))
        assertEquals(LayoutBackendTarget.KDE, LayoutBackendTarget.fromId("kde"))
        assertNull(LayoutBackendTarget.fromId("qt"))

        val styled = StyledContainer()
        val styledButton = com.nexustools.genericui.component.action.styled.StyledButton("Go")
        val regularButton = Button("Ok")
        for (target in LayoutBackendTarget.entries) {
            assertTrue(AestheticStylePolicy.supportsBackdropFilterBlur(styled, target))
            assertTrue(AestheticStylePolicy.supportsBackdropFilterBlur(styledButton, target))
            assertFalse(AestheticStylePolicy.supportsBackdropFilterBlur(regularButton, target))
        }
        styled.setBackdropBlur(12)
        assertEquals(12f, styled.backdropFilter?.blur?.resolvePx())
        assertEquals(12f, styled.resolveVisualSpec().backdropBlurPx)
        assertTrue(AestheticStylePolicy.supportsBackdropFilterBlur(styled, BackendTarget.IPadOS))
        assertTrue(AestheticStylePolicy.supportsBackdropFilterBlur(styled, BackendTarget.Windows))
        assertTrue(AestheticStylePolicy.supportsBackdropFilterBlur(styledButton, BackendTarget.IPadOS))
        assertTrue(AestheticStylePolicy.supportsBackdropFilterBlur(styledButton, BackendTarget.Windows))

        styledButton.backdropFilter = CssFilter(blur = CssLength.px(10f))
        assertEquals(10f, styledButton.backdropFilter?.blur?.resolvePx())
        assertEquals(10f, styledButton.resolveCurrentVisualSpec().backdropBlurPx)
        styledButton.setBackdropBlur(0)
        assertNull(styledButton.backdropFilter)

        val spec = StyledVisualSpec(
            backgroundColor = RgbaColor(20, 30, 40, 0.8f),
            backdropBlurPx = 12f,
            backdropFilter = CssFilter(blur = CssLength.px(12f))
        )
        val ipadSpec = spec.forBackend(LayoutBackendTarget.IPADOS)
        assertEquals(12f, ipadSpec.backdropBlurPx)
        assertNotNull(ipadSpec.backdropFilter)

        val macSpec = spec.forBackend(LayoutBackendTarget.MACOS)
        assertEquals(12f, macSpec.backdropBlurPx)
        assertNotNull(macSpec.backdropFilter)

        val winSpec = spec.forBackend(LayoutBackendTarget.WINDOWS)
        assertEquals(12f, winSpec.backdropBlurPx)
        assertNotNull(winSpec.backdropFilter)

        val stripped = spec.withoutBackdropFilter()
        assertNull(stripped.backdropBlurPx)
        assertNull(stripped.backdropFilter)
    }

    @Test
    fun testFragmentBundleCommandLineArgsHelpers() {
        val rawArgs = arrayOf("--project", "demo.hacanvas", "--headless")
        val bundle = FragmentBundle.fromCommandLineArgs(rawArgs)

        assertTrue(bundle.hasCommandLineArgs())
        assertEquals(listOf("--project", "demo.hacanvas", "--headless"), bundle.getCommandLineArgs())
        assertContentEquals(rawArgs, bundle.getCommandLineArgsArray())

        val emptyBundle = FragmentBundle()
        assertFalse(emptyBundle.hasCommandLineArgs())
        assertTrue(emptyBundle.getCommandLineArgs().isEmpty())
    }

    @Test
    fun testIWindowImplementationsAndSinglePlacementTitleBarMounting() {
        val mainWindow: IWindow = Window("Main App")
        assertEquals("Main App", mainWindow.getTitle())
        assertTrue(mainWindow.titleCapabilities.canEmbedComponentsInTitleBar)

        val actionBtn = Button("Settings")
        assertFalse(actionBtn.mountedInTitleBar)

        val tb = TitleBar()
        tb.addTrailing(actionBtn)
        mainWindow.setTitleBar(tb)
        assertTrue(actionBtn.mountedInTitleBar)

        tb.clearComponents()
        assertFalse(actionBtn.mountedInTitleBar)

        val panelWindow: IWindow = PanelWindow("Floating Tool", ownerWindow = mainWindow as Window)
        assertTrue(panelWindow.titleCapabilities.isToolWindowTitleBar)
        assertFalse(panelWindow.titleCapabilities.supportsMinimizeButton)
        assertFalse(panelWindow.titleCapabilities.supportsMaximizeButton)

        val dialogWindow: IWindow = DialogWindow("Preferences")
        assertEquals("Preferences", dialogWindow.getTitle())
        assertTrue(dialogWindow.titleCapabilities.isEmulated)

        val mdiContainer = MdiContainer()
        val mdiWindow: IWindow = MdiWindow(windowId = "doc-1", title = "Document 1")
        mdiContainer.addWindow(mdiWindow as MdiWindow)
        val mdiBtn = Button("Pin")
        val mdiTb = TitleBar().addTrailing(mdiBtn)
        mdiWindow.setTitleBar(mdiTb)
        assertTrue(mdiBtn.mountedInTitleBar)

        val dockPanel = DockPanel(panelId = "inspector", title = "Inspector", preferredPosition = DockPosition.RIGHT)
        val dockBtn = Button("Gear")
        val dockTb = TitleBar().addTrailing(dockBtn)
        dockPanel.setTitleBar(dockTb)
        assertTrue(dockBtn.mountedInTitleBar)
    }

    private class MinimalCapabilitiesNativeFactory : NativeFactory {
        private fun unsupported(): Nothing = throw UnsupportedOperationException("Unused in test")
        override val backendTarget: BackendTarget = BackendTarget.Swing
        override fun createApplication(): ApplicationNativeImpl<*> = unsupported()
        override fun createWindow(): WindowNativeImpl<*> = unsupported()
        override fun createMenuBar(): MenuBarNativeImpl<*> = unsupported()
        override fun createMenu(): MenuNativeImpl<*> = unsupported()
        override fun createMenuItem(checkable: Boolean, radio: Boolean): MenuItemNativeImpl<*> = unsupported()
        override fun createContextMenu(): ContextMenuNativeImpl<*> = unsupported()
        override fun createDockWorkspace(): DockWorkspaceNativeImpl<*> = unsupported()
        override fun createDockArea(): DockAreaNativeImpl<*> = unsupported()
        override fun createDockGroup(): DockGroupNativeImpl<*> = unsupported()
        override fun createDockPanel(): DockPanelNativeImpl<*> = unsupported()
        override fun createDockWindow(): DockWindowNativeImpl<*> = unsupported()
        override fun createToolPalette(): ToolPaletteNativeImpl<*> = unsupported()
        override fun createContainer(classPrefix: String): ContainerNativeImpl<*> = unsupported()
        override fun createStyledContainer(classPrefix: String, tagName: String): StyledContainerNativeImpl<*> = unsupported()
        override fun createStyledComponent(
            classPrefix: String,
            tagName: String
        ): com.nexustools.genericui.nativeimpl.surface.StyledComponentNativeImpl<*> = unsupported()
        override fun createBookContainer(): BookContainerNativeImpl<*> = unsupported()
        override fun createScrollView(): ScrollViewNativeImpl<*> = unsupported()
        override fun createDivider(): DividerNativeImpl<*> = unsupported()
        override fun createText(tagName: String, classPrefix: String): TextNativeImpl<*> = unsupported()
        override fun createMarkdownView(): MarkdownViewNativeImpl<*> = unsupported()
        override fun createImage(): ImageNativeImpl<*> = unsupported()
        override fun createTable(): TableNativeImpl<*> = unsupported()
        override fun createButton(classPrefix: String): ButtonNativeImpl<*> = unsupported()
        override fun createStyledButton(classPrefix: String): StyledButtonNativeImpl<*> = unsupported()
        override fun createDropdown(): DropdownNativeImpl<*> = unsupported()
        override fun createTextInput(multiline: Boolean): TextInputNativeImpl<*> = unsupported()
        override fun createStyledTextInput(multiline: Boolean, classPrefix: String): StyledTextInputNativeImpl<*> = unsupported()
        override fun createSelect(): SelectNativeImpl<*> = unsupported()
        override fun createCheckbox(switchStyle: Boolean, radioStyle: Boolean): CheckboxNativeImpl<*> = unsupported()
        override fun createRangeSlider(): RangeSliderNativeImpl<*> = unsupported()
        override fun createTab(closable: Boolean): TabNativeImpl<*> = unsupported()
        override fun createTabContainer(): TabContainerNativeImpl<*> = unsupported()
        override fun createNavbar(): NavbarNativeImpl<*> = unsupported()
        override fun createBottomNavigationBar(): BottomNavigationBarNativeImpl<*> = unsupported()
        override fun createProgressBar(): ProgressBarNativeImpl<*> = unsupported()
        override fun createSpinner(): SpinnerNativeImpl<*> = unsupported()
        override fun createPopover(): PopoverNativeImpl<*> = unsupported()
        override fun createDialogWindow(): DialogWindowNativeImpl<*> = unsupported()
        override fun createFileBrowserDialog(): FileBrowserDialogNativeImpl = unsupported()
        override fun createStandardDialogs(): StandardDialogsNativeImpl = unsupported()
        override fun createLazyList(): LazyListNativeImpl<*> = unsupported()
        override fun createLazyGrid(): LazyGridNativeImpl<*> = unsupported()
        override fun createTreeView(): TreeViewNativeImpl<*> = unsupported()
        override fun createPermissionManager(): PermissionManagerNativeImpl = unsupported()
        override fun createSystemTray(): SystemTrayNativeImpl = unsupported()
        override fun createNotificationManager(): NotificationManagerNativeImpl = unsupported()
        override fun createNetworkMonitor(): NetworkMonitorNativeImpl = object : NetworkMonitorNativeImpl {
            override var isDisposed: Boolean = false
            override fun dispose() { isDisposed = true }
            override fun isOnline(): Boolean = true
            override fun setOnConnectivityChanged(listener: ((Boolean) -> Unit)?) {}
        }
        override fun supportsTcp(): Boolean = false
        override fun supportsUdp(): Boolean = false
    }

    @Test
    fun testIApplicationAndIFragmentLifecycleWithLaunchCoordinator() {
        val prevFactory = GenericUI.activeFactory
        try {
            GenericUI.initialize(MinimalCapabilitiesNativeFactory())
            FragmentRegistry.register(SampleMainFragment::class) { SampleMainFragment() }
            FragmentRegistry.register(SampleDetailFragment::class) { SampleDetailFragment() }

            val cliBundle = FragmentBundle.fromCommandLineArgs(listOf("--mode=test"))
            val mainFrag = SampleMainFragment().apply {
                arguments = cliBundle
            }
            val app = FragmentApplication(initialFragment = mainFrag, title = "TestApp")
            val iApp: IApplication = app
            assertEquals("TestApp", iApp.mainIWindow.getTitle())
            assertSame(mainFrag, app.currentFragment)
            assertNotNull(mainFrag.createdBundle)
            assertTrue(mainFrag.createdBundle!!.hasCommandLineArgs())
            assertEquals(listOf("--mode=test"), mainFrag.createdBundle!!.getCommandLineArgs())

            val detail: SampleDetailFragment = mainFrag.startFragment<SampleDetailFragment>(
                launchArgs = FragmentBundle.of("tab" to "layers"),
                addToBackStack = true
            )
            assertSame(detail, app.currentFragment)
            assertEquals("layers", detail.arguments?.getString("tab"))
            assertTrue(app.canGoBack)

            assertTrue(detail.finishFragment())
            assertSame(mainFrag, app.currentFragment)
            app.dispose()
        } finally {
            GenericUI.resetForTests()
            GenericUI.activeFactory = prevFactory
        }
    }
}
