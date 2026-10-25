package com.nexustools.genericui.style.sass

import com.nexustools.genericui.style.css.CssStyleSheet

/**
 * Result of compiling SCSS source text via [SassCompiler.compileDetailed] or [LiveScssStyleEngine].
 */
data class ScssCompileResult(
    val scssSource: String,
    val compiledCss: String,
    val styleSheet: CssStyleSheet,
    val errors: List<ScssSyntaxError> = emptyList(),
    val variables: Map<String, String> = emptyMap(),
    val mixinNames: Set<String> = emptySet()
) {
    val isSuccess: Boolean
        get() = errors.isEmpty()
}
