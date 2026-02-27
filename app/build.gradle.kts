import org.gradle.api.tasks.Exec

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val requestedTaskNames = gradle.startParameter.taskNames
val isDebugTaskRequested = requestedTaskNames.any { taskName ->
    taskName.contains("debug", ignoreCase = true)
}

val rustFfiEnabled = providers.gradleProperty("chimera.rustFfi")
    .map { value -> value.toBooleanStrictOrNull() == true }
    .orElse(isDebugTaskRequested)

val rustFfiCrateDir = rootProject.layout.projectDirectory.dir("uniffi/chimera-ffi")
val rustFfiJniLibsDir = layout.buildDirectory.dir("generated/rust/jniLibs")

val buildRustFfi by tasks.registering(Exec::class) {
    group = "rust"
    description = "Build Android Rust FFI shared libraries via cargo-ndk."
    outputs.dir(rustFfiJniLibsDir)
    onlyIf { rustFfiEnabled.get() }

    val outputDir = rustFfiJniLibsDir.get().asFile
    workingDir = rustFfiCrateDir.asFile
    commandLine(
        "cargo",
        "ndk",
        "-t",
        "armeabi-v7a",
        "-t",
        "arm64-v8a",
        "-t",
        "x86_64",
        "-o",
        outputDir.absolutePath,
        "build",
        "--release",
    )

    doFirst {
        outputDir.mkdirs()
    }
}

android {
    namespace = "rs.chimera.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "rs.chimera.android.dev"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
    }

    sourceSets {
        getByName("main").jniLibs.srcDir(rustFfiJniLibsDir)
    }
}

kotlin {
    jvmToolchain(21)
}

tasks.named("preBuild").configure {
    dependsOn(buildRustFfi)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
