package com.nexustools.genericui.plugin

import org.gradle.testfixtures.ProjectBuilder
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Wave4PluginFeaturesTest {

    @Test
    fun `categorized extension coordinates support gamepad engine engine2d and engine3d with dsl and transitive providers`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("com.nexustools.genericui")
        val ext = project.extensions.getByType(GenericUiExtension::class.java)

        ext.extensions {
            gamepad()
        }
        assertTrue(ext.extensions.gamepadEnabled)
        assertEquals(listOf(":extensions:game:gamepad"), ext.extensions.enabledExtensionModulePaths())

        ext.extensions {
            engine()
        }
        assertTrue(ext.extensions.engineEnabled)
        assertTrue(ext.extensions.gamepadEnabled)
        assertEquals(
            listOf(":extensions:game:gamepad", ":extensions:game:engine"),
            ext.extensions.enabledExtensionModulePaths()
        )

        ext.extensions {
            engine2d()
            dsl()
        }
        val engine2dModules = ext.extensions.enabledExtensionModulePaths()
        assertTrue(":extensions:game:engine2d" in engine2dModules)
        assertTrue(":extensions:game:engine" in engine2dModules)
        assertTrue(":extensions:game:gamepad" in engine2dModules)
        assertTrue(":extensions:physics:box2d" in engine2dModules)
        assertTrue(":extensions:graphics:canvas2d" in engine2dModules)
        assertTrue(":extensions:graphics:imageprocessing" in engine2dModules)
        assertTrue(":extensions:graphics:gpu" in engine2dModules)
        assertTrue(":extensions:ui:overlay" in engine2dModules)
        assertTrue(":extensions:ui:dsl" in engine2dModules)
        assertTrue(":extensions:game:engine2d:dsl" in engine2dModules)
        assertTrue(":extensions:game:engine:dsl" in engine2dModules)
        assertFalse(":extensions:game:gamepad:dsl" in engine2dModules)

        val macProviders = ext.extensions.providerInstallerCallsForBackend(GenericUiVariantScope.MACOS)
        assertTrue("com.nexustools.genericui.ext.gpu.desktop.DesktopGpuProvider.install()" in macProviders)
        assertTrue("com.nexustools.genericui.ext.imageprocessing.context2d.SystemGpuImageBuffer.installFactory()" in macProviders)
        assertTrue("com.nexustools.genericui.ext.canvas2d.desktop.DesktopCanvas2DProvider.install()" in macProviders)

        val project3d = ProjectBuilder.builder().build()
        project3d.pluginManager.apply("com.nexustools.genericui")
        val ext3d = project3d.extensions.getByType(GenericUiExtension::class.java)
        ext3d.extensions {
            engine3d()
            dsl()
        }
        val modules3d = ext3d.extensions.enabledExtensionModulePaths()
        assertTrue(":extensions:game:engine3d" in modules3d)
        assertTrue(":extensions:graphics:threejs" in modules3d)
        assertTrue(":extensions:physics:box3d" in modules3d)
        assertTrue(":extensions:graphics:canvas3d" in modules3d)
        assertTrue(":extensions:game:engine3d:dsl" in modules3d)

        val ipadosProviders = ext3d.extensions.providerInstallerCallsForBackend(GenericUiVariantScope.IPADOS)
        assertTrue("com.nexustools.genericui.ext.gpu.mobile.MobileGpuProvider.install()" in ipadosProviders)
        assertTrue("com.nexustools.genericui.ext.canvas3d.mobile.MobileCanvas3DProvider.install()" in ipadosProviders)

        assertTrue(":extensions:ui:webview" in GenericUiGradlePlugin.KNOWN_GENERICUI_MODULES)
        assertTrue(":extensions:ui:pip" in GenericUiGradlePlugin.KNOWN_GENERICUI_MODULES)
        assertFalse(":extensions:ui:pip:dsl" in GenericUiGradlePlugin.KNOWN_GENERICUI_MODULES)
        assertTrue(":extensions:data:schema" in GenericUiGradlePlugin.KNOWN_GENERICUI_MODULES)
        assertTrue(":extensions:data:schema:dsl" in GenericUiGradlePlugin.KNOWN_GENERICUI_MODULES)
        assertTrue(":extensions:system:ipc" in GenericUiGradlePlugin.KNOWN_GENERICUI_MODULES)
        assertTrue(":extensions:system:serviceworker" in GenericUiGradlePlugin.KNOWN_GENERICUI_MODULES)
        assertFalse(":extensions:system:serviceworker:dsl" in GenericUiGradlePlugin.KNOWN_GENERICUI_MODULES)
        assertTrue(":extensions:graphics:svg" in GenericUiGradlePlugin.KNOWN_GENERICUI_MODULES)
        assertTrue(":extensions:graphics:svg:dsl" in GenericUiGradlePlugin.KNOWN_GENERICUI_MODULES)
        assertTrue(":extensions:graphics:svg:alien" in GenericUiGradlePlugin.KNOWN_GENERICUI_MODULES)
        assertTrue(":system" in GenericUiGradlePlugin.KNOWN_GENERICUI_MODULES)
        assertTrue(":jvm" in GenericUiGradlePlugin.KNOWN_GENERICUI_MODULES)
        assertTrue(":apple" in GenericUiGradlePlugin.KNOWN_GENERICUI_MODULES)
        assertTrue(":tests" in GenericUiGradlePlugin.KNOWN_GENERICUI_MODULES)
        assertEquals(52, GenericUiGradlePlugin.KNOWN_GENERICUI_MODULES.size)
    }

    @Test
    fun `webview desktop worker replaces legacy c stub bundle task cleanly`() {
        val project = ProjectBuilder.builder().withName("webview-consumer").build()
        project.pluginManager.apply("com.nexustools.genericui")
        val ext = project.extensions.getByType(GenericUiExtension::class.java)
        ext.extensions {
            webView()
        }

        assertNull(
            project.tasks.findByName("bundleGenericUiWebViewProcess"),
            "Legacy bundleGenericUiWebViewProcess C-stub task must not be registered"
        )
        assertNotNull(
            project.tasks.findByName("generateGenericUiBinaryCodecs"),
            "generateGenericUiBinaryCodecs task must be registered cleanly"
        )
    }

    @Test
    fun `ipados backend generates UIDeviceFamily 2 Xcode project shared apple jvm modules and dual ios ipados frameworks`() {
        val project = ProjectBuilder.builder().withName("tablet-suite").build()
        project.group = "com.example.tablet"
        project.version = "2.1.0"

        val srcDir = File(project.projectDir, "src/commonMain/kotlin/com/example/tablet").apply { mkdirs() }
        File(srcDir, "MainFragment.kt").writeText(
            """
            package com.example.tablet
            import com.nexustools.genericui.component.fragment.Fragment
            import com.nexustools.genericui.component.fragment.MainAppFragment
            import com.nexustools.genericui.enums.system.AppTarget

            @MainAppFragment(targets = [AppTarget.COMMON])
            class MainTabletFragment : Fragment()
            """.trimIndent()
        )

        project.pluginManager.apply("com.nexustools.genericui")
        val ext = project.extensions.getByType(GenericUiExtension::class.java)
        ext.backends {
            ios(true)
            ipados(true)
        }

        assertTrue(ext.backends.requiresAppleModule)
        assertTrue(ext.backends.requiresMobileModule)
        assertTrue(":apple" in ext.backends.enabledBackendModulePaths())
        assertTrue(":mobile" in ext.backends.enabledBackendModulePaths())
        assertTrue(":system" in ext.backends.enabledBackendModulePaths())

        val kmp = project.extensions.getByType(org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension::class.java)
        val ss = kmp.sourceSets
        assertTrue("appleCommon" in ss.names)
        assertTrue("appleMain" in ss.names)
        assertTrue("iosCommon" in ss.names)
        assertTrue("iosMain" in ss.names)
        assertTrue("ipadosMain" in ss.names)
        assertTrue("ipadosTest" in ss.names)

        assertNotNull(project.tasks.findByName("developmentIos"))
        assertNotNull(project.tasks.findByName("developmentIpados"))
        assertNotNull(project.tasks.findByName("productionIpados"))
        assertNotNull(project.tasks.findByName("testIpados"))

        for (targetName in listOf("iosArm64", "iosSimulatorArm64")) {
            val nativeTarget = kmp.targets.getByName(targetName)
            val binaries = nativeTarget.javaClass.getMethod("getBinaries").invoke(nativeTarget) as Collection<*>
            val binaryNames = binaries.mapNotNull { it?.javaClass?.getMethod("getName")?.invoke(it) as? String }
            assertTrue("debugFramework" in binaryNames, "Expected iOS debugFramework on $targetName, found $binaryNames")
            assertTrue("ipadosDebugFramework" in binaryNames, "Expected ipadosDebugFramework on $targetName, found $binaryNames")
            assertTrue("ipadosReleaseFramework" in binaryNames, "Expected ipadosReleaseFramework on $targetName, found $binaryNames")
        }

        val iosAppTask = project.tasks.getByName("generateGenericUiIosApp") as GenericUiGenerateIosAppTask
        iosAppTask.generateIosApp()
        val iosOutDir = iosAppTask.outputDirectory.get().asFile
        assertTrue(File(iosOutDir, "Info.plist").readText().contains("<integer>1</integer>"))
        assertTrue(File(iosOutDir, "project.yml").readText().contains("TARGETED_DEVICE_FAMILY: \"1\""))
        assertTrue(File(iosOutDir, "iosApp.xcodeproj/project.pbxproj").readText().contains("TARGETED_DEVICE_FAMILY = \"1\";"))

        val ipadosAppTask = project.tasks.getByName("generateGenericUiIpadosApp") as GenericUiGenerateIosAppTask
        ipadosAppTask.generateIosApp()
        val ipadosOutDir = ipadosAppTask.outputDirectory.get().asFile
        val ipadosPlist = File(ipadosOutDir, "Info.plist").readText()
        val ipadosYml = File(ipadosOutDir, "project.yml").readText()
        val ipadosPbxproj = File(ipadosOutDir, "ipadosApp.xcodeproj/project.pbxproj").readText()
        assertTrue(ipadosPlist.contains("<key>UIDeviceFamily</key>"))
        assertTrue(ipadosPlist.contains("<integer>2</integer>"))
        assertFalse(ipadosPlist.contains("<integer>1</integer>"))
        assertTrue(ipadosYml.contains("TARGETED_DEVICE_FAMILY: \"2\""))
        assertTrue(ipadosYml.contains("ipadosDebugFramework"))
        assertTrue(ipadosPbxproj.contains("TARGETED_DEVICE_FAMILY = \"2\";"))
        assertTrue(ipadosPbxproj.contains("ipadosDebugFramework"))
    }

    @Test
    fun `window alwaysOnTop and client viewport aspectRatio validate and emit into generated descriptors`() {
        val project = ProjectBuilder.builder().withName("pip-window").build()
        project.group = "com.example.pip"
        project.version = "1.0.0"
        project.pluginManager.apply("com.nexustools.genericui")

        val ext = project.extensions.getByType(GenericUiExtension::class.java)
        ext.application {
            appId = "com.example.pip"
        }
        ext.backends {
            windows()
            macos()
            android()
            ipados()
        }
        ext.window {
            alwaysOnTop = true
            aspectRatio(16, 9)
        }
        ext.macos {
            window {
                alwaysOnTop = false
                aspectRatio(4, 3)
            }
        }

        assertFailsWith<IllegalArgumentException> {
            ext.windows {
                window {
                    aspectRatio(0, 9)
                }
            }
        }
        assertFailsWith<IllegalArgumentException> {
            ext.windows {
                window {
                    aspectRatio(16, -9)
                }
            }
        }

        val winResolved = GenericUiVariantResolver.resolve(ext, GenericUiVariantScope.WINDOWS)
        assertTrue(winResolved.windowAlwaysOnTop)
        assertEquals(16.0 / 9.0, winResolved.windowAspectRatio)

        val macResolved = GenericUiVariantResolver.resolve(ext, GenericUiVariantScope.MACOS)
        assertFalse(macResolved.windowAlwaysOnTop)
        assertEquals(4.0 / 3.0, macResolved.windowAspectRatio)

        val srcDir = File(project.projectDir, "src/commonMain/kotlin/com/example/pip").apply { mkdirs() }
        File(srcDir, "PipFragment.kt").writeText(
            """
            package com.example.pip
            import com.nexustools.genericui.component.fragment.Fragment
            import com.nexustools.genericui.component.fragment.MainAppFragment
            import com.nexustools.genericui.enums.system.AppTarget

            @MainAppFragment(targets = [AppTarget.COMMON])
            class PipMainFragment : Fragment()
            """.trimIndent()
        )

        val launchersTask = project.tasks.getByName("generateGenericUiLaunchers") as GenericUiGenerateLaunchersTask
        launchersTask.generateLaunchers()
        val launchersRoot = launchersTask.outputDirectory.get().asFile

        val winDesc = File(launchersRoot, "windows/kotlin/com/example/pip/generated/GeneratedWindowsAppDescriptor.kt").readText()
        assertTrue(winDesc.contains("windowAlwaysOnTop = true"))
        assertTrue(winDesc.contains("windowAspectRatio = ${16.0 / 9.0}"))

        val macDesc = File(launchersRoot, "macos/kotlin/com/example/pip/generated/GeneratedMacosAppDescriptor.kt").readText()
        assertTrue(macDesc.contains("windowAlwaysOnTop = false"))
        assertTrue(macDesc.contains("windowAspectRatio = ${4.0 / 3.0}"))

        val ipadDesc = File(launchersRoot, "ipados/kotlin/com/example/pip/generated/GeneratedIpadosAppDescriptor.kt").readText()
        assertTrue(ipadDesc.contains("windowAlwaysOnTop = true"))
        assertTrue(ipadDesc.contains("windowAspectRatio = ${16.0 / 9.0}"))

        val androidActTask = project.tasks.getByName("generateGenericUiAndroidActivities") as GenericUiGenerateAndroidActivitiesTask
        androidActTask.generate()
        val androidApp = File(androidActTask.outputDirectory.get().asFile, "com/example/pip/GeneratedAndroidApplication.kt").readText()
        assertTrue(androidApp.contains("windowAlwaysOnTop = true"))
        assertTrue(androidApp.contains("windowAspectRatio = ${16.0 / 9.0}"))
    }

    @Test
    fun `apple icon precedence and Chrome white squircle auto wrapping produce expected descriptor flag and output pixels`() {
        // Case 1: Only generic icon configured -> auto-wrapped Chrome-style white squircle (macOS) & opaque white tile (iOS/iPadOS)
        val projectGeneric = ProjectBuilder.builder().withName("generic-icon-app").build()
        projectGeneric.group = "com.example.icons"
        projectGeneric.version = "1.0.0"
        projectGeneric.pluginManager.apply("com.nexustools.genericui")

        val brandPng = File(projectGeneric.projectDir, "brand.png")
        writeSolidRgbaPng(brandPng, size = 256, color = Color(220, 35, 50, 255))

        val srcDirGeneric = File(projectGeneric.projectDir, "src/commonMain/kotlin/com/example/icons").apply { mkdirs() }
        File(srcDirGeneric, "Main.kt").writeText(
            """
            package com.example.icons
            import com.nexustools.genericui.component.fragment.Fragment
            import com.nexustools.genericui.component.fragment.MainAppFragment
            import com.nexustools.genericui.enums.system.AppTarget

            @MainAppFragment(targets = [AppTarget.COMMON])
            class IconMainFragment : Fragment()
            """.trimIndent()
        )

        val extGeneric = projectGeneric.extensions.getByType(GenericUiExtension::class.java)
        extGeneric.backends {
            macos()
            ios()
            ipados()
        }
        extGeneric.application {
            icon("brand.png")
        }

        val resolvedMacGeneric = GenericUiVariantResolver.resolve(extGeneric, GenericUiVariantScope.MACOS)
        val resolvedIosGeneric = GenericUiVariantResolver.resolve(extGeneric, GenericUiVariantScope.IOS)
        val resolvedIpadGeneric = GenericUiVariantResolver.resolve(extGeneric, GenericUiVariantScope.IPADOS)
        assertFalse(resolvedMacGeneric.appleIconExplicitlyProvided)
        assertFalse(resolvedIosGeneric.appleIconExplicitlyProvided)
        assertFalse(resolvedIpadGeneric.appleIconExplicitlyProvided)
        assertEquals("brand.png", resolvedMacGeneric.iconPath)

        val launchersGeneric = projectGeneric.tasks.getByName("generateGenericUiLaunchers") as GenericUiGenerateLaunchersTask
        launchersGeneric.generateLaunchers()
        val macDescText = File(
            launchersGeneric.outputDirectory.get().asFile,
            "macos/kotlin/com/example/icons/generated/GeneratedMacosAppDescriptor.kt"
        ).readText()
        assertTrue(macDescText.contains("appleIconExplicitlyProvided = false"))

        val desktopResTask = projectGeneric.tasks.getByName("prepareGenericUiDesktopResources") as GenericUiDesktopResourceTask
        desktopResTask.generateDesktopResources()
        val macIcnsFile = File(desktopResTask.outputDirectory.get().asFile, "macos/icon.icns")
        assertTrue(macIcnsFile.isFile)
        val macIcnsLargestPng = decodeLargestPngChunkFromIcns(macIcnsFile)
        assertNotNull(macIcnsLargestPng)
        // Outer corner pixel must be transparent (squircle margin around Chrome-style tile)
        val cornerAlpha = (macIcnsLargestPng.getRGB(2, 2) ushr 24) and 0xFF
        assertTrue(cornerAlpha < 15, "Expected transparent corner around macOS squircle tile, got alpha=$cornerAlpha")
        // Top rim inside the squircle padding must be bright near-white
        val topWhiteRgb = macIcnsLargestPng.getRGB(macIcnsLargestPng.width / 2, (macIcnsLargestPng.height * 0.14).toInt())
        val topR = (topWhiteRgb ushr 16) and 0xFF
        val topG = (topWhiteRgb ushr 8) and 0xFF
        val topB = topWhiteRgb and 0xFF
        assertTrue(topR >= 240 && topG >= 240 && topB >= 240, "Expected bright white squircle border at top-center, got rgb=($topR,$topG,$topB)")
        // Center pixel must be the red brand artwork
        val centerRgb = macIcnsLargestPng.getRGB(macIcnsLargestPng.width / 2, macIcnsLargestPng.height / 2)
        val centerR = (centerRgb ushr 16) and 0xFF
        val centerG = (centerRgb ushr 8) and 0xFF
        assertTrue(centerR > 180 && centerG < 80, "Expected red artwork at center of macOS tile, got R=$centerR, G=$centerG")

        val iosAppTask = projectGeneric.tasks.getByName("generateGenericUiIosApp") as GenericUiGenerateIosAppTask
        iosAppTask.generateIosApp()
        val iosAppIconPng = ImageIO.read(File(iosAppTask.outputDirectory.get().asFile, "Assets.xcassets/AppIcon.appiconset/icon.png"))
        assertEquals(Color(255, 255, 255, 255).rgb, iosAppIconPng.getRGB(0, 0), "iOS AppIcon outer corner must be solid opaque white")

        val ipadosAppTask = projectGeneric.tasks.getByName("generateGenericUiIpadosApp") as GenericUiGenerateIosAppTask
        ipadosAppTask.generateIosApp()
        val ipadosAppIconPng = ImageIO.read(File(ipadosAppTask.outputDirectory.get().asFile, "Assets.xcassets/AppIcon.appiconset/icon.png"))
        assertEquals(Color(255, 255, 255, 255).rgb, ipadosAppIconPng.getRGB(0, 0), "iPadOS AppIcon outer corner must be solid opaque white")

        // Case 2 & 3: Explicit shared appleIcon vs per-target override (ipados { icon = File(...) })
        val projectExplicit = ProjectBuilder.builder().withName("explicit-apple-app").build()
        projectExplicit.group = "com.example.explicit"
        projectExplicit.version = "1.0.0"
        projectExplicit.pluginManager.apply("com.nexustools.genericui")

        val genericFile = File(projectExplicit.projectDir, "generic.png")
        val appleFile = File(projectExplicit.projectDir, "apple-shared.png")
        val ipadFile = File(projectExplicit.projectDir, "ipad-override.png")
        writeSolidRgbaPng(genericFile, size = 256, color = Color(220, 35, 50, 255))
        writeSolidRgbaPng(appleFile, size = 256, color = Color(25, 110, 240, 255))
        writeSolidRgbaPng(ipadFile, size = 256, color = Color(20, 175, 90, 255))

        val srcDirExplicit = File(projectExplicit.projectDir, "src/commonMain/kotlin/com/example/explicit").apply { mkdirs() }
        File(srcDirExplicit, "Main.kt").writeText(
            """
            package com.example.explicit
            import com.nexustools.genericui.component.fragment.Fragment
            import com.nexustools.genericui.component.fragment.MainAppFragment
            import com.nexustools.genericui.enums.system.AppTarget

            @MainAppFragment(targets = [AppTarget.COMMON])
            class ExplicitMainFragment : Fragment()
            """.trimIndent()
        )

        val extExplicit = projectExplicit.extensions.getByType(GenericUiExtension::class.java)
        extExplicit.backends {
            windows()
            macos()
            ios()
            ipados()
        }
        extExplicit.application {
            icon("generic.png")
            apple {
                icon = "apple-shared.png"
            }
            ipados {
                icon = ipadFile
            }
        }

        val winResolved = GenericUiVariantResolver.resolve(extExplicit, GenericUiVariantScope.WINDOWS)
        val macExplicit = GenericUiVariantResolver.resolve(extExplicit, GenericUiVariantScope.MACOS)
        val iosExplicit = GenericUiVariantResolver.resolve(extExplicit, GenericUiVariantScope.IOS)
        val ipadExplicit = GenericUiVariantResolver.resolve(extExplicit, GenericUiVariantScope.IPADOS)

        assertEquals("generic.png", winResolved.iconPath)
        assertFalse(winResolved.appleIconExplicitlyProvided)
        assertNull(winResolved.windowAspectRatio)

        assertEquals("apple-shared.png", macExplicit.iconPath)
        assertTrue(macExplicit.appleIconExplicitlyProvided)
        assertEquals("apple-shared.png", iosExplicit.iconPath)
        assertTrue(iosExplicit.appleIconExplicitlyProvided)
        assertEquals(ipadFile.path, ipadExplicit.iconPath)
        assertTrue(ipadExplicit.appleIconExplicitlyProvided)

        val launchersExplicit = projectExplicit.tasks.getByName("generateGenericUiLaunchers") as GenericUiGenerateLaunchersTask
        launchersExplicit.generateLaunchers()
        val macExplicitDesc = File(
            launchersExplicit.outputDirectory.get().asFile,
            "macos/kotlin/com/example/explicit/generated/GeneratedMacosAppDescriptor.kt"
        ).readText()
        assertTrue(macExplicitDesc.contains("appleIconExplicitlyProvided = true"))

        val explicitMacRes = projectExplicit.tasks.getByName("prepareGenericUiDesktopResources") as GenericUiDesktopResourceTask
        explicitMacRes.generateDesktopResources()
        val unwrappedMacIcns = decodeLargestPngChunkFromIcns(File(explicitMacRes.outputDirectory.get().asFile, "macos/icon.icns"))
        assertNotNull(unwrappedMacIcns)
        // Explicit shared blue apple icon must remain unwrapped at corners (blue, not white/transparent tile!)
        val unwrappedCorner = unwrappedMacIcns.getRGB(2, 2)
        val unwrappedCornerB = unwrappedCorner and 0xFF
        val unwrappedCornerR = (unwrappedCorner ushr 16) and 0xFF
        assertTrue(unwrappedCornerB > 200 && unwrappedCornerR < 60, "Explicit Apple icon must remain unwrapped at macOS corners")

        val explicitIpadosTask = projectExplicit.tasks.getByName("generateGenericUiIpadosApp") as GenericUiGenerateIosAppTask
        explicitIpadosTask.generateIosApp()
        val explicitIpadosIcon = ImageIO.read(File(explicitIpadosTask.outputDirectory.get().asFile, "Assets.xcassets/AppIcon.appiconset/icon.png"))
        val ipadCorner = explicitIpadosIcon.getRGB(0, 0)
        val ipadCornerG = (ipadCorner ushr 8) and 0xFF
        val ipadCornerR = (ipadCorner ushr 16) and 0xFF
        assertTrue(ipadCornerG > 140 && ipadCornerR < 60, "Explicit ipados icon override must remain unwrapped green at corners")

        // Verify compileGenericUiResources also translates per-target Apple icons (apple/, ios/, ipados/)
        val compileResTask = projectExplicit.tasks.getByName("compileGenericUiResources") as GenericUiResourceCompilerTask
        compileResTask.compileResources()
        val backendResRoot = compileResTask.backendOutputDirectory.get().asFile
        val compiledIosIcon = ImageIO.read(File(backendResRoot, "ios/Assets.xcassets/AppIcon.appiconset/icon.png"))
        assertTrue((compiledIosIcon.getRGB(0, 0) and 0xFF) > 200, "Compiled ios AppIcon should preserve unwrapped blue corner")
        assertFalse(File(backendResRoot, "apple/Assets.xcassets").exists(), "macOS must not emit loose Assets.xcassets")
        val compiledIpadosIcon = ImageIO.read(File(backendResRoot, "ipados/Assets.xcassets/AppIcon.appiconset/icon.png"))
        assertTrue(((compiledIpadosIcon.getRGB(0, 0) ushr 8) and 0xFF) > 140, "Compiled ipados AppIcon should preserve unwrapped green corner")
    }

    private fun writeSolidRgbaPng(file: File, size: Int, color: Color) {
        file.parentFile?.mkdirs()
        val img = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.color = color
        g.fillRect(0, 0, size, size)
        g.dispose()
        ImageIO.write(img, "PNG", file)
    }

    private fun decodeLargestPngChunkFromIcns(icnsFile: File): BufferedImage? {
        val bytes = icnsFile.readBytes()
        if (bytes.size < 16) return null
        var offset = 8
        var best: BufferedImage? = null
        var bestArea = -1
        while (offset + 8 <= bytes.size) {
            val chunkLen = ByteBuffer.wrap(bytes, offset + 4, 4).order(ByteOrder.BIG_ENDIAN).int
            if (chunkLen < 8 || offset + chunkLen > bytes.size) break
            val payload = bytes.copyOfRange(offset + 8, offset + chunkLen)
            if (payload.size >= 8 && payload[0] == 0x89.toByte() && payload[1] == 0x50.toByte()) {
                val img = ImageIO.read(ByteArrayInputStream(payload))
                if (img != null && img.width * img.height > bestArea) {
                    best = img
                    bestArea = img.width * img.height
                }
            }
            offset += chunkLen
        }
        return best
    }
}
