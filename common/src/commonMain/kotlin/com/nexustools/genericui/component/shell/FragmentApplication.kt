package com.nexustools.genericui.component.shell

import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.fragment.FragmentContainer
import com.nexustools.genericui.component.fragment.FragmentHost

/**
 * Fragment-driven application (`FragmentApplication`).
 *
 * Requires an initial [Fragment] upon construction and implements [FragmentHost] (`setFragment`,
 * `pushFragment`, `popFragment`, `replaceFragment`) so applications can navigate between fragments
 * or host specialized Helper Fragments such as `HybridColumnFragment`
 * or `DockWorkspaceFragment`.
 */
open class FragmentApplication(
    initialFragment: Fragment? = null,
    title: String = "GenericUI Application",
    initialThemeColor: String? = null
) : Application(
    initialContent = null,
    title = title,
    initialThemeColor = initialThemeColor
), FragmentHost {

    override val rootFragmentContainer: FragmentContainer = FragmentContainer(initialFragment, hostWindow = mainWindow)

    init {
        initialFragment?.application = this
        mainWindow.setContent(rootFragmentContainer)
    }

    override val currentFragment: Fragment?
        get() = rootFragmentContainer.currentFragment

    override val backStackDepth: Int
        get() = rootFragmentContainer.backStackDepth

    override val forwardStackDepth: Int
        get() = rootFragmentContainer.forwardStackDepth

    override val canGoBack: Boolean
        get() = rootFragmentContainer.canGoBack

    override val canGoForward: Boolean
        get() = rootFragmentContainer.canGoForward

    override fun setFragment(fragment: Fragment) {
        fragment.application = this
        rootFragmentContainer.setFragment(fragment)
    }

    override fun pushFragment(fragment: Fragment) {
        fragment.application = this
        rootFragmentContainer.pushFragment(fragment)
    }

    override fun popFragment(): Boolean =
        rootFragmentContainer.popFragment()

    override fun goForward(): Boolean =
        rootFragmentContainer.goForward()

    override fun replaceFragment(fragment: Fragment) {
        fragment.application = this
        rootFragmentContainer.replaceFragment(fragment)
    }

    override fun onApplicationStarted() {
        super.onApplicationStarted()
        if (rootFragmentContainer.currentFragment == null &&
            (mainFragmentFactory != null || mainFragmentClass != null)
        ) {
            val created = onCreateMainFragment(launchArguments)
            if (created != null && rootFragmentContainer.currentFragment == null) {
                setFragment(created)
            }
        }
        rootFragmentContainer.ensureActiveFragmentStartedAndResumed()
    }
}
