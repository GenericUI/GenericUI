package com.nexustools.genericui.plugin

/**
 * DSL configuration block for native GPU drivers, translation layers, shader toolchains,
 * and `:desktop` VLC HDR / hardware video decoding:
 * - Google ANGLE (`libEGL.dll`, `libGLESv2.dll`, `d3dcompiler_47.dll`) on Windows so OpenGL ES 3.0+
 *   runs atop Direct3D 11 rather than relying on buggy vendor desktop WGL drivers.
 * - Khronos `MoltenVK` (`libMoltenVK.dylib` / `MoltenVK.xcframework`) on macOS and iOS so Vulkan 1.2+
 *   runs seamlessly on Apple Metal.
 * - Runtime GLSL-to-SPIR-V shader compilation via `shaderc`.
 * - Shared `:desktop` VLC playback pipeline with HDR10 / HLG / Dolby Vision tone-mapping shaders
 *   implemented in **Vulkan (`#version 450` / SPIR-V)** as the primary GPU pipeline and
 *   **OpenGL ES 3.0 (`#version 300 es`, via ANGLE on Windows)** as the automatic fallback,
 *   alongside hardware acceleration (`D3D11VA`, `DXVA2`, `NVDEC`, `VAAPI`, `VDPAU`, `VideoToolbox`).
 */
open class GenericUiGraphicsConfig {
    /**
     * When true (default), stages Google ANGLE (`libEGL.dll`, `libGLESv2.dll`, `d3dcompiler_47.dll`)
     * for Windows builds using `GLCanvas` or OpenGL ES fallback so OpenGL ES 3.0/3.1 translates cleanly to Direct3D 11.
     */
    var bundleAngleOnWindows: Boolean = true

    /**
     * Underlying ANGLE backend renderer on Windows (`"d3d11"`, `"d3d12"`, or `"vulkan"`).
     * Defaults to `"d3d11"` for maximum hardware compatibility across Windows systems.
     */
    var angleBackendRenderer: String = "d3d11"

    /**
     * When true (default), stages Khronos `MoltenVK` (`libMoltenVK.dylib` / `MoltenVK_icd.json`)
     * on macOS and iOS targets using `VKCanvas` or Vulkan VLC shaders so Vulkan 1.2+ translates to Apple Metal.
     */
    var bundleMoltenVkOnApple: Boolean = true

    /**
     * When true (default), includes `shaderc` for runtime GLSL-to-SPIR-V compilation in `VKCanvas` and `:desktop` VLC Vulkan shaders.
     */
    var bundleShaderc: Boolean = true

    /**
     * Whether to enable `VK_LAYER_KHRONOS_validation` automatically during debug builds.
     */
    var enableVulkanValidationLayersInDebug: Boolean = true

    /**
     * Whether `GLCanvas` contexts should request OpenGL ES 3.1 (with compute shader support)
     * before falling back to OpenGL ES 3.0 / 2.0.
     */
    var preferOpenGLES31: Boolean = true

    /**
     * Whether to enable native `libvlc` (`:extensions:media:desktop:shared` + `:extensions:media:desktop:{qt,gtk,windows,macos}`)
     * and `libvlcj` (`:extensions:media:desktop:swing`) on desktop backends.
     */
    var bundleVlcOnDesktop: Boolean = true

    /**
     * Primary GPU shader backend for `:extensions:media` video frame shaders
     * (`"vulkan"` primary with automatic `"opengles"` fallback).
     */
    var vlcPrimaryShaderBackend: String = "vulkan"

    /**
     * Fallback GPU shader backend for `:extensions:media` when Vulkan is unavailable (`"opengles"` via EGL / Google ANGLE).
     */
    var vlcFallbackShaderBackend: String = "opengles"

    /**
     * Whether `:extensions:media:desktop` enables HDR10, HLG, and Dolby Vision tone-mapping via `libvlc` / `libvlcj`.
     */
    var enableVlcHdrToneMapping: Boolean = true

    /**
     * Whether `:extensions:media:desktop` enables hardware decode via `libvlc` / `libvlcj`.
     */
    var enableVlcHardwareDecode: Boolean = true
}
