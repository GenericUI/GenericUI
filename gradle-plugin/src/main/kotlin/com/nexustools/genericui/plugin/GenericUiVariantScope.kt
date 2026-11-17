package com.nexustools.genericui.plugin

/**
 * Represents every source-set / backend tier supported by the GenericUI Gradle plugin,
 * ordered by specificity rank for configuration resolution (`0` = widest, `6` = concrete target),
 * matching `com.nexustools.genericui.enums.system.AppTarget` 1:1.
 */
enum class GenericUiVariantScope(
    val id: String,
    val specificityRank: Int,
    val isConcreteBackend: Boolean = false
) {
    COMMON("common", 0),
    SYSTEM_COMMON("systemCommon", 1),
    APPLE_COMMON("appleCommon", 2),
    JVM_COMMON("jvmCommon", 2),
    DESKTOP_COMMON("desktopCommon", 3),
    MOBILE_COMMON("mobileCommon", 3),
    NATIVE_DESKTOP_COMMON("nativeDesktopCommon", 4),
    LINUX_COMMON("linuxCommon", 5),
    IOS_COMMON("iosCommon", 5),
    WINDOWS("windows", 6, isConcreteBackend = true),
    MACOS("macos", 6, isConcreteBackend = true),
    GTK("gtk", 6, isConcreteBackend = true),
    QT("qt", 6, isConcreteBackend = true),
    SWING("swing", 6, isConcreteBackend = true),
    ANDROID("android", 6, isConcreteBackend = true),
    IOS("ios", 6, isConcreteBackend = true),
    IPADOS("ipados", 6, isConcreteBackend = true),
    WEB("web", 6, isConcreteBackend = true);

    /** Returns true when this scope applies to concrete [targetBackend]. */
    fun matchesBackend(targetBackend: GenericUiVariantScope): Boolean =
        this in specificityChainFor(targetBackend)

    companion object {
        val ALL_BACKENDS: List<GenericUiVariantScope> = listOf(
            WINDOWS,
            MACOS,
            GTK,
            QT,
            SWING,
            ANDROID,
            IOS,
            IPADOS,
            WEB
        )

        /**
         * Resolves a scope from its canonical id or common aliases (`mac`, `macos`, `ipad`, `ipados`,
         * `system`, `apple`, `jvm`, `desktop`, `desktopNative`, `linux`, `mobile`, `iosCommon`, etc.).
         */
        fun fromNameOrNull(raw: String): GenericUiVariantScope? {
            val normalized = raw.trim()
                .substringAfterLast('.')
                .replace("_", "")
                .replace("-", "")
                .lowercase()
            return when (normalized) {
                "common", "commonmain" -> COMMON
                "system", "systemcommon", "systemmain", "systemshared" -> SYSTEM_COMMON
                "apple", "applecommon", "applemain" -> APPLE_COMMON
                "jvm", "jvmcommon", "jvmmain" -> JVM_COMMON
                "desktop", "desktopcommon", "desktopmain" -> DESKTOP_COMMON
                "nativedesktop", "nativedesktopcommon", "nativedesktopmain",
                "desktopnative", "desktopnativecommon", "desktopnativemain",
                "desktopshared" -> NATIVE_DESKTOP_COMMON
                "linux", "linuxcommon", "linuxmain" -> LINUX_COMMON
                "mobile", "mobilecommon", "mobilemain", "mobileshared" -> MOBILE_COMMON
                "ioscommon", "ioscommonmain" -> IOS_COMMON
                "win", "windows", "windowsmain", "mingwx64" -> WINDOWS
                "mac", "macos", "macmain", "macosmain", "macosarm64" -> MACOS
                "gtk", "gtkmain" -> GTK
                "qt", "qtmain" -> QT
                "swing", "swingmain" -> SWING
                "android", "androidmain" -> ANDROID
                "ios", "iosmain" -> IOS
                "ipad", "ipados", "ipadmain", "ipadosmain" -> IPADOS
                "web", "webmain", "js", "wasmjs" -> WEB
                else -> null
            }
        }

        /** Alias for [specificityChainFor]: returns chain ordered from [COMMON] to [target]. */
        fun chainFromCommonToTarget(target: GenericUiVariantScope): List<GenericUiVariantScope> =
            specificityChainFor(target)

        /**
         * Returns the highest [specificityRank] among [targets] that match [targetBackend], or `null`
         * when none match.
         */
        fun resolveHighestSpecificityRank(
            targets: Iterable<GenericUiVariantScope>,
            targetBackend: GenericUiVariantScope
        ): Int? = targets
            .filter { it.matchesBackend(targetBackend) }
            .maxOfOrNull { it.specificityRank }

        /**
         * Ordered specificity chain from most general ([COMMON]) to [target].
         * Concrete targets inherit settings along this exact hierarchy.
         */
        fun specificityChainFor(target: GenericUiVariantScope): List<GenericUiVariantScope> = when (target) {
            WINDOWS -> listOf(COMMON, SYSTEM_COMMON, DESKTOP_COMMON, NATIVE_DESKTOP_COMMON, WINDOWS)
            MACOS -> listOf(COMMON, SYSTEM_COMMON, APPLE_COMMON, DESKTOP_COMMON, NATIVE_DESKTOP_COMMON, MACOS)
            GTK -> listOf(COMMON, SYSTEM_COMMON, DESKTOP_COMMON, NATIVE_DESKTOP_COMMON, LINUX_COMMON, GTK)
            QT -> listOf(COMMON, SYSTEM_COMMON, DESKTOP_COMMON, NATIVE_DESKTOP_COMMON, LINUX_COMMON, QT)
            SWING -> listOf(COMMON, SYSTEM_COMMON, JVM_COMMON, DESKTOP_COMMON, SWING)
            ANDROID -> listOf(COMMON, SYSTEM_COMMON, JVM_COMMON, MOBILE_COMMON, ANDROID)
            IOS -> listOf(COMMON, SYSTEM_COMMON, APPLE_COMMON, MOBILE_COMMON, IOS_COMMON, IOS)
            IPADOS -> listOf(COMMON, SYSTEM_COMMON, APPLE_COMMON, MOBILE_COMMON, IOS_COMMON, IPADOS)
            WEB -> listOf(COMMON, WEB)
            LINUX_COMMON -> listOf(COMMON, SYSTEM_COMMON, DESKTOP_COMMON, NATIVE_DESKTOP_COMMON, LINUX_COMMON)
            NATIVE_DESKTOP_COMMON -> listOf(COMMON, SYSTEM_COMMON, DESKTOP_COMMON, NATIVE_DESKTOP_COMMON)
            DESKTOP_COMMON -> listOf(COMMON, SYSTEM_COMMON, DESKTOP_COMMON)
            IOS_COMMON -> listOf(COMMON, SYSTEM_COMMON, APPLE_COMMON, MOBILE_COMMON, IOS_COMMON)
            MOBILE_COMMON -> listOf(COMMON, SYSTEM_COMMON, MOBILE_COMMON)
            APPLE_COMMON -> listOf(COMMON, SYSTEM_COMMON, APPLE_COMMON)
            JVM_COMMON -> listOf(COMMON, SYSTEM_COMMON, JVM_COMMON)
            SYSTEM_COMMON -> listOf(COMMON, SYSTEM_COMMON)
            COMMON -> listOf(COMMON)
        }
    }
}
