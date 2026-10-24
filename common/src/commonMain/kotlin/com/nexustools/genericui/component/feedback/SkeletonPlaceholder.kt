package com.nexustools.genericui.component.feedback

import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.nativeimpl.layout.ContainerNativeImpl

/**
 * Animated shimmer skeleton placeholder (`placeholder-glow` / `placeholder-wave`).
 */
class SkeletonPlaceholder(
    widthPx: Int = 160,
    heightPx: Int = 20
) : AbstractContainer() {
    init {
        this.widthPx = widthPx
        this.heightPx = heightPx
    }

    override fun createNativeImpl(factory: NativeFactory): ContainerNativeImpl<*> =
        factory.createContainer("placeholder-glow")
}
