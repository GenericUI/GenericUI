package com.nexustools.genericui.plugin.spi

/**
 * Service Worker & background worker contributions for GenericUI Web distributions.
 */
data class WebServiceWorkerContribution(
    val enableServiceWorker: Boolean = false,
    val pluginScripts: List<WebSwPluginScript> = emptyList(),
    val workerBinaries: List<WebWorkerBinarySpec> = emptyList(),
    val offlineCacheUrls: List<String> = emptyList()
)
