package com.nexustools.genericui.component.layout

import com.nexustools.genericui.geometry.Size

class Spacer(widthPx: Int = 0, heightPx: Int = 0) : Container("spacer") {
    init {
        if (widthPx > 0 || heightPx > 0) {
            setPreferredSize(Size(widthPx, heightPx))
        }
    }
}
