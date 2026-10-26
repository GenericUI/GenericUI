package com.nexustools.genericui.component.navigation

import com.nexustools.genericui.component.action.Button
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.layout.VStack
import com.nexustools.genericui.enums.bootstrap.BootstrapSize
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.enums.bootstrap.OverflowBehavior
import com.nexustools.genericui.enums.bootstrap.PositionMode
import com.nexustools.genericui.enums.bootstrap.TextAlignment

/**
 * Vertical stepper scroll container (`scrollview`) with top `▲` (`selectitemup`) and bottom `▼`
 * (`selectitemdown`) buttons, a clipped [viewport], wrap-around scrolling, and mouse wheel support.
 *
 * Designed for scrollable item lists (`ScrollView`), overlay association pickers, and
 * single-row vertical wheel selectors ([com.nexustools.genericui.component.fragment.helper.VSelectPageFragment]).
 */
open class StepScrollView(
    var wrapAround: Boolean = true,
    var stepHeightPx: Int? = null
) : VStack() {

    val upButton: Button = Button("▲", BootstrapType.SECONDARY, BootstrapSize.SM).apply {
        addClass("selectitemup")
        textAlignment = TextAlignment.CENTER
    }

    val viewport: Container = Container("div", "viewport w-100").apply {
        overflowBehavior = OverflowBehavior.HIDDEN
        positionMode = PositionMode.RELATIVE
        transition = "height .5s ease"
    }

    val contentTable: Container = Container("table", "w-100").apply {
        positionMode = PositionMode.RELATIVE
        transition = "top .5s ease"
        topOffset = "0px"
    }

    val downButton: Button = Button("▼", BootstrapType.SECONDARY, BootstrapSize.SM).apply {
        addClass("selectitemdown")
        textAlignment = TextAlignment.CENTER
    }

    var currentTopOffsetPx: Int = 0
        private set

    var viewportHeightPx: Int = 240
        set(value) {
            field = value.coerceAtLeast(1)
            viewport.heightPx = field
        }

    var contentHeightPx: Int = 240
        set(value) {
            field = value.coerceAtLeast(0)
        }

    var onScrollOffsetChanged: ((topOffsetPx: Int) -> Unit)? = null

    init {
        htmlTag = "genericui:stepscrollview"
        addClass("scrollview selectitemlisting")
        viewport.addChild(contentTable)
        addChild(upButton)
        addChild(viewport)
        addChild(downButton)

        upButton.setOnClick { scrollUp() }
        downButton.setOnClick { scrollDown() }
        viewport.setOnWheel { ev ->
            if (ev.deltaY < 0f) {
                scrollUp()
            } else if (ev.deltaY > 0f) {
                scrollDown()
            }
        }
    }

    fun addRow(row: Container) {
        contentTable.addChild(row)
    }

    fun clearRows() {
        contentTable.clearChildren()
        scrollToTop()
    }

    fun maxScrollDistancePx(): Int =
        (contentHeightPx - viewportHeightPx).coerceAtLeast(0)

    fun isScrollable(): Boolean =
        contentHeightPx > viewportHeightPx

    fun updateButtonStates() {
        val scrollable = isScrollable()
        upButton.isEnabled = scrollable
        downButton.isEnabled = scrollable
    }

    fun scrollUp() {
        val maxDist = maxScrollDistancePx()
        if (maxDist <= 0) {
            scrollToTop()
            return
        }
        val step = (stepHeightPx ?: (viewportHeightPx / 2)).coerceAtLeast(1)
        if (currentTopOffsetPx >= 0) {
            if (wrapAround) {
                scrollToBottom()
            } else {
                scrollToTop()
            }
        } else {
            val next = (currentTopOffsetPx + step).coerceAtMost(0)
            applyTopOffset(next)
        }
    }

    fun scrollDown() {
        val maxDist = maxScrollDistancePx()
        if (maxDist <= 0) {
            scrollToTop()
            return
        }
        val step = (stepHeightPx ?: (viewportHeightPx / 2)).coerceAtLeast(1)
        val minOffset = -maxDist
        if (currentTopOffsetPx <= minOffset) {
            if (wrapAround) {
                scrollToTop()
            } else {
                scrollToBottom()
            }
        } else {
            val next = (currentTopOffsetPx - step).coerceAtLeast(minOffset)
            applyTopOffset(next)
        }
    }

    fun scrollToTop() {
        applyTopOffset(0)
    }

    fun scrollToBottom() {
        val maxDist = maxScrollDistancePx()
        applyTopOffset(-maxDist)
    }

    fun scrollToItem(index: Int, rowHeightPx: Int = stepHeightPx ?: 32) {
        val rowCount = contentTable.children.size
        if (rowCount <= 0) {
            scrollToTop()
            return
        }
        val safeRowH = rowHeightPx.coerceAtLeast(1)
        contentHeightPx = rowCount * safeRowH
        val clampedIndex = index.coerceIn(0, rowCount - 1)
        val targetOffset = -(clampedIndex * safeRowH).coerceAtMost(maxScrollDistancePx())
        applyTopOffset(targetOffset)
    }

    private fun applyTopOffset(offsetPx: Int) {
        currentTopOffsetPx = offsetPx
        contentTable.topOffset = "${offsetPx}px"
        updateButtonStates()
        onScrollOffsetChanged?.invoke(offsetPx)
    }
}
