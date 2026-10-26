package com.nexustools.genericui.component.fragment.helper

import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.fragment.FragmentBundle
import com.nexustools.genericui.component.fragment.FragmentContainer
import com.nexustools.genericui.component.layout.BentoGridItemSpec
import com.nexustools.genericui.component.layout.HybridBreakpointSpec
import com.nexustools.genericui.component.layout.ResponsiveBentoGridLayout
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.window.WindowConfiguration

/**
 * Helper [Fragment] hosting a [ResponsiveBentoGridLayout] that displays multi-column Bootstrap 12-grid
 * cards on tablets, wide web, and desktop, and automatically reflows into a single-column (`colXs = 12`)
 * vertical feed on small-screen mobile (`< 600dp`) and narrow web viewports.
 */
open class ResponsiveBentoGridFragment(
    breakpointSpec: HybridBreakpointSpec = HybridBreakpointSpec.DEFAULT
) : Fragment() {

    val bentoGridLayout: ResponsiveBentoGridLayout = ResponsiveBentoGridLayout(
        breakpointSpec = breakpointSpec
    )

    private val cardFragments = linkedMapOf<String, Fragment>()
    private val cardFragmentContainers = linkedMapOf<String, FragmentContainer>()

    val isSingleColumnFeed: Boolean
        get() = bentoGridLayout.isSingleColumnFeed

    val cardIds: List<String>
        get() = bentoGridLayout.cardIds

    val cardCount: Int
        get() = bentoGridLayout.cardCount

    init {
        addChild(bentoGridLayout)
    }

    override fun onCreate(savedState: FragmentBundle?) {
        super.onCreate(savedState)
        if (!children.contains(bentoGridLayout)) {
            addChild(bentoGridLayout)
        }
        val winSize = hostingWindow?.getWindowSize() ?: attachedWindow?.getWindowSize()
        if (winSize != null && winSize.width > 0 && winSize.height > 0) {
            bentoGridLayout.updateViewport(winSize.width, winSize.height)
        }
    }

    /**
     * Adds a card backed by an [AbstractComponent].
     */
    fun addCard(
        spec: BentoGridItemSpec,
        component: AbstractComponent
    ): ResponsiveBentoGridFragment = apply {
        cardFragments.remove(spec.id)
        cardFragmentContainers.remove(spec.id)?.dispose()
        bentoGridLayout.addCard(spec, component)
    }

    /**
     * Adds a card backed by a child [Fragment].
     */
    fun addFragmentCard(
        spec: BentoGridItemSpec,
        fragment: Fragment
    ): ResponsiveBentoGridFragment = apply {
        cardFragments.remove(spec.id)
        cardFragmentContainers.remove(spec.id)?.dispose()
        val host = FragmentContainer(
            initialFragment = fragment,
            hostWindow = attachedWindow
        ).apply {
            hostingIWindow = hostingWindow
        }
        cardFragments[spec.id] = fragment
        cardFragmentContainers[spec.id] = host
        bentoGridLayout.addCard(spec, host)
    }

    fun removeCard(id: String): Boolean {
        cardFragments.remove(id)
        cardFragmentContainers.remove(id)?.dispose()
        return bentoGridLayout.removeCard(id)
    }

    fun getCardSpec(id: String): BentoGridItemSpec? = bentoGridLayout.getCardSpec(id)

    fun getCardComponent(id: String): AbstractComponent? = bentoGridLayout.getCardComponent(id)

    fun getCardFragment(id: String): Fragment? = cardFragments[id]

    fun activeColumnsForItem(id: String): Int = bentoGridLayout.activeColumnsForItem(id)

    override fun onResize(viewportWidth: Int, viewportHeight: Int) {
        super.onResize(viewportWidth, viewportHeight)
        bentoGridLayout.updateViewport(viewportWidth, viewportHeight)
        for (fragment in cardFragments.values) {
            fragment.onResize(viewportWidth, viewportHeight)
        }
    }

    override fun onConfigurationChanged(newConfig: WindowConfiguration) {
        super.onConfigurationChanged(newConfig)
        bentoGridLayout.applyWindowConfiguration(newConfig)
        for (fragment in cardFragments.values) {
            fragment.onConfigurationChanged(newConfig)
        }
    }
}
