package com.nexustools.genericui.enums.document

import kotlin.math.roundToInt

/**
 * Standard ISO and North American paper sizes for [com.nexustools.genericui.component.document.BookContainer]
 * and [com.nexustools.genericui.component.document.BookPage], defaulting to [A4].
 */
enum class PrintPageSize(
    val widthMm: Float,
    val heightMm: Float,
    val widthPt: Int,
    val heightPt: Int,
    val widthPx96Dpi: Int,
    val heightPx96Dpi: Int,
    val cssSize: String
) {
    A4(
        widthMm = 210f,
        heightMm = 297f,
        widthPt = 595,
        heightPt = 842,
        widthPx96Dpi = 794,
        heightPx96Dpi = 1123,
        cssSize = "A4"
    ),
    A3(
        widthMm = 297f,
        heightMm = 420f,
        widthPt = 842,
        heightPt = 1191,
        widthPx96Dpi = 1123,
        heightPx96Dpi = 1587,
        cssSize = "A3"
    ),
    A5(
        widthMm = 148f,
        heightMm = 210f,
        widthPt = 420,
        heightPt = 595,
        widthPx96Dpi = 559,
        heightPx96Dpi = 794,
        cssSize = "A5"
    ),
    LETTER(
        widthMm = 215.9f,
        heightMm = 279.4f,
        widthPt = 612,
        heightPt = 792,
        widthPx96Dpi = 816,
        heightPx96Dpi = 1056,
        cssSize = "letter"
    ),
    LEGAL(
        widthMm = 215.9f,
        heightMm = 355.6f,
        widthPt = 612,
        heightPt = 1008,
        widthPx96Dpi = 816,
        heightPx96Dpi = 1344,
        cssSize = "legal"
    ),
    TABLOID(
        widthMm = 279.4f,
        heightMm = 431.8f,
        widthPt = 792,
        heightPt = 1224,
        widthPx96Dpi = 1056,
        heightPx96Dpi = 1632,
        cssSize = "ledger"
    ),
    B4(
        widthMm = 250f,
        heightMm = 353f,
        widthPt = 709,
        heightPt = 1001,
        widthPx96Dpi = 945,
        heightPx96Dpi = 1334,
        cssSize = "B4"
    ),
    B5(
        widthMm = 176f,
        heightMm = 250f,
        widthPt = 499,
        heightPt = 709,
        widthPx96Dpi = 665,
        heightPx96Dpi = 945,
        cssSize = "B5"
    );

    fun widthMm(landscape: Boolean = false): Float =
        if (landscape) heightMm else widthMm

    fun heightMm(landscape: Boolean = false): Float =
        if (landscape) widthMm else heightMm

    fun widthMm(orientation: PrintPageOrientation): Float =
        widthMm(orientation == PrintPageOrientation.LANDSCAPE)

    fun heightMm(orientation: PrintPageOrientation): Float =
        heightMm(orientation == PrintPageOrientation.LANDSCAPE)

    fun widthPt(landscape: Boolean = false): Int =
        if (landscape) heightPt else widthPt

    fun heightPt(landscape: Boolean = false): Int =
        if (landscape) widthPt else heightPt

    fun widthPt(orientation: PrintPageOrientation): Int =
        widthPt(orientation == PrintPageOrientation.LANDSCAPE)

    fun heightPt(orientation: PrintPageOrientation): Int =
        heightPt(orientation == PrintPageOrientation.LANDSCAPE)

    fun widthPx(dpi: Int = 96, landscape: Boolean = false): Int {
        if (dpi == 96) {
            return if (landscape) heightPx96Dpi else widthPx96Dpi
        }
        val mm = widthMm(landscape)
        return ((mm / 25.4f) * dpi.toFloat()).roundToInt()
    }

    fun heightPx(dpi: Int = 96, landscape: Boolean = false): Int {
        if (dpi == 96) {
            return if (landscape) widthPx96Dpi else heightPx96Dpi
        }
        val mm = heightMm(landscape)
        return ((mm / 25.4f) * dpi.toFloat()).roundToInt()
    }

    fun widthPx(dpi: Int = 96, orientation: PrintPageOrientation): Int =
        widthPx(dpi, orientation == PrintPageOrientation.LANDSCAPE)

    fun heightPx(dpi: Int = 96, orientation: PrintPageOrientation): Int =
        heightPx(dpi, orientation == PrintPageOrientation.LANDSCAPE)

    fun aspectRatio(landscape: Boolean = false): Float =
        widthMm(landscape) / heightMm(landscape)
}
