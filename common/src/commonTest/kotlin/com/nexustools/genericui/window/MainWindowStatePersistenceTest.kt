package com.nexustools.genericui.window

import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.shell.Application
import com.nexustools.genericui.component.system.PreferenceStore
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.enums.bootstrap.AlignItems
import com.nexustools.genericui.enums.bootstrap.AlignSelf
import com.nexustools.genericui.enums.bootstrap.BootstrapSize
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.enums.bootstrap.BorderRadiusStyle
import com.nexustools.genericui.enums.bootstrap.DisplayMode
import com.nexustools.genericui.enums.bootstrap.FlexDirection
import com.nexustools.genericui.enums.bootstrap.FlexWrap
import com.nexustools.genericui.enums.bootstrap.FontWeight
import com.nexustools.genericui.enums.bootstrap.JustifyContent
import com.nexustools.genericui.enums.bootstrap.OverflowBehavior
import com.nexustools.genericui.enums.bootstrap.PositionMode
import com.nexustools.genericui.enums.bootstrap.ShadowElevation
import com.nexustools.genericui.enums.bootstrap.SpacingScale
import com.nexustools.genericui.enums.bootstrap.TextAlignment
import com.nexustools.genericui.enums.bootstrap.TextOverflow
import com.nexustools.genericui.enums.bootstrap.TextTransform
import com.nexustools.genericui.enums.system.BackendTarget
import com.nexustools.genericui.enums.window.DisplayCutoutMode
import com.nexustools.genericui.enums.window.WindowState
import com.nexustools.genericui.event.KeyEvent
import com.nexustools.genericui.event.PointerEvent
import com.nexustools.genericui.event.WheelEvent
import com.nexustools.genericui.geometry.EdgeInsets
import com.nexustools.genericui.geometry.Point
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.geometry.Size
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
import com.nexustools.genericui.style.css.ComputedStyle
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MainWindowStatePersistenceTest {

    private open class StubNativeImpl(val tag: String) : NativeImpl<Any> {
        override val nativeWidget: Any = tag
        override val componentClassPrefix: String = tag
        override var isDisposed: Boolean = false
        private var className: String = ""
        private var type: BootstrapType? = null
        private var outline: Boolean = false
        private var size: BootstrapSize? = null
        private var bgType: BootstrapType? = null
        private var textColorType: BootstrapType? = null
        private var borderType: BootstrapType? = null
        private var textAlign: TextAlignment? = null
        private var fontWeight: FontWeight? = null
        private var textTransform: TextTransform? = null
        private var textOverflow: TextOverflow? = null
        private var borderRadius: BorderRadiusStyle? = null
        private var shadow: ShadowElevation? = null
        private var display: DisplayMode? = null
        private var flexDir: FlexDirection? = null
        private var flexWrap: FlexWrap? = null
        private var justify: JustifyContent? = null
        private var alignItems: AlignItems? = null
        private var alignSelf: AlignSelf? = null
        private var overflow: OverflowBehavior? = null
        private var position: PositionMode? = null
        private var marginAll: SpacingScale? = null
        private var paddingAll: SpacingScale? = null
        private var visible: Boolean = true
        private var enabled: Boolean = true
        private var opacity: Float = 1f
        private var tooltip: String? = null

        override fun dispose() { isDisposed = true }
        override fun setClass(className: String) { this.className = className }
        override fun getClass(): String = className
        override fun addClass(className: String) {}
        override fun removeClass(className: String) {}
        override fun hasClass(className: String): Boolean = false
        override fun setType(type: BootstrapType?) { this.type = type }
        override fun getType(): BootstrapType? = type
        override fun setOutline(outline: Boolean) { this.outline = outline }
        override fun isOutline(): Boolean = outline
        override fun setSize(size: BootstrapSize?) { this.size = size }
        override fun getSize(): BootstrapSize? = size
        override fun setBackgroundType(type: BootstrapType?) { bgType = type }
        override fun getBackgroundType(): BootstrapType? = bgType
        override fun setTextColorType(type: BootstrapType?) { textColorType = type }
        override fun getTextColorType(): BootstrapType? = textColorType
        override fun setBorderType(type: BootstrapType?) { borderType = type }
        override fun getBorderType(): BootstrapType? = borderType
        override fun setTextAlignment(alignment: TextAlignment?) { textAlign = alignment }
        override fun getTextAlignment(): TextAlignment? = textAlign
        override fun setFontWeight(weight: FontWeight?) { fontWeight = weight }
        override fun getFontWeight(): FontWeight? = fontWeight
        override fun setTextTransform(transform: TextTransform?) { textTransform = transform }
        override fun getTextTransform(): TextTransform? = textTransform
        override fun setTextOverflow(overflow: TextOverflow?) { textOverflow = overflow }
        override fun getTextOverflow(): TextOverflow? = textOverflow
        override fun setBorderRadius(radius: BorderRadiusStyle?) { borderRadius = radius }
        override fun getBorderRadius(): BorderRadiusStyle? = borderRadius
        override fun setShadow(shadow: ShadowElevation?) { this.shadow = shadow }
        override fun getShadow(): ShadowElevation? = shadow
        override fun setDisplay(display: DisplayMode?) { this.display = display }
        override fun getDisplay(): DisplayMode? = display
        override fun setFlexDirection(direction: FlexDirection?) { flexDir = direction }
        override fun getFlexDirection(): FlexDirection? = flexDir
        override fun setFlexWrap(wrap: FlexWrap?) { flexWrap = wrap }
        override fun getFlexWrap(): FlexWrap? = flexWrap
        override fun setJustifyContent(justify: JustifyContent?) { this.justify = justify }
        override fun getJustifyContent(): JustifyContent? = justify
        override fun setAlignItems(align: AlignItems?) { alignItems = align }
        override fun getAlignItems(): AlignItems? = alignItems
        override fun setAlignSelf(align: AlignSelf?) { alignSelf = align }
        override fun getAlignSelf(): AlignSelf? = alignSelf
        override fun setOverflow(overflow: OverflowBehavior?) { this.overflow = overflow }
        override fun getOverflow(): OverflowBehavior? = overflow
        override fun setPosition(position: PositionMode?) { this.position = position }
        override fun getPosition(): PositionMode? = position
        override fun setMarginAll(scale: SpacingScale?) { marginAll = scale }
        override fun getMarginAll(): SpacingScale? = marginAll
        override fun setPaddingAll(scale: SpacingScale?) { paddingAll = scale }
        override fun getPaddingAll(): SpacingScale? = paddingAll
        override fun setVisible(visible: Boolean) { this.visible = visible }
        override fun isVisible(): Boolean = visible
        override fun setEnabled(enabled: Boolean) { this.enabled = enabled }
        override fun isEnabled(): Boolean = enabled
        override fun setOpacity(opacity: Float) { this.opacity = opacity }
        override fun getOpacity(): Float = opacity
        override fun setTooltipText(tooltip: String?) { this.tooltip = tooltip }
        override fun getTooltipText(): String? = tooltip
        override fun setPreferredSize(size: Size?) {}
        override fun getBounds(): Rect = Rect.ZERO
        override fun setPadding(padding: EdgeInsets) {}
        override fun setMargin(margin: EdgeInsets) {}
        override fun applyComputedStyle(style: ComputedStyle) {}
        override fun setOnClick(listener: (() -> Unit)?) {}
        override fun setOnDoubleClick(listener: (() -> Unit)?) {}
        override fun setOnContextMenu(listener: ((PointerEvent) -> Unit)?) {}
        override fun setOnPointerDown(listener: ((PointerEvent) -> Unit)?) {}
        override fun setOnPointerMove(listener: ((PointerEvent) -> Unit)?) {}
        override fun setOnPointerUp(listener: ((PointerEvent) -> Unit)?) {}
        override fun setOnWheel(listener: ((WheelEvent) -> Unit)?) {}
        override fun setOnKeyDown(listener: ((KeyEvent) -> Unit)?) {}
        override fun setOnKeyUp(listener: ((KeyEvent) -> Unit)?) {}
    }

    private class FakeContainerNativeImpl(prefix: String) : StubNativeImpl(prefix), ContainerNativeImpl<Any> {
        override fun addChildNative(childImpl: NativeImpl<*>, index: Int) {}
        override fun removeChildNative(childImpl: NativeImpl<*>) {}
        override fun clearChildrenNative() {}
    }

    private class FakePersistenceWindowNativeImpl : StubNativeImpl("window"), WindowNativeImpl<Any> {
        override val supportsCustomTitleBar: Boolean = true
        override val supportsEmbeddedMenuBarInTitleBar: Boolean = true
        override val supportsMultiWindow: Boolean = true
        private var title: String = "GenericUI"
        private var windowSize: Size = Size(1280, 800)
        private var posX: Int = 0
        private var posY: Int = 0
        private var hasPos: Boolean = false
        private var aspect: Double? = null
        private var minSize: Size? = null
        private var windowState: WindowState = WindowState.NORMAL
        private var fullscreen: Boolean = false
        override fun addChildNative(childImpl: NativeImpl<*>, index: Int) {}
        override fun removeChildNative(childImpl: NativeImpl<*>) {}
        override fun clearChildrenNative() {}
        override fun setTitle(title: String) { this.title = title }
        override fun getTitle(): String = title
        override fun setWindowSize(size: Size) { windowSize = size }
        override fun getWindowSize(): Size = windowSize
        override fun setWindowPosition(x: Int, y: Int) {
            posX = x
            posY = y
            hasPos = true
        }
        override fun getWindowPosition(): Point = Point(posX.toFloat(), posY.toFloat())
        override fun hasExplicitWindowPosition(): Boolean = hasPos
        override fun getMinWindowSize(): Size? = minSize
        override fun setMinWindowSize(minSize: Size?) { this.minSize = minSize }
        override fun setAspectRatio(aspectRatio: Double?) { this.aspect = aspectRatio }
        override fun getAspectRatio(): Double? = aspect
        override fun setWindowState(state: WindowState) {
            windowState = state
            fullscreen = (state == WindowState.FULLSCREEN)
        }
        override fun getWindowState(): WindowState = windowState
        override fun setFullscreen(fullscreen: Boolean, cutoutMode: DisplayCutoutMode) {
            this.fullscreen = fullscreen
            windowState = if (fullscreen) WindowState.FULLSCREEN else WindowState.NORMAL
        }
        override fun isFullscreen(): Boolean = fullscreen
        override fun setFullscreenElement(elementImpl: NativeImpl<*>?, cutoutMode: DisplayCutoutMode) {
            setFullscreen(elementImpl != null, cutoutMode)
        }
        override fun applyTitleBarInstructions(titleBar: TitleBar?) {}
        override fun setMenuBarNative(menuBarImpl: MenuBarNativeImpl<*>?) {}
        override fun setNavBarNative(navBarImpl: NativeImpl<*>?) {}
        override fun setBottomNavigationBarNative(bottomNavImpl: NativeImpl<*>?) {}
        override fun showWindow() {}
        override fun closeWindow() {}
        override fun setOnWindowResize(listener: ((WindowConfiguration) -> Unit)?) {}
        override fun setOnWindowFocusChanged(listener: ((Boolean) -> Unit)?) {}
        override fun setOnWindowCloseRequested(listener: (() -> Boolean)?) {}
    }

    private class FakePersistenceNativeFactory : NativeFactory {
        private fun unsupported(): Nothing = throw UnsupportedOperationException("Unused in MainWindowStatePersistenceTest")
        override val backendTarget: BackendTarget = BackendTarget.Swing
        override val supportsWindowStatePersistence: Boolean = true
        override fun createApplication(): ApplicationNativeImpl<*> = unsupported()
        override fun createWindow(): WindowNativeImpl<*> = FakePersistenceWindowNativeImpl()
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
        override fun createContainer(classPrefix: String): ContainerNativeImpl<*> = FakeContainerNativeImpl(classPrefix)
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

    private var savedFactory = GenericUI.activeFactory

    @AfterTest
    fun cleanup() {
        GenericUI.activeFactory = savedFactory
    }

    @Test
    fun savedStateRoundTripAndMonitorVisibilityOverlap() {
        val state = MainWindowSavedState(
            x = 120,
            y = 85,
            width = 1280,
            height = 720,
            fullscreen = false,
            windowState = WindowState.MAXIMIZED
        )
        val decoded = MainWindowSavedState.fromPreferenceElement(state.toPreferenceElement())
        assertNotNull(decoded)
        assertEquals(state, decoded)
        assertTrue(decoded.hasValidSize)
        assertTrue(decoded.hasPosition)
        assertEquals(Rect(120, 85, 1280, 720), decoded.toBounds())

        // Legacy fullscreen-only migration payload keeps fullscreen while leaving size unconfigured
        val legacyFullscreenOnly = MainWindowSavedState(width = 0, height = 0, fullscreen = false)
        val decodedLegacy = MainWindowSavedState.fromPreferenceElement(legacyFullscreenOnly.toPreferenceElement())
        assertNotNull(decodedLegacy)
        assertFalse(decodedLegacy.hasValidSize)
        assertFalse(decodedLegacy.fullscreen)
        assertNull(decodedLegacy.toBounds())

        val monitor = Rect(0, 0, 1920, 1080)
        assertTrue(MainWindowSavedState.intersectsVisibleArea(Rect(100, 100, 800, 600), monitor))
        assertTrue(MainWindowSavedState.intersectsVisibleArea(Rect(-700, 100, 800, 600), monitor)) // 100px visible
        assertFalse(MainWindowSavedState.intersectsVisibleArea(Rect(-790, 100, 800, 600), monitor)) // only 10px visible (<48)
        assertFalse(MainWindowSavedState.intersectsVisibleArea(Rect(2000, 100, 800, 600), monitor))
    }

    @Test
    fun consumerDefaultFullscreenAndGeometryPersistAcrossHideShowAndRestarts() {
        val prevFactory = GenericUI.activeFactory
        GenericUI.initialize(FakePersistenceNativeFactory())
        try {
            val prefNamespace = "common_main_win_persist_${Random.nextInt()}"
            val store = PreferenceStore(prefNamespace).apply {
                clear()
                flush()
            }

            class ReceiverLikeApp(private val customStore: PreferenceStore) : Application() {
                init {
                    defaultMainWindowFullscreen = true
                    mainWindow.setMinWindowSize(Size(640, 360))
                    mainWindow.setAspectRatio(16, 9)
                    mainWindow.setContent(Container())
                    mainWindow.hide()
                }

                override fun getPreferenceStore(): PreferenceStore = customStore
            }

            // 1. Starts hidden -> does NOT enter fullscreen prematurely
            val app1 = ReceiverLikeApp(store)
            assertFalse(app1.mainWindow.isVisible)
            assertFalse(app1.mainWindow.isFullscreen())

            // 2. First show enters consumer default fullscreen
            app1.mainWindow.show()
            assertTrue(app1.mainWindow.isVisible)
            assertTrue(app1.mainWindow.isFullscreen())

            // 3. Re-hiding and re-showing stays fullscreen
            app1.mainWindow.hide()
            assertFalse(app1.mainWindow.isVisible)
            assertTrue(app1.mainWindow.isFullscreen())
            app1.mainWindow.show()
            assertTrue(app1.mainWindow.isFullscreen())

            // 4. User exits fullscreen, moves and resizes window -> persisted immediately & flushed on hide
            app1.mainWindow.setFullscreen(false)
            assertFalse(app1.mainWindow.isFullscreen())
            app1.mainWindow.setWindowPosition(220, 140)
            app1.mainWindow.setWindowSize(Size(960, 540))
            app1.mainWindow.hide()

            val persisted = MainWindowSavedState.fromPreferenceElement(
                store.getElement(MainWindowSavedState.PREF_KEY)
            )
            assertNotNull(persisted)
            assertFalse(persisted.fullscreen)
            assertEquals(220, persisted.x)
            assertEquals(140, persisted.y)
            assertEquals(960, persisted.width)
            assertEquals(540, persisted.height)

            // 5. Re-showing preserves windowed geometry & non-fullscreen state
            app1.mainWindow.show()
            assertFalse(app1.mainWindow.isFullscreen())
            assertEquals(Rect(220, 140, 960, 540), app1.mainWindow.windowBounds)
            app1.dispose()

            // 6. Fresh Application restart reads saved state and overrides defaultMainWindowFullscreen=true
            val app2 = ReceiverLikeApp(store)
            assertFalse(app2.mainWindow.isFullscreen())
            app2.mainWindow.show()
            assertFalse(app2.mainWindow.isFullscreen())
            assertEquals(Rect(220, 140, 960, 540), app2.mainWindow.windowBounds)
            app2.dispose()
        } finally {
            GenericUI.activeFactory = prevFactory
        }
    }

    @Test
    fun consumerDefaultGeometryAppliesWhenEmptyAndOptOutFlagDisablesPersistence() {
        val prevFactory = GenericUI.activeFactory
        GenericUI.initialize(FakePersistenceNativeFactory())
        try {
            val prefNamespace = "common_main_win_optout_${Random.nextInt()}"
            val store = PreferenceStore(prefNamespace).apply {
                clear()
                flush()
            }

            val defaultApp = object : Application() {
                init {
                    setDefaultMainWindowGeometry(width = 1024, height = 576, x = 90, y = 75)
                    mainWindow.setContent(Container())
                }

                override fun getPreferenceStore(): PreferenceStore = store
            }
            defaultApp.mainWindow.show()
            assertEquals(Rect(90, 75, 1024, 576), defaultApp.mainWindow.windowBounds)

            // User mutates state -> written to store
            defaultApp.mainWindow.setWindowSize(Size(900, 500))
            defaultApp.mainWindow.setFullscreen(true)
            defaultApp.dispose()
            assertNotNull(store.getElement(MainWindowSavedState.PREF_KEY))

            // App opting out via persistMainWindowState = false ignores existing store & does not overwrite it
            val optOutApp = object : Application() {
                init {
                    persistMainWindowState = false
                    setDefaultMainWindowGeometry(width = 800, height = 600, x = 40, y = 30)
                    mainWindow.setContent(Container())
                }

                override fun getPreferenceStore(): PreferenceStore = store
            }
            optOutApp.mainWindow.show()
            assertFalse(optOutApp.mainWindow.isFullscreen())
            assertEquals(Rect(40, 30, 800, 600), optOutApp.mainWindow.windowBounds)
            optOutApp.mainWindow.setWindowSize(Size(700, 500))
            optOutApp.dispose()

            val untouchedSaved = MainWindowSavedState.fromPreferenceElement(
                store.getElement(MainWindowSavedState.PREF_KEY)
            )
            assertNotNull(untouchedSaved)
            assertTrue(untouchedSaved.fullscreen)
            assertEquals(900, untouchedSaved.width)
            assertEquals(500, untouchedSaved.height)
        } finally {
            GenericUI.activeFactory = prevFactory
        }
    }
}
