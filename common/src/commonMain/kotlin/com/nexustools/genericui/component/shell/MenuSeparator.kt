package com.nexustools.genericui.component.shell

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.nativeimpl.layout.DividerNativeImpl

class MenuSeparator : AbstractComponent("dropdown-divider", htmlTag = "genericui:menuseparator") {
    override fun createNativeImpl(factory: NativeFactory): DividerNativeImpl<*> =
        factory.createDivider()
}
