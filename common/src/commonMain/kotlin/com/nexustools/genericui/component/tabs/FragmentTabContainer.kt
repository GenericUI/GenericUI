package com.nexustools.genericui.component.tabs

import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.fragment.FragmentContainer
import com.nexustools.genericui.component.shell.Window
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.enums.fragment.FragmentState
import com.nexustools.genericui.nativeimpl.tabs.TabContainerNativeImpl
import com.nexustools.genericui.nativeimpl.tabs.TabNativeImpl

/**
 * Tabbed container where each tab hosts a full-lifecycle [Fragment] inside a managed [FragmentContainer],
 * while supporting custom [Tab] components ([Tab], [CloseableTab], [BadgeTab], [PinnedTab], [EditableTab],
 * or any custom [AbstractComponent] tab header) translated to native tab widgets on each platform.
 *
 * Switching tabs automatically drives the outgoing [Fragment] through `onPause()` / `onStop()` and the
 * incoming [Fragment] through `onCreate()` -> `onStart()` -> `onResume()`. Closing a tab drives its
 * [Fragment] through `onDestroy()`.
 */
open class FragmentTabContainer(
    var fadeTransition: Boolean = true,
    tabPosition: TabPosition = TabPosition.TOP,
    tabBarStyle: TabBarStyle = TabBarStyle.TABS,
    internal var hostWindow: Window? = null
) : AbstractContainer() {
    init {
        htmlTag = "genericui:fragmenttabcontainer"
    }

    private val _entries = mutableListOf<FragmentTabEntry>()
    val entries: List<FragmentTabEntry> get() = _entries
    val tabs: List<FragmentTabEntry> get() = _entries

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
            val previousIndex = field
            field = clamped
            _entries.forEachIndexed { idx, entry ->
                entry.tab.isSelected = (idx == clamped)
            }
            if (previousIndex != clamped) {
                _entries.getOrNull(previousIndex)?.fragment?.let { outgoing ->
                    if (outgoing.state == FragmentState.RESUMED || outgoing.state == FragmentState.STARTED) {
                        outgoing.performStop()
                    }
                }
            }
            val selectedEntry = _entries.getOrNull(clamped)
            if (selectedEntry != null && resolveActiveFactory() != null) {
                selectedEntry.hostContainer.ensureActiveFragmentStartedAndResumed()
            }
            typedNative<TabContainerNativeImpl<*>>()?.setSelectedTabIndex(clamped, fadeTransition)
            selectedEntry?.tab?.onSelected?.invoke()
            onTabSelected?.invoke(clamped)
            if (selectedEntry != null) {
                onFragmentTabSelected?.invoke(selectedEntry)
                onTabIdSelected?.invoke(selectedEntry.id)
            }
        }

    var activeIndex: Int
        get() = selectedIndex
        set(value) {
            selectedIndex = value
        }

    val activeFragment: Fragment?
        get() = _entries.getOrNull(selectedIndex)?.fragment

    var onTabSelected: ((Int) -> Unit)? = null
    var onFragmentTabSelected: ((FragmentTabEntry) -> Unit)? = null
    var onTabCloseRequested: ((FragmentTabEntry) -> Boolean)? = null
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
     * Adds a [Fragment] tab using a [Tab] component ([Tab], [CloseableTab], [BadgeTab], [PinnedTab], [EditableTab]).
     */
    fun addTab(tab: Tab, fragment: Fragment, index: Int = -1): FragmentTabContainer = apply {
        wireTabCallbacks(tab)
        val hostContainer = FragmentContainer(initialFragment = fragment, hostWindow = hostWindow)
        val entry = FragmentTabEntry(tab = tab, fragment = fragment, hostContainer = hostContainer)
        val insertIdx = if (index < 0 || index >= _entries.size) {
            _entries.add(entry)
            _entries.size - 1
        } else {
            _entries.add(index, entry)
            index
        }
        addChild(tab)
        addChild(hostContainer)
        tab.isSelected = (insertIdx == selectedIndex)

        val impl = typedNative<TabContainerNativeImpl<*>>()
        val tabImpl = tab.nativeImpl as? TabNativeImpl<*>
        val contentImpl = hostContainer.nativeImpl
        if (impl != null && contentImpl != null) {
            if (tabImpl != null) {
                impl.addTabNative(tabImpl, contentImpl, insertIdx)
            } else {
                impl.addTabNative(tab.displayTitle, contentImpl, insertIdx)
            }
        }
    }

    /**
     * Adds a [Fragment] tab using any custom [AbstractComponent] as the tab header component.
     */
    fun addTab(
        tabComponent: AbstractComponent,
        fragment: Fragment,
        id: String = "frag_tab_${_entries.size}",
        title: String = "",
        index: Int = -1
    ): FragmentTabContainer {
        val resolvedTab = if (tabComponent is Tab) {
            tabComponent
        } else {
            Tab(tabId = id, title = title, customComponent = tabComponent)
        }
        return addTab(resolvedTab, fragment, index)
    }

    /**
     * Adds a [Fragment] tab by title and optional icon.
     */
    fun addTab(
        title: String,
        fragment: Fragment,
        id: String = "frag_tab_${_entries.size}",
        icon: String? = null,
        closable: Boolean = false
    ): FragmentTabContainer = apply {
        val tab: Tab = if (closable) {
            CloseableTab(tabId = id, title = title, icon = icon, isCloseable = true)
        } else {
            Tab(tabId = id, title = title, icon = icon)
        }
        addTab(tab, fragment)
    }

    /**
     * Adds a closeable [Fragment] tab and returns the created [CloseableTab] header component.
     */
    fun addCloseableTab(
        title: String,
        fragment: Fragment,
        id: String = "frag_tab_${_entries.size}",
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
        addTab(tab, fragment)
        return tab
    }

    fun removeTabAt(index: Int): FragmentTabEntry? {
        if (index !in _entries.indices) return null
        val removed = _entries.removeAt(index)
        removed.tab.ownerNotifyUpdated = null
        removed.tab.ownerRequestClose = null
        removed.fragment.performDestroy()
        removeChild(removed.tab)
        removeChild(removed.hostContainer)
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
            val contentImpl = entry.hostContainer.nativeImpl ?: return@forEachIndexed
            val tabImpl = entry.tab.nativeImpl as? TabNativeImpl<*>
            if (tabImpl != null) {
                impl.addTabNative(tabImpl, contentImpl, idx)
            } else {
                impl.addTabNative(entry.tab.displayTitle, contentImpl, idx)
            }
        }
        _entries.getOrNull(selectedIndex)?.hostContainer?.ensureActiveFragmentStartedAndResumed()
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
