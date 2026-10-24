package com.nexustools.genericui.component.tabs

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.nativeimpl.tabs.TabNativeImpl

/**
 * Base tab header component inside a [TabContainer] or [FragmentTabContainer].
 *
 * A [Tab] is a first-class component that is translated to a native tab item (`JTabbedPane` tab / custom tab
 * component on `:swing`, `TCITEMW` on `:windows`, `NSTabViewItem` on `:macos`, `AdwTabPage` / `GtkNotebookPage`
 * on `:gtk`, `QTabBar` item on `:qt`, `TabLayout.Tab` on `:android`, `UITabBarItem` on `:ios`, and `.nav-link`
 * on `:web`).
 *
 * It also supports embedding any arbitrary [customComponent] inside the tab header while retaining native tab
 * selection, accessibility, and keyboard navigation.
 */
open class Tab(
    val tabId: String = "tab_${nextTabCounter()}",
    title: String = "",
    icon: String? = null,
    tooltip: String? = null,
    customComponent: AbstractComponent? = null
) : AbstractContainer() {

    var title: String = title
        set(value) {
            field = value
            typedNative<TabNativeImpl<*>>()?.setTitleNative(displayTitle)
            ownerNotifyUpdated?.invoke(this)
        }

    var icon: String? = icon
        set(value) {
            field = value
            typedNative<TabNativeImpl<*>>()?.setIconNative(value)
            ownerNotifyUpdated?.invoke(this)
        }

    var tooltip: String? = tooltip
        set(value) {
            field = value
            typedNative<TabNativeImpl<*>>()?.setTooltipNative(value)
            ownerNotifyUpdated?.invoke(this)
        }

    var isDirty: Boolean = false
        set(value) {
            field = value
            typedNative<TabNativeImpl<*>>()?.setDirtyNative(value)
            typedNative<TabNativeImpl<*>>()?.setTitleNative(displayTitle)
            ownerNotifyUpdated?.invoke(this)
        }

    var isSelected: Boolean = false
        internal set(value) {
            field = value
            typedNative<TabNativeImpl<*>>()?.setSelectedNative(value)
        }

    var customComponent: AbstractComponent? = null
        set(value) {
            field?.let { removeChild(it) }
            field = value
            if (value != null) {
                addChild(value)
            }
            typedNative<TabNativeImpl<*>>()?.setCustomHeaderComponentNative(value?.nativeImpl)
            ownerNotifyUpdated?.invoke(this)
        }

    val displayTitle: String
        get() = if (isDirty && !title.startsWith("*")) "*$title" else title

    var onSelected: (() -> Unit)? = null

    internal var ownerNotifyUpdated: ((Tab) -> Unit)? = null
    internal var ownerRequestClose: ((Tab) -> Boolean)? = null

    init {
        if (customComponent != null) {
            this.customComponent = customComponent
        }
    }

    override fun createNativeImpl(factory: NativeFactory): TabNativeImpl<*> =
        factory.createTab(closable = false)

    override fun syncStateToNative() {
        super.syncStateToNative()
        val impl = typedNative<TabNativeImpl<*>>() ?: return
        impl.setTabIdNative(tabId)
        impl.setTitleNative(displayTitle)
        impl.setIconNative(icon)
        impl.setTooltipNative(tooltip)
        impl.setClosableNative(false)
        impl.setDirtyNative(isDirty)
        impl.setSelectedNative(isSelected)
        impl.setCustomHeaderComponentNative(customComponent?.nativeImpl)
    }

    companion object {
        private var counter: Long = 0L
        internal fun nextTabCounter(): Long = ++counter
    }
}
