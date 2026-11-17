package com.nexustools.genericui.ide.model

/**
 * Enum of all 19 first-party `GenericUI` extension modules organized into consolidated categories
 * (`Graphics`, `Physics`, `Game & Input`, `Media`, `UI`), selectable via checkboxes in the
 * IntelliJ IDEA New Project Wizard and Manage Targets Dialog.
 *
 * Includes companion `:dsl` module metadata ([dslModulePath]) and automatic transitive
 * dependency resolution via [requiredExtensions] and [resolveTransitive].
 */
enum class GenericUiExtensionModule(
    val id: String,
    val dslMethodName: String,
    val category: String,
    val displayName: String,
    val description: String,
    val gradleModulePath: String,
    val dslModulePath: String?
) {
    // --- Graphics ---
    GPU(
        id = "gpu",
        dslMethodName = "gpu",
        category = "Graphics",
        displayName = "GPU Acceleration (:extensions:graphics:gpu)",
        description = "Unified Vulkan primary + OpenGL ES fallback GPU contexts, GpuDevice, VKContext, GLContext",
        gradleModulePath = ":extensions:graphics:gpu",
        dslModulePath = null
    ),
    IMAGE_PROCESSING(
        id = "imageprocessing",
        dslMethodName = "imageProcessing",
        category = "Graphics",
        displayName = "GPU Image Processing (:extensions:graphics:imageprocessing)",
        description = "Offscreen in-memory GPU-accelerated image processing, filter chains, and compute kernels",
        gradleModulePath = ":extensions:graphics:imageprocessing",
        dslModulePath = null
    ),
    CANVAS_2D(
        id = "canvas2d",
        dslMethodName = "canvas2d",
        category = "Graphics",
        displayName = "Canvas 2D & Curve Editor (:extensions:graphics:canvas2d)",
        description = "Immediate & retained-mode 2D vector/raster Canvas2D and interactive Bezier CurveEditor",
        gradleModulePath = ":extensions:graphics:canvas2d",
        dslModulePath = ":extensions:graphics:canvas2d:dsl"
    ),
    CANVAS_3D(
        id = "canvas3d",
        dslMethodName = "canvas3d",
        category = "Graphics",
        displayName = "Canvas 3D GL & Vulkan (:extensions:graphics:canvas3d)",
        description = "Hardware-accelerated Canvas3D and ScrollCanvas3D viewport widgets",
        gradleModulePath = ":extensions:graphics:canvas3d",
        dslModulePath = ":extensions:graphics:canvas3d:dsl"
    ),
    THREE_JS(
        id = "threejs",
        dslMethodName = "threeJs",
        category = "Graphics",
        displayName = "Three.js 3D Scene Graph (:extensions:graphics:threejs)",
        description = "Three.js-compatible 3D scene graph, PBR materials, lights, cameras, and glTF loader",
        gradleModulePath = ":extensions:graphics:threejs",
        dslModulePath = ":extensions:graphics:threejs:dsl"
    ),

    // --- Physics ---
    BOX_2D(
        id = "box2d",
        dslMethodName = "box2d",
        category = "Physics",
        displayName = "Box2D Physics Engine (:extensions:physics:box2d)",
        description = "Pure Kotlin Multiplatform 2D rigid-body physics engine and Canvas2D/Canvas3D debug renderer",
        gradleModulePath = ":extensions:physics:box2d",
        dslModulePath = ":extensions:physics:box2d:dsl"
    ),
    BOX_3D(
        id = "box3d",
        dslMethodName = "box3d",
        category = "Physics",
        displayName = "Box3D Physics Engine (:extensions:physics:box3d)",
        description = "Pure Kotlin Multiplatform 3D rigid-body physics engine with Three.js scene synchronizer",
        gradleModulePath = ":extensions:physics:box3d",
        dslModulePath = ":extensions:physics:box3d:dsl"
    ),

    // --- Game & Input ---
    GAMEPAD(
        id = "gamepad",
        dslMethodName = "gamepad",
        category = "Game & Input",
        displayName = "Gamepad & Controller Input (:extensions:game:gamepad)",
        description = "Cross-platform controller manager (Xbox, PlayStation, Switch, Generic), 6-axis IMU fusion, rumble/adaptive triggers, and spatial UI navigation",
        gradleModulePath = ":extensions:game:gamepad",
        dslModulePath = ":extensions:game:gamepad:dsl"
    ),
    ENGINE(
        id = "engine",
        dslMethodName = "engine",
        category = "Game & Input",
        displayName = "Game Engine Core & Animation (:extensions:game:engine)",
        description = "Scene/entity/subsystem framework, GameLoopScheduler, VBlank animation curves, 2D skeletons/IK, and UI stage host",
        gradleModulePath = ":extensions:game:engine",
        dslModulePath = ":extensions:game:engine:dsl"
    ),
    ENGINE_2D(
        id = "engine2d",
        dslMethodName = "engine2d",
        category = "Game & Input",
        displayName = "2D Game Engine (:extensions:game:engine2d)",
        description = "Canvas2D-backed 2D game engine with sprites, tilemaps, particle emitters, SVG characters, 2D camera, and Box2D physics",
        gradleModulePath = ":extensions:game:engine2d",
        dslModulePath = ":extensions:game:engine2d:dsl"
    ),
    ENGINE_3D(
        id = "engine3d",
        dslMethodName = "engine3d",
        category = "Game & Input",
        displayName = "3D Game Engine (:extensions:game:engine3d)",
        description = "Three.js/Canvas3D-backed 3D game engine with PBR mesh nodes, lights, cameras, procedural rigs, and Box3D physics",
        gradleModulePath = ":extensions:game:engine3d",
        dslModulePath = ":extensions:game:engine3d:dsl"
    ),

    // --- Media ---
    MEDIA(
        id = "media",
        dslMethodName = "media",
        category = "Media",
        displayName = "Media Player & Audio Analyzer (:extensions:media:media)",
        description = "VideoPlayer, AudioPlayer, FFT AudioAnalyzer, WebVTT/SRT subtitles, and Desktop VLC HDR engine",
        gradleModulePath = ":extensions:media:media",
        dslModulePath = ":extensions:media:media:dsl"
    ),
    MEDIA_CONTROLS(
        id = "mediacontrols",
        dslMethodName = "mediaControls",
        category = "Media",
        displayName = "System Media Controls (:extensions:media:mediacontrols)",
        description = "OS-level media transport controls, Now Playing metadata, and hardware media key integration",
        gradleModulePath = ":extensions:media:mediacontrols",
        dslModulePath = ":extensions:media:mediacontrols:dsl"
    ),

    // --- UI ---
    DSL(
        id = "dsl",
        dslMethodName = "dsl",
        category = "UI",
        displayName = "kotlinx.html-Style MVC & Component DSL (:extensions:ui:dsl)",
        description = "In-place kotlinx.html-style component builders, Model-View-Controller (MVC) classes, and companion :dsl extension builders",
        gradleModulePath = ":extensions:ui:dsl",
        dslModulePath = null
    ),
    OVERLAY(
        id = "overlay",
        dslMethodName = "overlay",
        category = "UI",
        displayName = "Overlay System (:extensions:ui:overlay)",
        description = "Hardware-accelerated floating overlay layer, HUDs, tooltips, and modal compositing",
        gradleModulePath = ":extensions:ui:overlay",
        dslModulePath = ":extensions:ui:overlay:dsl"
    ),
    ALIEN(
        id = "alien",
        dslMethodName = "alien",
        category = "UI",
        displayName = "Alien Canvas2D Bootstrap Renderer (:extensions:ui:alien)",
        description = "Self-rendered Bootstrap 5.3 Canvas2D UI engine on System targets with zero-overhead DOM pass-through on Web",
        gradleModulePath = ":extensions:ui:alien",
        dslModulePath = ":extensions:ui:alien:dsl"
    ),
    WEB_VIEW(
        id = "webview",
        dslMethodName = "webView",
        category = "UI",
        displayName = "Hybrid WebView (:extensions:ui:webview)",
        description = "Chromium Blink + NJS Native JIT on Desktop/Swing and native WebView/WKWebView/iframe on Mobile/Web",
        gradleModulePath = ":extensions:ui:webview",
        dslModulePath = ":extensions:ui:webview:dsl"
    ),
    CODE_EDITOR(
        id = "codeeditor",
        dslMethodName = "codeEditor",
        category = "UI",
        displayName = "IDE Code Editor (:extensions:ui:codeeditor)",
        description = "Multi-language syntax-highlighted IDE code editor with gutter, folding, minimap, and diff view",
        gradleModulePath = ":extensions:ui:codeeditor",
        dslModulePath = ":extensions:ui:codeeditor:dsl"
    ),
    AI_CHAT(
        id = "aichat",
        dslMethodName = "aiChat",
        category = "UI",
        displayName = "AI Chat & Agent Orchestrator (:extensions:ui:aichat)",
        description = "Reusable AI chat drawer, streaming markdown/code blocks, and multi-agent orchestrator",
        gradleModulePath = ":extensions:ui:aichat",
        dslModulePath = ":extensions:ui:aichat:dsl"
    );

    /** True when this extension provides a companion `:dsl` Gradle module (`<gradleModulePath>:dsl`). */
    val hasDslModule: Boolean
        get() = dslModulePath != null

    /**
     * Returns all transitive required extension modules that are automatically enabled
     * when this extension module is selected.
     */
    fun requiredExtensions(): Set<GenericUiExtensionModule> = when (this) {
        GPU -> emptySet()
        IMAGE_PROCESSING -> linkedSetOf(GPU)
        CANVAS_2D -> linkedSetOf(GPU, IMAGE_PROCESSING, OVERLAY)
        CANVAS_3D -> linkedSetOf(GPU, IMAGE_PROCESSING, CANVAS_2D, OVERLAY)
        THREE_JS -> linkedSetOf(GPU, IMAGE_PROCESSING, CANVAS_2D, CANVAS_3D, OVERLAY)
        BOX_2D -> linkedSetOf(GPU, IMAGE_PROCESSING, CANVAS_2D, CANVAS_3D, OVERLAY)
        BOX_3D -> linkedSetOf(GPU, IMAGE_PROCESSING, CANVAS_2D, CANVAS_3D, THREE_JS, OVERLAY)
        GAMEPAD -> emptySet()
        ENGINE -> linkedSetOf(GAMEPAD)
        ENGINE_2D -> linkedSetOf(GPU, IMAGE_PROCESSING, CANVAS_2D, CANVAS_3D, BOX_2D, GAMEPAD, ENGINE, OVERLAY)
        ENGINE_3D -> linkedSetOf(GPU, IMAGE_PROCESSING, CANVAS_2D, CANVAS_3D, THREE_JS, BOX_3D, GAMEPAD, ENGINE, OVERLAY)
        MEDIA -> linkedSetOf(OVERLAY)
        MEDIA_CONTROLS -> emptySet()
        DSL -> emptySet()
        OVERLAY -> emptySet()
        ALIEN -> linkedSetOf(GPU, IMAGE_PROCESSING, CANVAS_2D, OVERLAY)
        WEB_VIEW -> emptySet()
        CODE_EDITOR -> linkedSetOf(GPU, IMAGE_PROCESSING, CANVAS_2D, OVERLAY)
        AI_CHAT -> linkedSetOf(GPU, IMAGE_PROCESSING, CANVAS_2D, OVERLAY, CODE_EDITOR)
    }

    companion object {
        /** Canonical UI display order for extension categories. */
        val CATEGORIES: List<String> = listOf(
            "Graphics",
            "Physics",
            "Game & Input",
            "Media",
            "UI"
        )

        /** Returns all extensions belonging to [category] in canonical declaration order. */
        fun byCategory(category: String): List<GenericUiExtensionModule> =
            entries.filter { it.category == category }

        /**
         * Expands [selected] to include all transitively required extensions in canonical declaration order.
         */
        fun resolveTransitive(selected: Set<GenericUiExtensionModule>): Set<GenericUiExtensionModule> {
            val expanded = linkedSetOf<GenericUiExtensionModule>()
            val queue = ArrayDeque(selected)
            while (queue.isNotEmpty()) {
                val ext = queue.removeFirst()
                if (expanded.add(ext)) {
                    for (req in ext.requiredExtensions()) {
                        if (req !in expanded) {
                            queue.addLast(req)
                        }
                    }
                }
            }
            return entries.filter { it in expanded }.toCollection(LinkedHashSet())
        }

        /**
         * Returns the ordered list of Gradle module paths for [selected], including transitive base
         * extension modules and (when [DSL] is enabled) every companion `:dsl` module path.
         */
        fun resolveModulePaths(selected: Set<GenericUiExtensionModule>): List<String> {
            val resolved = resolveTransitive(selected)
            val includeDslVariants = DSL in resolved
            return buildList {
                for (ext in resolved) {
                    add(ext.gradleModulePath)
                }
                if (includeDslVariants) {
                    for (ext in resolved) {
                        val dslPath = ext.dslModulePath
                        if (dslPath != null) {
                            add(dslPath)
                        }
                    }
                }
            }
        }

        /** Looks up an extension module by its [dslMethodName], [id], [gradleModulePath], or [dslModulePath]. */
        fun fromDslMethod(name: String): GenericUiExtensionModule? {
            val trimmed = name.trim()
            return entries.firstOrNull {
                it.dslMethodName.equals(trimmed, ignoreCase = true) ||
                    it.id.equals(trimmed, ignoreCase = true) ||
                    it.gradleModulePath.equals(trimmed, ignoreCase = true) ||
                    it.dslModulePath?.equals(trimmed, ignoreCase = true) == true
            }
        }
    }
}
