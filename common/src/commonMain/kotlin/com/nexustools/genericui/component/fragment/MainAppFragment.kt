package com.nexustools.genericui.component.fragment

import com.nexustools.genericui.enums.system.AppTarget

/**
 * Marks a [Fragment] class as the initial/main application fragment for the specified [targets].
 *
 * Multiple `@MainAppFragment` classes may exist in a project for different [AppTarget] scopes;
 * the Gradle plugin resolves the most specific `@MainAppFragment` per enabled backend target
 * and fails the build if more than one `@MainAppFragment` ties at the highest specificity rank.
 * On Android, the plugin also generates a 1:1 `AppCompatActivity` subclass with the `MAIN`/`LAUNCHER`
 * intent filter for the resolved Android `@MainAppFragment`.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class MainAppFragment(
    val targets: Array<AppTarget> = [AppTarget.COMMON],
    val label: String = "",
    val route: String = "",
    val exported: Boolean = true,
    val screenOrientation: String = ""
)
