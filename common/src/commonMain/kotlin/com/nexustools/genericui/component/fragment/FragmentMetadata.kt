package com.nexustools.genericui.component.fragment

/**
 * Configures a top-level fragment class as the primary entry screen of a GenericUI application.
 *
 * Main fragments open inside the primary application window at launch. Set [multipleWindows]
 * to `true` only if the application explicitly supports spawning additional standalone windows
 * showing another instance of the main fragment. Main fragments cannot be marked `dialog = true`.
 */
data class FragmentMetadata(
    val fragmentId: String,
    val route: String = "",
    val label: String = "",
    val isMainFragment: Boolean = false,
    val multipleWindows: Boolean = false,
    val dialog: Boolean = false
)
