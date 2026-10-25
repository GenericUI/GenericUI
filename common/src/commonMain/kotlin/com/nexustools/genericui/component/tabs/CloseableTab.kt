package com.nexustools.genericui.component.tabs

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.nativeimpl.tabs.TabNativeImpl

/**
 * A [Tab] with a native close button, dirty state indicator (`*`), and close-confirmation hook
 * ([onCloseRequested]), used inside [TabContainer] and [FragmentTabContainer] for IDE editors,
 * studio documents (such as HAEditor's `Main Canvas` and `Shared Object` tabs), and browser-style tabs.
 *
 * Translates directly to each platform's native closeable tab widget (`QTabBar` close button on `:qt`,
 * `AdwTabPage` / `GtkNotebook` close button on `:gtk`, `NSTabViewItem` close `NSButton` on `:macos`,
 * `WC_TABCONTROL` close button on `:windows`, `JTabbedPane` tab component with native close `JButton` on `:swing`,
 * closeable `TabLayout.Tab` on `:android`, `UIButton` close action on `:ios`, and `.btn-close` on `:web`).
 */
open class CloseableTab(
    tabId: String = "closeable_tab_${nextTabCounter()}",
    title: String = "",
    icon: String? = null,
    tooltip: String? = null,
    isCloseable: Boolean = true,
    isDirty: Boolean = false,
    customComponent: AbstractComponent? = null,
    var onCloseRequested: (() -> Boolean)? = null,
    var onClosed: (() -> Unit)? = null
) : Tab(
    tabId = tabId,
    title = title,
    icon = icon,
    tooltip = tooltip,
    customComponent = customComponent
) {

    var isCloseable: Boolean = isCloseable
        set(value) {
            field = value
            typedNative<TabNativeImpl<*>>()?.setClosableNative(value)
            ownerNotifyUpdated?.invoke(this)
        }

    var isClosable: Boolean
        get() = isCloseable
        set(value) {
            isCloseable = value
        }

    init {
        htmlTag = "genericui:closeabletab"
        this.isDirty = isDirty
    }

    /**
     * Requests closing this tab. Invokes [onCloseRequested] (if set) to allow vetoing (e.g. unsaved changes prompt),
     * then notifies the owning [TabContainer] or [FragmentTabContainer] to remove the tab and invokes [onClosed].
     */
    open fun requestClose(): Boolean {
        if (!isCloseable) return false
        val allowed = onCloseRequested?.invoke() ?: true
        if (!allowed) return false
        val closedByOwner = ownerRequestClose?.invoke(this) ?: true
        if (closedByOwner) {
            onClosed?.invoke()
        }
        return closedByOwner
    }

    override fun createNativeImpl(factory: NativeFactory): TabNativeImpl<*> =
        factory.createTab(closable = isCloseable)

    override fun syncStateToNative() {
        super.syncStateToNative()
        val impl = typedNative<TabNativeImpl<*>>() ?: return
        impl.setClosableNative(isCloseable)
        impl.setOnCloseClicked {
            requestClose()
        }
    }
}
