package com.nexustools.genericui.plugin

import org.gradle.api.Action

/**
 * DSL container (`genericui { sourceSets { ... } }`) providing first-class GenericUI multiplatform
 * source sets organized in a hierarchical tree:
 * ```
 * - common (common / commonMain, commonTest)
 *   - web (web / webMain, webTest -> jsMain, wasmJsMain)
 *   - systemCommon (systemCommon / systemMain, systemTest)
 *     - desktopCommon (desktopCommon / desktopMain, desktopTest)
 *       - swing (swing / swingMain, swingTest -> jvmMain)
 *       - desktopNativeCommon (desktopNativeCommon / desktopNativeMain, desktopNativeTest)
 *         - qt, gtk, windows, mac (qt / qtMain, gtk / gtkMain, linuxCommon / linuxMain, windows / windowsMain, mac / macMain / macosMain)
 *     - mobileCommon (mobileCommon / mobileMain, mobileTest)
 *       - android, ios (android / androidMain, ios / iosMain)
 * ```
 */
open class GenericUiSourceSetsConfig {
    val common: GenericUiSourceSetSpec = GenericUiSourceSetSpec("commonMain")
    val commonMain: GenericUiSourceSetSpec get() = common
    val commonTest: GenericUiSourceSetSpec = GenericUiSourceSetSpec("commonTest")

    val web: GenericUiSourceSetSpec = GenericUiSourceSetSpec("webMain")
    val webMain: GenericUiSourceSetSpec get() = web
    val webTest: GenericUiSourceSetSpec = GenericUiSourceSetSpec("webTest")

    val systemCommon: GenericUiSourceSetSpec = GenericUiSourceSetSpec("systemCommon")
    val systemMain: GenericUiSourceSetSpec get() = systemCommon
    val systemTest: GenericUiSourceSetSpec = GenericUiSourceSetSpec("systemTest")

    val desktopCommon: GenericUiSourceSetSpec = GenericUiSourceSetSpec("desktopCommon")
    val desktopMain: GenericUiSourceSetSpec get() = desktopCommon
    val desktopTest: GenericUiSourceSetSpec = GenericUiSourceSetSpec("desktopTest")

    val swing: GenericUiSourceSetSpec = GenericUiSourceSetSpec("swingMain")
    val swingMain: GenericUiSourceSetSpec get() = swing
    val swingTest: GenericUiSourceSetSpec = GenericUiSourceSetSpec("swingTest")

    val nativeDesktopCommon: GenericUiSourceSetSpec = GenericUiSourceSetSpec("nativeDesktopCommon")
    val nativeDesktopMain: GenericUiSourceSetSpec get() = nativeDesktopCommon
    val nativeDesktopTest: GenericUiSourceSetSpec = GenericUiSourceSetSpec("nativeDesktopTest")

    @Deprecated("Use nativeDesktopCommon", ReplaceWith("nativeDesktopCommon"))
    val desktopNativeCommon: GenericUiSourceSetSpec get() = nativeDesktopCommon

    @Deprecated("Use nativeDesktopMain", ReplaceWith("nativeDesktopMain"))
    val desktopNativeMain: GenericUiSourceSetSpec get() = nativeDesktopCommon

    @Deprecated("Use nativeDesktopTest", ReplaceWith("nativeDesktopTest"))
    val desktopNativeTest: GenericUiSourceSetSpec get() = nativeDesktopTest

    val windows: GenericUiSourceSetSpec = GenericUiSourceSetSpec("windowsMain")
    val windowsMain: GenericUiSourceSetSpec get() = windows
    val windowsTest: GenericUiSourceSetSpec = GenericUiSourceSetSpec("windowsTest")

    val mac: GenericUiSourceSetSpec = GenericUiSourceSetSpec("macMain")
    val macMain: GenericUiSourceSetSpec get() = mac
    val macosMain: GenericUiSourceSetSpec get() = mac
    val macTest: GenericUiSourceSetSpec = GenericUiSourceSetSpec("macTest")
    val macosTest: GenericUiSourceSetSpec get() = macTest

    val linuxCommon: GenericUiSourceSetSpec = GenericUiSourceSetSpec("linuxCommon")
    val linuxMain: GenericUiSourceSetSpec get() = linuxCommon
    val linuxTest: GenericUiSourceSetSpec = GenericUiSourceSetSpec("linuxTest")

    val gtk: GenericUiSourceSetSpec = GenericUiSourceSetSpec("gtkMain")
    val gtkMain: GenericUiSourceSetSpec get() = gtk
    val gtkTest: GenericUiSourceSetSpec = GenericUiSourceSetSpec("gtkTest")

    val qt: GenericUiSourceSetSpec = GenericUiSourceSetSpec("qtMain")
    val qtMain: GenericUiSourceSetSpec get() = qt
    val qtTest: GenericUiSourceSetSpec = GenericUiSourceSetSpec("qtTest")

    val mobileCommon: GenericUiSourceSetSpec = GenericUiSourceSetSpec("mobileCommon")
    val mobileMain: GenericUiSourceSetSpec get() = mobileCommon
    val mobileTest: GenericUiSourceSetSpec = GenericUiSourceSetSpec("mobileTest")

    val android: GenericUiSourceSetSpec = GenericUiSourceSetSpec("androidMain")
    val androidMain: GenericUiSourceSetSpec get() = android
    val androidTest: GenericUiSourceSetSpec = GenericUiSourceSetSpec("androidTest")

    val ios: GenericUiSourceSetSpec = GenericUiSourceSetSpec("iosMain")
    val iosMain: GenericUiSourceSetSpec get() = ios
    val iosTest: GenericUiSourceSetSpec = GenericUiSourceSetSpec("iosTest")

    fun common(action: Action<in GenericUiSourceSetSpec>) = action.execute(common)
    fun commonMain(action: Action<in GenericUiSourceSetSpec>) = action.execute(common)
    fun commonTest(action: Action<in GenericUiSourceSetSpec>) = action.execute(commonTest)

    fun web(action: Action<in GenericUiSourceSetSpec>) = action.execute(web)
    fun webMain(action: Action<in GenericUiSourceSetSpec>) = action.execute(web)
    fun webTest(action: Action<in GenericUiSourceSetSpec>) = action.execute(webTest)

    fun systemCommon(action: Action<in GenericUiSourceSetSpec>) = action.execute(systemCommon)
    fun systemMain(action: Action<in GenericUiSourceSetSpec>) = action.execute(systemCommon)
    fun systemTest(action: Action<in GenericUiSourceSetSpec>) = action.execute(systemTest)

    fun desktopCommon(action: Action<in GenericUiSourceSetSpec>) = action.execute(desktopCommon)
    fun desktopMain(action: Action<in GenericUiSourceSetSpec>) = action.execute(desktopCommon)
    fun desktopTest(action: Action<in GenericUiSourceSetSpec>) = action.execute(desktopTest)

    fun swing(action: Action<in GenericUiSourceSetSpec>) = action.execute(swing)
    fun swingMain(action: Action<in GenericUiSourceSetSpec>) = action.execute(swing)
    fun swingTest(action: Action<in GenericUiSourceSetSpec>) = action.execute(swingTest)

    fun nativeDesktopCommon(action: Action<in GenericUiSourceSetSpec>) = action.execute(nativeDesktopCommon)
    fun nativeDesktopMain(action: Action<in GenericUiSourceSetSpec>) = action.execute(nativeDesktopCommon)
    fun nativeDesktopTest(action: Action<in GenericUiSourceSetSpec>) = action.execute(nativeDesktopTest)

    @Deprecated("Use nativeDesktopCommon", ReplaceWith("nativeDesktopCommon(action)"))
    fun desktopNativeCommon(action: Action<in GenericUiSourceSetSpec>) = action.execute(nativeDesktopCommon)

    @Deprecated("Use nativeDesktopMain", ReplaceWith("nativeDesktopMain(action)"))
    fun desktopNativeMain(action: Action<in GenericUiSourceSetSpec>) = action.execute(nativeDesktopCommon)

    @Deprecated("Use nativeDesktopTest", ReplaceWith("nativeDesktopTest(action)"))
    fun desktopNativeTest(action: Action<in GenericUiSourceSetSpec>) = action.execute(nativeDesktopTest)

    fun windows(action: Action<in GenericUiSourceSetSpec>) = action.execute(windows)
    fun windowsMain(action: Action<in GenericUiSourceSetSpec>) = action.execute(windows)
    fun windowsTest(action: Action<in GenericUiSourceSetSpec>) = action.execute(windowsTest)

    fun mac(action: Action<in GenericUiSourceSetSpec>) = action.execute(mac)
    fun macMain(action: Action<in GenericUiSourceSetSpec>) = action.execute(mac)
    fun macosMain(action: Action<in GenericUiSourceSetSpec>) = action.execute(mac)
    fun macTest(action: Action<in GenericUiSourceSetSpec>) = action.execute(macTest)
    fun macosTest(action: Action<in GenericUiSourceSetSpec>) = action.execute(macTest)

    fun linuxCommon(action: Action<in GenericUiSourceSetSpec>) = action.execute(linuxCommon)
    fun linuxMain(action: Action<in GenericUiSourceSetSpec>) = action.execute(linuxCommon)
    fun linuxTest(action: Action<in GenericUiSourceSetSpec>) = action.execute(linuxTest)

    fun gtk(action: Action<in GenericUiSourceSetSpec>) = action.execute(gtk)
    fun gtkMain(action: Action<in GenericUiSourceSetSpec>) = action.execute(gtk)
    fun gtkTest(action: Action<in GenericUiSourceSetSpec>) = action.execute(gtkTest)

    fun qt(action: Action<in GenericUiSourceSetSpec>) = action.execute(qt)
    fun qtMain(action: Action<in GenericUiSourceSetSpec>) = action.execute(qt)
    fun qtTest(action: Action<in GenericUiSourceSetSpec>) = action.execute(qtTest)

    fun mobileCommon(action: Action<in GenericUiSourceSetSpec>) = action.execute(mobileCommon)
    fun mobileMain(action: Action<in GenericUiSourceSetSpec>) = action.execute(mobileCommon)
    fun mobileTest(action: Action<in GenericUiSourceSetSpec>) = action.execute(mobileTest)

    fun android(action: Action<in GenericUiSourceSetSpec>) = action.execute(android)
    fun androidMain(action: Action<in GenericUiSourceSetSpec>) = action.execute(android)
    fun androidTest(action: Action<in GenericUiSourceSetSpec>) = action.execute(androidTest)

    fun ios(action: Action<in GenericUiSourceSetSpec>) = action.execute(ios)
    fun iosMain(action: Action<in GenericUiSourceSetSpec>) = action.execute(ios)
    fun iosTest(action: Action<in GenericUiSourceSetSpec>) = action.execute(iosTest)

    /** Resolves an existing [GenericUiSourceSetSpec] by name or defaults to [common]. */
    fun maybeCreate(sourceSetName: String): GenericUiSourceSetSpec {
        return allSpecs().firstOrNull {
            it.name.equals(sourceSetName, ignoreCase = true)
        } ?: when (sourceSetName.lowercase()) {
            "common", "commonmain" -> common
            "commontest" -> commonTest
            "web", "webmain", "jsmain", "wasmjsmain" -> web
            "webtest", "jstest", "wasmjstest" -> webTest
            "system", "systemcommon", "systemmain" -> systemCommon
            "systemtest" -> systemTest
            "desktop", "desktopmain", "desktopcommon" -> desktopCommon
            "desktoptest" -> desktopTest
            "swing", "swingmain", "jvmmain" -> swing
            "swingtest", "jvmtest" -> swingTest
            "nativedesktop", "nativedesktopmain", "nativedesktopcommon",
            "desktopnative", "desktopnativemain", "desktopnativecommon" -> nativeDesktopCommon
            "nativedesktoptest", "desktopnativetest" -> nativeDesktopTest
            "windows", "windowsmain", "mingwx64main" -> windows
            "windowstest", "mingwx64test" -> windowsTest
            "mac", "macos", "macmain", "macosmain" -> mac
            "mactest", "macostest" -> macTest
            "linux", "linuxmain", "linuxcommon" -> linuxCommon
            "linuxtest" -> linuxTest
            "gtk", "gtkmain" -> gtk
            "gtktest" -> gtkTest
            "qt", "qtmain" -> qt
            "qttest" -> qtTest
            "mobile", "mobilemain", "mobilecommon" -> mobileCommon
            "mobiletest" -> mobileTest
            "android", "androidmain" -> android
            "androidtest", "androidunittest" -> androidTest
            "ios", "iosmain" -> ios
            "iostest" -> iosTest
            else -> common
        }
    }

    /** Returns all registered [GenericUiSourceSetSpec] instances in hierarchical order. */
    fun allSpecs(): List<GenericUiSourceSetSpec> = listOf(
        common,
        commonTest,
        web,
        webTest,
        systemCommon,
        systemTest,
        desktopCommon,
        desktopTest,
        swing,
        swingTest,
        nativeDesktopCommon,
        nativeDesktopTest,
        windows,
        windowsTest,
        mac,
        macTest,
        linuxCommon,
        linuxTest,
        gtk,
        gtkTest,
        qt,
        qtTest,
        mobileCommon,
        mobileTest,
        android,
        androidTest,
        ios,
        iosTest
    )
}
