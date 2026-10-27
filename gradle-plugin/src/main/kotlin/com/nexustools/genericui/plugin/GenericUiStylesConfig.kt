package com.nexustools.genericui.plugin

/**
 * DSL configuration block for GenericUI's SASS/CSS stylesheet compilation and Kotlin precompilation pipeline.
 *
 * GenericUI supports a dual-mode styling architecture:
 * 1. Type-safe `Bootstrap*` enums (`BootstrapType`, `BootstrapSize`, `SpacingScale`, `FontWeight`, etc.)
 * 2. `.scss` / `.sass` / `.css` stylesheets compiled at build time into both `.css` bundles and
 *    precompiled `.kt` (`PrecompiledStyleSheet`) objects so zero SASS/CSS parsing occurs at runtime.
 */
open class GenericUiStylesConfig {
    /** Whether to compile `.scss` and `.sass` files into `.css` bundles and precompiled `.kt` files during the build. */
    var compileSass: Boolean = true

    /**
     * Whether to generate `.css` files from compiled SASS/SCSS specifically for the Web target (`js`, `wasmJs`, `web`)
     * and bundle them into the Web target's processed resources and browser distribution.
     */
    var generateWebCss: Boolean = true

    /** Whether to precompile `.scss` / `.sass` / `.css` files into Kotlin (`PrecompiledStyleSheet`) `.kt` files for runtime efficiency. */
    var precompileToKotlin: Boolean = true

    /** Relative path (from project directory) containing `.scss`, `.sass`, and `.css` source files. */
    var sourceDir: String = "src/commonMain/resources/styles"

    /** Relative path (from project `layout.buildDirectory`) where compiled `.css` bundles are output. */
    var outputDir: String = "generated/genericui/styles"

    /**
     * Relative path (from project `layout.buildDirectory`) where compiled `.css` files for the Web target
     * are generated and wired into `jsMain` / `wasmJsMain` / `webMain` resources.
     */
    var webCssOutputDir: String = "generated/genericui/web-css"

    /**
     * Optional combined `.css` filename (e.g. `"styles.css"`) to emit into [webCssOutputDir] in addition
     * to each individual `<name>.css` file compiled from `<name>.scss` / `<name>.sass`.
     */
    var webCssBundleFileName: String = ""

    /** Relative path (from project `layout.buildDirectory`) where precompiled `.kt` (`PrecompiledStyleSheet`) files are output. */
    var kotlinOutputDir: String = "generated/genericui/kotlin"

    /** Target Kotlin package name for generated `PrecompiledStyleSheet` `.kt` files. */
    var kotlinPackageName: String = "com.nexustools.genericui.generated.styles"

    /** Whether to minify compiled `.css` rules by stripping comments and redundant whitespace. */
    var minify: Boolean = false

    /** Whether to emit Bootstrap 5 design tokens (`--bs-primary`, `--bs-border-radius`, etc.) at the root `:root` scope. */
    var includeBootstrapVariables: Boolean = true

    /**
     * Optional global stylesheet path (e.g. `"styles/main.scss"`, `"style.scss"`, or `"main.css"`)
     * automatically attached via `app.addStyleSheet(...)` at application startup across all targets.
     */
    var globalStyleSheet: String? = null

    /** Alias for [globalStyleSheet]. */
    var styleSheet: String?
        get() = globalStyleSheet
        set(value) {
            globalStyleSheet = value
        }

    /** Sets [globalStyleSheet] for this project. */
    fun globalStyleSheet(path: String) {
        this.globalStyleSheet = path
    }

    /** Sets [styleSheet] ([globalStyleSheet]) for this project. */
    fun styleSheet(path: String) {
        this.globalStyleSheet = path
    }
}
