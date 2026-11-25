package com.nexustools.genericui.component.navigation

import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.enums.system.BackendTarget
import com.nexustools.genericui.nativeimpl.navigation.NavbarNativeImpl
import com.nexustools.genericui.resources.ResourceID

/**
 * Top application/fragment navigation bar (`<nav class="navbar">`, `MaterialToolbar`, `UINavigationBar`).
 *
 * By default (`showBrand == null`), desktop backends (`Windows`, `MacOS`, `Gtk`, `Kde`, `Swing`) suppress
 * [brandTitle] and [brandIcon] because the OS window titlebar directly above already renders them, whereas
 * `Web`, `Android`, and `IOS` display them unless [showBrand] is explicitly set to `false`.
 */
class Navbar(
    brandTitle: String = "",
    brandIcon: String? = null,
    showBrand: Boolean? = null
) : AbstractContainer() {
    init {
        htmlTag = "genericui:navbar"
    }

    constructor(
        brandTitle: String,
        brandIconRes: ResourceID?,
        showBrand: Boolean? = null
    ) : this(
        brandTitle = brandTitle,
        brandIcon = brandIconRes?.toResourceUri(),
        showBrand = showBrand
    ) {
        this.brandIconRes = brandIconRes
    }

    var brandTitle: String = brandTitle
        set(value) {
            field = value
            notifyParentCacheDirty()
            typedNative<NavbarNativeImpl<*>>()?.setBrandTitle(value)
        }

    var brandIcon: String? = brandIcon
        set(value) {
            field = value
            notifyParentCacheDirty()
            typedNative<NavbarNativeImpl<*>>()?.setBrandIcon(value)
        }

    var brandIconRes: ResourceID? = null
        set(value) {
            field = value
            brandIcon = value?.toResourceUri()
        }

    /**
     * Explicit override controlling whether [brandTitle] and [brandIcon] are rendered inside the navbar.
     * When `null` (default), desktop platforms (`Windows`, `MacOS`, `Gtk`, `Kde`, `Swing`) hide them
     * and non-desktop platforms (`Web`, `Android`, `IOS`) show them.
     */
    var showBrand: Boolean? = showBrand
        set(value) {
            field = value
            notifyParentCacheDirty()
            typedNative<NavbarNativeImpl<*>>()?.setShowBrand(value)
        }

    val isBrandVisible: Boolean
        get() = typedNative<NavbarNativeImpl<*>>()?.isBrandVisible() ?: isBrandVisibleForBackend()

    fun setBrandIcon(resId: ResourceID?) {
        brandIconRes = resId
    }

    fun isBrandVisibleForBackend(
        backendTarget: BackendTarget? = (resolveActiveFactory() ?: GenericUI.activeFactory)?.backendTarget
    ): Boolean {
        showBrand?.let { return it }
        return backendTarget?.isDesktop != true
    }

    protected override fun createNativeImpl(factory: NativeFactory): NavbarNativeImpl<*> =
        factory.createNavbar()

    override fun syncStateToNative() {
        super.syncStateToNative()
        typedNative<NavbarNativeImpl<*>>()?.let { impl ->
            impl.setBrandTitle(brandTitle)
            impl.setBrandIcon(brandIcon)
            impl.setShowBrand(showBrand)
        }
    }
}
