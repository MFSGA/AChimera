plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.rust.android)
}

android {
    namespace = "rs.chimera.android.ffi"
    compileSdk = 37

    ndkVersion = rootProject.extra["ndkVersion"] as String
    buildToolsVersion = rootProject.extra["buildToolsVersion"] as String

    defaultConfig {
        minSdk = 23
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_25
        targetCompatibility = JavaVersion.VERSION_25
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    jvmToolchain(25)
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

val hasSccache = System.getenv("PATH")
    ?.split(File.pathSeparator)
    ?.any { dir -> File(dir, "sccache").exists() }
    ?: false
val fullAbiBuild = providers
    .gradleProperty("chimera.fullAbi")
    .map(String::toBoolean)
    .getOrElse(
        gradle.startParameter.taskNames.any { taskName ->
            taskName.contains("Release", ignoreCase = true)
        },
    )

cargo {
    module = "../uniffi"
    libname = "chimera_ffi"
    // Resolve Python from PATH so Nix, Linux, and Windows environments can provide it differently.
    pythonCommand = "python3"

    extraCargoBuildArguments = arrayListOf("--locked", "-p", "chimera-ffi")

    if (hasSccache) {
        environmentalOverrides["RUSTC_WRAPPER"] = "sccache"
    }
    environmentalOverrides["RUSTC_BOOTSTRAP"] = "1"

    targets = if (fullAbiBuild) {
        listOf("arm64", "arm", "x86", "x86_64")
    } else {
        listOf("x86_64")
    }

    profile = if (fullAbiBuild) "release" else "debug"
}

val rustJniLibsDir = layout.buildDirectory.dir("rustJniLibs/android").get()!!
tasks.matching { it.name.matches(Regex("merge.*JniLibFolders")) }.configureEach {
    inputs.dir(rustJniLibsDir)
    dependsOn("cargoBuild")
}
