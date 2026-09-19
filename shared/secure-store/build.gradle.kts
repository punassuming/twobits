import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library") version "8.7.3"
    id("org.jetbrains.kotlin.android") version "2.3.0"
    id("com.google.dagger.hilt.android") version "2.58"
    id("com.google.devtools.ksp") version "2.3.0"
    id("io.gitlab.arturbosch.detekt") version "1.23.7"
    id("org.jlleitschuh.gradle.ktlint") version "12.1.1"
}

group = "com.twobits.core"

android {
    namespace = "com.twobits.securestore"
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
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
}

// Applied per module, not from the root build file: see the comment there on why
// `subprojects { apply(...) }` cannot work in this build.
detekt {
    config.setFrom(rootProject.file("../detekt.yml"))
    basePath = rootProject.projectDir.absolutePath
    parallel = true
}

// Same reason detekt is declared here rather than in the root build file: the ktlint plugin also
// inspects the Android extension, and the shared root's classpath has no AGP on it.
//
// The engine version is pinned to match `ktlint` 1.5.0, which is what the pre-commit hook and
// AGENTS.md's local commands use. The Gradle plugin otherwise bundles an older engine, so the tool
// that formats a file and the tool that checks it would disagree — and the disagreement would only
// show up in CI, after the hook had already called the file clean.
ktlint {
    version.set("1.5.0")
    android.set(true)
    outputToConsole.set(true)
    ignoreFailures.set(false)
    filter {
        exclude("**/build/**")
        exclude("**/generated/**")
    }
}
