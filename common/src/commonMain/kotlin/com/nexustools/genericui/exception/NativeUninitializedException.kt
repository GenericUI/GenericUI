package com.nexustools.genericui.exception

/**
 * Thrown when an operation requiring an initialized native implementation or active factory
 * is invoked before initialization.
 */
open class NativeUninitializedException(
    val targetName: String,
    val operationName: String? = null,
    message: String = buildMessage(targetName, operationName),
    cause: Throwable? = null
) : GenericUiException(message, cause) {
    companion object {
        fun buildMessage(targetName: String, operationName: String?): String =
            if (operationName.isNullOrBlank()) {
                "$targetName native implementation is not initialized. Call GenericUI.initialize(factory) and show/realize the component first."
            } else {
                "$targetName.$operationName requires an initialized native implementation / GenericUI.activeFactory."
            }
    }
}
