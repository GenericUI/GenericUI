package com.nexustools.genericui.component.layout

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.nativeimpl.layout.DividerNativeImpl

open class Divider : AbstractComponent("hr", htmlTag = "hr") {
    override fun createNativeImpl(factory: NativeFactory): DividerNativeImpl<*> =
        factory.createDivider()
}
