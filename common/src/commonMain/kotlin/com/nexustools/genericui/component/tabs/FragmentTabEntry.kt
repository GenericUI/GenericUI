package com.nexustools.genericui.component.tabs

import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.fragment.FragmentContainer

/**
 * Associates a [Tab] header component ([Tab], [CloseableTab], [BadgeTab], [PinnedTab], [EditableTab],
 * or a [Tab] wrapping a custom component) with a hosted [Fragment] inside a [FragmentTabContainer].
 */
data class FragmentTabEntry(
    val tab: Tab,
    val fragment: Fragment,
    internal val hostContainer: FragmentContainer
) {
    val id: String
        get() = tab.tabId

    var title: String
        get() = tab.title
        set(value) {
            tab.title = value
        }
}
