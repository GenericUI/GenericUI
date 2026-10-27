package com.nexustools.genericui.component.layout

import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.nativeimpl.layout.ContainerNativeImpl

/**
 * Standard Bootstrap 5 container (`Container`) and base class for layout containers and [com.nexustools.genericui.component.fragment.Fragment].
 */
open class Container(
    classPrefix: String = "container"
) : AbstractContainer(classPrefix) {

    init {
        htmlTag = "genericui:container"
        if (classPrefix.isNotBlank() && classPrefix !in BUILTIN_LAYOUT_PREFIXES) {
            setClass(classPrefix)
        }
    }

    constructor(tagName: String, classPrefix: String = "") : this(classPrefix) {
        this.htmlTag = tagName
    }

    override fun createNativeImpl(factory: NativeFactory): ContainerNativeImpl<*> =
        factory.createContainer(componentClassPrefix, htmlTag)

    companion object {
        private val BUILTIN_LAYOUT_PREFIXES = setOf(
            "container",
            "fragment",
            "row",
            "col",
            "hstack",
            "vstack",
            "position-relative",
            "d-flex",
            "list-group"
        )
    }
}
