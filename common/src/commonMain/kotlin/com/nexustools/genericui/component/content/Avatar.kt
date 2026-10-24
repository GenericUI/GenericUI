package com.nexustools.genericui.component.content

import com.nexustools.genericui.enums.bootstrap.BorderRadiusStyle
import com.nexustools.genericui.enums.ui.ObjectFit

/**
 * Circular or rounded user/channel avatar component with initials fallback.
 */
class Avatar(
    imageUrl: String? = null,
    initialsFallback: String = "",
    sizePx: Int = 40
) : ImageView(sourceUrl = imageUrl, objectFit = ObjectFit.COVER) {
    init {
        this.fallbackText = initialsFallback
        this.widthPx = sizePx
        this.heightPx = sizePx
        this.borderRadiusStyle = BorderRadiusStyle.CIRCLE
    }
}
