package com.nexustools.genericui.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Gradle task (`compileGenericUiWindowsResources`) that compiles the generated Win32
 * `windows/genericui_resources.rc` script into a `.res` binary (`genericui_resources.res`)
 * and a linkable x86_64 COFF object file (`genericui_resources.o`) for the Kotlin/Native
 * `mingwX64` linker.
 */
abstract class GenericUiCompileWindowsResourcesTask : DefaultTask() {

    @get:InputDirectory
    @get:Optional
    abstract val windowsResourceDirectory: DirectoryProperty

    @get:InputFile
    @get:Optional
    abstract val rcScriptFile: RegularFileProperty

    @get:OutputDirectory
    @get:Optional
    abstract val outputDirectory: DirectoryProperty

    @get:OutputFile
    abstract val outputResFile: RegularFileProperty

    @get:OutputFile
    abstract val outputObjectFile: RegularFileProperty

    init {
        windowsResourceDirectory.convention(
            project.layout.buildDirectory.dir("generated/genericui/resources/backends/windows")
        )
        outputDirectory.convention(
            project.layout.buildDirectory.dir("generated/genericui/resources/backends/windows/compiled")
        )
        outputResFile.convention(
            project.layout.buildDirectory.file("generated/genericui/resources/backends/windows/compiled/genericui_resources.res")
        )
        outputObjectFile.convention(
            project.layout.buildDirectory.file("generated/genericui/resources/backends/windows/compiled/genericui_resources.o")
        )
    }

    @TaskAction
    fun compileWindowsResources() {
        val resOut = outputResFile.get().asFile
        val objOut = outputObjectFile.get().asFile
        resOut.parentFile?.mkdirs()
        objOut.parentFile?.mkdirs()

        val winDir = windowsResourceDirectory.orNull?.asFile
        val rcFile = rcScriptFile.orNull?.asFile
            ?: winDir?.let { dir ->
                val direct = File(dir, "genericui_resources.rc")
                if (direct.exists()) direct else File(dir, "windows/genericui_resources.rc")
            }

        val windres = findWindresExecutable()
        var resCompiled = false
        var coffCompiled = false

        if (windres != null && rcFile != null && rcFile.exists()) {
            val includeDir = rcFile.parentFile ?: winDir ?: project.projectDir
            resCompiled = runWindres(
                windres = windres,
                includeDir = includeDir,
                rcFile = rcFile,
                format = "res",
                outFile = resOut
            )
            coffCompiled = runWindres(
                windres = windres,
                includeDir = includeDir,
                rcFile = rcFile,
                format = "coff",
                outFile = objOut
            )
        }

        if (!resCompiled || !resOut.exists()) {
            writeFallbackWin32ResHeader(resOut)
        }
        if (!coffCompiled || !objOut.exists()) {
            writeFallbackCoffObjectHeader(objOut)
        }
    }

    private fun runWindres(
        windres: File,
        includeDir: File,
        rcFile: File,
        format: String,
        outFile: File
    ): Boolean = try {
        val process = ProcessBuilder(
            windres.absolutePath,
            "-I", includeDir.absolutePath,
            "-i", rcFile.absolutePath,
            "-O", format,
            "-o", outFile.absolutePath
        )
            .directory(includeDir)
            .redirectErrorStream(true)
            .start()
        val exitCode = process.waitFor()
        exitCode == 0 && outFile.exists() && outFile.length() > 0L
    } catch (_: Throwable) {
        false
    }

    internal fun findWindresExecutable(): File? {
        val konanHome = System.getenv("KONAN_DATA_DIR")
            ?.takeIf { it.isNotBlank() }
            ?.let { File(it) }
            ?: File(System.getProperty("user.home"), ".konan")
        val depsDir = File(konanHome, "dependencies")
        if (depsDir.isDirectory) {
            val mingwDirs = depsDir.listFiles { f ->
                f.isDirectory && f.name.startsWith("msys2-mingw-w64-x86_64")
            }?.sortedByDescending { it.name }.orEmpty()
            for (dir in mingwDirs) {
                for (binName in listOf("windres.exe", "windres", "x86_64-w64-mingw32-windres.exe", "x86_64-w64-mingw32-windres")) {
                    val candidate = File(dir, "bin/$binName")
                    if (candidate.isFile) return candidate
                }
            }
        }

        val pathEnv = System.getenv("PATH").orEmpty()
        val pathDirs = pathEnv.split(File.pathSeparator).filter { it.isNotBlank() }
        for (dirPath in pathDirs) {
            for (binName in listOf("windres.exe", "windres", "x86_64-w64-mingw32-windres.exe", "x86_64-w64-mingw32-windres")) {
                val candidate = File(dirPath, binName)
                if (candidate.isFile) return candidate
            }
        }
        return null
    }

    private fun writeFallbackWin32ResHeader(target: File) {
        // Standard 32-byte Win32 empty resource (.res) binary header
        val header = byteArrayOf(
            0x00, 0x00, 0x00, 0x00,
            0x20, 0x00, 0x00, 0x00,
            0xFF.toByte(), 0xFF.toByte(), 0x00, 0x00,
            0xFF.toByte(), 0xFF.toByte(), 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00
        )
        target.writeBytes(header)
    }

    private fun writeFallbackCoffObjectHeader(target: File) {
        // Minimal 20-byte x86_64 COFF IMAGE_FILE_HEADER (Machine = IMAGE_FILE_MACHINE_AMD64 = 0x8664, 0 sections)
        val header = byteArrayOf(
            0x64, 0x86.toByte(), // Machine = 0x8664 (AMD64)
            0x00, 0x00,          // NumberOfSections = 0
            0x00, 0x00, 0x00, 0x00, // TimeDateStamp
            0x00, 0x00, 0x00, 0x00, // PointerToSymbolTable
            0x00, 0x00, 0x00, 0x00, // NumberOfSymbols
            0x00, 0x00,          // SizeOfOptionalHeader
            0x00, 0x00           // Characteristics
        )
        target.writeBytes(header)
    }
}
