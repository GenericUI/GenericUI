package com.nexustools.genericui.plugin

/**
 * DSL configuration block for first-party GenericUI extension modules.
 *
 * Extensions in GenericUI follow a flexible two-pattern Gradle architecture:
 * 1. **Project-of-Projects Extensions** (`:extensions:<name>:common`, `:extensions:<name>:desktop`,
 *    `:extensions:<name>:mobile`, `:extensions:<name>:swing`, `:extensions:<name>:web`, plus umbrella
 *    `:extensions:<name>`) for extensions with native C/C++/ObjC, Java/LWJGL, and Web platform-specific implementations:
 *    - `:extensions:gpu` (`GpuDevice`, `GpuOffscreenContext`, `VKContext` primary, `GLContext` fallback with ANGLE on Windows)
 *    - `:extensions:canvas2d` (`Canvas2D`, `Canvas2DContext`, `CurveEditor`)
 *    - `:extensions:canvas3d` (`GLCanvas`, `VKCanvas` — relies on `:extensions:gpu`)
 *    - `:extensions:media` (`VideoPlayer`, `AudioPlayer`, `AudioAnalyzer`, `WebVttSrtParser`, plus `:extensions:media:desktop`
 *      VLC playback, Vulkan/OpenGL ES HDR tone-mapping, and HW/SW decoding — relies on `:extensions:gpu`)
 *    - `:extensions:webview` (`WebView`, `WebViewController`, `WebViewJsBridge` — uses Chromium `ChromiumBlinkEngine` +
 *      `NJS` (`lib/NJS` AsmJit/SLJIT Native JIT Engine) on Desktop (`:desktop` & `:swing`), and native platform `WebView`
 *      on Mobile (`:android` `android.webkit.WebView`, `:ios` `WKWebView`) and `:web` (`<iframe>`), NEVER using NJS on Mobile/Web)
 * 2. **Single Multiplatform Project Extensions** (`:extensions:<name>`) built purely atop `:common` and other extensions:
 *    - `:extensions:imageprocessing` (`GpuImageProcessor`, `GpuImageFilterChain`, `GpuImageKernel` — relies on `:extensions:gpu`
 *      and base `:common` Image API `com.nexustools.genericui.image.*`)
 *    - `:extensions:codeeditor` (`IdeCodeEditor` — relies on `:extensions:canvas2d`)
 *    - `:extensions:aichat` (`AIChatDrawer`, `AiChatContainer`, `AIChatAgentOrchestrator` — relies on `:extensions:codeeditor`)
 *    - `:extensions:threejs` (`ThreeSceneCanvas`, `ThreeGLCanvas`, `ThreeVKCanvas` — relies on `:extensions:gpu` and `:extensions:canvas3d`)
 *    - `:extensions:box2d` (`Box2DWorld`, `Box2DCanvas` — relies on `:extensions:canvas2d` and `:extensions:canvas3d`)
 *    - `:extensions:box3d` (`Box3DWorld`, `Box3DThreeSynchronizer` — relies on `:extensions:canvas3d` and `:extensions:threejs`)
 */
open class GenericUiExtensionsConfig {
    var gpuEnabled: Boolean = false
    var imageProcessingEnabled: Boolean = false
    var canvas2dEnabled: Boolean = false
    var canvas3dEnabled: Boolean = false
    var mediaEnabled: Boolean = false
    var webViewEnabled: Boolean = false
    var codeEditorEnabled: Boolean = false
    var aiChatEnabled: Boolean = false
    var threeJsEnabled: Boolean = false
    var box2dEnabled: Boolean = false
    var box3dEnabled: Boolean = false
    var dslEnabled: Boolean = false

    /** Alias property for [codeEditorEnabled] (`:extensions:codeeditor`). */
    var useCodeEditor: Boolean
        get() = codeEditorEnabled
        set(value) {
            codeEditor(value)
        }

    /**
     * Includes `:extensions:gpu` (unified Vulkan primary + OpenGL ES fallback GPU contexts,
     * `GpuDevice`, `GpuOffscreenContext`, `VKContext`, `GLContext`).
     */
    fun gpu(enabled: Boolean = true) {
        gpuEnabled = enabled
    }

    /**
     * Includes `:extensions:imageprocessing` (offscreen in-memory GPU-accelerated image processing
     * `GpuImageProcessor`, `GpuImageFilterChain`, `GpuImageKernel`). Automatically enables `gpu`.
     */
    fun imageProcessing(enabled: Boolean = true) {
        imageProcessingEnabled = enabled
        if (enabled) {
            gpu(true)
        }
    }

    /**
     * Includes `:extensions:canvas2d` (`Canvas2D`, `Canvas2DContext`, `CurveEditor` — GPU-accelerated
     * via `:extensions:gpu` using Vulkan primary and OpenGL ES / ANGLE fallback). Automatically enables `gpu`.
     */
    fun canvas2d(enabled: Boolean = true) {
        canvas2dEnabled = enabled
        if (enabled) {
            gpu(true)
        }
    }

    /**
     * Includes `:extensions:canvas3d` (`GLCanvas`, `VKCanvas`). Automatically enables `gpu` and `canvas2d`.
     */
    fun canvas3d(enabled: Boolean = true) {
        canvas3dEnabled = enabled
        if (enabled) {
            gpu(true)
            canvas2d(true)
        }
    }

    /**
     * Includes `:extensions:media` (`VideoPlayer`, `AudioPlayer`, `AudioAnalyzer`, `WebVttSrtParser`,
     * and `:extensions:media:desktop` VLC HDR & HW/SW decode engine). Automatically enables `gpu`.
     */
    fun media(enabled: Boolean = true) {
        mediaEnabled = enabled
        if (enabled) {
            gpu(true)
        }
    }

    /**
     * Includes `:extensions:webview` (Project-of-Projects `WebView` extension: Chromium `ChromiumBlinkEngine`
     * + `NJS` Native JIT Engine on Desktop/Swing, and native `android.webkit.WebView` / `WKWebView` / `<iframe>`
     * on Android, iOS, and Web).
     */
    fun webView(enabled: Boolean = true) {
        webViewEnabled = enabled
    }

    /**
     * Includes `:extensions:codeeditor` (extension-only IDE-style code editor `IdeCodeEditor`).
     * Automatically enables `canvas2d`.
     */
    fun codeEditor(enabled: Boolean = true) {
        codeEditorEnabled = enabled
        if (enabled) {
            canvas2d(true)
        }
    }

    /**
     * Includes `:extensions:aichat` (reusable AI chat drawer, conversation list, and multi-agent orchestrator).
     * Automatically enables `codeEditor` (and transitively `canvas2d`).
     */
    fun aiChat(enabled: Boolean = true) {
        aiChatEnabled = enabled
        if (enabled) {
            codeEditor(true)
        }
    }

    /**
     * Includes `:extensions:threejs` (Three.js-style 3D scene graph built atop `GLCanvas` / `VKCanvas`).
     * Automatically enables `gpu` and `canvas3d`.
     */
    fun threeJs(enabled: Boolean = true) {
        threeJsEnabled = enabled
        if (enabled) {
            gpu(true)
            canvas3d(true)
        }
    }

    /**
     * Includes `:extensions:box2d` (pure Kotlin Multiplatform 2D rigid-body physics engine & `Canvas2D`/`Canvas3D` debug renderer).
     * Automatically enables `canvas2d` and `canvas3d` (and transitively `gpu`).
     */
    fun box2d(enabled: Boolean = true) {
        box2dEnabled = enabled
        if (enabled) {
            canvas2d(true)
            canvas3d(true)
        }
    }

    /**
     * Includes `:extensions:box3d` (pure Kotlin Multiplatform 3D rigid-body physics engine & `Box3DThreeJsBridge`).
     * Automatically enables `canvas3d` and `threeJs` (and transitively `gpu`).
     */
    fun box3d(enabled: Boolean = true) {
        box3dEnabled = enabled
        if (enabled) {
            canvas3d(true)
            threeJs(true)
        }
    }

    /**
     * Includes `:extensions:dsl` (`kotlinx.html`-style in-place component builders and
     * Model-View-Controller `MvcModel` / `MvcView` / `MvcController` classes).
     */
    fun dsl(enabled: Boolean = true) {
        dslEnabled = enabled
    }

    /** Enables all 12 first-party extension modules. */
    fun enableAll() {
        gpu(true)
        imageProcessing(true)
        canvas2d(true)
        canvas3d(true)
        media(true)
        webView(true)
        codeEditor(true)
        aiChat(true)
        threeJs(true)
        box2d(true)
        box3d(true)
        dsl(true)
    }

    /**
     * Evaluates transitive extension dependencies in case boolean properties were set directly,
     * and returns the ordered list of enabled top-level extension module paths (e.g. `":extensions:gpu"`).
     */
    fun enabledExtensionModulePaths(): List<String> {
        val resolvedBox3d = box3dEnabled
        val resolvedBox2d = box2dEnabled
        val resolvedThreeJs = threeJsEnabled || resolvedBox3d
        val resolvedAiChat = aiChatEnabled
        val resolvedCodeEditor = codeEditorEnabled || resolvedAiChat
        val resolvedWebView = webViewEnabled
        val resolvedMedia = mediaEnabled
        val resolvedCanvas3d = canvas3dEnabled || resolvedThreeJs || resolvedBox2d || resolvedBox3d
        val resolvedCanvas2d = canvas2dEnabled || resolvedCanvas3d || resolvedCodeEditor || resolvedBox2d
        val resolvedImageProcessing = imageProcessingEnabled
        val resolvedGpu = gpuEnabled || resolvedImageProcessing || resolvedCanvas2d || resolvedCanvas3d || resolvedMedia || resolvedThreeJs
        val resolvedDsl = dslEnabled

        return buildList {
            if (resolvedGpu) add(":extensions:gpu")
            if (resolvedImageProcessing) add(":extensions:imageprocessing")
            if (resolvedCanvas2d) add(":extensions:canvas2d")
            if (resolvedCanvas3d) add(":extensions:canvas3d")
            if (resolvedMedia) add(":extensions:media")
            if (resolvedWebView) add(":extensions:webview")
            if (resolvedCodeEditor) add(":extensions:codeeditor")
            if (resolvedAiChat) add(":extensions:aichat")
            if (resolvedThreeJs) add(":extensions:threejs")
            if (resolvedBox2d) add(":extensions:box2d")
            if (resolvedBox3d) add(":extensions:box3d")
            if (resolvedDsl) {
                add(":extensions:dsl")
                if (resolvedCanvas2d) add(":extensions:canvas2d:dsl")
                if (resolvedCanvas3d) add(":extensions:canvas3d:dsl")
                if (resolvedMedia) add(":extensions:media:dsl")
                if (resolvedWebView) add(":extensions:webview:dsl")
                if (resolvedCodeEditor) add(":extensions:codeeditor:dsl")
                if (resolvedAiChat) add(":extensions:aichat:dsl")
                if (resolvedThreeJs) add(":extensions:threejs:dsl")
                if (resolvedBox2d) add(":extensions:box2d:dsl")
                if (resolvedBox3d) add(":extensions:box3d:dsl")
            }
        }
    }
}
