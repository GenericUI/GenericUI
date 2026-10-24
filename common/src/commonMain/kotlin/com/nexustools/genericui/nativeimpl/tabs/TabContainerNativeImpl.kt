package com.nexustools.genericui.nativeimpl.tabs

import com.nexustools.genericui.component.tabs.TabBarStyle
import com.nexustools.genericui.component.tabs.TabPosition
import com.nexustools.genericui.core.AbstractContainerNativeImpl
import com.nexustools.genericui.core.NativeImpl

/**
 * Native implementation contract for [com.nexustools.genericui.component.tabs.TabContainer]
 * and [com.nexustools.genericui.component.tabs.FragmentTabContainer].
 *
 * Uses the genuine native tab widget on each backend (`JTabbedPane` on `:swing`, `WC_TABCONTROL` on `:windows`,
 * `QTabWidget` on `:qt`, `NSTabView` on `:macos`, `AdwTabView`/`GtkNotebook` on `:gtk`, `TabLayout` on `:android`,
 * `UITabBar` on `:ios`, and `.nav-tabs` + `.tab-pane.fade` on `:web`), translating each [TabNativeImpl]
 * into the platform's native tab header component.
 */
interface TabContainerNativeImpl<out W> : AbstractContainerNativeImpl<W> {
    val supportsNativeFadeTransition: Boolean
        get() = true

    fun addTabNative(tabImpl: TabNativeImpl<*>, contentImpl: NativeImpl<*>, index: Int = -1) {
        addTabNative(title = "", contentImpl = contentImpl, index = index)
    }

    fun addTabNative(title: String, contentImpl: NativeImpl<*>, index: Int = -1)

    fun updateTabNative(index: Int, tabImpl: TabNativeImpl<*>) {}

    fun removeTabNative(index: Int)

    fun setTabTitleNative(index: Int, title: String)

    fun setTabPositionNative(position: TabPosition) {}

    fun setTabBarStyleNative(style: TabBarStyle) {}

    fun setSelectedTabIndex(index: Int, fadeTransition: Boolean = false)

    fun getSelectedTabIndex(): Int

    fun setOnTabSelected(listener: ((Int) -> Unit)?)

    fun setOnTabCloseRequested(listener: ((Int) -> Boolean)?) {}
}
