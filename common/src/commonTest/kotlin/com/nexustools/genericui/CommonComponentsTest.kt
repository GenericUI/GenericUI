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
        assertEquals("genericui:app", container.htmlTag)

        val fragment = object : Fragment() {}
        assertEquals("genericui:fragment", fragment.htmlTag)
        val app = FragmentApplication(fragment, title = "Fragment App")
        assertEquals("genericui:app", app.rootFragmentContainer.htmlTag)
        assertEquals("genericui:fragment", fragment.htmlTag)

        val nextFragment = object : Fragment() {}
        app.pushFragment(nextFragment)
        assertEquals("genericui:fragment", nextFragment.htmlTag)
    }

    @Test
    fun testGenericUiRootSelectorAndScssEscapingMatchRootContainerAndFragment() {
        val sheet = CssStyleSheet.parse("genericui:app { background-color: #112233; }")

        val container = Container()
        val window = Window("Styled Window")
        window.setContent(container)
        container.applyStyleSheet(sheet)

        assertEquals(RgbaColor.fromHex("#112233"), container.computedStyle?.backgroundColor)

        val manualRoot = Container()
        manualRoot.htmlTag = "app"
        manualRoot.applyStyleSheet(sheet)
        assertEquals(RgbaColor.fromHex("#112233"), manualRoot.computedStyle?.backgroundColor)

        // Verify applying stylesheet before Window.setContent re-evaluates when htmlTag becomes genericui:app
        val preStyledContainer = Container()
        preStyledContainer.applyStyleSheet(sheet)
        Window("Deferred Root Window").setContent(preStyledContainer)
        assertEquals(RgbaColor.fromHex("#112233"), preStyledContainer.computedStyle?.backgroundColor)

        // Verify SassCompiler escapes genericui:app to genericui\:app and parses cleanly
        val compiledCss = SassCompiler.compileToCss(
            """
            genericui:app {
                background-color: #223344;
                .child-btn {
                    border-radius: 14px;
                }
            }
            """.trimIndent()
        )
        assertTrue(compiledCss.contains("genericui\\:app"))

        val scssSheet = CssStyleSheet.parse(compiledCss)
        val childBtn = Button("Action")
        childBtn.addClass("child-btn")
        container.addChild(childBtn)
        container.applyStyleSheet(scssSheet)

        assertEquals(RgbaColor.fromHex("#223344"), container.computedStyle?.backgroundColor)
        assertEquals(14f, childBtn.computedStyle?.borderRadiusPx)
    }

    @Test
    fun testBuiltInStyleSheetResourcesAndApplicationRemoval() {
        assertSame(com.nexustools.genericui.res.Resources.GENERICUI, com.nexustools.genericui.core.GenericUI.GENERICUI)
        assertSame(com.nexustools.genericui.res.Resources.BOOTSTRAP, com.nexustools.genericui.core.GenericUI.BOOTSTRAP)
        assertSame(com.nexustools.genericui.res.Resources.BOOTSTRAP_JS, com.nexustools.genericui.core.GenericUI.BOOTSTRAP_JS)
        assertSame(com.nexustools.genericui.res.Resources.BOOTSTRAP_ICONS, com.nexustools.genericui.core.GenericUI.BOOTSTRAP_ICONS)
        assertSame(com.nexustools.genericui.res.Resources.GENERICUI_NAMESPACE, com.nexustools.genericui.core.GenericUI.GENERICUI_NAMESPACE)

        val app = Application(title = "StyleSheet App")
        assertTrue(app.hasStyleSheet(com.nexustools.genericui.res.Resources.GENERICUI))
        assertTrue(app.hasStyleSheet(com.nexustools.genericui.res.Resources.BOOTSTRAP))
        assertTrue(app.hasStyleSheet(com.nexustools.genericui.res.Resources.BOOTSTRAP_ICONS))

        assertTrue(app.removeStyleSheet(com.nexustools.genericui.res.Resources.BOOTSTRAP))
        assertTrue(!app.hasStyleSheet(com.nexustools.genericui.res.Resources.BOOTSTRAP))
        assertTrue(app.hasStyleSheet(com.nexustools.genericui.res.Resources.GENERICUI))
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
        assertEquals("genericui:fragment", scrollFragment.htmlTag)
        assertEquals(com.nexustools.genericui.enums.bootstrap.OverflowBehavior.AUTO, scrollFragment.getBootstrap().overflow)
        assertTrue(!scrollFragment.hasClass("overflow-auto"))
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

    @Test
    fun testThemeModeAutoPreservationLiveSystemSyncAndDarkThemeCssVariables() {
        val prevOverride = com.nexustools.genericui.style.theme.SystemThemeDetector.overrideDetectedTheme
        try {
            com.nexustools.genericui.style.theme.SystemThemeDetector.overrideDetectedTheme =
                com.nexustools.genericui.enums.ui.ThemeMode.DARK

            val app = Application(title = "Theme Auto Test")
            app.setThemeMode(com.nexustools.genericui.enums.ui.ThemeMode.AUTO)
            assertEquals(com.nexustools.genericui.enums.ui.ThemeMode.AUTO, app.getThemeMode())
            assertEquals(com.nexustools.genericui.enums.ui.ThemeMode.DARK, app.getResolvedTheme())

            val observedThemes = mutableListOf<com.nexustools.genericui.enums.ui.ThemeMode>()
            app.addThemeChangeListener { resolved ->
                observedThemes.add(resolved)
            }

            // Simulate live OS theme switching to LIGHT while app is in AUTO mode
            com.nexustools.genericui.style.theme.SystemThemeDetector.overrideDetectedTheme =
                com.nexustools.genericui.enums.ui.ThemeMode.LIGHT
            com.nexustools.genericui.style.theme.SystemThemeDetector.notifySystemThemeChanged(
                com.nexustools.genericui.enums.ui.ThemeMode.LIGHT
            )

            assertEquals(com.nexustools.genericui.enums.ui.ThemeMode.AUTO, app.getThemeMode())
            assertEquals(com.nexustools.genericui.enums.ui.ThemeMode.LIGHT, app.getResolvedTheme())
            assertTrue(observedThemes.contains(com.nexustools.genericui.enums.ui.ThemeMode.LIGHT))

            // Simulate live OS theme switching back to DARK while app is in AUTO mode
            com.nexustools.genericui.style.theme.SystemThemeDetector.overrideDetectedTheme =
                com.nexustools.genericui.enums.ui.ThemeMode.DARK
            com.nexustools.genericui.style.theme.SystemThemeDetector.notifySystemThemeChanged(
                com.nexustools.genericui.enums.ui.ThemeMode.DARK
            )

            assertEquals(com.nexustools.genericui.enums.ui.ThemeMode.AUTO, app.getThemeMode())
            assertEquals(com.nexustools.genericui.enums.ui.ThemeMode.DARK, app.getResolvedTheme())
            assertEquals(com.nexustools.genericui.enums.ui.ThemeMode.DARK, observedThemes.last())

            // Verify Bootstrap CSS variables and dark-mode contrast remapping in CssStyleSheet
            val sheet = CssStyleSheet.parse(
                """
                .card-item {
                    color: var(--bs-body-color);
                    background-color: var(--bs-body-bg);
                }
                .legacy-body-text {
                    color: #212529;
                }
                """.trimIndent()
            )
            val cardStyleDark = sheet.resolveStyleForClasses(setOf("card-item"))
            assertEquals(RgbaColor(222, 226, 230, 255), cardStyleDark.textColor)
            assertEquals(RgbaColor(33, 37, 41, 255), cardStyleDark.backgroundColor)

            val legacyTextDark = sheet.resolveStyleForClasses(setOf("legacy-body-text"))
            assertEquals(RgbaColor(222, 226, 230, 255), legacyTextDark.textColor)
        } finally {
            com.nexustools.genericui.style.theme.SystemThemeDetector.overrideDetectedTheme = prevOverride
        }
    }

    @Test
    fun testMonocolorImageRecolorProcessorAndCssFilterOnImageView() {
        // 1. Create a 2x2 monocolor black icon buffer (black pixels with varying alpha)
        val iconBuffer = com.nexustools.genericui.image.ImageBuffer(2, 2)
        iconBuffer.setPixel(0, 0, RgbaColor(0, 0, 0, 255))
        iconBuffer.setPixel(1, 0, RgbaColor(0, 0, 0, 128))
        iconBuffer.setPixel(0, 1, RgbaColor(0, 0, 0, 0))
        iconBuffer.setPixel(1, 1, RgbaColor(12, 12, 12, 255))

        assertTrue(
            com.nexustools.genericui.image.MonocolorImageRecolorProcessor.isMonocolorRgba(
                iconBuffer.bytes,
                iconBuffer.width,
                iconBuffer.height
            )
        )

        val darkRecolored = com.nexustools.genericui.image.MonocolorImageRecolorProcessor.processImageBuffer(
            buffer = iconBuffer,
            mode = com.nexustools.genericui.style.color.ImageTintMode.MONOCOLOR_AUTO_THEME,
            tintColor = null,
            isDarkTheme = true
        )
        assertEquals(245, darkRecolored.getPixel(0, 0).r)
        assertEquals(245, darkRecolored.getPixel(0, 0).g)
        assertEquals(247, darkRecolored.getPixel(0, 0).b)
        assertEquals(255, darkRecolored.getPixel(0, 0).a)
        assertEquals(128, darkRecolored.getPixel(1, 0).a)
        assertEquals(0, darkRecolored.getPixel(0, 1).a)

        // 2. Create a 2x2 colorful thumbnail buffer and verify MONOCOLOR_AUTO_THEME leaves it untouched
        val colorBuffer = com.nexustools.genericui.image.ImageBuffer(2, 2)
        colorBuffer.setPixel(0, 0, RgbaColor(240, 20, 30, 255))
        colorBuffer.setPixel(1, 0, RgbaColor(20, 220, 40, 255))
        colorBuffer.setPixel(0, 1, RgbaColor(30, 60, 240, 255))
        colorBuffer.setPixel(1, 1, RgbaColor(250, 220, 10, 255))

        assertTrue(
            !com.nexustools.genericui.image.MonocolorImageRecolorProcessor.isMonocolorRgba(
                colorBuffer.bytes,
                colorBuffer.width,
                colorBuffer.height
            )
        )
        val preservedColor = com.nexustools.genericui.image.MonocolorImageRecolorProcessor.processImageBuffer(
            buffer = colorBuffer,
            mode = com.nexustools.genericui.style.color.ImageTintMode.MONOCOLOR_AUTO_THEME,
            tintColor = null,
            isDarkTheme = true
        )
        assertEquals(RgbaColor(240, 20, 30, 255), preservedColor.getPixel(0, 0))

        // 3. Verify SRC_IN and MULTIPLY modes
        val srcInBuffer = com.nexustools.genericui.image.MonocolorImageRecolorProcessor.processImageBuffer(
            buffer = iconBuffer,
            mode = com.nexustools.genericui.style.color.ImageTintMode.SRC_IN,
            tintColor = RgbaColor(100, 150, 200, 255),
            isDarkTheme = false
        )
        assertEquals(RgbaColor(100, 150, 200, 255), srcInBuffer.getPixel(0, 0))

        // 4. Verify CssFilter.monocolorWhite() ("brightness(0) invert(1)") on ImageView
        val imgView = com.nexustools.genericui.component.content.ImageView()
        imgView.setImageBuffer(iconBuffer)
        imgView.cssFilter = com.nexustools.genericui.style.css.CssFilter.monocolorWhite()
        val filteredBuf = imgView.imageBuffer
        assertNotNull(filteredBuf)
        assertEquals(RgbaColor(255, 255, 255, 255), filteredBuf.getPixel(0, 0))
        assertEquals(128, filteredBuf.getPixel(1, 0).a)
    }

    @Test
    fun testElementLevelFullscreenOnComponentAndWindow() {
        val window = Window("Fullscreen Host")
        val root = com.nexustools.genericui.component.layout.VStack()
        val playerContainer = com.nexustools.genericui.component.layout.VStack()
        val controlsOverlay = com.nexustools.genericui.component.layout.HStack()
        playerContainer.addChild(controlsOverlay)
        root.addChild(playerContainer)
        window.setContent(root)

        val fullscreenEvents = mutableListOf<Boolean>()
        playerContainer.onFullscreenChanged = { isFs ->
            fullscreenEvents.add(isFs)
        }

        assertTrue(!window.isFullscreen())
        assertTrue(!playerContainer.isFullscreen())

        // Request element-level fullscreen on playerContainer
        playerContainer.requestFullscreen()
        assertTrue(playerContainer.isFullscreen())
        assertTrue(window.isFullscreen())
        assertSame(playerContainer, window.fullscreenElement)
        assertEquals("true", playerContainer.getAttribute("data-genericui-fullscreen-element"))
        assertEquals(listOf(true), fullscreenEvents)

        // Toggle fullscreen off
        val afterToggleOff = playerContainer.toggleFullscreen()
        assertTrue(!afterToggleOff)
        assertTrue(!playerContainer.isFullscreen())
        assertTrue(!window.isFullscreen())
        assertEquals(null, window.fullscreenElement)
        assertEquals(listOf(true, false), fullscreenEvents)
    }

    @Test
    fun testButtonWithImageAndStyledContainerAndStyledButtonStates() {
        val iconImg = com.nexustools.genericui.component.media.Image(src = "res://drawable/ic_play")
        val btnWithImage = Button(image = iconImg, text = "Play")
        assertSame(iconImg, btnWithImage.image)
        assertEquals("res://drawable/ic_play", btnWithImage.icon)

        val styledContainer = com.nexustools.genericui.component.layout.StyledContainer().apply {
            backgroundColor = RgbaColor(12, 8, 6, 200)
            setBorder(1, RgbaColor(255, 255, 255, 32), com.nexustools.genericui.enums.css.CssBorderStyle.SOLID, radiusPx = 8)
            setBackground(
                com.nexustools.genericui.style.gradient.Gradient.LinearGradient(
                    angleDegrees = 180f,
                    stops = listOf(
                        com.nexustools.genericui.style.gradient.Gradient.GradientStop(RgbaColor(12, 8, 6, 224), 0f),
                        com.nexustools.genericui.style.gradient.Gradient.GradientStop(RgbaColor(12, 8, 6, 0), 1f)
                    )
                )
            )
        }
        val containerSpec = styledContainer.resolveVisualSpec()
        assertEquals(RgbaColor(12, 8, 6, 200), containerSpec.backgroundColor)
        assertEquals(1f, containerSpec.borderWidthPx)
        assertEquals(8f, containerSpec.borderRadiusPx)
        assertNotNull(containerSpec.backgroundGradient)

        val styledBtn = com.nexustools.genericui.component.action.StyledButton(
            image = com.nexustools.genericui.component.media.Image(src = "res://drawable/ic_pause"),
            text = "Pause"
        ).apply {
            backgroundColor = RgbaColor(255, 255, 255, 16)
            textColor = RgbaColor(246, 240, 234, 255)
            setBorder(1, RgbaColor(255, 255, 255, 36), radiusPx = 6)
            setHoverStyle(
                backgroundColor = RgbaColor(255, 102, 0, 72),
                borderColor = RgbaColor(255, 122, 26, 210),
                textColor = RgbaColor(255, 255, 255, 255)
            )
            setFocusStyle(
                borderColor = RgbaColor(255, 122, 26, 245),
                focusRingColor = RgbaColor(255, 102, 0, 128),
                focusRingWidthPx = 2f
            )
            activeBackgroundColor = RgbaColor(255, 102, 0, 120)
            disabledOpacity = 0.4f
        }

        val baseResolved = styledBtn.resolveCurrentVisualSpec()
        assertEquals(RgbaColor(255, 255, 255, 16), baseResolved.backgroundColor)
        assertEquals(6f, baseResolved.borderRadiusPx)

        styledBtn.isHovered = true
        val hoverResolved = styledBtn.resolveCurrentVisualSpec()
        assertEquals(RgbaColor(255, 102, 0, 72), hoverResolved.backgroundColor)
        assertEquals(RgbaColor(255, 122, 26, 210), hoverResolved.borderColor)

        styledBtn.isFocused = true
        val focusResolved = styledBtn.resolveCurrentVisualSpec()
        assertEquals(RgbaColor(255, 122, 26, 245), focusResolved.borderColor)
        assertEquals(RgbaColor(255, 102, 0, 128), focusResolved.focusRingColor)
        assertEquals(2f, focusResolved.focusRingWidthPx)

        styledBtn.isActivePressed = true
        val activeResolved = styledBtn.resolveCurrentVisualSpec()
        assertEquals(RgbaColor(255, 102, 0, 120), activeResolved.backgroundColor)

        styledBtn.isEnabled = false
        val disabledResolved = styledBtn.resolveCurrentVisualSpec()
        assertEquals(0.4f, disabledResolved.opacity)
    }

    @Test
    fun testVStackAndHStackShrinkCrossAxisConstraint() {
        val vstack = com.nexustools.genericui.component.layout.VStack()
        val vChild = com.nexustools.genericui.component.content.ImageView("banner.png")
        vstack.addChild(
            vChild,
            com.nexustools.genericui.component.layout.StackConstraint.shrinkCrossAxis()
        )
        assertEquals("true", vChild.getAttribute("data-stack-shrink-cross"))
        assertEquals("0px", vChild.getStyle().getWidth()?.toCssString())
        assertEquals("100%", vChild.getStyle().getMinWidth()?.toCssString())
        assertEquals("100%", vChild.getStyle().getMaxWidth()?.toCssString())
        assertEquals("auto", vChild.getStyle().getHeight()?.toCssString())

        vstack.clearChildConstraint(vChild)
        assertEquals(null, vChild.getAttribute("data-stack-shrink-cross"))

        val hstack = com.nexustools.genericui.component.layout.HStack()
        val hChild = com.nexustools.genericui.component.content.ImageView("poster.png")
        hstack.addChild(
            hChild,
            com.nexustools.genericui.component.layout.StackConstraint(shrinkCrossAxis = true)
        )
        assertEquals("true", hChild.getAttribute("data-stack-shrink-cross"))
        assertEquals("0px", hChild.getStyle().getHeight()?.toCssString())
        assertEquals("100%", hChild.getStyle().getMinHeight()?.toCssString())
        assertEquals("100%", hChild.getStyle().getMaxHeight()?.toCssString())
        assertEquals("auto", hChild.getStyle().getWidth()?.toCssString())
    }
}
