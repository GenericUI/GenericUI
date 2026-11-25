package com.nexustools.genericui.core

import com.nexustools.genericui.component.action.Button
import com.nexustools.genericui.component.action.StyledButton
import com.nexustools.genericui.component.form.CheckBox
import com.nexustools.genericui.component.form.Select
import com.nexustools.genericui.component.form.Slider
import com.nexustools.genericui.component.form.StyledSlider
import com.nexustools.genericui.component.form.TextField
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.layout.HStack
import com.nexustools.genericui.component.layout.VStack
import com.nexustools.genericui.component.lazy.LazyAdapter
import com.nexustools.genericui.component.lazy.LazyList
import com.nexustools.genericui.component.lazy.ViewHolder
import com.nexustools.genericui.component.shell.Window
import com.nexustools.genericui.component.surface.StyledComponent
import com.nexustools.genericui.component.typography.Text
import com.nexustools.genericui.enums.bootstrap.*
import com.nexustools.genericui.enums.system.BackendTarget
import com.nexustools.genericui.enums.ui.Orientation
import com.nexustools.genericui.enums.window.DisplayCutoutMode
import com.nexustools.genericui.enums.window.WindowState
import com.nexustools.genericui.event.InputFocusManager
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
import com.nexustools.genericui.nativeimpl.dock.*
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
import com.nexustools.genericui.nativeimpl.shell.*
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ComponentFocusabilityTest {

    private open class RecordingNativeImpl(val prefix: String) : NativeImpl<Any> {
        override val nativeWidget: Any = Any()
        override val componentClassPrefix: String = prefix
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
        var visibleField = true
        var enabledField = true
        var hoveredField = false
        var focusedField = false
        var activeField = false
        var focusableField = true
        var setFocusableCallCount = 0
        var opacityField = 1f
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
        override fun setVisible(visible: Boolean) { this.visibleField = visible }
        override fun isVisible(): Boolean = visibleField
        override fun setEnabled(enabled: Boolean) { this.enabledField = enabled }
        override fun isEnabled(): Boolean = enabledField
        override fun setHoveredState(hovered: Boolean) { this.hoveredField = hovered }
        override fun isHoveredState(): Boolean = hoveredField
        override fun setFocusedState(focused: Boolean) { this.focusedField = focused }
        override fun isFocusedState(): Boolean = focusedField
        override fun setFocusable(focusable: Boolean) {
            setFocusableCallCount++
            this.focusableField = focusable
        }
        override fun isFocusable(): Boolean = focusableField
        override fun setActivePressedState(active: Boolean) { this.activeField = active }
        override fun isActivePressedState(): Boolean = activeField
        override fun setOpacity(opacity: Float) { this.opacityField = opacity }
        override fun getOpacity(): Float = opacityField
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

    private open class RecordingContainerImpl(prefix: String) : RecordingNativeImpl(prefix), ContainerNativeImpl<Any> {
        val attached = mutableListOf<NativeImpl<*>>()
        override fun addChildNative(childImpl: NativeImpl<*>, index: Int) {
            attached.removeAll { it === childImpl }
            attached.add(index.coerceIn(0, attached.size), childImpl)
        }
        override fun removeChildNative(childImpl: NativeImpl<*>) {
            attached.removeAll { it === childImpl }
        }
        override fun clearChildrenNative() { attached.clear() }
    }

    private class RecordingTextImpl : RecordingNativeImpl("text"), TextNativeImpl<Any> {
        private var text = ""
        private var maxLines: Int? = null
        override fun setText(text: String) { this.text = text }
        override fun getText(): String = text
        override fun setMaxLines(maxLines: Int?) { this.maxLines = maxLines }
        override fun getMaxLines(): Int? = maxLines
    }

    private class RecordingButtonImpl(prefix: String) : RecordingNativeImpl(prefix), ButtonNativeImpl<Any> {
        private var text = ""
        private var icon: String? = null
        override fun setText(text: String) { this.text = text }
        override fun getText(): String = text
        override fun setIcon(icon: String?) { this.icon = icon }
        override fun getIcon(): String? = icon
    }

    private class RecordingStyledButtonImpl(prefix: String) : RecordingNativeImpl(prefix), StyledButtonNativeImpl<Any> {
        private var text = ""
        private var icon: String? = null
        private var base = StyledVisualSpec()
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

    private class RecordingStyledContainerImpl(prefix: String) : RecordingContainerImpl(prefix), StyledContainerNativeImpl<Any> {
        private var visuals = StyledVisualSpec()
        override fun setStyledVisuals(visuals: StyledVisualSpec) { this.visuals = visuals }
        override fun getStyledVisuals(): StyledVisualSpec = visuals
    }

    private class RecordingStyledComponentImpl(prefix: String) : RecordingNativeImpl(prefix), StyledComponentNativeImpl<Any> {
        private var base = StyledVisualSpec()
        private var hover: StyledVisualSpec? = null
        private var focus: StyledVisualSpec? = null
        private var active: StyledVisualSpec? = null
        private var disabled: StyledVisualSpec? = null
        override fun setStyledVisuals(visuals: StyledVisualSpec) { base = visuals }
        override fun getStyledVisuals(): StyledVisualSpec = base
        override fun setHoverVisuals(visuals: StyledVisualSpec?) { hover = visuals }
        override fun getHoverVisuals(): StyledVisualSpec? = hover
        override fun setFocusVisuals(visuals: StyledVisualSpec?) { focus = visuals }
        override fun getFocusVisuals(): StyledVisualSpec? = focus
        override fun setActiveVisuals(visuals: StyledVisualSpec?) { active = visuals }
        override fun getActiveVisuals(): StyledVisualSpec? = active
        override fun setDisabledVisuals(visuals: StyledVisualSpec?) { disabled = visuals }
        override fun getDisabledVisuals(): StyledVisualSpec? = disabled
    }

    private class RecordingTextInputImpl : RecordingNativeImpl("textinput"), TextInputNativeImpl<Any> {
        private var value = ""
        private var placeholder = ""
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

    private class RecordingLazyListImpl : RecordingContainerImpl("lazylist"), LazyListNativeImpl<Any> {
        private var itemFactory: ((Int) -> NativeImpl<*>)? = null
        var itemCount: Int = 0
        override fun setOrientation(orientation: Orientation) {}
        override fun setItemCount(count: Int, itemFactory: (index: Int) -> NativeImpl<*>) {
            itemCount = count
            this.itemFactory = itemFactory
        }
        override fun notifyItemRangeChanged(startIndex: Int, count: Int) {}
        override fun scrollToIndex(index: Int) {}
    }

    private class RecordingWindowImpl : RecordingContainerImpl("window"), WindowNativeImpl<Any> {
        override val supportsCustomTitleBar: Boolean = true
        override val supportsEmbeddedMenuBarInTitleBar: Boolean = true
        override val supportsMultiWindow: Boolean = true
        override fun setTitle(title: String) {}
        override fun getTitle(): String = ""
        override fun setWindowSize(size: Size) {}
        override fun getWindowSize(): Size = Size(1280, 720)
        override fun setWindowState(state: WindowState) {}
        override fun getWindowState(): WindowState = WindowState.NORMAL
        override fun setFullscreen(fullscreen: Boolean, cutoutMode: DisplayCutoutMode) {}
        override fun isFullscreen(): Boolean = false
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

    private class RecordingFactory : NativeFactory {
        private fun unsupported(): Nothing = throw UnsupportedOperationException("Unused in test")
        override val backendTarget: BackendTarget = BackendTarget.Swing
        override fun createApplication(): ApplicationNativeImpl<*> = unsupported()
        override fun createWindow(): WindowNativeImpl<*> = RecordingWindowImpl()
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
        override fun createContainer(classPrefix: String): ContainerNativeImpl<*> = RecordingContainerImpl(classPrefix)
        override fun createStyledContainer(classPrefix: String, tagName: String): StyledContainerNativeImpl<*> = RecordingStyledContainerImpl(classPrefix)
        override fun createStyledComponent(classPrefix: String, tagName: String): StyledComponentNativeImpl<*> = RecordingStyledComponentImpl(classPrefix)
        override fun createBookContainer(): BookContainerNativeImpl<*> = unsupported()
        override fun createScrollView(): ScrollViewNativeImpl<*> = unsupported()
        override fun createDivider(): DividerNativeImpl<*> = unsupported()
        override fun createText(tagName: String, classPrefix: String): TextNativeImpl<*> = RecordingTextImpl()
        override fun createMarkdownView(): MarkdownViewNativeImpl<*> = unsupported()
        override fun createImage(): ImageNativeImpl<*> = unsupported()
        override fun createTable(): TableNativeImpl<*> = unsupported()
        override fun createButton(classPrefix: String): ButtonNativeImpl<*> = RecordingButtonImpl(classPrefix)
        override fun createStyledButton(classPrefix: String): StyledButtonNativeImpl<*> = RecordingStyledButtonImpl(classPrefix)
        override fun createDropdown(): DropdownNativeImpl<*> = unsupported()
        override fun createTextInput(multiline: Boolean): TextInputNativeImpl<*> = RecordingTextInputImpl()
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
        override fun createLazyList(): LazyListNativeImpl<*> = RecordingLazyListImpl()
        override fun createLazyGrid(): LazyGridNativeImpl<*> = unsupported()
        override fun createTreeView(): TreeViewNativeImpl<*> = unsupported()
        override fun createPermissionManager(): PermissionManagerNativeImpl = unsupported()
        override fun createSystemTray(): SystemTrayNativeImpl = unsupported()
        override fun createNotificationManager(): NotificationManagerNativeImpl = unsupported()
        override fun createNetworkMonitor(): NetworkMonitorNativeImpl = unsupported()
        override fun supportsTcp(): Boolean = false
        override fun supportsUdp(): Boolean = false
    }

    private class SpyButton(text: String) : Button(text) {
        var setFocusableCalls = 0
        override fun setFocusable(focusable: Boolean) {
            setFocusableCalls++
            super.setFocusable(focusable)
        }
    }

    @AfterTest
    fun tearDown() {
        InputFocusManager.reset()
        GenericUI.resetForTests()
    }

    @Test
    fun nonFocusableLeafSilentlyIgnoresSetFocusableAndRejectsFocus() {
        val factory = RecordingFactory()
        val text = Text("Static label")
        val styledLeaf = StyledComponent("decor")

        assertFalse(text.supportsFocus)
        assertFalse(text.isFocusable)
        assertFalse(styledLeaf.supportsFocus)
        assertFalse(styledLeaf.isFocusable)

        text.setFocusable(true)
        assertFalse(text.isFocusable)
        text.isFocusable = false
        assertFalse(text.isFocusable)
        text.isFocused = true
        assertFalse(text.isFocused)

        val root = Container().apply {
            addChild(text)
            addChild(styledLeaf)
        }
        Window().apply { setContent(root) }.show(factory)

        val textNative = text.nativeImpl as RecordingTextImpl
        val leafNative = styledLeaf.nativeImpl as RecordingStyledComponentImpl
        assertEquals(0, textNative.setFocusableCallCount)
        assertEquals(0, leafNative.setFocusableCallCount)

        text.setFocusable(false)
        styledLeaf.setFocusable(true)
        assertEquals(0, textNative.setFocusableCallCount)
        assertEquals(0, leafNative.setFocusableCallCount)
    }

    @Test
    fun focusableControlDisablesFocusClearsInputFocusAndRejectsRefocus() {
        val factory = RecordingFactory()
        val button = Button("Play")
        val textField = TextField(value = "hello")
        val checkBox = CheckBox("Check")
        val select = Select()
        val slider = Slider()

        assertTrue(button.supportsFocus && button.isFocusable)
        assertTrue(textField.supportsFocus && textField.isFocusable)
        assertTrue(checkBox.supportsFocus && checkBox.isFocusable)
        assertTrue(select.supportsFocus && select.isFocusable)
        assertTrue(slider.supportsFocus && slider.isFocusable)

        val root = VStack().apply {
            addChild(button)
            addChild(textField)
        }
        Window().apply { setContent(root) }.show(factory)

        val textNative = textField.nativeImpl as RecordingTextInputImpl
        assertTrue(textNative.focusableField)

        textField.isFocused = true
        assertTrue(textField.isFocused)
        assertSame(textField, InputFocusManager.focusedComponent)
        assertTrue(InputFocusManager.isTypingInTextInput)

        textField.setFocusable(false)
        assertFalse(textField.isFocusable)
        assertFalse(textField.isFocused)
        assertFalse(textNative.focusableField)
        assertFalse(textNative.focusedField)
        assertNull(InputFocusManager.focusedComponent)
        assertFalse(InputFocusManager.isTypingInTextInput)

        textField.isFocused = true
        assertFalse(textField.isFocused)
        assertFalse(textNative.focusedField)

        textField.setFocusable(true)
        assertTrue(textField.isFocusable)
        assertTrue(textNative.focusableField)
        textField.isFocused = true
        assertTrue(textField.isFocused)
        assertSame(textField, InputFocusManager.focusedComponent)
    }

    @Test
    fun containerSetFocusablePreservesChildLocalFocusableStateWithoutCallingChildSetFocusable() {
        val factory = RecordingFactory()
        val btnEnabled = SpyButton("Enabled")
        val btnSelfDisabled = SpyButton("Self Disabled").apply {
            setFocusable(false)
        }
        val nestedBtn = SpyButton("Nested")
        val label = Text("Non-focusable")

        assertEquals(0, btnEnabled.setFocusableCalls)
        assertEquals(1, btnSelfDisabled.setFocusableCalls)
        assertFalse(btnSelfDisabled.isFocusable)

        val nestedRow = HStack().apply { addChild(nestedBtn) }
        val container = VStack().apply {
            addChild(btnEnabled)
            addChild(btnSelfDisabled)
            addChild(nestedRow)
            addChild(label)
        }
        Window().apply { setContent(container) }.show(factory)

        val btnEnabledNative = btnEnabled.nativeImpl as RecordingButtonImpl
        val btnSelfDisabledNative = btnSelfDisabled.nativeImpl as RecordingButtonImpl
        val nestedBtnNative = nestedBtn.nativeImpl as RecordingButtonImpl
        val labelNative = label.nativeImpl as RecordingTextImpl

        assertTrue(btnEnabledNative.focusableField)
        assertFalse(btnSelfDisabledNative.focusableField)
        assertTrue(nestedBtnNative.focusableField)

        btnEnabled.isFocused = true
        assertTrue(btnEnabled.isFocused)

        container.setFocusable(false)
        assertFalse(container.isFocusable)
        assertFalse(btnEnabled.isFocusable)
        assertFalse(btnEnabled.isFocused)
        assertFalse(btnEnabledNative.focusableField)
        assertFalse(btnSelfDisabled.isFocusable)
        assertFalse(btnSelfDisabledNative.focusableField)
        assertFalse(nestedRow.isFocusable)
        assertFalse(nestedBtn.isFocusable)
        assertFalse(nestedBtnNative.focusableField)
        assertFalse(label.isFocusable)
        assertEquals(0, labelNative.setFocusableCallCount)

        // Container must NEVER call child.setFocusable(...)
        assertEquals(0, btnEnabled.setFocusableCalls)
        assertEquals(1, btnSelfDisabled.setFocusableCalls)
        assertEquals(0, nestedBtn.setFocusableCalls)

        // Dynamic attachment while container is non-focusable inherits false; detachment restores true
        val dynamicBtn = SpyButton("Dynamic")
        container.addChild(dynamicBtn)
        assertFalse(dynamicBtn.isFocusable)
        assertEquals(0, dynamicBtn.setFocusableCalls)
        container.removeChild(dynamicBtn)
        assertTrue(dynamicBtn.isFocusable)

        // Re-enabling container restores focusable siblings while keeping btnSelfDisabled non-focusable
        container.setFocusable(true)
        assertTrue(container.isFocusable)
        assertTrue(btnEnabled.isFocusable)
        assertTrue(btnEnabledNative.focusableField)
        assertTrue(nestedRow.isFocusable)
        assertTrue(nestedBtn.isFocusable)
        assertTrue(nestedBtnNative.focusableField)
        assertFalse(btnSelfDisabled.isFocusable)
        assertFalse(btnSelfDisabledNative.focusableField)
        assertEquals(0, btnEnabled.setFocusableCalls)
        assertEquals(1, btnSelfDisabled.setFocusableCalls)
        assertEquals(0, nestedBtn.setFocusableCalls)
    }

    @Test
    fun lazyListPropagatesFocusableAcrossPrewarmedActiveAndRecycledHolders() {
        val factory = RecordingFactory()
        val lazyList = LazyList(orientation = Orientation.VERTICAL, estimatedItemExtentPx = 50f, overscanItems = 1)
        val buttonsByIndex = linkedMapOf<Int, Button>()
        lazyList.setItems(
            count = 20,
            onLoadItem = { idx ->
                Button("Item $idx").also { btn ->
                    if (idx == 1) btn.setFocusable(false)
                    buttonsByIndex[idx] = btn
                }
            }
        )

        Window().apply { setContent(Container().apply { addChild(lazyList) }) }.show(factory)

        // Index 0 is active and focusable; index 1 explicitly setFocusable(false)
        assertTrue(buttonsByIndex[0]!!.isFocusable)
        assertFalse(buttonsByIndex[1]!!.isFocusable)

        lazyList.setFocusable(false)
        assertFalse(lazyList.isFocusable)
        for ((_, btn) in buttonsByIndex) {
            assertFalse(btn.isFocusable)
        }

        // Scroll to load later items while LazyList is non-focusable
        lazyList.onViewportChanged(scrollOffsetPx = 600f, viewportExtentPx = 200f)
        val loadedAfterScroll = lazyList.loadedItemIndices.mapNotNull { buttonsByIndex[it] }
        assertTrue(loadedAfterScroll.isNotEmpty())
        for (btn in loadedAfterScroll) {
            assertFalse(btn.isFocusable, "Newly bound LazyList items must inherit non-focusable state")
        }

        // Re-enable focusability on LazyList and test adapter recycling
        lazyList.setFocusable(true)
        for (idx in lazyList.loadedItemIndices) {
            assertTrue(buttonsByIndex[idx]!!.isFocusable)
        }

        val recycledList = LazyList(orientation = Orientation.VERTICAL, estimatedItemExtentPx = 50f, overscanItems = 0)
        val createdHolders = mutableListOf<ViewHolder>()
        recycledList.adapter = object : LazyAdapter<ViewHolder>() {
            override fun getItemCount(): Int = 10
            override fun onCreateViewHolder(viewType: Int): ViewHolder =
                ViewHolder(Button("Pooled"), viewType).also { createdHolders.add(it) }
            override fun onBindViewHolder(holder: ViewHolder, position: Int) {
                (holder.itemView as Button).text = "Item $position"
            }
        }
        Window().apply { setContent(Container().apply { addChild(recycledList) }) }.show(factory)
        recycledList.setFocusable(false)
        recycledList.onViewportChanged(scrollOffsetPx = 400f, viewportExtentPx = 100f)
        assertTrue(recycledList.loadedItemIndices.isNotEmpty())
        for (child in recycledList.children) {
            assertFalse(child.isFocusable, "Recycled ViewHolder item must inherit LazyList focusability")
        }
    }

    @Test
    fun styledSliderDelegatesFocusStrictlyToKnobButton() {
        val factory = RecordingFactory()
        val slider = StyledSlider(min = 0.0, max = 100.0, value = 25.0, step = 5.0)
        val track = slider.internalTrackButton()
        val progress = slider.internalProgressSegment()
        val knob = slider.internalKnobButton()

        assertTrue(slider.supportsFocus)
        assertTrue(slider.isFocusable)
        assertFalse(track.isFocusable)
        assertFalse(progress.isFocusable)
        assertTrue(knob.isFocusable)
        assertNull(slider.getAttribute("tabindex"))
        assertNull(track.getAttribute("tabindex"))
        assertNull(knob.getAttribute("tabindex"))

        Window().apply { setContent(Container().apply { addChild(slider) }) }.show(factory)

        val knobNative = knob.nativeImpl as RecordingStyledButtonImpl
        val trackNative = track.nativeImpl as RecordingStyledButtonImpl
        val sliderNative = slider.nativeImpl as RecordingStyledContainerImpl

        assertTrue(knobNative.focusableField)
        assertFalse(trackNative.focusableField)
        assertEquals(0, sliderNative.setFocusableCallCount)

        slider.isFocused = true
        assertTrue(slider.isFocused)
        assertTrue(knob.isFocused)
        assertFalse(track.isFocused)

        slider.setFocusable(false)
        assertFalse(slider.isFocusable)
        assertFalse(slider.isFocused)
        assertFalse(knob.isFocusable)
        assertFalse(knob.isFocused)
        assertFalse(knobNative.focusableField)
        assertEquals(0, sliderNative.setFocusableCallCount)

        slider.isFocused = true
        assertFalse(slider.isFocused)
        assertFalse(knob.isFocused)

        slider.setFocusable(true)
        assertTrue(slider.isFocusable)
        assertTrue(knob.isFocusable)
        assertTrue(knobNative.focusableField)
        // Track stays permanently non-focusable
        assertFalse(track.isFocusable)
        assertFalse(trackNative.focusableField)
    }
}
