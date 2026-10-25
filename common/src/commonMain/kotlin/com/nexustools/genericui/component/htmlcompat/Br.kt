package com.nexustools.genericui.component.htmlcompat

import com.nexustools.genericui.component.layout.Spacer

open class Br(
    heightPx: Int = 8
) : Spacer(widthPx = 0, heightPx = heightPx) {
    init {
        htmlTag = "br"
    }
}
