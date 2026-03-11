import org.gradle.api.tasks.Exec

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val jniLibsDir = layout.buildDirectory.dir("generated/rust/jniLibs")

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
    ndkVersion = rootProject.extra["ndkVersion"] as String

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
            dependsOn(if (variantName == "Release") buildCargoNdkRelease else buildCargoNdkDebug)
        }

        variant.javaCompileProvider.get().dependsOn(generateBindings)
        tasks.named("compile${variantName}Kotlin").configure {
            dependsOn(generateBindings)
        }
    }
}
