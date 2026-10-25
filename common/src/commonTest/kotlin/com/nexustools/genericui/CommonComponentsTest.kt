package com.nexustools.genericui

import com.nexustools.genericui.component.action.Button
import com.nexustools.genericui.component.dock.DockPanel
import com.nexustools.genericui.component.dock.DockWorkspace
import com.nexustools.genericui.component.dock.PanelWindow
import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.fragment.ScrollFragment
import com.nexustools.genericui.component.fragment.helper.DockWorkspaceFragment
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.shell.Application
import com.nexustools.genericui.component.shell.ContextMenu
import com.nexustools.genericui.component.shell.FragmentApplication
import com.nexustools.genericui.component.shell.Menu
import com.nexustools.genericui.component.shell.MenuBar
import com.nexustools.genericui.component.shell.MenuItem
import com.nexustools.genericui.component.shell.MenuSeparator
import com.nexustools.genericui.component.shell.Window
import com.nexustools.genericui.enums.dock.DockPosition
import com.nexustools.genericui.enums.dock.StudioWindowMode
import com.nexustools.genericui.event.KeyShortcut
import com.nexustools.genericui.event.PointerEvent
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.CssStyleSheet
import com.nexustools.genericui.style.sass.SassCompiler
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CommonComponentsTest {

    @Test
    fun testWindowAndFragmentApplicationTagRootWithGenericUiRoot() {
        val window = Window("Test Window")
        val container = Container()
        assertEquals("genericui:container", container.htmlTag)

        window.setContent(container)
        assertEquals("genericui:root", container.htmlTag)

        val fragment = object : Fragment() {}
        val app = FragmentApplication(fragment, title = "Fragment App")
        assertEquals("genericui:root", app.rootFragmentContainer.htmlTag)
        assertEquals("genericui:root", fragment.htmlTag)

        val nextFragment = object : Fragment() {}
        app.pushFragment(nextFragment)
        assertEquals("genericui:root", nextFragment.htmlTag)
    }

    @Test
    fun testGenericUiRootSelectorAndScssEscapingMatchRootContainerAndFragment() {
        val sheet = CssStyleSheet.parse("genericui:root { background-color: #112233; }")

        val container = Container()
        val window = Window("Styled Window")
        window.setContent(container)
        container.applyStyleSheet(sheet)

        assertEquals(RgbaColor.fromHex("#112233"), container.computedStyle?.backgroundColor)

        val manualRoot = Container()
        manualRoot.htmlTag = "root"
        manualRoot.applyStyleSheet(sheet)
        assertEquals(RgbaColor.fromHex("#112233"), manualRoot.computedStyle?.backgroundColor)

        // Verify applying stylesheet before Window.setContent re-evaluates when htmlTag becomes genericui:root
        val preStyledContainer = Container()
        preStyledContainer.applyStyleSheet(sheet)
        Window("Deferred Root Window").setContent(preStyledContainer)
        assertEquals(RgbaColor.fromHex("#112233"), preStyledContainer.computedStyle?.backgroundColor)

        // Verify SassCompiler escapes genericui:root to genericui\:root and parses cleanly
        val compiledCss = SassCompiler.compileToCss(
            """
            genericui:root {
                background-color: #223344;
                .child-btn {
                    border-radius: 14px;
                }
            }
            """.trimIndent()
        )
        assertTrue(compiledCss.contains("genericui\\:root"))

        val scssSheet = CssStyleSheet.parse(compiledCss)
        val childBtn = Button("Action")
        childBtn.addClass("child-btn")
        container.addChild(childBtn)
        container.applyStyleSheet(scssSheet)

        assertEquals(RgbaColor.fromHex("#223344"), container.computedStyle?.backgroundColor)
        assertEquals(14f, childBtn.computedStyle?.borderRadiusPx)
    }

    @Test
    fun testContextMenuAndMenuApiAndDispatchContextMenu() {
        val menu = ContextMenu()
        var clickedCut = false
        val cutItem = menu.addItem(
            label = "Cut",
            icon = "bi-scissors",
            shortcut = KeyShortcut.parse("Ctrl+X")
        ) {
            clickedCut = true
        }
        assertEquals("Cut", cutItem.getLabel())
        assertEquals("bi-scissors", cutItem.getIcon())
        assertNotNull(cutItem.getShortcut())

        val copyItem = MenuItem("Copy")
        menu.addItem(copyItem)
        menu.addSeparator()

        val submenu = Menu("Export")
        val exportPng = submenu.addItem("PNG", icon = "bi-image")
        submenu.addSeparator()
        menu.addSubmenu(submenu)

        assertEquals(4, menu.children.size)
        assertTrue(menu.children[0] is MenuItem)
        assertSame(copyItem, menu.children[1])
        assertTrue(menu.children[2] is MenuSeparator)
        assertSame(submenu, menu.children[3])
        assertEquals("PNG", exportPng.getLabel())

        val button = Button("Right Click Target")
        var customHandlerEvent: PointerEvent? = null
        button.contextMenu = menu
        button.onContextMenu { event ->
            customHandlerEvent = event
        }

        button.dispatchContextMenu(PointerEvent(x = 48f, y = 96f))

        assertTrue(menu.isShowing)
        assertEquals(48, menu.lastShownX)
        assertEquals(96, menu.lastShownY)
        assertSame(button, menu.lastAnchor)
        assertNotNull(customHandlerEvent)
        assertEquals(48f, customHandlerEvent.x)
        assertEquals(96f, customHandlerEvent.y)

        cutItem.onClick?.invoke()
        assertTrue(clickedCut)

        menu.hide()
        assertTrue(!menu.isShowing)

        // Verify Application.setMenuBar / getMenuBar convenience methods
        val app = Application(title = "Menu App")
        val menuBar = MenuBar().addMenu(submenu)
        app.setMenuBar(menuBar)
        assertSame(menuBar, app.getMenuBar())
    }

    @Test
    fun testScrollFragmentStateAndEvents() {
        val scrollFragment = object : ScrollFragment() {}
        assertEquals("genericui:scrollfragment", scrollFragment.htmlTag)
        assertTrue(scrollFragment.hasClass("overflow-auto"))
        assertTrue(scrollFragment.hasClass("scroll-fragment"))

        var observedX = -1
        var observedY = -1
        scrollFragment.onScroll { sx, sy ->
            observedX = sx
            observedY = sy
        }

        scrollFragment.scrollTo(24, 160)
        assertEquals(24, scrollFragment.scrollX)
        assertEquals(160, scrollFragment.scrollY)
        assertEquals(24, observedX)
        assertEquals(160, observedY)

        scrollFragment.dispatchScroll(0, 320)
        assertEquals(0, scrollFragment.scrollX)
        assertEquals(320, scrollFragment.scrollY)
        assertEquals(0, observedX)
        assertEquals(320, observedY)
    }

    @Test
    fun testPanelWindowToolModalAttachmentAndMultiWindowFocusBringingPanelsToFront() {
        val workspaceFragment = object : DockWorkspaceFragment(StudioWindowMode.SINGLE_WINDOW_MODE) {}
        val app = FragmentApplication(workspaceFragment, title = "Studio App")
        val mainWindow = app.mainWindow

        val layersPanel = DockPanel("layers", "Layers")
        val toolsPanel = DockPanel("tools", "Tool Options")
        workspaceFragment.addDockPanel(layersPanel, DockPosition.RIGHT)
        workspaceFragment.addDockPanel(toolsPanel, DockPosition.LEFT)

        // Switch to Multi-Window Mode
        workspaceFragment.setWindowMode(StudioWindowMode.MULTI_WINDOW_MODE)

        val panelWindows = workspaceFragment.dockWorkspace.panelWindows.values.toList()
        assertEquals(2, panelWindows.size)
        assertEquals(2, mainWindow.attachedPanelWindows.size)

        for (panelWin in panelWindows) {
            assertTrue(panelWin.isToolModalWindow)
            assertTrue(!panelWin.hasTaskbarEntry)
            assertTrue(!panelWin.isMinimizable)
            assertTrue(panelWin.isAlwaysOnTopOfOwner)
            assertEquals(false, panelWin.getTitleBar()?.showMinimizeButton)
            assertSame(mainWindow, panelWin.attachedMainWindow)
            assertTrue(panelWin in mainWindow.attachedPanelWindows)
        }

        // Simulate focusing another window and then focusing mainWindow:
        // Focusing mainWindow must bring mainWindow and then all attached PanelWindows to the front!
        mainWindow.dispatchWindowFocusChanged(true)
        assertTrue(mainWindow.isFocused)
        assertTrue(mainWindow.frontOrderSequence > 0L)
        for (panelWin in panelWindows) {
            assertTrue(
                panelWin.frontOrderSequence > mainWindow.frontOrderSequence,
                "Expected attached PanelWindow '${panelWin.getTitle()}' frontOrderSequence (${panelWin.frontOrderSequence}) > mainWindow (${mainWindow.frontOrderSequence})"
            )
        }

        // Switching back to Single-Window Mode closes and unregisters detached PanelWindows
        workspaceFragment.setWindowMode(StudioWindowMode.SINGLE_WINDOW_MODE)
        assertTrue(mainWindow.attachedPanelWindows.isEmpty())
    }
}
