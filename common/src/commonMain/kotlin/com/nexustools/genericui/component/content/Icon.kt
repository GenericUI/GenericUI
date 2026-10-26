package com.nexustools.genericui.component.content

import com.nexustools.genericui.resources.ResourceID

/**
 * Monochrome or multi-color icon component.
 */
open class Icon(
    iconNameOrSvg: String = "",
    sizePx: Int = 20
) : ImageView(sourceUrl = iconNameOrSvg) {

    var iconRes: ResourceID? = null
        protected set

    init {
        htmlTag = "i"
        this.widthPx = sizePx
        this.heightPx = sizePx
    }

    constructor(resId: ResourceID) : this(
        iconNameOrSvg = resId.toResourceUri(),
        sizePx = 20
    ) {
        this.iconRes = resId
    }

    constructor(resId: ResourceID, sizePx: Int) : this(
        iconNameOrSvg = resId.toResourceUri(),
        sizePx = sizePx
    ) {
        this.iconRes = resId
    }

    open fun setIcon(resId: ResourceID) {
        this.iconRes = resId
        this.sourceUrl = resId.toResourceUri()
    }

    open fun setIcon(iconNameOrSvg: String) {
        this.iconRes = null
        this.sourceUrl = iconNameOrSvg
    }
}
