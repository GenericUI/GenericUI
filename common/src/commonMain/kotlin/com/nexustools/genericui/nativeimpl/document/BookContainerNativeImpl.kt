package com.nexustools.genericui.nativeimpl.document

import com.nexustools.genericui.enums.document.PrintPageOrientation
import com.nexustools.genericui.enums.document.PrintPageSize
import com.nexustools.genericui.nativeimpl.layout.ContainerNativeImpl

/**
 * Platform-native implementation contract for [com.nexustools.genericui.component.document.BookContainer].
 *
 * Uses the operating system or browser's native print dialog (which natively supports printing to physical
 * printers or saving as PDF depending on the system). On `:web`, renders its child
 * [com.nexustools.genericui.component.document.BookPage] components inside an onscreen `<iframe>` element
 * styled with `@page` size/orientation rules and opens the native print dialog for the `<iframe>` via
 * `iframe.contentWindow.print()`.
 */
interface BookContainerNativeImpl<out W> : ContainerNativeImpl<W> {
    fun setPageSize(pageSize: PrintPageSize, orientation: PrintPageOrientation, marginMm: Float)

    fun setDocumentTitle(title: String) {}

    fun printBook(
        documentTitle: String,
        pageSize: PrintPageSize,
        orientation: PrintPageOrientation,
        marginMm: Float
    ): Boolean
}
