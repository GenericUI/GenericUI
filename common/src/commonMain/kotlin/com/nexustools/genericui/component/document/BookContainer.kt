package com.nexustools.genericui.component.document

import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.enums.document.PrintPageOrientation
import com.nexustools.genericui.enums.document.PrintPageSize
import com.nexustools.genericui.nativeimpl.document.BookContainerNativeImpl
import com.nexustools.genericui.res.Resources
import com.nexustools.genericui.resources.ResourceID
import com.nexustools.genericui.style.css.CssStyleSheet
import com.nexustools.genericui.style.css.PrecompiledStyleSheet
import com.nexustools.genericui.style.css.StyleSheetRegistry

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

    private val _styleSheets = mutableListOf<PrecompiledStyleSheet>()

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
        getStyle().apply {
            setWidth(com.nexustools.genericui.style.css.CssLength.PERCENT_100)
            setHeight(com.nexustools.genericui.style.css.CssLength.PERCENT_100)
            setBorderStyle(com.nexustools.genericui.enums.css.CssBorderStyle.NONE)
            setBackgroundColor(com.nexustools.genericui.style.color.RgbaColor.WHITE)
            setDisplay(com.nexustools.genericui.enums.css.CssDisplay.BLOCK)
            setOverflowY(com.nexustools.genericui.enums.bootstrap.OverflowBehavior.AUTO)
        }
    }

    fun addStyleSheet(styleSheetPath: String) {
        val normalized = StyleSheetRegistry.normalizePath(styleSheetPath)
        if (normalized.isEmpty()) return
        val resolved = StyleSheetRegistry.resolveByPath(normalized) ?: createFallbackStyleSheet(normalized)
        addStyleSheet(resolved)
    }

    fun addStyleSheet(styleSheetResourceId: ResourceID) {
        val resolved = StyleSheetRegistry.resolveByResourceId(styleSheetResourceId)
            ?: Resources.getStyleSheet(styleSheetResourceId)
            ?: createFallbackStyleSheet(styleSheetResourceId.toResourceUri())
        StyleSheetRegistry.register(resolved, styleSheetResourceId)
        addStyleSheet(resolved)
    }

    fun addStyleSheet(styleSheet: PrecompiledStyleSheet) {
        val normSource = StyleSheetRegistry.normalizePath(styleSheet.sourcePath)
        val existingIdx = _styleSheets.indexOfFirst {
            it === styleSheet ||
                (normSource.isNotEmpty() && StyleSheetRegistry.normalizePath(it.sourcePath) == normSource)
        }
        if (existingIdx >= 0) {
            _styleSheets[existingIdx] = styleSheet
        } else {
            _styleSheets.add(styleSheet)
        }
        for (page in _pages) {
            page.applyPrecompiledStyleSheet(styleSheet)
        }
        typedNative<BookContainerNativeImpl<*>>()?.addStyleSheet(styleSheet)
    }

    fun removeStyleSheet(styleSheetPath: String): Boolean {
        val normalized = StyleSheetRegistry.normalizePath(styleSheetPath)
        if (normalized.isEmpty()) return false
        val target = _styleSheets.firstOrNull {
            val src = StyleSheetRegistry.normalizePath(it.sourcePath)
            val css = StyleSheetRegistry.normalizePath(it.cssResourcePath)
            val name = StyleSheetRegistry.normalizePath(it.name)
            src == normalized || css == normalized || name == normalized
        } ?: return false
        return removeStyleSheet(target)
    }

    fun removeStyleSheet(styleSheetResourceId: ResourceID): Boolean {
        val registered = StyleSheetRegistry.resolveByResourceId(styleSheetResourceId)
            ?: Resources.getStyleSheet(styleSheetResourceId)
        if (registered != null && removeStyleSheet(registered)) return true
        val uri = StyleSheetRegistry.normalizePath(styleSheetResourceId.toResourceUri())
        val relPath = StyleSheetRegistry.normalizePath(styleSheetResourceId.relativePath)
        val target = _styleSheets.firstOrNull {
            val src = StyleSheetRegistry.normalizePath(it.sourcePath)
            val css = StyleSheetRegistry.normalizePath(it.cssResourcePath)
            val name = StyleSheetRegistry.normalizePath(it.name)
            src == uri ||
                (relPath.isNotEmpty() && (src == relPath || css == relPath)) ||
                name == StyleSheetRegistry.normalizePath(styleSheetResourceId.name)
        } ?: return false
        return removeStyleSheet(target)
    }

    fun removeStyleSheet(styleSheet: PrecompiledStyleSheet): Boolean {
        val normSource = StyleSheetRegistry.normalizePath(styleSheet.sourcePath)
        val toRemove = _styleSheets.filter {
            it === styleSheet ||
                (normSource.isNotEmpty() && StyleSheetRegistry.normalizePath(it.sourcePath) == normSource)
        }
        if (toRemove.isEmpty()) return false
        _styleSheets.removeAll(toRemove)
        for (sheet in toRemove) {
            typedNative<BookContainerNativeImpl<*>>()?.removeStyleSheet(sheet)
        }
        return true
    }

    fun hasStyleSheet(styleSheetPath: String): Boolean {
        val normalized = StyleSheetRegistry.normalizePath(styleSheetPath)
        if (normalized.isEmpty()) return false
        return _styleSheets.any {
            val src = StyleSheetRegistry.normalizePath(it.sourcePath)
            val css = StyleSheetRegistry.normalizePath(it.cssResourcePath)
            val name = StyleSheetRegistry.normalizePath(it.name)
            src == normalized || css == normalized || name == normalized
        }
    }

    fun hasStyleSheet(styleSheetResourceId: ResourceID): Boolean {
        val registered = StyleSheetRegistry.resolveByResourceId(styleSheetResourceId)
            ?: Resources.getStyleSheet(styleSheetResourceId)
        if (registered != null && registered in _styleSheets) return true
        val uri = StyleSheetRegistry.normalizePath(styleSheetResourceId.toResourceUri())
        val relPath = StyleSheetRegistry.normalizePath(styleSheetResourceId.relativePath)
        return _styleSheets.any {
            val src = StyleSheetRegistry.normalizePath(it.sourcePath)
            val css = StyleSheetRegistry.normalizePath(it.cssResourcePath)
            val name = StyleSheetRegistry.normalizePath(it.name)
            src == uri ||
                (relPath.isNotEmpty() && (src == relPath || css == relPath)) ||
                name == StyleSheetRegistry.normalizePath(styleSheetResourceId.name)
        }
    }

    fun hasStyleSheet(styleSheet: PrecompiledStyleSheet): Boolean {
        val normSource = StyleSheetRegistry.normalizePath(styleSheet.sourcePath)
        return _styleSheets.any {
            it === styleSheet ||
                (normSource.isNotEmpty() && StyleSheetRegistry.normalizePath(it.sourcePath) == normSource)
        }
    }

    fun clearStyleSheets() {
        _styleSheets.clear()
        typedNative<BookContainerNativeImpl<*>>()?.clearStyleSheets()
    }

    fun getStyleSheets(): List<PrecompiledStyleSheet> = _styleSheets.toList()

    private fun createFallbackStyleSheet(sourcePath: String): PrecompiledStyleSheet {
        val emptyCss = CssStyleSheet.empty()
        val logicalName = sourcePath.substringAfterLast('/').substringBeforeLast('.').ifBlank { "StyleSheet" }
        return object : PrecompiledStyleSheet {
            override val name: String = logicalName
            override val sourcePath: String = sourcePath
            override val cssResourcePath: String = sourcePath
            override val styleSheet: CssStyleSheet = emptyCss
            override val rawCss: String = ""
        }
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
        for (sheet in _styleSheets) {
            page.applyPrecompiledStyleSheet(sheet)
        }
        _pages.add(page)
        addChild(page)
        return page
    }

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

    protected override fun createNativeImpl(factory: NativeFactory): BookContainerNativeImpl<*> =
        factory.createBookContainer()

    override fun syncStateToNative() {
        super.syncStateToNative()
        val impl = typedNative<BookContainerNativeImpl<*>>() ?: return
        impl.setPageSize(pageSize, orientation, marginMm)
        impl.setDocumentTitle(documentTitle)
        for (sheet in _styleSheets) {
            impl.addStyleSheet(sheet)
        }
    }
}
