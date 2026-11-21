package com.nexustools.genericui.plugin.spi

import java.io.File

/**
 * Service Worker plugin JS script contributed to Web distribution's unified `./serviceworker.js`.
 */
data class WebSwPluginScript(
    val relativeUrlPath: String,
    val scriptContent: String? = null,
    val sourceFile: File? = null
)
