package com.nexustools.genericui.nativeimpl.navigation

import com.nexustools.genericui.core.AbstractContainerNativeImpl

interface NavbarNativeImpl<out W> : AbstractContainerNativeImpl<W> {
    fun setBrandTitle(title: String)
    fun getBrandTitle(): String

    fun setBrandIcon(icon: String?) {}
    fun getBrandIcon(): String? = null

    fun setShowBrand(show: Boolean?) {}
    fun getShowBrand(): Boolean? = null

    fun isBrandVisible(): Boolean = getShowBrand() ?: true
}
