package com.nexustools.genericui.jvm

import java.io.File
import java.net.URLClassLoader
import java.nio.file.Files
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private object JniLibraryLoaderProbe {
    @JvmStatic
    external fun nativeAdd(a: Int, b: Int): Int
}

class JniLibraryLoaderTest {

    @Test
    fun testOsArchNormalizationAndResourcePaths() {
        assertEquals("windows", JniLibraryLoader.normalizedOsName("Windows 11"))
        assertEquals("macos", JniLibraryLoader.normalizedOsName("Mac OS X"))
        assertEquals("macos", JniLibraryLoader.normalizedOsName("Darwin"))
        assertEquals("linux", JniLibraryLoader.normalizedOsName("Linux"))

        assertEquals("x86_64", JniLibraryLoader.normalizedArchName("amd64"))
        assertEquals("x86_64", JniLibraryLoader.normalizedArchName("x86_64"))
        assertEquals("aarch64", JniLibraryLoader.normalizedArchName("aarch64"))
        assertEquals("aarch64", JniLibraryLoader.normalizedArchName("arm64"))
        assertEquals("x86", JniLibraryLoader.normalizedArchName("i686"))
        assertEquals("x86", JniLibraryLoader.normalizedArchName("x86"))
        assertEquals("arm", JniLibraryLoader.normalizedArchName("armeabi-v7a"))

        assertEquals("windows-x86_64", JniLibraryLoader.osArchTag("Windows 11", "amd64"))
        assertEquals("macos-aarch64", JniLibraryLoader.osArchTag("Mac OS X", "arm64"))
        assertEquals("linux-x86_64", JniLibraryLoader.osArchTag("Linux", "x86_64"))

        assertEquals("sample.dll", JniLibraryLoader.mapLibraryName("sample", "Windows 11"))
        assertEquals("libsample.dylib", JniLibraryLoader.mapLibraryName("sample", "Mac OS X"))
        assertEquals("libsample.so", JniLibraryLoader.mapLibraryName("sample", "Linux"))

        assertEquals(
            "META-INF/native/windows-x86_64/sample.dll",
            JniLibraryLoader.resolveNativeResourcePath("sample", "Windows 11", "amd64")
        )
        assertEquals(
            "META-INF/native/macos-aarch64/libsample.dylib",
            JniLibraryLoader.resolveNativeResourcePath("sample", "Mac OS X", "arm64")
        )
        assertEquals(
            "META-INF/native/linux-x86_64/libsample.so",
            JniLibraryLoader.resolveNativeResourcePath("sample", "Linux", "amd64")
        )
    }

    @Test
    fun testBlankAndMissingLibraryErrors() {
        assertFailsWith<IllegalArgumentException> {
            JniLibraryLoader.load("   ")
        }

        val missingName = "genericui_missing_native_probe_${System.nanoTime()}"
        assertFalse(JniLibraryLoader.isLoaded(missingName))
        val err = assertFailsWith<UnsatisfiedLinkError> {
            JniLibraryLoader.load(missingName)
        }
        val msg = err.message.orEmpty()
        assertTrue(msg.contains(missingName), "Expected library name in error: $msg")
        assertTrue(msg.contains("META-INF/native/"), "Expected resource fallback path in error: $msg")
        assertFalse(JniLibraryLoader.isLoaded(missingName))
    }

    @Test
    fun testRealCompiledSharedLibraryJarFallbackLoadAndInvocation() {
        val tempDir = Files.createTempDirectory("genericui_jni_loader_test").toFile()
        try {
            val libName = "genericui_loader_probe_${System.nanoTime()}"
            val mappedName = JniLibraryLoader.mapLibraryName(libName)
            val resourcePath = JniLibraryLoader.resolveNativeResourcePath(libName)
            val compiledLib = File(tempDir, mappedName)

            val jniHeaders = findHostJniHeaders()
            val cSource = File(tempDir, "probe.c")
            cSource.writeText(
                buildString {
                    appendLine("#include <stdint.h>")
                    if (jniHeaders != null) {
                        appendLine("#include <jni.h>")
                    }
                    appendLine("#ifdef _WIN32")
                    appendLine("#define GENERICUI_EXPORT __declspec(dllexport)")
                    appendLine("#else")
                    appendLine("#define GENERICUI_EXPORT __attribute__((visibility(\"default\")))")
                    appendLine("#endif")
                    if (jniHeaders != null) {
                        appendLine("JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {")
                        appendLine("    (void)vm;")
                        appendLine("    (void)reserved;")
                        appendLine("    return JNI_VERSION_1_6;")
                        appendLine("}")
                        appendLine(
                            "JNIEXPORT jint JNICALL Java_com_nexustools_genericui_jvm_JniLibraryLoaderProbe_nativeAdd("
                        )
                        appendLine("    JNIEnv* env, jclass cls, jint a, jint b")
                        appendLine(") {")
                        appendLine("    (void)env;")
                        appendLine("    (void)cls;")
                        appendLine("    return a + b;")
                        appendLine("}")
                    } else {
                        appendLine(
                            "GENERICUI_EXPORT int32_t Java_com_nexustools_genericui_jvm_JniLibraryLoaderProbe_nativeAdd("
                        )
                        appendLine("    void* env, void* cls, int32_t a, int32_t b")
                        appendLine(") {")
                        appendLine("    (void)env;")
                        appendLine("    (void)cls;")
                        appendLine("    return a + b;")
                        appendLine("}")
                    }
                }
            )

            compileRealHostSharedLibrary(cSource, compiledLib, jniHeaders)
            assertTrue(compiledLib.isFile && compiledLib.length() > 0L, "Compiled shared library missing: $compiledLib")

            val bundleJar = File(tempDir, "native-bundle.jar")
            JarOutputStream(bundleJar.outputStream().buffered()).use { jos ->
                jos.putNextEntry(JarEntry(resourcePath))
                compiledLib.inputStream().use { it.copyTo(jos) }
                jos.closeEntry()
            }

            URLClassLoader(arrayOf(bundleJar.toURI().toURL()), null).use { isolatedLoader ->
                val threadCount = 4
                val startLatch = CountDownLatch(1)
                val pool = Executors.newFixedThreadPool(threadCount)
                try {
                    val futures = (0 until threadCount).map {
                        pool.submit(
                            Callable {
                                startLatch.await()
                                JniLibraryLoader.load(libName, isolatedLoader)
                            }
                        )
                    }
                    startLatch.countDown()
                    futures.forEach { it.get() }
                } finally {
                    pool.shutdownNow()
                }

                // Subsequent idempotent call without classloader succeeds immediately once loaded.
                JniLibraryLoader.load(libName)
                assertTrue(JniLibraryLoader.isLoaded(libName))
                assertEquals(42, JniLibraryLoaderProbe.nativeAdd(19, 23))

                val expectedCacheDir = File(
                    System.getProperty("java.io.tmpdir"),
                    "genericui-jni/${JniLibraryLoader.osArchTag()}"
                )
                assertTrue(expectedCacheDir.isDirectory, "Expected cache dir at $expectedCacheDir")
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun compileRealHostSharedLibrary(
        sourceFile: File,
        outputFile: File,
        jniHeaders: Pair<File, File>?
    ) {
        val os = JniLibraryLoader.normalizedOsName()
        val arch = JniLibraryLoader.normalizedArchName()
        val konanDeps = File(System.getProperty("user.home"), ".konan/dependencies")
        val exeSuffix = if (os == "windows") ".exe" else ""

        val konanLlvm = konanDeps.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith("llvm-") && File(it, "bin/clang$exeSuffix").isFile }
            ?.maxByOrNull { it.name }
        val konanClang = konanLlvm?.let { File(it, "bin/clang$exeSuffix") }

        val command = mutableListOf<String>()
        when (os) {
            "windows" -> {
                val mingw = konanDeps.listFiles()
                    ?.firstOrNull { it.isDirectory && it.name.startsWith("msys2-mingw-w64-") }
                val compiler = konanClang ?: findOnPath("clang.exe", "gcc.exe")
                    ?: error("No Windows C compiler available for JNI test.")
                command.add(compiler.absolutePath)
                if (konanClang != null && mingw != null) {
                    val triple = if (arch == "aarch64") "aarch64-pc-windows-gnu" else "x86_64-pc-windows-gnu"
                    command.add("-target")
                    command.add(triple)
                    command.add("--sysroot=${mingw.absolutePath}")
                    command.add("-fuse-ld=lld")
                }
                command.add("-shared")
            }

            "macos" -> {
                val compiler = findOnPath("clang", "gcc", "cc") ?: konanClang
                    ?: error("No macOS C compiler available for JNI test.")
                command.add(compiler.absolutePath)
                command.add("-dynamiclib")
                command.add("-fPIC")
            }

            else -> {
                val compiler = konanClang ?: findOnPath("clang", "gcc", "cc")
                    ?: error("No Linux C compiler available for JNI test.")
                command.add(compiler.absolutePath)
                command.add("-shared")
                command.add("-fPIC")
            }
        }

        if (jniHeaders != null) {
            command.add("-I${jniHeaders.first.absolutePath}")
            command.add("-I${jniHeaders.second.absolutePath}")
        }
        command.add("-O2")
        command.add(sourceFile.absolutePath)
        command.add("-o")
        command.add(outputFile.absolutePath)

        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        assertEquals(0, exitCode, "C compilation failed (${command.joinToString(" ")}):\n$output")
    }

    private fun findHostJniHeaders(): Pair<File, File>? {
        val mdSubdir = when (JniLibraryLoader.normalizedOsName()) {
            "windows" -> "win32"
            "macos" -> "darwin"
            else -> "linux"
        }
        val candidates = LinkedHashSet<File>()
        fun addHome(home: String?) {
            if (home.isNullOrBlank()) return
            val dir = File(home.trim())
            candidates.add(File(dir, "include"))
            dir.parentFile?.let { candidates.add(File(it, "include")) }
        }
        addHome(System.getenv("JAVA_HOME"))
        addHome(System.getProperty("java.home"))

        val userHome = System.getProperty("user.home").orEmpty()
        val roots = listOfNotNull(
            System.getenv("GRADLE_USER_HOME")?.takeIf { it.isNotBlank() }?.let { File(it, "jdks") },
            if (userHome.isNotEmpty()) File(userHome, ".gradle/jdks") else null,
            if (userHome.isNotEmpty()) File(userHome, ".jdks") else null
        )
        for (root in roots) {
            if (!root.isDirectory) continue
            root.walkTopDown().maxDepth(4).filter { it.isDirectory && it.name == "include" }.forEach {
                candidates.add(it)
            }
        }
        return candidates.firstNotNullOfOrNull { inc ->
            val jniH = File(inc, "jni.h")
            val mdDir = File(inc, mdSubdir)
            val mdH = File(mdDir, "jni_md.h")
            if (jniH.isFile && mdH.isFile) inc to mdDir else null
        }
    }

    private fun findOnPath(vararg names: String): File? {
        val path = System.getenv("PATH").orEmpty().lowercase(Locale.ROOT)
        val rawPath = System.getenv("PATH").orEmpty()
        if (path.isEmpty()) return null
        for (entry in rawPath.split(File.pathSeparatorChar)) {
            if (entry.isBlank()) continue
            val dir = File(entry)
            if (!dir.isDirectory) continue
            for (name in names) {
                val f = File(dir, name)
                if (f.isFile) return f
            }
        }
        return null
    }
}
