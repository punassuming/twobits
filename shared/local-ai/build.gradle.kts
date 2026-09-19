import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library") version "8.7.3"
    id("org.jetbrains.kotlin.android") version "2.3.0"
    id("io.gitlab.arturbosch.detekt") version "1.23.7"
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
    implementation("com.twobits.core:local-models")
    implementation(libs.litertlm.android)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.work.runtime.ktx)
}

// Applied per module, not from the root build file: see the comment there on why
// `subprojects { apply(...) }` cannot work in this build.
detekt {
    config.setFrom(rootProject.file("../detekt.yml"))
    basePath = rootProject.projectDir.absolutePath
    parallel = true
}
