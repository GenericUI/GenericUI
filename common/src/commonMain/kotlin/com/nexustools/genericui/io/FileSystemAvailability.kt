package com.nexustools.genericui.io

/**
 * Availability of [IFileSystem] operations on the active target platform:
 * - [Always]: File system operations are always available without interactive user prompts (e.g., Desktop, `UserDirectory`).
 * - [WithPermission]: File system operations require user permission or a file/directory picker grant (e.g., Android SAF, iOS document picker, Web File System Access API).
 * - [Unavailable]: Raw file system path operations are unavailable in the current environment.
 */
enum class FileSystemAvailability {
    Always,
    WithPermission,
    Unavailable;

    companion object {
        val ALWAYS: FileSystemAvailability = Always
        val WITH_PERMISSION: FileSystemAvailability = WithPermission
        val UNAVAILABLE: FileSystemAvailability = Unavailable
    }
}
