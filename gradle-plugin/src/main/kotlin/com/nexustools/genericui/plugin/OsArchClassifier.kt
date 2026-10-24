package com.nexustools.genericui.plugin

import java.util.Locale

/**
 * Enumerates supported desktop OS and CPU architecture combinations for native library
 * resolution (such as LWJGL OpenGL ES, ANGLE, Vulkan, Shaderc, and MoltenVK classifiers).
 */
enum class OsArchClassifier(
    val osFamily: String,
    val archName: String,
    val lwjglClassifier: String
) {
    WINDOWS_X64("windows", "x64", "natives-windows"),
    WINDOWS_ARM64("windows", "arm64", "natives-windows-arm64"),
    LINUX_X64("linux", "x64", "natives-linux"),
    LINUX_ARM64("linux", "arm64", "natives-linux-arm64"),
    MACOS_X64("macos", "x64", "natives-macos"),
    MACOS_ARM64("macos", "arm64", "natives-macos-arm64");

    val isWindows: Boolean
        get() = osFamily == "windows"

    val isLinux: Boolean
        get() = osFamily == "linux"

    val isMacOs: Boolean
        get() = osFamily == "macos"

    companion object {
        /**
         * Detects the current host operating system and CPU architecture.
         */
        fun detectHost(): OsArchClassifier {
            val osName = System.getProperty("os.name", "generic").lowercase(Locale.ROOT)
            val osArch = System.getProperty("os.arch", "x86_64").lowercase(Locale.ROOT)
            val isArm64 = osArch.contains("aarch64") || osArch.contains("arm64")

            return when {
                osName.contains("win") -> if (isArm64) WINDOWS_ARM64 else WINDOWS_X64
                osName.contains("mac") || osName.contains("darwin") -> if (isArm64) MACOS_ARM64 else MACOS_X64
                else -> if (isArm64) LINUX_ARM64 else LINUX_X64
            }
        }
    }
}
