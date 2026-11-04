package com.nexustools.genericui.component.layout

import com.nexustools.genericui.component.surface.Card
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.enums.bootstrap.AlignItems
import com.nexustools.genericui.enums.bootstrap.BootstrapWidth
import com.nexustools.genericui.enums.bootstrap.DisplayMode
import com.nexustools.genericui.enums.bootstrap.FlexDirection
import com.nexustools.genericui.enums.bootstrap.JustifyContent
import com.nexustools.genericui.enums.bootstrap.SpacingScale
import com.nexustools.genericui.window.WindowConfiguration
import kotlin.math.min

/**
 * Shared layout helper (`AdaptiveCardLayout`) that wraps its [contentItems] inside a centered [Card]
 * (`maxWidthPx = cardMaxWidthPx`) when [isCardActive] is `true`, or renders them directly inside an
 * inline full-width [VStack] ([inlineContentStack]) when [isCardActive] is `false`.
 */
open class AdaptiveCardLayout(
    host: AbstractContainer = Container("genericui:adaptive-card-layout", "gui-adaptive-card-layout"),
    cardTitle: String? = null,
    cardSubtitle: String? = null,
    cardMode: AdaptiveCardMode = AdaptiveCardMode.CARD_ON_LARGE_SCREENS,
    cardMaxWidthPx: Int = 520,
    cardBreakpointDp: Float = 600f,
    outerPadding: SpacingScale = SpacingScale.S4,
    cardPadding: SpacingScale = SpacingScale.S3,
    breakpointSpec: HybridBreakpointSpec = HybridBreakpointSpec.DEFAULT
) : Layout<AdaptiveCardLayoutImpl>(host, breakpointSpec) {

    val cardContainer: Card = Card(title = cardTitle)
    val inlineContentStack: VStack = VStack()

    private val _contentItems = mutableListOf<AbstractComponent>()
    private var lastAppliedCardState: Boolean? = null

    private var lastViewportWidthDp: Float = 1280f
    private var lastViewportHeightDp: Float = 800f
    private var lastSmallestScreenWidthDp: Int = 800

    val contentItems: List<AbstractComponent>
        get() = _contentItems.toList()

    val isCardActive: Boolean
        get() = impl.isCardActive

    var cardTitle: String?
        get() = cardContainer.title
        set(value) {
            cardContainer.title = value
        }

    var cardSubtitle: String? = cardSubtitle
        set(value) {
            field = value
            if (!value.isNullOrBlank()) {
                cardContainer.setAttribute("data-card-subtitle", value)
            } else {
                cardContainer.removeAttribute("data-card-subtitle")
            }
        }

    var cardMode: AdaptiveCardMode = cardMode
        set(value) {
            field = value
            if (isMounted) {
                impl.syncLayout(lastViewportWidthDp, lastViewportHeightDp, lastSmallestScreenWidthDp)
            }
        }

    var cardMaxWidthPx: Int = cardMaxWidthPx
        set(value) {
            field = value
            cardContainer.maxWidthPx = value
            if (isMounted) {
                impl.syncLayout(lastViewportWidthDp, lastViewportHeightDp, lastSmallestScreenWidthDp)
            }
        }

    var cardBreakpointDp: Float = cardBreakpointDp
        set(value) {
            field = value
            if (isMounted) {
                impl.syncLayout(lastViewportWidthDp, lastViewportHeightDp, lastSmallestScreenWidthDp)
            }
        }

    var outerPadding: SpacingScale = outerPadding
        set(value) {
            field = value
            if (isMounted) {
                applyCardState(impl.isCardActive)
            }
        }

    var cardPadding: SpacingScale = cardPadding
        set(value) {
            field = value
            if (isMounted) {
                applyCardState(impl.isCardActive)
            }
        }

    var onCardModeChanged: ((Boolean) -> Unit)? = null

    init {
        if (!cardSubtitle.isNullOrBlank()) {
            cardContainer.setAttribute("data-card-subtitle", cardSubtitle)
        }
        cardContainer.maxWidthPx = cardMaxWidthPx
        ensureMounted()
    }

    override fun createImpl(factory: LayoutFactory): AdaptiveCardLayoutImpl =
        factory.createAdaptiveCardLayout(this)

    override fun onMount() {
        host.addClass("gui-adaptive-card-layout")
        cardContainer.addClass("gui-adaptive-card")
        inlineContentStack.addClass("gui-adaptive-card-inline-stack")

        if (!host.children.contains(cardContainer)) {
            host.addChild(cardContainer)
        }
        if (!host.children.contains(inlineContentStack)) {
            host.addChild(inlineContentStack)
        }

        impl.syncLayout(lastViewportWidthDp, lastViewportHeightDp, lastSmallestScreenWidthDp)
    }

    /**
     * Adds a component to the card/inline content list and mounts it into the currently active container.
     */
    fun addContent(component: AbstractComponent): AdaptiveCardLayout = apply {
        ensureMounted()
        if (!_contentItems.contains(component)) {
            _contentItems.add(component)
        }
        applyCardState(impl.isCardActive)
    }

    /**
     * Removes a component from the card/inline content list.
     */
    fun removeContent(component: AbstractComponent): Boolean {
        val removed = _contentItems.remove(component)
        cardContainer.removeChild(component)
        inlineContentStack.removeChild(component)
        return removed
    }

    /**
     * Clears all content components from the layout.
     */
    fun clearContent() {
        for (item in _contentItems.toList()) {
            cardContainer.removeChild(item)
            inlineContentStack.removeChild(item)
        }
        _contentItems.clear()
        cardContainer.clearChildren()
        inlineContentStack.clearChildren()
    }

    internal fun applyCardState(active: Boolean) {
        host.addClass("gui-adaptive-card-layout")
        if (!host.children.contains(cardContainer)) {
            host.addChild(cardContainer)
        }
        if (!host.children.contains(inlineContentStack)) {
            host.addChild(inlineContentStack)
        }

        if (active) {
            host.addClass("gui-adaptive-card-active")
            host.removeClass("gui-adaptive-card-inline")
            host.getBootstrap().apply {
                display = DisplayMode.FLEX
                flexDirection = FlexDirection.COLUMN
                alignItems = AlignItems.CENTER
                justifyContent = JustifyContent.CENTER
                padding = outerPadding
            }
            cardContainer.setPaddingAll(cardPadding)
            cardContainer.maxWidthPx = cardMaxWidthPx
            cardContainer.getBootstrap().width = BootstrapWidth.W_100

            inlineContentStack.clearChildren()
            for (item in _contentItems) {
                if (item.parent != cardContainer) {
                    (item.parent as? AbstractContainer)?.removeChild(item)
                    cardContainer.addChild(item)
                }
            }
            cardContainer.isVisible = true
            inlineContentStack.isVisible = false
        } else {
            host.addClass("gui-adaptive-card-inline")
            host.removeClass("gui-adaptive-card-active")
            host.getBootstrap().apply {
                display = DisplayMode.FLEX
                flexDirection = FlexDirection.COLUMN
                alignItems = AlignItems.STRETCH
                justifyContent = JustifyContent.START
                padding = outerPadding
            }
            inlineContentStack.getBootstrap().width = BootstrapWidth.W_100

            cardContainer.clearChildren()
            for (item in _contentItems) {
                if (item.parent != inlineContentStack) {
                    (item.parent as? AbstractContainer)?.removeChild(item)
                    inlineContentStack.addChild(item)
                }
            }
            cardContainer.isVisible = false
            inlineContentStack.isVisible = true
        }

        if (lastAppliedCardState != active) {
            lastAppliedCardState = active
            onCardModeChanged?.invoke(active)
        }
    }

    fun updateViewport(
        viewportWidthDp: Float,
        viewportHeightDp: Float,
        smallestScreenWidthDp: Int = 0
    ) {
        ensureMounted()
        lastViewportWidthDp = viewportWidthDp.coerceAtLeast(1f)
        lastViewportHeightDp = viewportHeightDp.coerceAtLeast(1f)
        lastSmallestScreenWidthDp = if (smallestScreenWidthDp > 0) {
            smallestScreenWidthDp
        } else {
            min(lastViewportWidthDp.toInt(), lastViewportHeightDp.toInt())
        }
        impl.syncLayout(lastViewportWidthDp, lastViewportHeightDp, lastSmallestScreenWidthDp)
    }

    fun updateViewport(
        viewportWidthPx: Int,
        viewportHeightPx: Int,
        densityScale: Float = 1f
    ) {
        val widthDp = breakpointSpec.toDp(viewportWidthPx, densityScale).toFloat()
        val heightDp = breakpointSpec.toDp(viewportHeightPx, densityScale).toFloat()
        val smallestDp = min(widthDp.toInt(), heightDp.toInt())
        updateViewport(widthDp, heightDp, smallestDp)
    }

    fun applyWindowConfiguration(config: WindowConfiguration) {
        onConfigurationChanged(config)
    }

    override fun onConfigurationChanged(config: WindowConfiguration) {
        super.onConfigurationChanged(config)
        val widthDp = breakpointSpec.toDp(config.widthPx, config.densityScale).toFloat()
        val heightDp = breakpointSpec.toDp(config.heightPx, config.densityScale).toFloat()
        updateViewport(widthDp, heightDp, config.smallestScreenWidthDp)
    }

    override fun onResize(viewportWidth: Int, viewportHeight: Int) {
        super.onResize(viewportWidth, viewportHeight)
        updateViewport(viewportWidth.toFloat(), viewportHeight.toFloat(), min(viewportWidth, viewportHeight))
    }
}
