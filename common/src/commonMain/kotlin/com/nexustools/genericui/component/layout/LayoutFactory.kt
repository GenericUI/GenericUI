package com.nexustools.genericui.component.layout

/**
 * Factory provided by [com.nexustools.genericui.core.NativeFactory.layoutFactory] that creates
 * backend-group-specific [LayoutImpl] instances for [Layout] helpers.
 */
interface LayoutFactory {
    fun createColumnLayout(layout: ColumnLayout): ColumnLayoutImpl =
        ResponsiveColumnLayoutImpl(layout)

    fun createAdaptiveCardLayout(layout: AdaptiveCardLayout): AdaptiveCardLayoutImpl =
        ResponsiveAdaptiveCardLayoutImpl(layout)

    fun createHybridColumnLayout(layout: HybridColumnLayout): HybridColumnLayoutImpl =
        ResponsiveHybridColumnLayoutImpl(layout)

    fun createHybridSplitNavigationLayout(layout: HybridSplitNavigationLayout): HybridSplitNavigationLayoutImpl =
        ResponsiveHybridSplitNavigationLayoutImpl(layout)

    fun createCollapsibleBottomDockLayout(layout: CollapsibleBottomDockLayout): CollapsibleBottomDockLayoutImpl =
        ResponsiveCollapsibleBottomDockLayoutImpl(layout)

    fun createResponsiveMasterDetailLayout(layout: ResponsiveMasterDetailLayout): ResponsiveMasterDetailLayoutImpl =
        DefaultResponsiveMasterDetailLayoutImpl(layout)

    fun createResponsiveBentoGridLayout(layout: ResponsiveBentoGridLayout): ResponsiveBentoGridLayoutImpl =
        DefaultResponsiveBentoGridLayoutImpl(layout)

    fun createAdaptiveNavigationScaffoldLayout(layout: AdaptiveNavigationScaffoldLayout): AdaptiveNavigationScaffoldLayoutImpl =
        DefaultAdaptiveNavigationScaffoldLayoutImpl(layout)

    fun createSplitPaneLayout(layout: SplitPaneLayout): SplitPaneLayoutImpl =
        DefaultSplitPaneLayoutImpl(layout)

    fun createCollapsiblePanelLayout(layout: CollapsiblePanelLayout): CollapsiblePanelLayoutImpl =
        DefaultCollapsiblePanelLayoutImpl(layout)

    fun createAspectRatioLayout(layout: AspectRatioLayout): AspectRatioLayoutImpl =
        DefaultAspectRatioLayoutImpl(layout)

    fun createLayeredLayout(layout: LayeredLayout): LayeredLayoutImpl =
        DefaultLayeredLayoutImpl(layout)
}
