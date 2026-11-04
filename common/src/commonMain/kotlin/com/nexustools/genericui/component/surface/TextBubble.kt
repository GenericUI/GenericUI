package com.nexustools.genericui.component.surface

import com.nexustools.genericui.component.layout.StyledContainer
import com.nexustools.genericui.component.typography.Text
import com.nexustools.genericui.enums.css.CssBorderStyle
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.CssEdgeLengths

/**
 * Comic-book-style speech/callout bubble component (`<genericui:text-bubble>`) with a configurable
 * triangular pointer ([pointerSide] and [pointerOffset] in `0.0f..1.0f`, where `0.5f` is centered).
 */
open class TextBubble(
    text: String = "",
    pointerSide: TextBubblePointerSide = TextBubblePointerSide.BOTTOM,
    pointerOffset: Float = 0.5f,
    pointerWidthPx: Int = 12,
    pointerHeightPx: Int = 7
) : StyledContainer(
    classPrefix = "gui-text-bubble",
    tagName = "genericui:text-bubble"
) {

    val textLabel: Text = Text(text).apply {
        addClass("gui-text-bubble-label")
    }

    var text: String = text
        set(value) {
            field = value
            textLabel.text = value
            setAttribute("bubbleText", value)
        }

    var pointerSide: TextBubblePointerSide = pointerSide
        set(value) {
            field = value
            syncBubbleAttributesAndStyles()
        }

    var pointerOffset: Float = pointerOffset.coerceIn(0.0f, 1.0f)
        set(value) {
            field = value.coerceIn(0.0f, 1.0f)
            syncBubbleAttributesAndStyles()
        }

    var pointerWidthPx: Int = pointerWidthPx.coerceAtLeast(4)
        set(value) {
            field = value.coerceAtLeast(4)
            syncBubbleAttributesAndStyles()
        }

    var pointerHeightPx: Int = pointerHeightPx.coerceAtLeast(3)
        set(value) {
            field = value.coerceAtLeast(3)
            syncBubbleAttributesAndStyles()
        }

    init {
        htmlTag = "genericui:text-bubble"
        addClass("gui-text-bubble")
        backgroundColor = RgbaColor(15, 18, 26, 238)
        textColor = RgbaColor(248, 250, 252, 255)
        setBorder(1, RgbaColor(255, 255, 255, 48), CssBorderStyle.SOLID, radiusPx = 8)
        getStyle().setPadding(CssEdgeLengths.px(4, 10))
        setAttribute("bubbleText", text)
        addChild(textLabel)
        syncBubbleAttributesAndStyles()
    }

    private fun syncBubbleAttributesAndStyles() {
        setAttribute("pointerSide", pointerSide.name)
        setAttribute("pointerOffset", pointerOffset.toString())
        setAttribute("pointerWidthPx", pointerWidthPx.toString())
        setAttribute("pointerHeightPx", pointerHeightPx.toString())

        for (side in TextBubblePointerSide.entries) {
            val cls = "gui-text-bubble-pointer-${side.name.lowercase()}"
            if (side == pointerSide) {
                addClass(cls)
            } else {
                removeClass(cls)
            }
        }

        setStyleProperty("--gui-bubble-pointer-offset", "${(pointerOffset * 100f)}%")
        setStyleProperty("--gui-bubble-pointer-width", "${pointerWidthPx}px")
        setStyleProperty("--gui-bubble-pointer-height", "${pointerHeightPx}px")
    }
}
