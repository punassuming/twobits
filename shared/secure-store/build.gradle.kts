import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library") version "8.7.3"
    id("org.jetbrains.kotlin.android") version "2.3.0"
    id("com.google.dagger.hilt.android") version "2.58"
    id("com.google.devtools.ksp") version "2.3.0"
    id("io.gitlab.arturbosch.detekt") version "1.23.7"
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
