package com.nexustools.genericui.plugin

import org.gradle.testfixtures.ProjectBuilder
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PipActivityExtensionCodegenTest {

    @Test
    fun `scanner captures PiPCompatible before and after MainAppFragment and AppFragment cleanly`() {
        val discovered = GenericUiFragmentScanner.parseKotlinSource(
            """
            package com.example.pipapp

            import com.nexustools.genericui.component.fragment.AppApplication
            import com.nexustools.genericui.component.fragment.AppFragment
            import com.nexustools.genericui.component.fragment.Fragment
            import com.nexustools.genericui.component.fragment.MainAppFragment
            import com.nexustools.genericui.enums.system.AppTarget
            import com.nexustools.genericui.ext.pip.PiPCompatible

            @Deprecated("unrelated earlier declaration without braces")
            val LEGACY_FLAG = "class FakeInString @MainAppFragment"

            @PiPCompatible
            @Suppress("UNUSED", "class FakeInsideAnnotationArgs")
            @MainAppFragment(targets = [AppTarget.MOBILE_COMMON], label = "Player")
            open class BeforeMainPlayerFragment : Fragment()

            @AppFragment(targets = [AppTarget.ANDROID], label = "Inline Player")
            @PiPCompatible(autoEnter = true)
            internal open class AfterAppPlayerFragment : Fragment()

            @com.nexustools.genericui.ext.pip.PiPCompatible
            @AppFragment(targets = [AppTarget.ANDROID], label = "Qualified Before")
            class QualifiedBeforeFragment : Fragment()

            @AppFragment(targets = [AppTarget.ANDROID], label = "Qualified After")
            @com.nexustools.genericui.ext.pip.PiPCompatible
            public class QualifiedAfterFragment : Fragment()

            @AppFragment(targets = [AppTarget.ANDROID], label = "Plain Settings")
            class PlainSettingsFragment : Fragment()

            @PiPCompatible
            @AppApplication(targets = [AppTarget.COMMON])
            class SamplePipApp : com.nexustools.genericui.component.shell.Application()
            """.trimIndent()
        )

        assertEquals(6, discovered.size)

        val beforeMain = discovered.first { it.simpleName == "BeforeMainPlayerFragment" }
        assertTrue(beforeMain.isMainFragment)
        assertTrue(beforeMain.hasAnnotation("PiPCompatible"))
        assertTrue(beforeMain.hasAnnotation("@PiPCompatible"))
        assertTrue(beforeMain.hasAnnotation("pipcompatible"))
        assertTrue(beforeMain.hasAnnotation("com.nexustools.genericui.ext.pip.PiPCompatible"))
        assertTrue(beforeMain.hasAnnotation("Suppress"))
        assertFalse(beforeMain.hasAnnotation("Deprecated"))
        assertFalse(beforeMain.hasAnnotation("open"))

        val afterApp = discovered.first { it.simpleName == "AfterAppPlayerFragment" }
        assertFalse(afterApp.isMainFragment)
        assertTrue(afterApp.hasAnnotation("PiPCompatible"))
        assertFalse(afterApp.hasAnnotation("internal"))
        assertFalse(afterApp.hasAnnotation("open"))

        val qualifiedBefore = discovered.first { it.simpleName == "QualifiedBeforeFragment" }
        assertTrue(qualifiedBefore.hasAnnotation("PiPCompatible"))
        assertTrue(qualifiedBefore.hasAnnotation("com.nexustools.genericui.ext.pip.PiPCompatible"))
        assertTrue("com.nexustools.genericui.ext.pip.PiPCompatible" in qualifiedBefore.annotations)
        assertTrue("PiPCompatible" in qualifiedBefore.annotations)

        val qualifiedAfter = discovered.first { it.simpleName == "QualifiedAfterFragment" }
        assertTrue(qualifiedAfter.hasAnnotation("PiPCompatible"))
        assertTrue(qualifiedAfter.hasAnnotation("com.nexustools.genericui.ext.pip.PiPCompatible"))

        val plain = discovered.first { it.simpleName == "PlainSettingsFragment" }
        assertFalse(plain.hasAnnotation("PiPCompatible"))

        val appClass = discovered.first { it.simpleName == "SamplePipApp" }
        assertTrue(appClass.isApplicationClass)
        assertTrue(appClass.hasAnnotation("PiPCompatible"))
        assertFalse(
            PipAndroidActivityExtensionContributor.appliesTo(appClass, setOf(":extensions:ui:pip")),
            "@AppApplication should never trigger Android Activity PiP contributor"
        )
    }

    @Test
    fun `generateAndroidActivities emits open PiP Activity only when pip extension is enabled`() {
        val fragments = GenericUiFragmentScanner.parseKotlinSource(
            """
            package com.example.media

            import com.nexustools.genericui.component.fragment.AppFragment
            import com.nexustools.genericui.component.fragment.Fragment
            import com.nexustools.genericui.component.fragment.MainAppFragment
            import com.nexustools.genericui.enums.system.AppTarget
            import com.nexustools.genericui.ext.pip.PiPCompatible

            @PiPCompatible
            @MainAppFragment(targets = [AppTarget.MOBILE_COMMON], label = "Media Player")
            class PlayerHostFragment : Fragment()

            @AppFragment(targets = [AppTarget.ANDROID], label = "Detail")
            @com.nexustools.genericui.ext.pip.PiPCompatible
            class MiniDetailFragment : Fragment()

            @AppFragment(targets = [AppTarget.ANDROID], label = "Settings")
            class SettingsFragment : Fragment()
            """.trimIndent()
        )

        val resolvedConfig = GenericUiResolvedVariantConfig(
            targetScope = GenericUiVariantScope.ANDROID,
            appId = "com.example.media",
            appName = "Media App",
            appVersion = "1.0.0",
            packageName = "com.example.media"
        )

        val outWithPip = Files.createTempDirectory("genericui-pip-act-with").toFile()
        val outWithoutPip = Files.createTempDirectory("genericui-pip-act-without").toFile()
        try {
            GenericUiGenerateAndroidActivitiesTask.generateAndroidActivities(
                outputDir = outWithPip,
                discoveredFragments = fragments,
                resolvedConfig = resolvedConfig,
                enabledExtensionModulePaths = listOf(":extensions:ui:overlay", ":extensions:ui:pip")
            )

            val playerActivitySrc = File(outWithPip, "com/example/media/PlayerHostFragmentActivity.kt").readText()
            assertTrue(playerActivitySrc.contains("open class PlayerHostFragmentActivity : GenericUiFragmentActivity() {"))
            assertTrue(playerActivitySrc.contains("import android.content.res.Configuration"))
            assertTrue(playerActivitySrc.contains("import android.os.Bundle"))
            assertTrue(playerActivitySrc.contains("import com.nexustools.genericui.ext.pip.android.AndroidPipActivityDelegate"))
            assertTrue(playerActivitySrc.contains("val pipActivityDelegate: AndroidPipActivityDelegate = AndroidPipActivityDelegate(this)"))
            assertTrue(playerActivitySrc.contains("val isPipSupportedOnDevice: Boolean get() = pipActivityDelegate.isPipSupportedOnDevice"))
            assertTrue(playerActivitySrc.contains("val isCurrentlyInPipMode: Boolean get() = pipActivityDelegate.isCurrentlyInPipMode"))
            assertTrue(playerActivitySrc.contains("override fun onCreate(savedInstanceState: Bundle?)"))
            assertTrue(playerActivitySrc.contains("super.onCreate(savedInstanceState)"))
            assertTrue(playerActivitySrc.contains("pipActivityDelegate.onActivityCreated(savedInstanceState)"))
            assertTrue(playerActivitySrc.contains("override fun onDestroy()"))
            assertTrue(playerActivitySrc.contains("pipActivityDelegate.onActivityDestroyed()"))
            assertTrue(playerActivitySrc.contains("super.onDestroy()"))
            assertTrue(
                playerActivitySrc.indexOf("pipActivityDelegate.onActivityDestroyed()") <
                    playerActivitySrc.indexOf("super.onDestroy()")
            )
            assertTrue(playerActivitySrc.contains("open fun enterFragmentPictureInPicture(): Boolean = pipActivityDelegate.enterFragmentPictureInPicture()"))
            assertTrue(playerActivitySrc.contains("open fun updatePictureInPictureParams(playing: Boolean = false, width: Int = 16, height: Int = 9)"))
            assertTrue(playerActivitySrc.contains("pipActivityDelegate.updatePictureInPictureParams(playing = playing, aspectWidth = width, aspectHeight = height)"))
            assertTrue(playerActivitySrc.contains("override fun onPause()"))
            assertTrue(playerActivitySrc.contains("pipActivityDelegate.onActivityPaused()"))
            assertTrue(
                playerActivitySrc.indexOf("pipActivityDelegate.onActivityPaused()") <
                    playerActivitySrc.indexOf("super.onPause()")
            )
            assertTrue(playerActivitySrc.contains("override fun onStop()"))
            assertTrue(playerActivitySrc.contains("pipActivityDelegate.onActivityStopped()"))
            assertTrue(
                playerActivitySrc.indexOf("pipActivityDelegate.onActivityStopped()") <
                    playerActivitySrc.indexOf("super.onStop()")
            )
            assertTrue(playerActivitySrc.contains("override fun onUserLeaveHint()"))
            assertTrue(playerActivitySrc.contains("super.onUserLeaveHint()"))
            assertTrue(playerActivitySrc.contains("pipActivityDelegate.onUserLeaveHint()"))
            assertTrue(playerActivitySrc.contains("override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration)"))
            assertTrue(playerActivitySrc.contains("super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)"))
            assertTrue(playerActivitySrc.contains("pipActivityDelegate.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)"))

            val miniDetailSrc = File(outWithPip, "com/example/media/MiniDetailFragmentActivity.kt").readText()
            assertTrue(miniDetailSrc.contains("open class MiniDetailFragmentActivity : GenericUiFragmentActivity() {"))
            assertTrue(miniDetailSrc.contains("pipActivityDelegate"))
            assertTrue(miniDetailSrc.contains("enterFragmentPictureInPicture()"))

            val settingsActivitySrc = File(outWithPip, "com/example/media/SettingsFragmentActivity.kt").readText()
            assertTrue(settingsActivitySrc.contains("open class SettingsFragmentActivity : GenericUiFragmentActivity() {"))
            assertFalse(settingsActivitySrc.contains("pipActivityDelegate"))
            assertFalse(settingsActivitySrc.contains("enterFragmentPictureInPicture"))
            assertFalse(settingsActivitySrc.contains("onActivityPaused"))
            assertFalse(settingsActivitySrc.contains("onActivityStopped"))
            assertFalse(settingsActivitySrc.contains("onUserLeaveHint"))
            assertFalse(settingsActivitySrc.contains("onPictureInPictureModeChanged"))
            assertFalse(settingsActivitySrc.contains("onCreate("))
            assertFalse(settingsActivitySrc.contains("onDestroy("))

            // Without :extensions:ui:pip enabled, even @PiPCompatible fragments must NOT get PiP codegen
            GenericUiGenerateAndroidActivitiesTask.generateAndroidActivities(
                outputDir = outWithoutPip,
                discoveredFragments = fragments,
                resolvedConfig = resolvedConfig,
                enabledExtensionModulePaths = emptyList()
            )
            val playerWithoutPipSrc = File(outWithoutPip, "com/example/media/PlayerHostFragmentActivity.kt").readText()
            assertTrue(playerWithoutPipSrc.contains("open class PlayerHostFragmentActivity : GenericUiFragmentActivity() {"))
            assertFalse(playerWithoutPipSrc.contains("pipActivityDelegate"))
            assertFalse(playerWithoutPipSrc.contains("enterFragmentPictureInPicture"))
            assertFalse(playerWithoutPipSrc.contains("onActivityPaused"))
            assertFalse(playerWithoutPipSrc.contains("onActivityStopped"))
            assertFalse(playerWithoutPipSrc.contains("onUserLeaveHint"))
            assertFalse(playerWithoutPipSrc.contains("onPictureInPictureModeChanged"))
        } finally {
            outWithPip.deleteRecursively()
            outWithoutPip.deleteRecursively()
        }
    }

    @Test
    fun `manifest generator auto-enables supportsPictureInPicture and smallestScreenSize unless overridden false`() {
        val fragments = GenericUiFragmentScanner.parseKotlinSource(
            """
            package com.example.manifestpip

            import com.nexustools.genericui.component.fragment.AppFragment
            import com.nexustools.genericui.component.fragment.Fragment
            import com.nexustools.genericui.component.fragment.MainAppFragment
            import com.nexustools.genericui.enums.system.AppTarget
            import com.nexustools.genericui.ext.pip.PiPCompatible

            @PiPCompatible
            @MainAppFragment(targets = [AppTarget.MOBILE_COMMON], label = "Main Player")
            class MainPlayerFragment : Fragment()

            @AppFragment(targets = [AppTarget.ANDROID], label = "Disabled Override Player")
            @PiPCompatible
            class DisabledOverrideFragment : Fragment()

            @AppFragment(targets = [AppTarget.ANDROID], label = "Custom Subclass Player")
            @PiPCompatible
            class CustomHostFragment : Fragment()

            @AppFragment(targets = [AppTarget.ANDROID], label = "About")
            class AboutFragment : Fragment()
            """.trimIndent()
        )

        val disabledOverride = GenericUiAndroidActivityOverrideSpec("DisabledOverrideFragment").apply {
            supportsPictureInPicture = false
        }
        val customClassOverride = GenericUiAndroidActivityOverrideSpec("CustomHostFragment").apply {
            activityClass = "com.example.manifestpip.CustomAndroidActivity"
        }

        // Model default supportsPictureInPicture is explicitly false
        val model = GenericUiAndroidManifestModel(
            supportsPictureInPicture = false,
            activityOverrides = mapOf(
                "DisabledOverrideFragment" to disabledOverride,
                "CustomHostFragment" to customClassOverride
            )
        )

        val output = GenericUiAndroidManifestXmlGenerator.generate(
            appId = "com.example.manifestpip",
            appName = "Manifest PiP",
            appVersion = "1.0.0",
            model = model,
            fragments = fragments,
            enabledExtensionModulePaths = listOf(":extensions:ui:pip")
        )
        val xml = output.manifestXml

        val mainBlock = extractActivityBlock(xml, "com.example.manifestpip.MainPlayerFragmentActivity")
        assertTrue(mainBlock.contains("""android:supportsPictureInPicture="true""""))
        assertTrue(mainBlock.contains("smallestScreenSize"))
        val mainConfigTokens = extractConfigChangesTokens(mainBlock)
        assertEquals(mainConfigTokens.size, mainConfigTokens.distinct().size, "Config changes must not contain duplicates")
        assertTrue("smallestScreenSize" in mainConfigTokens)
        assertTrue("orientation" in mainConfigTokens)
        assertTrue("screenSize" in mainConfigTokens)
        assertTrue("screenLayout" in mainConfigTokens)

        val disabledBlock = extractActivityBlock(xml, "com.example.manifestpip.DisabledOverrideFragmentActivity")
        assertTrue(disabledBlock.contains("""android:supportsPictureInPicture="false""""))

        val customPrimaryBlock = extractActivityBlock(xml, "com.example.manifestpip.CustomAndroidActivity")
        assertTrue(customPrimaryBlock.contains("""android:supportsPictureInPicture="true""""))
        assertTrue("smallestScreenSize" in extractConfigChangesTokens(customPrimaryBlock))

        val customFallbackBlock = extractActivityBlock(xml, "com.example.manifestpip.CustomHostFragmentActivity")
        assertTrue(customFallbackBlock.contains("""android:supportsPictureInPicture="true""""))
        assertTrue("smallestScreenSize" in extractConfigChangesTokens(customFallbackBlock))

        val aboutBlock = extractActivityBlock(xml, "com.example.manifestpip.AboutFragmentActivity")
        assertTrue(aboutBlock.contains("""android:supportsPictureInPicture="false""""))
        assertFalse("smallestScreenSize" in extractConfigChangesTokens(aboutBlock))
    }

    @Test
    fun `extensions pip enables overlay dsl modules known modules and Contract B provider installers`() {
        val cfg = GenericUiExtensionsConfig()
        cfg.pip()
        assertTrue(cfg.pipEnabled)
        assertTrue(cfg.overlayEnabled, "Enabling pip must automatically enable overlay")
        assertEquals(
            listOf(":extensions:ui:overlay", ":extensions:ui:pip"),
            cfg.enabledExtensionModulePaths()
        )

        cfg.dsl(true)
        assertEquals(
            listOf(
                ":extensions:ui:overlay",
                ":extensions:ui:pip",
                ":extensions:ui:dsl",
                ":extensions:ui:overlay:dsl"
            ),
            cfg.enabledExtensionModulePaths()
        )
        assertFalse(":extensions:ui:pip:dsl" in cfg.enabledExtensionModulePaths())

        val allCfg = GenericUiExtensionsConfig()
        allCfg.enableAll()
        assertTrue(allCfg.pipEnabled)
        assertTrue(":extensions:ui:pip" in allCfg.enabledExtensionModulePaths())
        assertFalse(":extensions:ui:pip:dsl" in allCfg.enabledExtensionModulePaths())
        assertTrue(":extensions:ui:pip" in GenericUiGradlePlugin.KNOWN_GENERICUI_MODULES)
        assertFalse(":extensions:ui:pip:dsl" in GenericUiGradlePlugin.KNOWN_GENERICUI_MODULES)

        val expectedProviders = mapOf(
            GenericUiVariantScope.WINDOWS to "com.nexustools.genericui.ext.pip.windows.WindowsPipProvider.register()",
            GenericUiVariantScope.MACOS to "com.nexustools.genericui.ext.pip.macos.MacosPipProvider.register()",
            GenericUiVariantScope.GTK to "com.nexustools.genericui.ext.pip.gtk.GtkPipProvider.register()",
            GenericUiVariantScope.KDE to "com.nexustools.genericui.ext.pip.kde.KdePipProvider.register()",
            GenericUiVariantScope.LINUX_COMMON to "com.nexustools.genericui.ext.pip.desktop.DesktopPipProvider.register()",
            GenericUiVariantScope.NATIVE_DESKTOP_COMMON to "com.nexustools.genericui.ext.pip.desktop.DesktopPipProvider.register()",
            GenericUiVariantScope.DESKTOP_COMMON to "com.nexustools.genericui.ext.pip.desktop.DesktopPipProvider.register()",
            GenericUiVariantScope.SWING to "com.nexustools.genericui.ext.pip.swing.SwingPipProvider.register()",
            GenericUiVariantScope.ANDROID to "com.nexustools.genericui.ext.pip.android.AndroidPipProvider.register()",
            GenericUiVariantScope.IOS to "com.nexustools.genericui.ext.pip.ios.IosPipProvider.register()",
            GenericUiVariantScope.IPADOS to "com.nexustools.genericui.ext.pip.ios.IosPipProvider.register()",
            GenericUiVariantScope.IOS_COMMON to "com.nexustools.genericui.ext.pip.ios.IosPipProvider.register()",
            GenericUiVariantScope.WEB to "com.nexustools.genericui.ext.pip.web.WebPipProvider.register()"
        )

        for ((scope, expectedCall) in expectedProviders) {
            val calls = cfg.providerInstallerCallsForBackend(scope)
            assertTrue(
                expectedCall in calls,
                "Expected '$expectedCall' for scope ${scope.id}, got: $calls"
            )
        }
    }

    @Test
    fun `ios Info plist emits UIBackgroundModes audio for pip and media and plugin wires tasks`() {
        val noMediaPlist = GenericUiGenerateIosAppTask.renderInfoPlist(
            appName = "PlainApp",
            appId = "com.example.plain",
            appVersion = "1.0.0",
            enabledExtensionModulePaths = emptyList()
        )
        assertFalse(noMediaPlist.contains("<key>UIBackgroundModes</key>"))

        val pipPlist = GenericUiGenerateIosAppTask.renderInfoPlist(
            appName = "PipApp",
            appId = "com.example.pip",
            appVersion = "1.0.0",
            enabledExtensionModulePaths = listOf(":extensions:ui:pip")
        )
        assertTrue(pipPlist.contains("<key>UIBackgroundModes</key>"))
        assertTrue(pipPlist.contains("<string>audio</string>"))

        val mediaPlist = GenericUiGenerateIosAppTask.renderInfoPlist(
            appName = "MediaApp",
            appId = "com.example.media",
            appVersion = "1.0.0",
            enabledExtensionModulePaths = listOf(":extensions:media:media")
        )
        assertTrue(mediaPlist.contains("<key>UIBackgroundModes</key>"))
        assertTrue(mediaPlist.contains("<string>audio</string>"))

        val project = ProjectBuilder.builder().withName("pip-wired-app").build()
        project.group = "com.example.pipwired"
        project.version = "1.0.0"
        project.pluginManager.apply("com.nexustools.genericui")

        val ext = project.extensions.getByType(GenericUiExtension::class.java)
        ext.backends {
            android()
            ios()
            ipados()
        }
        ext.extensions {
            pip()
        }

        val srcDir = File(project.projectDir, "src/commonMain/kotlin/com/example/pipwired").apply { mkdirs() }
        File(srcDir, "WatchFragment.kt").writeText(
            """
            package com.example.pipwired

            import com.nexustools.genericui.component.fragment.Fragment
            import com.nexustools.genericui.component.fragment.MainAppFragment
            import com.nexustools.genericui.enums.system.AppTarget
            import com.nexustools.genericui.ext.pip.PiPCompatible

            @PiPCompatible
            @MainAppFragment(targets = [AppTarget.COMMON], label = "Watch")
            class WatchFragment : Fragment()
            """.trimIndent()
        )

        val actTask = project.tasks.getByName("generateGenericUiAndroidActivities") as GenericUiGenerateAndroidActivitiesTask
        actTask.generate()
        val actSrc = File(actTask.outputDirectory.get().asFile, "com/example/pipwired/WatchFragmentActivity.kt").readText()
        assertTrue(actSrc.contains("open class WatchFragmentActivity : GenericUiFragmentActivity() {"))
        assertTrue(actSrc.contains("pipActivityDelegate"))
        assertTrue(actSrc.contains("enterFragmentPictureInPicture()"))

        val manifestTask = project.tasks.getByName("generateGenericUiAndroidManifest") as GenericUiGenerateAndroidManifestTask
        manifestTask.generate()
        val manifestXml = manifestTask.outputManifestFile.get().asFile.readText()
        assertTrue(manifestXml.contains("""android:supportsPictureInPicture="true""""))
        assertTrue(manifestXml.contains("smallestScreenSize"))

        val iosTask = project.tasks.getByName("generateGenericUiIosApp") as GenericUiGenerateIosAppTask
        iosTask.generateIosApp()
        val iosPlist = File(iosTask.outputDirectory.get().asFile, "Info.plist").readText()
        assertTrue(iosPlist.contains("<key>UIBackgroundModes</key>"))
        assertTrue(iosPlist.contains("<string>audio</string>"))

        val ipadosTask = project.tasks.getByName("generateGenericUiIpadosApp") as GenericUiGenerateIosAppTask
        ipadosTask.generateIosApp()
        val ipadosPlist = File(ipadosTask.outputDirectory.get().asFile, "Info.plist").readText()
        assertTrue(ipadosPlist.contains("<key>UIBackgroundModes</key>"))
        assertTrue(ipadosPlist.contains("<string>audio</string>"))
    }

    @Test
    fun `VlcNativeBundleTask retains video_output vout d3d11 gl opengl direct3d and chroma plugins`() {
        val tempRoot = Files.createTempDirectory("genericui-vlc-plugins-test").toFile()
        try {
            val srcPlugins = File(tempRoot, "src/plugins").apply { mkdirs() }
            val dstPlugins = File(tempRoot, "dst/plugins")

            val expectedRetained = listOf(
                "video_output/libdirect3d11_plugin.dll",
                "video_output/libdirect3d9_plugin.dll",
                "video_output/libgl_plugin.dll",
                "video_output/libglwin32_plugin.dll",
                "video_output/libvmem_plugin.dll",
                "d3d11/libdirect3d11_filters_plugin.dll",
                "d3d9/libdirect3d9_filters_plugin.dll",
                "video_chroma/libswscale_plugin.dll",
                "video_chroma/libyuvp_plugin.dll",
                "vout/libvout_custom_plugin.so",
                "opengl/libopengl_plugin.so",
                "direct3d/libdirect3d_plugin.dll",
                "chroma/libchroma_plugin.so",
                "libvout_macosx_plugin.dylib",
                "libgl_plugin.dylib"
            )
            for (rel in expectedRetained) {
                File(srcPlugins, rel).apply {
                    parentFile.mkdirs()
                    writeText("stub-plugin")
                }
            }

            val expectedExcluded = listOf(
                "video_output/libcaca_plugin.dll",
                "access/libvnc_plugin.dll"
            )
            for (rel in expectedExcluded) {
                File(srcPlugins, rel).apply {
                    parentFile.mkdirs()
                    writeText("excluded-plugin")
                }
            }

            VlcNativeBundleTask.copyVlcPluginsFiltered(srcPlugins, dstPlugins)

            for (rel in expectedRetained) {
                assertTrue(
                    File(dstPlugins, rel).isFile,
                    "Expected VLC plugin '$rel' to be retained in staged plugins/"
                )
            }
            for (rel in expectedExcluded) {
                assertFalse(
                    File(dstPlugins, rel).exists(),
                    "Expected heavy/excluded VLC plugin '$rel' to be omitted"
                )
            }
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    private fun extractActivityBlock(manifestXml: String, activityName: String): String {
        val marker = """android:name="$activityName""""
        val idx = manifestXml.indexOf(marker)
        assertTrue(idx >= 0, "Activity '$activityName' not found in manifest:\n$manifestXml")
        val openIdx = manifestXml.lastIndexOf("<activity", idx)
        val selfCloseIdx = manifestXml.indexOf("/>", idx)
        val fullCloseIdx = manifestXml.indexOf("</activity>", idx)
        val tagStartAfterOpen = manifestXml.indexOf(">", idx)
        val endIdx = if (tagStartAfterOpen > 0 && manifestXml[tagStartAfterOpen - 1] == '/') {
            selfCloseIdx + 2
        } else {
            fullCloseIdx + "</activity>".length
        }
        return manifestXml.substring(openIdx, endIdx)
    }

    private fun extractConfigChangesTokens(activityBlock: String): List<String> {
        val match = Regex("""android:configChanges="([^"]*)"""").find(activityBlock) ?: return emptyList()
        return match.groupValues[1].split('|').map { it.trim() }.filter { it.isNotEmpty() }
    }
}
