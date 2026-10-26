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
 * Generates a self-contained Swift/Xcode iOS host application project (`build/genericui/iosApp/`)
 * that embeds the Kotlin/Native iOS framework (`MainViewController()`) inside a SwiftUI `@main`
 * application (`iOSApp.swift` + `ContentView.swift` + `Info.plist` + `project.yml` +
 * `iosApp.xcodeproj/project.pbxproj`).
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

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

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

        generateIosHostProject(
            outputDir = outDir,
            appName = resolvedAppName,
            appId = resolvedAppId,
            appVersion = resolvedAppVersion,
            frameworkName = resolvedFramework,
            minimumOsVersion = resolvedMinOs,
            orientations = resolvedOrientations
        )
    }

    companion object {
        const val DEFAULT_MIN_IOS_VERSION: String = "15.0"

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
         * `ContentView.swift`, `project.yml`, and `iosApp.xcodeproj/project.pbxproj`) inside [outputDir].
         */
        fun generateIosHostProject(
            outputDir: File,
            appName: String,
            appId: String,
            appVersion: String,
            frameworkName: String = "GenericUiApp",
            minimumOsVersion: String = DEFAULT_MIN_IOS_VERSION,
            orientations: List<String> = DEFAULT_ORIENTATIONS
        ): List<File> {
            outputDir.mkdirs()
            val sanitizedFramework = sanitizeFrameworkName(frameworkName)
            val generatedFiles = mutableListOf<File>()

            val infoPlistFile = File(outputDir, "Info.plist")
            infoPlistFile.writeText(
                renderInfoPlist(
                    appName = appName,
                    appId = appId,
                    appVersion = appVersion,
                    orientations = orientations
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

            val projectYmlFile = File(outputDir, "project.yml")
            projectYmlFile.writeText(
                renderProjectYml(
                    appName = appName,
                    appId = appId,
                    appVersion = appVersion,
                    frameworkName = sanitizedFramework,
                    minimumOsVersion = minimumOsVersion
                ),
                Charsets.UTF_8
            )
            generatedFiles.add(projectYmlFile)

            val xcodeProjDir = File(outputDir, "iosApp.xcodeproj").apply { mkdirs() }
            val pbxprojFile = File(xcodeProjDir, "project.pbxproj")
            pbxprojFile.writeText(
                renderPbxproj(
                    appName = appName,
                    appId = appId,
                    appVersion = appVersion,
                    frameworkName = sanitizedFramework,
                    minimumOsVersion = minimumOsVersion
                ),
                Charsets.UTF_8
            )
            generatedFiles.add(pbxprojFile)

            return generatedFiles
        }

        fun renderInfoPlist(
            appName: String,
            appId: String,
            appVersion: String,
            orientations: List<String> = DEFAULT_ORIENTATIONS
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
                |    <key>CADisableMinimumFrameDurationOnPhone</key>
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
            minimumOsVersion: String = DEFAULT_MIN_IOS_VERSION
        ): String =
            """
                |# AUTO-GENERATED BY GenericUI Gradle Plugin — DO NOT EDIT.
                |name: iosApp
                |options:
                |  bundleIdPrefix: $appId
                |  deploymentTarget:
                |    iOS: "$minimumOsVersion"
                |targets:
                |  iosApp:
                |    type: application
                |    platform: iOS
                |    deploymentTarget: "$minimumOsVersion"
                |    sources:
                |      - iOSApp.swift
                |      - ContentView.swift
                |    info:
                |      path: Info.plist
                |    settings:
                |      base:
                |        PRODUCT_NAME: "$appName"
                |        PRODUCT_BUNDLE_IDENTIFIER: "$appId"
                |        MARKETING_VERSION: "$appVersion"
                |        CURRENT_PROJECT_VERSION: "$appVersion"
                |        FRAMEWORK_SEARCH_PATHS:
                |          - "$(inherited)"
                |          - "$(SRCROOT)/../bin/iosArm64/debugFramework"
                |          - "$(SRCROOT)/../bin/iosArm64/releaseFramework"
                |          - "$(SRCROOT)/../bin/iosSimulatorArm64/debugFramework"
                |          - "$(SRCROOT)/../bin/iosSimulatorArm64/releaseFramework"
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
            minimumOsVersion: String = DEFAULT_MIN_IOS_VERSION
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
                |/* End PBXBuildFile section */
                |
                |/* Begin PBXFileReference section */
                |		A20000010000000000000001 /* iOSApp.swift */ = {isa = PBXFileReference; lastKnownFileType = sourcecode.swift; path = iOSApp.swift; sourceTree = "<group>"; };
                |		A20000020000000000000002 /* ContentView.swift */ = {isa = PBXFileReference; lastKnownFileType = sourcecode.swift; path = ContentView.swift; sourceTree = "<group>"; };
                |		A20000030000000000000003 /* Info.plist */ = {isa = PBXFileReference; lastKnownFileType = text.plist.xml; path = Info.plist; sourceTree = "<group>"; };
                |		A20000040000000000000004 /* iosApp.app */ = {isa = PBXFileReference; explicitFileType = wrapper.application; includeInIndex = 0; path = iosApp.app; sourceTree = BUILT_PRODUCTS_DIR; };
                |/* End PBXFileReference section */
                |
                |/* Begin PBXGroup section */
                |		A30000010000000000000001 = {
                |			isa = PBXGroup;
                |			children = (
                |				A20000010000000000000001 /* iOSApp.swift */,
                |				A20000020000000000000002 /* ContentView.swift */,
                |				A20000030000000000000003 /* Info.plist */,
                |				A20000040000000000000004 /* iosApp.app */,
                |			);
                |			sourceTree = "<group>";
                |		};
                |/* End PBXGroup section */
                |
                |/* Begin PBXNativeTarget section */
                |		A40000010000000000000001 /* iosApp */ = {
                |			isa = PBXNativeTarget;
                |			buildConfigurationList = A60000020000000000000002 /* Build configuration list for PBXNativeTarget "iosApp" */;
                |			buildPhases = (
                |				A50000010000000000000001 /* Sources */,
                |			);
                |			buildRules = (
                |			);
                |			dependencies = (
                |			);
                |			name = iosApp;
                |			productName = "$appName";
                |			productReference = A20000040000000000000004 /* iosApp.app */;
                |			productType = "com.apple.product-type.application";
                |		};
                |/* End PBXNativeTarget section */
                |
                |/* Begin PBXProject section */
                |		A70000010000000000000001 /* Project object */ = {
                |			isa = PBXProject;
                |			buildConfigurationList = A60000010000000000000001 /* Build configuration list for PBXProject "iosApp" */;
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
                |				A40000010000000000000001 /* iosApp */,
                |			);
                |		};
                |/* End PBXProject section */
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
                |				CLANG_ENABLE_MODULES = YES;
                |				CURRENT_PROJECT_VERSION = "$appVersion";
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
                |			};
                |			name = Debug;
                |		};
                |		A80000020000000000000002 /* Release */ = {
                |			isa = XCBuildConfiguration;
                |			buildSettings = {
                |				CLANG_ENABLE_MODULES = YES;
                |				CURRENT_PROJECT_VERSION = "$appVersion";
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
                |			};
                |			name = Release;
                |		};
                |/* End XCBuildConfiguration section */
                |
                |/* Begin XCConfigurationList section */
                |		A60000010000000000000001 /* Build configuration list for PBXProject "iosApp" */ = {
                |			isa = XCConfigurationList;
                |			buildConfigurations = (
                |				A80000010000000000000001 /* Debug */,
                |				A80000020000000000000002 /* Release */,
                |			);
                |			defaultConfigurationIsVisible = 0;
                |			defaultConfigurationName = Release;
                |		};
                |		A60000020000000000000002 /* Build configuration list for PBXNativeTarget "iosApp" */ = {
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
