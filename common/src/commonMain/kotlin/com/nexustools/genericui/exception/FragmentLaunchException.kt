package com.nexustools.genericui.exception

/**
 * Base exception for fragment registry, metadata, and launch errors.
 */
open class FragmentLaunchException(
    val fragmentId: String,
    message: String,
    cause: Throwable? = null
) : GenericUiException(message, cause)
