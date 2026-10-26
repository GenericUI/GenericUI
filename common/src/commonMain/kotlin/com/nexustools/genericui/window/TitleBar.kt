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

    internal fun markEmbeddedComponentsMounted(mounted: Boolean) {
        for (c in _leadingComponents) c.mountedInTitleBar = mounted
        for (c in _centerComponents) c.mountedInTitleBar = mounted
        for (c in _trailingComponents) c.mountedInTitleBar = mounted
    }

    fun setHostEmbedsComponents(embeds: Boolean) {
        activeHostEmbedsComponents = embeds
        markEmbeddedComponentsMounted(embeds)
    }

    fun addLeading(component: AbstractComponent): TitleBar {
        component.mountedInTitleBar = activeHostEmbedsComponents
        _leadingComponents.add(component)
        onInstructionsChanged?.invoke()
        return this
    }

    fun addCenter(component: AbstractComponent): TitleBar {
        component.mountedInTitleBar = activeHostEmbedsComponents
        _centerComponents.add(component)
        onInstructionsChanged?.invoke()
        return this
    }

    fun addTrailing(component: AbstractComponent): TitleBar {
        component.mountedInTitleBar = activeHostEmbedsComponents
        _trailingComponents.add(component)
        onInstructionsChanged?.invoke()
        return this
    }

    fun clearComponents() {
        markEmbeddedComponentsMounted(false)
        _leadingComponents.clear()
        _centerComponents.clear()
        _trailingComponents.clear()
        onInstructionsChanged?.invoke()
    }
}
