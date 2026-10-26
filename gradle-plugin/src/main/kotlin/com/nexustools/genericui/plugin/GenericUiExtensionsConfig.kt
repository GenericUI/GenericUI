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
 *    - `:extensions:media` (`VideoPlayer`, `AudioPlayer`, `AudioAnalyzer`, `WebVttSrtParser` — uses native `libvlc` on
 *      native desktop backends, `libvlcj` on `:swing`, and native platform media components on `:mobile` and `:web`;
 *      does NOT depend on `:extensions:gpu`, `:extensions:canvas2d`, or any other extension)
 *    - `:extensions:mediacontrols` (`MediaSessionBridge`, `MediaPlaybackState`, `MediaTrackMetadata`, `MediaRemoteCommand` —
 *      native OS media session integration via Win32 SMTC, Linux MPRIS2, macOS/iOS `MPNowPlayingInfoCenter`,
 *      Android `MediaSession`, and Web `navigator.mediaSession`; standalone and decoupled from `:extensions:media`)
 *    - `:extensions:webview` (`WebView`, `WebViewController`, `WebViewJsBridge` — uses Chromium `ChromiumBlinkEngine` +
 *      `NJS` (`lib/NJS` AsmJit/SLJIT Native JIT Engine) on Desktop (`:desktop` & `:swing`), and native platform `WebView`
 *      on Mobile (`:android` `android.webkit.WebView`, `:ios` `WKWebView`) and `:web` (`<iframe>`), NEVER using NJS on Mobile/Web)
 * 2. **Single Multiplatform Project Extensions** (`:extensions:<name>`) built purely atop `:common` and other extensions:
 *    - `:extensions:overlay` (`AbstractOverlay`, `OverlayHost`, `OverlayHostBox`, `OverlayElementRegistry`,
 *      `AutoHideController`, `OverlayInputRouter`, plus default and custom overlay elements)
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
    var overlayEnabled: Boolean = false
    var canvas2dEnabled: Boolean = false
    var canvas3dEnabled: Boolean = false
    var mediaEnabled: Boolean = false
    var mediaControlsEnabled: Boolean = false
    var webViewEnabled: Boolean = false
    var codeEditorEnabled: Boolean = false
    var aiChatEnabled: Boolean = false
    var threeJsEnabled: Boolean = false
    var box2dEnabled: Boolean = false
    var box3dEnabled: Boolean = false
    var alienEnabled: Boolean = false
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
     * Includes `:extensions:overlay` (universal overlay chrome architecture: `AbstractOverlay`,
     * `OverlayHost`, `OverlayHostBox`, `OverlayElementRegistry`, `AutoHideController`, and
     * `OverlayInputRouter`).
     */
    fun overlay(enabled: Boolean = true) {
        overlayEnabled = enabled
    }

    /**
     * Includes `:extensions:canvas2d` (`Canvas2D`, `Canvas2DContext`, `CurveEditor` — accelerated
     * via `:extensions:imageprocessing`'s `ImageBuffer` and 2D context backend).
     * Automatically enables `imageProcessing` and `overlay`.
     */
    fun canvas2d(enabled: Boolean = true) {
        canvas2dEnabled = enabled
        if (enabled) {
            imageProcessing(true)
            overlay(true)
        }
    }

    /**
     * Includes `:extensions:canvas3d` (`GLCanvas`, `VKCanvas`). Automatically enables `gpu`, `canvas2d`, and `overlay`.
     */
    fun canvas3d(enabled: Boolean = true) {
        canvas3dEnabled = enabled
        if (enabled) {
            gpu(true)
            canvas2d(true)
            overlay(true)
        }
    }

    /**
     * Includes `:extensions:media` (`VideoPlayer`, `AudioPlayer`, `AudioAnalyzer`, `WebVttSrtParser`,
     * using `libvlc` on native desktop backends, `libvlcj` on `:swing`, and native components on
     * `:mobile` and `:web`). Automatically enables `overlay`. Does not depend on `:extensions:gpu`,
     * `:extensions:canvas2d`, or `:extensions:mediacontrols`.
     */
    fun media(enabled: Boolean = true) {
        mediaEnabled = enabled
        if (enabled) {
            overlay(true)
        }
    }

    /**
     * Includes `:extensions:mediacontrols` (standalone OS media session & remote command bridge
     * `MediaSessionBridge`, `MediaPlaybackState`, `MediaTrackMetadata`, `MediaRemoteCommand`).
     * Decoupled from `:extensions:media`.
     */
    fun mediaControls(enabled: Boolean = true) {
        mediaControlsEnabled = enabled
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
     * Includes `:extensions:alien` (self-rendered Bootstrap 5.3 `Canvas2D` UI engine with
     * `AlienFactory`, `AlienContainer`, `AlienFragment`, `UnalienContainer`, and `AlienRepaintManager`
     * with dirty update regions on System targets, and zero-overhead native pass-through on Web).
     * Automatically enables `canvas2d` (and transitively `imageProcessing` and `gpu`).
     */
    fun alien(enabled: Boolean = true) {
        alienEnabled = enabled
        if (enabled) {
            canvas2d(true)
        }
    }

    /**
     * Includes `:extensions:dsl` (`kotlinx.html`-style in-place component builders and
     * Model-View-Controller `MvcModel` / `MvcView` / `MvcController` classes).
     */
    fun dsl(enabled: Boolean = true) {
        dslEnabled = enabled
    }

    /** Enables all first-party extension modules. */
    fun enableAll() {
        gpu(true)
        imageProcessing(true)
        overlay(true)
        canvas2d(true)
        canvas3d(true)
        media(true)
        mediaControls(true)
        webView(true)
        codeEditor(true)
        aiChat(true)
        threeJs(true)
        box2d(true)
        box3d(true)
        alien(true)
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
        val resolvedAlien = alienEnabled
        val resolvedWebView = webViewEnabled
        val resolvedMedia = mediaEnabled
        val resolvedMediaControls = mediaControlsEnabled
        val resolvedCanvas3d = canvas3dEnabled || resolvedThreeJs || resolvedBox2d || resolvedBox3d
        val resolvedCanvas2d = canvas2dEnabled || resolvedCanvas3d || resolvedCodeEditor || resolvedBox2d || resolvedAlien
        val resolvedOverlay = overlayEnabled || resolvedMedia || resolvedCanvas2d || resolvedCanvas3d
        val resolvedImageProcessing = imageProcessingEnabled || resolvedCanvas2d
        val resolvedGpu = gpuEnabled || resolvedImageProcessing || resolvedCanvas3d || resolvedThreeJs
        val resolvedDsl = dslEnabled

        return buildList {
            if (resolvedGpu) add(":extensions:gpu")
            if (resolvedImageProcessing) add(":extensions:imageprocessing")
            if (resolvedOverlay) add(":extensions:overlay")
            if (resolvedCanvas2d) add(":extensions:canvas2d")
            if (resolvedCanvas3d) add(":extensions:canvas3d")
            if (resolvedMedia) add(":extensions:media")
            if (resolvedMediaControls) add(":extensions:mediacontrols")
            if (resolvedWebView) add(":extensions:webview")
            if (resolvedCodeEditor) add(":extensions:codeeditor")
            if (resolvedAiChat) add(":extensions:aichat")
            if (resolvedThreeJs) add(":extensions:threejs")
            if (resolvedBox2d) add(":extensions:box2d")
            if (resolvedBox3d) add(":extensions:box3d")
            if (resolvedAlien) add(":extensions:alien")
            if (resolvedDsl) {
                add(":extensions:dsl")
                if (resolvedOverlay) add(":extensions:overlay:dsl")
                if (resolvedCanvas2d) add(":extensions:canvas2d:dsl")
                if (resolvedCanvas3d) add(":extensions:canvas3d:dsl")
                if (resolvedMedia) add(":extensions:media:dsl")
                if (resolvedMediaControls) add(":extensions:mediacontrols:dsl")
                if (resolvedWebView) add(":extensions:webview:dsl")
                if (resolvedCodeEditor) add(":extensions:codeeditor:dsl")
                if (resolvedAiChat) add(":extensions:aichat:dsl")
                if (resolvedThreeJs) add(":extensions:threejs:dsl")
                if (resolvedBox2d) add(":extensions:box2d:dsl")
                if (resolvedBox3d) add(":extensions:box3d:dsl")
                if (resolvedAlien) add(":extensions:alien:dsl")
            }
        }
    }

    /**
     * Returns the ordered list of fully-qualified Kotlin provider registration / installation
     * expressions for all enabled extensions on [backendScope] (applying optional per-variant
     * [extensionOverrides]).
     */
    @JvmOverloads
    fun providerInstallerCallsForBackend(
        backendScope: GenericUiVariantScope,
        extensionOverrides: Map<String, Boolean> = emptyMap()
    ): List<String> {
        fun overrideOrDefault(defaultVal: Boolean, vararg keys: String): Boolean {
            for (k in keys) {
                extensionOverrides[k]?.let { return it }
            }
            return defaultVal
        }

        val rawBox3d = overrideOrDefault(box3dEnabled, "box3d")
        val rawBox2d = overrideOrDefault(box2dEnabled, "box2d")
        val rawThreeJs = overrideOrDefault(threeJsEnabled, "threeJs", "threejs")
        val rawAiChat = overrideOrDefault(aiChatEnabled, "aiChat", "aichat")
        val rawCodeEditor = overrideOrDefault(codeEditorEnabled, "codeEditor", "codeeditor")
        val rawAlien = overrideOrDefault(alienEnabled, "alien")
        val rawWebView = overrideOrDefault(webViewEnabled, "webView", "webview")
        val rawMedia = overrideOrDefault(mediaEnabled, "media")
        val rawMediaControls = overrideOrDefault(mediaControlsEnabled, "mediaControls", "mediacontrols")
        val rawCanvas3d = overrideOrDefault(canvas3dEnabled, "canvas3d")
        val rawCanvas2d = overrideOrDefault(canvas2dEnabled, "canvas2d")
        val rawImageProcessing = overrideOrDefault(imageProcessingEnabled, "imageProcessing", "imageprocessing")
        val rawGpu = overrideOrDefault(gpuEnabled, "gpu")

        val resolvedThreeJs = rawThreeJs || rawBox3d
        val resolvedCodeEditor = rawCodeEditor || rawAiChat
        val resolvedAlien = rawAlien
        val resolvedWebView = rawWebView
        val resolvedMedia = rawMedia
        val resolvedMediaControls = rawMediaControls
        val resolvedCanvas3d = rawCanvas3d || resolvedThreeJs || rawBox2d || rawBox3d
        val resolvedCanvas2d = rawCanvas2d || resolvedCanvas3d || resolvedCodeEditor || rawBox2d || resolvedAlien
        val resolvedImageProcessing = rawImageProcessing || resolvedCanvas2d
        val resolvedGpu = rawGpu || resolvedImageProcessing || resolvedCanvas3d || resolvedThreeJs

        return buildList {
            if (resolvedGpu) {
                when (backendScope) {
                    GenericUiVariantScope.WINDOWS,
                    GenericUiVariantScope.MACOS,
                    GenericUiVariantScope.GTK,
                    GenericUiVariantScope.QT,
                    GenericUiVariantScope.LINUX_COMMON,
                    GenericUiVariantScope.NATIVE_DESKTOP_COMMON,
                    GenericUiVariantScope.DESKTOP_COMMON ->
                        add("com.nexustools.genericui.ext.gpu.desktop.DesktopGpuProvider.install()")
                    GenericUiVariantScope.SWING ->
                        add("com.nexustools.genericui.ext.gpu.swing.SwingGpuProvider.install()")
                    GenericUiVariantScope.ANDROID,
                    GenericUiVariantScope.IOS,
                    GenericUiVariantScope.MOBILE_COMMON ->
                        add("com.nexustools.genericui.ext.gpu.mobile.MobileGpuProvider.install()")
                    GenericUiVariantScope.WEB ->
                        add("com.nexustools.genericui.ext.gpu.web.WebGpuProvider.install()")
                    else -> Unit
                }
            }

            if (resolvedImageProcessing) {
                when (backendScope) {
                    GenericUiVariantScope.WEB ->
                        add("com.nexustools.genericui.ext.imageprocessing.context2d.WebImageBuffer.installFactory()")
                    GenericUiVariantScope.WINDOWS,
                    GenericUiVariantScope.MACOS,
                    GenericUiVariantScope.GTK,
                    GenericUiVariantScope.QT,
                    GenericUiVariantScope.SWING,
                    GenericUiVariantScope.ANDROID,
                    GenericUiVariantScope.IOS,
                    GenericUiVariantScope.LINUX_COMMON,
                    GenericUiVariantScope.NATIVE_DESKTOP_COMMON,
                    GenericUiVariantScope.DESKTOP_COMMON,
                    GenericUiVariantScope.MOBILE_COMMON,
                    GenericUiVariantScope.SYSTEM_COMMON ->
                        add("com.nexustools.genericui.ext.imageprocessing.context2d.SystemGpuImageBuffer.installFactory()")
                    else -> Unit
                }
            }

            if (resolvedCanvas2d) {
                when (backendScope) {
                    GenericUiVariantScope.WINDOWS,
                    GenericUiVariantScope.MACOS,
                    GenericUiVariantScope.GTK,
                    GenericUiVariantScope.QT,
                    GenericUiVariantScope.LINUX_COMMON,
                    GenericUiVariantScope.NATIVE_DESKTOP_COMMON,
                    GenericUiVariantScope.DESKTOP_COMMON ->
                        add("com.nexustools.genericui.ext.canvas2d.desktop.DesktopCanvas2DProvider.install()")
                    GenericUiVariantScope.SWING ->
                        add("com.nexustools.genericui.ext.canvas2d.swing.SwingCanvas2DProvider.install()")
                    GenericUiVariantScope.ANDROID,
                    GenericUiVariantScope.IOS,
                    GenericUiVariantScope.MOBILE_COMMON ->
                        add("com.nexustools.genericui.ext.canvas2d.mobile.MobileCanvas2DProvider.install()")
                    GenericUiVariantScope.WEB ->
                        add("com.nexustools.genericui.ext.canvas2d.web.WebCanvas2DProvider.install()")
                    else -> Unit
                }
            }

            if (resolvedCanvas3d) {
                when (backendScope) {
                    GenericUiVariantScope.WINDOWS,
                    GenericUiVariantScope.MACOS,
                    GenericUiVariantScope.GTK,
                    GenericUiVariantScope.QT,
                    GenericUiVariantScope.LINUX_COMMON,
                    GenericUiVariantScope.NATIVE_DESKTOP_COMMON,
                    GenericUiVariantScope.DESKTOP_COMMON ->
                        add("com.nexustools.genericui.ext.canvas3d.desktop.DesktopCanvas3DProvider.install()")
                    GenericUiVariantScope.SWING ->
                        add("com.nexustools.genericui.ext.canvas3d.swing.SwingCanvas3DProvider.install()")
                    GenericUiVariantScope.ANDROID,
                    GenericUiVariantScope.IOS,
                    GenericUiVariantScope.MOBILE_COMMON ->
                        add("com.nexustools.genericui.ext.canvas3d.mobile.MobileCanvas3DProvider.install()")
                    GenericUiVariantScope.WEB ->
                        add("com.nexustools.genericui.ext.canvas3d.web.WebCanvas3DProvider.install()")
                    else -> Unit
                }
            }

            if (resolvedMedia) {
                when (backendScope) {
                    GenericUiVariantScope.WINDOWS,
                    GenericUiVariantScope.MACOS,
                    GenericUiVariantScope.GTK,
                    GenericUiVariantScope.QT,
                    GenericUiVariantScope.LINUX_COMMON,
                    GenericUiVariantScope.NATIVE_DESKTOP_COMMON,
                    GenericUiVariantScope.DESKTOP_COMMON ->
                        add("com.nexustools.genericui.ext.media.desktop.DesktopVlcMediaExtensionProvider.install()")
                    GenericUiVariantScope.SWING ->
                        add("com.nexustools.genericui.ext.media.swing.SwingMediaExtensionProvider.install()")
                    GenericUiVariantScope.ANDROID ->
                        add("com.nexustools.genericui.android.AndroidMediaProvider.install()")
                    GenericUiVariantScope.IOS ->
                        add("com.nexustools.genericui.ios.IosMediaProvider.install()")
                    GenericUiVariantScope.MOBILE_COMMON ->
                        add("com.nexustools.genericui.ext.media.mobile.MobileMediaExtensionProvider.install()")
                    GenericUiVariantScope.WEB ->
                        add("com.nexustools.genericui.ext.media.web.WebMediaExtensionProvider.install()")
                    else -> Unit
                }
            }

            if (resolvedMediaControls) {
                when (backendScope) {
                    GenericUiVariantScope.WINDOWS ->
                        add("com.nexustools.genericui.ext.mediacontrols.windows.WindowsMediaControlsProvider.register()")
                    GenericUiVariantScope.MACOS ->
                        add("com.nexustools.genericui.ext.mediacontrols.macos.MacosMediaControlsProvider.register()")
                    GenericUiVariantScope.GTK ->
                        add("com.nexustools.genericui.ext.mediacontrols.gtk.GtkMediaControlsProvider.register()")
                    GenericUiVariantScope.QT ->
                        add("com.nexustools.genericui.ext.mediacontrols.qt.QtMediaControlsProvider.register()")
                    GenericUiVariantScope.SWING ->
                        add("com.nexustools.genericui.ext.mediacontrols.swing.SwingMediaControlsProvider.register()")
                    GenericUiVariantScope.ANDROID ->
                        add("com.nexustools.genericui.ext.mediacontrols.android.AndroidMediaControlsProvider.register()")
                    GenericUiVariantScope.IOS ->
                        add("com.nexustools.genericui.ext.mediacontrols.ios.IosMediaControlsProvider.register()")
                    GenericUiVariantScope.WEB ->
                        add("com.nexustools.genericui.ext.mediacontrols.web.WebMediaControlsProvider.register()")
                    else -> Unit
                }
            }

            if (resolvedWebView) {
                when (backendScope) {
                    GenericUiVariantScope.WINDOWS,
                    GenericUiVariantScope.MACOS,
                    GenericUiVariantScope.GTK,
                    GenericUiVariantScope.QT,
                    GenericUiVariantScope.LINUX_COMMON,
                    GenericUiVariantScope.NATIVE_DESKTOP_COMMON,
                    GenericUiVariantScope.DESKTOP_COMMON ->
                        add("com.nexustools.genericui.ext.webview.desktop.DesktopWebViewExtensionProvider.install()")
                    GenericUiVariantScope.SWING ->
                        add("com.nexustools.genericui.ext.webview.swing.SwingWebViewExtensionProvider.install()")
                    GenericUiVariantScope.ANDROID,
                    GenericUiVariantScope.MOBILE_COMMON ->
                        add("com.nexustools.genericui.ext.webview.mobile.MobileWebViewExtensionProvider.install(false)")
                    GenericUiVariantScope.IOS ->
                        add("com.nexustools.genericui.ext.webview.mobile.MobileWebViewExtensionProvider.install(true)")
                    GenericUiVariantScope.WEB ->
                        add("com.nexustools.genericui.ext.webview.web.WebWebViewExtensionProvider.install()")
                    else -> Unit
                }
            }

            if (resolvedAlien) {
                when (backendScope) {
                    GenericUiVariantScope.WEB ->
                        add("com.nexustools.genericui.ext.alien.web.WebAlienBootstrap.install()")
                    GenericUiVariantScope.WINDOWS,
                    GenericUiVariantScope.MACOS,
                    GenericUiVariantScope.GTK,
                    GenericUiVariantScope.QT,
                    GenericUiVariantScope.SWING,
                    GenericUiVariantScope.ANDROID,
                    GenericUiVariantScope.IOS,
                    GenericUiVariantScope.LINUX_COMMON,
                    GenericUiVariantScope.NATIVE_DESKTOP_COMMON,
                    GenericUiVariantScope.DESKTOP_COMMON,
                    GenericUiVariantScope.MOBILE_COMMON,
                    GenericUiVariantScope.SYSTEM_COMMON ->
                        add("com.nexustools.genericui.ext.alien.system.SystemAlienBootstrap.install()")
                    else -> Unit
                }
            }
        }
    }
}
