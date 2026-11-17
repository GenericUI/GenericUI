package com.nexustools.genericui.plugin

import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidManifestDslTest {

    @Test
    fun `default manifest omits optional attributes while setting adaptive icons`() {
        val project = ProjectBuilder.builder().withName("minimal-manifest-app").build()
        project.group = "com.example.minimal"
        project.version = "1.2.3"
        project.pluginManager.apply("com.nexustools.genericui")
        project.extensions.getByType(GenericUiExtension::class.java).backends {
            android()
        }

        File(project.projectDir, "src/commonMain/kotlin/com/example/minimal/MainFragment.kt").apply {
            parentFile.mkdirs()
            writeText(
                """
                package com.example.minimal
                import com.nexustools.genericui.component.fragment.Fragment
                import com.nexustools.genericui.component.fragment.MainAppFragment
                @MainAppFragment
                class MainFragment : Fragment()
                """.trimIndent()
            )
        }

        val manifestTask = project.tasks.getByName("generateGenericUiAndroidManifest") as GenericUiGenerateAndroidManifestTask
        manifestTask.generate()

        val xml = manifestTask.outputManifestFile.get().asFile.readText()
        assertTrue(xml.contains("""android:icon="@mipmap/ic_launcher""""))
        assertTrue(xml.contains("""android:roundIcon="@mipmap/ic_launcher_round""""))
        assertTrue(xml.contains("""android:supportsRtl="true""""))
        assertFalse(xml.contains("android:usesCleartextTraffic="))
        assertFalse(xml.contains("android:allowBackup="))
        assertFalse(xml.contains("android:largeHeap="))
        assertFalse(xml.contains("android:networkSecurityConfig="))
        assertEquals(10203, GenericUiAndroidManifestXmlGenerator.deriveVersionCode("1.2.3"))
    }

    @Test
    fun `full manifest DSL generates network security config, rich components, activity precedence, and variants`() {
        val project = ProjectBuilder.builder().withName("full-dsl-app").build()
        project.group = "com.example.fulldsl"
        project.version = "2.4.5"
        project.pluginManager.apply("com.nexustools.genericui")

        val srcDir = File(project.projectDir, "src/commonMain/kotlin/com/example/fulldsl").apply { mkdirs() }
        File(srcDir, "ConnectFragment.kt").writeText(
            """
            package com.example.fulldsl
            import com.nexustools.genericui.component.fragment.Fragment
            import com.nexustools.genericui.component.fragment.MainAppFragment
            @MainAppFragment
            class ConnectFragment : Fragment()
            """.trimIndent()
        )
        File(srcDir, "PlayerFragment.kt").writeText(
            """
            package com.example.fulldsl
            import com.nexustools.genericui.component.fragment.Fragment
            import com.nexustools.genericui.component.fragment.AppFragment
            @AppFragment(screenOrientation = "sensorLandscape", label = "Annotation Player")
            class PlayerFragment : Fragment()
            """.trimIndent()
        )

        val ext = project.extensions.getByType(GenericUiExtension::class.java)
        ext.app {
            id = "com.example.fulldsl"
            name = "Full DSL App"
            version = "2.4.5"
            packageName = "com.example.fulldsl"
        }
        ext.backends {
            android()
        }
        ext.application.desktop.webManifest.shortcut(
            name = "Web Shortcut",
            shortName = "WebShort",
            description = "Inherited shortcut",
            url = "./?tab=web"
        )

        ext.android {
            manifest {
                versionCode = 42
                versionName = "2.4.5"
                usesCleartextTraffic = true
                allowBackup = false
                largeHeap = true
                hardwareAccelerated = true
                enableOnBackInvokedCallback = false
                appCategory = "video"
                placeholder("CUSTOM_KEY", "hello-world")
                applicationMetaData("com.example.META", value = "\${CUSTOM_KEY}-\${applicationId}")
                applicationAttribute("android:requestRawExternalStorageAccess", "true")
                manifestAttribute("tools:ignore", "LockedOrientationActivity")

                networkSecurity {
                    cleartextPermittedByDefault = false
                    cleartextDomain("10.0.0.233")
                    domainConfig("api.example.com", includeSubdomains = true) {
                        cleartextPermitted = false
                        pinSet("2027-12-31") {
                            pin("SHA-256", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
                        }
                    }
                    debugOverrides {
                        trustAnchors {
                            user()
                        }
                    }
                }

                activityDefaults {
                    launchMode = "singleTop"
                    windowSoftInputMode = "adjustResize"
                    configChanges = "orientation|screenSize|smallestScreenSize|keyboardHidden"
                    resizeableActivity = true
                    property("android.window.PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY", "true")
                }

                deepLink("episodecaster://connect", autoVerify = false)

                activity("PlayerFragment") {
                    screenOrientation = "portrait" // @AppFragment annotation ("sensorLandscape") must win
                    showWhenLocked = true
                    windowSoftInputMode = "adjustNothing"
                }

                permissionGroup {
                    network()
                    bluetooth(nearbyDevicesOnly = true)
                    storageMedia(images = true, video = true, audio = true)
                }
                permission("android.permission.CAMERA", maxSdkVersion = 34)
                usesFeature("android.hardware.touchscreen", required = false)
                foregroundService("com.example.fulldsl.PlaybackService", "mediaPlayback")

                androidTv {
                    enabled = true
                    banner = "@drawable/tv_banner"
                }
                androidAuto {
                    mediaBrowserService = "com.example.fulldsl.MusicBrowserService"
                }
                shortcuts {
                    shortcut("music", "Music Mode", longLabel = "Open Music Mode", urlOrAction = "episodecaster://music")
                }
                locales("en", "fr")
                backupRules {
                    include("sharedpref", "settings.xml")
                    exclude("file", "cache/")
                }
                fileProvider {
                    path("cache", "offline-music/", name = "offline_music")
                }
                widget("com.example.fulldsl.NowPlayingWidgetProvider") {
                    minWidthDp = 250
                    minHeightDp = 110
                }
                queries {
                    packageName("com.google.android.youtube")
                    intentAction("android.media.browse.MediaBrowserService")
                }
                receiver("androidx.media.session.MediaButtonReceiver") {
                    exported = true
                    intentAction("android.intent.action.MEDIA_BUTTON")
                }
            }

            variant("development") {
                applicationIdSuffix = ".dev"
                versionNameSuffix = "-dev"
                debuggable = true
                manifest {
                    usesCleartextTraffic = true
                }
            }
        }

        val manifestTask = project.tasks.getByName("generateGenericUiAndroidManifest") as GenericUiGenerateAndroidManifestTask
        manifestTask.generate()

        val manifestXml = manifestTask.outputManifestFile.get().asFile.readText()
        val resDir = manifestTask.outputResDirectory.get().asFile

        assertTrue(manifestXml.contains("""xmlns:tools="http://schemas.android.com/tools""""))
        assertTrue(manifestXml.contains("""tools:ignore="LockedOrientationActivity""""))
        assertTrue(manifestXml.contains("""android:usesCleartextTraffic="true""""))
        assertTrue(manifestXml.contains("""android:allowBackup="false""""))
        assertTrue(manifestXml.contains("""android:largeHeap="true""""))
        assertTrue(manifestXml.contains("""android:networkSecurityConfig="@xml/network_security_config""""))
        assertTrue(manifestXml.contains("""android:fullBackupContent="@xml/backup_rules""""))
        assertTrue(manifestXml.contains("""android:dataExtractionRules="@xml/data_extraction_rules""""))
        assertTrue(manifestXml.contains("""android:localeConfig="@xml/locales_config""""))
        assertTrue(manifestXml.contains("""android:banner="@drawable/tv_banner""""))
        assertTrue(manifestXml.contains("""android:value="hello-world-com.example.fulldsl""""))
        assertTrue(manifestXml.contains("""android:requestRawExternalStorageAccess="true""""))
        assertTrue(manifestXml.contains("""android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK""""))
        assertTrue(manifestXml.contains("""android:maxSdkVersion="30""""))
        assertTrue(manifestXml.contains("""android:usesPermissionFlags="neverForLocation""""))
        assertTrue(manifestXml.contains("""android.hardware.touchscreen"""))
        assertTrue(manifestXml.contains("""android.software.leanback"""))
        assertTrue(manifestXml.contains("""android:launchMode="singleTop""""))
        assertTrue(manifestXml.contains("""PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY"""))
        assertTrue(manifestXml.contains("""android:scheme="episodecaster""""))
        assertTrue(manifestXml.contains("""android:host="connect""""))
        assertTrue(manifestXml.contains("""android.intent.category.LEANBACK_LAUNCHER"""))
        assertTrue(manifestXml.contains("""android:showWhenLocked="true""""))
        assertTrue(manifestXml.contains("""android:label="Annotation Player""""))
        assertTrue(manifestXml.contains("""android:screenOrientation="sensorLandscape""""))
        assertTrue(manifestXml.contains("""com.google.android.gms.car.application"""))
        assertTrue(manifestXml.contains("""android.media.browse.MediaBrowserService"""))
        assertTrue(manifestXml.contains("""android.app.shortcuts"""))
        assertTrue(manifestXml.contains("""androidx.core.content.FileProvider"""))
        assertTrue(manifestXml.contains("""android.appwidget.action.APPWIDGET_UPDATE"""))
        assertTrue(manifestXml.contains("""androidx.media.session.MediaButtonReceiver"""))

        val netSecXml = File(resDir, "xml/network_security_config.xml").readText()
        assertTrue(netSecXml.contains("""<domain>10.0.0.233</domain>"""))
        assertTrue(netSecXml.contains("""<domain includeSubdomains="true">api.example.com</domain>"""))
        assertTrue(netSecXml.contains("""<pin-set expiration="2027-12-31">"""))
        assertTrue(netSecXml.contains("""<debug-overrides>"""))
        assertTrue(File(resDir, "xml/automotive_app_desc.xml").readText().contains("""<uses name="media" />"""))
        assertTrue(File(resDir, "xml/locales_config.xml").readText().contains("""android:name="fr""""))
        assertTrue(File(resDir, "xml/backup_rules.xml").readText().contains("""sharedpref"""))
        assertTrue(File(resDir, "xml/data_extraction_rules.xml").readText().contains("""cloud-backup"""))
        assertTrue(File(resDir, "xml/file_paths.xml").readText().contains("""<cache-path name="offline_music" path="offline-music/" />"""))
        assertTrue(File(resDir, "xml/shortcuts.xml").readText().contains("""android:shortcutId="music""""))

        // Variant output
        val devManifest = File(manifestTask.outputDirectory.get().asFile, "variants/development/AndroidManifest.xml").readText()
        assertTrue(devManifest.contains("""package="com.example.fulldsl.dev""""))
        assertTrue(devManifest.contains("""android:value="hello-world-com.example.fulldsl.dev""""))
    }

    @Test
    fun `manifest validation rejects CIDR domains, invalid versionCode, launchMode, and softInputMode`() {
        val cidrEx = assertFailsWith<GradleException> {
            GenericUiAndroidManifestConfig().apply {
                networkSecurity {
                    cleartextDomain("10.0.0.0/8")
                }
            }
        }
        assertTrue(cidrEx.message!!.contains("CIDR"))

        val cidrFromModelEx = assertFailsWith<GradleException> {
            GenericUiAndroidNetworkSecurityConfig().apply {
                domainConfigs.add(
                    GenericUiAndroidNetworkDomainConfigSpec().apply {
                        domains.add("10.0.0.0/8")
                    }
                )
            }.validate()
        }
        assertTrue(cidrFromModelEx.message!!.contains("CIDR"))

        val badVersion = GenericUiAndroidManifestModel(versionCode = 0)
        assertFailsWith<GradleException> {
            GenericUiAndroidManifestXmlGenerator.validate(badVersion, "1.0.0")
        }

        val badLaunchMode = GenericUiAndroidManifestModel(
            activityDefaults = GenericUiAndroidActivityDefaultsSpec().apply { launchMode = "invalidMode" }
        )
        assertFailsWith<GradleException> {
            GenericUiAndroidManifestXmlGenerator.validate(badLaunchMode, "1.0.0")
        }

        val badSoftInput = GenericUiAndroidManifestModel(
            activityDefaults = GenericUiAndroidActivityDefaultsSpec().apply { windowSoftInputMode = "stateHidden|adjustBogus" }
        )
        assertFailsWith<GradleException> {
            GenericUiAndroidManifestXmlGenerator.validate(badSoftInput, "1.0.0")
        }
    }

    @Test
    fun `ManifestMerger2 merges src androidMain AndroidManifest overlay with tools replace and meta-data`() {
        val project = ProjectBuilder.builder().withName("overlay-app").build()
        project.group = "com.example.overlay"
        project.pluginManager.apply("com.nexustools.genericui")
        project.extensions.getByType(GenericUiExtension::class.java).backends {
            android()
        }

        File(project.projectDir, "src/commonMain/kotlin/com/example/overlay/MainFragment.kt").apply {
            parentFile.mkdirs()
            writeText(
                """
                package com.example.overlay
                import com.nexustools.genericui.component.fragment.Fragment
                import com.nexustools.genericui.component.fragment.MainAppFragment
                @MainAppFragment
                class MainFragment : Fragment()
                """.trimIndent()
            )
        }

        File(project.projectDir, "src/androidMain/AndroidManifest.xml").apply {
            parentFile.mkdirs()
            writeText(
                """
                <?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:tools="http://schemas.android.com/tools"
                    package="com.example.overlay">
                    <application
                        android:theme="@style/CustomOverlayTheme"
                        tools:replace="android:theme">
                        <meta-data
                            android:name="com.example.overlay.EXTRA"
                            android:value="from-overlay" />
                    </application>
                </manifest>
                """.trimIndent()
            )
        }

        val manifestTask = project.tasks.getByName("generateGenericUiAndroidManifest") as GenericUiGenerateAndroidManifestTask
        manifestTask.generate()

        val mergedXml = manifestTask.outputManifestFile.get().asFile.readText()
        assertTrue(mergedXml.contains("""android:theme="@style/CustomOverlayTheme""""), "ManifestMerger2 should replace theme:\n$mergedXml")
        assertTrue(mergedXml.contains("""com.example.overlay.EXTRA"""), "ManifestMerger2 should merge overlay meta-data:\n$mergedXml")
        assertTrue(mergedXml.contains("""from-overlay"""))
    }

    @Test
    fun `mainActivity custom activityClass emits single LAUNCHER activity and non-exported fallback`() {
        val project = ProjectBuilder.builder().withName("custom-main-activity-app").build()
        project.group = "com.example.custommain"
        project.version = "1.0.0"
        project.pluginManager.apply("com.nexustools.genericui")

        File(project.projectDir, "src/commonMain/kotlin/com/example/custommain/HostFragment.kt").apply {
            parentFile.mkdirs()
            writeText(
                """
                package com.example.custommain
                import com.nexustools.genericui.component.fragment.Fragment
                import com.nexustools.genericui.component.fragment.MainAppFragment
                @MainAppFragment
                class HostFragment : Fragment()
                """.trimIndent()
            )
        }

        val ext = project.extensions.getByType(GenericUiExtension::class.java)
        ext.app {
            id = "com.example.custommain"
            name = "Custom Main App"
            version = "1.0.0"
            packageName = "com.example.custommain"
        }
        ext.backends {
            android()
        }
        ext.android {
            manifest {
                activityDefaults {
                    launchMode = "singleTop"
                    windowSoftInputMode = "adjustResize"
                    property("android.window.PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY", "true")
                }
                mainActivity {
                    activityClass = "com.example.custommain.CustomMainActivity"
                }
            }
        }

        val manifestTask = project.tasks.getByName("generateGenericUiAndroidManifest") as GenericUiGenerateAndroidManifestTask
        manifestTask.generate()

        val xml = manifestTask.outputManifestFile.get().asFile.readText()
        assertTrue(xml.contains("""android:name="com.example.custommain.CustomMainActivity""""))
        assertTrue(xml.contains("""android:name="com.example.custommain.HostFragmentActivity""""))
        assertTrue(xml.contains("""android:exported="false""""))
        assertEquals(1, Regex("""android\.intent\.category\.LAUNCHER""").findAll(xml).count(), "Expected exactly 1 LAUNCHER intent-filter:\n$xml")
        assertEquals(1, Regex("""android:launchMode="singleTop"""").findAll(xml).count())
        assertEquals(1, Regex("""android:windowSoftInputMode="adjustResize"""").findAll(xml).count())
    }
}
