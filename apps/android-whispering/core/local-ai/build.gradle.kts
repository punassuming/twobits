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

// Sherpa-ONNX is distributed as a prebuilt AAR on GitHub Releases only (not JitPack/Maven Central).
val sherpaOnnxVersion = "1.13.0"
val sherpaOnnxAarName = "sherpa-onnx-$sherpaOnnxVersion.aar"
val sherpaOnnxDest =
    layout.buildDirectory
        .file("sherpa-onnx/$sherpaOnnxAarName")
        .get()
        .asFile

val downloadSherpaOnnx by tasks.registering(Exec::class) {
    outputs.file(sherpaOnnxDest)
    commandLine(
        "curl",
        "-L",
        "-f",
        "--create-dirs",
        "-o",
        sherpaOnnxDest.absolutePath,
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/v$sherpaOnnxVersion/$sherpaOnnxAarName",
    )
}

afterEvaluate {
    tasks.named("preBuild").configure { dependsOn(downloadSherpaOnnx) }
}

dependencies {
    implementation(project(":core:datastore"))
    implementation(project(":core:model"))
    implementation(project(":core:transcription"))
    implementation(project(":core:transforms"))

    implementation(files(sherpaOnnxDest))
    implementation(libs.mediapipe.tasks.genai)
    implementation(libs.commons.compress)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    testImplementation(libs.junit)
}
