package com.nexustools.genericui.component.fragment.helper

import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.fragment.FragmentBundle
import com.nexustools.genericui.window.WindowConfiguration

/**
 * Base page [Fragment] for multi-page navigation inside a [PageHostContainer] or [FragmentHostFragment].
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

    override var pageTitle: String
        get() = super.pageTitle.ifBlank { title }
        set(value) {
            super.pageTitle = value
        }

    init {
        addClass("page")
    }

    abstract fun buildPageView(args: List<Any?>)

    open fun onPageController(args: List<Any?>) {}

    override fun onResize(viewportWidth: Int, viewportHeight: Int) {}

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

    override fun onBeginEnterTransition(durationMs: Int) {
        onBeginLoadTransition(durationMs)
    }

    override fun onActivateEnterTransition(durationMs: Int) {
        onFinishLoadTransition(durationMs)
    }

    override fun onBeginExitTransition(durationMs: Int) {
        onBeginUnloadTransition(durationMs)
    }

    override fun onCreate(savedState: FragmentBundle?) {
        super.onCreate(savedState)
        if (pageArgs.isEmpty() && savedState != null) {
            val bundleArgs = savedState["pageArgs"] as? List<*>
            if (bundleArgs != null) {
                pageArgs = bundleArgs
            }
        }
        clearChildren()
        buildPageView(pageArgs)
        onPageController(pageArgs)
    }

    override fun onLoadingFinished() {
        super.onLoadingFinished()
        val host = pageHost
        if (host != null) {
            onResize(host.viewportWidthPx, host.viewportHeightPx)
            return
        }
        val sysHost = when (val fh = fragmentHost) {
            is FragmentHostSystem -> fh
            is FragmentHostFragment -> fh.hostSystem
            else -> null
        }
        if (sysHost != null) {
            onResize(sysHost.viewportWidthPx, sysHost.viewportHeightPx)
        }
    }

    override fun onConfigurationChanged(newConfig: WindowConfiguration) {
        super.onConfigurationChanged(newConfig)
        onResize(newConfig.widthPx, newConfig.heightPx)
    }

    fun setPageArguments(vararg args: Any?) {
        pageArgs = args.toList()
    }

    fun setPageArgumentsWithList(args: List<Any?>) {
        pageArgs = args
    }

    open fun navigate(nextPage: PageFragment, vararg args: Any?) {
        navigateWithArgsList(nextPage, args.toList())
    }

    open fun navigateWithArgsList(nextPage: PageFragment, args: List<Any?>) {
        nextPage.pageArgs = args
        val ph = pageHost
        if (ph != null) {
            ph.navigateToPageWithList(nextPage, args)
            return
        }
        val fh = fragmentHost
        if (fh != null) {
            fh.pushFragment(nextPage)
        }
    }
}
