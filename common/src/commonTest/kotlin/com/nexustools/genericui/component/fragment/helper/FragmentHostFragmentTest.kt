package com.nexustools.genericui.component.fragment.helper

import com.nexustools.genericui.component.action.Button
import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.fragment.FragmentBundle
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.shell.FragmentApplication
import com.nexustools.genericui.component.shell.Window
import com.nexustools.genericui.core.UiTimerScheduler
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Unit tests for [FragmentHostFragment], [FragmentHostSystem], [FragmentTransitionSpec]
 * (including `FadeExpandShrink`), [FragmentHistoryBridge] `#hash` + browser history state,
 * and adaptive Back/Forward navigation chrome in [com.nexustools.genericui.window.TitleBar] vs fallback `Navbar`.
 */
class FragmentHostFragmentTest {

    private class FakeWebHistoryBridge : FragmentHistoryBridge {
        override val isSupported: Boolean = true
        var currentHash: String = ""
        var currentState: FragmentBundle? = null
        var currentIndex: Int = 0
        var popStateListener: ((String, Int?, FragmentBundle?) -> Unit)? = null

        data class Entry(val route: String, val index: Int, val title: String, val state: FragmentBundle)
        val history = mutableListOf<Entry>()

        override fun currentRouteHash(): String? = currentHash.removePrefix("#").takeIf { it.isNotEmpty() }

        override fun currentSavedState(): FragmentBundle? = currentState

        override fun currentHistoryIndex(): Int? = currentIndex

        override fun pushState(route: String, historyIndex: Int, title: String, state: FragmentBundle?) {
            val copy = FragmentBundle().apply { if (state != null) putAll(state) }
            currentHash = "#$route"
            currentIndex = historyIndex
            currentState = copy
            history.add(Entry(route, historyIndex, title, copy))
        }

        override fun replaceState(route: String, historyIndex: Int, title: String, state: FragmentBundle?) {
            val copy = FragmentBundle().apply { if (state != null) putAll(state) }
            currentHash = "#$route"
            currentIndex = historyIndex
            currentState = copy
            val existingIdx = history.indexOfFirst { it.index == historyIndex }
            val entry = Entry(route, historyIndex, title, copy)
            if (existingIdx >= 0) {
                history[existingIdx] = entry
            } else {
                history.add(entry)
            }
        }

        override fun goBack(): Boolean {
            val targetIdx = currentIndex - 1
            val entry = history.firstOrNull { it.index == targetIdx } ?: return false
            currentHash = "#${entry.route}"
            currentIndex = entry.index
            currentState = entry.state
            popStateListener?.invoke(entry.route, entry.index, entry.state)
            return true
        }

        override fun goForward(): Boolean {
            val targetIdx = currentIndex + 1
            val entry = history.firstOrNull { it.index == targetIdx } ?: return false
            currentHash = "#${entry.route}"
            currentIndex = entry.index
            currentState = entry.state
            popStateListener?.invoke(entry.route, entry.index, entry.state)
            return true
        }

        override fun setOnPopStateListener(
            listener: ((route: String, historyIndex: Int?, restoredState: FragmentBundle?) -> Unit)?
        ) {
            popStateListener = listener
        }
    }

    private class MenuRouteFragment : Fragment() {
        val menuBox = Container("div", "menu")
        var restoredScrollY: Int = 0

        init {
            pageTitle = "Main Menu"
            addChild(menuBox)
        }

        override fun onCreate(savedState: FragmentBundle?) {
            super.onCreate(savedState)
            restoredScrollY = savedState?.getInt("scrollY", 0) ?: 0
        }

        override fun onSaveInstanceState(outState: FragmentBundle) {
            super.onSaveInstanceState(outState)
            outState.putInt("scrollY", restoredScrollY)
        }
    }

    private class PlayRouteFragment : Fragment() {
        val menuBox = Container("div", "menu")
        var levelId: String = "1"

        init {
            pageTitle = "Play Game"
            addChild(menuBox)
        }

        override fun onCreate(savedState: FragmentBundle?) {
            super.onCreate(savedState)
            levelId = savedState?.getString("level", "1") ?: "1"
        }

        override fun onSaveInstanceState(outState: FragmentBundle) {
            super.onSaveInstanceState(outState)
            outState.putString("level", levelId)
        }
    }

    @Test
    fun testFragmentHostSystemRoutesBackForwardAndWebHashHistoryState() {
        val bridge = FakeWebHistoryBridge()
        val root = Container("div")
        val host = FragmentHostSystem(
            rootContainer = root,
            appTitle = "DCApp",
            transitionSpec = FragmentTransitionSpec.None,
            historyBridge = bridge
        )

        host.registerRoute("menu", MenuRouteFragment::class, title = "Main Menu") { MenuRouteFragment() }
        host.registerRoute("play", PlayRouteFragment::class, title = "Play Game") { PlayRouteFragment() }

        val menuFrag = host.navigateToRoute("menu", addToBackStack = false) as MenuRouteFragment
        menuFrag.restoredScrollY = 240
        assertEquals("#menu", bridge.currentHash)
        assertEquals(0, host.backStackDepth)
        assertFalse(host.canGoBack)

        val playArgs = FragmentBundle.of("level" to "boss-stage")
        val playFrag = host.navigateToRoute("play", args = playArgs, addToBackStack = true) as PlayRouteFragment
        assertEquals("boss-stage", playFrag.levelId)
        assertEquals("#play", bridge.currentHash)
        assertEquals(1, host.backStackDepth)
        assertTrue(host.canGoBack)
        assertFalse(host.canGoForward)

        // Simulate browser Back button via popstate
        assertTrue(bridge.goBack())
        val restoredMenu = host.currentFragment as? MenuRouteFragment
        assertNotNull(restoredMenu)
        assertEquals(240, restoredMenu.restoredScrollY)
        assertEquals("menu", host.currentRoute)
        assertTrue(host.canGoForward)

        // Simulate browser Forward button via popstate
        assertTrue(bridge.goForward())
        val restoredPlay = host.currentFragment as? PlayRouteFragment
        assertNotNull(restoredPlay)
        assertEquals("boss-stage", restoredPlay.levelId)
        assertEquals("play", host.currentRoute)
    }

    @Test
    fun testFadeExpandShrinkTransitionAndLoaderOverlay() {
        val root = Container("div")
        val host = FragmentHostSystem(
            rootContainer = root,
            appTitle = "DCGame",
            transitionSpec = FragmentTransitionSpec.FadeExpandShrink()
        )
        host.registerRoute("menu", MenuRouteFragment::class) { MenuRouteFragment() }
        host.registerRoute("play", PlayRouteFragment::class) { PlayRouteFragment() }

        val first = host.navigateToRoute("menu", addToBackStack = false)
        assertTrue(first.hasClass("created"))

        // Advance past the 50ms enter activation delay
        UiTimerScheduler.advanceTimeBy(60)
        assertFalse(first.hasClass("created"))

        // Test loading spinner synchronization
        assertFalse(host.loaderContainer.isVisible)
        first.beginLoading()
        assertTrue(host.loaderContainer.isVisible)
        first.endLoading()
        assertFalse(host.loaderContainer.isVisible)

        // Navigate to second fragment with FadeExpandShrink (keepOutgoingMountedDuringExit = true)
        val second = host.navigateToRoute("play", addToBackStack = true)
        assertTrue(first.hasClass("destroyed"))
        assertTrue(second.hasClass("created"))
        assertEquals(2, host.stageContainer.children.size)

        // Advance past exit + enter duration (500ms)
        UiTimerScheduler.advanceTimeBy(560)
        assertEquals(1, host.stageContainer.children.size)
        assertSame(second, host.currentFragment)
    }

    @Test
    fun testFragmentHostFragmentNavigationChromeModes() {
        // 1. DCGame mode: showBackButton = false, showForwardButton = false -> neither titlebar buttons nor fallback navbar
        val gameHost = FragmentHostFragment(
            appTitle = "DCGame",
            transitionSpec = FragmentTransitionSpec.FadeExpandShrink(),
            navigationChrome = FragmentNavigationChromeConfig(
                showBackButton = false,
                showForwardButton = false,
                showNavbarFallback = false
            )
        )
        val gameApp = FragmentApplication(initialFragment = gameHost, title = "DCGame")
        assertFalse(gameHost.fallbackNavbar.isVisible)
        assertFalse(gameHost.backButton.mountedInTitleBar)
        assertEquals("DCGame", gameApp.mainIWindow.getTitle())

        // 2. DCApp mode on custom-titlebar window: showBackButton = true -> embeds backButton in TitleBar (mountedInTitleBar = true)
        val customBtn = Button("Sync")
        val appHost = FragmentHostFragment(
            appTitle = "DCApp",
            navigationChrome = FragmentNavigationChromeConfig(
                showBackButton = true,
                showForwardButton = true,
                preferTitleBarNavigation = true
            ).addTrailing(customBtn)
        )
        val win = Window("DCApp")
        win.getOrCreateFragmentContainer().setFragment(appHost)
        appHost.refreshNavigationChrome()

        assertTrue(appHost.backButton.mountedInTitleBar)
        assertTrue(appHost.forwardButton.mountedInTitleBar)
        assertTrue(customBtn.mountedInTitleBar)
        assertFalse(appHost.fallbackNavbar.isVisible)

        // 3. Fallback Navbar mode when preferTitleBarNavigation = false
        val navbarHost = FragmentHostFragment(
            appTitle = "Episode Caster",
            navigationChrome = FragmentNavigationChromeConfig(
                showBackButton = true,
                preferTitleBarNavigation = false,
                showNavbarFallback = true
            )
        )
        navbarHost.refreshNavigationChrome()
        assertFalse(navbarHost.backButton.mountedInTitleBar)
        assertTrue(navbarHost.fallbackNavbar.isVisible)
    }
}
