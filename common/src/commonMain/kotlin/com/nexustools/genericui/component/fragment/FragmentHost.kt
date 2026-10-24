package com.nexustools.genericui.component.fragment

/**
 * Interface implemented by containers and applications that host and switch [Fragment]s
 * ([FragmentContainer], [com.nexustools.genericui.component.shell.FragmentApplication]).
 */
interface FragmentHost {
    val currentFragment: Fragment?
    val backStackDepth: Int

    fun setFragment(fragment: Fragment)
    fun pushFragment(fragment: Fragment)
    fun popFragment(): Boolean
    fun replaceFragment(fragment: Fragment)
}
