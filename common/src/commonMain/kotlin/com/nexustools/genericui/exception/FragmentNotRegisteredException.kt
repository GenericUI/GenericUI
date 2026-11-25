package com.nexustools.genericui.exception

/**
 * Thrown when attempting to resolve or instantiate a fragment that is not registered in FragmentRegistry.
 */
open class FragmentNotRegisteredException(
    fragmentId: String,
    message: String = "Fragment '$fragmentId' is not registered in FragmentRegistry and cannot be instantiated.",
    cause: Throwable? = null
) : FragmentLaunchException(fragmentId, message, cause)
