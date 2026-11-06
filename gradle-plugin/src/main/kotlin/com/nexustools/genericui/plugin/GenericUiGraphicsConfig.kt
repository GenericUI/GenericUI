package com.nexustools.genericui.plugin

/**
 * Graphics pipeline configuration for `Canvas3D` (`GLContext` and `VKContext`).
 *
 * Configures:
 * - **Google ANGLE** (`libEGL.dll`, `libGLESv2.dll`, `d3dcompiler_47.dll`) bundling on Windows and
 *   Metal ANGLE (`libEGL.dylib`, `libGLESv2.dylib`) on Apple platforms for OpenGL ES 3.0+ support.
 * - **Software Fallback** (`vk_swiftshader.dll` / `libvk_swiftshader.dylib` / `swiftshader_icd.json`)
 *   bundling when `bundleSoftwareFallback = true`.
 * - **MoltenVK** (`libMoltenVK.dylib` / `MoltenVK.xcframework`) bundling on macOS and iOS for Vulkan 1.2 over Metal.
 * - **Shaderc** (`libshaderc` / `compileShaders`) for offline and runtime GLSL -> SPIR-V shader compilation.
 */
open class GenericUiGraphicsConfig {
    /** Whether to bundle Google ANGLE (`libEGL.dll`, `libGLESv2.dll`, `d3dcompiler_47.dll`) for Windows targets. */
    var bundleAngleOnWindows: Boolean = true

    /** Whether to bundle Google ANGLE (`libEGL.dylib`, `libGLESv2.dylib`) over Metal on macOS and iOS targets. */
    var bundleAngleOnApple: Boolean = true

    /** Whether to bundle SwiftShader (`vk_swiftshader.dll`, `vk_swiftshader_icd.json`) for software fallback rendering. */
    var bundleSoftwareFallback: Boolean = false

    /** Whether to compile GLSL shaders into SPIR-V binaries during the Gradle build. */
    var compileShaders: Boolean = false

    /** Whether to bundle `MoltenVK` (`libMoltenVK.dylib`) for macOS and iOS Vulkan targets. */
    var bundleMoltenVkOnApple: Boolean = true

    /** Whether to include `shaderc` for runtime/build-time GLSL-to-SPIR-V shader compilation. */
    var bundleShaderc: Boolean = true

    /** Whether to enable `VK_LAYER_KHRONOS_validation` automatically in debug builds. */
    var enableVulkanValidationLayersInDebug: Boolean = true

    /** Preferred ANGLE backend renderer on Windows (`"d3d11"`, `"vulkan"`, or `"gl"`). */
    var angleBackendRenderer: String = "d3d11"

    /** Minimum OpenGL ES version to request on Desktop/Mobile (`"3.0"` or `"3.1"`). */
    var preferOpenGLES31: Boolean = true

    /** Whether to bundle libVLC native libraries on desktop targets. */
    var bundleVlcOnDesktop: Boolean = true

    /** Whether to enable HDR-to-SDR tone mapping in desktop VLC video playback. */
    var enableVlcHdrToneMapping: Boolean = true

    /** Whether to enable hardware video decode acceleration in desktop VLC video playback. */
    var enableVlcHardwareDecode: Boolean = true
}
