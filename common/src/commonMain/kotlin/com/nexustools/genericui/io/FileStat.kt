package com.nexustools.genericui.io

/**
 * Metadata describing a file or directory returned by [FileSystem.stat].
 */
data class FileStat(
    val path: String,
    val name: String = path.substringAfterLast('/').substringAfterLast('\\'),
    val size: Long = 0L,
    val isFile: Boolean = true,
    val isDirectory: Boolean = false,
    val isSymbolicLink: Boolean = false,
    val lastModifiedEpochMs: Long = 0L,
    val createdEpochMs: Long = 0L,
    val isReadable: Boolean = true,
    val isWritable: Boolean = true
)
