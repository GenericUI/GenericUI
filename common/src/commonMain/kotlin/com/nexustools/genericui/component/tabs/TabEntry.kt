package com.nexustools.genericui.component.tabs

import com.nexustools.genericui.core.AbstractComponent

/**
 * Associates a [Tab] header component ([Tab], [CloseableTab], [BadgeTab], [PinnedTab], [EditableTab],
 * or a [Tab] wrapping a custom [AbstractComponent]) with its tab page [content] inside a [TabContainer].
 */
data class TabEntry(
    val tab: Tab,
    val content: AbstractComponent
) {
    val id: String
        get() = tab.tabId

    var title: String
        get() = tab.title
        set(value) {
            tab.title = value
        }

    val iconSvgOrPath: String?
        get() = tab.icon
}
