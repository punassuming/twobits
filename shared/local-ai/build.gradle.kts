plugins {
    id("com.android.library") version "8.7.3"
    // litertlm-android's own Kotlin metadata is version 2.3.0 (confirmed from a real CI failure:
    // "Module was compiled with an incompatible version of Kotlin. The binary version of its
    // metadata is 2.3.0, expected version is 2.0.0") — every other module in this repo pins
    // 2.0.21, which can't read that. Bumped only here since this is the only module that
    // directly depends on litertlm-android; other modules' compilation is independent.
    // NOT independently verified beyond that CI error: whether "2.3.0" is exactly right (as
    // opposed to a lower version that also reads 2.3.0 metadata) or compatible with AGP 8.7.3.
    id("org.jetbrains.kotlin.android") version "2.3.0"
}

group = "com.twobits.core"

android {
    namespace = "com.twobits.localai"
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
    implementation(libs.litertlm.android)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
}
