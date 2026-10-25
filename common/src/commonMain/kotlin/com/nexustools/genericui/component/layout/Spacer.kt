package com.nexustools.genericui.component.layout

import com.nexustools.genericui.geometry.Size

open class Spacer(widthPx: Int = 0, heightPx: Int = 0) : Container("br", "spacer") {
    init {
        htmlTag = "br"
        if (widthPx > 0 || heightPx > 0) {
            setPreferredSize(Size(widthPx, heightPx))
        }
    }
}
