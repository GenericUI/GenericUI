package com.nexustools.genericui.plugin

/**
 * Android `AndroidManifest.xml` & SDK configuration block (`genericui { android { manifest { ... } } }`).
 */
open class GenericUiAndroidManifestConfig {
    val permissions: MutableList<String> = mutableListOf()
    var screenOrientation: String = "unspecified"
    var configChanges: String = "orientation|screenSize|screenLayout|keyboardHidden|uiMode|density"
    var supportsPictureInPicture: Boolean = false
    var applicationClass: String = ""
    var theme: String = "@style/Theme.AppCompat.DayNight.NoActionBar"
    var minSdk: Int = 24
    var compileSdk: Int = 35
    var targetSdk: Int = 35

    /** Adds one or more Android manifest permissions (e.g. `"android.permission.INTERNET"`). */
    fun permissions(vararg perms: String) {
        for (perm in perms) {
            permission(perm)
        }
    }

    /** Adds a single Android manifest permission. */
    fun permission(perm: String) {
        val trimmed = perm.trim()
        if (trimmed.isNotEmpty() && trimmed !in permissions) {
            permissions.add(trimmed)
        }
    }
}
