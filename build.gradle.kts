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
}
