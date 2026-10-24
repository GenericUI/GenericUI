package com.nexustools.genericui.component.surface

import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.nativeimpl.layout.ContainerNativeImpl

/**
 * Vertically stacked group of [AccordionItem] panels (`<div class="accordion">`).
 */
class Accordion(
    var allowMultipleExpanded: Boolean = false
) : AbstractContainer() {
    private val _items = mutableListOf<AccordionItem>()
    val items: List<AccordionItem> get() = _items

    fun addItem(item: AccordionItem): Accordion = apply {
        _items.add(item)
        addChild(item)
        item.onExpandedChanged = { expanded ->
            if (expanded && !allowMultipleExpanded) {
                for (other in _items) {
                    if (other !== item && other.isExpanded) {
                        other.isExpanded = false
                    }
                }
            }
        }
    }

    override fun createNativeImpl(factory: NativeFactory): ContainerNativeImpl<*> =
        factory.createContainer("accordion")
}
