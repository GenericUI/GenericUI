package com.nexustools.genericui.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Generates a self-contained Swift/Xcode iOS or iPadOS host application project (`build/genericui/iosApp/`
 * or `build/genericui/ipadosApp/`) that embeds the Kotlin/Native framework (`MainViewController()`)
 * inside a SwiftUI `@main` application (`iOSApp.swift` + `ContentView.swift` + `Info.plist` +
 * `Assets.xcassets/AppIcon.appiconset` + `project.yml` + `<app>.xcodeproj/project.pbxproj`).
 */
abstract class GenericUiGenerateIosAppTask : DefaultTask() {

    @get:Input
    abstract val iosEnabled: Property<Boolean>

    @get:Input
    abstract val projectRootPath: Property<String>

    @get:Input
    abstract val baseName: Property<String>

    @get:Input
    abstract val appName: Property<String>

    @get:Input
    abstract val appId: Property<String>

    @get:Input
    abstract val appVersion: Property<String>

    @get:Input
    @get:Optional
    abstract val frameworkName: Property<String>

    @get:Input
    @get:Optional
    abstract val minimumOsVersion: Property<String>

    @get:Input
    @get:Optional
    abstract val supportedOrientations: ListProperty<String>

    @get:Input
    @get:Optional
    abstract val deviceFamily: Property<Int>

    @get:Input
    @get:Optional
    abstract val frameworkSearchSubdirDebug: Property<String>

    @get:Input
    @get:Optional
    abstract val frameworkSearchSubdirRelease: Property<String>

    @get:Input
    @get:Optional
    abstract val iconPath: Property<String>

    @get:Input
    @get:Optional
    abstract val fallbackIconPath: Property<String>

    @get:Input
    @get:Optional
    abstract val appleIconExplicitlyProvided: Property<Boolean>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    init {
        deviceFamily.convention(DEVICE_FAMILY_IPHONE)
        frameworkSearchSubdirDebug.convention("debugFramework")
        frameworkSearchSubdirRelease.convention("releaseFramework")
        appleIconExplicitlyProvided.convention(false)
    }

    @TaskAction
    fun generateIosApp() {
        val outDir = outputDirectory.get().asFile
        if (!iosEnabled.getOrElse(true)) {
            outDir.mkdirs()
            return
        }

        val resolvedBaseName = baseName.orNull?.trim().orEmpty().ifEmpty { "GenericUiApp" }
        val resolvedAppName = appName.orNull?.trim().orEmpty().ifEmpty { resolvedBaseName }
        val resolvedAppId = appId.orNull?.trim().orEmpty().ifEmpty { "com.nexustools.genericui.app" }
        val resolvedAppVersion = appVersion.orNull?.trim().orEmpty().ifEmpty { "1.0.0" }
        val resolvedFramework = sanitizeFrameworkName(
            frameworkName.orNull?.trim().orEmpty().ifEmpty { resolvedBaseName }
        )
        val resolvedMinOs = minimumOsVersion.orNull?.trim().orEmpty().ifEmpty { DEFAULT_MIN_IOS_VERSION }
        val resolvedOrientations = supportedOrientations.orNull
            ?.filter { it.isNotBlank() }
            ?.ifEmpty { null }
            ?: DEFAULT_ORIENTATIONS
        val resolvedDeviceFamily = deviceFamily.getOrElse(DEVICE_FAMILY_IPHONE)
        val debugFwDir = frameworkSearchSubdirDebug.orNull?.trim().orEmpty().ifEmpty { "debugFramework" }
        val releaseFwDir = frameworkSearchSubdirRelease.orNull?.trim().orEmpty().ifEmpty { "releaseFramework" }
        val rootDir = projectRootPath.orNull?.trim()?.takeIf { it.isNotEmpty() }?.let { File(it) }

        generateIosHostProject(
            outputDir = outDir,
            appName = resolvedAppName,
            appId = resolvedAppId,
            appVersion = resolvedAppVersion,
            frameworkName = resolvedFramework,
            minimumOsVersion = resolvedMinOs,
            orientations = resolvedOrientations,
            deviceFamily = resolvedDeviceFamily,
            frameworkSearchSubdirDebug = debugFwDir,
            frameworkSearchSubdirRelease = releaseFwDir,
            projectRootDir = rootDir,
            iconPath = iconPath.orNull ?: fallbackIconPath.orNull,
            appleIconExplicitlyProvided = appleIconExplicitlyProvided.getOrElse(false)
        )
    }

    companion object {
        const val DEFAULT_MIN_IOS_VERSION: String = "15.0"
        const val DEVICE_FAMILY_IPHONE: Int = 1
        const val DEVICE_FAMILY_IPAD: Int = 2

        val DEFAULT_ORIENTATIONS: List<String> = listOf(
            "UIInterfaceOrientationPortrait",
            "UIInterfaceOrientationLandscapeLeft",
            "UIInterfaceOrientationLandscapeRight",
            "UIInterfaceOrientationPortraitUpsideDown"
        )

        /**
         * Sanitizes an arbitrary project/base name into a valid Swift/Clang module identifier.
         */
        fun sanitizeFrameworkName(rawName: String): String {
            val cleaned = buildString {
                var capitalizeNext = true
                for (ch in rawName.trim()) {
                    if (ch.isLetterOrDigit()) {
                        append(if (capitalizeNext) ch.uppercaseChar() else ch)
                        capitalizeNext = false
                    } else {
                        capitalizeNext = true
                    }
                }
            }
            if (cleaned.isEmpty()) return "GenericUiApp"
            return if (cleaned.first().isDigit()) "App$cleaned" else cleaned
        }

        /**
         * Generates the complete Swift/Xcode host project (`Info.plist`, `iOSApp.swift`,
         * `ContentView.swift`, `Assets.xcassets/AppIcon.appiconset`, `project.yml`, and
         * `<xcodeProjName>.xcodeproj/project.pbxproj`) inside [outputDir].
         */
        fun generateIosHostProject(
            outputDir: File,
            appName: String,
            appId: String,
            appVersion: String,
            frameworkName: String = "GenericUiApp",
            minimumOsVersion: String = DEFAULT_MIN_IOS_VERSION,
            orientations: List<String> = DEFAULT_ORIENTATIONS,
            deviceFamily: Int = DEVICE_FAMILY_IPHONE,
            frameworkSearchSubdirDebug: String = "debugFramework",
            frameworkSearchSubdirRelease: String = "releaseFramework",
            projectRootDir: File? = null,
            iconPath: String? = null,
            appleIconExplicitlyProvided: Boolean = false
        ): List<File> {
            outputDir.mkdirs()
            val sanitizedFramework = sanitizeFrameworkName(frameworkName)
            val xcodeProjName = if (deviceFamily == DEVICE_FAMILY_IPAD) "ipadosApp" else "iosApp"
            val generatedFiles = mutableListOf<File>()

            val infoPlistFile = File(outputDir, "Info.plist")
            infoPlistFile.writeText(
                renderInfoPlist(
                    appName = appName,
                    appId = appId,
                    appVersion = appVersion,
                    orientations = orientations,
                    deviceFamily = deviceFamily
                ),
                Charsets.UTF_8
            )
            generatedFiles.add(infoPlistFile)

            val iosAppSwiftFile = File(outputDir, "iOSApp.swift")
            iosAppSwiftFile.writeText(renderIosAppSwift(), Charsets.UTF_8)
            generatedFiles.add(iosAppSwiftFile)

            val contentViewSwiftFile = File(outputDir, "ContentView.swift")
            contentViewSwiftFile.writeText(
                renderContentViewSwift(frameworkName = sanitizedFramework),
                Charsets.UTF_8
            )
            generatedFiles.add(contentViewSwiftFile)

            val xcassetsDir = File(outputDir, "Assets.xcassets").apply { mkdirs() }
            val xcassetsContents = File(xcassetsDir, "Contents.json")
            xcassetsContents.writeText(
                """
                {
                  "info": { "version": 1, "author": "genericui" }
                }
                """.trimIndent() + "\n",
                Charsets.UTF_8
            )
            generatedFiles.add(xcassetsContents)

            val appIconSetDir = File(xcassetsDir, "AppIcon.appiconset").apply { mkdirs() }
            val sourceIconFile = resolveIconCandidateFile(projectRootDir, iconPath)
            val generatedIconPng = GenericUiDefaultIconProvider.ensurePngFromSourceOrDefault(
                sourceIconFile = sourceIconFile,
                projectRootDir = projectRootDir,
                outputDir = appIconSetDir,
                fileName = GenericUiDefaultIconProvider.DEFAULT_ICON_PNG_FILE_NAME,
                wrapInAppleTile = !appleIconExplicitlyProvided,
                fullBleedSquare = true,
                requestedSizeOverride = 1024
            )
            generatedFiles.add(generatedIconPng)

            val idiom = if (deviceFamily == DEVICE_FAMILY_IPAD) "ipad" else "iphone"
            val appIconContents = File(appIconSetDir, "Contents.json")
            appIconContents.writeText(
                """
                {
                  "images": [
                    {
                      "filename": "icon.png",
                      "idiom": "universal",
                      "platform": "ios",
                      "size": "1024x1024"
                    },
                    {
                      "filename": "icon.png",
                      "idiom": "$idiom",
                      "scale": "1x",
                      "size": "1024x1024"
                    }
                  ],
                  "info": { "version": 1, "author": "genericui" }
                }
                """.trimIndent() + "\n",
                Charsets.UTF_8
            )
            generatedFiles.add(appIconContents)

            val projectYmlFile = File(outputDir, "project.yml")
            projectYmlFile.writeText(
                renderProjectYml(
                    appName = appName,
                    appId = appId,
                    appVersion = appVersion,
                    frameworkName = sanitizedFramework,
                    minimumOsVersion = minimumOsVersion,
                    deviceFamily = deviceFamily,
                    projectName = xcodeProjName,
                    frameworkSearchSubdirDebug = frameworkSearchSubdirDebug,
                    frameworkSearchSubdirRelease = frameworkSearchSubdirRelease
                ),
                Charsets.UTF_8
            )
            generatedFiles.add(projectYmlFile)

            val xcodeProjDir = File(outputDir, "$xcodeProjName.xcodeproj").apply { mkdirs() }
            val pbxprojFile = File(xcodeProjDir, "project.pbxproj")
            pbxprojFile.writeText(
                renderPbxproj(
                    appName = appName,
                    appId = appId,
                    appVersion = appVersion,
                    frameworkName = sanitizedFramework,
                    minimumOsVersion = minimumOsVersion,
                    deviceFamily = deviceFamily,
                    projectName = xcodeProjName,
                    frameworkSearchSubdirDebug = frameworkSearchSubdirDebug,
                    frameworkSearchSubdirRelease = frameworkSearchSubdirRelease
                ),
                Charsets.UTF_8
            )
            generatedFiles.add(pbxprojFile)

            return generatedFiles
        }

        private fun resolveIconCandidateFile(projectRootDir: File?, configuredPath: String?): File? {
            val trimmed = configuredPath?.trim()?.takeIf { it.isNotEmpty() }
            if (trimmed != null) {
                val explicit = File(trimmed).let {
                    if (it.isAbsolute || projectRootDir == null) it else File(projectRootDir, trimmed)
                }
                if (explicit.exists() && explicit.isFile) return explicit
            }
            if (projectRootDir == null) return null
            val fallbackRelPaths = listOf(
                "src/commonMain/resources/icon.svg",
                "src/commonMain/resources/icon.png",
                "src/commonMain/resources/icon.ico",
                "src/commonMain/resources/favicon.ico",
                "icon.svg",
                "icon.png",
                "icon.ico"
            )
            return fallbackRelPaths.map { File(projectRootDir, it) }.firstOrNull { it.exists() && it.isFile }
        }

        fun renderInfoPlist(
            appName: String,
            appId: String,
            appVersion: String,
            orientations: List<String> = DEFAULT_ORIENTATIONS,
            deviceFamily: Int = DEVICE_FAMILY_IPHONE
        ): String {
            val orientationEntries = orientations.joinToString("\n") {
                "        <string>${escapeXml(it)}</string>"
            }
            return """
                |<?xml version="1.0" encoding="UTF-8"?>
                |<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
                |<plist version="1.0">
                |<dict>
                |    <key>CFBundleDevelopmentRegion</key>
                |    <string>en</string>
                |    <key>CFBundleDisplayName</key>
                |    <string>${escapeXml(appName)}</string>
                |    <key>CFBundleExecutable</key>
                |    <string>$(EXECUTABLE_NAME)</string>
                |    <key>CFBundleIconName</key>
                |    <string>AppIcon</string>
                |    <key>CFBundleIdentifier</key>
                |    <string>${escapeXml(appId)}</string>
                |    <key>CFBundleInfoDictionaryVersion</key>
                |    <string>6.0</string>
                |    <key>CFBundleName</key>
                |    <string>${escapeXml(appName)}</string>
                |    <key>CFBundlePackageType</key>
                |    <string>APPL</string>
                |    <key>CFBundleShortVersionString</key>
                |    <string>${escapeXml(appVersion)}</string>
                |    <key>CFBundleVersion</key>
                |    <string>${escapeXml(appVersion)}</string>
                |    <key>LSRequiresIPhoneOS</key>
                |    <true/>
                |    <key>UIDeviceFamily</key>
                |    <array>
                |        <integer>$deviceFamily</integer>
                |    </array>
                |    <key>CADisableMinimumFrameDurationOnPhone</key>
                |    <true/>
                |    <key>UIApplicationSupportsIndirectInputEvents</key>
                |    <true/>
                |    <key>UILaunchScreen</key>
                |    <dict/>
                |    <key>UIRequiredDeviceCapabilities</key>
                |    <array>
                |        <string>arm64</string>
                |    </array>
                |    <key>UISupportedInterfaceOrientations</key>
                |    <array>
                |$orientationEntries
                |    </array>
                |    <key>UISupportedInterfaceOrientations~ipad</key>
                |    <array>
                |$orientationEntries
                |    </array>
                |</dict>
                |</plist>
                |
            """.trimMargin()
        }

        fun renderIosAppSwift(): String =
            """
                |// AUTO-GENERATED BY GenericUI Gradle Plugin — DO NOT EDIT.
                |import SwiftUI
                |
                |@main
                |struct iOSApp: App {
                |    var body: some Scene {
                |        WindowGroup {
                |            ContentView()
                |                .ignoresSafeArea()
                |        }
                |    }
                |}
                |
            """.trimMargin()

        fun renderContentViewSwift(frameworkName: String): String =
            """
                |// AUTO-GENERATED BY GenericUI Gradle Plugin — DO NOT EDIT.
                |import UIKit
                |import SwiftUI
                |import $frameworkName
                |
                |struct GenericUiHostViewController: UIViewControllerRepresentable {
                |    func makeUIViewController(context: Context) -> UIViewController {
                |        MainViewController()
                |    }
                |
                |    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
                |}
                |
                |struct ContentView: View {
                |    var body: some View {
                |        GenericUiHostViewController()
                |            .ignoresSafeArea()
                |    }
                |}
                |
            """.trimMargin()

        fun renderProjectYml(
            appName: String,
            appId: String,
            appVersion: String,
            frameworkName: String,
            minimumOsVersion: String = DEFAULT_MIN_IOS_VERSION,
            deviceFamily: Int = DEVICE_FAMILY_IPHONE,
            projectName: String = if (deviceFamily == DEVICE_FAMILY_IPAD) "ipadosApp" else "iosApp",
            frameworkSearchSubdirDebug: String = "debugFramework",
            frameworkSearchSubdirRelease: String = "releaseFramework"
        ): String =
            """
                |# AUTO-GENERATED BY GenericUI Gradle Plugin — DO NOT EDIT.
                |name: $projectName
                |options:
                |  bundleIdPrefix: $appId
                |  deploymentTarget:
                |    iOS: "$minimumOsVersion"
                |targets:
                |  $projectName:
                |    type: application
                |    platform: iOS
                |    deploymentTarget: "$minimumOsVersion"
                |    sources:
                |      - iOSApp.swift
                |      - ContentView.swift
                |      - Assets.xcassets
                |    info:
                |      path: Info.plist
                |    settings:
                |      base:
                |        PRODUCT_NAME: "$appName"
                |        PRODUCT_BUNDLE_IDENTIFIER: "$appId"
                |        MARKETING_VERSION: "$appVersion"
                |        CURRENT_PROJECT_VERSION: "$appVersion"
                |        TARGETED_DEVICE_FAMILY: "$deviceFamily"
                |        ASSETCATALOG_COMPILER_APPICON_NAME: AppIcon
                |        FRAMEWORK_SEARCH_PATHS:
                |          - "$(inherited)"
                |          - "$(SRCROOT)/../bin/iosArm64/$frameworkSearchSubdirDebug"
                |          - "$(SRCROOT)/../bin/iosArm64/$frameworkSearchSubdirRelease"
                |          - "$(SRCROOT)/../bin/iosSimulatorArm64/$frameworkSearchSubdirDebug"
                |          - "$(SRCROOT)/../bin/iosSimulatorArm64/$frameworkSearchSubdirRelease"
                |        OTHER_LDFLAGS:
                |          - "$(inherited)"
                |          - "-framework"
                |          - "$frameworkName"
                |
            """.trimMargin()

        fun renderPbxproj(
            appName: String,
            appId: String,
            appVersion: String,
            frameworkName: String,
            minimumOsVersion: String = DEFAULT_MIN_IOS_VERSION,
            deviceFamily: Int = DEVICE_FAMILY_IPHONE,
            projectName: String = if (deviceFamily == DEVICE_FAMILY_IPAD) "ipadosApp" else "iosApp",
            frameworkSearchSubdirDebug: String = "debugFramework",
            frameworkSearchSubdirRelease: String = "releaseFramework"
        ): String =
            """
                |// !$*UTF8*$!
                |{
                |	archiveVersion = 1;
                |	classes = {
                |	};
                |	objectVersion = 56;
                |	objects = {
                |/* Begin PBXBuildFile section */
                |		A10000010000000000000001 /* iOSApp.swift in Sources */ = {isa = PBXBuildFile; fileRef = A20000010000000000000001 /* iOSApp.swift */; };
                |		A10000020000000000000002 /* ContentView.swift in Sources */ = {isa = PBXBuildFile; fileRef = A20000020000000000000002 /* ContentView.swift */; };
                |		A10000030000000000000003 /* Assets.xcassets in Resources */ = {isa = PBXBuildFile; fileRef = A20000050000000000000005 /* Assets.xcassets */; };
                |/* End PBXBuildFile section */
                |
                |/* Begin PBXFileReference section */
                |		A20000010000000000000001 /* iOSApp.swift */ = {isa = PBXFileReference; lastKnownFileType = sourcecode.swift; path = iOSApp.swift; sourceTree = "<group>"; };
                |		A20000020000000000000002 /* ContentView.swift */ = {isa = PBXFileReference; lastKnownFileType = sourcecode.swift; path = ContentView.swift; sourceTree = "<group>"; };
                |		A20000030000000000000003 /* Info.plist */ = {isa = PBXFileReference; lastKnownFileType = text.plist.xml; path = Info.plist; sourceTree = "<group>"; };
                |		A20000040000000000000004 /* $projectName.app */ = {isa = PBXFileReference; explicitFileType = wrapper.application; includeInIndex = 0; path = $projectName.app; sourceTree = BUILT_PRODUCTS_DIR; };
                |		A20000050000000000000005 /* Assets.xcassets */ = {isa = PBXFileReference; lastKnownFileType = folder.assetcatalog; path = Assets.xcassets; sourceTree = "<group>"; };
                |/* End PBXFileReference section */
                |
                |/* Begin PBXGroup section */
                |		A30000010000000000000001 = {
                |			isa = PBXGroup;
                |			children = (
                |				A20000010000000000000001 /* iOSApp.swift */,
                |				A20000020000000000000002 /* ContentView.swift */,
                |				A20000030000000000000003 /* Info.plist */,
                |				A20000050000000000000005 /* Assets.xcassets */,
                |				A20000040000000000000004 /* $projectName.app */,
                |			);
                |			sourceTree = "<group>";
                |		};
                |/* End PBXGroup section */
                |
                |/* Begin PBXNativeTarget section */
                |		A40000010000000000000001 /* $projectName */ = {
                |			isa = PBXNativeTarget;
                |			buildConfigurationList = A60000020000000000000002 /* Build configuration list for PBXNativeTarget "$projectName" */;
                |			buildPhases = (
                |				A50000010000000000000001 /* Sources */,
                |				A50000020000000000000002 /* Resources */,
                |			);
                |			buildRules = (
                |			);
                |			dependencies = (
                |			);
                |			name = $projectName;
                |			productName = "$appName";
                |			productReference = A20000040000000000000004 /* $projectName.app */;
                |			productType = "com.apple.product-type.application";
                |		};
                |/* End PBXNativeTarget section */
                |
                |/* Begin PBXProject section */
                |		A70000010000000000000001 /* Project object */ = {
                |			isa = PBXProject;
                |			buildConfigurationList = A60000010000000000000001 /* Build configuration list for PBXProject "$projectName" */;
                |			compatibilityVersion = "Xcode 14.0";
                |			developmentRegion = en;
                |			hasScannedForEncodings = 0;
                |			knownRegions = (
                |				en,
                |				Base,
                |			);
                |			mainGroup = A30000010000000000000001;
                |			productRefGroup = A30000010000000000000001;
                |			projectDirPath = "";
                |			projectRoot = "";
                |			targets = (
                |				A40000010000000000000001 /* $projectName */,
                |			);
                |		};
                |/* End PBXProject section */
                |
                |/* Begin PBXResourcesBuildPhase section */
                |		A50000020000000000000002 /* Resources */ = {
                |			isa = PBXResourcesBuildPhase;
                |			buildActionMask = 2147483647;
                |			files = (
                |				A10000030000000000000003 /* Assets.xcassets in Resources */,
                |			);
                |			runOnlyForDeploymentPostprocessing = 0;
                |		};
                |/* End PBXResourcesBuildPhase section */
                |
                |/* Begin PBXSourcesBuildPhase section */
                |		A50000010000000000000001 /* Sources */ = {
                |			isa = PBXSourcesBuildPhase;
                |			buildActionMask = 2147483647;
                |			files = (
                |				A10000010000000000000001 /* iOSApp.swift in Sources */,
                |				A10000020000000000000002 /* ContentView.swift in Sources */,
                |			);
                |			runOnlyForDeploymentPostprocessing = 0;
                |		};
                |/* End PBXSourcesBuildPhase section */
                |
                |/* Begin XCBuildConfiguration section */
                |		A80000010000000000000001 /* Debug */ = {
                |			isa = XCBuildConfiguration;
                |			buildSettings = {
                |				ASSETCATALOG_COMPILER_APPICON_NAME = AppIcon;
                |				CLANG_ENABLE_MODULES = YES;
                |				CURRENT_PROJECT_VERSION = "$appVersion";
                |				FRAMEWORK_SEARCH_PATHS = (
                |					"$(inherited)",
                |					"$(SRCROOT)/../bin/iosArm64/$frameworkSearchSubdirDebug",
                |					"$(SRCROOT)/../bin/iosSimulatorArm64/$frameworkSearchSubdirDebug",
                |				);
                |				INFOPLIST_FILE = Info.plist;
                |				IPHONEOS_DEPLOYMENT_TARGET = $minimumOsVersion;
                |				MARKETING_VERSION = "$appVersion";
                |				OTHER_LDFLAGS = (
                |					"$(inherited)",
                |					"-framework",
                |					"$frameworkName",
                |				);
                |				PRODUCT_BUNDLE_IDENTIFIER = "$appId";
                |				PRODUCT_NAME = "$appName";
                |				SDKROOT = iphoneos;
                |				SWIFT_VERSION = 5.0;
                |				TARGETED_DEVICE_FAMILY = "$deviceFamily";
                |			};
                |			name = Debug;
                |		};
                |		A80000020000000000000002 /* Release */ = {
                |			isa = XCBuildConfiguration;
                |			buildSettings = {
                |				ASSETCATALOG_COMPILER_APPICON_NAME = AppIcon;
                |				CLANG_ENABLE_MODULES = YES;
                |				CURRENT_PROJECT_VERSION = "$appVersion";
                |				FRAMEWORK_SEARCH_PATHS = (
                |					"$(inherited)",
                |					"$(SRCROOT)/../bin/iosArm64/$frameworkSearchSubdirRelease",
                |					"$(SRCROOT)/../bin/iosSimulatorArm64/$frameworkSearchSubdirRelease",
                |				);
                |				INFOPLIST_FILE = Info.plist;
                |				IPHONEOS_DEPLOYMENT_TARGET = $minimumOsVersion;
                |				MARKETING_VERSION = "$appVersion";
                |				OTHER_LDFLAGS = (
                |					"$(inherited)",
                |					"-framework",
                |					"$frameworkName",
                |				);
                |				PRODUCT_BUNDLE_IDENTIFIER = "$appId";
                |				PRODUCT_NAME = "$appName";
                |				SDKROOT = iphoneos;
                |				SWIFT_VERSION = 5.0;
                |				TARGETED_DEVICE_FAMILY = "$deviceFamily";
                |			};
                |			name = Release;
                |		};
                |/* End XCBuildConfiguration section */
                |
                |/* Begin XCConfigurationList section */
                |		A60000010000000000000001 /* Build configuration list for PBXProject "$projectName" */ = {
                |			isa = XCConfigurationList;
                |			buildConfigurations = (
                |				A80000010000000000000001 /* Debug */,
                |				A80000020000000000000002 /* Release */,
                |			);
                |			defaultConfigurationIsVisible = 0;
                |			defaultConfigurationName = Release;
                |		};
                |		A60000020000000000000002 /* Build configuration list for PBXNativeTarget "$projectName" */ = {
                |			isa = XCConfigurationList;
                |			buildConfigurations = (
                |				A80000010000000000000001 /* Debug */,
                |				A80000020000000000000002 /* Release */,
                |			);
                |			defaultConfigurationIsVisible = 0;
                |			defaultConfigurationName = Release;
                |		};
                |/* End XCConfigurationList section */
                |	};
                |	rootObject = A70000010000000000000001 /* Project object */;
                |}
                |
            """.trimMargin()

        private fun escapeXml(value: String): String =
            value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;")
    }
}
