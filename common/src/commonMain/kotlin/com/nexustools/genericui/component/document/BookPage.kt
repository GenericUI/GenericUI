package com.nexustools.genericui.component.document

import com.nexustools.genericui.component.action.Button
import com.nexustools.genericui.component.content.Table
import com.nexustools.genericui.component.htmlcompat.Td
import com.nexustools.genericui.component.htmlcompat.Th
import com.nexustools.genericui.component.htmlcompat.Tr
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.surface.Card
import com.nexustools.genericui.component.typography.Label
import com.nexustools.genericui.component.typography.MarkdownView
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.enums.document.PrintPageOrientation
import com.nexustools.genericui.enums.document.PrintPageSize
import com.nexustools.genericui.geometry.Size

/**
 * Single printable page container inside a [BookContainer], sized to the aspect ratio and physical
 * dimensions of its [PrintPageSize] (defaulting to [PrintPageSize.A4]).
 *
 * [BookPage] is a general-purpose [Container] (`<section class="book-page">`) that supports any
 * GenericUI component (`Heading`, `Text`, `Label`, `Table`, `Row`, `Col`, `HStack`, `VStack`,
 * `Card`, `ImageView`, `Canvas2D`, etc.) as children.
 */
open class BookPage(
    pageNumber: Int = 1,
    pageTitle: String = "",
    pageSize: PrintPageSize = PrintPageSize.A4,
    orientation: PrintPageOrientation = PrintPageOrientation.PORTRAIT,
    marginMm: Float = 12f
) : Container("section", "book-page") {

    var pageNumber: Int = pageNumber
        set(value) {
            field = value
            setAttribute("data-page-number", value.toString())
        }

    var pageTitle: String = pageTitle
        set(value) {
            field = value
            if (value.isNotBlank()) {
                setAttribute("data-page-title", value)
            } else {
                removeAttribute("data-page-title")
            }
        }

    var pageSize: PrintPageSize = pageSize
        set(value) {
            field = value
            applyPageMetrics()
        }

    var orientation: PrintPageOrientation = orientation
        set(value) {
            field = value
            applyPageMetrics()
        }

    var marginMm: Float = marginMm
        set(value) {
            field = value
            applyPageMetrics()
        }

    override var backgroundImageUrl: String?
        get() = super.backgroundImageUrl
        set(value) {
            super.backgroundImageUrl = value
            if (!value.isNullOrBlank()) {
                setAttribute("data-bg-image", value)
                setStyleProperty("background-image", "url('$value')")
                setStyleProperty("background-size", "100% 100%")
                setStyleProperty("background-repeat", "no-repeat")
            } else {
                removeAttribute("data-bg-image")
                setStyleProperty("background-image", "")
            }
        }

    val widthMm: Float
        get() = pageSize.widthMm(orientation)

    val heightMm: Float
        get() = pageSize.heightMm(orientation)

    val widthPt: Int
        get() = pageSize.widthPt(orientation)

    val heightPt: Int
        get() = pageSize.heightPt(orientation)

    val pageWidthPx: Int
        get() = pageSize.widthPx(96, orientation)

    val pageHeightPx: Int
        get() = pageSize.heightPx(96, orientation)

    init {
        this.htmlTag = "section"
        setAttribute("data-page-number", pageNumber.toString())
        if (pageTitle.isNotBlank()) {
            setAttribute("data-page-title", pageTitle)
        }
        applyPageMetrics()
    }

    fun applyPageMetrics() {
        val wMm = widthMm
        val hMm = heightMm
        val wPx = pageWidthPx
        val hPx = pageHeightPx
        widthPx = wPx
        heightPx = hPx
        setPreferredSize(Size(wPx, hPx))
        setAttribute("data-page-size", pageSize.cssSize)
        setAttribute("data-page-orientation", orientation.cssOrientation)
        setAttribute("data-page-width-mm", wMm.toString())
        setAttribute("data-page-height-mm", hMm.toString())
        setStyleProperty("width", "${wMm}mm")
        setStyleProperty("min-height", "${hMm}mm")
        setStyleProperty("aspect-ratio", "$wMm / $hMm")
        setStyleProperty("padding", "${marginMm}mm")
        setStyleProperty("background-color", "#ffffff")
        setStyleProperty("color", "#000000")
        setStyleProperty("box-sizing", "border-box")
        setStyleProperty("position", "relative")
        setStyleProperty("page-break-after", "always")
        setStyleProperty("break-after", "page")
        setStyleProperty("margin", "0 auto 12px auto")
        setStyleProperty("box-shadow", "0 2px 8px rgba(0, 0, 0, 0.22)")
    }

    /**
     * Adds a semantic header container (`<header class="book-page-header">`) to this page.
     */
    fun header(block: Container.() -> Unit): Container {
        val headerBox = Container("header", "book-page-header")
        headerBox.block()
        addChild(headerBox)
        return headerBox
    }

    /**
     * Adds a semantic body content container (`<div class="book-page-content">`) to this page.
     */
    fun content(block: Container.() -> Unit): Container {
        val contentBox = Container("div", "book-page-content")
        contentBox.block()
        addChild(contentBox)
        return contentBox
    }

    /**
     * Adds a semantic footer container (`<footer class="book-page-footer">`) to this page.
     */
    fun footer(block: Container.() -> Unit): Container {
        val footerBox = Container("footer", "book-page-footer")
        footerBox.block()
        addChild(footerBox)
        return footerBox
    }

    /**
     * Traverses this [BookPage]'s component subtree and extracts ordered printable text lines
     * from any child components.
     */
    fun collectPrintableLines(): List<String> {
        val out = mutableListOf<String>()
        if (pageTitle.isNotBlank()) {
            out.add(pageTitle)
        }
        collectComponentLines(this, out)
        return out
    }

    private fun collectComponentLines(component: AbstractComponent, out: MutableList<String>) {
        if (!component.isVisible) return
        when (component) {
            is Label -> {
                if (component.text.isNotBlank()) {
                    component.text.split('\n').map { it.trimEnd() }.filter { it.isNotEmpty() }.forEach { out.add(it) }
                }
            }
            is Button -> {
                if (component.text.isNotBlank()) {
                    out.add(component.text.trim())
                }
            }
            is MarkdownView -> {
                if (component.markdown.isNotBlank()) {
                    component.markdown.split('\n').map { it.trimEnd() }.filter { it.isNotEmpty() }.forEach { out.add(it) }
                }
            }
            is Card -> {
                val cardTitle = component.title
                if (!cardTitle.isNullOrBlank()) {
                    out.add(cardTitle.trim())
                }
                for (child in component.children) {
                    collectComponentLines(child, out)
                }
            }
            is Table -> {
                if (component.columns.isNotEmpty()) {
                    out.add(component.columns.joinToString(" | ") { it.headerText })
                }
                for (row in component.rows) {
                    out.add(row.cells.joinToString(" | ") { it.text })
                }
                for (child in component.children) {
                    collectComponentLines(child, out)
                }
            }
            is Tr -> {
                val cellTexts = mutableListOf<String>()
                for (cell in component.children) {
                    if (cell is Th || cell is Td || cell is AbstractContainer) {
                        val cellLines = mutableListOf<String>()
                        collectComponentLines(cell, cellLines)
                        if (cellLines.isNotEmpty()) {
                            cellTexts.add(cellLines.joinToString(" "))
                        }
                    }
                }
                if (cellTexts.isNotEmpty()) {
                    out.add(cellTexts.joinToString(" | "))
                }
            }
            is AbstractContainer -> {
                for (child in component.children) {
                    collectComponentLines(child, out)
                }
            }
        }
    }
}
