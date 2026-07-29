import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library") version "8.7.3"
    // litertlm-android's own Kotlin metadata is version 2.3.0 (confirmed from a real CI failure:
    // "Module was compiled with an incompatible version of Kotlin. The binary version of its
    // metadata is 2.3.0, expected version is 2.0.0") — every other module in this repo pins
    // 2.0.21, which can't read that. Bumped only here since this is the only module that
    // directly depends on litertlm-android; other modules' compilation is independent.
    // NOT independently verified beyond that CI error: whether "2.3.0" is exactly right (as
    // opposed to a lower version that also reads 2.3.0 metadata) or compatible with AGP 8.7.3.
    //
    // 2.3.0's Kotlin Gradle plugin also hard-errors on the legacy `kotlinOptions { jvmTarget }`
    // DSL every other module here still uses (fine on their pinned 2.0.21) — confirmed from a
    // second real CI failure: "Using 'jvmTarget: String' is an error. Please migrate to the
    // compilerOptions DSL." Migrated below, scoped to this module only.
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
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.litertlm.android)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
}
