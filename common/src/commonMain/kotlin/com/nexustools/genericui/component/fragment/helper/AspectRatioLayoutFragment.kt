package com.nexustools.genericui.component.fragment.helper

import com.nexustools.genericui.component.fragment.FragmentBundle
import com.nexustools.genericui.component.layout.AspectRatioContainer
import com.nexustools.genericui.component.layout.AspectRatioLayout
import com.nexustools.genericui.component.layout.AspectRatioLayoutImpl
import com.nexustools.genericui.component.layout.HybridBreakpointSpec
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.enums.bootstrap.BootstrapAspectRatio

/**
 * Helper [LayoutFragment] that hosts an [AspectRatioContainer] / [AspectRatioLayout].
 */
open class AspectRatioLayoutFragment(
    ratio: BootstrapAspectRatio? = BootstrapAspectRatio.RATIO_16X9,
    customAspectRatio: Float? = null,
    breakpointSpec: HybridBreakpointSpec = HybridBreakpointSpec.DEFAULT,
    id: String? = null
) : LayoutFragment<AspectRatioLayoutImpl, AspectRatioLayout>(id = id) {

    val aspectRatioContainer: AspectRatioContainer = AspectRatioContainer(
        ratio = ratio,
        customAspectRatio = customAspectRatio,
        breakpointSpec = breakpointSpec
    )

    override val layout: AspectRatioLayout = aspectRatioContainer.layout

    var ratio: BootstrapAspectRatio?
        get() = aspectRatioContainer.ratio
        set(value) {
            aspectRatioContainer.ratio = value
        }

    var customAspectRatio: Float?
        get() = aspectRatioContainer.customAspectRatio
        set(value) {
            aspectRatioContainer.customAspectRatio = value
        }

    val effectiveAspectRatio: Float
        get() = aspectRatioContainer.effectiveAspectRatio

    init {
        addChild(aspectRatioContainer)
    }

    fun addAspectContent(component: AbstractComponent, index: Int = -1): AspectRatioLayoutFragment = apply {
        aspectRatioContainer.addChild(component, index)
    }

    override fun onCreate(savedState: FragmentBundle?) {
        super.onCreate(savedState)
        if (aspectRatioContainer.parent == null) {
            addChild(aspectRatioContainer)
        }
    }
}
