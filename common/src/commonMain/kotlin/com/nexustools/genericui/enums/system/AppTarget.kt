package com.nexustools.genericui.enums.system

/**
 * Target scopes supported by `@AppFragment` and `@MainAppFragment`, covering all common
 * multi-platform scopes and all 9 concrete backend targets.
 *
 * Strict non-tying DAG specificity ranks (`6 -> 5 -> 4 -> 3 -> 2 -> 1 -> 0`) allow the
 * Gradle plugin and runtime to resolve the most specific `@MainAppFragment` for each enabled [BackendTarget].
 */
enum class AppTarget(val id: String, val specificityRank: Int) {
    COMMON("common", 0),
    SYSTEM_COMMON("systemCommon", 1),
    APPLE_COMMON("appleCommon", 2),
    JVM_COMMON("jvmCommon", 2),
    DESKTOP_COMMON("desktopCommon", 3),
    MOBILE_COMMON("mobileCommon", 3),
    NATIVE_DESKTOP_COMMON("nativeDesktopCommon", 4),
    LINUX_COMMON("linuxCommon", 5),
    IOS_COMMON("iosCommon", 5),
    WINDOWS("windows", 6),
    MACOS("macos", 6),
    GTK("gtk", 6),
    QT("qt", 6),
    SWING("swing", 6),
    ANDROID("android", 6),
    IOS("ios", 6),
    IPADOS("ipados", 6),
    WEB("web", 6);

    fun matches(backend: BackendTarget): Boolean = when (this) {
        COMMON -> true
        SYSTEM_COMMON -> backend.isSystem
        APPLE_COMMON -> backend.isApple
        JVM_COMMON -> backend == BackendTarget.Swing || backend == BackendTarget.Android
        DESKTOP_COMMON -> backend.isDesktop
        MOBILE_COMMON -> backend.isMobile
        NATIVE_DESKTOP_COMMON -> backend.isNativeDesktop
        LINUX_COMMON -> backend.isLinux
        IOS_COMMON -> backend.isIosFamily
        WINDOWS -> backend == BackendTarget.Windows
        MACOS -> backend == BackendTarget.MacOS
        GTK -> backend == BackendTarget.Gtk
        QT -> backend == BackendTarget.Qt
        SWING -> backend == BackendTarget.Swing
        ANDROID -> backend == BackendTarget.Android
        IOS -> backend == BackendTarget.IOS
        IPADOS -> backend == BackendTarget.IPadOS
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
            BackendTarget.IPadOS -> IPADOS
            BackendTarget.Web -> WEB
        }

        fun specificityChainFor(backend: BackendTarget): List<AppTarget> = when (backend) {
            BackendTarget.Windows -> listOf(WINDOWS, NATIVE_DESKTOP_COMMON, DESKTOP_COMMON, SYSTEM_COMMON, COMMON)
            BackendTarget.MacOS -> listOf(MACOS, NATIVE_DESKTOP_COMMON, DESKTOP_COMMON, APPLE_COMMON, SYSTEM_COMMON, COMMON)
            BackendTarget.Gtk -> listOf(GTK, LINUX_COMMON, NATIVE_DESKTOP_COMMON, DESKTOP_COMMON, SYSTEM_COMMON, COMMON)
            BackendTarget.Qt -> listOf(QT, LINUX_COMMON, NATIVE_DESKTOP_COMMON, DESKTOP_COMMON, SYSTEM_COMMON, COMMON)
            BackendTarget.Swing -> listOf(SWING, DESKTOP_COMMON, JVM_COMMON, SYSTEM_COMMON, COMMON)
            BackendTarget.Android -> listOf(ANDROID, MOBILE_COMMON, JVM_COMMON, SYSTEM_COMMON, COMMON)
            BackendTarget.IOS -> listOf(IOS, IOS_COMMON, MOBILE_COMMON, APPLE_COMMON, SYSTEM_COMMON, COMMON)
            BackendTarget.IPadOS -> listOf(IPADOS, IOS_COMMON, MOBILE_COMMON, APPLE_COMMON, SYSTEM_COMMON, COMMON)
            BackendTarget.Web -> listOf(WEB, COMMON)
        }

        fun resolveHighestSpecificityRank(targets: Array<out AppTarget>, backend: BackendTarget): Int? =
            targets.filter { it.matches(backend) }.maxOfOrNull { it.specificityRank }
    }
}
