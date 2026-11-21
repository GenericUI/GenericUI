package com.nexustools.genericui.plugin

import java.io.File

/**
 * Generates and compiles standalone native desktop resource tables for shared libraries
 * (`genericui`, `genericui-ext-<ext>`) and consumer host executables across Windows (`RT_RCDATA`),
 * macOS (Mach-O `__TEXT,__const`), and Linux ELF (`.rodata`), exporting C lookup symbols keyed by
 * prefix-free [com.nexustools.genericui.resources.ResourceID.nativeResourceKey].
 */
object GenericUiNativeResourceEmbedder {

    fun lookupSymbolForGroup(groupId: String): String {
        val trimmed = groupId.trim()
        if (trimmed.isEmpty() || trimmed.equals("app", ignoreCase = true)) {
            return "genericui_res_lookup_app"
        }
        return "genericui_res_lookup_${GenericUiModuleResourceManifestParser.sanitizeFlatIdentifier(trimmed)}"
    }

    fun generateWindowsRcSource(
        resources: Map<String, ByteArray>,
        payloadOutputDir: File
    ): String {
        payloadOutputDir.mkdirs()
        val sortedKeys = resources.keys.sorted()
        return buildString {
            appendLine("#pragma code_page(65001)")
            appendLine("/* Auto-generated GenericUI Win32 RT_RCDATA module resources. */")
            for (key in sortedKeys) {
                val safeKey = key.trim().ifEmpty { continue }
                val bytes = resources.getValue(key)
                val binFile = File(payloadOutputDir, "$safeKey.bin")
                binFile.parentFile?.mkdirs()
                binFile.writeBytes(bytes)
                val escapedPath = binFile.absolutePath.replace("\\", "\\\\").replace("\"", "\\\"")
                appendLine("$safeKey RCDATA \"$escapedPath\"")
            }
        }
    }

    fun generatePosixResourceTableCSource(
        lookupSymbol: String,
        resources: Map<String, ByteArray>,
        includeMachOConstSection: Boolean = true
    ): String = buildString {
        val sortedKeys = resources.keys.sorted()
        appendLine("/* Auto-generated GenericUI native binary resource table. */")
        appendLine()
        appendLine("static int genericui_res_streq(const char* a, const char* b) {")
        appendLine("    if (a == (const char*)0 || b == (const char*)0) return 0;")
        appendLine("    while (*a && *b) {")
        appendLine("        if (*a != *b) return 0;")
        appendLine("        ++a;")
        appendLine("        ++b;")
        appendLine("    }")
        appendLine("    return (*a == 0 && *b == 0) ? 1 : 0;")
        appendLine("}")
        appendLine()

        val sectionPragmaPrefix = if (includeMachOConstSection) {
            "#ifdef __APPLE__\n#define GENERICUI_CONST_SECTION __attribute__((section(\"__TEXT,__const\")))\n#else\n#define GENERICUI_CONST_SECTION\n#endif\n"
        } else {
            "#define GENERICUI_CONST_SECTION\n"
        }
        appendLine(sectionPragmaPrefix)

        sortedKeys.forEachIndexed { idx, key ->
            val bytes = resources.getValue(key)
            val arrName = "genericui_res_blob_$idx"
            append("static const unsigned char GENERICUI_CONST_SECTION $arrName[")
            append(bytes.size.coerceAtLeast(1))
            append("] = {")
            if (bytes.isEmpty()) {
                append("0")
            } else {
                for (bIdx in bytes.indices) {
                    if (bIdx > 0) append(',')
                    if (bIdx % 24 == 0) append("\n    ")
                    append("0x")
                    append((bytes[bIdx].toInt() and 0xFF).toString(16).padStart(2, '0'))
                }
            }
            appendLine("\n};")
        }
        appendLine()
        appendLine("#if defined(_WIN32) || defined(__CYGWIN__)")
        appendLine("#define GENERICUI_EXPORT __declspec(dllexport)")
        appendLine("#else")
        appendLine("#define GENERICUI_EXPORT __attribute__((visibility(\"default\"), used))")
        appendLine("#endif")
        appendLine()
        appendLine("GENERICUI_EXPORT const unsigned char* $lookupSymbol(const char* key, int* out_size) {")
        appendLine("    if (out_size) *out_size = 0;")
        appendLine("    if (key == (const char*)0) return (const unsigned char*)0;")
        sortedKeys.forEachIndexed { idx, key ->
            val bytes = resources.getValue(key)
            val escKey = key.replace("\\", "\\\\").replace("\"", "\\\"")
            appendLine("    if (genericui_res_streq(key, \"$escKey\")) {")
            appendLine("        if (out_size) *out_size = ${bytes.size};")
            appendLine("        return genericui_res_blob_$idx;")
            appendLine("    }")
        }
        appendLine("    return (const unsigned char*)0;")
        appendLine("}")
    }

    fun targetTripleForKonanTarget(konanTargetName: String): String? = when (konanTargetName) {
        "macos_arm64" -> "arm64-apple-darwin"
        "macos_x64" -> "x86_64-apple-darwin"
        "linux_x64" -> "x86_64-unknown-linux-gnu"
        "linux_arm64" -> "aarch64-unknown-linux-gnu"
        "mingw_x64" -> "x86_64-w64-mingw32"
        else -> null
    }

    fun compileCSourceToObject(
        cFile: File,
        objOut: File,
        konanTargetName: String,
        clangExecutable: File? = null
    ): Boolean {
        val clangExe = clangExecutable?.takeIf { it.isFile }
            ?: GenericUiDesktopResourceTask.findClangPreprocessorExecutable()
            ?: return false
        val triple = targetTripleForKonanTarget(konanTargetName) ?: return false
        objOut.parentFile?.mkdirs()
        if (objOut.exists()) {
            objOut.delete()
        }
        val cmd = buildList {
            add(clangExe.absolutePath)
            add("-target")
            add(triple)
            add("-c")
            add("-O2")
            if (konanTargetName.startsWith("linux")) {
                add("-fPIC")
                add("-fvisibility=hidden")
            } else if (konanTargetName.startsWith("macos")) {
                add("-fvisibility=hidden")
            }
            add("-o")
            add(objOut.absolutePath)
            add(cFile.absolutePath)
        }
        return runCatching {
            val process = ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start()
            process.inputStream.bufferedReader().readText()
            val exit = process.waitFor()
            exit == 0 && objOut.isFile && objOut.length() > 0L
        }.getOrDefault(false)
    }

    fun compileWindowsRcToObject(
        rcFile: File,
        objOut: File,
        includeDir: File,
        windresExecutable: File? = null,
        clangExecutable: File? = null
    ): Boolean {
        val windres = windresExecutable?.takeIf { it.isFile }
            ?: GenericUiDesktopResourceTask.findWindresExecutable()
            ?: return false
        val clangExe = clangExecutable?.takeIf { it.isFile }
            ?: GenericUiDesktopResourceTask.findClangPreprocessorExecutable()
        objOut.parentFile?.mkdirs()
        if (objOut.exists()) {
            objOut.delete()
        }
        val attempts = buildList {
            if (clangExe != null) {
                add(
                    listOf(
                        windres.absolutePath,
                        "--preprocessor=${clangExe.absolutePath}",
                        "--preprocessor-arg=-E",
                        "--preprocessor-arg=-xc-header",
                        "--preprocessor-arg=-DRC_INVOKED",
                        "-I", includeDir.absolutePath,
                        "-i", rcFile.absolutePath,
                        "-O", "coff",
                        "-o", objOut.absolutePath
                    )
                )
            }
            add(
                listOf(
                    windres.absolutePath,
                    "-I", includeDir.absolutePath,
                    "-i", rcFile.absolutePath,
                    "-O", "coff",
                    "-o", objOut.absolutePath
                )
            )
        }
        for (cmd in attempts) {
            val ok = runCatching {
                val p = ProcessBuilder(cmd)
                    .directory(includeDir)
                    .redirectErrorStream(true)
                    .start()
                p.inputStream.bufferedReader().readText()
                p.waitFor() == 0 && objOut.isFile && objOut.length() > 0L
            }.getOrDefault(false)
            if (ok) return true
        }
        return false
    }
}
