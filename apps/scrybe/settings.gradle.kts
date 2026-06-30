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

// ─── Sherpa-ONNX bootstrap ────────────────────────────────────────────────────
// Dependency resolution runs during Gradle configuration (after settings but
// before any task executes), so a downloadSherpaOnnx task always fires too late.
// Pre-download the AAR here, in the initialization phase, so it exists in the
// local-maven directory before Gradle attempts to resolve any configuration.
val sherpaBootstrapVersion = "1.13.0"
val sherpaBootstrapGroup = "com.k2fsa"
val sherpaBootstrapArtifact = "sherpa-onnx-android"
val localMavenDir = settingsDir.resolve(".gradle/local-maven")
val sherpaAarDir =
    localMavenDir.resolve(
        "${sherpaBootstrapGroup.replace('.', '/')}/$sherpaBootstrapArtifact/$sherpaBootstrapVersion",
    )
val sherpaAarFile = sherpaAarDir.resolve("$sherpaBootstrapArtifact-$sherpaBootstrapVersion.aar")
val sherpaPomFile = sherpaAarDir.resolve("$sherpaBootstrapArtifact-$sherpaBootstrapVersion.pom")

if (!sherpaAarFile.exists()) {
    logger.lifecycle("Downloading sherpa-onnx $sherpaBootstrapVersion...")
    sherpaAarDir.mkdirs()
    val downloadUrl =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/v$sherpaBootstrapVersion" +
            "/sherpa-onnx-$sherpaBootstrapVersion.aar"
    java.net.URI(downloadUrl).toURL().openStream().use { input ->
        sherpaAarFile.outputStream().use { output -> input.copyTo(output) }
    }
    sherpaPomFile.writeText(
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <project>
          <modelVersion>4.0.0</modelVersion>
          <groupId>$sherpaBootstrapGroup</groupId>
          <artifactId>$sherpaBootstrapArtifact</artifactId>
          <version>$sherpaBootstrapVersion</version>
          <packaging>aar</packaging>
        </project>
        """.trimIndent(),
    )
    logger.lifecycle("sherpa-onnx $sherpaBootstrapVersion downloaded.")
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = settingsDir.resolve(".gradle/local-maven").toPath().toUri() }
    }
    versionCatalogs {
        create("libs") {
            from(files("../../gradle/libs.versions.toml"))
        }
    }
}

includeBuild("../../shared") {
    dependencySubstitution {
        substitute(module("com.twobits.core:billing")).using(project(":billing"))
        substitute(module("com.twobits.core:common")).using(project(":common"))
        substitute(module("com.twobits.core:api-keys")).using(project(":api-keys"))
        substitute(module("com.twobits.core:network")).using(project(":network"))
        substitute(module("com.twobits.core:design")).using(project(":design"))
        substitute(module("com.twobits.core:secure-store")).using(project(":secure-store"))
        substitute(module("com.twobits.core:local-models")).using(project(":local-models"))
        substitute(module("com.twobits.core:pro")).using(project(":pro"))
    }
}

rootProject.name = "scrybe-android"

include(":app")

// Core modules
include(":core:local-ai")
include(":core:base") // Renamed from :core:common to avoid confusion with shared :common
project(":core:base").projectDir = File(settingsDir, "core/common")

include(":core:model")
include(":core:database")
include(":core:datastore")
include(":core:audio")
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
include(":feature:tasks")

// Service modules
include(":service:recording")

// Workers
include(":workers")
