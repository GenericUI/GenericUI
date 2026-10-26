package com.nexustools.genericui.component.fragment.helper

import com.nexustools.genericui.core.AbstractComponent

/**
 * Navigation chrome configuration for [FragmentHostFragment].
 *
 * - When [showBackButton] and [showForwardButton] are `false` and [leadingComponents], [centerComponents],
 *   and [trailingComponents] are empty (the default, as used in `DCGame`), [FragmentHostFragment] mounts
 *   **neither** titlebar navigation controls nor a fallback `Navbar`.
 * - When navigation items are enabled and `titleCapabilities.canEmbedComponentsInTitleBar && preferTitleBarNavigation`
 *   is `true`, [FragmentHostFragment] embeds the Back/Forward buttons and custom components directly into the
 *   window's [com.nexustools.genericui.window.TitleBar] (`mountedInTitleBar = true`).
 * - Otherwise, when [showNavbarFallback] is `true`, [FragmentHostFragment] mounts a top `Navbar` below the window
 *   titlebar containing the configured Back/Forward buttons, optional current page title, and custom components.
 */
class FragmentNavigationChromeConfig(
    var showBackButton: Boolean = false,
    var showForwardButton: Boolean = false,
    var hideDisabledNavButtons: Boolean = false,
    var preferTitleBarNavigation: Boolean = true,
    var showNavbarFallback: Boolean = true,
    var showCurrentPageTitleInNavbar: Boolean = true,
    var backButtonText: String = "← Back",
    var forwardButtonText: String = "Forward →"
) {
    val leadingComponents: MutableList<AbstractComponent> = mutableListOf()
    val centerComponents: MutableList<AbstractComponent> = mutableListOf()
    val trailingComponents: MutableList<AbstractComponent> = mutableListOf()

    /** `true` if any back/forward button or custom leading/center/trailing component is configured. */
    val hasNavigationItems: Boolean
        get() = showBackButton ||
            showForwardButton ||
            leadingComponents.isNotEmpty() ||
            centerComponents.isNotEmpty() ||
            trailingComponents.isNotEmpty()

    fun addLeading(component: AbstractComponent): FragmentNavigationChromeConfig = apply {
        if (component !in leadingComponents) {
            leadingComponents.add(component)
        }
    }

    fun addCenter(component: AbstractComponent): FragmentNavigationChromeConfig = apply {
        if (component !in centerComponents) {
            centerComponents.add(component)
        }
    }

    fun addTrailing(component: AbstractComponent): FragmentNavigationChromeConfig = apply {
        if (component !in trailingComponents) {
            trailingComponents.add(component)
        }
    }

    fun clearCustomComponents() {
        for (comp in leadingComponents + centerComponents + trailingComponents) {
            comp.mountedInTitleBar = false
        }
        leadingComponents.clear()
        centerComponents.clear()
        trailingComponents.clear()
    }
}
