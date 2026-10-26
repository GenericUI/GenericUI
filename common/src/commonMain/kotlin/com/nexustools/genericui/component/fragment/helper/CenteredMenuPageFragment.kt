package com.nexustools.genericui.component.fragment.helper

import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.enums.bootstrap.PositionMode
import com.nexustools.genericui.enums.bootstrap.TextAlignment
import kotlin.math.min

/**
 * Centered `.menu` page fragment that wraps page content
 * inside a centered [menuContainer] (`left: 50%; top: 50%; transform: translate(-50%, -50%)`)
 * and proportionally scales the menu (`scale(s, s)`) on viewport resize when the viewport exceeds
 * the menu's natural dimensions.
 *
 * Uses [Container] pre-rendered buffer caching + CSS `transform` transitions so scaling or
 * `.loading` / `.created` / `.destroyed` animations do not re-render menu children unless dirtied.
 */
abstract class CenteredMenuPageFragment(
    title: String = "",
    var maxViewportRatio: Float = 0.8f
) : PageFragment(title) {

    val menuContainer: Container = Container("div", "menu").apply {
        positionMode = PositionMode.ABSOLUTE
        textAlignment = TextAlignment.CENTER
        leftOffset = "50%"
        topOffset = "50%"
        transition = "transform .5s ease"
        transform = "translate(-50%, -50%)"
    }

    var currentScale: Float = 1.0f
        private set

    open fun menuClass(): String = ""

    abstract fun buildMenuView(menu: Container, args: List<Any?>)

    open fun onMenuController(args: List<Any?>) {}

    override fun buildPageView(args: List<Any?>) {
        val extraClass = menuClass().trim()
        if (extraClass.isNotEmpty()) {
            menuContainer.addClass(extraClass)
        }
        menuContainer.clearChildren()
        buildMenuView(menuContainer, args)
        addChild(menuContainer)
    }

    override fun onPageController(args: List<Any?>) {
        onMenuController(args)
    }

    override fun onLoadingStart() {
        super.onLoadingStart()
        menuContainer.addClass("loading")
        menuContainer.transform = "translate(-50%, -50%) scale(0, 0)"
    }

    override fun onLoadingFinished() {
        menuContainer.removeClass("loading")
        if (hasClass("created") || hasClass("destroyed")) {
            menuContainer.transform = "translate(-50%, -50%) scale(0, 0)"
        } else {
            menuContainer.transform = activeMenuTransform()
        }
        super.onLoadingFinished()
    }

    override fun onBeginLoadTransition(durationMs: Int) {
        super.onBeginLoadTransition(durationMs)
        menuContainer.transition = "transform ${durationMs}ms ease"
        menuContainer.transform = "translate(-50%, -50%) scale(0, 0)"
    }

    override fun onFinishLoadTransition(durationMs: Int) {
        super.onFinishLoadTransition(durationMs)
        menuContainer.transition = "transform ${durationMs}ms ease"
        menuContainer.transform = activeMenuTransform()
    }

    override fun onBeginUnloadTransition(durationMs: Int) {
        super.onBeginUnloadTransition(durationMs)
        menuContainer.transition = "transform ${durationMs}ms ease"
        menuContainer.transform = "translate(-50%, -50%) scale(0, 0)"
    }

    override fun onBeginEnterTransition(durationMs: Int) {
        onBeginLoadTransition(durationMs)
    }

    override fun onActivateEnterTransition(durationMs: Int) {
        onFinishLoadTransition(durationMs)
    }

    override fun onBeginExitTransition(durationMs: Int) {
        onBeginUnloadTransition(durationMs)
    }

    private fun activeMenuTransform(): String =
        if (currentScale > 1.0f) {
            "translate(-50%, -50%) scale($currentScale, $currentScale)"
        } else {
            "translate(-50%, -50%)"
        }

    override fun onResize(viewportWidth: Int, viewportHeight: Int) {
        super.onResize(viewportWidth, viewportHeight)
        if (isLoading || hasClass("destroyed") || hasClass("created")) return

        val bounds = menuContainer.getBounds()
        val fallbackW = (menuContainer.minWidthPx ?: 640).coerceAtLeast(1)
        val fallbackH = (menuContainer.minHeightPx ?: 360).coerceAtLeast(1)
        val rawW = menuContainer.widthPx ?: bounds.width
        val rawH = menuContainer.heightPx ?: bounds.height
        val menuW = if (rawW > 0) rawW else fallbackW
        val menuH = if (rawH > 0) rawH else fallbackH

        if (viewportWidth > menuW && viewportHeight > menuH) {
            val scaleW = viewportWidth.toFloat() / menuW.toFloat()
            val scaleH = viewportHeight.toFloat() / menuH.toFloat()
            val s = min(scaleW, scaleH) * maxViewportRatio
            if (s > 1.0f) {
                currentScale = s
                menuContainer.transform = "translate(-50%, -50%) scale($s, $s)"
                return
            }
        }
        currentScale = 1.0f
        menuContainer.transform = "translate(-50%, -50%)"
    }
}
