package com.nexustools.genericui.component.fragment.helper

import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.fragment.FragmentBundle
import com.nexustools.genericui.component.fragment.FragmentContainer
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.layout.Row

/**
 * Helper [Fragment] providing an adaptive navigation drawer sidebar (`drawerContent`) alongside
 * a main `FragmentContainer` (`bodyFragmentContainer`).
 */
open class NavigationDrawerFragment(
    val drawerContent: Container = Container("navigation-drawer")
) : Fragment() {

    val bodyFragmentContainer: FragmentContainer = FragmentContainer()
    private val rootRow = Row()
    var isDrawerOpen: Boolean = true
        private set

    override fun onCreate(savedState: FragmentBundle?) {
        super.onCreate(savedState)
        rootRow.addChild(drawerContent)
        rootRow.addChild(bodyFragmentContainer)
        addChild(rootRow)
    }

    fun setDrawerOpen(open: Boolean) {
        isDrawerOpen = open
        drawerContent.isVisible = open
    }

    fun toggleDrawer() {
        setDrawerOpen(!isDrawerOpen)
    }

    fun setBodyFragment(fragment: Fragment) {
        bodyFragmentContainer.setFragment(fragment)
    }
}
