package com.nexustools.genericui.component.layout

import com.nexustools.genericui.enums.bootstrap.DisplayMode
import com.nexustools.genericui.enums.bootstrap.FlexDirection
import com.nexustools.genericui.enums.bootstrap.FlexWrap
import com.nexustools.genericui.style.css.CssLength

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
                    col.getStyle().apply {
                        setFlexGrow(0f)
                        setFlexShrink(0f)
                        setFlexBasis(CssLength.AUTO)
                        setWidth(CssLength.AUTO)
                        setMaxWidth(null)
                    }
                }
                effectiveSpan != null -> {
                    val clamped = effectiveSpan.coerceIn(1, 12)
                    val pct = (clamped * 100.0) / 12.0
                    val roundedPct = ((pct * 10000.0).toInt() / 10000.0).toFloat()
                    val pctLen = CssLength.percent(roundedPct)
                    col.getStyle().apply {
                        setFlexGrow(0f)
                        setFlexShrink(0f)
                        setFlexBasis(pctLen)
                        setWidth(pctLen)
                        setMaxWidth(pctLen)
                    }
                }
                else -> {
                    col.getStyle().apply {
                        setFlexGrow(1f)
                        setFlexShrink(0f)
                        setFlexBasis(CssLength.percent(0))
                        setWidth(null)
                        setMaxWidth(null)
                    }
                }
            }
        }
    }

    override fun dispose() {
        isDisposed = true
    }
}
