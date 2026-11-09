package com.nexustools.genericui.component.tabs

import com.nexustools.genericui.component.media.Image
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.nativeimpl.tabs.TabContainerNativeImpl
import com.nexustools.genericui.nativeimpl.tabs.TabNativeImpl
import com.nexustools.genericui.resources.ResourceID

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
    tabBarStyle: TabBarStyle = TabBarStyle.TABS,
    var adaptiveTabPosition: Boolean = false
) : AbstractContainer() {
    private val _entries = mutableListOf<TabEntry>()
    private val registeredNativeEntries = HashSet<TabEntry>()
    val entries: List<TabEntry> get() = _entries
    val tabs: List<TabEntry> get() = _entries

    var tabPosition: TabPosition = tabPosition
        set(value) {
            field = value
            syncTabPositionClass(value)
            typedNative<TabContainerNativeImpl<*>>()?.setTabPositionNative(value)
        }

    val isTabsOnTop: Boolean
        get() = tabPosition == TabPosition.TOP

    val isTabsOnBottom: Boolean
        get() = tabPosition == TabPosition.BOTTOM

    fun setTabsOnTop(): TabContainer = apply {
        tabPosition = TabPosition.TOP
    }

    fun setTabsOnBottom(): TabContainer = apply {
        tabPosition = TabPosition.BOTTOM
    }

    fun updateAdaptiveTabPosition(inColumnMode: Boolean): TabContainer = apply {
        tabPosition = if (inColumnMode) TabPosition.TOP else TabPosition.BOTTOM
    }

    private fun syncTabPositionClass(position: TabPosition) {
        for (pos in TabPosition.entries) {
            if (pos != position) {
                removeClass(pos.cssClass)
            }
        }
        addClass(position.cssClass)
    }

    init {
        htmlTag = "genericui:tabcontainer"
        syncTabPositionClass(this.tabPosition)
    }

    var tabBarStyle: TabBarStyle = tabBarStyle
        set(value) {
            field = value
            typedNative<TabContainerNativeImpl<*>>()?.setTabBarStyleNative(value)
        }

    var fullWidthTabs: Boolean = false
        set(value) {
            field = value
            typedNative<TabContainerNativeImpl<*>>()?.setFullWidthTabsNative(value)
        }

    private var isUpdatingSelectionFromNative: Boolean = false
    private var isSettingSelectionProgrammatically: Boolean = false

    var selectedIndex: Int = 0
        set(value) {
            if (_entries.isEmpty()) {
                field = 0
                return
            }
            val clamped = value.coerceIn(0, (_entries.size - 1).coerceAtLeast(0))
            val candidate = _entries.getOrNull(clamped)
            if (candidate != null && !candidate.tab.isSelectable) {
                (candidate.tab as? ButtonTab)?.performClick()
                syncTabAndContentVisibility()
                if (field in _entries.indices && _entries[field].tab.isSelectable) {
                    isSettingSelectionProgrammatically = true
                    try {
                        typedNative<TabContainerNativeImpl<*>>()?.setSelectedTabIndex(field, false)
                    } finally {
                        isSettingSelectionProgrammatically = false
                    }
                }
                return
            }
            if (field == clamped &&
                _entries.getOrNull(clamped)?.tab?.isSelected == true &&
                (isUpdatingSelectionFromNative || isSettingSelectionProgrammatically)
            ) {
                syncTabAndContentVisibility()
                return
            }
            field = clamped
            syncTabAndContentVisibility()
            val selectedEntry = _entries.getOrNull(clamped)
            selectedEntry?.content?.let { activeContent ->
                if (isVisible) {
                    val sheet = lastAppliedStyleSheet
                    if (sheet != null) {
                        activeContent.applyStyleSheet(sheet, lastStyleSheetViewportWidthPx)
                    }
                    activeContent.ensureNativeImpl()
                }
            }
            if (!isUpdatingSelectionFromNative && !isSettingSelectionProgrammatically) {
                isSettingSelectionProgrammatically = true
                try {
                    typedNative<TabContainerNativeImpl<*>>()?.setSelectedTabIndex(clamped, fadeTransition)
                } finally {
                    isSettingSelectionProgrammatically = false
                }
            }
            selectedEntry?.tab?.onSelected?.invoke()
            onTabSelected?.invoke(clamped)
            if (selectedEntry != null) {
                onTabEntrySelected?.invoke(selectedEntry)
                onTabIdSelected?.invoke(selectedEntry.id)
            }
        }

    private fun realizeTabContentNativeImpl(content: AbstractComponent): com.nexustools.genericui.core.NativeImpl<*>? {
        if (!this.isVisible) return content.nativeImpl
        content.nativeImpl?.let { return it }
        if (!content.isVisible) {
            content.isVisible = true
        }
        val sheet = lastAppliedStyleSheet
        if (sheet != null && content.lastAppliedStyleSheet !== sheet) {
            content.applyStyleSheet(sheet, lastStyleSheetViewportWidthPx)
        }
        val impl = content.ensureNativeImpl()
        if (sheet != null) {
            content.applyStyleSheet(sheet, lastStyleSheetViewportWidthPx)
        }
        return impl
    }

    private fun syncTabAndContentVisibility() {
        _entries.forEachIndexed { idx, entry ->
            val isActive = (idx == selectedIndex) && entry.tab.isSelectable
            entry.tab.isSelected = isActive
            entry.content?.let { content ->
                content.isVisible = isActive
                if (isActive) {
                    content.removeClass("d-none")
                    if (content.getStyle().getDisplay() == com.nexustools.genericui.enums.css.CssDisplay.NONE) {
                        content.getStyle().setDisplay(null)
                    }
                } else {
                    content.addClass("d-none")
                }
            }
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
        require(tab.supportsContent && tab !is ButtonTab) {
            "ButtonTab does not support tab content; use addButtonTab() instead."
        }
        tab.isVisible = true
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
        if (selectedIndex !in _entries.indices || !_entries[selectedIndex].tab.isSelectable) {
            val firstSelectable = _entries.indexOfFirst { it.tab.isSelectable }
            if (firstSelectable >= 0) {
                selectedIndex = firstSelectable
            }
        }

        val impl = typedNative<TabContainerNativeImpl<*>>()
        if (impl != null && isVisible && entry !in registeredNativeEntries) {
            val tabImpl = (tab.nativeImpl ?: tab.ensureNativeImpl()) as? TabNativeImpl<*>
            val contentImpl = realizeTabContentNativeImpl(content)
            if (contentImpl != null) {
                if (tabImpl != null) {
                    impl.addTabNative(tabImpl, contentImpl, insertIdx)
                } else {
                    impl.addTabNative(tab.displayTitle, contentImpl, insertIdx)
                }
                registeredNativeEntries.add(entry)
            }
        }
        syncTabAndContentVisibility()
    }

    /**
     * Adds a [ButtonTab] to the tab strip that invokes its [ButtonTab.onClick] callback without
     * switching tabs or mounting a content pane.
     */
    fun addButtonTab(tab: ButtonTab, index: Int = -1): ButtonTab {
        tab.isVisible = true
        wireTabCallbacks(tab)
        val entry = TabEntry(tab = tab, content = null)
        val insertIdx = if (index < 0 || index >= _entries.size) {
            _entries.add(entry)
            _entries.size - 1
        } else {
            _entries.add(index, entry)
            index
        }
        addChild(tab)
        tab.isSelected = false

        val impl = typedNative<TabContainerNativeImpl<*>>()
        if (impl != null && isVisible && entry !in registeredNativeEntries) {
            val tabImpl = (tab.nativeImpl ?: tab.ensureNativeImpl()) as? TabNativeImpl<*>
            if (tabImpl != null) {
                impl.addButtonTabNative(tabImpl, insertIdx)
                registeredNativeEntries.add(entry)
            }
        }
        syncTabAndContentVisibility()
        return tab
    }

    /**
     * Convenience overload to create and add a [ButtonTab] directly to this [TabContainer].
     */
    fun addButtonTab(
        title: String,
        id: String = "button_tab_${_entries.size}",
        icon: String? = null,
        iconRes: ResourceID? = null,
        image: Image? = null,
        tintImageToTheme: Boolean = true,
        iconSizePx: Int = image?.widthPx ?: image?.heightPx ?: 20,
        iconPlacement: TabIconPlacement = TabIconPlacement.START,
        index: Int = -1,
        onClick: () -> Unit
    ): ButtonTab {
        val buttonTab = ButtonTab(
            title = title,
            id = id,
            icon = icon,
            iconRes = iconRes,
            image = image,
            tintImageToTheme = tintImageToTheme,
            iconSizePx = iconSizePx,
            iconPlacement = iconPlacement,
            onClick = onClick
        )
        return addButtonTab(buttonTab, index)
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
     * Adds a standard [Tab] by title and optional icon or [Image] / [ResourceID].
     */
    fun addTab(
        title: String,
        content: AbstractComponent,
        id: String = "tab_${_entries.size}",
        icon: String? = null,
        closable: Boolean = false,
        iconRes: ResourceID? = null,
        image: Image? = null,
        tintImageToTheme: Boolean = true,
        iconSizePx: Int = image?.widthPx ?: image?.heightPx ?: 20,
        iconPlacement: TabIconPlacement = TabIconPlacement.START
    ): TabContainer = apply {
        val tab: Tab = if (closable) {
            CloseableTab(
                tabId = id,
                title = title,
                icon = icon,
                isCloseable = true,
                iconRes = iconRes,
                image = image,
                tintImageToTheme = tintImageToTheme,
                iconSizePx = iconSizePx,
                iconPlacement = iconPlacement
            )
        } else {
            Tab(
                tabId = id,
                title = title,
                icon = icon,
                iconRes = iconRes,
                image = image,
                tintImageToTheme = tintImageToTheme,
                iconSizePx = iconSizePx,
                iconPlacement = iconPlacement
            )
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
        iconRes: ResourceID? = null,
        image: Image? = null,
        tintImageToTheme: Boolean = true,
        iconSizePx: Int = image?.widthPx ?: image?.heightPx ?: 20,
        iconPlacement: TabIconPlacement = TabIconPlacement.START,
        onCloseRequested: (() -> Boolean)? = null
    ): CloseableTab {
        val tab = CloseableTab(
            tabId = id,
            title = title,
            icon = icon,
            isCloseable = isCloseable,
            isDirty = isDirty,
            customComponent = customComponent,
            iconRes = iconRes,
            image = image,
            tintImageToTheme = tintImageToTheme,
            iconSizePx = iconSizePx,
            iconPlacement = iconPlacement,
            onCloseRequested = onCloseRequested
        )
        addTab(tab, content)
        return tab
    }

    fun removeTabAt(index: Int): TabEntry? {
        if (index !in _entries.indices) return null
        val removed = _entries.removeAt(index)
        val wasRegistered = registeredNativeEntries.remove(removed)
        removed.tab.ownerNotifyUpdated = null
        removed.tab.ownerRequestClose = null
        removeChild(removed.tab)
        removed.content?.let { removeChild(it) }
        if (wasRegistered) {
            typedNative<TabContainerNativeImpl<*>>()?.removeTabNative(index)
        }
        if (_entries.isNotEmpty()) {
            val clamped = selectedIndex.coerceIn(0, _entries.lastIndex)
            if (_entries[clamped].tab.isSelectable) {
                selectedIndex = clamped
            } else {
                val fallback = (clamped downTo 0).firstOrNull { _entries[it].tab.isSelectable }
                    ?: (clamped + 1.._entries.lastIndex).firstOrNull { _entries[it].tab.isSelectable }
                if (fallback != null) {
                    selectedIndex = fallback
                } else {
                    syncTabAndContentVisibility()
                }
            }
        }
        return removed
    }

    fun clearTabs() {
        val impl = typedNative<TabContainerNativeImpl<*>>()
        for (i in _entries.lastIndex downTo 0) {
            val removed = _entries.removeAt(i)
            val wasRegistered = registeredNativeEntries.remove(removed)
            removed.tab.ownerNotifyUpdated = null
            removed.tab.ownerRequestClose = null
            removeChild(removed.tab)
            removed.content?.let { removeChild(it) }
            if (wasRegistered) {
                impl?.removeTabNative(i)
            }
        }
        registeredNativeEntries.clear()
        selectedIndex = 0
    }

    fun indexOfTab(tabId: String): Int =
        _entries.indexOfFirst { it.id == tabId }

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
        val impl = typedNative<TabContainerNativeImpl<*>>()
        impl?.setTabTitleNative(idx, entry.tab.displayTitle)
        (entry.tab.nativeImpl as? TabNativeImpl<*>)?.let { impl?.updateTabNative(idx, it) }
    }

    fun setTabImage(tabId: String, image: Image?, tintToTheme: Boolean = true) {
        val entry = _entries.firstOrNull { it.id == tabId } ?: return
        entry.tab.setImage(image, tintToTheme)
    }

    fun setTabIcon(tabId: String, resId: ResourceID, tintToTheme: Boolean = true) {
        val entry = _entries.firstOrNull { it.id == tabId } ?: return
        entry.tab.setImage(resId, tintToTheme)
    }

    fun setActiveTab(tabId: String) {
        selectTab(tabId)
    }

    fun selectTab(tabId: String): Boolean {
        val idx = indexOfTab(tabId)
        if (idx < 0) return false
        val entry = _entries[idx]
        if (!entry.tab.isSelectable) {
            (entry.tab as? ButtonTab)?.performClick()
            return false
        }
        selectedIndex = idx
        return true
    }

    fun selectTab(tab: Tab) {
        if (!tab.isSelectable) {
            (tab as? ButtonTab)?.performClick()
            return
        }
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

    protected override fun createNativeImpl(factory: NativeFactory): TabContainerNativeImpl<*> =
        factory.createTabContainer()

    protected override fun onAttachNative(impl: com.nexustools.genericui.core.NativeImpl<*>) {
        registeredNativeEntries.clear()
        super.onAttachNative(impl)
    }

    override var isVisible: Boolean
        get() = super.isVisible
        set(value) {
            val wasVisible = super.isVisible
            super.isVisible = value
            if (value && !wasVisible && typedNative<TabContainerNativeImpl<*>>() != null) {
                syncStateToNative()
            }
        }

    override fun syncStateToNative() {
        super.syncStateToNative()
        val impl = typedNative<TabContainerNativeImpl<*>>() ?: return
        impl.setTabPositionNative(tabPosition)
        impl.setTabBarStyleNative(tabBarStyle)
        impl.setFullWidthTabsNative(fullWidthTabs)
        _entries.forEachIndexed { idx, entry ->
            entry.tab.isVisible = true
            val tabImpl = (entry.tab.nativeImpl ?: entry.tab.ensureNativeImpl()) as? TabNativeImpl<*>
            if (!entry.tab.supportsContent || entry.content == null) {
                if (tabImpl != null && entry !in registeredNativeEntries) {
                    impl.addButtonTabNative(tabImpl, idx)
                    registeredNativeEntries.add(entry)
                }
            } else {
                val contentImpl = realizeTabContentNativeImpl(entry.content) ?: return@forEachIndexed
                if (entry !in registeredNativeEntries) {
                    if (tabImpl != null) {
                        impl.addTabNative(tabImpl, contentImpl, idx)
                    } else {
                        impl.addTabNative(entry.tab.displayTitle, contentImpl, idx)
                    }
                    registeredNativeEntries.add(entry)
                }
            }
        }
        syncTabAndContentVisibility()
        isSettingSelectionProgrammatically = true
        try {
            if (selectedIndex in _entries.indices && _entries[selectedIndex].tab.isSelectable) {
                impl.setSelectedTabIndex(selectedIndex, fadeTransition)
            }
        } finally {
            isSettingSelectionProgrammatically = false
        }
        impl.setOnTabSelected { idx ->
            val entry = _entries.getOrNull(idx)
            if (entry != null && !entry.tab.isSelectable) {
                (entry.tab as? ButtonTab)?.performClick()
                isSettingSelectionProgrammatically = true
                try {
                    if (selectedIndex in _entries.indices && _entries[selectedIndex].tab.isSelectable) {
                        impl.setSelectedTabIndex(selectedIndex, false)
                    }
                } finally {
                    isSettingSelectionProgrammatically = false
                }
                return@setOnTabSelected
            }
            if (!isSettingSelectionProgrammatically && !isUpdatingSelectionFromNative && selectedIndex != idx) {
                isUpdatingSelectionFromNative = true
                try {
                    selectedIndex = idx
                } finally {
                    isUpdatingSelectionFromNative = false
                }
            }
        }
        impl.setOnTabCloseRequested { idx ->
            val entry = _entries.getOrNull(idx) ?: return@setOnTabCloseRequested false
            closeTab(entry.id)
        }
    }
}
