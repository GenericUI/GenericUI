package com.nexustools.genericui.nativeimpl.layout

import com.nexustools.genericui.core.AbstractContainerNativeImpl

interface ScrollViewNativeImpl<out W> : AbstractContainerNativeImpl<W> {
    fun scrollTo(x: Int, y: Int)
    fun scrollToBottom()
}
