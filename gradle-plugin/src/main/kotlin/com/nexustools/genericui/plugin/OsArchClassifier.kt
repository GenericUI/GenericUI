package com.nexustools.genericui.plugin

import java.util.Locale

/**
 * Enumerates supported OS and CPU architecture combinations for native library
 * resolution (such as LWJGL OpenGL ES, ANGLE, Vulkan, Shaderc, and MoltenVK classifiers).
 */
enum class OsArchClassifier(
    val osFamily: String,
    val archName: String,
    val lwjglClassifier: String
) {
    WINDOWS_X64("windows", "x64", "natives-windows"),
    WINDOWS_X32("windows", "x32", "natives-windows-x86"),
    WINDOWS_ARM64("windows", "arm64", "natives-windows-arm64"),
    MACOS_ARM64("macos", "arm64", "natives-macos-arm64"),
    LINUX_X64("linux", "x64", "natives-linux"),
    LINUX_X32("linux", "x32", "natives-linux-x86"),
    LINUX_ARM64("linux", "arm64", "natives-linux-arm64"),
    LINUX_ARM32("linux", "arm32", "natives-linux-arm32"),
    ANDROID_X64("android", "x64", "natives-android-x64"),
    ANDROID_X32("android", "x32", "natives-android-x86"),
    ANDROID_ARM64("android", "arm64", "natives-android-arm64"),
    ANDROID_ARM32("android", "arm32", "natives-android-arm32"),
    IOS_ARM64("ios", "arm64", "natives-ios-arm64"),
    IOS_SIMULATOR_ARM64("ios", "simulatorArm64", "natives-ios-simulator-arm64");

    val isWindows: Boolean
        get() = osFamily == "windows"

    val isLinux: Boolean
        get() = osFamily == "linux"

    val isMacOs: Boolean
        get() = osFamily == "macos"

    val isAndroid: Boolean
        get() = osFamily == "android"

    val isIos: Boolean
        get() = osFamily == "ios"

    val isDesktop: Boolean
        get() = isWindows || isLinux || isMacOs

    val isMobile: Boolean
        get() = isAndroid || isIos

    companion object {
        /**
         * Detects the current host operating system and CPU architecture.
         */
        fun detectHost(): OsArchClassifier {
            val osName = System.getProperty("os.name", "generic").lowercase(Locale.ROOT)
            val osArch = System.getProperty("os.arch", "x86_64").lowercase(Locale.ROOT)
            val isArm64 = osArch.contains("aarch64") || osArch.contains("arm64")
            val isArm32 = !isArm64 && osArch.contains("arm")
            val isX32 = osArch == "x86" || osArch == "i386" || osArch == "i486" || osArch == "i586" || osArch == "i686"

            return when {
                osName.contains("win") -> if (isArm64) WINDOWS_ARM64 else if (isX32) WINDOWS_X32 else WINDOWS_X64
                osName.contains("mac") || osName.contains("darwin") -> MACOS_ARM64
                else -> if (isArm64) LINUX_ARM64 else if (isArm32) LINUX_ARM32 else if (isX32) LINUX_X32 else LINUX_X64
            }
        }
    }
}
