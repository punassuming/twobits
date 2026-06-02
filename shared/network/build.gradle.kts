plugins {
    id("com.android.library") version "8.7.3"
    id("org.jetbrains.kotlin.android") version "1.9.25"
    id("com.google.dagger.hilt.android") version "2.51.1"
    id("com.google.devtools.ksp") version "1.9.25-1.0.20"
}

group = "com.twobits.core"

android {
    namespace = "com.twobits.network"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions { jvmTarget = "1.8" }
    buildFeatures { buildConfig = true }
}

dependencies {
    api("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.google.dagger:hilt-android:2.51.1")
    ksp("com.google.dagger:hilt-android-compiler:2.51.1")
}
