package com.nexustools.genericui.component.fragment

import com.nexustools.genericui.component.action.Button
import com.nexustools.genericui.component.fragment.helper.TabItemSpec
import com.nexustools.genericui.component.fragment.helper.TabbedChromeMode
import com.nexustools.genericui.component.fragment.helper.TabbedChromeStyle
import com.nexustools.genericui.component.fragment.helper.TabbedFragment
import com.nexustools.genericui.component.fragment.helper.TabbedFragmentConfig
import com.nexustools.genericui.component.layout.FlexBox
import com.nexustools.genericui.component.layout.styled.StyledContainer
import com.nexustools.genericui.component.media.Image
import com.nexustools.genericui.component.shell.Application
import com.nexustools.genericui.component.shell.Window
import com.nexustools.genericui.component.surface.styled.StyledComponent
import com.nexustools.genericui.component.typography.Label
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
import com.nexustools.genericui.enums.ui.ObjectFit
import com.nexustools.genericui.enums.ui.ThemeMode
import com.nexustools.genericui.enums.window.DisplayCutoutMode
import com.nexustools.genericui.enums.window.WindowState
import com.nexustools.genericui.resources.ResourceID
import com.nexustools.genericui.resources.ResourceType
import com.nexustools.genericui.style.color.ImageTintMode
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
import com.nexustools.genericui.nativeimpl.surface.StyledComponentNativeImpl
import com.nexustools.genericui.nativeimpl.system.NetworkMonitorNativeImpl
import com.nexustools.genericui.nativeimpl.system.NotificationManagerNativeImpl
import com.nexustools.genericui.nativeimpl.system.PermissionManagerNativeImpl
import com.nexustools.genericui.nativeimpl.system.SystemTrayNativeImpl
import com.nexustools.genericui.nativeimpl.tabs.TabContainerNativeImpl
import com.nexustools.genericui.nativeimpl.tabs.TabNativeImpl
import com.nexustools.genericui.nativeimpl.typography.MarkdownViewNativeImpl
import com.nexustools.genericui.nativeimpl.typography.TextNativeImpl
import com.nexustools.genericui.style.css.ComputedStyle
import com.nexustools.genericui.style.css.CssCornerRadii
import com.nexustools.genericui.style.css.CssEdgeLengths
import com.nexustools.genericui.style.css.CssLength
import com.nexustools.genericui.style.css.StyledVisualSpec
import com.nexustools.genericui.style.sass.SassCompiler
import com.nexustools.genericui.window.TitleBar
import com.nexustools.genericui.window.TitleCapabilities
import com.nexustools.genericui.window.WindowConfiguration
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TabbedFragmentChromiumTest {

    private class SamplePageFragment(label: String) : Fragment() {
        init {
            pageTitle = label
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
        val attachedChildren = mutableListOf<NativeImpl<*>>()
        override fun addChildNative(childImpl: NativeImpl<*>, index: Int) {
            attachedChildren.removeAll { it === childImpl }
            if (index in 0..attachedChildren.size) {
                attachedChildren.add(index, childImpl)
            } else {
                attachedChildren.add(childImpl)
            }
        }
        override fun removeChildNative(childImpl: NativeImpl<*>) {
            attachedChildren.removeAll { it === childImpl }
        }
        override fun clearChildrenNative() {
            attachedChildren.clear()
        }
    }

    private class FakeStyledContainerNativeImpl(prefix: String) : FakeContainerNativeImpl(prefix), StyledContainerNativeImpl<Any> {
        private var visuals: StyledVisualSpec = StyledVisualSpec()
        override fun setStyledVisuals(visuals: StyledVisualSpec) { this.visuals = visuals }
        override fun getStyledVisuals(): StyledVisualSpec = visuals
    }

    private class FakeStyledComponentNativeImpl(prefix: String) : StubNativeImpl(prefix), StyledComponentNativeImpl<Any> {
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

    private open class FakeButtonNativeImpl(prefix: String) : StubNativeImpl(prefix), ButtonNativeImpl<Any> {
        private var text: String = ""
        private var icon: String? = null
        override fun setText(text: String) { this.text = text }
        override fun getText(): String = text
        override fun setIcon(icon: String?) { this.icon = icon }
        override fun getIcon(): String? = icon
    }

    private class FakeStyledButtonNativeImpl(prefix: String) : FakeButtonNativeImpl(prefix), StyledButtonNativeImpl<Any> {
        private var base: StyledVisualSpec = StyledVisualSpec()
        private var hover: StyledVisualSpec? = null
        private var focus: StyledVisualSpec? = null
        private var active: StyledVisualSpec? = null
        private var disabled: StyledVisualSpec? = null
        override fun setBaseVisuals(visuals: StyledVisualSpec) { this.base = visuals }
        override fun getBaseVisuals(): StyledVisualSpec = base
        override fun setHoverVisuals(visuals: StyledVisualSpec?) { this.hover = visuals }
        override fun getHoverVisuals(): StyledVisualSpec? = hover
        override fun setFocusVisuals(visuals: StyledVisualSpec?) { this.focus = visuals }
        override fun getFocusVisuals(): StyledVisualSpec? = focus
        override fun setActiveVisuals(visuals: StyledVisualSpec?) { this.active = visuals }
        override fun getActiveVisuals(): StyledVisualSpec? = active
        override fun setDisabledVisuals(visuals: StyledVisualSpec?) { this.disabled = visuals }
        override fun getDisabledVisuals(): StyledVisualSpec? = disabled
    }

    private class FakeTextNativeImpl : StubNativeImpl("text"), TextNativeImpl<Any> {
        private var text: String = ""
        private var maxLines: Int? = null
        override fun setText(text: String) { this.text = text }
        override fun getText(): String = text
        override fun setMaxLines(maxLines: Int?) { this.maxLines = maxLines }
        override fun getMaxLines(): Int? = maxLines
    }

    private class FakeImageNativeImpl : StubNativeImpl("img"), ImageNativeImpl<Any> {
        private var sourceUrl: String? = null
        private var placeholderUrl: String? = null
        private var objectFit: ObjectFit = ObjectFit.CONTAIN
        private var tintMode: ImageTintMode = ImageTintMode.NONE
        override fun setSourceUrl(url: String?) { this.sourceUrl = url }
        override fun getSourceUrl(): String? = sourceUrl
        override fun setPlaceholderUrl(url: String?) { this.placeholderUrl = url }
        override fun getPlaceholderUrl(): String? = placeholderUrl
        override fun setFallbackText(fallback: String?) {}
        override fun setObjectFit(fit: ObjectFit) { this.objectFit = fit }
        override fun getObjectFit(): ObjectFit = objectFit
        override fun setTintMode(mode: ImageTintMode) { this.tintMode = mode }
        override fun getTintMode(): ImageTintMode = tintMode
    }

    private class FakeContextMenuNativeImpl : FakeContainerNativeImpl("context-menu"), ContextMenuNativeImpl<Any> {
        var showAtCalls: Int = 0
        override fun showAt(anchor: NativeImpl<*>?, x: Int, y: Int) { showAtCalls++ }
        override fun hide() {}
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

    private class FakeWindowNativeImpl(
        private val capabilities: TitleCapabilities
    ) : FakeContainerNativeImpl("window"), WindowNativeImpl<Any> {
        override val supportsCustomTitleBar: Boolean = capabilities.supportsCustomTitleBar
        override val supportsEmbeddedMenuBarInTitleBar: Boolean = capabilities.supportsEmbeddedMenuBar
        override val supportsMultiWindow: Boolean = true
        override val supportsNavbarMenuBar: Boolean = capabilities.titleBarRendersAsNavbar
        override fun getTitleCapabilities(): TitleCapabilities = capabilities

        private var title: String = "GenericUI"
        private var windowSize: Size = Size(1280, 800)
        private var windowState: WindowState = WindowState.NORMAL
        private var fullscreen: Boolean = false
        val appliedTitleBars = mutableListOf<TitleBar?>()

        override fun setTitle(title: String) { this.title = title }
        override fun getTitle(): String = title
        override fun setWindowSize(size: Size) { windowSize = size }
        override fun getWindowSize(): Size = windowSize
        override fun setWindowState(state: WindowState) { windowState = state }
        override fun getWindowState(): WindowState = windowState
        override fun setFullscreen(fullscreen: Boolean, cutoutMode: DisplayCutoutMode) { this.fullscreen = fullscreen }
        override fun isFullscreen(): Boolean = fullscreen
        override fun applyTitleBarInstructions(titleBar: TitleBar?) {
            appliedTitleBars.add(titleBar)
        }
        override fun setMenuBarNative(menuBarImpl: MenuBarNativeImpl<*>?) {}
        override fun setNavBarNative(navBarImpl: NativeImpl<*>?) {}
        override fun setBottomNavigationBarNative(bottomNavImpl: NativeImpl<*>?) {}
        override fun showWindow() {}
        override fun closeWindow() {}
        override fun setOnWindowResize(listener: ((WindowConfiguration) -> Unit)?) {}
        override fun setOnWindowFocusChanged(listener: ((Boolean) -> Unit)?) {}
        override fun setOnWindowCloseRequested(listener: (() -> Boolean)?) {}
        override fun configureAsToolModalWindow(
            ownerWindow: WindowNativeImpl<*>?,
            showInTaskbar: Boolean,
            minimizable: Boolean,
            alwaysOnTopOfOwner: Boolean
        ) {}
    }

    private class FakeChromiumFactory(
        override val backendTarget: BackendTarget = BackendTarget.Windows,
        override val defaultTitleCapabilities: TitleCapabilities
    ) : NativeFactory {
        val createdWindows = mutableListOf<FakeWindowNativeImpl>()
        val createdContextMenus = mutableListOf<FakeContextMenuNativeImpl>()

        private fun unsupported(): Nothing = throw UnsupportedOperationException("Unused in TabbedFragmentChromiumTest")

        override fun createApplication(): ApplicationNativeImpl<*> = unsupported()
        override fun createWindow(): WindowNativeImpl<*> =
            FakeWindowNativeImpl(defaultTitleCapabilities).also { createdWindows.add(it) }
        override fun createMenuBar(): MenuBarNativeImpl<*> = unsupported()
        override fun createMenu(): MenuNativeImpl<*> = unsupported()
        override fun createMenuItem(checkable: Boolean, radio: Boolean): MenuItemNativeImpl<*> = FakeMenuItemNativeImpl()
        override fun createContextMenu(): ContextMenuNativeImpl<*> =
            FakeContextMenuNativeImpl().also { createdContextMenus.add(it) }
        override fun createDockWorkspace(): DockWorkspaceNativeImpl<*> = unsupported()
        override fun createDockArea(): DockAreaNativeImpl<*> = unsupported()
        override fun createDockGroup(): DockGroupNativeImpl<*> = unsupported()
        override fun createDockPanel(): DockPanelNativeImpl<*> = unsupported()
        override fun createDockWindow(): DockWindowNativeImpl<*> = unsupported()
        override fun createToolPalette(): ToolPaletteNativeImpl<*> = unsupported()
        override fun createContainer(classPrefix: String): ContainerNativeImpl<*> = FakeContainerNativeImpl(classPrefix)
        override fun createStyledContainer(classPrefix: String, tagName: String): StyledContainerNativeImpl<*> =
            FakeStyledContainerNativeImpl(classPrefix)
        override fun createStyledComponent(classPrefix: String, tagName: String): StyledComponentNativeImpl<*> =
            FakeStyledComponentNativeImpl(classPrefix)
        override fun createBookContainer(): BookContainerNativeImpl<*> = unsupported()
        override fun createScrollView(): ScrollViewNativeImpl<*> = unsupported()
        override fun createDivider(): DividerNativeImpl<*> = unsupported()
        override fun createText(tagName: String, classPrefix: String): TextNativeImpl<*> = FakeTextNativeImpl()
        override fun createMarkdownView(): MarkdownViewNativeImpl<*> = unsupported()
        override fun createImage(): ImageNativeImpl<*> = FakeImageNativeImpl()
        override fun createTable(): TableNativeImpl<*> = unsupported()
        override fun createButton(classPrefix: String): ButtonNativeImpl<*> = FakeButtonNativeImpl(classPrefix)
        override fun createStyledButton(classPrefix: String): StyledButtonNativeImpl<*> =
            FakeStyledButtonNativeImpl(classPrefix)
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
        override fun createSystemTray(): SystemTrayNativeImpl = unsupported()
        override fun createNotificationManager(): NotificationManagerNativeImpl = unsupported()
        override fun createNetworkMonitor(): NetworkMonitorNativeImpl = unsupported()
        override fun supportsTcp(): Boolean = false
        override fun supportsUdp(): Boolean = false
    }

    private fun desktopCaps(): TitleCapabilities = TitleCapabilities(
        isSupported = true,
        isTitleBarRequired = true,
        supportsCustomTitleBar = true,
        supportsCustomComponents = true
    )

    @AfterTest
    fun tearDown() {
        GenericUI.resetForTests()
    }

    @Test
    fun chromiumEmbedsStripInTitleBarPreservesUserComponentsAndCleansUpOnCompactAndDetach() {
        val factory = FakeChromiumFactory(
            backendTarget = BackendTarget.Windows,
            defaultTitleCapabilities = desktopCaps()
        )
        GenericUI.activeFactory = factory

        val window = Window(title = "BrowserWindow", initialSize = Size(1280, 800))
        val userLeadingBtn = Button("Menu")
        val userTrailingBtn = Button("Profile")
        val userTitleBar = TitleBar()
            .addLeading(userLeadingBtn)
            .addTrailing(userTrailingBtn)
        window.setTitleBar(userTitleBar)

        val tabbed = TabbedFragment(
            config = TabbedFragmentConfig(
                chromeStyle = TabbedChromeStyle.CHROMIUM,
                showTabSearchButton = true,
                connectActiveTabToNavbar = true
            )
        )
        tabbed.openTab("tab-1", "Home", selectImmediately = true) { SamplePageFragment("Home") }
        tabbed.openTab("tab-2", "Docs", selectImmediately = false) { SamplePageFragment("Docs") }
        val navbar = FlexBox().apply {
            addChild(Button("Back"))
            addChild(Button("Reload"))
        }
        tabbed.setTabNavbar(navbar)

        window.setFragment(tabbed)
        window.show(factory)

        assertTrue(tabbed.isEmbeddedInTitleBar)
        assertFalse(tabbed.headerSlotContainer.isVisible)
        assertTrue(tabbed.desktopTabStripContainer.isVisible)
        assertTrue(tabbed.desktopTabStripContainer.mountedInTitleBar)
        assertTrue(tabbed.tabNavbarSlotContainer.isVisible)
        assertEquals(1, tabbed.children.indexOf(tabbed.tabNavbarSlotContainer))
        assertEquals(CssEdgeLengths.ZERO, tabbed.tabNavbarSlotContainer.getStyle().margin)
        assertEquals(CssLength.ZERO, tabbed.tabNavbarSlotContainer.getStyle().marginTop)

        assertEquals(2, userTitleBar.leadingComponents.size)
        assertSame(userLeadingBtn, userTitleBar.leadingComponents[0])
        assertSame(tabbed.desktopTabStripContainer, userTitleBar.leadingComponents[1])
        assertEquals(listOf(userTrailingBtn), userTitleBar.trailingComponents)

        // Strip and active tab classes & surface connection
        assertTrue(tabbed.desktopTabStripContainer.hasClass("gui-tab-strip"))
        assertTrue(tabbed.desktopTabStripContainer.hasClass("gui-tab-strip--chromium"))
        assertTrue(tabbed.desktopTabStripContainer.hasClass("gui-tab-strip--titlebar"))
        val activeTab = tabbed.desktopTabStripContainer.children.filterIsInstance<StyledContainer>()
            .single { it.hasClass("gui-tab-item--active") }
        assertTrue(activeTab.hasClass("gui-tab-item"))
        assertTrue(activeTab.hasClass("gui-tab-item--chromium"))
        assertEquals("tab-1", activeTab.getAttribute("data-tab-id"))
        assertEquals("true", activeTab.getAttribute("data-gui-tab-connect"))
        assertEquals(
            CssCornerRadii.px(topLeftPx = 10f, topRightPx = 10f, bottomRightPx = 0f, bottomLeftPx = 0f),
            activeTab.getStyle().borderRadius
        )
        assertEquals(CssLength.ZERO, activeTab.getStyle().marginBottom)
        assertNotNull(activeTab.backgroundColor)
        assertEquals(tabbed.tabNavbarSlotContainer.backgroundColor, activeTab.backgroundColor)

        // Stylesheet propagation to embedded TitleBar components
        val customSheet = SassCompiler.compileToStyleSheet(".gui-tab-strip { color: #123456; }")
        window.applyStyleSheet(customSheet, 1280)
        assertSame(customSheet, tabbed.desktopTabStripContainer.lastAppliedStyleSheet)

        // Switching to compact mode removes ONLY TabbedFragment's strip without clobbering user components
        tabbed.updateViewportSize(520, 800)
        assertTrue(tabbed.isCompactMode)
        assertFalse(tabbed.isEmbeddedInTitleBar)
        assertTrue(tabbed.headerSlotContainer.isVisible)
        assertFalse(tabbed.desktopTabStripContainer.mountedInTitleBar)
        assertSame(tabbed.headerSlotContainer, tabbed.desktopTabStripContainer.parent)
        assertEquals(listOf(userLeadingBtn), userTitleBar.leadingComponents)
        assertEquals(listOf(userTrailingBtn), userTitleBar.trailingComponents)

        // Switching back to desktop restores TitleBar embedding without duplicating native widgets
        tabbed.updateViewportSize(1280, 800)
        assertTrue(tabbed.isEmbeddedInTitleBar)
        assertEquals(2, userTitleBar.leadingComponents.size)
        assertEquals(listOf(userTrailingBtn), userTitleBar.trailingComponents)
        val stripNative = tabbed.desktopTabStripContainer.nativeImpl as FakeStyledContainerNativeImpl
        assertEquals(tabbed.desktopTabStripContainer.children.size, stripNative.attachedChildren.size)

        // Replacing / detaching TabbedFragment cleanly removes owned strip and preserves unrelated components
        window.setFragment(SamplePageFragment("Replacement"))
        assertFalse(tabbed.isEmbeddedInTitleBar)
        assertFalse(tabbed.desktopTabStripContainer.mountedInTitleBar)
        assertEquals(listOf(userLeadingBtn), userTitleBar.leadingComponents)
        assertEquals(listOf(userTrailingBtn), userTitleBar.trailingComponents)

        window.close()
    }

    @Test
    fun chromiumCreatesAndCleansUpManagedTitleBarWhenNoneConfigured() {
        val factory = FakeChromiumFactory(
            backendTarget = BackendTarget.MacOS,
            defaultTitleCapabilities = desktopCaps()
        )
        GenericUI.activeFactory = factory

        val window = Window(title = "ManagedTitleBarWindow", initialSize = Size(1280, 800))
        val tabbed = TabbedFragment(
            config = TabbedFragmentConfig(chromeStyle = TabbedChromeStyle.CHROMIUM)
        )
        tabbed.openTab("t1", "Alpha", selectImmediately = true) { SamplePageFragment("Alpha") }

        window.setFragment(tabbed)
        assertTrue(tabbed.isEmbeddedInTitleBar)
        assertNotNull(window.getTitleBar())
        assertEquals(listOf(tabbed.desktopTabStripContainer), window.getTitleBar()?.leadingComponents)
        assertFalse(window.getTitleBar()!!.showWindowTitle)

        tabbed.chromeStyleOverride = TabbedChromeStyle.CLASSIC
        assertFalse(tabbed.isEmbeddedInTitleBar)
        assertNull(window.getTitleBar())
        assertFalse(tabbed.desktopTabStripContainer.mountedInTitleBar)
        assertSame(tabbed.headerSlotContainer, tabbed.desktopTabStripContainer.parent)

        tabbed.chromeStyleOverride = null
        assertTrue(tabbed.isEmbeddedInTitleBar)
        assertNotNull(window.getTitleBar())

        tabbed.dispose()
        assertFalse(tabbed.isEmbeddedInTitleBar)
        assertNull(window.getTitleBar())
        window.dispose()
    }

    @Test
    fun chromiumKeepsTabStripInsideContentHeaderWhenEmbeddingUnsupportedOrOnWeb() {
        // Case 1: Swing-like factory where canEmbedComponentsInTitleBar == false
        val swingCaps = TitleCapabilities(
            isSupported = false,
            isTitleBarRequired = true,
            supportsCustomTitleBar = false,
            supportsCustomComponents = false
        )
        assertFalse(swingCaps.canEmbedComponentsInTitleBar)
        val swingFactory = FakeChromiumFactory(
            backendTarget = BackendTarget.Swing,
            defaultTitleCapabilities = swingCaps
        )
        GenericUI.activeFactory = swingFactory

        val swingWindow = Window(title = "SwingTabs", initialSize = Size(1280, 800))
        val swingTitleBar = TitleBar()
        swingWindow.setTitleBar(swingTitleBar)
        val swingTabbed = TabbedFragment(
            config = TabbedFragmentConfig(chromeStyle = TabbedChromeStyle.CHROMIUM)
        )
        swingTabbed.openTab("s1", "First", selectImmediately = true) { SamplePageFragment("First") }
        swingTabbed.setTabNavbar(FlexBox().apply { addChild(Button("Nav")) })
        swingWindow.setFragment(swingTabbed)
        swingWindow.show(swingFactory)

        assertFalse(swingTabbed.isEmbeddedInTitleBar)
        assertTrue(swingTitleBar.leadingComponents.isEmpty())
        assertTrue(swingTabbed.headerSlotContainer.isVisible)
        assertSame(swingTabbed.headerSlotContainer, swingTabbed.desktopTabStripContainer.parent)
        assertFalse(swingTabbed.desktopTabStripContainer.mountedInTitleBar)
        assertFalse(swingTabbed.desktopTabStripContainer.hasClass("gui-tab-strip--titlebar"))
        assertTrue(swingTabbed.desktopTabStripContainer.hasClass("gui-tab-strip--chromium"))
        assertEquals(0, swingTabbed.children.indexOf(swingTabbed.headerSlotContainer))
        assertEquals(1, swingTabbed.children.indexOf(swingTabbed.tabNavbarSlotContainer))
        assertEquals(CssEdgeLengths.ZERO, swingTabbed.headerSlotContainer.getStyle().margin)
        assertEquals(CssEdgeLengths.ZERO, swingTabbed.headerSlotContainer.getStyle().padding)
        assertEquals(CssLength.ZERO, swingTabbed.headerSlotContainer.getStyle().gap)
        assertEquals(CssEdgeLengths.ZERO, swingTabbed.tabNavbarSlotContainer.getStyle().margin)
        swingWindow.close()

        // Case 2: Web-like factory where titleBarRendersAsNavbar == true
        GenericUI.resetForTests()
        val webCaps = TitleCapabilities(
            isSupported = true,
            isEmulated = true,
            supportsCustomTitleBar = true,
            supportsCustomComponents = true,
            isTitleBarRequired = false,
            titleBarRendersAsNavbar = true
        )
        val webFactory = FakeChromiumFactory(
            backendTarget = BackendTarget.Web,
            defaultTitleCapabilities = webCaps
        )
        GenericUI.activeFactory = webFactory

        val webWindow = Window(title = "WebTabs", initialSize = Size(1280, 800))
        val webTabbed = TabbedFragment(
            config = TabbedFragmentConfig(chromeStyle = TabbedChromeStyle.CHROMIUM)
        )
        webTabbed.openTab("w1", "WebTab", selectImmediately = true) { SamplePageFragment("WebTab") }
        webWindow.setFragment(webTabbed)
        webWindow.show(webFactory)

        assertFalse(webTabbed.isEmbeddedInTitleBar)
        assertNull(webWindow.getTitleBar())
        assertTrue(webTabbed.headerSlotContainer.isVisible)
        assertSame(webTabbed.headerSlotContainer, webTabbed.desktopTabStripContainer.parent)
        webWindow.close()
    }

    @Test
    fun tabSearchDividersDragSpacerAndDisconnectedActiveTabOptionWork() {
        val tabbed = TabbedFragment(
            config = TabbedFragmentConfig(
                chromeStyle = TabbedChromeStyle.CHROMIUM,
                showTabSearchButton = true,
                showNewTabButton = true,
                connectActiveTabToNavbar = true
            )
        )
        tabbed.openTab("t1", "Tab One", selectImmediately = true) { SamplePageFragment("1") }
        tabbed.openTab("t2", "Tab Two", selectImmediately = false) { SamplePageFragment("2") }
        tabbed.openTab("t3", "Tab Three", selectImmediately = false) { SamplePageFragment("3") }
        tabbed.openTab("t4", "Tab Four", selectImmediately = false) { SamplePageFragment("4") }

        val stripChildrenWithT1Active = tabbed.desktopTabStripContainer.children
        assertSame(tabbed.tabSearchButton, stripChildrenWithT1Active.first())
        assertSame(tabbed.chromiumDragSpacer, stripChildrenWithT1Active.last())
        assertFalse(tabbed.chromiumDragSpacer.pointerEventsEnabled)
        assertEquals("true", tabbed.chromiumDragSpacer.getAttribute("data-gui-drag-spacer"))

        // Dividers only appear between adjacent inactive tabs: (t2|t3) and (t3|t4) -> 2 dividers
        val dividersForT1 = stripChildrenWithT1Active.filter { it.hasClass("gui-tab-divider") }
        assertEquals(2, dividersForT1.size)

        // Selecting t2 suppresses dividers on both sides of t2, leaving only (t3|t4) -> 1 divider
        assertTrue(tabbed.selectTab("t2"))
        val dividersForT2 = tabbed.desktopTabStripContainer.children.filter { it.hasClass("gui-tab-divider") }
        assertEquals(1, dividersForT2.size)

        // Headless click on tabSearchButton cycles to t3 and populates menu items
        tabbed.tabSearchButton.onClick?.invoke()
        assertEquals("t3", tabbed.activeTabId)
        assertEquals(4, tabbed.tabSearchContextMenu.children.size)

        // With NativeFactory present, openTabSearchPicker shows the ContextMenu popup
        val factory = FakeChromiumFactory(defaultTitleCapabilities = desktopCaps())
        GenericUI.activeFactory = factory
        tabbed.openTabSearchPicker()
        assertTrue(tabbed.tabSearchContextMenu.isShowing)
        assertEquals(1, factory.createdContextMenus.single().showAtCalls)

        // Non-connected active tab option keeps uniform pill radius and data-gui-tab-connect = false
        val detachedTabs = TabbedFragment(
            config = TabbedFragmentConfig(
                chromeStyle = TabbedChromeStyle.CHROMIUM,
                connectActiveTabToNavbar = false
            )
        )
        detachedTabs.openTab("x1", "Detached", selectImmediately = true) { SamplePageFragment("Detached") }
        val detachedActive = detachedTabs.desktopTabStripContainer.children.filterIsInstance<StyledContainer>()
            .single { it.hasClass("gui-tab-item--active") }
        assertEquals("false", detachedActive.getAttribute("data-gui-tab-connect"))
        assertEquals(CssCornerRadii.px(8f), detachedActive.getStyle().borderRadius)
    }

    @Test
    fun themeChangeUpdatesChromiumStripAndNavbarColorsConsistently() {
        val factory = FakeChromiumFactory(
            backendTarget = BackendTarget.Windows,
            defaultTitleCapabilities = desktopCaps()
        )
        GenericUI.activeFactory = factory

        val app = Application(title = "ChromiumThemeApp")
        val tabbed = TabbedFragment(
            config = TabbedFragmentConfig(chromeStyle = TabbedChromeStyle.CHROMIUM)
        )
        tabbed.openTab("a", "Home", selectImmediately = true) { SamplePageFragment("Home") }
        tabbed.setTabNavbar(FlexBox().apply { addChild(Button("Nav")) })

        app.mainWindow.setFragment(tabbed)
        app.mainWindow.show(factory)

        app.setThemeMode(ThemeMode.LIGHT)
        val lightNavbarBg = assertNotNull(tabbed.tabNavbarSlotContainer.backgroundColor)
        val lightActiveBg = tabbed.desktopTabStripContainer.children.filterIsInstance<StyledContainer>()
            .single { it.hasClass("gui-tab-item--active") }
            .backgroundColor
        assertEquals(lightNavbarBg, lightActiveBg)
        assertEquals("light", tabbed.tabNavbarSlotContainer.getAttribute("data-gui-theme"))
        assertTrue(lightNavbarBg.r > 200)

        val winImpl = factory.createdWindows.single()
        val countBeforeDark = winImpl.appliedTitleBars.size

        app.setThemeMode(ThemeMode.DARK)
        val darkNavbarBg = assertNotNull(tabbed.tabNavbarSlotContainer.backgroundColor)
        val darkActiveBg = tabbed.desktopTabStripContainer.children.filterIsInstance<StyledContainer>()
            .single { it.hasClass("gui-tab-item--active") }
            .backgroundColor
        assertEquals(darkNavbarBg, darkActiveBg)
        assertNotEquals(lightNavbarBg, darkNavbarBg)
        assertEquals("dark", tabbed.tabNavbarSlotContainer.getAttribute("data-gui-theme"))
        assertTrue(darkNavbarBg.r < 120)
        assertTrue(winImpl.appliedTitleBars.size > countBeforeDark)

        app.mainWindow.close()
        app.dispose()
    }

    @Test
    fun classicChromePreservesExactLegacyContentHeaderBehavior() {
        val factory = FakeChromiumFactory(
            backendTarget = BackendTarget.Windows,
            defaultTitleCapabilities = desktopCaps()
        )
        GenericUI.activeFactory = factory

        val window = Window(title = "ClassicWindow", initialSize = Size(1280, 800))
        val classic = TabbedFragment(config = TabbedFragmentConfig())
        classic.openTab("c1", "Classic One", selectImmediately = true) { SamplePageFragment("Classic One") }
        classic.openTab("c2", "Classic Two", selectImmediately = false) { SamplePageFragment("Classic Two") }
        window.setFragment(classic)

        assertEquals(TabbedChromeStyle.CLASSIC, classic.resolvedChromeStyle)
        assertFalse(classic.isEmbeddedInTitleBar)
        assertNull(window.getTitleBar())
        assertTrue(classic.headerSlotContainer.isVisible)
        assertFalse(classic.tabNavbarSlotContainer.isVisible)
        assertFalse(classic.desktopTabStripContainer.hasClass("gui-tab-strip"))
        val legacyChips = classic.desktopTabStripContainer.children.filterIsInstance<FlexBox>()
            .filter { it.hasClass("genericui-tabbed-chip") }
        assertEquals(2, legacyChips.size)
        assertTrue(legacyChips.first().children.any { it is Button && it.hasClass("genericui-tabbed-select-btn") })
        assertSame(classic.newTabButton, classic.desktopTabStripContainer.children.last())
    }

    @Test
    fun chromiumFaviconFieldsFallbackGlobeAndInPlaceUpdateTabIconAndTitleWork() {
        val svgRes = ResourceID(0x7f010011, ResourceType.DRAWABLE, "ic_docs", "drawable/ic_docs.svg")
        val tabbed = TabbedFragment(
            config = TabbedFragmentConfig(chromeStyle = TabbedChromeStyle.CHROMIUM)
        )
        tabbed.openTab("t1", "Blank Page", selectImmediately = true) { SamplePageFragment("Blank") }
        tabbed.openTab(
            tabId = "t2",
            title = "Example",
            iconUrl = "https://example.com/favicon.png",
            selectImmediately = false
        ) { SamplePageFragment("Example") }
        tabbed.openTab(
            tabId = "t3",
            title = "Docs",
            iconRes = svgRes,
            tintIconToTheme = true,
            selectImmediately = false
        ) { SamplePageFragment("Docs") }
        tabbed.openTab(
            tabId = "t4",
            title = "Starred",
            iconName = "★",
            selectImmediately = false
        ) { SamplePageFragment("Starred") }

        fun findTabContainer(tabId: String): StyledContainer =
            tabbed.desktopTabStripContainer.children
                .filterIsInstance<StyledContainer>()
                .single { it.getAttribute("data-tab-id") == tabId }

        // t1: no icon set -> subtle 14x14 inline vector globe SVG fallback (never ASCII "[W]")
        val t1Item = findTabContainer("t1")
        val t1Slot = t1Item.children.filterIsInstance<FlexBox>().single { it.hasClass("gui-tab-icon-slot") }
        assertTrue(t1Slot.isVisible)
        assertFalse(t1Slot.pointerEventsEnabled)
        val t1Img = t1Slot.children.filterIsInstance<Image>().single()
        assertTrue(t1Img.sourceUrl?.startsWith("data:image/svg+xml") == true)
        assertEquals(14, t1Img.widthPx)
        assertEquals(14, t1Img.heightPx)
        assertEquals(ObjectFit.CONTAIN, t1Img.objectFit)
        assertFalse(t1Img.pointerEventsEnabled)
        val t1TitleLabel = t1Item.children.filterIsInstance<Label>().single { it.hasClass("gui-tab-title") }

        // t2: remote favicon URL -> 16x16 Image with tintMode NONE by default
        val t2Item = findTabContainer("t2")
        val t2Slot = t2Item.children.filterIsInstance<FlexBox>().single { it.hasClass("gui-tab-icon-slot") }
        val t2Img = t2Slot.children.filterIsInstance<Image>().single()
        assertEquals("https://example.com/favicon.png", t2Img.sourceUrl)
        assertEquals(16, t2Img.widthPx)
        assertEquals(16, t2Img.heightPx)
        assertEquals(ImageTintMode.NONE, t2Img.tintMode)
        assertFalse(t2Img.pointerEventsEnabled)

        // t3: iconRes + tintIconToTheme = true -> MONOCOLOR_AUTO_THEME
        val t3Item = findTabContainer("t3")
        val t3Slot = t3Item.children.filterIsInstance<FlexBox>().single { it.hasClass("gui-tab-icon-slot") }
        val t3Img = t3Slot.children.filterIsInstance<Image>().single()
        assertEquals(svgRes, t3Img.resourceId)
        assertEquals(ImageTintMode.MONOCOLOR_AUTO_THEME, t3Img.tintMode)

        // t4: short iconName symbol -> fallback Label visible and icon slot hidden
        val t4Item = findTabContainer("t4")
        val t4Slot = t4Item.children.filterIsInstance<FlexBox>().single { it.hasClass("gui-tab-icon-slot") }
        val t4FallbackLabel = t4Item.children.filterIsInstance<Label>().single { it.hasClass("gui-tab-icon") }
        assertFalse(t4Slot.isVisible)
        assertTrue(t4FallbackLabel.isVisible)
        assertEquals("★", t4FallbackLabel.text)

        // In-place updateTabIcon & updateTabTitle do NOT recreate strip children or header views
        assertTrue(tabbed.updateTabIcon("t1", iconUrl = "https://kotlinlang.org/favicon.ico"))
        assertTrue(tabbed.updateTabTitle("t1", "Kotlin Docs", "https://kotlinlang.org"))

        assertSame(t1Item, findTabContainer("t1"))
        assertSame(t1Slot, t1Item.children.filterIsInstance<FlexBox>().single { it.hasClass("gui-tab-icon-slot") })
        assertSame(t1Img, t1Slot.children.filterIsInstance<Image>().single())
        assertSame(t1TitleLabel, t1Item.children.filterIsInstance<Label>().single { it.hasClass("gui-tab-title") })

        assertEquals("https://kotlinlang.org/favicon.ico", t1Img.sourceUrl)
        assertEquals(16, t1Img.widthPx)
        assertEquals(16, t1Img.heightPx)
        assertEquals(ImageTintMode.NONE, t1Img.tintMode)
        assertEquals("Kotlin Docs", t1TitleLabel.text)
        assertEquals("https://kotlinlang.org/favicon.ico", tabbed.tabs.first { it.tabId == "t1" }.iconUrl)
        assertEquals("https://kotlinlang.org", tabbed.tabs.first { it.tabId == "t1" }.subtitle)

        // Explicit custom Image via updateTabIcon also updates slot in place
        val customImg = Image("https://example.org/custom.png")
        assertTrue(tabbed.updateTabIcon("t1", iconImage = customImg, tintIconToTheme = true))
        assertSame(t1Item, findTabContainer("t1"))
        assertSame(customImg, t1Slot.children.filterIsInstance<Image>().single())
        assertEquals(ImageTintMode.MONOCOLOR_AUTO_THEME, customImg.tintMode)

        tabbed.dispose()
    }

    @Test
    fun updateViewportSizeDoesNotRebuildTabStripWhenChromeModeIsUnchanged() {
        val factory = FakeChromiumFactory(
            backendTarget = BackendTarget.Windows,
            defaultTitleCapabilities = desktopCaps()
        )
        GenericUI.activeFactory = factory

        val window = Window(title = "ResizePerfWindow", initialSize = Size(1280, 800))
        val tabbed = TabbedFragment(
            config = TabbedFragmentConfig(
                chromeMode = TabbedChromeMode.AUTO,
                mobileBreakpointWidthPx = 720,
                chromeStyle = TabbedChromeStyle.CHROMIUM
            )
        )
        tabbed.openTab("tab-1", "Home", selectImmediately = true) { SamplePageFragment("Home") }
        tabbed.openTab("tab-2", "Docs", selectImmediately = false) { SamplePageFragment("Docs") }

        window.setFragment(tabbed)
        window.show(factory)

        val winImpl = factory.createdWindows.single()
        var chromeSyncCount = 0
        tabbed.onChromeLayoutSynced = { chromeSyncCount++ }

        val tab1Before = tabbed.desktopTabStripContainer.children
            .filterIsInstance<StyledContainer>()
            .single { it.getAttribute("data-tab-id") == "tab-1" }
        val titleBarAppliesBefore = winImpl.appliedTitleBars.size

        // Desktop -> Desktop maximize/resize calls must NOT rebuild strip or re-apply titlebar
        tabbed.updateViewportSize(1280, 800)
        tabbed.updateViewportSize(1600, 900)
        tabbed.updateViewportSize(1920, 1080)
        tabbed.onResize(1920, 1080)

        assertEquals(0, chromeSyncCount)
        assertEquals(titleBarAppliesBefore, winImpl.appliedTitleBars.size)
        val tab1AfterMaximize = tabbed.desktopTabStripContainer.children
            .filterIsInstance<StyledContainer>()
            .single { it.getAttribute("data-tab-id") == "tab-1" }
        assertSame(tab1Before, tab1AfterMaximize)

        // Crossing mobile breakpoint triggers chrome sync cleanly, then staying in mobile does not rebuild
        tabbed.updateViewportSize(520, 800)
        assertEquals(1, chromeSyncCount)
        assertEquals(TabbedChromeMode.MOBILE_SWITCHER_PILL, tabbed.resolvedChromeMode)

        tabbed.updateViewportSize(480, 800)
        assertEquals(1, chromeSyncCount)

        // Crossing back to desktop triggers chrome sync cleanly
        tabbed.updateViewportSize(1280, 800)
        assertEquals(2, chromeSyncCount)
        assertEquals(TabbedChromeMode.DESKTOP_BROWSER_TABS, tabbed.resolvedChromeMode)

        window.close()
    }

    @Test
    fun showTitleBarWindowIconDefaultsFalseForManagedTitleBarAndPreservesExplicitWindowTitleBar() {
        assertFalse(TabbedFragmentConfig().showTitleBarWindowIcon)

        val factory = FakeChromiumFactory(
            backendTarget = BackendTarget.Windows,
            defaultTitleCapabilities = desktopCaps()
        )
        GenericUI.activeFactory = factory

        // Case 1: Auto-managed TitleBar uses config.showTitleBarWindowIcon (false by default)
        val managedWindow = Window(title = "ManagedDefaultIcon", initialSize = Size(1280, 800))
        val managedTabbed = TabbedFragment(
            config = TabbedFragmentConfig(chromeStyle = TabbedChromeStyle.CHROMIUM)
        )
        managedTabbed.openTab("m1", "Tab", selectImmediately = true) { SamplePageFragment("Tab") }
        managedWindow.setFragment(managedTabbed)
        assertFalse(managedWindow.getTitleBar()!!.showWindowIcon)
        managedWindow.dispose()
        managedTabbed.dispose()

        // Case 2: Explicit TitleBar on host Window preserves its showWindowIcon = true
        val explicitWindow = Window(title = "ExplicitTitleBarIcon", initialSize = Size(1280, 800))
        val explicitTitleBar = TitleBar(showWindowIcon = true)
        explicitWindow.setTitleBar(explicitTitleBar)
        val hostedTabbed = TabbedFragment(
            config = TabbedFragmentConfig(
                chromeStyle = TabbedChromeStyle.CHROMIUM,
                showTitleBarWindowIcon = false
            )
        )
        hostedTabbed.openTab("e1", "Tab", selectImmediately = true) { SamplePageFragment("Tab") }
        explicitWindow.setFragment(hostedTabbed)
        assertSame(explicitTitleBar, explicitWindow.getTitleBar())
        assertTrue(explicitWindow.getTitleBar()!!.showWindowIcon)
        explicitWindow.dispose()
        hostedTabbed.dispose()
    }
}
