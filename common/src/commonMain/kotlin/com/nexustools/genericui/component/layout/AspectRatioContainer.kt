package com.nexustools.genericui.component.layout

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.enums.bootstrap.BootstrapAspectRatio

/**
 * [LayoutContainer] hosting an [AspectRatioLayout] that constrains its children to a fixed aspect ratio
 * (such as `16:9` via [BootstrapAspectRatio.RATIO_16X9] using Bootstrap's `.ratio` and `.ratio-16x9` classes on `:web`).
 */
open class AspectRatioContainer(
    ratio: BootstrapAspectRatio? = BootstrapAspectRatio.RATIO_16X9,
    customAspectRatio: Float? = null,
    breakpointSpec: HybridBreakpointSpec = HybridBreakpointSpec.DEFAULT
) : LayoutContainer<AspectRatioLayoutImpl, AspectRatioLayout>(
    "genericui:aspectratiocontainer",
    "ratio"
) {
    constructor(
        widthRatio: Float,
        heightRatio: Float,
        breakpointSpec: HybridBreakpointSpec = HybridBreakpointSpec.DEFAULT
    ) : this(
        ratio = AspectRatioLayout.resolveMatchingBootstrapRatio(widthRatio, heightRatio),
        customAspectRatio = if (AspectRatioLayout.resolveMatchingBootstrapRatio(widthRatio, heightRatio) == null && heightRatio > 0f) {
            widthRatio / heightRatio
        } else {
            null
        },
        breakpointSpec = breakpointSpec
    )

    override val layout: AspectRatioLayout = AspectRatioLayout(
        host = this,
        ratio = ratio,
        customAspectRatio = customAspectRatio,
        breakpointSpec = breakpointSpec
    )

    var ratio: BootstrapAspectRatio?
        get() = layout.ratio
        set(value) {
            layout.ratio = value
        }

    var customAspectRatio: Float?
        get() = layout.customAspectRatio
        set(value) {
            layout.customAspectRatio = value
        }

    val effectiveAspectRatio: Float
        get() = layout.effectiveAspectRatio

    init {
        htmlTag = "genericui:aspectratiocontainer"
        initializeLayout()
    }

    fun setAspectRatio(ratio: BootstrapAspectRatio): AspectRatioContainer = apply {
        layout.setAspectRatio(ratio)
    }

    fun setAspectRatio(widthRatio: Float, heightRatio: Float): AspectRatioContainer = apply {
        layout.setAspectRatio(widthRatio, heightRatio)
    }

    override fun addChild(child: AbstractComponent, index: Int) {
        super.addChild(child, index)
        layout.formatAspectChild(child)
    }
}
