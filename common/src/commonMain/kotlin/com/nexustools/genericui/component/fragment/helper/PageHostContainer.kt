package com.nexustools.genericui.component.fragment.helper

import com.nexustools.genericui.component.feedback.Spinner
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.shell.Window
import com.nexustools.genericui.style.css.CssStyleSheet

/**
 * Root host container for [PageFragment] navigation (`DCGame`'s `Context.navigate` & global `.loader` coordinator).
 *
 * Coordinates:
 * - Window title updates (`"$appTitle - ${page.title}"`).
 * - Global `.loader` [Spinner] visibility synchronized with active page reference-counted `loading` states.
 * - Cross-fade / CSS transition lifecycle (`.created` -> active -> `.destroyed`).
 * - Automatic theme [CssStyleSheet] application and viewport [PageFragment.onResize] dispatch.
 */
open class PageHostContainer(
    var appTitle: String = "Application",
    var transitionDurationMs: Int = 500,
    var themeStyleSheet: CssStyleSheet? = null
) : Container("div", "page-host") {

    val loaderContainer: Container = Container("div", "loader").apply {
        isVisible = false
    }
    val loaderSpinner: Spinner = Spinner()

    var currentPage: PageFragment? = null
        private set

    val pageHistory: MutableList<PageFragment> = mutableListOf()

    var ownerWindow: Window? = null
    var viewportWidthPx: Int = 1280
        private set
    var viewportHeightPx: Int = 720
        private set

    val isAnyPageLoading: Boolean
        get() = (currentPage?.isLoading == true)

    init {
        loaderContainer.addChild(loaderSpinner)
        addChild(loaderContainer)
    }

    fun navigateToPage(page: PageFragment, vararg args: Any?) {
        navigateToPageWithList(page, args.toList())
    }

    fun navigateToPageWithList(page: PageFragment, args: List<Any?>) {
        val outgoing = currentPage
        if (outgoing != null && outgoing !== page) {
            outgoing.addClass("destroyed")
            themeStyleSheet?.let { outgoing.applyStyleSheet(it, viewportWidthPx) }
            outgoing.performDestroy()
            removeChild(outgoing)
        }

        page.pageHost = this
        page.pageArgs = args
        page.attachedWindow = ownerWindow
        page.onLoadingStateChanged = { _, _ -> syncGlobalLoaderVisibility() }

        currentPage = page
        pageHistory.add(page)

        val windowTitle = if (page.title.isNotBlank()) "$appTitle - ${page.title}" else appTitle
        ownerWindow?.setTitle(windowTitle)

        page.addClass("created")
        addChild(page)
        page.performCreate(null)
        page.performStart()
        page.performResume()

        themeStyleSheet?.let { page.applyStyleSheet(it, viewportWidthPx) }

        // Transition from .created to active state
        page.removeClass("created")
        themeStyleSheet?.let { page.applyStyleSheet(it, viewportWidthPx) }

        syncGlobalLoaderVisibility()
        page.onResize(viewportWidthPx, viewportHeightPx)
    }

    fun updateViewportSize(widthPx: Int, heightPx: Int) {
        viewportWidthPx = widthPx.coerceAtLeast(1)
        viewportHeightPx = heightPx.coerceAtLeast(1)
        val active = currentPage ?: return
        themeStyleSheet?.let { active.applyStyleSheet(it, viewportWidthPx) }
        active.onResize(viewportWidthPx, viewportHeightPx)
    }

    private fun syncGlobalLoaderVisibility() {
        val loading = currentPage?.isLoading == true
        loaderContainer.isVisible = loading
        val active = currentPage
        if (active != null) {
            themeStyleSheet?.let { active.applyStyleSheet(it, viewportWidthPx) }
        }
    }
}
