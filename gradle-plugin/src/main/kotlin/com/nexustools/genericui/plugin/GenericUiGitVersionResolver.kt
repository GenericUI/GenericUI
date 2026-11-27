package com.nexustools.genericui.plugin

import org.gradle.api.Project
import org.gradle.api.invocation.Gradle
import java.io.File
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Resolves Git commit short hash and latest reachable Git tag version for consumer projects
 * without cross-build daemon staleness (caches strictly per [Gradle] build invocation).
 */
object GenericUiGitVersionResolver {

    private val HASH_REGEX = Regex("^[0-9a-fA-F]{4,40}$")
    private val NUMERIC_DOTTED_REGEX = Regex("^\\d+(?:\\.\\d+){0,3}$")
    private val cacheByGradle = Collections.synchronizedMap(
        WeakHashMap<Gradle, ConcurrentHashMap<String, GenericUiGitInfo>>()
    )

    fun resolveForProject(project: Project): GenericUiGitInfo {
        val dirKey = runCatching { project.projectDir.canonicalPath }.getOrElse { project.projectDir.absolutePath }
        val gradle = runCatching { project.gradle }.getOrNull() ?: return resolveFromDirectory(project.projectDir)
        val perBuildCache = synchronized(cacheByGradle) {
            cacheByGradle.getOrPut(gradle) { ConcurrentHashMap() }
        }
        return perBuildCache.getOrPut(dirKey) {
            resolveFromDirectory(project.projectDir)
        }
    }

    fun resolveFromDirectory(projectDir: File): GenericUiGitInfo {
        if (!projectDir.isDirectory) return GenericUiGitInfo()
        val topLevel = runGitCommand(projectDir, "rev-parse", "--show-toplevel") ?: return GenericUiGitInfo()
        if (topLevel.isBlank()) return GenericUiGitInfo()

        val rawHash = runGitCommand(projectDir, "rev-parse", "--short", "HEAD")
            ?.trim()
            ?.takeIf { HASH_REGEX.matches(it) }

        val rawTag = runGitCommand(
            projectDir,
            "describe",
            "--tags",
            "--abbrev=0",
            "--match",
            "v[0-9]*",
            "--match",
            "V[0-9]*",
            "--match",
            "[0-9]*"
        )
            ?: runGitCommand(projectDir, "describe", "--tags", "--abbrev=0")
            ?: runGitCommand(projectDir, "tag", "--list", "--sort=-v:refname")
                ?.lineSequence()
                ?.map { it.trim() }
                ?.firstOrNull { it.isNotEmpty() }

        val normalizedTag = normalizeTagVersion(rawTag)
        return GenericUiGitInfo(
            shortHash = rawHash,
            latestTag = rawTag?.trim()?.takeIf { it.isNotEmpty() },
            normalizedTagVersion = normalizedTag
        )
    }

    /**
     * Normalizes a Git tag such as `"v1.0.0-beta.1"` or `"V2.3.4"` by stripping a single leading
     * `v`/`V` prefix when followed by a digit.
     */
    fun normalizeTagVersion(rawTag: String?): String? {
        val trimmed = rawTag?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return if (trimmed.length >= 2 &&
            (trimmed[0] == 'v' || trimmed[0] == 'V') &&
            trimmed[1].isDigit()
        ) {
            trimmed.substring(1)
        } else {
            trimmed
        }
    }

    /**
     * Appends `+$gitHash` build metadata when [gitHash] is non-blank and not already embedded in [version].
     */
    fun formatVersionWithGitHash(version: String, gitHash: String?): String {
        val cleanVersion = version.trim().ifEmpty { "1.0.0" }
        val cleanHash = gitHash?.trim()?.takeIf { it.isNotEmpty() } ?: return cleanVersion
        if (cleanVersion.contains(cleanHash, ignoreCase = true)) {
            return cleanVersion
        }
        return "$cleanVersion+$cleanHash"
    }

    /**
     * Resolves macOS `CFBundleVersion` (shown inside parentheses by About panel as
     * `Version $CFBundleShortVersionString ($CFBundleVersion)`), preferring explicit [buildVersion],
     * then [gitHash], then [version].
     */
    fun resolveMacosBundleVersion(version: String, buildVersion: String, gitHash: String?): String {
        val cleanBuild = buildVersion.trim()
        if (cleanBuild.isNotEmpty()) return cleanBuild
        val cleanHash = gitHash?.trim().orEmpty()
        if (cleanHash.isNotEmpty()) return cleanHash
        return version.trim().ifEmpty { "1.0.0" }
    }

    /**
     * Resolves numeric-safe Apple mobile (iOS/iPadOS) `CFBundleVersion` / `CURRENT_PROJECT_VERSION`
     * suitable for Xcode & App Store Connect validation.
     */
    fun resolveNumericAppleBuildVersion(version: String, buildVersion: String): String {
        val cleanBuild = buildVersion.trim()
        if (cleanBuild.isNotEmpty() && NUMERIC_DOTTED_REGEX.matches(cleanBuild)) {
            return cleanBuild
        }
        val numericPrefix = version.trim()
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore('-')
            .substringBefore('+')
            .trim()
        return if (numericPrefix.isNotEmpty() && NUMERIC_DOTTED_REGEX.matches(numericPrefix)) {
            numericPrefix
        } else {
            "1.0.0"
        }
    }

    private fun runGitCommand(workDir: File, vararg args: String): String? {
        return runCatching {
            val process = ProcessBuilder(listOf("git") + args.toList())
                .directory(workDir)
                .redirectErrorStream(false)
                .start()
            val finished = process.waitFor(3, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return null
            }
            if (process.exitValue() != 0) {
                return null
            }
            process.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                .trim()
                .takeIf { it.isNotEmpty() }
        }.getOrNull()
    }
}
