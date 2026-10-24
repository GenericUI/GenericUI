package com.nexustools.genericui.component.dock

import com.nexustools.genericui.component.shell.Window
import com.nexustools.genericui.core.AbstractComponent

/**
 * Represents an open document/canvas inside a [DockWorkspace] or [com.nexustools.genericui.component.fragment.helper.DockWorkspaceFragment].
 *
 * In [com.nexustools.genericui.enums.dock.StudioWindowMode.SINGLE_WINDOW], documents are displayed
 * as closable tabs in the central workspace. In [com.nexustools.genericui.enums.dock.StudioWindowMode.MULTI_WINDOW],
 * each document can be hosted in its own OS-level window.
 */
data class StudioDocument(
    val id: String,
    var title: String,
    val content: AbstractComponent,
    var isDirty: Boolean = false,
    var isClosable: Boolean = true,
    var filePath: String? = null,
    var icon: String? = null
) {
    val documentId: String
        get() = id

    val displayTitle: String
        get() = if (isDirty) "*$title" else title

    var detachedDocumentWindow: Window? = null
    var onMetadataChanged: ((StudioDocument) -> Unit)? = null
    var onCloseRequested: (() -> Boolean)? = null
}
