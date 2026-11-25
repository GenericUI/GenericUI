package com.nexustools.genericui.plugin

import org.gradle.api.Action

/**
 * DSL configuration for the GenericUI Android-like `R` / `RC` Resource Compiler (`genericui { resources { ... } }`).
 *
 * Scans `drawable/`, `values/` (`strings.xml`, `colors.xml`, `dimens.xml`), `raw/`, and `font/`
 * directories and automatically generates type-safe Kotlin `R` and `RC` objects (`1 Class Per File`)
 * alongside translated native resource bundles for Windows (`.rc`), Android (`VectorDrawable` & `values/`),
 * macOS/iOS/iPadOS (`Localizable.strings` & `Assets.xcassets`), Linux GTK4 (`gresource.xml`),
 * Linux KDE (ELF `.rodata`), and Web/Swing (`genericui-resources.json` & CSS variables).
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

    /** Whether to emit native backend resource translations (`.rc`, `VectorDrawable`, `gresource.xml`, ELF `.rodata`, `Assets.xcassets`, Web JSON). */
    var translateForAllBackends: Boolean = true

    /** Optional generic icon path override configured directly inside `resources { ... }`. */
    var icon: String = ""

    /** Optional shared Apple icon path across macOS, iOS, and iPadOS (`resources { appleIcon = ... }`). */
    var appleIcon: String = ""

    /** Optional macOS-specific icon path (`resources { macIcon = ... }`). */
    var macIcon: String = ""

    /** Alias for [macIcon]. */
    var macosIcon: String
        get() = macIcon
        set(value) {
            macIcon = value
        }

    /** Optional iOS-specific icon path (`resources { iosIcon = ... }`). */
    var iosIcon: String = ""

    /** Optional iPadOS-specific icon path (`resources { ipadosIcon = ... }`). */
    var ipadosIcon: String = ""

    /** Alias for [ipadosIcon]. */
    var ipadIcon: String
        get() = ipadosIcon
        set(value) {
            ipadosIcon = value
        }

    /** Adds an additional resource directory (relative to `projectDir`) to scan. */
    fun sourceDir(relativePath: String) {
        if (relativePath.isNotBlank() && relativePath !in sourceDirs) {
            sourceDirs.add(relativePath)
        }
    }

    /** Sets the generic fallback icon path (`String`, `File`, etc.). */
    fun icon(path: Any?) {
        this.icon = GenericUiAppleIconConfig.normalizeIconValue(path)
    }

    /** Sets the shared Apple icon path across macOS, iOS, and iPadOS. */
    fun appleIcon(path: Any?) {
        this.appleIcon = GenericUiAppleIconConfig.normalizeIconValue(path)
    }

    /** Configures the shared Apple icon (`resources { apple { icon = ... } }`). */
    fun apple(action: Action<in GenericUiAppleIconConfig>) {
        action.execute(GenericUiAppleIconConfig({ appleIcon }, { appleIcon = it }))
    }

    /** Sets the macOS-specific icon path. */
    fun macIcon(path: Any?) {
        this.macIcon = GenericUiAppleIconConfig.normalizeIconValue(path)
    }

    /** Alias for [macIcon]. */
    fun macosIcon(path: Any?) {
        this.macIcon = GenericUiAppleIconConfig.normalizeIconValue(path)
    }

    /** Configures the macOS-specific icon (`resources { mac { icon = ... } }`). */
    fun mac(action: Action<in GenericUiAppleIconConfig>) {
        action.execute(GenericUiAppleIconConfig({ macIcon }, { macIcon = it }))
    }

    /** Alias for [mac] (`resources { macos { icon = ... } }`). */
    fun macos(action: Action<in GenericUiAppleIconConfig>) {
        mac(action)
    }

    /** Sets the iOS-specific icon path. */
    fun iosIcon(path: Any?) {
        this.iosIcon = GenericUiAppleIconConfig.normalizeIconValue(path)
    }

    /** Configures the iOS-specific icon (`resources { ios { icon = ... } }`). */
    fun ios(action: Action<in GenericUiAppleIconConfig>) {
        action.execute(GenericUiAppleIconConfig({ iosIcon }, { iosIcon = it }))
    }

    /** Sets the iPadOS-specific icon path. */
    fun ipadosIcon(path: Any?) {
        this.ipadosIcon = GenericUiAppleIconConfig.normalizeIconValue(path)
    }

    /** Alias for [ipadosIcon]. */
    fun ipadIcon(path: Any?) {
        this.ipadosIcon = GenericUiAppleIconConfig.normalizeIconValue(path)
    }

    /** Configures the iPadOS-specific icon (`resources { ipados { icon = ... } }`). */
    fun ipados(action: Action<in GenericUiAppleIconConfig>) {
        action.execute(GenericUiAppleIconConfig({ ipadosIcon }, { ipadosIcon = it }))
    }

    /** Alias for [ipados] (`resources { ipad { icon = ... } }`). */
    fun ipad(action: Action<in GenericUiAppleIconConfig>) {
        ipados(action)
    }
}
