package com.nexustools.genericui.component.fragment.helper

import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.fragment.FragmentBundle
import com.nexustools.genericui.window.WindowConfiguration

/**
 * Base page [Fragment] matching `DCGame`'s `PageController` architecture.
 *
 * Provides:
 * - [title] for automatic window title updates (`"$appTitle - $title"`).
 * - Variadic [pageArgs] (`List<Any?>`) passed via [navigate] / [PageHostContainer.navigateToPage].
 * - [buildPageView] and [onPageController] lifecycle hooks.
 * - Automatic `"page"`, `"created"`, `"destroyed"`, and `"loading"` CSS class lifecycle.
 * - [onResize] viewport callback invoked on initial mount, when `loading` finishes, and on window resize.
 */
abstract class PageFragment(
    open val title: String = ""
) : Fragment() {

    var pageHost: PageHostContainer? = null
        internal set

    var pageArgs: List<Any?> = emptyList()

    init {
        addClass("page")
    }

    abstract fun buildPageView(args: List<Any?>)

    open fun onPageController(args: List<Any?>) {}

    open fun onResize(viewportWidth: Int, viewportHeight: Int) {}

    open fun onBeginLoadTransition(durationMs: Int) {
        removeClass("destroyed")
        addClass("created")
        pointerEventsEnabled = false
        zIndex = 0
        transition = "opacity ${durationMs}ms ease"
        opacity = 0f
    }

    open fun onFinishLoadTransition(durationMs: Int) {
        removeClass("created")
        pointerEventsEnabled = true
        zIndex = 5
        transition = "opacity ${durationMs}ms ease"
        opacity = 1f
    }

    open fun onBeginUnloadTransition(durationMs: Int) {
        removeClass("created")
        addClass("destroyed")
        pointerEventsEnabled = false
        zIndex = 0
        transition = "opacity ${durationMs}ms ease"
        opacity = 0f
    }

    override fun onCreate(savedState: FragmentBundle?) {
        super.onCreate(savedState)
        clearChildren()
        buildPageView(pageArgs)
        onPageController(pageArgs)
    }

    override fun onLoadingFinished() {
        super.onLoadingFinished()
        val host = pageHost
        if (host != null) {
            onResize(host.viewportWidthPx, host.viewportHeightPx)
        }
    }

    override fun onConfigurationChanged(newConfig: WindowConfiguration) {
        super.onConfigurationChanged(newConfig)
        onResize(newConfig.widthPx, newConfig.heightPx)
    }

    fun navigate(nextPage: PageFragment, vararg args: Any?) {
        pageHost?.navigateToPage(nextPage, *args)
    }

    fun navigateWithArgsList(nextPage: PageFragment, args: List<Any?>) {
        pageHost?.navigateToPageWithList(nextPage, args)
    }
}
