plugins {
    id("com.android.library") version "8.7.3"
    id("org.jetbrains.kotlin.android") version "2.0.21"
    id("com.google.dagger.hilt.android") version "2.51.1"
    id("com.google.devtools.ksp") version "2.0.21-1.0.25"
}

group = "com.twobits.core"

android {
    namespace = "com.twobits.common"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
}
