package com.nexustools.genericui.component.tabs

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.nativeimpl.tabs.TabNativeImpl

/**
 * A [CloseableTab] that supports pinning. When [isPinned] is `true`, the tab displays a native pin indicator
 * and suppresses accidental closing until unpinned.
 */
open class PinnedTab(
    tabId: String = "pinned_tab_${nextTabCounter()}",
    title: String = "",
    icon: String? = null,
    tooltip: String? = null,
    isPinned: Boolean = true,
    customComponent: AbstractComponent? = null,
    onCloseRequested: (() -> Boolean)? = null
) : CloseableTab(
    tabId = tabId,
    title = title,
    icon = icon,
    tooltip = tooltip,
    isCloseable = !isPinned,
    customComponent = customComponent,
    onCloseRequested = onCloseRequested
) {

    var isPinned: Boolean = isPinned
        set(value) {
            field = value
            isCloseable = !value
            typedNative<TabNativeImpl<*>>()?.setPinnedNative(value)
            ownerNotifyUpdated?.invoke(this)
        }

    init {
        htmlTag = "genericui:pinnedtab"
    }

    override fun requestClose(): Boolean {
        if (isPinned) return false
        return super.requestClose()
    }

    override fun syncStateToNative() {
        super.syncStateToNative()
        val impl = typedNative<TabNativeImpl<*>>() ?: return
        impl.setPinnedNative(isPinned)
        impl.setClosableNative(!isPinned)
    }
}
