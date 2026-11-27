package com.nexustools.genericui

import com.nexustools.genericui.component.action.Button
import com.nexustools.genericui.component.action.IButton
import com.nexustools.genericui.component.action.setOnClick
import com.nexustools.genericui.component.action.styled.StyledButton
import com.nexustools.genericui.component.form.ISlider
import com.nexustools.genericui.component.form.ITextArea
import com.nexustools.genericui.component.form.ITextField
import com.nexustools.genericui.component.form.NumberField
import com.nexustools.genericui.component.form.PasswordField
import com.nexustools.genericui.component.form.RangeSlider
import com.nexustools.genericui.component.form.SearchField
import com.nexustools.genericui.component.form.Slider
import com.nexustools.genericui.component.form.TextArea
import com.nexustools.genericui.component.form.TextField
import com.nexustools.genericui.component.form.styled.StyledSlider
import com.nexustools.genericui.component.form.styled.StyledTextArea
import com.nexustools.genericui.component.form.styled.StyledTextField
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.shell.Window
import com.nexustools.genericui.component.typography.ILabelTarget
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
import com.nexustools.genericui.enums.css.CssBorderStyle
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
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.ComputedStyle
import com.nexustools.genericui.style.css.CssFontFamily
import com.nexustools.genericui.style.css.StyledVisualSpec
import com.nexustools.genericui.style.gradient.Gradient.LinearGradient
import com.nexustools.genericui.style.policy.AestheticStylePolicy
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
import kotlin.test.assertTrue

class StyledTextFieldAndTextAreaTest {

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
        var hoverState: Boolean = false
        var focusState: Boolean = false
        var activeState: Boolean = false
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
        override fun isHoveredState(): Boolean = hoverState
        override fun isFocusedState(): Boolean = focusState
        override fun setFocusedState(focused: Boolean) { focusState = focused }
        override fun isActivePressedState(): Boolean = activeState
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

    private class RecordingContainerNativeImpl(prefix: String) : StubNativeImpl(prefix), ContainerNativeImpl<Any> {
        override fun addChildNative(childImpl: NativeImpl<*>, index: Int) {}
        override fun removeChildNative(childImpl: NativeImpl<*>) {}
        override fun clearChildrenNative() {}
    }

    private class RecordingWindowNativeImpl : StubNativeImpl("window"), WindowNativeImpl<Any> {
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

    private open class RecordingTextInputNativeImpl(
        val initialMultiline: Boolean,
        prefix: String
    ) : StubNativeImpl(prefix), TextInputNativeImpl<Any> {
        private var currentValue: String = ""
        private var currentPlaceholder: String = ""
        private var currentLabel: String? = null
        var readOnlyFlag: Boolean = false
        var passwordFlag: Boolean = false
        var multilineFlag: Boolean = initialMultiline
        var onValueChangedListener: ((String) -> Unit)? = null
        var onSubmitListener: ((String) -> Unit)? = null

        override fun setValue(value: String) { currentValue = value }
        override fun getValue(): String = currentValue
        override fun setPlaceholder(placeholder: String) { currentPlaceholder = placeholder }
        override fun getPlaceholder(): String = currentPlaceholder
        override fun setLabel(label: String?) { currentLabel = label }
        override fun getLabel(): String? = currentLabel
        override fun setReadOnly(readOnly: Boolean) { readOnlyFlag = readOnly }
        override fun setPasswordMode(password: Boolean) { passwordFlag = password }
        override fun setMultiline(multiline: Boolean) { multilineFlag = multiline }
        override fun setOnValueChanged(listener: ((String) -> Unit)?) { onValueChangedListener = listener }
        override fun setOnSubmit(listener: ((String) -> Unit)?) { onSubmitListener = listener }
    }

    private class RecordingStyledTextInputNativeImpl(
        multiline: Boolean,
        prefix: String
    ) : RecordingTextInputNativeImpl(multiline, prefix), StyledTextInputNativeImpl<Any> {
        private var baseVisuals: StyledVisualSpec = StyledVisualSpec.EMPTY
        private var hoverVisuals: StyledVisualSpec? = null
        private var focusVisuals: StyledVisualSpec? = null
        private var activeVisuals: StyledVisualSpec? = null
        private var disabledVisuals: StyledVisualSpec? = null
        var setBaseVisualsCalls: Int = 0
            private set

        override fun setBaseVisuals(visuals: StyledVisualSpec) {
            setBaseVisualsCalls++
            baseVisuals = visuals
        }
        override fun getBaseVisuals(): StyledVisualSpec = baseVisuals
        override fun setHoverVisuals(visuals: StyledVisualSpec?) { hoverVisuals = visuals }
        override fun getHoverVisuals(): StyledVisualSpec? = hoverVisuals
        override fun setFocusVisuals(visuals: StyledVisualSpec?) { focusVisuals = visuals }
        override fun getFocusVisuals(): StyledVisualSpec? = focusVisuals
        override fun setActiveVisuals(visuals: StyledVisualSpec?) { activeVisuals = visuals }
        override fun getActiveVisuals(): StyledVisualSpec? = activeVisuals
        override fun setDisabledVisuals(visuals: StyledVisualSpec?) { disabledVisuals = visuals }
        override fun getDisabledVisuals(): StyledVisualSpec? = disabledVisuals
    }

    private class RecordingFormFactory : NativeFactory {
        val plainInputs = mutableListOf<RecordingTextInputNativeImpl>()
        val styledInputs = mutableListOf<RecordingStyledTextInputNativeImpl>()
        override val backendTarget: BackendTarget = BackendTarget.Windows
        private fun unsupported(): Nothing = throw UnsupportedOperationException("Unused in StyledTextFieldAndTextAreaTest")
        override fun supportsTcp(): Boolean = false
        override fun supportsUdp(): Boolean = false
        override fun createApplication(): ApplicationNativeImpl<*> = unsupported()
        override fun createWindow(): WindowNativeImpl<*> = RecordingWindowNativeImpl()
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
        override fun createContainer(classPrefix: String): ContainerNativeImpl<*> = RecordingContainerNativeImpl(classPrefix)
        override fun createStyledContainer(classPrefix: String, tagName: String): StyledContainerNativeImpl<*> = unsupported()
        override fun createStyledComponent(classPrefix: String, tagName: String): StyledComponentNativeImpl<*> = unsupported()
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
        override fun createTextInput(multiline: Boolean): TextInputNativeImpl<*> =
            RecordingTextInputNativeImpl(multiline, "form-control").also { plainInputs.add(it) }
        override fun createStyledTextInput(multiline: Boolean, classPrefix: String): StyledTextInputNativeImpl<*> =
            RecordingStyledTextInputNativeImpl(multiline, classPrefix).also { styledInputs.add(it) }
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
    }

    @Test
    fun `uniform component interfaces work polymorphically across native and styled controls`() {
        var buttonClicks = 0
        val buttons: List<IButton> = listOf(Button("Native"), StyledButton("Styled"))
        buttons.forEach { button ->
            assertIs<ILabelTarget>(button)
            button.setOnClick { buttonClicks++ }
            button.click()
            button.isDisabled = true
            assertFalse(button.isEnabled)
            button.isDisabled = false
            assertTrue(button.isEnabled)
        }
        assertEquals(2, buttonClicks)

        val sliderValues = mutableListOf<Double>()
        val sliders: List<ISlider> = listOf(
            Slider(min = 0.0, max = 100.0, value = 10.0, step = 5.0),
            RangeSlider(min = 0.0, max = 100.0, minValue = 20.0, maxValue = 80.0, step = 10.0),
            StyledSlider(min = 0.0, max = 100.0, value = 30.0, step = 10.0)
        )
        sliders.forEachIndexed { index, slider ->
            assertIs<ILabelTarget>(slider)
            val target = 50.0 + index * 10.0
            slider.onValueChanged = { sliderValues.add(it) }
            slider.value = target - 10.0
            assertEquals(target - 10.0, slider.value)
            when (slider) {
                is Slider -> slider.dispatchUserValueChange(target)
                is StyledSlider -> slider.value = target
            }
            assertEquals(target, slider.value)
        }
        // Slider & RangeSlider stay silent on programmatic .value= (avoiding feedback loops), while StyledSlider notifies.
        assertEquals(listOf(50.0, 60.0, 60.0, 70.0), sliderValues)

        val formFactory = RecordingFormFactory()
        val formWindow = Window("Uniform Fields")
        val formRoot = Container()
        val fieldChanges = mutableListOf<String>()
        val fields: List<ITextField> = listOf(
            TextField(value = "a", placeholder = "p1", label = "L1"),
            StyledTextField(value = "b", placeholder = "p2", label = "L2"),
            TextArea(value = "c", placeholder = "p3", rows = 3, label = "L3"),
            StyledTextArea(value = "d", placeholder = "p4", rows = 6, label = "L4")
        )
        fields.forEach { formRoot.addChild(it.labelTargetComponent) }
        formWindow.setContent(formRoot)
        formWindow.show(formFactory)
        fields.forEachIndexed { idx, field ->
            assertIs<ILabelTarget>(field)
            field.isReadOnly = idx % 2 == 1
            field.onValueChanged = { fieldChanges.add(it) }
            field.value = "next-$idx"
            assertEquals("next-$idx", field.value)
            val native = field.labelTargetComponent.nativeImpl as RecordingTextInputNativeImpl
            native.onValueChangedListener?.invoke("typed-$idx")
        }
        assertEquals(listOf("typed-0", "typed-1", "typed-2", "typed-3"), fieldChanges)

        val areas: List<ITextArea> = listOf(
            TextArea(rows = 2),
            StyledTextArea(rows = 5)
        )
        assertEquals(2, areas[0].rows)
        assertEquals(5, areas[1].rows)
        areas[0].rows = 0
        areas[1].rows = 9
        assertEquals(1, areas[0].rows)
        assertEquals(9, areas[1].rows)
    }

    @Test
    fun `StyledTextField and StyledTextArea resolve and sync full visual states and rows to StyledTextInputNativeImpl`() {
        val factory = RecordingFormFactory()
        val window = Window("Styled Inputs")
        val root = Container()

        val plainField = TextField(value = "sys", placeholder = "System")
        val styledField = StyledTextField(value = "hello", placeholder = "Search...").apply {
            setBackground(RgbaColor(18, 22, 34, 230))
            setBorder(widthPx = 2f, color = RgbaColor(80, 120, 200, 255), style = CssBorderStyle.SOLID)
            setPill(true)
            textColor = RgbaColor(240, 244, 255, 255)
            placeholderColor = RgbaColor(140, 152, 178, 255)
            setPadding(EdgeInsets(top = 8, right = 16, bottom = 8, left = 16))
            setFont(family = CssFontFamily(familyName = "Inter"), sizePx = 15f, weight = FontWeight.SEMIBOLD)
            setBackdropBlur(8)
            type = BootstrapType.DANGER
            setHoverStyle(
                borderColor = RgbaColor(110, 160, 255, 255),
                placeholderColor = RgbaColor(170, 185, 210, 255),
                opacity = 0.96f
            )
            setFocusStyle(
                backgroundColor = RgbaColor(24, 30, 48, 255),
                borderColor = RgbaColor(64, 156, 255, 255),
                borderWidthPx = 2.5f,
                focusRingColor = RgbaColor(64, 156, 255, 110),
                focusRingWidthPx = 3f
            )
            activeBackgroundColor = RgbaColor(14, 18, 28, 255)
            activeTextColor = RgbaColor(255, 255, 255, 255)
            disabledBackgroundColor = RgbaColor(45, 48, 56, 180)
            disabledPlaceholderColor = RgbaColor(100, 104, 112, 255)
            disabledOpacity = 0.55f
        }
        val styledArea = StyledTextArea(value = "line1\nline2", placeholder = "Notes", rows = 7).apply {
            setBackground(LinearGradient(90f, RgbaColor(20, 28, 46, 255), RgbaColor(32, 44, 70, 255)))
            borderRadiusPx = 14f
        }

        root.addChild(plainField)
        root.addChild(styledField)
        root.addChild(styledArea)
        window.setContent(root)
        window.show(factory)

        assertEquals(1, factory.plainInputs.size)
        assertEquals(2, factory.styledInputs.size)

        val fieldNative = factory.styledInputs[0]
        val areaNative = factory.styledInputs[1]

        assertFalse(fieldNative.multilineFlag)
        assertEquals(StyledTextField.DEFAULT_CLASS_PREFIX, fieldNative.componentClassPrefix)
        assertTrue(areaNative.multilineFlag)
        assertEquals(StyledTextArea.DEFAULT_CLASS_PREFIX, areaNative.componentClassPrefix)
        assertEquals("textarea", styledArea.htmlTag)
        assertEquals("7", areaNative.attributes["rows"])

        val base = fieldNative.getBaseVisuals()
        assertEquals(RgbaColor(18, 22, 34, 230), base.backgroundColor)
        assertEquals(RgbaColor(80, 120, 200, 255), base.borderColor)
        assertEquals(2f, base.borderWidthPx)
        assertEquals(CssBorderStyle.SOLID, base.borderStyle)
        assertEquals(999f, base.borderRadiusPx)
        assertEquals(RgbaColor(240, 244, 255, 255), base.textColor)
        assertEquals(RgbaColor(140, 152, 178, 255), base.placeholderColor)
        assertEquals(EdgeInsets(top = 8, right = 16, bottom = 8, left = 16), base.padding)
        assertEquals("Inter", base.fontFamily)
        assertEquals(15f, base.fontSizePx)
        assertEquals(FontWeight.SEMIBOLD.numericWeight, base.fontWeightNumeric)
        assertEquals(8f, base.backdropBlurPx)

        val areaBase = areaNative.getBaseVisuals()
        assertNotNull(areaBase.backgroundGradient)
        assertEquals(14f, areaBase.borderRadiusPx)

        // Verify interactive state transitions on StyledVisualSpec & native mirror
        assertEquals(base, styledField.resolveCurrentVisualSpec())
        styledField.isHovered = true
        fieldNative.hoverState = true
        assertEquals(RgbaColor(110, 160, 255, 255), styledField.resolveCurrentVisualSpec().borderColor)
        assertEquals(styledField.resolveCurrentVisualSpec(), fieldNative.resolveCurrentVisuals())

        styledField.isFocused = true
        fieldNative.focusState = true
        assertEquals(RgbaColor(64, 156, 255, 255), styledField.resolveCurrentVisualSpec().borderColor)
        assertEquals(3f, styledField.resolveCurrentVisualSpec().focusRingWidthPx)
        assertEquals(styledField.resolveCurrentVisualSpec(), fieldNative.resolveCurrentVisuals())

        styledField.isActivePressed = true
        fieldNative.activeState = true
        assertEquals(RgbaColor(14, 18, 28, 255), styledField.resolveCurrentVisualSpec().backgroundColor)
        assertEquals(styledField.resolveCurrentVisualSpec(), fieldNative.resolveCurrentVisuals())

        styledField.isEnabled = false
        assertEquals(0.55f, styledField.resolveCurrentVisualSpec().opacity)
        assertEquals(RgbaColor(100, 104, 112, 255), fieldNative.resolveCurrentVisuals().placeholderColor)

        // Verify dynamic property updates after attach sync cleanly
        styledField.setPill(false)
        assertNull(fieldNative.getBaseVisuals().borderRadiusPx)
        styledField.placeholderColor = RgbaColor(200, 210, 220, 255)
        assertEquals(RgbaColor(200, 210, 220, 255), fieldNative.getBaseVisuals().placeholderColor)
        styledArea.rows = 12
        assertEquals("12", areaNative.attributes["rows"])

        window.dispose()
    }

    @Test
    fun `AestheticStylePolicy separates system TextField and TextArea from StyledTextField and StyledTextArea`() {
        val systemControls = listOf(
            TextField(),
            TextArea(),
            PasswordField(),
            SearchField(),
            NumberField()
        )
        val styledInputs = listOf(
            StyledTextField(),
            StyledTextArea()
        )

        systemControls.forEach { control ->
            assertFalse(AestheticStylePolicy.isExplicitlyStyledComponent(control))
            assertFalse(AestheticStylePolicy.supportsBackdropFilterBlur(control, BackendTarget.Windows))
            assertTrue(AestheticStylePolicy.usesSystemThemeControlAppearance(control, BackendTarget.Windows))
            assertTrue(AestheticStylePolicy.usesSystemThemeControlAppearance(control, BackendTarget.MacOS))
            assertTrue(AestheticStylePolicy.usesSystemThemeControlAppearance(control, BackendTarget.Android))
            assertTrue(AestheticStylePolicy.usesSystemThemeControlAppearance(control, BackendTarget.IOS))
        }

        styledInputs.forEach { styled ->
            assertTrue(AestheticStylePolicy.isExplicitlyStyledComponent(styled))
            assertTrue(AestheticStylePolicy.supportsBackdropFilterBlur(styled, BackendTarget.Windows))
            assertTrue(AestheticStylePolicy.supportsBackdropFilterBlur(styled, BackendTarget.Web))
            assertFalse(AestheticStylePolicy.usesSystemThemeControlAppearance(styled, BackendTarget.Windows))
            assertFalse(AestheticStylePolicy.usesSystemThemeControlAppearance(styled, BackendTarget.MacOS))
            assertFalse(AestheticStylePolicy.usesSystemThemeControlAppearance(styled, BackendTarget.Swing))
            assertFalse(AestheticStylePolicy.usesSystemThemeControlAppearance(styled, BackendTarget.Web))
        }

        // StyledTextField must never apply BootstrapType semantic colors
        val bootstrapProbe = StyledTextField().apply { type = BootstrapType.DANGER }
        val probeSpec = bootstrapProbe.resolveBaseVisualSpec()
        assertNull(probeSpec.backgroundColor)
        assertNull(probeSpec.borderColor)
        assertNull(probeSpec.textColor)
    }
}
