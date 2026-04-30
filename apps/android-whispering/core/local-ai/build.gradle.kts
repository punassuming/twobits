plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "dev.scrybe.core.localai"
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
    implementation(project(":core:datastore"))
    implementation(project(":core:model"))
    implementation(project(":core:transcription"))
    implementation(project(":core:transforms"))

    // Downloaded to .gradle/local-maven/ in settings.gradle.kts (library modules
    // cannot use direct local .aar file deps per AGP restriction).
    implementation("com.k2fsa:sherpa-onnx-android:1.13.0")
    implementation(libs.mediapipe.tasks.genai)
    implementation(libs.commons.compress)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    testImplementation(libs.junit)
}
