package com.nexustools.genericui.component.document

import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.enums.document.PrintPageOrientation
import com.nexustools.genericui.enums.document.PrintPageSize
import com.nexustools.genericui.nativeimpl.document.BookContainerNativeImpl

/**
 * Multi-page printable book and document container (`BookContainer`) that renders a live onscreen
 * Print Preview of standard-sized [BookPage] sheets (defaulting to [PrintPageSize.A4]).
 *
 * - Any GenericUI components (`Heading`, `Text`, `Label`, `Table`, `Row`, `Col`, `HStack`, `VStack`,
 *   `Card`, `ImageView`, `Canvas2D`, etc.) can be added inside each [BookPage] to compose multi-page
 *   printable documents.
 * - Calling [print] invokes the platform's native print dialog (which supports printing or saving to PDF
 *   via the system print dialog).
 * - On `:web`, [BookContainer] renders its [BookPage] children directly inside an onscreen `<iframe>`
 *   with `@page` size/orientation rules so calling [print] prints the `<iframe>` directly via
 *   `iframe.contentWindow.print()`.
 */
open class BookContainer(
    pageSize: PrintPageSize = PrintPageSize.A4,
    orientation: PrintPageOrientation = PrintPageOrientation.PORTRAIT,
    marginMm: Float = 12f,
    documentTitle: String = "Document"
) : Container("iframe", "book-container") {

    var pageSize: PrintPageSize = pageSize
        set(value) {
            field = value
            for (page in _pages) {
                page.pageSize = value
            }
            setAttribute("data-page-size", value.cssSize)
            typedNative<BookContainerNativeImpl<*>>()?.setPageSize(value, orientation, marginMm)
        }

    var orientation: PrintPageOrientation = orientation
        set(value) {
            field = value
            for (page in _pages) {
                page.orientation = value
            }
            setAttribute("data-page-orientation", value.cssOrientation)
            typedNative<BookContainerNativeImpl<*>>()?.setPageSize(pageSize, value, marginMm)
        }

    var marginMm: Float = marginMm
        set(value) {
            field = value
            for (page in _pages) {
                page.marginMm = value
            }
            setAttribute("data-margin-mm", value.toString())
            typedNative<BookContainerNativeImpl<*>>()?.setPageSize(pageSize, orientation, value)
        }

    var documentTitle: String = documentTitle
        set(value) {
            field = value
            setAttribute("title", value)
            typedNative<BookContainerNativeImpl<*>>()?.setDocumentTitle(value)
        }

    private val _pages = mutableListOf<BookPage>()
    val pages: List<BookPage>
        get() = _pages

    var currentPageIndex: Int = 0
        private set

    var printCount: Int = 0
        private set

    init {
        this.htmlTag = "iframe"
        setAttribute("data-page-size", pageSize.cssSize)
        setAttribute("data-page-orientation", orientation.cssOrientation)
        setAttribute("data-margin-mm", marginMm.toString())
        setAttribute("title", documentTitle)
        setStyleProperty("width", "100%")
        setStyleProperty("height", "100%")
        setStyleProperty("border", "none")
        setStyleProperty("background-color", "#ffffff")
        setStyleProperty("display", "block")
        setStyleProperty("overflow-y", "auto")
    }

    /**
     * Appends a new [BookPage] sized to this container's [pageSize] and [orientation], configures it
     * with [block], and mounts it as a child component.
     */
    fun addPage(title: String = "", block: (BookPage.() -> Unit)? = null): BookPage {
        val page = BookPage(
            pageNumber = _pages.size + 1,
            pageTitle = title,
            pageSize = pageSize,
            orientation = orientation,
            marginMm = marginMm
        )
        block?.invoke(page)
        _pages.add(page)
        addChild(page)
        return page
    }

    /**
     * Alias for [addPage] matching `createPage { ... }` style call sites.
     */
    fun createPage(title: String = "", block: (BookPage.() -> Unit)? = null): BookPage =
        addPage(title = title, block = block)

    fun removePage(index: Int) {
        if (index !in _pages.indices) return
        val removed = _pages.removeAt(index)
        removeChild(removed)
        for ((idx, page) in _pages.withIndex()) {
            page.pageNumber = idx + 1
        }
        if (currentPageIndex >= _pages.size) {
            currentPageIndex = (_pages.size - 1).coerceAtLeast(0)
        }
    }

    fun clearPages() {
        _pages.clear()
        clearChildren()
        currentPageIndex = 0
    }

    fun getPage(index: Int): BookPage? = _pages.getOrNull(index)

    fun pageCount(): Int = _pages.size

    fun goToPage(index: Int): Boolean {
        if (index !in _pages.indices) return false
        currentPageIndex = index
        return true
    }

    fun nextPage(): Boolean = goToPage(currentPageIndex + 1)

    fun previousPage(): Boolean = goToPage(currentPageIndex - 1)

    /**
     * Opens the system native print dialog for this [BookContainer] (on `:web`, focuses and calls
     * `print()` on the onscreen `<iframe>`'s `contentWindow`).
     */
    fun print(): Boolean {
        printCount++
        setAttribute("data-print-count", printCount.toString())
        val impl = typedNative<BookContainerNativeImpl<*>>()
        return impl?.printBook(documentTitle, pageSize, orientation, marginMm) ?: true
    }

    /**
     * Convenience alias for [print].
     */
    fun printDocument(): Boolean = print()

    override fun createNativeImpl(factory: NativeFactory): BookContainerNativeImpl<*> =
        factory.createBookContainer()

    override fun syncStateToNative() {
        super.syncStateToNative()
        val impl = typedNative<BookContainerNativeImpl<*>>() ?: return
        impl.setPageSize(pageSize, orientation, marginMm)
        impl.setDocumentTitle(documentTitle)
    }
}
