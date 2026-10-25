package com.nexustools.genericui.component.surface

/**
 * Individual collapsible item inside an [Accordion].
 */
class AccordionItem(
    val itemId: String,
    title: String,
    expanded: Boolean = false
) : CollapsiblePanel(title = title, expanded = expanded) {
    init {
        htmlTag = "genericui:accordionitem"
    }
}
