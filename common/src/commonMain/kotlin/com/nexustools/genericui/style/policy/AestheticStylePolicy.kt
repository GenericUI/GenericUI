package com.nexustools.genericui.style.policy

import com.nexustools.genericui.component.action.StyledButton
import com.nexustools.genericui.component.feedback.Alert
import com.nexustools.genericui.component.layout.LayoutBackendTarget
import com.nexustools.genericui.component.layout.StyledContainer
import com.nexustools.genericui.component.surface.Card
import com.nexustools.genericui.component.surface.TextBubble
import com.nexustools.genericui.component.typography.Badge
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.enums.system.BackendTarget

/**
 * Policy defining visual styling tiers across GenericUI components and backends:
 *
 * 1. **Explicitly Styled Components** ([StyledContainer], [StyledButton], [TextBubble], [Card], [Badge], [Alert]):
 *    - Support full custom backgrounds, alpha translucency, gradients, borders, corner radii, shadows,
 *      and focus rings across all 9 backends.
 *    - `backdrop-filter` / `backdropBlurPx` is supported on both [StyledContainer] (excluding
 *      [TextBubble]) and [StyledButton] across all 9 backends (`WEB`, `ANDROID`, `IOS`, `IPADOS`,
 *      `WINDOWS`, `MACOS`, `GTK`, `QT`, `SWING`).
 * 2. **Standard System-Themed Controls** (`Button`, `Container`, `TextInput`, `Select`, `CheckBox`, etc.):
 *    - On Desktop and Mobile backends, use the host OS / system theme appearance (supporting Dark, Light,
 *      and Auto themes) while still obeying layout CSS (`width`, `height`, `flex`, `margin`, `padding`,
 *      `display`, `position`, `gap`, `overflow`).
 *    - On Web, all components support full CSS styling natively in the browser DOM.
 */
object AestheticStylePolicy {

    private val STYLED_BACKDROP_TAG_TOKENS = setOf(
        "styledcontainer",
        "styled-container",
        "styledbutton",
        "styled-button",
        "styled-btn"
    )

    private val STYLED_TAG_TOKENS = setOf(
        "styledcontainer",
        "styled-container",
        "styledbutton",
        "styled-button",
        "styled-btn",
        "textbubble",
        "text-bubble",
        "gui-text-bubble",
        "card",
        "badge",
        "alert"
    )

    /**
     * Returns `true` if [component] belongs to the explicitly styled component tier
     * ([StyledContainer], [StyledButton], [TextBubble], [Card], [Badge], [Alert]).
     */
    fun isExplicitlyStyledComponent(component: AbstractComponent): Boolean =
        component is StyledContainer ||
            component is StyledButton ||
            component is TextBubble ||
            component is Card ||
            component is Badge ||
            component is Alert ||
            isExplicitlyStyledTagOrPrefix(component.htmlTag, component.componentClassPrefix)

    /**
     * Returns `true` if [tagName] or [classPrefix] identifies an explicitly styled component.
     */
    fun isExplicitlyStyledTagOrPrefix(tagName: String, classPrefix: String = ""): Boolean {
        val normalizedTag = tagName.trim().lowercase().removePrefix("genericui:")
        if (normalizedTag in STYLED_TAG_TOKENS) return true
        val prefixTokens = classPrefix.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return prefixTokens.any { it in STYLED_TAG_TOKENS }
    }

    /**
     * Returns `true` when [component] supports extended visual styling (gradients, custom borders,
     * corner radii, shadows, custom backgrounds) on [backendTarget].
     */
    fun supportsExtendedVisualStyles(
        component: AbstractComponent,
        backendTarget: LayoutBackendTarget
    ): Boolean = backendTarget == LayoutBackendTarget.WEB || isExplicitlyStyledComponent(component)

    fun supportsExtendedVisualStyles(
        component: AbstractComponent,
        backendTarget: BackendTarget
    ): Boolean = supportsExtendedVisualStyles(component, LayoutBackendTarget.fromBackendTarget(backendTarget))

    /**
     * Returns `true` if [component] is a [StyledContainer] (excluding [TextBubble] and other
     * components such as [StyledButton], [Card], [Badge], [Alert]).
     */
    fun isStyledContainerOnly(component: AbstractComponent): Boolean {
        if (component is TextBubble) return false
        if (component is StyledContainer) return true
        val normalizedTag = component.htmlTag.trim().lowercase().removePrefix("genericui:")
        if (normalizedTag == "styledcontainer" || normalizedTag == "styled-container") return true
        val prefixTokens = component.componentClassPrefix.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return prefixTokens.any { it == "styledcontainer" || it == "styled-container" }
    }

    /**
     * Returns `true` when [component] supports `backdrop-filter` on [backendTarget]:
     * both [StyledContainer] (excluding [TextBubble]) and [StyledButton] across all 9 backends
     * (`WEB`, `ANDROID`, `IOS`, `IPADOS`, `WINDOWS`, `MACOS`, `GTK`, `QT`, `SWING`).
     */
    fun supportsBackdropFilterBlur(
        component: AbstractComponent,
        backendTarget: LayoutBackendTarget
    ): Boolean {
        if (component is TextBubble) return false
        if (component is StyledContainer || component is StyledButton) return true
        val normalizedTag = component.htmlTag.trim().lowercase().removePrefix("genericui:")
        if (normalizedTag in STYLED_BACKDROP_TAG_TOKENS) return true
        val prefixTokens = component.componentClassPrefix.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return prefixTokens.any { it in STYLED_BACKDROP_TAG_TOKENS }
    }

    fun supportsBackdropFilterBlur(
        component: AbstractComponent,
        backendTarget: BackendTarget
    ): Boolean = supportsBackdropFilterBlur(component, LayoutBackendTarget.fromBackendTarget(backendTarget))

    /**
     * Returns `true` when [component] should render using native/system theme control appearance
     * (supporting Dark, Light, and Auto system themes) rather than custom CSS skinning.
     */
    fun usesSystemThemeControlAppearance(
        component: AbstractComponent,
        backendTarget: LayoutBackendTarget
    ): Boolean = backendTarget != LayoutBackendTarget.WEB && !isExplicitlyStyledComponent(component)

    fun usesSystemThemeControlAppearance(
        component: AbstractComponent,
        backendTarget: BackendTarget
    ): Boolean = usesSystemThemeControlAppearance(component, LayoutBackendTarget.fromBackendTarget(backendTarget))
}
