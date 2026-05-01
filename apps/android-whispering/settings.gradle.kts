import java.io.File

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

// ---------------------------------------------------------------------------
// Sherpa-ONNX is not published to JitPack or Maven Central. It ships as a
// prebuilt AAR on GitHub Releases. We download it here (settings evaluation
// always runs, even with configuration cache) into a local Maven directory
// so that :core:local-ai can declare it as a normal Maven coordinate.
// Library modules cannot use direct local .aar file deps (AGP restriction),
// but they CAN resolve from a local Maven repository.
// ---------------------------------------------------------------------------
val sherpaOnnxVersion = "1.13.0"
val sherpaOnnxGroup = "com.k2fsa"
val sherpaOnnxArtifactId = "sherpa-onnx-android"
val localMavenDir = settingsDir.resolve(".gradle/local-maven")
val sherpaOnnxAarDir =
    localMavenDir.resolve(
        "${sherpaOnnxGroup.replace('.', '/')}/$sherpaOnnxArtifactId/$sherpaOnnxVersion",
    )
val sherpaOnnxAar = File(sherpaOnnxAarDir, "$sherpaOnnxArtifactId-$sherpaOnnxVersion.aar")

if (!sherpaOnnxAar.exists()) {
    println("Downloading sherpa-onnx $sherpaOnnxVersion…")
    sherpaOnnxAarDir.mkdirs()
    val downloadUrl =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/" +
            "v$sherpaOnnxVersion/sherpa-onnx-$sherpaOnnxVersion.aar"
    val exit =
        ProcessBuilder("curl", "-L", "-f", "-o", sherpaOnnxAar.absolutePath, downloadUrl)
            .inheritIO()
            .start()
            .waitFor()
    check(exit == 0) { "sherpa-onnx download failed (curl exit $exit)" }
    // Minimal POM so Gradle resolves this as an AAR artifact with no transitive deps.
    File(sherpaOnnxAarDir, "$sherpaOnnxArtifactId-$sherpaOnnxVersion.pom").writeText(
        """<?xml version="1.0" encoding="UTF-8"?>
<project>
  <modelVersion>4.0.0</modelVersion>
  <groupId>$sherpaOnnxGroup</groupId>
  <artifactId>$sherpaOnnxArtifactId</artifactId>
  <version>$sherpaOnnxVersion</version>
  <packaging>aar</packaging>
</project>
""",
    )
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = localMavenDir.toPath().toUri() }
    }
}

rootProject.name = "scrybe-android"

include(":app")

// Core modules
include(":core:local-ai")
include(":core:common")
include(":core:model")
include(":core:database")
include(":core:datastore")
include(":core:audio")
include(":core:network")
include(":core:transcription")
include(":core:transforms")
include(":core:export")

// Feature modules
include(":feature:capture")
include(":feature:file-manager")
include(":feature:history")
include(":feature:profiles")
include(":feature:session-detail")
include(":feature:settings")

// Service modules
include(":service:recording")

// Workers
include(":workers")
