package com.nexustools.genericui.component.fragment

import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.enums.bootstrap.OverflowBehavior
import com.nexustools.genericui.nativeimpl.layout.ScrollViewNativeImpl

/**
 * Scrollable [Fragment] backed by [ScrollViewNativeImpl] (`factory.createScrollView()`).
 *
 * Automatically enables scrolling (`overflow-auto`) for full-page fragment layouts
 * and provides programmatic scroll controls ([scrollTo], [scrollToBottom]) and scroll listeners ([onScroll]).
 */
open class ScrollFragment : Fragment() {

    var scrollX: Int = 0
        private set
    var scrollY: Int = 0
        private set

    private var onScrollHandler: ((scrollX: Int, scrollY: Int) -> Unit)? = null

    init {
        htmlTag = "genericui:scrollfragment"
        getBootstrap().overflow = OverflowBehavior.AUTO
        addClass("scroll-fragment")
    }

    override fun createNativeImpl(factory: NativeFactory): ScrollViewNativeImpl<*> =
        factory.createScrollView()

    override fun onAttachNative(impl: NativeImpl<*>) {
        super.onAttachNative(impl)
        val scrollImpl = impl as? ScrollViewNativeImpl<*> ?: return
        if (scrollX != 0 || scrollY != 0) {
            scrollImpl.scrollTo(scrollX, scrollY)
        }
    }

    fun scrollTo(x: Int, y: Int) {
        scrollX = x.coerceAtLeast(0)
        scrollY = y.coerceAtLeast(0)
        typedNative<ScrollViewNativeImpl<*>>()?.scrollTo(scrollX, scrollY)
        onScrollHandler?.invoke(scrollX, scrollY)
    }

    fun scrollToBottom() {
        typedNative<ScrollViewNativeImpl<*>>()?.scrollToBottom()
    }

    fun onScroll(handler: (scrollX: Int, scrollY: Int) -> Unit) {
        onScrollHandler = handler
    }

    fun dispatchScroll(x: Int, y: Int) {
        scrollX = x.coerceAtLeast(0)
        scrollY = y.coerceAtLeast(0)
        onScrollHandler?.invoke(scrollX, scrollY)
    }
}
