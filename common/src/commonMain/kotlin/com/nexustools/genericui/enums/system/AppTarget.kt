package com.nexustools.genericui.enums.system

/**
 * Target scopes supported by `@AppFragment` and `@MainAppFragment`, covering all common
 * multi-platform scopes and all 8 concrete backend targets.
 *
 * Specificity ranks (`5 -> 4 -> 3 -> 2 -> 1 -> 0`) allow the Gradle plugin and runtime
 * to resolve the most specific `@MainAppFragment` for each enabled [BackendTarget].
 */
enum class AppTarget(val id: String, val specificityRank: Int) {
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

    fun matches(backend: BackendTarget): Boolean = when (this) {
        COMMON -> true
        SYSTEM_COMMON -> backend.isSystem
        DESKTOP_COMMON -> backend.isDesktop
        MOBILE_COMMON -> backend.isMobile
        NATIVE_DESKTOP_COMMON -> backend.isNativeDesktop
        LINUX_COMMON -> backend.isLinux
        WINDOWS -> backend == BackendTarget.Windows
        MACOS -> backend == BackendTarget.MacOS
        GTK -> backend == BackendTarget.Gtk
        QT -> backend == BackendTarget.Qt
        SWING -> backend == BackendTarget.Swing
        ANDROID -> backend == BackendTarget.Android
        IOS -> backend == BackendTarget.IOS
        WEB -> backend == BackendTarget.Web
    }

    companion object {
        fun fromBackendTarget(backend: BackendTarget): AppTarget = when (backend) {
            BackendTarget.Windows -> WINDOWS
            BackendTarget.MacOS -> MACOS
            BackendTarget.Gtk -> GTK
            BackendTarget.Qt -> QT
            BackendTarget.Swing -> SWING
            BackendTarget.Android -> ANDROID
            BackendTarget.IOS -> IOS
            BackendTarget.Web -> WEB
        }

        fun specificityChainFor(backend: BackendTarget): List<AppTarget> = when (backend) {
            BackendTarget.Windows -> listOf(WINDOWS, NATIVE_DESKTOP_COMMON, DESKTOP_COMMON, SYSTEM_COMMON, COMMON)
            BackendTarget.MacOS -> listOf(MACOS, NATIVE_DESKTOP_COMMON, DESKTOP_COMMON, SYSTEM_COMMON, COMMON)
            BackendTarget.Gtk -> listOf(GTK, LINUX_COMMON, NATIVE_DESKTOP_COMMON, DESKTOP_COMMON, SYSTEM_COMMON, COMMON)
            BackendTarget.Qt -> listOf(QT, LINUX_COMMON, NATIVE_DESKTOP_COMMON, DESKTOP_COMMON, SYSTEM_COMMON, COMMON)
            BackendTarget.Swing -> listOf(SWING, DESKTOP_COMMON, SYSTEM_COMMON, COMMON)
            BackendTarget.Android -> listOf(ANDROID, MOBILE_COMMON, SYSTEM_COMMON, COMMON)
            BackendTarget.IOS -> listOf(IOS, MOBILE_COMMON, SYSTEM_COMMON, COMMON)
            BackendTarget.Web -> listOf(WEB, COMMON)
        }

        fun resolveHighestSpecificityRank(targets: Array<out AppTarget>, backend: BackendTarget): Int? =
            targets.filter { it.matches(backend) }.maxOfOrNull { it.specificityRank }
    }
}
