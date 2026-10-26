package com.nexustools.genericui.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Gradle task (`genericuiValidateApp`) that validates `@MainAppFragment`, `@AppFragment`, and
 * `@AppApplication` declarations across the project's Kotlin sources and enabled backend targets.
 *
 * Enforces:
 * 1. Every enabled backend target resolves to a single unambiguous `@MainAppFragment` at the highest
 *    matching `AppTarget` specificity rank (`5 -> 0`).
 * 2. Every discovered `@MainAppFragment`, `@AppFragment`, and `@AppApplication` class provides a
 *    no-arg (or all-default-parameter) constructor.
 */
abstract class GenericUiValidateAppTask : DefaultTask() {

    @get:Input
    abstract val projectRootPath: Property<String>

    @get:Input
    @get:Optional
    abstract val extraSourceDirectories: ListProperty<String>

    @get:Input
    abstract val enabledBackendIds: ListProperty<String>

    @get:Input
    @get:Optional
    abstract val generatedLauncherPackage: Property<String>

    @get:Input
    abstract val requireMainAppFragment: Property<Boolean>

    @get:OutputFile
    @get:Optional
    abstract val validationReportFile: RegularFileProperty

    init {
        group = GenericUiGradlePlugin.TASK_GROUP
        description = "Validates @MainAppFragment and @AppFragment annotations for all enabled GenericUI backend targets."
        requireMainAppFragment.convention(true)
    }

    @TaskAction
    fun validateApp() {
        val projectDir = File(projectRootPath.get())
        val extraDirs = extraSourceDirectories.getOrElse(emptyList()).map { path ->
            val f = File(path)
            if (f.isAbsolute) f else File(projectDir, path)
        }
        val enabledBackends = enabledBackendIds.getOrElse(emptyList())
            .mapNotNull { GenericUiVariantScope.fromNameOrNull(it) }
            .distinct()

        val reportLines = validateProjectSources(
            projectDir = projectDir,
            extraSourceDirs = extraDirs,
            enabledBackends = enabledBackends,
            generatedLauncherPackage = generatedLauncherPackage.orNull.orEmpty(),
            requireMainAppFragment = requireMainAppFragment.getOrElse(true)
        )

        val outFile = validationReportFile.orNull?.asFile
        if (outFile != null) {
            outFile.parentFile?.mkdirs()
            outFile.writeText(reportLines.joinToString("\n") + "\n")
        }
    }

    companion object {
        /**
         * Scans [projectDir] (plus any [extraSourceDirs]) and validates `@MainAppFragment` / `@AppFragment`
         * rules for [enabledBackends].
         *
         * @return diagnostic summary lines written to the validation report when validation succeeds.
         * @throws IllegalStateException if validation fails.
         */
        fun validateProjectSources(
            projectDir: File,
            extraSourceDirs: List<File> = emptyList(),
            enabledBackends: List<GenericUiVariantScope> = emptyList(),
            generatedLauncherPackage: String = "",
            requireMainAppFragment: Boolean = true
        ): List<String> {
            val discoveredFromProject = GenericUiFragmentScanner.scanProject(projectDir)
            val discoveredFromExtra = if (extraSourceDirs.isNotEmpty()) {
                GenericUiFragmentScanner.scanSourceDirectories(extraSourceDirs)
            } else {
                emptyList()
            }
            val discovered = (discoveredFromProject + discoveredFromExtra)
                .distinctBy { "${it.qualifiedName}:${it.isMainFragment}:${it.isApplicationClass}" }

            return validateDiscoveredFragments(
                discoveredFragments = discovered,
                enabledBackends = enabledBackends,
                generatedLauncherPackage = generatedLauncherPackage,
                requireMainAppFragment = requireMainAppFragment
            )
        }

        /**
         * Validates pre-scanned [discoveredFragments] against [enabledBackends].
         */
        fun validateDiscoveredFragments(
            discoveredFragments: List<GenericUiDiscoveredFragment>,
            enabledBackends: List<GenericUiVariantScope>,
            generatedLauncherPackage: String = "",
            requireMainAppFragment: Boolean = true
        ): List<String> {
            val mainFragments = discoveredFragments.filter { it.isMainFragment && !it.isApplicationClass }

            // Validate constructors of all discovered fragments/applications immediately
            for (fragment in discoveredFragments) {
                if (!fragment.hasNoArgConstructor) {
                    val annotationLabel = when {
                        fragment.isApplicationClass -> "@AppApplication"
                        fragment.isMainFragment -> "@MainAppFragment"
                        else -> "@AppFragment"
                    }
                    throw IllegalStateException(
                        "$annotationLabel class '${fragment.qualifiedName}' must declare a no-arg constructor " +
                            "(or default values for all primary constructor parameters) so GenericUI can instantiate it."
                    )
                }
            }

            if (mainFragments.isEmpty() && !requireMainAppFragment) {
                return listOf(
                    "mode=library",
                    "discoveredFragments=${discoveredFragments.size}"
                )
            }

            val backendsToValidate = enabledBackends.ifEmpty {
                listOf(GenericUiVariantScope.COMMON)
            }

            val report = mutableListOf<String>()
            report.add("mode=annotation-driven")
            report.add("discoveredMainFragments=${mainFragments.size}")
            report.add("discoveredTotal=${discoveredFragments.size}")

            for (backend in backendsToValidate) {
                val winningMain = GenericUiMainFragmentResolver.resolveMainFragmentForBackend(
                    discoveredFragments = discoveredFragments,
                    targetBackend = backend
                )
                val applicable = GenericUiMainFragmentResolver.resolveApplicableFragmentsForBackend(
                    discoveredFragments = discoveredFragments,
                    targetBackend = backend
                )
                val customApp = GenericUiMainFragmentResolver.resolveApplicationForBackendOrNull(
                    discoveredFragments = discoveredFragments,
                    targetBackend = backend
                )
                report.add(
                    "backend.${backend.id}.mainFragment=${winningMain.qualifiedName}" +
                        " (applicableFragments=${applicable.size}, application=${customApp?.qualifiedName ?: "FragmentApplication"})"
                )
            }

            return report
        }
    }
}
