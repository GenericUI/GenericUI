package com.nexustools.genericui.component.navigation

import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.nativeimpl.navigation.NavbarNativeImpl

/**
 * Top application/fragment navigation bar (`<nav class="navbar">`, `MaterialToolbar`, `UINavigationBar`).
 *
 * Optional on any [com.nexustools.genericui.component.fragment.Fragment] via `Fragment.setNavBar(navbar)`.
 */
class Navbar(
    brandTitle: String = ""
) : AbstractContainer() {
    init {
        htmlTag = "genericui:navbar"
    }

    var brandTitle: String = brandTitle
        set(value) {
            field = value
            typedNative<NavbarNativeImpl<*>>()?.setBrandTitle(value)
        }

    override fun createNativeImpl(factory: NativeFactory): NavbarNativeImpl<*> =
        factory.createNavbar()

    override fun syncStateToNative() {
        super.syncStateToNative()
        typedNative<NavbarNativeImpl<*>>()?.setBrandTitle(brandTitle)
    }
}
