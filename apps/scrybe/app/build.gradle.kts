import org.gradle.api.tasks.Copy

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

val releaseKeystorePath: String? = System.getenv("KEYSTORE_PATH")
val repositoryChangelog = projectDir.resolve("../CHANGELOG.md")
val generatedChangelogAssetsDir = layout.buildDirectory.dir("generated/assets/changelog")
val bundleChangelogAsset =
    tasks.register<Copy>("bundleChangelogAsset") {
        from(repositoryChangelog)
        into(generatedChangelogAssetsDir)
        rename { "CHANGELOG.md" }
    }

if (releaseKeystorePath != null) {
    listOf("STORE_PASSWORD", "KEY_ALIAS", "KEY_PASSWORD").forEach { varName ->
        requireNotNull(System.getenv(varName)) {
            "KEYSTORE_PATH is set but $varName is missing. " +
                "All four signing variables (KEYSTORE_PATH, STORE_PASSWORD, " +
                "KEY_ALIAS, KEY_PASSWORD) must be provided together."
        }
    }
}

android {
    namespace = "dev.scrybe.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.scrybe.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 1015000
        versionName = "1.15.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            storeFile = releaseKeystorePath?.let { file(it) }
            storePassword = System.getenv("STORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig =
                if (releaseKeystorePath != null) {
                    signingConfigs.getByName("release")
                } else {
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
    }
    buildFeatures {
        compose = true
    }
    splits {
        abi {
            isEnable = true
            reset()
            // Added x86_64 for emulator support
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs.useLegacyPackaging = false
    }
    sourceSets.getByName("main").assets.srcDir(generatedChangelogAssetsDir)
}

tasks.named("preBuild").configure {
    dependsOn(bundleChangelogAsset)
}

dependencies {
    implementation("com.twobits.core:billing")
    implementation("com.twobits.core:common")
    implementation("com.twobits.core:design")

    implementation(project(":core:audio"))
    implementation(project(":core:local-ai"))
    implementation(project(":core:base")) // Renamed from :core:common
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(project(":core:model"))
    implementation(project(":core:transforms"))

    implementation(project(":feature:capture"))
    implementation(project(":feature:file-manager"))
    implementation(project(":feature:history"))
    implementation(project(":feature:profiles"))
    implementation(project(":feature:session-detail"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:tasks"))
    implementation(project(":service:recording"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.hilt.navigation.compose)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit.ext)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
