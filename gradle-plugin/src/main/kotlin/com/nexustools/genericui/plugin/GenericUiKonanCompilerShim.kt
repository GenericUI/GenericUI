package com.nexustools.genericui.plugin

import org.gradle.api.GradleException
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.zip.ZipFile

/**
 * Generates and caches a minimal bytecode shim JAR (`genericui-konan-shim.jar`) for Kotlin/Native's
 * `kotlin-native-compiler-embeddable.jar` and launches `konanc` out-of-process with `bin/run_konan`
 * JVM flags and blacklisted environment cleanup.
 *
 * The shim patches two checks in Kotlin/Native 2.4.x without altering `~/.konan` on disk:
 * 1. `TopLevelPhasesKt`: substitutes `CachedLibraries.getHasStaticCaches()` with `getHasDynamicCaches()`
 *    so `-opt -Xbinary=enableReleaseBinaryCache=true` honors dynamic library caches in Release builds.
 * 2. `CachedLibraries.<init>`: short-circuits the `KonanTarget$MINGW_X64` cache rejection warning for
 *    non-stdlib klibs so `mingwX64` dynamic caches apply identically to macOS and Linux.
 */
object GenericUiKonanCompilerShim {

    private const val SHIM_FORMAT_VERSION = "v2"
    private const val SHIM_JAR_FILE_NAME = "genericui-konan-shim.jar"
    private const val SHIM_STAMP_FILE_NAME = "genericui-konan-shim.stamp"
    private const val PER_FILE_OPT_SHIM_JAR_FILE_NAME = "genericui-konan-perfile-opt-shim.jar"
    private const val PER_FILE_OPT_SHIM_STAMP_FILE_NAME = "genericui-konan-perfile-opt-shim.stamp"
    private const val TOP_LEVEL_PHASES_CLASS =
        "org/jetbrains/kotlin/backend/konan/driver/phases/TopLevelPhasesKt.class"
    private const val CACHED_LIBRARIES_CLASS =
        "org/jetbrains/kotlin/backend/konan/CachedLibraries.class"
    private const val CACHED_LIBRARIES_COMPANION_CLASS =
        "org/jetbrains/kotlin/backend/konan/CachedLibraries\$Companion.class"
    private const val PROPERTY_ACCESSOR_INLINE_CLASS =
        "org/jetbrains/kotlin/backend/common/lower/optimizations/PropertyAccessorInlineLowering.class"
    private const val MAX_INLINE_COMMAND_CHARS = 24_000

    /**
     * Returns the path to an up-to-date `genericui-konan-shim.jar` inside [shimOutputDir].
     */
    fun ensureShimJar(konanHomeDir: File, shimOutputDir: File): File {
        return ensurePatchedJar(
            konanHomeDir = konanHomeDir,
            shimOutputDir = shimOutputDir,
            jarFileName = SHIM_JAR_FILE_NAME,
            stampFileName = SHIM_STAMP_FILE_NAME
        ) { zip ->
            val topEntry = zip.getEntry(TOP_LEVEL_PHASES_CLASS)
                ?: throw GradleException(
                    "Missing $TOP_LEVEL_PHASES_CLASS in ${zip.name}"
                )
            val cachedEntry = zip.getEntry(CACHED_LIBRARIES_CLASS)
                ?: throw GradleException(
                    "Missing $CACHED_LIBRARIES_CLASS in ${zip.name}"
                )
            val companionEntry = zip.getEntry(CACHED_LIBRARIES_COMPANION_CLASS)
                ?: throw GradleException(
                    "Missing $CACHED_LIBRARIES_COMPANION_CLASS in ${zip.name}"
                )
            val topLevelBytes = zip.getInputStream(topEntry).use { it.readBytes() }
            val cachedLibsBytes = zip.getInputStream(cachedEntry).use { it.readBytes() }
            val companionBytes = zip.getInputStream(companionEntry).use { it.readBytes() }
            linkedMapOf(
                TOP_LEVEL_PHASES_CLASS to patchTopLevelPhasesClass(topLevelBytes),
                CACHED_LIBRARIES_CLASS to patchCachedLibrariesClass(cachedLibsBytes),
                CACHED_LIBRARIES_COMPANION_CLASS to patchCachedLibrariesCompanionClass(companionBytes)
            )
        }
    }

    /**
     * Returns the path to an up-to-date `genericui-konan-perfile-opt-shim.jar` inside [shimOutputDir],
     * used only when `-Xmake-per-file-cache` and `-opt` are combined.
     */
    fun ensurePerFileOptShimJar(konanHomeDir: File, shimOutputDir: File): File {
        return ensurePatchedJar(
            konanHomeDir = konanHomeDir,
            shimOutputDir = shimOutputDir,
            jarFileName = PER_FILE_OPT_SHIM_JAR_FILE_NAME,
            stampFileName = PER_FILE_OPT_SHIM_STAMP_FILE_NAME
        ) { zip ->
            val propInlineEntry = zip.getEntry(PROPERTY_ACCESSOR_INLINE_CLASS)
                ?: throw GradleException(
                    "Missing $PROPERTY_ACCESSOR_INLINE_CLASS in ${zip.name}"
                )
            val propInlineBytes = zip.getInputStream(propInlineEntry).use { it.readBytes() }
            linkedMapOf(
                PROPERTY_ACCESSOR_INLINE_CLASS to patchPropertyAccessorInlineLoweringClass(propInlineBytes)
            )
        }
    }

    private fun ensurePatchedJar(
        konanHomeDir: File,
        shimOutputDir: File,
        jarFileName: String,
        stampFileName: String,
        entriesBuilder: (ZipFile) -> Map<String, ByteArray>
    ): File {
        val embeddableJar = File(konanHomeDir, "konan/lib/kotlin-native-compiler-embeddable.jar")
        if (!embeddableJar.isFile) {
            throw GradleException(
                "Kotlin/Native compiler embeddable JAR not found at ${embeddableJar.absolutePath}"
            )
        }

        shimOutputDir.mkdirs()
        val expectedStamp = buildStamp(embeddableJar)
        val shimJar = File(shimOutputDir, jarFileName)
        val stampFile = File(shimOutputDir, stampFileName)
        val lockFile = File(shimOutputDir, "shim.lock")

        RandomAccessFile(lockFile, "rw").use { raf ->
            raf.channel.lock().use {
                if (shimJar.isFile && stampFile.isFile &&
                    runCatching { stampFile.readText(Charsets.UTF_8) }.getOrNull() == expectedStamp
                ) {
                    return shimJar
                }

                val patchedEntries = ZipFile(embeddableJar).use { zip ->
                    entriesBuilder(zip)
                }

                val tmpJar = File(shimOutputDir, "$jarFileName.tmp.${ProcessHandle.current().pid()}")
                try {
                    JarOutputStream(tmpJar.outputStream().buffered()).use { jos ->
                        for ((entryName, entryBytes) in patchedEntries) {
                            jos.putNextEntry(JarEntry(entryName))
                            jos.write(entryBytes)
                            jos.closeEntry()
                        }
                    }
                    Files.move(
                        tmpJar.toPath(),
                        shimJar.toPath(),
                        StandardCopyOption.REPLACE_EXISTING
                    )
                    stampFile.writeText(expectedStamp, Charsets.UTF_8)
                } finally {
                    if (tmpJar.exists()) {
                        tmpJar.delete()
                    }
                }
            }
        }

        return shimJar
    }

    /**
     * Runs `org.jetbrains.kotlin.cli.utilities.MainKt konanc <args>` with [shimJar] ahead of
     * `kotlin-native-compiler-embeddable.jar` on the classpath.
     */
    fun runKonanc(
        konanHomeDir: File,
        shimJar: File,
        konanDataDir: File?,
        args: List<String>,
        workingDir: File? = null
    ) {
        val embeddableJar = File(konanHomeDir, "konan/lib/kotlin-native-compiler-embeddable.jar")
        if (!embeddableJar.isFile) {
            throw GradleException(
                "Kotlin/Native compiler embeddable JAR not found at ${embeddableJar.absolutePath}"
            )
        }
        if (!shimJar.isFile) {
            throw GradleException("GenericUI Konan shim JAR not found at ${shimJar.absolutePath}")
        }

        val needsPerFileOptShim = args.contains("-Xmake-per-file-cache") && args.contains("-opt")
        val classpath = if (needsPerFileOptShim) {
            val shimOutputDir = shimJar.parentFile ?: shimJar.absoluteFile.parentFile
            val perFileOptShim = ensurePerFileOptShimJar(konanHomeDir, shimOutputDir)
            "${perFileOptShim.absolutePath}${File.pathSeparator}${shimJar.absolutePath}${File.pathSeparator}${embeddableJar.absolutePath}"
        } else {
            "${shimJar.absolutePath}${File.pathSeparator}${embeddableJar.absolutePath}"
        }

        val javaExe = resolveJavaExecutable()
        val javaFeature = detectJavaFeatureVersion()

        val tempArgFile: File? = if (args.sumOf { it.length + 1 } > MAX_INLINE_COMMAND_CHARS) {
            Files.createTempFile("genericui-konanc-args-", ".txt").toFile().apply {
                writeText(
                    args.joinToString("\n") { formatArgFileToken(it) },
                    Charsets.UTF_8
                )
            }
        } else {
            null
        }

        try {
            val command = buildList {
                add(javaExe.absolutePath)
                add("-ea")
                add("-Xmx4G")
                add("-XX:TieredStopAtLevel=1")
                add("-Dfile.encoding=UTF-8")
                add("-Dkonan.home=${konanHomeDir.absolutePath}")
                if (konanDataDir != null) {
                    add("-Dkonan.data.dir=${konanDataDir.absolutePath}")
                }
                if (javaFeature >= 24) {
                    add("--enable-native-access=ALL-UNNAMED")
                }
                if (javaFeature == 24) {
                    add("--sun-misc-unsafe-memory-access=allow")
                }
                add("-cp")
                add(classpath)
                add("org.jetbrains.kotlin.cli.utilities.MainKt")
                add("konanc")
                if (tempArgFile != null) {
                    add("@${tempArgFile.absolutePath}")
                } else {
                    addAll(args)
                }
            }

            val pb = ProcessBuilder(command)
            if (workingDir != null && workingDir.isDirectory) {
                pb.directory(workingDir)
            }
            pb.redirectErrorStream(true)

            val env = pb.environment()
            val blacklistFile = File(konanHomeDir, "tools/env_blacklist")
            if (blacklistFile.isFile) {
                blacklistFile.readLines(Charsets.UTF_8)
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .forEach { env.remove(it) }
            }
            if (konanDataDir != null) {
                env["KONAN_DATA_DIR"] = konanDataDir.absolutePath
            }
            env["LIBCLANG_DISABLE_CRASH_RECOVERY"] = "1"

            val proc = pb.start()
            val output = proc.inputStream.bufferedReader(Charsets.UTF_8).readText()
            val exitCode = proc.waitFor()
            if (exitCode != 0) {
                throw GradleException(
                    buildString {
                        appendLine("konanc exited with code $exitCode.")
                        appendLine("Command: ${command.joinToString(" ")}")
                        if (tempArgFile != null) {
                            appendLine("Args (@file expanded):")
                            args.forEach { appendLine("  $it") }
                        }
                        if (output.isNotBlank()) {
                            appendLine("Output:")
                            appendLine(output.trimEnd())
                        }
                    }
                )
            }
        } finally {
            tempArgFile?.delete()
        }
    }

    /**
     * Maps the current JVM host OS/architecture to Kotlin/Native's target name.
     */
    fun hostKonanTargetName(): String {
        val osName = System.getProperty("os.name").orEmpty().lowercase(Locale.ROOT)
        val arch = System.getProperty("os.arch").orEmpty().lowercase(Locale.ROOT)
        val isArm64 = arch == "aarch64" || arch == "arm64"
        return when {
            osName.contains("mac") || osName.contains("darwin") ->
                if (isArm64) "macos_arm64" else "macos_x64"
            osName.contains("win") -> "mingw_x64"
            osName.contains("linux") ->
                if (isArm64) "linux_arm64" else "linux_x64"
            else -> throw GradleException("Unsupported host OS '$osName' ($arch) for Kotlin/Native shim")
        }
    }

    internal fun buildStamp(embeddableJar: File): String =
        "version=$SHIM_FORMAT_VERSION\nsize=${embeddableJar.length()}\nmtime=${embeddableJar.lastModified()}\n"

    internal fun patchTopLevelPhasesClass(classBytes: ByteArray): ByteArray {
        val parsed = parseConstantPool(classBytes)
        var targetLenOffset = -1
        var targetByteLen = -1
        var matchCount = 0

        for (entry in parsed.entries) {
            if (entry != null && entry.tag == 1 && entry.utf8 == "getHasStaticCaches") {
                matchCount++
                targetLenOffset = entry.payloadOffset
                targetByteLen = entry.utf8ByteLen
            }
        }
        check(matchCount == 1 && targetLenOffset >= 0 && targetByteLen >= 0) {
            "Expected exactly 1 CONSTANT_Utf8 'getHasStaticCaches' in TopLevelPhasesKt.class, found $matchCount"
        }

        val replacementBytes = "getHasDynamicCaches".toByteArray(Charsets.UTF_8)
        val out = ByteArrayOutputStream(classBytes.size + replacementBytes.size - targetByteLen)
        out.write(classBytes, 0, targetLenOffset)
        out.write((replacementBytes.size ushr 8) and 0xFF)
        out.write(replacementBytes.size and 0xFF)
        out.write(replacementBytes)
        val afterOffset = targetLenOffset + 2 + targetByteLen
        out.write(classBytes, afterOffset, classBytes.size - afterOffset)
        return out.toByteArray()
    }

    internal fun patchCachedLibrariesClass(classBytes: ByteArray): ByteArray {
        var patchedCount = 0
        val result = patchMethodCode(classBytes, "<init>") { cp, bytes, codeStart, codeLen ->
            val cpTargetField = findMemberRefIndex(
                cp = cp,
                tag = 9,
                ownerInternalName = "org/jetbrains/kotlin/backend/konan/CachedLibraries",
                memberName = "target",
                memberDesc = "Lorg/jetbrains/kotlin/konan/target/KonanTarget;"
            )
            val cpMingwInstance = findMemberRefIndex(
                cp = cp,
                tag = 9,
                ownerInternalName = "org/jetbrains/kotlin/konan/target/KonanTarget\$MINGW_X64",
                memberName = "INSTANCE",
                memberDesc = "Lorg/jetbrains/kotlin/konan/target/KonanTarget\$MINGW_X64;"
            )
            val cpAreEqual = findMemberRefIndex(
                cp = cp,
                tag = 10,
                ownerInternalName = "kotlin/jvm/internal/Intrinsics",
                memberName = "areEqual",
                memberDesc = "(Ljava/lang/Object;Ljava/lang/Object;)Z"
            )
            val limit = codeStart + codeLen - 11
            var i = codeStart
            while (i <= limit) {
                if ((bytes[i].toInt() and 0xFF) == 0x2a &&
                    (bytes[i + 1].toInt() and 0xFF) == 0xb4 &&
                    readU2(bytes, i + 2) == cpTargetField &&
                    (bytes[i + 4].toInt() and 0xFF) == 0xb2 &&
                    readU2(bytes, i + 5) == cpMingwInstance &&
                    (bytes[i + 7].toInt() and 0xFF) == 0xb8 &&
                    readU2(bytes, i + 8) == cpAreEqual &&
                    (bytes[i + 10].toInt() and 0xFF) == 0x99
                ) {
                    // Replace `invokestatic Intrinsics.areEqual` (pops 2 refs, pushes int)
                    // with `pop; pop; iconst_0` so `ifeq` always skips the MingW rejection.
                    bytes[i + 7] = 0x57.toByte() // pop
                    bytes[i + 8] = 0x57.toByte() // pop
                    bytes[i + 9] = 0x03.toByte() // iconst_0
                    patchedCount++
                }
                i++
            }
            patchedCount
        }
        check(patchedCount == 1) {
            "Expected exactly 1 MINGW_X64 cache guard sequence in CachedLibraries.<init>, patched $patchedCount"
        }
        return result
    }

    internal fun patchPropertyAccessorInlineLoweringClass(classBytes: ByteArray): ByteArray {
        var patchedCount = 0
        val result = patchMethodCode(classBytes, "isSafeToInline") { cp, bytes, codeStart, codeLen ->
            val cpIsSafeClosedWorld = findMemberRefIndex(
                cp = cp,
                tag = 10,
                ownerInternalName = "org/jetbrains/kotlin/backend/common/lower/optimizations/PropertyAccessorInlineLowering",
                memberName = "isSafeToInlineInClosedWorld",
                memberDesc = "(Lorg/jetbrains/kotlin/ir/declarations/IrProperty;)Z"
            )
            val limit = codeStart + codeLen - 6
            var i = codeStart
            while (i <= limit) {
                if ((bytes[i].toInt() and 0xFF) == 0x2a &&
                    (bytes[i + 1].toInt() and 0xFF) == 0x2b &&
                    (bytes[i + 2].toInt() and 0xFF) == 0xb6 &&
                    readU2(bytes, i + 3) == cpIsSafeClosedWorld &&
                    (bytes[i + 5].toInt() and 0xFF) == 0xac
                ) {
                    bytes[i] = 0x00.toByte()     // nop
                    bytes[i + 1] = 0x00.toByte() // nop
                    bytes[i + 2] = 0x00.toByte() // nop
                    bytes[i + 3] = 0x00.toByte() // nop
                    bytes[i + 4] = 0x03.toByte() // iconst_0
                    // bytes[i + 5] stays 0xac (ireturn)
                    patchedCount++
                }
                i++
            }
            patchedCount
        }
        check(patchedCount == 1) {
            "Expected exactly 1 isSafeToInlineInClosedWorld call in PropertyAccessorInlineLowering.isSafeToInline, patched $patchedCount"
        }
        return result
    }

    internal fun patchCachedLibrariesCompanionClass(classBytes: ByteArray): ByteArray {
        val parsed = parseConstantPool(classBytes)
        val cp = parsed.entries
        val cpGetUniqueName = findMemberRefIndex(
            cp = cp,
            tag = 10,
            ownerInternalName = "org/jetbrains/kotlin/library/KotlinLibraryKt",
            memberName = "getUniqueName",
            memberDesc = "(Lorg/jetbrains/kotlin/library/BaseKotlinLibrary;)Ljava/lang/String;"
        )
        val cpSbAppend = findMemberRefIndex(
            cp = cp,
            tag = 10,
            ownerInternalName = "java/lang/StringBuilder",
            memberName = "append",
            memberDesc = "(Ljava/lang/String;)Ljava/lang/StringBuilder;"
        )
        val cpFileChild = findMemberRefIndex(
            cp = cp,
            tag = 10,
            ownerInternalName = "org/jetbrains/kotlin/konan/file/File",
            memberName = "child",
            memberDesc = "(Ljava/lang/String;)Lorg/jetbrains/kotlin/konan/file/File;"
        )

        val baseIdx = cp.size
        val idxStrClassUtf8 = baseIdx
        val idxStrClass = baseIdx + 1
        val idxReplaceNameUtf8 = baseIdx + 2
        val idxReplaceDescUtf8 = baseIdx + 3
        val idxReplaceNat = baseIdx + 4
        val idxStringReplace = baseIdx + 5
        val newCpCount = baseIdx + 6

        val out = ByteArrayOutputStream(classBytes.size + 128)
        out.write(classBytes, 0, 8)
        writeU2(out, newCpCount)
        out.write(classBytes, 10, parsed.cpEndOffset - 10)
        writeCpUtf8(out, "java/lang/String")
        writeCpU2Entry(out, 7, idxStrClassUtf8)
        writeCpUtf8(out, "replace")
        writeCpUtf8(out, "(CC)Ljava/lang/String;")
        writeCpU2PairEntry(out, 12, idxReplaceNameUtf8, idxReplaceDescUtf8)
        writeCpU2PairEntry(out, 10, idxStrClass, idxReplaceNat)

        val sanitizeInstr = byteArrayOf(
            0x10.toByte(), ':'.code.toByte(),
            0x10.toByte(), '_'.code.toByte(),
            0xB6.toByte(),
            ((idxStringReplace ushr 8) and 0xFF).toByte(),
            (idxStringReplace and 0xFF).toByte()
        )

        var pos = parsed.cpEndOffset
        val interfacesStart = pos + 6
        val interfacesCount = readU2(classBytes, interfacesStart)
        val fieldsStart = interfacesStart + 2 + interfacesCount * 2
        out.write(classBytes, pos, fieldsStart - pos)
        pos = fieldsStart

        val fieldsCount = readU2(classBytes, pos)
        var cursor = pos + 2
        repeat(fieldsCount) {
            cursor += 6
            val attrCount = readU2(classBytes, cursor)
            cursor += 2
            repeat(attrCount) {
                val attrLen = readU4(classBytes, cursor + 2)
                cursor += 6 + attrLen
            }
        }
        out.write(classBytes, pos, cursor - pos)
        pos = cursor

        val methodsCount = readU2(classBytes, pos)
        writeU2(out, methodsCount)
        pos += 2
        var patchedMethods = 0

        repeat(methodsCount) {
            val nameIdx = readU2(classBytes, pos + 2)
            val descIdx = readU2(classBytes, pos + 4)
            val methodName = cp[nameIdx]?.utf8
            val methodDesc = cp[descIdx]?.utf8
            val attrCount = readU2(classBytes, pos + 6)
            out.write(classBytes, pos, 8)
            pos += 8

            val patchMode = when {
                methodName == "getCachedLibraryName" &&
                    methodDesc == "(Ljava/lang/String;)Ljava/lang/String;" -> 1
                methodName == "getPerFileCachedLibraryName" &&
                    methodDesc == "(Lorg/jetbrains/kotlin/library/KotlinLibrary;)Ljava/lang/String;" -> 2
                methodName == "computeLibraryCacheDirectory" &&
                    methodDesc?.startsWith("(Lorg/jetbrains/kotlin/konan/file/File;Lorg/jetbrains/kotlin/library/KotlinLibrary;") == true -> 3
                else -> 0
            }

            repeat(attrCount) {
                val attrNameIdx = readU2(classBytes, pos)
                val attrLen = readU4(classBytes, pos + 2)
                val attrStart = pos + 6
                val attrEnd = attrStart + attrLen
                if (patchMode != 0 && cp[attrNameIdx]?.utf8 == "Code") {
                    val maxStack = readU2(classBytes, attrStart)
                    val maxLocals = readU2(classBytes, attrStart + 2)
                    val oldCodeLen = readU4(classBytes, attrStart + 4)
                    val codeStart = attrStart + 8
                    val exTableCount = readU2(classBytes, codeStart + oldCodeLen)
                    check(exTableCount == 0) {
                        "Expected zero exception handlers in CachedLibraries\$Companion.$methodName"
                    }
                    val oldCode = classBytes.copyOfRange(codeStart, codeStart + oldCodeLen)
                    val newCode = insertSanitizeBeforeInvokeVirtual(
                        code = oldCode,
                        patchMode = patchMode,
                        cpGetUniqueName = cpGetUniqueName,
                        cpSbAppend = cpSbAppend,
                        cpFileChild = cpFileChild,
                        sanitizeInstr = sanitizeInstr
                    )
                    val newMaxStack = maxOf(maxStack + 2, 4)
                    writeU2(out, attrNameIdx)
                    writeU4(out, 12 + newCode.size)
                    writeU2(out, newMaxStack)
                    writeU2(out, maxLocals)
                    writeU4(out, newCode.size)
                    out.write(newCode)
                    writeU2(out, 0)
                    writeU2(out, 0)
                    patchedMethods++
                } else {
                    out.write(classBytes, pos, attrEnd - pos)
                }
                pos = attrEnd
            }
        }
        check(patchedMethods == 3) {
            "Expected 3 patched methods in CachedLibraries\$Companion, patched $patchedMethods"
        }
        out.write(classBytes, pos, classBytes.size - pos)
        return out.toByteArray()
    }

    private fun insertSanitizeBeforeInvokeVirtual(
        code: ByteArray,
        patchMode: Int,
        cpGetUniqueName: Int,
        cpSbAppend: Int,
        cpFileChild: Int,
        sanitizeInstr: ByteArray
    ): ByteArray {
        val getUniqueHi = ((cpGetUniqueName ushr 8) and 0xFF).toByte()
        val getUniqueLo = (cpGetUniqueName and 0xFF).toByte()
        val appendHi = ((cpSbAppend ushr 8) and 0xFF).toByte()
        val appendLo = (cpSbAppend and 0xFF).toByte()
        val childHi = ((cpFileChild ushr 8) and 0xFF).toByte()
        val childLo = (cpFileChild and 0xFF).toByte()

        var insertAt = -1
        var matchCount = 0
        when (patchMode) {
            1 -> {
                for (i in 0..(code.size - 4)) {
                    if ((code[i].toInt() and 0xFF) == 0x2B &&
                        (code[i + 1].toInt() and 0xFF) == 0xB6 &&
                        code[i + 2] == appendHi &&
                        code[i + 3] == appendLo
                    ) {
                        insertAt = i + 1
                        matchCount++
                    }
                }
            }
            2 -> {
                for (i in 0..(code.size - 6)) {
                    if ((code[i].toInt() and 0xFF) == 0xB8 &&
                        code[i + 1] == getUniqueHi &&
                        code[i + 2] == getUniqueLo &&
                        (code[i + 3].toInt() and 0xFF) == 0xB6 &&
                        code[i + 4] == appendHi &&
                        code[i + 5] == appendLo
                    ) {
                        insertAt = i + 3
                        matchCount++
                    }
                }
            }
            3 -> {
                for (i in 0..(code.size - 6)) {
                    if ((code[i].toInt() and 0xFF) == 0xB8 &&
                        code[i + 1] == getUniqueHi &&
                        code[i + 2] == getUniqueLo &&
                        (code[i + 3].toInt() and 0xFF) == 0xB6 &&
                        code[i + 4] == childHi &&
                        code[i + 5] == childLo
                    ) {
                        insertAt = i + 3
                        matchCount++
                    }
                }
            }
        }
        check(matchCount == 1 && insertAt >= 0) {
            "Expected exactly 1 insertion site for patchMode=$patchMode in CachedLibraries\$Companion, found $matchCount"
        }
        val out = ByteArrayOutputStream(code.size + sanitizeInstr.size)
        out.write(code, 0, insertAt)
        out.write(sanitizeInstr)
        out.write(code, insertAt, code.size - insertAt)
        return out.toByteArray()
    }

    private fun writeU2(out: ByteArrayOutputStream, value: Int) {
        out.write((value ushr 8) and 0xFF)
        out.write(value and 0xFF)
    }

    private fun writeU4(out: ByteArrayOutputStream, value: Int) {
        out.write((value ushr 24) and 0xFF)
        out.write((value ushr 16) and 0xFF)
        out.write((value ushr 8) and 0xFF)
        out.write(value and 0xFF)
    }

    private fun writeCpUtf8(out: ByteArrayOutputStream, text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        out.write(1)
        writeU2(out, bytes.size)
        out.write(bytes)
    }

    private fun writeCpU2Entry(out: ByteArrayOutputStream, tag: Int, index1: Int) {
        out.write(tag)
        writeU2(out, index1)
    }

    private fun writeCpU2PairEntry(out: ByteArrayOutputStream, tag: Int, index1: Int, index2: Int) {
        out.write(tag)
        writeU2(out, index1)
        writeU2(out, index2)
    }

    private fun patchMethodCode(
        classBytes: ByteArray,
        targetMethodName: String,
        patcher: (cp: Array<CpEntry?>, result: ByteArray, codeStart: Int, codeLen: Int) -> Int
    ): ByteArray {
        val parsed = parseConstantPool(classBytes)
        val cp = parsed.entries
        val result = classBytes.copyOf()
        var pos = parsed.cpEndOffset
        // access_flags(2), this_class(2), super_class(2), interfaces_count(2)
        pos += 6
        val interfacesCount = readU2(result, pos)
        pos += 2 + interfacesCount * 2

        val fieldsCount = readU2(result, pos)
        pos += 2
        repeat(fieldsCount) {
            pos += 6 // access_flags, name_index, descriptor_index
            val attrCount = readU2(result, pos)
            pos += 2
            repeat(attrCount) {
                val attrLen = readU4(result, pos + 2)
                pos += 6 + attrLen
            }
        }

        val methodsCount = readU2(result, pos)
        pos += 2
        repeat(methodsCount) {
            val nameIdx = readU2(result, pos + 2)
            val methodName = cp[nameIdx]?.utf8
            pos += 6
            val attrCount = readU2(result, pos)
            pos += 2
            repeat(attrCount) {
                val attrNameIdx = readU2(result, pos)
                val attrLen = readU4(result, pos + 2)
                val attrStart = pos + 6
                if (methodName == targetMethodName && cp[attrNameIdx]?.utf8 == "Code") {
                    val codeLen = readU4(result, attrStart + 4)
                    val codeStart = attrStart + 8
                    patcher(cp, result, codeStart, codeLen)
                }
                pos = attrStart + attrLen
            }
        }
        return result
    }

    private fun resolveJavaExecutable(): File {
        val javaHome = System.getProperty("java.home")
            ?: throw GradleException("java.home system property is unset")
        val isWin = System.getProperty("os.name").orEmpty().lowercase(Locale.ROOT).contains("win")
        val candidate = File(javaHome, if (isWin) "bin/java.exe" else "bin/java")
        if (!candidate.isFile) {
            throw GradleException("Java executable not found at ${candidate.absolutePath}")
        }
        return candidate
    }

    private fun detectJavaFeatureVersion(): Int {
        return runCatching { Runtime.version().feature() }.getOrElse {
            val spec = System.getProperty("java.specification.version").orEmpty()
            val first = spec.substringBefore('.').toIntOrNull() ?: 17
            if (first == 1) spec.substringAfter('.', "8").toIntOrNull() ?: 8 else first
        }
    }

    private fun formatArgFileToken(arg: String): String {
        val needsQuoting = arg.isEmpty() || arg.any {
            it.isWhitespace() || it == '"' || it == '\'' || it == '\\' || it == '@' || it == '#'
        }
        if (!needsQuoting) return arg
        val escaped = buildString {
            append('"')
            for (ch in arg) {
                when (ch) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(ch)
                }
            }
            append('"')
        }
        return escaped
    }

    private data class CpEntry(
        val tag: Int,
        val payloadOffset: Int,
        val utf8: String? = null,
        val utf8ByteLen: Int = 0,
        val index1: Int = 0,
        val index2: Int = 0
    )

    private data class ParsedConstantPool(
        val entries: Array<CpEntry?>,
        val cpEndOffset: Int
    )

    private fun parseConstantPool(bytes: ByteArray): ParsedConstantPool {
        check(bytes.size >= 10 && readU4(bytes, 0) == 0xCAFEBABE.toInt()) {
            "Invalid JVM classfile header"
        }
        val cpCount = readU2(bytes, 8)
        val entries = arrayOfNulls<CpEntry>(cpCount)
        var pos = 10
        var i = 1
        while (i < cpCount) {
            val tag = bytes[pos++].toInt() and 0xFF
            when (tag) {
                1 -> {
                    val lenOffset = pos
                    val len = readU2(bytes, pos)
                    pos += 2
                    val str = String(bytes, pos, len, Charsets.UTF_8)
                    entries[i] = CpEntry(
                        tag = tag,
                        payloadOffset = lenOffset,
                        utf8 = str,
                        utf8ByteLen = len
                    )
                    pos += len
                }
                3, 4 -> {
                    entries[i] = CpEntry(tag = tag, payloadOffset = pos)
                    pos += 4
                }
                5, 6 -> {
                    entries[i] = CpEntry(tag = tag, payloadOffset = pos)
                    pos += 8
                    i++ // Long and Double occupy two constant-pool indices.
                }
                7, 8, 16, 19, 20 -> {
                    val idx1 = readU2(bytes, pos)
                    entries[i] = CpEntry(tag = tag, payloadOffset = pos, index1 = idx1)
                    pos += 2
                }
                9, 10, 11, 12, 17, 18 -> {
                    val idx1 = readU2(bytes, pos)
                    val idx2 = readU2(bytes, pos + 2)
                    entries[i] = CpEntry(tag = tag, payloadOffset = pos, index1 = idx1, index2 = idx2)
                    pos += 4
                }
                15 -> {
                    val idx1 = bytes[pos].toInt() and 0xFF
                    val idx2 = readU2(bytes, pos + 1)
                    entries[i] = CpEntry(tag = tag, payloadOffset = pos, index1 = idx1, index2 = idx2)
                    pos += 3
                }
                else -> error("Unsupported constant pool tag $tag at offset ${pos - 1}")
            }
            i++
        }
        return ParsedConstantPool(entries = entries, cpEndOffset = pos)
    }

    private fun findMemberRefIndex(
        cp: Array<CpEntry?>,
        tag: Int,
        ownerInternalName: String,
        memberName: String,
        memberDesc: String
    ): Int {
        for (idx in 1 until cp.size) {
            val entry = cp[idx] ?: continue
            if (entry.tag != tag) continue
            val classEntry = cp[entry.index1] ?: continue
            val natEntry = cp[entry.index2] ?: continue
            val className = cp[classEntry.index1]?.utf8
            val name = cp[natEntry.index1]?.utf8
            val desc = cp[natEntry.index2]?.utf8
            if (className == ownerInternalName && name == memberName && desc == memberDesc) {
                return idx
            }
        }
        error(
            "Constant pool entry (tag=$tag) not found for $ownerInternalName.$memberName:$memberDesc"
        )
    }

    private fun readU2(bytes: ByteArray, offset: Int): Int {
        return ((bytes[offset].toInt() and 0xFF) shl 8) or
            (bytes[offset + 1].toInt() and 0xFF)
    }

    private fun readU4(bytes: ByteArray, offset: Int): Int {
        return ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)
    }
}
