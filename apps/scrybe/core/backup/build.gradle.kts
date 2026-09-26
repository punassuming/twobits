import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/**
 * Whole-history backup and restore.
 *
 * Separate from `:core:export` rather than folded into it. That module deliberately depends on
 * `:core:model` and not `:core:database` — it works in domain models and writes human-readable
 * transcripts. A faithful backup needs the entities themselves: domain models carry
 * `java.time.Instant`, which kotlinx.serialization has no built-in serializer for, while the
 * entities already hold the epoch `Long` the database stores. Restore is also not an export.
 */
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "dev.scrybe.core.backup"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { unitTests { isReturnDefaultValues = true } }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core:base"))
    implementation(project(":core:model"))
    implementation(project(":core:database"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.work.runtime.ktx)
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // `kotlin.reflect.full.primaryConstructor`, used by EntityFieldCoverageTest to compare each
    // entity against its DTO. Declared with kotlin("reflect") rather than a catalog alias so the
    // version tracks the Kotlin plugin automatically instead of being pinned separately and drifting
    // from it. Test-only — it does not ship.
    testImplementation(kotlin("reflect"))
}
