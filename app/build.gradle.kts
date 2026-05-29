plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val baseVersionName = "0.1.0"
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

android {
    namespace = "rs.chimera.android"
    compileSdk = 36
    ndkVersion = rootProject.extra["ndkVersion"] as String
    val keystore = env("KEYSTORE_FILE")

    defaultConfig {
        applicationId = "rs.chimera.android.dev"
        minSdk = 23
        targetSdk = 36
        versionCode = verCode
        versionName = verName

        resValue("string", "app_name", if (keystore == null) "chimera dev" else "Chimera Lite")
        resValue("string", "app_ver", verName)
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
        }
    }

    splits {
        abi {
            isEnable = env("ANDROID_SPLIT_ABI_ENABLE") == "true"
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86_64")
            isUniversalApk = env("ANDROID_SPLIT_ABI_UNIVERSAL_APK") == "true"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        resValues = true
    }
}

kotlin {
    jvmToolchain(21)
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
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.compose.destinations.core)

    debugImplementation(libs.androidx.ui.tooling)
}
