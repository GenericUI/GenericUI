package com.nexustools.genericui.ide.model

/**
 * Immutable configuration model representing a new or existing `GenericUI` project,
 * its selected target backends, extension modules, Swing GPU options, and SASS settings.
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
    val bundleShaderc: Boolean = true
) {
    /** Returns [selectedExtensions] expanded with all transitively required extension modules. */
    fun resolvedExtensions(): Set<GenericUiExtensionModule> =
        GenericUiExtensionModule.resolveTransitive(selectedExtensions)

    /** True if any native desktop backend (`windows`, `macos`, `gtk`, `qt`) is selected. */
    fun requiresSharedDesktop(): Boolean =
        selectedBackends.any { it.isNativeDesktop }

    /** True if any mobile backend (`android`, `ios`) is selected. */
    fun requiresSharedMobile(): Boolean =
        selectedBackends.any { it.isMobile }

    /** True if the selected backend set matches `allExceptSwing()` (`windows`, `macos`, `gtk`, `qt`, `android`, `ios`, `web`). */
    fun isAllExceptSwingSelected(): Boolean =
        selectedBackends == GenericUiTargetBackend.allExceptSwingSet()

    /** True if the selected backend set matches `allDesktopExceptSwing()` / `allNativeDesktop()` (`windows`, `macos`, `gtk`, `qt`). */
    fun isDesktopExceptSwingSelected(): Boolean =
        selectedBackends == GenericUiTargetBackend.desktopExceptSwingSet()

    /** Alias for [isDesktopExceptSwingSelected] matching `allNativeDesktop()`. */
    fun isNativeDesktopSelected(): Boolean =
        isDesktopExceptSwingSelected()

    /** True if all 8 backends are selected (`all()`), and Swing uses default flags. */
    fun isAllBackendsSelected(): Boolean =
        selectedBackends == GenericUiTargetBackend.allSet()
}
