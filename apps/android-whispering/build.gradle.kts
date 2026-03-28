import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.jlleitschuh.gradle.ktlint.KtlintExtension

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ktlint) apply false
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
