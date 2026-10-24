import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinJvm)
    `java-library`
}

group = "com.nexustools.genericui"
version = "0.1.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
}

tasks.jar {
    manifest {
        attributes(
            "Implementation-Title" to "GenericUI IntelliJ IDEA Plugin",
            "Implementation-Version" to project.version,
            "Plugin-Id" to "com.nexustools.genericui.idea"
        )
    }
}
