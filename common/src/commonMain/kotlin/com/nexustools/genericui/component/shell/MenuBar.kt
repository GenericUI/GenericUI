package com.nexustools.genericui.component.shell

import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.nativeimpl.shell.MenuBarNativeImpl

class MenuBar : AbstractContainer("navbar-nav") {
    override fun createNativeImpl(factory: NativeFactory): MenuBarNativeImpl<*> =
        factory.createMenuBar()

    fun addMenu(menu: Menu): MenuBar {
        addChild(menu)
        return this
    }
}
