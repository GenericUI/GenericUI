package com.nexustools.genericui.style.policy

import com.nexustools.genericui.component.action.Button
import com.nexustools.genericui.component.action.DropdownButton
import com.nexustools.genericui.component.action.StyledButton
import com.nexustools.genericui.component.composite.Composite
import com.nexustools.genericui.component.feedback.Alert
import com.nexustools.genericui.component.feedback.Banner
import com.nexustools.genericui.component.feedback.Toast
import com.nexustools.genericui.component.form.StyledSlider
import com.nexustools.genericui.component.layout.LayoutBackendTarget
import com.nexustools.genericui.component.layout.StyledContainer
import com.nexustools.genericui.component.surface.Card
import com.nexustools.genericui.component.surface.Jumbotron
import com.nexustools.genericui.component.surface.ListGroup
import com.nexustools.genericui.component.surface.ListGroupItem
import com.nexustools.genericui.component.surface.StyledComponent
import com.nexustools.genericui.component.surface.Well
import com.nexustools.genericui.component.typography.Badge
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.enums.system.BackendTarget

/**
 * Policy defining visual styling tiers across GenericUI components and backends:
 *
 * 1. **Explicitly Styled Components** ([StyledContainer], [StyledButton], [StyledComponent],
 *    [Composite], [StyledSlider], [Card], [Badge], [Alert], [Banner], [Toast], [Jumbotron],
 *    [Well]):
 *    - Support full custom backgrounds, alpha translucency, gradients, borders, corner radii, shadows,
 *      and focus rings across all 9 backends.
 *    - `backdrop-filter` / `backdropBlurPx` is supported across all 9 backends (`WEB`, `ANDROID`,
 *      `IOS`, `IPADOS`, `WINDOWS`, `MACOS`, `GTK`, `KDE`, `SWING`) on explicitly styled surfaces
 *      ([StyledContainer], [StyledButton], [StyledComponent], [Composite], [StyledSlider]).
 * 2. **Bootstrap-Specific Non-Control Classes** ([Badge], [Alert], [Banner], [Toast], [Card],
 *    [ListGroup], [ListGroupItem], [Jumbotron], [Well]):
 *    - Support semantic `BootstrapType` coloring across all backends including system backends.
 * 3. **Standard System-Themed Controls** (`Button`, `Container`, `TextField`, `Select`, `CheckBox`, etc.):
 *    - On Desktop and Mobile backends, use the host OS / system theme appearance (supporting Dark, Light,
 *      and Auto themes) while still obeying layout CSS (`width`, `height`, `flex`, `margin`, `padding`,
 *      `display`, `position`, `gap`, `overflow`).
 *    - Among control components, ONLY [Button] and its subclasses ([Button], `IconButton`,
 *      `ToggleButton`, `FloatingActionButton`, [StyledButton]) support `BootstrapType` on system
 *      backends; all other controls (`DropdownButton`, `SplitDropdownButton`, `ProgressBar`,
 *      `Spinner`, [StyledSlider], [Composite], `Slider`, `RangeSlider`, `CheckBox`, etc.) return
 *      `false` for `BootstrapType` on system backends.
 *    - On Web (`WEB`) and custom style engines (`ALIEN`), all components support full CSS/Bootstrap styling.
 */
object AestheticStylePolicy {

    private val STYLED_CONTAINER_TOKENS = setOf(
        "styledcontainer",
        "styled-container"
    )

    private val STYLED_BACKDROP_TAG_TOKENS = setOf(
        "styledcontainer",
        "styled-container",
        "styledbutton",
        "styled-button",
        "styled-btn",
        "styledcomponent",
        "styled-component",
        "composite",
        "styledslider",
        "styled-slider"
    )

    private val STYLED_TAG_TOKENS = setOf(
        "styledcontainer",
        "styled-container",
        "styledbutton",
        "styled-button",
        "styled-btn",
        "styledcomponent",
        "styled-component",
        "composite",
        "styledslider",
        "styled-slider",
        "card",
        "badge",
        "alert",
        "alert-dismissible",
        "banner",
        "toast",
        "jumbotron",
        "well"
    )

    private val BOOTSTRAP_NON_CONTROL_TOKENS = setOf(
        "badge",
        "alert",
        "alert-dismissible",
        "banner",
        "toast",
        "card",
        "listgroup",
        "list-group",
        "listgroupitem",
        "list-group-item",
        "jumbotron",
        "well"
    )

    private val STANDARD_BUTTON_TAG_TOKENS = setOf(
        "button",
        "btn",
        "iconbutton",
        "icon-button",
        "togglebutton",
        "toggle-button",
        "floatingactionbutton",
        "floating-action-button"
    )

    private val STANDARD_BUTTON_PREFIX_TOKENS = setOf(
        "btn",
        "btn-icon",
        "btn-fab",
        "btn-toggle",
        "button",
        "iconbutton",
        "icon-button",
        "togglebutton",
        "toggle-button",
        "floatingactionbutton",
        "floating-action-button"
    )

    private val EXCLUDED_STANDARD_BUTTON_CONTROL_TOKENS = setOf(
        "btn-dropdown",
        "btn-split-dropdown",
        "dropdown",
        "dropdownbutton",
        "dropdown-button",
        "splitdropdownbutton",
        "split-dropdown-button"
    )

    private fun normalizeTagToken(tagName: String): String =
        tagName.trim().lowercase().removePrefix("genericui:")

    private fun hasClassPrefixToken(classPrefix: String, tokens: Set<String>): Boolean {
        val len = classPrefix.length
        if (len == 0) return false
        var start = -1
        for (i in 0..len) {
            val ch = if (i < len) classPrefix[i] else ' '
            if (ch.isWhitespace()) {
                if (start >= 0) {
                    val sub = if (start == 0 && i == len) classPrefix else classPrefix.substring(start, i)
                    var hasUpper = false
                    for (j in 0 until sub.length) {
                        if (sub[j] in 'A'..'Z') {
                            hasUpper = true
                            break
                        }
                    }
                    val token = if (hasUpper) sub.lowercase() else sub
                    if (token in tokens) return true
                    start = -1
                }
            } else if (start < 0) {
                start = i
            }
        }
        return false
    }

    /**
     * Returns `true` if [component] belongs to the explicitly styled component tier
     * ([StyledContainer], [StyledButton], [StyledComponent], [Composite], [StyledSlider],
     * [Card], [Badge], [Alert], [Banner], [Toast], [Jumbotron], [Well]).
     */
    fun isExplicitlyStyledComponent(component: AbstractComponent): Boolean =
        component is StyledContainer ||
            component is StyledButton ||
            component is StyledComponent ||
            component is Composite ||
            component is StyledSlider ||
            component is Card ||
            component is Badge ||
            component is Alert ||
            component is Banner ||
            component is Toast ||
            component is Jumbotron ||
            component is Well ||
            isExplicitlyStyledTagOrPrefix(component.htmlTag, component.componentClassPrefix)

    /**
     * Returns `true` if [tagName] or [classPrefix] identifies an explicitly styled component.
     */
    fun isExplicitlyStyledTagOrPrefix(tagName: String, classPrefix: String = ""): Boolean {
        val normalizedTag = normalizeTagToken(tagName)
        if (normalizedTag in STYLED_TAG_TOKENS) return true
        return hasClassPrefixToken(classPrefix, STYLED_TAG_TOKENS)
    }

    /**
     * Returns `true` if [component] is a Bootstrap-specific non-control component
     * ([Badge], [Alert], [Banner], [Toast], [Card], [ListGroup], [ListGroupItem],
     * [Jumbotron], [Well]) eligible for `BootstrapType` coloring on system backends.
     */
    fun isBootstrapSpecificNonControlComponent(component: AbstractComponent): Boolean =
        component is Badge ||
            component is Alert ||
            component is Banner ||
            component is Toast ||
            component is Card ||
            component is ListGroup ||
            component is ListGroupItem ||
            component is Jumbotron ||
            component is Well ||
            isBootstrapSpecificNonControlTagOrPrefix(component.htmlTag, component.componentClassPrefix)

    /**
     * Returns `true` if [tagName] or [classPrefix] identifies a Bootstrap-specific non-control
     * component (`badge`, `alert`, `banner`, `toast`, `card`, `list-group`, `list-group-item`,
     * `jumbotron`, `well`).
     */
    fun isBootstrapSpecificNonControlTagOrPrefix(tagName: String, classPrefix: String = ""): Boolean {
        val normalizedTag = normalizeTagToken(tagName)
        if (normalizedTag in BOOTSTRAP_NON_CONTROL_TOKENS) return true
        return hasClassPrefixToken(classPrefix, BOOTSTRAP_NON_CONTROL_TOKENS)
    }

    /**
     * Returns `true` if [component] is a standard system-themed button (`Button`, `IconButton`,
     * `ToggleButton`, `FloatingActionButton`) eligible for `BootstrapType` offscreen sprite tinting,
     * excluding explicitly styled [StyledButton]s and non-`Button` controls (`DropdownButton`,
     * `SplitDropdownButton`, `ProgressBar`, `Spinner`, `StyledSlider`, etc.).
     */
    fun isStandardButtonComponent(component: AbstractComponent): Boolean {
        if (isExplicitlyStyledComponent(component) || component is DropdownButton) return false
        return component is Button ||
            isStandardButtonTagOrPrefix(component.htmlTag, component.componentClassPrefix)
    }

    /**
     * Returns `true` if [tagName] or [classPrefix] identifies a standard button component
     * (`button`, `btn`, `btn-icon`, `btn-fab`, `iconbutton`, `togglebutton`, etc.), excluding
     * explicitly styled tokens such as `styled-btn` / `styled-button` and non-`Button` controls such
     * as `dropdownbutton` / `btn-dropdown` / `btn-split-dropdown`.
     */
    fun isStandardButtonTagOrPrefix(tagName: String, classPrefix: String = ""): Boolean {
        if (isExplicitlyStyledTagOrPrefix(tagName, classPrefix)) return false
        val normalizedTag = normalizeTagToken(tagName)
        if (normalizedTag in EXCLUDED_STANDARD_BUTTON_CONTROL_TOKENS) return false
        if (hasClassPrefixToken(classPrefix, EXCLUDED_STANDARD_BUTTON_CONTROL_TOKENS)) return false
        if (normalizedTag in STANDARD_BUTTON_TAG_TOKENS) return true
        return hasClassPrefixToken(classPrefix, STANDARD_BUTTON_PREFIX_TOKENS)
    }

    /**
     * Returns `true` when [component] supports `BootstrapType` semantic color tinting on system
     * backends: `true` iff [component] is a [Button] (including `IconButton`, `ToggleButton`,
     * `FloatingActionButton`, [StyledButton]), a standard button tag/prefix, or a Bootstrap-specific
     * non-control component ([Badge], [Alert], [Banner], [Toast], [Card], [ListGroup],
     * [ListGroupItem], [Jumbotron], [Well]); and `false` for all other controls and generic
     * explicitly styled components (`StyledContainer`, `StyledComponent`, `Composite`, `StyledSlider`,
     * `DropdownButton`, `SplitDropdownButton`, `ProgressBar`, `Spinner`, `Slider`, `RangeSlider`,
     * `CheckBox`, `RadioButton`, `Switch`, `Select`, `ComboBox`, `TextField`, `ScrollView`,
     * `TabContainer`, `Tab`, `Toolbar`, `CollapsiblePanel`, `Accordion`, `Container`, etc.).
     */
    fun supportsBootstrapColorOnSystemBackend(component: AbstractComponent): Boolean =
        component is Button ||
            isStandardButtonComponent(component) ||
            isBootstrapSpecificNonControlComponent(component)

    /**
     * Returns `true` when [component] supports `BootstrapType` semantic coloring on [backendTarget],
     * preserving full Bootstrap styling on `WEB` and custom style engines (`ALIEN`).
     */
    fun supportsBootstrapColor(
        component: AbstractComponent,
        backendTarget: LayoutBackendTarget
    ): Boolean = backendTarget == LayoutBackendTarget.WEB ||
        component.isHostedInCustomStyleEngine() ||
        supportsBootstrapColorOnSystemBackend(component)

    fun supportsBootstrapColor(
        component: AbstractComponent,
        backendTarget: BackendTarget
    ): Boolean = supportsBootstrapColor(component, LayoutBackendTarget.fromBackendTarget(backendTarget))

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
     * Returns `true` if [component] is a [StyledContainer] (excluding other
     * components such as [StyledButton], [Card], [Badge], [Alert]).
     */
    fun isStyledContainerOnly(component: AbstractComponent): Boolean {
        if (component is StyledContainer) return true
        val normalizedTag = normalizeTagToken(component.htmlTag)
        if (normalizedTag == "styledcontainer" || normalizedTag == "styled-container") return true
        return hasClassPrefixToken(component.componentClassPrefix, STYLED_CONTAINER_TOKENS)
    }

    /**
     * Returns `true` when [component] supports `backdrop-filter` on [backendTarget]:
     * both [StyledContainer] and [StyledButton] across all 9 backends
     * (`WEB`, `ANDROID`, `IOS`, `IPADOS`, `WINDOWS`, `MACOS`, `GTK`, `KDE`, `SWING`).
     */
    fun supportsBackdropFilterBlur(
        component: AbstractComponent,
        backendTarget: LayoutBackendTarget
    ): Boolean {
        if (component is StyledContainer ||
            component is StyledButton ||
            component is StyledComponent ||
            component is Composite
        ) return true
        val normalizedTag = normalizeTagToken(component.htmlTag)
        if (normalizedTag in STYLED_BACKDROP_TAG_TOKENS) return true
        return hasClassPrefixToken(component.componentClassPrefix, STYLED_BACKDROP_TAG_TOKENS)
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
