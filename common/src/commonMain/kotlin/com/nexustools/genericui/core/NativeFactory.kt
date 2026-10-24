package com.nexustools.genericui.core

import com.nexustools.genericui.nativeimpl.action.ButtonNativeImpl
import com.nexustools.genericui.nativeimpl.action.DropdownNativeImpl
import com.nexustools.genericui.nativeimpl.content.ImageNativeImpl
import com.nexustools.genericui.nativeimpl.content.TableNativeImpl
import com.nexustools.genericui.nativeimpl.dialog.DialogWindowNativeImpl
import com.nexustools.genericui.nativeimpl.dialog.FileBrowserDialogNativeImpl
import com.nexustools.genericui.nativeimpl.dialog.StandardDialogsNativeImpl
import com.nexustools.genericui.nativeimpl.dock.*
import com.nexustools.genericui.nativeimpl.feedback.ProgressBarNativeImpl
import com.nexustools.genericui.nativeimpl.feedback.SpinnerNativeImpl
import com.nexustools.genericui.nativeimpl.form.CheckboxNativeImpl
import com.nexustools.genericui.nativeimpl.form.RangeSliderNativeImpl
import com.nexustools.genericui.nativeimpl.form.SelectNativeImpl
import com.nexustools.genericui.nativeimpl.form.TextInputNativeImpl
import com.nexustools.genericui.nativeimpl.layout.ContainerNativeImpl
import com.nexustools.genericui.nativeimpl.layout.DividerNativeImpl
import com.nexustools.genericui.nativeimpl.layout.ScrollViewNativeImpl
import com.nexustools.genericui.nativeimpl.lazy.LazyGridNativeImpl
import com.nexustools.genericui.nativeimpl.lazy.LazyListNativeImpl
import com.nexustools.genericui.nativeimpl.lazy.TreeViewNativeImpl
import com.nexustools.genericui.nativeimpl.navigation.BottomNavigationBarNativeImpl
import com.nexustools.genericui.nativeimpl.navigation.NavbarNativeImpl
import com.nexustools.genericui.nativeimpl.tabs.TabContainerNativeImpl
import com.nexustools.genericui.nativeimpl.tabs.TabNativeImpl
import com.nexustools.genericui.nativeimpl.overlay.PopoverNativeImpl
import com.nexustools.genericui.nativeimpl.shell.*
import com.nexustools.genericui.nativeimpl.surface.CollapseNativeImpl
import com.nexustools.genericui.nativeimpl.system.*
import com.nexustools.genericui.nativeimpl.typography.MarkdownViewNativeImpl
import com.nexustools.genericui.nativeimpl.typography.TextNativeImpl

/**
 * Abstract factory interface implemented by all 8 platform backends (`:qt`, `:gtk`, `:windows`, `:macos`,
 * `:swing`, `:android`, `:ios`, `:web`).
 *
 * Components only request their `NativeImpl` from [NativeFactory] when they are connected to a visible
 * on-screen `Window` / `Application` and have `isVisible == true`.
 */
interface NativeFactory {
    val backendName: String

    // Shell & Window
    fun createApplication(): ApplicationNativeImpl<*>
    fun createWindow(): WindowNativeImpl<*>
    fun createMenuBar(): MenuBarNativeImpl<*>
    fun createMenu(): MenuNativeImpl<*>
    fun createMenuItem(checkable: Boolean = false, radio: Boolean = false): MenuItemNativeImpl<*>
    fun createContextMenu(): ContextMenuNativeImpl<*>
    fun createSplitPane(): SplitPaneNativeImpl<*>

    // Dockable Panel System (GIMP / Photoshop Studio)
    fun createDockWorkspace(): DockWorkspaceNativeImpl<*>
    fun createDockArea(): DockAreaNativeImpl<*>
    fun createDockGroup(): DockGroupNativeImpl<*>
    fun createDockPanel(): DockPanelNativeImpl<*>
    fun createDockWindow(): DockWindowNativeImpl<*>
    fun createToolPalette(): ToolPaletteNativeImpl<*>

    // Layout
    fun createContainer(classPrefix: String = "container"): ContainerNativeImpl<*>
    fun createScrollView(): ScrollViewNativeImpl<*>
    fun createDivider(): DividerNativeImpl<*>

    // Typography & Content
    fun createText(tagName: String = "span", classPrefix: String = ""): TextNativeImpl<*>
    fun createMarkdownView(): MarkdownViewNativeImpl<*>
    fun createImage(): ImageNativeImpl<*>
    fun createTable(): TableNativeImpl<*>

    // Action & Form
    fun createButton(classPrefix: String = "btn"): ButtonNativeImpl<*>
    fun createDropdown(): DropdownNativeImpl<*>
    fun createTextInput(multiline: Boolean = false): TextInputNativeImpl<*>
    fun createSelect(): SelectNativeImpl<*>
    fun createCheckbox(switchStyle: Boolean = false, radioStyle: Boolean = false): CheckboxNativeImpl<*>
    fun createRangeSlider(): RangeSliderNativeImpl<*>

    // Surface, Tabs, Navigation, Feedback & Overlay
    fun createCollapse(): CollapseNativeImpl<*>
    fun createTab(closable: Boolean = false): TabNativeImpl<*>
    fun createTabContainer(): TabContainerNativeImpl<*>
    fun createNavbar(): NavbarNativeImpl<*>
    fun createBottomNavigationBar(): BottomNavigationBarNativeImpl<*>
    fun createProgressBar(): ProgressBarNativeImpl<*>
    fun createSpinner(): SpinnerNativeImpl<*>
    fun createPopover(): PopoverNativeImpl<*>

    // Dialogs & Native File/Folder Browser
    fun createDialogWindow(): DialogWindowNativeImpl<*>
    fun createFileBrowserDialog(): FileBrowserDialogNativeImpl
    fun createStandardDialogs(): StandardDialogsNativeImpl

    // Lazy Virtualized Lists, Grids & Trees
    fun createLazyList(): LazyListNativeImpl<*>
    fun createLazyGrid(): LazyGridNativeImpl<*>
    fun createTreeView(): TreeViewNativeImpl<*>

    // System Services
    fun createPermissionManager(): PermissionManagerNativeImpl
    fun createSystemTray(): SystemTrayNativeImpl
    fun createNotificationManager(): NotificationManagerNativeImpl
    fun createNetworkMonitor(): NetworkMonitorNativeImpl
}
