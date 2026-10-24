package com.nexustools.genericui.component.shell

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.nativeimpl.shell.MenuNativeImpl

class Menu(label: String = "") : AbstractContainer("nav-item") {
    private var labelState: String = label

    override fun createNativeImpl(factory: NativeFactory): MenuNativeImpl<*> =
        factory.createMenu()

    override fun onAttachNative(impl: NativeImpl<*>) {
        super.onAttachNative(impl)
        (impl as? MenuNativeImpl<*>)?.setLabel(labelState)
    }

    fun setLabel(label: String) {
        labelState = label
        typedNative<MenuNativeImpl<*>>()?.setLabel(label)
    }

    fun getLabel(): String = typedNative<MenuNativeImpl<*>>()?.getLabel() ?: labelState

    fun addItem(item: AbstractComponent): Menu {
        addChild(item)
        return this
    }
}
