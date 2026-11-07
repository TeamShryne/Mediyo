@file:Suppress("UnstableApiUsage")

plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("kapt")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.teamshryne.mediyo"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    // Version comes from the release workflow (-PversionCode/-PversionName from
    // the git tag). Falls back to the next release line for local builds.
    val appVersionCode =
        (project.findProperty("versionCode") as String?
            ?: System.getenv("VERSION_CODE") ?: "9000").toInt()
    val appVersionName =
        (project.findProperty("versionName") as String?
            ?: System.getenv("VERSION_NAME") ?: "0.9.0") as String

    // Release signing comes from env (CI decodes the keystore from secrets).
    val keystorePath = System.getenv("ANDROID_KEYSTORE_PATH")

    signingConfigs {
        create("release") {
            if (!keystorePath.isNullOrBlank() && file(keystorePath).exists()) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }

    defaultConfig {
        applicationId = "com.teamshryne.mediyo"
        minSdk = 24
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
        buildConfigField("String", "GIT_SHA", "\"${gitSha()}\"")
        val channel = (project.findProperty("channel") as String?) ?: "stable"
        buildConfigField("String", "CHANNEL", "\"$channel\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isCrunchPngs = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (!keystorePath.isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            isCrunchPngs = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    flavorDimensions += "version"
    productFlavors {
        create("foss") {
            dimension = "version"
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        jvmToolchain(17)
    }
    kotlinOptions {
        freeCompilerArgs = freeCompilerArgs + "-Xcontext-receivers"
        jvmTarget = "17"
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
    lint {
        // Resolved against the module directory, so this is `app/lint.xml`.
        lintConfig = file("lint.xml")
        disable += "NewApi"
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

/**
 * Short HEAD SHA for BuildConfig.
 *
 * Read from `GIT_SHA` when the environment provides it (CI does), otherwise
 * from `.git` directly. Starting a process here would make the configuration
 * cache non-storable, and this build runs with it on.
 */
fun gitSha(): String {
    System.getenv("GIT_SHA")?.trim()?.takeIf { it.isNotEmpty() }?.let { return it.take(7) }
    return try {
        var gitDir = rootProject.file(".git")
        if (gitDir.isFile) {
            val pointer = gitDir.readText().trim().removePrefix("gitdir: ")
            gitDir = rootProject.file(pointer)
        }
        val head = gitDir.resolve("HEAD").readText().trim()
        val sha = if (head.startsWith("ref: ")) {
            val refFile = gitDir.resolve(head.removePrefix("ref: "))
            if (refFile.exists()) refFile.readText().trim() else ""
        } else {
            head
        }
        sha.take(7).ifEmpty { "unknown" }
    } catch (_: Exception) {
        "unknown"
    }
}

dependencies {
    implementation(libs.guava)
    implementation(libs.coroutines.guava)
    implementation(libs.concurrent.futures)

    implementation(libs.activity)
    implementation(libs.navigation)
    implementation(libs.hilt.navigation)
    implementation(libs.datastore)

    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.util)
    implementation(libs.compose.ui.tooling)
    implementation(libs.compose.animation)
    implementation(libs.compose.animation.graphics)
    implementation(libs.compose.reorderable)
    implementation(libs.compose.material.icons)

    implementation(libs.viewmodel)
    implementation(libs.viewmodel.compose)

    implementation(libs.material3)
    implementation(libs.palette)
    implementation(projects.materialColorUtilities)
    implementation(libs.squigglyslider)

    implementation(libs.coil)

    implementation(libs.shimmer)

    implementation(libs.media3)
    implementation(libs.media3.session)
    implementation(libs.media3.okhttp)

    implementation(libs.room.runtime)
    ksp(libs.room.compiler)
    implementation(libs.room.ktx)

    implementation(libs.apache.lang3)

    implementation(libs.hilt)
    kapt(libs.hilt.compiler)

    implementation(projects.innertube)
    implementation(projects.kugou)
    implementation(projects.lrclib)

    implementation(libs.ktor.client.core)

    implementation(libs.kotlinx.serialization.json)

    // Go core: search / artist / artist sections / podcast / episodes.
    // CI downloads it (see scripts/fetch-mediyo-core.sh and gradle.properties).
    implementation(files("libs/mediyo.aar"))

    coreLibraryDesugaring(libs.desugaring)

    implementation(libs.timber)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
