package com.nexustools.genericui.component.shell

import com.nexustools.genericui.component.action.Button
import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.fragment.FragmentContainer
import com.nexustools.genericui.component.fragment.IFragment
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.layout.HStack
import com.nexustools.genericui.component.layout.ScrollView
import com.nexustools.genericui.component.typography.Badge
import com.nexustools.genericui.component.typography.Label
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.enums.bootstrap.AlignItems
import com.nexustools.genericui.enums.bootstrap.BootstrapSize
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.enums.bootstrap.DisplayMode
import com.nexustools.genericui.enums.bootstrap.FlexDirection
import com.nexustools.genericui.enums.bootstrap.JustifyContent
import com.nexustools.genericui.enums.bootstrap.PositionMode
import com.nexustools.genericui.enums.bootstrap.SpacingScale
import com.nexustools.genericui.enums.window.DisplayCutoutMode
import com.nexustools.genericui.enums.window.WindowState
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.window.IWindow
import com.nexustools.genericui.window.TitleBar
import com.nexustools.genericui.window.TitleCapabilities
import com.nexustools.genericui.window.WindowConfiguration
import kotlin.jvm.JvmName

/**
 * Multi-Document Interface (MDI) inner sub-window (`<genericui:mdiwindow class="mdi-window">`)
 * managed inside an [MdiContainer].
 *
 * Supports interactive drag-to-move via [headerBar], minimize/maximize/restore/close actions,
 * z-index focus activation, and hosting either arbitrary [AbstractComponent] trees or a full-lifecycle
 * [Fragment] via [setFragment].
 */
open class MdiWindow(
    override val windowId: String,
    title: String = "Window",
    var category: String = "",
    initialBounds: Rect = Rect(24, 24, 480, 360),
    icon: String? = null
) : Container(tagName = "genericui:mdiwindow", classPrefix = "mdi-window"), IWindow {

    var parentMdiContainer: MdiContainer? = null
        internal set

    override val ownerWindow: IWindow?
        get() {
            var curr: AbstractComponent? = parentMdiContainer ?: parent
            while (curr != null) {
                if (curr is IWindow && curr !== this) return curr
                curr = curr.parent
            }
            return null
        }

    override val childWindows: List<IWindow>
        get() = emptyList()

    override val titleCapabilities: TitleCapabilities = TitleCapabilities(
        isEmulated = true
    )

    val leadingSlot: HStack = HStack(gap = SpacingScale.S1).apply {
        alignItems = AlignItems.CENTER
    }
    val centerSlot: HStack = HStack(gap = SpacingScale.S1).apply {
        alignItems = AlignItems.CENTER
    }
    val trailingSlot: HStack = HStack(gap = SpacingScale.S1).apply {
        alignItems = AlignItems.CENTER
    }

    val titleLabel: Label = Label(title, tagName = "span", classPrefix = "mdi-window-title fw-bold")
    val stateBadge: Badge = Badge(
        text = if (category.isNotEmpty()) category else "NORMAL",
        type = BootstrapType.SECONDARY,
        pill = true
    )

    val minimizeButton: Button = Button("—", BootstrapType.SECONDARY, BootstrapSize.SM, outline = true).apply {
        id = "mdi-win-min-$windowId"
        addClass("mdi-window-btn mdi-btn-minimize")
    }
    val maximizeButton: Button = Button("□", BootstrapType.SECONDARY, BootstrapSize.SM, outline = true).apply {
        id = "mdi-win-max-$windowId"
        addClass("mdi-window-btn mdi-btn-maximize")
    }
    val closeButton: Button = Button("✕", BootstrapType.DANGER, BootstrapSize.SM, outline = true).apply {
        id = "mdi-win-close-$windowId"
        addClass("mdi-window-btn mdi-btn-close")
    }

    val headerBar: HStack = HStack(gap = SpacingScale.S2).apply {
        addClass("mdi-window-header")
        alignItems = AlignItems.CENTER
        justifyContent = JustifyContent.BETWEEN
    }

    val bodyContainer: Container = Container("div", "mdi-window-body")
    val contentContainer: Container
        get() = bodyContainer
    val contentScroll: ScrollView = ScrollView().apply {
        addClass("mdi-window-scroll")
        addChild(bodyContainer)
    }

    private var contentComponent: AbstractComponent? = null
    private var _fragmentContainer: FragmentContainer? = null
    private var titleBarInstructions: TitleBar? = null
    private var fullscreenState: Boolean = false
    private var shownState: Boolean = true

    private var onResizeListener: ((WindowConfiguration) -> Unit)? = null
    private var onFocusListener: ((Boolean) -> Unit)? = null
    private var onCloseRequestedListener: (() -> Boolean)? = null

    @get:JvmName("getTitleProperty")
    @set:JvmName("setTitleProperty")
    var title: String = title
        set(value) {
            field = value
            titleLabel.text = value
            parentMdiContainer?.syncTaskbarButton(this)
        }

    override fun getTitle(): String = title

    override fun setTitle(title: String) {
        this.title = title
    }

    var icon: String? = icon
        set(value) {
            field = value
            parentMdiContainer?.syncTaskbarButton(this)
        }

    override fun getWindowIcon(): String? = icon

    override fun setWindowIcon(iconPathOrSvg: String?) {
        icon = iconPathOrSvg
    }

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
            syncTitleBarIntoHeader()
        }
        syncTitleBarIntoHeader()
    }

    private fun syncTitleBarIntoHeader() {
        val tb = titleBarInstructions
        tb?.setHostEmbedsComponents(titleCapabilities.canEmbedComponentsInTitleBar)
        leadingSlot.clearChildren()
        centerSlot.clearChildren()
        trailingSlot.clearChildren()
        if (tb != null) {
            for (comp in tb.leadingComponents) {
                leadingSlot.addChild(comp)
            }
            for (comp in tb.centerComponents) {
                centerSlot.addChild(comp)
            }
            for (comp in tb.trailingComponents) {
                trailingSlot.addChild(comp)
            }
            titleLabel.isVisible = tb.showWindowTitle
            minimizeButton.isVisible = isMinimizable && tb.showMinimizeButton
            maximizeButton.isVisible = isMaximizable && tb.showMaximizeButton
            closeButton.isVisible = isClosable && tb.showCloseButton
            if (tb.heightPx != null) {
                headerBar.heightPx = tb.heightPx
            }
        } else {
            titleLabel.isVisible = true
            minimizeButton.isVisible = isMinimizable
            maximizeButton.isVisible = isMaximizable
            closeButton.isVisible = isClosable
        }
    }

    var normalBounds: Rect = initialBounds

    override var windowBounds: Rect = initialBounds
        set(value) {
            val prev = field
            field = value
            leftOffset = "${value.x}px"
            topOffset = "${value.y}px"
            widthPx = value.width
            heightPx = value.height
            setStyleProperty("width", "${value.width}px")
            setStyleProperty("height", "${value.height}px")
            if (prev.width != value.width || prev.height != value.height) {
                onResizeListener?.invoke(
                    WindowConfiguration(
                        widthPx = value.width,
                        heightPx = value.height,
                        windowState = getWindowState(),
                        isFullscreen = fullscreenState
                    )
                )
            }
        }

    override fun getWindowSize(): Size = Size(windowBounds.width, windowBounds.height)

    override fun setWindowSize(size: Size) {
        windowBounds = Rect(windowBounds.x, windowBounds.y, size.width, size.height)
    }

    var windowState: MdiWindowState = MdiWindowState.NORMAL
        internal set(value) {
            field = value
            stateBadge.text = if (category.isNotEmpty() && value == MdiWindowState.NORMAL) {
                category
            } else {
                value.name
            }
            when (value) {
                MdiWindowState.MINIMIZED -> {
                    addClass("minimized")
                    removeClass("maximized")
                }
                MdiWindowState.MAXIMIZED -> {
                    addClass("maximized")
                    removeClass("minimized")
                }
                MdiWindowState.NORMAL -> {
                    removeClass("minimized")
                    removeClass("maximized")
                }
            }
        }

    override fun getWindowState(): WindowState = when {
        fullscreenState -> WindowState.FULLSCREEN
        windowState == MdiWindowState.MINIMIZED -> WindowState.MINIMIZED
        windowState == MdiWindowState.MAXIMIZED -> WindowState.MAXIMIZED
        else -> WindowState.NORMAL
    }

    override fun setWindowState(state: WindowState) {
        when (state) {
            WindowState.MINIMIZED -> {
                fullscreenState = false
                minimize()
            }
            WindowState.MAXIMIZED -> {
                fullscreenState = false
                maximize()
            }
            WindowState.FULLSCREEN -> {
                setFullscreen(true, DisplayCutoutMode.SHORT_EDGES)
            }
            WindowState.NORMAL, WindowState.PICTURE_IN_PICTURE -> {
                fullscreenState = false
                restore()
            }
        }
    }

    override val isWindowShown: Boolean
        get() = shownState && isVisible

    var isActiveWindow: Boolean = false
        internal set(value) {
            val changed = (field != value)
            field = value
            if (value) {
                addClass("active")
                stateBadge.type = BootstrapType.PRIMARY
            } else {
                removeClass("active")
                stateBadge.type = BootstrapType.SECONDARY
            }
            if (changed) {
                onFocusListener?.invoke(value)
            }
        }

    override val isWindowFocused: Boolean
        get() = isActiveWindow

    override var isClosable: Boolean = true
        set(value) {
            field = value
            closeButton.isVisible = value && (titleBarInstructions?.showCloseButton ?: true)
        }

    override var isMinimizable: Boolean = true
        set(value) {
            field = value
            minimizeButton.isVisible = value && (titleBarInstructions?.showMinimizeButton ?: true)
        }

    override var isMaximizable: Boolean = true
        set(value) {
            field = value
            maximizeButton.isVisible = value && (titleBarInstructions?.showMaximizeButton ?: true)
        }

    var isDraggable: Boolean = true
    override var isResizable: Boolean = true

    override val isModal: Boolean = false
    override val isToolWindow: Boolean = false
    override val isAlwaysOnTop: Boolean = false

    override fun isFullscreen(): Boolean = fullscreenState

    override fun setFullscreen(fullscreen: Boolean, cutoutMode: DisplayCutoutMode) {
        fullscreenState = fullscreen
        if (fullscreen) {
            maximize()
        } else if (windowState == MdiWindowState.MAXIMIZED) {
            restore()
        }
    }

    var hostedFragment: Fragment? = null
        private set

    private var dragActive: Boolean = false
    private var dragOffsetX: Float = 0f
    private var dragOffsetY: Float = 0f

    var onActivated: ((MdiWindow) -> Unit)? = null
    var onStateChanged: ((MdiWindow, MdiWindowState) -> Unit)? = null
    var onClosed: ((MdiWindow) -> Unit)? = null

    init {
        htmlTag = "genericui:mdiwindow"
        id = windowId
        positionMode = PositionMode.ABSOLUTE
        displayMode = DisplayMode.FLEX
        flexDirection = FlexDirection.COLUMN
        windowBounds = initialBounds

        val titleGroup = HStack(gap = SpacingScale.S2).apply {
            alignItems = AlignItems.CENTER
            addChild(leadingSlot)
            addChild(titleLabel)
            addChild(stateBadge)
        }
        val controlsGroup = HStack(gap = SpacingScale.S1).apply {
            alignItems = AlignItems.CENTER
            addChild(trailingSlot)
            addChild(minimizeButton)
            addChild(maximizeButton)
            addChild(closeButton)
        }
        headerBar.addChild(titleGroup)
        headerBar.addChild(centerSlot)
        headerBar.addChild(controlsGroup)

        addChild(headerBar)
        addChild(contentScroll)

        setOnPointerDown {
            activate()
        }

        headerBar.setOnPointerDown { ev ->
            activate()
            if (isDraggable && windowState == MdiWindowState.NORMAL) {
                dragActive = true
                dragOffsetX = ev.x - windowBounds.x
                dragOffsetY = ev.y - windowBounds.y
            }
        }

        headerBar.setOnPointerMove { ev ->
            if (dragActive && isDraggable && windowState == MdiWindowState.NORMAL) {
                val nextX = (ev.x - dragOffsetX).toInt().coerceAtLeast(0)
                val nextY = (ev.y - dragOffsetY).toInt().coerceAtLeast(0)
                val updated = Rect(nextX, nextY, windowBounds.width, windowBounds.height)
                windowBounds = updated
                normalBounds = updated
            }
        }

        headerBar.setOnPointerUp {
            dragActive = false
        }

        headerBar.setOnDoubleClick {
            if (isMaximizable) {
                if (windowState == MdiWindowState.MAXIMIZED) {
                    restore()
                } else {
                    maximize()
                }
            }
        }

        minimizeButton.setOnClick { minimize() }
        maximizeButton.setOnClick {
            if (windowState == MdiWindowState.MAXIMIZED) {
                restore()
            } else {
                maximize()
            }
        }
        closeButton.setOnClick { close() }
    }

    fun addContent(component: AbstractComponent): MdiWindow = apply {
        if (contentComponent == null) {
            contentComponent = component
        }
        bodyContainer.addChild(component)
    }

    fun setContent(component: AbstractComponent): MdiWindow = apply {
        contentComponent = component
        if (component is FragmentContainer) {
            component.hostingIWindow = this
            _fragmentContainer = component
        }
        bodyContainer.clearChildren()
        bodyContainer.addChild(component)
    }

    override fun setContent(container: Container) {
        setContent(container as AbstractComponent)
    }

    override fun getContent(): Container? = contentComponent as? Container

    override val fragmentContainer: FragmentContainer?
        get() = (contentComponent as? FragmentContainer) ?: _fragmentContainer

    override fun getOrCreateFragmentContainer(): FragmentContainer {
        val existing = fragmentContainer
        if (existing != null) return existing
        val created = FragmentContainer(null, ownerWindow as? Window).also {
            it.hostingIWindow = this
        }
        _fragmentContainer = created
        setContent(created as AbstractComponent)
        return created
    }

    @Suppress("USELESS_CAST")
    override val activeFragment: IFragment?
        get() = (fragmentContainer?.currentFragment ?: hostedFragment) as? IFragment

    fun setFragment(fragment: Fragment): MdiWindow = apply {
        hostedFragment?.performDestroy()
        hostedFragment = fragment
        contentComponent = fragment
        bodyContainer.clearChildren()
        fragment.performCreate(null)
        fragment.performStart()
        fragment.performResume()
        bodyContainer.addChild(fragment)
    }

    override fun show(factory: NativeFactory?) {
        shownState = true
        isVisible = true
        activate()
    }

    override fun hide() {
        isVisible = false
        if (isActiveWindow) {
            isActiveWindow = false
        }
    }

    override fun focus() {
        activate()
    }

    override fun bringToFront() {
        activate()
    }

    fun activate() {
        val owner = parentMdiContainer
        if (owner != null) {
            owner.activateWindow(this)
        } else {
            isActiveWindow = true
            onActivated?.invoke(this)
        }
    }

    override fun minimize() {
        if (!isMinimizable) return
        val owner = parentMdiContainer
        if (owner != null) {
            owner.minimizeWindow(this)
        } else {
            if (windowState == MdiWindowState.NORMAL) {
                normalBounds = windowBounds
            }
            windowState = MdiWindowState.MINIMIZED
            isVisible = false
            hostedFragment?.performPause()
            onStateChanged?.invoke(this, windowState)
        }
    }

    override fun maximize() {
        maximize(1160, 640)
    }

    fun maximize(desktopWidth: Int = 1160, desktopHeight: Int = 640) {
        if (!isMaximizable) return
        val owner = parentMdiContainer
        if (owner != null) {
            owner.maximizeWindow(this, desktopWidth, desktopHeight)
        } else {
            if (windowState == MdiWindowState.NORMAL) {
                normalBounds = windowBounds
            }
            isVisible = true
            windowState = MdiWindowState.MAXIMIZED
            windowBounds = Rect(0, 0, desktopWidth, desktopHeight)
            hostedFragment?.performResume()
            onStateChanged?.invoke(this, windowState)
        }
    }

    override fun restore() {
        val owner = parentMdiContainer
        if (owner != null) {
            owner.restoreWindow(this)
        } else {
            isVisible = true
            windowState = MdiWindowState.NORMAL
            windowBounds = normalBounds
            hostedFragment?.performResume()
            onStateChanged?.invoke(this, windowState)
        }
    }

    override fun close() {
        if (!isClosable) return
        if (onCloseRequestedListener?.invoke() == false) return
        titleBarInstructions?.setHostEmbedsComponents(false)
        val owner = parentMdiContainer
        if (owner != null) {
            owner.removeWindow(this)
        } else {
            hostedFragment?.performDestroy()
            hostedFragment = null
            shownState = false
            isVisible = false
            isActiveWindow = false
            onClosed?.invoke(this)
        }
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
}
