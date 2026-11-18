package com.nexustools.genericui.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import java.io.File

/**
 * Gradle plugin (`id("com.nexustools.genericui")`) that configures GenericUI multiplatform targets,
 * optional LWJGL 3 OpenGL ES & Vulkan native dependencies for `:swing`, Google ANGLE (`libEGL.dll`,
 * `libGLESv2.dll`, `d3dcompiler_47.dll`) on Windows, `MoltenVK` on macOS/iOS, `shaderc` SPIR-V
 * compilation, SASS/CSS compilation, and first-party extension modules (`:extensions:aichat`,
 * `:extensions:threejs`, `:extensions:box2d`, `:extensions:box3d`).
 */
class GenericUiGradlePlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val extension = project.extensions.create("genericui", GenericUiExtension::class.java)
        extension.onConfiguredCallback = {
            GenericUiMultiplatformConfigurator.configureMultiplatform(project, extension)
            GenericUiMultiplatformConfigurator.registerBuildTargetTasks(project, extension)
        }
        project.tasks.withType(org.gradle.api.tasks.testing.AbstractTestTask::class.java).configureEach {
            val secs = extension.testing.testTimeoutSeconds.coerceAtLeast(10L)
            timeout.set(java.time.Duration.ofSeconds(secs))
        }

        val angleTask = project.tasks.register("bundleGenericUiAngle", AngleNativeBundleTask::class.java) {
            group = TASK_GROUP
            description = "Stages Google ANGLE (libEGL, libGLESv2, d3dcompiler_47) and optional SwiftShader runtime metadata for OpenGL ES targets."
            bundleAngleOnWindows.set(project.provider { extension.graphics.bundleAngleOnWindows })
            bundleAngleOnApple.set(project.provider { extension.graphics.bundleAngleOnApple })
            bundleSoftwareFallback.set(project.provider { extension.graphics.bundleSoftwareFallback })
            compileShaders.set(project.provider { extension.graphics.compileShaders })
            angleBackendRenderer.set(project.provider { extension.graphics.angleBackendRenderer })
            preferOpenGLES31.set(project.provider { extension.graphics.preferOpenGLES31 })
            targetClassifiers.set(project.provider {
                extension.backends.swingConfig.targetClassifiers.map { it.lwjglClassifier }
            })
            outputDirectory.set(project.layout.buildDirectory.dir("generated/genericui/angle"))
        }

        val vulkanTask = project.tasks.register("bundleGenericUiVulkan", VulkanNativeBundleTask::class.java) {
            group = TASK_GROUP
            description = "Stages Vulkan loader, MoltenVK ICD, and Shaderc configuration metadata for Canvas3D targets."
            bundleMoltenVkOnApple.set(project.provider { extension.graphics.bundleMoltenVkOnApple })
            bundleShaderc.set(project.provider { extension.graphics.bundleShaderc })
            enableValidationLayersInDebug.set(project.provider { extension.graphics.enableVulkanValidationLayersInDebug })
            lwjglVersion.set(project.provider { extension.backends.swingConfig.lwjglVersion })
            targetClassifiers.set(project.provider {
                extension.backends.swingConfig.targetClassifiers.map { it.lwjglClassifier }
            })
            outputDirectory.set(project.layout.buildDirectory.dir("generated/genericui/vulkan"))
        }

        val vlcTask = if (project.tasks.findByName("bundleGenericUiVlc") != null) {
            project.tasks.named("bundleGenericUiVlc", VlcNativeBundleTask::class.java)
        } else {
            project.tasks.register("bundleGenericUiVlc", VlcNativeBundleTask::class.java) {
                group = TASK_GROUP
                description = "Stages LibVLC native runtime libraries (libvlc, libvlccore, plugins/) for desktop media targets."
                bundleVlcOnDesktop.set(project.provider { GenericUiMultiplatformConfigurator.isMediaExtensionEnabled(extension) })
                outputDirectory.set(project.layout.buildDirectory.dir("generated/genericui/nativeBundle/vlc"))
            }
        }

        val webViewProcessTask = if (project.tasks.findByName("bundleGenericUiWebViewProcess") != null) {
            project.tasks.named("bundleGenericUiWebViewProcess", WebViewNativeProcessBundleTask::class.java)
        } else {
            project.tasks.register("bundleGenericUiWebViewProcess", WebViewNativeProcessBundleTask::class.java) {
                group = TASK_GROUP
                description = "Stages the desktop WebView out-of-process helper executable (genericui-webview-process)."
                webViewEnabled.set(project.provider { GenericUiMultiplatformConfigurator.isWebViewExtensionEnabled(extension) })
                genericUiRootPath.set(project.projectDir.absolutePath)
                outputDirectory.set(project.layout.buildDirectory.dir("generated/genericui/nativeBundle/webview"))
            }
        }

        val sassTask = project.tasks.register("compileGenericUiSass", GenericUiSassCompileTask::class.java) {
            group = TASK_GROUP
            description = "Compiles GenericUI .scss and .sass stylesheets into normalized .css bundles for the Web target and precompiled .kt PrecompiledStyleSheet objects."
            compileSass.set(project.provider { extension.styles.compileSass })
            generateWebCss.set(project.provider { extension.styles.generateWebCss })
            webCssBundleFileName.set(project.provider { extension.styles.webCssBundleFileName })
            precompileToKotlin.set(project.provider { extension.styles.precompileToKotlin })
            kotlinPackageName.set(project.provider {
                resolveStylesKotlinPackageName(project, extension)
            })
            minify.set(project.provider { extension.styles.minify })
            includeBootstrapVariables.set(project.provider { extension.styles.includeBootstrapVariables })
            val resolvedSrcDir = resolveStylesSourceDir(project, extension.styles.sourceDir)
            if (resolvedSrcDir != null) {
                sourceDirectory.set(resolvedSrcDir)
            }
            outputDirectory.set(project.layout.buildDirectory.dir(project.provider { extension.styles.outputDir }))
            webCssOutputDirectory.set(project.layout.buildDirectory.dir(project.provider { extension.styles.webCssOutputDir }))
            kotlinOutputDirectory.set(project.layout.buildDirectory.dir(project.provider { extension.styles.kotlinOutputDir }))
        }

        val resourcesTask = project.tasks.register(
            "compileGenericUiResources",
            GenericUiResourceCompilerTask::class.java
        ) {
            group = TASK_GROUP
            description = "Compiles Android-like resources (drawable/, values/, raw/, font/) into R.kt / RC.kt and translates native backend bundles."
            resourcesEnabled.set(project.provider { extension.resources.enabled })
            projectRootPath.set(project.projectDir.absolutePath)
            resourcePackageName.set(project.provider {
                val configured = extension.resources.packageName.trim()
                if (configured.isNotEmpty()) {
                    configured
                } else {
                    val grp = project.group.toString().trim()
                    if (grp.isNotEmpty() && grp != "unspecified") "$grp.res" else "com.nexustools.genericui.generated.res"
                }
            })
            candidateSourceDirs.set(project.provider { extension.resources.sourceDirs.toList() })
            upstreamResourceManifestPaths.set(project.provider {
                resolveUpstreamResourceManifestPaths(project)
            })
            translateForAllBackends.set(project.provider { extension.resources.translateForAllBackends })
            themeColor.set(project.provider {
                resolveAndroidVariant(project, extension).themeColor
            })
            themeMode.set(project.provider {
                resolveAndroidVariant(project, extension).themeMode.trim().ifEmpty { "auto" }
            })
            lightThemeColors.set(project.provider {
                resolveAndroidVariant(project, extension).lightThemeColors
            })
            darkThemeColors.set(project.provider {
                resolveAndroidVariant(project, extension).darkThemeColors
            })
            appName.set(project.provider {
                extension.application.desktop.appName.trim().ifEmpty {
                    extension.application.baseName.trim().ifEmpty { project.name }
                }
            })
            configuredIconPath.set(project.provider {
                extension.application.desktop.icon.trim().ifEmpty {
                    extension.resources.icon.trim().ifEmpty {
                        extension.windowConfig.icon?.trim().orEmpty().ifEmpty {
                            GenericUiVariantResolver.resolve(extension, GenericUiVariantScope.COMMON).iconPath.orEmpty()
                        }
                    }
                }
            })
            appleIconPath.set(project.provider {
                extension.application.desktop.appleIcon.trim().ifEmpty {
                    extension.resources.appleIcon.trim()
                }
            })
            macIconPath.set(project.provider {
                val resolved = GenericUiVariantResolver.resolve(extension, GenericUiVariantScope.MACOS)
                if (resolved.appleIconExplicitlyProvided) resolved.iconPath.orEmpty() else ""
            })
            iosIconPath.set(project.provider {
                val resolved = GenericUiVariantResolver.resolve(extension, GenericUiVariantScope.IOS)
                if (resolved.appleIconExplicitlyProvided) resolved.iconPath.orEmpty() else ""
            })
            ipadosIconPath.set(project.provider {
                val resolved = GenericUiVariantResolver.resolve(extension, GenericUiVariantScope.IPADOS)
                if (resolved.appleIconExplicitlyProvided) resolved.iconPath.orEmpty() else ""
            })
            macIconExplicitlyProvided.set(project.provider {
                GenericUiVariantResolver.resolve(extension, GenericUiVariantScope.MACOS).appleIconExplicitlyProvided
            })
            iosIconExplicitlyProvided.set(project.provider {
                GenericUiVariantResolver.resolve(extension, GenericUiVariantScope.IOS).appleIconExplicitlyProvided
            })
            ipadosIconExplicitlyProvided.set(project.provider {
                GenericUiVariantResolver.resolve(extension, GenericUiVariantScope.IPADOS).appleIconExplicitlyProvided
            })
            androidLauncherIconConfig.set(project.provider {
                extension.android.launcherIconConfig.toSerializedString()
            })
            androidSvgConverterVersion.set(project.provider {
                extension.android.svgConverterVersion
            })
            failOnSvgConversionWarnings.set(project.provider {
                extension.android.failOnSvgConversionWarnings
            })
            kotlinOutputDirectory.set(project.layout.buildDirectory.dir("generated/genericui/resources/kotlin/commonMain"))
            backendOutputDirectory.set(project.layout.buildDirectory.dir("generated/genericui/resources/backends"))
        }

        val compileWindowsResourcesTask = project.tasks.register(
            "compileGenericUiWindowsResources",
            GenericUiCompileWindowsResourcesTask::class.java
        ) {
            group = TASK_GROUP
            description = "Compiles generated Win32 genericui_resources.rc into genericui_resources.res and genericui_resources.o."
            dependsOn(resourcesTask)
            windowsResourceDirectory.set(project.layout.buildDirectory.dir("generated/genericui/resources/backends/windows"))
            rcScriptFile.set(project.layout.buildDirectory.file("generated/genericui/resources/backends/windows/genericui_resources.rc"))
            outputDirectory.set(project.layout.buildDirectory.dir("generated/genericui/resources/backends/windows/compiled"))
            outputResFile.set(project.layout.buildDirectory.file("generated/genericui/resources/backends/windows/compiled/genericui_resources.res"))
            outputObjectFile.set(project.layout.buildDirectory.file("generated/genericui/resources/backends/windows/compiled/genericui_resources.o"))
        }

        val validateAppTask = project.tasks.register(
            "genericuiValidateApp",
            GenericUiValidateAppTask::class.java
        ) {
            group = TASK_GROUP
            description = "Validates @MainAppFragment, @AppFragment, and @AppApplication declarations for all enabled GenericUI backend targets."
            projectRootPath.set(project.projectDir.absolutePath)
            extraSourceDirectories.set(project.provider {
                extension.sourceSets.allSpecs().flatMap { spec ->
                    spec.kotlinSrcDirs.mapNotNull { dir -> (dir as? File)?.absolutePath ?: (dir as? String) }
                }
            })
            enabledBackendIds.set(project.provider {
                GenericUiGenerateLaunchersTask.enabledAllBackendScopes(extension.backends).map { it.id }
            })
            generatedLauncherPackage.set(project.provider {
                val discovered = GenericUiFragmentScanner.scanProject(project.projectDir)
                GenericUiGenerateLaunchersTask.resolveGeneratedLauncherPackage(
                    extension = extension,
                    projectGroup = project.group.toString(),
                    discoveredFragments = discovered
                )
            })
            requireMainAppFragment.set(project.provider { extension.application.createExecutableBinaries })
            validationReportFile.set(project.layout.buildDirectory.file("generated/genericui/validation/app-validation.txt"))
        }

        val generateLaunchersTask = project.tasks.register(
            "generateGenericUiLaunchers",
            GenericUiGenerateLaunchersTask::class.java
        ) {
            group = TASK_GROUP
            description = "Generates per-backend GenericUiAppDescriptor and launcher main() entrypoints for @MainAppFragment."
            projectRootPath.set(project.projectDir.absolutePath)
            extraSourceDirectories.set(project.provider {
                extension.sourceSets.allSpecs().flatMap { spec ->
                    spec.kotlinSrcDirs.mapNotNull { dir -> (dir as? File)?.absolutePath ?: (dir as? String) }
                }
            })
            enabledBackendIds.set(project.provider {
                GenericUiGenerateLaunchersTask.enabledAllBackendScopes(extension.backends).map { it.id }
            })
            generatedPackageName.set(project.provider {
                val discovered = GenericUiFragmentScanner.scanProject(project.projectDir)
                GenericUiGenerateLaunchersTask.resolveGeneratedLauncherPackage(
                    extension = extension,
                    projectGroup = project.group.toString(),
                    discoveredFragments = discovered
                )
            })
            resourcesEnabled.set(project.provider { extension.resources.enabled })
            resourcePackageName.set(project.provider {
                val configured = extension.resources.packageName.trim()
                if (configured.isNotEmpty()) {
                    configured
                } else {
                    val grp = project.group.toString().trim()
                    if (grp.isNotEmpty() && grp != "unspecified") "$grp.res" else "com.nexustools.genericui.generated.res"
                }
            })
            backendAppIds.set(project.provider {
                resolveBackendVariantMap(project, extension) { it.appId }
            })
            backendAppNames.set(project.provider {
                resolveBackendVariantMap(project, extension) { it.appName }
            })
            backendAppVersions.set(project.provider {
                resolveBackendVariantMap(project, extension) { it.appVersion }
            })
            backendWindowTitles.set(project.provider {
                resolveBackendVariantMap(project, extension) { it.windowTitle }
            })
            backendWindowWidths.set(project.provider {
                resolveBackendVariantMap(project, extension) { it.windowWidth }
            })
            backendWindowHeights.set(project.provider {
                resolveBackendVariantMap(project, extension) { it.windowHeight }
            })
            backendMinWindowWidths.set(project.provider {
                resolveBackendVariantMap(project, extension) { it.minWindowWidth }
            })
            backendMinWindowHeights.set(project.provider {
                resolveBackendVariantMap(project, extension) { it.minWindowHeight }
            })
            backendWindowResizables.set(project.provider {
                resolveBackendVariantMap(project, extension) { it.windowResizable }
            })
            backendFullscreens.set(project.provider {
                resolveBackendVariantMap(project, extension) { it.fullscreen }
            })
            backendWindowAlwaysOnTops.set(project.provider {
                resolveBackendVariantMap(project, extension) { it.windowAlwaysOnTop }
            })
            backendWindowAspectRatios.set(project.provider {
                val aspectRatios = linkedMapOf<String, Double>()
                for (backendScope in GenericUiGenerateLaunchersTask.enabledLauncherBackendScopes(extension.backends)) {
                    val ratio = resolveVariantWithProjectFallbacks(project, extension, backendScope).windowAspectRatio
                    if (ratio != null && ratio.isFinite() && ratio > 0.0) {
                        aspectRatios[backendScope.id] = ratio
                    }
                }
                aspectRatios
            })
            backendAppleIconExplicitlyProvided.set(project.provider {
                resolveBackendVariantMap(project, extension) { it.appleIconExplicitlyProvided }
            })
            backendWindowSizeExplicitlySet.set(project.provider {
                resolveBackendVariantMap(project, extension) { it.windowSizeExplicitlySet }
            })
            backendFullscreenExplicitlySet.set(project.provider {
                resolveBackendVariantMap(project, extension) { it.fullscreenExplicitlySet }
            })
            backendThemeModes.set(project.provider {
                resolveBackendVariantMap(project, extension) { it.themeMode }
            })
            backendThemeColors.set(project.provider {
                resolveBackendVariantMap(project, extension) { it.themeColor }
            })
            backendLightThemeColors.set(project.provider {
                resolveBackendVariantMap(project, extension) { it.lightThemeColors }
            })
            backendDarkThemeColors.set(project.provider {
                resolveBackendVariantMap(project, extension) { it.darkThemeColors }
            })
            backendHasCustomThemeOverrides.set(project.provider {
                resolveBackendVariantMap(project, extension) { it.hasCustomThemeOverrides }
            })
            backendIconPaths.set(project.provider {
                resolveBackendVariantMap(project, extension) { it.iconPath ?: "" }
            })
            backendGlobalStyleSheets.set(project.provider {
                resolveBackendVariantMap(project, extension) { it.globalStyleSheet ?: "" }
            })
            backendProviderInstallerCalls.set(project.provider {
                resolveBackendVariantMap(project, extension) { resolved ->
                    extension.extensions.providerInstallerCallsForBackend(resolved.targetScope, resolved.extensionOverrides)
                }
            })
            precompiledStyleSheetObjectFqns.set(project.provider {
                if (!extension.styles.compileSass || !extension.styles.precompileToKotlin) {
                    emptyList()
                } else {
                    val stylesDir = resolveStylesSourceDir(project, extension.styles.sourceDir)
                    val pkg = resolveStylesKotlinPackageName(project, extension)
                    val fqns = LinkedHashSet<String>()
                    if (stylesDir != null && stylesDir.isDirectory) {
                        stylesDir.walkTopDown()
                            .filter {
                                it.isFile &&
                                    !it.name.startsWith("_") &&
                                    it.extension.lowercase() in setOf("scss", "sass", "css")
                            }
                            .sortedBy { it.relativeTo(stylesDir).invariantSeparatorsPath }
                            .forEach { file ->
                                val objName = GenericUiSassCompileTask.toPascalIdentifier(file.nameWithoutExtension) +
                                    "PrecompiledStyleSheet"
                                fqns.add(if (pkg.isNotEmpty()) "$pkg.$objName" else objName)
                            }
                    }
                    fqns.toList()
                }
            })
            generateVisualWindowsTestHelper.set(project.provider { extension.testing.visualOnWindows })
            testingEnabled.set(project.provider { extension.testing.enabled })
            outputDirectory.set(project.layout.buildDirectory.dir("generated/genericui/launchers"))
        }

        val generateAndroidActivitiesTask = project.tasks.register(
            "generateGenericUiAndroidActivities",
            GenericUiGenerateAndroidActivitiesTask::class.java
        ) {
            group = TASK_GROUP
            description = "Generates one concrete AppCompatActivity per Android @MainAppFragment/@AppFragment and GeneratedAndroidApplication."
            androidEnabled.set(project.provider { extension.backends.androidEnabled })
            projectRootPath.set(project.projectDir.absolutePath)
            sourceDirectories.set(project.provider {
                extension.sourceSets.allSpecs().flatMap { spec ->
                    spec.kotlinSrcDirs.mapNotNull { dir -> (dir as? File)?.absolutePath ?: (dir as? String) }
                }
            })
            appId.set(project.provider { resolveAndroidVariant(project, extension).appId })
            appName.set(project.provider { resolveAndroidVariant(project, extension).appName })
            appVersion.set(project.provider { resolveAndroidVariant(project, extension).appVersion })
            packageName.set(project.provider { resolveAndroidVariant(project, extension).packageName })
            windowTitle.set(project.provider { resolveAndroidVariant(project, extension).windowTitle })
            windowWidth.set(project.provider { resolveAndroidVariant(project, extension).windowWidth })
            windowHeight.set(project.provider { resolveAndroidVariant(project, extension).windowHeight })
            minWindowWidth.set(project.provider { resolveAndroidVariant(project, extension).minWindowWidth })
            minWindowHeight.set(project.provider { resolveAndroidVariant(project, extension).minWindowHeight })
            windowResizable.set(project.provider { resolveAndroidVariant(project, extension).windowResizable })
            fullscreen.set(project.provider { resolveAndroidVariant(project, extension).fullscreen })
            windowAlwaysOnTop.set(project.provider { resolveAndroidVariant(project, extension).windowAlwaysOnTop })
            windowAspectRatio.set(project.provider { resolveAndroidVariant(project, extension).windowAspectRatio })
            appleIconExplicitlyProvided.set(project.provider { resolveAndroidVariant(project, extension).appleIconExplicitlyProvided })
            themeMode.set(project.provider { resolveAndroidVariant(project, extension).themeMode })
            themeColor.set(project.provider { resolveAndroidVariant(project, extension).themeColor })
            lightThemeColors.set(project.provider { resolveAndroidVariant(project, extension).lightThemeColors })
            darkThemeColors.set(project.provider { resolveAndroidVariant(project, extension).darkThemeColors })
            hasCustomThemeOverrides.set(project.provider { resolveAndroidVariant(project, extension).hasCustomThemeOverrides })
            iconPath.set(project.provider { resolveAndroidVariant(project, extension).iconPath ?: "" })
            globalStyleSheet.set(project.provider { resolveAndroidVariant(project, extension).globalStyleSheet ?: "" })
            precompiledStyleSheetObjectFqn.set(project.provider {
                val styleSheet = resolveAndroidVariant(project, extension).globalStyleSheet?.trim().orEmpty()
                if (styleSheet.isEmpty() || !extension.styles.precompileToKotlin) {
                    ""
                } else {
                    val fileName = styleSheet.substringAfterLast('/').substringAfterLast('\\')
                    val stem = fileName.substringBeforeLast('.')
                    val objName = GenericUiSassCompileTask.toPascalIdentifier(stem) + "PrecompiledStyleSheet"
                    val pkg = resolveStylesKotlinPackageName(project, extension)
                    if (pkg.isNotEmpty()) "$pkg.$objName" else objName
                }
            })
            providerInstallers.set(project.provider {
                val resolved = resolveAndroidVariant(project, extension)
                extension.extensions.providerInstallerCallsForBackend(GenericUiVariantScope.ANDROID, resolved.extensionOverrides)
            })
            resourceRegistryInstallerExpression.set(project.provider {
                if (!extension.resources.enabled) {
                    ""
                } else {
                    val configured = extension.resources.packageName.trim()
                    val resPkg = if (configured.isNotEmpty()) {
                        configured
                    } else {
                        val grp = project.group.toString().trim()
                        if (grp.isNotEmpty() && grp != "unspecified") "$grp.res" else "com.nexustools.genericui.generated.res"
                    }
                    "$resPkg.GeneratedResourceRegistry.ensureInitialized()"
                }
            })
            outputDirectory.set(project.layout.buildDirectory.dir("generated/genericui/android/kotlin"))
        }

        val generateAndroidManifestTask = project.tasks.register(
            "generateGenericUiAndroidManifest",
            GenericUiGenerateAndroidManifestTask::class.java
        ) {
            group = TASK_GROUP
            description = "Generates AndroidManifest.xml with activities for all discovered Android @MainAppFragment/@AppFragment classes and services."
            androidEnabled.set(project.provider { extension.backends.androidEnabled })
            projectRootPath.set(project.projectDir.absolutePath)
            sourceDirectories.set(project.provider {
                extension.sourceSets.allSpecs().flatMap { spec ->
                    spec.kotlinSrcDirs.mapNotNull { dir -> (dir as? File)?.absolutePath ?: (dir as? String) }
                }
            })
            appId.set(project.provider { resolveAndroidVariant(project, extension).appId })
            appName.set(project.provider { resolveAndroidVariant(project, extension).appName })
            appVersion.set(project.provider { resolveAndroidVariant(project, extension).appVersion })
            packageName.set(project.provider { resolveAndroidVariant(project, extension).packageName })
            manifestModelJson.set(project.provider {
                GenericUiAndroidManifestModelSerializer.toJson(
                    manifestConfig = extension.android.manifestConfig,
                    services = extension.android.servicesConfig.services,
                    variantOverrides = extension.android.variants,
                    inheritedWebShortcuts = extension.application.desktop.webManifest.shortcuts
                )
            })
            manifestMergerVersion.set(project.provider { extension.android.manifestMergerVersion })
            outputDirectory.set(project.layout.buildDirectory.dir("generated/genericui/android-manifest"))
            outputManifestFile.set(project.layout.buildDirectory.file("generated/genericui/android-manifest/AndroidManifest.xml"))
            outputResDirectory.set(project.layout.buildDirectory.dir("generated/genericui/android-manifest/res"))
        }

        val generateIosAppTask = project.tasks.register(
            "generateGenericUiIosApp",
            GenericUiGenerateIosAppTask::class.java
        ) {
            group = TASK_GROUP
            description = "Generates the minimal Swift/Xcode iOS host application project (build/genericui/iosApp/)."
            iosEnabled.set(project.provider { extension.backends.iosEnabled })
            projectRootPath.set(project.projectDir.absolutePath)
            baseName.set(project.provider {
                extension.application.baseName.trim().ifEmpty { project.name }
            })
            appName.set(project.provider { resolveIosVariant(project, extension).appName })
            appId.set(project.provider { resolveIosVariant(project, extension).appId })
            appVersion.set(project.provider { resolveIosVariant(project, extension).appVersion })
            frameworkName.set(project.provider {
                GenericUiGenerateIosAppTask.sanitizeFrameworkName(
                    extension.application.baseName.trim().ifEmpty { project.name }
                )
            })
            deviceFamily.set(GenericUiGenerateIosAppTask.DEVICE_FAMILY_IPHONE)
            frameworkSearchSubdirDebug.set("debugFramework")
            frameworkSearchSubdirRelease.set("releaseFramework")
            iconPath.set(project.provider { resolveIosVariant(project, extension).iconPath ?: "" })
            fallbackIconPath.set(project.provider {
                extension.application.desktop.icon.trim().ifEmpty { extension.resources.icon.trim() }
            })
            appleIconExplicitlyProvided.set(project.provider {
                resolveIosVariant(project, extension).appleIconExplicitlyProvided
            })
            outputDirectory.set(project.layout.buildDirectory.dir("genericui/iosApp"))
        }

        val generateIpadosAppTask = project.tasks.register(
            "generateGenericUiIpadosApp",
            GenericUiGenerateIosAppTask::class.java
        ) {
            group = TASK_GROUP
            description = "Generates the minimal Swift/Xcode iPadOS host application project (build/genericui/ipadosApp/)."
            iosEnabled.set(project.provider { extension.backends.ipadosEnabled })
            projectRootPath.set(project.projectDir.absolutePath)
            baseName.set(project.provider {
                extension.application.baseName.trim().ifEmpty { project.name }
            })
            appName.set(project.provider { resolveIpadosVariant(project, extension).appName })
            appId.set(project.provider { resolveIpadosVariant(project, extension).appId })
            appVersion.set(project.provider { resolveIpadosVariant(project, extension).appVersion })
            frameworkName.set(project.provider {
                GenericUiGenerateIosAppTask.sanitizeFrameworkName(
                    extension.application.baseName.trim().ifEmpty { project.name }
                )
            })
            deviceFamily.set(GenericUiGenerateIosAppTask.DEVICE_FAMILY_IPAD)
            frameworkSearchSubdirDebug.set(project.provider {
                if (extension.backends.iosEnabled) "ipadosDebugFramework" else "debugFramework"
            })
            frameworkSearchSubdirRelease.set(project.provider {
                if (extension.backends.iosEnabled) "ipadosReleaseFramework" else "releaseFramework"
            })
            iconPath.set(project.provider { resolveIpadosVariant(project, extension).iconPath ?: "" })
            fallbackIconPath.set(project.provider {
                extension.application.desktop.icon.trim().ifEmpty { extension.resources.icon.trim() }
            })
            appleIconExplicitlyProvided.set(project.provider {
                resolveIpadosVariant(project, extension).appleIconExplicitlyProvided
            })
            outputDirectory.set(project.layout.buildDirectory.dir("genericui/ipadosApp"))
        }

        val desktopResourcesTask = project.tasks.register(
            "prepareGenericUiDesktopResources",
            GenericUiDesktopResourceTask::class.java
        ) {
            group = TASK_GROUP
            description = "Generates and compiles Windows (.rc/.res), macOS (Info.plist), and Linux (.desktop) application icons & metadata."
            projectRootPath.set(project.projectDir.absolutePath)
            baseName.set(project.provider {
                extension.application.baseName.trim().ifEmpty { project.name }
            })
            appName.set(project.provider {
                extension.application.desktop.appName.trim().ifEmpty {
                    extension.application.baseName.trim().ifEmpty { project.name }
                }
            })
            appId.set(project.provider {
                extension.application.desktop.appId.trim().ifEmpty {
                    val grp = project.group.toString().trim()
                    if (grp.isNotEmpty()) grp else "com.nexustools.${project.name.lowercase()}"
                }
            })
            appVersion.set(project.provider {
                extension.application.desktop.appVersion.trim().ifEmpty {
                    val v = project.version.toString().trim()
                    if (v.isNotEmpty() && v != "unspecified") v else "1.0.0"
                }
            })
            vendor.set(project.provider { extension.application.desktop.vendor })
            appDescription.set(project.provider { extension.application.desktop.description })
            copyright.set(project.provider { extension.application.desktop.copyright })
            license.set(project.provider { extension.application.desktop.license })
            homepageUrl.set(project.provider { extension.application.desktop.homepageUrl })
            issuesUrl.set(project.provider { extension.application.desktop.issuesUrl })
            themeColor.set(project.provider { extension.application.desktop.themeColor })
            windowsIconPath.set(project.provider {
                extension.application.desktop.windowsIcon.trim().ifEmpty {
                    GenericUiVariantResolver.resolve(extension, GenericUiVariantScope.WINDOWS).iconPath.orEmpty()
                }
            })
            appleIconPath.set(project.provider {
                extension.application.desktop.appleIcon.trim().ifEmpty {
                    extension.resources.appleIcon.trim()
                }
            })
            macIconPath.set(project.provider {
                val resolvedMac = GenericUiVariantResolver.resolve(extension, GenericUiVariantScope.MACOS)
                if (resolvedMac.appleIconExplicitlyProvided) resolvedMac.iconPath.orEmpty() else ""
            })
            macAppleIconExplicitlyProvided.set(project.provider {
                GenericUiVariantResolver.resolve(extension, GenericUiVariantScope.MACOS).appleIconExplicitlyProvided
            })
            linuxIconPath.set(project.provider {
                extension.application.desktop.linuxIcon.trim().ifEmpty {
                    GenericUiVariantResolver.resolve(extension, GenericUiVariantScope.GTK).iconPath.orEmpty()
                }
            })
            fallbackIconPath.set(project.provider {
                extension.application.desktop.icon.trim().ifEmpty {
                    extension.resources.icon.trim().ifEmpty {
                        extension.windowConfig.icon?.trim().orEmpty().ifEmpty {
                            GenericUiVariantResolver.resolve(extension, GenericUiVariantScope.COMMON).iconPath.orEmpty()
                        }
                    }
                }
            })
            embedWindowsResources.set(project.provider { extension.application.desktop.embedWindowsResources })
            macMinimumSystemVersion.set(project.provider { extension.application.desktop.macMinimumSystemVersion })
            macCategory.set(project.provider { extension.application.desktop.macCategory })
            macHighResolutionCapable.set(project.provider { extension.application.desktop.macHighResolutionCapable })
            linuxCategories.set(project.provider { extension.application.desktop.linuxCategories })
            linuxStartupWmClass.set(project.provider { extension.application.desktop.linuxStartupWmClass })
            linuxTerminal.set(project.provider { extension.application.desktop.linuxTerminal })
            customResourceEntries.set(project.provider { extension.application.desktop.customResourceEntries })
            bundledResourcePaths.set(project.provider { extension.application.desktop.bundledResourcePaths })
            outputDirectory.set(project.layout.buildDirectory.dir("generated/genericui/desktop-resources"))
        }

        fun configureWebDistTask(
            task: GenericUiWebDistributionTask,
            variant: String,
            outputRelPath: String
        ) {
            configureWebDistributionTaskInstance(project, extension, task, variant, outputRelPath)
        }

        val webDistTask = project.tasks.register(
            "packageGenericUiWebDistribution",
            GenericUiWebDistributionTask::class.java
        ) {
            configureWebDistTask(this, variant = "development", outputRelPath = "dist/web/development")
        }

        val webDevDistTask = project.tasks.register(
            "packageGenericUiWebDevelopmentDistribution",
            GenericUiWebDistributionTask::class.java
        ) {
            configureWebDistTask(this, variant = "development", outputRelPath = "dist/web/development")
        }

        val webProdDistTask = project.tasks.register(
            "packageGenericUiWebProductionDistribution",
            GenericUiWebDistributionTask::class.java
        ) {
            configureWebDistTask(this, variant = "production", outputRelPath = "dist/web/production")
        }

        fun configureAndroidApkTask(
            task: GenericUiPackageAndroidApkTask,
            variant: String,
            outputRelPath: String,
            secondaryOutputRelPath: String? = null,
            abi: String = ""
        ) {
            task.group = TASK_GROUP
            val abiLabel = if (abi.isBlank()) "DEX universal" else abi
            task.description = "Packages and signs the GenericUI Android ($abiLabel $variant) APK using aapt2, d8, zipalign, and apksigner into build/$outputRelPath."
            task.androidEnabled.set(project.provider { extension.backends.androidEnabled })
            task.projectRootPath.set(project.projectDir.absolutePath)
            task.baseName.set(project.provider {
                extension.application.baseName.trim().ifEmpty { project.name }
            })
            task.appId.set(project.provider {
                val baseId = resolveAndroidVariant(project, extension).appId
                val suffix = extension.android.variants[variant]?.applicationIdSuffix.orEmpty()
                baseId + suffix
            })
            task.appVersion.set(project.provider {
                val variantCfg = extension.android.variants[variant]
                val explicitName = variantCfg?.manifestConfig?.versionName?.trim()?.takeIf { it.isNotEmpty() }
                    ?: extension.android.manifestConfig.versionName?.trim()?.takeIf { it.isNotEmpty() }
                    ?: resolveAndroidVariant(project, extension).appVersion
                val suffix = variantCfg?.versionNameSuffix.orEmpty()
                explicitName + suffix
            })
            task.versionCode.set(project.provider {
                val variantCfg = extension.android.variants[variant]
                val resolvedVerName = variantCfg?.manifestConfig?.versionName?.trim()?.takeIf { it.isNotEmpty() }
                    ?: extension.android.manifestConfig.versionName?.trim()?.takeIf { it.isNotEmpty() }
                    ?: resolveAndroidVariant(project, extension).appVersion
                variantCfg?.manifestConfig?.versionCode
                    ?: extension.android.manifestConfig.versionCode
                    ?: GenericUiAndroidManifestXmlGenerator.deriveVersionCode(resolvedVerName)
            })
            task.minSdk.set(project.provider {
                val variantMin = extension.android.variants[variant]?.manifestConfig?.takeIf {
                    "minSdk" in it.explicitFields
                }?.minSdk
                variantMin ?: extension.android.manifestConfig.minSdk.takeIf { it > 0 } ?: extension.application.androidMinSdk
            })
            task.compileSdk.set(project.provider {
                val variantCompile = extension.android.variants[variant]?.manifestConfig?.takeIf {
                    "compileSdk" in it.explicitFields
                }?.compileSdk
                variantCompile ?: extension.android.manifestConfig.compileSdk.takeIf { it > 0 } ?: extension.application.androidCompileSdk
            })
            task.targetSdk.set(project.provider {
                val variantCfg = extension.android.variants[variant]?.manifestConfig
                val variantTarget = variantCfg?.takeIf { "targetSdk" in it.explicitFields }?.targetSdk
                variantTarget
                    ?: extension.android.manifestConfig.targetSdk.takeIf { it > 0 }
                    ?: extension.android.manifestConfig.compileSdk.takeIf { it > 0 }
                    ?: extension.application.androidCompileSdk
            })
            task.buildVariant.set(variant)
            task.targetAbi.set(abi)
            task.enabledGenericUiModulePaths.set(project.provider {
                resolveEnabledAndroidGenericUiModulePaths(project, extension)
            })
            task.includedBuildRootPaths.set(project.provider {
                val roots = LinkedHashSet<String>()
                runCatching {
                    project.gradle.includedBuilds.forEach { ib ->
                        roots.add(ib.projectDir.absolutePath)
                    }
                }
                listOfNotNull(
                    File(project.rootProject.projectDir, "lib/GenericUI"),
                    File(project.projectDir, "lib/GenericUI"),
                    project.rootProject.projectDir.parentFile?.let { File(it, "GenericUI") }
                ).forEach { candidate ->
                    if (candidate.isDirectory) {
                        roots.add(candidate.absolutePath)
                    }
                }
                if (project.rootProject.name.equals("GenericUI", ignoreCase = true) ||
                    File(project.rootProject.projectDir, "mobile/build.gradle.kts").isFile
                ) {
                    roots.add(project.rootProject.projectDir.absolutePath)
                }
                roots.toList()
            })
            task.manifestFile.set(
                project.layout.file(
                    generateAndroidManifestTask.map { genTask ->
                        val variantFile = genTask.outputDirectory.file("variants/$variant/AndroidManifest.xml").get().asFile
                        if (variantFile.isFile) variantFile else genTask.outputManifestFile.get().asFile
                    }
                )
            )
            task.generatedManifestResDir.set(
                project.layout.dir(
                    generateAndroidManifestTask.map { genTask ->
                        val variantRes = genTask.outputDirectory.dir("variants/$variant/res").get().asFile
                        if (variantRes.isDirectory) variantRes else genTask.outputResDirectory.get().asFile
                    }
                )
            )
            task.generatedAndroidResDir.set(project.layout.buildDirectory.dir("generated/genericui/resources/backends/android/res"))
            task.outputDirectory.set(project.layout.buildDirectory.dir(outputRelPath))
            if (secondaryOutputRelPath != null) {
                task.secondaryOutputDirectory.set(project.layout.buildDirectory.dir(secondaryOutputRelPath))
            }
            task.outputs.upToDateWhen { false }
        }

        val androidDevApkTask = project.tasks.register(
            "packageGenericUiAndroidDevelopmentApk",
            GenericUiPackageAndroidApkTask::class.java
        ) {
            configureAndroidApkTask(
                task = this,
                variant = "development",
                outputRelPath = "dist/android/dex/development",
                secondaryOutputRelPath = "dist/android/development"
            )
        }

        val androidProdApkTask = project.tasks.register(
            "packageGenericUiAndroidProductionApk",
            GenericUiPackageAndroidApkTask::class.java
        ) {
            configureAndroidApkTask(
                task = this,
                variant = "production",
                outputRelPath = "dist/android/dex/production",
                secondaryOutputRelPath = "dist/android/production"
            )
        }

        val androidX32DevApkTask = project.tasks.register(
            "packageGenericUiAndroidX32DevelopmentApk",
            GenericUiPackageAndroidApkTask::class.java
        ) {
            configureAndroidApkTask(this, variant = "development", outputRelPath = "dist/android/x32/development", abi = "x86")
        }

        val androidX32ProdApkTask = project.tasks.register(
            "packageGenericUiAndroidX32ProductionApk",
            GenericUiPackageAndroidApkTask::class.java
        ) {
            configureAndroidApkTask(this, variant = "production", outputRelPath = "dist/android/x32/production", abi = "x86")
        }

        val androidX64DevApkTask = project.tasks.register(
            "packageGenericUiAndroidX64DevelopmentApk",
            GenericUiPackageAndroidApkTask::class.java
        ) {
            configureAndroidApkTask(this, variant = "development", outputRelPath = "dist/android/x64/development", abi = "x86_64")
        }

        val androidX64ProdApkTask = project.tasks.register(
            "packageGenericUiAndroidX64ProductionApk",
            GenericUiPackageAndroidApkTask::class.java
        ) {
            configureAndroidApkTask(this, variant = "production", outputRelPath = "dist/android/x64/production", abi = "x86_64")
        }

        val androidArm32DevApkTask = project.tasks.register(
            "packageGenericUiAndroidArm32DevelopmentApk",
            GenericUiPackageAndroidApkTask::class.java
        ) {
            configureAndroidApkTask(this, variant = "development", outputRelPath = "dist/android/arm32/development", abi = "armeabi-v7a")
        }

        val androidArm32ProdApkTask = project.tasks.register(
            "packageGenericUiAndroidArm32ProductionApk",
            GenericUiPackageAndroidApkTask::class.java
        ) {
            configureAndroidApkTask(this, variant = "production", outputRelPath = "dist/android/arm32/production", abi = "armeabi-v7a")
        }

        val androidArm64DevApkTask = project.tasks.register(
            "packageGenericUiAndroidArm64DevelopmentApk",
            GenericUiPackageAndroidApkTask::class.java
        ) {
            configureAndroidApkTask(this, variant = "development", outputRelPath = "dist/android/arm64/development", abi = "arm64-v8a")
        }

        val androidArm64ProdApkTask = project.tasks.register(
            "packageGenericUiAndroidArm64ProductionApk",
            GenericUiPackageAndroidApkTask::class.java
        ) {
            configureAndroidApkTask(this, variant = "production", outputRelPath = "dist/android/arm64/production", abi = "arm64-v8a")
        }

        val androidApkTaskProviders = listOf(
            androidDevApkTask,
            androidProdApkTask,
            androidX32DevApkTask,
            androidX32ProdApkTask,
            androidX64DevApkTask,
            androidX64ProdApkTask,
            androidArm32DevApkTask,
            androidArm32ProdApkTask,
            androidArm64DevApkTask,
            androidArm64ProdApkTask
        )

        fun configureSwingJarTask(
            task: GenericUiPackageSwingJarTask,
            variant: String,
            outputRelPath: String
        ) {
            task.group = TASK_GROUP
            task.description = "Packages all classes, resources, GenericUI modules, and JVM runtime libraries into a single executable Swing ($variant) fat JAR in build/$outputRelPath."
            task.swingEnabled.set(project.provider { extension.backends.swingConfig.enabled })
            task.projectRootPath.set(project.projectDir.absolutePath)
            task.baseName.set(project.provider {
                extension.application.baseName.trim().ifEmpty { project.name }
            })
            task.appName.set(project.provider {
                resolveVariantWithProjectFallbacks(project, extension, GenericUiVariantScope.SWING).appName
            })
            task.appVersion.set(project.provider {
                resolveVariantWithProjectFallbacks(project, extension, GenericUiVariantScope.SWING).appVersion
            })
            task.mainClass.set(project.provider {
                extension.application.mainClass.trim()
            })
            task.generatedLauncherPackage.set(project.provider {
                val discovered = GenericUiFragmentScanner.scanProject(project.projectDir)
                GenericUiGenerateLaunchersTask.resolveGeneratedLauncherPackage(
                    extension = extension,
                    projectGroup = project.group.toString(),
                    discoveredFragments = discovered
                )
            })
            task.buildVariant.set(variant)
            task.enabledGenericUiModulePaths.set(project.provider {
                resolveEnabledSwingGenericUiModulePaths(project, extension)
            })
            task.includedBuildRootPaths.set(project.provider {
                val roots = LinkedHashSet<String>()
                runCatching {
                    project.gradle.includedBuilds.forEach { ib ->
                        roots.add(ib.projectDir.absolutePath)
                    }
                }
                listOfNotNull(
                    File(project.rootProject.projectDir, "lib/GenericUI"),
                    File(project.projectDir, "lib/GenericUI"),
                    project.rootProject.projectDir.parentFile?.let { File(it, "GenericUI") },
                    project.projectDir.parentFile?.parentFile?.parentFile?.takeIf { File(it, "desktop/build.gradle.kts").isFile }
                ).forEach { candidate ->
                    if (candidate.isDirectory) {
                        roots.add(candidate.absolutePath)
                    }
                }
                if (project.rootProject.name.equals("GenericUI", ignoreCase = true) ||
                    File(project.rootProject.projectDir, "desktop/build.gradle.kts").isFile
                ) {
                    roots.add(project.rootProject.projectDir.absolutePath)
                }
                roots.toList()
            })
            task.outputDirectory.set(project.layout.buildDirectory.dir(outputRelPath))
            task.outputs.upToDateWhen { false }
        }

        val swingDevJarTask = project.tasks.register(
            "packageGenericUiSwingDevelopmentJar",
            GenericUiPackageSwingJarTask::class.java
        ) {
            configureSwingJarTask(this, variant = "development", outputRelPath = "dist/swing/development")
        }

        val swingProdJarTask = project.tasks.register(
            "packageGenericUiSwingProductionJar",
            GenericUiPackageSwingJarTask::class.java
        ) {
            configureSwingJarTask(this, variant = "production", outputRelPath = "dist/swing/production")
        }

        val swingJarTaskProviders = listOf(
            swingDevJarTask,
            swingProdJarTask
        )

        val projectPath = project.path
        project.tasks.register("windowsVisualTest", GenericUiVisualTestTask::class.java) {
            group = "verification"
            description = "Runs GenericUI JestElectricRunner tests with a live visual Win32 window on Windows."
            visualOnWindows.set(project.provider { extension.testing.visualOnWindows })
            visualStepDelayMs.set(project.provider { extension.testing.visualStepDelayMs })
        }

        project.tasks.register("runJestElectricWindowsVisualTest", GenericUiVisualTestTask::class.java) {
            group = TASK_GROUP
            description = "Alias for windowsVisualTest: runs JestElectricRunner tests in a live Win32 window."
            visualOnWindows.set(project.provider { extension.testing.visualOnWindows })
            visualStepDelayMs.set(project.provider { extension.testing.visualStepDelayMs })
        }

        project.tasks.register("printGenericUiConfig") {
            group = TASK_GROUP
            description = "Prints the resolved GenericUI target backends, extensions, and module coordinates."
            notCompatibleWithConfigurationCache("Prints resolved GenericUI configuration")
            doLast {
                println("=== GenericUI Configuration ($projectPath) ===")
                println("Backends:   ${extension.backends.enabledBackendModulePaths()}")
                println("Extensions: ${extension.extensions.enabledExtensionModulePaths()}")
                println("Sass Dir:   ${extension.styles.sourceDir} (compile=${extension.styles.compileSass}, webCss=${extension.styles.generateWebCss})")
                println("Icons:      win=${extension.application.desktop.windowsIcon}, apple=${extension.application.desktop.appleIcon}, mac=${extension.application.desktop.macIcon}, ios=${extension.application.desktop.iosIcon}, ipados=${extension.application.desktop.ipadosIcon}, linux=${extension.application.desktop.linuxIcon}, web=${extension.application.desktop.webIcon}")
            }
        }

        project.afterEvaluate {
            GenericUiMultiplatformConfigurator.configureMultiplatform(project, extension)
            GenericUiMultiplatformConfigurator.registerBuildTargetTasks(project, extension)
            GenericUiMultiplatformConfigurator.applySourceSetDependencies(project, extension)
            sassTask.configure {
                val resolvedAfterEval = resolveStylesSourceDir(project, extension.styles.sourceDir)
                if (resolvedAfterEval != null) {
                    sourceDirectory.set(resolvedAfterEval)
                }
            }
            wireExtensionAndBackendModules(project, extension)
            wireTestingModules(project, extension)
            wireOptionalSwingGraphicsDependencies(project, extension)
            wireGeneratedResourceKotlinSources(project, extension, resourcesTask.name)
            wireGeneratedLauncherAndAndroidSources(
                project = project,
                extension = extension,
                validateAppTaskName = validateAppTask.name,
                generateLaunchersTaskName = generateLaunchersTask.name,
                generateAndroidActivitiesTaskName = generateAndroidActivitiesTask.name,
                generateAndroidManifestTaskName = generateAndroidManifestTask.name,
                generateIosAppTaskName = generateIosAppTask.name,
                generateIpadosAppTaskName = generateIpadosAppTask.name
            )
            wirePrecompiledSassKotlinSources(project, extension, sassTask.name)
            wireGeneratedWebCssResources(project, extension, sassTask.name)
            wireDesktopResourceTasks(project, desktopResourcesTask.name)
            wireCompanionDependencyBuildTasks(project, extension, desktopResourcesTask.name)
            wireBuildLifecycleTasks(project, angleTask.name, vulkanTask.name, sassTask.name, resourcesTask.name, desktopResourcesTask.name)
            val webDistTaskNames = setOf(
                "packageGenericUiWebDistribution",
                "packageGenericUiWebDevelopmentDistribution",
                "packageGenericUiWebProductionDistribution",
                "packageGenericUiWebTestDistribution"
            )
            project.tasks.withType(GenericUiWebDistributionTask::class.java).configureEach {
                mustRunAfter(
                    project.tasks.matching { t ->
                        t.name == "compileGenericUiSass" ||
                            t.name.endsWith("ProcessResources") ||
                            (t.name.startsWith("compile") && (t.name.contains("KotlinWasmJs") || t.name.contains("KotlinJs"))) ||
                            t.name.endsWith("CompileSync") ||
                            t.name.endsWith("Webpack") ||
                            (t.name.endsWith("Distribution") && t.name !in webDistTaskNames)
                    }
                )
            }
            for (apkTaskProvider in androidApkTaskProviders) {
                apkTaskProvider.configure {
                    if (extension.backends.androidEnabled) {
                        dependsOn(
                            sassTask,
                            resourcesTask,
                            generateAndroidManifestTask,
                            generateAndroidActivitiesTask
                        )
                        listOf(
                            "compileAndroidMain",
                            "bundleAndroidMainClassesToRuntimeJar",
                            "bundleAndroidMainClassesToCompileJar",
                            "compileDebugKotlinAndroid",
                            "compileReleaseKotlinAndroid"
                        ).forEach { taskName ->
                            project.tasks.findByName(taskName)?.let { dependsOn(it) }
                        }
                        val artifactTypeAttr = org.gradle.api.attributes.Attribute.of("artifactType", String::class.java)
                        listOf(
                            "androidRuntimeClasspath",
                            "androidCompileClasspath",
                            "debugRuntimeClasspath",
                            "releaseRuntimeClasspath"
                        ).forEach { cfgName ->
                            val cfg = project.configurations.findByName(cfgName)
                            if (cfg != null && cfg.isCanBeResolved) {
                                for (artType in listOf("android-classes-jar", "jar")) {
                                    runCatching {
                                        dependsOn(
                                            cfg.incoming.artifactView {
                                                lenient(true)
                                                attributes {
                                                    attribute(artifactTypeAttr, artType)
                                                }
                                            }.files
                                        )
                                    }
                                }
                            }
                        }
                        val androidModules = resolveEnabledAndroidGenericUiModulePaths(project, extension)
                        for (modPath in androidModules) {
                            project.rootProject.findProject(modPath)?.let { subProj ->
                                subProj.tasks.findByName("bundleAndroidMainClassesToRuntimeJar")?.let { dependsOn(it) }
                                subProj.tasks.findByName("bundleAndroidMainClassesToCompileJar")?.let { dependsOn(it) }
                                subProj.tasks.findByName("compileAndroidMain")?.let { dependsOn(it) }
                            }
                            runCatching {
                                project.gradle.includedBuilds.forEach { ib ->
                                    val relModDir = File(ib.projectDir, modPath.removePrefix(":").replace(':', '/'))
                                    if (relModDir.isDirectory && File(relModDir, "build.gradle.kts").isFile) {
                                        dependsOn(ib.task("$modPath:bundleAndroidMainClassesToRuntimeJar"))
                                        dependsOn(ib.task("$modPath:bundleAndroidMainClassesToCompileJar"))
                                    }
                                }
                            }
                        }
                    }
                    mustRunAfter(
                        project.tasks.matching { t ->
                            t.name == "compileGenericUiSass" ||
                                t.name == "compileGenericUiResources" ||
                                t.name == "generateGenericUiAndroidManifest" ||
                                t.name == "generateGenericUiAndroidActivities" ||
                                t.name == "compileAndroidMain" ||
                                t.name == "bundleAndroidMainClassesToRuntimeJar" ||
                                t.name == "bundleAndroidMainClassesToCompileJar" ||
                                t.name == "compileDebugKotlinAndroid" ||
                                t.name == "compileReleaseKotlinAndroid" ||
                                (t.name.startsWith("compile") && t.name.contains("Android")) ||
                                (t.name.startsWith("link") && t.name.contains("AndroidNative"))
                        }
                    )
                }
            }
            for (swingJarTaskProvider in swingJarTaskProviders) {
                swingJarTaskProvider.configure {
                    if (extension.backends.swingConfig.enabled) {
                        dependsOn(
                            sassTask,
                            resourcesTask,
                            desktopResourcesTask,
                            validateAppTask,
                            generateLaunchersTask
                        )
                        listOf(
                            "compileKotlinJvm",
                            "jvmProcessResources",
                            "jvmMainClasses",
                            "jvmJar",
                            "compileKotlin",
                            "processResources",
                            "classes",
                            "jar"
                        ).forEach { taskName ->
                            project.tasks.findByName(taskName)?.let { dependsOn(it) }
                        }
                        val artifactTypeAttr = org.gradle.api.attributes.Attribute.of("artifactType", String::class.java)
                        listOf(
                            "jvmRuntimeClasspath",
                            "jvmCompileClasspath",
                            "runtimeClasspath",
                            "compileClasspath"
                        ).forEach { cfgName ->
                            val cfg = project.configurations.findByName(cfgName)
                            if (cfg != null && cfg.isCanBeResolved) {
                                runCatching {
                                    dependsOn(
                                        cfg.incoming.artifactView {
                                            lenient(true)
                                            attributes {
                                                attribute(artifactTypeAttr, "jar")
                                            }
                                        }.files
                                    )
                                }
                            }
                        }
                        val swingModules = resolveEnabledSwingGenericUiModulePaths(project, extension)
                        for (modPath in swingModules) {
                            project.rootProject.findProject(modPath)?.let { subProj ->
                                subProj.tasks.findByName("jvmJar")?.let { dependsOn(it) }
                                subProj.tasks.findByName("jar")?.let { dependsOn(it) }
                                subProj.tasks.findByName("compileKotlinJvm")?.let { dependsOn(it) }
                                subProj.tasks.findByName("compileKotlin")?.let { dependsOn(it) }
                                subProj.tasks.findByName("jvmProcessResources")?.let { dependsOn(it) }
                            }
                            runCatching {
                                project.gradle.includedBuilds.forEach { ib ->
                                    val relModDir = File(ib.projectDir, modPath.removePrefix(":").replace(':', '/'))
                                    val buildScript = File(relModDir, "build.gradle.kts")
                                    if (relModDir.isDirectory && buildScript.isFile) {
                                        val scriptText = buildScript.readText()
                                        if (scriptText.contains("kotlinJvm") || scriptText.contains("kotlin(\"jvm\")")) {
                                            dependsOn(ib.task("$modPath:jar"))
                                        } else {
                                            dependsOn(ib.task("$modPath:jvmJar"))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    mustRunAfter(
                        project.tasks.matching { t ->
                            t.name == "compileGenericUiSass" ||
                                t.name == "compileGenericUiResources" ||
                                t.name == "prepareGenericUiDesktopResources" ||
                                t.name == "generateGenericUiLaunchers" ||
                                t.name == "genericuiValidateApp" ||
                                t.name == "compileKotlinJvm" ||
                                t.name == "jvmProcessResources" ||
                                t.name == "jvmMainClasses" ||
                                t.name == "jvmJar" ||
                                t.name == "compileKotlin" ||
                                t.name == "classes" ||
                                t.name == "jar"
                        }
                    )
                }
            }
        }
    }

    private fun resolveEnabledSwingGenericUiModulePaths(
        project: Project,
        extension: GenericUiExtension
    ): List<String> {
        val paths = LinkedHashSet<String>()
        val backends = extension.backends
        if (extension.includeCommon) {
            paths.add(":common")
        }
        if (backends.requiresSystemModule) {
            paths.add(":system")
        }
        if (backends.requiresJvmModule) {
            paths.add(":jvm")
        }
        if (backends.requiresDesktopModule) {
            paths.add(":desktop")
        }
        for (extPath in extension.extensions.enabledExtensionModulePaths()) {
            paths.add(extPath)
        }
        return paths.toList()
    }

    private fun resolveEnabledAndroidGenericUiModulePaths(
        project: Project,
        extension: GenericUiExtension
    ): List<String> {
        val paths = LinkedHashSet<String>()
        val backends = extension.backends
        if (extension.includeCommon) {
            paths.add(":common")
        }
        if (backends.requiresSystemModule) {
            paths.add(":system")
        }
        if (backends.requiresJvmModule) {
            paths.add(":jvm")
        }
        if (backends.requiresMobileModule) {
            paths.add(":mobile")
        }
        for (extPath in extension.extensions.enabledExtensionModulePaths()) {
            paths.add(extPath)
        }
        return paths.toList()
    }

    private fun resolveStylesKotlinPackageName(
        project: Project,
        extension: GenericUiExtension
    ): String {
        val configured = extension.styles.kotlinPackageName.trim()
        if (configured.isNotEmpty() && configured != "com.nexustools.genericui.generated.styles") {
            return configured
        }
        val grp = project.group.toString().trim()
        if (grp.isNotEmpty() && grp != "unspecified") {
            return "$grp.styles"
        }
        val appPkg = extension.app.packageName.trim().ifEmpty { extension.app.id.trim() }
        if (appPkg.isNotEmpty() && appPkg != "com.nexustools.genericui.app") {
            return "$appPkg.styles"
        }
        return "com.nexustools.genericui.generated.styles"
    }

    private fun wireGeneratedLauncherAndAndroidSources(
        project: Project,
        extension: GenericUiExtension,
        validateAppTaskName: String,
        generateLaunchersTaskName: String,
        generateAndroidActivitiesTaskName: String,
        generateAndroidManifestTaskName: String,
        generateIosAppTaskName: String,
        generateIpadosAppTaskName: String
    ) {
        project.tasks.matching {
            it.name.startsWith("compile") &&
                !it.name.startsWith("compileGenericUi") &&
                (it.name.contains("Kotlin") || it.name.contains("Android"))
        }.configureEach {
            dependsOn(validateAppTaskName, generateLaunchersTaskName)
            if (extension.backends.androidEnabled &&
                (name.contains("Android", ignoreCase = true) || name == "compileKotlin")
            ) {
                dependsOn(generateAndroidActivitiesTaskName, generateAndroidManifestTaskName)
            }
            if (extension.backends.iosEnabled && name.contains("Ios", ignoreCase = true)) {
                dependsOn(generateIosAppTaskName)
            }
            if (extension.backends.ipadosEnabled && (name.contains("Ios", ignoreCase = true) || name.contains("Ipados", ignoreCase = true))) {
                dependsOn(generateIpadosAppTaskName)
            }
        }
    }

    private fun <T> resolveBackendVariantMap(
        project: Project,
        extension: GenericUiExtension,
        selector: (GenericUiResolvedVariantConfig) -> T
    ): Map<String, T> {
        val result = linkedMapOf<String, T>()
        for (backendScope in GenericUiGenerateLaunchersTask.enabledLauncherBackendScopes(extension.backends)) {
            val resolved = resolveVariantWithProjectFallbacks(project, extension, backendScope)
            result[backendScope.id] = selector(resolved)
        }
        return result
    }

    private fun resolveAndroidVariant(
        project: Project,
        extension: GenericUiExtension
    ): GenericUiResolvedVariantConfig =
        resolveVariantWithProjectFallbacks(project, extension, GenericUiVariantScope.ANDROID)

    private fun resolveIosVariant(
        project: Project,
        extension: GenericUiExtension
    ): GenericUiResolvedVariantConfig =
        resolveVariantWithProjectFallbacks(project, extension, GenericUiVariantScope.IOS)

    private fun resolveIpadosVariant(
        project: Project,
        extension: GenericUiExtension
    ): GenericUiResolvedVariantConfig =
        resolveVariantWithProjectFallbacks(project, extension, GenericUiVariantScope.IPADOS)

    private fun resolveWebVariant(
        project: Project,
        extension: GenericUiExtension
    ): GenericUiResolvedVariantConfig =
        resolveVariantWithProjectFallbacks(project, extension, GenericUiVariantScope.WEB)

    private fun resolveVariantWithProjectFallbacks(
        project: Project,
        extension: GenericUiExtension,
        backend: GenericUiVariantScope
    ): GenericUiResolvedVariantConfig {
        val base = GenericUiVariantResolver.resolve(extension, backend)
        val projectGroup = project.group.toString().trim().takeIf { it.isNotEmpty() && it != "unspecified" }
        val projectVersion = project.version.toString().trim().takeIf { it.isNotEmpty() && it != "unspecified" }
        val fallbackAppId = if (base.appId == "com.nexustools.genericui.app" && projectGroup != null) {
            projectGroup
        } else {
            base.appId
        }
        val fallbackAppName = if (base.appName == "GenericUI" && project.name.isNotBlank()) {
            project.name
        } else {
            base.appName
        }
        val fallbackVersion = if (base.appVersion == "1.0.0" && projectVersion != null) {
            projectVersion
        } else {
            base.appVersion
        }
        return base.copy(
            appId = fallbackAppId,
            appName = fallbackAppName,
            appVersion = fallbackVersion,
            packageName = base.packageName.ifBlank { fallbackAppId }
        )
    }

    private fun resolveUpstreamResourceManifestPaths(project: Project): List<String> {
        val paths = linkedSetOf<String>()
        for (cfg in project.configurations) {
            for (dep in cfg.dependencies) {
                if (dep is org.gradle.api.artifacts.ProjectDependency) {
                    val depPath = dep.path
                    val otherProj = project.rootProject.findProject(depPath)
                    if (otherProj != null && otherProj != project) {
                        val manifestFile = File(
                            otherProj.layout.buildDirectory.asFile.get(),
                            "generated/genericui/resources/backends/manifest/genericui-module-resources.json"
                        )
                        paths.add(manifestFile.absolutePath)
                    }
                }
            }
        }
        return paths.toList()
    }

    private fun wireGeneratedResourceKotlinSources(
        project: Project,
        extension: GenericUiExtension,
        resourcesTaskName: String
    ) {
        if (!extension.resources.enabled) return
        val ktResDir = project.layout.buildDirectory.dir("generated/genericui/resources/kotlin/commonMain")
        val kotlinExt = project.extensions.findByName("kotlin")
        if (kotlinExt != null) {
            runCatching {
                val getSourceSets = kotlinExt.javaClass.methods.firstOrNull {
                    it.name == "getSourceSets" && it.parameterCount == 0
                }
                val sourceSetsContainer = getSourceSets?.invoke(kotlinExt) as? org.gradle.api.NamedDomainObjectContainer<*>
                val targetSourceSet = sourceSetsContainer?.findByName("commonMain")
                    ?: sourceSetsContainer?.findByName("common")
                    ?: sourceSetsContainer?.findByName("main")
                if (targetSourceSet != null) {
                    val getKotlin = targetSourceSet.javaClass.methods.firstOrNull {
                        it.name == "getKotlin" && it.parameterCount == 0
                    }
                    val kotlinSourceDirSet = getKotlin?.invoke(targetSourceSet) as? org.gradle.api.file.SourceDirectorySet
                    kotlinSourceDirSet?.srcDir(ktResDir)
                }
            }
        }
        project.tasks.matching {
            it.name.startsWith("compile") &&
                !it.name.startsWith("compileGenericUi") &&
                (it.name.contains("Kotlin") || it.name.contains("Android"))
        }.configureEach {
            dependsOn(resourcesTaskName)
        }
    }

    private fun wireCompanionDependencyBuildTasks(
        project: Project,
        extension: GenericUiExtension,
        desktopResourcesTaskName: String
    ) {
        val depTasks = GenericUiMultiplatformConfigurator.resolveCompanionDependencyBuildTasks(project, extension)
        if (depTasks.isEmpty()) return

        project.tasks.matching {
            (it.name.startsWith("compile") &&
                !it.name.startsWith("compileGenericUi") &&
                (it.name.contains("Kotlin") || it.name.contains("Android"))) ||
                it.name == desktopResourcesTaskName ||
                it.name.endsWith("ProcessResources", ignoreCase = true) ||
                it.name == "processResources"
        }.configureEach {
            for (depTask in depTasks) {
                if (this != depTask && this.name != depTask.toString()) {
                    dependsOn(depTask)
                }
            }
        }
    }

    private fun resolveStylesSourceDir(project: Project, configuredPath: String): File? {
        val primary = File(project.projectDir, configuredPath)
        if (primary.exists() && primary.isDirectory) {
            return primary
        }
        val fallbackDirs = listOf(
            "src/commonMain/resources/styles",
            "src/commonMain/resources/style",
            "src/commonMain/resources/css",
            "src/commonMain/resources",
            "src/webMain/resources/styles",
            "src/webMain/resources",
            "src/jsMain/resources/styles",
            "src/jsMain/resources",
            "src/wasmJsMain/resources/styles",
            "src/wasmJsMain/resources",
            "src/main/resources/styles",
            "src/main/resources"
        )
        return fallbackDirs
            .map { File(project.projectDir, it) }
            .firstOrNull { dir ->
                dir.exists() && dir.isDirectory && dir.walkTopDown().any { f ->
                    f.isFile && f.extension.lowercase() in setOf("scss", "sass", "css")
                }
            }
    }

    private fun wireExtensionAndBackendModules(project: Project, extension: GenericUiExtension) {
        val commonConfigName = resolvePrimaryImplementationConfiguration(project) ?: return
        val backends = extension.backends
        val version = extension.version

        if (extension.includeCommon) {
            resolveModuleDependency(project, ":common", version)?.let { dep ->
                project.dependencies.add(commonConfigName, dep)
                addDepToEnabledBackendConfigs(project, backends, dep, commonConfigName)
            }
        }

        for (extPath in extension.extensions.enabledExtensionModulePaths()) {
            resolveModuleDependency(project, extPath, version)?.let { dep ->
                project.dependencies.add(commonConfigName, dep)
                addDepToEnabledBackendConfigs(project, backends, dep, commonConfigName)
            }
        }

        for (backendPath in backends.enabledBackendModulePaths()) {
            addBackendModuleDependency(project, backendPath, backendPath, version, commonConfigName)
        }
    }

    private fun wireTestingModules(project: Project, extension: GenericUiExtension) {
        if (!extension.testing.enabled) return
        val version = extension.version
        val backends = extension.backends
        val commonTestConfig = listOf("commonTestImplementation", "testImplementation").firstOrNull {
            project.configurations.findByName(it) != null
        } ?: return

        resolveModuleDependency(project, ":tests", version)?.let { dep ->
            project.dependencies.add(commonTestConfig, dep)
        }

        fun addTestsToConfigs(vararg configNames: String) {
            val dep = resolveModuleDependency(project, ":tests", version) ?: return
            for (cfg in configNames) {
                if (project.configurations.findByName(cfg) != null) {
                    project.dependencies.add(cfg, dep)
                }
            }
        }

        if (backends.requiresSystemModule) {
            addTestsToConfigs("systemTestImplementation")
        }
        if (backends.requiresAppleModule) {
            addTestsToConfigs("appleTestImplementation")
        }
        if (backends.requiresJvmModule) {
            addTestsToConfigs("jvmCommonTestImplementation")
        }
        if (backends.requiresNativeDesktopCommonModule) {
            addTestsToConfigs(
                "nativeDesktopTestImplementation",
                "nativeDesktopCommonTestImplementation",
                "desktopNativeTestImplementation",
                "desktopNativeCommonTestImplementation",
                "mingwX64TestImplementation",
                "linuxX64TestImplementation",
                "linuxArm64TestImplementation",
                "macosArm64TestImplementation"
            )
        }
        if (backends.windowsEnabled) {
            addTestsToConfigs("windowsTestImplementation", "mingwX64TestImplementation")
        }
        if (backends.macosEnabled) {
            addTestsToConfigs("macTestImplementation", "macosTestImplementation", "macosArm64TestImplementation")
        }
        if (backends.gtkEnabled || backends.qtEnabled) {
            addTestsToConfigs(
                "linuxTestImplementation",
                "gtkTestImplementation",
                "qtTestImplementation",
                "linuxX64TestImplementation",
                "linuxArm64TestImplementation"
            )
        }
        if (backends.swingConfig.enabled) {
            addTestsToConfigs("desktopTestImplementation", "swingTestImplementation", "jvmTestImplementation", "testImplementation")
        }
        if (backends.requiresMobileModule) {
            addTestsToConfigs(
                "mobileCommonTestImplementation",
                "mobileTestImplementation",
                "iosCommonTestImplementation",
                "androidTestImplementation",
                "androidUnitTestImplementation",
                "androidHostTestImplementation",
                "iosArm64TestImplementation",
                "iosSimulatorArm64TestImplementation"
            )
        }
        if (backends.androidEnabled) {
            addTestsToConfigs(
                "androidTestImplementation",
                "androidUnitTestImplementation",
                "androidHostTestImplementation",
                "androidInstrumentedTestImplementation"
            )
        }
        if (backends.iosEnabled) {
            addTestsToConfigs("iosTestImplementation", "iosArm64TestImplementation", "iosSimulatorArm64TestImplementation")
        }
        if (backends.ipadosEnabled) {
            addTestsToConfigs("ipadosTestImplementation", "iosArm64TestImplementation", "iosSimulatorArm64TestImplementation")
        }
        if (backends.webEnabled) {
            addTestsToConfigs("webTestImplementation", "jsTestImplementation", "wasmJsTestImplementation")
        }
    }

    private fun addDepToEnabledBackendConfigs(
        project: Project,
        backends: GenericUiBackendsConfig,
        dep: Any,
        fallbackSingleTargetConfig: String
    ) {
        val addedConfigs = mutableSetOf<String>()
        for (backendKey in backends.enabledBackendModulePaths()) {
            val targetConfigs = resolveBackendConfigurations(project, backendKey, fallbackSingleTargetConfig)
            for (cfg in targetConfigs) {
                if (cfg != fallbackSingleTargetConfig && addedConfigs.add(cfg)) {
                    project.dependencies.add(cfg, dep)
                }
            }
        }
    }

    private fun addBackendModuleDependency(
        project: Project,
        backendKey: String,
        modulePath: String,
        version: String,
        fallbackSingleTargetConfig: String
    ) {
        val dep = resolveModuleDependency(project, modulePath, version) ?: return
        val targetConfigs = resolveBackendConfigurations(project, backendKey, fallbackSingleTargetConfig)
        for (cfg in targetConfigs) {
            project.dependencies.add(cfg, dep)
        }
    }

    /**
     * Resolves a GenericUI module either as a local subproject (`project(":...")` when building inside
     * GenericUI) or as its exact composite-build / Maven coordinate (`com.nexustools.genericui...:0.1.0-SNAPSHOT`)
     * when `GenericUI` is linked via `includeBuild("lib/GenericUI")`.
     */
    internal fun resolveModuleDependency(
        project: Project,
        modulePath: String,
        version: String = DEFAULT_VERSION
    ): Any? {
        val localProject = project.rootProject.findProject(modulePath)
        if (localProject != null) {
            return if (localProject == project) null else localProject
        }
        if (modulePath !in KNOWN_GENERICUI_MODULES) {
            return null
        }
        if (modulePath != ":extensions:ui:dsl" && modulePath.endsWith(":dsl")) {
            val relPath = modulePath.trimStart(':').replace(':', '/')
            val compositeCandidate = File(project.rootProject.projectDir, "lib/GenericUI/$relPath")
            val localCandidate = File(project.rootProject.projectDir, relPath)
            if (!compositeCandidate.isDirectory && !localCandidate.isDirectory) {
                return null
            }
        }
        return toMavenCoordinate(modulePath, version)
    }

    private fun wireOptionalSwingGraphicsDependencies(project: Project, extension: GenericUiExtension) {
        val swing = extension.backends.swingConfig
        if (!swing.useLwjglGL && !swing.useVulkan) {
            return
        }

        val jvmImplConfig = resolveJvmImplementationConfiguration(project) ?: return
        val jvmRuntimeConfig = resolveJvmRuntimeConfiguration(project) ?: jvmImplConfig
        val version = swing.lwjglVersion
        val classifiers = swing.targetClassifiers.ifEmpty { setOf(OsArchClassifier.detectHost()) }

        // Core LWJGL + JAWT bridge are shared by both GLContext and VKContext on Swing/AWT
        project.dependencies.add(jvmImplConfig, "org.lwjgl:lwjgl:$version")
        project.dependencies.add(jvmImplConfig, "org.lwjgl:lwjgl-jawt:$version")
        for (target in classifiers) {
            project.dependencies.add(jvmRuntimeConfig, "org.lwjgl:lwjgl:$version:${target.lwjglClassifier}")
        }

        if (swing.useLwjglGL) {
            val glModules = listOf(
                "org.lwjgl:lwjgl-egl",
                "org.lwjgl:lwjgl-opengles",
                "org.lwjgl:lwjgl-opengl"
            )
            for (module in glModules) {
                project.dependencies.add(jvmImplConfig, "$module:$version")
                for (target in classifiers) {
                    project.dependencies.add(jvmRuntimeConfig, "$module:$version:${target.lwjglClassifier}")
                }
            }
        }

        if (swing.useVulkan) {
            project.dependencies.add(jvmImplConfig, "org.lwjgl:lwjgl-vulkan:$version")
            for (target in classifiers) {
                if (target.isMacOs && extension.graphics.bundleMoltenVkOnApple) {
                    project.dependencies.add(
                        jvmRuntimeConfig,
                        "org.lwjgl:lwjgl-vulkan:$version:${target.lwjglClassifier}"
                    )
                }
            }

            if (extension.graphics.bundleShaderc) {
                project.dependencies.add(jvmImplConfig, "org.lwjgl:lwjgl-shaderc:$version")
                for (target in classifiers) {
                    project.dependencies.add(
                        jvmRuntimeConfig,
                        "org.lwjgl:lwjgl-shaderc:$version:${target.lwjglClassifier}"
                    )
                }
            }

            project.dependencies.add(jvmImplConfig, "org.lwjgl:lwjgl-vma:$version")
            for (target in classifiers) {
                project.dependencies.add(
                    jvmRuntimeConfig,
                    "org.lwjgl:lwjgl-vma:$version:${target.lwjglClassifier}"
                )
            }
        }
    }

    private fun wirePrecompiledSassKotlinSources(
        project: Project,
        extension: GenericUiExtension,
        sassTaskName: String
    ) {
        if (!extension.styles.compileSass || !extension.styles.precompileToKotlin) {
            return
        }
        val ktOutDir = project.layout.buildDirectory.dir(extension.styles.kotlinOutputDir)
        val kotlinExt = project.extensions.findByName("kotlin")
        if (kotlinExt != null) {
            runCatching {
                val getSourceSets = kotlinExt.javaClass.methods.firstOrNull {
                    it.name == "getSourceSets" && it.parameterCount == 0
                }
                val sourceSetsContainer = getSourceSets?.invoke(kotlinExt) as? org.gradle.api.NamedDomainObjectContainer<*>
                val targetSourceSet = sourceSetsContainer?.findByName("systemMain")
                    ?: sourceSetsContainer?.findByName("systemCommon")
                    ?: sourceSetsContainer?.findByName("systemCommonMain")
                    ?: sourceSetsContainer?.findByName("commonMain")
                    ?: sourceSetsContainer?.findByName("main")
                if (targetSourceSet != null) {
                    val getKotlin = targetSourceSet.javaClass.methods.firstOrNull {
                        it.name == "getKotlin" && it.parameterCount == 0
                    }
                    val kotlinSourceDirSet = getKotlin?.invoke(targetSourceSet) as? org.gradle.api.file.SourceDirectorySet
                    kotlinSourceDirSet?.srcDir(ktOutDir)
                }
            }
        }
        project.tasks.matching {
            it.name.startsWith("compile") &&
                !it.name.startsWith("compileGenericUi") &&
                (it.name.contains("Kotlin") || it.name.contains("Android"))
        }.configureEach {
            dependsOn(sassTaskName)
        }
    }

    /**
     * Wires the compiled `.css` files generated by `compileGenericUiSass` (`webCssOutputDirectory`)
     * into the Web target (`jsMain`, `wasmJsMain`, `webMain`) resource source sets, `jsProcessResources` /
     * `wasmJsProcessResources` copy tasks, and Web distribution / webpack tasks so the Web target
     * always has compiled `.css` files generated from `.scss` / `.sass`.
     */
    private fun wireGeneratedWebCssResources(
        project: Project,
        extension: GenericUiExtension,
        sassTaskName: String
    ) {
        if (!extension.styles.compileSass || !extension.styles.generateWebCss) {
            return
        }
        val webCssOutDir = project.layout.buildDirectory.dir(extension.styles.webCssOutputDir)
        val kotlinExt = project.extensions.findByName("kotlin")
        if (kotlinExt != null) {
            runCatching {
                val getSourceSets = kotlinExt.javaClass.methods.firstOrNull {
                    it.name == "getSourceSets" && it.parameterCount == 0
                }
                val sourceSetsContainer = getSourceSets?.invoke(kotlinExt) as? org.gradle.api.NamedDomainObjectContainer<*>
                if (sourceSetsContainer != null) {
                    val webSourceSetNames = listOf("jsMain", "wasmJsMain", "webMain")
                    val matchedWebSourceSets = webSourceSetNames.mapNotNull { sourceSetsContainer.findByName(it) }
                    val targetSourceSets = matchedWebSourceSets.ifEmpty {
                        listOfNotNull(
                            sourceSetsContainer.findByName("commonMain")
                                ?: sourceSetsContainer.findByName("main")
                        )
                    }
                    for (ss in targetSourceSets) {
                        val getResources = ss.javaClass.methods.firstOrNull {
                            it.name == "getResources" && it.parameterCount == 0
                        }
                        val resourcesDirSet = getResources?.invoke(ss) as? org.gradle.api.file.SourceDirectorySet
                        resourcesDirSet?.srcDir(webCssOutDir)
                    }
                }
            }
        }

        val webResourceTaskNames = setOf(
            "jsProcessResources",
            "wasmJsProcessResources",
            "webProcessResources"
        )
        project.tasks.matching {
            it.name in webResourceTaskNames ||
                (extension.backends.webEnabled && it.name == "processResources")
        }.configureEach {
            dependsOn(sassTaskName)
            if (this is org.gradle.api.tasks.Copy) {
                from(webCssOutDir)
                duplicatesStrategy = org.gradle.api.file.DuplicatesStrategy.INCLUDE
            }
        }

        val webLifecycleTaskPrefixes = listOf(
            "jsBrowser",
            "wasmJsBrowser",
            "jsDevelopmentExecutableCompileSync",
            "jsProductionExecutableCompileSync",
            "wasmJsDevelopmentExecutableCompileSync",
            "wasmJsProductionExecutableCompileSync"
        )
        project.tasks.matching { task ->
            webLifecycleTaskPrefixes.any { prefix -> task.name.startsWith(prefix) }
        }.configureEach {
            dependsOn(sassTaskName)
        }
    }

    private fun wireDesktopResourceTasks(
        project: Project,
        desktopResourcesTaskName: String
    ) {
        project.tasks.matching {
            it.name.startsWith("link") && it.name.contains("Executable")
        }.configureEach {
            dependsOn(desktopResourcesTaskName)
        }
    }

    private fun wireBuildLifecycleTasks(
        project: Project,
        angleTaskName: String,
        vulkanTaskName: String,
        sassTaskName: String,
        resourcesTaskName: String,
        desktopResourcesTaskName: String
    ) {
        project.tasks.matching {
            it.name.endsWith("ProcessResources", ignoreCase = true) || it.name == "processResources"
        }.configureEach {
            dependsOn(angleTaskName, vulkanTaskName, sassTaskName, resourcesTaskName, desktopResourcesTaskName)
        }
        project.tasks.matching {
            it.name == desktopResourcesTaskName
        }.configureEach {
            dependsOn(resourcesTaskName)
        }
    }

    private fun resolvePrimaryImplementationConfiguration(project: Project): String? =
        listOf("commonMainImplementation", "implementation").firstOrNull {
            project.configurations.findByName(it) != null
        }

    private fun resolveBackendConfigurations(
        project: Project,
        backendPath: String,
        fallbackSingleTargetConfig: String
    ): List<String> {
        val isMultiplatform = project.configurations.findByName("commonMainImplementation") != null
        if (!isMultiplatform) {
            return if (project.configurations.findByName(fallbackSingleTargetConfig) != null) {
                listOf(fallbackSingleTargetConfig)
            } else {
                emptyList()
            }
        }

        fun firstExisting(vararg names: String): List<String> {
            val match = names.firstOrNull { project.configurations.findByName(it) != null }
            return if (match != null) listOf(match) else emptyList()
        }

        fun allExisting(vararg names: String): List<String> =
            names.filter { project.configurations.findByName(it) != null }

        return when (backendPath) {
            ":system", ":systemCommon", ":system:shared" ->
                allExisting(
                    "systemCommonImplementation",
                    "systemMainImplementation",
                    "appleCommonImplementation",
                    "appleMainImplementation",
                    "jvmCommonImplementation",
                    "desktopCommonImplementation",
                    "desktopMainImplementation",
                    "nativeDesktopCommonImplementation",
                    "nativeDesktopMainImplementation",
                    "desktopNativeCommonImplementation",
                    "desktopNativeMainImplementation",
                    "linuxCommonImplementation",
                    "linuxMainImplementation",
                    "mobileCommonImplementation",
                    "mobileMainImplementation",
                    "iosCommonImplementation",
                    "swingImplementation",
                    "swingMainImplementation",
                    "jvmMainImplementation",
                    "androidImplementation",
                    "androidMainImplementation",
                    "windowsMainImplementation",
                    "mingwX64MainImplementation",
                    "macMainImplementation",
                    "macosMainImplementation",
                    "macosArm64MainImplementation",
                    "gtkMainImplementation",
                    "qtMainImplementation",
                    "linuxX64MainImplementation",
                    "linuxArm64MainImplementation",
                    "linuxArm32HfpMainImplementation",
                    "iosImplementation",
                    "iosMainImplementation",
                    "ipadosMainImplementation",
                    "iosArm64MainImplementation",
                    "iosSimulatorArm64MainImplementation"
                )
            ":jvm" ->
                allExisting(
                    "jvmCommonImplementation",
                    "jvmMainImplementation",
                    "swingImplementation",
                    "swingMainImplementation",
                    "androidImplementation",
                    "androidMainImplementation"
                )
            ":apple" ->
                allExisting(
                    "appleCommonImplementation",
                    "appleMainImplementation",
                    "macMainImplementation",
                    "macosMainImplementation",
                    "macosArm64MainImplementation",
                    "iosCommonImplementation",
                    "iosImplementation",
                    "iosMainImplementation",
                    "ipadosMainImplementation",
                    "iosArm64MainImplementation",
                    "iosSimulatorArm64MainImplementation"
                )
            ":desktop", ":desktop:common", ":desktop:nativeCommon", ":desktop:shared" ->
                allExisting(
                    "desktopCommonImplementation",
                    "desktopMainImplementation",
                    "nativeDesktopCommonImplementation",
                    "nativeDesktopMainImplementation",
                    "desktopNativeCommonImplementation",
                    "desktopNativeMainImplementation",
                    "linuxCommonImplementation",
                    "linuxMainImplementation",
                    "windowsMainImplementation",
                    "mingwX64MainImplementation",
                    "macMainImplementation",
                    "macosMainImplementation",
                    "macosArm64MainImplementation",
                    "gtkImplementation",
                    "gtkMainImplementation",
                    "qtImplementation",
                    "qtMainImplementation",
                    "linuxX64MainImplementation",
                    "linuxArm64MainImplementation",
                    "linuxArm32HfpMainImplementation",
                    "linuxX64GtkImplementation",
                    "linuxArm64GtkImplementation",
                    "linuxX64QtImplementation",
                    "linuxArm64QtImplementation",
                    "swingImplementation",
                    "swingMainImplementation",
                    "jvmMainImplementation"
                )
            ":mobile", ":mobile:common", ":mobile:shared" ->
                allExisting(
                    "mobileCommonImplementation",
                    "mobileMainImplementation",
                    "androidImplementation",
                    "androidMainImplementation",
                    "iosCommonImplementation",
                    "iosImplementation",
                    "iosMainImplementation",
                    "ipadosMainImplementation",
                    "iosArm64MainImplementation",
                    "iosSimulatorArm64MainImplementation"
                )
            ":web" ->
                allExisting("webImplementation", "webMainImplementation", "jsMainImplementation", "wasmJsMainImplementation")
            else -> emptyList()
        }
    }

    private fun resolveJvmImplementationConfiguration(project: Project): String? =
        listOf("jvmMainImplementation", "implementation", "commonMainImplementation").firstOrNull {
            project.configurations.findByName(it) != null
        }

    private fun resolveJvmRuntimeConfiguration(project: Project): String? =
        listOf("jvmMainRuntimeOnly", "runtimeOnly", "jvmMainImplementation", "implementation").firstOrNull {
            project.configurations.findByName(it) != null
        }

    companion object {
        const val TASK_GROUP: String = "genericui"
        const val BASE_GROUP: String = "com.nexustools.genericui"
        const val DEFAULT_VERSION: String = "0.1.0-SNAPSHOT"

        internal fun configureWebDistributionTaskInstance(
            project: Project,
            extension: GenericUiExtension,
            task: GenericUiWebDistributionTask,
            variant: String,
            outputRelPath: String
        ) {
            task.group = TASK_GROUP
            task.description = "Packages the unified GenericUI Web ($variant) distribution with Wasm primary + JS IR fallback, splash screen icon, favicon, optional PWA manifest.webmanifest & installable WebApp tags, loader.js, and SHA-1 cache-busting hashes into build/$outputRelPath."
            task.projectRootPath.set(project.projectDir.absolutePath)
            task.projectBuildPath.set(project.layout.buildDirectory.asFile.map { it.absolutePath })
            task.baseName.set(project.provider {
                extension.application.baseName.trim().ifEmpty { project.name }
            })
            task.appName.set(project.provider {
                extension.application.desktop.appName.trim().ifEmpty {
                    extension.application.baseName.trim().ifEmpty { project.name }
                }
            })
            task.appDescription.set(project.provider { extension.application.desktop.description })
            task.themeColor.set(project.provider {
                resolveVariantWithProjectFallbacksStatic(project, extension, GenericUiVariantScope.WEB).themeColor
            })
            task.themeMode.set(project.provider {
                resolveVariantWithProjectFallbacksStatic(project, extension, GenericUiVariantScope.WEB).themeMode
            })
            task.lightThemeColors.set(project.provider {
                resolveVariantWithProjectFallbacksStatic(project, extension, GenericUiVariantScope.WEB).lightThemeColors
            })
            task.darkThemeColors.set(project.provider {
                resolveVariantWithProjectFallbacksStatic(project, extension, GenericUiVariantScope.WEB).darkThemeColors
            })
            task.hasCustomThemeOverrides.set(project.provider {
                resolveVariantWithProjectFallbacksStatic(project, extension, GenericUiVariantScope.WEB).hasCustomThemeOverrides
            })
            task.webIconPath.set(project.provider { extension.application.desktop.webIcon })
            task.fallbackIconPath.set(project.provider { extension.application.desktop.icon })
            task.linuxIconPath.set(project.provider { extension.application.desktop.linuxIcon })
            task.macIconPath.set(project.provider { extension.application.desktop.macIcon })
            task.windowsIconPath.set(project.provider { extension.application.desktop.windowsIcon })
            task.webCssBundleFileName.set(project.provider { extension.styles.webCssBundleFileName })
            task.globalStyleSheet.set(project.provider {
                extension.styles.globalStyleSheet?.trim()?.takeIf { it.isNotEmpty() }
                    ?: resolveVariantWithProjectFallbacksStatic(project, extension, GenericUiVariantScope.WEB).globalStyleSheet
                    ?: ""
            })
            task.manifestEnabled.set(project.provider { extension.application.desktop.webManifest.enabled })
            task.allowInstallAsWebApp.set(project.provider { extension.application.desktop.webManifest.allowInstallAsWebApp })
            task.manifestFileName.set(project.provider { extension.application.desktop.webManifest.fileName })
            task.manifestId.set(project.provider { extension.application.desktop.webManifest.id })
            task.manifestName.set(project.provider { extension.application.desktop.webManifest.name })
            task.manifestShortName.set(project.provider { extension.application.desktop.webManifest.shortName })
            task.manifestDescription.set(project.provider { extension.application.desktop.webManifest.description })
            task.manifestStartUrl.set(project.provider { extension.application.desktop.webManifest.startUrl })
            task.manifestScope.set(project.provider { extension.application.desktop.webManifest.scope })
            task.manifestDisplay.set(project.provider { extension.application.desktop.webManifest.display })
            task.manifestDisplayOverride.set(project.provider { extension.application.desktop.webManifest.displayOverride.toList() })
            task.manifestOrientation.set(project.provider { extension.application.desktop.webManifest.orientation })
            task.manifestThemeColor.set(project.provider { extension.application.desktop.webManifest.themeColor })
            task.manifestBackgroundColor.set(project.provider { extension.application.desktop.webManifest.backgroundColor })
            task.manifestLang.set(project.provider { extension.application.desktop.webManifest.lang })
            task.manifestDir.set(project.provider { extension.application.desktop.webManifest.dir })
            task.manifestCategories.set(project.provider { extension.application.desktop.webManifest.categories.toList() })
            task.manifestPreferRelatedApplications.set(project.provider { extension.application.desktop.webManifest.preferRelatedApplications })
            task.manifestAppleStatusBarStyle.set(project.provider { extension.application.desktop.webManifest.appleStatusBarStyle })
            task.manifestIcons.set(project.provider { extension.application.desktop.webManifest.icons.toList() })
            task.manifestShortcuts.set(project.provider { extension.application.desktop.webManifest.shortcuts.toList() })
            task.buildVariant.set(variant)
            task.outputDirectory.set(project.layout.buildDirectory.dir(outputRelPath))
            task.outputs.upToDateWhen { false }
        }

        private fun resolveVariantWithProjectFallbacksStatic(
            project: Project,
            extension: GenericUiExtension,
            backend: GenericUiVariantScope
        ): GenericUiResolvedVariantConfig {
            val base = GenericUiVariantResolver.resolve(extension, backend)
            val projectGroup = project.group.toString().trim().takeIf { it.isNotEmpty() && it != "unspecified" }
            val projectVersion = project.version.toString().trim().takeIf { it.isNotEmpty() && it != "unspecified" }
            val fallbackAppId = if (base.appId == "com.nexustools.genericui.app" && projectGroup != null) {
                projectGroup
            } else {
                base.appId
            }
            val fallbackAppName = if (base.appName == "GenericUI" && project.name.isNotBlank()) {
                project.name
            } else {
                base.appName
            }
            val fallbackVersion = if (base.appVersion == "1.0.0" && projectVersion != null) {
                projectVersion
            } else {
                base.appVersion
            }
            return base.copy(
                appId = fallbackAppId,
                appName = fallbackAppName,
                appVersion = fallbackVersion,
                packageName = base.packageName.ifBlank { fallbackAppId }
            )
        }

        /**
         * Converts a GenericUI Gradle module path (e.g. `":common"`, `":desktop"`,
         * `":extensions:graphics:canvas2d"`, `":extensions:game:engine2d:dsl"`) into its exact Maven /
         * composite-build coordinate matching GenericUI's `group` and artifact hierarchy.
         */
        fun toMavenCoordinate(modulePath: String, version: String = DEFAULT_VERSION): String {
            val segments = modulePath.trimStart(':').split(':').filter { it.isNotEmpty() }
            require(segments.isNotEmpty()) { "Invalid GenericUI module path: $modulePath" }
            return if (segments.size == 1) {
                "$BASE_GROUP:${segments.first()}:$version"
            } else {
                val groupSuffix = segments.dropLast(1).joinToString(".")
                val artifact = segments.last()
                "$BASE_GROUP.$groupSuffix:$artifact:$version"
            }
        }

        /**
         * Historically listed extensions structured as a Project-of-Projects.
         * All modules in the consolidated 45-project architecture are single multiplatform Gradle projects.
         */
        val PROJECT_OF_PROJECTS_EXTENSIONS: Set<String> = emptySet()

        /**
         * Canonical set of GenericUI library/test module paths present in `GenericUI/settings.gradle.kts`.
         */
        val KNOWN_GENERICUI_MODULES: Set<String> = setOf(
            ":common",
            ":system",
            ":jvm",
            ":apple",
            ":desktop",
            ":mobile",
            ":web",
            ":extensions:graphics:gpu",
            ":extensions:graphics:imageprocessing",
            ":extensions:graphics:canvas2d",
            ":extensions:graphics:canvas2d:dsl",
            ":extensions:graphics:canvas3d",
            ":extensions:graphics:canvas3d:dsl",
            ":extensions:graphics:threejs",
            ":extensions:graphics:threejs:dsl",
            ":extensions:physics:box2d",
            ":extensions:physics:box2d:dsl",
            ":extensions:physics:box3d",
            ":extensions:physics:box3d:dsl",
            ":extensions:game:gamepad",
            ":extensions:game:gamepad:dsl",
            ":extensions:game:engine",
            ":extensions:game:engine:dsl",
            ":extensions:game:engine2d",
            ":extensions:game:engine2d:dsl",
            ":extensions:game:engine3d",
            ":extensions:game:engine3d:dsl",
            ":extensions:media:media",
            ":extensions:media:media:dsl",
            ":extensions:media:mediacontrols",
            ":extensions:media:mediacontrols:dsl",
            ":extensions:ui:dsl",
            ":extensions:ui:overlay",
            ":extensions:ui:overlay:dsl",
            ":extensions:ui:alien",
            ":extensions:ui:alien:dsl",
            ":extensions:ui:webview",
            ":extensions:ui:webview:dsl",
            ":extensions:ui:codeeditor",
            ":extensions:ui:codeeditor:dsl",
            ":extensions:ui:aichat",
            ":extensions:ui:aichat:dsl",
            ":tests"
        )
    }
}
