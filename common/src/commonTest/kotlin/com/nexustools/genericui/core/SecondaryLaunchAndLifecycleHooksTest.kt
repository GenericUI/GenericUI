package com.nexustools.genericui.core

import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.fragment.FragmentBundle
import com.nexustools.genericui.component.fragment.FragmentRegistry
import com.nexustools.genericui.component.shell.Application
import com.nexustools.genericui.component.shell.FragmentApplication
import com.nexustools.genericui.component.shell.IApplication
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
import com.nexustools.genericui.style.css.ComputedStyle
import com.nexustools.genericui.window.TitleBar
import com.nexustools.genericui.window.WindowConfiguration
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SecondaryLaunchAndLifecycleHooksTest {

    private class PrimaryVideoFragment : Fragment()
    private class SecondaryQueueFragment : Fragment()

    private class TrackingCustomApp : FragmentApplication(initialFragment = PrimaryVideoFragment()) {
        var appliedBundles = mutableListOf<FragmentBundle?>()
        var startedCount: Int = 0

        override fun onLaunchArgumentsApplied(args: FragmentBundle?) {
            appliedBundles.add(args)
        }

        override fun onApplicationStarted() {
            startedCount++
            super.onApplicationStarted()
        }
    }

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
        override fun addClass(className: String) { this.className = className }
        override fun removeClass(className: String) {}
        override fun hasClass(className: String): Boolean = this.className == className
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

    private class FakeWindowNativeImpl : StubNativeImpl("window"), WindowNativeImpl<Any> {
        override val supportsCustomTitleBar: Boolean = true
        override val supportsEmbeddedMenuBarInTitleBar: Boolean = true
        override val supportsMultiWindow: Boolean = true
        var showWindowCalls: Int = 0
        var closeWindowCalls: Int = 0
        private var title: String = ""
        private var size: Size = Size(1280, 800)
        private var state: WindowState = WindowState.NORMAL
        private var fullscreen: Boolean = false
        private var undecorated: Boolean = false

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
        override fun isUndecorated(): Boolean = undecorated
        override fun setUndecorated(undecorated: Boolean) { this.undecorated = undecorated }
        override fun showWindow() { showWindowCalls++ }
        override fun closeWindow() { closeWindowCalls++ }
        override fun setOnWindowResize(listener: ((WindowConfiguration) -> Unit)?) {}
        override fun setOnWindowStateChanged(listener: ((WindowState) -> Unit)?) {}
        override fun setOnWindowFocusChanged(listener: ((Boolean) -> Unit)?) {}
        override fun setOnWindowCloseRequested(listener: (() -> Boolean)?) {}
    }

    private class FakeApplicationNativeImpl : ApplicationNativeImpl<Any> {
        override val nativeApplication: Any = "fake-app"
        override var isDisposed: Boolean = false
        private var mode: ThemeMode = ThemeMode.AUTO
        var runEventLoopCalls: Int = 0
        var exitCalls: Int = 0

        override fun dispose() { isDisposed = true }
        override fun setThemeMode(mode: ThemeMode) { this.mode = mode }
        override fun getThemeMode(): ThemeMode = mode
        override fun runEventLoop(onStarted: () -> Unit) {
            runEventLoopCalls++
            onStarted()
        }
        override fun exitApplication(exitCode: Int) { exitCalls++ }
    }

    private class FakeNetworkMonitorNativeImpl : NetworkMonitorNativeImpl {
        override var isDisposed: Boolean = false
        override fun dispose() { isDisposed = true }
        override fun isOnline(): Boolean = true
        override fun setOnConnectivityChanged(listener: ((Boolean) -> Unit)?) {}
    }

    private class FakeDesktopFactory : NativeFactory {
        val windows = mutableListOf<FakeWindowNativeImpl>()
        val apps = mutableListOf<FakeApplicationNativeImpl>()
        private fun unsupported(): Nothing = throw UnsupportedOperationException("Unused in test")
        override val backendTarget: BackendTarget = BackendTarget.Swing
        override fun createApplication(): ApplicationNativeImpl<*> = FakeApplicationNativeImpl().also { apps.add(it) }
        override fun createWindow(): WindowNativeImpl<*> = FakeWindowNativeImpl().also { windows.add(it) }
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
        override fun createNetworkMonitor(): NetworkMonitorNativeImpl = FakeNetworkMonitorNativeImpl()
        override fun supportsTcp(): Boolean = false
        override fun supportsUdp(): Boolean = false
    }

    private var savedActiveFactory: NativeFactory? = null

    @BeforeTest
    fun setup() {
        savedActiveFactory = GenericUI.activeFactory
        FragmentRegistry.clear()
        GenericUiExtensionRegistry.clear()
    }

    @AfterTest
    fun teardown() {
        FragmentRegistry.clear()
        GenericUiExtensionRegistry.clear()
        GenericUI.activeFactory = savedActiveFactory
        Application.current = null
    }

    @Test
    fun `secondary fragment launch suppresses mainWindow show dispatches eventLoopStarted and idempotent shutdown`() {
        val events = mutableListOf<String>()
        var registeredDuringFactory = false

        GenericUiExtensionRegistry.registerLifecycleHook(object : ApplicationLifecycleExtensionHook {
            override fun onAppEventLoopStarted(app: Application, factory: NativeFactory) {
                events.add("eventLoopStarted:${app.suppressInitialMainWindowShow}")
            }

            override fun onAppShutdown(app: IApplication?) {
                events.add("shutdown")
            }
        })

        val descriptor = GenericUiAppDescriptor(
            appName = "LaunchTest",
            mainFragmentClass = PrimaryVideoFragment::class,
            mainFragmentFactory = { PrimaryVideoFragment() },
            applicationFactory = { _ ->
                registeredDuringFactory =
                    FragmentRegistry.resolveFragmentClass("/queue") == SecondaryQueueFragment::class
                TrackingCustomApp()
            },
            fragmentRegistrations = listOf(
                PrimaryVideoFragment::class to { _ -> PrimaryVideoFragment() },
                SecondaryQueueFragment::class to { _ -> SecondaryQueueFragment() }
            ),
            fragmentIds = mapOf(
                PrimaryVideoFragment::class to "com.example.PrimaryVideoFragment",
                SecondaryQueueFragment::class to "com.example.SecondaryQueueFragment"
            ),
            fragmentRoutes = mapOf(
                PrimaryVideoFragment::class to "/video",
                SecondaryQueueFragment::class to "/queue"
            )
        )

        val factory = FakeDesktopFactory()
        val app = GenericUiLaunchSequence.launch(
            factory = factory,
            args = arrayOf("--route=/queue"),
            descriptor = descriptor,
            isSystemTarget = true
        ) as TrackingCustomApp

        assertTrue(registeredDuringFactory, "Metadata must be registered before applicationFactory executes.")
        assertTrue(app.suppressInitialMainWindowShow)
        assertNull(app.mainWindow.fragmentContainer?.currentFragment)
        assertEquals(1, app.appliedBundles.size)
        assertEquals("/queue", app.launchArguments?.getString(FragmentBundle.KEY_LAUNCH_ROUTE))
        assertEquals(0, factory.windows.sumOf { it.showWindowCalls })
        assertTrue(factory.windows.isEmpty(), "mainWindow must not allocate or show a native Window on secondary launch.")
        assertTrue("eventLoopStarted:true" in events)

        app.exit(0)
        app.dispose()
        app.dispose()
        assertEquals(1, events.count { it == "shutdown" }, "onAppShutdown must fire at most once across exit and dispose.")
    }

    @Test
    fun `secondary target with launch-mode SAME_WINDOW keeps mainWindow visible and mounts target fragment`() {
        val descriptor = GenericUiAppDescriptor(
            appName = "SameWindowTest",
            mainFragmentClass = PrimaryVideoFragment::class,
            mainFragmentFactory = { PrimaryVideoFragment() },
            fragmentRegistrations = listOf(
                PrimaryVideoFragment::class to { _ -> PrimaryVideoFragment() },
                SecondaryQueueFragment::class to { _ -> SecondaryQueueFragment() }
            ),
            fragmentIds = mapOf(
                PrimaryVideoFragment::class to "com.example.PrimaryVideoFragment",
                SecondaryQueueFragment::class to "com.example.SecondaryQueueFragment"
            ),
            fragmentRoutes = mapOf(
                SecondaryQueueFragment::class to "/queue"
            )
        )

        val factory = FakeDesktopFactory()
        val app = GenericUiLaunchSequence.launch(
            factory = factory,
            args = arrayOf("--fragment=SecondaryQueueFragment", "--launch-mode=SAME_WINDOW"),
            descriptor = descriptor,
            isSystemTarget = true
        ) as Application

        assertFalse(app.suppressInitialMainWindowShow)
        val active = app.mainWindow.fragmentContainer?.currentFragment
        assertNotNull(active)
        assertTrue(active is SecondaryQueueFragment)
        assertEquals(1, factory.windows.single().showWindowCalls)
    }
}
