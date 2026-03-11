plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.cargo.ndk) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.ktlint) apply false
}
val buildToolsVersion by extra("36.0.0")
val ndkVersion by extra("29.0.14206865")

fun RepositoryHandler.rustlsPlatformVerifier(): MavenArtifactRepository? {
    val manifestPath = runCatching {
        val dependencyJson = providers.exec {
            workingDir = File(project.rootDir, "./uniffi")
            commandLine(
                "cargo",
                "metadata",
                "--format-version",
                "1",
                "--filter-platform",
                "aarch64-linux-android",
                "--manifest-path",
                "chimera-ffi/Cargo.toml"
            )
        }.standardOutput.asText

        val jsonSlurper = groovy.json.JsonSlurper()
        val jsonData = jsonSlurper.parseText(dependencyJson.get()) as Map<*, *>
        val packages = jsonData["packages"] as List<*>
        val path = packages
            .firstOrNull { element ->
                val pkg = element as Map<*, *>
                pkg["name"] == "rustls-platform-verifier-android"
            }
            ?.let { it as Map<*, *> }
            ?.get("manifest_path") as String?
            ?: return null

        File(path)
    }.getOrNull() ?: return null

    return maven {
        url = uri(File(manifestPath.parentFile, "maven").path)
        metadataSources.artifact()
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        rustlsPlatformVerifier()
    }
}
