package com.nexustools.genericui.window

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.enums.window.TitleBarControlsPlacement

/**
 * Pure instruction/configuration object describing how a [com.nexustools.genericui.component.shell.Window]
 * should construct its native window titlebar on platforms that support custom titlebars.
 */
class TitleBar(
    var embedMenuBar: Boolean = false,
    var showWindowTitle: Boolean = true,
    var showWindowIcon: Boolean = true,
    var heightPx: Int? = null,
    var controlsPlacement: TitleBarControlsPlacement = TitleBarControlsPlacement.PLATFORM_DEFAULT,
    var customTitleBar: Boolean = true,
    var showMinimizeButton: Boolean = true,
    var showMaximizeButton: Boolean = true,
    var showCloseButton: Boolean = true,
    var iconPath: String? = null
) {
    var htmlTag: String = "genericui:titlebar"

    private val _leadingComponents = mutableListOf<AbstractComponent>()
    private val _centerComponents = mutableListOf<AbstractComponent>()
    private val _trailingComponents = mutableListOf<AbstractComponent>()

    val leadingComponents: List<AbstractComponent>
        get() = _leadingComponents
    val centerComponents: List<AbstractComponent>
        get() = _centerComponents
    val trailingComponents: List<AbstractComponent>
        get() = _trailingComponents

    internal var activeHostEmbedsComponents: Boolean = false
    internal var onInstructionsChanged: (() -> Unit)? = null
    internal var onHostInstructionsChanged: (() -> Unit)? = null
    private var batchUpdateDepth: Int = 0
    private var pendingChangeNotification: Boolean = false

    private fun dispatchInstructionsChanged() {
        if (batchUpdateDepth > 0) {
            pendingChangeNotification = true
            return
        }
        val hostListener = onHostInstructionsChanged
        if (hostListener != null) {
            hostListener.invoke()
        } else {
            onInstructionsChanged?.invoke()
        }
    }

    internal fun beginBatchUpdate() {
        batchUpdateDepth++
    }

    internal fun endBatchUpdate() {
        if (batchUpdateDepth <= 0) return
        batchUpdateDepth--
        if (batchUpdateDepth == 0 && pendingChangeNotification) {
            pendingChangeNotification = false
            dispatchInstructionsChanged()
        }
    }

    /**
     * Notifies the host [com.nexustools.genericui.component.shell.Window] that embedded children or layout
     * properties inside an attached [TitleBar] component have changed and need native re-sync/repaint.
     */
    fun notifyComponentsUpdated() {
        dispatchInstructionsChanged()
    }

    internal fun markEmbeddedComponentsMounted(mounted: Boolean) {
        for (c in _leadingComponents) c.mountedInTitleBar = mounted
        for (c in _centerComponents) c.mountedInTitleBar = mounted
        for (c in _trailingComponents) c.mountedInTitleBar = mounted
    }

    fun setHostEmbedsComponents(embeds: Boolean) {
        activeHostEmbedsComponents = embeds
        markEmbeddedComponentsMounted(embeds)
    }

    fun containsComponent(component: AbstractComponent): Boolean =
        _leadingComponents.any { it === component } ||
            _centerComponents.any { it === component } ||
            _trailingComponents.any { it === component }

    fun addLeading(component: AbstractComponent): TitleBar {
        component.mountedInTitleBar = activeHostEmbedsComponents
        _leadingComponents.add(component)
        dispatchInstructionsChanged()
        return this
    }

    fun addCenter(component: AbstractComponent): TitleBar {
        component.mountedInTitleBar = activeHostEmbedsComponents
        _centerComponents.add(component)
        dispatchInstructionsChanged()
        return this
    }

    fun addTrailing(component: AbstractComponent): TitleBar {
        component.mountedInTitleBar = activeHostEmbedsComponents
        _trailingComponents.add(component)
        dispatchInstructionsChanged()
        return this
    }

    fun removeLeading(component: AbstractComponent): Boolean {
        val idx = _leadingComponents.indexOfFirst { it === component }
        if (idx < 0) return false
        _leadingComponents.removeAt(idx)
        component.mountedInTitleBar = false
        dispatchInstructionsChanged()
        return true
    }

    fun removeCenter(component: AbstractComponent): Boolean {
        val idx = _centerComponents.indexOfFirst { it === component }
        if (idx < 0) return false
        _centerComponents.removeAt(idx)
        component.mountedInTitleBar = false
        dispatchInstructionsChanged()
        return true
    }

    fun removeTrailing(component: AbstractComponent): Boolean {
        val idx = _trailingComponents.indexOfFirst { it === component }
        if (idx < 0) return false
        _trailingComponents.removeAt(idx)
        component.mountedInTitleBar = false
        dispatchInstructionsChanged()
        return true
    }

    fun removeComponent(component: AbstractComponent): Boolean {
        var removed = false
        for (i in _leadingComponents.lastIndex downTo 0) {
            if (_leadingComponents[i] === component) {
                _leadingComponents.removeAt(i)
                removed = true
            }
        }
        for (i in _centerComponents.lastIndex downTo 0) {
            if (_centerComponents[i] === component) {
                _centerComponents.removeAt(i)
                removed = true
            }
        }
        for (i in _trailingComponents.lastIndex downTo 0) {
            if (_trailingComponents[i] === component) {
                _trailingComponents.removeAt(i)
                removed = true
            }
        }
        if (!removed) return false
        component.mountedInTitleBar = false
        dispatchInstructionsChanged()
        return true
    }

    internal fun replaceComponents(
        leading: List<AbstractComponent>,
        center: List<AbstractComponent>,
        trailing: List<AbstractComponent>
    ): Boolean {
        fun listsMatch(current: List<AbstractComponent>, next: List<AbstractComponent>): Boolean {
            if (current.size != next.size) return false
            for (i in current.indices) {
                if (current[i] !== next[i] || current[i].mountedInTitleBar != activeHostEmbedsComponents) {
                    return false
                }
            }
            return true
        }
        if (listsMatch(_leadingComponents, leading) &&
            listsMatch(_centerComponents, center) &&
            listsMatch(_trailingComponents, trailing)
        ) {
            return false
        }
        markEmbeddedComponentsMounted(false)
        _leadingComponents.clear()
        _centerComponents.clear()
        _trailingComponents.clear()
        for (c in leading) {
            c.mountedInTitleBar = activeHostEmbedsComponents
            _leadingComponents.add(c)
        }
        for (c in center) {
            c.mountedInTitleBar = activeHostEmbedsComponents
            _centerComponents.add(c)
        }
        for (c in trailing) {
            c.mountedInTitleBar = activeHostEmbedsComponents
            _trailingComponents.add(c)
        }
        dispatchInstructionsChanged()
        return true
    }

    fun clearComponents() {
        if (_leadingComponents.isEmpty() && _centerComponents.isEmpty() && _trailingComponents.isEmpty()) {
            return
        }
        markEmbeddedComponentsMounted(false)
        _leadingComponents.clear()
        _centerComponents.clear()
        _trailingComponents.clear()
        dispatchInstructionsChanged()
    }
}
