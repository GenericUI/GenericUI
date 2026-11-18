package com.nexustools.genericui.plugin

import java.io.File
import java.io.IOException
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

internal object GenericUiSymlinkPreservingCopy {

    fun isBinaryFileName(fileName: String): Boolean {
        val lower = fileName.lowercase()
        val ext = lower.substringAfterLast('.', "")
        return !lower.contains('.') ||
            ext == "so" ||
            ext == "dylib" ||
            ext == "kexe" ||
            lower.contains(".so.")
    }

    fun deletePathNoFollow(path: Path) {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(path)) {
            return
        }
        if (Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            Files.deleteIfExists(path)
            return
        }
        Files.newDirectoryStream(path).use { stream ->
            for (child in stream) {
                deletePathNoFollow(child)
            }
        }
        Files.deleteIfExists(path)
    }

    fun createOrUpdateSymlinkOrFallback(
        dstLink: File,
        targetPath: Path,
        fallbackCopyAction: (() -> Unit)? = null
    ) {
        dstLink.parentFile?.mkdirs()
        val linkPath = dstLink.toPath()
        try {
            if (Files.isSymbolicLink(linkPath) && Files.readSymbolicLink(linkPath) == targetPath) {
                return
            }
            deletePathNoFollow(linkPath)
            Files.createSymbolicLink(linkPath, targetPath)
        } catch (ex: UnsupportedOperationException) {
            fallbackCopyAction?.invoke()
        } catch (ex: FileSystemException) {
            fallbackCopyAction?.invoke()
        } catch (ex: SecurityException) {
            fallbackCopyAction?.invoke()
        }
    }

    fun copyRegularFileNoFollowDst(
        src: File,
        dst: File,
        setExecutableOnBinaries: Boolean = false
    ) {
        val srcPath = src.toPath().toAbsolutePath().normalize()
        val dstPath = dst.toPath().toAbsolutePath().normalize()
        if (srcPath == dstPath) {
            return
        }
        dst.parentFile?.mkdirs()
        if (Files.isSymbolicLink(dstPath) || Files.isDirectory(dstPath, LinkOption.NOFOLLOW_LINKS)) {
            deletePathNoFollow(dstPath)
        }
        try {
            Files.copy(
                srcPath,
                dstPath,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.COPY_ATTRIBUTES
            )
        } catch (_: UnsupportedOperationException) {
            Files.copy(srcPath, dstPath, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: IOException) {
            Files.copy(srcPath, dstPath, StandardCopyOption.REPLACE_EXISTING)
        }
        if (setExecutableOnBinaries && isBinaryFileName(dst.name) && !Files.isSymbolicLink(dstPath)) {
            dst.setExecutable(true, false)
        }
    }

    fun copyEntryPreservingSymlinks(
        src: File,
        dst: File,
        sourceRoot: File = src.parentFile ?: src,
        setExecutableOnBinaries: Boolean = false,
        entryFilter: (File) -> Boolean = { true }
    ) {
        val srcPath = src.toPath()
        if (!Files.exists(srcPath, LinkOption.NOFOLLOW_LINKS) || !entryFilter(src)) {
            return
        }

        val sourceRootNorm = sourceRoot.toPath().toAbsolutePath().normalize()
        if (Files.isSymbolicLink(srcPath)) {
            val rawTarget = Files.readSymbolicLink(srcPath)
            val srcParentAbs = srcPath.toAbsolutePath().parent ?: sourceRootNorm
            val resolvedFromSrc = srcParentAbs.resolve(rawTarget).normalize()
            if (!rawTarget.isAbsolute && resolvedFromSrc.startsWith(sourceRootNorm)) {
                createOrUpdateSymlinkOrFallback(dst, rawTarget) {
                    copyResolvedTargetIfRegular(srcPath, dst, setExecutableOnBinaries)
                }
                return
            }
            if (rawTarget.isAbsolute && resolvedFromSrc.startsWith(sourceRootNorm)) {
                val relTarget = srcParentAbs.relativize(resolvedFromSrc)
                createOrUpdateSymlinkOrFallback(dst, relTarget) {
                    copyResolvedTargetIfRegular(srcPath, dst, setExecutableOnBinaries)
                }
                return
            }

            // External symlink pointing outside sourceRootNorm (e.g. Homebrew Cellar links):
            // materialize a single canonical binary and symlink sibling aliases to it.
            val realTarget = runCatching { srcPath.toRealPath() }.getOrNull() ?: return
            val siblingMatches = findMatchingExternalAliases(src.parentFile, realTarget, entryFilter)
            val canonicalName = selectCanonicalAliasName(
                candidates = if (src.name in siblingMatches) siblingMatches else siblingMatches + src.name,
                realTargetFileName = realTarget.fileName?.toString()
            )
            if (canonicalName != null && src.name != canonicalName) {
                createOrUpdateSymlinkOrFallback(dst, Paths.get(canonicalName)) {
                    copyResolvedTargetIfRegular(realTarget, dst, setExecutableOnBinaries)
                }
            } else if (Files.isDirectory(realTarget)) {
                copyDirectoryContentsPreservingSymlinks(
                    sourceDir = realTarget.toFile(),
                    targetDir = dst,
                    sourceRoot = realTarget.toFile(),
                    setExecutableOnBinaries = setExecutableOnBinaries,
                    entryFilter = entryFilter
                )
            } else if (Files.isRegularFile(realTarget)) {
                copyRegularFileNoFollowDst(
                    src = realTarget.toFile(),
                    dst = dst,
                    setExecutableOnBinaries = setExecutableOnBinaries
                )
            }
            return
        }

        if (Files.isDirectory(srcPath, LinkOption.NOFOLLOW_LINKS)) {
            copyDirectoryContentsPreservingSymlinks(
                sourceDir = src,
                targetDir = dst,
                sourceRoot = sourceRoot,
                setExecutableOnBinaries = setExecutableOnBinaries,
                entryFilter = entryFilter
            )
            return
        }

        if (Files.isRegularFile(srcPath, LinkOption.NOFOLLOW_LINKS)) {
            copyRegularFileNoFollowDst(
                src = src,
                dst = dst,
                setExecutableOnBinaries = setExecutableOnBinaries
            )
        }
    }

    fun copyDirectoryContentsPreservingSymlinks(
        sourceDir: File,
        targetDir: File,
        sourceRoot: File = sourceDir,
        setExecutableOnBinaries: Boolean = false,
        entryFilter: (File) -> Boolean = { true }
    ) {
        val sourcePath = sourceDir.toPath()
        if (!Files.isDirectory(sourcePath, LinkOption.NOFOLLOW_LINKS)) {
            return
        }
        val targetPath = targetDir.toPath()
        if (Files.isSymbolicLink(targetPath) ||
            (Files.exists(targetPath, LinkOption.NOFOLLOW_LINKS) && !Files.isDirectory(targetPath, LinkOption.NOFOLLOW_LINKS))
        ) {
            deletePathNoFollow(targetPath)
        }
        Files.createDirectories(targetPath)

        val children = Files.newDirectoryStream(sourcePath).use { stream ->
            stream.toList().sortedWith(
                compareBy<Path> { if (Files.isSymbolicLink(it)) 1 else 0 }
                    .thenBy { it.fileName.toString() }
            )
        }
        for (childPath in children) {
            val childFile = childPath.toFile()
            if (!entryFilter(childFile)) continue
            copyEntryPreservingSymlinks(
                src = childFile,
                dst = File(targetDir, childFile.name),
                sourceRoot = sourceRoot,
                setExecutableOnBinaries = setExecutableOnBinaries,
                entryFilter = entryFilter
            )
        }
    }

    fun walkPathsNoFollow(
        rootDir: File,
        bottomUp: Boolean = false,
        onEntry: (Path) -> Unit
    ) {
        val rootPath = rootDir.toPath()
        if (!Files.exists(rootPath, LinkOption.NOFOLLOW_LINKS)) {
            return
        }
        fun visit(path: Path) {
            val isRealDir = Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
            if (!bottomUp) {
                onEntry(path)
            }
            if (isRealDir) {
                runCatching {
                    Files.newDirectoryStream(path).use { stream ->
                        for (child in stream) {
                            visit(child)
                        }
                    }
                }
            }
            if (bottomUp) {
                onEntry(path)
            }
        }
        visit(rootPath)
    }

    fun collectMacCodeSignTargets(frameworksDir: File): Pair<List<File>, List<File>> {
        val regularBinaries = mutableListOf<File>()
        val frameworkBundles = mutableListOf<File>()
        val rootPath = frameworksDir.toPath().toAbsolutePath().normalize()
        walkPathsNoFollow(frameworksDir, bottomUp = false) { path ->
            if (Files.isSymbolicLink(path)) return@walkPathsNoFollow
            val name = path.fileName?.toString().orEmpty().lowercase()
            if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                if (name.endsWith(".dylib") || name.endsWith(".so")) {
                    regularBinaries.add(path.toFile())
                }
            } else if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                if (path.toAbsolutePath().normalize() != rootPath && name.endsWith(".framework")) {
                    frameworkBundles.add(path.toFile())
                }
            }
        }
        val sortedBinaries = regularBinaries.sortedByDescending { it.toPath().nameCount }
        val sortedFrameworks = frameworkBundles.sortedByDescending { it.toPath().nameCount }
        return sortedBinaries to sortedFrameworks
    }

    private fun copyResolvedTargetIfRegular(
        srcOrReal: Path,
        dst: File,
        setExecutableOnBinaries: Boolean
    ) {
        val real = runCatching { srcOrReal.toRealPath() }.getOrNull() ?: return
        if (Files.isRegularFile(real)) {
            copyRegularFileNoFollowDst(real.toFile(), dst, setExecutableOnBinaries)
        }
    }

    private fun findMatchingExternalAliases(
        parentDir: File?,
        realTarget: Path,
        entryFilter: (File) -> Boolean
    ): List<String> {
        val parentPath = parentDir?.toPath() ?: return emptyList()
        if (!Files.isDirectory(parentPath, LinkOption.NOFOLLOW_LINKS)) return emptyList()
        val result = mutableListOf<String>()
        Files.newDirectoryStream(parentPath).use { stream ->
            for (sibling in stream) {
                val sibFile = sibling.toFile()
                if (!entryFilter(sibFile)) continue
                val sibReal = runCatching { sibling.toRealPath() }.getOrNull()
                if (sibReal == realTarget) {
                    result.add(sibFile.name)
                }
            }
        }
        return result
    }

    private fun selectCanonicalAliasName(
        candidates: List<String>,
        realTargetFileName: String?
    ): String? {
        return candidates.minWithOrNull(
            compareBy<String>(
                { if (realTargetFileName != null && it == realTargetFileName) 0 else 1 },
                { -it.length },
                { it }
            )
        )
    }
}
