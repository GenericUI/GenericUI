import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

group = "com.nexustools.genericui"
version = "0.1.0"

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
    compileOnly(gradleApi())
    implementation(libs.kotlin.gradle.plugin)
    testImplementation(libs.kotlin.test)
    testImplementation(gradleTestKit())
}

gradlePlugin {
    plugins {
        create("genericUiPlugin") {
            id = "com.nexustools.genericui"
            implementationClass = "com.nexustools.genericui.plugin.GenericUiGradlePlugin"
            displayName = "GenericUI Multiplatform Configuration & Packaging Plugin"
            description = "Configures GenericUI multiplatform targets, optional LWJGL GL & Vulkan libraries, Google ANGLE & MoltenVK native binaries, SASS compilation, and extensions (:extensions:aichat, :extensions:threejs, :extensions:box2d, :extensions:box3d)."
        }
        create("genericUiSettingsPlugin") {
            id = "com.nexustools.genericui.settings"
            implementationClass = "com.nexustools.genericui.plugin.GenericUiSettingsPlugin"
            displayName = "GenericUI Settings & Android App Wrapper Plugin"
            description = "Synthesizes and includes a companion Android application wrapper module (build/genericui/android-app) for multiplatform GenericUI projects."
        }
    }
}
