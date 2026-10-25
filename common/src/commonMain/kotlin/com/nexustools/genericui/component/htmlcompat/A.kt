package com.nexustools.genericui.component.htmlcompat

import com.nexustools.genericui.component.typography.Label

open class A(
    text: String = "",
    href: String? = null,
    classPrefix: String = "link-primary"
) : Label(text = text, tagName = "a", classPrefix = classPrefix) {
    var href: String? = href
        set(value) {
            field = value
            if (value != null) {
                setAttribute("href", value)
            } else {
                removeAttribute("href")
            }
        }

    var download: String? = null
        set(value) {
            field = value
            if (value != null) {
                setAttribute("download", value)
            } else {
                removeAttribute("download")
            }
        }

    init {
        htmlTag = "a"
        if (href != null) {
            setAttribute("href", href)
        }
    }
}
