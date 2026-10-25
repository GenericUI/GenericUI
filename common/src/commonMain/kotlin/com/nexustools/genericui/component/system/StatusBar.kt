package com.nexustools.genericui.component.system

import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.nativeimpl.layout.ContainerNativeImpl

/**
 * Bottom window status bar component displaying application status, cursor coordinates,
 * zoom percentage, or background task progress.
 */
class StatusBar : AbstractContainer() {
    init {
        htmlTag = "genericui:statusbar"
    }

    override fun createNativeImpl(factory: NativeFactory): ContainerNativeImpl<*> =
        factory.createContainer("status-bar", htmlTag)
}
