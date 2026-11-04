package com.nexustools.genericui.component.layout

import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.Disposable
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.window.IWindow
import com.nexustools.genericui.window.WindowConfiguration

/**
 * Shared composite layout helper that coordinates child components inside [host]
 * and delegates target-specific layout behavior to [impl] created by [LayoutFactory].
 */
abstract class Layout<I : LayoutImpl>(
    val host: AbstractContainer,
    open var breakpointSpec: HybridBreakpointSpec = HybridBreakpointSpec.DEFAULT
) : Disposable {

    private var cachedImpl: I? = null
    private var cachedFactory: LayoutFactory? = null

    protected abstract fun createImpl(factory: LayoutFactory): I

    val impl: I
        get() {
            val resolvedFactory = (host.resolveActiveFactory() ?: GenericUI.activeFactory)?.layoutFactory
                ?: DefaultLayoutFactory
            val current = cachedImpl
            if (current != null && !current.isDisposed && cachedFactory === resolvedFactory) {
                return current
            }
            current?.dispose()
            cachedFactory = resolvedFactory
            return createImpl(resolvedFactory).also { cachedImpl = it }
        }

    var isMounted: Boolean = false
        private set

    fun ensureMounted() {
        if (!isMounted) {
            isMounted = true
            onMount()
        }
    }

    protected abstract fun onMount()

    open fun onAttachWindow(window: IWindow) {
        ensureMounted()
        impl.onAttachWindow(window)
    }

    open fun onConfigurationChanged(config: WindowConfiguration) {
        ensureMounted()
    }

    open fun onResize(viewportWidth: Int, viewportHeight: Int) {
        ensureMounted()
    }

    override var isDisposed: Boolean = false
        protected set

    override fun dispose() {
        if (isDisposed) return
        isDisposed = true
        cachedImpl?.dispose()
        cachedImpl = null
    }
}
