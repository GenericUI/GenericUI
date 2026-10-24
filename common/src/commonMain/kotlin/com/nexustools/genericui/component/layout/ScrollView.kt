package com.nexustools.genericui.component.layout

import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.nativeimpl.layout.ScrollViewNativeImpl

open class ScrollView : AbstractContainer("overflow-auto") {

    override fun createNativeImpl(factory: NativeFactory): ScrollViewNativeImpl<*> =
        factory.createScrollView()

    fun scrollTo(x: Int, y: Int) {
        typedNative<ScrollViewNativeImpl<*>>()?.scrollTo(x, y)
    }

    fun scrollToBottom() {
        typedNative<ScrollViewNativeImpl<*>>()?.scrollToBottom()
    }
}
