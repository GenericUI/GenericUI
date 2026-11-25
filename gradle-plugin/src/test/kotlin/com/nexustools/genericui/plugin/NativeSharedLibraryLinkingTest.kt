package com.nexustools.genericui.plugin

import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeLink
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URLClassLoader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Paths
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NativeSharedLibraryLinkingTest {

    @Test
    fun `classifyKlibGroup and sharedLibraryFileName cover core backends dsl njs extensions tests and consumer modules`() {
        assertTrue(GenericUiNativeSharedLibraryCatalog.isNativeDesktopKonanTarget("macos_arm64"))
        assertTrue(GenericUiNativeSharedLibraryCatalog.isNativeDesktopKonanTarget("linux_x64"))
        assertTrue(GenericUiNativeSharedLibraryCatalog.isNativeDesktopKonanTarget("linux_arm64"))
        assertTrue(GenericUiNativeSharedLibraryCatalog.isNativeDesktopKonanTarget("mingw_x64"))
        assertFalse(GenericUiNativeSharedLibraryCatalog.isNativeDesktopKonanTarget("ios_arm64"))
        assertFalse(GenericUiNativeSharedLibraryCatalog.isNativeDesktopKonanTarget("ios_simulator_arm64"))
        assertFalse(GenericUiNativeSharedLibraryCatalog.isNativeDesktopKonanTarget("android_arm64"))

        // Core group
        for (coreName in listOf(
            "stdlib",
            "org.jetbrains.kotlinx:atomicfu",
            "org.jetbrains.kotlinx:atomicfu-cinterop-interop",
            "org.jetbrains.kotlinx:kotlinx-coroutines-core",
            "com.nexustools.genericui:common",
            "com.nexustools.genericui:system"
        )) {
            assertEquals("genericui", GenericUiNativeSharedLibraryCatalog.classifyKlibGroup(coreName, "macos_arm64", "macos"))
            assertEquals("genericui", GenericUiNativeSharedLibraryCatalog.classifyKlibGroup(coreName, "linux_arm64", "gtk"))
            assertEquals("genericui", GenericUiNativeSharedLibraryCatalog.classifyKlibGroup(coreName, "mingw_x64", "windows"))
        }
        assertEquals("genericui", GenericUiNativeSharedLibraryCatalog.classifyKlibGroup("com.nexustools.genericui:apple", "macos_arm64", "macos"))
        assertNull(GenericUiNativeSharedLibraryCatalog.classifyKlibGroup("com.nexustools.genericui:apple", "ios_arm64", "ios"))

        // Backends (strict GTK vs KDE separation on Linux; dual-backend Linux test uses genericui-linux)
        assertEquals("genericui-macos", GenericUiNativeSharedLibraryCatalog.classifyKlibGroup("com.nexustools.genericui:desktop", "macos_arm64", "macos"))
        assertEquals("genericui-windows", GenericUiNativeSharedLibraryCatalog.classifyKlibGroup("com.nexustools.genericui:desktop", "mingw_x64", "windows"))
        assertEquals("genericui-gtk", GenericUiNativeSharedLibraryCatalog.classifyKlibGroup("com.nexustools.genericui:desktop", "linux_x64", "gtk"))
        assertEquals("genericui-kde", GenericUiNativeSharedLibraryCatalog.classifyKlibGroup("com.nexustools.genericui:desktop", "linux_arm64", "kde"))
        assertEquals("genericui-linux", GenericUiNativeSharedLibraryCatalog.classifyKlibGroup("com.nexustools.genericui:desktop", "linux_arm64", "linux"))

        // NJS + extensions + DSL submodules + tests
        assertEquals("njs", GenericUiNativeSharedLibraryCatalog.classifyKlibGroup("com.nexustools.njs:njs", "macos_arm64", "macos"))
        assertEquals("genericui-ext-media", GenericUiNativeSharedLibraryCatalog.classifyKlibGroup("com.nexustools.genericui.extensions.media:media", "linux_x64", "gtk"))
        assertEquals("genericui-ext-mediacontrols", GenericUiNativeSharedLibraryCatalog.classifyKlibGroup("com.nexustools.genericui.extensions.media:mediacontrols", "mingw_x64", "windows"))
        assertEquals("genericui-ext-canvas2d", GenericUiNativeSharedLibraryCatalog.classifyKlibGroup("com.nexustools.genericui.extensions.graphics:canvas2d", "linux_arm64", "kde"))
        assertEquals("genericui-ext-dsl", GenericUiNativeSharedLibraryCatalog.classifyKlibGroup("com.nexustools.genericui.extensions.ui:dsl", "macos_arm64", "macos"))
        assertEquals("genericui-ext-engine2d-dsl", GenericUiNativeSharedLibraryCatalog.classifyKlibGroup("com.nexustools.genericui.extensions.game.engine2d:dsl", "linux_x64", "gtk"))
        assertEquals("genericui-tests", GenericUiNativeSharedLibraryCatalog.classifyKlibGroup("com.nexustools.genericui:tests", "mingw_x64", "windows"))
        assertEquals("genericui-tests", GenericUiNativeSharedLibraryCatalog.classifyKlibGroup("org.jetbrains.kotlin:kotlin-test", "macos_arm64", "macos"))

        // Platform klibs and consumer/third-party klibs stay uncached/null
        assertNull(GenericUiNativeSharedLibraryCatalog.classifyKlibGroup("org.jetbrains.kotlin.native.platform.AppKit", "macos_arm64", "macos"))
        assertNull(GenericUiNativeSharedLibraryCatalog.classifyKlibGroup("com.example:episode-caster-receiver", "macos_arm64", "macos"))

        // File naming rules (no 'lib' prefix on Windows)
        assertEquals("libgenericui.dylib", GenericUiNativeSharedLibraryCatalog.sharedLibraryFileName("genericui", "macos_arm64"))
        assertEquals("libgenericui-gtk.so", GenericUiNativeSharedLibraryCatalog.sharedLibraryFileName("genericui-gtk", "linux_x64"))
        assertEquals("libgenericui-kde.so", GenericUiNativeSharedLibraryCatalog.sharedLibraryFileName("genericui-kde", "linux_arm64"))
        assertEquals("libgenericui-linux.so", GenericUiNativeSharedLibraryCatalog.sharedLibraryFileName("genericui-linux", "linux_arm64"))
        assertEquals("genericui.dll", GenericUiNativeSharedLibraryCatalog.sharedLibraryFileName("genericui", "mingw_x64"))
        assertEquals("genericui-windows.dll", GenericUiNativeSharedLibraryCatalog.sharedLibraryFileName("genericui-windows", "mingw_x64"))
        assertEquals("genericui-ext-media.dll", GenericUiNativeSharedLibraryCatalog.sharedLibraryFileName("genericui-ext-media", "mingw_x64"))
        assertEquals("njs.dll", GenericUiNativeSharedLibraryCatalog.sharedLibraryFileName("njs", "mingw_x64"))
        assertEquals("genericui-tests.dll", GenericUiNativeSharedLibraryCatalog.sharedLibraryFileName("genericui-tests", "mingw_x64"))
        assertEquals(
            "org.jetbrains.kotlinx_atomicfu-cache.dll",
            GenericUiNativeSharedLibraryCatalog.cacheArtifactFileName("org.jetbrains.kotlinx:atomicfu", "mingw_x64")
        )
    }

    @Test
    fun `readManifest unescapes properties and planSharedLibraries orders topologically with closed world check`() {
        val tempDir = Files.createTempDirectory("genericui-klib-plan-test").toFile()
        try {
            val stdlib = writeFakeKlibDir(tempDir, "stdlib", "stdlib", emptyList())
            val atomicfu = writeFakeKlibDir(tempDir, "atomicfu", "org.jetbrains.kotlinx\\:atomicfu", listOf("stdlib"))
            val common = writeFakeKlibDir(tempDir, "common", "com.nexustools.genericui\\:common", listOf("stdlib", "org.jetbrains.kotlinx:atomicfu"))
            val desktop = writeFakeKlibDir(tempDir, "desktop", "com.nexustools.genericui\\:desktop", listOf("stdlib", "com.nexustools.genericui:common"))
            val media = writeFakeKlibDir(tempDir, "media", "com.nexustools.genericui.extensions.media\\:media", listOf("stdlib", "com.nexustools.genericui:common", "com.nexustools.genericui:desktop"))
            val consumer = writeFakeKlibDir(tempDir, "consumer", "com.example\\:app", listOf("stdlib", "com.nexustools.genericui.extensions.media:media"))

            val atomicInfo = GenericUiNativeSharedLibraryCatalog.readManifest(atomicfu, "linux_arm64")
            assertNotNull(atomicInfo)
            assertEquals("org.jetbrains.kotlinx:atomicfu", atomicInfo.uniqueName)

            val plan = GenericUiNativeSharedLibraryCatalog.planSharedLibraries(
                libraryFiles = listOf(consumer, media, desktop, common, atomicfu, stdlib),
                konanTargetName = "linux_arm64",
                backendVariant = "kde"
            )
            assertEquals(
                listOf("genericui", "genericui-kde", "genericui-ext-media"),
                plan.groupsOrdered.map { it.groupId }
            )
            assertEquals(listOf("com.example:app"), plan.uncachedKlibs.map { it.uniqueName })
            assertEquals(
                listOf("genericui", "genericui-kde"),
                plan.groupsOrdered.last().upstreamGroupIds
            )
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `planSharedLibraries discovers reachable platform klibs and assigns them to dominating groups`() {
        val tempDir = Files.createTempDirectory("genericui-platform-klib-test").toFile()
        try {
            val platformDir = File(tempDir, "platform/macos_arm64")
            writeFakeKlibDir(platformDir, "org.jetbrains.kotlin.native.platform.posix", "org.jetbrains.kotlin.native.platform.posix", listOf("stdlib"))
            writeFakeKlibDir(platformDir, "org.jetbrains.kotlin.native.platform.AppKit", "org.jetbrains.kotlin.native.platform.AppKit", listOf("stdlib", "org.jetbrains.kotlin.native.platform.posix"))
            writeFakeKlibDir(platformDir, "org.jetbrains.kotlin.native.platform.UnusedFramework", "org.jetbrains.kotlin.native.platform.UnusedFramework", listOf("stdlib"))

            val stdlib = writeFakeKlibDir(tempDir, "stdlib", "stdlib", emptyList())
            val common = writeFakeKlibDir(tempDir, "common", "com.nexustools.genericui\\:common", listOf("stdlib", "org.jetbrains.kotlin.native.platform.posix"))
            val desktop = writeFakeKlibDir(tempDir, "desktop", "com.nexustools.genericui\\:desktop", listOf("stdlib", "com.nexustools.genericui:common", "org.jetbrains.kotlin.native.platform.AppKit"))
            val consumer = writeFakeKlibDir(tempDir, "consumer", "com.example\\:app", listOf("stdlib", "com.nexustools.genericui:desktop", "org.jetbrains.kotlin.native.platform.UnusedFramework"))

            val plan = GenericUiNativeSharedLibraryCatalog.planSharedLibraries(
                libraryFiles = listOf(consumer, desktop, common, stdlib),
                konanTargetName = "macos_arm64",
                backendVariant = "macos",
                platformKlibsRootDir = platformDir
            )
            assertEquals("genericui", plan.klibToGroupId["org.jetbrains.kotlin.native.platform.posix"])
            assertEquals("genericui-macos", plan.klibToGroupId["org.jetbrains.kotlin.native.platform.AppKit"])
            assertNull(plan.klibToGroupId["org.jetbrains.kotlin.native.platform.UnusedFramework"])

            val coreNames = plan.groupsOrdered.first { it.groupId == "genericui" }.klibs.map { it.uniqueName }
            assertEquals(listOf("stdlib", "org.jetbrains.kotlin.native.platform.posix", "com.nexustools.genericui:common"), coreNames)

            val macNames = plan.groupsOrdered.first { it.groupId == "genericui-macos" }.klibs.map { it.uniqueName }
            assertEquals(listOf("org.jetbrains.kotlin.native.platform.AppKit", "com.nexustools.genericui:desktop"), macNames)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `konan compiler shim produces verifiable CachedLibraries and TopLevelPhasesKt bytecode`() {
        val konanHome = findInstalledKonanHome() ?: return
        val tempDir = Files.createTempDirectory("genericui-shim-test").toFile()
        try {
            val shimJar = GenericUiKonanCompilerShim.ensureShimJar(konanHome, tempDir)
            assertTrue(shimJar.isFile)
            ZipFile(shimJar).use { zip ->
                assertNotNull(zip.getEntry("org/jetbrains/kotlin/backend/konan/CachedLibraries.class"))
                assertNotNull(zip.getEntry("org/jetbrains/kotlin/backend/konan/CachedLibraries\$Companion.class"))
                assertNotNull(zip.getEntry("org/jetbrains/kotlin/backend/konan/driver/phases/TopLevelPhasesKt.class"))
            }

            // Re-invocation reuses stamp cleanly.
            val mtimeBefore = shimJar.lastModified()
            val secondJar = GenericUiKonanCompilerShim.ensureShimJar(konanHome, tempDir)
            assertEquals(shimJar.absolutePath, secondJar.absolutePath)
            assertEquals(mtimeBefore, secondJar.lastModified())

            val perFileShimJar = GenericUiKonanCompilerShim.ensurePerFileOptShimJar(konanHome, tempDir)
            assertTrue(perFileShimJar.isFile)

            val embeddableJar = File(konanHome, "konan/lib/kotlin-native-compiler-embeddable.jar")
            URLClassLoader(
                arrayOf(perFileShimJar.toURI().toURL(), shimJar.toURI().toURL(), embeddableJar.toURI().toURL()),
                null
            ).use { loader ->
                val cachedCls = Class.forName("org.jetbrains.kotlin.backend.konan.CachedLibraries", true, loader)
                val companionCls = Class.forName("org.jetbrains.kotlin.backend.konan.CachedLibraries\$Companion", true, loader)
                val phasesCls = Class.forName("org.jetbrains.kotlin.backend.konan.driver.phases.TopLevelPhasesKt", false, loader)
                val propInlineCls = Class.forName(
                    "org.jetbrains.kotlin.backend.common.lower.optimizations.PropertyAccessorInlineLowering",
                    true,
                    loader
                )
                assertNotNull(cachedCls.declaredMethods.firstOrNull { it.name == "getHasDynamicCaches" })
                val companionInstance = cachedCls.getField("Companion").get(null)
                val getCachedNameMethod = companionCls.getMethod("getCachedLibraryName", String::class.java)
                assertEquals(
                    "org.jetbrains.kotlinx_atomicfu-cinterop-interop-cache",
                    getCachedNameMethod.invoke(companionInstance, "org.jetbrains.kotlinx:atomicfu-cinterop-interop")
                )
                assertNotNull(phasesCls)
                assertTrue(propInlineCls.declaredMethods.any { it.name == "isSafeToInline" })
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `plugin wires native desktop shared libs tasks onto desktop KotlinNativeLink without affecting iOS frameworks`() {
        val projectDir = Files.createTempDirectory("genericui-plugin-shared-wiring").toFile()
        try {
            val mainFile = File(projectDir, "src/commonMain/kotlin/sample/MainApp.kt")
            mainFile.parentFile.mkdirs()
            mainFile.writeText(
                """
                package sample
                import com.nexustools.genericui.component.fragment.Fragment
                import com.nexustools.genericui.component.fragment.MainAppFragment

                @MainAppFragment
                class MainApp : Fragment() {
                    override fun onCreate() {}
                }
                """.trimIndent(),
                Charsets.UTF_8
            )

            val project = ProjectBuilder.builder()
                .withName("sample-shared-app")
                .withProjectDir(projectDir)
                .build()
            project.pluginManager.apply("com.nexustools.genericui")
            val ext = project.extensions.getByType(GenericUiExtension::class.java)
            ext.application {
                baseName = "SampleSharedApp"
            }
            ext.backends {
                allNativeDesktop()
                ios()
            }
            (project as org.gradle.api.internal.project.ProjectInternal).evaluate()

            val macLink = project.tasks.getByName("linkDebugExecutableMacosArm64") as KotlinNativeLink
            val gtkLink = project.tasks.getByName("linkGtkDebugExecutableLinuxX64") as KotlinNativeLink
            val kdeLink = project.tasks.getByName("linkKdeDebugExecutableLinuxX64") as KotlinNativeLink
            val winLink = project.tasks.getByName("linkDebugExecutableMingwX64") as KotlinNativeLink
            val testLink = project.tasks.getByName("linkDebugTestMacosArm64") as KotlinNativeLink
            val iosFrameworkLink = project.tasks.getByName("linkDebugFrameworkIosArm64") as KotlinNativeLink

            val macSharedTask = project.tasks.getByName("buildGenericUiSharedLibsForLinkDebugExecutableMacosArm64") as GenericUiBuildNativeSharedLibsTask
            val gtkSharedTask = project.tasks.getByName("buildGenericUiSharedLibsForLinkGtkDebugExecutableLinuxX64") as GenericUiBuildNativeSharedLibsTask
            val kdeSharedTask = project.tasks.getByName("buildGenericUiSharedLibsForLinkKdeDebugExecutableLinuxX64") as GenericUiBuildNativeSharedLibsTask
            val winSharedTask = project.tasks.getByName("buildGenericUiSharedLibsForLinkDebugExecutableMingwX64") as GenericUiBuildNativeSharedLibsTask
            val testSharedTask = project.tasks.getByName("buildGenericUiSharedLibsForLinkDebugTestMacosArm64") as GenericUiBuildNativeSharedLibsTask
            val linuxTestSharedTask = project.tasks.getByName("buildGenericUiSharedLibsForLinkDebugTestLinuxX64") as GenericUiBuildNativeSharedLibsTask

            assertEquals("macos", macSharedTask.backendVariant.get())
            assertEquals("gtk", gtkSharedTask.backendVariant.get())
            assertEquals("kde", kdeSharedTask.backendVariant.get())
            assertEquals("windows", winSharedTask.backendVariant.get())
            assertEquals("macos", testSharedTask.backendVariant.get())
            assertEquals("linux", linuxTestSharedTask.backendVariant.get())

            assertTrue(macSharedTask in macLink.taskDependencies.getDependencies(macLink))
            assertTrue(gtkSharedTask in gtkLink.taskDependencies.getDependencies(gtkLink))
            assertTrue(kdeSharedTask in kdeLink.taskDependencies.getDependencies(kdeLink))
            assertTrue(winSharedTask in winLink.taskDependencies.getDependencies(winLink))
            assertTrue(testSharedTask in testLink.taskDependencies.getDependencies(testLink))

            // iOS framework stays integrated with the app package (no shared lib build task created).
            assertNull(project.tasks.findByName("buildGenericUiSharedLibsForLinkDebugFrameworkIosArm64"))
            assertTrue(iosFrameworkLink.taskDependencies.getDependencies(iosFrameworkLink).none {
                it is GenericUiBuildNativeSharedLibsTask
            })

            // Single-backend KDE-only project resolves Linux TEST variant to "kde".
            val kdeOnlyProject = ProjectBuilder.builder()
                .withName("sample-kde-only-app")
                .withProjectDir(projectDir)
                .build()
            kdeOnlyProject.pluginManager.apply("com.nexustools.genericui")
            val kdeOnlyExt = kdeOnlyProject.extensions.getByType(GenericUiExtension::class.java)
            kdeOnlyExt.application {
                baseName = "SampleKdeOnlyApp"
            }
            kdeOnlyExt.backends {
                kde()
            }
            (kdeOnlyProject as org.gradle.api.internal.project.ProjectInternal).evaluate()
            val kdeOnlyTestTask = kdeOnlyProject.tasks.getByName("buildGenericUiSharedLibsForLinkDebugTestLinuxX64") as GenericUiBuildNativeSharedLibsTask
            assertEquals("kde", kdeOnlyTestTask.backendVariant.get())
        } finally {
            projectDir.deleteRecursively()
        }
    }

    @Test
    fun `linux shared library symbol isolation enforces strict gtk vs kde separation`() {
        assertEquals(
            "com.nexustools.genericui.gtk",
            GenericUiBuildNativeSharedLibsTask.linuxExcludedPackageForKlib("com.nexustools.genericui:desktop", "kde")
        )
        assertEquals(
            "com.nexustools.genericui.kde",
            GenericUiBuildNativeSharedLibsTask.linuxExcludedPackageForKlib("com.nexustools.genericui:desktop", "gtk")
        )
        assertEquals(
            "com.nexustools.genericui.tests.gtk",
            GenericUiBuildNativeSharedLibsTask.linuxExcludedPackageForKlib("com.nexustools.genericui:tests", "kde")
        )
        assertEquals(
            "com.nexustools.genericui.tests.kde",
            GenericUiBuildNativeSharedLibsTask.linuxExcludedPackageForKlib("com.nexustools.genericui:tests", "gtk")
        )

        val tempDir = Files.createTempDirectory("genericui-linux-isolation-test").toFile()
        try {
            val cleanGtkSo = File(tempDir, "libgenericui-gtk.so").apply {
                writeBytes(
                    buildMinimalElf64SharedObject(
                        listOf("kfun:com.nexustools.genericui.gtk.GtkLauncher#launch(){}")
                    )
                )
            }
            GenericUiBuildNativeSharedLibsTask.verifyLinuxSharedLibrarySymbolIsolation(
                groupId = "genericui-gtk",
                backendVariant = "gtk",
                isLinuxSingleBackendVariant = true,
                soFile = cleanGtkSo
            )

            val leakedKdeInGtkSo = File(tempDir, "libgenericui-gtk-leaked.so").apply {
                writeBytes(
                    buildMinimalElf64SharedObject(
                        listOf(
                            "kfun:com.nexustools.genericui.gtk.GtkLauncher#launch(){}",
                            "kfun:com.nexustools.genericui.kde.KdeLauncher#launch(){}"
                        )
                    )
                )
            }
            val gtkFail = assertFailsWith<GradleException> {
                GenericUiBuildNativeSharedLibsTask.verifyLinuxSharedLibrarySymbolIsolation(
                    groupId = "genericui-gtk",
                    backendVariant = "gtk",
                    isLinuxSingleBackendVariant = true,
                    soFile = leakedKdeInGtkSo
                )
            }
            assertTrue(gtkFail.message.orEmpty().contains("com.nexustools.genericui.kde"))

            val cleanKdeSo = File(tempDir, "libgenericui-kde.so").apply {
                writeBytes(
                    buildMinimalElf64SharedObject(
                        listOf("kfun:com.nexustools.genericui.kde.KdeLauncher#launch(){}")
                    )
                )
            }
            GenericUiBuildNativeSharedLibsTask.verifyLinuxSharedLibrarySymbolIsolation(
                groupId = "genericui-kde",
                backendVariant = "kde",
                isLinuxSingleBackendVariant = true,
                soFile = cleanKdeSo
            )

            val leakedGtkInKdeSo = File(tempDir, "libgenericui-kde-leaked.so").apply {
                writeBytes(
                    buildMinimalElf64SharedObject(
                        listOf(
                            "kfun:com.nexustools.genericui.kde.KdeLauncher#launch(){}",
                            "kfun:com.nexustools.genericui.gtk.GtkLauncher#launch(){}"
                        )
                    )
                )
            }
            val kdeFail = assertFailsWith<GradleException> {
                GenericUiBuildNativeSharedLibsTask.verifyLinuxSharedLibrarySymbolIsolation(
                    groupId = "genericui-kde",
                    backendVariant = "kde",
                    isLinuxSingleBackendVariant = true,
                    soFile = leakedGtkInKdeSo
                )
            }
            assertTrue(kdeFail.message.orEmpty().contains("com.nexustools.genericui.gtk"))

            val cleanKdeTestsSo = File(tempDir, "libgenericui-tests-kde.so").apply {
                writeBytes(
                    buildMinimalElf64SharedObject(
                        listOf("kfun:com.nexustools.genericui.tests.kde.KdeJestElectricBootstrap#installIfEnabled(){}")
                    )
                )
            }
            GenericUiBuildNativeSharedLibsTask.verifyLinuxSharedLibrarySymbolIsolation(
                groupId = GenericUiNativeSharedLibraryCatalog.TESTS_GROUP_ID,
                backendVariant = "kde",
                isLinuxSingleBackendVariant = true,
                soFile = cleanKdeTestsSo
            )

            val leakedGtkTestsInKdeSo = File(tempDir, "libgenericui-tests-kde-leaked.so").apply {
                writeBytes(
                    buildMinimalElf64SharedObject(
                        listOf(
                            "kfun:com.nexustools.genericui.tests.kde.KdeJestElectricBootstrap#installIfEnabled(){}",
                            "kfun:com.nexustools.genericui.tests.gtk.GtkJestElectricBootstrap#installIfEnabled(){}"
                        )
                    )
                )
            }
            val testsFail = assertFailsWith<GradleException> {
                GenericUiBuildNativeSharedLibsTask.verifyLinuxSharedLibrarySymbolIsolation(
                    groupId = GenericUiNativeSharedLibraryCatalog.TESTS_GROUP_ID,
                    backendVariant = "kde",
                    isLinuxSingleBackendVariant = true,
                    soFile = leakedGtkTestsInKdeSo
                )
            }
            assertTrue(testsFail.message.orEmpty().contains("com.nexustools.genericui.tests.gtk"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun buildMinimalElf64SharedObject(symbolNames: List<String>): ByteArray {
        val dynstr = ByteArrayOutputStream().apply { write(0) }
        val symOffsets = symbolNames.map { name ->
            val off = dynstr.size()
            dynstr.write(name.toByteArray(Charsets.UTF_8))
            dynstr.write(0)
            off
        }
        val dynstrBytes = dynstr.toByteArray()

        val symEntrySize = 24
        val dynsymBytes = ByteArray((1 + symbolNames.size) * symEntrySize)
        val symBuf = ByteBuffer.wrap(dynsymBytes).order(ByteOrder.LITTLE_ENDIAN)
        for ((index, strOff) in symOffsets.withIndex()) {
            val base = (index + 1) * symEntrySize
            symBuf.putInt(base, strOff)
            dynsymBytes[base + 4] = 0x12 // STB_GLOBAL | STT_FUNC
            symBuf.putShort(base + 6, 1) // defined in section 1
        }

        val ehdrSize = 64
        val dynstrOffset = ehdrSize
        val dynsymOffset = dynstrOffset + dynstrBytes.size
        val shOff = dynsymOffset + dynsymBytes.size
        val shEntSize = 64
        val shNum = 3
        val totalSize = shOff + shNum * shEntSize

        val out = ByteArray(totalSize)
        out[0] = 0x7f.toByte()
        out[1] = 'E'.code.toByte()
        out[2] = 'L'.code.toByte()
        out[3] = 'F'.code.toByte()
        out[4] = 2 // ELFCLASS64
        out[5] = 1 // ELFDATA2LSB
        out[6] = 1 // EV_CURRENT

        val buf = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN)
        buf.putShort(16, 3) // ET_DYN
        buf.putShort(18, 62) // EM_X86_64
        buf.putInt(20, 1) // EV_CURRENT
        buf.putLong(40, shOff.toLong())
        buf.putShort(52, ehdrSize.toShort())
        buf.putShort(58, shEntSize.toShort())
        buf.putShort(60, shNum.toShort())

        System.arraycopy(dynstrBytes, 0, out, dynstrOffset, dynstrBytes.size)
        System.arraycopy(dynsymBytes, 0, out, dynsymOffset, dynsymBytes.size)

        // Section 1: SHT_STRTAB (3)
        val sec1 = shOff + shEntSize
        buf.putInt(sec1 + 4, 3)
        buf.putLong(sec1 + 24, dynstrOffset.toLong())
        buf.putLong(sec1 + 32, dynstrBytes.size.toLong())

        // Section 2: SHT_DYNSYM (11)
        val sec2 = shOff + 2 * shEntSize
        buf.putInt(sec2 + 4, 11)
        buf.putLong(sec2 + 24, dynsymOffset.toLong())
        buf.putLong(sec2 + 32, dynsymBytes.size.toLong())
        buf.putInt(sec2 + 40, 1) // sh_link -> .dynstr
        buf.putLong(sec2 + 56, symEntrySize.toLong())

        return out
    }

    @Test
    fun `copyFilteredDirectoryContents preserves VLC symlinks and replaces pre-existing duplicate regular files`() {
        val tempDir = Files.createTempDirectory("genericui-vlc-symlink-copy").toFile()
        try {
            val srcDir = File(tempDir, "src").apply { mkdirs() }
            val dstDir = File(tempDir, "dst").apply { mkdirs() }

            val libVlcVersioned = File(srcDir, "libvlc.5.dylib")
            val libVlcCoreVersioned = File(srcDir, "libvlccore.9.dylib")
            libVlcVersioned.writeBytes("LIBVLC_5_BINARY".encodeToByteArray())
            libVlcCoreVersioned.writeBytes("LIBVLCCORE_9_BINARY".encodeToByteArray())

            val symlinksSupported = runCatching {
                Files.createSymbolicLink(File(srcDir, "libvlc.dylib").toPath(), Paths.get("libvlc.5.dylib"))
                Files.createSymbolicLink(File(srcDir, "libvlccore.dylib").toPath(), Paths.get("libvlccore.9.dylib"))
            }.isSuccess
            if (!symlinksSupported) return

            // Seed destination with stale dereferenced duplicate regular files
            File(dstDir, "libvlc.5.dylib").writeBytes("STALE_5".encodeToByteArray())
            File(dstDir, "libvlc.dylib").writeBytes("STALE_DUPLICATE_VLC".encodeToByteArray())
            File(dstDir, "libvlccore.9.dylib").writeBytes("STALE_9".encodeToByteArray())
            File(dstDir, "libvlccore.dylib").writeBytes("STALE_DUPLICATE_CORE".encodeToByteArray())

            GenericUiMultiplatformConfigurator.copyFilteredDirectoryContents(
                sourceDir = srcDir,
                targetDir = dstDir,
                setExecutableOnBinaries = true
            )

            val dstVlc5 = File(dstDir, "libvlc.5.dylib").toPath()
            val dstVlc = File(dstDir, "libvlc.dylib").toPath()
            val dstCore9 = File(dstDir, "libvlccore.9.dylib").toPath()
            val dstCore = File(dstDir, "libvlccore.dylib").toPath()

            assertFalse(Files.isSymbolicLink(dstVlc5))
            assertTrue(Files.isRegularFile(dstVlc5, LinkOption.NOFOLLOW_LINKS))
            assertEquals("LIBVLC_5_BINARY", dstVlc5.toFile().readText())

            assertFalse(Files.isSymbolicLink(dstCore9))
            assertTrue(Files.isRegularFile(dstCore9, LinkOption.NOFOLLOW_LINKS))
            assertEquals("LIBVLCCORE_9_BINARY", dstCore9.toFile().readText())

            assertTrue(Files.isSymbolicLink(dstVlc))
            assertEquals(Paths.get("libvlc.5.dylib"), Files.readSymbolicLink(dstVlc))
            assertEquals("LIBVLC_5_BINARY", dstVlc.toFile().readText())

            assertTrue(Files.isSymbolicLink(dstCore))
            assertEquals(Paths.get("libvlccore.9.dylib"), Files.readSymbolicLink(dstCore))
            assertEquals("LIBVLCCORE_9_BINARY", dstCore.toFile().readText())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `copyFilteredDirectoryContents preserves macOS framework symlinks without duplicating Versions and excludes symlinks from codesign targets`() {
        val tempDir = Files.createTempDirectory("genericui-framework-symlink-copy").toFile()
        try {
            val srcFrameworksRoot = File(tempDir, "srcFrameworks").apply { mkdirs() }
            val dstFrameworksRoot = File(tempDir, "dstFrameworks").apply { mkdirs() }

            val srcFw = File(srcFrameworksRoot, "SampleKit.framework")
            val versionABin = File(srcFw, "Versions/A/SampleKit")
            val versionAResPlist = File(srcFw, "Versions/A/Resources/Info.plist")
            val versionALib = File(srcFw, "Versions/A/Libraries/libhelper.dylib")
            versionABin.parentFile.mkdirs()
            versionAResPlist.parentFile.mkdirs()
            versionALib.parentFile.mkdirs()
            versionABin.writeBytes("SAMPLEKIT_BIN".encodeToByteArray())
            versionAResPlist.writeText("<plist/>")
            versionALib.writeBytes("HELPER_DYLIB".encodeToByteArray())

            val symlinksSupported = runCatching {
                Files.createSymbolicLink(File(srcFw, "Versions/Current").toPath(), Paths.get("A"))
                Files.createSymbolicLink(File(srcFw, "SampleKit").toPath(), Paths.get("Versions/Current/SampleKit"))
                Files.createSymbolicLink(File(srcFw, "Resources").toPath(), Paths.get("Versions/Current/Resources"))
                Files.createSymbolicLink(
                    File(srcFw, "Versions/A/Libraries/libhelper.1.dylib").toPath(),
                    Paths.get("libhelper.dylib")
                )
            }.isSuccess
            if (!symlinksSupported) return

            GenericUiMultiplatformConfigurator.copyFilteredDirectoryContents(
                sourceDir = srcFrameworksRoot,
                targetDir = dstFrameworksRoot,
                setExecutableOnBinaries = true
            )

            val dstFw = File(dstFrameworksRoot, "SampleKit.framework")
            val dstCurrent = File(dstFw, "Versions/Current").toPath()
            val dstTopBin = File(dstFw, "SampleKit").toPath()
            val dstTopResources = File(dstFw, "Resources").toPath()
            val dstNestedLink = File(dstFw, "Versions/A/Libraries/libhelper.1.dylib").toPath()
            val dstNestedReal = File(dstFw, "Versions/A/Libraries/libhelper.dylib").toPath()
            val dstVersionAPlist = File(dstFw, "Versions/A/Resources/Info.plist").toPath()

            assertTrue(Files.isSymbolicLink(dstCurrent))
            assertEquals(Paths.get("A"), Files.readSymbolicLink(dstCurrent))

            assertTrue(Files.isSymbolicLink(dstTopBin))
            assertEquals(Paths.get("Versions/Current/SampleKit"), Files.readSymbolicLink(dstTopBin))

            assertTrue(Files.isSymbolicLink(dstTopResources))
            assertEquals(Paths.get("Versions/Current/Resources"), Files.readSymbolicLink(dstTopResources))
            assertEquals("<plist/>", File(dstFw, "Resources/Info.plist").readText())

            assertTrue(Files.isSymbolicLink(dstNestedLink))
            assertEquals(Paths.get("libhelper.dylib"), Files.readSymbolicLink(dstNestedLink))

            assertFalse(Files.isSymbolicLink(dstNestedReal))
            assertFalse(Files.isSymbolicLink(dstVersionAPlist))

            val (regularBinaries, frameworkBundles) =
                GenericUiSymlinkPreservingCopy.collectMacCodeSignTargets(dstFrameworksRoot)
            assertEquals(listOf(dstNestedReal.toFile().absolutePath), regularBinaries.map { it.absolutePath })
            assertEquals(listOf(dstFw.absolutePath), frameworkBundles.map { it.absolutePath })
            assertTrue(regularBinaries.none { Files.isSymbolicLink(it.toPath()) })
            assertTrue(frameworkBundles.none { Files.isSymbolicLink(it.toPath()) })
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `copyDirectoryContentsPreservingSymlinks deduplicates external multi-alias symlinks to a single binary plus relative symlink`() {
        val tempDir = Files.createTempDirectory("genericui-external-alias-symlink").toFile()
        try {
            val cellarDir = File(tempDir, "cellar").apply { mkdirs() }
            val optDir = File(tempDir, "opt").apply { mkdirs() }
            val outDir = File(tempDir, "out").apply { mkdirs() }

            val cellarVersioned = File(cellarDir, "libfoo.1.dylib")
            cellarVersioned.writeBytes("LIBFOO_REAL_BYTES".encodeToByteArray())

            val symlinksSupported = runCatching {
                Files.createSymbolicLink(File(cellarDir, "libfoo.dylib").toPath(), Paths.get("libfoo.1.dylib"))
                Files.createSymbolicLink(File(optDir, "libfoo.1.dylib").toPath(), Paths.get("../cellar/libfoo.1.dylib"))
                Files.createSymbolicLink(File(optDir, "libfoo.dylib").toPath(), Paths.get("../cellar/libfoo.dylib"))
            }.isSuccess
            if (!symlinksSupported) return

            GenericUiSymlinkPreservingCopy.copyDirectoryContentsPreservingSymlinks(
                sourceDir = optDir,
                targetDir = outDir,
                sourceRoot = optDir,
                setExecutableOnBinaries = true
            )

            val outVersioned = File(outDir, "libfoo.1.dylib").toPath()
            val outAlias = File(outDir, "libfoo.dylib").toPath()

            assertFalse(Files.isSymbolicLink(outVersioned))
            assertTrue(Files.isRegularFile(outVersioned, LinkOption.NOFOLLOW_LINKS))
            assertEquals("LIBFOO_REAL_BYTES", outVersioned.toFile().readText())

            assertTrue(Files.isSymbolicLink(outAlias))
            assertEquals(Paths.get("libfoo.1.dylib"), Files.readSymbolicLink(outAlias))
            assertEquals("LIBFOO_REAL_BYTES", outAlias.toFile().readText())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `windows coff partitioner isolates dead MinGW imports so linked DLL loads cleanly`() {
        val osName = System.getProperty("os.name").orEmpty().lowercase()
        if (!osName.contains("win")) return
        val konanHome = findInstalledKonanHome() ?: return
        val konanDataDir = System.getenv("KONAN_DATA_DIR")?.takeIf { it.isNotBlank() }?.let { File(it) }
            ?: File(System.getProperty("user.home"), ".konan")
        val depsDir = File(konanDataDir, "dependencies")
        val llvmDir = depsDir.listFiles()
            ?.firstOrNull { it.isDirectory && File(it, "bin/clang.exe").isFile && File(it, "bin/ld.lld.exe").isFile }
            ?: return
        val sysRoot = File(depsDir, "msys2-mingw-w64-x86_64-2")
        if (!sysRoot.isDirectory) return

        val clangExe = File(llvmDir, "bin/clang.exe")
        val llvmArExe = File(llvmDir, "bin/llvm-ar.exe")
        val ldLldExe = File(llvmDir, "bin/ld.lld.exe")

        val tempDir = Files.createTempDirectory("genericui-coff-partition-test").toFile()
        try {
            val libSrc = File(tempDir, "lib_monolith.c").apply {
                writeText(
                    """
                    extern int _getdllprocaddr(void);
                    int used_fn(void) { return 7; }
                    int dead_fn(void) { return _getdllprocaddr(); }
                    """.trimIndent(),
                    Charsets.UTF_8
                )
            }
            val callerSrc = File(tempDir, "caller.c").apply {
                writeText(
                    """
                    extern int used_fn(void);
                    __declspec(dllexport) int caller_entry(void) { return used_fn(); }
                    """.trimIndent(),
                    Charsets.UTF_8
                )
            }
            val libObj = File(tempDir, "lib_monolith.o")
            val callerObj = File(tempDir, "caller.o")
            val archive = File(tempDir, "libpartitioned.a")
            val outDll = File(tempDir, "partition_probe.dll")

            fun runTool(args: List<String>) {
                val proc = ProcessBuilder(args).redirectErrorStream(true).start()
                val out = proc.inputStream.bufferedReader().readText()
                assertEquals(0, proc.waitFor(), "Tool failed: ${args.joinToString(" ")}\n$out")
            }

            runTool(
                listOf(
                    clangExe.absolutePath,
                    "-target", "x86_64-pc-windows-gnu",
                    "-ffunction-sections", "-fdata-sections",
                    "-c", libSrc.absolutePath,
                    "-o", libObj.absolutePath
                )
            )
            runTool(
                listOf(
                    clangExe.absolutePath,
                    "-target", "x86_64-pc-windows-gnu",
                    "-ffunction-sections", "-fdata-sections",
                    "-c", callerSrc.absolutePath,
                    "-o", callerObj.absolutePath
                )
            )

            WindowsCoffObjectPartitioner.partitionObjectsIntoArchive(
                objectFiles = listOf(libObj),
                llvmArExecutable = llvmArExe,
                targetArchive = archive
            )
            assertTrue(archive.isFile && archive.length() > 0L)

            runTool(
                listOf(
                    clangExe.absolutePath,
                    "--sysroot", sysRoot.absolutePath,
                    "-target", "x86_64-pc-windows-gnu",
                    "-shared",
                    "-fuse-ld=${ldLldExe.absolutePath}",
                    "-Wl,--gc-sections",
                    "-o", outDll.absolutePath,
                    callerObj.absolutePath,
                    archive.absolutePath,
                    "-lmsvcrt"
                )
            )
            assertTrue(outDll.isFile && outDll.length() > 0L)

            // Fails immediately with UnsatisfiedLinkError if _getdllprocaddr was pulled into msvcrt imports.
            System.load(outDll.absolutePath)
            assertNotNull(konanHome)
        } finally {
            runCatching { tempDir.deleteRecursively() }
        }
    }

    private fun writeFakeKlibDir(
        rootDir: File,
        folderName: String,
        escapedUniqueName: String,
        depends: List<String>
    ): File {
        val dir = File(rootDir, folderName)
        val manifest = File(dir, "default/manifest")
        manifest.parentFile.mkdirs()
        manifest.writeText(
            buildString {
                appendLine("unique_name=$escapedUniqueName")
                if (depends.isNotEmpty()) {
                    appendLine("depends=${depends.joinToString(" ")}")
                }
                appendLine("native_targets=macos_arm64 linux_x64 linux_arm64 mingw_x64")
            },
            Charsets.UTF_8
        )
        val zipOut = File(rootDir, "$folderName.klib")
        ZipOutputStream(zipOut.outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("default/manifest"))
            zos.write(manifest.readBytes())
            zos.closeEntry()
        }
        return dir
    }

    private fun findInstalledKonanHome(): File? {
        val konanDir = File(System.getProperty("user.home"), ".konan")
        if (!konanDir.isDirectory) return null
        return konanDir.listFiles()
            ?.filter {
                it.isDirectory &&
                    it.name.startsWith("kotlin-native-prebuilt-") &&
                    File(it, "konan/lib/kotlin-native-compiler-embeddable.jar").isFile
            }
            ?.sortedByDescending { it.name }
            ?.firstOrNull()
    }
}
