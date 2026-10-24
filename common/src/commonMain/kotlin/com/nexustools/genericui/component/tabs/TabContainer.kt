package com.nexustools.genericui.component.tabs

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.nativeimpl.tabs.TabContainerNativeImpl
import com.nexustools.genericui.nativeimpl.tabs.TabNativeImpl

/**
 * Native tabbed view container (`JTabbedPane`, `WC_TABCONTROL`, `QTabWidget`, `NSTabView`,
 * `AdwTabView`/`GtkNotebook`, `TabLayout`, `UITabBar`, or `.nav-tabs` + `.tab-pane.fade`).
 *
 * Supports:
 * - Standard [Tab]s, [CloseableTab]s, [BadgeTab]s, [PinnedTab]s, [EditableTab]s, and arbitrary custom
 *   [AbstractComponent] tab headers (`addTab(customTabComponent, content)`).
 * - Automatic translation of both the container and each [Tab] header component into native widgets
 *   on platforms that support them.
 * - Optional cross-fade transitions (`fadeTransition = true`) on platforms that support animated tab transitions.
 */
open class TabContainer(
    var fadeTransition: Boolean = true,
    tabPosition: TabPosition = TabPosition.TOP,
    tabBarStyle: TabBarStyle = TabBarStyle.TABS
) : AbstractContainer() {

    private val _entries = mutableListOf<TabEntry>()
    val entries: List<TabEntry> get() = _entries
    val tabs: List<TabEntry> get() = _entries

    var tabPosition: TabPosition = tabPosition
        set(value) {
            field = value
            typedNative<TabContainerNativeImpl<*>>()?.setTabPositionNative(value)
        }

    var tabBarStyle: TabBarStyle = tabBarStyle
        set(value) {
            field = value
            typedNative<TabContainerNativeImpl<*>>()?.setTabBarStyleNative(value)
        }

    var selectedIndex: Int = 0
        set(value) {
            val clamped = value.coerceIn(0, (_entries.size - 1).coerceAtLeast(0))
            field = clamped
            _entries.forEachIndexed { idx, entry ->
                entry.tab.isSelected = (idx == clamped)
            }
            typedNative<TabContainerNativeImpl<*>>()?.setSelectedTabIndex(clamped, fadeTransition)
            val selectedEntry = _entries.getOrNull(clamped)
            selectedEntry?.tab?.onSelected?.invoke()
            onTabSelected?.invoke(clamped)
            if (selectedEntry != null) {
                onTabEntrySelected?.invoke(selectedEntry)
                onTabIdSelected?.invoke(selectedEntry.id)
            }
        }

    var activeIndex: Int
        get() = selectedIndex
        set(value) {
            selectedIndex = value
        }

    var onTabSelected: ((Int) -> Unit)? = null
    var onTabEntrySelected: ((TabEntry) -> Unit)? = null
    var onTabCloseRequested: ((TabEntry) -> Boolean)? = null
    private var onTabIdSelected: ((String) -> Unit)? = null
    private var onTabIdCloseRequested: ((String) -> Unit)? = null

    @kotlin.jvm.JvmName("setOnTabIdSelected")
    fun setOnTabSelected(listener: (String) -> Unit) {
        onTabIdSelected = listener
    }

    @kotlin.jvm.JvmName("setOnTabIdCloseRequested")
    fun setOnTabCloseRequested(listener: (String) -> Unit) {
        onTabIdCloseRequested = listener
    }

    /**
     * Adds a tab using a [Tab] component ([Tab], [CloseableTab], [BadgeTab], [PinnedTab], [EditableTab]).
     */
    fun addTab(tab: Tab, content: AbstractComponent, index: Int = -1): TabContainer = apply {
        wireTabCallbacks(tab)
        val entry = TabEntry(tab = tab, content = content)
        val insertIdx = if (index < 0 || index >= _entries.size) {
            _entries.add(entry)
            _entries.size - 1
        } else {
            _entries.add(index, entry)
            index
        }
        addChild(tab)
        addChild(content)
        tab.isSelected = (insertIdx == selectedIndex)

        val impl = typedNative<TabContainerNativeImpl<*>>()
        val tabImpl = tab.nativeImpl as? TabNativeImpl<*>
        val contentImpl = content.nativeImpl
        if (impl != null && contentImpl != null) {
            if (tabImpl != null) {
                impl.addTabNative(tabImpl, contentImpl, insertIdx)
            } else {
                impl.addTabNative(tab.displayTitle, contentImpl, insertIdx)
            }
        }
    }

    /**
     * Adds a tab using any custom [AbstractComponent] as the tab header component.
     * If [tabComponent] is already a [Tab] subclass, it is used directly; otherwise it is wrapped in a [Tab]
     * whose native implementation mounts [tabComponent] inside the platform's native tab header slot.
     */
    fun addTab(
        tabComponent: AbstractComponent,
        content: AbstractComponent,
        id: String = "tab_${_entries.size}",
        title: String = "",
        index: Int = -1
    ): TabContainer {
        val resolvedTab = if (tabComponent is Tab) {
            tabComponent
        } else {
            Tab(tabId = id, title = title, customComponent = tabComponent)
        }
        return addTab(resolvedTab, content, index)
    }

    /**
     * Adds a standard [Tab] by title and optional icon.
     */
    fun addTab(
        title: String,
        content: AbstractComponent,
        id: String = "tab_${_entries.size}",
        icon: String? = null,
        closable: Boolean = false
    ): TabContainer = apply {
        val tab: Tab = if (closable) {
            CloseableTab(tabId = id, title = title, icon = icon, isCloseable = true)
        } else {
            Tab(tabId = id, title = title, icon = icon)
        }
        addTab(tab, content)
    }

    /**
     * Convenience overload matching `addTab(tabId = ..., title = ..., content = ..., icon = ..., closable = ...)`.
     */
    fun addCloseableTab(
        title: String,
        content: AbstractComponent,
        id: String = "tab_${_entries.size}",
        icon: String? = null,
        isCloseable: Boolean = true,
        isDirty: Boolean = false,
        customComponent: AbstractComponent? = null,
        onCloseRequested: (() -> Boolean)? = null
    ): CloseableTab {
        val tab = CloseableTab(
            tabId = id,
            title = title,
            icon = icon,
            isCloseable = isCloseable,
            isDirty = isDirty,
            customComponent = customComponent,
            onCloseRequested = onCloseRequested
        )
        addTab(tab, content)
        return tab
    }

    fun removeTabAt(index: Int): TabEntry? {
        if (index !in _entries.indices) return null
        val removed = _entries.removeAt(index)
        removed.tab.ownerNotifyUpdated = null
        removed.tab.ownerRequestClose = null
        removeChild(removed.tab)
        removeChild(removed.content)
        typedNative<TabContainerNativeImpl<*>>()?.removeTabNative(index)
        if (_entries.isNotEmpty() && selectedIndex >= _entries.size) {
            selectedIndex = _entries.size - 1
        }
        return removed
    }

    fun removeTab(tabId: String): Boolean {
        val idx = _entries.indexOfFirst { it.id == tabId }
        if (idx < 0) return false
        removeTabAt(idx)
        return true
    }

    fun removeTab(tab: Tab): Boolean {
        val idx = _entries.indexOfFirst { it.tab === tab }
        if (idx < 0) return false
        removeTabAt(idx)
        return true
    }

    fun closeTab(tabId: String): Boolean {
        val idx = _entries.indexOfFirst { it.id == tabId }
        if (idx < 0) return false
        val entry = _entries[idx]
        val closeable = entry.tab as? CloseableTab
        if (closeable != null && !closeable.isCloseable) return false
        val tabAllowed = closeable?.onCloseRequested?.invoke() ?: true
        if (!tabAllowed) return false
        val containerAllowed = onTabCloseRequested?.invoke(entry) ?: true
        if (!containerAllowed) return false
        onTabIdCloseRequested?.invoke(tabId)
        if (_entries.any { it.id == tabId }) {
            removeTab(tabId)
        }
        return true
    }

    fun updateTabTitle(tabId: String, newTitle: String, icon: String? = null) {
        val idx = _entries.indexOfFirst { it.id == tabId }
        if (idx < 0) return
        val entry = _entries[idx]
        entry.tab.isDirty = newTitle.startsWith("*")
        entry.tab.title = newTitle.removePrefix("*")
        if (icon != null) {
            entry.tab.icon = icon
        }
        typedNative<TabContainerNativeImpl<*>>()?.setTabTitleNative(idx, entry.tab.displayTitle)
    }

    fun setActiveTab(tabId: String) {
        val idx = _entries.indexOfFirst { it.id == tabId }
        if (idx >= 0) {
            selectedIndex = idx
        }
    }

    fun selectTab(tab: Tab) {
        val idx = _entries.indexOfFirst { it.tab === tab }
        if (idx >= 0) {
            selectedIndex = idx
        }
    }

    fun getTab(tabId: String): Tab? = _entries.firstOrNull { it.id == tabId }?.tab

    private fun wireTabCallbacks(tab: Tab) {
        tab.ownerNotifyUpdated = { updatedTab ->
            val idx = _entries.indexOfFirst { it.tab === updatedTab }
            if (idx >= 0) {
                val impl = typedNative<TabContainerNativeImpl<*>>()
                impl?.setTabTitleNative(idx, updatedTab.displayTitle)
                (updatedTab.nativeImpl as? TabNativeImpl<*>)?.let { impl?.updateTabNative(idx, it) }
            }
        }
        tab.ownerRequestClose = { closingTab ->
            closeTab(closingTab.tabId)
        }
    }

    override fun createNativeImpl(factory: NativeFactory): TabContainerNativeImpl<*> =
        factory.createTabContainer()

    override fun syncStateToNative() {
        super.syncStateToNative()
        val impl = typedNative<TabContainerNativeImpl<*>>() ?: return
        impl.setTabPositionNative(tabPosition)
        impl.setTabBarStyleNative(tabBarStyle)
        _entries.forEachIndexed { idx, entry ->
            val contentImpl = entry.content.nativeImpl ?: return@forEachIndexed
            val tabImpl = entry.tab.nativeImpl as? TabNativeImpl<*>
            if (tabImpl != null) {
                impl.addTabNative(tabImpl, contentImpl, idx)
            } else {
                impl.addTabNative(entry.tab.displayTitle, contentImpl, idx)
            }
        }
        impl.setSelectedTabIndex(selectedIndex, fadeTransition)
        impl.setOnTabSelected { idx ->
            selectedIndex = idx
        }
        impl.setOnTabCloseRequested { idx ->
            val entry = _entries.getOrNull(idx) ?: return@setOnTabCloseRequested false
            closeTab(entry.id)
        }
    }
}
