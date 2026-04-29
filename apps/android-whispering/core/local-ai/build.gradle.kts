import java.net.URI

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

// Sherpa-ONNX is not on JitPack or Maven Central; distributed as a prebuilt AAR on GitHub Releases.
val sherpaOnnxVersion = "1.13.0"
val sherpaOnnxAarName = "sherpa-onnx-$sherpaOnnxVersion.aar"
val sherpaOnnxAar = layout.buildDirectory.file("sherpa-onnx/$sherpaOnnxAarName")

val downloadSherpaOnnx by tasks.registering {
    outputs.file(sherpaOnnxAar)
    doLast {
        val dest = sherpaOnnxAar.get().asFile
        dest.parentFile.mkdirs()
        val url =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/" +
                "v$sherpaOnnxVersion/$sherpaOnnxAarName"
        URI(url).toURL().openStream().use { input ->
            dest.outputStream().use { input.copyTo(it) }
        }
    }
}

afterEvaluate {
    tasks.named("preBuild").configure { dependsOn(downloadSherpaOnnx) }
}

dependencies {
    implementation(project(":core:datastore"))
    implementation(project(":core:model"))
    implementation(project(":core:transcription"))
    implementation(project(":core:transforms"))

    implementation(files(sherpaOnnxAar))
    implementation(libs.mediapipe.tasks.genai)
    implementation(libs.commons.compress)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    testImplementation(libs.junit)
}
