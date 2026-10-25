package com.nexustools.genericui.util

import com.nexustools.genericui.core.Disposable
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.nativeimpl.system.DefaultUserDirectoryNativeImpl
import com.nexustools.genericui.nativeimpl.system.UserDirectoryNativeImpl

/**
 * Abstract base class for OS-specific user application directories ([ConfigDirectory] and [CacheDirectory]).
 *
 * Automatically resolves its platform-specific [UserDirectoryNativeImpl] from the active [NativeFactory]
 * (or [factory] when explicitly provided), and transparently migrates any files written to the in-memory
 * fallback when [GenericUI.activeFactory] is initialized later in the application lifecycle.
 */
abstract class UserDirectory(
    val appName: String = GenericUI.applicationName,
    val subdirectory: String = "",
    protected val factory: NativeFactory? = null,
    private val nativeImplOverride: UserDirectoryNativeImpl? = null
) : Disposable {

    protected abstract val fallbackScheme: String

    protected abstract fun createBackendImpl(activeFactory: NativeFactory): UserDirectoryNativeImpl

    private var boundFactory: NativeFactory? = null
    private var backingNativeImpl: UserDirectoryNativeImpl? = nativeImplOverride

    val nativeImpl: UserDirectoryNativeImpl
        get() {
            nativeImplOverride?.let { return it }
            val currentImpl = backingNativeImpl
            if (currentImpl == null) {
                val initialFactory = factory ?: GenericUI.activeFactory
                boundFactory = initialFactory
                val created = initialFactory?.let { createBackendImpl(it) }
                    ?: DefaultUserDirectoryNativeImpl(appName, subdirectory, fallbackScheme)
                backingNativeImpl = created
                return created
            }
            if (factory == null) {
                val active = GenericUI.activeFactory
                if (active != null && active !== boundFactory) {
                    boundFactory = active
                    val upgraded = createBackendImpl(active)
                    if (currentImpl is DefaultUserDirectoryNativeImpl) {
                        for (name in currentImpl.listFiles()) {
                            if (!upgraded.exists(name)) {
                                currentImpl.readBytes(name)?.let { upgraded.writeBytes(name, it) }
                            }
                        }
                    }
                    backingNativeImpl = upgraded
                    return upgraded
                }
            }
            return currentImpl
        }

    val directoryPath: String
        get() = nativeImpl.directoryPath

    val fileSeparator: Char
        get() = nativeImpl.fileSeparator

    val fileCount: Int
        get() = listFiles().size

    override val isDisposed: Boolean
        get() = backingNativeImpl?.isDisposed == true

    fun ensureExists(): Boolean = nativeImpl.ensureDirectoryExists()

    fun resolvePath(fileName: String): String = nativeImpl.resolveFilePath(fileName)

    fun writeBytes(fileName: String, bytes: ByteArray): Boolean = nativeImpl.writeBytes(fileName, bytes)

    fun readBytes(fileName: String): ByteArray? = nativeImpl.readBytes(fileName)

    fun writeText(fileName: String, text: String): Boolean = nativeImpl.writeText(fileName, text)

    fun readText(fileName: String): String? = nativeImpl.readText(fileName)

    fun exists(fileName: String): Boolean = nativeImpl.exists(fileName)

    fun fileSize(fileName: String): Long = nativeImpl.fileSize(fileName)

    fun delete(fileName: String): Boolean = nativeImpl.delete(fileName)

    fun listFiles(): List<String> = nativeImpl.listFiles()

    fun listFilesWithSuffix(suffix: String): List<String> = listFiles().filter { it.endsWith(suffix) }

    fun clear(): Int = nativeImpl.clearAll()

    fun totalByteSize(): Long = nativeImpl.totalByteSize()

    override fun dispose() {
        backingNativeImpl?.dispose()
    }

    companion object {
        fun sanitizeFileName(raw: String): String {
            val trimmed = raw.trim().trim('/', '\\').ifEmpty { "unnamed" }
            return trimmed.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        }
    }
}
