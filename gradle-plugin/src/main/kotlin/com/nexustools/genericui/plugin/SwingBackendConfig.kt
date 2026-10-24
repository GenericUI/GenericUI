package com.nexustools.genericui.plugin

/**
 * Configuration block for the `:swing` (Java/JVM AWT & Swing) backend.
 *
 * By default, `:swing` uses zero-dependency Java2D (`Graphics2D`) rendering for `Canvas2D`.
 * Enabling [useLwjglGL] or [useVulkan] automatically injects the required LWJGL 3 artifacts
 * (`lwjgl-jawt`, `lwjgl-egl`, `lwjgl-opengles`, `lwjgl-opengl`, `lwjgl-vulkan`, `lwjgl-shaderc`,
 * `lwjgl-vma`) and platform native classifiers for hardware-accelerated `GLCanvas` and `VKCanvas`.
 */
open class SwingBackendConfig {
    /** Whether the `:swing` backend is enabled for this project. */
    var enabled: Boolean = false

    /**
     * Whether to wire LWJGL OpenGL / OpenGL ES (`lwjgl-jawt`, `lwjgl-egl`, `lwjgl-opengles`, `lwjgl-opengl`)
     * and native classifiers into the JVM runtime for hardware-accelerated `GLCanvas`.
     */
    var useLwjglGL: Boolean = false

    /**
     * Whether to wire LWJGL Vulkan (`lwjgl-jawt`, `lwjgl-vulkan`, `lwjgl-shaderc`, `lwjgl-vma`)
     * and native classifiers (including `MoltenVK` on macOS) into the JVM runtime for `VKCanvas`.
     */
    var useVulkan: Boolean = false

    /** The LWJGL 3 version to resolve when [useLwjglGL] or [useVulkan] is enabled. */
    var lwjglVersion: String = "3.3.6"

    /**
     * Target OS/Architecture classifiers for LWJGL native binaries.
     * Defaults to the current host platform detected via [OsArchClassifier.detectHost].
     */
    val targetClassifiers: MutableSet<OsArchClassifier> = linkedSetOf(OsArchClassifier.detectHost())

    /**
     * Adds all 6 desktop OS/Architecture classifiers (`windows-x64`, `windows-arm64`,
     * `linux-x64`, `linux-arm64`, `macos-x64`, `macos-arm64`) for cross-platform fat-JAR packaging.
     */
    fun targetAllDesktopClassifiers() {
        targetClassifiers.addAll(OsArchClassifier.entries)
    }

    /**
     * Convenience helper to enable LWJGL OpenGL ES support with an optional version override.
     */
    fun enableOpenGLES(version: String = lwjglVersion) {
        useLwjglGL = true
        lwjglVersion = version
    }

    /**
     * Convenience helper to enable LWJGL Vulkan support with an optional version override.
     */
    fun enableVulkan(version: String = lwjglVersion) {
        useVulkan = true
        lwjglVersion = version
    }
}
