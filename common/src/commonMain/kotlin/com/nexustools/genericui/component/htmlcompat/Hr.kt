package com.nexustools.genericui.component.htmlcompat

import com.nexustools.genericui.component.layout.Divider

open class Hr : Divider() {
    init {
        htmlTag = "hr"
    }
}
