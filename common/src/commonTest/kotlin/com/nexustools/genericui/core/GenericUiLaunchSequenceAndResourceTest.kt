package com.nexustools.genericui.core

import com.nexustools.genericui.component.action.Button
import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.fragment.FragmentBundle
import com.nexustools.genericui.component.fragment.FragmentRegistry
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.layout.StyledContainer
import com.nexustools.genericui.component.media.Image
import com.nexustools.genericui.component.shell.Application
import com.nexustools.genericui.component.shell.Window
import com.nexustools.genericui.component.typography.Icon
import com.nexustools.genericui.component.typography.Label
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
import com.nexustools.genericui.enums.ui.ThemeMode
import com.nexustools.genericui.enums.window.DisplayCutoutMode
import com.nexustools.genericui.enums.window.WindowState
import com.nexustools.genericui.event.KeyEvent
import com.nexustools.genericui.event.PointerEvent
import com.nexustools.genericui.event.WheelEvent
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
import com.nexustools.genericui.res.Resources
import com.nexustools.genericui.resources.ResourceID
import com.nexustools.genericui.resources.ResourceResolver
import com.nexustools.genericui.resources.ResourceType
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.ComputedStyle
import com.nexustools.genericui.style.css.CssStyleSheet
import com.nexustools.genericui.style.css.PrecompiledStyleSheet
import com.nexustools.genericui.style.theme.ThemeConfig
import com.nexustools.genericui.window.TitleBar
import com.nexustools.genericui.window.WindowConfiguration
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GenericUiLaunchSequenceAndResourceTest {

    private var savedThemeMode = GenericUI.defaultThemeMode
    private var savedThemeConfig = GenericUI.defaultThemeConfig
    private var savedAppName = GenericUI.applicationName
    private var savedActiveFactory = GenericUI.activeFactory

    @BeforeTest
    fun saveGenericUiState() {
        savedThemeMode = GenericUI.defaultThemeMode
        savedThemeConfig = GenericUI.defaultThemeConfig
        savedAppName = GenericUI.applicationName
        savedActiveFactory = GenericUI.activeFactory
    }

    @AfterTest
    fun restoreGenericUiState() {
        GenericUI.defaultThemeMode = savedThemeMode
        GenericUI.defaultThemeConfig = savedThemeConfig
        GenericUI.applicationName = savedAppName
        GenericUI.activeFactory = savedActiveFactory
    }

    private class CliAwareMainFragment : Fragment() {
        var receivedBundle: FragmentBundle? = null
        override fun onCreate(savedState: FragmentBundle?) {
            super.onCreate(savedState)
            receivedBundle = savedState
        }
    }

    private class SecondaryDetailFragment : Fragment()

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

    private class FakeStyledContainerNativeImpl(prefix: String) :
        StubNativeImpl(prefix), StyledContainerNativeImpl<Any> {
        private var visuals: com.nexustools.genericui.style.css.StyledVisualSpec =
            com.nexustools.genericui.style.css.StyledVisualSpec()
        override fun setStyledVisuals(visuals: com.nexustools.genericui.style.css.StyledVisualSpec) { this.visuals = visuals }
        override fun getStyledVisuals(): com.nexustools.genericui.style.css.StyledVisualSpec = visuals
        override fun addChildNative(childImpl: NativeImpl<*>, index: Int) {}
        override fun removeChildNative(childImpl: NativeImpl<*>) {}
        override fun clearChildrenNative() {}
    }

    private class FakeStyledComponentNativeImpl(prefix: String) :
        StubNativeImpl(prefix), com.nexustools.genericui.nativeimpl.surface.StyledComponentNativeImpl<Any> {
        private var visuals: com.nexustools.genericui.style.css.StyledVisualSpec =
            com.nexustools.genericui.style.css.StyledVisualSpec()
        private var hover: com.nexustools.genericui.style.css.StyledVisualSpec? = null
        private var focus: com.nexustools.genericui.style.css.StyledVisualSpec? = null
        private var active: com.nexustools.genericui.style.css.StyledVisualSpec? = null
        private var disabled: com.nexustools.genericui.style.css.StyledVisualSpec? = null
        override fun setStyledVisuals(visuals: com.nexustools.genericui.style.css.StyledVisualSpec) { this.visuals = visuals }
        override fun getStyledVisuals(): com.nexustools.genericui.style.css.StyledVisualSpec = visuals
        override fun setHoverVisuals(visuals: com.nexustools.genericui.style.css.StyledVisualSpec?) { this.hover = visuals }
        override fun getHoverVisuals(): com.nexustools.genericui.style.css.StyledVisualSpec? = hover
        override fun setFocusVisuals(visuals: com.nexustools.genericui.style.css.StyledVisualSpec?) { this.focus = visuals }
        override fun getFocusVisuals(): com.nexustools.genericui.style.css.StyledVisualSpec? = focus
        override fun setActiveVisuals(visuals: com.nexustools.genericui.style.css.StyledVisualSpec?) { this.active = visuals }
        override fun getActiveVisuals(): com.nexustools.genericui.style.css.StyledVisualSpec? = active
        override fun setDisabledVisuals(visuals: com.nexustools.genericui.style.css.StyledVisualSpec?) { this.disabled = visuals }
        override fun getDisabledVisuals(): com.nexustools.genericui.style.css.StyledVisualSpec? = disabled
    }

    private class FakeWindowNativeImpl : StubNativeImpl("window"), WindowNativeImpl<Any> {
        override val supportsCustomTitleBar: Boolean = true
        override val supportsEmbeddedMenuBarInTitleBar: Boolean = true
        override val supportsMultiWindow: Boolean = true
        private var title: String = "GenericUI"
        private var windowSize: Size = Size(1280, 720)
        private var windowState: WindowState = WindowState.NORMAL
        private var fullscreen: Boolean = false
        override fun addChildNative(childImpl: NativeImpl<*>, index: Int) {}
        override fun removeChildNative(childImpl: NativeImpl<*>) {}
        override fun clearChildrenNative() {}
        override fun setTitle(title: String) { this.title = title }
        override fun getTitle(): String = title
        override fun setWindowSize(size: Size) { windowSize = size }
        override fun getWindowSize(): Size = windowSize
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

    private class FakeLaunchNativeFactory : NativeFactory {
        private fun unsupported(): Nothing = throw UnsupportedOperationException("Unused in test")
        override val backendTarget: BackendTarget = BackendTarget.Swing
        override val supportsWindowStatePersistence: Boolean = false
        override fun createApplication(): ApplicationNativeImpl<*> = unsupported()
        override fun createWindow(): WindowNativeImpl<*> = FakeWindowNativeImpl()
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
        override fun createStyledContainer(classPrefix: String, tagName: String): StyledContainerNativeImpl<*> =
            FakeStyledContainerNativeImpl(classPrefix)
        override fun createStyledComponent(
            classPrefix: String,
            tagName: String
        ): com.nexustools.genericui.nativeimpl.surface.StyledComponentNativeImpl<*> =
            FakeStyledComponentNativeImpl(classPrefix)
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

    private class HiddenReceiverApplication : com.nexustools.genericui.component.shell.Application() {
        var onCreateMainFragmentCalls: Int = 0
        var lastReceivedArgs: List<String> = emptyList()

        override fun onCreateMainFragment(args: FragmentBundle?): Fragment? {
            onCreateMainFragmentCalls++
            lastReceivedArgs = args?.getCommandLineArgs().orEmpty()
            val fragment = super.createMainFragment(args) ?: CliAwareMainFragment()
            setFragment(fragment)
            mainWindow.hide()
            return fragment
        }
    }

    @Test
    fun `Desktop Application overrides onCreateMainFragment while Web ignores applicationFactory and Window supports non-destructive hide and show`() {
        val descriptor = GenericUiAppDescriptor(
            appId = "com.example.receiver",
            appName = "ReceiverApp",
            windowTitle = "Episode Caster Receiver",
            windowWidth = 1280,
            windowHeight = 720,
            mainFragmentClass = CliAwareMainFragment::class,
            mainFragmentFactory = { _ -> CliAwareMainFragment() },
            applicationFactory = { _ -> HiddenReceiverApplication() }
        )

        val desktopApp = GenericUiLaunchSequence.prepareApplication(
            factory = FakeLaunchNativeFactory(),
            args = arrayOf("--port", "8009"),
            descriptor = descriptor,
            isSystemTarget = true
        ) as HiddenReceiverApplication

        assertEquals(1, desktopApp.onCreateMainFragmentCalls)
        assertEquals(listOf("--port", "8009"), desktopApp.lastReceivedArgs)
        val mainFrag = desktopApp.currentFragment as? CliAwareMainFragment
        assertNotNull(mainFrag)
        assertFalse(desktopApp.mainWindow.isVisible, "Expected mainWindow to start hidden when mainWindow.hide() is called in onCreateMainFragment")
        assertFalse(mainFrag.isWindowVisible)

        // Show window when casting starts, toggle fullscreen, then hide again when casting stops
        mainFrag.setFullscreen(true)
        assertTrue(mainFrag.isFullscreen())
        assertTrue(desktopApp.mainWindow.isFullscreen())

        mainFrag.showWindow()
        assertTrue(desktopApp.mainWindow.isVisible)
        assertTrue(mainFrag.isWindowVisible)
        assertTrue(desktopApp.mainWindow.isFullscreen())

        mainFrag.hideWindow()
        assertFalse(desktopApp.mainWindow.isVisible)
        assertFalse(mainFrag.isWindowVisible)
        assertEquals(com.nexustools.genericui.enums.fragment.FragmentState.RESUMED, mainFrag.state)
        desktopApp.dispose()

        // Verify Web / non-desktop target ignores applicationFactory
        val webApp = GenericUiLaunchSequence.prepareApplication(
            factory = null,
            args = emptyArray(),
            descriptor = descriptor,
            isSystemTarget = false
        )
        assertFalse(webApp is HiddenReceiverApplication, "Non-desktop target must not instantiate custom Application override")
        assertNotNull(webApp.mainWindow.activeFragment as? CliAwareMainFragment)
    }

    @Test
    fun `GenericUiLaunchSequence injects command line args on system targets and omits on web`() {
        var resourceInstalled = false
        var providerInstalled = false

        val descriptor = GenericUiAppDescriptor(
            appId = "com.example.test",
            appName = "TestApp",
            windowTitle = "Test Window",
            windowWidth = 1440,
            windowHeight = 900,
            themeMode = "light",
            themeColor = "#ff5500",
            mainFragmentClass = CliAwareMainFragment::class,
            mainFragmentFactory = { _ -> CliAwareMainFragment() },
            fragmentRegistrations = listOf(
                CliAwareMainFragment::class to { _ -> CliAwareMainFragment() },
                SecondaryDetailFragment::class to { _ -> SecondaryDetailFragment() }
            ),
            providerInstallers = listOf({ providerInstalled = true }),
            resourceRegistryInstaller = { resourceInstalled = true }
        )

        val systemApp = GenericUiLaunchSequence.prepareApplication(
            factory = null,
            args = arrayOf("--workspace", "D:/Projects", "--debug"),
            descriptor = descriptor,
            isSystemTarget = true
        )
        assertTrue(resourceInstalled)
        assertTrue(providerInstalled)
        assertTrue(FragmentRegistry.isRegistered(SecondaryDetailFragment::class))
        assertEquals(ThemeMode.LIGHT, systemApp.getThemeMode())
        assertEquals("#ff5500", systemApp.getThemeColor())
        assertEquals("Test Window", systemApp.mainWindow.getTitle())
        assertEquals(1440, systemApp.mainWindow.getWindowSize().width)
        assertEquals(900, systemApp.mainWindow.getWindowSize().height)

        val activeMain = systemApp.mainWindow.activeFragment as? CliAwareMainFragment
        assertNotNull(activeMain)
        val sysBundle = activeMain.receivedBundle ?: activeMain.arguments
        assertNotNull(sysBundle)
        assertTrue(sysBundle.hasCommandLineArgs())
        assertEquals(listOf("--workspace", "D:/Projects", "--debug"), sysBundle.getCommandLineArgs())

        val webApp = GenericUiLaunchSequence.prepareApplication(
            factory = null,
            args = arrayOf("--ignored"),
            descriptor = descriptor,
            isSystemTarget = false
        )
        val webMain = webApp.mainWindow.activeFragment as? CliAwareMainFragment
        assertNotNull(webMain)
        assertFalse(webMain.receivedBundle?.hasCommandLineArgs() == true)
        assertFalse(webMain.arguments?.hasCommandLineArgs() == true)
    }

    @Test
    fun `ResourceID and ResourceResolver resolve strings colors dimens and component overloads`() {
        val titleId = ResourceID(0x7f020001, ResourceType.STRING, "welcome_msg")
        val accentId = ResourceID(0x7f030001, ResourceType.COLOR, "accent_color")
        val spacingId = ResourceID(0x7f040001, ResourceType.DIMEN, "header_height")
        val logoId = ResourceID(0x7f010001, ResourceType.DRAWABLE, "app_logo", "drawable/app_logo.png")

        Resources.activeTable.registerString(titleId, "Hello %1\$s (%2\$d)")
        Resources.activeTable.registerColor(accentId, RgbaColor.fromHex("#123456"))
        Resources.activeTable.registerDimen(spacingId, 64f)

        assertEquals("Hello World (42)", ResourceResolver.getString(titleId, "World", 42))
        assertEquals(RgbaColor.fromHex("#123456"), ResourceResolver.getColor(accentId))
        assertEquals(64f, ResourceResolver.getDimenPx(spacingId))
        assertTrue(ResourceResolver.getUrl(logoId).contains("app_logo"))

        val label = Label(titleId)
        assertEquals("Hello %1\$s (%2\$d)", label.text)
        label.setText(titleId, "GenericUI", 7)
        assertEquals("Hello GenericUI (7)", label.text)

        val btn = Button(titleId, logoId)
        assertTrue(btn.text.isNotEmpty())

        val img = Image(logoId)
        assertEquals(logoId, img.resourceId)

        val icon = Icon(logoId, 24)
        assertEquals(logoId, icon.iconRes)

        val win = Window(title = "Icon Window")
        win.setWindowIcon(logoId)
        assertEquals(logoId.toResourceUri(), win.getWindowIcon())
    }

    @Test
    fun `GenericUiLaunchSequence preserves explicit Application dark theme and applies stylesheets before single showWindow`() {
        val events = mutableListOf<String>()
        var showWindowCalls = 0
        val createdContainer = StyledContainer("root-box").apply {
            addClass("dark-panel")
        }

        class ExplicitDarkStyledApp : Application(
            initialContent = createdContainer,
            title = "Explicit Dark Receiver",
            initialThemeColor = "#4f8cff"
        ) {
            init {
                setThemeMode(ThemeMode.DARK)
                setThemeColor("#FF7E96")
            }

            override fun onApplicationStarted() {
                val bgHex = (createdContainer.computedStyle?.backgroundColor ?: createdContainer.getStyle().backgroundColor)
                    ?.toHex()?.lowercase().orEmpty()
                events.add("started:$bgHex")
                super.onApplicationStarted()
                if (!mainWindow.isWindowShown) {
                    mainWindow.show()
                }
            }
        }

        val appImpl = object : ApplicationNativeImpl<Any> {
            override val nativeApplication: Any = Any()
            override var isDisposed: Boolean = false
            private var mode: ThemeMode = ThemeMode.AUTO
            private var colorHex: String? = null
            private var themeConfig: ThemeConfig = ThemeConfig.DEFAULT
            override fun dispose() { isDisposed = true }
            override fun setThemeMode(mode: ThemeMode) { this.mode = mode }
            override fun getThemeMode(): ThemeMode = mode
            override fun setThemeColor(colorHex: String?) { this.colorHex = colorHex }
            override fun getThemeColor(): String? = colorHex
            override fun setThemeConfig(config: ThemeConfig) { this.themeConfig = config }
            override fun getThemeConfig(): ThemeConfig = themeConfig
            override fun runEventLoop(onStarted: () -> Unit) { onStarted() }
            override fun exitApplication(exitCode: Int) {}
        }

        val trackingFactory = object : NativeFactory by FakeLaunchNativeFactory() {
            override fun createApplication(): ApplicationNativeImpl<*> = appImpl
            override fun createWindow(): WindowNativeImpl<*> = object : WindowNativeImpl<Any> by FakeWindowNativeImpl() {
                override fun showWindow() {
                    showWindowCalls++
                    val bgHex = (createdContainer.computedStyle?.backgroundColor ?: createdContainer.getStyle().backgroundColor)
                        ?.toHex()?.lowercase().orEmpty()
                    events.add("show:$bgHex")
                }
            }
        }

        val parsedSheet = CssStyleSheet.parse(
            """
            .dark-panel {
                background-color: #112233;
            }
            """.trimIndent()
        )
        val compiledSheet = object : PrecompiledStyleSheet {
            override val name: String = "receiver_test"
            override val sourcePath: String = "styles/receiver_test.scss"
            override val cssResourcePath: String = "styles/receiver_test.css"
            override val styleSheet: CssStyleSheet = parsedSheet
            override val rawCss: String = ".dark-panel { background-color: #112233; }"
        }

        val descriptor = GenericUiAppDescriptor(
            appId = "com.example.dark.receiver",
            appName = "Explicit Dark Receiver",
            themeMode = "auto",
            themeColor = "#0d6efd",
            applicationFactory = { ExplicitDarkStyledApp() },
            precompiledStyleSheetInstaller = { it.addStyleSheet(compiledSheet) }
        )

        val prevApp = Application.current
        try {
            val launched = GenericUiLaunchSequence.launch(
                factory = trackingFactory,
                args = emptyArray(),
                descriptor = descriptor,
                isSystemTarget = true
            ) as ExplicitDarkStyledApp

            assertEquals(ThemeMode.DARK, launched.getThemeMode())
            assertEquals("#FF7E96", launched.getThemeColor())
            assertEquals(1, showWindowCalls, "Window.show must not be called redundantly twice during launch.")
            assertEquals(listOf("started:#112233", "show:#112233"), events)
            launched.dispose()
        } finally {
            Application.current = prevApp
        }
    }
}
