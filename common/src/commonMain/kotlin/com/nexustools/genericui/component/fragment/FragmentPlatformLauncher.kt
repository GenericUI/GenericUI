package com.nexustools.genericui.component.fragment

import com.nexustools.genericui.window.IWindow
import kotlin.reflect.KClass

/**
 * Backend hook installed into [FragmentLaunchCoordinator.platformLauncher] that lets a platform backend
 * (e.g. `:mobile:android` hosting fragments 1:1 as Android `Activity` instances) intercept fragment launches
 * before the default in-process window/container handling runs.
 */
fun interface FragmentPlatformLauncher {
    /**
     * Attempts to launch [fragmentClass] natively (e.g. via `startActivity`).
     *
     * @param fragmentClass the [IFragment] implementation being launched.
     * @param launchArgs the launch arguments to deliver to the new fragment's `onCreate`.
     * @param openAdditionalWindow `true` when opening [fragmentClass] in a separate top-level window.
     * @param clearTask `true` when clearing the navigation stack before showing [fragmentClass].
     * @param isDialog `true` when [fragmentClass] metadata marks it as a dialog fragment (`dialog = true`).
     * @param sourceWindow the window the launch originated from, if known.
     * @return `true` if the platform fully handled the launch (the coordinator then performs no
     *   in-process mounting), `false` to fall back to the default [FragmentLaunchCoordinator] behavior.
     */
    fun launch(
        fragmentClass: KClass<out IFragment>,
        launchArgs: FragmentBundle?,
        openAdditionalWindow: Boolean,
        clearTask: Boolean,
        isDialog: Boolean,
        sourceWindow: IWindow?
    ): Boolean
}
