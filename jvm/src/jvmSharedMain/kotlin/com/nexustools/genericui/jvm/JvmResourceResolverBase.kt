package com.nexustools.genericui.jvm

import com.nexustools.genericui.io.ByteBuffer
import com.nexustools.genericui.io.Readable
import com.nexustools.genericui.io.ReadableByteBuffer
import com.nexustools.genericui.res.Resources
import com.nexustools.genericui.resources.DefaultResourceResolverNativeImpl
import com.nexustools.genericui.resources.ResourceID
import java.io.File

/**
 * Shared JVM and Android (`minSdk >= 24`) base implementation of
 * [com.nexustools.genericui.resources.ResourceResolverNativeImpl] resolving classpath resources via
 * [ClassLoader.getResource] / [ClassLoader.getResourceAsStream] and local candidate resource files,
 * with fallback to [DefaultResourceResolverNativeImpl].
 */
open class JvmResourceResolverBase(
    private val customClassLoader: ClassLoader? = null,
    protected val isOnMainThread: () -> Boolean = { false }
) : DefaultResourceResolverNativeImpl() {

    protected open fun resolveClassLoader(): ClassLoader? =
        customClassLoader ?: Thread.currentThread().contextClassLoader ?: this::class.java.classLoader

    protected open fun candidateClasspathPaths(resId: ResourceID): List<String> {
        val registered = Resources.activeTable.findResourceId(resId.id)
            ?: Resources.activeTable.findResourceId(resId.type, resId.name)
            ?: resId
        val canonical = registered.canonicalRelativePath.trimStart('/')
        val rel = registered.relativePath.ifEmpty { resId.relativePath }.trimStart('/')
        val stripped = ResourceID.stripScopePrefix(canonical)
        val fileName = canonical.substringAfterLast('/').ifEmpty { resId.name }
        return buildList {
            if (canonical.isNotEmpty()) {
                add(canonical)
            }
            if (stripped.isNotEmpty() && stripped != canonical) {
                add(stripped)
            }
            if (rel.isNotEmpty()) {
                add(rel)
                add("assets/$rel")
            }
            add("assets/${resId.type.directoryName}/$fileName")
            add("${resId.type.directoryName}/$fileName")
        }.distinct()
    }

    protected open fun findLocalResourceFile(resId: ResourceID): File? {
        val paths = candidateClasspathPaths(resId)
        val roots = listOf(
            File("."),
            File("src/commonMain/resources"),
            File("resources"),
            File("../src/commonMain/resources")
        )
        for (rel in paths) {
            for (root in roots) {
                val candidate = if (root.path == ".") File(rel) else File(root, rel)
                if (candidate.isFile && candidate.exists()) {
                    return candidate
                }
            }
        }
        return null
    }

    override fun resolveUrl(resId: ResourceID): String {
        val loader = resolveClassLoader()
        if (loader != null) {
            for (path in candidateClasspathPaths(resId)) {
                val url = loader.getResource(path)
                if (url != null) return url.toExternalForm()
            }
        }
        findLocalResourceFile(resId)?.let { file ->
            return file.toURI().toString()
        }
        return super.resolveUrl(resId)
    }

    override fun resolveBytes(resId: ResourceID): ByteArray? {
        val loader = resolveClassLoader()
        if (loader != null) {
            for (path in candidateClasspathPaths(resId)) {
                val stream = loader.getResourceAsStream(path)
                if (stream != null) {
                    val bytes = runCatching { stream.use { it.readBytes() } }.getOrNull()
                    if (bytes != null) return bytes
                }
            }
        }
        findLocalResourceFile(resId)?.let { file ->
            runCatching { file.readBytes() }.getOrNull()?.let { return it }
        }
        return super.resolveBytes(resId)
    }

    override suspend fun openResourceStream(resId: ResourceID): Readable? {
        val loader = resolveClassLoader()
        if (loader != null) {
            for (path in candidateClasspathPaths(resId)) {
                val stream = loader.getResourceAsStream(path)
                if (stream != null) {
                    return JvmStreamFileReadableNativeImpl(
                        inputStream = stream,
                        filePath = resolveUrl(resId),
                        isOnMainThread = isOnMainThread
                    )
                }
            }
        }
        findLocalResourceFile(resId)?.let { file ->
            return JvmStreamFileReadableNativeImpl(
                file = file,
                isOnMainThread = isOnMainThread
            )
        }
        val rawBytes = super.resolveBytes(resId)
        if (rawBytes != null) {
            return ReadableByteBuffer(ByteBuffer.wrap(rawBytes))
        }
        val rawText = Resources.activeTable.getRawText(resId)
            ?: Resources.activeTable.getStringOrNull(resId)
            ?: Resources.activeTable.getDrawable(resId)?.svgSource
        if (rawText != null) {
            return ReadableByteBuffer(ByteBuffer.wrap(rawText.encodeToByteArray()))
        }
        return null
    }
}
