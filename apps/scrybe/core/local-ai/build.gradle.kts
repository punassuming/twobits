import org.jetbrains.kotlin.gradle.dsl.JvmTarget

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
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("com.twobits.core:local-models")
    // api, not implementation: LocalModelManager publicly implements LlmDownloadSource, so
    // consumers of this module (e.g. feature:settings, which references LocalModelManager
    // directly) need that supertype on their own compile classpath too.
    api("com.twobits.core:local-ai")
    implementation(project(":core:datastore"))
    implementation(project(":core:model"))
    implementation(project(":core:transcription"))
    implementation(project(":core:transforms"))

    // Downloaded to .gradle/local-maven/ in settings.gradle.kts (library modules
    // cannot use direct local .aar file deps per AGP restriction).
    implementation("com.k2fsa:sherpa-onnx-android:1.13.0")
    implementation(libs.commons.compress)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.work.runtime.ktx)
    testImplementation(libs.junit)
}
