package com.nexustools.genericui.component.fragment

import com.nexustools.genericui.component.fragment.helper.TabItemSpec
import com.nexustools.genericui.component.fragment.helper.TabbedChromeMode
import com.nexustools.genericui.component.fragment.helper.TabbedFragment
import com.nexustools.genericui.component.fragment.helper.TabbedFragmentConfig
import com.nexustools.genericui.component.shell.Application
import com.nexustools.genericui.component.shell.FragmentApplication
import com.nexustools.genericui.enums.fragment.FragmentState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TabbedFragmentTest {

    private class SampleTabFragment(val label: String) : Fragment() {
        init {
            pageTitle = label
        }
    }

    @Test
    fun applicationGetPreferenceStoreLazilyInitializesAndReusesInstance() {
        val app = Application(title = "PrefStoreTestApp")
        try {
            val store1 = app.getPreferenceStore()
            val store2 = app.getPreferenceStore()
            assertSame(store1, store2, "Application.getPreferenceStore() must return the same cached instance")

            store1.putString("theme.accent", "#2A80FF")
            assertEquals("#2A80FF", store2.getString("theme.accent"))

            app.dispose()
            val storeAfterDispose = app.getPreferenceStore()
            assertNotSame(store1, storeAfterDispose, "Disposing Application should clear the cached PreferenceStore instance")
        } finally {
            app.dispose()
        }
    }

    @Test
    fun fragmentGetPreferenceStoreStandaloneAndApplicationDelegation() {
        val standalone = SampleTabFragment("Standalone")
        try {
            val fallback1 = standalone.getPreferenceStore()
            val fallback2 = standalone.getPreferenceStore()
            assertSame(fallback1, fallback2, "Standalone Fragment.getPreferenceStore() must cache and reuse its fallback store")

            fallback1.putInt("standalone.counter", 42)
            assertEquals(42, fallback2.getInt("standalone.counter"))
        } finally {
            standalone.dispose()
        }

        val hostedFragment = SampleTabFragment("Hosted")
        val app = FragmentApplication(hostedFragment, title = "HostedFragmentApp")
        try {
            val appStore = app.getPreferenceStore()
            val fragmentStore = hostedFragment.getPreferenceStore()
            assertSame(appStore, fragmentStore, "Attached Fragment.getPreferenceStore() must delegate to Application.getPreferenceStore()")
        } finally {
            app.dispose()
        }
    }

    @Test
    fun tabbedFragmentOpenSelectReuseUpdateMoveAndCloseLifecycle() {
        val tabbed = TabbedFragment(
            config = TabbedFragmentConfig(allowCloseLastTab = false)
        )
        var homeCreateCount = 0
        var docsCreateCount = 0
        var settingsCreateCount = 0

        try {
            tabbed.openTab(
                tabId = "home",
                title = "Home",
                subtitle = "Dashboard",
                closable = true
            ) {
                homeCreateCount++
                SampleTabFragment("Home")
            }
            tabbed.openTab(
                tabId = "docs",
                title = "Docs",
                subtitle = "API Reference",
                badgeText = "3",
                closable = true
            ) {
                docsCreateCount++
                SampleTabFragment("Docs")
            }
            tabbed.openTab(
                tabId = "pinned-settings",
                title = "Settings",
                pinned = true,
                closable = true
            ) {
                settingsCreateCount++
                SampleTabFragment("Settings")
            }

            assertEquals(3, tabbed.tabCount)
            assertEquals("home", tabbed.activeTabId)
            assertEquals(0, tabbed.activeTabIndex)
            assertEquals(1, homeCreateCount)
            assertEquals(0, docsCreateCount)

            val homeFragment = tabbed.getTabFragment("home")
            assertNotNull(homeFragment)
            assertSame(homeFragment, tabbed.activeFragment)

            // Switch to docs tab and verify lazy creation
            assertTrue(tabbed.selectTab("docs"))
            assertEquals("docs", tabbed.activeTabId)
            assertEquals(1, tabbed.activeTabIndex)
            assertEquals(1, docsCreateCount)
            val docsFragment = tabbed.getTabFragment("docs")
            assertNotNull(docsFragment)
            assertSame(docsFragment, tabbed.activeFragment)

            // Switch back to home tab and verify cached Fragment instance is reused
            assertTrue(tabbed.selectTabAt(0))
            assertEquals("home", tabbed.activeTabId)
            assertEquals(1, homeCreateCount)
            assertSame(homeFragment, tabbed.activeFragment)

            // Update title, subtitle, badge, and reorder tabs
            assertTrue(tabbed.updateTabTitle("docs", "Documentation"))
            assertTrue(tabbed.updateTabSubtitle("docs", "Updated Reference"))
            assertTrue(tabbed.updateTabBadge("docs", "5"))
            val updatedDocsSpec = tabbed.tabs.first { it.tabId == "docs" }
            assertEquals("Documentation", updatedDocsSpec.title)
            assertEquals("Updated Reference", updatedDocsSpec.subtitle)
            assertEquals("5", updatedDocsSpec.badgeText)

            assertTrue(tabbed.moveTab(0, 1))
            assertEquals(listOf("docs", "home", "pinned-settings"), tabbed.tabs.map { it.tabId })

            // Pinned tab cannot be closed without forceClosePinned = true
            assertFalse(tabbed.closeTab("pinned-settings", forceClosePinned = false))
            assertEquals(3, tabbed.tabCount)

            // Close active tab ("home") and verify neighbor selection and fragment destruction
            assertTrue(tabbed.closeTab("home"))
            assertEquals(2, tabbed.tabCount)
            assertEquals(FragmentState.DESTROYED, homeFragment.state)
            assertNotNull(tabbed.activeTabId)

            // Force-close pinned tab
            assertTrue(tabbed.closeTab("pinned-settings", forceClosePinned = true))
            assertEquals(1, tabbed.tabCount)
            assertEquals("docs", tabbed.activeTabId)

            // Last remaining tab cannot be closed when allowCloseLastTab = false
            assertFalse(tabbed.closeTab("docs"))
            assertEquals(1, tabbed.tabCount)

            // Verify requestNewTab callback
            tabbed.onNewTabRequested = {
                TabItemSpec(
                    tabId = "new-tab",
                    title = "New Tab",
                    fragmentFactory = { SampleTabFragment("New Tab") }
                )
            }
            val createdSpec = tabbed.requestNewTab()
            assertNotNull(createdSpec)
            assertEquals("new-tab", tabbed.activeTabId)
            assertEquals(2, tabbed.tabCount)
        } finally {
            tabbed.dispose()
        }
    }

    @Test
    fun tabbedFragmentAdaptiveChromeModesAndMobileOverviewSwitcher() {
        val tabbed = TabbedFragment(
            config = TabbedFragmentConfig(
                chromeMode = TabbedChromeMode.AUTO,
                mobileBreakpointWidthPx = 720
            )
        )
        try {
            tabbed.openTab("tab-1", "First Tab") { SampleTabFragment("First") }
            tabbed.openTab("tab-2", "Second Tab") { SampleTabFragment("Second") }

            // Desktop width (>= 720px)
            tabbed.updateViewportSize(1280, 800)
            assertEquals(TabbedChromeMode.DESKTOP_BROWSER_TABS, tabbed.resolvedChromeMode)
            assertTrue(tabbed.desktopTabStripContainer.isVisible)
            assertFalse(tabbed.mobileHeaderBarContainer.isVisible)

            // Mobile width (< 720px)
            tabbed.updateViewportSize(480, 800)
            assertEquals(TabbedChromeMode.MOBILE_SWITCHER_PILL, tabbed.resolvedChromeMode)
            assertFalse(tabbed.desktopTabStripContainer.isVisible)
            assertTrue(tabbed.mobileHeaderBarContainer.isVisible)
            assertEquals("2", tabbed.mobileTabSwitcherPillButton.text)
            assertEquals("First Tab", tabbed.mobileActiveTabTitleLabel.text)

            // Open mobile tab overview grid
            assertFalse(tabbed.isMobileTabOverviewOpen)
            tabbed.toggleMobileTabOverview()
            assertTrue(tabbed.isMobileTabOverviewOpen)
            assertTrue(tabbed.mobileTabOverviewContainer.isVisible)
            assertFalse(tabbed.stageContainer.isVisible)

            // Back press closes the mobile tab overview first
            assertTrue(tabbed.onBackPressed())
            assertFalse(tabbed.isMobileTabOverviewOpen)
            assertFalse(tabbed.mobileTabOverviewContainer.isVisible)
            assertTrue(tabbed.stageContainer.isVisible)

            // Selecting a tab while mobile overview is open switches tabs and closes overview
            tabbed.openMobileTabOverview()
            assertTrue(tabbed.isMobileTabOverviewOpen)
            assertTrue(tabbed.selectTab("tab-2"))
            assertFalse(tabbed.isMobileTabOverviewOpen)
            assertEquals("Second Tab", tabbed.mobileActiveTabTitleLabel.text)

            // Explicit chromeModeOverride overrides viewport width
            tabbed.chromeModeOverride = TabbedChromeMode.DESKTOP_BROWSER_TABS
            assertEquals(TabbedChromeMode.DESKTOP_BROWSER_TABS, tabbed.resolvedChromeMode)
            assertTrue(tabbed.desktopTabStripContainer.isVisible)
        } finally {
            tabbed.dispose()
        }
    }

    @Test
    fun tabbedFragmentPersistsAndRestoresActiveTabViaPreferenceStore() {
        val persistKey = "test.tabbed.last_active_tab"
        val firstHost = TabbedFragment(
            config = TabbedFragmentConfig(persistLastActiveTabKey = persistKey)
        )
        try {
            firstHost.getPreferenceStore().remove(persistKey)
            firstHost.openTab("alpha", "Alpha") { SampleTabFragment("Alpha") }
            firstHost.openTab("beta", "Beta") { SampleTabFragment("Beta") }
            firstHost.openTab("gamma", "Gamma") { SampleTabFragment("Gamma") }

            assertTrue(firstHost.selectTab("gamma"))
            assertEquals("gamma", firstHost.getPreferenceStore().getString(persistKey))
        } finally {
            firstHost.dispose()
        }

        val restoredHost = TabbedFragment(
            config = TabbedFragmentConfig(persistLastActiveTabKey = persistKey)
        )
        try {
            restoredHost.openTab("alpha", "Alpha") { SampleTabFragment("Alpha") }
            restoredHost.openTab("beta", "Beta") { SampleTabFragment("Beta") }
            restoredHost.openTab("gamma", "Gamma") { SampleTabFragment("Gamma") }

            assertEquals("gamma", restoredHost.activeTabId, "TabbedFragment must restore the persisted active tabId from PreferenceStore")
            assertEquals("Gamma", restoredHost.activeFragment?.pageTitle)
        } finally {
            restoredHost.getPreferenceStore().remove(persistKey)
            restoredHost.dispose()
        }
    }
}
