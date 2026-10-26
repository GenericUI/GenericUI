package com.nexustools.genericui.component.fragment

import com.nexustools.genericui.enums.system.AppTarget

/**
 * Marks a [Fragment] class as an application fragment discovered by the GenericUI Gradle plugin.
 *
 * On Android, the Gradle plugin generates a 1:1 `AppCompatActivity` subclass (`<FragmentName>Activity`)
 * and registers it in `AndroidManifest.xml`. Across all targets, the plugin registers the fragment
 * in [FragmentRegistry].
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class AppFragment(
    val targets: Array<AppTarget> = [AppTarget.COMMON],
    val label: String = "",
    val route: String = "",
    val exported: Boolean = false,
    val screenOrientation: String = ""
)
