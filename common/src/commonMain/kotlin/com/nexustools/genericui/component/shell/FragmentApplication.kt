package com.nexustools.genericui.component.shell

import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.fragment.FragmentContainer
import com.nexustools.genericui.component.fragment.FragmentHost

/**
 * Fragment-driven application (`FragmentApplication`).
 *
 * Requires an initial [Fragment] upon construction and implements [FragmentHost] (`setFragment`,
 * `pushFragment`, `popFragment`, `replaceFragment`) so applications can navigate between fragments
 * or host specialized Helper Fragments such as `AdaptiveSplitTabFragment` (Episode Caster)
 * or `DockWorkspaceFragment` (HAEditor).
 */
open class FragmentApplication(
    initialFragment: Fragment,
    title: String = "GenericUI Application",
    initialThemeColor: String? = null
) : Application(
    initialContent = null,
    title = title,
    initialThemeColor = initialThemeColor
), FragmentHost {

    val rootFragmentContainer: FragmentContainer = FragmentContainer(initialFragment, hostWindow = mainWindow)

    init {
        mainWindow.setContent(rootFragmentContainer)
    }

    override val currentFragment: Fragment?
        get() = rootFragmentContainer.currentFragment

    override val backStackDepth: Int
        get() = rootFragmentContainer.backStackDepth

    override fun setFragment(fragment: Fragment) {
        rootFragmentContainer.setFragment(fragment)
    }

    override fun pushFragment(fragment: Fragment) {
        rootFragmentContainer.pushFragment(fragment)
    }

    override fun popFragment(): Boolean =
        rootFragmentContainer.popFragment()

    override fun replaceFragment(fragment: Fragment) {
        rootFragmentContainer.replaceFragment(fragment)
    }

    override fun onApplicationStarted() {
        super.onApplicationStarted()
        rootFragmentContainer.ensureActiveFragmentStartedAndResumed()
    }
}
