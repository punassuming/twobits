plugins {
    id("com.android.library") version "8.7.3"
    id("org.jetbrains.kotlin.android") version "1.9.25"
}

group = "com.twobits.core"

android {
    namespace = "com.twobits.billing"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    api("com.revenuecat.purchases:purchases:8.7.0")
}
