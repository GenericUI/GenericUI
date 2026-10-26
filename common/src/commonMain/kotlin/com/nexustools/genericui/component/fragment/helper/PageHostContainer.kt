package com.nexustools.genericui.component.fragment.helper

import com.nexustools.genericui.component.feedback.Spinner
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.shell.Window
import com.nexustools.genericui.enums.fragment.FragmentState
import com.nexustools.genericui.style.css.CssStyleSheet
import com.nexustools.genericui.style.css.PrecompiledStyleSheet

/**
 * Root host container for [PageFragment] navigation and global `.loader` coordination.
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

    val activePage: PageFragment?
        get() = currentPage

    val pageHistory: MutableList<PageFragment> = mutableListOf()

    var ownerWindow: Window? = null
    var viewportWidthPx: Int = 1280
        private set
    var viewportHeightPx: Int = 720
        private set

    val isAnyPageLoading: Boolean
        get() = (currentPage?.isLoading == true)

    var delayedCallbackScheduler: ((delayMs: Int, action: () -> Unit) -> Unit)? = null

    init {
        loaderContainer.addChild(loaderSpinner)
        addChild(loaderContainer)
    }

    private fun scheduleDelayedCallback(delayMs: Int, action: () -> Unit): Boolean {
        val custom = delayedCallbackScheduler
        if (custom != null) {
            custom(delayMs, action)
            return true
        }
        return nativeImpl?.scheduleDelayed(delayMs, action) == true
    }

    fun navigateToPage(page: PageFragment, vararg args: Any?) {
        navigateToPageWithList(page, args.toList())
    }

    fun navigateToPageWithList(page: PageFragment, args: List<Any?>) {
        val outgoing = currentPage
        if (outgoing != null && outgoing !== page) {
            outgoing.onBeginUnloadTransition(transitionDurationMs)
            themeStyleSheet?.let { outgoing.applyStyleSheet(it, viewportWidthPx) }
            outgoing.onBeginUnloadTransition(transitionDurationMs)
            val destroyOutgoing = {
                if (outgoing.state != FragmentState.DESTROYED) {
                    outgoing.performDestroy()
                }
                if (children.contains(outgoing)) {
                    removeChild(outgoing)
                }
            }
            val scheduled = if (transitionDurationMs > 0) {
                scheduleDelayedCallback(transitionDurationMs, destroyOutgoing)
            } else {
                false
            }
            if (!scheduled) {
                destroyOutgoing()
            }
        }

        page.pageHost = this
        page.pageArgs = args
        page.attachedWindow = ownerWindow
        page.onLoadingStateChanged = { _, _ -> syncGlobalLoaderVisibility() }

        currentPage = page
        pageHistory.add(page)

        val windowTitle = if (page.title.isNotBlank()) "$appTitle - ${page.title}" else appTitle
        ownerWindow?.setTitle(windowTitle)

        page.onBeginLoadTransition(transitionDurationMs)
        addChild(page)
        page.performCreate(null)
        page.performStart()
        page.performResume()

        themeStyleSheet?.let { page.applyStyleSheet(it, viewportWidthPx) }
        if (page.hasClass("created")) {
            page.onBeginLoadTransition(transitionDurationMs)
        }
        syncGlobalLoaderVisibility()

        val activateIncoming = {
            if (!page.hasClass("destroyed")) {
                page.onFinishLoadTransition(transitionDurationMs)
                themeStyleSheet?.let { page.applyStyleSheet(it, viewportWidthPx) }
                if (page === currentPage) {
                    syncGlobalLoaderVisibility()
                    page.onResize(viewportWidthPx, viewportHeightPx)
                }
            }
        }
        val createScheduled = if (transitionDurationMs > 0) {
            scheduleDelayedCallback(50, activateIncoming)
        } else {
            false
        }
        if (!createScheduled) {
            activateIncoming()
        }
    }

    fun updateViewportSize(widthPx: Int, heightPx: Int) {
        viewportWidthPx = widthPx.coerceAtLeast(1)
        viewportHeightPx = heightPx.coerceAtLeast(1)
        val active = currentPage ?: return
        themeStyleSheet?.let { active.applyStyleSheet(it, viewportWidthPx) }
        active.onResize(viewportWidthPx, viewportHeightPx)
    }

    override fun applyStyleSheet(styleSheet: CssStyleSheet, viewportWidthPx: Int) {
        themeStyleSheet = styleSheet
        super.applyStyleSheet(styleSheet, viewportWidthPx)
        if (viewportWidthPx > 0 && this.viewportWidthPx <= 0) {
            this.viewportWidthPx = viewportWidthPx
        }
        activePage?.onResize(this.viewportWidthPx, this.viewportHeightPx)
    }

    override fun applyPrecompiledStyleSheet(precompiled: PrecompiledStyleSheet, viewportWidthPx: Int) {
        themeStyleSheet = precompiled.styleSheet
        super.applyPrecompiledStyleSheet(precompiled, viewportWidthPx)
        if (viewportWidthPx > 0 && this.viewportWidthPx <= 0) {
            this.viewportWidthPx = viewportWidthPx
        }
        activePage?.onResize(this.viewportWidthPx, this.viewportHeightPx)
    }

    private fun syncGlobalLoaderVisibility() {
        val loading = currentPage?.isLoading == true
        loaderContainer.isVisible = loading
        val active = currentPage
        if (active != null) {
            themeStyleSheet?.let { active.applyStyleSheet(it, viewportWidthPx) }
            if (!loading) {
                active.onResize(viewportWidthPx, viewportHeightPx)
            }
        }
    }
}
