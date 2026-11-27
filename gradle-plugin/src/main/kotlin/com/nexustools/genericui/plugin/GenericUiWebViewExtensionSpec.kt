package com.nexustools.genericui.plugin

/**
 * Configuration block for `:extensions:ui:webview` (`genericui { extensions { webView { ... } } }`).
 *
 * Controls capability flags exposed to the desktop Alien/NJS out-of-process runtime
 * and determines which Alien + media/graphics companion modules are auto-wired on Desktop targets.
 */
class GenericUiWebViewExtensionSpec {
    var enableCanvas2D: Boolean = true
    var enableWebGL: Boolean = true
    var enableMedia: Boolean = true
    var enableSvg: Boolean = true
    var enableWebAudio: Boolean = true
    var enableWebSockets: Boolean = true
    var enableStorage: Boolean = true
    var enableShadowDom: Boolean = true
    var enableIframes: Boolean = true
    var enableWorkers: Boolean = true

    fun toOptionsMap(): Map<String, String> = linkedMapOf(
        OPTION_ENABLE_CANVAS2D to enableCanvas2D.toString(),
        OPTION_ENABLE_WEBGL to enableWebGL.toString(),
        OPTION_ENABLE_MEDIA to enableMedia.toString(),
        OPTION_ENABLE_SVG to enableSvg.toString(),
        OPTION_ENABLE_WEBAUDIO to enableWebAudio.toString(),
        OPTION_ENABLE_WEBSOCKETS to enableWebSockets.toString(),
        OPTION_ENABLE_STORAGE to enableStorage.toString(),
        OPTION_ENABLE_SHADOW_DOM to enableShadowDom.toString(),
        OPTION_ENABLE_IFRAMES to enableIframes.toString(),
        OPTION_ENABLE_WORKERS to enableWorkers.toString()
    )

    companion object {
        const val OPTION_ENABLE_CANVAS2D: String = "webView.enableCanvas2D"
        const val OPTION_ENABLE_WEBGL: String = "webView.enableWebGL"
        const val OPTION_ENABLE_MEDIA: String = "webView.enableMedia"
        const val OPTION_ENABLE_SVG: String = "webView.enableSvg"
        const val OPTION_ENABLE_WEBAUDIO: String = "webView.enableWebAudio"
        const val OPTION_ENABLE_WEBSOCKETS: String = "webView.enableWebSockets"
        const val OPTION_ENABLE_STORAGE: String = "webView.enableStorage"
        const val OPTION_ENABLE_SHADOW_DOM: String = "webView.enableShadowDom"
        const val OPTION_ENABLE_IFRAMES: String = "webView.enableIframes"
        const val OPTION_ENABLE_WORKERS: String = "webView.enableWorkers"
    }
}
