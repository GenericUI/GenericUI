package com.nexustools.genericui.nativeimpl.tabs

import com.nexustools.genericui.core.AbstractContainerNativeImpl
import com.nexustools.genericui.core.NativeImpl

/**
 * Native implementation contract for a tab header component ([com.nexustools.genericui.component.tabs.Tab],
 * [com.nexustools.genericui.component.tabs.CloseableTab], [com.nexustools.genericui.component.tabs.BadgeTab],
 * [com.nexustools.genericui.component.tabs.PinnedTab], [com.nexustools.genericui.component.tabs.EditableTab],
 * or a tab containing a custom [com.nexustools.genericui.core.AbstractComponent]).
 *
 * Translates directly to each platform's native tab item / tab header widget:
 * - `:swing`: `JTabbedPane` tab entry + `setTabComponentAt` (`JPanel` with native `JLabel`, close `JButton`, or custom `JComponent`)
 * - `:windows`: Win32 `TCITEMW` inside `WC_TABCONTROLW` (`comctl32.dll`) with native close button / child `HWND`
 * - `:macos`: AppKit `NSTabViewItem` with native `NSStackView` / close `NSButton` / custom `NSView`
 * - `:gtk`: GTK 4 / Libadwaita `AdwTabPage` / `GtkNotebookPage` with native `GtkBox` + `GtkButton("window-close-symbolic")`
 * - `:qt`: Qt 6 `QTabBar` item with `QTabBar::setTabText`, `setTabToolTip`, and `setTabButton(index, QTabBar::RightSide, widget)`
 * - `:android`: Material `TabLayout.Tab` with `setText`, `getOrCreateBadge`, and `setCustomView`
 * - `:ios`: UIKit `UITabBarItem` / `UISegmentedControl` item with `badgeValue` and custom `UIView`
 * - `:web`: Bootstrap `<li class="nav-item"><button class="nav-link">` with `.btn-close`, `.badge`, or custom DOM child
 */
interface TabNativeImpl<out W> : AbstractContainerNativeImpl<W> {
    fun setTabIdNative(id: String)
    fun setTitleNative(title: String)
    fun setIconNative(icon: String?)
    fun setTooltipNative(tooltip: String?)
    fun setClosableNative(closable: Boolean)
    fun setDirtyNative(dirty: Boolean)
    fun setPinnedNative(pinned: Boolean)
    fun setEditableNative(editable: Boolean)
    fun setBadgeNative(badgeText: String?, variantCss: String? = null)
    fun setSelectedNative(selected: Boolean)
    fun setCustomHeaderComponentNative(customImpl: NativeImpl<*>?)
    fun setOnCloseClicked(listener: (() -> Unit)?)
    fun setOnTitleEdited(listener: ((String) -> Unit)?)
}
