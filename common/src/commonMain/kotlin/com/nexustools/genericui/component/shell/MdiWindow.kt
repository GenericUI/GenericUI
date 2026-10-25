package com.nexustools.genericui.component.shell

import com.nexustools.genericui.component.action.Button
import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.layout.HStack
import com.nexustools.genericui.component.layout.ScrollView
import com.nexustools.genericui.component.typography.Badge
import com.nexustools.genericui.component.typography.Label
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.enums.bootstrap.AlignItems
import com.nexustools.genericui.enums.bootstrap.BootstrapSize
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.enums.bootstrap.DisplayMode
import com.nexustools.genericui.enums.bootstrap.FlexDirection
import com.nexustools.genericui.enums.bootstrap.JustifyContent
import com.nexustools.genericui.enums.bootstrap.PositionMode
import com.nexustools.genericui.enums.bootstrap.SpacingScale
import com.nexustools.genericui.geometry.Rect

/**
 * Multi-Document Interface (MDI) inner sub-window (`<genericui:mdiwindow class="mdi-window">`)
 * managed inside an [MdiContainer].
 *
 * Supports interactive drag-to-move via [headerBar], minimize/maximize/restore/close actions,
 * z-index focus activation, and hosting either arbitrary [AbstractComponent] trees or a full-lifecycle
 * [Fragment] via [setFragment].
 */
open class MdiWindow(
    val windowId: String,
    title: String = "Window",
    var category: String = "",
    initialBounds: Rect = Rect(24, 24, 480, 360),
    icon: String? = null
) : Container(tagName = "genericui:mdiwindow", classPrefix = "mdi-window") {

    var parentMdiContainer: MdiContainer? = null
        internal set

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

    var title: String = title
        set(value) {
            field = value
            titleLabel.text = value
            parentMdiContainer?.syncTaskbarButton(this)
        }

    var icon: String? = icon
        set(value) {
            field = value
            parentMdiContainer?.syncTaskbarButton(this)
        }

    var normalBounds: Rect = initialBounds

    var windowBounds: Rect = initialBounds
        set(value) {
            field = value
            leftOffset = "${value.x}px"
            topOffset = "${value.y}px"
            widthPx = value.width
            heightPx = value.height
            setStyleProperty("width", "${value.width}px")
            setStyleProperty("height", "${value.height}px")
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

    var isActiveWindow: Boolean = false
        internal set(value) {
            field = value
            if (value) {
                addClass("active")
                stateBadge.type = BootstrapType.PRIMARY
            } else {
                removeClass("active")
                stateBadge.type = BootstrapType.SECONDARY
            }
        }

    var isClosable: Boolean = true
        set(value) {
            field = value
            closeButton.isVisible = value
        }

    var isMinimizable: Boolean = true
        set(value) {
            field = value
            minimizeButton.isVisible = value
        }

    var isMaximizable: Boolean = true
        set(value) {
            field = value
            maximizeButton.isVisible = value
        }

    var isDraggable: Boolean = true
    var isResizable: Boolean = true

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
            addChild(titleLabel)
            addChild(stateBadge)
        }
        val controlsGroup = HStack(gap = SpacingScale.S1).apply {
            alignItems = AlignItems.CENTER
            addChild(minimizeButton)
            addChild(maximizeButton)
            addChild(closeButton)
        }
        headerBar.addChild(titleGroup)
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
        bodyContainer.addChild(component)
    }

    fun setContent(component: AbstractComponent): MdiWindow = apply {
        bodyContainer.clearChildren()
        bodyContainer.addChild(component)
    }

    fun setFragment(fragment: Fragment): MdiWindow = apply {
        hostedFragment?.performDestroy()
        hostedFragment = fragment
        bodyContainer.clearChildren()
        fragment.performCreate(null)
        fragment.performStart()
        fragment.performResume()
        bodyContainer.addChild(fragment)
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

    fun minimize() {
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

    fun restore() {
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

    fun close() {
        if (!isClosable) return
        val owner = parentMdiContainer
        if (owner != null) {
            owner.removeWindow(this)
        } else {
            hostedFragment?.performDestroy()
            hostedFragment = null
            isVisible = false
            onClosed?.invoke(this)
        }
    }
}
