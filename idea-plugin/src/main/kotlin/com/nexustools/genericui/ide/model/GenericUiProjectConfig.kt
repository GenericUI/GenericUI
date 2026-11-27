package com.nexustools.genericui.ide.model

/**
 * Immutable configuration model representing a new or existing `GenericUI` project,
 * its selected target backends, extension modules, Swing GPU options, SASS settings,
 * window defaults (`alwaysOnTop`, `aspectRatio`), Apple icon overrides, and `:tests` (`JestElectricRunner`) config.
 */
data class GenericUiProjectConfig(
    val projectName: String,
    val packageName: String,
    val projectRootPath: String,
    val genericUiLibPath: String = "D:/IdeaProjects/GenericUI",
    val selectedBackends: Set<GenericUiTargetBackend> = GenericUiTargetBackend.defaultSelectedSet(),
    val selectedExtensions: Set<GenericUiExtensionModule> = emptySet(),
    val templateType: GenericUiProjectTemplateType = GenericUiProjectTemplateType.STANDARD_FRAGMENT,
    val compileSass: Boolean = true,
    val swingUseLwjglGL: Boolean = true,
    val swingUseVulkan: Boolean = true,
    val bundleAngleOnWindows: Boolean = true,
    val bundleMoltenVkOnApple: Boolean = true,
    val bundleShaderc: Boolean = true,
    val themeMode: String = "auto",
    val globalStyleSheet: String = "styles/main.scss",
    val enableTesting: Boolean = true,
    val windowAlwaysOnTop: Boolean = false,
    val windowAspectRatioWidth: Int? = null,
    val windowAspectRatioHeight: Int? = null,
    val appleIcon: String = "",
    val macIcon: String = "",
    val iosIcon: String = "",
    val ipadosIcon: String = ""
) {
    /** Transitively resolved set of enabled extension modules for this project configuration. */
    val enabledExtensions: Set<GenericUiExtensionModule>
        get() = resolvedExtensions()

    /** Effective window aspect ratio (`width / height`), or `null` when unconstrained. */
    val windowAspectRatio: Double?
        get() = if (windowAspectRatioWidth != null && windowAspectRatioHeight != null && windowAspectRatioHeight > 0) {
            windowAspectRatioWidth.toDouble() / windowAspectRatioHeight.toDouble()
        } else {
            null
        }

    /** Returns [selectedExtensions] expanded with all transitively required extension modules compatible with [selectedBackends]. */
    fun resolvedExtensions(): Set<GenericUiExtensionModule> =
        GenericUiExtensionModule.resolveTransitive(selectedExtensions, selectedBackends)

    /** Returns the ordered categorized Gradle module paths (including `:alien` when [GenericUiExtensionModule.ALIEN] is enabled and `:dsl` submodules when [GenericUiExtensionModule.DSL] is enabled). */
    fun resolvedExtensionModulePaths(): List<String> =
        GenericUiExtensionModule.resolveModulePaths(selectedExtensions, selectedBackends)

    /** True if any non-web system backend is selected (requiring `:system`). */
    fun requiresSystemModule(): Boolean =
        selectedBackends.any { it.isSystem }

    /** True if any JVM / ART backend (`swing`, `android`) is selected (requiring `:jvm`). */
    fun requiresJvmModule(): Boolean =
        selectedBackends.any { it.isJvm }

    /** True if any Apple backend (`macos`, `ios`, `ipados`) is selected (requiring `:apple`). */
    fun requiresAppleModule(): Boolean =
        selectedBackends.any { it.isApple }

    /** True if any desktop backend (`windows`, `macos`, `gtk`, `kde`, `swing`) is selected (requiring `:desktop`). */
    fun requiresDesktopModule(): Boolean =
        selectedBackends.any { it.isDesktop }

    /** True if any native desktop backend (`windows`, `macos`, `gtk`, `kde`) is selected. */
    fun requiresSharedDesktop(): Boolean =
        selectedBackends.any { it.isNativeDesktop }

    /** True if any mobile backend (`android`, `ios`, `ipados`) is selected (requiring `:mobile`). */
    fun requiresSharedMobile(): Boolean =
        selectedBackends.any { it.isMobile }

    /** True if the web backend (`web`) is selected (requiring `:web`). */
    fun requiresWebModule(): Boolean =
        GenericUiTargetBackend.WEB in selectedBackends

    /** Returns the consolidated core Gradle module paths (`:common`, `:system`, `:jvm`, `:apple`, `:desktop`, `:mobile`, `:web`, and `:tests`). */
    fun requiredCoreModules(): Set<String> =
        GenericUiTargetBackend.requiredCoreModulesFor(selectedBackends, includeTests = enableTesting)

    /** True if the selected backend set matches `allExceptSwing()` (`windows`, `macos`, `gtk`, `kde`, `android`, `ios`, `ipados`, `web`). */
    fun isAllExceptSwingSelected(): Boolean =
        selectedBackends == GenericUiTargetBackend.allExceptSwingSet()

    /** True if the selected backend set matches `allDesktopExceptSwing()` / `allNativeDesktop()` (`windows`, `macos`, `gtk`, `kde`). */
    fun isDesktopExceptSwingSelected(): Boolean =
        selectedBackends == GenericUiTargetBackend.desktopExceptSwingSet()

    /** Alias for [isDesktopExceptSwingSelected] matching `allNativeDesktop()`. */
    fun isNativeDesktopSelected(): Boolean =
        isDesktopExceptSwingSelected()

    /** True if all 9 backends are selected (`all()`). */
    fun isAllBackendsSelected(): Boolean =
        selectedBackends == GenericUiTargetBackend.allSet()
}
