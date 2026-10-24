package com.nexustools.genericui.component.fragment.helper

import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.fragment.FragmentBundle
import com.nexustools.genericui.component.fragment.FragmentContainer
import com.nexustools.genericui.component.navigation.BottomNavigationBar

/**
 * Helper [Fragment] that manages multiple child [Fragment] destinations switched via a [BottomNavigationBar]
 * while preserving each destination's state across tab switches.
 */
open class BottomNavigationFragment : Fragment() {

    private val destinations = linkedMapOf<String, Triple<String, String?, Fragment>>()
    val contentHost: FragmentContainer = FragmentContainer()
    val bottomBar: BottomNavigationBar = BottomNavigationBar()

    override fun onCreate(savedState: FragmentBundle?) {
        super.onCreate(savedState)
        setBottomNavigationBar(bottomBar)
        addChild(contentHost)
        bottomBar.onItemSelected = { id ->
            selectDestination(id)
        }
    }

    fun addDestination(id: String, label: String, icon: String? = null, fragment: Fragment) {
        destinations[id] = Triple(label, icon, fragment)
        bottomBar.setItems(destinations.map { (k, v) -> Triple(k, v.first, v.second) })
        if (contentHost.currentFragment == null) {
            selectDestination(id)
        }
    }

    fun selectDestination(id: String) {
        val target = destinations[id]?.third ?: return
        bottomBar.selectedItemId = id
        contentHost.replaceFragment(target)
    }
}
