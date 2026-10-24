package com.nexustools.genericui.component.fragment

import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.shell.Window
import com.nexustools.genericui.enums.fragment.FragmentState

/**
 * Container component implementing [FragmentHost] that hosts an active [Fragment],
 * drives its Activity-like lifecycle (`onCreate`, `onStart`, `onResume`, `onPause`, `onStop`, `onDestroy`),
 * and maintains an optional fragment back stack with saved [FragmentBundle] state.
 */
open class FragmentContainer(
    initialFragment: Fragment? = null,
    internal var hostWindow: Window? = null
) : Container("fragment-container"), FragmentHost {

    private data class BackStackEntry(
        val fragment: Fragment,
        val savedState: FragmentBundle
    )

    private val backStack = mutableListOf<BackStackEntry>()
    override var currentFragment: Fragment? = null
        protected set

    override val backStackDepth: Int
        get() = backStack.size

    init {
        hostWindow?.setOnWindowResize { config ->
            currentFragment?.onConfigurationChanged(config)
        }
        hostWindow?.setOnWindowFocusChanged { focused ->
            currentFragment?.onWindowFocusChanged(focused)
        }
        if (initialFragment != null) {
            setFragment(initialFragment)
        }
    }

    override fun setFragment(fragment: Fragment) {
        for (entry in backStack) {
            entry.fragment.performDestroy()
        }
        backStack.clear()
        transitionToFragment(fragment, destroyPrevious = true, restoredState = null)
    }

    override fun pushFragment(fragment: Fragment) {
        val outgoing = currentFragment
        if (outgoing != null) {
            val bundle = FragmentBundle()
            outgoing.onSaveInstanceState(bundle)
            outgoing.performStop()
            removeChild(outgoing)
            backStack.add(BackStackEntry(outgoing, bundle))
        }
        transitionToFragment(fragment, destroyPrevious = false, restoredState = null)
    }

    override fun popFragment(): Boolean {
        if (backStack.isEmpty()) return false
        val entry = backStack.removeAt(backStack.lastIndex)
        transitionToFragment(entry.fragment, destroyPrevious = true, restoredState = entry.savedState)
        return true
    }

    override fun replaceFragment(fragment: Fragment) {
        transitionToFragment(fragment, destroyPrevious = true, restoredState = null)
    }

    private fun transitionToFragment(
        incoming: Fragment,
        destroyPrevious: Boolean,
        restoredState: FragmentBundle?
    ) {
        val outgoing = currentFragment
        if (outgoing != null && outgoing !== incoming) {
            if (destroyPrevious) {
                outgoing.performDestroy()
            } else {
                outgoing.performStop()
            }
            outgoing.attachedWindow = null
            outgoing.fragmentHost = null
            removeChild(outgoing)
        }

        currentFragment = incoming
        incoming.fragmentHost = this
        incoming.attachedWindow = hostWindow

        if (incoming.state == FragmentState.INITIALIZED || incoming.state == FragmentState.DESTROYED) {
            incoming.performCreate(restoredState)
        }
        addChild(incoming)
        if (resolveActiveFactory() != null) {
            incoming.performStart()
            incoming.performResume()
        }
    }

    internal fun ensureActiveFragmentStartedAndResumed() {
        val active = currentFragment ?: return
        active.attachedWindow = hostWindow
        if (active.state == FragmentState.INITIALIZED) {
            active.performCreate(null)
        }
        active.performStart()
        active.performResume()
    }
}
