package com.nexustools.genericui.apple

import com.nexustools.genericui.io.Readable
import com.nexustools.genericui.nativeimpl.io.MemoryReadableNativeImpl
import com.nexustools.genericui.res.Resources
import com.nexustools.genericui.resources.ResourceID
import com.nexustools.genericui.resources.ResourceResolverNativeImpl
import com.nexustools.genericui.style.color.RgbaColor
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.dataWithContentsOfFile

class AppleResourceResolverNativeImpl(
    private val bundle: NSBundle = NSBundle.mainBundle
) : ResourceResolverNativeImpl {
    private val lock = AppleReentrantLock()
    private var disposedState = false

    override val isDisposed: Boolean
        get() = lock.withLock { disposedState }

    override fun resolveUrl(resId: ResourceID): String {
        if (isDisposed) return ""
        findBundlePath(resId)?.let { path ->
            return "file://$path"
        }
        return Resources.activeTable.getUrl(resId)
    }

    override fun resolveBytes(resId: ResourceID): ByteArray? {
        if (isDisposed) return null
        findBundlePath(resId)?.let { path ->
            val data = NSData.dataWithContentsOfFile(path)
            if (data != null) {
                return AppleDataBridge.nsDataToByteArray(data)
            }
        }
        return Resources.activeTable.getBytes(resId)
            ?: Resources.activeTable.getRawText(resId)?.encodeToByteArray()
    }

    override fun resolveString(resId: ResourceID): String? {
        if (isDisposed) return null
        Resources.activeTable.getStringOrNull(resId)?.let { return it }
        Resources.activeTable.getRawText(resId)?.let { return it }
        findBundlePath(resId)?.let { path ->
            val data = NSData.dataWithContentsOfFile(path)
            if (data != null) {
                return AppleDataBridge.nsDataToByteArray(data).decodeToString()
            }
        }
        return null
    }

    override fun resolveColor(resId: ResourceID): RgbaColor? {
        if (isDisposed) return null
        return Resources.activeTable.getColorOrNull(resId)
    }

    override fun resolveDimenPx(resId: ResourceID): Float? {
        if (isDisposed) return null
        return Resources.activeTable.getDimenOrNull(resId)
    }

    override suspend fun openResourceStream(resId: ResourceID): Readable? {
        return openReadable(resId)
    }

    override suspend fun openReadable(resId: ResourceID): Readable? {
        if (isDisposed) return null
        findBundlePath(resId)?.let { path ->
            return AppleFileReadableNativeImpl(path)
        }
        val fontPath = Resources.activeTable.getFontPath(resId)
        if (!fontPath.isNullOrBlank() && NSFileManager.defaultManager.fileExistsAtPath(fontPath)) {
            return AppleFileReadableNativeImpl(fontPath)
        }
        val bytes = resolveBytes(resId) ?: return null
        return MemoryReadableNativeImpl(bytes)
    }

    override fun dispose() {
        lock.withLock {
            disposedState = true
        }
    }

    private fun findBundlePath(resId: ResourceID): String? {
        val candidates = ArrayList<String>(4)
        if (resId.relativePath.isNotBlank()) {
            candidates.add(resId.relativePath.trimStart('/'))
            candidates.add("resources/${resId.relativePath.trimStart('/')}")
        }
        val typeDir = resId.type.directoryName
        if (typeDir.isNotBlank() && resId.name.isNotBlank()) {
            candidates.add("$typeDir/${resId.name}")
            candidates.add("resources/$typeDir/${resId.name}")
        }

        for (candidate in candidates) {
            val slashIdx = candidate.lastIndexOf('/')
            val dir = if (slashIdx > 0) candidate.substring(0, slashIdx) else null
            val fileName = if (slashIdx >= 0) candidate.substring(slashIdx + 1) else candidate
            val dotIdx = fileName.lastIndexOf('.')
            val baseName = if (dotIdx > 0) fileName.substring(0, dotIdx) else fileName
            val ext = if (dotIdx > 0 && dotIdx < fileName.lastIndex) fileName.substring(dotIdx + 1) else null

            bundle.pathForResource(baseName, ofType = ext, inDirectory = dir)?.let { return it }
            bundle.URLForResource(baseName, withExtension = ext, subdirectory = dir)?.path?.let { return it }
        }

        val fallbackDir = resId.type.directoryName.takeIf { it.isNotBlank() }
        if (resId.name.isNotBlank()) {
            bundle.pathForResource(resId.name, ofType = null, inDirectory = fallbackDir)?.let { return it }
            bundle.pathForResource(resId.name, ofType = null)?.let { return it }
        }
        return null
    }
}
