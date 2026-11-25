package com.nexustools.genericui.component.fragment

import com.nexustools.genericui.component.dialog.DialogWindow
import com.nexustools.genericui.component.dock.PanelWindow
import com.nexustools.genericui.component.fragment.helper.FragmentHostFragment
import com.nexustools.genericui.component.fragment.helper.FragmentHostSystem
import com.nexustools.genericui.component.shell.Application
import com.nexustools.genericui.component.shell.MdiWindow
import com.nexustools.genericui.component.shell.Window
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.core.GenericUiExtensionRegistry
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.exception.FragmentMultipleWindowsUnsupportedException
import com.nexustools.genericui.exception.MultipleWindowsUnsupportedException
import com.nexustools.genericui.exception.NativeUninitializedException
import com.nexustools.genericui.exception.UnsupportedBackendCapabilityException
import com.nexustools.genericui.window.IWindow
import kotlin.reflect.KClass

/**
 * Central coordinator behind every `startFragment(...)` / `finishFragment()` call on [IFragment],
 * [IWindow], and [com.nexustools.genericui.component.shell.IApplication].
 *
 * Resolution order for the window a fragment is started relative to:
 * `targetWindow ?: sourceWindow ?: sourceFragment?.hostingWindow`.
 */
object FragmentLaunchCoordinator {
    /**
     * Optional backend hook (e.g. Android `startActivity`) consulted before in-process mounting.
     * When it returns `true` the launch is considered handled natively.
     */
    var platformLauncher: FragmentPlatformLauncher? = null

    private val openFragmentWindows = mutableListOf<Pair<KClass<out IFragment>, Window>>()
    private val openDialogWindows = mutableListOf<Pair<KClass<out IFragment>, DialogWindow>>()

    /**
     * Instantiates [fragmentClass] via [FragmentRegistry] (assigning [IFragment.arguments] = [launchArgs])
     * and presents it in the current window, a new additional top-level window (when [openAdditionalWindow]
     * is `true`), or an overlay/standalone [DialogWindow] (when fragment metadata marks `dialog = true`).
     *
     * If [platformLauncher] handles the launch, the freshly instantiated (unmounted) instance is returned.
     */
    fun <T : IFragment> start(
        sourceWindow: IWindow?,
        sourceFragment: IFragment?,
        fragmentClass: KClass<T>,
        launchArgs: FragmentBundle?,
        openAdditionalWindow: Boolean,
        addToBackStack: Boolean,
        clearTask: Boolean,
        targetWindow: IWindow?
    ): T {
        val resolvedFactory = resolveFactory(sourceWindow, targetWindow, sourceFragment)
            ?: throw NativeUninitializedException("FragmentLaunchCoordinator", "startFragment")
        val isDialog = FragmentRegistry.isDialogFragment(fragmentClass)
        if (openAdditionalWindow) {
            if (!resolvedFactory.supportsMultipleWindows) {
                throw MultipleWindowsUnsupportedException(resolvedFactory.backendTarget.displayName)
            }
            if (!FragmentRegistry.supportsMultipleWindows(fragmentClass)) {
                throw FragmentMultipleWindowsUnsupportedException(FragmentRegistry.resolveFragmentId(fragmentClass))
            }
        }
        if (isDialog && !resolvedFactory.supportsDialogWindows) {
            throw UnsupportedBackendCapabilityException("dialogWindows", resolvedFactory.backendTarget.displayName)
        }
        val window = resolveWindow(sourceWindow, sourceFragment, targetWindow)
        if (isDialog && !openAdditionalWindow) {
            findOpenDialog(fragmentClass)?.let { (existingDialog, existingFragment) ->
                if (launchArgs != null) {
                    existingFragment.arguments = launchArgs
                }
                existingDialog.bringToFront()
                @Suppress("UNCHECKED_CAST")
                return existingFragment as T
            }
        }
        val fragment = FragmentRegistry.instantiate(fragmentClass, launchArgs)
        if (platformLauncher?.launch(fragmentClass, launchArgs, openAdditionalWindow, clearTask, isDialog, window) == true) {
            return fragment
        }
        return mount(
            factory = resolvedFactory,
            window = window,
            sourceFragment = sourceFragment,
            fragment = fragment,
            openAdditionalWindow = openAdditionalWindow,
            addToBackStack = addToBackStack,
            clearTask = clearTask,
            isDialog = isDialog
        )
    }

    /**
     * Starts an already-constructed [fragment], assigning [IFragment.arguments] = [launchArgs]
     * when [launchArgs] is non-null.
     */
    fun <T : IFragment> start(
        sourceWindow: IWindow?,
        sourceFragment: IFragment?,
        fragment: T,
        launchArgs: FragmentBundle?,
        openAdditionalWindow: Boolean,
        addToBackStack: Boolean,
        clearTask: Boolean,
        targetWindow: IWindow?
    ): T {
        val resolvedFactory = resolveFactory(sourceWindow, targetWindow, sourceFragment)
            ?: throw NativeUninitializedException("FragmentLaunchCoordinator", "startFragment")
        val fragmentClass = fragment::class
        val isDialog = FragmentRegistry.isDialogFragment(fragmentClass)
        if (openAdditionalWindow) {
            if (!resolvedFactory.supportsMultipleWindows) {
                throw MultipleWindowsUnsupportedException(resolvedFactory.backendTarget.displayName)
            }
            if (!FragmentRegistry.supportsMultipleWindows(fragmentClass)) {
                throw FragmentMultipleWindowsUnsupportedException(FragmentRegistry.resolveFragmentId(fragmentClass))
            }
        }
        if (isDialog && !resolvedFactory.supportsDialogWindows) {
            throw UnsupportedBackendCapabilityException("dialogWindows", resolvedFactory.backendTarget.displayName)
        }
        if (launchArgs != null) {
            fragment.arguments = launchArgs
        }
        val window = resolveWindow(sourceWindow, sourceFragment, targetWindow)
        if (isDialog && !openAdditionalWindow) {
            findOpenDialog(fragmentClass)?.let { (existingDialog, existingFragment) ->
                if (existingFragment !== fragment) {
                    if (launchArgs != null) {
                        existingFragment.arguments = launchArgs
                    }
                    existingDialog.bringToFront()
                    @Suppress("UNCHECKED_CAST")
                    return existingFragment as T
                }
            }
        }
        if (platformLauncher?.launch(fragmentClass, launchArgs, openAdditionalWindow, clearTask, isDialog, window) == true) {
            return fragment
        }
        return mount(
            factory = resolvedFactory,
            window = window,
            sourceFragment = sourceFragment,
            fragment = fragment,
            openAdditionalWindow = openAdditionalWindow,
            addToBackStack = addToBackStack,
            clearTask = clearTask,
            isDialog = isDialog
        )
    }

    /**
     * Returns `true` when any tracked secondary fragment window or dialog window remains open.
     */
    fun hasOpenFragments(): Boolean {
        pruneClosedTrackedWindows()
        return openFragmentWindows.isNotEmpty() || openDialogWindows.isNotEmpty()
    }

    /**
     * Finishes [fragment]:
     * 1. If its [FragmentHost] has back stack entries (`backStackDepth > 0`), pops the back stack and returns `true`.
     * 2. Otherwise, if [fragment] is hosted in a secondary window (an additional `Window` opened via
     *    `openAdditionalWindow`, `DialogWindow`, `PanelWindow`, or `MdiWindow`), closes that window,
     *    checks if the application should exit when no fragments remain, and returns `true`.
     * 3. Otherwise, if [fragment] is the root fragment hosted in [Application.mainWindow], clears it,
     *    hides `mainWindow` when secondary/dialog fragments remain or closes `mainWindow` otherwise,
     *    checks if the application should exit when no fragments remain, and returns `true`.
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
                (hostingWin is Window && openFragmentWindows.any { it.second === hostingWin }) ||
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
                openFragmentWindows.removeAll { it.second === hostingWin }
                openDialogWindows.removeAll { it.second === hostingWin }
                Application.current?.checkExitIfNoFragmentsOpen()
                return true
            }
        }
        val app = Application.current
        val mainWin = app?.mainWindow
        if (app != null && mainWin != null && (hostingWin === mainWin || mainWin.fragmentContainer?.currentFragment === fragment)) {
            val hasOtherOpenFragments = hasOpenFragments() ||
                mainWin.attachedDialogWindows.any {
                    !it.isDisposed && it.isWindowShown && it.isVisible && it.fragmentContainer?.currentFragment != null
                } ||
                GenericUiExtensionRegistry.hasAnyExtensionOpenFragments(app)
            mainWin.fragmentContainer?.clearCurrentFragment()
            if (hasOtherOpenFragments) {
                mainWin.hide()
            } else {
                mainWin.close()
            }
            app.checkExitIfNoFragmentsOpen()
            return true
        }
        return false
    }

    /** Closes any tracked secondary fragment/dialog windows and clears coordinator state between unit tests. */
    fun resetForTests(clearPlatformLauncher: Boolean = false) {
        for ((_, dw) in openDialogWindows.toList()) {
            runCatching { dw.close() }
        }
        openDialogWindows.clear()
        for ((_, w) in openFragmentWindows.toList()) {
            runCatching { w.close() }
        }
        openFragmentWindows.clear()
        if (clearPlatformLauncher) {
            platformLauncher = null
        }
    }

    private fun resolveFactory(
        sourceWindow: IWindow?,
        targetWindow: IWindow?,
        sourceFragment: IFragment?
    ): NativeFactory? {
        val targetExplicit = (targetWindow as? Window)?.let { it.ExplicitFactory ?: it.resolveActiveFactory() }
            ?: (targetWindow as? AbstractComponent)?.resolveActiveFactory()
        if (targetExplicit != null) return targetExplicit

        val sourceExplicit = (sourceWindow as? Window)?.let { it.ExplicitFactory ?: it.resolveActiveFactory() }
            ?: (sourceWindow as? AbstractComponent)?.resolveActiveFactory()
        if (sourceExplicit != null) return sourceExplicit

        val fragmentExplicit = (sourceFragment as? AbstractComponent)?.resolveActiveFactory()
        if (fragmentExplicit != null) return fragmentExplicit

        return GenericUI.activeFactory
    }

    private fun pruneClosedTrackedWindows() {
        openDialogWindows.removeAll { (_, dw) ->
            dw.isDisposed || !dw.isWindowShown || !dw.isVisible || dw.fragmentContainer?.currentFragment == null
        }
        openFragmentWindows.removeAll { (_, w) ->
            w.isDisposed || !w.isWindowShown || !w.isVisible || w.fragmentContainer?.currentFragment == null
        }
    }

    private fun findOpenDialog(fragmentClass: KClass<out IFragment>): Pair<DialogWindow, IFragment>? {
        pruneClosedTrackedWindows()
        for ((trackedClass, dw) in openDialogWindows) {
            if (trackedClass == fragmentClass) {
                val activeFrag = dw.fragmentContainer?.currentFragment
                if (activeFrag != null) {
                    return dw to activeFrag
                }
            }
        }
        return null
    }

    private fun resolveWindow(
        sourceWindow: IWindow?,
        sourceFragment: IFragment?,
        targetWindow: IWindow?
    ): IWindow? = targetWindow ?: sourceWindow ?: sourceFragment?.hostingWindow

    private fun <T : IFragment> mount(
        factory: NativeFactory,
        window: IWindow?,
        sourceFragment: IFragment?,
        fragment: T,
        openAdditionalWindow: Boolean,
        addToBackStack: Boolean,
        clearTask: Boolean,
        isDialog: Boolean
    ): T {
        val concrete = fragment as? Fragment ?: throw IllegalStateException(
            "${fragment::class.simpleName} must extend Fragment to be mounted in a FragmentContainer."
        )
        if (isDialog) {
            val initialTitle = concrete.pageTitle.ifBlank {
                FragmentRegistry.getMetadata(fragment::class)?.label.orEmpty()
            }
            val dialogWindow = DialogWindow(title = initialTitle)
            val container = dialogWindow.getOrCreateFragmentContainer()
            container.hostingIWindow = dialogWindow
            container.setFragment(concrete)

            val candidateFromSource = (window as? Window ?: sourceFragment?.attachedWindow)?.takeIf { candidate ->
                candidate.isWindowShown &&
                    candidate.isVisible &&
                    (candidate.fragmentContainer?.currentFragment != null || candidate.getContent() != null)
            }
            val resolvedOwner = candidateFromSource ?: Application.current?.mainWindow?.takeIf { mainWin ->
                mainWin.isWindowShown &&
                    mainWin.isVisible &&
                    (mainWin.fragmentContainer?.currentFragment != null || mainWin.getContent() != null)
            }
            openDialogWindows.add(fragment::class to dialogWindow)
            if (resolvedOwner == null && GenericUI.activeFactory == null) {
                dialogWindow.show(factory)
            } else {
                dialogWindow.open(resolvedOwner)
            }
            return fragment
        }

        if (openAdditionalWindow) {
            val initialTitle = concrete.pageTitle.ifBlank {
                FragmentRegistry.getMetadata(fragment::class)?.label?.takeIf { it.isNotBlank() }
                    ?: window?.getTitle()?.takeIf { it.isNotBlank() }
                    ?: "GenericUI"
            }
            val newWindow = Window(title = initialTitle)
            val ownerCandidate = window ?: Application.current?.mainIWindow
            if (ownerCandidate !== newWindow) {
                newWindow.secondaryOwnerWindow = ownerCandidate
            }
            val container = newWindow.getOrCreateFragmentContainer()
            container.hostingIWindow = newWindow
            container.setFragment(concrete)
            openFragmentWindows.add(fragment::class to newWindow)
            newWindow.show(factory)
            return fragment
        }

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
                clearTask -> delegatedHost.setFragment(concrete)
                addToBackStack -> delegatedHost.pushFragment(concrete)
                else -> delegatedHost.replaceFragment(concrete)
            }
            return fragment
        }

        val host = window ?: throw IllegalStateException(
            "startFragment(${fragment::class.simpleName}) requires a target IWindow."
        )
        val container = host.getOrCreateFragmentContainer()
        container.hostingIWindow = host
        when {
            clearTask -> container.setFragment(concrete)
            addToBackStack -> container.pushFragment(concrete)
            else -> container.replaceFragment(concrete)
        }
        return fragment
    }
}
