package com.nexustools.genericui.plugin

import com.nexustools.genericui.plugin.nativebuild.GenericUiArchiveExtractor
import com.nexustools.genericui.plugin.nativebuild.GenericUiCompileJniTask
import com.nexustools.genericui.plugin.nativebuild.GenericUiFetchNativeSourceArchiveTask
import com.nexustools.genericui.plugin.nativebuild.GenericUiJniToolchainResolver
import com.nexustools.genericui.plugin.nativebuild.GenericUiNativeBuildResolver
import com.sun.net.httpserver.HttpServer
import org.gradle.api.Project
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.tasks.testing.Test as GradleTestTask
import org.gradle.testfixtures.ProjectBuilder
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetSocketAddress
import java.net.URLClassLoader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.Properties
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.tools.ToolProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GenericUiJniBuildSystemTest {

    @Test
    fun testDslScopesMergingAndGradleTaskWiring() {
        val projectDir = Files.createTempDirectory("genericui_jni_dsl_test").toFile()
        try {
            File(projectDir, "native").mkdirs()
            File(projectDir, "native/probe.c").writeText("int probe(void) { return 1; }\n")
            File(projectDir, "native/probe.h").writeText("int probe(void);\n")
            File(projectDir, "native/probe.def").writeText("headers = probe.h\n")
            File(projectDir, "native/swing.c").writeText("int swing_only(void) { return 2; }\n")
            File(projectDir, "native/CMakeLists.txt").writeText("cmake_minimum_required(VERSION 3.18)\nproject(cmprobe C)\n")
            File(projectDir, "prebuilts/global/arm64-v8a").mkdirs()

            val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
            project.pluginManager.apply("com.nexustools.genericui")
            val ext = project.extensions.getByType(GenericUiExtension::class.java)

            ext.backends {
                android(true)
                swing(true)
                macos(true)
                windows(true)
                gtk(true)
                ios(true)
            }

            ext.jni {
                jniLibsDirs("prebuilts/global")
                library("probe") {
                    sources("native/probe.c")
                    includeDirs("native")
                    defines("BASE=1")
                    abis("arm64-v8a", "armeabi-v7a")
                    cStandard = "c11"
                    cppStandard = "c++17"
                    pageSize16Kb = true
                }
                cmake("cmprobe") {
                    listsFile("native/CMakeLists.txt")
                    arguments("-DFOO=ON")
                }
            }
            ext.cinterops {
                create("nativeprobe") {
                    defFile("native/probe.def")
                    packageName("com.example.nativeprobe")
                    headers("native/probe.h")
                    includeDirs("native")
                    compilerOpts("-DTEST_OPT=1")
                    extraOpts("-verbose")
                }
            }
            ext.nativeLibraries {
                link("m", "-Wl,-z,now")
            }

            ext.android {
                jni {
                    library("probe") {
                        defines("ANDROID_EXTRA=2")
                        androidLinkLibraries("log")
                        abis("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
                    }
                }
                nativeLibraries { link("android") }
            }
            ext.swing {
                jni {
                    library("swing_only") {
                        sources("native/swing.c")
                    }
                }
            }
            ext.nativeDesktopCommon {
                nativeLibraries { link("pthread") }
            }
            ext.macos {
                nativeLibraries { linkerOpts("-framework", "CoreFoundation") }
            }
            ext.windows {
                nativeLibraries { link("winmm") }
            }
            ext.linuxCommon {
                nativeLibraries { link("dl") }
            }
            ext.iosCommon {
                nativeLibraries { linkerOpts("-framework", "Foundation") }
            }

            (project as ProjectInternal).evaluate()

            val mergedLibs = GenericUiNativeBuildResolver.resolveJniLibraries(project, ext)
            val probe = mergedLibs.first { it.name == "probe" }
            assertEquals("1", probe.defines["BASE"])
            assertEquals("2", probe.defines["ANDROID_EXTRA"])
            assertTrue(probe.androidLinkLibraries.contains("log"))
            assertEquals(listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86"), probe.abis)
            assertTrue(probe.buildHostLibraryForTests)
            assertTrue(probe.bundleInSwingJar)
            assertTrue(probe.buildAndroidLibraries)

            val swingOnly = mergedLibs.first { it.name == "swing_only" }
            assertFalse(swingOnly.buildAndroidLibraries)
            assertTrue(swingOnly.buildHostLibraryForTests)
            assertTrue(swingOnly.bundleInSwingJar)

            val mergedCmake = GenericUiNativeBuildResolver.resolveJniCmakes(project, ext).first { it.name == "cmprobe" }
            assertTrue(mergedCmake.arguments.contains("-DFOO=ON"))

            val prebuiltPaths = GenericUiNativeBuildResolver.resolveJniLibsDirPaths(project, ext)
            assertTrue(prebuiltPaths.any { File(it).name == "global" })

            val fetchTask = project.tasks.getByName("fetchGenericUiNativeSources") as GenericUiFetchNativeSourceArchiveTask
            val compileTask = project.tasks.getByName("compileGenericUiJniLibraries") as GenericUiCompileJniTask
            assertTrue(
                compileTask.taskDependencies.getDependencies(compileTask).contains(fetchTask),
                "compileGenericUiJniLibraries must depend on fetchGenericUiNativeSources"
            )

            val apkTasks = project.tasks.matching {
                it.name.startsWith("packageGenericUiAndroid") && it.name.endsWith("Apk")
            }.toList()
            assertTrue(apkTasks.isNotEmpty(), "Expected packageGenericUiAndroid*Apk tasks")
            apkTasks.forEach { apkTask ->
                assertTrue(
                    apkTask.taskDependencies.getDependencies(apkTask).contains(compileTask),
                    "${apkTask.name} must depend on compileGenericUiJniLibraries"
                )
            }

            val jarTasks = project.tasks.matching {
                it.name.startsWith("packageGenericUiSwing") && it.name.endsWith("Jar")
            }.toList()
            assertTrue(jarTasks.isNotEmpty(), "Expected packageGenericUiSwing*Jar tasks")
            jarTasks.forEach { jarTask ->
                assertTrue(
                    jarTask.taskDependencies.getDependencies(jarTask).contains(compileTask),
                    "${jarTask.name} must depend on compileGenericUiJniLibraries"
                )
            }

            // Register a Test task after plugin evaluation to verify configureEach wiring for
            // jvmTest / testAndroidHostTest / testDebugUnitTest / testReleaseUnitTest.
            val syntheticUnitTest = project.tasks.create("testDebugUnitTest", GradleTestTask::class.java)
            assertTrue(
                syntheticUnitTest.taskDependencies.getDependencies(syntheticUnitTest).contains(compileTask),
                "testDebugUnitTest must depend on compileGenericUiJniLibraries"
            )
            // Execute only the prepended doFirst injection action so java.library.path and PATH/LD/DYLD are verified.
            syntheticUnitTest.actions.first().execute(syntheticUnitTest)
            val expectedHostDir = project.layout.buildDirectory.dir(
                "intermediates/genericui_jni/host/${GenericUiJniToolchainResolver.hostOsArchTag()}"
            ).get().asFile.absolutePath
            val libPath = syntheticUnitTest.systemProperties["java.library.path"]?.toString().orEmpty()
            assertTrue(libPath.contains(expectedHostDir), "Expected $expectedHostDir in java.library.path: $libPath")
        } finally {
            projectDir.deleteRecursively()
        }
    }

    @Test
    fun testRemoteSourceArchiveFetchShaVerificationOfflineReuseAndKonanDepCache() {
        val tempDir = Files.createTempDirectory("genericui_archive_fetch_test").toFile()
        try {
            val archiveEntries = linkedMapOf(
                "libprobe-1.2.3/include/probe_math.h" to
                    "#pragma once\nint probe_triple(int x);\n".toByteArray(StandardCharsets.UTF_8),
                "libprobe-1.2.3/src/probe_math.c" to
                    "#include \"probe_math.h\"\nint probe_triple(int x) { return x * 3; }\n".toByteArray(StandardCharsets.UTF_8),
                "libprobe-1.2.3/src/probe_demo.c" to
                    "#error demo should be excluded\n".toByteArray(StandardCharsets.UTF_8)
            )
            val tarGzBytes = buildTarGzArchive(archiveEntries)
            val validSha256 = GenericUiArchiveExtractor.sha256Hex(tarGzBytes)

            val konanMockZipBytes = buildZipArchive(
                linkedMapOf(
                    "target-sysroot-1-testmock/usr/lib/aarch64-linux-android/24/crtbegin_so.o" to
                        byteArrayOf(0x7F, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte())
                )
            )

            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            server.createContext("/libprobe-1.2.3.tar.gz") { exchange ->
                exchange.sendResponseHeaders(200, tarGzBytes.size.toLong())
                exchange.responseBody.use { it.write(tarGzBytes) }
            }
            server.createContext("/konan/target-sysroot-1-testmock.zip") { exchange ->
                exchange.sendResponseHeaders(200, konanMockZipBytes.size.toLong())
                exchange.responseBody.use { it.write(konanMockZipBytes) }
            }
            server.start()

            val port = server.address.port
            val baseUrl = "http://127.0.0.1:$port"
            val gradleUserHome = File(tempDir, "gradle-home")
            val projectDir = File(tempDir, "project").apply { mkdirs() }

            // 1. Mismatched SHA-256 must fail strictly.
            val badProject = ProjectBuilder.builder()
                .withProjectDir(projectDir)
                .withGradleUserHomeDir(gradleUserHome)
                .build()
            badProject.pluginManager.apply("com.nexustools.genericui")
            val badExt = badProject.extensions.getByType(GenericUiExtension::class.java)
            badExt.jni {
                library("remoteprobe") {
                    sourceArchive(
                        url = "$baseUrl/libprobe-1.2.3.tar.gz",
                        sha256 = "0000000000000000000000000000000000000000000000000000000000000000",
                        stripComponents = 1
                    )
                    archiveSources("src/*.c")
                    exclude("src/*_demo.c")
                }
            }
            (badProject as ProjectInternal).evaluate()
            val badFetch = badProject.tasks.getByName("fetchGenericUiNativeSources") as GenericUiFetchNativeSourceArchiveTask
            assertFailsWith<Exception> {
                badFetch.fetchSources()
            }

            // 2. Valid SHA-256 downloads, extracts with stripComponents=1, and writes manifest.
            val goodProject = ProjectBuilder.builder()
                .withProjectDir(projectDir)
                .withGradleUserHomeDir(gradleUserHome)
                .build()
            goodProject.pluginManager.apply("com.nexustools.genericui")
            val goodExt = goodProject.extensions.getByType(GenericUiExtension::class.java)
            goodExt.jni {
                library("remoteprobe") {
                    sourceArchive(
                        url = "$baseUrl/libprobe-1.2.3.tar.gz",
                        sha256 = validSha256,
                        stripComponents = 1
                    )
                    archiveSources("src/*.c")
                    exclude("src/*_demo.c")
                    archiveIncludeDirs("include")
                }
            }
            (goodProject as ProjectInternal).evaluate()
            val goodFetch = goodProject.tasks.getByName("fetchGenericUiNativeSources") as GenericUiFetchNativeSourceArchiveTask
            goodFetch.fetchSources()

            val manifestFile = goodFetch.sourcesManifestFile.get().asFile
            val mappedPaths = manifestFile.readLines(StandardCharsets.UTF_8)
                .filter { it.contains('\t') }
                .associate { line ->
                    val parts = line.split('\t', limit = 2)
                    parts[0] to parts[1]
                }
            val extractedRoot = mappedPaths["remoteprobe"]
            assertNotNull(extractedRoot, "Expected remoteprobe entry in manifest")
            assertTrue(File(extractedRoot, "include/probe_math.h").isFile)
            assertTrue(File(extractedRoot, "src/probe_math.c").isFile)

            // 3. Test Konan dependency resolver download & caching against HttpServer.
            val mockKonanDeps = File(tempDir, "konan-deps").apply { mkdirs() }
            val downloadedDep = GenericUiJniToolchainResolver.ensureKonanDependencyDir(
                depsDir = mockKonanDeps,
                depName = "target-sysroot-1-testmock",
                fallbackPrefix = "target-sysroot-",
                fallbackSuffix = "-testmock",
                dependenciesUrl = "$baseUrl/konan",
                allowNetworkDownload = true
            )
            assertNotNull(downloadedDep)
            assertTrue(
                File(downloadedDep, "usr/lib/aarch64-linux-android/24/crtbegin_so.o").isFile
            )

            // 4. Stop HTTP server and verify both tasks work offline from cache.
            server.stop(0)

            goodFetch.fetchSources()
            assertTrue(File(extractedRoot, "src/probe_math.c").isFile)

            val offlineDep = GenericUiJniToolchainResolver.ensureKonanDependencyDir(
                depsDir = mockKonanDeps,
                depName = "target-sysroot-1-testmock",
                fallbackPrefix = "target-sysroot-",
                fallbackSuffix = "-testmock",
                dependenciesUrl = "$baseUrl/konan",
                allowNetworkDownload = false
            )
            assertNotNull(offlineDep)
            assertEquals(downloadedDep.absolutePath, offlineDep.absolutePath)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testRealAndroid4Abi16KbElfAndHostDllCompilationIncrementalCacheAndJvmInvocation() {
        val tempDir = Files.createTempDirectory("genericui_real_native_compile_test").toFile()
        try {
            val gradleUserHome = File(System.getProperty("user.home"), ".gradle")
            val projectDir = File(tempDir, "consumer-app").apply { mkdirs() }
            File(projectDir, "native/include").mkdirs()
            File(projectDir, "native/src").mkdirs()

            File(projectDir, "native/include/calc_math.h").writeText(
                """
                #pragma once
                int calc_weighted(int a, int b);
                """.trimIndent() + "\n"
            )
            val calcMathC = File(projectDir, "native/src/calc_math.c").apply {
                writeText(
                    """
                    #include "calc_math.h"
                    int calc_weighted(int a, int b) {
                        return a + b;
                    }
                    """.trimIndent() + "\n"
                )
            }
            val calcJniC = File(projectDir, "native/src/calc_jni.c").apply {
                writeText(
                    """
                    #include <jni.h>
                    #include "calc_math.h"

                    JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
                        (void)vm;
                        (void)reserved;
                        return JNI_VERSION_1_6;
                    }

                    JNIEXPORT jint JNICALL Java_com_example_GenericUiJniTestBridge_compute(
                        JNIEnv* env, jclass cls, jint a, jint b
                    ) {
                        (void)env;
                        (void)cls;
                        return (jint)calc_weighted((int)a, (int)b);
                    }
                    """.trimIndent() + "\n"
                )
            }
            // Excluded demo file containing intentional error so glob exclusion is proved real.
            File(projectDir, "native/src/calc_demo.c").writeText("#error calc_demo.c must be excluded\n")

            val project = ProjectBuilder.builder()
                .withProjectDir(projectDir)
                .withGradleUserHomeDir(gradleUserHome)
                .build()
            project.pluginManager.apply("com.nexustools.genericui")
            val ext = project.extensions.getByType(GenericUiExtension::class.java)
            ext.backends {
                android(true)
                swing(true)
            }
            ext.jni {
                library("calcnative") {
                    sources("native/src/*.c")
                    exclude("native/src/*_demo.c")
                    includeDirs("native/include")
                    defines("PROBE_FACTOR=1")
                    abis("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
                    pageSize16Kb = true
                    buildHostLibraryForTests = true
                    bundleInSwingJar = true
                    generateJniHeaders = false
                }
            }
            (project as ProjectInternal).evaluate()

            val fetchTask = project.tasks.getByName("fetchGenericUiNativeSources") as GenericUiFetchNativeSourceArchiveTask
            fetchTask.fetchSources()

            val compileTask = project.tasks.getByName("compileGenericUiJniLibraries") as GenericUiCompileJniTask
            compileTask.compileAll()

            // 1. Verify all 4 Android ABI .so outputs, ELF machine types, 16KB PT_LOAD alignment, and JNI export symbol.
            val androidOut = File(project.layout.buildDirectory.asFile.get(), "intermediates/genericui_jni/android")
            val expectedMachines = mapOf(
                "arm64-v8a" to (2 to 183),    // ELF64, EM_AARCH64
                "armeabi-v7a" to (1 to 40),   // ELF32, EM_ARM
                "x86_64" to (2 to 62),        // ELF64, EM_X86_64
                "x86" to (1 to 3)             // ELF32, EM_386
            )
            for ((abi, classAndMachine) in expectedMachines) {
                val soFile = File(androidOut, "$abi/libcalcnative.so")
                assertTrue(soFile.isFile && soFile.length() > 0L, "Missing Android .so for $abi: $soFile")
                val bytes = soFile.readBytes()
                verifySharedLibraryElf16Kb(
                    bytes = bytes,
                    expectedEiClass = classAndMachine.first,
                    expectedMachine = classAndMachine.second,
                    abiLabel = abi,
                    expectedSymbol = "Java_com_example_GenericUiJniTestBridge_compute"
                )
            }

            // 2. Verify host shared library + bundle manifest + real JVM invocation via System.load!
            val hostTag = GenericUiJniToolchainResolver.hostOsArchTag()
            val mappedHostName = GenericUiJniToolchainResolver.mapHostLibraryName("calcnative")
            val hostLibFile = File(
                project.layout.buildDirectory.asFile.get(),
                "intermediates/genericui_jni/host/$hostTag/$mappedHostName"
            )
            assertTrue(hostLibFile.isFile && hostLibFile.length() > 0L, "Missing host shared lib: $hostLibFile")
            val bundleManifest = compileTask.hostBundleManifestFile.get().asFile.readText()
            assertTrue(
                bundleManifest.contains("$hostTag\t${hostLibFile.absolutePath}\ttrue"),
                "Unexpected host bundle manifest:\n$bundleManifest"
            )

            assertEquals(42, compileAndInvokeJavaBridge(tempDir, hostLibFile, 19, 23))

            // 3. Verify incremental `.o` cache keeps object files untouched on rerun, then recompiles only modified TU.
            val arm64ObjDir = File(
                project.layout.buildDirectory.asFile.get(),
                "intermediates/genericui_jni/calcnative/android-arm64-v8a/obj"
            )
            val objFiles = arm64ObjDir.listFiles()?.filter { it.isFile && it.name.endsWith(".o") }.orEmpty()
            assertEquals(2, objFiles.size, "Expected 2 cached .o files in $arm64ObjDir")
            val mathObj = objFiles.first { it.name.contains("calc_math") }
            val jniObj = objFiles.first { it.name.contains("calc_jni") }
            val sentinelMtime = 1_600_000_000_000L
            assertTrue(mathObj.setLastModified(sentinelMtime))
            assertTrue(jniObj.setLastModified(sentinelMtime))

            compileTask.compileAll()
            assertEquals(sentinelMtime, mathObj.lastModified(), "calc_math.o should not be recompiled on unchanged inputs")
            assertEquals(sentinelMtime, jniObj.lastModified(), "calc_jni.o should not be recompiled on unchanged inputs")

            // Modify ONLY calc_math.c and verify calc_math.o updates while calc_jni.o remains cached + output reflects change.
            calcMathC.writeText(
                """
                #include "calc_math.h"
                int calc_weighted(int a, int b) {
                    return a + b + 100;
                }
                """.trimIndent() + "\n"
            )
            compileTask.compileAll()
            assertTrue(mathObj.lastModified() > sentinelMtime, "calc_math.o should be recompiled after source edit")
            assertEquals(sentinelMtime, jniObj.lastModified(), "calc_jni.o should remain cached when only calc_math.c changed")

            val updatedHostLib = File(tempDir, "calcnative-v2-$mappedHostName")
            hostLibFile.copyTo(updatedHostLib, overwrite = true)
            assertEquals(142, compileAndInvokeJavaBridge(tempDir, updatedHostLib, 19, 23))

            // 4. Verify bundled fallback OpenJDK-compatible jni.h / jni_md.h headers compile C JNI cleanly.
            val fallbackDir = File(tempDir, "bundled-jni-headers")
            val (bundledInc, bundledMd) = GenericUiJniToolchainResolver.extractBundledJniHeaders(fallbackDir)
            assertTrue(File(bundledInc, "jni.h").isFile)
            assertTrue(File(bundledMd, "jni_md.h").isFile)
            val hostTc = GenericUiJniToolchainResolver.resolveHostToolchain(projectDir, gradleUserHome)
            val bundledProbeOut = File(tempDir, "bundled-hdr-probe-$mappedHostName")
            val bundledObj = File(tempDir, "bundled_jni.o")
            val bundledMathObj = File(tempDir, "bundled_math.o")
            val baseArgs = hostTc.buildCompileArgs(isCpp = false).filterNot {
                it == "-I${hostTc.jniIncludeDir.absolutePath}" || it == "-I${hostTc.jniMdIncludeDir.absolutePath}"
            }
            fun compileWithBundledHeaders(src: File, objOut: File) {
                val cmd = buildList {
                    add(hostTc.clang.absolutePath)
                    addAll(baseArgs)
                    add("-I${File(projectDir, "native/include").absolutePath}")
                    add("-I${bundledInc.absolutePath}")
                    add("-I${bundledMd.absolutePath}")
                    add("-c")
                    add(src.absolutePath)
                    add("-o")
                    add(objOut.absolutePath)
                }
                val p = ProcessBuilder(cmd).redirectErrorStream(true).start()
                val out = p.inputStream.bufferedReader().readText()
                assertEquals(0, p.waitFor(), "Compile using bundled jni.h failed (${cmd.joinToString(" ")}):\n$out")
            }
            compileWithBundledHeaders(calcJniC, bundledObj)
            compileWithBundledHeaders(calcMathC, bundledMathObj)
            val linkCmd = hostTc.buildLinkCommand(
                hasCpp = false,
                objectFiles = listOf(bundledObj, bundledMathObj),
                userLinkerFlags = emptyList(),
                userLinkLibraries = emptyList(),
                outputSharedLib = bundledProbeOut
            )
            val proc = ProcessBuilder(linkCmd).redirectErrorStream(true).start()
            val procOut = proc.inputStream.bufferedReader().readText()
            assertEquals(0, proc.waitFor(), "Compilation using bundled jni.h headers failed:\n$procOut")
            assertTrue(bundledProbeOut.isFile && bundledProbeOut.length() > 0L)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testAndroidApkJniLibs16KbAlignmentAndSwingJarNativeBundling() {
        val tempDir = Files.createTempDirectory("genericui_apk_jar_jni_pkg_test").toFile()
        try {
            val gradleUserHome = File(System.getProperty("user.home"), ".gradle")
            val projectDir = File(tempDir, "app").apply { mkdirs() }

            // Create minimal C JNI source that builds for both Android and Host.
            File(projectDir, "native").mkdirs()
            File(projectDir, "native/bridge.c").writeText(
                """
                #include <jni.h>
                JNIEXPORT jint JNICALL Java_com_example_PkgProbe_ping(JNIEnv* env, jclass cls) {
                    (void)env;
                    (void)cls;
                    return 77;
                }
                """.trimIndent() + "\n"
            )
            File(projectDir, "native/host_excluded.c").writeText(
                """
                #include <jni.h>
                JNIEXPORT jint JNICALL Java_com_example_PkgProbe_ex(JNIEnv* env, jclass cls) {
                    (void)env;
                    (void)cls;
                    return 11;
                }
                """.trimIndent() + "\n"
            )

            // Stage prebuilt .so files across src/androidMain/jniLibs, src/android/jniLibs, and configured jniLibsDirs.
            val mainJniSo = File(projectDir, "src/androidMain/jniLibs/arm64-v8a/libprebuilt_main.so")
            val altJniSo = File(projectDir, "src/android/jniLibs/arm64-v8a/libprebuilt_alt.so")
            val customJniSo = File(projectDir, "custom-jnilibs/arm64-v8a/libcustom_dir.so")
            val filteredOutSo = File(projectDir, "custom-jnilibs/x86/libfiltered_out.so")
            listOf(mainJniSo, altJniSo, customJniSo, filteredOutSo).forEachIndexed { idx, file ->
                file.parentFile.mkdirs()
                file.writeBytes(
                    byteArrayOf(0x7F, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte()) +
                        ByteArray(256) { (idx + it).toByte() }
                )
            }

            // Minimal compiled Java class for Swing JAR + APK dex packaging inputs.
            val classesDir = File(projectDir, "build/classes/kotlin/jvm/main").apply { mkdirs() }
            JarOutputStream(File(tempDir, "dummy-lib.jar").outputStream()).use { jos ->
                jos.putNextEntry(JarEntry("com/example/Dummy.class"))
                jos.write(byteArrayOf(0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte()))
                jos.closeEntry()
            }
            File(classesDir, "Main.class").writeBytes(
                byteArrayOf(0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte())
            )

            val project = ProjectBuilder.builder()
                .withProjectDir(projectDir)
                .withGradleUserHomeDir(gradleUserHome)
                .build()
            project.pluginManager.apply("com.nexustools.genericui")
            val ext = project.extensions.getByType(GenericUiExtension::class.java)
            ext.app {
                name = "ProbeApp"
                packageName = "com.example.probeapp"
            }
            ext.mainClass = "com.example.MainKt"
            ext.backends {
                android(true)
                swing(true)
            }
            ext.jni {
                jniLibsDirs("custom-jnilibs")
                library("compiledjni") {
                    sources("native/bridge.c")
                    abis("arm64-v8a", "x86")
                    pageSize16Kb = true
                    buildHostLibraryForTests = true
                    bundleInSwingJar = true
                    generateJniHeaders = false
                }
                library("excludedhost") {
                    sources("native/host_excluded.c")
                    buildAndroidLibraries = false
                    buildHostLibraryForTests = true
                    bundleInSwingJar = false
                    generateJniHeaders = false
                }
            }
            (project as ProjectInternal).evaluate()

            val fetchTask = project.tasks.getByName("fetchGenericUiNativeSources") as GenericUiFetchNativeSourceArchiveTask
            fetchTask.fetchSources()
            val compileTask = project.tasks.getByName("compileGenericUiJniLibraries") as GenericUiCompileJniTask
            compileTask.compileAll()

            val buildDir = project.layout.buildDirectory.asFile.get()

            // Verify Swing JAR native bundling (bundles bundleInSwingJar=true, excludes bundleInSwingJar=false).
            val hostTag = GenericUiJniToolchainResolver.hostOsArchTag()
            val includedMapped = GenericUiJniToolchainResolver.mapHostLibraryName("compiledjni")
            val excludedMapped = GenericUiJniToolchainResolver.mapHostLibraryName("excludedhost")
            val swingJarTask = project.tasks.matching {
                it.name.startsWith("packageGenericUiSwing") && it.name.endsWith("Jar")
            }.first() as GenericUiPackageSwingJarTask
            val swingEntries = swingJarTask.collectBundledNativeEntries(
                buildDir = buildDir,
                rootDir = projectDir
            )
            val expectedIncludeRes = "META-INF/native/$hostTag/$includedMapped"
            val expectedExcludeRes = "META-INF/native/$hostTag/$excludedMapped"
            assertTrue(swingEntries.containsKey(expectedIncludeRes), "Missing $expectedIncludeRes in Swing entries")
            assertFalse(swingEntries.containsKey(expectedExcludeRes), "Excluded lib must not be bundled: $expectedExcludeRes")

            val outJar = File(tempDir, "probe-swing-fat.jar")
            swingJarTask.buildFatSwingJar(
                inputJars = listOf(File(tempDir, "dummy-lib.jar")),
                mainClass = "com.example.MainKt",
                appTitle = "ProbeApp",
                appVersion = "1.0.0",
                outputJarFile = outJar,
                additionalEntries = swingEntries
            )
            assertTrue(outJar.isFile && outJar.length() > 0L, "Expected packaged Swing JAR at $outJar")
            JarFile(outJar).use { jar ->
                val entry = jar.getJarEntry(expectedIncludeRes)
                assertNotNull(entry, "Swing JAR missing $expectedIncludeRes")
                assertEquals(null, jar.getJarEntry(expectedExcludeRes), "Swing JAR must not include $expectedExcludeRes")
                val jarBytes = jar.getInputStream(entry).use { it.readBytes() }
                val diskBytes = swingEntries.getValue(expectedIncludeRes).readBytes()
                assertTrue(jarBytes.contentEquals(diskBytes), "Packaged JAR native lib bytes mismatch")
            }

            // Build unsigned APK via GenericUiPackageAndroidApkTask.assembleUnsignedApk staging all native sources.
            val apkTask = project.tasks.matching {
                it.name.startsWith("packageGenericUiAndroid") && it.name.endsWith("Apk")
            }.first() as GenericUiPackageAndroidApkTask
            assertTrue(
                apkTask.jniLibsDirectories.get().any { File(it).name == "custom-jnilibs" },
                "Expected custom-jnilibs wired onto APK task jniLibsDirectories"
            )
            assertTrue(apkTask.pageSize16Kb.get())

            val linkedApFile = File(tempDir, "linked.ap_")
            ZipOutputStream(linkedApFile.outputStream().buffered()).use { zos ->
                zos.putNextEntry(ZipEntry("AndroidManifest.xml"))
                zos.write(
                    """
                    <?xml version="1.0" encoding="utf-8"?>
                    <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.example.probeapp">
                        <application android:label="ProbeApp" />
                    </manifest>
                    """.trimIndent().toByteArray(StandardCharsets.UTF_8)
                )
                zos.closeEntry()
                zos.putNextEntry(ZipEntry("resources.arsc"))
                zos.write(byteArrayOf(0x02, 0x00, 0x0C, 0x00, 0, 0, 0, 0, 0, 0, 0, 0))
                zos.closeEntry()
            }
            val dexOutDir = File(tempDir, "dex-out").apply { mkdirs() }
            File(dexOutDir, "classes.dex").writeBytes(
                byteArrayOf('d'.code.toByte(), 'e'.code.toByte(), 'x'.code.toByte(), '\n'.code.toByte(), '0'.code.toByte(), '3'.code.toByte(), '5'.code.toByte(), 0)
            )
            val apkOut = File(tempDir, "probe-16kb.apk")
            apkTask.assembleUnsignedApk(
                linkedApFile = linkedApFile,
                dexOutDir = dexOutDir,
                rootDir = projectDir,
                buildDir = buildDir,
                extractedAars = emptyList(),
                allInputJars = emptyList(),
                abiFilter = "arm64-v8a",
                unsignedApk = apkOut,
                extraJniLibsDirs = emptyList(),
                alignSoTo16Kb = true
            )
            assertTrue(apkOut.isFile && apkOut.length() > 0L)

            ZipFile(apkOut).use { zip ->
                assertNotNull(zip.getEntry("lib/arm64-v8a/libprebuilt_main.so"))
                assertNotNull(zip.getEntry("lib/arm64-v8a/libprebuilt_alt.so"))
                assertNotNull(zip.getEntry("lib/arm64-v8a/libcustom_dir.so"))
                assertNotNull(zip.getEntry("lib/arm64-v8a/libcompiledjni.so"))
                assertEquals(null, zip.getEntry("lib/x86/libcompiledjni.so"))
            }

            // Parse raw ZIP local file headers to verify every STORED lib/**/*.so payload starts on a 16KB boundary!
            val offsets = readZipEntryPayloadOffsets(apkOut)
            val soOffsets = offsets.filterKeys { it.startsWith("lib/") && it.endsWith(".so") }
            assertEquals(4, soOffsets.size)
            for ((entryName, info) in soOffsets) {
                assertEquals(0, info.compressionMethod, "$entryName must be STORED (method=0) for direct mmap")
                assertEquals(
                    0L,
                    info.dataOffset % 16384L,
                    "$entryName payload offset ${info.dataOffset} is not 16KB (16384B) aligned"
                )
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun compileAndInvokeJavaBridge(
        workDir: File,
        sharedLibFile: File,
        a: Int,
        b: Int
    ): Int {
        val compiler = ToolProvider.getSystemJavaCompiler()
            ?: error("System Java compiler is required to verify JNI native execution.")
        val javaSrcDir = File(workDir, "java-bridge-src-${System.nanoTime()}").apply { mkdirs() }
        val javaClassDir = File(workDir, "java-bridge-classes-${System.nanoTime()}").apply { mkdirs() }
        val bridgeSource = File(javaSrcDir, "com/example/GenericUiJniTestBridge.java").apply {
            parentFile.mkdirs()
            writeText(
                """
                package com.example;

                public final class GenericUiJniTestBridge {
                    public static native int compute(int a, int b);

                    public static int loadAndCompute(String libraryPath, int a, int b) {
                        System.load(libraryPath);
                        return compute(a, b);
                    }
                }
                """.trimIndent() + "\n"
            )
        }
        val rc = compiler.run(null, null, null, "-d", javaClassDir.absolutePath, bridgeSource.absolutePath)
        assertEquals(0, rc, "Failed to compile Java JNI bridge class")

        // Copy shared library to an isolated per-invocation file so Windows DLL locks on loaded modules
        // do not block incremental re-linking of build/intermediates/genericui_jni/host/<os>-<arch>/*.
        val isolatedLibCopy = File(javaClassDir, sharedLibFile.name)
        sharedLibFile.copyTo(isolatedLibCopy, overwrite = true)

        URLClassLoader(arrayOf(javaClassDir.toURI().toURL()), null).use { loader ->
            val cls = Class.forName("com.example.GenericUiJniTestBridge", true, loader)
            val method = cls.getMethod(
                "loadAndCompute",
                String::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            return method.invoke(null, isolatedLibCopy.absolutePath, a, b) as Int
        }
    }

    private fun verifySharedLibraryElf16Kb(
        bytes: ByteArray,
        expectedEiClass: Int,
        expectedMachine: Int,
        abiLabel: String,
        expectedSymbol: String
    ) {
        assertTrue(bytes.size >= 64, "ELF too small for $abiLabel")
        assertEquals(0x7F.toByte(), bytes[0], "Bad ELF magic[0] for $abiLabel")
        assertEquals('E'.code.toByte(), bytes[1], "Bad ELF magic[1] for $abiLabel")
        assertEquals('L'.code.toByte(), bytes[2], "Bad ELF magic[2] for $abiLabel")
        assertEquals('F'.code.toByte(), bytes[3], "Bad ELF magic[3] for $abiLabel")
        assertEquals(expectedEiClass, bytes[4].toInt() and 0xFF, "Unexpected EI_CLASS for $abiLabel")
        assertEquals(1, bytes[5].toInt() and 0xFF, "Expected little-endian ELFDATA2LSB for $abiLabel")

        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val eType = buf.getShort(16).toInt() and 0xFFFF
        val eMachine = buf.getShort(18).toInt() and 0xFFFF
        assertEquals(3, eType, "Expected ET_DYN (3) shared object for $abiLabel")
        assertEquals(expectedMachine, eMachine, "Unexpected ELF e_machine for $abiLabel")

        var ptLoadCount = 0
        if (expectedEiClass == 2) {
            val phOff = buf.getLong(32).toInt()
            val phEntSize = buf.getShort(54).toInt() and 0xFFFF
            val phNum = buf.getShort(56).toInt() and 0xFFFF
            assertTrue(phNum > 0, "Expected non-zero ELF64 program headers for $abiLabel")
            for (i in 0 until phNum) {
                val base = phOff + i * phEntSize
                val pType = buf.getInt(base)
                if (pType == 1) { // PT_LOAD
                    ptLoadCount++
                    val pAlign = buf.getLong(base + 48)
                    assertEquals(
                        16384L,
                        pAlign,
                        "PT_LOAD[$i] p_align=$pAlign for $abiLabel must be 16384 (16KB page size)"
                    )
                }
            }
        } else {
            val phOff = buf.getInt(28)
            val phEntSize = buf.getShort(42).toInt() and 0xFFFF
            val phNum = buf.getShort(44).toInt() and 0xFFFF
            assertTrue(phNum > 0, "Expected non-zero ELF32 program headers for $abiLabel")
            for (i in 0 until phNum) {
                val base = phOff + i * phEntSize
                val pType = buf.getInt(base)
                if (pType == 1) { // PT_LOAD
                    ptLoadCount++
                    val pAlign = buf.getInt(base + 28).toLong() and 0xFFFFFFFFL
                    assertEquals(
                        16384L,
                        pAlign,
                        "PT_LOAD[$i] p_align=$pAlign for $abiLabel must be 16384 (16KB page size)"
                    )
                }
            }
        }
        assertTrue(ptLoadCount > 0, "Expected at least one PT_LOAD segment for $abiLabel")

        val asciiScan = String(bytes, StandardCharsets.ISO_8859_1)
        assertTrue(
            asciiScan.contains(expectedSymbol),
            "Expected exported JNI symbol '$expectedSymbol' in $abiLabel .so"
        )
    }

    private data class ZipEntryOffsetInfo(
        val compressionMethod: Int,
        val dataOffset: Long
    )

    private fun readZipEntryPayloadOffsets(zipFile: File): Map<String, ZipEntryOffsetInfo> {
        val bytes = zipFile.readBytes()
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        var eocdOffset = -1
        val minStart = maxOf(0, bytes.size - 65557)
        for (i in (bytes.size - 22) downTo minStart) {
            if (buf.getInt(i) == 0x06054b50) {
                eocdOffset = i
                break
            }
        }
        assertTrue(eocdOffset >= 0, "ZIP EOCD signature not found in ${zipFile.name}")
        val totalEntries = buf.getShort(eocdOffset + 10).toInt() and 0xFFFF
        var cdOffset = buf.getInt(eocdOffset + 16)

        val result = linkedMapOf<String, ZipEntryOffsetInfo>()
        repeat(totalEntries) {
            assertEquals(0x02014b50, buf.getInt(cdOffset), "Bad CEN signature at $cdOffset")
            val method = buf.getShort(cdOffset + 10).toInt() and 0xFFFF
            val nameLen = buf.getShort(cdOffset + 28).toInt() and 0xFFFF
            val extraLen = buf.getShort(cdOffset + 30).toInt() and 0xFFFF
            val commentLen = buf.getShort(cdOffset + 32).toInt() and 0xFFFF
            val lfhOffset = buf.getInt(cdOffset + 42).toLong() and 0xFFFFFFFFL
            val name = String(bytes, cdOffset + 46, nameLen, StandardCharsets.UTF_8)

            val lfhOffInt = lfhOffset.toInt()
            assertEquals(0x04034b50, buf.getInt(lfhOffInt), "Bad LFH signature for $name")
            val lfhNameLen = buf.getShort(lfhOffInt + 26).toInt() and 0xFFFF
            val lfhExtraLen = buf.getShort(lfhOffInt + 28).toInt() and 0xFFFF
            val dataOffset = lfhOffset + 30L + lfhNameLen + lfhExtraLen

            result[name] = ZipEntryOffsetInfo(method, dataOffset)
            cdOffset += 46 + nameLen + extraLen + commentLen
        }
        return result
    }

    private fun buildZipArchive(entries: Map<String, ByteArray>): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            for ((path, content) in entries) {
                zos.putNextEntry(ZipEntry(path))
                zos.write(content)
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }

    private fun buildTarGzArchive(entries: Map<String, ByteArray>): ByteArray {
        val baos = ByteArrayOutputStream()
        GZIPOutputStream(baos).use { gzos ->
            for ((path, content) in entries) {
                val nameBytes = path.toByteArray(StandardCharsets.UTF_8)
                require(nameBytes.size <= 100) { "Test tar path exceeds 100 bytes: $path" }
                val header = ByteArray(512)
                System.arraycopy(nameBytes, 0, header, 0, nameBytes.size)
                writeAsciiOctal(header, 100, 8, 0b110_100_100) // 0644
                writeAsciiOctal(header, 108, 8, 0)
                writeAsciiOctal(header, 116, 8, 0)
                writeAsciiOctal(header, 124, 12, content.size.toLong())
                writeAsciiOctal(header, 136, 12, 1700000000L)
                for (i in 148 until 156) header[i] = ' '.code.toByte()
                header[156] = '0'.code.toByte()
                System.arraycopy("ustar\u0000".toByteArray(StandardCharsets.US_ASCII), 0, header, 257, 6)
                System.arraycopy("00".toByteArray(StandardCharsets.US_ASCII), 0, header, 263, 2)
                var sum = 0
                for (b in header) sum += (b.toInt() and 0xFF)
                val chk = sum.toString(8).padStart(6, '0')
                System.arraycopy(chk.toByteArray(StandardCharsets.US_ASCII), 0, header, 148, 6)
                header[154] = 0
                header[155] = ' '.code.toByte()

                gzos.write(header)
                gzos.write(content)
                val rem = content.size % 512
                if (rem != 0) {
                    gzos.write(ByteArray(512 - rem))
                }
            }
            gzos.write(ByteArray(1024))
        }
        return baos.toByteArray()
    }

    private fun writeAsciiOctal(buf: ByteArray, offset: Int, length: Int, value: Long) {
        val digitsLen = length - 1
        val oct = value.toString(8).padStart(digitsLen, '0')
        val bytes = oct.takeLast(digitsLen).toByteArray(StandardCharsets.US_ASCII)
        System.arraycopy(bytes, 0, buf, offset, bytes.size)
        buf[offset + digitsLen] = 0
    }
}
