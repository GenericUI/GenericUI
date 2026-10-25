package com.nexustools.genericui.component.document

import com.nexustools.genericui.component.action.Button
import com.nexustools.genericui.component.content.ImageView
import com.nexustools.genericui.component.content.Table
import com.nexustools.genericui.component.content.TableCell
import com.nexustools.genericui.component.content.TableColumn
import com.nexustools.genericui.component.content.TableRow
import com.nexustools.genericui.component.layout.Col
import com.nexustools.genericui.component.layout.HStack
import com.nexustools.genericui.component.layout.Row
import com.nexustools.genericui.component.layout.VStack
import com.nexustools.genericui.component.surface.Card
import com.nexustools.genericui.component.typography.Heading
import com.nexustools.genericui.component.typography.Label
import com.nexustools.genericui.component.typography.Text
import com.nexustools.genericui.enums.document.PrintPageOrientation
import com.nexustools.genericui.enums.document.PrintPageSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BookContainerTest {

    @Test
    fun testDefaultA4PageSizeAndStandardPaperSizes() {
        val book = BookContainer(documentTitle = "Adventurer Compendium")
        assertEquals(PrintPageSize.A4, book.pageSize)
        assertEquals(PrintPageOrientation.PORTRAIT, book.orientation)
        assertEquals("iframe", book.htmlTag)
        assertEquals("A4", book.getAttribute("data-page-size"))
        assertEquals("portrait", book.getAttribute("data-page-orientation"))

        val page1 = book.addPage(title = "Cover & Summary")
        assertEquals(1, page1.pageNumber)
        assertEquals("section", page1.htmlTag)
        assertEquals(210f, page1.widthMm)
        assertEquals(297f, page1.heightMm)
        assertEquals(595, page1.widthPt)
        assertEquals(842, page1.heightPt)

        // Switch orientation to LANDSCAPE and verify propagation to existing pages
        book.orientation = PrintPageOrientation.LANDSCAPE
        assertEquals(297f, page1.widthMm)
        assertEquals(210f, page1.heightMm)
        assertEquals("landscape", page1.getAttribute("data-page-orientation"))

        // Switch paper size to LETTER portrait
        book.orientation = PrintPageOrientation.PORTRAIT
        book.pageSize = PrintPageSize.LETTER
        assertEquals(215.9f, page1.widthMm)
        assertEquals(279.4f, page1.heightMm)
        assertEquals("letter", page1.getAttribute("data-page-size"))
    }

    @Test
    fun testBookPageSupportsAnyGenericUiComponentsAndNativePrint() {
        val book = BookContainer(
            pageSize = PrintPageSize.A4,
            orientation = PrintPageOrientation.PORTRAIT,
            marginMm = 12f,
            documentTitle = "Multi-Component Book"
        )

        val page1 = book.addPage(title = "Page 1 - Layout & Typography") {
            header {
                addChild(Heading("Chapter 1: Hero Overview", level = 2))
                addChild(Text("Composed using standard GenericUI components.", paragraph = true))
            }
            content {
                val row = Row()
                val leftCol = Col(span = 6)
                val card = Card(title = "Attributes")
                val table = Table()
                    .setColumns(
                        listOf(
                            TableColumn("stat", "Stat"),
                            TableColumn("val", "Value")
                        )
                    )
                    .setRows(
                        listOf(
                            TableRow("str", listOf(TableCell("STR"), TableCell("18"))),
                            TableRow("dex", listOf(TableCell("DEX"), TableCell("14")))
                        )
                    )
                card.addChild(table)
                leftCol.addChild(card)

                val rightCol = Col(span = 6)
                val vstack = VStack()
                vstack.addChild(Label("Portrait Preview"))
                vstack.addChild(ImageView(sourceUrl = "https://example.com/portrait.png"))
                val hstack = HStack()
                hstack.addChild(Button("Action 1"))
                hstack.addChild(Button("Action 2"))
                vstack.addChild(hstack)
                rightCol.addChild(vstack)

                row.addChild(leftCol)
                row.addChild(rightCol)
                addChild(row)
            }
            footer {
                addChild(Label("Page 1 Footer"))
            }
        }

        val page2 = book.addPage(title = "Page 2 - Inventory") {
            addChild(Heading("Equipment List", level = 3))
            addChild(Text("Longsword, Shield, Backpack", paragraph = true))
        }

        assertEquals(2, book.pageCount())
        assertEquals(page1, book.getPage(0))
        assertEquals(page2, book.getPage(1))

        val lines1 = page1.collectPrintableLines()
        assertTrue(lines1.any { it.contains("Chapter 1: Hero Overview") })
        assertTrue(lines1.any { it.contains("Attributes") })
        assertTrue(lines1.any { it.contains("STR | 18") })
        assertTrue(lines1.any { it.contains("Page 1 Footer") })

        assertTrue(book.print())
        assertEquals(1, book.printCount)
        assertEquals("1", book.getAttribute("data-print-count"))
    }
}
