package com.nexustools.genericui.plugin

/**
 * [AndroidActivityExtensionContributor] for `:extensions:ui:pip` that wires
 * `AndroidPipActivityDelegate` lifecycle hooks, Picture-in-Picture helper methods,
 * `android:supportsPictureInPicture="true"`, and `smallestScreenSize` config-change flags onto
 * `@PiPCompatible`-annotated `@MainAppFragment` and `@AppFragment` Android activities.
 */
object PipAndroidActivityExtensionContributor : AndroidActivityExtensionContributor {

    override val extensionModulePath: String = ":extensions:ui:pip"

    override fun appliesTo(
        fragment: GenericUiDiscoveredFragment,
        enabledExtensionPaths: Set<String>
    ): Boolean = !fragment.isApplicationClass &&
        extensionModulePath in enabledExtensionPaths &&
        fragment.hasAnnotation("PiPCompatible")

    override fun contribute(fragment: GenericUiDiscoveredFragment): AndroidActivityExtensionContribution =
        AndroidActivityExtensionContribution(
            imports = listOf(
                "android.content.res.Configuration",
                "android.os.Bundle",
                "com.nexustools.genericui.ext.pip.android.AndroidPipActivityDelegate"
            ),
            properties = listOf(
                "val pipActivityDelegate: AndroidPipActivityDelegate = AndroidPipActivityDelegate(this)",
                "val isPipSupportedOnDevice: Boolean get() = pipActivityDelegate.isPipSupportedOnDevice",
                "val isCurrentlyInPipMode: Boolean get() = pipActivityDelegate.isCurrentlyInPipMode"
            ),
            onCreateAfterSuperStatements = listOf(
                "pipActivityDelegate.onActivityCreated(savedInstanceState)"
            ),
            onDestroyBeforeSuperStatements = listOf(
                "pipActivityDelegate.onActivityDestroyed()"
            ),
            methods = listOf(
                "open fun enterFragmentPictureInPicture(): Boolean = pipActivityDelegate.enterFragmentPictureInPicture()",
                """
                open fun updatePictureInPictureParams(playing: Boolean = false, width: Int = 16, height: Int = 9) {
                    pipActivityDelegate.updatePictureInPictureParams(playing = playing, aspectWidth = width, aspectHeight = height)
                }
                """.trimIndent(),
                """
                override fun onPause() {
                    pipActivityDelegate.onActivityPaused()
                    super.onPause()
                }
                """.trimIndent(),
                """
                override fun onStop() {
                    pipActivityDelegate.onActivityStopped()
                    super.onStop()
                }
                """.trimIndent(),
                """
                override fun onUserLeaveHint() {
                    super.onUserLeaveHint()
                    pipActivityDelegate.onUserLeaveHint()
                }
                """.trimIndent(),
                """
                override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
                    super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
                    pipActivityDelegate.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
                }
                """.trimIndent()
            ),
            supportsPictureInPictureManifest = true,
            requiredConfigChanges = listOf("orientation", "screenSize", "smallestScreenSize", "screenLayout")
        )
}
