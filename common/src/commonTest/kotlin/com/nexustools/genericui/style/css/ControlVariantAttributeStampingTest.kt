package com.nexustools.genericui.style.css

import com.nexustools.genericui.component.action.SplitDropdownButton
import com.nexustools.genericui.component.form.ColorPicker
import com.nexustools.genericui.component.form.ComboBox
import com.nexustools.genericui.component.form.DatePicker
import com.nexustools.genericui.component.form.NumberField
import com.nexustools.genericui.component.form.SearchField
import com.nexustools.genericui.component.form.SelectOption
import com.nexustools.genericui.component.form.TimePicker
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.shell.Window
import com.nexustools.genericui.core.AbstractComponent
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
import com.nexustools.genericui.nativeimpl.system.NetworkMonitorNativeImpl
import com.nexustools.genericui.nativeimpl.system.NotificationManagerNativeImpl
import com.nexustools.genericui.nativeimpl.system.PermissionManagerNativeImpl
import com.nexustools.genericui.nativeimpl.system.SystemTrayNativeImpl
import com.nexustools.genericui.nativeimpl.tabs.TabContainerNativeImpl
import com.nexustools.genericui.nativeimpl.tabs.TabNativeImpl
import com.nexustools.genericui.nativeimpl.typography.MarkdownViewNativeImpl
import com.nexustools.genericui.nativeimpl.typography.TextNativeImpl
import com.nexustools.genericui.window.TitleBar
import com.nexustools.genericui.window.WindowConfiguration
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ControlVariantAttributeStampingTest {

    private var savedFactory: NativeFactory? = null

    @BeforeTest
    fun setUp() {
        savedFactory = GenericUI.activeFactory
    }

    @AfterTest
    fun tearDown() {
        GenericUI.resetForTests()
        GenericUI.activeFactory = savedFactory
    }

    private open class BaseRecordingNativeImpl(
        override val componentClassPrefix: String
    ) : NativeImpl<Any> {
        override val nativeWidget: Any = Any()
        override var isDisposed: Boolean = false
        val attributes = linkedMapOf<String, String?>()

        override fun dispose() { isDisposed = true }
        override fun setClass(className: String) {}
        override fun getClass(): String = componentClassPrefix
        override fun addClass(className: String) {}
        override fun removeClass(className: String) {}
        override fun hasClass(className: String): Boolean = false
        override fun setType(type: BootstrapType?) {}
        override fun getType(): BootstrapType? = null
        override fun setOutline(outline: Boolean) {}
        override fun isOutline(): Boolean = false
        override fun setSize(size: BootstrapSize?) {}
        override fun getSize(): BootstrapSize? = null
        override fun setBackgroundType(type: BootstrapType?) {}
        override fun getBackgroundType(): BootstrapType? = null
        override fun setTextColorType(type: BootstrapType?) {}
        override fun getTextColorType(): BootstrapType? = null
        override fun setBorderType(type: BootstrapType?) {}
        override fun getBorderType(): BootstrapType? = null
        override fun setTextAlignment(alignment: TextAlignment?) {}
        override fun getTextAlignment(): TextAlignment? = null
        override fun setFontWeight(weight: FontWeight?) {}
        override fun getFontWeight(): FontWeight? = null
        override fun setTextTransform(transform: TextTransform?) {}
        override fun getTextTransform(): TextTransform? = null
        override fun setTextOverflow(overflow: TextOverflow?) {}
        override fun getTextOverflow(): TextOverflow? = null
        override fun setBorderRadius(radius: BorderRadiusStyle?) {}
        override fun getBorderRadius(): BorderRadiusStyle? = null
        override fun setShadow(shadow: ShadowElevation?) {}
        override fun getShadow(): ShadowElevation? = null
        override fun setDisplay(display: DisplayMode?) {}
        override fun getDisplay(): DisplayMode? = null
        override fun setFlexDirection(direction: FlexDirection?) {}
        override fun getFlexDirection(): FlexDirection? = null
        override fun setFlexWrap(wrap: FlexWrap?) {}
        override fun getFlexWrap(): FlexWrap? = null
        override fun setJustifyContent(justify: JustifyContent?) {}
        override fun getJustifyContent(): JustifyContent? = null
        override fun setAlignItems(align: AlignItems?) {}
        override fun getAlignItems(): AlignItems? = null
        override fun setAlignSelf(align: AlignSelf?) {}
        override fun getAlignSelf(): AlignSelf? = null
        override fun setOverflow(overflow: OverflowBehavior?) {}
        override fun getOverflow(): OverflowBehavior? = null
        override fun setPosition(position: PositionMode?) {}
        override fun getPosition(): PositionMode? = null
        override fun setMarginAll(scale: SpacingScale?) {}
        override fun getMarginAll(): SpacingScale? = null
        override fun setPaddingAll(scale: SpacingScale?) {}
        override fun getPaddingAll(): SpacingScale? = null
        override fun setVisible(visible: Boolean) {}
        override fun isVisible(): Boolean = true
        override fun setEnabled(enabled: Boolean) {}
        override fun isEnabled(): Boolean = true
        override fun setOpacity(opacity: Float) {}
        override fun getOpacity(): Float = 1f
        override fun setTooltipText(tooltip: String?) {}
        override fun getTooltipText(): String? = null
        override fun setAttribute(name: String, value: String?) {
            if (value == null) attributes.remove(name) else attributes[name] = value
        }
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

    private class RecordingContainerNativeImpl(prefix: String) : BaseRecordingNativeImpl(prefix), ContainerNativeImpl<Any> {
        override fun addChildNative(childImpl: NativeImpl<*>, index: Int) {}
        override fun removeChildNative(childImpl: NativeImpl<*>) {}
        override fun clearChildrenNative() {}
    }

    private class RecordingTextInputNativeImpl : BaseRecordingNativeImpl("form-control"), TextInputNativeImpl<Any> {
        private var textValue = ""
        private var placeholderText = ""
        private var labelText: String? = null
        override fun setValue(value: String) { textValue = value }
        override fun getValue(): String = textValue
        override fun setPlaceholder(placeholder: String) { placeholderText = placeholder }
        override fun getPlaceholder(): String = placeholderText
        override fun setLabel(label: String?) { labelText = label }
        override fun getLabel(): String? = labelText
        override fun setReadOnly(readOnly: Boolean) {}
        override fun setPasswordMode(password: Boolean) {}
        override fun setMultiline(multiline: Boolean) {}
        override fun setOnValueChanged(listener: ((String) -> Unit)?) {}
        override fun setOnSubmit(listener: ((String) -> Unit)?) {}
    }

    private class RecordingSelectNativeImpl : BaseRecordingNativeImpl("form-select"), SelectNativeImpl<Any> {
        private var selected: String? = null
        override fun setOptions(options: List<Pair<String, String>>) {}
        override fun setSelectedValue(value: String?) { selected = value }
        override fun getSelectedValue(): String? = selected
        override fun setOnSelectionChanged(listener: ((String) -> Unit)?) {}
    }

    private class RecordingDropdownNativeImpl : BaseRecordingNativeImpl("dropdown"), DropdownNativeImpl<Any> {
        var primaryActionListener: (() -> Unit)? = null
        override fun addChildNative(childImpl: NativeImpl<*>, index: Int) {}
        override fun removeChildNative(childImpl: NativeImpl<*>) {}
        override fun clearChildrenNative() {}
        override fun setLabel(label: String) {}
        override fun setItems(items: List<Pair<String, String>>) {}
        override fun setOnItemSelected(listener: ((String) -> Unit)?) {}
        override fun setOnPrimaryAction(listener: (() -> Unit)?) {
            primaryActionListener = listener
        }
    }

    private class RecordingWindowNativeImpl : BaseRecordingNativeImpl("window"), WindowNativeImpl<Any> {
        override val supportsCustomTitleBar: Boolean = true
        override val supportsEmbeddedMenuBarInTitleBar: Boolean = true
        override val supportsMultiWindow: Boolean = true
        private var title: String = "Test"
        private var size: Size = Size(800, 600)
        private var windowState: WindowState = WindowState.NORMAL
        private var fullscreen: Boolean = false
        override fun addChildNative(childImpl: NativeImpl<*>, index: Int) {}
        override fun removeChildNative(childImpl: NativeImpl<*>) {}
        override fun clearChildrenNative() {}
        override fun setTitle(title: String) { this.title = title }
        override fun getTitle(): String = title
        override fun setWindowSize(size: Size) { this.size = size }
        override fun getWindowSize(): Size = size
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

    private class RecordingVariantFactory : NativeFactory {
        val dropdowns = mutableListOf<RecordingDropdownNativeImpl>()

        private fun unsupported(): Nothing = throw UnsupportedOperationException("Unused in test")
        override val backendTarget: BackendTarget = BackendTarget.Kde
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
        override fun createStyledComponent(classPrefix: String, tagName: String): com.nexustools.genericui.nativeimpl.surface.StyledComponentNativeImpl<*> = unsupported()
        override fun createBookContainer(): BookContainerNativeImpl<*> = unsupported()
        override fun createScrollView(): ScrollViewNativeImpl<*> = unsupported()
        override fun createDivider(): DividerNativeImpl<*> = unsupported()
        override fun createText(tagName: String, classPrefix: String): TextNativeImpl<*> = unsupported()
        override fun createMarkdownView(): MarkdownViewNativeImpl<*> = unsupported()
        override fun createImage(): ImageNativeImpl<*> = unsupported()
        override fun createTable(): TableNativeImpl<*> = unsupported()
        override fun createButton(classPrefix: String): ButtonNativeImpl<*> = unsupported()
        override fun createStyledButton(classPrefix: String): StyledButtonNativeImpl<*> = unsupported()
        override fun createDropdown(): DropdownNativeImpl<*> = RecordingDropdownNativeImpl().also { dropdowns.add(it) }
        override fun createTextInput(multiline: Boolean): TextInputNativeImpl<*> = RecordingTextInputNativeImpl()
        override fun createStyledTextInput(multiline: Boolean, classPrefix: String): StyledTextInputNativeImpl<*> = unsupported()
        override fun createSelect(): SelectNativeImpl<*> = RecordingSelectNativeImpl()
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

    @Test
    fun testPreMountVariantAttributeStampingAcrossFormAndSplitControls() {
        GenericUI.activeFactory = null

        val combo = ComboBox(options = listOf(SelectOption("a", "A")), allowCustomInput = true)
        assertEquals("combobox", combo.getAttribute("data-control-variant"))
        assertEquals("true", combo.getAttribute("data-allow-custom-input"))
        combo.allowCustomInput = false
        assertEquals("false", combo.getAttribute("data-allow-custom-input"))

        val datePicker = DatePicker()
        assertEquals("date", datePicker.getAttribute("data-control-variant"))

        val timePicker = TimePicker()
        assertEquals("time", timePicker.getAttribute("data-control-variant"))

        val colorPicker = ColorPicker()
        assertEquals("color", colorPicker.getAttribute("data-control-variant"))

        val searchField = SearchField()
        assertEquals("search", searchField.getAttribute("data-control-variant"))

        val numberField = NumberField()
        assertEquals("number", numberField.getAttribute("data-control-variant"))

        val split = SplitDropdownButton("Run")
        assertEquals("split-dropdown", split.getAttribute("data-control-variant"))
    }

    @Test
    fun testPostMountSyncAndSplitDropdownPrimaryActionWiring() {
        val factory = RecordingVariantFactory()
        GenericUI.initialize(factory)

        var initialClicks = 0
        var updatedClicks = 0
        val combo = ComboBox(options = listOf(SelectOption("one", "One")), allowCustomInput = false)
        val date = DatePicker()
        val time = TimePicker()
        val color = ColorPicker()
        val search = SearchField()
        val number = NumberField()
        val split = SplitDropdownButton("Build") { initialClicks++ }

        val root = Container().apply {
            addChild(combo)
            addChild(date)
            addChild(time)
            addChild(color)
            addChild(search)
            addChild(number)
            addChild(split)
        }
        val window = Window("VariantTest")
        window.setContent(root)
        window.show(factory)

        val controls: List<Pair<AbstractComponent, String>> = listOf(
            combo to "combobox",
            date to "date",
            time to "time",
            color to "color",
            search to "search",
            number to "number",
            split to "split-dropdown"
        )
        for ((comp, expectedVariant) in controls) {
            val impl = comp.nativeImpl as? BaseRecordingNativeImpl
            assertNotNull(impl)
            assertEquals(expectedVariant, impl.attributes["data-control-variant"])
        }
        assertEquals("false", (combo.nativeImpl as BaseRecordingNativeImpl).attributes["data-allow-custom-input"])

        val dropdownImpl = factory.dropdowns.single()
        dropdownImpl.primaryActionListener?.invoke()
        assertEquals(1, initialClicks)

        split.onPrimaryAction = { updatedClicks++ }
        dropdownImpl.primaryActionListener?.invoke()
        assertEquals(1, initialClicks)
        assertEquals(1, updatedClicks)
    }
}
