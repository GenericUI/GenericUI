package com.nexustools.genericui.plugin

import org.gradle.api.Action

/**
 * Top-level `genericui { ... }` Gradle DSL extension registered by [GenericUiGradlePlugin].
 *
 * Example usage in `build.gradle.kts`:
 * ```kotlin
 * plugins {
 *     id("com.nexustools.genericui")
 * }
 *
 * genericui {
 *     backends {
 *         windows()
 *         macos()
 *         gtk()
 *         qt()
 *         swing {
 *             useLwjglGL = true
 *             useVulkan = true
 *         }
 *         android()
 *         ios()
 *         web()
 *     }
 *     graphics {
 *         bundleAngleOnWindows = true
 *         bundleMoltenVkOnApple = true
 *         bundleShaderc = true
 *     }
 *     extensions {
 *         aiChat()
 *         threeJs()
 *         box2d()
 *         box3d()
 *     }
 *     styles {
 *         compileSass = true
 *         sourceDir = "src/commonMain/resources/styles"
 *         minify = false
 *     }
 * }
 * ```
 */
open class GenericUiExtension {
    /** Whether to automatically wire `:common` (`com.nexustools.genericui:common`) into `commonMainImplementation` / `implementation`. */
    var includeCommon: Boolean = true

    /** Version string used when resolving GenericUI composite build or Maven coordinates. */
    var version: String = "0.1.0-SNAPSHOT"

    /** Target platform backends configuration (`windows`, `macos`, `gtk`, `qt`, `swing`, `android`, `ios`, `web`). */
    val backends: GenericUiBackendsConfig = GenericUiBackendsConfig()

    /** Native GPU drivers, ANGLE, MoltenVK, and Shaderc configuration. */
    val graphics: GenericUiGraphicsConfig = GenericUiGraphicsConfig()

    /** Optional first-party extension modules (`dsl`, `aiChat`, `threeJs`, `box2d`, `box3d`, etc.). */
    @get:JvmName("getExtensionsConfig")
    val extensions: GenericUiExtensionsConfig = GenericUiExtensionsConfig()

    /** SASS/CSS stylesheet compilation configuration. */
    val styles: GenericUiStylesConfig = GenericUiStylesConfig()

    /** Configures target platform backends. */
    fun backends(action: Action<in GenericUiBackendsConfig>) {
        action.execute(backends)
    }

    /** Configures GPU acceleration, ANGLE, MoltenVK, and Shaderc bundling. */
    fun graphics(action: Action<in GenericUiGraphicsConfig>) {
        action.execute(graphics)
    }

    /** Configures first-party extension modules (`:extensions:aichat`, `:extensions:threejs`, `:extensions:box2d`, `:extensions:box3d`). */
    fun extensions(action: Action<in GenericUiExtensionsConfig>) {
        action.execute(extensions)
    }

    /** Configures `.scss` / `.sass` compilation into `.css` bundles. */
    fun styles(action: Action<in GenericUiStylesConfig>) {
        action.execute(styles)
    }
}
