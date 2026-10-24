package com.nexustools.genericui.component.fragment.helper

import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.fragment.FragmentBundle
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.layout.ScrollView
import com.nexustools.genericui.component.layout.VStack

/**
 * Helper [Fragment] featuring a collapsible hero/banner header above a scrollable body container.
 */
open class CollapsibleHeaderFragment(
    val headerContainer: Container = Container("collapsible-header"),
    val bodyContainer: Container = Container("collapsible-body")
) : Fragment() {

    val scrollView: ScrollView = ScrollView()

    override fun onCreate(savedState: FragmentBundle?) {
        super.onCreate(savedState)
        val stack = VStack()
        stack.addChild(headerContainer)
        stack.addChild(bodyContainer)
        scrollView.addChild(stack)
        addChild(scrollView)
    }
}
