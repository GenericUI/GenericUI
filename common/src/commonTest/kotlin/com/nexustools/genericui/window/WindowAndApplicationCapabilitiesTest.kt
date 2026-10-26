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
import com.nexustools.genericui.component.shell.FragmentApplication
import com.nexustools.genericui.component.shell.IApplication
import com.nexustools.genericui.component.shell.MdiContainer
import com.nexustools.genericui.component.shell.MdiWindow
import com.nexustools.genericui.component.shell.Window
import com.nexustools.genericui.enums.dock.DockPosition
import com.nexustools.genericui.enums.fragment.FragmentLaunchMode
import com.nexustools.genericui.enums.system.AppTarget
import com.nexustools.genericui.enums.system.BackendTarget
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
 * [BackendTarget], [AppTarget], [AppFragment], [MainAppFragment], [FragmentBundle]
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
        assertTrue(BackendTarget.Windows.isSystem)
        assertTrue(BackendTarget.Windows.isDesktop)
        assertTrue(BackendTarget.Windows.isNativeDesktop)
        assertFalse(BackendTarget.Windows.isLinux)
        assertFalse(BackendTarget.Windows.isMobile)
        assertFalse(BackendTarget.Windows.isBrowser)

        assertTrue(BackendTarget.Gtk.isLinux)
        assertTrue(BackendTarget.Qt.isLinux)
        assertTrue(BackendTarget.Swing.isDesktop)
        assertFalse(BackendTarget.Swing.isNativeDesktop)
        assertTrue(BackendTarget.Swing.isJvm)

        assertTrue(BackendTarget.Android.isMobile)
        assertTrue(BackendTarget.Android.isTouchPrimary)
        assertTrue(BackendTarget.IOS.isMobile)
        assertTrue(BackendTarget.IOS.isApple)
        assertTrue(BackendTarget.MacOS.isApple)

        assertFalse(BackendTarget.Web.isSystem)
        assertTrue(BackendTarget.Web.isBrowser)

        assertEquals(BackendTarget.Windows, BackendTarget.fromBackendName("Windows (Win32 / Direct2D / ANGLE / Vulkan / VLC)"))
        assertEquals(BackendTarget.MacOS, BackendTarget.fromBackendName("macOS (AppKit / CoreGraphics / MoltenVK / VLC)"))
        assertEquals(BackendTarget.Gtk, BackendTarget.fromBackendName("GTK4 (Libadwaita / Cairo / GtkGLArea / Vulkan / VLC)"))
        assertEquals(BackendTarget.Qt, BackendTarget.fromBackendName("Qt6 (QWidgets / QPainter / QOpenGLWidget / QVulkanWindow / VLC)"))
        assertEquals(BackendTarget.Swing, BackendTarget.fromBackendName("swing"))
        assertEquals(BackendTarget.Android, BackendTarget.fromBackendName("android"))
        assertEquals(BackendTarget.IOS, BackendTarget.fromBackendName("ios"))
        assertEquals(BackendTarget.Web, BackendTarget.fromBackendName("web"))
    }

    @Test
    fun testAppTargetSpecificityChainAndMatching() {
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
            listOf(AppTarget.WEB, AppTarget.COMMON),
            AppTarget.specificityChainFor(BackendTarget.Web)
        )
        assertEquals(
            listOf(AppTarget.ANDROID, AppTarget.MOBILE_COMMON, AppTarget.SYSTEM_COMMON, AppTarget.COMMON),
            AppTarget.specificityChainFor(BackendTarget.Android)
        )

        val desktopAndCommon = arrayOf(AppTarget.COMMON, AppTarget.DESKTOP_COMMON)
        assertEquals(2, AppTarget.resolveHighestSpecificityRank(desktopAndCommon, BackendTarget.Windows))
        assertEquals(0, AppTarget.resolveHighestSpecificityRank(desktopAndCommon, BackendTarget.Android))
        assertEquals(5, AppTarget.resolveHighestSpecificityRank(arrayOf(AppTarget.WINDOWS), BackendTarget.Windows))
        assertNull(AppTarget.resolveHighestSpecificityRank(arrayOf(AppTarget.WINDOWS), BackendTarget.Web))
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

    @Test
    fun testIApplicationAndIFragmentLifecycleWithLaunchCoordinator() {
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

        val detail: SampleDetailFragment = mainFrag.launchFragment<SampleDetailFragment>(
            fragmentClass = SampleDetailFragment::class,
            launchArgs = FragmentBundle.of("tab" to "layers"),
            addToBackStack = true,
            launchMode = FragmentLaunchMode.SAME_WINDOW
        )
        assertSame(detail, app.currentFragment)
        assertEquals("layers", detail.arguments?.getString("tab"))
        assertTrue(app.canGoBack)

        assertTrue(detail.finishFragment())
        assertSame(mainFrag, app.currentFragment)
    }
}
