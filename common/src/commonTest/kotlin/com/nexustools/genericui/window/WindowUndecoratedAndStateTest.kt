package com.nexustools.genericui.window

import com.nexustools.genericui.component.action.Button
import com.nexustools.genericui.component.action.DropdownButton
import com.nexustools.genericui.component.action.FloatingActionButton
import com.nexustools.genericui.component.action.IconButton
import com.nexustools.genericui.component.action.SplitDropdownButton
import com.nexustools.genericui.component.action.StyledButton
import com.nexustools.genericui.component.action.ToggleButton
import com.nexustools.genericui.component.action.Toolbar
import com.nexustools.genericui.component.composite.Composite
import com.nexustools.genericui.component.feedback.Alert
import com.nexustools.genericui.component.feedback.Banner
import com.nexustools.genericui.component.feedback.ProgressBar
import com.nexustools.genericui.component.feedback.Spinner
import com.nexustools.genericui.component.feedback.Toast
import com.nexustools.genericui.component.form.CheckBox
import com.nexustools.genericui.component.form.ComboBox
import com.nexustools.genericui.component.form.RadioButton
import com.nexustools.genericui.component.form.RangeSlider
import com.nexustools.genericui.component.form.Select
import com.nexustools.genericui.component.form.Slider
import com.nexustools.genericui.component.form.StyledSlider
import com.nexustools.genericui.component.form.Switch
import com.nexustools.genericui.component.form.TextField
import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.fragment.FragmentContainer
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.layout.LayoutBackendTarget
import com.nexustools.genericui.component.layout.ScrollView
import com.nexustools.genericui.component.layout.StyledContainer
import com.nexustools.genericui.component.shell.Window
import com.nexustools.genericui.component.surface.Accordion
import com.nexustools.genericui.component.surface.Card
import com.nexustools.genericui.component.surface.CollapsiblePanel
import com.nexustools.genericui.component.surface.Jumbotron
import com.nexustools.genericui.component.surface.ListGroup
import com.nexustools.genericui.component.surface.ListGroupItem
import com.nexustools.genericui.component.surface.StyledComponent
import com.nexustools.genericui.component.surface.Well
import com.nexustools.genericui.component.tabs.Tab
import com.nexustools.genericui.component.tabs.TabContainer
import com.nexustools.genericui.component.typography.Badge
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.style.policy.AestheticStylePolicy
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
import com.nexustools.genericui.exception.NativeUninitializedException
import com.nexustools.genericui.geometry.EdgeInsets
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
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WindowUndecoratedAndStateTest {

    private var savedActiveFactory: NativeFactory? = null

    @BeforeTest
    fun setUp() {
        savedActiveFactory = GenericUI.activeFactory
    }

    @AfterTest
    fun tearDown() {
        GenericUI.activeFactory = savedActiveFactory
    }

    private open class StubNativeImpl(
        override val componentClassPrefix: String
    ) : NativeImpl<Any> {
        override val nativeWidget: Any = Any()
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
        override fun addClass(className: String) {
            val parts = this.className.split(" ").filter { it.isNotBlank() }.toMutableSet()
            parts.add(className)
            this.className = parts.joinToString(" ")
        }
        override fun removeClass(className: String) {
            val parts = this.className.split(" ").filter { it.isNotBlank() && it != className }
            this.className = parts.joinToString(" ")
        }
        override fun hasClass(className: String): Boolean =
            this.className.split(" ").any { it == className }
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

    private class FakeContainerNativeImpl(
        prefix: String
    ) : StubNativeImpl(prefix), ContainerNativeImpl<Any> {
        override fun addChildNative(childImpl: NativeImpl<*>, index: Int) {}
        override fun removeChildNative(childImpl: NativeImpl<*>) {}
        override fun clearChildrenNative() {}
    }

    private class FakeWindowNativeImpl(
        private val capabilities: TitleCapabilities = TitleCapabilities(
            isSupported = true,
            isTitleBarRequired = true,
            supportsCustomTitleBar = true,
            supportsCustomComponents = true
        )
    ) : StubNativeImpl("window"), WindowNativeImpl<Any> {
        override val supportsCustomTitleBar: Boolean = capabilities.supportsCustomTitleBar
        override val supportsEmbeddedMenuBarInTitleBar: Boolean = true
        override val supportsMultiWindow: Boolean = true
        override val supportsNavbarMenuBar: Boolean = true
        override val supportsMobileNavbarMenuBar: Boolean = false
        override fun getTitleCapabilities(): TitleCapabilities = capabilities

        private var title: String = "GenericUI"
        private var windowSize: Size = Size(800, 600)
        private var windowState: WindowState = WindowState.NORMAL
        private var fullscreen: Boolean = false
        private var undecorated: Boolean = false
        private var resizeListener: ((WindowConfiguration) -> Unit)? = null
        private var stateListener: ((WindowState) -> Unit)? = null

        val setUndecoratedCalls = mutableListOf<Boolean>()
        var startSystemMoveCount: Int = 0

        override fun addChildNative(childImpl: NativeImpl<*>, index: Int) {}
        override fun removeChildNative(childImpl: NativeImpl<*>) {}
        override fun clearChildrenNative() {}
        override fun setTitle(title: String) { this.title = title }
        override fun getTitle(): String = title
        override fun setWindowSize(size: Size) { windowSize = size }
        override fun getWindowSize(): Size = windowSize
        override fun setWindowState(state: WindowState) {
            windowState = state
            if (state == WindowState.FULLSCREEN) fullscreen = true
            else if (fullscreen) fullscreen = false
        }
        override fun getWindowState(): WindowState = windowState
        override fun setFullscreen(fullscreen: Boolean, cutoutMode: DisplayCutoutMode) {
            this.fullscreen = fullscreen
            if (fullscreen) {
                windowState = WindowState.FULLSCREEN
            } else if (windowState == WindowState.FULLSCREEN) {
                windowState = WindowState.NORMAL
            }
        }
        override fun isFullscreen(): Boolean = fullscreen
        override fun setFullscreenElement(elementImpl: NativeImpl<*>?, cutoutMode: DisplayCutoutMode) {
            setFullscreen(elementImpl != null, cutoutMode)
        }
        override fun applyTitleBarInstructions(titleBar: TitleBar?) {}
        override fun setMenuBarNative(menuBarImpl: MenuBarNativeImpl<*>?) {}
        override fun setNavBarNative(navBarImpl: NativeImpl<*>?) {}
        override fun setBottomNavigationBarNative(bottomNavImpl: NativeImpl<*>?) {}
        override fun isUndecorated(): Boolean = undecorated
        override fun setUndecorated(undecorated: Boolean) {
            this.undecorated = undecorated
            setUndecoratedCalls.add(undecorated)
        }
        override fun startSystemMove() {
            startSystemMoveCount++
        }
        override fun showWindow() {}
        override fun closeWindow() {}
        override fun setOnWindowResize(listener: ((WindowConfiguration) -> Unit)?) {
            resizeListener = listener
        }
        override fun setOnWindowStateChanged(listener: ((WindowState) -> Unit)?) {
            stateListener = listener
        }
        override fun setOnWindowFocusChanged(listener: ((Boolean) -> Unit)?) {}
        override fun setOnWindowCloseRequested(listener: (() -> Boolean)?) {}
        override fun configureAsToolModalWindow(
            ownerWindow: WindowNativeImpl<*>?,
            showInTaskbar: Boolean,
            minimizable: Boolean,
            alwaysOnTopOfOwner: Boolean
        ) {}

        fun emitNativeStateChanged(state: WindowState) {
            windowState = state
            fullscreen = (state == WindowState.FULLSCREEN)
            stateListener?.invoke(state)
        }

        fun emitNativeResize(config: WindowConfiguration) {
            windowSize = Size(config.widthPx, config.heightPx)
            windowState = config.windowState
            fullscreen = config.isFullscreen
            resizeListener?.invoke(config)
        }
    }

    private class FakeWindowTestFactory(
        override val defaultTitleCapabilities: TitleCapabilities = TitleCapabilities(
            isSupported = true,
            isTitleBarRequired = true,
            supportsCustomTitleBar = true,
            supportsCustomComponents = true
        ),
        private val windowCapabilitiesOverride: TitleCapabilities = defaultTitleCapabilities
    ) : NativeFactory {
        val createdWindows = mutableListOf<FakeWindowNativeImpl>()

        private fun unsupported(): Nothing = throw UnsupportedOperationException("Unused in WindowUndecoratedAndStateTest")

        override val backendTarget: BackendTarget = BackendTarget.Swing
        override fun createApplication(): ApplicationNativeImpl<*> = unsupported()
        override fun createWindow(): WindowNativeImpl<*> =
            FakeWindowNativeImpl(windowCapabilitiesOverride).also { createdWindows.add(it) }
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
        override fun createNetworkMonitor(): NetworkMonitorNativeImpl = unsupported()
        override fun supportsTcp(): Boolean = false
        override fun supportsUdp(): Boolean = false
    }

    @Test
    fun undecoratedAppliesBeforeAndAfterShowAndStartSystemMoveForwards() {
        val factory = FakeWindowTestFactory()
        GenericUI.activeFactory = factory

        val window = Window(initialSize = Size(480, 270))
        window.setContent(Container())

        assertFalse(window.isUndecorated)
        window.setUndecorated(true)
        assertTrue(window.isUndecorated)
        assertFailsWith<NativeUninitializedException> {
            window.startSystemMove()
        }
        assertTrue(factory.createdWindows.isEmpty())

        window.show(factory)
        assertEquals(1, factory.createdWindows.size)
        val fakeImpl = factory.createdWindows.single()
        assertTrue(fakeImpl.isUndecorated())
        assertEquals(listOf(true), fakeImpl.setUndecoratedCalls)
        assertTrue(window.isUndecorated)

        window.setUndecorated(false)
        assertFalse(fakeImpl.isUndecorated())
        assertFalse(window.isUndecorated)
        assertEquals(listOf(true, false), fakeImpl.setUndecoratedCalls)

        window.startSystemMove()
        window.startSystemMove()
        assertEquals(2, fakeImpl.startSystemMoveCount)

        window.close()
    }

    @Test
    fun setOnWindowStateChangedTracksProgrammaticAndNativeTransitionsWithDeduplication() {
        val factory = FakeWindowTestFactory()
        GenericUI.activeFactory = factory

        val window = Window(initialSize = Size(640, 360))
        window.setContent(Container())
        val observed = mutableListOf<WindowState>()
        window.setOnWindowStateChanged { observed.add(it) }

        window.show(factory)
        val fakeImpl = factory.createdWindows.single()
        observed.clear()

        window.minimize()
        window.minimize()
        assertEquals(WindowState.MINIMIZED, window.getWindowState())

        window.maximize()
        assertEquals(WindowState.MAXIMIZED, window.getWindowState())

        window.restore()
        assertEquals(WindowState.NORMAL, window.getWindowState())

        window.setFullscreen(true)
        assertEquals(WindowState.FULLSCREEN, window.getWindowState())

        window.setFullscreen(false)
        assertEquals(WindowState.NORMAL, window.getWindowState())

        fakeImpl.emitNativeStateChanged(WindowState.MINIMIZED)
        fakeImpl.emitNativeStateChanged(WindowState.MINIMIZED)
        assertEquals(WindowState.MINIMIZED, window.getWindowState())

        fakeImpl.emitNativeResize(
            WindowConfiguration(
                widthPx = 960,
                heightPx = 540,
                windowState = WindowState.MAXIMIZED
            )
        )
        fakeImpl.emitNativeResize(
            WindowConfiguration(
                widthPx = 960,
                heightPx = 540,
                windowState = WindowState.MAXIMIZED
            )
        )
        assertEquals(WindowState.MAXIMIZED, window.getWindowState())

        fakeImpl.emitNativeStateChanged(WindowState.NORMAL)
        assertEquals(WindowState.NORMAL, window.getWindowState())

        assertEquals(
            listOf(
                WindowState.MINIMIZED,
                WindowState.MAXIMIZED,
                WindowState.NORMAL,
                WindowState.FULLSCREEN,
                WindowState.NORMAL,
                WindowState.MINIMIZED,
                WindowState.MAXIMIZED,
                WindowState.NORMAL
            ),
            observed
        )

        window.close()
    }

    @Test
    fun fragmentPrefersUndecoratedWindowAppliesAndRestoresOnTransition() {
        val factory = FakeWindowTestFactory()
        GenericUI.activeFactory = factory

        val window = Window(initialSize = Size(800, 450))
        val container = com.nexustools.genericui.component.fragment.FragmentContainer(hostWindow = window)
        val undecoratedFrag = object : com.nexustools.genericui.component.fragment.Fragment() {
            override val prefersUndecoratedWindow: Boolean = true
        }
        val standardFrag = com.nexustools.genericui.component.fragment.Fragment()

        container.setFragment(undecoratedFrag)
        assertTrue(undecoratedFrag.isOverlayWindowTitleBarSupported)
        assertTrue(window.isUndecorated)
        window.setContent(container)
        window.show(factory)
        val fakeImpl = factory.createdWindows.single()
        assertTrue(fakeImpl.isUndecorated())

        container.setFragment(standardFrag)
        assertFalse(window.isUndecorated)
        assertFalse(fakeImpl.isUndecorated())

        window.close()
    }

    @Test
    fun fragmentPrefersUndecoratedWindowRefusesToStripChromeWhenLinuxWmUnsupported() {
        val unsupportedLinuxCaps = TitleCapabilities(
            isSupported = true,
            isTitleBarRequired = true,
            supportsCustomTitleBar = false,
            supportsCustomComponents = false
        )
        val factory = FakeWindowTestFactory(defaultTitleCapabilities = unsupportedLinuxCaps)
        GenericUI.activeFactory = factory

        val window = Window(initialSize = Size(800, 450))
        val container = FragmentContainer(hostWindow = window)
        val undecoratedFrag = object : Fragment() {
            override val prefersUndecoratedWindow: Boolean = true
        }
        val standardFrag = Fragment()

        container.setFragment(undecoratedFrag)
        assertFalse(undecoratedFrag.isOverlayWindowTitleBarSupported)
        assertFalse(window.isUndecorated)

        window.setContent(container)
        window.show(factory)
        val fakeImpl = factory.createdWindows.single()
        assertFalse(fakeImpl.isUndecorated())
        assertFalse(fakeImpl.setUndecoratedCalls.contains(true))

        container.setFragment(standardFrag)
        assertFalse(window.isUndecorated)
        assertFalse(fakeImpl.isUndecorated())

        container.setFragment(undecoratedFrag)
        assertFalse(window.isUndecorated)
        assertFalse(fakeImpl.isUndecorated())
        assertFalse(fakeImpl.setUndecoratedCalls.contains(true))

        window.close()
    }

    @Test
    fun fragmentLateCapabilityMismatchRestoresDecoratedFrameOnShow() {
        val unsupportedLinuxCaps = TitleCapabilities(
            isSupported = true,
            isTitleBarRequired = true,
            supportsCustomTitleBar = false,
            supportsCustomComponents = false
        )
        // Simulate factory default reporting true prior to window creation, while actual WindowNativeImpl
        // detects an unsupported Linux WM and reports supportsCustomTitleBar = false at realize/show time.
        val factory = FakeWindowTestFactory(
            defaultTitleCapabilities = TitleCapabilities(
                isSupported = true,
                isTitleBarRequired = true,
                supportsCustomTitleBar = true,
                supportsCustomComponents = true
            ),
            windowCapabilitiesOverride = unsupportedLinuxCaps
        )
        GenericUI.activeFactory = factory

        val window = Window(initialSize = Size(800, 450))
        val container = FragmentContainer(hostWindow = window)
        val undecoratedFrag = object : Fragment() {
            override val prefersUndecoratedWindow: Boolean = true
        }

        container.setFragment(undecoratedFrag)
        assertTrue(window.isUndecorated)
        window.setContent(container)
        window.show(factory)

        val fakeImpl = factory.createdWindows.single()
        assertFalse(undecoratedFrag.isOverlayWindowTitleBarSupported)
        assertFalse(window.isUndecorated)
        assertFalse(fakeImpl.isUndecorated())

        window.close()
    }

    @Test
    fun aestheticStylePolicyDistinguishesButtonsAndExplicitTokensFromSystemControls() {
        val btn = Button("Save")
        val iconBtn = IconButton("star")
        val toggleBtn = ToggleButton("Pin")
        val fabBtn = FloatingActionButton("plus")
        val styledBtn = StyledButton("Styled")
        val dropdownBtn = DropdownButton("Menu")
        val splitBtn = SplitDropdownButton("Run")
        val styledContainer = StyledContainer()
        val styledComponent = StyledComponent()
        val compositeControl = object : Composite("custom-composite") {}
        val styledSlider = StyledSlider()
        val card = Card("Card")
        val badge = Badge("New")
        val alert = Alert("Heads up")
        val banner = Banner("Notice")
        val toast = Toast("Info", "Saved")
        val listGroup = ListGroup()
        val listGroupItem = ListGroupItem()
        val jumbotron = Jumbotron()
        val well = Well()

        assertTrue(AestheticStylePolicy.isStandardButtonComponent(btn))
        assertTrue(AestheticStylePolicy.isStandardButtonComponent(iconBtn))
        assertTrue(AestheticStylePolicy.isStandardButtonComponent(toggleBtn))
        assertTrue(AestheticStylePolicy.isStandardButtonComponent(fabBtn))
        assertFalse(AestheticStylePolicy.isStandardButtonComponent(dropdownBtn))
        assertFalse(AestheticStylePolicy.isStandardButtonComponent(splitBtn))
        assertFalse(AestheticStylePolicy.isStandardButtonComponent(styledBtn))
        assertFalse(AestheticStylePolicy.isStandardButtonComponent(styledContainer))
        assertFalse(AestheticStylePolicy.isStandardButtonComponent(styledComponent))
        assertFalse(AestheticStylePolicy.isStandardButtonComponent(compositeControl))
        assertFalse(AestheticStylePolicy.isStandardButtonComponent(styledSlider))

        assertTrue(AestheticStylePolicy.isStandardButtonTagOrPrefix("button", "btn"))
        assertTrue(AestheticStylePolicy.isStandardButtonTagOrPrefix("button", "btn btn-fab"))
        assertFalse(AestheticStylePolicy.isStandardButtonTagOrPrefix("genericui:dropdownbutton", "btn-dropdown"))
        assertFalse(AestheticStylePolicy.isStandardButtonTagOrPrefix("button", "btn-split-dropdown"))
        assertFalse(AestheticStylePolicy.isStandardButtonTagOrPrefix("button", "styled-btn"))
        assertFalse(AestheticStylePolicy.isStandardButtonTagOrPrefix("genericui:toolbar", "btn-toolbar"))
        assertFalse(AestheticStylePolicy.isStandardButtonTagOrPrefix("genericui:progressbar"))
        assertFalse(AestheticStylePolicy.isStandardButtonTagOrPrefix("genericui:styled-slider", "styled-slider"))

        for (eligibleButton in listOf(btn, iconBtn, toggleBtn, fabBtn, styledBtn)) {
            assertTrue(
                AestheticStylePolicy.supportsBootstrapColorOnSystemBackend(eligibleButton),
                "Expected supportsBootstrapColorOnSystemBackend == true for ${eligibleButton::class.simpleName}"
            )
            assertFalse(
                AestheticStylePolicy.isBootstrapSpecificNonControlComponent(eligibleButton),
                "Expected isBootstrapSpecificNonControlComponent == false for ${eligibleButton::class.simpleName}"
            )
        }

        val bootstrapNonControls = listOf(
            badge,
            alert,
            banner,
            toast,
            card,
            listGroup,
            listGroupItem,
            jumbotron,
            well
        )
        for (nonControl in bootstrapNonControls) {
            assertTrue(
                AestheticStylePolicy.isBootstrapSpecificNonControlComponent(nonControl),
                "Expected isBootstrapSpecificNonControlComponent == true for ${nonControl::class.simpleName}"
            )
            assertTrue(
                AestheticStylePolicy.supportsBootstrapColorOnSystemBackend(nonControl),
                "Expected supportsBootstrapColorOnSystemBackend == true for ${nonControl::class.simpleName}"
            )
            assertFalse(
                AestheticStylePolicy.isStandardButtonComponent(nonControl),
                "Expected isStandardButtonComponent == false for ${nonControl::class.simpleName}"
            )
        }

        assertTrue(AestheticStylePolicy.isBootstrapSpecificNonControlTagOrPrefix("span", "badge"))
        assertTrue(AestheticStylePolicy.isBootstrapSpecificNonControlTagOrPrefix("genericui:alert"))
        assertTrue(AestheticStylePolicy.isBootstrapSpecificNonControlTagOrPrefix("genericui:banner"))
        assertTrue(AestheticStylePolicy.isBootstrapSpecificNonControlTagOrPrefix("genericui:toast"))
        assertTrue(AestheticStylePolicy.isBootstrapSpecificNonControlTagOrPrefix("genericui:card", "card"))
        assertTrue(AestheticStylePolicy.isBootstrapSpecificNonControlTagOrPrefix("genericui:listgroup", "list-group"))
        assertTrue(AestheticStylePolicy.isBootstrapSpecificNonControlTagOrPrefix("genericui:listgroupitem"))
        assertTrue(AestheticStylePolicy.isBootstrapSpecificNonControlTagOrPrefix("genericui:jumbotron"))
        assertTrue(AestheticStylePolicy.isBootstrapSpecificNonControlTagOrPrefix("genericui:well"))
        assertFalse(AestheticStylePolicy.isBootstrapSpecificNonControlTagOrPrefix("span", "label"))
        assertFalse(AestheticStylePolicy.isBootstrapSpecificNonControlTagOrPrefix("genericui:styled-slider", "styled-slider"))

        val nonBootstrapStyledSurfaces = listOf(styledContainer, styledComponent, compositeControl, styledSlider)
        for (surface in nonBootstrapStyledSurfaces) {
            assertTrue(
                AestheticStylePolicy.isExplicitlyStyledComponent(surface),
                "Expected isExplicitlyStyledComponent == true for ${surface::class.simpleName}"
            )
            assertFalse(
                AestheticStylePolicy.supportsBootstrapColorOnSystemBackend(surface),
                "Expected supportsBootstrapColorOnSystemBackend == false for ${surface::class.simpleName}"
            )
        }

        val nonButtonControlsAndContainers = listOf(
            dropdownBtn,
            splitBtn,
            ProgressBar(),
            Spinner(),
            styledSlider,
            compositeControl,
            CheckBox(),
            RadioButton("a"),
            Switch(),
            Slider(),
            RangeSlider(),
            Select(),
            ComboBox(),
            TextField(),
            ScrollView(),
            TabContainer(),
            Tab(),
            Toolbar(),
            CollapsiblePanel("Section"),
            Accordion(),
            Container()
        )
        for (control in nonButtonControlsAndContainers) {
            assertFalse(
                AestheticStylePolicy.isStandardButtonComponent(control),
                "Expected isStandardButtonComponent == false for ${control::class.simpleName}"
            )
            assertFalse(
                AestheticStylePolicy.isBootstrapSpecificNonControlComponent(control),
                "Expected isBootstrapSpecificNonControlComponent == false for ${control::class.simpleName}"
            )
            assertFalse(
                AestheticStylePolicy.supportsBootstrapColorOnSystemBackend(control),
                "Expected supportsBootstrapColorOnSystemBackend == false for ${control::class.simpleName}"
            )
            assertTrue(
                AestheticStylePolicy.supportsBootstrapColor(control, LayoutBackendTarget.WEB),
                "Expected WEB to support Bootstrap color for ${control::class.simpleName}"
            )
        }
    }
}
