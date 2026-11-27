package com.nexustools.genericui.exception

import com.nexustools.genericui.component.dialog.Dialogs
import com.nexustools.genericui.component.dialog.DialogWindow
import com.nexustools.genericui.component.dialog.FileBrowserCallback
import com.nexustools.genericui.component.dialog.FileBrowserDialog
import com.nexustools.genericui.component.dialog.FileTypeFilter
import com.nexustools.genericui.component.dialog.SelectedFile
import com.nexustools.genericui.component.dock.PanelWindow
import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.fragment.FragmentRegistry
import com.nexustools.genericui.component.fragment.IFragment
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.overlay.Modal
import com.nexustools.genericui.component.overlay.Popover
import com.nexustools.genericui.component.shell.Application
import com.nexustools.genericui.component.shell.ContextMenu
import com.nexustools.genericui.component.shell.IApplication
import com.nexustools.genericui.component.shell.Window
import com.nexustools.genericui.component.system.SystemTrayIcon
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
import com.nexustools.genericui.enums.dialog.FileBrowserMode
import com.nexustools.genericui.enums.system.BackendTarget
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
import com.nexustools.genericui.nativeimpl.form.StyledTextInputNativeImpl
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
import com.nexustools.genericui.net.Network
import com.nexustools.genericui.style.color.RgbaColor
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

class NativeUninitializedAndCapabilityExceptionsTest {

    private class SampleFragment : Fragment()

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

    private class FakeWindowNativeImpl : StubNativeImpl("window"), WindowNativeImpl<Any> {
        override val supportsCustomTitleBar: Boolean = true
        override val supportsEmbeddedMenuBarInTitleBar: Boolean = true
        override val supportsMultiWindow: Boolean = true
        private var title: String = "GenericUI"
        private var size: Size = Size(800, 600)
        private var state: WindowState = WindowState.NORMAL
        private var fullscreen: Boolean = false
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
        override fun showWindow() {}
        override fun closeWindow() {}
        override fun setOnWindowResize(listener: ((WindowConfiguration) -> Unit)?) {}
        override fun setOnWindowFocusChanged(listener: ((Boolean) -> Unit)?) {}
        override fun setOnWindowCloseRequested(listener: (() -> Boolean)?) {}
    }

    private class UnsupportedTrayImpl : SystemTrayNativeImpl {
        override val isSupported: Boolean = false
        override var isDisposed: Boolean = false
        override fun dispose() { isDisposed = true }
        override fun setIcon(iconPathOrUrl: String?) {}
        override fun setTooltip(tooltip: String?) {}
        override fun setBadgeText(badge: String?) {}
        override fun setContextMenu(menuImpl: ContextMenuNativeImpl<*>?) {}
        override fun setOnTrayIconClicked(listener: (() -> Unit)?) {}
    }

    private class ConstrainedBackendFactory(
        override val backendTarget: BackendTarget = BackendTarget.Web,
        override val supportsMultipleWindows: Boolean = false,
        override val supportsToolModalWindows: Boolean = false,
        override val supportsSystemTray: Boolean = false,
        override val supportsDirectorySelection: Boolean = false,
        private val tcpSupported: Boolean = false,
        private val udpSupported: Boolean = false,
        private val trayImplSupported: Boolean = false
    ) : NativeFactory {
        private fun unsupported(): Nothing = throw UnsupportedOperationException("Unused in test")
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
        override fun createStyledTextInput(multiline: Boolean, classPrefix: String): StyledTextInputNativeImpl<*> = unsupported()
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
        override fun createSystemTray(): SystemTrayNativeImpl = if (trayImplSupported) {
            throw IllegalStateException("Expected trayImplSupported = false")
        } else {
            UnsupportedTrayImpl()
        }
        override fun createNotificationManager(): NotificationManagerNativeImpl = unsupported()
        override fun createNetworkMonitor(): NetworkMonitorNativeImpl = object : NetworkMonitorNativeImpl {
            override var isDisposed: Boolean = false
            override fun dispose() { isDisposed = true }
            override fun isOnline(): Boolean = true
            override fun setOnConnectivityChanged(listener: ((Boolean) -> Unit)?) {}
        }
        override fun supportsTcp(): Boolean = tcpSupported
        override fun supportsUdp(): Boolean = udpSupported
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

    @Test
    fun nativeUninitializedExceptionsAreThrownAcrossAllImperativeApis() {
        assertFalse(GenericUI.isInitialized)
        assertFailsWith<NativeUninitializedException> { GenericUI.requireFactory() }
        assertFailsWith<NativeUninitializedException> { GenericUI.supportsMultipleWindows }
        assertFailsWith<NativeUninitializedException> { GenericUI.supportsDialogWindows }
        assertFailsWith<NativeUninitializedException> { GenericUI.canOpenAdditionalWindow(SampleFragment::class) }

        val app = Application(title = "DetachedApp")
        val iApp: IApplication = app
        assertFailsWith<NativeUninitializedException> { iApp.supportsMultipleWindows }
        assertFailsWith<NativeUninitializedException> { iApp.supportsDialogWindows }
        assertFailsWith<NativeUninitializedException> { iApp.canOpenAdditionalWindow(SampleFragment::class) }

        val window = Window("DetachedWindow")
        val iWin: IWindow = window
        assertFailsWith<NativeUninitializedException> { iWin.supportsMultipleWindows }
        assertFailsWith<NativeUninitializedException> { iWin.supportsDialogWindows }
        assertFailsWith<NativeUninitializedException> { iWin.canOpenAdditionalWindow(SampleFragment::class) }
        assertFailsWith<NativeUninitializedException> { window.show() }
        assertFailsWith<NativeUninitializedException> { window.startSystemMove() }
        assertFailsWith<NativeUninitializedException> { window.bringToFront() }
        assertFailsWith<NativeUninitializedException> { window.focus() }

        val frag: IFragment = SampleFragment()
        assertFailsWith<NativeUninitializedException> { frag.supportsMultipleWindows }
        assertFailsWith<NativeUninitializedException> { frag.supportsDialogWindows }
        assertFailsWith<NativeUninitializedException> { frag.canOpenAdditionalWindow(SampleFragment::class) }
        FragmentRegistry.registerNoArg(SampleFragment::class) { SampleFragment() }
        assertFailsWith<NativeUninitializedException> { frag.startFragment(SampleFragment::class) }

        val dialog = DialogWindow(title = "UninitDialog")
        assertFailsWith<NativeUninitializedException> { dialog.open() }
        assertFailsWith<NativeUninitializedException> { dialog.focus() }
        assertFailsWith<NativeUninitializedException> { dialog.bringToFront() }

        val modal = Modal(title = "UninitModal")
        assertFailsWith<NativeUninitializedException> { modal.open() }

        assertFailsWith<NativeUninitializedException> { ContextMenu().showAt(null, 10, 20) }
        assertFailsWith<NativeUninitializedException> { Popover(title = "Info").showAnchored(null, 0, 0) }
        assertFailsWith<NativeUninitializedException> { SystemTrayIcon("tray.png", "App").show() }

        assertFailsWith<NativeUninitializedException> { Dialogs.alert("Title", "Message") }
        assertFailsWith<NativeUninitializedException> { Dialogs.confirm("Title", "Confirm?") {} }
        assertFailsWith<NativeUninitializedException> { Dialogs.prompt("Title", "Name?") {} }
        assertFailsWith<NativeUninitializedException> { Dialogs.choice("Title", "Pick", listOf("A", "B")) {} }
        assertFailsWith<NativeUninitializedException> { Dialogs.chooseColor("Color") {} }

        assertFailsWith<NativeUninitializedException> {
            FileBrowserDialog(mode = FileBrowserMode.OPEN_SINGLE_FILE).show(
                callback = object : FileBrowserCallback {
                    override fun onFilesSelected(files: List<SelectedFile>, selectedFilter: FileTypeFilter?) {}
                    override fun onCancelled() {}
                }
            )
        }
        assertFailsWith<NativeUninitializedException> { Network.openTcpSocket() }
        assertFailsWith<NativeUninitializedException> { Network.openUdpSocket() }

        app.dispose()
    }

    @Test
    fun unsupportedBackendCapabilityExceptionsThrowTypedSubclasses() {
        val constrainedFactory = ConstrainedBackendFactory(
            backendTarget = BackendTarget.Web,
            supportsMultipleWindows = false,
            supportsToolModalWindows = false,
            supportsSystemTray = false,
            supportsDirectorySelection = false,
            tcpSupported = false,
            udpSupported = false
        )
        GenericUI.initialize(constrainedFactory)

        // MultipleWindowsUnsupportedException on 2nd shown top-level Window
        val firstWindow = Window("First").apply { setContent(Container()) }
        val secondWindow = Window("Second").apply { setContent(Container()) }
        try {
            firstWindow.show(constrainedFactory)
            val mwEx = assertFailsWith<MultipleWindowsUnsupportedException> {
                secondWindow.show(constrainedFactory)
            }
            assertEquals("multipleWindows", mwEx.capability)
            assertEquals(BackendTarget.Web.displayName, mwEx.backendName)
        } finally {
            firstWindow.close()
        }

        // ToolModalWindowsUnsupportedException
        val panelWindow = PanelWindow("Inspector")
        val toolEx = assertFailsWith<ToolModalWindowsUnsupportedException> {
            panelWindow.show(constrainedFactory)
        }
        assertEquals("toolModalWindows", toolEx.capability)

        // SystemTrayUnsupportedException (factory capability disabled)
        val trayEx = assertFailsWith<SystemTrayUnsupportedException> {
            SystemTrayIcon("icon.png", "Tray").show()
        }
        assertEquals("systemTray", trayEx.capability)

        // SystemTrayUnsupportedException (trayImpl.isSupported == false even when factory reports true)
        val implDisabledFactory = ConstrainedBackendFactory(
            backendTarget = BackendTarget.Gtk,
            supportsSystemTray = true,
            trayImplSupported = false
        )
        GenericUI.initialize(implDisabledFactory)
        assertFailsWith<SystemTrayUnsupportedException> {
            SystemTrayIcon("icon.png", "Tray").show()
        }
        GenericUI.initialize(constrainedFactory)

        // DirectorySelectionUnsupportedException
        val dirEx = assertFailsWith<DirectorySelectionUnsupportedException> {
            FileBrowserDialog(mode = FileBrowserMode.SELECT_FOLDER).show(onSelected = {})
        }
        assertEquals("directorySelection", dirEx.capability)
        assertFailsWith<DirectorySelectionUnsupportedException> {
            Dialogs.selectFolder(onSelected = {})
        }

        // RawSocketUnsupportedException
        val tcpEx = assertFailsWith<RawSocketUnsupportedException> {
            constrainedFactory.createTcpSocket()
        }
        assertEquals("tcp", tcpEx.protocol)
        val udpEx = assertFailsWith<RawSocketUnsupportedException> {
            constrainedFactory.createUdpSocket()
        }
        assertEquals("udp", udpEx.protocol)
        assertFailsWith<RawSocketUnsupportedException> { Network.openTcpSocket() }
        assertFailsWith<RawSocketUnsupportedException> { Network.openUdpSocket() }
    }
}
