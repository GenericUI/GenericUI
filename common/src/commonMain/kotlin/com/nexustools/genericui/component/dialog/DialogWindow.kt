package com.nexustools.genericui.component.dialog

import com.nexustools.genericui.component.action.Button
import com.nexustools.genericui.component.fragment.FragmentBundle
import com.nexustools.genericui.component.fragment.FragmentContainer
import com.nexustools.genericui.component.fragment.FragmentRegistry
import com.nexustools.genericui.component.fragment.IFragment
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.layout.HStack
import com.nexustools.genericui.component.layout.Spacer
import com.nexustools.genericui.component.shell.Application
import com.nexustools.genericui.component.shell.Window
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.enums.bootstrap.AlignItems
import com.nexustools.genericui.enums.bootstrap.BootstrapSize
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.enums.bootstrap.JustifyContent
import com.nexustools.genericui.enums.bootstrap.SpacingScale
import com.nexustools.genericui.enums.dialog.DialogPresentationStyle
import com.nexustools.genericui.enums.window.DisplayCutoutMode
import com.nexustools.genericui.enums.window.WindowState
import com.nexustools.genericui.exception.NativeUninitializedException
import com.nexustools.genericui.exception.UnsupportedBackendCapabilityException
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.nativeimpl.dialog.DialogWindowNativeImpl
import com.nexustools.genericui.nativeimpl.shell.WindowNativeImpl
import com.nexustools.genericui.window.IWindow
import com.nexustools.genericui.window.TitleBar
import com.nexustools.genericui.window.TitleCapabilities
import com.nexustools.genericui.window.WindowConfiguration
import kotlin.jvm.JvmName
import kotlin.reflect.KClass

/**
 * Custom modal or modeless dialog window capable of hosting arbitrary GenericUI component trees.
 */
open class DialogWindow(
    title: String = "",
    modal: Boolean = true,
    presentationStyle: DialogPresentationStyle = DialogPresentationStyle.MODAL_CENTERED
) : AbstractContainer(), IWindow {
    init {
        htmlTag = "genericui:dialogwindow"
    }

    override val windowId: String = "dialog-${nextDialogWindowId()}"

    private var currentOwnerWindow: Window? = null
    private var explicitFactory: NativeFactory? = null

    override val ownerWindow: IWindow?
        get() = currentOwnerWindow ?: (parent as? IWindow)

    override val childWindows: List<IWindow>
        get() = emptyList()

    override val titleCapabilities: TitleCapabilities = TitleCapabilities(
        isEmulated = true,
        supportsWindowIcon = false,
        supportsMinimizeButton = false,
        supportsMaximizeButton = false
    )

    @get:JvmName("getTitleProperty")
    @set:JvmName("setTitleProperty")
    var title: String = title
        set(value) {
            field = value
            typedNative<DialogWindowNativeImpl<*>>()?.setTitle(value)
        }

    override fun getTitle(): String = typedNative<DialogWindowNativeImpl<*>>()?.getTitle() ?: title

    override fun setTitle(title: String) {
        this.title = title
    }

    private var windowIconState: String? = null

    override fun getWindowIcon(): String? = windowIconState

    override fun setWindowIcon(iconPathOrSvg: String?) {
        windowIconState = iconPathOrSvg
    }

    private var titleBarInstructions: TitleBar? = null

    override fun getTitleBar(): TitleBar? = titleBarInstructions

    override fun setTitleBar(titleBar: TitleBar?) {
        val old = titleBarInstructions
        old?.onInstructionsChanged = null
        if (old !== titleBar) {
            old?.setHostEmbedsComponents(false)
        }
        titleBarInstructions = titleBar
        titleBar?.setHostEmbedsComponents(titleCapabilities.canEmbedComponentsInTitleBar)
        titleBar?.onInstructionsChanged = {
            syncTitleBarComponents()
        }
        syncTitleBarComponents()
    }

    private fun syncTitleBarComponents() {
        val tb = titleBarInstructions
        tb?.setHostEmbedsComponents(titleCapabilities.canEmbedComponentsInTitleBar)
        if (tb != null) {
            for (comp in tb.leadingComponents + tb.centerComponents + tb.trailingComponents) {
                if (comp.parent == null) {
                    comp.parent = this
                }
                if (comp.isVisible && resolveActiveFactory() != null) {
                    comp.ensureNativeImpl()
                }
            }
        }
    }

    private var windowBoundsState: Rect = Rect(120, 120, 560, 400)
    private var windowStateVal: WindowState = WindowState.NORMAL
    var fitToContent: Boolean = true
    private var hasExplicitSize: Boolean = false

    override fun setPreferredSize(size: Size?) {
        if (size != null) {
            hasExplicitSize = true
            fitToContent = false
            windowBoundsState = Rect(windowBoundsState.x, windowBoundsState.y, size.width, size.height)
            super.setPreferredSize(size)
        } else {
            hasExplicitSize = false
            fitToContent = true
            super.setPreferredSize(null)
        }
    }

    override fun getWindowSize(): Size = Size(windowBoundsState.width, windowBoundsState.height)

    override fun setWindowSize(size: Size) {
        hasExplicitSize = true
        fitToContent = false
        windowBoundsState = Rect(windowBoundsState.x, windowBoundsState.y, size.width, size.height)
        widthPx = size.width
        heightPx = size.height
        typedNative<DialogWindowNativeImpl<*>>()?.setBounds(windowBoundsState.x, windowBoundsState.y, size.width, size.height)
        onResizeListener?.invoke(
            WindowConfiguration(
                widthPx = size.width,
                heightPx = size.height,
                windowState = getWindowState(),
                isFullscreen = isFullscreen
            )
        )
    }

    override var windowBounds: Rect
        get() = windowBoundsState
        set(value) {
            hasExplicitSize = true
            fitToContent = false
            val sizeChanged = value.width != windowBoundsState.width || value.height != windowBoundsState.height
            windowBoundsState = value
            widthPx = value.width
            heightPx = value.height
            typedNative<DialogWindowNativeImpl<*>>()?.setBounds(value.x, value.y, value.width, value.height)
            if (sizeChanged) {
                onResizeListener?.invoke(
                    WindowConfiguration(
                        widthPx = value.width,
                        heightPx = value.height,
                        windowState = getWindowState(),
                        isFullscreen = isFullscreen
                    )
                )
            }
        }

    override fun getWindowState(): WindowState =
        if (isFullscreen) WindowState.FULLSCREEN else windowStateVal

    override fun setWindowState(state: WindowState) {
        windowStateVal = state
        if (state == WindowState.FULLSCREEN) {
            isFullscreen = true
        } else if (isFullscreen) {
            isFullscreen = false
        }
    }

    final override var isWindowShown: Boolean = false
        private set

    final override var isWindowFocused: Boolean = false
        private set

    internal override fun canAcceptFocusForIsFocused(): Boolean = true

    override var isClosable: Boolean = true
    var dismissOnBackdropClick: Boolean = true
    override val isMinimizable: Boolean = false
    override val isMaximizable: Boolean = false
    override var isResizable: Boolean = true

    override var isModal: Boolean = modal
        set(value) {
            field = value
            typedNative<DialogWindowNativeImpl<*>>()?.setModal(value)
        }

    override val isToolWindow: Boolean = false
    override val isAlwaysOnTop: Boolean = false

    var presentationStyle: DialogPresentationStyle = presentationStyle
        set(value) {
            field = value
            typedNative<DialogWindowNativeImpl<*>>()?.setPresentationStyle(value)
        }

    @get:JvmName("isFullscreenProperty")
    @set:JvmName("setFullscreenProperty")
    var isFullscreen: Boolean = false
        set(value) {
            field = value
            typedNative<DialogWindowNativeImpl<*>>()?.setFullscreen(value)
        }

    override fun isFullscreen(): Boolean = isFullscreen

    override fun setFullscreen(fullscreen: Boolean, cutoutMode: DisplayCutoutMode) {
        isFullscreen = fullscreen
    }

    var onCloseHandler: (() -> Unit)? = null

    val leftButtonBarGroup: HStack = HStack(SpacingScale.S2).apply {
        alignItems = AlignItems.CENTER
    }

    val rightButtonBarGroup: HStack = HStack(SpacingScale.S2).apply {
        alignItems = AlignItems.CENTER
    }

    val buttonBar: HStack = HStack(SpacingScale.S2).apply {
        addClass("modal-footer")
        alignItems = AlignItems.CENTER
        justifyContent = JustifyContent.BETWEEN
        isVisible = false
        addChild(leftButtonBarGroup)
        addChild(Spacer.fill())
        addChild(rightButtonBarGroup)
    }

    private var contentContainer: Container? = null
    private var _fragmentContainer: FragmentContainer? = null

    private var onResizeListener: ((WindowConfiguration) -> Unit)? = null
    private var onFocusListener: ((Boolean) -> Unit)? = null
    private var onCloseRequestedListener: (() -> Boolean)? = null

    private fun ensureButtonBarMounted() {
        if (buttonBar.parent !== this) {
            super.addChild(buttonBar, -1)
            if (isWindowShown && (resolveActiveFactory() ?: GenericUI.activeFactory)?.isStyleSheetEngineEnabled != false) {
                Application.current?.getStyleSheets()?.forEach { sheet ->
                    sheet.styleSheet.applyToTree(buttonBar)
                }
            }
        }
        buttonBar.isVisible = true
    }

    fun addLeftButton(component: AbstractComponent): AbstractComponent {
        ensureButtonBarMounted()
        if (leftButtonBarGroup.children.isNotEmpty()) {
            leftButtonBarGroup.addChild(Spacer.horizontal(8))
        }
        leftButtonBarGroup.addChild(component)
        return component
    }

    fun addLeftButton(
        text: String,
        type: BootstrapType = BootstrapType.SECONDARY,
        size: BootstrapSize = BootstrapSize.SM,
        outline: Boolean = false,
        onClick: () -> Unit
    ): Button {
        val button = Button(text, type, size, outline = outline).apply {
            this.onClick = onClick
        }
        addLeftButton(button)
        return button
    }

    fun addRightButton(component: AbstractComponent): AbstractComponent {
        ensureButtonBarMounted()
        if (rightButtonBarGroup.children.isNotEmpty()) {
            rightButtonBarGroup.addChild(Spacer.horizontal(8))
        }
        rightButtonBarGroup.addChild(component)
        return component
    }

    fun addRightButton(
        text: String,
        type: BootstrapType = BootstrapType.SECONDARY,
        size: BootstrapSize = BootstrapSize.SM,
        outline: Boolean = false,
        onClick: () -> Unit
    ): Button {
        val button = Button(text, type, size, outline = outline).apply {
            this.onClick = onClick
        }
        addRightButton(button)
        return button
    }

    fun addCloseButton(
        text: String = "Close",
        type: BootstrapType = BootstrapType.SECONDARY,
        size: BootstrapSize = BootstrapSize.SM,
        outline: Boolean = false,
        onClose: (() -> Unit)? = null
    ): Button = addRightButton(text, type, size, outline = outline) {
        onClose?.invoke()
        close()
    }

    fun clearButtons() {
        leftButtonBarGroup.clearChildren()
        rightButtonBarGroup.clearChildren()
        buttonBar.isVisible = false
    }

    override fun resolveActiveFactory(): NativeFactory? =
        super.resolveActiveFactory()
            ?: explicitFactory
            ?: currentOwnerWindow?.resolveActiveFactory()
            ?: (if (isWindowShown && isVisible) GenericUI.activeFactory else null)

    private fun resolveCapabilityFactory(operationName: String): NativeFactory {
        val targetName = if (this is com.nexustools.genericui.component.overlay.Modal) "Modal" else "DialogWindow"
        return explicitFactory
            ?: currentOwnerWindow?.let { it.ExplicitFactory ?: it.resolveActiveFactory() }
            ?: resolveActiveFactory()
            ?: GenericUI.activeFactory
            ?: throw NativeUninitializedException(targetName, operationName)
    }

    override val supportsMultipleWindows: Boolean
        get() = resolveCapabilityFactory("supportsMultipleWindows").supportsMultipleWindows

    override val supportsDialogWindows: Boolean
        get() = resolveCapabilityFactory("supportsDialogWindows").supportsDialogWindows

    override fun canOpenAdditionalWindow(fragmentClass: KClass<out IFragment>): Boolean {
        val factory = resolveCapabilityFactory("canOpenAdditionalWindow")
        return factory.supportsMultipleWindows &&
            !FragmentRegistry.isDialogFragment(fragmentClass) &&
            FragmentRegistry.supportsMultipleWindows(fragmentClass)
    }

    /** Reified convenience for [canOpenAdditionalWindow]. */
    inline fun <reified T : IFragment> canOpenAdditionalWindow(): Boolean =
        canOpenAdditionalWindow(T::class)

    /** Reified convenience for [startFragment]. */
    inline fun <reified T : IFragment> startFragment(
        launchArgs: FragmentBundle? = null,
        openAdditionalWindow: Boolean = false,
        addToBackStack: Boolean = true,
        clearTask: Boolean = false
    ): T = startFragment(
        fragmentClass = T::class,
        launchArgs = launchArgs,
        openAdditionalWindow = openAdditionalWindow,
        addToBackStack = addToBackStack,
        clearTask = clearTask
    )

    /** Reified convenience for [startFragment] with key-value pair arguments. */
    inline fun <reified T : IFragment> startFragment(
        firstArg: Pair<String, Any?>,
        vararg extraArgs: Pair<String, Any?>,
        openAdditionalWindow: Boolean = false,
        addToBackStack: Boolean = true,
        clearTask: Boolean = false
    ): T = startFragment(
        fragmentClass = T::class,
        launchArgs = FragmentBundle.of(firstArg, *extraArgs),
        openAdditionalWindow = openAdditionalWindow,
        addToBackStack = addToBackStack,
        clearTask = clearTask
    )

    private var isHandlingClose: Boolean = false
    private var nativeCloseDispatched: Boolean = false

    fun open(ownerWindow: Window? = null) {
        val resolvedOwner = ownerWindow
            ?: currentOwnerWindow
            ?: Application.current?.mainWindow?.takeIf { mainWin ->
                mainWin.isWindowShown &&
                    mainWin.isVisible &&
                    (mainWin.fragmentContainer?.currentFragment != null || mainWin.getContent() != null)
            }
        val targetName = if (this is com.nexustools.genericui.component.overlay.Modal) "Modal" else "DialogWindow"
        val resolvedFactory = resolvedOwner?.resolveActiveFactory()
            ?: resolvedOwner?.ExplicitFactory
            ?: explicitFactory
            ?: super.resolveActiveFactory()
            ?: GenericUI.activeFactory
            ?: throw NativeUninitializedException(targetName, "open")
        if (!resolvedFactory.supportsDialogWindows) {
            throw UnsupportedBackendCapabilityException("dialogWindows", resolvedFactory.backendTarget.displayName)
        }
        val existingParent = parent
        if (existingParent != null && existingParent !is Window) {
            (existingParent as? AbstractContainer)?.removeChild(this)
        }
        if (resolvedOwner != null) {
            if (currentOwnerWindow != null && currentOwnerWindow !== resolvedOwner) {
                currentOwnerWindow?.unregisterAttachedDialogWindow(this)
            }
            currentOwnerWindow = resolvedOwner
            resolvedOwner.registerAttachedDialogWindow(this)
            if (parent == null) {
                this.parent = resolvedOwner
            }
        }
        if (explicitFactory == null && resolvedOwner == null && GenericUI.activeFactory == null) {
            explicitFactory = resolvedFactory
        }
        nativeCloseDispatched = false
        isVisible = true
        isWindowShown = true
        isWindowFocused = true
        if (resolvedFactory.isStyleSheetEngineEnabled) {
            Application.current?.getStyleSheets()?.forEach { sheet ->
                sheet.styleSheet.applyToTree(this)
            }
        }
        val impl = ensureNativeImpl() as? DialogWindowNativeImpl<*>
            ?: throw NativeUninitializedException(targetName, "open")
        syncTitleBarComponents()
        val ownerNative = if (resolvedOwner?.isWindowShown == true) {
            resolvedOwner.ensureNativeImpl() as? WindowNativeImpl<*>
        } else {
            null
        }
        if (hasExplicitSize) {
            impl.setBounds(windowBoundsState.x, windowBoundsState.y, windowBoundsState.width, windowBoundsState.height)
            impl.setPreferredSize(Size(windowBoundsState.width, windowBoundsState.height))
        } else {
            impl.setPreferredSize(null)
        }
        impl.showDialog(ownerNative)
        fragmentContainer?.ensureActiveFragmentStartedAndResumed()
        onFocusListener?.invoke(true)
    }

    override fun addChild(child: AbstractComponent, index: Int) {
        val resolvedIndex = if (child !== buttonBar && index < 0 && children.contains(buttonBar)) {
            children.indexOf(buttonBar)
        } else {
            index
        }
        super.addChild(child, resolvedIndex)
        if (isWindowShown && (resolveActiveFactory() ?: GenericUI.activeFactory)?.isStyleSheetEngineEnabled != false) {
            Application.current?.getStyleSheets()?.forEach { sheet ->
                sheet.styleSheet.applyToTree(child)
            }
        }
    }

    override fun show(factory: NativeFactory?) {
        if (factory != null) {
            explicitFactory = factory
        }
        open(currentOwnerWindow)
    }

    override fun hide() {
        val impl = typedNative<DialogWindowNativeImpl<*>>()
        if (impl != null && !nativeCloseDispatched) {
            nativeCloseDispatched = true
            impl.closeDialog()
        }
        currentOwnerWindow?.unregisterAttachedDialogWindow(this)
        isVisible = false
        isWindowShown = false
        if (isWindowFocused) {
            isWindowFocused = false
            onFocusListener?.invoke(false)
        }
    }

    override fun close() {
        if (isHandlingClose) return
        if (onCloseRequestedListener?.invoke() == false) return
        val impl = typedNative<DialogWindowNativeImpl<*>>()
        if (impl != null && !nativeCloseDispatched) {
            nativeCloseDispatched = true
            impl.closeDialog()
        }
        finalizeDialogClose()
    }

    private fun finalizeDialogClose() {
        if (isHandlingClose) return
        if (!isWindowShown && !isVisible && fragmentContainer?.currentFragment == null && currentOwnerWindow == null) {
            return
        }
        isHandlingClose = true
        try {
            val impl = typedNative<DialogWindowNativeImpl<*>>()
            if (impl != null && !nativeCloseDispatched) {
                nativeCloseDispatched = true
                impl.closeDialog()
            }
            currentOwnerWindow?.unregisterAttachedDialogWindow(this)
            currentOwnerWindow = null
            if (parent is Window) {
                parent = null
            }
            isVisible = false
            isWindowShown = false
            isFocused = false
            if (isWindowFocused) {
                isWindowFocused = false
                onFocusListener?.invoke(false)
            }
            titleBarInstructions?.setHostEmbedsComponents(false)
            fragmentContainer?.clearCurrentFragment()
            onCloseHandler?.invoke()
        } finally {
            isHandlingClose = false
        }
        Application.current?.checkExitIfNoFragmentsOpen()
    }

    override fun minimize() {
        // Dialog windows are not minimizable
    }

    override fun maximize() {
        setWindowState(WindowState.MAXIMIZED)
    }

    override fun restore() {
        setWindowState(WindowState.NORMAL)
    }

    override fun focus() {
        val impl = typedNative<DialogWindowNativeImpl<*>>()
        val targetName = if (this is com.nexustools.genericui.component.overlay.Modal) "Modal" else "DialogWindow"
        if (!isWindowShown || impl == null) {
            throw NativeUninitializedException(targetName, "focus")
        }
        isFocused = true
        isWindowFocused = true
        val ownerNative = if (currentOwnerWindow?.isWindowShown == true) {
            currentOwnerWindow?.ensureNativeImpl() as? WindowNativeImpl<*>
        } else {
            null
        }
        impl.showDialog(ownerNative)
        onFocusListener?.invoke(true)
    }

    override fun bringToFront() {
        val impl = typedNative<DialogWindowNativeImpl<*>>()
        val targetName = if (this is com.nexustools.genericui.component.overlay.Modal) "Modal" else "DialogWindow"
        if (!isWindowShown || impl == null) {
            throw NativeUninitializedException(targetName, "bringToFront")
        }
        isFocused = true
        isWindowFocused = true
        val ownerNative = if (currentOwnerWindow?.isWindowShown == true) {
            currentOwnerWindow?.ensureNativeImpl() as? WindowNativeImpl<*>
        } else {
            null
        }
        impl.showDialog(ownerNative)
        onFocusListener?.invoke(true)
    }

    override fun setContent(container: Container) {
        contentContainer?.let { removeChild(it) }
        contentContainer = container
        if (container is FragmentContainer) {
            container.hostingIWindow = this
            _fragmentContainer = container
        }
        addChild(container)
    }

    override fun getContent(): Container? =
        contentContainer ?: (children.firstOrNull { it !== buttonBar } as? Container)

    override val fragmentContainer: FragmentContainer?
        get() = (contentContainer as? FragmentContainer)
            ?: _fragmentContainer
            ?: (children.firstOrNull { it is FragmentContainer } as? FragmentContainer)

    override fun getOrCreateFragmentContainer(): FragmentContainer {
        val existing = fragmentContainer
        if (existing != null) return existing
        val created = FragmentContainer(null, null).also {
            it.hostingIWindow = this
        }
        _fragmentContainer = created
        setContent(created)
        return created
    }

    override fun setOnWindowResize(listener: ((WindowConfiguration) -> Unit)?) {
        onResizeListener = listener
    }

    override fun setOnWindowFocusChanged(listener: ((Boolean) -> Unit)?) {
        onFocusListener = listener
    }

    override fun setOnWindowCloseRequested(listener: (() -> Boolean)?) {
        onCloseRequestedListener = listener
    }

    protected override fun createNativeImpl(factory: NativeFactory): DialogWindowNativeImpl<*> =
        factory.createDialogWindow()

    override fun syncStateToNative() {
        super.syncStateToNative()
        val impl = typedNative<DialogWindowNativeImpl<*>>() ?: return
        impl.setTitle(title)
        impl.setModal(isModal)
        impl.setPresentationStyle(presentationStyle)
        impl.setFullscreen(isFullscreen)
        if (hasExplicitSize) {
            impl.setBounds(windowBoundsState.x, windowBoundsState.y, windowBoundsState.width, windowBoundsState.height)
            impl.setPreferredSize(Size(windowBoundsState.width, windowBoundsState.height))
        } else {
            impl.setPreferredSize(null)
        }
        impl.setOnDialogClosed {
            finalizeDialogClose()
        }
        syncTitleBarComponents()
    }

    companion object {
        private var nextDialogWinSeq: Long = 0L

        internal fun nextDialogWindowId(): Long = ++nextDialogWinSeq
    }
}
