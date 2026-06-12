plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.ktlint)
}

val baseVersionName = "0.4.1"
val Project.verName: String get() = "${baseVersionName}$versionNameSuffix.${exec("git rev-parse --short HEAD")}"
val Project.verCode: Int get() = exec("git rev-list --count HEAD").toInt()
val Project.isDevVersion: Boolean get() = exec("git tag -l v$baseVersionName").isEmpty()
val Project.versionNameSuffix: String get() = if (isDevVersion) ".dev" else ""

fun Project.exec(command: String): String =
    providers
        .exec {
            commandLine(command.split(" "))
        }.standardOutput.asText
        .get()
        .trim()

fun env(key: String): String? = System.getenv(key).let { if (it.isNullOrEmpty()) null else it }

val fullAbiBuild = providers
    .gradleProperty("chimera.fullAbi")
    .map(String::toBoolean)
    .getOrElse(
        gradle.startParameter.taskNames.any { taskName ->
            taskName.contains("Release", ignoreCase = true)
        },
    )
val enabledAbis = if (fullAbiBuild) {
    listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
} else {
    listOf("x86_64")
}

android {
    namespace = "rs.chimera.android"
    compileSdk = 37
    ndkVersion = rootProject.extra["ndkVersion"] as String
    val keystore = env("KEYSTORE_FILE")

    defaultConfig {
        applicationId = "rs.chimera.android"
        minSdk = 23
        targetSdk = 37
        versionCode = verCode
        versionName = verName

        resValue("string", "app_name", if (keystore == null) "chimera dev" else "Chimera Lite")
        resValue("string", "app_ver", verName)

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters.addAll(enabledAbis)
        }
    }

    signingConfigs {
        if (keystore == null) {
            return@signingConfigs
        }
        create("release") {
            storeFile = file(keystore)
            storePassword = env("KEYSTORE_PASSWORD")
            keyAlias = env("KEY_ALIAS")
            keyPassword = env("KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (keystore != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            isMinifyEnabled = false
            if (keystore == null) {
                applicationIdSuffix = ".dev"
            }
        }
    }

    splits {
        abi {
            isEnable = env("ANDROID_SPLIT_ABI_ENABLE") == "true"
            reset()
            include(*enabledAbis.toTypedArray())
            isUniversalApk = env("ANDROID_SPLIT_ABI_UNIVERSAL_APK") == "true"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_25
        targetCompatibility = JavaVersion.VERSION_25
    }

    buildFeatures {
        compose = true
        resValues = true
    }
}

kotlin {
    jvmToolchain(25)
}

dependencies {
    implementation(project(":core"))
    implementation(files("../deps/rustls-platform-verifier-0.1.1.aar"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.compose.destinations.core)
    implementation(libs.androidx.runtime.livedata)

    ksp(libs.compose.destinations.ksp)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    ktlintRuleset(libs.ktlint.compose.rules)
}
