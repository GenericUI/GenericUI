package com.nexustools.genericui.plugin.spi

/**
 * Secondary Web Worker or Service Worker Wasm/JS binary specification contributed to Web builds.
 */
data class WebWorkerBinarySpec(
    val binaryBaseName: String,
    val entryPointFqn: String? = null,
    val serviceWorker: Boolean = true
)
