import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// ---------------------------------------------------------------------------------------------
// SIGNING: release keys never live in this repository and never reach CI.
//   * The release keystore and its passwords stay in a folder OUTSIDE the project
//     (default ~/.wishyclip-signing, override with env WISHYCLIP_SIGNING_DIR or -Pwishyclip.signingDir=...).
//     Create it once with:  python tools/release/setup_signing.py
//   * On CI (env CI=true) no release signing is ever applied and release tasks are refused.
//   * Debug builds use the public, shared tools/signing/debug.keystore and a different application id
//     (org.wishyclip.app.debug), so a test build can never conflict with the real app's signature.
// Read AGENT_RELEASE_GUIDE.md before touching anything here.
// ---------------------------------------------------------------------------------------------
val onCi = System.getenv("CI") == "true"
val signingDir: File = File(
    System.getenv("WISHYCLIP_SIGNING_DIR")
        ?: providers.gradleProperty("wishyclip.signingDir").orNull
        ?: "${System.getProperty("user.home")}/.wishyclip-signing"
)
val keystoreProperties = Properties().apply {
    val f = File(signingDir, "keystore.properties")
    if (!onCi && f.exists()) FileInputStream(f).use { load(it) }
}
val releaseStoreFile: File? = keystoreProperties.getProperty("storeFile")?.let { name ->
    File(name).takeIf { it.isAbsolute } ?: File(signingDir, name)
}?.takeIf { it.exists() }
val hasReleaseSigning = !onCi && releaseStoreFile != null &&
    keystoreProperties.getProperty("storePassword") != null &&
    keystoreProperties.getProperty("keyAlias") != null &&
    keystoreProperties.getProperty("keyPassword") != null
val selfUpdate = providers.gradleProperty("wishyclip.selfUpdate").orNull?.toBoolean() ?: true

gradle.taskGraph.whenReady {
    if (onCi && allTasks.any { it.name in setOf("assembleRelease", "bundleRelease", "packageRelease", "signReleaseBundle") }) {
        throw GradleException(
            "Release builds are never made on CI: the signing key must not leave the maintainer's machine. " +
                "Build locally with: python tools/release/build_release.py"
        )
    }
}

android {
    namespace = "org.wishyclip.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.wishyclip.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "1.2.0"

        manifestPlaceholders["appLabel"] = "@string/app_name"
        buildConfigField("boolean", "SELF_UPDATE", selfUpdate.toString())
        buildConfigField("String", "UPDATE_REPO", "\"wishissue/WishyClip\"")
    }

    signingConfigs {
        // The shared, public debug key (password "android"). Safe to commit: it protects nothing.
        getByName("debug") {
            storeFile = rootProject.file("tools/signing/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Its own app id: installs next to the real app instead of fighting its signature.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            manifestPlaceholders["appLabel"] = "WishyClip (debug)"
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            // Without the local key the release APK stays UNSIGNED (never silently debug-signed).
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation("androidx.graphics:graphics-core:1.0.4")
    implementation("net.jpountz.lz4:lz4:1.3.0")
    implementation("androidx.metrics:metrics-performance:1.0.0")

    // Audio playback in the editor (see audio/AudioTracks.kt).
    implementation(libs.androidx.media3.exoplayer)

    implementation(libs.coil.compose)
    implementation(libs.androidx.datastore.preferences)
    // Folder access (Storage Access Framework) for projects stored outside the app.
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
}
