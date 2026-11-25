package com.nexustools.genericui.ide.model

/**
 * Enum of all 9 target platform backends supported by the `GenericUI` framework across the
 * consolidated core modules (`:common`, `:system`, `:jvm`, `:apple`, `:desktop`, `:mobile`, `:web`, `:tests`).
 *
 * Used by the IntelliJ IDEA New Project Wizard and Manage Targets Dialog to render
 * interactive checkboxes and generate the corresponding `genericui { backends { ... } }`
 * and Kotlin Multiplatform target blocks in `build.gradle.kts`.
 */
enum class GenericUiTargetBackend(
    val id: String,
    val displayName: String,
    val category: String,
    val defaultSelected: Boolean,
    val gradleModulePath: String,
    val dslMethodName: String
) {
    WINDOWS(
        id = "windows",
        displayName = "Windows (Win32 / Direct2D / ANGLE / Vulkan)",
        category = "Desktop",
        defaultSelected = true,
        gradleModulePath = ":desktop",
        dslMethodName = "windows"
    ),
    MACOS(
        id = "macos",
        displayName = "macOS (AppKit / CoreGraphics / MoltenVK)",
        category = "Desktop",
        defaultSelected = true,
        gradleModulePath = ":desktop",
        dslMethodName = "macos"
    ),
    GTK(
        id = "gtk",
        displayName = "Linux (GTK4 / Libadwaita / Cairo / EGL / Vulkan)",
        category = "Desktop",
        defaultSelected = true,
        gradleModulePath = ":desktop",
        dslMethodName = "gtk"
    ),
    KDE(
        id = "kde",
        displayName = "Linux KDE (KF6 / Kirigami / QPainter / Vulkan)",
        category = "Desktop",
        defaultSelected = true,
        gradleModulePath = ":desktop",
        dslMethodName = "kde"
    ),
    SWING(
        id = "swing",
        displayName = "Java Swing (JVM AWT / Optional LWJGL GL & Vulkan)",
        category = "Desktop",
        defaultSelected = false,
        gradleModulePath = ":desktop",
        dslMethodName = "swing"
    ),
    ANDROID(
        id = "android",
        displayName = "Android (Native Material / RecyclerView / Vulkan & GLES)",
        category = "Mobile",
        defaultSelected = true,
        gradleModulePath = ":mobile",
        dslMethodName = "android"
    ),
    IOS(
        id = "ios",
        displayName = "iOS (UIKit / CoreGraphics / MoltenVK)",
        category = "Mobile",
        defaultSelected = true,
        gradleModulePath = ":mobile",
        dslMethodName = "ios"
    ),
    IPADOS(
        id = "ipados",
        displayName = "iPadOS (UIKit / Multi-Window / MenuBar / MoltenVK)",
        category = "Mobile",
        defaultSelected = true,
        gradleModulePath = ":mobile",
        dslMethodName = "ipados"
    ),
    WEB(
        id = "web",
        displayName = "Web (HTML5 DOM / Canvas2D / WebGL2 / WebGPU)",
        category = "Web",
        defaultSelected = true,
        gradleModulePath = ":web",
        dslMethodName = "web"
    );

    /** True if this backend targets an installed OS/system environment (`false` only for [WEB]). */
    val isSystem: Boolean
        get() = this != WEB

    /** True if this backend belongs to the desktop family sharing `:desktop`. */
    val isDesktop: Boolean
        get() = isNativeDesktop || this == SWING

    /** True if this backend belongs to the native desktop family (`windows`, `macos`, `gtk`, `kde`). */
    val isNativeDesktop: Boolean
        get() = this == WINDOWS || this == MACOS || this == GTK || this == KDE

    /** True if this backend belongs to the mobile family sharing `:mobile` (`android`, `ios`, `ipados`). */
    val isMobile: Boolean
        get() = this == ANDROID || this == IOS || this == IPADOS

    /** True if this backend belongs to the Apple ecosystem sharing `:apple` (`macos`, `ios`, `ipados`). */
    val isApple: Boolean
        get() = this == MACOS || this == IOS || this == IPADOS

    /** True if this backend belongs to the Apple UIKit family (`ios`, `ipados`). */
    val isIosFamily: Boolean
        get() = this == IOS || this == IPADOS

    /** True if this backend targets JVM or Android ART bytecode sharing `:jvm` (`swing`, `android`). */
    val isJvm: Boolean
        get() = this == SWING || this == ANDROID

    /** Returns the consolidated core Gradle module paths required by this backend. */
    fun requiredCoreModules(): Set<String> = buildSet {
        add(":common")
        if (isSystem) add(":system")
        if (isJvm) add(":jvm")
        if (isApple) add(":apple")
        if (isDesktop) add(":desktop")
        if (isMobile) add(":mobile")
        if (this@GenericUiTargetBackend == WEB) add(":web")
    }

    companion object {
        /** Default selection: all 8 native/mobile/web targets EXCEPT Swing (`allExceptSwing()`). */
        fun defaultSelectedSet(): Set<GenericUiTargetBackend> =
            entries.filter { it.defaultSelected }.toCollection(LinkedHashSet())

        /** All 8 backends except Swing (`windows`, `macos`, `gtk`, `kde`, `android`, `ios`, `ipados`, `web`). */
        fun allExceptSwingSet(): Set<GenericUiTargetBackend> =
            linkedSetOf(WINDOWS, MACOS, GTK, KDE, ANDROID, IOS, IPADOS, WEB)

        /** All 4 native desktop backends except Swing (`windows`, `macos`, `gtk`, `kde`). */
        fun desktopExceptSwingSet(): Set<GenericUiTargetBackend> =
            linkedSetOf(WINDOWS, MACOS, GTK, KDE)

        /** Alias for [desktopExceptSwingSet] matching `allNativeDesktop()`. */
        fun nativeDesktopSet(): Set<GenericUiTargetBackend> =
            desktopExceptSwingSet()

        /** All 5 desktop backends including Swing (`windows`, `macos`, `gtk`, `kde`, `swing`). */
        fun desktopAllSet(): Set<GenericUiTargetBackend> =
            linkedSetOf(WINDOWS, MACOS, GTK, KDE, SWING)

        /** Mobile backends (`android`, `ios`, `ipados`). */
        fun mobileSet(): Set<GenericUiTargetBackend> =
            linkedSetOf(ANDROID, IOS, IPADOS)

        /** Apple ecosystem backends (`macos`, `ios`, `ipados`). */
        fun appleSet(): Set<GenericUiTargetBackend> =
            linkedSetOf(MACOS, IOS, IPADOS)

        /** All 9 backends (`all()`). */
        fun allSet(): Set<GenericUiTargetBackend> =
            entries.toCollection(LinkedHashSet())

        /**
         * Returns the canonical consolidated core module paths required for [backends],
         * optionally including the single `:tests` (`JestElectricRunner`) module.
         */
        fun requiredCoreModulesFor(
            backends: Set<GenericUiTargetBackend>,
            includeTests: Boolean = true
        ): Set<String> {
            val result = linkedSetOf(":common")
            if (backends.any { it.isSystem }) result.add(":system")
            if (backends.any { it.isJvm }) result.add(":jvm")
            if (backends.any { it.isApple }) result.add(":apple")
            if (backends.any { it.isDesktop }) result.add(":desktop")
            if (backends.any { it.isMobile }) result.add(":mobile")
            if (WEB in backends) result.add(":web")
            if (includeTests) result.add(":tests")
            return result
        }

        /** Looks up a backend by its [id] or [dslMethodName] (case-insensitive). */
        fun fromId(id: String): GenericUiTargetBackend? {
            val normalized = id.trim().lowercase()
            if (normalized == "mac") return MACOS
            return entries.firstOrNull {
                it.id.lowercase() == normalized || it.dslMethodName.lowercase() == normalized
            }
        }
    }
}
