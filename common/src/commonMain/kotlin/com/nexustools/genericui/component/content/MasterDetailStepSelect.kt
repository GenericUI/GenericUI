package com.nexustools.genericui.component.content

import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.layout.HStack
import com.nexustools.genericui.component.layout.VStack
import com.nexustools.genericui.component.navigation.StepScrollView
import com.nexustools.genericui.component.typography.Label
import com.nexustools.genericui.enums.bootstrap.DisplayMode
import com.nexustools.genericui.enums.bootstrap.FontWeight

/**
 * Master-Detail selector (`vhselect`) combining a left [StepScrollView] item list (with optional
 * superscript status badges `P`/`S`/`TP`/`TS`/`T`/`U` or counts) and a right collapsible
 * [detailContainer] (`itemdescriptions`) that slides open when an item is selected.
 */
open class MasterDetailStepSelect : VStack() {

    private class ItemRowRecord(
        val id: String,
        val title: String,
        val rowContainer: Container,
        val titleLabel: Label,
        val badgeLabel: Label
    )

    val filterHeaderContainer: Container = Container("div", "vhselect-header")
    val mainSplitRow: HStack = HStack().apply {
        addClass("vhlisting")
    }
    val scrollView: StepScrollView = StepScrollView()
    val detailContainer: Container = Container("div", "itemdescriptions ms-1").apply {
        displayMode = DisplayMode.NONE
    }

    private val itemsById = linkedMapOf<String, ItemRowRecord>()
    private val overlayScrollViews = linkedMapOf<String, StepScrollView>()

    var selectedItemId: String? = null
        private set

    var onItemSelected: ((id: String) -> Unit)? = null
    var onSelectionCleared: (() -> Unit)? = null

    init {
        htmlTag = "genericui:masterdetailstepselect"
        addClass("vhselect")
        mainSplitRow.addChild(scrollView)
        mainSplitRow.addChild(detailContainer)
        addChild(filterHeaderContainer)
        addChild(mainSplitRow)
    }

    fun addItem(
        id: String,
        title: String,
        badgeText: String = "",
        configureRow: ((Container) -> Unit)? = null
    ): Container {
        val row = Container("tr", "selectitem cursor-pointer")
        row.id = "selectitem$id"
        val cell = Container("td", "ps-2 pe-2 nowrap")
        val titleLbl = Label(title, "span", "item-name")
        val badgeLbl = Label(badgeText, "sup", "ps-1").apply {
            isVisible = badgeText.isNotEmpty()
        }
        cell.addChild(titleLbl)
        cell.addChild(badgeLbl)
        row.addChild(cell)
        row.setOnClick { selectItem(id) }
        configureRow?.invoke(row)

        itemsById[id] = ItemRowRecord(id, title, row, titleLbl, badgeLbl)
        scrollView.addRow(row)
        scrollView.contentHeightPx = itemsById.size * 32
        scrollView.updateButtonStates()
        return row
    }

    fun clearItems() {
        itemsById.clear()
        scrollView.clearRows()
        clearSelection()
    }

    fun selectItem(id: String): Boolean {
        val target = itemsById[id] ?: return false
        for ((itemId, rec) in itemsById) {
            if (itemId == id) {
                rec.rowContainer.addClass("selected")
            } else {
                rec.rowContainer.removeClass("selected")
            }
        }
        selectedItemId = id
        detailContainer.displayMode = DisplayMode.BLOCK
        detailContainer.isVisible = true
        onItemSelected?.invoke(target.id)
        return true
    }

    fun clearSelection() {
        for (rec in itemsById.values) {
            rec.rowContainer.removeClass("selected")
        }
        selectedItemId = null
        detailContainer.clearChildren()
        detailContainer.displayMode = DisplayMode.NONE
        onSelectionCleared?.invoke()
    }

    fun setItemBadge(id: String, badgeText: String, enabled: Boolean = true, bold: Boolean = false) {
        val rec = itemsById[id] ?: return
        rec.badgeLabel.text = badgeText
        rec.badgeLabel.isVisible = enabled && badgeText.isNotEmpty()
        rec.badgeLabel.fontWeight = if (bold) FontWeight.BOLD else FontWeight.NORMAL
    }

    fun setItemFaded(id: String, faded: Boolean) {
        val rec = itemsById[id] ?: return
        if (faded) {
            rec.rowContainer.addClass("faded")
        } else {
            rec.rowContainer.removeClass("faded")
        }
    }

    fun setItemVisible(id: String, visible: Boolean) {
        val rec = itemsById[id] ?: return
        rec.rowContainer.isVisible = visible
        val visibleCount = itemsById.values.count { it.rowContainer.isVisible }
        scrollView.contentHeightPx = visibleCount * 32
        scrollView.updateButtonStates()
    }

    fun getItemRow(id: String): Container? = itemsById[id]?.rowContainer

    fun registerOverlayScrollView(key: String, overlay: StepScrollView = StepScrollView()) {
        overlayScrollViews[key] = overlay
    }

    fun getOverlayScrollView(key: String): StepScrollView? = overlayScrollViews[key]
}
