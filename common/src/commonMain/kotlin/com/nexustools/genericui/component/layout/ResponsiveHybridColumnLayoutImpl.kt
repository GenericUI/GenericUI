package com.nexustools.genericui.component.layout

/**
 * Responsive [HybridColumnLayoutImpl] that evaluates [HybridLayoutMode.COLUMNS],
 * [HybridLayoutMode.SINGLE], [HybridLayoutMode.FULLSCREEN], and [HybridLayoutMode.PIP]
 * dynamically based on viewport dimensions and device breakpoints.
 */
open class ResponsiveHybridColumnLayoutImpl(
    override val layout: HybridColumnLayout
) : HybridColumnLayoutImpl {

    override var isDisposed: Boolean = false
        protected set

    override val layoutMode: HybridLayoutMode
        get() = layout.layoutMode

    override fun evaluateLayoutMode(
        viewportWidthDp: Float,
        viewportHeightDp: Float,
        smallestScreenWidthDp: Int
    ): HybridLayoutMode {
        if (layout.isInPictureInPictureMode) return HybridLayoutMode.PIP
        if (layout.isFullscreen) return HybridLayoutMode.FULLSCREEN
        val wide = layout.breakpointSpec.shouldUseWideColumns(
            viewportWidthDp = viewportWidthDp.toInt(),
            viewportHeightDp = viewportHeightDp.toInt(),
            smallestScreenWidthDp = smallestScreenWidthDp,
            backendTarget = layout.host.resolveActiveFactory()?.backendTarget
        )
        return if (wide) HybridLayoutMode.COLUMNS else HybridLayoutMode.SINGLE
    }

    override fun syncLayout(
        viewportWidthDp: Float,
        viewportHeightDp: Float,
        smallestScreenWidthDp: Int
    ) {
        val nextMode = evaluateLayoutMode(viewportWidthDp, viewportHeightDp, smallestScreenWidthDp)
        layout.applyEvaluatedMode(nextMode)
    }

    override fun dispose() {
        isDisposed = true
    }
}
