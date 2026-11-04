package com.nexustools.genericui.component.layout

import com.nexustools.genericui.enums.bootstrap.DisplayMode
import com.nexustools.genericui.enums.bootstrap.FlexDirection
import com.nexustools.genericui.enums.bootstrap.FlexWrap

/**
 * Default responsive Bootstrap 5 12-column grid implementation of [ColumnLayoutImpl].
 */
open class ResponsiveColumnLayoutImpl(
    override val layout: ColumnLayout
) : ColumnLayoutImpl {

    override var isDisposed: Boolean = false
        protected set

    override fun syncLayout(viewportWidthDp: Float, viewportHeightDp: Float) {
        val tier = layout.breakpointSpec.resolveBootstrapBreakpoint(viewportWidthDp.toInt())
        layout.activeBreakpoint = tier

        layout.host.addClass("row")
        layout.host.setDisplay(DisplayMode.FLEX)
        layout.host.setFlexDirection(FlexDirection.ROW)
        layout.host.setFlexWrap(FlexWrap.WRAP)
        layout.syncHostGridClasses()

        val rowColsForTier = layout.resolveRowColsForBreakpoint(tier)
        val defaultSpanFromRowCols = rowColsForTier?.let { cols ->
            (12 / cols.coerceIn(1, 12)).coerceIn(1, 12)
        }

        for (col in layout.columns) {
            col.syncColumnClasses()
            val isAuto = col.spec.resolveAutoWidthForBreakpoint(tier)
            val effectiveSpan = col.spec.resolveSpanForBreakpoint(tier) ?: defaultSpanFromRowCols

            when {
                isAuto -> {
                    col.setStyleProperty("flex-grow", "0")
                    col.setStyleProperty("flex-shrink", "0")
                    col.setStyleProperty("flex-basis", "auto")
                    col.setStyleProperty("width", "auto")
                    col.removeStyleProperty("max-width")
                }
                effectiveSpan != null -> {
                    val clamped = effectiveSpan.coerceIn(1, 12)
                    val pct = (clamped * 100.0) / 12.0
                    val roundedPct = ((pct * 10000.0).toInt() / 10000.0).toString() + "%"
                    col.setStyleProperty("flex-grow", "0")
                    col.setStyleProperty("flex-shrink", "0")
                    col.setStyleProperty("flex-basis", roundedPct)
                    col.setStyleProperty("width", roundedPct)
                    col.setStyleProperty("max-width", roundedPct)
                }
                else -> {
                    col.setStyleProperty("flex-grow", "1")
                    col.setStyleProperty("flex-shrink", "0")
                    col.setStyleProperty("flex-basis", "0%")
                    col.removeStyleProperty("width")
                    col.removeStyleProperty("max-width")
                }
            }
        }
    }

    override fun dispose() {
        isDisposed = true
    }
}
