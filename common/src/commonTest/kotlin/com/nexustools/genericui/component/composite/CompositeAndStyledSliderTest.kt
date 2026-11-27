package com.nexustools.genericui.component.composite

import com.nexustools.genericui.component.action.styled.StyledButton
import com.nexustools.genericui.component.form.styled.StyledSlider
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.shell.Window
import com.nexustools.genericui.component.surface.styled.StyledComponent
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.enums.bootstrap.*
import com.nexustools.genericui.enums.css.CssCursor
import com.nexustools.genericui.enums.system.BackendTarget
import com.nexustools.genericui.enums.window.DisplayCutoutMode
import com.nexustools.genericui.enums.window.WindowState
import com.nexustools.genericui.event.KeyEvent
import com.nexustools.genericui.event.PointerDragManager
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
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.ComputedStyle
import com.nexustools.genericui.style.css.CssCursorValue
import com.nexustools.genericui.style.css.CssCustomCursor
import com.nexustools.genericui.style.css.CssProperty
import com.nexustools.genericui.style.css.CssStyleSheet
import com.nexustools.genericui.style.css.StyledVisualSpec
import com.nexustools.genericui.window.TitleBar
import com.nexustools.genericui.window.WindowConfiguration
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CompositeAndStyledSliderTest {

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
        var visibleState = true
        var enabledState = true
        private var hoveredField = false
        private var focusedField = false
        private var activeField = false
        var opacityValue = 1f
        private var tooltip: String? = null
        var customCursorState: CssCursorValue? = null
        val classes = linkedSetOf<String>()
        var lastComputedStyle: ComputedStyle? = null

        override fun dispose() { isDisposed = true }
        override fun setClass(className: String) {
            this.className = className
            classes.clear()
            className.split(' ').filter { it.isNotBlank() }.forEach { classes.add(it) }
        }
        override fun getClass(): String = className
        override fun addClass(className: String) { classes.add(className) }
        override fun removeClass(className: String) { classes.remove(className) }
        override fun hasClass(className: String): Boolean = className in classes
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
        override fun setVisible(visible: Boolean) { this.visibleState = visible }
        override fun isVisible(): Boolean = visibleState
        override fun setEnabled(enabled: Boolean) { this.enabledState = enabled }
        override fun isEnabled(): Boolean = enabledState
        override fun setHoveredState(hovered: Boolean) { this.hoveredField = hovered }
        override fun isHoveredState(): Boolean = hoveredField
        override fun setFocusedState(focused: Boolean) { this.focusedField = focused }
        override fun isFocusedState(): Boolean = focusedField
        override fun setActivePressedState(active: Boolean) { this.activeField = active }
        override fun isActivePressedState(): Boolean = activeField
        override fun setOpacity(opacity: Float) { this.opacityValue = opacity }
        override fun getOpacity(): Float = opacityValue
        override fun setTooltipText(tooltip: String?) { this.tooltip = tooltip }
        override fun getTooltipText(): String? = tooltip
        override fun setPreferredSize(size: Size?) {}
        override fun getBounds(): Rect = Rect.ZERO
        override fun setPadding(padding: EdgeInsets) {}
        override fun setMargin(margin: EdgeInsets) {}
        override fun setCustomCursor(cursor: CssCursorValue?) { customCursorState = cursor }
        override fun applyComputedStyle(style: ComputedStyle) { lastComputedStyle = style }
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
        val attachedChildren = mutableListOf<NativeImpl<*>>()
        override fun addChildNative(childImpl: NativeImpl<*>, index: Int) {
            attachedChildren.removeAll { it === childImpl }
            attachedChildren.add(index.coerceIn(0, attachedChildren.size), childImpl)
        }
        override fun removeChildNative(childImpl: NativeImpl<*>) {
            attachedChildren.removeAll { it === childImpl }
        }
        override fun clearChildrenNative() {
            attachedChildren.clear()
        }
    }

    private class RecordingStyledContainerImpl(
        prefix: String,
        val tagName: String
    ) : RecordingContainerImpl(prefix), StyledContainerNativeImpl<Any> {
        private var visualsState: StyledVisualSpec = StyledVisualSpec()
        override fun setStyledVisuals(visuals: StyledVisualSpec) { this.visualsState = visuals }
        override fun getStyledVisuals(): StyledVisualSpec = visualsState
    }

    private class RecordingStyledComponentImpl(
        prefix: String,
        val tagName: String
    ) : RecordingNativeImpl(prefix), StyledComponentNativeImpl<Any> {
        private var baseVisualsState = StyledVisualSpec()
        private var hoverVisualsState: StyledVisualSpec? = null
        private var focusVisualsState: StyledVisualSpec? = null
        private var activeVisualsState: StyledVisualSpec? = null
        private var disabledVisualsState: StyledVisualSpec? = null
        override fun setStyledVisuals(visuals: StyledVisualSpec) { baseVisualsState = visuals }
        override fun getStyledVisuals(): StyledVisualSpec = baseVisualsState
        override fun setHoverVisuals(visuals: StyledVisualSpec?) { hoverVisualsState = visuals }
        override fun getHoverVisuals(): StyledVisualSpec? = hoverVisualsState
        override fun setFocusVisuals(visuals: StyledVisualSpec?) { focusVisualsState = visuals }
        override fun getFocusVisuals(): StyledVisualSpec? = focusVisualsState
        override fun setActiveVisuals(visuals: StyledVisualSpec?) { activeVisualsState = visuals }
        override fun getActiveVisuals(): StyledVisualSpec? = activeVisualsState
        override fun setDisabledVisuals(visuals: StyledVisualSpec?) { disabledVisualsState = visuals }
        override fun getDisabledVisuals(): StyledVisualSpec? = disabledVisualsState
    }

    private class RecordingStyledButtonImpl(prefix: String) : RecordingNativeImpl(prefix), StyledButtonNativeImpl<Any> {
        private var baseVisualsState = StyledVisualSpec()
        private var hoverVisualsState: StyledVisualSpec? = null
        private var focusVisualsState: StyledVisualSpec? = null
        private var activeVisualsState: StyledVisualSpec? = null
        private var disabledVisualsState: StyledVisualSpec? = null
        private var textState: String = ""
        private var iconState: String? = null
        override fun setText(text: String) { textState = text }
        override fun getText(): String = textState
        override fun setIcon(icon: String?) { iconState = icon }
        override fun getIcon(): String? = iconState
        override fun setBaseVisuals(visuals: StyledVisualSpec) { baseVisualsState = visuals }
        override fun getBaseVisuals(): StyledVisualSpec = baseVisualsState
        override fun setHoverVisuals(visuals: StyledVisualSpec?) { hoverVisualsState = visuals }
        override fun getHoverVisuals(): StyledVisualSpec? = hoverVisualsState
        override fun setFocusVisuals(visuals: StyledVisualSpec?) { focusVisualsState = visuals }
        override fun getFocusVisuals(): StyledVisualSpec? = focusVisualsState
        override fun setActiveVisuals(visuals: StyledVisualSpec?) { activeVisualsState = visuals }
        override fun getActiveVisuals(): StyledVisualSpec? = activeVisualsState
        override fun setDisabledVisuals(visuals: StyledVisualSpec?) { disabledVisualsState = visuals }
        override fun getDisabledVisuals(): StyledVisualSpec? = disabledVisualsState
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
        val styledContainerImpls = mutableListOf<RecordingStyledContainerImpl>()
        val styledComponentImpls = mutableListOf<RecordingStyledComponentImpl>()
        val styledButtonImpls = mutableListOf<RecordingStyledButtonImpl>()

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
        override fun createStyledContainer(classPrefix: String, tagName: String): StyledContainerNativeImpl<*> =
            RecordingStyledContainerImpl(classPrefix, tagName).also { styledContainerImpls.add(it) }
        override fun createStyledComponent(classPrefix: String, tagName: String): StyledComponentNativeImpl<*> =
            RecordingStyledComponentImpl(classPrefix, tagName).also { styledComponentImpls.add(it) }
        override fun createBookContainer(): BookContainerNativeImpl<*> = unsupported()
        override fun createScrollView(): ScrollViewNativeImpl<*> = unsupported()
        override fun createDivider(): DividerNativeImpl<*> = unsupported()
        override fun createText(tagName: String, classPrefix: String): TextNativeImpl<*> = unsupported()
        override fun createMarkdownView(): MarkdownViewNativeImpl<*> = unsupported()
        override fun createImage(): ImageNativeImpl<*> = unsupported()
        override fun createTable(): TableNativeImpl<*> = unsupported()
        override fun createButton(classPrefix: String): ButtonNativeImpl<*> = unsupported()
        override fun createStyledButton(classPrefix: String): StyledButtonNativeImpl<*> =
            RecordingStyledButtonImpl(classPrefix).also { styledButtonImpls.add(it) }
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

    private class SampleComposite : Composite(classPrefix = "sample-composite") {
        val childA = StyledComponent("child-a")
        val childB = StyledButton("B", styledClassPrefix = "child-b")

        init {
            addCompositeChild(childA)
            insertCompositeChild(0, childB)
        }

        fun exposedChildren(): List<AbstractComponent> = compositeChildren
        fun removeFirst(): Boolean = removeCompositeChild(childB)
    }

    @AfterTest
    fun tearDown() {
        PointerDragManager.cancelActiveDrag()
        GenericUI.resetForTests()
    }

    @Test
    fun testCompositeEncapsulationNativeBackingLifecycleAndNoAlphaDoubling() {
        val factory = RecordingFactory()
        val comp = SampleComposite()

        assertFalse((comp as Any) is AbstractContainer, "Composite must NOT extend AbstractContainer")
        assertEquals(listOf(comp.childB, comp.childA), comp.exposedChildren())
        assertEquals("genericui:composite", comp.htmlTag)

        val window = Window().apply {
            setContent(Container().apply { addChild(comp) })
        }
        window.show(factory)

        val compNative = comp.nativeImpl as RecordingStyledContainerImpl
        assertEquals("genericui:composite", compNative.tagName)
        assertEquals(2, compNative.attachedChildren.size)
        assertTrue(compNative.attachedChildren[0] === comp.childB.nativeImpl)
        assertTrue(compNative.attachedChildren[1] === comp.childA.nativeImpl)

        // Setting opacity on Composite updates its own visual spec/opacity without alpha-doubling internal children
        comp.opacity = 0.45f
        assertEquals(0.45f, compNative.opacityValue)
        assertEquals(0.45f, compNative.getStyledVisuals().opacity)
        assertEquals(1.0f, comp.childA.opacity)
        assertEquals(1.0f, comp.childB.opacity)

        // Propagating enablement & key dispatch across composite children
        val receivedKeys = mutableListOf<String>()
        comp.childA.onKeyDown = { ev -> receivedKeys.add(ev.key) }
        comp.triggerKeyDown(KeyEvent(key = "ArrowRight"))
        assertEquals(listOf("ArrowRight"), receivedKeys)

        comp.isEnabled = false
        assertFalse(comp.childA.isEnabled)
        assertFalse(comp.childB.isEnabled)
        comp.triggerKeyDown(KeyEvent(key = "ArrowLeft"))
        assertEquals(listOf("ArrowRight"), receivedKeys)
        comp.isEnabled = true

        assertTrue(comp.removeFirst())
        assertEquals(listOf(comp.childA), comp.exposedChildren())
        assertEquals(1, compNative.attachedChildren.size)

        val childANative = comp.childA.nativeImpl as RecordingStyledComponentImpl
        comp.dispose()
        assertTrue(compNative.isDisposed)
        assertTrue(childANative.isDisposed)
    }

    @Test
    fun testStyledComponentVisualSpecsOpacityAndCursorNone() {
        val factory = RecordingFactory()
        val leaf = StyledComponent(classPrefix = "meter-fill").apply {
            backgroundColor = RgbaColor(20, 140, 255, 255)
            borderRadiusPx = 4f
            hoverBackgroundColor = RgbaColor(40, 170, 255, 255)
            disabledOpacity = 0.35f
            pointerEventsEnabled = false
            cursor = CssCursor.NONE.cssValue
            zIndex = 3
        }
        assertFalse((leaf as Any) is AbstractContainer)
        assertEquals("genericui:styled-component", leaf.htmlTag)
        assertEquals(CssCursor.NONE, CssCustomCursor.parse("none"))

        val window = Window().apply {
            setContent(Container().apply { addChild(leaf) })
        }
        window.show(factory)

        val impl = leaf.nativeImpl as RecordingStyledComponentImpl
        assertEquals(RgbaColor(20, 140, 255, 255), impl.getStyledVisuals().backgroundColor)
        assertEquals(4f, impl.getStyledVisuals().borderRadiusPx)
        assertEquals(RgbaColor(40, 170, 255, 255), impl.getHoverVisuals()?.backgroundColor)
        assertEquals(0.35f, impl.getDisabledVisuals()?.opacity)
        assertEquals(CssCursor.NONE, impl.customCursorState)
        assertEquals(3, leaf.zIndex)
        assertFalse(leaf.pointerEventsEnabled)

        leaf.opacity = 0.6f
        assertEquals(0.6f, impl.opacityValue)
        assertEquals(0.6f, impl.getStyledVisuals().opacity)

        leaf.isHovered = true
        assertEquals(RgbaColor(40, 170, 255, 255), impl.resolveCurrentVisuals().backgroundColor)
        leaf.isEnabled = false
        assertEquals(0.35f, impl.resolveCurrentVisuals().opacity)
    }

    @Test
    fun testStyledSliderCompositionInteractionKeyboardAndDisabledState() {
        val factory = RecordingFactory()
        val slider = StyledSlider(min = 0.0, max = 100.0, value = 25.0, step = 5.0)
        assertTrue((slider as Any) is Composite)
        assertFalse((slider as Any) is AbstractContainer)
        assertEquals("genericui:styled-slider", slider.htmlTag)
        assertTrue(slider.hasClass("styled-slider"))

        val children = slider.internalCompositeChildren()
        assertEquals(3, children.size)
        val track = children[0] as StyledButton
        val progress = children[1] as StyledComponent
        val knob = children[2] as StyledButton
        assertTrue(track.hasClass("styled-slider-track"))
        assertTrue(progress.hasClass("styled-slider-progress"))
        assertTrue(knob.hasClass("styled-slider-knob"))

        // Verify zero static theme inline styles are hardcoded on Kotlin construction; only dynamic width/left pct!
        assertTrue(slider.style.isEmpty(), "StyledSlider root style must come from CSS")
        assertTrue(track.style.isEmpty(), "Track inline style must come from CSS")
        assertTrue(knob.style.hasProperty(CssProperty.LEFT))
        assertFalse(knob.style.hasProperty(CssProperty.BACKGROUND_COLOR))
        assertEquals("25%", progress.style.getWidth()?.toCssString())
        assertEquals("25%", knob.style.getLeft()?.toCssString())

        val window = Window().apply {
            setContent(Container().apply { addChild(slider) })
        }
        window.show(factory)

        val valueEvents = mutableListOf<Double>()
        val scrubEvents = mutableListOf<Double>()
        val changeEvents = mutableListOf<Double>()
        slider.onValueChanged { valueEvents.add(it) }
        slider.onUserScrub { scrubEvents.add(it) }
        slider.onChange { changeEvents.add(it) }

        track.setBounds(Rect(0, 0, 200, 20))
        slider.setBounds(Rect(0, 0, 200, 20))

        // Programmatic value set must NOT emit onUserScrub
        slider.value = 40.0
        assertEquals("40%", progress.style.getWidth()?.toCssString())
        assertEquals("40%", knob.style.getLeft()?.toCssString())
        assertEquals(listOf(40.0), valueEvents)
        assertEquals(listOf(40.0), changeEvents)
        assertTrue(scrubEvents.isEmpty())

        // Pointer click-to-seek at x = 152 on 200px wide track (76% -> snapped to step 5.0 = 75.0)
        track.onPointerDown?.invoke(PointerEvent(x = 152f, screenX = 152f))
        assertTrue(slider.isScrubbing)
        assertEquals(75.0, slider.value)
        assertEquals(listOf(75.0), scrubEvents)

        PointerDragManager.notifyGlobalPointerMove(PointerEvent(x = 180f, screenX = 180f), coordinatesAreTargetLocal = true)
        assertEquals(90.0, slider.value)
        assertEquals("90%", progress.style.getWidth()?.toCssString())
        assertEquals("90%", knob.style.getLeft()?.toCssString())

        track.onPointerUp?.invoke(PointerEvent(x = 180f, screenX = 180f))
        assertFalse(slider.isScrubbing)

        // Knob drag scrubbing from 90% backward by 80px (-40% -> 50%)
        knob.onPointerDown?.invoke(PointerEvent(x = 4f, screenX = 180f))
        assertTrue(slider.isScrubbing)
        knob.onPointerMove?.invoke(PointerEvent(x = 4f, screenX = 100f))
        assertEquals(50.0, slider.value)
        knob.onPointerUp?.invoke(PointerEvent(x = 4f, screenX = 100f))
        assertFalse(slider.isScrubbing)

        // Keyboard navigation only when focused, without 3x duplicate increments
        slider.isFocused = false
        track.isFocused = false
        knob.isFocused = false
        slider.triggerKeyDown(KeyEvent("ArrowRight"))
        assertEquals(50.0, slider.value, "Unfocused slider must ignore arrow keys")

        slider.isFocused = true
        slider.triggerKeyDown(KeyEvent("ArrowRight"))
        assertEquals(55.0, slider.value)
        slider.triggerKeyDown(KeyEvent("ArrowLeft"))
        assertEquals(50.0, slider.value)
        slider.triggerKeyDown(KeyEvent("PageUp"))
        assertEquals(100.0, slider.value)
        slider.triggerKeyDown(KeyEvent("Home"))
        assertEquals(0.0, slider.value)
        slider.triggerKeyDown(KeyEvent("End"))
        assertEquals(100.0, slider.value)

        // Disabled state rejects interaction
        slider.isEnabled = false
        slider.triggerKeyDown(KeyEvent("ArrowLeft"))
        track.onPointerDown?.invoke(PointerEvent(x = 20f, screenX = 20f))
        assertFalse(slider.isScrubbing)
        assertEquals(100.0, slider.value)

        slider.isEnabled = true
        slider.addClass("disabled")
        track.onPointerDown?.invoke(PointerEvent(x = 20f, screenX = 20f))
        assertFalse(slider.isScrubbing)
        assertEquals(100.0, slider.value)

        // Apply stylesheet matching StyledSlider sub-elements cleanly
        val css = CssStyleSheet.parse(
            """
            .styled-slider-track { z-index: 1; }
            .styled-slider-progress { z-index: 3; pointer-events: none; }
            .styled-slider-knob { z-index: 4; }
            .styled-slider:disabled { opacity: 0.45; pointer-events: none; }
            .styled-slider:disabled .styled-slider-knob { cursor: default; }
            """.trimIndent()
        )
        slider.applyStyleSheet(css)
        assertEquals(1, track.computedStyle?.zIndex)
        assertEquals(3, progress.computedStyle?.zIndex)
        assertEquals(false, progress.computedStyle?.pointerEventsEnabled)
        assertEquals(4, knob.computedStyle?.zIndex)

        slider.removeClass("disabled")
        slider.isEnabled = false
        assertNotNull(slider.computedStyle?.disabledStyle)
        assertEquals(0.45f, slider.opacity)
        assertEquals(CssCursor.DEFAULT, knob.computedStyle?.customCursor)
        assertNull(track.style.getBackgroundColor())
    }
}
