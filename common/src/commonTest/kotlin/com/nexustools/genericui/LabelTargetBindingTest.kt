package com.nexustools.genericui

import com.nexustools.genericui.component.action.Button
import com.nexustools.genericui.component.action.styled.StyledButton
import com.nexustools.genericui.component.form.CheckBox
import com.nexustools.genericui.component.form.CompoundUnitInput
import com.nexustools.genericui.component.form.FormGroup
import com.nexustools.genericui.component.form.RadioButton
import com.nexustools.genericui.component.form.RangeSlider
import com.nexustools.genericui.component.form.Select
import com.nexustools.genericui.component.form.Slider
import com.nexustools.genericui.component.form.Switch
import com.nexustools.genericui.component.form.TextArea
import com.nexustools.genericui.component.form.TextField
import com.nexustools.genericui.component.form.styled.StyledSlider
import com.nexustools.genericui.component.form.styled.StyledTextArea
import com.nexustools.genericui.component.form.styled.StyledTextField
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.shell.Window
import com.nexustools.genericui.component.typography.ILabelTarget
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
import com.nexustools.genericui.style.css.StyledVisualSpec
import com.nexustools.genericui.window.TitleBar
import com.nexustools.genericui.window.WindowConfiguration
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LabelTargetBindingTest {

    private var savedActiveFactory: NativeFactory? = null

    @BeforeTest
    fun captureFactory() {
        savedActiveFactory = GenericUI.activeFactory
    }

    @AfterTest
    fun restoreFactory() {
        GenericUI.activeFactory = savedActiveFactory
    }

    private open class StubNativeImpl(prefix: String) : NativeImpl<Any> {
        override val nativeWidget: Any = prefix
        override val componentClassPrefix: String = prefix
        override var isDisposed: Boolean = false
        val attributes = mutableMapOf<String, String>()
        var focusedBacking: Boolean = false
        var clickListener: (() -> Unit)? = null
            private set
        var setOnClickCalls: Int = 0
            private set

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
        override fun setAttribute(name: String, value: String?) {
            if (value == null) attributes.remove(name) else attributes[name] = value
        }
        override fun setFocusedState(focused: Boolean) { focusedBacking = focused }
        override fun isFocusedState(): Boolean = focusedBacking
        override fun setOnClick(listener: (() -> Unit)?) {
            setOnClickCalls++
            clickListener = listener
        }
        override fun setOnDoubleClick(listener: (() -> Unit)?) {}
        override fun setOnContextMenu(listener: ((PointerEvent) -> Unit)?) {}
        override fun setOnPointerDown(listener: ((PointerEvent) -> Unit)?) {}
        override fun setOnPointerMove(listener: ((PointerEvent) -> Unit)?) {}
        override fun setOnPointerUp(listener: ((PointerEvent) -> Unit)?) {}
        override fun setOnWheel(listener: ((WheelEvent) -> Unit)?) {}
        override fun setOnKeyDown(listener: ((KeyEvent) -> Unit)?) {}
        override fun setOnKeyUp(listener: ((KeyEvent) -> Unit)?) {}
    }

    private class FakeTextNativeImpl : StubNativeImpl("text"), TextNativeImpl<Any> {
        private var text: String = ""
        private var maxLines: Int? = null
        override fun setText(text: String) { this.text = text }
        override fun getText(): String = text
        override fun setMaxLines(maxLines: Int?) { this.maxLines = maxLines }
        override fun getMaxLines(): Int? = maxLines
    }

    private open class FakeContainerNativeImpl(prefix: String) : StubNativeImpl(prefix), ContainerNativeImpl<Any> {
        override fun addChildNative(childImpl: NativeImpl<*>, index: Int) {}
        override fun removeChildNative(childImpl: NativeImpl<*>) {}
        override fun clearChildrenNative() {}
    }

    private class FakeStyledContainerNativeImpl(prefix: String) : FakeContainerNativeImpl(prefix), StyledContainerNativeImpl<Any> {
        private var visuals: StyledVisualSpec = StyledVisualSpec.EMPTY
        override fun setStyledVisuals(visuals: StyledVisualSpec) { this.visuals = visuals }
        override fun getStyledVisuals(): StyledVisualSpec = visuals
    }

    private class FakeStyledComponentNativeImpl(prefix: String) : StubNativeImpl(prefix), StyledComponentNativeImpl<Any> {
        private var visuals: StyledVisualSpec = StyledVisualSpec.EMPTY
        private var hover: StyledVisualSpec? = null
        private var focus: StyledVisualSpec? = null
        private var active: StyledVisualSpec? = null
        private var disabled: StyledVisualSpec? = null
        override fun setStyledVisuals(visuals: StyledVisualSpec) { this.visuals = visuals }
        override fun getStyledVisuals(): StyledVisualSpec = visuals
        override fun setHoverVisuals(visuals: StyledVisualSpec?) { hover = visuals }
        override fun getHoverVisuals(): StyledVisualSpec? = hover
        override fun setFocusVisuals(visuals: StyledVisualSpec?) { focus = visuals }
        override fun getFocusVisuals(): StyledVisualSpec? = focus
        override fun setActiveVisuals(visuals: StyledVisualSpec?) { active = visuals }
        override fun getActiveVisuals(): StyledVisualSpec? = active
        override fun setDisabledVisuals(visuals: StyledVisualSpec?) { disabled = visuals }
        override fun getDisabledVisuals(): StyledVisualSpec? = disabled
    }

    private class FakeButtonNativeImpl(prefix: String) : StubNativeImpl(prefix), ButtonNativeImpl<Any> {
        private var text: String = ""
        private var icon: String? = null
        override fun setText(text: String) { this.text = text }
        override fun getText(): String = text
        override fun setIcon(icon: String?) { this.icon = icon }
        override fun getIcon(): String? = icon
    }

    private class FakeStyledButtonNativeImpl(prefix: String) : StubNativeImpl(prefix), StyledButtonNativeImpl<Any> {
        private var text: String = ""
        private var icon: String? = null
        private var base: StyledVisualSpec = StyledVisualSpec.EMPTY
        private var hover: StyledVisualSpec? = null
        private var focus: StyledVisualSpec? = null
        private var active: StyledVisualSpec? = null
        private var disabled: StyledVisualSpec? = null
        override fun setText(text: String) { this.text = text }
        override fun getText(): String = text
        override fun setIcon(icon: String?) { this.icon = icon }
        override fun getIcon(): String? = icon
        override fun setBaseVisuals(visuals: StyledVisualSpec) { base = visuals }
        override fun getBaseVisuals(): StyledVisualSpec = base
        override fun setHoverVisuals(visuals: StyledVisualSpec?) { hover = visuals }
        override fun getHoverVisuals(): StyledVisualSpec? = hover
        override fun setFocusVisuals(visuals: StyledVisualSpec?) { focus = visuals }
        override fun getFocusVisuals(): StyledVisualSpec? = focus
        override fun setActiveVisuals(visuals: StyledVisualSpec?) { active = visuals }
        override fun getActiveVisuals(): StyledVisualSpec? = active
        override fun setDisabledVisuals(visuals: StyledVisualSpec?) { disabled = visuals }
        override fun getDisabledVisuals(): StyledVisualSpec? = disabled
    }

    private class FakeTextInputNativeImpl(prefix: String) : StubNativeImpl(prefix), TextInputNativeImpl<Any> {
        private var value: String = ""
        private var placeholder: String = ""
        private var label: String? = null
        override fun setValue(value: String) { this.value = value }
        override fun getValue(): String = value
        override fun setPlaceholder(placeholder: String) { this.placeholder = placeholder }
        override fun getPlaceholder(): String = placeholder
        override fun setLabel(label: String?) { this.label = label }
        override fun getLabel(): String? = label
        override fun setReadOnly(readOnly: Boolean) {}
        override fun setPasswordMode(password: Boolean) {}
        override fun setMultiline(multiline: Boolean) {}
        override fun setOnValueChanged(listener: ((String) -> Unit)?) {}
        override fun setOnSubmit(listener: ((String) -> Unit)?) {}
    }

    private class FakeStyledTextInputNativeImpl(prefix: String) : StubNativeImpl(prefix), StyledTextInputNativeImpl<Any> {
        private var value: String = ""
        private var placeholder: String = ""
        private var label: String? = null
        private var base: StyledVisualSpec = StyledVisualSpec.EMPTY
        private var hover: StyledVisualSpec? = null
        private var focus: StyledVisualSpec? = null
        private var active: StyledVisualSpec? = null
        private var disabled: StyledVisualSpec? = null
        override fun setValue(value: String) { this.value = value }
        override fun getValue(): String = value
        override fun setPlaceholder(placeholder: String) { this.placeholder = placeholder }
        override fun getPlaceholder(): String = placeholder
        override fun setLabel(label: String?) { this.label = label }
        override fun getLabel(): String? = label
        override fun setReadOnly(readOnly: Boolean) {}
        override fun setPasswordMode(password: Boolean) {}
        override fun setMultiline(multiline: Boolean) {}
        override fun setOnValueChanged(listener: ((String) -> Unit)?) {}
        override fun setOnSubmit(listener: ((String) -> Unit)?) {}
        override fun setBaseVisuals(visuals: StyledVisualSpec) { base = visuals }
        override fun getBaseVisuals(): StyledVisualSpec = base
        override fun setHoverVisuals(visuals: StyledVisualSpec?) { hover = visuals }
        override fun getHoverVisuals(): StyledVisualSpec? = hover
        override fun setFocusVisuals(visuals: StyledVisualSpec?) { focus = visuals }
        override fun getFocusVisuals(): StyledVisualSpec? = focus
        override fun setActiveVisuals(visuals: StyledVisualSpec?) { active = visuals }
        override fun getActiveVisuals(): StyledVisualSpec? = active
        override fun setDisabledVisuals(visuals: StyledVisualSpec?) { disabled = visuals }
        override fun getDisabledVisuals(): StyledVisualSpec? = disabled
    }

    private class FakeSelectNativeImpl : StubNativeImpl("select"), SelectNativeImpl<Any> {
        private var selected: String? = null
        override fun setOptions(options: List<Pair<String, String>>) {}
        override fun setSelectedValue(value: String?) { selected = value }
        override fun getSelectedValue(): String? = selected
        override fun setOnSelectionChanged(listener: ((String) -> Unit)?) {}
    }

    private class FakeCheckboxNativeImpl : StubNativeImpl("checkbox"), CheckboxNativeImpl<Any> {
        private var label: String = ""
        private var checked: Boolean = false
        override fun setLabel(label: String) { this.label = label }
        override fun getLabel(): String = label
        override fun setChecked(checked: Boolean) { this.checked = checked }
        override fun isChecked(): Boolean = checked
        override fun setOnCheckedChanged(listener: ((Boolean) -> Unit)?) {}
    }

    private class FakeRangeSliderNativeImpl : StubNativeImpl("range"), RangeSliderNativeImpl<Any> {
        private var value: Double = 0.0
        override fun setRange(min: Double, max: Double, step: Double) {}
        override fun setValue(value: Double) { this.value = value }
        override fun getValue(): Double = value
        override fun setOnValueChanged(listener: ((Double) -> Unit)?) {}
    }

    private class FakeWindowNativeImpl : StubNativeImpl("window"), WindowNativeImpl<Any> {
        override val supportsCustomTitleBar: Boolean = true
        override val supportsEmbeddedMenuBarInTitleBar: Boolean = true
        override val supportsMultiWindow: Boolean = true
        private var title: String = ""
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

    private class TargetRecordingFactory(override val backendTarget: BackendTarget) : NativeFactory {
        val textImpls = mutableListOf<FakeTextNativeImpl>()
        val textInputImpls = mutableListOf<FakeTextInputNativeImpl>()
        private fun unsupported(): Nothing = throw UnsupportedOperationException("Unused in LabelTargetBindingTest")
        override fun supportsTcp(): Boolean = false
        override fun supportsUdp(): Boolean = false
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
        override fun createStyledComponent(classPrefix: String, tagName: String): StyledComponentNativeImpl<*> =
            FakeStyledComponentNativeImpl(classPrefix)
        override fun createBookContainer(): BookContainerNativeImpl<*> = unsupported()
        override fun createScrollView(): ScrollViewNativeImpl<*> = unsupported()
        override fun createDivider(): DividerNativeImpl<*> = unsupported()
        override fun createText(tagName: String, classPrefix: String): TextNativeImpl<*> =
            FakeTextNativeImpl().also { textImpls.add(it) }
        override fun createMarkdownView(): MarkdownViewNativeImpl<*> = unsupported()
        override fun createImage(): ImageNativeImpl<*> = unsupported()
        override fun createTable(): TableNativeImpl<*> = unsupported()
        override fun createButton(classPrefix: String): ButtonNativeImpl<*> = FakeButtonNativeImpl(classPrefix)
        override fun createStyledButton(classPrefix: String): StyledButtonNativeImpl<*> = FakeStyledButtonNativeImpl(classPrefix)
        override fun createDropdown(): DropdownNativeImpl<*> = unsupported()
        override fun createTextInput(multiline: Boolean): TextInputNativeImpl<*> =
            FakeTextInputNativeImpl("form-control").also { textInputImpls.add(it) }
        override fun createStyledTextInput(multiline: Boolean, classPrefix: String): StyledTextInputNativeImpl<*> =
            FakeStyledTextInputNativeImpl(classPrefix)
        override fun createSelect(): SelectNativeImpl<*> = FakeSelectNativeImpl()
        override fun createCheckbox(switchStyle: Boolean, radioStyle: Boolean): CheckboxNativeImpl<*> = FakeCheckboxNativeImpl()
        override fun createRangeSlider(): RangeSliderNativeImpl<*> = FakeRangeSliderNativeImpl()
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
    }

    @Test
    fun `web Label target sets DOM id and HTML for attribute without duplicate click handler and clears for on null`() {
        val webFactory = TargetRecordingFactory(BackendTarget.Web)
        assertTrue(webFactory.supportsNativeHtmlLabelFor)

        val fieldPreMount = TextField().apply { id = "explicit-email-id" }
        val generatedField = StyledTextField()
        val label = Label("Email", target = fieldPreMount)
        val root = Container().apply {
            addChild(label)
            addChild(fieldPreMount)
            addChild(generatedField)
        }

        val window = Window("Web Label Binding")
        window.setContent(root)
        window.show(webFactory)

        val labelNative = webFactory.textImpls.single()
        val preMountNative = webFactory.textInputImpls.single()
        assertEquals("explicit-email-id", fieldPreMount.id)
        assertEquals("explicit-email-id", preMountNative.attributes["id"])
        assertEquals("explicit-email-id", labelNative.attributes["for"])
        assertNull(labelNative.clickListener)

        // Switching target to a control without an explicit id generates a stable id and updates "for"
        label.setTarget(generatedField)
        val generatedId = generatedField.id
        assertNotNull(generatedId)
        assertTrue(generatedId.isNotBlank())
        assertEquals(generatedId, labelNative.attributes["for"])
        assertEquals(generatedId, generatedField.ensureLabelTargetId())
        assertNull(labelNative.clickListener)

        // Clearing target removes HTML "for"
        label.target = null
        assertFalse(labelNative.attributes.containsKey("for"))
        assertNull(labelNative.clickListener)

        // User-supplied onClick still works independently on Web without triggering click-to-focus
        var userClicks = 0
        label.onClick = { userClicks++ }
        label.target = fieldPreMount
        fieldPreMount.isFocused = false
        labelNative.clickListener?.invoke()
        assertEquals(1, userClicks)
        assertFalse(fieldPreMount.isFocused)

        window.dispose()
    }

    @Test
    fun `non-web Label target focuses every ILabelTarget control and never clobbers user onClick`() {
        val swingFactory = TargetRecordingFactory(BackendTarget.Swing)
        assertFalse(swingFactory.supportsNativeHtmlLabelFor)
        GenericUI.activeFactory = swingFactory

        val textField = TextField()
        val textArea = TextArea()
        val styledTextField = StyledTextField()
        val styledTextArea = StyledTextArea()
        val button = Button("Submit")
        val styledButton = StyledButton("Styled")
        val slider = Slider()
        val rangeSlider = RangeSlider()
        val styledSlider = StyledSlider()
        val select = Select()
        val checkBox = CheckBox("Agree")
        val switchControl = Switch("Enabled")
        val radioButton = RadioButton("opt1", "Option 1")
        val compound = CompoundUnitInput()

        val targets: List<ILabelTarget> = listOf(
            textField,
            textArea,
            styledTextField,
            styledTextArea,
            button,
            styledButton,
            slider,
            rangeSlider,
            styledSlider,
            select,
            checkBox,
            switchControl,
            radioButton,
            compound
        )

        val root = Container()
        targets.forEachIndexed { idx, target ->
            root.addChild(Label("Label $idx", target = target))
        }
        root.addChild(textField)
        root.addChild(textArea)
        root.addChild(styledTextField)
        root.addChild(styledTextArea)
        root.addChild(button)
        root.addChild(styledButton)
        root.addChild(slider)
        root.addChild(rangeSlider)
        root.addChild(styledSlider)
        root.addChild(select)
        root.addChild(checkBox)
        root.addChild(switchControl)
        root.addChild(radioButton)
        root.addChild(compound)

        val window = Window("Desktop Label Focus")
        window.setContent(root)
        window.show(swingFactory)

        // No HTML "for" attribute on non-Web backends
        swingFactory.textImpls.forEach { textImpl ->
            assertFalse(textImpl.attributes.containsKey("for"))
        }

        // Order A: onClick first, target second
        var orderAClicks = 0
        val labelOrderA = Label("Order A")
        labelOrderA.onClick = { orderAClicks++ }
        labelOrderA.target = textField
        root.addChild(labelOrderA)
        textField.isFocused = false
        labelOrderA.triggerClick()
        assertEquals(1, orderAClicks)
        assertTrue(textField.isFocused)

        // Order B: target first, onClick second, then clear target and keep onClick
        var orderBClicks = 0
        val labelOrderB = Label("Order B", target = styledTextField)
        labelOrderB.onClick = { orderBClicks++ }
        root.addChild(labelOrderB)
        styledTextField.isFocused = false
        labelOrderB.triggerClick()
        assertEquals(1, orderBClicks)
        assertTrue(styledTextField.isFocused)

        styledTextField.isFocused = false
        labelOrderB.target = null
        labelOrderB.triggerClick()
        assertEquals(2, orderBClicks)
        assertFalse(styledTextField.isFocused)

        // Composite targets delegate focus properly: StyledSlider & CompoundUnitInput
        val sliderLabel = Label("Volume", target = styledSlider)
        root.addChild(sliderLabel)
        styledSlider.isFocused = false
        sliderLabel.triggerClick()
        assertTrue(styledSlider.isFocused)
        assertTrue(styledSlider.labelTargetComponent is StyledButton)
        assertTrue(styledSlider.labelTargetComponent.hasClass(StyledSlider.KNOB_CLASS))
        assertTrue(styledSlider.labelTargetComponent.isFocused)

        val compoundLabel = Label("Length", target = compound)
        root.addChild(compoundLabel)
        compound.primaryField.isFocused = false
        compoundLabel.triggerClick()
        assertTrue(compound.primaryField.isFocused)
        assertSame(compound.primaryField, compound.labelTargetComponent)

        // Standard controls & StyledButton
        targets.forEach { target ->
            val component = target.labelTargetComponent
            component.isFocused = false
            Label("Click", target = target).triggerClick()
            assertTrue(component.isFocused, "Expected ${target::class.simpleName} to focus when its label is clicked")
        }

        // Disabled target must not receive focus
        textArea.isFocused = false
        textArea.isEnabled = false
        Label("Disabled", target = textArea).triggerClick()
        assertFalse(textArea.isFocused)

        window.dispose()
    }

    @Test
    fun `FormGroup automatically binds and updates label target when control changes`() {
        val webFactory = TargetRecordingFactory(BackendTarget.Web)
        GenericUI.activeFactory = webFactory

        val firstField = TextField()
        val group = FormGroup(labelText = "Host", control = firstField)
        assertSame(firstField, group.label.target)
        val firstId = firstField.id
        assertNotNull(firstId)
        assertEquals(firstId, group.label.getAttribute("for"))

        val secondField = StyledTextArea()
        group.control = secondField
        assertSame(secondField, group.label.target)
        val secondId = secondField.id
        assertNotNull(secondId)
        assertEquals(secondId, group.label.getAttribute("for"))

        // Reassigning to non-ILabelTarget clears label.target and removes "for"
        val nonTargetContainer = Container()
        assertFalse(nonTargetContainer is ILabelTarget)
        group.control = nonTargetContainer
        assertNull(group.label.target)
        assertNull(group.label.getAttribute("for"))

        group.control = null
        assertNull(group.label.target)
    }
}
