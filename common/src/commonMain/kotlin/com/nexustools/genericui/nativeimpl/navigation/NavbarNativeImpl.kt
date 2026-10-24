package com.nexustools.genericui.nativeimpl.navigation

import com.nexustools.genericui.core.AbstractContainerNativeImpl

interface NavbarNativeImpl<out W> : AbstractContainerNativeImpl<W> {
    fun setBrandTitle(title: String)
    fun getBrandTitle(): String
}
