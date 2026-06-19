plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.shelfsnap.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.shelfsnap.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1011000
        versionName = "1.11.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Release signing is driven by environment variables so CI can inject a keystore
    // via secrets. When they are absent (local dev, forked PRs) we fall back to the
    // debug signing config so `assembleRelease` still produces an installable APK.
    // Use ?: "" so releaseStoreFile is non-nullable (file() takes Any, not Any?).
    val releaseStoreFile = System.getenv("SIGNING_STORE_FILE") ?: ""
    val hasReleaseSigning = releaseStoreFile.isNotBlank() && file(releaseStoreFile).exists()

    val releaseStorePassword = System.getenv("SIGNING_STORE_PASSWORD") ?: ""
    val releaseKeyAlias = System.getenv("SIGNING_KEY_ALIAS") ?: ""
    val releaseKeyPassword = System.getenv("SIGNING_KEY_PASSWORD") ?: ""

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                // Debug-signed so the artifact is still installable for testing.
                signingConfigs.getByName("debug")
            }
        }
    }

    bundle {
        language { enableSplit = true }
        density { enableSplit = true }
        abi { enableSplit = true }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        // Material3's TopAppBar (and related components) are gated behind
        // @ExperimentalMaterial3Api, which is @RequiresOptIn(level = ERROR). They're
        // used across many screens, so opt in once here rather than annotating each
        // file. Without this the debug Kotlin compile fails with "This material API
        // is experimental…" errors.
        freeCompilerArgs += "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api"
        // Type.kt pins DM Sans weights via FontVariation.Settings, which is gated
        // behind @ExperimentalTextApi (also @RequiresOptIn(level = ERROR)). Opt in
        // here so the variable-font typography compiles.
        freeCompilerArgs += "-opt-in=androidx.compose.ui.text.ExperimentalTextApi"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets.getByName("main").assets.srcDir(
        layout.buildDirectory.dir("generated/assets/changelog")
    )

    lint {
        // CI runs `:app:lintDebug` as an informational gate and uploads the HTML/XML
        // report as an artifact, but pre-existing findings must not block building the
        // debug/release test APKs. abortOnError=false keeps the report without failing
        // the build; checkReleaseBuilds=false stops `lintVitalRelease` (which
        // `assembleRelease` triggers by default) from blocking the signed release APK.
        // Tighten these (and add a baseline) once the existing issues are triaged.
        abortOnError = false
        checkReleaseBuilds = false
        warningsAsErrors = false
    }
}

// Bundle apps/shelf-snap/CHANGELOG.md into the app's assets so the in-app "What's new"
// screen renders the same source of truth that CI maintains. The generated copy
// is git-ignored; it's refreshed before every build.
val generatedChangelogAssetsDir = layout.buildDirectory.dir("generated/assets/changelog")

val copyChangelogToAssets by tasks.registering(Copy::class) {
    val changelog = rootProject.file("CHANGELOG.md")
    onlyIf { changelog.exists() }
    from(changelog)
    into(generatedChangelogAssetsDir)
}

tasks.named("preBuild") {
    dependsOn(copyChangelogToAssets)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // CameraX
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)

    // DataStore
    implementation(libs.datastore.preferences)

    // Networking
    implementation(libs.okhttp)
    implementation(libs.gson)

    // Permissions
    implementation(libs.accompanist.permissions)

    // Image loading
    implementation(libs.coil.compose)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Shared core modules
    implementation("com.twobits.core:billing")
    implementation("com.twobits.core:common")
    implementation("com.twobits.core:design")

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
