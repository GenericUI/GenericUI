package com.nexustools.genericui.component.fragment.helper

import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.fragment.FragmentBundle
import com.nexustools.genericui.component.fragment.FragmentContainer
import com.nexustools.genericui.component.layout.BentoGridItemSpec
import com.nexustools.genericui.component.layout.HybridBreakpointSpec
import com.nexustools.genericui.component.layout.ResponsiveBentoGridContainer
import com.nexustools.genericui.component.layout.ResponsiveBentoGridLayout
import com.nexustools.genericui.component.layout.ResponsiveBentoGridLayoutImpl
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.window.WindowConfiguration

/**
 * Helper [LayoutFragment] hosting a [ResponsiveBentoGridLayout] that displays multi-column Bootstrap 12-grid
 * cards on tablets, wide web, and desktop, and automatically reflows into a single-column (`colXs = 12`)
 * vertical feed on small-screen mobile (`< 600dp`) and narrow web viewports.
 */
open class ResponsiveBentoGridFragment(
    breakpointSpec: HybridBreakpointSpec = HybridBreakpointSpec.DEFAULT,
    gapPx: Int = 16,
    id: String? = null
) : LayoutFragment<ResponsiveBentoGridLayoutImpl, ResponsiveBentoGridLayout>(id = id) {

    val bentoGridContainer: ResponsiveBentoGridContainer = ResponsiveBentoGridContainer(
        breakpointSpec = breakpointSpec,
        gapPx = gapPx
    )

    override val layout: ResponsiveBentoGridLayout = bentoGridContainer.layout

    val bentoGridLayout: ResponsiveBentoGridLayout
        get() = layout

    private val cardFragments = linkedMapOf<String, Fragment>()
    private val cardFragmentContainers = linkedMapOf<String, FragmentContainer>()

    val isSingleColumnFeed: Boolean
        get() = layout.isSingleColumnFeed

    val cardIds: List<String>
        get() = layout.cardIds

    val cardCount: Int
        get() = layout.cardCount

    init {
        addChild(bentoGridContainer)
    }

    override fun onCreate(savedState: FragmentBundle?) {
        if (!children.contains(bentoGridContainer)) {
            addChild(bentoGridContainer)
        }
        super.onCreate(savedState)
        val winSize = hostingWindow?.getWindowSize() ?: attachedWindow?.getWindowSize()
        if (winSize != null && winSize.width > 0 && winSize.height > 0) {
            layout.updateViewport(winSize.width, winSize.height)
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
        layout.addCard(spec, component)
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
        layout.addCard(spec, host)
    }

    fun removeCard(id: String): Boolean {
        cardFragments.remove(id)
        cardFragmentContainers.remove(id)?.dispose()
        return layout.removeCard(id)
    }

    fun getCardSpec(id: String): BentoGridItemSpec? = layout.getCardSpec(id)

    fun getCardComponent(id: String): AbstractComponent? = layout.getCardComponent(id)

    fun getCardFragment(id: String): Fragment? = cardFragments[id]

    fun activeColumnsForItem(id: String): Int = layout.activeColumnsForItem(id)

    override fun onResize(viewportWidth: Int, viewportHeight: Int) {
        super.onResize(viewportWidth, viewportHeight)
        for (fragment in cardFragments.values) {
            fragment.onResize(viewportWidth, viewportHeight)
        }
    }

    override fun onConfigurationChanged(newConfig: WindowConfiguration) {
        super.onConfigurationChanged(newConfig)
        for (fragment in cardFragments.values) {
            fragment.onConfigurationChanged(newConfig)
        }
    }
}
