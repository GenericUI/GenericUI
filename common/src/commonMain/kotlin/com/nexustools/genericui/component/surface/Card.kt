package com.nexustools.genericui.component.surface

import com.nexustools.genericui.component.typography.Heading
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.nativeimpl.layout.ContainerNativeImpl

/**
 * Bootstrap card surface container (`<div class="card">`).
 */
open class Card(
    title: String? = null
) : AbstractContainer() {
    var title: String? = title
        set(value) {
            field = value
            headerHeading?.text = value ?: ""
        }

    private var headerHeading: Heading? = null

    init {
        htmlTag = "genericui:card"
        if (title != null) {
            val h = Heading(title, level = 5)
            headerHeading = h
            addChild(h)
        }
    }

    override fun createNativeImpl(factory: NativeFactory): ContainerNativeImpl<*> =
        factory.createContainer("card", htmlTag)
}
