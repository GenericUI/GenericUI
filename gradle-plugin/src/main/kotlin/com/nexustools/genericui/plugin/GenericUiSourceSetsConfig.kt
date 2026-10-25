package com.nexustools.genericui.plugin

import org.gradle.api.Action

/**
 * DSL container (`genericui { sourceSets { ... } }`) providing first-class GenericUI multiplatform
 * source sets:
 * - Root common: [common] / [commonMain], [commonTest]
 * - Desktop shared: [desktopCommon] / [desktopMain], [desktopTest]
 * - Native desktop shared (`windows`, `macos`, `gtk`, `qt`): [nativeDesktopCommon] / [nativeDesktopMain], [nativeDesktopTest]
 * - Native desktop platforms: [windowsMain] / [windowsTest], [macMain] / [macosMain] / [macTest] / [macosTest],
 *   [linuxCommon] / [linuxMain] / [linuxTest], [gtkMain] / [gtkTest], [qtMain] / [qtTest]
 * - JVM Swing desktop: [swingMain] / [swingTest]
 * - Mobile shared (`android`, `ios`): [mobileCommon] / [mobileMain], [mobileTest]
 * - Mobile platforms: [androidMain] / [androidTest], [iosMain] / [iosTest]
 * - Web (`js`, `wasmJs`): [webMain] / [webTest]
 */
open class GenericUiSourceSetsConfig {
    val common: GenericUiSourceSetSpec = GenericUiSourceSetSpec("commonMain")
    val commonMain: GenericUiSourceSetSpec get() = common
    val commonTest: GenericUiSourceSetSpec = GenericUiSourceSetSpec("commonTest")

    val desktopCommon: GenericUiSourceSetSpec = GenericUiSourceSetSpec("desktopCommon")
    val desktopMain: GenericUiSourceSetSpec get() = desktopCommon
    val desktopTest: GenericUiSourceSetSpec = GenericUiSourceSetSpec("desktopTest")

    val nativeDesktopCommon: GenericUiSourceSetSpec = GenericUiSourceSetSpec("nativeDesktopCommon")
    val nativeDesktopMain: GenericUiSourceSetSpec get() = nativeDesktopCommon
    val nativeDesktopTest: GenericUiSourceSetSpec = GenericUiSourceSetSpec("nativeDesktopTest")

    val windowsMain: GenericUiSourceSetSpec = GenericUiSourceSetSpec("windowsMain")
    val windowsTest: GenericUiSourceSetSpec = GenericUiSourceSetSpec("windowsTest")

    val macMain: GenericUiSourceSetSpec = GenericUiSourceSetSpec("macMain")
    val macosMain: GenericUiSourceSetSpec get() = macMain
    val macTest: GenericUiSourceSetSpec = GenericUiSourceSetSpec("macTest")
    val macosTest: GenericUiSourceSetSpec get() = macTest

    val linuxCommon: GenericUiSourceSetSpec = GenericUiSourceSetSpec("linuxCommon")
    val linuxMain: GenericUiSourceSetSpec get() = linuxCommon
    val linuxTest: GenericUiSourceSetSpec = GenericUiSourceSetSpec("linuxTest")

    val gtkMain: GenericUiSourceSetSpec = GenericUiSourceSetSpec("gtkMain")
    val gtkTest: GenericUiSourceSetSpec = GenericUiSourceSetSpec("gtkTest")

    val qtMain: GenericUiSourceSetSpec = GenericUiSourceSetSpec("qtMain")
    val qtTest: GenericUiSourceSetSpec = GenericUiSourceSetSpec("qtTest")

    val swingMain: GenericUiSourceSetSpec = GenericUiSourceSetSpec("swingMain")
    val swingTest: GenericUiSourceSetSpec = GenericUiSourceSetSpec("swingTest")

    val mobileCommon: GenericUiSourceSetSpec = GenericUiSourceSetSpec("mobileCommon")
    val mobileMain: GenericUiSourceSetSpec get() = mobileCommon
    val mobileTest: GenericUiSourceSetSpec = GenericUiSourceSetSpec("mobileTest")

    val androidMain: GenericUiSourceSetSpec = GenericUiSourceSetSpec("androidMain")
    val androidTest: GenericUiSourceSetSpec = GenericUiSourceSetSpec("androidTest")

    val iosMain: GenericUiSourceSetSpec = GenericUiSourceSetSpec("iosMain")
    val iosTest: GenericUiSourceSetSpec = GenericUiSourceSetSpec("iosTest")

    val webMain: GenericUiSourceSetSpec = GenericUiSourceSetSpec("webMain")
    val webTest: GenericUiSourceSetSpec = GenericUiSourceSetSpec("webTest")

    fun common(action: Action<in GenericUiSourceSetSpec>) = action.execute(common)
    fun commonMain(action: Action<in GenericUiSourceSetSpec>) = action.execute(common)
    fun commonTest(action: Action<in GenericUiSourceSetSpec>) = action.execute(commonTest)

    fun desktopCommon(action: Action<in GenericUiSourceSetSpec>) = action.execute(desktopCommon)
    fun desktopMain(action: Action<in GenericUiSourceSetSpec>) = action.execute(desktopCommon)
    fun desktopTest(action: Action<in GenericUiSourceSetSpec>) = action.execute(desktopTest)

    fun nativeDesktopCommon(action: Action<in GenericUiSourceSetSpec>) = action.execute(nativeDesktopCommon)
    fun nativeDesktopMain(action: Action<in GenericUiSourceSetSpec>) = action.execute(nativeDesktopCommon)
    fun nativeDesktopTest(action: Action<in GenericUiSourceSetSpec>) = action.execute(nativeDesktopTest)

    fun windowsMain(action: Action<in GenericUiSourceSetSpec>) = action.execute(windowsMain)
    fun windowsTest(action: Action<in GenericUiSourceSetSpec>) = action.execute(windowsTest)

    fun macMain(action: Action<in GenericUiSourceSetSpec>) = action.execute(macMain)
    fun macosMain(action: Action<in GenericUiSourceSetSpec>) = action.execute(macMain)
    fun macTest(action: Action<in GenericUiSourceSetSpec>) = action.execute(macTest)
    fun macosTest(action: Action<in GenericUiSourceSetSpec>) = action.execute(macTest)

    fun linuxCommon(action: Action<in GenericUiSourceSetSpec>) = action.execute(linuxCommon)
    fun linuxMain(action: Action<in GenericUiSourceSetSpec>) = action.execute(linuxCommon)
    fun linuxTest(action: Action<in GenericUiSourceSetSpec>) = action.execute(linuxTest)

    fun gtkMain(action: Action<in GenericUiSourceSetSpec>) = action.execute(gtkMain)
    fun gtkTest(action: Action<in GenericUiSourceSetSpec>) = action.execute(gtkTest)

    fun qtMain(action: Action<in GenericUiSourceSetSpec>) = action.execute(qtMain)
    fun qtTest(action: Action<in GenericUiSourceSetSpec>) = action.execute(qtTest)

    fun swingMain(action: Action<in GenericUiSourceSetSpec>) = action.execute(swingMain)
    fun swingTest(action: Action<in GenericUiSourceSetSpec>) = action.execute(swingTest)

    fun mobileCommon(action: Action<in GenericUiSourceSetSpec>) = action.execute(mobileCommon)
    fun mobileMain(action: Action<in GenericUiSourceSetSpec>) = action.execute(mobileCommon)
    fun mobileTest(action: Action<in GenericUiSourceSetSpec>) = action.execute(mobileTest)

    fun androidMain(action: Action<in GenericUiSourceSetSpec>) = action.execute(androidMain)
    fun androidTest(action: Action<in GenericUiSourceSetSpec>) = action.execute(androidTest)

    fun iosMain(action: Action<in GenericUiSourceSetSpec>) = action.execute(iosMain)
    fun iosTest(action: Action<in GenericUiSourceSetSpec>) = action.execute(iosTest)

    fun webMain(action: Action<in GenericUiSourceSetSpec>) = action.execute(webMain)
    fun webTest(action: Action<in GenericUiSourceSetSpec>) = action.execute(webTest)

    /** Resolves an existing [GenericUiSourceSetSpec] by name or defaults to [common]. */
    fun maybeCreate(sourceSetName: String): GenericUiSourceSetSpec {
        return allSpecs().firstOrNull {
            it.name.equals(sourceSetName, ignoreCase = true)
        } ?: when (sourceSetName.lowercase()) {
            "common", "commonmain" -> common
            "commontest" -> commonTest
            "desktop", "desktopmain", "desktopcommon" -> desktopCommon
            "nativedesktop", "nativedesktopmain", "nativedesktopcommon" -> nativeDesktopCommon
            "windows", "windowsmain", "mingwx64main" -> windowsMain
            "mac", "macos", "macmain", "macosmain" -> macMain
            "linux", "linuxmain", "linuxcommon" -> linuxCommon
            "gtk", "gtkmain" -> gtkMain
            "qt", "qtmain" -> qtMain
            "swing", "swingmain", "jvmmain" -> swingMain
            "mobile", "mobilemain", "mobilecommon" -> mobileCommon
            "android", "androidmain" -> androidMain
            "ios", "iosmain" -> iosMain
            "web", "webmain", "jsmain" -> webMain
            else -> common
        }
    }

    /** Returns all registered [GenericUiSourceSetSpec] instances in hierarchical order. */
    fun allSpecs(): List<GenericUiSourceSetSpec> = listOf(
        common,
        commonTest,
        desktopCommon,
        desktopTest,
        nativeDesktopCommon,
        nativeDesktopTest,
        windowsMain,
        windowsTest,
        macMain,
        macTest,
        linuxCommon,
        linuxTest,
        gtkMain,
        gtkTest,
        qtMain,
        qtTest,
        swingMain,
        swingTest,
        mobileCommon,
        mobileTest,
        androidMain,
        androidTest,
        iosMain,
        iosTest,
        webMain,
        webTest
    )
}
