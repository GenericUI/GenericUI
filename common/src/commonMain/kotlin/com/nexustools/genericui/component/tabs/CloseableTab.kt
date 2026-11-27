package com.nexustools.genericui.component.tabs

import com.nexustools.genericui.component.media.Image
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.resources.ResourceID

/**
 * A [Tab] with a native close button, dirty state indicator (`*`), and close-confirmation hook
 * ([onCloseRequested]), used inside [TabContainer] and [FragmentTabContainer] for IDE editors,
 * studio document tabs, and browser-style tabs.
 *
 * Translates directly to each platform's native closeable tab widget (`QTabBar` close button on `:kde`,
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
    iconRes: ResourceID? = null,
    image: Image? = null,
    tintImageToTheme: Boolean? = null,
    iconSizePx: Int = image?.widthPx ?: image?.heightPx ?: 20,
    iconPlacement: TabIconPlacement = TabIconPlacement.START,
    onCloseRequested: (() -> Boolean)? = null,
    onClosed: (() -> Unit)? = null
) : Tab(
    tabId = tabId,
    title = title,
    icon = icon,
    tooltip = tooltip,
    customComponent = customComponent,
    iconRes = iconRes,
    image = image,
    tintImageToTheme = tintImageToTheme,
    iconSizePx = iconSizePx,
    iconPlacement = iconPlacement,
    closable = isCloseable,
    onCloseRequested = onCloseRequested,
    onClosed = onClosed
) {

    init {
        htmlTag = "genericui:closeabletab"
        this.isDirty = isDirty
    }

    override fun requestClose(): Boolean = super.requestClose()
}
