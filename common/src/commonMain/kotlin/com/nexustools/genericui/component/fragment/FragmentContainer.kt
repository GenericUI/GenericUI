package com.nexustools.genericui.component.fragment

import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.shell.Window
import com.nexustools.genericui.enums.fragment.FragmentState
import com.nexustools.genericui.window.IWindow

/**
 * Container component implementing [FragmentHost] that hosts an active [Fragment],
 * drives its Activity-like lifecycle (`onCreate`, `onStart`, `onResume`, `onPause`, `onStop`, `onDestroy`),
 * and maintains optional fragment back and forward stacks with saved [FragmentBundle] state.
 */
open class FragmentContainer(
    initialFragment: Fragment? = null,
    internal var hostWindow: Window? = null
) : Container("fragment-container"), FragmentHost {

    private data class StackEntry(
        val fragment: Fragment,
        val savedState: FragmentBundle
    )

    private val backStack = mutableListOf<StackEntry>()
    private val forwardStack = mutableListOf<StackEntry>()

    @Suppress("USELESS_CAST")
    var hostingIWindow: IWindow? = hostWindow as? IWindow

    override var currentFragment: Fragment? = null
        protected set

    override val backStackDepth: Int
        get() = backStack.size

    override val forwardStackDepth: Int
        get() = forwardStack.size

    override val canGoBack: Boolean
        get() = backStack.isNotEmpty()

    override val canGoForward: Boolean
        get() = forwardStack.isNotEmpty()

    init {
        hostWindow?.setOnWindowResize { config ->
            currentFragment?.onConfigurationChanged(config)
        }
        hostWindow?.setOnWindowFocusChanged { focused ->
            currentFragment?.onWindowFocusChanged(focused)
        }
        if (hostWindow == null && hostingIWindow != null) {
            hostingIWindow?.setOnWindowResize { config ->
                currentFragment?.onConfigurationChanged(config)
            }
            hostingIWindow?.setOnWindowFocusChanged { focused ->
                currentFragment?.onWindowFocusChanged(focused)
            }
        }
        if (initialFragment != null) {
            setFragment(initialFragment)
        }
    }

    override fun setFragment(fragment: Fragment) {
        clearAndDestroyStack(backStack)
        clearAndDestroyStack(forwardStack)
        transitionToFragment(fragment, destroyPrevious = true, restoredState = null)
    }

    override fun pushFragment(fragment: Fragment) {
        clearAndDestroyStack(forwardStack)
        val outgoing = currentFragment
        if (outgoing != null) {
            val bundle = FragmentBundle()
            outgoing.onSaveInstanceState(bundle)
            outgoing.performStop()
            outgoing.attachedWindow = null
            outgoing.attachedIWindow = null
            outgoing.fragmentHost = null
            removeChild(outgoing)
            backStack.add(StackEntry(outgoing, bundle))
        }
        transitionToFragment(fragment, destroyPrevious = false, restoredState = null)
    }

    override fun popFragment(): Boolean {
        if (backStack.isEmpty()) return false
        val outgoing = currentFragment
        if (outgoing != null) {
            val bundle = FragmentBundle()
            outgoing.onSaveInstanceState(bundle)
            outgoing.performStop()
            outgoing.attachedWindow = null
            outgoing.attachedIWindow = null
            outgoing.fragmentHost = null
            removeChild(outgoing)
            forwardStack.add(StackEntry(outgoing, bundle))
        }
        val entry = backStack.removeAt(backStack.lastIndex)
        transitionToFragment(entry.fragment, destroyPrevious = false, restoredState = entry.savedState)
        return true
    }

    override fun goForward(): Boolean {
        if (forwardStack.isEmpty()) return false
        val outgoing = currentFragment
        if (outgoing != null) {
            val bundle = FragmentBundle()
            outgoing.onSaveInstanceState(bundle)
            outgoing.performStop()
            outgoing.attachedWindow = null
            outgoing.attachedIWindow = null
            outgoing.fragmentHost = null
            removeChild(outgoing)
            backStack.add(StackEntry(outgoing, bundle))
        }
        val entry = forwardStack.removeAt(forwardStack.lastIndex)
        transitionToFragment(entry.fragment, destroyPrevious = false, restoredState = entry.savedState)
        return true
    }

    override fun replaceFragment(fragment: Fragment) {
        clearAndDestroyStack(forwardStack)
        transitionToFragment(fragment, destroyPrevious = true, restoredState = null)
    }

    private fun clearAndDestroyStack(stack: MutableList<StackEntry>) {
        for (entry in stack) {
            if (entry.fragment !== currentFragment) {
                entry.fragment.performDestroy()
            }
        }
        stack.clear()
    }

    @Suppress("USELESS_CAST")
    private fun resolveHostingIWindow(): IWindow? =
        hostingIWindow ?: (hostWindow as? IWindow)

    private fun resolveAttachedWindow(): Window? =
        hostWindow ?: (hostingIWindow as? Window)

    private fun transitionToFragment(
        incoming: Fragment,
        destroyPrevious: Boolean,
        restoredState: FragmentBundle?
    ) {
        val outgoing = currentFragment
        if (outgoing != null && outgoing !== incoming && children.contains(outgoing)) {
            if (destroyPrevious) {
                outgoing.performDestroy()
            } else {
                outgoing.performStop()
            }
            outgoing.attachedWindow = null
            outgoing.attachedIWindow = null
            outgoing.fragmentHost = null
            removeChild(outgoing)
        }

        val resolvedIWindow = resolveHostingIWindow()
        val resolvedWindow = resolveAttachedWindow()

        currentFragment = incoming
        incoming.fragmentHost = this
        incoming.attachedWindow = resolvedWindow
        incoming.attachedIWindow = resolvedIWindow
        if (resolvedWindow != null || resolvedIWindow != null) {
            htmlTag = "genericui:root"
            incoming.htmlTag = "genericui:root"
        }

        if (incoming.state == FragmentState.INITIALIZED || incoming.state == FragmentState.DESTROYED) {
            incoming.performCreate(restoredState)
        } else if (restoredState != null) {
            incoming.onRestoreInstanceState(restoredState)
        }
        if (resolvedWindow != null || resolvedIWindow != null) {
            htmlTag = "genericui:root"
            incoming.htmlTag = "genericui:root"
        }
        if (!children.contains(incoming)) {
            addChild(incoming)
        }
        syncFragmentChrome(incoming)
        if (resolveActiveFactory() != null) {
            incoming.performStart()
            incoming.performResume()
        }
    }

    internal fun syncFragmentChrome(fragment: Fragment) {
        val resolvedIWindow = resolveHostingIWindow()
        val resolvedWindow = resolveAttachedWindow()
        fragment.attachedWindow = resolvedWindow
        fragment.attachedIWindow = resolvedIWindow
        fragment.syncChromeToAttachedWindow()
        if (resolvedWindow == null && resolvedIWindow != null) {
            resolvedIWindow.setFullscreen(fragment.isFullscreen(), fragment.getDisplayCutoutMode())
            fragment.getTitleBar()?.let { resolvedIWindow.setTitleBar(it) }
            if (fragment.pageTitle.isNotBlank()) {
                resolvedIWindow.setTitle(fragment.pageTitle)
            }
        }
    }

    fun ensureActiveFragmentStartedAndResumed() {
        val active = currentFragment ?: return
        active.attachedWindow = resolveAttachedWindow()
        active.attachedIWindow = resolveHostingIWindow()
        if (active.state == FragmentState.INITIALIZED) {
            active.performCreate(null)
        }
        active.performStart()
        active.performResume()
        syncFragmentChrome(active)
    }

    override fun dispose() {
        clearAndDestroyStack(backStack)
        clearAndDestroyStack(forwardStack)
        currentFragment?.performDestroy()
        currentFragment = null
        super.dispose()
    }
}
