package com.nexustools.genericui.component.fragment

/**
 * Interface implemented by containers, fragments, and applications that host and switch [Fragment]s
 * ([FragmentContainer], [com.nexustools.genericui.component.fragment.helper.FragmentHostSystem],
 * [com.nexustools.genericui.component.fragment.helper.FragmentHostFragment],
 * [com.nexustools.genericui.component.shell.FragmentApplication]).
 */
interface FragmentHost {
    val currentFragment: Fragment?
    val backStackDepth: Int

    val forwardStackDepth: Int
        get() = 0

    val canGoBack: Boolean
        get() = backStackDepth > 0

    val canGoForward: Boolean
        get() = forwardStackDepth > 0

    fun setFragment(fragment: Fragment)
    fun pushFragment(fragment: Fragment)
    fun popFragment(): Boolean
    fun replaceFragment(fragment: Fragment)

    fun goForward(): Boolean = false
}
