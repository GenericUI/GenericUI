package com.nexustools.genericui.component.fragment

import com.nexustools.genericui.component.dialog.DialogWindow
import com.nexustools.genericui.component.dock.PanelWindow
import com.nexustools.genericui.component.fragment.helper.FragmentHostFragment
import com.nexustools.genericui.component.fragment.helper.FragmentHostSystem
import com.nexustools.genericui.component.shell.MdiWindow
import com.nexustools.genericui.component.shell.Window
import com.nexustools.genericui.enums.fragment.FragmentLaunchMode
import com.nexustools.genericui.window.IWindow
import kotlin.reflect.KClass

/**
 * Central coordinator behind every `launchFragment(...)` / `finishFragment()` call on [IFragment],
 * [IWindow], and [com.nexustools.genericui.component.shell.IApplication].
 *
 * Resolution order for the window a fragment is launched relative to:
 * `targetWindow ?: sourceWindow ?: sourceFragment?.hostingWindow`.
 */
object FragmentLaunchCoordinator {
    /**
     * Optional backend hook (e.g. Android `startActivity`) consulted before in-process mounting.
     * When it returns `true` the launch is considered handled natively.
     */
    var platformLauncher: FragmentPlatformLauncher? = null

    /**
     * Instantiates [fragmentClass] via [FragmentRegistry] (assigning [IFragment.arguments] = [launchArgs])
     * and mounts it according to [launchMode].
     *
     * If [platformLauncher] handles the launch, the freshly instantiated (unmounted) instance is returned.
     */
    fun <T : IFragment> launch(
        sourceWindow: IWindow?,
        sourceFragment: IFragment?,
        fragmentClass: KClass<T>,
        launchArgs: FragmentBundle?,
        addToBackStack: Boolean,
        launchMode: FragmentLaunchMode,
        targetWindow: IWindow?
    ): T {
        val window = resolveWindow(sourceWindow, sourceFragment, targetWindow)
        val fragment = FragmentRegistry.instantiate(fragmentClass, launchArgs)
        if (platformLauncher?.launch(fragmentClass, launchArgs, launchMode, window) == true) {
            return fragment
        }
        return mount(window, sourceFragment, fragment, addToBackStack, launchMode)
    }

    /**
     * Mounts an already-constructed [fragment] according to [launchMode], assigning
     * [IFragment.arguments] = [launchArgs] when [launchArgs] is non-null.
     */
    fun <T : IFragment> launch(
        sourceWindow: IWindow?,
        sourceFragment: IFragment?,
        fragment: T,
        launchArgs: FragmentBundle?,
        addToBackStack: Boolean,
        launchMode: FragmentLaunchMode,
        targetWindow: IWindow?
    ): T {
        if (launchArgs != null) {
            fragment.arguments = launchArgs
        }
        val window = resolveWindow(sourceWindow, sourceFragment, targetWindow)
        if (platformLauncher?.launch(fragment::class, launchArgs, launchMode, window) == true) {
            return fragment
        }
        return mount(window, sourceFragment, fragment, addToBackStack, launchMode)
    }

    /**
     * Finishes [fragment]:
     * 1. If its [FragmentHost] has back stack entries (`backStackDepth > 0`), pops the back stack and returns `true`.
     * 2. Otherwise, if [fragment] is hosted in a secondary window (`hostingWindow?.ownerWindow != null`,
     *    `DialogWindow`, `PanelWindow`, or `MdiWindow`), closes that window and returns `true`.
     */
    @Suppress("USELESS_IS_CHECK")
    fun finish(fragment: IFragment): Boolean {
        val host = fragment.fragmentHost
        if (host != null && (host.currentFragment === fragment || host is FragmentHostFragment) && host.backStackDepth > 0) {
            return host.popFragment()
        }
        val fragObj = fragment as? Fragment
        val hostingWin: Any? = fragment.hostingWindow
            ?: fragment.attachedWindow
            ?: (fragObj?.parent?.parent as? DialogWindow)
            ?: (fragObj?.parent?.parent as? PanelWindow)
            ?: (fragObj?.parent?.parent as? MdiWindow)

        if (hostingWin != null) {
            val isSecondary = (hostingWin is IWindow && hostingWin.ownerWindow != null) ||
                hostingWin is DialogWindow ||
                hostingWin is PanelWindow ||
                hostingWin is MdiWindow
            if (isSecondary) {
                when (hostingWin) {
                    is IWindow -> hostingWin.close()
                    is DialogWindow -> hostingWin.close()
                    is PanelWindow -> hostingWin.close()
                    is MdiWindow -> hostingWin.close()
                }
                return true
            }
        }
        return false
    }

    private fun resolveWindow(
        sourceWindow: IWindow?,
        sourceFragment: IFragment?,
        targetWindow: IWindow?
    ): IWindow? = targetWindow ?: sourceWindow ?: sourceFragment?.hostingWindow

    private fun <T : IFragment> mount(
        window: IWindow?,
        sourceFragment: IFragment?,
        fragment: T,
        addToBackStack: Boolean,
        launchMode: FragmentLaunchMode
    ): T {
        val concrete = fragment as? Fragment ?: throw IllegalStateException(
            "${fragment::class.simpleName} must extend Fragment to be mounted in a FragmentContainer."
        )
        when (launchMode) {
            FragmentLaunchMode.SAME_WINDOW, FragmentLaunchMode.CLEAR_TASK -> {
                val delegatedHost: FragmentHost? = when {
                    concrete is FragmentHostFragment -> null
                    sourceFragment?.fragmentHost is FragmentHostFragment -> sourceFragment.fragmentHost
                    sourceFragment?.fragmentHost is FragmentHostSystem -> sourceFragment.fragmentHost
                    sourceFragment is FragmentHostFragment -> sourceFragment
                    window?.activeFragment is FragmentHostFragment -> window.activeFragment as FragmentHostFragment
                    else -> null
                }
                if (delegatedHost != null) {
                    when {
                        launchMode == FragmentLaunchMode.CLEAR_TASK -> delegatedHost.setFragment(concrete)
                        addToBackStack -> delegatedHost.pushFragment(concrete)
                        else -> delegatedHost.replaceFragment(concrete)
                    }
                    return fragment
                }

                val host = window ?: throw IllegalStateException(
                    "launchFragment(${fragment::class.simpleName}) requires a target IWindow."
                )
                val container = host.getOrCreateFragmentContainer()
                container.hostingIWindow = host
                when {
                    launchMode == FragmentLaunchMode.CLEAR_TASK -> container.setFragment(concrete)
                    addToBackStack -> container.pushFragment(concrete)
                    else -> container.replaceFragment(concrete)
                }
            }
            FragmentLaunchMode.NEW_WINDOW -> {
                val initialTitle = concrete.pageTitle.ifBlank {
                    window?.getTitle()?.takeIf { it.isNotBlank() } ?: "GenericUI"
                }
                val newWindow = Window(title = initialTitle)
                mountInCreatedWindow(newWindow, concrete)
                showCreatedWindow(newWindow, ownerWindow = (window as? Window) ?: sourceFragment?.attachedWindow)
            }
            FragmentLaunchMode.NEW_PANEL_WINDOW -> {
                val initialTitle = concrete.pageTitle.ifBlank { "Panel" }
                val ownerWin = (window as? Window) ?: sourceFragment?.attachedWindow
                val panelWindow = PanelWindow(title = initialTitle, ownerWindow = ownerWin)
                mountInCreatedWindow(panelWindow, concrete)
                showCreatedWindow(panelWindow, ownerWindow = ownerWin)
            }
            FragmentLaunchMode.NEW_DIALOG_WINDOW -> {
                val initialTitle = concrete.pageTitle
                val ownerWin = (window as? Window) ?: sourceFragment?.attachedWindow
                val dialogWindow = DialogWindow(title = initialTitle)
                mountInCreatedWindow(dialogWindow, concrete)
                showCreatedWindow(dialogWindow, ownerWindow = ownerWin)
            }
        }
        return fragment
    }

    @Suppress("USELESS_IS_CHECK")
    private fun mountInCreatedWindow(windowObj: Any, concrete: Fragment) {
        val iWin = windowObj as? IWindow
        if (iWin != null) {
            val container = iWin.getOrCreateFragmentContainer()
            container.hostingIWindow = iWin
            container.setFragment(concrete)
            return
        }
        when (windowObj) {
            is Window -> {
                val container = FragmentContainer(hostWindow = windowObj)
                windowObj.setContent(container)
                container.setFragment(concrete)
            }
            is DialogWindow -> {
                val container = FragmentContainer()
                windowObj.clearChildren()
                windowObj.addChild(container)
                container.setFragment(concrete)
            }
        }
    }

    @Suppress("USELESS_IS_CHECK")
    private fun showCreatedWindow(windowObj: Any, ownerWindow: Window?) {
        when (windowObj) {
            is IWindow -> windowObj.show()
            is DialogWindow -> windowObj.open(ownerWindow)
            is Window -> windowObj.show()
        }
    }
}
