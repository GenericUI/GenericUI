package com.nexustools.genericui.plugin

/**
 * DSL configuration block for first-party GenericUI extension modules across the categorized
 * multiplatform project hierarchy:
 * - **Graphics**: `:extensions:graphics:gpu`, `:extensions:graphics:imageprocessing`,
 *   `:extensions:graphics:canvas2d`, `:extensions:graphics:canvas3d`, `:extensions:graphics:threejs`
 * - **Physics**: `:extensions:physics:box2d`, `:extensions:physics:box3d`
 * - **Game & Input**: `:extensions:game:gamepad`, `:extensions:game:engine`,
 *   `:extensions:game:engine2d`, `:extensions:game:engine3d`
 * - **Media**: `:extensions:media:media`, `:extensions:media:mediacontrols`
 * - **UI**: `:extensions:ui:dsl`, `:extensions:ui:overlay`, `:extensions:ui:alien`,
 *   `:extensions:ui:webview`, `:extensions:ui:codeeditor`, `:extensions:ui:aichat`, `:extensions:ui:pip`
 */
open class GenericUiExtensionsConfig {
    var gpuEnabled: Boolean = false
    var imageProcessingEnabled: Boolean = false
    var overlayEnabled: Boolean = false
    var canvas2dEnabled: Boolean = false
    var canvas3dEnabled: Boolean = false
    var mediaEnabled: Boolean = false
    var mediaControlsEnabled: Boolean = false
    var schemaEnabled: Boolean = false
    var serviceWorkerEnabled: Boolean = false

    val serviceWorkerConfig: GenericUiServiceWorkerExtensionSpec = GenericUiServiceWorkerExtensionSpec()
    var webViewEnabled: Boolean = false
    var codeEditorEnabled: Boolean = false
    var aiChatEnabled: Boolean = false
    var threeJsEnabled: Boolean = false
    var box2dEnabled: Boolean = false
    var box3dEnabled: Boolean = false
    var gamepadEnabled: Boolean = false
    var engineEnabled: Boolean = false
    var engine2dEnabled: Boolean = false
    var engine3dEnabled: Boolean = false
    var alienEnabled: Boolean = false
    var pipEnabled: Boolean = false
    var dslEnabled: Boolean = false
    val customModulePaths: LinkedHashSet<String> = linkedSetOf()
    val customDslModulePaths: LinkedHashSet<String> = linkedSetOf()
    val extensionOptions: LinkedHashMap<String, String> = linkedMapOf()

    /**
     * Registers a custom or third-party extension Gradle module path (for example
     * `":extensions:system:serviceworker"`), optionally enabling its companion `:dsl` module.
     */
    @JvmOverloads
    fun custom(modulePath: String, enableDsl: Boolean = false) {
        val trimmed = modulePath.trim()
        if (trimmed.isEmpty()) return
        customModulePaths.add(trimmed)
        if (enableDsl) {
            customDslModulePaths.add(if (trimmed.endsWith(":dsl")) trimmed else "$trimmed:dsl")
        }
    }

    /** Sets a build-time key/value option forwarded to active [com.nexustools.genericui.plugin.spi.GenericUiExtensionContributor]s. */
    fun option(key: String, value: String) {
        val trimmed = key.trim()
        if (trimmed.isNotEmpty()) {
            extensionOptions[trimmed] = value
        }
    }

    /** Alias property for [codeEditorEnabled] (`:extensions:ui:codeeditor`). */
    var useCodeEditor: Boolean
        get() = codeEditorEnabled
        set(value) {
            codeEditor(value)
        }

    /**
     * Includes `:extensions:graphics:gpu` (unified Vulkan primary + OpenGL ES fallback GPU contexts,
     * `GpuDevice`, `GpuOffscreenContext`, `VKContext`, `GLContext`).
     */
    fun gpu(enabled: Boolean = true) {
        gpuEnabled = enabled
    }

    /**
     * Includes `:extensions:graphics:imageprocessing` (offscreen GPU-accelerated image processing
     * `GpuImageProcessor`, `GpuImageFilterChain`, `GpuImageKernel`). Automatically enables `gpu`.
     */
    fun imageProcessing(enabled: Boolean = true) {
        imageProcessingEnabled = enabled
        if (enabled) {
            gpu(true)
        }
    }

    /**
     * Includes `:extensions:ui:overlay` (`AbstractOverlay`, `OverlayHost`, `OverlayHostBox`,
     * `OverlayElementRegistry`, `AutoHideController`, and `OverlayInputRouter`).
     */
    fun overlay(enabled: Boolean = true) {
        overlayEnabled = enabled
    }

    /**
     * Includes `:extensions:graphics:canvas2d` (`Canvas2D`, `Canvas2DContext`, `CurveEditor`).
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
     * Includes `:extensions:graphics:canvas3d` (`Canvas3D`, `ScrollCanvas3D`, `Canvas3DOverlayHost`).
     * Automatically enables `gpu`, `canvas2d`, and `overlay`.
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
     * Includes `:extensions:media:media` (`VideoPlayer`, `AudioPlayer`, `AudioAnalyzer`, `WebVttSrtParser`).
     * Automatically enables `overlay` and `schema`.
     */
    fun media(enabled: Boolean = true) {
        mediaEnabled = enabled
        if (enabled) {
            overlay(true)
            schema(true)
        }
    }

    /**
     * Includes `:extensions:data:schema` (meta-schema engine, `SchemaForm`, `SchemaModal`,
     * and compile-time `@SchemaData` descriptor code generation).
     * Automatically adds `:extensions:data:schema:dsl` when `dsl(true)` is enabled.
     */
    fun schema(enabled: Boolean = true) {
        schemaEnabled = enabled
    }

    /**
     * Includes `:extensions:system:serviceworker` (`@MainServiceWorker`, `@ServiceWorkerMessage`,
     * foreground/tray background service lifecycle, and typed channel IPC).
     */
    fun serviceWorker(enabled: Boolean = true) {
        serviceWorkerEnabled = enabled
        syncServiceWorkerExtensionOptions()
    }

    /**
     * Includes and configures `:extensions:system:serviceworker` via
     * [GenericUiServiceWorkerExtensionSpec].
     */
    fun serviceWorker(configure: org.gradle.api.Action<GenericUiServiceWorkerExtensionSpec>) {
        serviceWorkerEnabled = true
        configure.execute(serviceWorkerConfig)
        syncServiceWorkerExtensionOptions()
    }

    fun serviceWorker(configure: GenericUiServiceWorkerExtensionSpec.() -> Unit) {
        serviceWorkerEnabled = true
        serviceWorkerConfig.configure()
        syncServiceWorkerExtensionOptions()
    }

    internal fun syncServiceWorkerExtensionOptions() {
        extensionOptions["serviceWorker.autostart"] = serviceWorkerConfig.autostart.toString()
        extensionOptions["serviceWorker.openWindowOnLaunch"] = serviceWorkerConfig.openWindowOnLaunch.toString()
        extensionOptions["serviceWorker.androidForegroundServiceType"] =
            serviceWorkerConfig.androidForegroundServiceType.trim().ifEmpty { "specialUse" }
        extensionOptions["serviceWorker.androidSpecialUseFgsSubtype"] =
            serviceWorkerConfig.androidSpecialUseFgsSubtype.trim()
        val trayIcon = serviceWorkerConfig.trayIcon?.trim().orEmpty()
        if (trayIcon.isNotEmpty()) {
            extensionOptions["serviceWorker.trayIcon"] = trayIcon
        } else {
            extensionOptions.remove("serviceWorker.trayIcon")
        }
    }

    /**
     * Includes `:extensions:media:mediacontrols` (standalone OS media session & remote command bridge
     * `MediaSessionBridge`, `MediaPlaybackState`, `MediaTrackMetadata`, `MediaRemoteCommand`).
     */
    fun mediaControls(enabled: Boolean = true) {
        mediaControlsEnabled = enabled
    }

    /**
     * Includes `:extensions:ui:webview` (Chromium/NJS WebView on desktop/Swing and native WebView on mobile/web).
     */
    fun webView(enabled: Boolean = true) {
        webViewEnabled = enabled
    }

    /**
     * Includes `:extensions:ui:codeeditor` (`IdeCodeEditor`). Automatically enables `canvas2d`.
     */
    fun codeEditor(enabled: Boolean = true) {
        codeEditorEnabled = enabled
        if (enabled) {
            canvas2d(true)
        }
    }

    /**
     * Includes `:extensions:ui:aichat` (`AIChatDrawer`, `AiChatContainer`, `AIChatAgentOrchestrator`).
     * Automatically enables `codeEditor` (and transitively `canvas2d`).
     */
    fun aiChat(enabled: Boolean = true) {
        aiChatEnabled = enabled
        if (enabled) {
            codeEditor(true)
        }
    }

    /**
     * Includes `:extensions:graphics:threejs` (Three.js-style 3D scene graph built atop `Canvas3D`).
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
     * Includes `:extensions:physics:box2d` (pure Kotlin Multiplatform 2D rigid-body physics engine).
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
     * Includes `:extensions:physics:box3d` (pure Kotlin Multiplatform 3D rigid-body physics engine).
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
     * Includes `:extensions:game:gamepad` (cross-platform game controller, analog axis, trigger,
     * rumble/haptic feedback API).
     */
    fun gamepad(enabled: Boolean = true) {
        gamepadEnabled = enabled
    }

    /**
     * Includes `:extensions:game:engine` (core game loop, ECS/entity lifecycle, fixed timestep,
     * input action mapping, and resource/audio hooks). Automatically enables `gamepad`.
     */
    fun engine(enabled: Boolean = true) {
        engineEnabled = enabled
        if (enabled) {
            gamepad(true)
        }
    }

    /**
     * Includes `:extensions:game:engine2d` (2D game engine host `Game2DCanvasHost`, sprites,
     * cameras, tilemaps, and physics integration). Automatically enables `engine`, `canvas2d`,
     * `imageProcessing`, and `box2d`.
     */
    fun engine2d(enabled: Boolean = true) {
        engine2dEnabled = enabled
        if (enabled) {
            engine(true)
            canvas2d(true)
            imageProcessing(true)
            box2d(true)
        }
    }

    /** Alias for [engine2d]. */
    fun engine2D(enabled: Boolean = true) {
        engine2d(enabled)
    }

    /**
     * Includes `:extensions:game:engine3d` (3D game engine host `Game3DCanvasHost`, scene/camera/lighting
     * integration, and 3D physics). Automatically enables `engine`, `canvas3d`, `threeJs`, and `box3d`.
     */
    fun engine3d(enabled: Boolean = true) {
        engine3dEnabled = enabled
        if (enabled) {
            engine(true)
            canvas3d(true)
            threeJs(true)
            box3d(true)
        }
    }

    /** Alias for [engine3d]. */
    fun engine3D(enabled: Boolean = true) {
        engine3d(enabled)
    }

    /**
     * Includes `:extensions:ui:alien` (self-rendered Bootstrap 5.3 `Canvas2D` UI engine).
     * Automatically enables `canvas2d` (and transitively `imageProcessing` and `gpu`).
     */
    fun alien(enabled: Boolean = true) {
        alienEnabled = enabled
        if (enabled) {
            canvas2d(true)
        }
    }

    /**
     * Includes `:extensions:ui:pip` (cross-platform Picture-in-Picture & overlay transport controls).
     * Automatically enables `overlay`.
     */
    fun pip(enabled: Boolean = true) {
        pipEnabled = enabled
        if (enabled) {
            overlay(true)
        }
    }

    /**
     * Includes `:extensions:ui:dsl` (`kotlinx.html`-style in-place component builders and MVC helpers)
     * plus `:dsl` companion modules for every enabled extension.
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
        gamepad(true)
        engine(true)
        engine2d(true)
        engine3d(true)
        alien(true)
        pip(true)
        schema(true)
        serviceWorker(true)
        dsl(true)
    }

    /**
     * Evaluates transitive extension dependencies in case boolean properties were set directly,
     * and returns the ordered list of categorized extension module paths
     * (e.g. `":extensions:graphics:gpu"`, `":extensions:game:engine2d"`).
     */
    fun enabledExtensionModulePaths(): List<String> {
        val resolvedEngine3d = engine3dEnabled
        val resolvedEngine2d = engine2dEnabled
        val resolvedBox3d = box3dEnabled || resolvedEngine3d
        val resolvedBox2d = box2dEnabled || resolvedEngine2d
        val resolvedThreeJs = threeJsEnabled || resolvedBox3d || resolvedEngine3d
        val resolvedEngine = engineEnabled || resolvedEngine2d || resolvedEngine3d
        val resolvedGamepad = gamepadEnabled || resolvedEngine
        val resolvedAiChat = aiChatEnabled
        val resolvedCodeEditor = codeEditorEnabled || resolvedAiChat
        val resolvedAlien = alienEnabled
        val resolvedPip = pipEnabled
        val resolvedWebView = webViewEnabled
        val resolvedMedia = mediaEnabled
        val resolvedMediaControls = mediaControlsEnabled
        val resolvedSchema = schemaEnabled || resolvedMedia
        val resolvedServiceWorker = serviceWorkerEnabled
        syncServiceWorkerExtensionOptions()
        val resolvedCanvas3d = canvas3dEnabled || resolvedThreeJs || resolvedBox2d || resolvedBox3d || resolvedEngine3d
        val resolvedCanvas2d = canvas2dEnabled || resolvedCanvas3d || resolvedCodeEditor || resolvedBox2d || resolvedAlien || resolvedEngine2d
        val resolvedOverlay = overlayEnabled || resolvedMedia || resolvedCanvas2d || resolvedCanvas3d || resolvedPip
        val resolvedImageProcessing = imageProcessingEnabled || resolvedCanvas2d || resolvedEngine2d
        val resolvedGpu = gpuEnabled || resolvedImageProcessing || resolvedCanvas3d || resolvedThreeJs
        val resolvedDsl = dslEnabled

        return buildList {
            if (resolvedGpu) add(":extensions:graphics:gpu")
            if (resolvedImageProcessing) add(":extensions:graphics:imageprocessing")
            if (resolvedOverlay) add(":extensions:ui:overlay")
            if (resolvedSchema) add(":extensions:data:schema")
            if (resolvedCanvas2d) add(":extensions:graphics:canvas2d")
            if (resolvedCanvas3d) add(":extensions:graphics:canvas3d")
            if (resolvedMedia) add(":extensions:media:media")
            if (resolvedMediaControls) add(":extensions:media:mediacontrols")
            if (resolvedWebView) add(":extensions:ui:webview")
            if (resolvedCodeEditor) add(":extensions:ui:codeeditor")
            if (resolvedAiChat) add(":extensions:ui:aichat")
            if (resolvedThreeJs) add(":extensions:graphics:threejs")
            if (resolvedBox2d) add(":extensions:physics:box2d")
            if (resolvedBox3d) add(":extensions:physics:box3d")
            if (resolvedGamepad) add(":extensions:game:gamepad")
            if (resolvedEngine) add(":extensions:game:engine")
            if (resolvedEngine2d) add(":extensions:game:engine2d")
            if (resolvedEngine3d) add(":extensions:game:engine3d")
            if (resolvedAlien) add(":extensions:ui:alien")
            if (resolvedPip) add(":extensions:ui:pip")
            if (resolvedServiceWorker) add(":extensions:system:serviceworker")
            for (customMod in customModulePaths) {
                add(customMod)
            }
            if (resolvedDsl) {
                add(":extensions:ui:dsl")
                if (resolvedOverlay) add(":extensions:ui:overlay:dsl")
                if (resolvedSchema) add(":extensions:data:schema:dsl")
                if (resolvedCanvas2d) add(":extensions:graphics:canvas2d:dsl")
                if (resolvedCanvas3d) add(":extensions:graphics:canvas3d:dsl")
                if (resolvedMedia) add(":extensions:media:media:dsl")
                if (resolvedMediaControls) add(":extensions:media:mediacontrols:dsl")
                if (resolvedWebView) add(":extensions:ui:webview:dsl")
                if (resolvedCodeEditor) add(":extensions:ui:codeeditor:dsl")
                if (resolvedAiChat) add(":extensions:ui:aichat:dsl")
                if (resolvedThreeJs) add(":extensions:graphics:threejs:dsl")
                if (resolvedBox2d) add(":extensions:physics:box2d:dsl")
                if (resolvedBox3d) add(":extensions:physics:box3d:dsl")
                if (resolvedGamepad) add(":extensions:game:gamepad:dsl")
                if (resolvedEngine) add(":extensions:game:engine:dsl")
                if (resolvedEngine2d) add(":extensions:game:engine2d:dsl")
                if (resolvedEngine3d) add(":extensions:game:engine3d:dsl")
                if (resolvedAlien) add(":extensions:ui:alien:dsl")
                if (resolvedPip) add(":extensions:ui:pip:dsl")
                if (resolvedServiceWorker) add(":extensions:system:serviceworker:dsl")
                for (customMod in customModulePaths) {
                    val dslMod = if (customMod.endsWith(":dsl")) customMod else "$customMod:dsl"
                    if (dslMod !in this) add(dslMod)
                }
            }
            for (customDsl in customDslModulePaths) {
                if (customDsl !in this) add(customDsl)
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
        extensionOverrides: Map<String, Boolean> = emptyMap(),
        generatedPackageName: String = ""
    ): List<String> {
        fun overrideOrDefault(defaultVal: Boolean, vararg keys: String): Boolean {
            for (k in keys) {
                extensionOverrides[k]?.let { return it }
            }
            return defaultVal
        }

        val rawEngine3d = overrideOrDefault(engine3dEnabled, "engine3d", "engine3D")
        val rawEngine2d = overrideOrDefault(engine2dEnabled, "engine2d", "engine2D")
        val rawBox3d = overrideOrDefault(box3dEnabled, "box3d")
        val rawBox2d = overrideOrDefault(box2dEnabled, "box2d")
        val rawThreeJs = overrideOrDefault(threeJsEnabled, "threeJs", "threejs")
        val rawAiChat = overrideOrDefault(aiChatEnabled, "aiChat", "aichat")
        val rawCodeEditor = overrideOrDefault(codeEditorEnabled, "codeEditor", "codeeditor")
        val rawAlien = overrideOrDefault(alienEnabled, "alien")
        val rawPip = overrideOrDefault(pipEnabled, "pip", "PiP")
        val rawWebView = overrideOrDefault(webViewEnabled, "webView", "webview")
        val rawMedia = overrideOrDefault(mediaEnabled, "media")
        val rawMediaControls = overrideOrDefault(mediaControlsEnabled, "mediaControls", "mediacontrols")
        val rawSchema = overrideOrDefault(schemaEnabled, "schema")
        val rawCanvas3d = overrideOrDefault(canvas3dEnabled, "canvas3d")
        val rawCanvas2d = overrideOrDefault(canvas2dEnabled, "canvas2d")
        val rawImageProcessing = overrideOrDefault(imageProcessingEnabled, "imageProcessing", "imageprocessing")
        val rawGpu = overrideOrDefault(gpuEnabled, "gpu")

        val resolvedBox3d = rawBox3d || rawEngine3d
        val resolvedBox2d = rawBox2d || rawEngine2d
        val resolvedThreeJs = rawThreeJs || resolvedBox3d || rawEngine3d
        val resolvedCodeEditor = rawCodeEditor || rawAiChat
        val resolvedAlien = rawAlien
        val resolvedPip = rawPip
        val resolvedWebView = rawWebView
        val resolvedMedia = rawMedia
        val resolvedMediaControls = rawMediaControls
        val resolvedSchema = rawSchema || resolvedMedia
        val resolvedCanvas3d = rawCanvas3d || resolvedThreeJs || resolvedBox2d || resolvedBox3d || rawEngine3d
        val resolvedCanvas2d = rawCanvas2d || resolvedCanvas3d || resolvedCodeEditor || resolvedBox2d || resolvedAlien || rawEngine2d
        val resolvedImageProcessing = rawImageProcessing || resolvedCanvas2d || rawEngine2d
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
                    GenericUiVariantScope.IPADOS,
                    GenericUiVariantScope.IOS_COMMON,
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
                    GenericUiVariantScope.IPADOS,
                    GenericUiVariantScope.IOS_COMMON,
                    GenericUiVariantScope.LINUX_COMMON,
                    GenericUiVariantScope.NATIVE_DESKTOP_COMMON,
                    GenericUiVariantScope.DESKTOP_COMMON,
                    GenericUiVariantScope.MOBILE_COMMON,
                    GenericUiVariantScope.APPLE_COMMON,
                    GenericUiVariantScope.JVM_COMMON,
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
                    GenericUiVariantScope.IPADOS,
                    GenericUiVariantScope.IOS_COMMON,
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
                    GenericUiVariantScope.IPADOS,
                    GenericUiVariantScope.IOS_COMMON,
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
                    GenericUiVariantScope.IOS,
                    GenericUiVariantScope.IPADOS,
                    GenericUiVariantScope.IOS_COMMON ->
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
                    GenericUiVariantScope.IOS,
                    GenericUiVariantScope.IPADOS,
                    GenericUiVariantScope.IOS_COMMON ->
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
                    GenericUiVariantScope.ANDROID ->
                        add("com.nexustools.genericui.android.AndroidWebViewExtensionProvider.install()")
                    GenericUiVariantScope.IOS,
                    GenericUiVariantScope.IPADOS,
                    GenericUiVariantScope.IOS_COMMON ->
                        add("com.nexustools.genericui.ios.IosWebViewExtensionProvider.install()")
                    GenericUiVariantScope.MOBILE_COMMON ->
                        add("com.nexustools.genericui.ext.webview.mobile.MobileWebViewExtensionProvider.install(false)")
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
                    GenericUiVariantScope.IPADOS,
                    GenericUiVariantScope.IOS_COMMON,
                    GenericUiVariantScope.LINUX_COMMON,
                    GenericUiVariantScope.NATIVE_DESKTOP_COMMON,
                    GenericUiVariantScope.DESKTOP_COMMON,
                    GenericUiVariantScope.MOBILE_COMMON,
                    GenericUiVariantScope.APPLE_COMMON,
                    GenericUiVariantScope.JVM_COMMON,
                    GenericUiVariantScope.SYSTEM_COMMON ->
                        add("com.nexustools.genericui.ext.alien.system.SystemAlienBootstrap.install()")
                    else -> Unit
                }
            }

            if (resolvedPip) {
                when (backendScope) {
                    GenericUiVariantScope.WINDOWS ->
                        add("com.nexustools.genericui.ext.pip.windows.WindowsPipProvider.register()")
                    GenericUiVariantScope.MACOS ->
                        add("com.nexustools.genericui.ext.pip.macos.MacosPipProvider.register()")
                    GenericUiVariantScope.GTK ->
                        add("com.nexustools.genericui.ext.pip.gtk.GtkPipProvider.register()")
                    GenericUiVariantScope.QT ->
                        add("com.nexustools.genericui.ext.pip.qt.QtPipProvider.register()")
                    GenericUiVariantScope.LINUX_COMMON,
                    GenericUiVariantScope.NATIVE_DESKTOP_COMMON,
                    GenericUiVariantScope.DESKTOP_COMMON ->
                        add("com.nexustools.genericui.ext.pip.desktop.DesktopPipProvider.register()")
                    GenericUiVariantScope.SWING ->
                        add("com.nexustools.genericui.ext.pip.swing.SwingPipProvider.register()")
                    GenericUiVariantScope.ANDROID ->
                        add("com.nexustools.genericui.ext.pip.android.AndroidPipProvider.register()")
                    GenericUiVariantScope.IOS,
                    GenericUiVariantScope.IPADOS,
                    GenericUiVariantScope.IOS_COMMON ->
                        add("com.nexustools.genericui.ext.pip.ios.IosPipProvider.register()")
                    GenericUiVariantScope.WEB ->
                        add("com.nexustools.genericui.ext.pip.web.WebPipProvider.register()")
                    else -> Unit
                }
            }

            val spiEnabledPaths = enabledExtensionModulePaths().toMutableSet().apply {
                if (resolvedSchema) {
                    add(":extensions:data:schema")
                } else {
                    remove(":extensions:data:schema")
                }
            }
            val spiPaths = customModulePaths + spiEnabledPaths
            if (spiPaths.isNotEmpty()) {
                val stubCtx = com.nexustools.genericui.plugin.spi.ExtensionBuildContext(
                    projectRootDir = java.io.File("."),
                    projectName = "",
                    appId = "",
                    appName = "",
                    appVersion = "",
                    baseName = "",
                    generatedPackageName = generatedPackageName,
                    enabledExtensionModulePaths = spiEnabledPaths,
                    customExtensionModulePaths = customModulePaths.toSet(),
                    extensionOptions = extensionOptions.toMap()
                )
                for (call in com.nexustools.genericui.plugin.spi.GenericUiExtensionContributorRegistry.collectProviderInstallerCalls(backendScope, stubCtx)) {
                    if (call !in this) {
                        add(call)
                    }
                }
            }
        }
    }
}
