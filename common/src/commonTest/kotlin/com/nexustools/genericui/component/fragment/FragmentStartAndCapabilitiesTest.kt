package com.nexustools.genericui.component.fragment

import com.nexustools.genericui.component.dialog.DialogWindow
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.shell.Application
import com.nexustools.genericui.component.shell.FragmentApplication
import com.nexustools.genericui.component.shell.IApplication
import com.nexustools.genericui.component.shell.Window
import com.nexustools.genericui.component.shell.canOpenAdditionalWindow
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.core.GenericUiAppDescriptor
import com.nexustools.genericui.core.GenericUiLaunchSequence
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
import com.nexustools.genericui.enums.dialog.DialogPresentationStyle
import com.nexustools.genericui.enums.fragment.FragmentState
import com.nexustools.genericui.enums.system.AppTarget
import com.nexustools.genericui.enums.system.BackendTarget
import com.nexustools.genericui.enums.ui.ThemeMode
import com.nexustools.genericui.enums.window.DisplayCutoutMode
import com.nexustools.genericui.enums.window.WindowState
import com.nexustools.genericui.window.canOpenAdditionalWindow
import com.nexustools.genericui.event.KeyEvent
import com.nexustools.genericui.event.PointerEvent
import com.nexustools.genericui.event.WheelEvent
import com.nexustools.genericui.exception.FragmentMultipleWindowsUnsupportedException
import com.nexustools.genericui.exception.FragmentNotRegisteredException
import com.nexustools.genericui.exception.MultipleWindowsUnsupportedException
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
import com.nexustools.genericui.window.IWindow
import com.nexustools.genericui.window.TitleBar
import com.nexustools.genericui.window.WindowConfiguration
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class FragmentStartAndCapabilitiesTest {

    @MainAppFragment(targets = [AppTarget.COMMON], label = "Home", route = "/home")
    private class MainHomeFragment : Fragment() {
        val lifecycleLog = mutableListOf<String>()
        override fun onCreate(savedState: FragmentBundle?) {
            super.onCreate(savedState)
            lifecycleLog.add("onCreate")
        }
        override fun onStart() {
            super.onStart()
            lifecycleLog.add("onStart")
        }
        override fun onResume() {
            super.onResume()
            lifecycleLog.add("onResume")
        }
        override fun onPause() {
            super.onPause()
            lifecycleLog.add("onPause")
        }
        override fun onStop() {
            super.onStop()
            lifecycleLog.add("onStop")
        }
        override fun onDestroy() {
            super.onDestroy()
            lifecycleLog.add("onDestroy")
        }
    }

    @AppFragment(targets = [AppTarget.COMMON], label = "Single Window Detail", route = "/single")
    private class SingleWindowDetailFragment : Fragment()

    @AppFragment(
        targets = [AppTarget.COMMON],
        label = "Multi Window Inspector",
        route = "/multi",
        multipleWindows = true
    )
    private class MultiWindowInspectorFragment : Fragment() {
        var createdCount = 0
        override fun onCreate(savedState: FragmentBundle?) {
            super.onCreate(savedState)
            pageTitle = "Multi Window Inspector"
            createdCount++
        }
    }

    @AppFragment(
        targets = [AppTarget.COMMON],
        label = "Settings Dialog",
        route = "/settings",
        dialog = true
    )
    private class SettingsDialogFragment : Fragment() {
        val events = mutableListOf<String>()
        override fun onCreate(savedState: FragmentBundle?) {
            super.onCreate(savedState)
            pageTitle = "Settings"
            events.add("onCreate")
        }
        override fun onStart() {
            super.onStart()
            events.add("onStart")
        }
        override fun onResume() {
            super.onResume()
            events.add("onResume")
        }
        override fun onPause() {
            super.onPause()
            events.add("onPause")
        }
        override fun onStop() {
            super.onStop()
            events.add("onStop")
        }
        override fun onDestroy() {
            super.onDestroy()
            events.add("onDestroy")
        }
    }

    @AppFragment(label = "Unregistered")
    private class UnregisteredFragment : Fragment()

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
        override fun setTextOverflow(overflow: TextOverflow?) { this.textOverflow = overflow }
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

    private class FakeWindowNativeImpl : StubNativeImpl("window"), WindowNativeImpl<Any> {
        override val supportsCustomTitleBar: Boolean = true
        override val supportsEmbeddedMenuBarInTitleBar: Boolean = true
        override val supportsMultiWindow: Boolean = true
        private var title: String = "GenericUI"
        private var size: Size = Size(1024, 768)
        private var state: WindowState = WindowState.NORMAL
        private var fullscreen: Boolean = false
        var shownCount = 0
        var closedCount = 0
        override fun addChildNative(childImpl: NativeImpl<*>, index: Int) {}
        override fun removeChildNative(childImpl: NativeImpl<*>) {}
        override fun clearChildrenNative() {}
        override fun setTitle(title: String) { this.title = title }
        override fun getTitle(): String = title
        override fun setWindowSize(size: Size) { this.size = size }
        override fun getWindowSize(): Size = size
        override fun setWindowState(state: WindowState) { this.state = state }
        override fun getWindowState(): WindowState = state
        override fun setFullscreen(fullscreen: Boolean, cutoutMode: DisplayCutoutMode) { this.fullscreen = fullscreen }
        override fun isFullscreen(): Boolean = fullscreen
        override fun applyTitleBarInstructions(titleBar: TitleBar?) {}
        override fun setMenuBarNative(menuBarImpl: MenuBarNativeImpl<*>?) {}
        override fun setNavBarNative(navBarImpl: NativeImpl<*>?) {}
        override fun setBottomNavigationBarNative(bottomNavImpl: NativeImpl<*>?) {}
        override fun showWindow() { shownCount++ }
        override fun closeWindow() { closedCount++ }
        override fun setOnWindowResize(listener: ((WindowConfiguration) -> Unit)?) {}
        override fun setOnWindowFocusChanged(listener: ((Boolean) -> Unit)?) {}
        override fun setOnWindowCloseRequested(listener: (() -> Boolean)?) {}
    }

    private class FakeDialogWindowNativeImpl : StubNativeImpl("dialog"), DialogWindowNativeImpl<Any> {
        private var title: String = ""
        private var style: DialogPresentationStyle = DialogPresentationStyle.MODAL_CENTERED
        private var onClosed: (() -> Unit)? = null
        val ownerHistory = mutableListOf<WindowNativeImpl<*>?>()
        var showDialogCalls = 0
        var closeDialogCalls = 0
        override fun addChildNative(childImpl: NativeImpl<*>, index: Int) {}
        override fun removeChildNative(childImpl: NativeImpl<*>) {}
        override fun clearChildrenNative() {}
        override fun setTitle(title: String) { this.title = title }
        override fun getTitle(): String = title
        override fun setPresentationStyle(style: DialogPresentationStyle) { this.style = style }
        override fun getPresentationStyle(): DialogPresentationStyle = style
        override fun setModal(modal: Boolean) {}
        override fun setFullscreen(fullscreen: Boolean) {}
        override fun showDialog(ownerWindow: WindowNativeImpl<*>?) {
            ownerHistory.add(ownerWindow)
            showDialogCalls++
        }
        override fun closeDialog() {
            closeDialogCalls++
            onClosed?.invoke()
        }
        override fun setOnDialogClosed(listener: (() -> Unit)?) { onClosed = listener }
        fun simulateUserClosed() { onClosed?.invoke() }
    }

    private class FakeApplicationNativeImpl : ApplicationNativeImpl<Any> {
        override val nativeApplication: Any = "fake-app"
        override var isDisposed: Boolean = false
        private var mode: ThemeMode = ThemeMode.AUTO
        var runEventLoopCalls: Int = 0
        var exitCalls: Int = 0
        var lastExitCode: Int? = null

        override fun dispose() { isDisposed = true }
        override fun setThemeMode(mode: ThemeMode) { this.mode = mode }
        override fun getThemeMode(): ThemeMode = mode
        override fun runEventLoop(onStarted: () -> Unit) {
            runEventLoopCalls++
            onStarted()
        }
        override fun exitApplication(exitCode: Int) {
            exitCalls++
            lastExitCode = exitCode
        }
    }

    private class FakeConfigurableNativeFactory(
        override val backendTarget: BackendTarget = BackendTarget.Swing,
        override val supportsMultipleWindows: Boolean = true,
        override val supportsDialogWindows: Boolean = true,
        override val supportsToolModalWindows: Boolean = true,
        override val supportsSystemTray: Boolean = true,
        override val supportsSystemMenuBar: Boolean = false,
        override val supportsDirectorySelection: Boolean = true,
        override val supportsDirectFileSystemAccess: Boolean = true,
        override val supportsServices: Boolean = false
    ) : NativeFactory {
        val createdApps = mutableListOf<FakeApplicationNativeImpl>()
        val createdWindows = mutableListOf<FakeWindowNativeImpl>()
        val createdDialogs = mutableListOf<FakeDialogWindowNativeImpl>()
        private fun unsupported(): Nothing = throw UnsupportedOperationException("Unused in FragmentStartAndCapabilitiesTest")
        override val supportsWindowStatePersistence: Boolean = false
        override fun createApplication(): ApplicationNativeImpl<*> = FakeApplicationNativeImpl().also { createdApps.add(it) }
        override fun createWindow(): WindowNativeImpl<*> = FakeWindowNativeImpl().also { createdWindows.add(it) }
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
        override fun createDialogWindow(): DialogWindowNativeImpl<*> = FakeDialogWindowNativeImpl().also { createdDialogs.add(it) }
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
        override fun supportsTcp(): Boolean = true
        override fun supportsUdp(): Boolean = true
    }

    private var savedFactory: NativeFactory? = null

    @BeforeTest
    fun setUp() {
        savedFactory = GenericUI.activeFactory
        GenericUI.resetForTests()
    }

    @AfterTest
    fun tearDown() {
        GenericUI.resetForTests()
        GenericUI.activeFactory = savedFactory
    }

    private fun registerStandardTestFragments() {
        FragmentRegistry.register(
            fragmentClass = MainHomeFragment::class,
            fragmentId = "com.example.MainHomeFragment",
            route = "/home",
            label = "Home",
            isMainFragment = true,
            multipleWindows = false,
            dialog = false
        ) { MainHomeFragment() }
        FragmentRegistry.register(
            fragmentClass = SingleWindowDetailFragment::class,
            fragmentId = "com.example.SingleWindowDetailFragment",
            route = "/single",
            label = "Single",
            isMainFragment = false,
            multipleWindows = false,
            dialog = false
        ) { SingleWindowDetailFragment() }
        FragmentRegistry.register(
            fragmentClass = MultiWindowInspectorFragment::class,
            fragmentId = "com.example.MultiWindowInspectorFragment",
            route = "/multi",
            label = "Multi",
            isMainFragment = false,
            multipleWindows = true,
            dialog = false
        ) { MultiWindowInspectorFragment() }
        FragmentRegistry.register(
            fragmentClass = SettingsDialogFragment::class,
            fragmentId = "com.example.SettingsDialogFragment",
            route = "/settings",
            label = "Settings",
            isMainFragment = false,
            multipleWindows = false,
            dialog = true
        ) { SettingsDialogFragment() }
    }

    @Test
    fun metadataDefaultsAndCapabilityQueriesAcrossGenericUiIApplicationIWindowAndIFragment() {
        val defaultMeta = FragmentMetadata(fragmentId = "com.example.DefaultFrag")
        assertFalse(defaultMeta.isMainFragment)
        assertFalse(defaultMeta.multipleWindows)
        assertFalse(defaultMeta.dialog)

        registerStandardTestFragments()
        // MainHomeFragment cannot be marked dialog = true even if attempted directly
        FragmentRegistry.registerMetadata(
            fragmentClass = MainHomeFragment::class,
            metadata = FragmentMetadata(
                fragmentId = "com.example.MainHomeFragment",
                route = "/home",
                label = "Home",
                isMainFragment = true,
                multipleWindows = false,
                dialog = true
            )
        )
        assertFalse(FragmentRegistry.isDialogFragment(MainHomeFragment::class))
        assertFalse(FragmentRegistry.supportsMultipleWindows(MainHomeFragment::class))
        assertFalse(FragmentRegistry.supportsMultipleWindows(SingleWindowDetailFragment::class))
        assertTrue(FragmentRegistry.supportsMultipleWindows(MultiWindowInspectorFragment::class))
        assertTrue(FragmentRegistry.isDialogFragment(SettingsDialogFragment::class))
        assertNotNull(FragmentRegistry.getMetadataByIdOrRoute("/settings"))

        // Multi-window desktop backend capability checks
        val desktopFactory = FakeConfigurableNativeFactory(
            backendTarget = BackendTarget.MacOS,
            supportsMultipleWindows = true,
            supportsDialogWindows = true,
            supportsToolModalWindows = true,
            supportsSystemTray = true,
            supportsSystemMenuBar = true,
            supportsDirectorySelection = true,
            supportsDirectFileSystemAccess = true,
            supportsServices = false
        )
        GenericUI.initialize(desktopFactory)

        val homeFrag = MainHomeFragment()
        val app = FragmentApplication(initialFragment = homeFrag, title = "CapTest")
        val iApp: IApplication = app
        val iWin: IWindow = app.mainIWindow
        val iFrag: IFragment = homeFrag

        assertTrue(GenericUI.isInitialized)
        assertEquals(BackendTarget.MacOS, GenericUI.backendTarget)
        assertEquals(BackendTarget.MacOS.displayName, GenericUI.backendName)
        assertTrue(GenericUI.supportsMultipleWindows)
        assertTrue(GenericUI.supportsDialogWindows)
        assertTrue(GenericUI.supportsToolModalWindows)
        assertTrue(GenericUI.supportsSystemTray)
        assertTrue(GenericUI.supportsSystemMenuBar)
        assertTrue(GenericUI.supportsDirectorySelection)
        assertTrue(GenericUI.supportsDirectFileSystemAccess)
        assertFalse(GenericUI.supportsServices)

        assertTrue(iApp.supportsMultipleWindows)
        assertTrue(iApp.supportsDialogWindows)
        assertTrue(iWin.supportsMultipleWindows)
        assertTrue(iWin.supportsDialogWindows)
        assertTrue(iFrag.supportsMultipleWindows)
        assertTrue(iFrag.supportsDialogWindows)

        assertFalse(GenericUI.canOpenAdditionalWindow<SingleWindowDetailFragment>())
        assertFalse(iApp.canOpenAdditionalWindow<SingleWindowDetailFragment>())
        assertFalse(iWin.canOpenAdditionalWindow<SingleWindowDetailFragment>())
        assertFalse(iFrag.canOpenAdditionalWindow<SingleWindowDetailFragment>())

        assertFalse(GenericUI.canOpenAdditionalWindow<SettingsDialogFragment>())
        assertTrue(GenericUI.canOpenAdditionalWindow<MultiWindowInspectorFragment>())
        assertTrue(iApp.canOpenAdditionalWindow<MultiWindowInspectorFragment>())
        assertTrue(iWin.canOpenAdditionalWindow<MultiWindowInspectorFragment>())
        assertTrue(iFrag.canOpenAdditionalWindow<MultiWindowInspectorFragment>())

        // Unregistered fragment fails loudly with FragmentNotRegisteredException
        assertFailsWith<FragmentNotRegisteredException> {
            homeFrag.startFragment<UnregisteredFragment>()
        }

        // Single-window backend (Web/Android) reports false across all interfaces
        val singleWindowFactory = FakeConfigurableNativeFactory(
            backendTarget = BackendTarget.Web,
            supportsMultipleWindows = false,
            supportsDialogWindows = true,
            supportsToolModalWindows = false,
            supportsSystemTray = false,
            supportsDirectorySelection = false,
            supportsDirectFileSystemAccess = false
        )
        GenericUI.initialize(singleWindowFactory)
        assertFalse(GenericUI.supportsMultipleWindows)
        assertFalse(iApp.supportsMultipleWindows)
        assertFalse(iWin.supportsMultipleWindows)
        assertFalse(iFrag.supportsMultipleWindows)
        assertFalse(GenericUI.canOpenAdditionalWindow<MultiWindowInspectorFragment>())
        assertFalse(iApp.canOpenAdditionalWindow<MultiWindowInspectorFragment>())
        assertFalse(iWin.canOpenAdditionalWindow<MultiWindowInspectorFragment>())
        assertFalse(iFrag.canOpenAdditionalWindow<MultiWindowInspectorFragment>())

        app.dispose()
    }

    @Test
    fun startFragmentOpenAdditionalWindowValidationAndWindowLifecycle() {
        registerStandardTestFragments()

        // 1. Single-window backend rejects openAdditionalWindow = true with MultipleWindowsUnsupportedException
        val webFactory = FakeConfigurableNativeFactory(
            backendTarget = BackendTarget.Web,
            supportsMultipleWindows = false
        )
        GenericUI.initialize(webFactory)
        val homeWeb = MainHomeFragment()
        val webApp = FragmentApplication(initialFragment = homeWeb, title = "WebApp")
        webApp.mainWindow.show(webFactory)

        val singleBackendEx = assertFailsWith<MultipleWindowsUnsupportedException> {
            homeWeb.startFragment<MultiWindowInspectorFragment>(openAdditionalWindow = true)
        }
        assertEquals("multipleWindows", singleBackendEx.capability)
        webApp.dispose()

        // 2. Multi-window backend rejects fragment with multipleWindows = false via FragmentMultipleWindowsUnsupportedException
        val desktopFactory = FakeConfigurableNativeFactory(
            backendTarget = BackendTarget.Windows,
            supportsMultipleWindows = true
        )
        GenericUI.initialize(desktopFactory)
        val homeDesktop = MainHomeFragment()
        val desktopApp = FragmentApplication(initialFragment = homeDesktop, title = "DesktopApp")
        desktopApp.mainWindow.show(desktopFactory)

        assertFailsWith<FragmentMultipleWindowsUnsupportedException> {
            homeDesktop.startFragment<SingleWindowDetailFragment>(openAdditionalWindow = true)
        }

        // 3. Multi-window backend + multipleWindows = true opens a separate top-level Window
        val inspector = homeDesktop.startFragment<MultiWindowInspectorFragment>(
            "pane" to "layers",
            openAdditionalWindow = true
        )
        val secondWindow = inspector.hostingWindow as? Window
        assertNotNull(secondWindow)
        assertNotSame(desktopApp.mainWindow, secondWindow)
        assertSame(desktopApp.mainIWindow, secondWindow.ownerWindow)
        assertTrue(secondWindow.isWindowShown)
        assertTrue(secondWindow.isVisible)
        assertEquals("layers", inspector.arguments?.getString("pane"))
        assertEquals(FragmentState.RESUMED, inspector.state)

        // Finishing the additional-window fragment closes its secondary Window cleanly
        assertTrue(inspector.finishFragment())
        assertFalse(secondWindow.isWindowShown)

        desktopApp.dispose()
    }

    @Test
    fun dialogFragmentOpensOvertopActiveMainFragmentAndStandaloneWithFullLifecycle() {
        registerStandardTestFragments()
        val factory = FakeConfigurableNativeFactory(
            backendTarget = BackendTarget.MacOS,
            supportsMultipleWindows = true,
            supportsDialogWindows = true
        )
        GenericUI.initialize(factory)

        // Part A: Active mainWindow shown -> dialog = true opens DialogWindow attached overtop mainWindow
        val mainFrag = MainHomeFragment()
        val app = FragmentApplication(initialFragment = mainFrag, title = "Studio")
        app.mainWindow.show(factory)
        assertEquals(FragmentState.RESUMED, mainFrag.state)

        val settings1 = mainFrag.startFragment<SettingsDialogFragment>("tab" to "audio")
        val dialogWin1 = settings1.hostingWindow as? DialogWindow
        assertNotNull(dialogWin1)
        assertSame(app.mainWindow, dialogWin1.ownerWindow)
        assertTrue(dialogWin1.isWindowShown)
        assertSame(factory.createdWindows.single(), factory.createdDialogs.single().ownerHistory.single())
        assertEquals(listOf("onCreate", "onStart", "onResume"), settings1.events)
        assertEquals(FragmentState.RESUMED, settings1.state)
        assertEquals(FragmentState.RESUMED, mainFrag.state, "Main fragment underneath dialog must stay RESUMED")

        // Launching same non-multiwindow dialog again updates args & brings existing dialog to front
        val settingsReuse = mainFrag.startFragment<SettingsDialogFragment>("tab" to "video")
        assertSame(settings1, settingsReuse)
        assertEquals("video", settings1.arguments?.getString("tab"))
        assertEquals(1, factory.createdDialogs.size)
        assertEquals(2, factory.createdDialogs.single().showDialogCalls)

        // Closing via finishFragment tears down dialog lifecycle: onPause -> onStop -> onDestroy
        assertTrue(settings1.finishFragment())
        assertFalse(dialogWin1.isWindowShown)
        assertEquals(
            listOf("onCreate", "onStart", "onResume", "onPause", "onStop", "onDestroy"),
            settings1.events
        )
        assertEquals(FragmentState.DESTROYED, settings1.state)
        assertEquals(1, factory.createdDialogs.single().closeDialogCalls)
        app.dispose()

        // Part B: Secondary/standalone launch via GenericUiLaunchSequence (dialog = true or --open-additional-window=true)
        GenericUI.resetForTests()
        GenericUI.initialize(factory)
        val descriptor = GenericUiAppDescriptor(
            appId = "com.example.studio",
            appName = "Studio",
            mainFragmentClass = MainHomeFragment::class,
            mainFragmentFactory = { MainHomeFragment() },
            fragmentRegistrations = listOf(
                MainHomeFragment::class to { _ -> MainHomeFragment() },
                MultiWindowInspectorFragment::class to { _ -> MultiWindowInspectorFragment() },
                SettingsDialogFragment::class to { _ -> SettingsDialogFragment() }
            ),
            fragmentMetadata = mapOf(
                MainHomeFragment::class to FragmentMetadata(
                    fragmentId = "com.example.MainHomeFragment",
                    route = "/home",
                    label = "Home",
                    isMainFragment = true,
                    multipleWindows = false,
                    dialog = false
                ),
                MultiWindowInspectorFragment::class to FragmentMetadata(
                    fragmentId = "com.example.MultiWindowInspectorFragment",
                    route = "/multi",
                    label = "Multi",
                    isMainFragment = false,
                    multipleWindows = true,
                    dialog = false
                ),
                SettingsDialogFragment::class to FragmentMetadata(
                    fragmentId = "com.example.SettingsDialogFragment",
                    route = "/settings",
                    label = "Settings",
                    isMainFragment = false,
                    multipleWindows = false,
                    dialog = true
                )
            )
        )

        // Standalone launch for --fragment=/settings sets suppressInitialMainWindowShow = true
        val preparedDialogApp = GenericUiLaunchSequence.prepareApplication(
            factory = factory,
            args = arrayOf("--fragment=/settings"),
            descriptor = descriptor,
            isSystemTarget = true
        ) as Application
        assertTrue(preparedDialogApp.suppressInitialMainWindowShow)
        assertFalse(preparedDialogApp.mainWindow.isWindowShown)

        // Opening SettingsDialogFragment when mainWindow is hidden/unshown opens standalone (ownerWindow == null)
        val standaloneSettings = preparedDialogApp.startFragment<SettingsDialogFragment>("tab" to "general")
        val standaloneDialogWin = standaloneSettings.hostingWindow as? DialogWindow
        assertNotNull(standaloneDialogWin)
        assertNull(standaloneDialogWin.ownerWindow)
        assertTrue(standaloneDialogWin.isWindowShown)
        assertNull(factory.createdDialogs.last().ownerHistory.last())
        assertEquals(listOf("onCreate", "onStart", "onResume"), standaloneSettings.events)
        assertEquals(FragmentState.RESUMED, standaloneSettings.state)

        standaloneDialogWin.close()
        assertEquals(
            listOf("onCreate", "onStart", "onResume", "onPause", "onStop", "onDestroy"),
            standaloneSettings.events
        )
        assertEquals(FragmentState.DESTROYED, standaloneSettings.state)
        preparedDialogApp.dispose()

        // CLI launch with --open-additional-window=true also sets suppressInitialMainWindowShow = true
        val preparedMultiApp = GenericUiLaunchSequence.prepareApplication(
            factory = factory,
            args = arrayOf("--fragment=/multi", "--open-additional-window=true"),
            descriptor = descriptor,
            isSystemTarget = true
        ) as Application
        assertTrue(preparedMultiApp.suppressInitialMainWindowShow)
        preparedMultiApp.dispose()
    }

    @Test
    fun exitWhenNoFragmentsOpenRootFinishAndIdempotentDialogCloseLifecycle() {
        registerStandardTestFragments()
        val factory = FakeConfigurableNativeFactory(
            backendTarget = BackendTarget.MacOS,
            supportsMultipleWindows = true,
            supportsDialogWindows = true
        )
        GenericUI.initialize(factory)

        // 1. Finishing root mainWindow fragment while a dialog remains hides mainWindow and keeps app alive;
        //    closing the final dialog triggers exit(0).
        val mainFrag = MainHomeFragment()
        val app = FragmentApplication(initialFragment = mainFrag, title = "LifecycleExitTest")
        assertTrue(app.exitWhenNoFragmentsOpen)
        app.run(factory)
        val fakeApp = factory.createdApps.single()
        assertTrue(app.hasAnyOpenFragment())
        assertFalse(FragmentLaunchCoordinator.hasOpenFragments())
        assertEquals(0, fakeApp.exitCalls)

        val settingsFrag = mainFrag.startFragment<SettingsDialogFragment>()
        val dialogWin = settingsFrag.hostingWindow as DialogWindow
        assertTrue(FragmentLaunchCoordinator.hasOpenFragments())

        // Re-entrant close check: calling dialogWin.close() inside onCloseHandler must invoke native closeDialog once
        var reentrantCloseAttempted = false
        val prevCloseHandler = dialogWin.onCloseHandler
        dialogWin.onCloseHandler = {
            prevCloseHandler?.invoke()
            if (!reentrantCloseAttempted) {
                reentrantCloseAttempted = true
                dialogWin.close()
            }
        }

        // Finish root fragment while dialog stays open -> clears main fragment, hides mainWindow, does NOT exit
        assertTrue(mainFrag.finishFragment())
        assertNull(app.mainWindow.fragmentContainer?.currentFragment)
        assertFalse(app.mainWindow.isVisible)
        assertEquals(0, fakeApp.exitCalls)
        assertTrue(app.hasAnyOpenFragment())

        // Closing the final open dialog exits app cleanly and closes native dialog once
        dialogWin.close()
        assertTrue(reentrantCloseAttempted)
        assertEquals(1, factory.createdDialogs.single().closeDialogCalls)
        assertFalse(FragmentLaunchCoordinator.hasOpenFragments())
        assertFalse(app.hasAnyOpenFragment())
        assertEquals(1, fakeApp.exitCalls)
        assertEquals(0, fakeApp.lastExitCode)
        app.dispose()

        // 2. Opting out via exitWhenNoFragmentsOpen = false closes mainWindow on root finish without exiting
        GenericUI.resetForTests()
        GenericUI.initialize(factory)
        registerStandardTestFragments()
        val optOutMain = MainHomeFragment()
        val optOutApp = FragmentApplication(initialFragment = optOutMain, title = "OptOutExitTest").apply {
            exitWhenNoFragmentsOpen = false
        }
        optOutApp.run(factory)
        val optOutFakeApp = factory.createdApps.last()
        assertTrue(optOutMain.finishFragment())
        assertNull(optOutApp.mainWindow.fragmentContainer?.currentFragment)
        assertFalse(optOutApp.mainWindow.isWindowShown)
        assertEquals(0, optOutFakeApp.exitCalls)
        optOutApp.dispose()

        // 3. Non-fragment Container-driven Application does NOT auto-exit when checkExitIfNoFragmentsOpen runs
        GenericUI.resetForTests()
        GenericUI.initialize(factory)
        val containerApp = Application(initialContent = Container("non-fragment-root"), title = "ContainerApp")
        containerApp.run(factory)
        val containerFakeApp = factory.createdApps.last()
        assertFalse(containerApp.hasAnyOpenFragment())
        containerApp.checkExitIfNoFragmentsOpen()
        assertEquals(0, containerFakeApp.exitCalls)
        containerApp.dispose()
    }
}
