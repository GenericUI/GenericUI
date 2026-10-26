package com.nexustools.genericui.component.layout

/**
 * Folding strategy applied by [HybridColumnLayout] when transitioning from [HybridLayoutMode.COLUMNS]
 * into [HybridLayoutMode.SINGLE] on small-screen mobile (`smallestScreenWidthDp < 600`), tablet portrait,
 * or narrow Web/Desktop viewports (`viewportWidthDp < columnBreakpointDp`).
 */
enum class HybridFoldMode {
    /**
     * Pins Column 0 at the top of the vertical stack (with its configured compact height or aspect ratio)
     * and places Column 1 below it (`weight = 1f`). If there are 3+ columns (`columns.size > 2`),
     * Column 0 stays pinned at the top while Columns `1..N` are collapsed into tabs below it.
     *
     * Matches Episode-Caster's video `EpisodeScreen` layout.
     */
    PIN_FIRST_COLUMN_TOP,

    /**
     * Keeps Column 0 pinned while collapsing all secondary columns (`1..N`) into a switchable tab bar
     * so only the active secondary column is mounted below/beside Column 0.
     */
    COLLAPSE_SECONDARY_COLUMNS_TO_TABS,

    /**
     * Collapses **every** column (`0..N`, including the primary column such as `"Visualizer"`) into a single
     * tabbed interface in [HybridLayoutMode.SINGLE]. When transitioning to [HybridLayoutMode.COLUMNS],
     * Column 0 is automatically promoted out of the tab bar into its own dedicated left column and
     * the right-hand tab bar selects the first secondary tab if Column 0's tab was active.
     *
     * Matches Episode-Caster's music `MusicScreen` layout.
     */
    COLLAPSE_ALL_COLUMNS_TO_TABS,

    /**
     * Stacks all columns vertically in order (`0..N`) inside a vertical flex/scroll container.
     */
    STACK_VERTICAL
}
