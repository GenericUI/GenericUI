package com.nexustools.genericui.exception

/**
 * Thrown when attempting to open an additional window for a fragment with multipleWindows = false.
 */
open class FragmentMultipleWindowsUnsupportedException(
    fragmentId: String,
    message: String = "Fragment '$fragmentId' does not support opening multiple concurrent windows (multipleWindows = false).",
    cause: Throwable? = null
) : FragmentLaunchException(fragmentId, message, cause)
