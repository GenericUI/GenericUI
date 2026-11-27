package com.nexustools.genericui.component.tabs

import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.media.Image
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
import com.nexustools.genericui.resources.ResourceID
import com.nexustools.genericui.resources.ResourceType
import com.nexustools.genericui.style.color.ImageTintMode
import com.nexustools.genericui.style.css.ComputedStyle
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TabImageTest {

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

    private class FakeTabNativeImpl(val initialClosable: Boolean) : FakeContainerNativeImpl("tab"), TabNativeImpl<Any> {
        var closableState: Boolean = initialClosable
        var tintImageToThemeState: Boolean = true
        var iconState: String? = null
        var onCloseClickedListener: (() -> Unit)? = null
        val callLog = mutableListOf<String>()

        override fun setTabIdNative(id: String) {}
        override fun setTitleNative(title: String) {}
        override fun setIconNative(icon: String?) {
            iconState = icon
            callLog.add("setIconNative:$icon")
        }
        override fun setTintImageToThemeNative(tintToTheme: Boolean) {
            tintImageToThemeState = tintToTheme
            callLog.add("setTintImageToThemeNative:$tintToTheme")
        }
        override fun setTooltipNative(tooltip: String?) {}
        override fun setClosableNative(closable: Boolean) {
            closableState = closable
            callLog.add("setClosableNative:$closable")
        }
        override fun setDirtyNative(dirty: Boolean) {}
        override fun setPinnedNative(pinned: Boolean) {}
        override fun setEditableNative(editable: Boolean) {}
        override fun setBadgeNative(badgeText: String?, variantCss: String?) {}
        override fun setSelectedNative(selected: Boolean) {}
        override fun setCustomHeaderComponentNative(customImpl: NativeImpl<*>?) {}
        override fun setOnCloseClicked(listener: (() -> Unit)?) {
            onCloseClickedListener = listener
            callLog.add("setOnCloseClicked:${listener != null}")
        }
        override fun setOnTitleEdited(listener: ((String) -> Unit)?) {}
    }

    private class FakeTabContainerNativeImpl : FakeContainerNativeImpl("tab-container"), TabContainerNativeImpl<Any> {
        private var selectedIndex: Int = 0
        override fun addTabNative(title: String, contentImpl: NativeImpl<*>, index: Int) {}
        override fun removeTabNative(index: Int) {}
        override fun setTabTitleNative(index: Int, title: String) {}
        override fun setSelectedTabIndex(index: Int, fadeTransition: Boolean) { selectedIndex = index }
        override fun getSelectedTabIndex(): Int = selectedIndex
        override fun setOnTabSelected(listener: ((Int) -> Unit)?) {}
    }

    private class FakeImageNativeImpl : StubNativeImpl("img"), ImageNativeImpl<Any> {
        private var url: String? = null
        private var fit: ObjectFit = ObjectFit.CONTAIN
        override fun setSourceUrl(url: String?) { this.url = url }
        override fun getSourceUrl(): String? = url
        override fun setFallbackText(fallback: String?) {}
        override fun setObjectFit(fit: ObjectFit) { this.fit = fit }
        override fun getObjectFit(): ObjectFit = fit
    }

    private class FakeTabTestFactory : NativeFactory {
        override val backendTarget: BackendTarget = BackendTarget.Swing
        val createdTabs = mutableListOf<FakeTabNativeImpl>()
        private fun unsupported(): Nothing = throw UnsupportedOperationException("Unused in TabImageTest")
        override fun createApplication(): ApplicationNativeImpl<*> = unsupported()
        override fun createWindow(): WindowNativeImpl<*> = unsupported()
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
        override fun createStyledComponent(classPrefix: String, tagName: String): StyledComponentNativeImpl<*> = unsupported()
        override fun createBookContainer(): BookContainerNativeImpl<*> = unsupported()
        override fun createScrollView(): ScrollViewNativeImpl<*> = unsupported()
        override fun createDivider(): DividerNativeImpl<*> = unsupported()
        override fun createText(tagName: String, classPrefix: String): TextNativeImpl<*> = unsupported()
        override fun createMarkdownView(): MarkdownViewNativeImpl<*> = unsupported()
        override fun createImage(): ImageNativeImpl<*> = FakeImageNativeImpl()
        override fun createTable(): TableNativeImpl<*> = unsupported()
        override fun createButton(classPrefix: String): ButtonNativeImpl<*> = unsupported()
        override fun createStyledButton(classPrefix: String): StyledButtonNativeImpl<*> = unsupported()
        override fun createDropdown(): DropdownNativeImpl<*> = unsupported()
        override fun createTextInput(multiline: Boolean): TextInputNativeImpl<*> = unsupported()
        override fun createStyledTextInput(multiline: Boolean, classPrefix: String): StyledTextInputNativeImpl<*> = unsupported()
        override fun createSelect(): SelectNativeImpl<*> = unsupported()
        override fun createCheckbox(switchStyle: Boolean, radioStyle: Boolean): CheckboxNativeImpl<*> = unsupported()
        override fun createRangeSlider(): RangeSliderNativeImpl<*> = unsupported()
        override fun createTab(closable: Boolean): TabNativeImpl<*> =
            FakeTabNativeImpl(closable).also { createdTabs.add(it) }
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
        override fun createNetworkMonitor(): NetworkMonitorNativeImpl = unsupported()
        override fun supportsTcp(): Boolean = false
        override fun supportsUdp(): Boolean = false
    }

    @AfterTest
    fun tearDown() {
        GenericUI.resetForTests()
    }

    @Test
    fun tabSupportsImageIconResIconPlacementAndZeroDefaultTooltip() {
        val resId = ResourceID(0x7f010001, ResourceType.DRAWABLE, "ic_library", "drawable/ic_library.svg")
        val img = Image(resId, alt = "Library").apply {
            widthPx = 22
            heightPx = 22
        }

        val tab = Tab(
            tabId = "library",
            title = "LIBRARY",
            image = img,
            tintImageToTheme = true,
            iconSizePx = 22,
            iconPlacement = TabIconPlacement.TOP
        )

        assertEquals("library", tab.tabId)
        assertEquals("LIBRARY", tab.title)
        assertSame(img, tab.image)
        assertEquals(resId, tab.iconRes)
        assertEquals(resId.toResourceUri(), tab.icon)
        assertTrue(tab.tintImageToTheme)
        assertEquals(22, tab.iconSizePx)
        assertEquals(TabIconPlacement.TOP, tab.iconPlacement)
        assertNull(tab.tooltip, "Tab must never set a default or fallback tooltip")

        // Verify setIcon(ResourceID) updates iconRes, icon, and image
        val searchRes = ResourceID(0x7f010002, ResourceType.DRAWABLE, "ic_search", "drawable/ic_search.svg")
        tab.setIcon(searchRes)
        assertEquals(searchRes, tab.iconRes)
        assertEquals(searchRes.toResourceUri(), tab.icon)
        assertNotNull(tab.image)
        assertEquals(searchRes, tab.image?.resourceId)

        // Verify setImage(src, tintToTheme = false)
        tab.setImage("https://example.com/custom.png", tintToTheme = false)
        assertFalse(tab.tintImageToTheme)
        assertEquals("https://example.com/custom.png", tab.icon)
        assertNotNull(tab.image)
        assertEquals(ImageTintMode.NONE, tab.image!!.tintMode)
    }

    @Test
    fun tabSubclassesForwardImageAndIconParametersWithZeroDefaultTooltips() {
        val resId = ResourceID(0x7f010003, ResourceType.DRAWABLE, "ic_downloads", "drawable/ic_downloads.svg")

        val closeable = CloseableTab(
            tabId = "closeable",
            title = "Closeable",
            iconRes = resId,
            tintImageToTheme = false,
            iconSizePx = 24,
            iconPlacement = TabIconPlacement.TOP
        )
        assertEquals(resId, closeable.iconRes)
        assertEquals(resId.toResourceUri(), closeable.icon)
        assertNotNull(closeable.image)
        assertFalse(closeable.tintImageToTheme)
        assertEquals(24, closeable.iconSizePx)
        assertEquals(TabIconPlacement.TOP, closeable.iconPlacement)
        assertNull(closeable.tooltip)

        val badge = BadgeTab(
            tabId = "badge",
            title = "Updates",
            badgeText = "4",
            iconRes = resId,
            iconSizePx = 18,
            iconPlacement = TabIconPlacement.TOP
        )
        assertEquals(resId, badge.iconRes)
        assertEquals("4", badge.badgeText)
        assertEquals(18, badge.iconSizePx)
        assertEquals(TabIconPlacement.TOP, badge.iconPlacement)
        assertNull(badge.tooltip)

        val buttonTab = ButtonTab(
            title = "+",
            id = "add",
            iconRes = resId,
            iconSizePx = 16
        )
        assertEquals(resId, buttonTab.iconRes)
        assertEquals(16, buttonTab.iconSizePx)
        assertNull(buttonTab.tooltip)

        val editable = EditableTab(
            tabId = "editable",
            title = "Editable",
            iconRes = resId,
            iconPlacement = TabIconPlacement.TOP
        )
        assertEquals(resId, editable.iconRes)
        assertEquals(TabIconPlacement.TOP, editable.iconPlacement)
        assertNull(editable.tooltip)

        val pinned = PinnedTab(
            tabId = "pinned",
            title = "Pinned",
            iconRes = resId,
            iconSizePx = 20
        )
        assertEquals(resId, pinned.iconRes)
        assertEquals(20, pinned.iconSizePx)
        assertNull(pinned.tooltip)
    }

    @Test
    fun tabContainerSupportsFullWidthTabsAndTabImageMutators() {
        val container = TabContainer()
        assertFalse(container.fullWidthTabs)
        container.fullWidthTabs = true
        assertTrue(container.fullWidthTabs)

        val libRes = ResourceID(0x7f010004, ResourceType.DRAWABLE, "ic_library", "drawable/ic_library.svg")
        val dlRes = ResourceID(0x7f010005, ResourceType.DRAWABLE, "ic_downloads", "drawable/ic_downloads.svg")

        container.addTab(
            title = "LIBRARY",
            content = Container(),
            iconRes = libRes,
            iconSizePx = 22,
            iconPlacement = TabIconPlacement.TOP
        )
        container.addCloseableTab(
            title = "DOWNLOADS",
            content = Container(),
            iconRes = dlRes,
            iconSizePx = 22,
            iconPlacement = TabIconPlacement.TOP
        )

        val entries = container.entries
        assertEquals(2, entries.size)
        assertEquals(libRes, entries[0].tab.iconRes)
        assertEquals(22, entries[0].tab.iconSizePx)
        assertEquals(TabIconPlacement.TOP, entries[0].tab.iconPlacement)
        assertNull(entries[0].tab.tooltip)

        val newRes = ResourceID(0x7f010006, ResourceType.DRAWABLE, "ic_sources", "drawable/ic_sources.svg")
        val firstTabId = entries[0].id
        container.setTabIcon(firstTabId, newRes, tintToTheme = false)
        assertEquals(newRes, entries[0].tab.iconRes)
        assertFalse(entries[0].tab.tintImageToTheme)

        val customImg = Image("https://example.com/tab.png").apply {
            widthPx = 24
            heightPx = 24
        }
        container.setTabImage(firstTabId, customImg, tintToTheme = true)
        assertSame(customImg, entries[0].tab.image)
        assertTrue(entries[0].tab.tintImageToTheme)
    }

    @Test
    fun fragmentTabContainerSupportsFullWidthTabsAndIconParameters() {
        val fragTabs = FragmentTabContainer()
        assertFalse(fragTabs.fullWidthTabs)
        fragTabs.fullWidthTabs = true
        assertTrue(fragTabs.fullWidthTabs)

        val res1 = ResourceID(0x7f010007, ResourceType.DRAWABLE, "ic_tab1", "drawable/ic_tab1.svg")
        val res2 = ResourceID(0x7f010008, ResourceType.DRAWABLE, "ic_tab2", "drawable/ic_tab2.svg")

        fragTabs.addTab(
            title = "FIRST",
            fragment = object : Fragment() {},
            iconRes = res1,
            iconSizePx = 22,
            iconPlacement = TabIconPlacement.TOP
        )
        fragTabs.addCloseableTab(
            title = "SECOND",
            fragment = object : Fragment() {},
            iconRes = res2,
            tintImageToTheme = false,
            iconSizePx = 20,
            iconPlacement = TabIconPlacement.START
        )

        val tab0 = fragTabs.entries[0].tab
        val tab1 = fragTabs.entries[1].tab
        assertEquals(res1, tab0.iconRes)
        assertEquals(22, tab0.iconSizePx)
        assertEquals(TabIconPlacement.TOP, tab0.iconPlacement)
        assertNull(tab0.tooltip)

        assertEquals(res2, tab1.iconRes)
        assertFalse(tab1.tintImageToTheme)
        assertEquals(20, tab1.iconSizePx)
        assertEquals(TabIconPlacement.START, tab1.iconPlacement)
        assertNull(tab1.tooltip)

        val updatedRes = ResourceID(0x7f010009, ResourceType.DRAWABLE, "ic_updated", "drawable/ic_updated.svg")
        fragTabs.setTabIcon(tab0.tabId, updatedRes, tintToTheme = false)
        assertEquals(updatedRes, tab0.iconRes)
        assertFalse(tab0.tintImageToTheme)
    }

    private class TestMountHost(private val factory: NativeFactory) : Container() {
        override fun resolveActiveFactory(): NativeFactory? = factory
    }

    @Test
    fun tabSupportsNativeClosableConstructorAndDynamicClosableToggling() {
        val factory = FakeTabTestFactory()
        GenericUI.initialize(factory)

        var allowClose = false
        var closeRequestedCount = 0
        var closedCount = 0

        val host = TestMountHost(factory)
        val container = TabContainer()
        host.addChild(container)
        val tab = Tab(
            tabId = "closable_base",
            title = "Browser Tab",
            closable = true,
            onCloseRequested = {
                closeRequestedCount++
                allowClose
            },
            onClosed = {
                closedCount++
            }
        )
        assertTrue(tab.isCloseable)
        assertTrue(tab.isClosable)
        container.addTab(tab, Container())
        host.ensureNativeImpl()

        val nativeTab = factory.createdTabs.single()
        assertTrue(nativeTab.initialClosable)
        assertTrue(nativeTab.closableState)
        assertNotNull(nativeTab.onCloseClickedListener)

        // Vetoed close leaves tab open and does not invoke onClosed
        nativeTab.onCloseClickedListener?.invoke()
        assertEquals(1, closeRequestedCount)
        assertEquals(0, closedCount)
        assertEquals(1, container.entries.size)

        // Allowed close removes tab from container and invokes onClosed once
        allowClose = true
        nativeTab.onCloseClickedListener?.invoke()
        assertEquals(2, closeRequestedCount)
        assertEquals(1, closedCount)
        assertTrue(container.entries.isEmpty())

        // Dynamic closability toggling after creation wires setClosableNative(true) & setOnCloseClicked
        var dynamicClosed = 0
        val dynamicTab = Tab(
            tabId = "dynamic_tab",
            title = "Dynamic",
            onClosed = { dynamicClosed++ }
        )
        container.addTab(dynamicTab, Container())
        val dynamicNative = factory.createdTabs[1]
        assertFalse(dynamicNative.initialClosable)
        assertFalse(dynamicNative.closableState)

        dynamicTab.isCloseable = true
        assertTrue(dynamicNative.closableState)
        assertNotNull(dynamicNative.onCloseClickedListener)
        dynamicNative.onCloseClickedListener?.invoke()
        assertEquals(1, dynamicClosed)
        assertTrue(container.entries.isEmpty())
    }

    @Test
    fun tabDisablesThemeTintingAutomaticallyForRemoteAndRasterFaviconsUnlessOverridden() {
        assertTrue(Tab.isRasterOrRemoteColorIcon("https://example.com/favicon.png"))
        assertTrue(Tab.isRasterOrRemoteColorIcon("http://localhost:8080/favicon.ico"))
        assertTrue(Tab.isRasterOrRemoteColorIcon("data:image/png;base64,iVBORw0KGgo="))
        assertTrue(Tab.isRasterOrRemoteColorIcon("icons/site.webp"))
        assertTrue(Tab.isRasterOrRemoteColorIcon("icons/photo.jpg"))
        assertTrue(Tab.isRasterOrRemoteColorIcon("icons/photo.jpeg"))
        assertTrue(Tab.isRasterOrRemoteColorIcon("icons/anim.gif"))
        assertTrue(Tab.isRasterOrRemoteColorIcon("icons/img.bmp"))

        assertFalse(Tab.isRasterOrRemoteColorIcon("https://example.com/logo.svg"))
        assertFalse(Tab.isRasterOrRemoteColorIcon("https://example.com/logo.svg?v=2"))
        assertFalse(Tab.isRasterOrRemoteColorIcon("data:image/svg+xml;base64,PHN2Zz48L3N2Zz4="))
        assertFalse(Tab.isRasterOrRemoteColorIcon("res://drawable/ic_home"))
        assertFalse(Tab.isRasterOrRemoteColorIcon(null))
        assertFalse(Tab.isRasterOrRemoteColorIcon(""))

        val factory = FakeTabTestFactory()
        GenericUI.initialize(factory)
        val host = TestMountHost(factory)

        // Remote raster favicon defaults tintImageToTheme to false and syncs tint before icon
        val autoFaviconTab = Tab(
            tabId = "auto_favicon",
            title = "Example",
            icon = "https://example.com/favicon.png"
        )
        assertFalse(autoFaviconTab.tintImageToTheme)
        assertEquals(ImageTintMode.NONE, autoFaviconTab.image?.tintMode)
        host.addChild(autoFaviconTab)
        autoFaviconTab.ensureNativeImpl()
        val autoNative = factory.createdTabs.last()
        assertFalse(autoNative.tintImageToThemeState)
        assertEquals("https://example.com/favicon.png", autoNative.iconState)
        val tintIdx = autoNative.callLog.indexOf("setTintImageToThemeNative:false")
        val iconIdx = autoNative.callLog.indexOf("setIconNative:https://example.com/favicon.png")
        assertTrue(tintIdx >= 0 && iconIdx > tintIdx, "setTintImageToThemeNative must precede setIconNative")

        // Image constructor with .ico defaults tintImageToTheme to false
        val icoImageTab = Tab(
            tabId = "ico_tab",
            title = "ICO",
            image = Image("https://example.org/favicon.ico")
        )
        assertFalse(icoImageTab.tintImageToTheme)

        // Explicit tintImageToTheme = true overrides smart default
        val forcedTintTab = Tab(
            tabId = "forced_tint",
            title = "Forced",
            icon = "https://example.com/favicon.png",
            tintImageToTheme = true
        )
        assertTrue(forcedTintTab.tintImageToTheme)
        assertEquals(ImageTintMode.MONOCOLOR_AUTO_THEME, forcedTintTab.image?.tintMode)

        // setImage(src) and icon = src without explicit tintToTheme follow smart default
        val dynamicTab = Tab(tabId = "dyn_icon", title = "Home")
        assertTrue(dynamicTab.tintImageToTheme)
        dynamicTab.setImage("https://kotlinlang.org/favicon.ico")
        assertFalse(dynamicTab.tintImageToTheme)
        assertEquals(ImageTintMode.NONE, dynamicTab.image?.tintMode)
        dynamicTab.icon = "res://drawable/ic_home.svg"
        assertTrue(dynamicTab.tintImageToTheme)
        dynamicTab.icon = "https://example.com/avatar.webp"
        assertFalse(dynamicTab.tintImageToTheme)
        dynamicTab.setImage("https://example.com/avatar.webp", tintToTheme = true)
        assertTrue(dynamicTab.tintImageToTheme)
    }
}
