package com.nexustools.genericui.component.lazy

import com.nexustools.genericui.core.AbstractComponent

/**
 * Cell descriptor for a single column within a [LazyGridTreeNode] row.
 *
 * A cell may hold either a plain [text] value (with an optional [badgeText]) OR an arbitrary
 * [AbstractComponent] (passed directly via [component] or lazily constructed via [componentFactory]
 * when the cell enters the visible [LazyGridTree] viewport).
 */
class LazyGridTreeCell(
    val text: String? = null,
    val component: AbstractComponent? = null,
    val componentFactory: (() -> AbstractComponent)? = null,
    val badgeText: String? = null,
    val tooltipText: String? = null
) {
    /**
     * Returns `true` when this cell is backed by a live [AbstractComponent] or [componentFactory]
     * rather than plain text.
     */
    val isComponentCell: Boolean
        get() = component != null || componentFactory != null

    /**
     * Resolves the [AbstractComponent] for this cell if it is a component cell, or `null` if it is a text cell.
     */
    fun resolveComponent(): AbstractComponent? =
        component ?: componentFactory?.invoke()

    companion object {
        /**
         * Creates a text-backed [LazyGridTreeCell].
         */
        fun ofText(
            text: String,
            badgeText: String? = null,
            tooltipText: String? = null
        ): LazyGridTreeCell = LazyGridTreeCell(
            text = text,
            badgeText = badgeText,
            tooltipText = tooltipText
        )

        /**
         * Creates a component-backed [LazyGridTreeCell] holding [component].
         */
        fun ofComponent(
            component: AbstractComponent,
            tooltipText: String? = null
        ): LazyGridTreeCell = LazyGridTreeCell(
            component = component,
            tooltipText = tooltipText
        )

        /**
         * Creates a lazily-instantiated component [LazyGridTreeCell] that invokes [factory]
         * only when the cell is materialized inside the visible lazy viewport.
         */
        fun ofComponentFactory(
            tooltipText: String? = null,
            factory: () -> AbstractComponent
        ): LazyGridTreeCell = LazyGridTreeCell(
            componentFactory = factory,
            tooltipText = tooltipText
        )
    }
}
