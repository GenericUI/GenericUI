package com.nexustools.genericui.style.sass

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.style.css.CssStyleSheet

/**
 * Live SCSS editing, validation, compilation, and real-time stylesheet application engine.
 *
 * Allows applications and developer tools to edit SCSS source text (`$variables`, `@mixin` / `@include`,
 * nested selectors, `&` parent selectors, `@media` queries, and `genericui:*` custom tags) at runtime,
 * immediately compiling it into a [CssStyleSheet] and applying the resulting styles to all bound
 * root components.
 */
class LiveScssStyleEngine(
    initialScss: String = ""
) {
    var currentScss: String = initialScss
        private set

    var lastCompileResult: ScssCompileResult = SassCompiler.compileDetailed(initialScss)
        private set

    val currentStyleSheet: CssStyleSheet
        get() = lastCompileResult.styleSheet

    val compiledCss: String
        get() = lastCompileResult.compiledCss

    val variables: Map<String, String>
        get() = lastCompileResult.variables

    val errors: List<ScssSyntaxError>
        get() = lastCompileResult.errors

    var revisionCount: Int = if (initialScss.isNotBlank()) 1 else 0
        private set

    private val boundComponents = mutableListOf<AbstractComponent>()
    private val updateListeners = mutableListOf<(ScssCompileResult) -> Unit>()

    /**
     * Binds a root [AbstractComponent] (e.g. a `Window`, `Fragment`, `MdiContainer`, or `Container`)
     * so that every [updateScss] call automatically re-evaluates and applies the compiled [CssStyleSheet]
     * to the component tree.
     */
    fun bindRootComponent(component: AbstractComponent, viewportWidthPx: Int = 1280): LiveScssStyleEngine = apply {
        if (component !in boundComponents) {
            boundComponents.add(component)
        }
        if (lastCompileResult.isSuccess) {
            component.applyStyleSheet(lastCompileResult.styleSheet, viewportWidthPx)
        }
    }

    fun unbindRootComponent(component: AbstractComponent) {
        boundComponents.remove(component)
    }

    /**
     * Re-applies the current compiled [CssStyleSheet] to all bound root components (e.g., when new child
     * components or MDI windows are added dynamically).
     */
    fun reapplyToBoundComponents(viewportWidthPx: Int = 1280) {
        if (!lastCompileResult.isSuccess) return
        for (root in boundComponents) {
            root.applyStyleSheet(lastCompileResult.styleSheet, viewportWidthPx)
        }
    }

    /**
     * Compiles [scssSource] in real time. If syntax validation succeeds, replaces the active [CssStyleSheet],
     * increments [revisionCount], applies the updated styles to all bound components, and notifies listeners.
     */
    fun updateScss(scssSource: String, viewportWidthPx: Int = 1280): ScssCompileResult {
        currentScss = scssSource
        val result = SassCompiler.compileDetailed(scssSource)
        if (result.isSuccess) {
            lastCompileResult = result
            revisionCount += 1
            for (root in boundComponents) {
                root.applyStyleSheet(result.styleSheet, viewportWidthPx)
            }
        } else {
            lastCompileResult = result.copy(
                compiledCss = lastCompileResult.compiledCss,
                styleSheet = lastCompileResult.styleSheet
            )
        }
        for (listener in updateListeners) {
            listener(lastCompileResult)
        }
        return lastCompileResult
    }

    /**
     * Updates a single `$variable` declaration inside [currentScss] (or prepends it if not yet defined)
     * and triggers a live recompile.
     */
    fun setVariable(name: String, value: String, viewportWidthPx: Int = 1280): ScssCompileResult {
        val cleanName = name.trim().removePrefix("$")
        val regex = Regex("(\\\$$cleanName\\s*:\\s*)([^;{}]+)(;)")
        val updatedScss = if (regex.containsMatchIn(currentScss)) {
            currentScss.replaceFirst(regex, "\$1$value\$3")
        } else {
            "\$$cleanName: $value;\n$currentScss"
        }
        return updateScss(updatedScss, viewportWidthPx)
    }

    fun validateScss(scssSource: String): List<ScssSyntaxError> =
        SassCompiler.validateScss(scssSource)

    fun extractVariables(scssSource: String = currentScss): Map<String, String> =
        SassCompiler.extractVariables(scssSource)

    fun addLiveUpdateListener(listener: (ScssCompileResult) -> Unit) {
        updateListeners.add(listener)
    }
}
