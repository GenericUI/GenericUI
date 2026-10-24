package com.nexustools.genericui.component.content

/**
 * Monochrome or multi-color icon component.
 */
class Icon(
    iconNameOrSvg: String,
    sizePx: Int = 20
) : ImageView(sourceUrl = iconNameOrSvg) {
    init {
        this.widthPx = sizePx
        this.heightPx = sizePx
    }
}
