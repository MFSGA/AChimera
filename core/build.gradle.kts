import org.gradle.api.GradleException
import org.gradle.api.tasks.Exec
import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val jniLibsDir = layout.buildDirectory.dir("generated/rust/jniLibs")

// note: to refactor the build process
val cargoTargetDir = layout.buildDirectory.dir("generated/rust/cargoTarget")
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use(::load)
    }
}
val configuredNdkVersion = rootProject.extra["ndkVersion"] as String
val androidSdkDir =
    localProperties.getProperty("sdk.dir")
        ?.takeIf(String::isNotBlank)
        ?.let(::file)
        ?: System.getenv("ANDROID_SDK_ROOT")?.takeIf(String::isNotBlank)?.let(::file)
        ?: System.getenv("ANDROID_HOME")?.takeIf(String::isNotBlank)?.let(::file)
val androidNdkDir =
    sequenceOf(
        localProperties.getProperty("ndk.dir"),
        System.getenv("ANDROID_NDK_HOME"),
        System.getenv("ANDROID_NDK_ROOT"),
        androidSdkDir?.resolve("ndk/$configuredNdkVersion")?.absolutePath,
        androidSdkDir?.resolve("ndk-bundle")?.absolutePath,
    )
        .filterNotNull()
        .map(::file)
        .firstOrNull(File::exists)

fun configureCargoNdkTask(task: Exec, release: Boolean) {
    task.workingDir = file("../uniffi")
    task.commandLine(
        "cargo",
        "ndk",
        "-t",
        "armeabi-v7a",
        "-t",
        "arm64-v8a",
        "-t",
        "x86_64",
        "-o",
        jniLibsDir.get().asFile.absolutePath,
        "build",
        "-p",
        "chimera-ffi",
    )
    if (release) {
        task.args("--release")
    }
    task.doFirst {
        val sdkDir = androidSdkDir
            ?: throw GradleException(
                "Android SDK path is not configured. Set sdk.dir in local.properties or ANDROID_SDK_ROOT."
            )
        val ndkDir = androidNdkDir
            ?: throw GradleException(
                "Android NDK $configuredNdkVersion is missing. Set ndk.dir in local.properties or install it under ${sdkDir.resolve("ndk").absolutePath}."
            )

        task.environment("ANDROID_HOME", sdkDir.absolutePath)
        task.environment("ANDROID_SDK_ROOT", sdkDir.absolutePath)
        task.environment("ANDROID_NDK_HOME", ndkDir.absolutePath)
        task.environment("ANDROID_NDK_ROOT", ndkDir.absolutePath)
        task.environment("CARGO_TARGET_DIR", cargoTargetDir.get().asFile.absolutePath)
    }
}

val buildCargoNdkDebug by tasks.registering(Exec::class) {
    configureCargoNdkTask(this, release = false)
}

val buildCargoNdkRelease by tasks.registering(Exec::class) {
    configureCargoNdkTask(this, release = true)
}

android {
    namespace = "rs.chimera.android.ffi"
    compileSdk = 36
    ndkVersion = configuredNdkVersion

    defaultConfig {
        minSdk = 23
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
    }

    buildToolsVersion = rootProject.extra["buildToolsVersion"] as String

    sourceSets {
        getByName("main").jniLibs.srcDir(jniLibsDir)
    }
}

kotlin {
    jvmToolchain(21)
}

tasks.named("preBuild").configure {
    dependsOn(buildCargoNdkDebug)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.runtime)
    implementation("net.java.dev.jna:jna:5.18.1@aar")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

android {
    libraryVariants.all {
        val variant = this
        val variantName = variant.name.replaceFirstChar(Char::titlecase)
        val bindingsDir = layout.projectDirectory.dir("src/main/java")
        val generateBindings = tasks.register("generate${variantName}UniFFIBindings", Exec::class) {
            workingDir = file("../uniffi")
            commandLine(
                "cargo",
                "run",
                "-p",
                "uniffi-bindgen",
                "generate",
                "--library",
                layout.buildDirectory.file("generated/rust/jniLibs/arm64-v8a/libchimera_ffi.so").get().asFile.absolutePath,
                "--language",
                "kotlin",
                "--out-dir",
                bindingsDir.asFile.absolutePath,
            )
            // TO DELETE
            environment("CARGO_TARGET_DIR", cargoTargetDir.get().asFile.absolutePath)
            dependsOn(if (variantName == "Release") buildCargoNdkRelease else buildCargoNdkDebug)
        }

        variant.javaCompileProvider.get().dependsOn(generateBindings)
        tasks.named("compile${variantName}Kotlin").configure {
            dependsOn(generateBindings)
        }
    }
}
