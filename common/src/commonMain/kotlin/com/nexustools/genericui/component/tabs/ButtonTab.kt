package com.nexustools.genericui.component.tabs

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.nativeimpl.tabs.TabNativeImpl

/**
 * A tab strip item that acts strictly as a clickable action button rather than a selectable content tab.
 *
 * Clicking a [ButtonTab] invokes [onClick] without changing the active tab or swapping content panes
 * in the owning [TabContainer].
 */
open class ButtonTab(
    title: String = "",
    id: String = "button_tab_${nextTabCounter()}",
    icon: String? = null,
    tooltip: String? = null,
    customComponent: AbstractComponent? = null,
    onClick: (() -> Unit)? = null
) : Tab(
    tabId = id.ifEmpty { "button_tab_${nextTabCounter()}" },
    title = title,
    icon = icon,
    tooltip = tooltip,
    customComponent = customComponent
) {
    override val isSelectable: Boolean = false
    override val supportsContent: Boolean = false

    override var isSelected: Boolean
        get() = false
        set(_) {
            super.isSelected = false
            nativeTabImpl?.setSelectedNative(false)
        }

    init {
        htmlTag = "genericui:buttontab"
        addClass("nav-button-tab")
        super.isSelected = false
        this.onClick = onClick
    }

    override fun syncStateToNative() {
        super.syncStateToNative()
        val impl = nativeTabImpl ?: return
        impl.setSelectedNative(false)
        impl.setOnClick {
            performClick()
        }
    }

    fun performClick() {
        if (!isEnabled) return
        onClick?.invoke()
    }
}
