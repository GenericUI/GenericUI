package com.nexustools.genericui.plugin.nativebuild

import org.gradle.api.GradleException
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Duration
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.ZipFile

/**
 * Shared HTTP downloader, SHA-256 verifier, and pure-JVM `.zip` / `.tar.gz` / `.tgz` / `.tar`
 * archive extractor used by [GenericUiFetchNativeSourceArchiveTask] and [GenericUiJniToolchainResolver].
 */
object GenericUiArchiveExtractor {

    private data class DeferredLink(
        val linkFile: File,
        val targetPath: String,
        val isHardLink: Boolean
    )

    private val httpClient: HttpClient by lazy {
        HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(30))
            .build()
    }

    /** Computes lowercase hexadecimal SHA-256 digest of [bytes]. */
    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    /** Computes lowercase hexadecimal SHA-256 digest of [file]. */
    fun sha256Hex(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(64 * 1024)
        FileInputStream(file).buffered().use { input ->
            while (true) {
                val read = input.read(buf)
                if (read < 0) break
                if (read > 0) md.update(buf, 0, read)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Downloads [url] (`http://`, `https://`, or `file://`) into [destFile], strictly verifying
     * [expectedSha256] when non-blank.
     */
    fun downloadFile(url: String, destFile: File, expectedSha256: String? = null) {
        val trimmedUrl = url.trim()
        require(trimmedUrl.isNotEmpty()) { "Archive download URL must not be blank." }
        destFile.parentFile?.mkdirs()
        val tempFile = File(destFile.parentFile, "${destFile.name}.part.${ProcessHandle.current().pid()}")
        try {
            val uri = URI.create(trimmedUrl)
            if (uri.scheme.equals("file", ignoreCase = true)) {
                val srcFile = File(uri)
                if (!srcFile.isFile) {
                    throw GradleException("Local archive file does not exist: ${srcFile.absolutePath}")
                }
                srcFile.inputStream().buffered().use { input ->
                    FileOutputStream(tempFile).buffered().use { output ->
                        input.copyTo(output)
                    }
                }
            } else {
                val request = HttpRequest.newBuilder(uri)
                    .GET()
                    .timeout(Duration.ofMinutes(10))
                    .header("User-Agent", "GenericUI-GradlePlugin/1.0")
                    .build()
                val response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream())
                if (response.statusCode() !in 200..299) {
                    response.body().close()
                    throw GradleException(
                        "Failed to download '$trimmedUrl': HTTP ${response.statusCode()}"
                    )
                }
                response.body().use { input ->
                    FileOutputStream(tempFile).buffered().use { output ->
                        input.copyTo(output)
                    }
                }
            }

            val expected = expectedSha256?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }
            if (expected != null) {
                val actual = sha256Hex(tempFile)
                if (actual != expected) {
                    throw GradleException(
                        "SHA-256 verification failed for '$trimmedUrl': expected $expected, got $actual"
                    )
                }
            }

            Files.move(
                tempFile.toPath(),
                destFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    /**
     * Extracts [archiveFile] (`.zip`, `.jar`, `.tar.gz`, `.tgz`, or `.tar`) into [destDir],
     * optionally stripping [stripComponents] leading directory segments.
     */
    fun extractArchive(archiveFile: File, destDir: File, stripComponents: Int = 0) {
        if (!archiveFile.isFile) {
            throw GradleException("Archive file does not exist: ${archiveFile.absolutePath}")
        }
        destDir.mkdirs()
        val lower = archiveFile.name.lowercase(Locale.ROOT)
        when {
            lower.endsWith(".zip") || lower.endsWith(".jar") ->
                extractZip(archiveFile, destDir, stripComponents)
            lower.endsWith(".tar.gz") || lower.endsWith(".tgz") ->
                BufferedInputStream(GZIPInputStream(FileInputStream(archiveFile))).use { tarIn ->
                    extractTarStream(tarIn, destDir, stripComponents)
                }
            lower.endsWith(".tar") ->
                BufferedInputStream(FileInputStream(archiveFile)).use { tarIn ->
                    extractTarStream(tarIn, destDir, stripComponents)
                }
            else -> throw GradleException(
                "Unsupported archive format '${archiveFile.name}'. Supported formats: .zip, .tar.gz, .tgz, .tar"
            )
        }
    }

    private fun extractZip(archiveFile: File, destDir: File, stripComponents: Int) {
        val canonicalRoot = destDir.canonicalFile
        ZipFile(archiveFile).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val stripped = stripPathComponents(entry.name, stripComponents) ?: continue
                val targetFile = resolveSafeChild(canonicalRoot, stripped)
                if (entry.isDirectory) {
                    targetFile.mkdirs()
                } else {
                    targetFile.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        FileOutputStream(targetFile).buffered().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        }
    }

    private fun extractTarStream(input: InputStream, destDir: File, stripComponents: Int) {
        val canonicalRoot = destDir.canonicalFile
        val header = ByteArray(512)
        var nextLongName: String? = null
        var nextLongLink: String? = null
        var paxPerEntry: Map<String, String> = emptyMap()
        var paxGlobal: Map<String, String> = emptyMap()
        val deferredLinks = mutableListOf<DeferredLink>()

        while (true) {
            val readCount = readFullyOrEof(input, header)
            if (readCount == 0) break
            if (readCount < 512) {
                throw EOFException("Truncated TAR header block")
            }
            if (isAllZero(header)) {
                break
            }

            val rawName = readTarString(header, 0, 100)
            val prefix = if (readTarString(header, 257, 6).startsWith("ustar")) {
                readTarString(header, 345, 155)
            } else {
                ""
            }
            val ustarName = if (prefix.isNotEmpty() && rawName.isNotEmpty()) "$prefix/$rawName" else rawName
            val mode = parseOctalOrZero(header, 100, 8)
            val rawSize = parseTarSize(header, 124, 12)
            val typeFlag = header[156].toInt().toChar()
            val rawLinkName = readTarString(header, 157, 100)

            val mergedPax = if (paxGlobal.isEmpty() && paxPerEntry.isEmpty()) {
                emptyMap()
            } else {
                LinkedHashMap<String, String>().apply {
                    putAll(paxGlobal)
                    putAll(paxPerEntry)
                }
            }

            val entryName = mergedPax["path"] ?: nextLongName ?: ustarName
            val linkTarget = mergedPax["linkpath"] ?: nextLongLink ?: rawLinkName
            val size = mergedPax["size"]?.toLongOrNull() ?: rawSize

            nextLongName = null
            nextLongLink = null
            paxPerEntry = emptyMap()

            when (typeFlag) {
                'L' -> {
                    val payload = readExactBytes(input, size.toInt())
                    skipTarPadding(input, size)
                    nextLongName = payload.toString(StandardCharsets.UTF_8).trimEnd('\u0000')
                    continue
                }
                'K' -> {
                    val payload = readExactBytes(input, size.toInt())
                    skipTarPadding(input, size)
                    nextLongLink = payload.toString(StandardCharsets.UTF_8).trimEnd('\u0000')
                    continue
                }
                'x' -> {
                    val payload = readExactBytes(input, size.toInt())
                    skipTarPadding(input, size)
                    paxPerEntry = parsePaxRecords(payload)
                    continue
                }
                'g' -> {
                    val payload = readExactBytes(input, size.toInt())
                    skipTarPadding(input, size)
                    paxGlobal = parsePaxRecords(payload)
                    continue
                }
            }

            val stripped = stripPathComponents(entryName, stripComponents)
            if (stripped == null) {
                skipExactly(input, size)
                skipTarPadding(input, size)
                continue
            }

            val targetFile = resolveSafeChild(canonicalRoot, stripped)
            when (typeFlag) {
                '5' -> {
                    targetFile.mkdirs()
                    skipExactly(input, size)
                    skipTarPadding(input, size)
                }
                '2' -> {
                    skipExactly(input, size)
                    skipTarPadding(input, size)
                    targetFile.parentFile?.mkdirs()
                    val created = runCatching {
                        if (Files.exists(targetFile.toPath()) || Files.isSymbolicLink(targetFile.toPath())) {
                            Files.deleteIfExists(targetFile.toPath())
                        }
                        Files.createSymbolicLink(targetFile.toPath(), Path.of(linkTarget))
                        true
                    }.getOrDefault(false)
                    if (!created) {
                        deferredLinks.add(DeferredLink(targetFile, linkTarget, isHardLink = false))
                    }
                }
                '1' -> {
                    skipExactly(input, size)
                    skipTarPadding(input, size)
                    deferredLinks.add(DeferredLink(targetFile, linkTarget, isHardLink = true))
                }
                '0', '\u0000' -> {
                    targetFile.parentFile?.mkdirs()
                    FileOutputStream(targetFile).buffered().use { output ->
                        copyExact(input, output, size)
                    }
                    skipTarPadding(input, size)
                    if ((mode and 0b001001001L) != 0L) {
                        targetFile.setExecutable(true, false)
                    }
                }
                else -> {
                    skipExactly(input, size)
                    skipTarPadding(input, size)
                }
            }
        }

        // Materialize deferred symlinks/hardlinks as copies when OS symlinks are disallowed
        for (deferred in deferredLinks) {
            val baseDir = if (deferred.isHardLink) {
                canonicalRoot
            } else {
                deferred.linkFile.parentFile ?: canonicalRoot
            }
            val cleanedTarget = if (deferred.isHardLink) {
                stripPathComponents(deferred.targetPath, stripComponents) ?: continue
            } else {
                deferred.targetPath
            }
            val resolvedTarget = File(baseDir, cleanedTarget).canonicalFile
            val rootPrefix = canonicalRoot.path + File.separator
            if (resolvedTarget.path != canonicalRoot.path && !resolvedTarget.path.startsWith(rootPrefix)) {
                continue
            }
            if (resolvedTarget.isFile) {
                deferred.linkFile.parentFile?.mkdirs()
                resolvedTarget.copyTo(deferred.linkFile, overwrite = true)
            } else if (resolvedTarget.isDirectory) {
                resolvedTarget.copyRecursively(deferred.linkFile, overwrite = true)
            }
        }
    }

    private fun stripPathComponents(rawPath: String, stripComponents: Int): String? {
        val parts = rawPath.replace('\\', '/')
            .split('/')
            .filter { it.isNotEmpty() && it != "." }
        if (parts.any { it == ".." }) {
            throw GradleException("Refusing archive entry with parent directory traversal: '$rawPath'")
        }
        if (parts.size <= stripComponents) {
            return null
        }
        return parts.drop(stripComponents).joinToString("/")
    }

    private fun resolveSafeChild(canonicalRoot: File, relPath: String): File {
        val candidate = File(canonicalRoot, relPath)
        val canonicalCandidate = candidate.canonicalFile
        val rootPrefix = canonicalRoot.path + File.separator
        if (canonicalCandidate.path != canonicalRoot.path && !canonicalCandidate.path.startsWith(rootPrefix)) {
            throw GradleException("Archive entry escapes destination directory: '$relPath'")
        }
        return candidate
    }

    private fun parsePaxRecords(bytes: ByteArray): Map<String, String> {
        val text = bytes.toString(StandardCharsets.UTF_8)
        val result = linkedMapOf<String, String>()
        var idx = 0
        while (idx < text.length) {
            val spaceIdx = text.indexOf(' ', idx)
            if (spaceIdx <= idx) break
            val recLen = text.substring(idx, spaceIdx).toIntOrNull() ?: break
            val recEnd = (idx + recLen).coerceAtMost(text.length)
            val body = text.substring(spaceIdx + 1, recEnd).trimEnd('\n')
            val eqIdx = body.indexOf('=')
            if (eqIdx > 0) {
                result[body.substring(0, eqIdx)] = body.substring(eqIdx + 1)
            }
            idx = recEnd
        }
        return result
    }

    private fun isAllZero(block: ByteArray): Boolean {
        for (b in block) {
            if (b != 0.toByte()) return false
        }
        return true
    }

    private fun readTarString(buf: ByteArray, offset: Int, maxLen: Int): String {
        var len = 0
        while (len < maxLen && buf[offset + len] != 0.toByte()) {
            len++
        }
        return String(buf, offset, len, StandardCharsets.UTF_8)
    }

    private fun parseOctalOrZero(buf: ByteArray, offset: Int, len: Int): Long {
        val raw = readTarString(buf, offset, len).trim()
        if (raw.isEmpty()) return 0L
        return raw.toLongOrNull(8) ?: 0L
    }

    private fun parseTarSize(buf: ByteArray, offset: Int, len: Int): Long {
        val first = buf[offset].toInt() and 0xFF
        if ((first and 0x80) != 0) {
            var value = (first and 0x7F).toLong()
            for (i in 1 until len) {
                value = (value shl 8) or (buf[offset + i].toInt() and 0xFF).toLong()
            }
            return value
        }
        return parseOctalOrZero(buf, offset, len)
    }

    private fun readFullyOrEof(input: InputStream, buf: ByteArray): Int {
        var total = 0
        while (total < buf.size) {
            val read = input.read(buf, total, buf.size - total)
            if (read < 0) break
            total += read
        }
        return total
    }

    private fun readExactBytes(input: InputStream, length: Int): ByteArray {
        val out = ByteArrayOutputStream(length)
        copyExact(input, out, length.toLong())
        return out.toByteArray()
    }

    private fun copyExact(input: InputStream, output: java.io.OutputStream, totalBytes: Long) {
        if (totalBytes <= 0L) return
        val buf = ByteArray(16 * 1024)
        var remaining = totalBytes
        while (remaining > 0L) {
            val toRead = minOf(buf.size.toLong(), remaining).toInt()
            val read = input.read(buf, 0, toRead)
            if (read < 0) throw EOFException("Unexpected EOF in TAR entry ($remaining bytes remaining)")
            output.write(buf, 0, read)
            remaining -= read.toLong()
        }
    }

    private fun skipTarPadding(input: InputStream, size: Long) {
        val rem = (size % 512L).toInt()
        if (rem != 0) {
            skipExactly(input, (512 - rem).toLong())
        }
    }

    private fun skipExactly(input: InputStream, totalBytes: Long) {
        if (totalBytes <= 0L) return
        var remaining = totalBytes
        val buf = ByteArray(8192)
        while (remaining > 0L) {
            val skipped = input.skip(remaining)
            if (skipped > 0L) {
                remaining -= skipped
            } else {
                val read = input.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
                if (read < 0) throw EOFException("Unexpected EOF while skipping $remaining TAR bytes")
                remaining -= read.toLong()
            }
        }
    }
}
