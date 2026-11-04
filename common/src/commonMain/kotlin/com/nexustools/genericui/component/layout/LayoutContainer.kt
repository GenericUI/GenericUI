package com.nexustools.genericui.component.layout

import com.nexustools.genericui.window.WindowConfiguration

/**
 * Base [Container] whose child layout is managed by a shared [Layout] helper.
 */
abstract class LayoutContainer<I : LayoutImpl, L : Layout<I>>(
    tagName: String = "genericui:layout-container",
    classPrefix: String = "gui-layout-container"
) : Container(tagName, classPrefix) {

    abstract val layout: L

    protected fun initializeLayout() {
        layout.ensureMounted()
    }

    open fun onConfigurationChanged(config: WindowConfiguration) {
        layout.onConfigurationChanged(config)
    }

    open fun onResize(viewportWidth: Int, viewportHeight: Int) {
        layout.onResize(viewportWidth, viewportHeight)
    }

    override fun dispose() {
        layout.dispose()
        super.dispose()
    }
}
