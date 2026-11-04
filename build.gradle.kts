import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.tasks.BaseKotlinCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile

plugins {
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
}

allprojects {
    val parentSuffix = parent?.path?.trimStart(':')?.replace(':', '.') ?: ""
    group = if (parentSuffix.isEmpty()) {
        "com.nexustools.genericui"
    } else {
        "com.nexustools.genericui.$parentSuffix"
    }
    version = "0.1.0-SNAPSHOT"

    tasks.withType(org.gradle.api.tasks.testing.AbstractTestTask::class.java).configureEach {
        timeout.set(java.time.Duration.ofSeconds(120))
    }
}

fun addFriendCompilation(
    consumer: KotlinCompilation<*>,
    friendProj: Project,
    friendComp: KotlinCompilation<*>
) {
    val friendOutputs = friendComp.output.classesDirs
    val friendTargetName = friendComp.target.name
    val isJvmOrAndroid = friendTargetName == "jvm" ||
        friendTargetName == "android" ||
        friendTargetName.isEmpty()
    val jarTaskNames = if (isJvmOrAndroid) {
        setOf(
            "jvmJar",
            "jar",
            "bundleAndroidMainClassesToCompileJar",
            "bundleAndroidMainClassesToRuntimeJar"
        )
    } else {
        emptySet()
    }
    val friendJarOutputs = friendProj.tasks.matching { it.name in jarTaskNames }
    consumer.compileTaskProvider.configure {
        when (this) {
            is BaseKotlinCompile -> {
                friendPaths.from(friendOutputs)
                if (isJvmOrAndroid) {
                    friendPaths.from(friendJarOutputs)
                }
            }
            is KotlinNativeCompile -> {
                val friendModuleCol = javaClass.methods
                    .firstOrNull { it.name.startsWith("getFriendModule") && it.parameterCount == 0 }
                    ?.invoke(this) as? org.gradle.api.file.ConfigurableFileCollection
                friendModuleCol?.from(friendOutputs)
            }
        }
    }
}

fun collectDirectProjectDependencies(project: Project, commonOnly: Boolean): Set<Project> {
    val result = linkedSetOf<Project>()
    for (config in project.configurations.toList()) {
        if (commonOnly) {
            val name = config.name
            val isCommonConfig = name.startsWith("commonMain") ||
                name == "api" ||
                name == "implementation" ||
                name == "compileOnly"
            if (!isCommonConfig) continue
        }
        for (dep in config.dependencies.withType(ProjectDependency::class.java)) {
            val targetProject = project.rootProject.findProject(dep.path)
            if (targetProject != null && targetProject != project) {
                result.add(targetProject)
            }
        }
    }
    return result
}

fun collectTransitiveFriendProjects(start: Project, commonOnly: Boolean): Set<Project> {
    val visited = linkedSetOf<Project>()
    val queue = ArrayDeque<Project>()
    for (direct in collectDirectProjectDependencies(start, commonOnly)) {
        if (visited.add(direct)) {
            queue.add(direct)
        }
    }
    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        for (next in collectDirectProjectDependencies(current, commonOnly)) {
            if (next != start && visited.add(next)) {
                queue.add(next)
            }
        }
    }
    return visited
}

gradle.projectsEvaluated {
    for (subproject in subprojects) {
        if (
            subproject.path == ":common" ||
            subproject.path == ":gradle-plugin" ||
            subproject.path == ":idea-plugin" ||
            subproject.path.startsWith(":examples")
        ) {
            continue
        }

        val kmpExt = subproject.extensions.findByType(KotlinMultiplatformExtension::class.java)
        if (kmpExt != null) {
            val allFriends = collectTransitiveFriendProjects(subproject, commonOnly = false)
            val commonFriends = collectTransitiveFriendProjects(subproject, commonOnly = true)

            for (target in kmpExt.targets) {
                for (compilation in target.compilations) {
                    if (target.name == "metadata") {
                        for (friendProj in commonFriends) {
                            val friendKmp = friendProj.extensions.findByType(KotlinMultiplatformExtension::class.java)
                                ?: continue
                            if (friendKmp.targets.count { it.name != "metadata" } <= 1) continue
                            val friendComp = friendKmp.targets.findByName("metadata")
                                ?.compilations
                                ?.findByName("commonMain")
                            if (friendComp != null) {
                                addFriendCompilation(compilation, friendProj, friendComp)
                            }
                        }
                    } else {
                        for (friendProj in allFriends) {
                            val friendKmp = friendProj.extensions.findByType(KotlinMultiplatformExtension::class.java)
                            if (friendKmp != null) {
                                val friendTarget = friendKmp.targets.findByName(target.name)
                                val friendComp = friendTarget?.compilations?.findByName("main")
                                    ?: friendTarget?.compilations?.findByName("${target.name}Main")
                                if (friendComp != null) {
                                    addFriendCompilation(compilation, friendProj, friendComp)
                                }
                            } else if (target.name == "jvm") {
                                val friendJvm = friendProj.extensions.findByType(KotlinJvmProjectExtension::class.java)
                                val friendComp = friendJvm?.target?.compilations?.findByName("main")
                                if (friendComp != null) {
                                    addFriendCompilation(compilation, friendProj, friendComp)
                                }
                            }
                        }
                    }
                }
            }
        }

        val jvmExt = subproject.extensions.findByType(KotlinJvmProjectExtension::class.java)
        if (jvmExt != null) {
            val allFriends = collectTransitiveFriendProjects(subproject, commonOnly = false)
            for (compilation in jvmExt.target.compilations) {
                for (friendProj in allFriends) {
                    val friendKmp = friendProj.extensions.findByType(KotlinMultiplatformExtension::class.java)
                    if (friendKmp != null) {
                        val friendComp = friendKmp.targets.findByName("jvm")
                            ?.compilations
                            ?.findByName("main")
                        if (friendComp != null) {
                            addFriendCompilation(compilation, friendProj, friendComp)
                        }
                    } else {
                        val friendJvm = friendProj.extensions.findByType(KotlinJvmProjectExtension::class.java)
                        val friendComp = friendJvm?.target?.compilations?.findByName("main")
                        if (friendComp != null) {
                            addFriendCompilation(compilation, friendProj, friendComp)
                        }
                    }
                }
            }
        }
    }
}
