package com.nexustools.genericui.component.layout

import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.geometry.Size

/**
 * Flexible or fixed-size layout spacing component (`<genericui:spacer>`).
 *
 * - When constructed with `widthPx <= 0 && heightPx <= 0` (e.g. `Spacer()` or `Spacer.fill()`), defaults to an
 *   expanding flexible spacer (`weight = 1f`, `flex: 1 1 0%`) that fills remaining space in an
 *   [HStack], [VStack], or [FlexBox].
 * - When constructed with a positive `widthPx` and/or `heightPx` (e.g. `Spacer(widthPx = 12)` or
 *   `Spacer.horizontal(12)`), acts as a non-shrinking fixed-size gap (`flex: 0 0 auto`,
 *   `width`/`min-width` and/or `height`/`min-height`).
 */
open class Spacer(
    widthPx: Int = 0,
    heightPx: Int = 0,
    weight: Float = if (widthPx <= 0 && heightPx <= 0) 1f else 0f
) : Container("genericui:spacer", "spacer") {

    var spacerWidthPx: Int = widthPx.coerceAtLeast(0)
        set(value) {
            field = value.coerceAtLeast(0)
            syncSpacerMetrics()
        }

    var spacerHeightPx: Int = heightPx.coerceAtLeast(0)
        set(value) {
            field = value.coerceAtLeast(0)
            syncSpacerMetrics()
        }

    var weight: Float = weight.coerceAtLeast(0f)
        set(value) {
            field = value.coerceAtLeast(0f)
            syncSpacerMetrics()
        }

    val isFill: Boolean
        get() = weight > 0f

    init {
        htmlTag = "genericui:spacer"
        syncSpacerMetrics()
    }

    private fun syncSpacerMetrics() {
        val w = spacerWidthPx
        val h = spacerHeightPx
        val wt = weight
        setAttribute("spacerWidthPx", w.toString())
        setAttribute("spacerHeightPx", h.toString())
        setAttribute("spacerWeight", wt.toString())
        setAttribute("spacerFill", (wt > 0f).toString())
        setStyleProperty("display", "inline-block")
        setStyleProperty("pointer-events", "none")
        if (w > 0 || h > 0) {
            setPreferredSize(Size(w, h))
        }
        if (w > 0) {
            widthPx = w
            minWidthPx = w
            setStyleProperty("width", "${w}px")
            setStyleProperty("min-width", "${w}px")
        } else {
            widthPx = null
            minWidthPx = null
            removeStyleProperty("width")
            removeStyleProperty("min-width")
        }
        if (h > 0) {
            heightPx = h
            minHeightPx = h
            setStyleProperty("height", "${h}px")
            setStyleProperty("min-height", "${h}px")
        } else {
            heightPx = null
            minHeightPx = null
            removeStyleProperty("height")
            removeStyleProperty("min-height")
        }
        if (wt > 0f) {
            val basis = when {
                w > 0 -> "${w}px"
                h > 0 -> "${h}px"
                else -> "0%"
            }
            setStyleProperty("flex-grow", wt.toString())
            setStyleProperty("flex-shrink", "1")
            setStyleProperty("flex-basis", basis)
            setStyleProperty("flex", "$wt 1 $basis")
            addClass("flex-grow-1")
            removeClass("flex-shrink-0")
        } else {
            setStyleProperty("flex-grow", "0")
            setStyleProperty("flex-shrink", "0")
            setStyleProperty("flex-basis", "auto")
            setStyleProperty("flex", "0 0 auto")
            removeClass("flex-grow-1")
            addClass("flex-shrink-0")
        }
    }

    override fun onAttachNative(impl: NativeImpl<*>) {
        super.onAttachNative(impl)
        impl.setAttribute("spacerWidthPx", spacerWidthPx.toString())
        impl.setAttribute("spacerHeightPx", spacerHeightPx.toString())
        impl.setAttribute("spacerWeight", weight.toString())
        impl.setAttribute("spacerFill", (weight > 0f).toString())
    }

    companion object {
        fun horizontal(widthPx: Int): Spacer = Spacer(widthPx = widthPx, heightPx = 0, weight = 0f)
        fun vertical(heightPx: Int): Spacer = Spacer(widthPx = 0, heightPx = heightPx, weight = 0f)
        fun fixed(widthPx: Int, heightPx: Int = widthPx): Spacer = Spacer(widthPx = widthPx, heightPx = heightPx, weight = 0f)
        fun fill(weight: Float = 1f): Spacer = Spacer(widthPx = 0, heightPx = 0, weight = weight)
        fun expanding(weight: Float = 1f): Spacer = Spacer(widthPx = 0, heightPx = 0, weight = weight)
    }
}
