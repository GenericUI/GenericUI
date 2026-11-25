package com.nexustools.genericui.component.system

import com.nexustools.genericui.component.shell.ContextMenu
import com.nexustools.genericui.component.shell.Menu
import com.nexustools.genericui.component.shell.MenuItem
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
import com.nexustools.genericui.event.KeyEvent
import com.nexustools.genericui.event.KeyShortcut
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
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SystemTrayStandaloneMenuTest {

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
        var visibleState: Boolean = true
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
        override fun setVisible(visible: Boolean) { this.visibleState = visible }
        override fun isVisible(): Boolean = visibleState
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
        val addedChildren = mutableListOf<NativeImpl<*>>()
        override fun addChildNative(childImpl: NativeImpl<*>, index: Int) { addedChildren.add(childImpl) }
        override fun removeChildNative(childImpl: NativeImpl<*>) { addedChildren.remove(childImpl) }
        override fun clearChildrenNative() { addedChildren.clear() }
    }

    private class FakeContextMenuNativeImpl : StubNativeImpl("context-menu"), ContextMenuNativeImpl<Any> {
        val children = mutableListOf<NativeImpl<*>>()
        override fun addChildNative(childImpl: NativeImpl<*>, index: Int) { children.add(childImpl) }
        override fun removeChildNative(childImpl: NativeImpl<*>) { children.remove(childImpl) }
        override fun clearChildrenNative() { children.clear() }
        override fun showAt(anchor: NativeImpl<*>?, x: Int, y: Int) {}
        override fun hide() {}
    }

    private class FakeMenuNativeImpl : StubNativeImpl("menu"), MenuNativeImpl<Any> {
        private var label: String = ""
        private var icon: String? = null
        val children = mutableListOf<NativeImpl<*>>()
        override fun setLabel(label: String) { this.label = label }
        override fun getLabel(): String = label
        override fun setIcon(icon: String?) { this.icon = icon }
        override fun getIcon(): String? = icon
        override fun addChildNative(childImpl: NativeImpl<*>, index: Int) { children.add(childImpl) }
        override fun removeChildNative(childImpl: NativeImpl<*>) { children.remove(childImpl) }
        override fun clearChildrenNative() { children.clear() }
    }

    private class FakeMenuItemNativeImpl : StubNativeImpl("menuitem"), MenuItemNativeImpl<Any> {
        private var label: String = ""
        private var icon: String? = null
        private var shortcut: KeyShortcut? = null
        private var checked: Boolean = false
        override fun setLabel(label: String) { this.label = label }
        override fun getLabel(): String = label
        override fun setIcon(icon: String?) { this.icon = icon }
        override fun getIcon(): String? = icon
        override fun setShortcut(shortcut: KeyShortcut?) { this.shortcut = shortcut }
        override fun getShortcut(): KeyShortcut? = shortcut
        override fun setChecked(checked: Boolean) { this.checked = checked }
        override fun isChecked(): Boolean = checked
    }

    private class FakeSystemTrayNativeImpl : SystemTrayNativeImpl {
        override val isSupported: Boolean = true
        override var isDisposed: Boolean = false
        var lastIcon: String? = null
        var lastMacosIcon: String? = null
        var lastTooltip: String? = null
        var lastBadge: String? = null
        var boundContextMenu: ContextMenuNativeImpl<*>? = null
        override fun dispose() { isDisposed = true }
        override fun setIcon(iconPathOrUrl: String?) { lastIcon = iconPathOrUrl }
        override fun setMacosIcon(iconPathOrUrl: String?) { lastMacosIcon = iconPathOrUrl }
        override fun setTooltip(tooltip: String?) { lastTooltip = tooltip }
        override fun setBadgeText(badge: String?) { lastBadge = badge }
        override fun setContextMenu(menuImpl: ContextMenuNativeImpl<*>?) { boundContextMenu = menuImpl }
        override fun setOnTrayIconClicked(listener: (() -> Unit)?) {}
    }

    private class FakeTrayFactory : NativeFactory {
        val anchorContainers = mutableListOf<FakeContainerNativeImpl>()
        val trayImpls = mutableListOf<FakeSystemTrayNativeImpl>()
        private fun unsupported(): Nothing = throw UnsupportedOperationException("Unused in test")
        override val backendTarget: BackendTarget = BackendTarget.Swing
        override fun createApplication(): ApplicationNativeImpl<*> = unsupported()
        override fun createWindow(): WindowNativeImpl<*> = unsupported()
        override fun createMenuBar(): MenuBarNativeImpl<*> = unsupported()
        override fun createMenu(): MenuNativeImpl<*> = FakeMenuNativeImpl()
        override fun createMenuItem(checkable: Boolean, radio: Boolean): MenuItemNativeImpl<*> = FakeMenuItemNativeImpl()
        override fun createContextMenu(): ContextMenuNativeImpl<*> = FakeContextMenuNativeImpl()
        override fun createDockWorkspace(): DockWorkspaceNativeImpl<*> = unsupported()
        override fun createDockArea(): DockAreaNativeImpl<*> = unsupported()
        override fun createDockGroup(): DockGroupNativeImpl<*> = unsupported()
        override fun createDockPanel(): DockPanelNativeImpl<*> = unsupported()
        override fun createDockWindow(): DockWindowNativeImpl<*> = unsupported()
        override fun createToolPalette(): ToolPaletteNativeImpl<*> = unsupported()
        override fun createContainer(classPrefix: String): ContainerNativeImpl<*> =
            FakeContainerNativeImpl(classPrefix).also { anchorContainers.add(it) }
        override fun createStyledContainer(classPrefix: String, tagName: String): StyledContainerNativeImpl<*> = unsupported()
        override fun createStyledComponent(
            classPrefix: String,
            tagName: String
        ): com.nexustools.genericui.nativeimpl.surface.StyledComponentNativeImpl<*> = unsupported()
        override fun createBookContainer(): BookContainerNativeImpl<*> = unsupported()
        override fun createScrollView(): ScrollViewNativeImpl<*> = unsupported()
        override fun createDivider(): DividerNativeImpl<*> = FakeDividerNativeImpl()
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
        override fun createSystemTray(): SystemTrayNativeImpl =
            FakeSystemTrayNativeImpl().also { trayImpls.add(it) }
        override fun createNotificationManager(): NotificationManagerNativeImpl = unsupported()
        override fun createNetworkMonitor(): NetworkMonitorNativeImpl = unsupported()
        override fun supportsTcp(): Boolean = false
        override fun supportsUdp(): Boolean = false

        private class FakeDividerNativeImpl : StubNativeImpl("divider"), DividerNativeImpl<Any>
    }

    private var savedFactory: NativeFactory? = null

    @BeforeTest
    fun setup() {
        savedFactory = GenericUI.activeFactory
    }

    @AfterTest
    fun teardown() {
        GenericUI.activeFactory = savedFactory
    }

    @Test
    fun `assigning standalone ContextMenu to SystemTrayIcon realizes ContextMenu and child items via hidden anchor`() {
        val factory = FakeTrayFactory()
        GenericUI.activeFactory = factory

        val tray = SystemTrayIcon(iconPathOrUrl = "tray.png", tooltip = "App Tray")
        val menu = ContextMenu()
        val openItem = menu.addItem("Open Player")
        menu.addSeparator()
        val subMenu = Menu("Quality")
        val hdItem = subMenu.addItem("1080p")
        menu.addSubmenu(subMenu)

        tray.contextMenu = menu

        assertNotNull(menu.nativeImpl)
        assertNotNull(openItem.nativeImpl)
        assertNotNull(subMenu.nativeImpl)
        assertNotNull(hdItem.nativeImpl)

        val trayImpl = factory.trayImpls.single()
        assertSame(menu.nativeImpl, trayImpl.boundContextMenu)
        val anchorImpl = factory.anchorContainers.single()
        assertFalse(anchorImpl.visibleState, "Hidden tray anchor container must initialize invisibly.")
        assertTrue(anchorImpl.addedChildren.isEmpty(), "ContextMenu must not be mounted as a layout child of Container.")

        tray.dispose()
    }

    @Test
    fun `standalone ContextMenu configured before GenericUI activeFactory realizes cleanly on show`() {
        GenericUI.activeFactory = null

        val tray = SystemTrayIcon(iconPathOrUrl = "tray.svg", tooltip = "Deferred Tray", macosIconPathOrUrl = "mac.svg")
        val menu = ContextMenu()
        val quitItem = menu.addItem("Quit")
        tray.contextMenu = menu
        tray.badgeText = "3"

        val factory = FakeTrayFactory()
        GenericUI.activeFactory = factory
        tray.show()

        val trayImpl = factory.trayImpls.single()
        assertEquals("tray.svg", trayImpl.lastIcon)
        assertEquals("mac.svg", trayImpl.lastMacosIcon)
        assertEquals("Deferred Tray", trayImpl.lastTooltip)
        assertEquals("3", trayImpl.lastBadge)
        assertNotNull(menu.nativeImpl)
        assertNotNull(quitItem.nativeImpl)
        assertSame(menu.nativeImpl, trayImpl.boundContextMenu)

        tray.dispose()
    }
}
