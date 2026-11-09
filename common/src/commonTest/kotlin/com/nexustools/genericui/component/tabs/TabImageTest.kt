package com.nexustools.genericui.component.tabs

import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.media.Image
import com.nexustools.genericui.resources.ResourceID
import com.nexustools.genericui.resources.ResourceType
import com.nexustools.genericui.style.color.ImageTintMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TabImageTest {

    @Test
    fun tabSupportsImageIconResIconPlacementAndZeroDefaultTooltip() {
        val resId = ResourceID(0x7f010001, ResourceType.DRAWABLE, "ic_library", "drawable/ic_library.svg")
        val img = Image(resId, alt = "Library").apply {
            widthPx = 22
            heightPx = 22
        }

        val tab = Tab(
            tabId = "library",
            title = "LIBRARY",
            image = img,
            tintImageToTheme = true,
            iconSizePx = 22,
            iconPlacement = TabIconPlacement.TOP
        )

        assertEquals("library", tab.tabId)
        assertEquals("LIBRARY", tab.title)
        assertSame(img, tab.image)
        assertEquals(resId, tab.iconRes)
        assertEquals(resId.toResourceUri(), tab.icon)
        assertTrue(tab.tintImageToTheme)
        assertEquals(22, tab.iconSizePx)
        assertEquals(TabIconPlacement.TOP, tab.iconPlacement)
        assertNull(tab.tooltip, "Tab must never set a default or fallback tooltip")

        // Verify setIcon(ResourceID) updates iconRes, icon, and image
        val searchRes = ResourceID(0x7f010002, ResourceType.DRAWABLE, "ic_search", "drawable/ic_search.svg")
        tab.setIcon(searchRes)
        assertEquals(searchRes, tab.iconRes)
        assertEquals(searchRes.toResourceUri(), tab.icon)
        assertNotNull(tab.image)
        assertEquals(searchRes, tab.image?.resourceId)

        // Verify setImage(src, tintToTheme = false)
        tab.setImage("https://example.com/custom.png", tintToTheme = false)
        assertFalse(tab.tintImageToTheme)
        assertEquals("https://example.com/custom.png", tab.icon)
        assertNotNull(tab.image)
        assertEquals(ImageTintMode.NONE, tab.image!!.tintMode)
    }

    @Test
    fun tabSubclassesForwardImageAndIconParametersWithZeroDefaultTooltips() {
        val resId = ResourceID(0x7f010003, ResourceType.DRAWABLE, "ic_downloads", "drawable/ic_downloads.svg")

        val closeable = CloseableTab(
            tabId = "closeable",
            title = "Closeable",
            iconRes = resId,
            tintImageToTheme = false,
            iconSizePx = 24,
            iconPlacement = TabIconPlacement.TOP
        )
        assertEquals(resId, closeable.iconRes)
        assertEquals(resId.toResourceUri(), closeable.icon)
        assertNotNull(closeable.image)
        assertFalse(closeable.tintImageToTheme)
        assertEquals(24, closeable.iconSizePx)
        assertEquals(TabIconPlacement.TOP, closeable.iconPlacement)
        assertNull(closeable.tooltip)

        val badge = BadgeTab(
            tabId = "badge",
            title = "Updates",
            badgeText = "4",
            iconRes = resId,
            iconSizePx = 18,
            iconPlacement = TabIconPlacement.TOP
        )
        assertEquals(resId, badge.iconRes)
        assertEquals("4", badge.badgeText)
        assertEquals(18, badge.iconSizePx)
        assertEquals(TabIconPlacement.TOP, badge.iconPlacement)
        assertNull(badge.tooltip)

        val buttonTab = ButtonTab(
            title = "+",
            id = "add",
            iconRes = resId,
            iconSizePx = 16
        )
        assertEquals(resId, buttonTab.iconRes)
        assertEquals(16, buttonTab.iconSizePx)
        assertNull(buttonTab.tooltip)

        val editable = EditableTab(
            tabId = "editable",
            title = "Editable",
            iconRes = resId,
            iconPlacement = TabIconPlacement.TOP
        )
        assertEquals(resId, editable.iconRes)
        assertEquals(TabIconPlacement.TOP, editable.iconPlacement)
        assertNull(editable.tooltip)

        val pinned = PinnedTab(
            tabId = "pinned",
            title = "Pinned",
            iconRes = resId,
            iconSizePx = 20
        )
        assertEquals(resId, pinned.iconRes)
        assertEquals(20, pinned.iconSizePx)
        assertNull(pinned.tooltip)
    }

    @Test
    fun tabContainerSupportsFullWidthTabsAndTabImageMutators() {
        val container = TabContainer()
        assertFalse(container.fullWidthTabs)
        container.fullWidthTabs = true
        assertTrue(container.fullWidthTabs)

        val libRes = ResourceID(0x7f010004, ResourceType.DRAWABLE, "ic_library", "drawable/ic_library.svg")
        val dlRes = ResourceID(0x7f010005, ResourceType.DRAWABLE, "ic_downloads", "drawable/ic_downloads.svg")

        container.addTab(
            title = "LIBRARY",
            content = Container(),
            iconRes = libRes,
            iconSizePx = 22,
            iconPlacement = TabIconPlacement.TOP
        )
        container.addCloseableTab(
            title = "DOWNLOADS",
            content = Container(),
            iconRes = dlRes,
            iconSizePx = 22,
            iconPlacement = TabIconPlacement.TOP
        )

        val entries = container.entries
        assertEquals(2, entries.size)
        assertEquals(libRes, entries[0].tab.iconRes)
        assertEquals(22, entries[0].tab.iconSizePx)
        assertEquals(TabIconPlacement.TOP, entries[0].tab.iconPlacement)
        assertNull(entries[0].tab.tooltip)

        val newRes = ResourceID(0x7f010006, ResourceType.DRAWABLE, "ic_sources", "drawable/ic_sources.svg")
        val firstTabId = entries[0].id
        container.setTabIcon(firstTabId, newRes, tintToTheme = false)
        assertEquals(newRes, entries[0].tab.iconRes)
        assertFalse(entries[0].tab.tintImageToTheme)

        val customImg = Image("https://example.com/tab.png").apply {
            widthPx = 24
            heightPx = 24
        }
        container.setTabImage(firstTabId, customImg, tintToTheme = true)
        assertSame(customImg, entries[0].tab.image)
        assertTrue(entries[0].tab.tintImageToTheme)
    }

    @Test
    fun fragmentTabContainerSupportsFullWidthTabsAndIconParameters() {
        val fragTabs = FragmentTabContainer()
        assertFalse(fragTabs.fullWidthTabs)
        fragTabs.fullWidthTabs = true
        assertTrue(fragTabs.fullWidthTabs)

        val res1 = ResourceID(0x7f010007, ResourceType.DRAWABLE, "ic_tab1", "drawable/ic_tab1.svg")
        val res2 = ResourceID(0x7f010008, ResourceType.DRAWABLE, "ic_tab2", "drawable/ic_tab2.svg")

        fragTabs.addTab(
            title = "FIRST",
            fragment = object : Fragment() {},
            iconRes = res1,
            iconSizePx = 22,
            iconPlacement = TabIconPlacement.TOP
        )
        fragTabs.addCloseableTab(
            title = "SECOND",
            fragment = object : Fragment() {},
            iconRes = res2,
            tintImageToTheme = false,
            iconSizePx = 20,
            iconPlacement = TabIconPlacement.START
        )

        val tab0 = fragTabs.entries[0].tab
        val tab1 = fragTabs.entries[1].tab
        assertEquals(res1, tab0.iconRes)
        assertEquals(22, tab0.iconSizePx)
        assertEquals(TabIconPlacement.TOP, tab0.iconPlacement)
        assertNull(tab0.tooltip)

        assertEquals(res2, tab1.iconRes)
        assertFalse(tab1.tintImageToTheme)
        assertEquals(20, tab1.iconSizePx)
        assertEquals(TabIconPlacement.START, tab1.iconPlacement)
        assertNull(tab1.tooltip)

        val updatedRes = ResourceID(0x7f010009, ResourceType.DRAWABLE, "ic_updated", "drawable/ic_updated.svg")
        fragTabs.setTabIcon(tab0.tabId, updatedRes, tintToTheme = false)
        assertEquals(updatedRes, tab0.iconRes)
        assertFalse(tab0.tintImageToTheme)
    }
}
