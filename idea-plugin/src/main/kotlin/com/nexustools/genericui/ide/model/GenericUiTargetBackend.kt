package com.nexustools.genericui.ide.model

/**
 * Enum of all 8 target platform backends supported by the `GenericUI` framework.
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
        gradleModulePath = ":desktop:windows",
        dslMethodName = "windows"
    ),
    MACOS(
        id = "macos",
        displayName = "macOS (AppKit / CoreGraphics / MoltenVK)",
        category = "Desktop",
        defaultSelected = true,
        gradleModulePath = ":desktop:macos",
        dslMethodName = "macos"
    ),
    GTK(
        id = "gtk",
        displayName = "Linux GTK (GTK4 / Cairo / EGL / Vulkan)",
        category = "Desktop",
        defaultSelected = true,
        gradleModulePath = ":desktop:gtk",
        dslMethodName = "gtk"
    ),
    QT(
        id = "qt",
        displayName = "Linux & Cross-Platform Qt (Qt6 / QPainter / QVulkanWindow)",
        category = "Desktop",
        defaultSelected = true,
        gradleModulePath = ":desktop:qt",
        dslMethodName = "qt"
    ),
    SWING(
        id = "swing",
        displayName = "Java Swing (JVM AWT / Optional LWJGL GL & Vulkan)",
        category = "Desktop",
        defaultSelected = false,
        gradleModulePath = ":desktop:swing",
        dslMethodName = "swing"
    ),
    ANDROID(
        id = "android",
        displayName = "Android (Native Material / RecyclerView / Vulkan & GLES)",
        category = "Mobile",
        defaultSelected = true,
        gradleModulePath = ":mobile:android",
        dslMethodName = "android"
    ),
    IOS(
        id = "ios",
        displayName = "iOS (UIKit / CoreGraphics / MoltenVK)",
        category = "Mobile",
        defaultSelected = true,
        gradleModulePath = ":mobile:ios",
        dslMethodName = "ios"
    ),
    WEB(
        id = "web",
        displayName = "Web (HTML5 DOM / Canvas2D / WebGL2 / WebGPU)",
        category = "Web",
        defaultSelected = true,
        gradleModulePath = ":web",
        dslMethodName = "web"
    );

    /** True if this backend belongs to the native desktop family sharing `:desktop:shared`. */
    val isNativeDesktop: Boolean
        get() = this == WINDOWS || this == MACOS || this == GTK || this == QT

    /** True if this backend belongs to the mobile family sharing `:mobile:shared`. */
    val isMobile: Boolean
        get() = this == ANDROID || this == IOS

    companion object {
        /** Default selection: all 7 native/mobile/web targets EXCEPT Swing (`allExceptSwing()`). */
        fun defaultSelectedSet(): Set<GenericUiTargetBackend> =
            entries.filter { it.defaultSelected }.toCollection(LinkedHashSet())

        /** All 7 backends except Swing (`windows`, `macos`, `gtk`, `qt`, `android`, `ios`, `web`). */
        fun allExceptSwingSet(): Set<GenericUiTargetBackend> =
            linkedSetOf(WINDOWS, MACOS, GTK, QT, ANDROID, IOS, WEB)

        /** All 4 native desktop backends except Swing (`windows`, `macos`, `gtk`, `qt`). */
        fun desktopExceptSwingSet(): Set<GenericUiTargetBackend> =
            linkedSetOf(WINDOWS, MACOS, GTK, QT)

        /** Alias for [desktopExceptSwingSet] matching `allNativeDesktop()`. */
        fun nativeDesktopSet(): Set<GenericUiTargetBackend> =
            desktopExceptSwingSet()

        /** All 5 desktop backends including Swing (`windows`, `macos`, `gtk`, `qt`, `swing`). */
        fun desktopAllSet(): Set<GenericUiTargetBackend> =
            linkedSetOf(WINDOWS, MACOS, GTK, QT, SWING)

        /** Mobile backends (`android`, `ios`). */
        fun mobileSet(): Set<GenericUiTargetBackend> =
            linkedSetOf(ANDROID, IOS)

        /** All 8 backends (`all()`). */
        fun allSet(): Set<GenericUiTargetBackend> =
            entries.toCollection(LinkedHashSet())

        /** Looks up a backend by its [id] or [dslMethodName] (case-insensitive). */
        fun fromId(id: String): GenericUiTargetBackend? {
            val normalized = id.trim().lowercase()
            return entries.firstOrNull {
                it.id.lowercase() == normalized || it.dslMethodName.lowercase() == normalized
            }
        }
    }
}
