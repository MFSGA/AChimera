plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.rust.android) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.ktlint) apply false
}
val buildToolsVersion by extra("36.0.0")
val ndkVersion by extra("29.0.14206865")

val externalBuildRoot = providers.gradleProperty("chimera.buildRoot").orNull

allprojects {
    externalBuildRoot?.let { buildRoot ->
        val relativeProjectPath = path
            .removePrefix(":")
            .replace(':', java.io.File.separatorChar)
            .ifBlank { "root" }
        layout.buildDirectory.set(rootProject.file(buildRoot).resolve(relativeProjectPath))
    }

    repositories {
        google()
        mavenCentral()
    }
}
