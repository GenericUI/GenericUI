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
    var showCloseButton: Boolean = true
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

    internal var onInstructionsChanged: (() -> Unit)? = null

    fun addLeading(component: AbstractComponent): TitleBar {
        _leadingComponents.add(component)
        onInstructionsChanged?.invoke()
        return this
    }

    fun addCenter(component: AbstractComponent): TitleBar {
        _centerComponents.add(component)
        onInstructionsChanged?.invoke()
        return this
    }

    fun addTrailing(component: AbstractComponent): TitleBar {
        _trailingComponents.add(component)
        onInstructionsChanged?.invoke()
        return this
    }

    fun clearComponents() {
        _leadingComponents.clear()
        _centerComponents.clear()
        _trailingComponents.clear()
        onInstructionsChanged?.invoke()
    }
}
