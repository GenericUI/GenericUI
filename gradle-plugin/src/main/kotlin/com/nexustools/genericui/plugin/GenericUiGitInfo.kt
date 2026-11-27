package com.nexustools.genericui.plugin

/**
 * Discovered Git repository version metadata for a GenericUI consumer project.
 */
data class GenericUiGitInfo(
    val shortHash: String? = null,
    val latestTag: String? = null,
    val normalizedTagVersion: String? = null
)
