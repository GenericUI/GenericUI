package com.nexustools.genericui.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

/**
 * Gradle task (`generateGenericUiAndroidManifest`) that generates `AndroidManifest.xml` from:
 * - Resolved `appId`, `appName`, and `packageName`
 * - `genericui { android { manifest { ... } } }` ([GenericUiAndroidManifestConfig])
 * - `genericui { android { services { ... } } }` ([GenericUiAndroidServiceSpec])
 * - Discovered Android `@MainAppFragment` (with `MAIN` + `LAUNCHER` `<intent-filter>`) and
 *   `@AppFragment` classes (each mapped 1:1 to `<frag.generatedActivityQualifiedName>`).
 */
@DisableCachingByDefault(because = "Generates AndroidManifest.xml")
abstract class GenericUiGenerateAndroidManifestTask : DefaultTask() {

    @get:Input
    @get:Optional
    abstract val androidEnabled: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val projectRootPath: Property<String>

    @get:Input
    @get:Optional
    abstract val sourceDirectories: ListProperty<String>

    @get:Input
    @get:Optional
    abstract val appId: Property<String>

    @get:Input
    @get:Optional
    abstract val appName: Property<String>

    @get:Input
    @get:Optional
    abstract val packageName: Property<String>

    @get:Input
    @get:Optional
    abstract val applicationClass: Property<String>

    @get:Input
    @get:Optional
    abstract val theme: Property<String>

    @get:Input
    @get:Optional
    abstract val screenOrientation: Property<String>

    @get:Input
    @get:Optional
    abstract val configChanges: Property<String>

    @get:Input
    @get:Optional
    abstract val supportsPictureInPicture: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val permissions: ListProperty<String>

    @get:Input
    @get:Optional
    abstract val serializedServices: ListProperty<String>

    @get:OutputDirectory
    @get:Optional
    abstract val outputDirectory: DirectoryProperty

    @get:OutputFile
    @get:Optional
    abstract val outputManifestFile: RegularFileProperty

    @TaskAction
    fun generate() {
        val targetFile: File = when {
            outputManifestFile.isPresent -> outputManifestFile.get().asFile
            outputDirectory.isPresent -> File(outputDirectory.get().asFile, "AndroidManifest.xml")
            else -> File(project.layout.buildDirectory.asFile.get(), "generated/genericui/android-manifest/AndroidManifest.xml")
        }

        if (androidEnabled.orNull == false) {
            targetFile.parentFile?.mkdirs()
            return
        }

        val rootDir = projectRootPath.orNull?.takeIf { it.isNotBlank() }?.let { File(it) } ?: project.projectDir
        val customDirs = sourceDirectories.orNull.orEmpty().map { File(it) }.filter { it.exists() }
        val discovered = if (customDirs.isNotEmpty()) {
            GenericUiFragmentScanner.scanSourceDirectories(customDirs)
        } else {
            GenericUiFragmentScanner.scanProject(rootDir)
        }

        val (winningMain, androidFragments) = GenericUiGenerateAndroidActivitiesTask.resolveAndroidFragments(discovered)

        val resolvedAppId = appId.orNull?.takeIf { it.isNotBlank() } ?: "com.nexustools.genericui.app"
        val resolvedAppName = appName.orNull?.takeIf { it.isNotBlank() } ?: "GenericUI"
        val resolvedPkg = packageName.orNull?.takeIf { it.isNotBlank() } ?: resolvedAppId

        val manifestConfig = GenericUiAndroidManifestConfig().apply {
            permissions.addAll(this@GenericUiGenerateAndroidManifestTask.permissions.orNull.orEmpty())
            this@GenericUiGenerateAndroidManifestTask.screenOrientation.orNull?.takeIf { it.isNotBlank() }?.let {
                screenOrientation = it
            }
            this@GenericUiGenerateAndroidManifestTask.configChanges.orNull?.takeIf { it.isNotBlank() }?.let {
                configChanges = it
            }
            this@GenericUiGenerateAndroidManifestTask.supportsPictureInPicture.orNull?.let {
                supportsPictureInPicture = it
            }
            this@GenericUiGenerateAndroidManifestTask.applicationClass.orNull?.takeIf { it.isNotBlank() }?.let {
                applicationClass = it
            }
            this@GenericUiGenerateAndroidManifestTask.theme.orNull?.takeIf { it.isNotBlank() }?.let {
                theme = it
            }
        }

        val parsedServices = serializedServices.orNull.orEmpty().mapNotNull { deserializeServiceSpec(it) }

        val xml = generateManifestXml(
            appId = resolvedAppId,
            appName = resolvedAppName,
            manifestConfig = manifestConfig,
            services = parsedServices,
            fragments = androidFragments,
            winningMainFragment = winningMain,
            defaultPackageName = resolvedPkg
        )

        targetFile.parentFile?.mkdirs()
        targetFile.writeText(xml)
    }

    companion object {
        /**
         * Encodes a [GenericUiAndroidServiceSpec] into a deterministic string for Gradle task input tracking.
         */
        fun serializeServiceSpec(spec: GenericUiAndroidServiceSpec): String = listOf(
            spec.className,
            spec.exported.toString(),
            spec.enabled.toString(),
            spec.foregroundServiceType,
            spec.permission,
            spec.process,
            spec.intentActions.joinToString(",")
        ).joinToString("|")

        /**
         * Decodes a [GenericUiAndroidServiceSpec] produced by [serializeServiceSpec].
         */
        fun deserializeServiceSpec(raw: String): GenericUiAndroidServiceSpec? {
            val parts = raw.split("|")
            val className = parts.getOrNull(0)?.trim().orEmpty()
            if (className.isEmpty()) return null
            return GenericUiAndroidServiceSpec(className).apply {
                exported = parts.getOrNull(1)?.toBooleanStrictOrNull() ?: false
                enabled = parts.getOrNull(2)?.toBooleanStrictOrNull() ?: true
                foregroundServiceType = parts.getOrNull(3).orEmpty()
                permission = parts.getOrNull(4).orEmpty()
                process = parts.getOrNull(5).orEmpty()
                val actions = parts.getOrNull(6).orEmpty()
                if (actions.isNotBlank()) {
                    actions.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { intentAction(it) }
                }
            }
        }

        /**
         * Generates the complete `AndroidManifest.xml` content.
         */
        fun generateManifestXml(
            appId: String,
            appName: String,
            manifestConfig: GenericUiAndroidManifestConfig,
            services: List<GenericUiAndroidServiceSpec>,
            fragments: List<GenericUiDiscoveredFragment>,
            winningMainFragment: GenericUiDiscoveredFragment? = fragments.firstOrNull { it.isMainFragment },
            defaultPackageName: String = appId
        ): String = buildString {
            val resolvedAppId = appId.trim().ifEmpty { "com.nexustools.genericui.app" }
            val resolvedPkg = defaultPackageName.trim().ifEmpty { resolvedAppId }
            val resolvedAppClass = manifestConfig.applicationClass.trim().ifEmpty {
                "$resolvedPkg.${GenericUiGenerateAndroidActivitiesTask.GENERATED_APPLICATION_SIMPLE_NAME}"
            }
            val resolvedTheme = manifestConfig.theme.trim().ifEmpty {
                "@style/Theme.AppCompat.DayNight.NoActionBar"
            }
            val defaultConfigChanges = manifestConfig.configChanges.trim().ifEmpty {
                "orientation|screenSize|screenLayout|keyboardHidden|uiMode|density"
            }
            val defaultOrientation = manifestConfig.screenOrientation.trim()

            appendLine("""<?xml version="1.0" encoding="utf-8"?>""")
            appendLine("""<manifest xmlns:android="http://schemas.android.com/apk/res/android"""")
            appendLine("""    package="${escapeXml(resolvedAppId)}">""")

            for (perm in manifestConfig.permissions) {
                val trimmedPerm = perm.trim()
                if (trimmedPerm.isNotEmpty()) {
                    appendLine("""    <uses-permission android:name="${escapeXml(trimmedPerm)}" />""")
                }
            }

            appendLine()
            appendLine("""    <application""")
            appendLine("""        android:name="${escapeXml(resolvedAppClass)}"""")
            appendLine("""        android:label="${escapeXml(appName)}"""")
            appendLine("""        android:theme="${escapeXml(resolvedTheme)}"""")
            appendLine("""        android:supportsRtl="true">""")

            for (frag in fragments) {
                val isMain = winningMainFragment != null && frag.qualifiedName == winningMainFragment.qualifiedName
                val exported = frag.exported || isMain
                val activityLabel = frag.label.trim().ifEmpty {
                    if (isMain) appName else frag.simpleName
                }
                val orientation = frag.screenOrientation.trim().ifEmpty { defaultOrientation }

                appendLine("""        <activity""")
                appendLine("""            android:name="${escapeXml(frag.generatedActivityQualifiedName)}"""")
                appendLine("""            android:label="${escapeXml(activityLabel)}"""")
                appendLine("""            android:exported="$exported"""")
                appendLine("""            android:configChanges="${escapeXml(defaultConfigChanges)}"""")
                appendLine("""            android:supportsPictureInPicture="${manifestConfig.supportsPictureInPicture}"""")
                if (orientation.isNotEmpty()) {
                    appendLine("""            android:screenOrientation="${escapeXml(orientation)}">""")
                } else {
                    appendLine("""            android:screenOrientation="unspecified">""")
                }

                if (isMain) {
                    appendLine("""            <intent-filter>""")
                    appendLine("""                <action android:name="android.intent.action.MAIN" />""")
                    appendLine("""                <category android:name="android.intent.category.LAUNCHER" />""")
                    appendLine("""            </intent-filter>""")
                }
                appendLine("""        </activity>""")
            }

            for (service in services) {
                appendLine("""        <service""")
                appendLine("""            android:name="${escapeXml(service.className)}"""")
                appendLine("""            android:exported="${service.exported}"""")
                val optionalAttrs = mutableListOf<String>()
                if (service.foregroundServiceType.isNotBlank()) {
                    optionalAttrs.add("""android:foregroundServiceType="${escapeXml(service.foregroundServiceType.trim())}"""")
                }
                if (service.permission.isNotBlank()) {
                    optionalAttrs.add("""android:permission="${escapeXml(service.permission.trim())}"""")
                }
                if (service.process.isNotBlank()) {
                    optionalAttrs.add("""android:process="${escapeXml(service.process.trim())}"""")
                }
                if (optionalAttrs.isEmpty() && service.intentActions.isEmpty()) {
                    appendLine("""            android:enabled="${service.enabled}" />""")
                } else {
                    appendLine("""            android:enabled="${service.enabled}"""")
                    optionalAttrs.forEachIndexed { index, attr ->
                        val isLast = index == optionalAttrs.lastIndex
                        if (isLast && service.intentActions.isEmpty()) {
                            appendLine("""            $attr />""")
                        } else if (isLast) {
                            appendLine("""            $attr>""")
                        } else {
                            appendLine("""            $attr""")
                        }
                    }
                    if (optionalAttrs.isEmpty()) {
                        appendLine("""            >""")
                    }
                    if (service.intentActions.isNotEmpty()) {
                        appendLine("""            <intent-filter>""")
                        for (action in service.intentActions) {
                            appendLine("""                <action android:name="${escapeXml(action)}" />""")
                        }
                        appendLine("""            </intent-filter>""")
                        appendLine("""        </service>""")
                    }
                }
            }

            appendLine("""    </application>""")
            appendLine("""</manifest>""")
        }

        private fun escapeXml(value: String): String = buildString(value.length) {
            for (ch in value) {
                when (ch) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '"' -> append("&quot;")
                    '\'' -> append("&apos;")
                    else -> append(ch)
                }
            }
        }
    }
}
