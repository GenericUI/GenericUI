package com.nexustools.genericui.io

/**
 * Result of checking or requesting OS file / directory access permission via [FileSystem].
 */
enum class FilePermissionStatus {
    GRANTED,
    DENIED,
    PROMPT_REQUIRED,
    UNSUPPORTED
}
