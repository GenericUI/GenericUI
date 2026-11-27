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
import com.nexustools.genericui.enums.dock.DockGroupMode
import com.nexustools.genericui.enums.dock.DockPosition
import com.nexustools.genericui.enums.dock.StudioWindowMode
import com.nexustools.genericui.enums.system.BackendTarget
import com.nexustools.genericui.enums.window.DisplayCutoutMode
import com.nexustools.genericui.enums.window.WindowState
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
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.ComputedStyle
import com.nexustools.genericui.style.css.CssStyleSheet
import com.nexustools.genericui.style.css.StyledVisualSpec
import com.nexustools.genericui.style.sass.SassCompiler
import com.nexustools.genericui.window.TitleBar
import com.nexustools.genericui.window.WindowConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CommonComponentsTest {

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
        private var hovered: Boolean = false
        private var focused: Boolean = false
        private var activePressed: Boolean = false
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
        override fun setHoveredState(hovered: Boolean) { this.hovered = hovered }
        override fun isHoveredState(): Boolean = hovered
        override fun setFocusedState(focused: Boolean) { this.focused = focused }
        override fun isFocusedState(): Boolean = focused
        override fun setActivePressedState(active: Boolean) { this.activePressed = active }
        override fun isActivePressedState(): Boolean = activePressed
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

    private open class FakeContainerNativeImpl(prefix: String) : StubNativeImpl(prefix), ContainerNativeImpl<Any> {
        override fun addChildNative(childImpl: NativeImpl<*>, index: Int) {}
        override fun removeChildNative(childImpl: NativeImpl<*>) {}
        override fun clearChildrenNative() {}
    }

    private class FakeStyledContainerNativeImpl(prefix: String) : FakeContainerNativeImpl(prefix), StyledContainerNativeImpl<Any> {
        private var visuals: StyledVisualSpec = StyledVisualSpec()
        override fun setStyledVisuals(visuals: StyledVisualSpec) { this.visuals = visuals }
        override fun getStyledVisuals(): StyledVisualSpec = visuals
    }

    private class FakeStyledComponentNativeImpl(prefix: String) : StubNativeImpl(prefix), com.nexustools.genericui.nativeimpl.surface.StyledComponentNativeImpl<Any> {
        private var visuals: StyledVisualSpec = StyledVisualSpec()
        private var hover: StyledVisualSpec? = null
        private var focus: StyledVisualSpec? = null
        private var active: StyledVisualSpec? = null
        private var disabled: StyledVisualSpec? = null
        override fun setStyledVisuals(visuals: StyledVisualSpec) { this.visuals = visuals }
        override fun getStyledVisuals(): StyledVisualSpec = visuals
        override fun setHoverVisuals(visuals: StyledVisualSpec?) { this.hover = visuals }
        override fun getHoverVisuals(): StyledVisualSpec? = hover
        override fun setFocusVisuals(visuals: StyledVisualSpec?) { this.focus = visuals }
        override fun getFocusVisuals(): StyledVisualSpec? = focus
        override fun setActiveVisuals(visuals: StyledVisualSpec?) { this.active = visuals }
        override fun getActiveVisuals(): StyledVisualSpec? = active
        override fun setDisabledVisuals(visuals: StyledVisualSpec?) { this.disabled = visuals }
        override fun getDisabledVisuals(): StyledVisualSpec? = disabled
    }

    private class FakeScrollViewNativeImpl : FakeContainerNativeImpl("scrollview"), ScrollViewNativeImpl<Any> {
        override fun scrollTo(x: Int, y: Int) {}
        override fun scrollToBottom() {}
    }

    private class FakeDividerNativeImpl : StubNativeImpl("divider"), DividerNativeImpl<Any>

    private class FakeTextNativeImpl : StubNativeImpl("text"), TextNativeImpl<Any> {
        private var text: String = ""
        private var maxLines: Int? = null
        override fun setText(text: String) { this.text = text }
        override fun getText(): String = text
        override fun setMaxLines(maxLines: Int?) { this.maxLines = maxLines }
        override fun getMaxLines(): Int? = maxLines
    }

    private class FakeButtonNativeImpl(prefix: String) : StubNativeImpl(prefix), ButtonNativeImpl<Any> {
        private var text: String = ""
        private var icon: String? = null
        override fun setText(text: String) { this.text = text }
        override fun getText(): String = text
        override fun setIcon(icon: String?) { this.icon = icon }
        override fun getIcon(): String? = icon
    }

    private class FakeContextMenuNativeImpl : FakeContainerNativeImpl("context-menu"), ContextMenuNativeImpl<Any> {
        override fun showAt(anchor: NativeImpl<*>?, x: Int, y: Int) {}
        override fun hide() {}
    }

    private class FakeMenuNativeImpl : FakeContainerNativeImpl("menu"), MenuNativeImpl<Any> {
        private var label: String = ""
        private var icon: String? = null
        override fun setLabel(label: String) { this.label = label }
        override fun getLabel(): String = label
        override fun setIcon(icon: String?) { this.icon = icon }
        override fun getIcon(): String? = icon
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

    private class FakeWindowNativeImpl : FakeContainerNativeImpl("window"), WindowNativeImpl<Any> {
        override val supportsCustomTitleBar: Boolean = true
        override val supportsEmbeddedMenuBarInTitleBar: Boolean = true
        override val supportsMultiWindow: Boolean = true
        private var title: String = "GenericUI"
        private var windowSize: Size = Size(1280, 800)
        private var windowState: WindowState = WindowState.NORMAL
        private var fullscreen: Boolean = false
        override fun setTitle(title: String) { this.title = title }
        override fun getTitle(): String = title
        override fun setWindowSize(size: Size) { windowSize = size }
        override fun getWindowSize(): Size = windowSize
        override fun setWindowState(state: WindowState) { windowState = state }
        override fun getWindowState(): WindowState = windowState
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

    private class FakeDockWorkspaceNativeImpl : FakeContainerNativeImpl("dock-workspace"), DockWorkspaceNativeImpl<Any> {
        override fun setCentralDocumentNative(centralImpl: NativeImpl<*>?) {}
        override fun attachDockAreaNative(position: DockPosition, areaImpl: DockAreaNativeImpl<*>) {}
    }

    private class FakeDockAreaNativeImpl : FakeContainerNativeImpl("dock-area"), DockAreaNativeImpl<Any> {
        private var collapsed: Boolean = false
        override fun setPosition(position: DockPosition) {}
        override fun setCollapsed(collapsed: Boolean) { this.collapsed = collapsed }
        override fun isCollapsed(): Boolean = collapsed
    }

    private class FakeDockGroupNativeImpl : FakeContainerNativeImpl("dock-group"), DockGroupNativeImpl<Any> {
        private var mode: DockGroupMode = DockGroupMode.TABBED
        private var activeIndex: Int = 0
        override fun setGroupMode(mode: DockGroupMode) { this.mode = mode }
        override fun getGroupMode(): DockGroupMode = mode
        override fun setActivePanelIndex(index: Int) { this.activeIndex = index }
        override fun getActivePanelIndex(): Int = activeIndex
    }

    private class FakeTabNativeImpl : FakeContainerNativeImpl("tab"), TabNativeImpl<Any> {
        private var title: String = ""
        override fun setTabIdNative(id: String) {}
        override fun setTitleNative(title: String) { this.title = title }
        override fun setIconNative(icon: String?) {}
        override fun setTooltipNative(tooltip: String?) {}
        override fun setClosableNative(closable: Boolean) {}
        override fun setDirtyNative(dirty: Boolean) {}
        override fun setPinnedNative(pinned: Boolean) {}
        override fun setEditableNative(editable: Boolean) {}
        override fun setBadgeNative(badgeText: String?, variantCss: String?) {}
        override fun setSelectedNative(selected: Boolean) {}
        override fun setCustomHeaderComponentNative(customImpl: NativeImpl<*>?) {}
        override fun setOnCloseClicked(listener: (() -> Unit)?) {}
        override fun setOnTitleEdited(listener: ((String) -> Unit)?) {}
    }

    private class FakeTabContainerNativeImpl : FakeContainerNativeImpl("tab-container"), TabContainerNativeImpl<Any> {
        private var selectedTabIndex: Int = 0
        override fun addTabNative(title: String, contentImpl: NativeImpl<*>, index: Int) {}
        override fun removeTabNative(index: Int) {}
        override fun setTabTitleNative(index: Int, title: String) {}
        override fun setSelectedTabIndex(index: Int, fadeTransition: Boolean) { selectedTabIndex = index }
        override fun getSelectedTabIndex(): Int = selectedTabIndex
        override fun setOnTabSelected(listener: ((Int) -> Unit)?) {}
    }

    private class FakeDockPanelNativeImpl : FakeContainerNativeImpl("dock-panel"), DockPanelNativeImpl<Any> {
        private var title: String = ""
        private var collapsed: Boolean = false
        private var floating: Boolean = false
        override fun setTitle(title: String) { this.title = title }
        override fun getTitle(): String = title
        override fun setIcon(icon: String?) {}
        override fun setCollapsed(collapsed: Boolean) { this.collapsed = collapsed }
        override fun isCollapsed(): Boolean = collapsed
        override fun setFloating(floating: Boolean) { this.floating = floating }
        override fun isFloating(): Boolean = floating
        override fun setHeaderActions(actions: List<com.nexustools.genericui.component.dock.DockHeaderAction>) {}
    }

    private class FakeCommonComponentsFactory : NativeFactory {
        private fun unsupported(): Nothing = throw UnsupportedOperationException("Unused in CommonComponentsTest")
        override val backendTarget: BackendTarget = BackendTarget.Swing
        override fun createApplication(): ApplicationNativeImpl<*> = unsupported()
        override fun createWindow(): WindowNativeImpl<*> = FakeWindowNativeImpl()
        override fun createMenuBar(): MenuBarNativeImpl<*> = unsupported()
        override fun createMenu(): MenuNativeImpl<*> = FakeMenuNativeImpl()
        override fun createMenuItem(checkable: Boolean, radio: Boolean): MenuItemNativeImpl<*> = FakeMenuItemNativeImpl()
        override fun createContextMenu(): ContextMenuNativeImpl<*> = FakeContextMenuNativeImpl()
        override fun createDockWorkspace(): DockWorkspaceNativeImpl<*> = FakeDockWorkspaceNativeImpl()
        override fun createDockArea(): DockAreaNativeImpl<*> = FakeDockAreaNativeImpl()
        override fun createDockGroup(): DockGroupNativeImpl<*> = FakeDockGroupNativeImpl()
        override fun createDockPanel(): DockPanelNativeImpl<*> = FakeDockPanelNativeImpl()
        override fun createDockWindow(): DockWindowNativeImpl<*> = unsupported()
        override fun createToolPalette(): ToolPaletteNativeImpl<*> = unsupported()
        override fun createContainer(classPrefix: String): ContainerNativeImpl<*> = FakeContainerNativeImpl(classPrefix)
        override fun createStyledContainer(classPrefix: String, tagName: String): StyledContainerNativeImpl<*> = FakeStyledContainerNativeImpl(classPrefix)
        override fun createStyledComponent(
            classPrefix: String,
            tagName: String
        ): com.nexustools.genericui.nativeimpl.surface.StyledComponentNativeImpl<*> = FakeStyledComponentNativeImpl(classPrefix)
        override fun createBookContainer(): BookContainerNativeImpl<*> = unsupported()
        override fun createScrollView(): ScrollViewNativeImpl<*> = FakeScrollViewNativeImpl()
        override fun createDivider(): DividerNativeImpl<*> = FakeDividerNativeImpl()
        override fun createText(tagName: String, classPrefix: String): TextNativeImpl<*> = FakeTextNativeImpl()
        override fun createMarkdownView(): MarkdownViewNativeImpl<*> = unsupported()
        override fun createImage(): ImageNativeImpl<*> = unsupported()
        override fun createTable(): TableNativeImpl<*> = unsupported()
        override fun createButton(classPrefix: String): ButtonNativeImpl<*> = FakeButtonNativeImpl(classPrefix)
        override fun createStyledButton(classPrefix: String): StyledButtonNativeImpl<*> = unsupported()
        override fun createDropdown(): DropdownNativeImpl<*> = unsupported()
        override fun createTextInput(multiline: Boolean): TextInputNativeImpl<*> = unsupported()
        override fun createStyledTextInput(multiline: Boolean, classPrefix: String): StyledTextInputNativeImpl<*> = unsupported()
        override fun createSelect(): SelectNativeImpl<*> = unsupported()
        override fun createCheckbox(switchStyle: Boolean, radioStyle: Boolean): CheckboxNativeImpl<*> = unsupported()
        override fun createRangeSlider(): RangeSliderNativeImpl<*> = unsupported()
        override fun createTab(closable: Boolean): TabNativeImpl<*> = FakeTabNativeImpl()
        override fun createTabContainer(): TabContainerNativeImpl<*> = FakeTabContainerNativeImpl()
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
        val prevFactory = GenericUI.activeFactory
        try {
            GenericUI.initialize(FakeCommonComponentsFactory())
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
        } finally {
            GenericUI.resetForTests()
            GenericUI.activeFactory = prevFactory
        }
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
        val prevFactory = GenericUI.activeFactory
        val factory = FakeCommonComponentsFactory()
        var app: FragmentApplication? = null
        try {
            GenericUI.initialize(factory)
            val workspaceFragment = object : DockWorkspaceFragment(StudioWindowMode.SINGLE_WINDOW_MODE) {}
            val createdApp = FragmentApplication(workspaceFragment, title = "Studio App")
            app = createdApp
            val mainWindow = createdApp.mainWindow
            mainWindow.show(factory)

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
        } finally {
            app?.dispose()
            GenericUI.resetForTests()
            GenericUI.activeFactory = prevFactory
        }
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

        val styledContainer = com.nexustools.genericui.component.layout.styled.StyledContainer().apply {
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

        val styledBtn = com.nexustools.genericui.component.action.styled.StyledButton(
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
