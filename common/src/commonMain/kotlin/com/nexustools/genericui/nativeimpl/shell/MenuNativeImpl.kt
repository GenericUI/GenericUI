package com.nexustools.genericui.nativeimpl.shell

import com.nexustools.genericui.core.AbstractContainerNativeImpl

interface MenuNativeImpl<out W> : AbstractContainerNativeImpl<W> {
    fun setLabel(label: String)
    fun getLabel(): String
}
