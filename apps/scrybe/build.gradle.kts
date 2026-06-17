import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import java.net.URI

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ktlint) apply false
}

val sherpaOnnxVersion = "1.13.0"
val sherpaOnnxGroup = "com.k2fsa"
val sherpaOnnxArtifactId = "sherpa-onnx-android"
val localMavenDir = rootProject.projectDir.resolve(".gradle/local-maven")

tasks.register("downloadSherpaOnnx") {
    group = "dependencies"
    description = "Downloads sherpa-onnx AAR and creates a local Maven repository."
    
    val sherpaOnnxAarDir = localMavenDir.resolve("${sherpaOnnxGroup.replace('.', '/')}/$sherpaOnnxArtifactId/$sherpaOnnxVersion")
    val sherpaOnnxAar = file(sherpaOnnxAarDir.resolve("$sherpaOnnxArtifactId-$sherpaOnnxVersion.aar"))
    val sherpaOnnxPom = file(sherpaOnnxAarDir.resolve("$sherpaOnnxArtifactId-$sherpaOnnxVersion.pom"))

    outputs.file(sherpaOnnxAar)
    outputs.file(sherpaOnnxPom)

    doLast {
        if (!sherpaOnnxAar.exists()) {
            println("Downloading sherpa-onnx $sherpaOnnxVersion...")
            sherpaOnnxAarDir.mkdirs()
            val downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/v$sherpaOnnxVersion/sherpa-onnx-$sherpaOnnxVersion.aar"
            URI(downloadUrl).toURL().openStream().use { input ->
                sherpaOnnxAar.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            
            sherpaOnnxPom.writeText("""
                <?xml version="1.0" encoding="UTF-8"?>
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>$sherpaOnnxGroup</groupId>
                  <artifactId>$sherpaOnnxArtifactId</artifactId>
                  <version>$sherpaOnnxVersion</version>
                  <packaging>aar</packaging>
                </project>
            """.trimIndent())
        }
    }
}

subprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    extensions.configure<DetektExtension> {
        config.setFrom(rootProject.file("detekt.yml"))
        basePath = rootProject.projectDir.absolutePath
        parallel = true
    }

    tasks.withType<Detekt>().configureEach {
        jvmTarget = "17"
    }

    extensions.configure<KtlintExtension> {
        android.set(
            plugins.hasPlugin("com.android.application") ||
                plugins.hasPlugin("com.android.library")
        )
        outputToConsole.set(true)
        ignoreFailures.set(false)
        filter {
            exclude("**/build/**")
            exclude("**/generated/**")
        }
    }
    
    // Ensure download happens before any project needs to resolve dependencies
    tasks.configureEach {
        if (name.contains("prepare", ignoreCase = true) || name.contains("compile", ignoreCase = true)) {
            dependsOn(":downloadSherpaOnnx")
        }
    }
}

tasks.register("ktlintFormat") {
    description = "Formats Kotlin sources across all Android subprojects."
    group = "formatting"
    dependsOn(subprojects.map { "${it.path}:ktlintFormat" })
}

tasks.register("ktlintCheck") {
    description = "Runs ktlint checks across all Android subprojects."
    group = "verification"
    dependsOn(subprojects.map { "${it.path}:ktlintCheck" })
}

tasks.register("detekt") {
    description = "Runs detekt across all Android subprojects."
    group = "verification"
    dependsOn(subprojects.map { "${it.path}:detekt" })
}
