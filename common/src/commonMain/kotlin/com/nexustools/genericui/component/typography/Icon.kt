package com.nexustools.genericui.component.typography

import com.nexustools.genericui.resources.ResourceID

/**
 * Vector or bitmap icon component (`<i>`) accepting an icon name/SVG or a compiled [ResourceID]
 * (`R.drawable.*` / `Resource.drawable.*`).
 */
open class Icon(
    iconNameOrSvg: String = "",
    sizePx: Int = 20
) : com.nexustools.genericui.component.content.Icon(
    iconNameOrSvg = iconNameOrSvg,
    sizePx = sizePx
) {
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
}
