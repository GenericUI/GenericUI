package com.nexustools.genericui.plugin

/**
 * Variant target scopes supported by the GenericUI Gradle plugin (`genericui { ... }` variant blocks
 * and `@MainAppFragment` / `@AppFragment` `AppTarget` resolution), matching `AppTarget` 1:1.
 *
 * Specificity ranks (`5 -> 4 -> 3 -> 2 -> 1 -> 0`) define the exact override order from concrete
 * platform backends down to [COMMON].
 */
enum class GenericUiVariantScope(val id: String, val specificityRank: Int) {
    COMMON("common", 0),
    SYSTEM_COMMON("systemCommon", 1),
    DESKTOP_COMMON("desktopCommon", 2),
    MOBILE_COMMON("mobileCommon", 2),
    NATIVE_DESKTOP_COMMON("nativeDesktopCommon", 3),
    LINUX_COMMON("linuxCommon", 4),
    WINDOWS("windows", 5),
    MACOS("macos", 5),
    GTK("gtk", 5),
    QT("qt", 5),
    SWING("swing", 5),
    ANDROID("android", 5),
    IOS("ios", 5),
    WEB("web", 5);

    /** `true` if this scope is one of the 8 concrete platform backend targets (rank `5`). */
    val isConcreteBackend: Boolean
        get() = specificityRank == 5

    /**
     * Returns `true` if this scope applies to the given [backendScope].
     */
    fun matchesBackend(backendScope: GenericUiVariantScope): Boolean {
        if (this == backendScope) return true
        return this in specificityChainFor(backendScope)
    }

    companion object {
        /** All 8 concrete backend scopes in canonical order. */
        val ALL_BACKENDS: List<GenericUiVariantScope> = listOf(
            WINDOWS,
            MACOS,
            GTK,
            QT,
            SWING,
            ANDROID,
            IOS,
            WEB
        )

        /**
         * Resolves a [GenericUiVariantScope] from an `AppTarget` enum constant name, DSL scope id,
         * or alias (such as `"AppTarget.WINDOWS"`, `"WINDOWS"`, `"windows"`, `"mac"`, `"desktopNativeCommon"`).
         */
        fun fromNameOrNull(raw: String): GenericUiVariantScope? {
            val cleaned = raw.trim()
                .removePrefix("com.nexustools.genericui.enums.system.AppTarget.")
                .removePrefix("AppTarget.")
                .removePrefix("GenericUiVariantScope.")
                .trim()
            if (cleaned.isEmpty()) return null
            entries.firstOrNull {
                it.name.equals(cleaned, ignoreCase = true) || it.id.equals(cleaned, ignoreCase = true)
            }?.let { return it }
            return when (cleaned.lowercase()) {
                "mac", "osx", "darwin" -> MACOS
                "desktopnativecommon", "desktop_native_common" -> NATIVE_DESKTOP_COMMON
                "nativedesktop", "native_desktop" -> NATIVE_DESKTOP_COMMON
                "system", "system_common" -> SYSTEM_COMMON
                "desktop", "desktop_common" -> DESKTOP_COMMON
                "mobile", "mobile_common" -> MOBILE_COMMON
                "linux", "linux_common" -> LINUX_COMMON
                else -> null
            }
        }

        /**
         * Returns the specificity chain for [backendScope] ordered from most specific (rank `5`)
         * down to least specific ([COMMON], rank `0`).
         */
        fun specificityChainFor(backendScope: GenericUiVariantScope): List<GenericUiVariantScope> =
            when (backendScope) {
                WINDOWS -> listOf(WINDOWS, NATIVE_DESKTOP_COMMON, DESKTOP_COMMON, SYSTEM_COMMON, COMMON)
                MACOS -> listOf(MACOS, NATIVE_DESKTOP_COMMON, DESKTOP_COMMON, SYSTEM_COMMON, COMMON)
                GTK -> listOf(GTK, LINUX_COMMON, NATIVE_DESKTOP_COMMON, DESKTOP_COMMON, SYSTEM_COMMON, COMMON)
                QT -> listOf(QT, LINUX_COMMON, NATIVE_DESKTOP_COMMON, DESKTOP_COMMON, SYSTEM_COMMON, COMMON)
                SWING -> listOf(SWING, DESKTOP_COMMON, SYSTEM_COMMON, COMMON)
                ANDROID -> listOf(ANDROID, MOBILE_COMMON, SYSTEM_COMMON, COMMON)
                IOS -> listOf(IOS, MOBILE_COMMON, SYSTEM_COMMON, COMMON)
                WEB -> listOf(WEB, COMMON)
                LINUX_COMMON -> listOf(LINUX_COMMON, NATIVE_DESKTOP_COMMON, DESKTOP_COMMON, SYSTEM_COMMON, COMMON)
                NATIVE_DESKTOP_COMMON -> listOf(NATIVE_DESKTOP_COMMON, DESKTOP_COMMON, SYSTEM_COMMON, COMMON)
                DESKTOP_COMMON -> listOf(DESKTOP_COMMON, SYSTEM_COMMON, COMMON)
                MOBILE_COMMON -> listOf(MOBILE_COMMON, SYSTEM_COMMON, COMMON)
                SYSTEM_COMMON -> listOf(SYSTEM_COMMON, COMMON)
                COMMON -> listOf(COMMON)
            }

        /**
         * Returns the specificity chain for [backendScope] ordered from least specific ([COMMON], rank `0`)
         * up to most specific ([backendScope]).
         */
        fun chainFromCommonToTarget(backendScope: GenericUiVariantScope): List<GenericUiVariantScope> =
            specificityChainFor(backendScope).asReversed()

        /**
         * Returns the highest [specificityRank] among [targets] that matches [backendScope],
         * or `null` if none of [targets] match [backendScope].
         */
        fun resolveHighestSpecificityRank(
            targets: Collection<GenericUiVariantScope>,
            backendScope: GenericUiVariantScope
        ): Int? = targets.filter { it.matchesBackend(backendScope) }.maxOfOrNull { it.specificityRank }
    }
}
