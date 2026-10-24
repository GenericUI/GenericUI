package com.nexustools.genericui.component.tabs

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.nativeimpl.tabs.TabNativeImpl

/**
 * A [CloseableTab] whose title can be renamed inline by the user (e.g., double-clicking the tab header
 * in an IDE, worksheet, or shared-object canvas editor).
 */
open class EditableTab(
    tabId: String = "editable_tab_${nextTabCounter()}",
    title: String = "",
    icon: String? = null,
    tooltip: String? = null,
    isCloseable: Boolean = true,
    isEditable: Boolean = true,
    customComponent: AbstractComponent? = null,
    var onTitleEdited: ((String) -> Unit)? = null,
    onCloseRequested: (() -> Boolean)? = null
) : CloseableTab(
    tabId = tabId,
    title = title,
    icon = icon,
    tooltip = tooltip,
    isCloseable = isCloseable,
    customComponent = customComponent,
    onCloseRequested = onCloseRequested
) {

    var isEditable: Boolean = isEditable
        set(value) {
            field = value
            typedNative<TabNativeImpl<*>>()?.setEditableNative(value)
            ownerNotifyUpdated?.invoke(this)
        }

    fun commitEditedTitle(newTitle: String) {
        val trimmed = newTitle.trim()
        if (trimmed.isNotEmpty() && trimmed != title) {
            title = trimmed
            onTitleEdited?.invoke(trimmed)
        }
    }

    override fun syncStateToNative() {
        super.syncStateToNative()
        val impl = typedNative<TabNativeImpl<*>>() ?: return
        impl.setEditableNative(isEditable)
        impl.setOnTitleEdited { edited ->
            commitEditedTitle(edited)
        }
    }
}
