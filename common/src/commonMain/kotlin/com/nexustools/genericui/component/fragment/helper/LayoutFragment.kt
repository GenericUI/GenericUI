package com.nexustools.genericui.component.fragment.helper

import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.fragment.FragmentBundle
import com.nexustools.genericui.component.layout.Layout
import com.nexustools.genericui.component.layout.LayoutImpl
import com.nexustools.genericui.window.WindowConfiguration

/**
 * Base [Fragment] whose child layout is managed by a shared [Layout] helper.
 */
abstract class LayoutFragment<I : LayoutImpl, L : Layout<I>>(
    id: String? = null
) : Fragment() {

    init {
        if (id != null) {
            this.id = id
        }
    }

    abstract val layout: L

    protected fun initializeLayout() {
        layout.ensureMounted()
    }

    override fun onCreate(savedState: FragmentBundle?) {
        super.onCreate(savedState)
        layout.ensureMounted()
        hostingWindow?.let { layout.onAttachWindow(it) }
    }

    override fun onConfigurationChanged(newConfig: WindowConfiguration) {
        super.onConfigurationChanged(newConfig)
        hostingWindow?.let { layout.onAttachWindow(it) }
        layout.onConfigurationChanged(newConfig)
    }

    override fun onResize(viewportWidth: Int, viewportHeight: Int) {
        super.onResize(viewportWidth, viewportHeight)
        hostingWindow?.let { layout.onAttachWindow(it) }
        layout.onResize(viewportWidth, viewportHeight)
    }

    override fun onDestroy() {
        layout.dispose()
        super.onDestroy()
    }
}
