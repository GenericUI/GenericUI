package com.nexustools.genericui.jvm

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe JNI native library loader shared across JVM (Swing) and Android targets.
 *
 * Attempts `System.loadLibrary(libraryName)` first so system-installed and Android APK `lib/<abi>/`
 * libraries resolve directly, then falls back to extracting a bundled library from
 * `META-INF/native/<os>-<arch>/<mappedName>` on the classpath into a private content-hashed
 * temporary directory before invoking `System.load(...)`.
 */
object JniLibraryLoader {

    private val loadedLibraries: ConcurrentHashMap<String, Boolean> = ConcurrentHashMap()
    private val libraryLocks: ConcurrentHashMap<String, Any> = ConcurrentHashMap()

    /**
     * Loads native library [libraryName] idempotently across threads.
     *
     * @throws IllegalArgumentException if [libraryName] is blank.
     * @throws UnsatisfiedLinkError if neither `System.loadLibrary` nor classpath resource extraction succeeds.
     */
    @JvmOverloads
    fun load(libraryName: String, classLoader: ClassLoader? = null) {
        val trimmed = libraryName.trim()
        require(trimmed.isNotEmpty()) { "Native library name must not be blank." }
        if (loadedLibraries[trimmed] == true) {
            return
        }

        val lock = libraryLocks[trimmed] ?: run {
            val candidate = Any()
            libraryLocks.putIfAbsent(trimmed, candidate) ?: candidate
        }

        synchronized(lock) {
            if (loadedLibraries[trimmed] == true) {
                return
            }

            val directError: UnsatisfiedLinkError = try {
                System.loadLibrary(trimmed)
                loadedLibraries[trimmed] = true
                return
            } catch (err: UnsatisfiedLinkError) {
                err
            }

            val resourcePath = resolveNativeResourcePath(trimmed)
            val bytes = readResourceBytes(resourcePath, classLoader)
                ?: throw UnsatisfiedLinkError(
                    "Failed to load native library '$trimmed' via System.loadLibrary " +
                        "(${directError.message ?: "not found on java.library.path"}) " +
                        "and classpath resource '$resourcePath' was not found."
                ).apply { initCause(directError) }

            val extractedFile = extractToPrivateTempFile(
                resourcePath = resourcePath,
                bytes = bytes
            )
            try {
                System.load(extractedFile.absolutePath)
                loadedLibraries[trimmed] = true
            } catch (loadErr: UnsatisfiedLinkError) {
                throw UnsatisfiedLinkError(
                    "Failed to load native library '$trimmed' from extracted resource " +
                        "'${extractedFile.absolutePath}' ($resourcePath): ${loadErr.message}"
                ).apply { initCause(loadErr) }
            }
        }
    }

    /**
     * Returns `true` if [libraryName] has already been loaded via [load] in this JVM process.
     */
    fun isLoaded(libraryName: String): Boolean =
        loadedLibraries[libraryName.trim()] == true

    /**
     * Normalizes an OS name (`os.name`) into `"windows"`, `"macos"`, or `"linux"`.
     */
    fun normalizedOsName(rawOsName: String = System.getProperty("os.name").orEmpty()): String {
        val lower = rawOsName.trim().lowercase(Locale.ROOT)
        return when {
            lower.contains("mac") || lower.contains("darwin") -> "macos"
            lower.contains("win") -> "windows"
            lower.contains("linux") -> "linux"
            else -> lower.replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "unknown" }
        }
    }

    /**
     * Normalizes a CPU architecture (`os.arch`) into `"x86_64"`, `"aarch64"`, `"x86"`, or `"arm"`.
     */
    fun normalizedArchName(rawArch: String = System.getProperty("os.arch").orEmpty()): String {
        val lower = rawArch.trim().lowercase(Locale.ROOT)
        return when (lower) {
            "amd64", "x86_64", "x64" -> "x86_64"
            "aarch64", "arm64" -> "aarch64"
            "x86", "i386", "i486", "i586", "i686" -> "x86"
            "arm", "armv7", "armv7l", "armeabi", "armeabi-v7a" -> "arm"
            else -> lower.replace(Regex("[^a-z0-9_]+"), "-").trim('-').ifEmpty { "unknown" }
        }
    }

    /**
     * Returns the `<os>-<arch>` classifier directory name used under `META-INF/native/`.
     */
    fun osArchTag(
        rawOsName: String = System.getProperty("os.name").orEmpty(),
        rawArch: String = System.getProperty("os.arch").orEmpty()
    ): String = "${normalizedOsName(rawOsName)}-${normalizedArchName(rawArch)}"

    /**
     * Maps [libraryName] to its platform shared-library file name (`foo.dll`, `libfoo.dylib`, `libfoo.so`).
     */
    fun mapLibraryName(
        libraryName: String,
        rawOsName: String = System.getProperty("os.name").orEmpty()
    ): String {
        val trimmed = libraryName.trim()
        require(trimmed.isNotEmpty()) { "Native library name must not be blank." }
        val os = normalizedOsName(rawOsName)
        return when (os) {
            "windows" -> if (trimmed.endsWith(".dll", ignoreCase = true)) trimmed else "$trimmed.dll"
            "macos" -> when {
                trimmed.endsWith(".dylib", ignoreCase = true) -> trimmed
                trimmed.startsWith("lib") -> "$trimmed.dylib"
                else -> "lib$trimmed.dylib"
            }
            else -> when {
                trimmed.endsWith(".so", ignoreCase = true) -> trimmed
                trimmed.startsWith("lib") -> "$trimmed.so"
                else -> "lib$trimmed.so"
            }
        }
    }

    /**
     * Returns the canonical JAR resource path `META-INF/native/<os>-<arch>/<mappedName>`.
     */
    fun resolveNativeResourcePath(
        libraryName: String,
        rawOsName: String = System.getProperty("os.name").orEmpty(),
        rawArch: String = System.getProperty("os.arch").orEmpty()
    ): String = "META-INF/native/${osArchTag(rawOsName, rawArch)}/${mapLibraryName(libraryName, rawOsName)}"

    private fun readResourceBytes(resourcePath: String, preferredLoader: ClassLoader?): ByteArray? {
        val candidates = listOfNotNull(
            preferredLoader,
            JniLibraryLoader::class.java.classLoader,
            Thread.currentThread().contextClassLoader,
            ClassLoader.getSystemClassLoader()
        ).distinct()

        for (loader in candidates) {
            openStream(loader, resourcePath)?.use { stream ->
                return readAllBytesCompat(stream)
            }
        }
        JniLibraryLoader::class.java.getResourceAsStream("/$resourcePath")?.use { stream ->
            return readAllBytesCompat(stream)
        }
        return null
    }

    private fun openStream(loader: ClassLoader, resourcePath: String): InputStream? =
        loader.getResourceAsStream(resourcePath)
            ?: loader.getResourceAsStream(resourcePath.trimStart('/'))

    private fun readAllBytesCompat(input: InputStream): ByteArray {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(16 * 1024)
        while (true) {
            val read = input.read(chunk)
            if (read < 0) break
            if (read > 0) {
                buffer.write(chunk, 0, read)
            }
        }
        return buffer.toByteArray()
    }

    private fun extractToPrivateTempFile(resourcePath: String, bytes: ByteArray): File {
        val hash16 = sha256Hex(bytes).take(16)
        val tmpRoot = System.getProperty("java.io.tmpdir")?.takeIf { it.isNotBlank() } ?: "."
        val osArchDirName = osArchTag()
        val fileName = resourcePath.substringAfterLast('/')
        val targetDir = File(File(File(tmpRoot, "genericui-jni"), osArchDirName), hash16)
        targetDir.mkdirs()

        val targetFile = File(targetDir, fileName)
        if (targetFile.isFile && targetFile.length() == bytes.size.toLong()) {
            return targetFile
        }

        val stagingFile = File(targetDir, "$fileName.${System.nanoTime()}.tmp")
        try {
            FileOutputStream(stagingFile).use { fos ->
                fos.write(bytes)
                fos.flush()
            }
            stagingFile.setReadable(true, true)
            stagingFile.setExecutable(true, true)
            if (!targetFile.exists() && stagingFile.renameTo(targetFile)) {
                return targetFile
            }
            if (targetFile.isFile && targetFile.length() == bytes.size.toLong()) {
                return targetFile
            }
            FileOutputStream(targetFile).use { fos ->
                fos.write(bytes)
                fos.flush()
            }
            targetFile.setReadable(true, true)
            targetFile.setExecutable(true, true)
            return targetFile
        } finally {
            if (stagingFile.exists()) {
                stagingFile.delete()
            }
        }
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) {
            val v = b.toInt() and 0xFF
            sb.append(HEX_CHARS[v ushr 4])
            sb.append(HEX_CHARS[v and 0x0F])
        }
        return sb.toString()
    }

    private val HEX_CHARS: CharArray = "0123456789abcdef".toCharArray()
}
