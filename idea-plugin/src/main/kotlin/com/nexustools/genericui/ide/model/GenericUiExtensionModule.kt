package com.nexustools.genericui.ide.model

/**
 * Enum of all 11 first-party `GenericUI` extension modules selectable via checkboxes
 * in the IntelliJ IDEA New Project Wizard and Manage Targets Dialog.
 *
 * Includes automatic transitive dependency resolution via [requiredExtensions].
 */
enum class GenericUiExtensionModule(
    val id: String,
    val dslMethodName: String,
    val displayName: String,
    val description: String,
    val gradleModulePath: String,
    val isProjectOfProjects: Boolean
) {
    GPU(
        id = "gpu",
        dslMethodName = "gpu",
        displayName = "GPU Acceleration (:extensions:gpu)",
        description = "Unified Vulkan primary + OpenGL ES fallback GPU contexts, GpuDevice, VKContext, GLContext",
        gradleModulePath = ":extensions:gpu",
        isProjectOfProjects = true
    ),
    IMAGE_PROCESSING(
        id = "imageprocessing",
        dslMethodName = "imageProcessing",
        displayName = "GPU Image Processing (:extensions:imageprocessing)",
        description = "Offscreen in-memory GPU-accelerated image processing, filter chains, and compute kernels",
        gradleModulePath = ":extensions:imageprocessing",
        isProjectOfProjects = false
    ),
    OVERLAY(
        id = "overlay",
        dslMethodName = "overlay",
        displayName = "Overlay System (:extensions:overlay)",
        description = "Hardware-accelerated floating overlay layer, HUDs, tooltips, and modal compositing",
        gradleModulePath = ":extensions:overlay",
        isProjectOfProjects = false
    ),
    CANVAS_2D(
        id = "canvas2d",
        dslMethodName = "canvas2d",
        displayName = "Canvas 2D & Curve Editor (:extensions:canvas2d)",
        description = "Immediate & retained-mode 2D vector/raster Canvas2D and interactive Bezier CurveEditor",
        gradleModulePath = ":extensions:canvas2d",
        isProjectOfProjects = true
    ),
    CANVAS_3D(
        id = "canvas3d",
        dslMethodName = "canvas3d",
        displayName = "Canvas 3D GL & Vulkan (:extensions:canvas3d)",
        description = "Hardware-accelerated GLCanvas and VKCanvas viewport widgets",
        gradleModulePath = ":extensions:canvas3d",
        isProjectOfProjects = true
    ),
    MEDIA(
        id = "media",
        dslMethodName = "media",
        displayName = "Media Player & Audio Analyzer (:extensions:media)",
        description = "VideoPlayer, AudioPlayer, FFT AudioAnalyzer, WebVTT/SRT subtitles, and Desktop VLC HDR engine",
        gradleModulePath = ":extensions:media",
        isProjectOfProjects = true
    ),
    MEDIA_CONTROLS(
        id = "mediacontrols",
        dslMethodName = "mediaControls",
        displayName = "System Media Controls (:extensions:mediacontrols)",
        description = "OS-level media transport controls, Now Playing metadata, and hardware media key integration",
        gradleModulePath = ":extensions:mediacontrols",
        isProjectOfProjects = true
    ),
    WEB_VIEW(
        id = "webview",
        dslMethodName = "webView",
        displayName = "Hybrid WebView (:extensions:webview)",
        description = "Chromium Blink + NJS Native JIT on Desktop/Swing and native WebView/WKWebView/iframe on Mobile/Web",
        gradleModulePath = ":extensions:webview",
        isProjectOfProjects = true
    ),
    CODE_EDITOR(
        id = "codeeditor",
        dslMethodName = "codeEditor",
        displayName = "IDE Code Editor (:extensions:codeeditor)",
        description = "Multi-language syntax-highlighted IDE code editor with gutter, folding, minimap, and diff view",
        gradleModulePath = ":extensions:codeeditor",
        isProjectOfProjects = false
    ),
    AI_CHAT(
        id = "aichat",
        dslMethodName = "aiChat",
        displayName = "AI Chat & Agent Orchestrator (:extensions:aichat)",
        description = "Reusable AI chat drawer, streaming markdown/code blocks, and multi-agent orchestrator",
        gradleModulePath = ":extensions:aichat",
        isProjectOfProjects = false
    ),
    THREE_JS(
        id = "threejs",
        dslMethodName = "threeJs",
        displayName = "Three.js 3D Scene Graph (:extensions:threejs)",
        description = "Three.js-compatible 3D scene graph, PBR materials, lights, cameras, and glTF loader",
        gradleModulePath = ":extensions:threejs",
        isProjectOfProjects = false
    ),
    BOX_2D(
        id = "box2d",
        dslMethodName = "box2d",
        displayName = "Box2D Physics Engine (:extensions:box2d)",
        description = "Pure Kotlin Multiplatform 2D rigid-body physics engine and Canvas2D/Canvas3D debug renderer",
        gradleModulePath = ":extensions:box2d",
        isProjectOfProjects = false
    ),
    BOX_3D(
        id = "box3d",
        dslMethodName = "box3d",
        displayName = "Box3D Physics Engine (:extensions:box3d)",
        description = "Pure Kotlin Multiplatform 3D rigid-body physics engine with Three.js scene synchronizer",
        gradleModulePath = ":extensions:box3d",
        isProjectOfProjects = false
    ),
    DSL(
        id = "dsl",
        dslMethodName = "dsl",
        displayName = "kotlinx.html-Style MVC & Component DSL (:extensions:dsl)",
        description = "In-place kotlinx.html-style component builders and Model-View-Controller (MVC) base classes",
        gradleModulePath = ":extensions:dsl",
        isProjectOfProjects = false
    );

    /**
     * Returns all transitive required extension modules that are automatically enabled
     * when this extension module is selected.
     */
    fun requiredExtensions(): Set<GenericUiExtensionModule> = when (this) {
        GPU -> emptySet()
        IMAGE_PROCESSING -> linkedSetOf(GPU)
        OVERLAY -> emptySet()
        CANVAS_2D -> linkedSetOf(OVERLAY)
        CANVAS_3D -> linkedSetOf(GPU, OVERLAY, CANVAS_2D)
        MEDIA -> linkedSetOf(GPU, OVERLAY)
        MEDIA_CONTROLS -> emptySet()
        WEB_VIEW -> emptySet()
        CODE_EDITOR -> linkedSetOf(OVERLAY, CANVAS_2D)
        AI_CHAT -> linkedSetOf(CODE_EDITOR, OVERLAY, CANVAS_2D)
        THREE_JS -> linkedSetOf(CANVAS_3D, OVERLAY, CANVAS_2D, GPU)
        BOX_2D -> linkedSetOf(OVERLAY, CANVAS_2D, CANVAS_3D, GPU)
        BOX_3D -> linkedSetOf(THREE_JS, CANVAS_3D, OVERLAY, CANVAS_2D, GPU)
        DSL -> emptySet()
    }

    companion object {
        /**
         * Expands [selected] to include all transitively required extensions in canonical declaration order.
         */
        fun resolveTransitive(selected: Set<GenericUiExtensionModule>): Set<GenericUiExtensionModule> {
            val expanded = linkedSetOf<GenericUiExtensionModule>()
            for (ext in selected) {
                expanded.add(ext)
                expanded.addAll(ext.requiredExtensions())
            }
            return entries.filter { it in expanded }.toCollection(LinkedHashSet())
        }

        /** Looks up an extension module by its [dslMethodName] or [id]. */
        fun fromDslMethod(name: String): GenericUiExtensionModule? {
            val trimmed = name.trim()
            return entries.firstOrNull {
                it.dslMethodName.equals(trimmed, ignoreCase = true) ||
                    it.id.equals(trimmed, ignoreCase = true)
            }
        }
    }
}
