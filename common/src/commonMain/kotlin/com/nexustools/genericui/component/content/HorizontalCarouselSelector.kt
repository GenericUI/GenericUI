package com.nexustools.genericui.component.content

import com.nexustools.genericui.component.action.Button
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.layout.HStack
import com.nexustools.genericui.component.layout.VStack
import com.nexustools.genericui.enums.bootstrap.BootstrapSize
import com.nexustools.genericui.enums.bootstrap.BootstrapType

/**
 * Horizontal centered carousel selector (`entries`) with `«` / `»` wrap-around navigation,
 * unselected entry opacity dimming (`0.35f`), proportional height/scale comparison
 * ([setItemScaleRatio]), and active selection callback.
 */
open class HorizontalCarouselSelector : VStack() {

    private class CarouselEntry(
        val id: String,
        val container: Container,
        var scaleRatio: Float = 1.0f
    )

    val viewport: Container = Container("div", "entries")

    val trackTable: HStack = HStack().apply {
        addClass("carousel-track")
    }

    val controlsBar: HStack = HStack()
    val prevButton: Button = Button("«", BootstrapType.SECONDARY, BootstrapSize.SM)
    val selectButton: Button = Button("Select", BootstrapType.PRIMARY, BootstrapSize.SM)
    val nextButton: Button = Button("»", BootstrapType.SECONDARY, BootstrapSize.SM)

    private val entries = mutableListOf<CarouselEntry>()

    var activeIndex: Int = 0
        private set

    var unselectedOpacity: Float = 0.35f
    var itemSlotWidthPx: Int = 180

    var onActiveItemChanged: ((index: Int, id: String) -> Unit)? = null
    var onItemConfirmed: ((index: Int, id: String) -> Unit)? = null

    init {
        htmlTag = "genericui:horizontalcarouselselector"
        viewport.addChild(trackTable)
        controlsBar.addChild(prevButton)
        controlsBar.addChild(selectButton)
        controlsBar.addChild(nextButton)
        addChild(viewport)
        addChild(controlsBar)

        prevButton.setOnClick { selectPrevious() }
        nextButton.setOnClick { selectNext() }
        selectButton.setOnClick {
            val current = entries.getOrNull(activeIndex)
            if (current != null) {
                onItemConfirmed?.invoke(activeIndex, current.id)
            }
        }
    }

    fun addEntry(id: String, scaleRatio: Float = 1.0f, configure: (Container) -> Unit = {}): Container {
        val entryContainer = Container("div", "blentry cursor-pointer")
        val idx = entries.size
        entryContainer.setOnClick { setActiveEntryIndex(idx) }
        configure(entryContainer)
        entries.add(CarouselEntry(id, entryContainer, scaleRatio.coerceIn(0.1f, 2.0f)))
        trackTable.addChild(entryContainer)
        syncCarouselState()
        return entryContainer
    }

    fun setItemScaleRatio(id: String, ratio: Float) {
        val entry = entries.firstOrNull { it.id == id } ?: return
        entry.scaleRatio = ratio.coerceIn(0.1f, 2.0f)
        val pct = (entry.scaleRatio * 100f).toInt()
        entry.container.getStyle().setHeight(com.nexustools.genericui.style.css.CssLength.percent(pct))
    }

    fun setActiveEntryIndex(index: Int) {
        if (entries.isEmpty()) return
        val count = entries.size
        activeIndex = ((index % count) + count) % count
        syncCarouselState()
        onActiveItemChanged?.invoke(activeIndex, entries[activeIndex].id)
    }

    fun selectPrevious() {
        if (entries.isEmpty()) return
        setActiveEntryIndex(activeIndex - 1)
    }

    fun selectNext() {
        if (entries.isEmpty()) return
        setActiveEntryIndex(activeIndex + 1)
    }

    fun activeEntryId(): String? = entries.getOrNull(activeIndex)?.id

    private fun syncCarouselState() {
        for (i in entries.indices) {
            val entry = entries[i]
            if (i == activeIndex) {
                entry.container.addClass("selected")
                entry.container.opacity = 1.0f
            } else {
                entry.container.removeClass("selected")
                entry.container.opacity = unselectedOpacity
            }
        }
        val centerOffsetPx = -(activeIndex * itemSlotWidthPx)
        trackTable.leftOffset = "${centerOffsetPx}px"
    }
}
