package com.nexustools.genericui.style.sass

/**
 * Diagnostic entry produced when validating or live-compiling SCSS source text.
 */
data class ScssSyntaxError(
    val line: Int,
    val column: Int,
    val message: String,
    val severity: String = "ERROR"
)
