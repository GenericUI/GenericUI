package com.nexustools.genericui.nativeimpl.layout

interface ScrollViewNativeImpl<out W> : ContainerNativeImpl<W> {
    fun scrollTo(x: Int, y: Int)
    fun scrollToBottom()
}
