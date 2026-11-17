package com.nexustools.genericui.jvm

import com.nexustools.genericui.image.ImageSource
import com.nexustools.genericui.image.cache.ImageCacheManager
import com.nexustools.genericui.res.Resources
import java.io.File
import java.net.URI

/**
 * Shared JVM and Android (`minSdk >= 24`) resolver for loading encoded image bytes from
 * `http://`/`https://` URLs, `file://` URIs, local filesystem paths, classpath resources,
 * and configurable platform resource readers.
 */
object JvmImageSourceResolver {

    @Volatile
    var platformResourceReaderHook: ((uriOrPath: String) -> ByteArray?)? = null

    @Volatile
    var defaultClassLoader: ClassLoader? = null

    /**
     * Registers [ImageCacheManager.localResourceReaderHook] and [ImageCacheManager.urlFetcherHook]
     * using JVM/Android-compatible `URL`, `File`, and `ClassLoader` resolution.
     */
    fun installHooks(
        classLoader: ClassLoader? = null,
        extraReader: ((String) -> ByteArray?)? = null,
        overwriteExisting: Boolean = false
    ) {
        if (classLoader != null) {
            defaultClassLoader = classLoader
        }
        if (extraReader != null) {
            platformResourceReaderHook = extraReader
        }
        if (overwriteExisting || ImageCacheManager.localResourceReaderHook == null) {
            ImageCacheManager.localResourceReaderHook = { uriOrPath ->
                readLocalOrResourceBytes(
                    uriOrPath = uriOrPath,
                    classLoader = defaultClassLoader,
                    extraReader = platformResourceReaderHook
                )
            }
        }
        if (overwriteExisting || ImageCacheManager.urlFetcherHook == null) {
            ImageCacheManager.urlFetcherHook = { url ->
                fetchUrlOrLocalBytes(
                    url = url,
                    classLoader = defaultClassLoader,
                    extraReader = platformResourceReaderHook
                )
            }
        }
    }

    /**
     * Resolves encoded bytes for [source], consulting in-memory bytes, registered [Resources],
     * [ImageCacheManager], and local/classpath resource resolution.
     */
    fun resolveSourceBytes(
        source: ImageSource,
        classLoader: ClassLoader? = defaultClassLoader,
        extraReader: ((String) -> ByteArray?)? = platformResourceReaderHook
    ): ByteArray? {
        source.encodedBytes?.let { return it }
        source.resId?.let { id ->
            Resources.activeTable.getRawBytes(id)?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        val uri = source.uriOrPath ?: return null
        ImageCacheManager.fetchAndCacheBytes(uri)?.let { return it }
        return readLocalOrResourceBytes(uri, classLoader, extraReader)
    }

    /**
     * Fetches remote `http://` / `https://` URLs via `URI.create(url).toURL()` or falls back
     * to [readLocalOrResourceBytes] for non-HTTP URIs and local paths.
     */
    fun fetchUrlOrLocalBytes(
        url: String,
        classLoader: ClassLoader? = defaultClassLoader,
        extraReader: ((String) -> ByteArray?)? = platformResourceReaderHook
    ): ByteArray? {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            return runCatching { URI.create(trimmed).toURL().readBytes() }.getOrNull()
        }
        return readLocalOrResourceBytes(trimmed, classLoader, extraReader)
    }

    /**
     * Converts a `file:` URI string (`file:/...`, `file:///...`, or `file://localhost/...`) into
     * a [File], or returns `null` if [uri] does not use the `file:` scheme.
     */
    fun resolveFileUri(uri: String): File? {
        val trimmed = uri.trim()
        if (!trimmed.startsWith("file:", ignoreCase = true)) return null
        val normalizedForUri = if (trimmed.startsWith("file:")) {
            trimmed
        } else {
            "file:" + trimmed.substring(5)
        }
        runCatching { File(URI.create(normalizedForUri)) }.getOrNull()?.let { return it }
        val afterScheme = trimmed.substring(5)
        val pathPart = when {
            afterScheme.startsWith("///") -> afterScheme.substring(2)
            afterScheme.startsWith("//localhost/", ignoreCase = true) -> afterScheme.substring(11)
            afterScheme.startsWith("//") -> afterScheme.substring(2)
            else -> afterScheme
        }
        val platformPath = if (pathPart.length >= 3 && pathPart[0] == '/' && pathPart[2] == ':' && File.separatorChar == '\\') {
            pathPart.substring(1)
        } else {
            pathPart
        }
        return if (platformPath.isEmpty()) null else File(platformPath)
    }

    /**
     * Reads raw bytes from [extraReader], a `file:` / `file://` URI, candidate local resource files,
     * or classpath resources via [ClassLoader.getResourceAsStream].
     */
    fun readLocalOrResourceBytes(
        uriOrPath: String,
        classLoader: ClassLoader? = defaultClassLoader,
        extraReader: ((String) -> ByteArray?)? = platformResourceReaderHook
    ): ByteArray? {
        val trimmedRaw = uriOrPath.trim()
        if (trimmedRaw.isEmpty()) return null
        val clean = trimmedRaw.substringBefore('?').substringBefore('#').trim()
        if (clean.isEmpty()) return null
        if (clean.startsWith("http://", ignoreCase = true) ||
            clean.startsWith("https://", ignoreCase = true)
        ) {
            return null
        }

        extraReader?.invoke(trimmedRaw)?.takeIf { it.isNotEmpty() }?.let { return it }
        if (clean != trimmedRaw) {
            extraReader?.invoke(clean)?.takeIf { it.isNotEmpty() }?.let { return it }
        }

        val isFileScheme = clean.startsWith("file:", ignoreCase = true)
        if (isFileScheme) {
            val uriFile = resolveFileUri(clean)
            if (uriFile != null && uriFile.isFile && uriFile.exists()) {
                runCatching { return uriFile.readBytes() }
            }
        }

        val hasUriScheme = clean.contains("://") || isFileScheme
        if (!hasUriScheme) {
            val candidates = listOf(
                File(clean),
                File("src/commonMain/resources/$clean"),
                File("resources/$clean"),
                File("../src/commonMain/resources/$clean")
            )
            for (f in candidates) {
                if (f.isFile && f.exists()) {
                    runCatching { return f.readBytes() }
                }
            }
        }

        val resourcePaths = buildClasspathCandidates(clean)
        val loaders = listOfNotNull(
            classLoader,
            Thread.currentThread().contextClassLoader,
            JvmImageSourceResolver::class.java.classLoader
        ).distinct()
        for (loader in loaders) {
            for (resPath in resourcePaths) {
                val stream = loader.getResourceAsStream(resPath)
                if (stream != null) {
                    val bytes = runCatching { stream.use { it.readBytes() } }.getOrNull()
                    if (bytes != null && bytes.isNotEmpty()) return bytes
                }
            }
        }
        return null
    }

    private fun buildClasspathCandidates(clean: String): List<String> {
        val out = ArrayList<String>(4)
        val stripped = clean.trimStart('/')
        if (stripped.isNotEmpty()) {
            out.add(stripped)
        }
        if (clean.startsWith("res://", ignoreCase = true)) {
            val withoutScheme = clean.substring(6).trimStart('/')
            if (withoutScheme.isNotEmpty()) {
                out.add(withoutScheme)
                out.add("assets/$withoutScheme")
            }
        }
        return out.distinct()
    }
}
