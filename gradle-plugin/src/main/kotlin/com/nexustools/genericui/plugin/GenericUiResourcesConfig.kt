package com.nexustools.genericui.plugin

/**
 * DSL configuration for the GenericUI Android-like `R` / `RC` Resource Compiler (`genericui { resources { ... } }`).
 *
 * Scans `drawable/`, `values/` (`strings.xml`, `colors.xml`, `dimens.xml`), `raw/`, and `font/`
 * directories and automatically generates type-safe Kotlin `R` and `RC` objects (`1 Class Per File`)
 * alongside translated native resource bundles for Windows (`.rc`), Android (`VectorDrawable` & `values/`),
 * macOS/iOS (`Localizable.strings` & `Assets.xcassets`), Linux GTK4 (`gresource.xml`), Linux Qt6 (`.qrc`),
 * and Web/Swing (`genericui-resources.json` & CSS variables).
 */
open class GenericUiResourcesConfig {
    /** Whether automatic `R` / `RC` resource compilation and multi-backend translation is enabled. */
    var enabled: Boolean = true

    /**
     * Package name for the generated `R`, `RC`, and `GeneratedResourceRegistry` Kotlin objects.
     * When blank, defaults to `<project.group>.res` (or `"com.nexustools.genericui.generated.res"`).
     */
    var packageName: String = ""

    /**
     * Candidate resource root directories (relative to `projectDir`) scanned for `drawable/`, `values/`,
     * `raw/`, `font/`, `icons/`, and `strings.xml`.
     */
    val sourceDirs: MutableList<String> = mutableListOf(
        "src/commonMain/resources",
        "src/common/resources",
        "src/main/resources",
        "res"
    )

    /** Whether to emit native backend resource translations (`.rc`, `VectorDrawable`, `gresource.xml`, `.qrc`, `Assets.xcassets`, Web JSON). */
    var translateForAllBackends: Boolean = true

    /** Adds an additional resource directory (relative to `projectDir`) to scan. */
    fun sourceDir(relativePath: String) {
        if (relativePath.isNotBlank() && relativePath !in sourceDirs) {
            sourceDirs.add(relativePath)
        }
    }
}
