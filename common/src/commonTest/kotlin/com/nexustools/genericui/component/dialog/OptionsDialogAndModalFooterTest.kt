package com.nexustools.genericui.component.dialog

import com.nexustools.genericui.component.action.Button
import com.nexustools.genericui.component.layout.Spacer
import com.nexustools.genericui.component.overlay.Modal
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
import com.nexustools.genericui.enums.dialog.DialogPresentationStyle
import com.nexustools.genericui.enums.system.BackendTarget
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
import com.nexustools.genericui.style.css.ComputedStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OptionsDialogAndModalFooterTest {

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

    private class FakeButtonNativeImpl(prefix: String) : StubNativeImpl(prefix), ButtonNativeImpl<Any> {
        private var text: String = ""
        private var icon: String? = null
        override fun setText(text: String) { this.text = text }
        override fun getText(): String = text
        override fun setIcon(icon: String?) { this.icon = icon }
        override fun getIcon(): String? = icon
    }

    private class FakeDialogWindowNativeImpl : StubNativeImpl("dialog"), DialogWindowNativeImpl<Any> {
        private var title: String = ""
        private var presentationStyle: DialogPresentationStyle = DialogPresentationStyle.MODAL_CENTERED
        private var onClosed: (() -> Unit)? = null
        override fun addChildNative(childImpl: NativeImpl<*>, index: Int) {}
        override fun removeChildNative(childImpl: NativeImpl<*>) {}
        override fun clearChildrenNative() {}
        override fun setTitle(title: String) { this.title = title }
        override fun getTitle(): String = title
        override fun setPresentationStyle(style: DialogPresentationStyle) { presentationStyle = style }
        override fun getPresentationStyle(): DialogPresentationStyle = presentationStyle
        override fun setModal(modal: Boolean) {}
        override fun setFullscreen(fullscreen: Boolean) {}
        override fun showDialog(ownerWindow: WindowNativeImpl<*>?) {}
        override fun closeDialog() { onClosed?.invoke() }
        override fun setOnDialogClosed(listener: (() -> Unit)?) { onClosed = listener }
    }

    private class FakeDialogTestFactory : NativeFactory {
        private fun unsupported(): Nothing = throw UnsupportedOperationException("Unused in OptionsDialogAndModalFooterTest")
        override val backendTarget: BackendTarget = BackendTarget.Swing
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
        override fun createButton(classPrefix: String): ButtonNativeImpl<*> = FakeButtonNativeImpl(classPrefix)
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
        override fun createDialogWindow(): DialogWindowNativeImpl<*> = FakeDialogWindowNativeImpl()
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

    @Test
    fun testDialogWindowAndModalFooterButtonBar() {
        val prevFactory = GenericUI.activeFactory
        try {
            GenericUI.initialize(FakeDialogTestFactory())
            val modal = Modal(title = "Codec Diagnostics")
            assertFalse(modal.buttonBar.isVisible)
            assertTrue(modal.buttonBar.getClasses().contains("modal-footer"))

            val leftBtn1 = modal.addLeftButton("Refresh", type = BootstrapType.SECONDARY) {}
            val leftBtn2 = modal.addLeftButton("Export", type = BootstrapType.INFO) {}
            assertTrue(modal.buttonBar.isVisible)
            assertTrue(modal.children.contains(modal.buttonBar))

            // Verify 8px horizontal spacer is automatically inserted between multiple left buttons
            assertEquals(3, modal.leftButtonBarGroup.children.size)
            assertEquals(leftBtn1, modal.leftButtonBarGroup.children[0])
            val leftSpacer = modal.leftButtonBarGroup.children[1] as? Spacer
            assertTrue(leftSpacer != null && !leftSpacer.isFill && leftSpacer.spacerWidthPx == 8)
            assertEquals(leftBtn2, modal.leftButtonBarGroup.children[2])

            var closeCallbackInvoked = false
            val cancelBtn = modal.addRightButton("Cancel", type = BootstrapType.SECONDARY) {}
            val closeBtn = modal.addCloseButton("Close") { closeCallbackInvoked = true }

            // Verify 8px horizontal spacer is automatically inserted between multiple right buttons
            assertEquals(3, modal.rightButtonBarGroup.children.size)
            assertEquals(cancelBtn, modal.rightButtonBarGroup.children[0])
            val rightSpacer = modal.rightButtonBarGroup.children[1] as? Spacer
            assertTrue(rightSpacer != null && !rightSpacer.isFill && rightSpacer.spacerWidthPx == 8)
            assertEquals(closeBtn, modal.rightButtonBarGroup.children[2])

            // Verify fill spacer separates leftButtonBarGroup and rightButtonBarGroup
            assertEquals(3, modal.buttonBar.children.size)
            assertEquals(modal.leftButtonBarGroup, modal.buttonBar.children[0])
            val middleFillSpacer = modal.buttonBar.children[1] as? Spacer
            assertTrue(middleFillSpacer != null && middleFillSpacer.isFill)
            assertEquals(modal.rightButtonBarGroup, modal.buttonBar.children[2])

            modal.open()
            assertTrue(modal.isWindowShown)
            closeBtn.onClick?.invoke()
            assertTrue(closeCallbackInvoked)
            assertFalse(modal.isWindowShown)

            modal.clearButtons()
            assertFalse(modal.buttonBar.isVisible)
            assertTrue(modal.leftButtonBarGroup.children.isEmpty())
            assertTrue(modal.rightButtonBarGroup.children.isEmpty())
        } finally {
            GenericUI.resetForTests()
            GenericUI.activeFactory = prevFactory
        }
    }

    @Test
    fun testOptionsDialogSingleTitleSpacedButtonsAndFooterCancel() {
        val prevFactory = GenericUI.activeFactory
        try {
            GenericUI.initialize(FakeDialogTestFactory())
            var selectedOptionId: String? = null
            val dialog = OptionsDialog(
                title = "Show Options",
                options = listOf(
                    OptionDialogItem(
                        text = "Play Next Episode",
                        type = BootstrapType.SUCCESS,
                        onClick = { selectedOptionId = "play_next" }
                    ),
                    OptionDialogItem(
                        text = "Browse Seasons & Episodes",
                        type = BootstrapType.PRIMARY,
                        onClick = { selectedOptionId = "browse_seasons" }
                    ),
                    OptionDialogItem(
                        text = "Disable Show",
                        type = BootstrapType.DANGER,
                        onClick = { selectedOptionId = "disable_show" }
                    )
                ),
                defaultBottomButtonText = "Cancel"
            )

            assertEquals("Show Options", dialog.title)

            // Verify body contains bodyStack (no duplicate Heading) and footer buttonBar
            assertEquals(2, dialog.children.size)
            assertEquals(dialog.bodyStack, dialog.children[0])
            assertEquals(dialog.buttonBar, dialog.children[1])

            // 3 option buttons + 2 vertical 8px spacers between them = 5 children in optionsStack
            assertEquals(5, dialog.optionsStack.children.size)
            val firstButton = dialog.optionsStack.children[0] as Button
            val firstSpacer = dialog.optionsStack.children[1] as Spacer
            val secondButton = dialog.optionsStack.children[2] as Button
            val secondSpacer = dialog.optionsStack.children[3] as Spacer
            val thirdButton = dialog.optionsStack.children[4] as Button

            assertEquals("Play Next Episode", firstButton.text)
            assertEquals(BootstrapType.SUCCESS, firstButton.type)
            assertEquals(8, firstSpacer.spacerHeightPx)
            assertEquals("Browse Seasons & Episodes", secondButton.text)
            assertEquals(BootstrapType.PRIMARY, secondButton.type)
            assertEquals(8, secondSpacer.spacerHeightPx)
            assertEquals("Disable Show", thirdButton.text)
            assertEquals(BootstrapType.DANGER, thirdButton.type)

            // Verify Cancel button is mounted inside the modal-footer rightButtonBarGroup
            assertTrue(dialog.buttonBar.isVisible)
            assertEquals(1, dialog.rightButtonBarGroup.children.size)
            val footerCancelBtn = dialog.rightButtonBarGroup.children[0] as Button
            assertEquals("Cancel", footerCancelBtn.text)

            dialog.open()
            assertTrue(dialog.isWindowShown)
            secondButton.onClick?.invoke()
            assertEquals("browse_seasons", selectedOptionId)
            assertFalse(dialog.isWindowShown)
        } finally {
            GenericUI.resetForTests()
            GenericUI.activeFactory = prevFactory
        }
    }
}
